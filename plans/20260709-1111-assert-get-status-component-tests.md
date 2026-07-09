# 2026-07-09 11:11:46 UTC — Assert GET response status in backend component tests

## TL;DR

**Why:** In the Java backend component tests, the happy-path flow places an order (POST, asserted `201 CREATED`) then reads it back via `GET /api/orders/{orderNumber}`, but only the response *body* fields are asserted — the GET's `200 OK` status is never verified. A broken/misrouted read that still returned a parseable body (or a non-200 with a compatible shape) would slip through.
**End result:** The Java component tests assert `200 OK` on the read/GET before inspecting the body, in both the `latest/` and `legacy/` twins. Java backend only — TypeScript already asserts this and .NET has no equivalent test; neither is in scope.

## Outcomes

What we get out of this — the goals and deliverables:

- Java `latest/` and `legacy/` `PlaceOrderComponentTest` assert `HttpStatus.OK` on the `GET /api/orders/{n}` read before asserting body fields.
- All Java backend component tests still green (`./gradlew build` in `backend-java`).

**Scope:** Java backend only. For context (no work here): TypeScript already asserts `details.status === 200` (`place-order.component.spec.ts:54`); .NET has no `PlaceOrder` component test at all (only a health smoke test) — a separate, larger gap, not this plan.

## ▶ Next executable step (resume here)

In `system/multitier/backend-java/src/componentTest/java/com/mycompany/myshop/backend/component/latest/PlaceOrderComponentTest.java`, in the private `placeAndFetch(...)` helper, assert the read returns `200 OK` before returning the body — e.g. use `restTemplate.getForEntity("/api/orders/" + orderNumber, ViewOrderDetailsResponse.class)`, assert `getStatusCode()` is `HttpStatus.OK`, then return `getBody()`. Apply the same to the `legacy/` twin. Gate: `./gradlew build` in `backend-java` green. (Coordinate with the BackendDsl refactor — see Open questions.)

## Steps

- [ ] Step 1: `latest/PlaceOrderComponentTest` — in `placeAndFetch`, switch the read to `getForEntity(...)`, assert `HttpStatus.OK`, then return the body. (Currently uses `getForObject`, which discards the status.)
- [ ] Step 2: `legacy/PlaceOrderComponentTest` — apply the identical change to its `placeAndFetch`.
- [ ] Step 3: Compile + test — `./gradlew build` in `system/multitier/backend-java`; confirm all component tests + the Pact provider verification (shared harness) pass.

## Open questions

- **Coordination with the BackendDsl refactor** (`plans/20260709-1104-backend-dsl-driver-component-tests.md`). Both plans touch `placeAndFetch` in both twins. Recommended: whichever lands first wins the mechanics —
  - if **this** plan runs first, edit the two `placeAndFetch` helpers directly (as above), and the DSL refactor later carries the assertion into its terminal op;
  - if the **DSL** plan runs first, add the `200 OK` assertion once in the DSL's happy-path terminal op instead of in two test files.
  Either way the assertion ends up in exactly one place per twin. Low risk of conflict; just avoid running them blindly in parallel on the same helper.
