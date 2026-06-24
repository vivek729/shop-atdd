# 2026-06-22 09:16 UTC — Gate the test pyramid in EVERY commit-stage `run` job via `gh optivem component test run --suite`

> 🤖 **Picked up by agent** — `Valentina_Desk` at `2026-06-24T11:52:15Z`

> **Redrafted 2026-06-24.** Original draft proposed moving raw `./gradlew componentTest`
> / `npm run test:*` into `run`, scoped to two workflows. Superseded twice: (1) the
> suite→command mapping now lives in each project's **`component-tests.yaml`** and
> `gh optivem component test run --suite <id>` is the canonical selector; (2) user expanded
> scope (2026-06-24) — **backend-java is the pilot; once confirmed, propagate to ALL 7
> commit-stage workflows.** The `20260622-0846-...reconcile` baseline is committed
> (`f7975dcf`, `8b708074`).

## TL;DR

**Why:** Today the in-process suites (unit / narrow-integration / component /
provider-verification) run in a **separate, parallel `component-tests` job** that does
**not** block the Docker image. `run` (which builds and pushes the image) only
`needs: check`, so a broken suite reds the workflow **but the image still ships**. User
decision (2026-06-22, reaffirmed + scope-expanded 2026-06-24): the pyramid **must gate the
image**, **uniformly across every commit stage**.

**End result:** In **all 7 commit-stage workflows**, the suites run **inside the gating
`run` job**, in test-pyramid order, **ahead of Build/Push**, each invoked as
`gh optivem component test run [--component <c>] --suite <id>`. The redundant separate
`component-tests` job is deleted everywhere. Suite definitions already exist in every
`component-tests.yaml` — no test code moves. **Rollout is pilot-first:** land backend-java,
get user sign-off, then propagate the identical pattern to the other 6.

**Not in conflict with the "opt-in" memory:** "opt-in / off the default build" means off a
*cloning student's* local `./gradlew build` / `npm test`. CI explicitly invoking the suites
to gate the image is compatible. The cost memory is about Pact **Broker/PactFlow infra** —
not Testcontainers/WireMock ($0, zero-standing-infra) in CI.
[[feedback_component_pact_layer_opt_in]] [[feedback_templates_propagate_cost_to_students]]

## The suites already exist (every `component-tests.yaml`)

| Workflow | `--component` | Suites (pyramid order) |
|---|---|---|
| `monolith-java` | _(none)_ | unit, integration, component, provider-verification |
| `monolith-dotnet` | _(none)_ | unit, integration, component, provider-verification |
| `monolith-typescript` | _(none)_ | unit, integration, component, provider-verification |
| `multitier-backend-java` _(pilot)_ | `backend` | unit, integration, component, provider-verification |
| `multitier-backend-dotnet` | `backend` | unit, integration, component, provider-verification |
| `multitier-backend-typescript` | `backend` | unit, integration, component, provider-verification |
| `multitier-frontend-react` | `frontend` | unit, integration, component _(consumer — no provider-verification)_ |

`requiresDocker` varies per suite/project (see each yaml) — the Docker daemon on GitHub
ubuntu runners covers Testcontainers/WireMock; no extra `buildx` needed for the test steps.

> ⚠️ **Stub reality:** several suites currently point at placeholder tests (e.g. backend-java
> `unit` → `SimpleArithmeticTest` "onePlusOne"). Gating them is still correct — it locks the
> pyramid in place so that as real tests replace stubs they are **already gated**. Confirm
> each suite is green in the existing `component-tests` job before deleting that job.

## Target `run` pipeline (applies to all 7)

```
Checkout → Should-Publish? → Compile
  → Unit → Narrow Integration → Component → Provider Verification   ← NEW gating block
  → Linter → Sonar → Build image → Push image
```

(Frontend: same, minus Provider Verification.)

## ▶ Next executable step (resume here)

**Phase 1 pilot is DONE and green (`5d8db666`, run `28096815510`).** Next is **Step 2 — the
propagation wave** to the other 6 commit-stage workflows, **but it needs the user's explicit
go** and a coordination decision first: a sibling plan
[[20260624-1203-compile-component-and-test-sources-via-gh-optivem]] proposes routing
**compilation** through the CLI too, touching the **same 7 workflows' compile/test region**. To
avoid editing each workflow twice, **settle that plan's OQ1/OQ4 before running this wave** — if
its Option B is chosen, land the CLI compile verb first, then this wave applies the gating steps
*and* the CLI compile call together. Otherwise, run Step 2 as written (gating only).

## Steps

### Phase 1 — Pilot ✅ DONE

Step 1 (backend-java) landed in `5d8db666`: the four suites (unit/integration/component/
provider-verification) now run via `gh optivem component test run --suite` inside the gating
`run` job ahead of Build/Push, the parallel `component-tests` job is deleted, and OQ1 (unit via
CLI), OQ2 (Linter/Sonar after the block) + OQ4 (one setup) are resolved. Confirmed green:
workflow_dispatch run `28096815510` passed end-to-end (pyramid + Build and Push). Awaiting the
user's explicit go for the Phase 2 wave.

### Phase 2 — Propagation wave (only after sign-off)

- [ ] **Step 2 — propagate the identical pattern to the other 6 commit stages.** For each,
  insert the CLI install + `component test setup` (+ `--component backend|frontend` where the
  multitier ones use it; monolith uses none) and the per-project `--suite` steps in pyramid
  order ahead of Build/Push, then delete the separate `component-tests` job + its `summary`
  need. One workflow per unit of work:
  - [ ] `multitier-frontend-react` — suites: unit, integration, component (no provider-verification)
  - [ ] `multitier-backend-dotnet` — unit, integration, component, provider-verification
  - [ ] `multitier-backend-typescript` — unit, integration, component, provider-verification
  - [ ] `monolith-java` — unit, integration, component, provider-verification
  - [ ] `monolith-dotnet` — unit, integration, component, provider-verification
  - [ ] `monolith-typescript` — unit, integration, component, provider-verification
  Each replaces the 0846 reconcile's `if: false` stub steps for that workflow.
  *Note:* frontend `run` runs no tests today → this gates its unit/integration/component for
  the first time.

### Phase 3 — Docs & diagram

- [ ] **Step 3 — diagram.** `docs/pipeline/commit-stage.md`: move the component/contract
  layer from the dashed non-gating opt-in branch onto the **main gating line**
  (Unit → Integration → Component → Provider Verification → Build), now the rule for *all*
  workflows. Reconcile with the 0846 plan's Step 2b.
- [ ] **Step 4 — docs/comments reframe.** Update "does not gate" language:
  `system/multitier/frontend-react/README.md`; any removed-job comment blocks; confirm each
  `component-tests.yaml` opt-in framing still reads correctly (suites stay off the *local*
  default build; CI gates them — say so).
- [ ] **Step 5 — verify all.** `actionlint` every changed workflow. Cannot verify Docker
  suites locally ([[project_local_testcontainers_blocked]]); rely on CI. Ask before any
  local system-test/stack run ([[feedback_ask_before_local_system_tests]]). Consider
  `./compile-all.sh` is irrelevant here (YAML only) — verification is actionlint + a green CI
  run per workflow.

## Decisions

1. **Gate the test pyramid in `run`** (user, 2026-06-22 / reaffirmed 2026-06-24) — failing
   suite blocks the image.
2. **Uniform across ALL 7 commit stages** (user, 2026-06-24) — not just the two multitier
   workflows. Pilot backend-java, then propagate.
3. **Select via `gh optivem component test run --suite <id>`**, not raw build-tool commands —
   `component-tests.yaml` is the single source of truth; consistent across languages.
4. **Delete the separate `component-tests` job everywhere** — gating in `run` makes it
   redundant.
5. **Provider verification is its own gating step** (`provider-verification` suite) — already
   split by package filter; frontend omits it (consumer side).
6. **Pilot-first rollout with a confirmation gate** — no wave until the backend-java pilot is
   signed off green.

## Open questions

- **OQ1 — route `unit` through the CLI?** Backends already run `./gradlew test` (Sonar reuses
  its jacoco). Keep the direct call for backends (preserve jacoco reuse) and route only the
  Docker suites via CLI; for frontend use `--suite unit` (no unit step there today).
  *Recommend:* per-language as stated — but pick one convention and apply it uniformly in the
  wave.
- **OQ2 — Linter/Sonar position.** *Recommend:* Compile → test block → Linter → Sonar → Build
  (pyramid contiguous, fails fast before slower Sonar).
- **OQ3 — Sonar coverage scope.** Keep unit-only for now; folding integration/component
  coverage into Sonar is a separate change.
- **OQ4 — `component test setup` once per job.** Confirm a single setup covers all suites in
  the job. *Recommend:* one setup step before the first suite.

## Risks

- **Testcontainers/WireMock in the gate** — flaky Docker startup now blocks image publish
  across every workflow. Use existing retry wrappers where the CLI exposes them; accept the
  trade-off.
- **Stub suites gating** — gating placeholder tests is intended, but a currently-red suite
  starts blocking immediately. Confirm green in the existing job before deleting it
  (per-workflow).
- **Lost parallelism** — suites ran concurrently with build/push; now serial in `run`.
- **Wave blast radius** — touches all 7 workflows. Pilot + confirmation gate contains it; do
  one workflow per commit so a regression is bisectable.
- **Doc drift if Steps 3–4 skipped** — README + diagram still claim "opt-in, does not gate."
