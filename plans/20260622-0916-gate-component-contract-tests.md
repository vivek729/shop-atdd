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

**Phases 1, 2 (code) and Phase 3 Steps 3–4 (docs) are DONE.** Only **Step 5 — verify all** remains,
and it is a **push gate, not a mechanical edit**: the wave commit (`52e47ac5`) plus the docs commit
are unpushed; pushing to `main` triggers all 7 commit-stage workflows. Docker suites cannot be
verified locally ([[project_local_testcontainers_blocked]]), so a **green CI run per workflow** is
the only verification — which needs the push.

Next move: **ask the user for the go to push** ([[feedback_ask_before_commit]]). Once pushed, watch
each of the 7 commit-stage runs go green (pyramid block + Build/Push). If a stub suite reds, fix or
revert per-workflow (bisectable — one workflow per commit). No further code edits expected unless CI
surfaces a failure.

## Steps

### Phase 1 — Pilot ✅ DONE

Step 1 (backend-java) landed in `5d8db666`: the four suites (unit/integration/component/
provider-verification) now run via `gh optivem component test run --suite` inside the gating
`run` job ahead of Build/Push, the parallel `component-tests` job is deleted, and OQ1 (unit via
CLI), OQ2 (Linter/Sonar after the block) + OQ4 (one setup) are resolved. Confirmed green:
workflow_dispatch run `28096815510` passed end-to-end (pyramid + Build and Push). Awaiting the
user's explicit go for the Phase 2 wave.

### Phase 2 — Propagation wave ✅ DONE (gating-only)

Step 2 landed: the gating test-pyramid block (`gh optivem component test run --suite …` in pyramid
order ahead of Build/Push, plus CLI install + `component test setup`) now runs inside the gating
`run` job of all 6 remaining commit-stage workflows, and each workflow's separate `component-tests`
job + its `summary` need were deleted. `--component backend` on the two backends, `--component
frontend` on the frontend (no provider-verification suite — consumer side), none on the three
monoliths; `GH_OPTIVEM_CONFIG` added to each `run` env. Frontend `run` ran no tests before → now
gates unit/integration/component for the first time. All 6 pass `actionlint`. Landed in a single
local commit `52e47ac5` (**not pushed** — pushing to `main` triggers these commit-stage workflows,
held pending the user's go). User chose the **gating-only** branch (2026-06-24) over waiting for plan
1203's CLI-compile verb, accepting a later second edit of these workflows' compile region.

### Phase 3 — Docs & diagram

Steps 3 (diagram) and 4 (docs reframe) are DONE: `docs/pipeline/commit-stage.md` now shows the
four suites on the main gating line inside `run` (ahead of Build/Push), its bullets + Diagram↔YAML
mapping table reflect the deleted `component-tests` job, and `system/multitier/frontend-react/README.md`
clarifies that "opt-in" is local-only while CI gates the suites. (Both `backend-java`/`frontend-react`
READMEs' local opt-in framing was confirmed already-correct.)

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
