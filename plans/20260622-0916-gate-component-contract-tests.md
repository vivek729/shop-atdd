# 2026-06-22 09:16 UTC — Promote component + contract tests to gating commit-stage steps

## TL;DR

**Why:** Today the real component + consumer-Pact contract suites run in a
**separate, non-gating `component-contract-tests` job** (parallel to `run`), so a
failing component or contract test does **not** block the Docker image
build/push. User decision (2026-06-22): these suites **should gate** — a broken
component/contract test must stop the commit stage and the image from shipping.

**End result:** In the two workflows that have real component/contract tests
(`multitier-backend-java`, `multitier-frontend-react`), the suites run **inside
the gating `run` job** in their canonical pipeline slots, the redundant separate
job is removed, and the docs/diagram/README that currently say "opt-in, does not
gate" are updated to match.

## ⚠️ Supersedes / conflicts with the in-flight reconcile plan

This reverses decisions in **`20260622-0846-commit-stage-diagram-yaml-reconcile.md`**
(currently *picked up by agent `ValentinaLaptop`*). Before executing, decide how
the two plans interact — do not run them against each other blindly:

- That plan **Step 4** fixes the component/contract stubs by **skipping** them
  (`if: false`, "pending"). For `backend-java` + `frontend-react` those slots are
  now **filled with real gating tests instead** — the skip treatment no longer
  applies to those two workflows (it still applies to monolith ×3 and the
  .NET/TS backends, which have no real tests).
- That plan **Step 2b / diagram** draws the component/contract layer as an
  **optional dashed, non-gating parallel branch** in `docs/pipeline/commit-stage.md`.
  Gating means redrawing those as **inline gating stages** (at least for the
  where-wired-up case).
- The README, the workflow comment blocks, and that plan's "Decisions" all
  document **"opt-in, does not gate."** All three need updating (see Step 4).

> **Note:** this plan may need to be refined again once the `...0846...` reconcile
> plan finishes — its final stub-skipping / diagram / grouping-comment changes
> could shift the baseline these steps build on. Re-check this plan against the
> committed state of the two multitier workflows before executing.

## Scope

**In scope — the only two workflows with real component/contract tests:**
- `.github/workflows/multitier-frontend-react-commit-stage.yml`
  (`npm run test:component`, `npm run test:pact`)
- `.github/workflows/multitier-backend-java-commit-stage.yml`
  (`./gradlew componentTest` — covers component + Pact in the `componentTest`
  source set)

**Out of scope — stubs stay pending (handled by the reconcile plan's `if: false`):**
- `monolith-{java,dotnet,typescript}-commit-stage.yml`
- `multitier-backend-{dotnet,typescript}-commit-stage.yml`
- These have no real component/contract tests to gate.

## ▶ Next executable step (resume here)

Resolve **Open Question 1** (Java step granularity — one `componentTest` step vs
two) and **Open Question 4** (how this interacts with the in-flight reconcile
plan — pause it, or let it own monolith/.NET/TS while this owns the two multitier
workflows). Then apply Step 1 (frontend-react) as the pilot and show the diff
before touching Java or the docs.

## Steps

- [ ] **Step 1 — frontend-react: make component + contract gating.** In the `run`
  job, replace the two TODO stub steps with the real commands (add
  `working-directory: system/multitier/frontend-react`):
  - `Run Component Tests` → `npm run test:component`
  - `Run Contract Tests` → `npm run test:pact`
  Then **delete** the separate `component-contract-tests` job and its comment
  block (it re-did checkout + setup-node + `npm ci` only to run the same two
  commands). Position is unchanged: after Compile, before Linter → Sonar →
  Docker, so a failure gates the build/push.
- [ ] **Step 2 — backend-java: make component + contract gating.** Move
  `./gradlew componentTest` into the `run` job and delete the separate
  `component-contract-tests` job + comment. **Decide granularity (OQ1):** either
  one step `Run Component + Contract Tests` running `componentTest`, or two steps
  via test filtering to keep the diagram's separate Component/Contract boxes.
  Note: this adds **Testcontainers-Postgres (Docker)** to the gating path — see
  Risks.
- [ ] **Step 3 — diagram.** Update `docs/pipeline/commit-stage.md`: the
  component/contract stages move from the dashed non-gating opt-in branch onto
  the **main gating line** (for the where-wired-up case). Reconcile wording with
  the 0846 plan's Step 2b so the two don't fight.
- [ ] **Step 4 — docs/comments reframe.** Update the "does not gate" language:
  - `system/multitier/frontend-react/README.md` — the "Optional: in-process
    component & contract tests" section (reframe per OQ2 outcome).
  - The removed workflow comment blocks (handled by Steps 1–2).
  - Add a cross-reference / superseding note to
    `20260622-0846-commit-stage-diagram-yaml-reconcile.md`.
- [ ] **Step 5 — verify.** Lint the two workflows; run `npm run test:component`
  + `npm run test:pact` locally for frontend, and `./gradlew componentTest` for
  Java, to confirm they pass as gating steps (ask before any system-test run —
  memory rule). Confirm Docker is available for the Java Testcontainers path.

## Decisions

1. **Gate the component + contract suites** (user, 2026-06-22) — overrides the
   prior "opt-in, non-gating" design for the two multitier workflows that have
   real tests.
2. **Scope to where real tests exist** — only `frontend-react` + `backend-java`.
   Monolith and .NET/TS backends keep pending/skipped stubs.
3. **Remove the separate `component-contract-tests` job** rather than keep it
   alongside — gating in `run` makes it redundant duplicate work.

## Open questions

- **Q1 — Java step granularity.** `componentTest` is one Gradle task covering
  both component and Pact tests. One gating step (`Run Component + Contract
  Tests`) or two (split by test filter to match the diagram's separate boxes)?
  *Recommend:* one step; rename to match, accept the diagram showing them merged
  for Java.
- **Q2 — Should the *local* default also run them?** CI would gate via
  `npm run test:component`/`test:pact` and `./gradlew componentTest`, but local
  `npm test` / `./gradlew test` still **exclude** them — so devs don't run
  locally what CI now blocks on. Keep that split (dormant locally, gating in CI),
  or fold them into the local default too so dev experience matches the gate?
  *Recommend:* decide explicitly — the split is surprising.
- **Q3 — Testcontainers cost in the gate (Java).** Moving `componentTest` into
  `run` adds Docker-based Postgres startup to **every** commit/PR, and a flaky
  Testcontainers start would now block image publish. Acceptable, or keep Java
  non-gating while only frontend gates? *Recommend:* accept, but flag the
  flake-blocks-publish risk.
- **Q4 — Interaction with the 0846 reconcile plan.** It's mid-execution. Options:
  (a) pause it, fold this in, resume; (b) let 0846 own monolith/.NET/TS stub
  skipping while this plan owns the two multitier workflows. *Recommend:* (b),
  with an explicit hand-off note in both plans.
- **Q5 — Provider-side Pact verification.** Gating the *consumer* pact generation
  (frontend) + component is one half. Is the **provider** verification (Java
  backend verifying the contract in `contracts/`) wired into CI and should it
  also gate? Out of scope here unless confirmed needed.

## Risks

- **Testcontainers in the gate** (Java) — see Q3; flaky DB startup blocks publish.
- **Lost parallelism** — the suites ran concurrently with build/push; now serial
  in `run`, slightly longer gate wall-clock.
- **Doc drift if Step 4 is skipped** — README + diagram + 0846 plan still claim
  "opt-in, does not gate," which would now be false.
