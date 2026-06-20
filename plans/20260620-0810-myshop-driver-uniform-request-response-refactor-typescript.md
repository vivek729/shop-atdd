# 2026-06-20 08:10 UTC — TypeScript refactor: uniform `*Request` / `*Response` for every `MyShopDriver` method

> Sibling of the Java refactor (`plans/20260620-0758-...`, committed `ba409b5c`) and the .NET plan (`...-dotnet.md`). Brings the **TypeScript** test-kit driver contract into line so all three languages share the uniform req/response shape.
> Child of the parent investigation plan `plans/20260620-0741-archunit-enforce-dsl-driver-rules-investigation.md`.

## TL;DR

**Why:** Java's `MyShopDriver` now gives every operation a `*Request` and `*Response`. TypeScript's `my-shop-driver.ts` still has the **identical 6-method violation** (verified), so the two languages have drifted. This plan applies the same refactor to TS.
**End result:** Every `MyShopDriver` method has the shape `x(request: XRequest): Promise<Result<XResponse, SystemError>>`. Payload-free operations carry **empty** `*Request`/`*Response` types (no rule exceptions, matching the Java Q2(b) decision). Both adapters, the 6 DSL use cases, and any legacy direct-caller tests compile (`npx tsc --noEmit`) and the TS `--sample` suite stays green. **Then ts-arch/ESLint+ts-morph checks enforce the A1/A2/A7/A10 structural rules — parity with Java's `ArchitectureRulesTest`** (overrides parent-plan Q4, which had scoped TS to a written note only).

## Resolved decisions (inherited from the Java plan — keep all three languages identical)

- **Scope = all 6 violators.** Same methods as Java.
- **`goToMyShop` / `browseCoupons` get empty req/resp** rather than a documented exception.
- **API/HTTP client stays raw.** The adapter unwraps the request object and wraps the result into the response object.
- **DSL use cases discard empty responses** (keep their existing void verification typing where the response is empty).
- **Mirror the existing TS DTO convention** (class vs interface) used by `PlaceOrderRequest.ts` / `PublishCouponRequest.ts` — read one before creating the new ones.

## The 6 methods — before → after (`driver/port/my-shop-driver.ts`, verified)

| Method | Before | After | New DTOs |
|--------|--------|-------|----------|
| `goToMyShop` | `() → void` | `(GoToMyShopRequest) → GoToMyShopResponse` | `GoToMyShopRequest` (empty), `GoToMyShopResponse` (empty) |
| `cancelOrder` | `(string) → void` | `(CancelOrderRequest) → CancelOrderResponse` | `CancelOrderRequest {orderNumber}`, `CancelOrderResponse` (empty) |
| `deliverOrder` | `(string) → void` | `(DeliverOrderRequest) → DeliverOrderResponse` | `DeliverOrderRequest {orderNumber}`, `DeliverOrderResponse` (empty) |
| `viewOrder` | `(string) → ViewOrderResponse` | `(ViewOrderRequest) → ViewOrderResponse` | `ViewOrderRequest {orderNumber}` |
| `publishCoupon` | `(PublishCouponRequest) → void` | `(PublishCouponRequest) → PublishCouponResponse` | `PublishCouponResponse` (empty) |
| `browseCoupons` | `() → BrowseCouponsResponse` | `(BrowseCouponsRequest) → BrowseCouponsResponse` | `BrowseCouponsRequest` (empty) |

## Touch points (verified by grep)

- **Port**: `src/testkit/driver/port/my-shop-driver.ts` — 6 signatures + imports.
- **DTOs**: `src/testkit/driver/port/dtos/` (existing: `PlaceOrderRequest`, `PlaceOrderResponse`, `PublishCouponRequest`, `ViewOrderResponse`, `BrowseCouponsResponse`, `OrderStatus`) — add the 9 new types.
- **Adapters**: `src/testkit/driver/adapter/ui/my-shop-ui-driver.ts`, `src/testkit/driver/adapter/api/my-shop-api-driver.ts` — unwrap/wrap; client logic unchanged.
- **DSL use cases**: `src/testkit/dsl/core/usecase/usecases/` — `GoToMyShop`, `CancelOrder`, `DeliverOrder`, `ViewOrder`, `PublishCoupon`, `BrowseCoupons` — build request from context; discard empty responses.
- **Legacy direct-caller tests**: re-grep `Driver\.(goToMyShop|cancelOrder|deliverOrder|viewOrder|publishCoupon|browseCoupons)\(` (and any `myShopDriver.`) under `system-test/typescript/tests/` — the src grep found only the 6 use cases, but the `tests/legacy/**` smoke/e2e specs likely call the driver directly (as Java's mod05/mod06 did). Confirm and fix the full set during execution.

## Steps

**T1–T6, T8 ✅ DONE (2026-06-20).** Refactor complete: 9 new DTOs created + `PublishCouponRequest` normalized to string-only (`discountRate`/`usageLimit`) for A1 parity; `close()` extracted into `AsyncCloseable` so A10 sees only the 7 business methods; both adapters + 6 use cases + 5 scenario `then-*.ts` + `assume-stage.ts` + legacy mod05/mod06 callers updated. The 4 empty-response use cases keep `VoidVerification` but parameterize `TResponse` to discard the empty response. `npx tsc --noEmit` green. **T8** implemented with **ts-morph** (single tool, all 4 rules — mirrors Java's one ArchUnit / .NET's one ArchUnitNET) as a standalone `architecture-test` Playwright project (`npm run test:architecture`); all 4 green + each demonstrated red-then-green.

- [ ] **T7 (sample run only) — ⏳ Deferred** (2026-06-20, Docker busy). `tsc --noEmit` half is **done & green**. Remaining: TS `--sample` suite (`GH_OPTIVEM_CONFIG=gh-optivem-monolith-typescript.yaml`) — **coordinate container usage with the user first**.

## ▶ Next executable step (resume here)

Only the **T7 `--sample` run** remains (deferred — needs Docker). When containers are free: `cd system-test/typescript`, set `GH_OPTIVEM_CONFIG=gh-optivem-monolith-typescript.yaml`, then `gh optivem system start` → `gh optivem test setup` → `gh optivem test run --sample` → `gh optivem system stop`. All else (T1–T6, T8, tsc) is done and committed.

## Non-goals

- ~~The architecture-rule checks~~ — **now in scope** (T8, full A1/A2/A7/A10 parity with Java via ts-arch/ts-morph; overrides parent Q4).
- Rules beyond the 4 proven in Java (A3/A4/A5/A6/A8/A9, B-tier, C-tier) — match Java's committed set only.
- Java or .NET changes (Java already done; .NET is the sibling plan `...-dotnet.md`).
