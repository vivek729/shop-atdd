# orderHistory in the DSL — parity to discuss (DISCUSSION, not yet actionable)

**Status:** Open for discussion. No implementation until the open questions below are decided.
**Related:** `plans/20260717-1015-component-stub-contract-mirror.md` (external-stub contract work —
separate; `orderHistory` is deliberately out of that scope because it involves no external stub).

## What this is about

`then().orderHistory().containsOrder()` exists in the **component** scenario DSL but has **no
equivalent in system-test's** scenario DSL. This surfaced repeatedly while aligning the two layers;
it is a genuine parity question that deserves its own decision rather than being folded into the
contract-test passes.

## The facts (verified 2026-07-17)

- **Component** has a full browse-history path in the scenario DSL:
  - `when().browseOrderHistory()` (`WhenBrowseOrderHistoryImpl`)
  - `then().orderHistory().containsOrder()` / `containsOrder(orderNumber)` (`ThenOrderHistoryImpl`)
  - reads the SUT's own **`GET /api/orders`** via `BrowseOrderHistory` → `BackendDriver`.
  - exercised by `OrderHistoryComponentTest.browseReturnsPlacedOrders()`.
- **System-test** has **none** of that in its scenario DSL. `ThenStep`/`ThenStage` there expose
  `order`, `coupon`, `clock`, `product`, `country` — **no `orderHistory`**, and there is no
  `when().browseOrderHistory()`.
- System-test exercises order history **only through the UI** — `OrderHistoryPage` /
  `MyShopUiDriver`, and only in a **legacy** UI test (`legacy/mod04/e2e/PlaceOrderPositiveUiTest`).
  There is **no `latest` acceptance/scenario test** for `GET /api/orders` at the system-test level.

## Why it is not a correctness problem

`orderHistory()` reads the SUT's own list endpoint — same category as `order()` and `coupon()`. It is
already honest (no stub involved, so no tautology). So this is purely about **parity and coverage
symmetry between the two test layers**, not about fixing a broken step. Whatever we decide, the
component step as written is fine.

## Open questions to decide

1. **Direction of reconciliation.** Component is currently *richer* than system-test here. Do we
   (a) bring system-test up to component by adding a `browse order history` scenario step, or
   (b) accept the asymmetry as intentional (component asserts the list via a scenario step; system-test
   asserts it via the UI), or (c) something else?
   - *Leaning:* (a) — a symmetric scenario step reads more consistently and would also close the
     coverage gap in Q2; but this is exactly what needs discussion.

2. **Is `GET /api/orders` under-covered at the system-test level?** Today it appears to be tested
   only via a *legacy* UI test — no `latest` scenario or acceptance test drives the list endpoint.
   If true, that is a real coverage gap independent of DSL parity.
   - *Leaning:* worth a `latest` system-test that browses order history (API + UI channels), which
     would naturally introduce the `orderHistory` scenario step and resolve Q1(a) at the same time.

3. **DSL shape if we add it to system-test.** Mirror the component surface
   (`when().browseOrderHistory()` + `then().orderHistory().containsOrder()`), or a different shape
   that fits system-test's multi-channel (UI + API) model — e.g. how `containsOrder()` resolves the
   SUT-generated order number across channels?

4. **`containsOrder()` no-arg resolution.** Component's no-arg `containsOrder()` resolves the order
   number the executed action produced (from `ExecutionResultContext`). Does the same
   context-resolution mechanism exist / make sense in system-test, or would system-test need the
   explicit `containsOrder(orderNumber)` form?

## Explicitly NOT in scope here

- The external-stub contract work (clock/product/country) — that is the other two plans. `orderHistory`
  has no external stub and no contract-test angle.

## Next step

Discuss Q1–Q4 (start with Q2: is the system-test list-endpoint coverage gap real?). Once a direction is
chosen, convert the decisions into a fresh actionable plan and delete this discussion file.
