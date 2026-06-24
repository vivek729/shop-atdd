# 2026-06-24 10:49 UTC — Make the `local` stage a superset of the commit stage

## TL;DR

**Why:** The meta-prerelease run on `d1fc8bb2` had every `run / local (...)` job
green while two `run / commit (...)` jobs went red (`multitier-backend-typescript`
lint + component, `multitier-backend-dotnet` unit + component/Pact). That is
**structural, not a fluke**: the `local` job and the `commit-stage` job run
**disjoint suites**, so a green `local` cannot predict a green `commit`.

- `local` (`_prerelease-pipeline.yml:105`): compile everything → `gh optivem
  system start` → `gh optivem test setup` → `gh optivem test run --sample`
  (latest + legacy). **No lint, no unit, no component/Pact.**
- `commit-stage` (per-project `*-commit-stage.yml`): compile → **lint** → **unit**
  → **component/Pact** (+ Docker build/push + Sonar).

**Decision (user, 2026-06-24):** Option **A** — keep both stages, but make
`local` a **superset** of the commit-stage *test gates* so that **green local ⟹
green commit**. The Docker build/push and Sonar analysis stay commit-stage-only
(they produce/scan the artifact; they are not test gates).

**End result (target state):** the `local` job in `_prerelease-pipeline.yml` also
runs that config's **lint + unit + component/Pact** checks, mirroring the exact
commands the matching `*-commit-stage.yml` workflows use, gated by the same
`architecture`/`language` conditions the existing compile steps already carry.
Concretely, once this lands:

- **Order (per config):** compile → **lint → unit → component/Pact** → `gh optivem
  system start` → `test setup` → `test run --sample`. The new checks sit **before
  `system start`** (resolved **OQ-1(a)**) so they fail fast and never collide on
  ports with the real+stub stack.
- **Frontend:** every multitier `local` config (java, dotnet, typescript) runs
  frontend-react `npm run lint` + `--component frontend` (resolved **OQ-2(b)** —
  true superset, each config self-contained), in addition to its backend checks.
  Frontend has no unit step.
- **Wiring:** the checks are **inlined** in `_prerelease-pipeline.yml` (resolved
  **OQ-3(a)**), each block carrying a comment pointing at its source
  `*-commit-stage.yml`; a separate follow-up plan tracks extracting a shared
  composite action in `optivem/actions`.
- **No new legacy axis** (resolved **OQ-4**): the SHA/source-level checks run once
  per config; the existing latest+legacy system-test sample split is untouched.

**What the user observes:** a green `local` line for a config now **guarantees** a
green `commit-stage` line for the same config's test gates — the structural gap
that let `d1fc8bb2` go green-local / red-commit is closed. `local` wall-clock grows
(it now does lint + unit + component before the sample), but failures surface at
the first fail-fast gate instead of after the commit-stage fan-out.

**Explicitly unchanged:** the `commit-stage` jobs still own **Docker build/push**
and **Sonar** (artifact-producing/scanning steps, not test gates); `local` does
not gain those. The stages stay separate jobs (Option A, not a collapse).

## Background — why "local" is misleading

"local" is a **stage name**, not a location. The `local` job is
`runs-on: ubuntu-latest` — it runs **on CI**, like every other stage. Its role in
the CD model is the cheap, single-runner fail-fast smoke (brings up the
real+stub docker-compose stack itself and runs a *sample* of the system tests)
that gates the expensive downstream fan-out (`commit-stage` builds + pushes GHCR
images; `acceptance-stage` deploys them). Because it is the first fail-fast gate,
it is the right place to also run the cheapest, highest-signal checks
(lint/unit/component) — today it skips exactly those.

## Project → checks mapping

The meta `local` matrix is **per-config** (6 variants); meta `commit` is
**per-project** (7 workflows, `_meta-prerelease-pipeline.yml:507-528`). Each
config's `local` job must mirror the commit checks of the project(s) under it:

| local config (variant) | projects to mirror | unit | lint | component |
|---|---|---|---|---|
| monolith-java | system/monolith/java | `./gradlew test` | `./gradlew checkstyleMain` | `gh optivem component test setup` / `run` |
| monolith-dotnet | system/monolith/dotnet | `dotnet test` | `dotnet format MyCompany.MyShop.Monolith.sln --verify-no-changes` | `gh optivem component test setup` / `run` |
| monolith-typescript | system/monolith/typescript | `npm test` | `npm run lint` | `gh optivem component test setup` / `run` |
| multitier-java | backend-java **+ frontend-react** | be: `./gradlew test` (fe: none) | be: `./gradlew checkstyleMain`; fe: `npm run lint` | be: `... --component backend`; fe: `... --component frontend` |
| multitier-dotnet | backend-dotnet **+ frontend-react** | be: `dotnet test` (fe: none) | be: `dotnet format MyCompany.MyShop.Backend.slnx --verify-no-changes`; fe: `npm run lint` | be: `... --component backend`; fe: `... --component frontend` |
| multitier-typescript | backend-typescript **+ frontend-react** | be: `npm test` (fe: none) | be: `npm run lint`; fe: `npm run lint` | be: `... --component backend`; fe: `... --component frontend` |

Per resolved **OQ-2(b)**, frontend-react lint + component run in **all three**
multitier `local` configs. Frontend-react has **no unit-test step** in its
commit-stage (build + lint + component only).

## ▶ Next executable step (resume here)

All inline edits are landed and `actionlint`-clean. The only remaining unit is a
**CI dry-run that runs system/component tests**, which needs explicit user
approval (memory rule). To do it: trigger a `level: local` meta-prerelease run on
current `HEAD` (e.g. `gh workflow run _meta-prerelease-pipeline.yml -f level=local`
or the relevant meta dispatch) and confirm (a) the new lint/unit/component steps
execute in each `local` job, and (b) a green `local` line matches a green
`commit-stage` line for the same config. If the component step fails on the bare
runner (no prior `system start`), fall back to OQ-1 option (b): tear down then run
component after `system start`.

## Steps

- [ ] **Step 6 (CI dry-run) — ⏳ Deferred: needs user approval to trigger CI.**
  Static validation is done (`actionlint` clean on `_prerelease-pipeline.yml` +
  all six `prerelease-pipeline-*.yml`). Remaining: trigger a `level: local` meta
  dry-run on `HEAD` and confirm (a) the new checks run in `local`, (b) green local
  lines up with green commit for the same config. **Ask before any system-test /
  component-test run** (memory rule); this runs the component harness on CI.

## Decisions

1. **Option A — superset, not restructure** (user, 2026-06-24). Keep `local` and
   `commit-stage` as separate jobs; `local` gains the commit-stage *test* gates so
   green-local ⟹ green-commit. Rejected: collapsing the stages, or dropping
   `local` as redundant with `acceptance-stage`.
2. **Scope = lint + unit + component/Pact** (user, 2026-06-24). The three layers
   that actually broke. Excludes Docker build/push and Sonar — those are
   artifact-producing/scanning steps, not test gates, and stay commit-stage-only.
3. **Mirror the commit-stage commands verbatim**, gated by the same
   architecture/language conditions the existing `local` compile steps already
   use — consistent with how `local` already inlines per-config compile.

## Resolved decisions

- **OQ-1 — Component-test placement = option (a): before `system start`** (user,
  2026-06-24). In the `local` job, run lint + unit + component **before** `gh
  optivem system start`, so the cheap checks fail fast and there is no port clash
  with the real+stub stack. Assumes the component harness stands up on a bare
  runner with no prior `system start` — consistent with how the commit-stage runs
  component today (no `system start` there either). Step 6's probe still confirms
  this; if it fails, fall back to (b) tear-down-then-component. Rejected: (b) run
  after the sample with a `system stop` first (later failure, more steps); (c)
  keep component commit-stage-only (gives up a gate that actually broke).
- **OQ-2 — Frontend redundancy = option (b): run in all three multitier configs**
  (user, 2026-06-24). Mirror frontend-react's `npm run lint` + `--component
  frontend` in **every** multitier `local` config (java, dotnet, typescript), not
  just one. This is a true superset — consistent with the existing frontend-compile
  duplication across the three multitier configs — and keeps each config's `local`
  self-contained (a green `multitier-dotnet` local independently proves the
  frontend it ships). Accepts running frontend lint + component 3× vs 1× in commit
  as the cost of that guarantee. Rejected: (a) pin to `multitier-java` only (cheaper
  but leaves `multitier-dotnet`/`-typescript` local blind to frontend regressions).
- **OQ-3 — Wiring = option (a): inline now, composite action as follow-up** (user,
  2026-06-24). Ship the lint + unit + component checks **inline** in
  `_prerelease-pipeline.yml` to close the green-local ⟹ green-commit gap
  immediately, consistent with how `local` already inlines per-config compile, and
  staying entirely in `shop`. Mitigate interim drift with a source-pointer comment
  on each inlined block naming its origin `*-commit-stage.yml`. File a **follow-up
  plan** to extract compile + lint + unit + component into a reusable composite
  action in `optivem/actions` that both `local` and each `*-commit-stage.yml` call
  (zero drift, single source of truth, also absorbing the existing compile-step
  duplication) — filed as
  [20260624-1057-composite-action-for-stage-checks.md](20260624-1057-composite-action-for-stage-checks.md).
  Rejected for now: (b) build the composite action first — correct
  long-term, but a larger cross-repo change that would block this fix.
- **OQ-4 — Legacy sample interaction = confirmed: no legacy variant** (user,
  2026-06-24). The new lint + unit + component checks are SHA/source-level — they
  test the current checkout and have no latest-vs-legacy axis the way the
  system-test sample does (where legacy means a previously-published config
  version). So they run **once** per `local` config, independent of the existing
  `skip-acceptance-legacy` system-test gate, which stays unchanged.

## Risks

- **Component harness on a bare runner** — if `gh optivem component test setup`
  implicitly assumes a started system, OQ-1 option (a) breaks; fall back to (b).
- **Longer `local` wall-clock** — `local` gains unit + lint + component on top of
  the system-test sample. Still cheaper than waiting for the full commit-stage
  fan-out to surface the same failure.
- **Drift if shipped inline (OQ-3)** — without the composite-action follow-up,
  `local` and the commit-stage commands can diverge again. Mitigate with a comment
  pointing each inlined block at its source `*-commit-stage.yml`.
- **Docker/Testcontainers locally** — irrelevant here (this runs on CI runners),
  but note the standing constraint that some Testcontainers paths 400 on the dev
  machine, so local reproduction of the component step may not be possible.
