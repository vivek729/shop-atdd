# 2026-06-24 10:49 UTC — Make the `local` stage a superset of the commit stage

> 🤖 **Picked up by agent (refine)** — `Valentina_Desk` at `2026-06-24T10:51:53Z`

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

**End result:** the `local` job in `_prerelease-pipeline.yml` also runs that
config's **lint + unit + component/Pact** checks, mirroring the exact commands the
matching `*-commit-stage.yml` workflows use, gated by the same
`architecture`/`language` conditions the existing compile steps already carry.

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

Resolve **OQ-1** (component-test placement / port clash) and **OQ-2** (frontend
redundancy), then apply **Step 1** as the pilot on a single config
(`monolith-typescript` — simplest, no frontend, no Docker-format quirks) and show
the diff before touching the other five.

## Steps

- [ ] **Step 1 — pilot: monolith-typescript.** In the `local` job, after the
  existing `Compile System (monolith, typescript)` step and **before** `Install
  gh-optivem CLI extension` / `Start system`, add (each `if: inputs.architecture
  == 'monolith' && inputs.language == 'typescript'`, `working-directory:
  system/monolith/typescript`):
  - `Run Unit Tests (monolith, typescript)` → `npm test`
  - `Run Linter (monolith, typescript)` → `npm run lint`
  Then add the component step(s) per **OQ-1** outcome. Show the diff; confirm the
  pipeline YAML still validates.
- [ ] **Step 2 — remaining monolith configs (java, dotnet).** Same shape with the
  per-language commands from the mapping table. Java lint = `./gradlew
  checkstyleMain`; dotnet lint = `dotnet format <sln> --verify-no-changes`.
- [ ] **Step 3 — multitier backends (java, dotnet, typescript).** Mirror the
  backend commit-stage commands, `working-directory: system/multitier/backend-<lang>`,
  `if: inputs.architecture == 'multitier' && inputs.language == '<lang>'`.
  Component step uses `--component backend`.
- [ ] **Step 4 — frontend-react.** Add `npm run lint` + `--component frontend` in
  **every** multitier `local` config (java, dotnet, typescript) per resolved
  **OQ-2(b)**. `working-directory: system/multitier/frontend-react`. No unit step.
- [ ] **Step 5 — ordering & fail-fast.** Place lint + unit (cheap, no Docker)
  ahead of `gh optivem system start` so they fail fast. Component placement per
  **OQ-1**.
- [ ] **Step 6 — verify.** Validate all six `prerelease-pipeline-*.yml` still
  parse (and `_prerelease-pipeline.yml`). Trigger a `level: local` meta dry-run
  on current `HEAD` and confirm: (a) the checks now run in `local`, (b) green
  local lines up with green commit for the same config. **Ask before any
  system-test / component-test run** (memory rule).

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
  duplication). Rejected for now: (b) build the composite action first — correct
  long-term, but a larger cross-repo change that would block this fix.

## Open questions

- **OQ-4 — Legacy sample interaction.** `local` already runs a legacy system-test
  sample (`skip-acceptance-legacy` gate). The new checks are SHA/source-level
  (not config-version specific), so they run once regardless of latest/legacy — no
  legacy variant needed. Confirm.

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
