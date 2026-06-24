# 2026-06-23 19:57:00 UTC — Frontend test-layer boundary: the symmetric 4-layer taxonomy

## TL;DR

**Why:** The division of labour between the frontend test suites is implicit — it lives only in file-header comments — and the earlier framing of it was *wrong*. It described a **2-layer** boundary with a binary rule "does a request go over the wire?" (No → component file with a `vi.fn()` fetch stub and no server; Yes → Pact file with a real mock server). The locked Target state in `[[20260624-0653-meta-narrow-integration-cluster]]` replaces that: the frontend **component** suite uses the **Pact mock server** (not a `vi.fn()` stub), and a **third** layer (narrow integration) also uses it. The "over the wire?" binary cannot distinguish narrow from component, because **both** go over the wire. Without one canonical statement of the real model, the design is easy to misread and the suites risk accidental duplication as they grow.
**End result:** One canonical page under `docs/atdd/` states the **symmetric 4-layer test taxonomy** (unit / narrow integration / component / provider-verification) that applies to the frontend *and* every backend, names the **boot/render discriminator** as the rule for "which suite does a new test go in?", and documents the frontend's three-suite instantiation (the frontend has no provider-verification suite — it is consumer-only). The test-file header comments shrink to one-line pointers at that page. This is documentation-only; the frontend code change it describes is owned by `[[20260623-1801-narrow-integration-tests]]` Step 3, so this plan is executed only *after* that lands.

## Outcomes

What we get out of this — the goals and deliverables:

- A canonical page under `docs/atdd/` that **is** the taxonomy doc for the whole cluster: it states the **symmetric 4-layer** rule (unit / narrow integration / component / provider-verification) that applies to the frontend and every backend.
- The **discriminator** stated plainly: *does the test boot/render the real component?* Yes → **component**; a single adapter called directly → **narrow integration**. The mocking stack (Pact mock server / WireMock / Testcontainers) is **orthogonal** — both middle layers share it, so it cannot be the discriminator.
- The frontend's **three-suite** instantiation detailed: `unit` (real in-memory domain logic — mappers, validation, formatting; `1+1` only as a placeholder where a component has no pure logic), `integration` (one adapter, `orderService`, ↔ Pact mock server, **no** React render), `component` (full UI render ↔ Pact mock server). The frontend has **no** `provider-verification` suite — it is **consumer-only**.
- The backend's **four-suite** instantiation **referenced, not restated** (cross-link to the Target state table): backend narrow integration = `OrderRepository` ↔ Testcontainers-Postgres *and* `TaxGateway`/`ErpGateway` ↔ WireMock-in-Testcontainers; backend component = full service boot against the REST API; backend provider-verification = verify the backend satisfies the frontend consumer's `.pact`.
- The **shared-fixture + both-emit-union** design documented: interaction *structure* (URLs, JSON request/response shapes + matchers) lives in **one** shared fixture as parameterized builders under `src/test/interactions/` (test-only; deliberately **not** named `contracts/`, to avoid colliding with the `.pact` output folder); each suite supplies only **data points**. **Both** the `integration` and `component` suites **emit** interactions into the single `frontend→backend` contract via `PactV3.executeTest` (component renders the UI; narrow drives `orderService` directly). The committed `.pact` is the **union** of both suites: they point at the same `contracts/` dir, write with **merge**, and must **run together** when regenerating (a partial run commits an incomplete contract). Identical interactions **merge idempotently**; narrow MAY add interactions the UI never exercises (e.g. cancel/deliver), which simply append.
- The **stub-only opt-out** documented: a mock server with **no** `.pact` written (low-level `pact-core` `createMockServer`/`cleanupMockServer`, never `writePactFile`) stays available for a narrow test that should deliberately not touch the contract. It is **not** the default.
- A **per-state table** stating which states live in which suite (validation short-circuit / pure formatting → unit; single-adapter request/response flows incl. cancel/deliver → integration; full render happy-path + contracted-error flows → component).
- An explicit **no-duplication** point: the folded `component` suite covers what a render proves; `integration` adds adapter-only interactions; the shared fixture guarantees one definition per `description`+state, so nothing is doubled. **Docker-free** is stated for both frontend middle suites (the Pact mock server is an in-process FFI server).
- The previously-standalone frontend `contract` suite (`npm run test:pact`) noted as **folded**: its render-the-UI-and-emit-pact behaviour becomes part of the `component` suite, and `narrow` adds emission. There is **no** standalone frontend layer-4 suite afterward.
- Test-file header comments reduced to **one-line pointers** at the canonical page.

## ▶ Next executable step (resume here)

This is a **documentation-only** plan, and it is **sequenced after** `[[20260623-1801-narrow-integration-tests]]` **Step 3** (the frontend narrow-integration suite + the component suite switching to the Pact mock server). It must not be executed before that code lands, or the doc would describe a state that does not yet exist.
Once 1801 Step 3 has landed: write the canonical page under `docs/atdd/` per the **symmetric 4-layer** model — the boot/render discriminator, the frontend's three-suite instantiation, the backend's four-suite instantiation (referenced via the Target state), the shared-fixture + both-emit-union design, the stub-only opt-out, the per-state table, and the no-duplication point — then reduce the three test-file header comments to one-line pointers at it. No code/test behaviour changes here.

## Steps

All steps done (Wave 2, 2026-06-24). Canonical page written at `docs/atdd/test-taxonomy.md`; three frontend test-file header comments reduced to one-line pointers. Note: the frontend `component` suite still uses `vi.fn()` stubs (the Pact mock server migration is pending); the doc describes the Target state and notes the rename is tracked separately.

## Decisions (resolved 2026-06-24)

These supersede the prior `## Open questions`. Each was set against the Target state in `[[20260624-0653-meta-narrow-integration-cluster]]`.

- **Where does the doc live?** → **(b): a canonical page under `docs/atdd/`** that the test-file header comments point at. Matches the repo convention of docs-as-markdown-under-`docs/` with a single source of truth, and stops the explanation living only in comments that drift. Header comments shrink to a one-line pointer. (Kept from the prior recommendation.)
- **Frontend-only or symmetric?** → **Symmetric — and this page IS the canonical taxonomy doc.** Upgraded per the Target state: the page **states** the symmetric 4-layer rule + the boot/render discriminator as the canonical frame, **details** the frontend's three-suite instantiation, and **references** (does not fully restate) the backend's four-suite instantiation. The old "frontend-first, generalise later" framing is replaced.
- **Docs-only or refactor?** → **Docs-only here.** The actual frontend **code** change — the `component` suite switching to the Pact mock server and the new narrow-integration suite — is owned by `[[20260623-1801-narrow-integration-tests]]` **Step 3**, **not** by this plan. Consequently this plan is **executed only after** 1801 Step 3 lands, so the doc describes reality rather than an intended future.
- **Audience?** → **Cloning students (primary), maintainer-friendly (secondary).** Tone = teaching-the-pattern, explaining *why* the split exists. Must stay **$0 / zero-infra-friendly** per repo conventions — no implication that Pact/broker infra is required on the default path (the frontend middle suites are Docker-free; broker infra is a cost-labelled opt-in, never the default). (Kept from the prior recommendation.)

## Cross-references

- `[[20260624-0653-meta-narrow-integration-cluster]]` — the **Target state**: the authoritative symmetric 4-layer taxonomy table, the boot/render discriminator, and the frontend contract mechanics this plan documents.
- `[[20260623-1801-narrow-integration-tests]]` — the **code change** (Step 3) that introduces the frontend narrow-integration suite and switches the component suite to the Pact mock server; this plan is sequenced **after** it lands.
- `[[20260623-1939-pact-mock-server-narrow-integration]]` — the **stub decision**: the shared-fixture + both-emit-union pattern and the **stub-only opt-out** documented here.
