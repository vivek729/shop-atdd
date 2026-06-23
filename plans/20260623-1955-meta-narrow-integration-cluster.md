# 2026-06-23 19:55 UTC — Plan coordination meta-plan: narrow-integration cluster

**Scope arguments:** first = `20260623-1801`, last = `20260623-1944` (inclusive range).
**Plans analysed:** 4 in-scope, 1 referenced-only.

> Produced inline (the `plan-coordinator` agent type is not registered in this
> repo's session — only the `/coordinate-plans` skill is). The analysis follows the
> `plan-coordinator` output spec verbatim. This file is read-only-derived: it edits
> none of the four source plans.

## Per-plan status snapshot

| Plan | Status | Steps done / total | Touched files (primary) | Notes |
|---|---|---|---|---|
| `20260623-1801-narrow-integration-tests` | ✅ refined, decisions resolved; next = Step 2 | 0 / 6 | `system/multitier/backend-java/component-tests.yaml`, backend-java `build.gradle` (+`integrationTest` source set), `src/test/…/SimpleArithmeticTest`, `src/integrationTest/…/{OrderRepositoryIntegrationTest, BackendApplicationTests}`, frontend-react integration spec + `package.json` + frontend `component-tests.yaml`, `docs/pipeline/commit-stage.md` (Step 6) | Pilot = backend-java + frontend-react only (OQ1). Defers OQ4→1939, OQ5→1941, OQ6→1944. **Step 5 ("roll out to remaining components") is superseded by 1944 — see Consolidation #1.** |
| `20260623-1939-pact-mock-server-narrow-integration` | ⚠️ decision plan, not refined; no mechanical edits | 0 / 5 | none (research/decision) — outcome updates 1801 OQ4 text | Decides the frontend stub mechanism (Pact mock server vs MSW). 1801 already has a provisional default (MSW); this either confirms or overrides it. |
| `20260623-1941-provider-pact-verification` | ⚠️ audit-first, not refined; has open questions | 0 / 4 | (per-audit) `.../component-tests.yaml` contract suites + provider-verification tests in backend-dotnet/backend-typescript/monolith ×3, GitHub Actions workflow (CI ordering), `docs/pipeline/commit-stage.md` (Step 4) | Concerns the **contract** suite / provider side — explicitly scoped OUT of 1801 (OQ5). Independent of narrow-integration work. |
| `20260623-1944-narrow-integration-rollout` | ⚠️ refined; **hard-gated on 1801** | 0 / 5 | `.../component-tests.yaml` + new integration tests in backend-dotnet, backend-typescript, monolith-java, monolith-dotnet, monolith-typescript | "The pilot plan 1801 must be fully executed first" (stated verbatim). Owns the 5-component rollout. |
| `20260623-1154-component-test-suite-config` | ✅ closed (referenced-only) | — | — | **Referenced-only, not in coordination scope.** Source of the symmetric-filter rule 1801 follows; already landed/closed. |

## Dependency graph

```
1939 ──(decides stub → feeds 1801 Step 3)──►  1801 ──(HARD prerequisite)──►  1944
                                               │
                                               └─ Step 6 docs ─┐
                                                               ├─ soft file collision on
1941  (independent — contract/provider side) ─ Step 4 docs ────┘   docs/pipeline/commit-stage.md
```

- **1939 → 1801**: soft/decision edge. Gates only 1801 **Step 3** (frontend pilot), not Step 2 (Java pilot). 1801 has a provisional MSW default, so this is "settle before the frontend step" rather than "blocks the whole plan."
- **1801 → 1944**: hard edge. 1944 cannot start until the 1801 pilot pattern is established.
- **1941**: no dependency edges to the cluster. Only a soft file collision (shared doc) with 1801 — see Conflict #1.

## Conflicts

### 1. `docs/pipeline/commit-stage.md` — soft conflict
- `20260623-1801` Step 6 documents the **narrow-integration** pyramid level.
- `20260623-1941` Step 4 documents the **consumer → contracts/ → provider verification** flow.
- **Why soft:** same file, different sections; mechanically non-overlapping. Safe to run sequentially with a rebase; unsafe to run in two *parallel* agent sessions (the second writes against stale content).
- **Resolution (recommended):** fold both doc edits into a **single final doc pass** (one session, after both feature bodies land). Cheap, removes the collision entirely. Alternative: serialise — whichever lands second rebases.

### 2. The 5 components' `component-tests.yaml` — apparent double-ownership (resolves to none)
- `20260623-1801` Step 5 nominally rolls out to backend-dotnet/backend-typescript/monolith ×3.
- `20260623-1944` owns exactly that 5-component rollout.
- **Why it's not a real conflict:** 1801's own OQ1 decision scopes the pilot to backend-java + frontend-react and assigns the rollout to 1944. Step 5 is vestigial. Resolved in Consolidation #1.

## Consolidation findings (decided)

### 1. `20260623-1801` Step 5 ⇄ `20260623-1944` — re-scope (drop the duplicate)
- 1801 Step 5 and 1944 both claim the remaining-5-component rollout, but 1801 OQ1 already reassigned it to 1944.
- **Resolution (recommended): re-order / re-scope.** Execute 1801 as the java+react pilot only — Steps 1–4 and 6 — and treat **Step 5 as deferred to 1944**. No merge, no plan edit required; the executor simply skips Step 5. **Why:** removes double-ownership of the 5 components' `integration` suites and keeps the hard 1801→1944 gate clean.
- **Alternative considered:** merge 1801 + 1944 into one plan. Rejected — 1944 is a deliberately separate refined rollout plan (`[[feedback_new_plan_not_extend]]`), and the pilot/rollout split is intentional.

### 2. `20260623-1939` → `20260623-1801` — settle the decision first, no merge
- 1939 is a pure decision input feeding 1801's frontend stub choice (OQ4).
- **Resolution (recommended): keep separate; resolve 1939 before 1801 Step 3.** Run `/refine-plan plans/20260623-1939-…` to settle Pact-mock-server-vs-MSW, write the result back to 1801 OQ4, then execute 1801 Step 3 against the settled choice. **Why:** 1939 is cheap research with no code touch; settling it first avoids reworking the frontend pilot. 1801 Step 2 (Java) does not wait on it.

## Execution units (post-consolidation)

| Unit | Plans | Type | Touched files (primary) |
|---|---|---|---|
| U1 | `20260623-1939` | standalone (decision/refine) | none (updates 1801 OQ4 text only) |
| U2 | `20260623-1801` (Steps 1–4, 6; Step 5 dropped) | standalone (pilot) | backend-java `component-tests.yaml` + gradle + tests; frontend-react spec + `package.json` + `component-tests.yaml`; `docs/pipeline/commit-stage.md` |
| U3 | `20260623-1941` | standalone (audit + complete) | dotnet/ts/monolith contract suites + provider tests; CI workflow; `docs/pipeline/commit-stage.md` |
| U4 | `20260623-1944` | standalone (rollout) | dotnet/ts/monolith ×… `component-tests.yaml` + integration tests |

## Execution waves

### Wave 1 — start now

**Batch A (parallel-safe — 3 independent agent sessions, disjoint files):**
- **U1 — `20260623-1939`**: settle the stub mechanism (`/refine-plan`). Research-only, fastest; finish this before U2's frontend step begins.
- **U2(java) — `20260623-1801` Steps 1–2 (+4 java portion)**: backend-java pilot. Owns `backend-java/component-tests.yaml`, gradle `integrationTest` source set, java tests. Touches no file U1/U3 touch.
- **U3(audit) — `20260623-1941` Steps 1–2**: audit the other backends' provider verification + confirm CI ordering. Read-mostly; touches the *contract* suites / CI, disjoint from U2.

**Batch B (serial — after U1 lands):**
- **U2(frontend) — `20260623-1801` Step 3 (+4 frontend portion)**: frontend-react narrow-integration spec, built against the stub U1 chose. One session.

> Token-efficiency note: U1, U2(java), U3-audit genuinely parallelise — three fresh
> sessions, no shared files. Do **not** start U2(frontend) before U1's decision is
> written back, or you risk redoing the frontend spec against the wrong stub.

### Wave 2 — after the 1801 pilot fully lands

**Batch A (parallel-safe):**
- **U4 — `20260623-1944`**: roll the pilot pattern out to the 5 remaining components. Its own OQ (one PR per component vs one PR for all 5) decides whether this is 1 session or up to 5 parallel sessions on disjoint `component-tests.yaml` files.
- **U3(complete) — `20260623-1941` Steps 3**: add any missing provider-verification tests found in the Wave-1 audit. Disjoint component files from U4 (contract vs integration suites) — parallel-safe.

**Batch B (serial — single final doc pass, after both feature bodies land):**
- **`20260623-1801` Step 6 + `20260623-1941` Step 4** together in one session on `docs/pipeline/commit-stage.md` (resolves Conflict #1).

## Pre-execute checks (apply before any wave starts)

- `grep -l "PICKUP\|in-flight\|claimed by" plans/*.md plans/deferred/*.md`
- `git status` — confirm no uncommitted changes on backend-java / frontend-react before Wave 1.
- Confirm U1 (`1939`) has written its stub decision back into `1801` OQ4 before starting U2(frontend).
- Confirm the 1801 pilot is fully landed (Java + frontend suites green via `gh optivem component test run --suite integration`) before starting U4 (`1944`).

## Out of scope of this meta-plan

- Plan content correctness (use the plan's own `/refine-plan` cycle, or `process-audit`).
- Architecture/code alignment (use `architecture-sync`).
- Actual execution (use `/execute-plan` against each unit in wave order).
