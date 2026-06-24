# 2026-06-24 06:53 UTC — Plan coordination meta-plan: narrow-integration cluster (v2, +frontend boundary)

## Target state

**North star (set by the author, 2026-06-24):** a single, **symmetric 4-layer test taxonomy** applied to the frontend *and* every backend, where **narrow-integration** and **component** differ by **scope** (single adapter vs. booted/rendered component), **not** by mocking technology. This reframes the whole cluster and supersedes the earlier 2-layer `vi.fn()` framing in `1957`.

| # | Layer (suite `id`) | **Frontend** | **Backend** (Java / .NET / TS) |
|---|---|---|---|
| 1 | **Unit** (`unit`) | **real in-memory domain logic** (mappers, validation, formatting); `1+1` only as a placeholder where a component has no pure logic | **real in-memory domain logic** (`Order` constructor validation; pricing/discount/tax calc in `OrderService`); `1+1` only as a placeholder |
| 2 | **Narrow integration** (`integration`) | one adapter (`orderService`) ↔ **Pact mock server**; **no** React render | **both kinds of seam, one test each:** `OrderRepository` ↔ **Testcontainers-Postgres** *and* `TaxGateway` / `ErpGateway` ↔ **WireMock-in-Testcontainers**; **no** app boot |
| 3 | **Component** (`component`) | full UI render ↔ **Pact mock server** | full service boot, hit the **REST API**; **Postgres** real (Testcontainers) + **ERP/Tax** mocked (WireMock-in-Testcontainers) |
| 4 | **Provider verification** (`provider-verification`) | **NONE** — frontend is consumer-only; its consumer-contract *emission* lives in suites 2 + 3 (the union), not a standalone suite | verify the backend satisfies the **frontend consumer's** `.pact` (`BackendPactVerificationTest`) |

**The discriminator (the rule the docs must state):** *does the test boot/render the real component?* Yes → **component**; a single adapter called directly → **narrow integration**. The mocking stack (Pact mock server / WireMock / Testcontainers) is **orthogonal** to this line — both middle layers share it.

**Suite naming (decided 2026-06-24):** layer 4's `id` is **`provider-verification`** — *not* `contract`. Today the `contract` suite is mislabeled "Consumer Contract (Pact)" on **both** sides, but on the backend it actually runs `BackendPactVerificationTest` (provider verification), so the name is backwards there. Renaming to `provider-verification` names it by what it does; the **frontend has no layer-4 suite** (provider verification = NONE; its consumer-contract emission lives in suites 2 + 3). **Blast radius:** this is a cross-cutting `id` rename — all 7 `component-tests.yaml`, the `suiteGroups.all` lists (backend → `[unit, integration, component, provider-verification]`; frontend → `[unit, integration, component]`), the suite-pinning CI workflows, and the docs. It is therefore **its own task** (candidate `/create-plan`), tracked here, not owned by any current sub-plan.

**Frontend contract mechanics** (verified against the installed `@pact-foundation/pact` v13 + `@pact-foundation/pact-core`):
- Interaction **structure** (URLs, JSON request/response shapes + matchers) lives in **one shared fixture as parameterized builders** under **`src/test/interactions/`** (test-only code; deliberately **not** named `contracts/` to avoid colliding with the `.pact` output folder); each suite supplies only **data points**.
- **Generated `.pact` location — already solved by `contracts/README.md`, do not re-litigate.** The pact is written to the repo's `contracts/` folder; in *this* template that's `shop/contracts/` (frontend `dir: '../../../contracts'`, backend `@PactFolder("../../../contracts")`). The **gh-optivem scaffolder flattens** modules to `frontend/`+`backend/` and **rewrites the deep `../../../contracts` → `../contracts`** per scaffolded repo. **Monorepo** → one `contracts/` at root (both tiers resolve `../contracts`). **Multi-repo** → each repo commits its **own** `contracts/` copy (broker-less duplication; copies may drift — accepted zero-infra cost). A **Pact Broker / PactFlow** is the cost-labelled **opt-in** for real multi-repo, **never the default**. So the `.pact` *location* needs no new decision here.
- **Union-model interaction with the above:** two suites now emit (`integration` + `component`), so both must point at the **same** `contracts/` dir, write with **merge**, and **run together** for the committed pact to be the true **union** (a partial run commits an incomplete contract). In multi-repo the frontend repo's regenerated copy *is* that union; the backend repo verifies its committed copy.
- **Both** the narrow-integration and component suites **emit** interactions into the single `frontend→backend` contract via `PactV3.executeTest` (component renders the UI; narrow drives `orderService` directly). Each test first **registers the request→response interaction** (from the shared fixture builder) with the mock server, then exercises the adapter/UI — the mock server replies with the configured response. The contract is the **union** of both suites' interactions. Because both build from the shared fixture, identical interactions **merge idempotently**; **narrow integration MAY add interactions the UI never exercises** (e.g. cancel/deliver) — these simply append.
- **Operational note:** the emitting suites must run **together** (merge write-mode) when (re)generating the contract, or a partial run can drop interactions. ("Exactly one emitter" is **not** a Pact rule — `coupon.pact` + `order.pact` already both emit today; the real constraint is *one definition per `description`+state*, which the shared fixture guarantees.)
- **Stub-only mode** (mock server with **no** `.pact` written — low-level `pact-core` `createMockServer`/`cleanupMockServer`, never `writePactFile`) stays **available as an opt-out** for a narrow test that should deliberately not touch the contract. It is **not** the default.
- **No** separate consumer-contract suite (emission is distributed across narrow + component, as `coupon`/`order` are today) and **no Docker** on the frontend (the Pact mock server is an in-process FFI server).

**Backend mechanics:**
- Narrow-integration + component **require Docker** (Testcontainers-Postgres and WireMock-in-Testcontainers are real containers) → both are **opt-in / off the default `$0` path**, per the repo's opt-in-component-layer convention. Local Testcontainers is currently blocked here (Docker Engine 29) → **CI-verified**.
- **ERP + Tax are real outbound HTTP systems** (`ErpGateway`, `TaxGateway` in all three backends; e.g. `TaxGateway` does `GET ${tax.url}/api/countries/{country}`) → WireMock has genuine targets; this layer is **present-tense**, not aspirational.
- Provider verification already exists (`BackendPactVerificationTest`); `1941` extends it to the other backends.

**What the developer/student observes when done:** every component is selected the same way — `gh optivem component test run --suite <id|alias>`. **Backends** expose four suites (`unit` / `integration` / `component` / `provider-verification`); the **frontend** exposes three (`unit` / `integration` / `component` — no `provider-verification`). The frontend's `integration` + `component` run **Docker-free** against a Pact mock server and jointly produce one `frontend-backend` contract (the union); the backends' `integration` + `component` run against Testcontainers-Postgres + WireMock and are **opt-in/Docker-gated**; the backend `provider-verification` suite verifies the frontend's contract. `--suite all` runs each component's full pyramid (`integration` is already in the `all` group). One canonical doc states the symmetric 4-layer rule and the boot/render discriminator.

> **CLI-consistency note (VJ6):** the `--suite <id|alias>` mechanism is already shared between `component-tests.yaml` and `system-test/*/tests.yaml` (both use `suites:` + `suiteGroups:`). `all` is a **component-tests** alias; **system-tests have no `all`** (they use the `acceptance`-family aliases). Whether a bare `gh optivem component test run` (no `--suite`) defaults to "all" is a Go-side CLI default, not visible in these YAMLs — `--suite all` is the explicit, self-documenting form. Adding an `all` alias to system-tests for symmetry is possible but **out of scope** for this cluster.

**Explicitly unchanged:** the default `$0` / zero-infra build path (Docker layers stay opt-in); the pilot→rollout split (`1801`→`1944`); provider verification staying backend-only; the repo-owned `shop/contracts/` `.pact` folder location. **Changed (not unchanged):** the frontend's current standalone `contract` suite (`npm run test:pact`) is **folded** — its render-the-UI-and-emit pact files become part of the `component` suite, and `narrow` adds emission; there is no standalone frontend layer-4 suite afterward.

**Coordination consequence — sub-plan decisions that must flip (each via its *own* `/refine-plan`; NOT done in this meta-plan):**
- **`1801` OQ4** — flip *"MSW/`vi.fn()`, not the Pact mock server"* → **Pact mock server** (narrow emits via `PactV3` + shared fixture; union with component).
- **`1957`** — **rewrite**, not extend: drop the 2-layer `vi.fn()` "over the wire?" rule; document the symmetric 4-layer rule, the boot/render discriminator, the shared-fixture + both-emit-union design, and the stub-only opt-out.
- **`1939`** — stub-only existence is now **verified** (above), so it **collapses** from a binary "Pact-vs-MSW" decision to documenting the **shared-fixture + both-emit union** pattern and the stub-only opt-out.
- **NEW — suite rename `contract` → `provider-verification`** (and drop the standalone frontend layer-4 suite): cross-cutting across all 7 `component-tests.yaml`, `suiteGroups.all`, suite-pinning CI workflows, and docs. **Own task** (candidate `/create-plan`); not owned by `1801`/`1939`/`1941`/`1944`/`1957`.

---

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
- **1939 → 1957** and **1801(Step 3) → 1957**: soft/sequencing edges. 1957's boundary doc must describe the **symmetric 4-layer** picture from the Target state (unit / narrow-integration / component / contract), **not** the old 2-layer `vi.fn()` rule. Writing it before the integration layer exists produces a doc that must be revised. See Consolidation #3. These are *sequencing* recommendations, not hard gates — the plans don't cross-reference each other.
  > **Reframed by Target state (2026-06-24):** the earlier "3-layer (component `vi.fn()` / narrow stub / Pact contract)" picture is superseded. Both the frontend narrow-integration **and** component suites use the **Pact mock server** and **both emit** into one contract (the union); they differ by boot/render scope, not by stub mechanism.

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

### 3. Frontend test-layer documentation — coordination conflict (reframed by Target state)
- `20260623-1957` documents a **2-layer** frontend boundary with a binary rule: *"does a request go over the wire?"* — No → component file (`vi.fn()` stub, no server); Yes → Pact file (real mock server). **This premise is now wrong** per the Target state: the frontend component suite uses the **Pact mock server**, not a `vi.fn()` stub, and a third (narrow-integration) layer also uses it. The "over the wire?" binary cannot tell narrow from component because **both** go over the wire.
- **Resolution (set by Target state):** `1957` is a **rewrite, not an extension** — it must document the **symmetric 4-layer** rule (unit / narrow / component / contract) and the **boot/render discriminator** (single adapter = narrow; booted/rendered = component; both use the Pact mock server). It is sequenced **after** the 1801 frontend pilot lands so it is authored once, correctly. Minor doc-home overlap stands: 1957 → new `docs/atdd/` page cross-links to the `docs/pipeline/commit-stage.md` pyramid note rather than restating it.
- **Why coordination (not hard):** still no shared *file* edit (1957 touches the existing test-file headers + a new `docs/atdd/` page; 1801 Step 3 adds a *new* integration spec + `package.json` + `component-tests.yaml`). The collision is on the *concept the doc describes*. The Target state resolves the concept; `1957`'s own `/refine-plan` pass must rewrite its body to match.

## Consolidation findings (decided)

### 1. `20260623-1801` Step 5 ⇄ `20260623-1944` — re-scope (drop the duplicate)
- 1801 Step 5 and 1944 both claim the remaining-5-component rollout, but 1801 OQ1 already reassigned it to 1944.
- **Resolution (recommended): re-order / re-scope.** Execute 1801 as the java+react pilot only — Steps 1–4 and 6 — and treat **Step 5 as deferred to 1944**. No merge, no plan edit required; the executor simply skips Step 5. **Why:** removes double-ownership of the 5 components' `integration` suites and keeps the hard 1801→1944 gate clean.
- **Alternative considered:** merge 1801 + 1944 into one plan. Rejected — 1944 is a deliberately separate refined rollout plan (`[[feedback_new_plan_not_extend]]`); the pilot/rollout split is intentional.

### 2. `20260623-1939` → `20260623-1801` — settle the decision first, no merge
- 1939 is a pure decision input feeding 1801's frontend stub choice (OQ4).
- **Resolution (recommended): keep separate; resolve 1939 before 1801 Step 3.** Run `/refine-plan plans/20260623-1939-…` to settle Pact-mock-server-vs-MSW, write the result back to 1801 OQ4, then execute 1801 Step 3 against the settled choice. **Why:** 1939 is cheap research with no code touch; settling it first avoids reworking the frontend pilot. 1801 Step 2 (Java) does not wait on it.

### 3. `20260623-1957` ⇄ `20260623-1939` + `20260623-1801` (frontend) — rewrite to the symmetric 4-layer model, write the doc once
- 1957 currently documents a **2-layer** rule; the Target state replaces it with the **symmetric 4-layer** taxonomy in which the frontend component suite uses the **Pact mock server** (not `vi.fn()`) and a narrow-integration layer joins it.
- **Resolution (set by Target state): re-order *and* re-aim.** Execute 1957 **after** the 1801 frontend pilot lands, and have its `/refine-plan` pass **rewrite** the body to: (a) the symmetric 4-layer rule, (b) the boot/render discriminator, (c) the **shared-fixture + both-emit-union** contract design, (d) the stub-only opt-out, then point the test-file headers at the canonical `docs/atdd/` page. **Why:** authoring the old 2-layer doc first yields a doc provably stale the day the integration suite ships; sequencing after costs nothing (documentation-only).
- **Alternative considered:** land 1957 now as a 2-layer `vi.fn()` doc and revise later. Rejected — wrong on arrival per the Target state, and risks the stale doc being copied/cited in a teaching repo.
- **Alternative considered:** merge 1957 into 1801 Step 6. Rejected — 1957 targets a `docs/atdd/` frontend page; 1801 Step 6 targets `docs/pipeline/commit-stage.md`. Different homes/audiences; cross-link instead of merge.

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
- **U1 — `20260623-1939`**: ~~settle the stub mechanism~~ — **direction already set by Target state (Pact mock server) and stub-only existence verified.** U1 collapses to documenting the **shared-fixture + both-emit-union** pattern + the stub-only opt-out (`/refine-plan`). Research-only, fastest; finish before U2's frontend step and before U5.
- **U2(java) — `20260623-1801` Steps 1–2 (+ Java portion of 4)**: backend-java pilot. Owns `backend-java/component-tests.yaml`, gradle `integrationTest` source set, Java tests. Disjoint from U1/U3.
- **U3(audit) — `20260623-1941` Steps 1–2**: audit the other backends' provider verification + confirm CI ordering. Read-mostly; touches the *contract* suites / CI — disjoint from U2.

**Batch B (serial — after U1 lands):**
- **U2(frontend) — `20260623-1801` Step 3 (+ frontend portion of 4)**: frontend-react narrow-integration spec, built against the **Pact mock server** via `PactV3` + the shared interaction fixture, emitting into the **union** contract (per Target state; requires flipping 1801 OQ4 first). One session.

> U1, U2(java), U3-audit genuinely parallelise — three fresh sessions, no shared files.
> Do **not** start U2(frontend) before U1's decision is written back to 1801 OQ4, or you
> risk redoing the frontend spec against the wrong stub.

### Wave 2 — after the 1801 pilot fully lands

**Batch A (parallel-safe — 3 independent agent sessions, disjoint files):**
- **U4 — `20260623-1944`**: roll the pilot pattern out to the 5 remaining components. Its own OQ (one PR per component vs one PR for all 5) decides whether this is 1 session or up to 5 parallel sessions on disjoint `component-tests.yaml` files.
- **U3(complete) — `20260623-1941` Step 3**: add any missing provider-verification tests found in the Wave-1 audit. Touches *contract* suites — disjoint from U4's *integration* suites.
- **U5 — `20260623-1957`**: **rewrite** the frontend test-layer boundary doc as the **symmetric 4-layer** rule (unit / narrow / component / contract) with the **boot/render discriminator** and the shared-fixture + both-emit-union design (the narrow-integration layer landed in Wave 1). Touches the existing frontend test-file headers + a new `docs/atdd/` page — disjoint from U4 and U3.

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
- **Multi-repo contract transport** (how the union `.pact` reaches the provider once repos are split; $0 mechanisms + free Dockerized OSS Pact Broker) — spun out to its own follow-up: **`plans/20260624-0814-multirepo-contract-transport-dockerized-broker.md`**.
- The `contract` → `provider-verification` suite rename (cross-cutting; its own `/create-plan` per the Target state).
