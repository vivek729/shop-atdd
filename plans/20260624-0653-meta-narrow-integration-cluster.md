# 2026-06-24 06:53 UTC — Plan coordination meta-plan: narrow-integration cluster (v2, +frontend boundary)

**Scope arguments:** first = `20260623-1801`, last = (open — from `1801` onwards).
**Plans analysed:** 5 in-scope executable, 1 in-scope coordination artifact (the prior meta-plan), 1 referenced-only.

> **Supersedes `plans/20260623-1955-meta-narrow-integration-cluster.md`.** That earlier
> meta-plan coordinated the range `1801..1944`. This v2 widens the slice to include the
> new plan `20260623-1957-frontend-test-layer-boundary.md` and re-derives the waves with
> it folded in. The `1801 / 1939 / 1941 / 1944` analysis below is carried forward from
> `1955` (still valid); the **new** material is everything touching `1957` — see
> Conflict #3, Consolidation #3, Unit U5, and its placement in Wave 2.
>
> Read-only on all source plans. This file edits none of them.

## Per-plan status snapshot

| Plan | Status | Steps done / total | Touched files (primary) | Notes |
|---|---|---|---|---|
| `20260623-1801-narrow-integration-tests` | ✅ refined, decisions resolved; next = Step 2 | 0 / 6 | `system/multitier/backend-java/component-tests.yaml`, backend-java `build.gradle` (+`integrationTest` source set), `src/test/…/SimpleArithmeticTest`, `src/integrationTest/…/{OrderRepositoryIntegrationTest, BackendApplicationTests}`, frontend-react integration spec + `package.json` + frontend `component-tests.yaml`, `docs/pipeline/commit-stage.md` (Step 6) | Pilot = backend-java + frontend-react only (OQ1). Defers OQ4→1939, OQ5→1941, OQ6→1944. **Step 5 ("roll out to remaining components") is superseded by 1944 — see Consolidation #1.** |
| `20260623-1939-pact-mock-server-narrow-integration` | ⚠️ decision plan, not refined; no mechanical edits | 0 / 5 | none (research/decision) — outcome updates 1801 OQ4 text | Decides the frontend **integration-suite** stub (Pact mock server vs MSW/`vi.fn()`). 1801 has a provisional MSW default; this confirms or overrides it. Also conditions 1957's third-layer description — see Conflict #3. |
| `20260623-1941-provider-pact-verification` | ⚠️ audit-first, not refined; has open questions | 0 / 4 | (per-audit) `.../component-tests.yaml` contract suites + provider-verification tests in backend-dotnet/backend-typescript/monolith ×3, GitHub Actions workflow (CI ordering), `docs/pipeline/commit-stage.md` (Step 4) | Concerns the **contract** / provider side — explicitly scoped OUT of 1801 (OQ5). Independent of narrow-integration work. |
| `20260623-1944-narrow-integration-rollout` | ⚠️ refined; **hard-gated on 1801** | 0 / 5 | `.../component-tests.yaml` + new integration tests in backend-dotnet, backend-typescript, monolith-java, monolith-dotnet, monolith-typescript | "The pilot plan 1801 must be fully executed first" (verbatim). Owns the 5-component rollout. |
| `20260623-1957-frontend-test-layer-boundary` | ⚠️ refined; documentation-only; OQs carry recommendations | 0 / 5 | `system/multitier/frontend-react/src/test/{component/order.component.test.tsx, pact/coupon.pact.test.tsx, pact/order.pact.test.tsx}` (header comments → one-line pointers), **new** page under `docs/atdd/` | Documents the frontend test-layer boundary: component = `vi.fn()` stub (no server); Pact = real HTTP mock server. **Written as a 2-layer rule** — but 1801+1939 are adding a *third* frontend layer (narrow integration). See Conflict #3 / Consolidation #3. |
| `20260623-1955-meta-narrow-integration-cluster` | 🗂️ prior meta-plan (coordination artifact) | — | — | **In slice but not an execution unit.** Superseded by this file. Excluded from units/waves. |
| `20260623-1154-component-test-suite-config` | ✅ closed (referenced-only) | — | — | **Referenced-only, not in coordination scope.** Source of the symmetric-filter rule 1801 follows; already landed/closed. |

## Dependency graph

```
1939 ──(decides integration-suite stub → feeds 1801 Step 3)──►  1801 ──(HARD prereq)──►  1944
  │                                                              │
  │  (stub choice conditions                                     ├─ Step 6 docs ─┐
  │   the 3rd-layer wording)                                     │               ├─ soft collision on
  └───────────────────────────────────┐                         │               │  docs/pipeline/commit-stage.md
                                       ▼                         │               │
1941 (contract/provider side — independent) ─ Step 4 docs ───────┘───────────────┘
                                       │
1801 Step 3 (frontend integration layer lands) ──►  1957 (frontend boundary doc — write once, 3-layer)
1939 ──────────────────────────────────────────►  1957
```

- **1939 → 1801**: soft/decision edge. Gates 1801 **Step 3** (frontend pilot) only, not Step 2 (Java). 1801 has a provisional MSW default → "settle before the frontend step," not "blocks the whole plan."
- **1801 → 1944**: hard edge. 1944 cannot start until the 1801 pilot pattern is established.
- **1941**: no dependency edge to the cluster; only a soft doc collision with 1801 — see Conflict #1.
- **1939 → 1957** and **1801(Step 3) → 1957**: soft/sequencing edges (new). 1957's boundary doc should describe the final 3-layer frontend picture (component `vi.fn()` / narrow-integration stub / Pact contract). Writing it before the integration layer exists and before its stub is chosen produces a 2-layer doc that must be revised. See Consolidation #3. These are *sequencing* recommendations, not hard gates — the plans don't cross-reference each other (see Needs-decision is intentionally empty; the choice is resolved below).

## Conflicts

### 1. `docs/pipeline/commit-stage.md` — soft conflict
- `20260623-1801` Step 6 documents the **narrow-integration** pyramid level.
- `20260623-1941` Step 4 documents the **consumer → contracts/ → provider verification** flow.
- **Why soft:** same file, different sections; mechanically non-overlapping. Safe sequentially with a rebase; unsafe in two *parallel* agent sessions (the second writes against stale content).
- **Resolution (recommended):** fold both doc edits into a **single final doc pass** (one session, after both feature bodies land). Cheap, removes the collision. Alternative: serialise — whichever lands second rebases.

### 2. The 5 components' `component-tests.yaml` — apparent double-ownership (resolves to none)
- `20260623-1801` Step 5 nominally rolls out to backend-dotnet/backend-typescript/monolith ×3.
- `20260623-1944` owns exactly that 5-component rollout.
- **Why it's not a real conflict:** 1801's OQ1 decision scopes the pilot to backend-java + frontend-react and assigns the rollout to 1944. Step 5 is vestigial. Resolved in Consolidation #1.

### 3. Frontend test-layer documentation — coordination conflict (new)
- `20260623-1957` documents a **2-layer** frontend boundary with a binary rule: *"does a request go over the wire?"* — No → component file (`vi.fn()` stub, no server); Yes → Pact file (real mock server).
- `20260623-1801` Step 3 (with the stub chosen by `20260623-1939`) introduces a **third** frontend layer: the `integration` (narrow) suite. Depending on 1939's decision, that layer uses either a Pact mock server (real server → collides with 1957's "Yes → Pact" branch) or MSW/`vi.fn()` (no server → collides with 1957's "No → component" branch). **Either way, the binary rule as written in 1957 is incomplete the moment the integration suite exists.**
- **Why coordination (not hard):** no shared *file* edit (1957 touches the three existing test-file headers + a new `docs/atdd/` page; 1801 Step 3 adds a *new* integration spec + `package.json` + `component-tests.yaml`). The collision is on the *concept the doc describes*, not on bytes. Whichever lands first dictates the other's mechanical content: if 1957 lands first it must be revised once the layer arrives; if the layer lands first 1957 is written once, correctly.
- **Resolution (recommended):** sequence 1957 **after** 1939's stub decision and the 1801 frontend pilot (Step 3) so the boundary doc is authored once as a 3-layer rule. See Consolidation #3. There is also a minor doc-home overlap (1957 → new `docs/atdd/` page; 1801 Step 6 → `docs/pipeline/commit-stage.md`): the executor of 1957 should cross-link to the commit-stage pyramid note rather than restate it.

## Consolidation findings (decided)

### 1. `20260623-1801` Step 5 ⇄ `20260623-1944` — re-scope (drop the duplicate)
- 1801 Step 5 and 1944 both claim the remaining-5-component rollout, but 1801 OQ1 already reassigned it to 1944.
- **Resolution (recommended): re-order / re-scope.** Execute 1801 as the java+react pilot only — Steps 1–4 and 6 — and treat **Step 5 as deferred to 1944**. No merge, no plan edit required; the executor simply skips Step 5. **Why:** removes double-ownership of the 5 components' `integration` suites and keeps the hard 1801→1944 gate clean.
- **Alternative considered:** merge 1801 + 1944 into one plan. Rejected — 1944 is a deliberately separate refined rollout plan (`[[feedback_new_plan_not_extend]]`); the pilot/rollout split is intentional.

### 2. `20260623-1939` → `20260623-1801` — settle the decision first, no merge
- 1939 is a pure decision input feeding 1801's frontend stub choice (OQ4).
- **Resolution (recommended): keep separate; resolve 1939 before 1801 Step 3.** Run `/refine-plan plans/20260623-1939-…` to settle Pact-mock-server-vs-MSW, write the result back to 1801 OQ4, then execute 1801 Step 3 against the settled choice. **Why:** 1939 is cheap research with no code touch; settling it first avoids reworking the frontend pilot. 1801 Step 2 (Java) does not wait on it.

### 3. `20260623-1957` ⇄ `20260623-1939` + `20260623-1801` (frontend) — re-order, write the doc once
- 1957 documents the frontend test-layer boundary as a 2-layer rule; 1939 + 1801 Step 3 add a third (narrow-integration) frontend layer whose stub mechanism 1939 decides.
- **Resolution (recommended): re-order — execute 1957 after 1939's decision and the 1801 frontend pilot land.** No merge, no plan edit. The executor then writes the boundary doc as a 3-layer rule (component `vi.fn()` / narrow-integration stub / Pact contract) in one pass, and points the three test-file headers at it. **Why:** authoring 1957 first yields a doc that is provably stale on the day the integration suite ships — a guaranteed rewrite. Sequencing it after costs nothing (it's documentation-only, gated on nothing else) and removes the rework.
- **Alternative considered:** land 1957 now as a 2-layer doc (Wave 1, parallel) and revise it when the integration layer arrives. Rejected — it doubles the authoring work and risks the interim doc being copied/cited before revision in a teaching repo.
- **Alternative considered:** merge 1957 into 1801 Step 6. Rejected — 1957 targets a `docs/atdd/` frontend page; 1801 Step 6 targets `docs/pipeline/commit-stage.md`. Different homes, different audiences; cross-link instead of merge.

## Execution units (post-consolidation)

The wave plan operates on these units, not on raw plan files. `20260623-1955` (prior meta-plan) and `20260623-1154` (closed) are **not** units.

| Unit | Plans | Type | Touched files (primary) |
|---|---|---|---|
| U1 | `20260623-1939` | standalone (decision/refine) | none (updates 1801 OQ4 text only) |
| U2 | `20260623-1801` (Steps 1–4, 6; Step 5 dropped) | standalone (pilot) | backend-java `component-tests.yaml` + gradle `integrationTest` set + tests; frontend-react integration spec + `package.json` + `component-tests.yaml`; `docs/pipeline/commit-stage.md` |
| U3 | `20260623-1941` | standalone (audit + complete) | dotnet/ts/monolith contract suites + provider tests; CI workflow; `docs/pipeline/commit-stage.md` |
| U4 | `20260623-1944` | standalone (rollout) | dotnet/ts/monolith ×5 `component-tests.yaml` + integration tests |
| U5 | `20260623-1957` | standalone (docs) | `frontend-react/src/test/{component/order.component.test.tsx, pact/coupon.pact.test.tsx, pact/order.pact.test.tsx}` headers; new `docs/atdd/` frontend boundary page |

## Execution waves

### Wave 1 — start now

**Batch A (parallel-safe — 3 independent agent sessions, disjoint files):**
- **U1 — `20260623-1939`**: settle the integration-suite stub mechanism (`/refine-plan`). Research-only, fastest; finish before U2's frontend step and before U5.
- **U2(java) — `20260623-1801` Steps 1–2 (+ Java portion of 4)**: backend-java pilot. Owns `backend-java/component-tests.yaml`, gradle `integrationTest` source set, Java tests. Disjoint from U1/U3.
- **U3(audit) — `20260623-1941` Steps 1–2**: audit the other backends' provider verification + confirm CI ordering. Read-mostly; touches the *contract* suites / CI — disjoint from U2.

**Batch B (serial — after U1 lands):**
- **U2(frontend) — `20260623-1801` Step 3 (+ frontend portion of 4)**: frontend-react narrow-integration spec, built against the stub U1 chose. One session.

> U1, U2(java), U3-audit genuinely parallelise — three fresh sessions, no shared files.
> Do **not** start U2(frontend) before U1's decision is written back to 1801 OQ4, or you
> risk redoing the frontend spec against the wrong stub.

### Wave 2 — after the 1801 pilot fully lands

**Batch A (parallel-safe — 3 independent agent sessions, disjoint files):**
- **U4 — `20260623-1944`**: roll the pilot pattern out to the 5 remaining components. Its own OQ (one PR per component vs one PR for all 5) decides whether this is 1 session or up to 5 parallel sessions on disjoint `component-tests.yaml` files.
- **U3(complete) — `20260623-1941` Step 3**: add any missing provider-verification tests found in the Wave-1 audit. Touches *contract* suites — disjoint from U4's *integration* suites.
- **U5 — `20260623-1957`**: write the frontend test-layer boundary doc, now as a **3-layer** rule (the narrow-integration layer landed in Wave 1, its stub chosen by U1). Touches the three existing frontend test-file headers + a new `docs/atdd/` page — disjoint from U4 and U3.

**Batch B (serial — single final doc pass, after both feature bodies land):**
- **`20260623-1801` Step 6 + `20260623-1941` Step 4** together in one session on `docs/pipeline/commit-stage.md` (resolves Conflict #1). U5's `docs/atdd/` page cross-links here rather than duplicating the pyramid note.

## Pre-execute checks (apply before any wave starts)

- `grep -lE "PICKUP|in-flight|claimed by" plans/*.md plans/deferred/*.md`
- `git status` — confirm no uncommitted changes on backend-java / frontend-react before Wave 1.
- Confirm U1 (`1939`) has written its stub decision back into `1801` OQ4 before starting U2(frontend) **and** U5.
- Confirm the 1801 pilot is fully landed (Java + frontend `integration` suites green via `gh optivem component test run --suite integration`) before starting U4 (`1944`) and U5 (`1957`).

## Out of scope of this meta-plan

- Plan content correctness (use each plan's own `/refine-plan` cycle, or `process-audit`).
- Architecture/code alignment (use `architecture-sync`).
- Actual execution (use `/execute-plan` against each unit in wave order).
- The prior meta-plan `20260623-1955` is superseded by this file; no action needed on it beyond noting the supersession.
