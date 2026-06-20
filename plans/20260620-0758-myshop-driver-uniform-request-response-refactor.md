🤖 **Picked up by agent** — `ValentinaLaptop` at `2026-06-20T08:43:32Z`

# 2026-06-20 07:58 UTC — Refactor: give every `MyShopDriver` method a uniform `*Request` / `*Response`

> Child of `plans/20260620-0741-archunit-enforce-dsl-driver-rules-investigation.md` (Step 3b, expanded).
> This is the concrete code-refactor plan that the investigation's strict-A10 rule (Q2(b)) depends on.

## TL;DR

**Why:** The strict "every driver method takes a `*Request` and returns a `*Response`" rule (A10) can only go green once the port actually complies. The investigation plan named **4** violators; the live `MyShopDriver` interface has **6**. Until all 6 are refactored, the A10 ArchUnit POC cannot be demonstrated green without carve-out exceptions — which contradicts the Q2(b) "enforce uniformly, don't accommodate" decision.
**End result:** Every `MyShopDriver` method has the shape `Result<XResponse, SystemError> x(XRequest request)`. Pure-navigation / payload-free operations carry **empty** `*Request`/`*Response` pairs (no exceptions in the rule). Both adapters, the 6 DSL use cases, and their scenario steps compile and `./gradlew :system-test:java:test` stays green. This unblocks the A10 POC in the parent plan.

## Resolved decisions (from the parent plan's clarification gate)

- **Scope = all 6 violators**, not the parent's listed 4. Confirmed against the live interface.
- **`goToMyShop` = empty req/resp pair** (`GoToMyShopRequest`/`GoToMyShopResponse`), not a documented A10 exception — keeps the rule exception-free.
- **Empty-payload operations get empty DTOs** uniformly (the cost of strict uniformity, accepted under Q2(b)).
- **API client controllers stay raw-`String`.** The adapter unwraps the request DTO before calling the controller and wraps the controller result into the response DTO. The `Ext*` / HTTP-client layer is untouched — blast radius stops at the adapter.
- **DSL use cases discard empty responses.** Where the new response is empty (cancel/deliver/goToMyShop/publishCoupon), the use case keeps its existing `<Void, VoidVerification>` typing and ignores the empty response — avoids churning the verification layer. Only `viewOrder`/`browseCoupons` (which already had real responses) keep their typed results.

## The 6 methods — before → after

| Method | Before | After | New DTOs |
|--------|--------|-------|----------|
| `goToMyShop` | `() → Void` | `(GoToMyShopRequest) → GoToMyShopResponse` | `GoToMyShopRequest` (empty), `GoToMyShopResponse` (empty) |
| `cancelOrder` | `(String) → Void` | `(CancelOrderRequest) → CancelOrderResponse` | `CancelOrderRequest {orderNumber}`, `CancelOrderResponse` (empty) |
| `deliverOrder` | `(String) → Void` | `(DeliverOrderRequest) → DeliverOrderResponse` | `DeliverOrderRequest {orderNumber}`, `DeliverOrderResponse` (empty) |
| `viewOrder` | `(String) → ViewOrderResponse` | `(ViewOrderRequest) → ViewOrderResponse` | `ViewOrderRequest {orderNumber}` |
| `publishCoupon` | `(PublishCouponRequest) → Void` | `(PublishCouponRequest) → PublishCouponResponse` | `PublishCouponResponse` (empty) |
| `browseCoupons` | `() → BrowseCouponsResponse` | `(BrowseCouponsRequest) → BrowseCouponsResponse` | `BrowseCouponsRequest` (empty) |

All new request DTOs are `@Data @Builder @NoArgsConstructor @AllArgsConstructor` with `String`-only fields (satisfies A1). Empty DTOs have no fields.

## Touch points (verified)

- **Port**: `driver/port/MyShopDriver.java` — 6 signatures + imports.
- **DTOs**: `driver/port/dtos/` — add the 8 new classes above.
- **Adapters** (both implement all 6):
  - `driver/adapter/ui/MyShopUiDriver.java` (lines ~42/82/101/120/170/184)
  - `driver/adapter/api/MyShopApiDriver.java` (lines ~27/37/42/47/52/57)
  - Each adapter: unwrap `request.getOrderNumber()` etc., call the existing controller/page logic unchanged, wrap the result into the (often empty) response DTO.
- **API client controllers stay as-is**: `driver/adapter/api/client/controllers/OrderController.java`, `CouponController.java` (raw `String` params — no change).
- **DSL use cases** (call `driver.x(...)`): `CancelOrder`, `DeliverOrder`, `ViewOrder`, `GoToMyShop`, `PublishCoupon`, `BrowseCoupons` under `dsl/core/usecase/usecases/` — build the request DTO from existing context values; discard empty responses.
- **Scenario step impls** (if they call the driver directly rather than via use case): `WhenViewOrderImpl`, `WhenPublishCouponImpl`, `WhenCancelOrderImpl`, `WhenBrowseCouponsImpl`, plus `GivenOrderImpl`/`GivenCouponImpl`/`AssumeImpl`/`ThenOrderImpl`/`ThenCouponImpl` — audit each; update only those that call the 6 methods directly.

## Steps

> R1–R7b complete. Compile green (`./gradlew compileJava compileTestJava` BUILD SUCCESSFUL). Java `--sample` suite all green (11/11 suites: smoke/acceptance/contract/e2e × stub/real × UI/API). 9 new DTOs (not 8 — empty pairs added up). R6 was extended: 4 **legacy test files** also called the driver directly (`mod05`/`mod06` smoke + e2e) and were updated. Remaining:

- [ ] **R8 — Hand back to the parent plan** — the A10 POC rule (parent Step 3 / Step 4 red-green) can now be asserted strictly with zero exceptions. Update the parent plan's Step 3b note to "done — 6 methods" and resume parent execution.

## Open sub-points (none blocking)

- Whether empty response DTOs should instead be a single shared `EmptyResponse` marker type. **Recommendation: no** — per-operation named types keep A10 a pure naming/shape rule (`x` ↔ `xRequest`/`xResponse`) and read better. Revisit only if the empty-class count feels noisy after R1.

## ▶ Next executable step (resume here)

The code refactor (R1–R7) is done and compiles green. Next: **R7b** — run the Java monolith `--sample` system-test suite (per CLAUDE.md pre-commit gate) to confirm the refactored UI/API drivers still behave, then commit `system-test/java` and proceed to **R8** (update the parent investigation plan's Step 3b to "done — 6 methods" and resume it for the ArchUnit A10 POC).

## Non-goals

- The ArchUnit POC test itself (lives in the parent plan, Steps 2–4) — this plan only makes the code *compliant* so that rule can go green.
- Per-operation port decomposition (`PlaceOrderUseCase` triads) — separate follow-up plan.
- .NET / TypeScript equivalents — Java-first; parent plan Step 7 covers them as a written note.
