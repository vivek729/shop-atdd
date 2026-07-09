# 2026-07-09 11:04:51 UTC — Backend DSL + Driver for component tests (SUT-side symmetry)

## TL;DR

**Why:** The backend component tests drive the *external systems* (ERP / Tax / Clock) through a fluent Driver+DSL pair under `support/`, but drive the *system under test* itself with inline `restTemplate` calls plus duplicated private `orderRequest(...)` / `placeAndFetch(...)` helpers in both the `latest/` and `legacy/` twins. The SUT interaction has no abstraction while its collaborators do — an inconsistency.
**End result:** A `BackendDriver` + `BackendDsl` pair lives alongside `ErpStub*` / `TaxStub*` / `ClockStub*` in `support/`, wrapping the `TestRestTemplate`. Both `latest/` and `legacy/` `PlaceOrderComponentTest` place and fetch orders through the fluent DSL; the duplicated private helpers are gone. The SUT is driven through the same four-layer abstraction as its collaborators.

## Outcomes

What we get out of this — the goals and deliverables:

- `BackendDriver` under `support/` wraps `TestRestTemplate` and owns the raw HTTP mechanics (`POST /api/orders`, `GET /api/orders/{orderNumber}`), mirroring how `ErpStubDriver` owns the WireMock mechanics.
- `BackendDsl` under `support/` exposes a fluent request builder (`placeOrder().withSku(...).withQuantity(...).withCountry(...).withCoupon(...)`) with terminal operations covering both happy-path (place → fetch parsed `ViewOrderDetailsResponse`) and rejection-path (place → assert HTTP status) scenarios.
- Both `latest/` and `legacy/` `PlaceOrderComponentTest` use `BackendDsl`; the duplicated private `orderRequest(...)` and `placeAndFetch(...)` helpers are deleted from both.
- The `latest/` vs `legacy/` pedagogical contrast is preserved — it stays purely about external-stub style (raw WireMock vs stub DSL); the SUT-side helper is shared identically by both, so it doesn't muddy that contrast.
- Behaviour-neutral: same HTTP calls, same assertions, all component tests still green (`./gradlew build` in `backend-java`).

**Scope:** Java backend only. TypeScript / .NET parity is explicitly out of scope for this plan (may be a later follow-up).

## ▶ Next executable step (resume here)

Create `system/multitier/backend-java/src/testSupport/java/com/mycompany/myshop/backend/support/BackendDriver.java` wrapping a `TestRestTemplate` with `placeOrder(PlaceOrderRequest)` → `ResponseEntity<PlaceOrderResponse>` and `viewOrder(String orderNumber)` → `ViewOrderDetailsResponse`, then `BackendDsl.java` with the fluent `placeOrder()` builder over it. Wiring is decided: expose `protected BackendDsl backend;` on `AbstractComponentTest`, initialized in a `@BeforeEach` from the autowired `restTemplate`. Gate: `./gradlew build` in `backend-java` green. Unblocks refactoring both `latest/` and `legacy/` `PlaceOrderComponentTest`.

## Steps

- [ ] Step 1: Wire `BackendDsl` into `AbstractComponentTest` — add `protected BackendDsl backend;` initialized in a `@BeforeEach` from the autowired `restTemplate` (decided approach (a); `restTemplate` is an instance field not available at field-init, unlike the static WireMock ports the stub DSLs use). Shared by every component-test subclass and the Pact verifier.
- [ ] Step 2: Create `BackendDriver` in `support/` — constructor takes `TestRestTemplate`; methods `placeOrder(PlaceOrderRequest) : ResponseEntity<PlaceOrderResponse>` and `viewOrder(String orderNumber) : ViewOrderDetailsResponse`. Keep it a thin wrapper over the two existing `restTemplate` calls (byte-identical behaviour).
- [ ] Step 3: Create `BackendDsl` in `support/` — fluent `placeOrder()` builder (`withSku`, `withQuantity`, `withCountry`, `withCoupon`) building a `PlaceOrderRequest`, with terminal ops covering both paths:
  - happy path: place, assert `201 CREATED`, then fetch and return `ViewOrderDetailsResponse` (replaces `placeAndFetch`);
  - rejection path: place and return the `ResponseEntity` / status so tests can assert `422 UNPROCESSABLE_ENTITY` (replaces the inline `restTemplate.postForEntity(..., String.class)`).
- [ ] Step 4: Refactor `latest/PlaceOrderComponentTest` to use `BackendDsl`; delete its private `orderRequest` / `placeAndFetch`.
- [ ] Step 5: Refactor `legacy/PlaceOrderComponentTest` to use `BackendDsl`; delete its private `orderRequest` / `placeAndFetch` (external stubs stay raw WireMock — unchanged, preserving the contrast).
- [ ] Step 6: Update the class-level Javadoc on both twins if it references the SUT helpers; ensure the "before/after" wording still reads correctly (contrast is now external-stub-only).
- [ ] Step 7: Compile + test: `./gradlew build` in `system/multitier/backend-java`. Confirm all component tests (and the Pact provider verification that shares the harness) pass.

## Decisions (settled)

- **Wiring:** approach (a) — `protected BackendDsl backend;` on `AbstractComponentTest`, initialized in a `@BeforeEach` from the autowired `restTemplate`. Single shared home for every component-test subclass and the Pact verifier.
- **Naming:** `BackendDsl` / `BackendDriver` — symmetric with the stub triplet, matches the "SUT = backend" model.
- **Scope:** Java backend only; no TS / .NET parity work in this plan.

## Open questions

- **Terminal-op shape.** Single `execute()` returning a rich result vs distinct terminals (`placeAndView()` vs `placeExpectingRejection()` / `place()`). Lean toward two explicit terminals so the rejection tests don't parse a body they don't need — mirrors the two real scenarios in the tests. (Design detail, settle during Step 3.)

## Follow-ups (out of scope here)

- **Assert the `GET /api/orders/{n}` HTTP status on the happy path.** Today `placeAndFetch` asserts `201 CREATED` on the POST but the subsequent view/GET only has its *body* fields checked — the `200 OK` status is never verified. That's a test-coverage gap, not a refactor concern; keep it out of this behaviour-neutral extraction and track it as its own plan. (Worth checking whether the same gap exists in the TS / .NET twins when that plan is written.)
