# 2026-06-20 08:10 UTC — .NET refactor: uniform `*Request` / `*Response` for every `IMyShopDriver` method

> Sibling of the Java refactor (`plans/20260620-0758-...`, committed `ba409b5c`). Brings the **.NET** test-kit driver contract into line with Java so all three languages share the uniform req/response shape.
> Child of the parent investigation plan `plans/20260620-0741-archunit-enforce-dsl-driver-rules-investigation.md` (the cross-language consistency follow-up).

## TL;DR

**Why:** Java's `MyShopDriver` now gives every operation a `*Request` and `*Response`. .NET's `IMyShopDriver` still has the **identical 6-method violation** (verified), so the two languages have drifted. This plan applies the same refactor to .NET.
**End result:** Every `IMyShopDriver` method has the shape `Task<Result<XResponse, SystemError>> XAsync(XRequest request)`. Payload-free operations carry **empty** `*Request`/`*Response` types (no rule exceptions, matching the Java Q2(b) decision). Both adapters, the 6 DSL use cases, and the legacy direct-caller tests compile and the .NET `--sample` suite stays green.

## Resolved decisions (inherited from the Java plan — keep all three languages identical)

- **Scope = all 6 violators.** Same methods as Java.
- **`GoToMyShop` / `BrowseCoupons` get empty req/resp** rather than a documented exception.
- **API client controllers / HTTP client stay raw.** The adapter unwraps the request DTO and wraps the result into the response DTO.
- **DSL use cases discard empty responses** (keep their existing `VoidValue`/void verification typing where the response is empty).
- **Mirror the existing .NET DTO convention** (record vs class) used by `PlaceOrderRequest` / `PublishCouponRequest` — confirm by reading one before creating the new ones.

## The 6 methods — before → after (`Driver.Port/IMyShopDriver.cs`, verified)

| Method | Before | After | New DTOs |
|--------|--------|-------|----------|
| `GoToMyShopAsync` | `() → VoidValue` | `(GoToMyShopRequest) → GoToMyShopResponse` | `GoToMyShopRequest` (empty), `GoToMyShopResponse` (empty) |
| `CancelOrderAsync` | `(string?) → VoidValue` | `(CancelOrderRequest) → CancelOrderResponse` | `CancelOrderRequest {OrderNumber}`, `CancelOrderResponse` (empty) |
| `DeliverOrderAsync` | `(string?) → VoidValue` | `(DeliverOrderRequest) → DeliverOrderResponse` | `DeliverOrderRequest {OrderNumber}`, `DeliverOrderResponse` (empty) |
| `ViewOrderAsync` | `(string?) → ViewOrderResponse` | `(ViewOrderRequest) → ViewOrderResponse` | `ViewOrderRequest {OrderNumber}` |
| `PublishCouponAsync` | `(PublishCouponRequest) → VoidValue` | `(PublishCouponRequest) → PublishCouponResponse` | `PublishCouponResponse` (empty) |
| `BrowseCouponsAsync` | `() → BrowseCouponsResponse` | `(BrowseCouponsRequest) → BrowseCouponsResponse` | `BrowseCouponsRequest` (empty) |

## Touch points (verified by grep)

- **Port**: `Driver.Port/IMyShopDriver.cs` — 6 signatures.
- **DTOs**: `Driver.Port/Dtos/` (confirm exact dir/case) — add the 9 new types.
- **Adapters**: `Driver.Adapter/Api/MyShopApiDriver.cs` and the UI adapter under `Driver.Adapter/Ui/` (confirm filename) — unwrap request / wrap response; client logic unchanged.
- **DSL use cases**: `Dsl.Core/UseCase/UseCases/` — `GoToMyShop`, `CancelOrder`, `DeliverOrder`, `ViewOrder`, `PublishCoupon`, `BrowseCoupons` — build request from context; discard empty responses.
- **Legacy direct-caller tests** (call the driver directly, not via DSL — must be updated): `SystemTests/Legacy/Mod05/SmokeTests/System/MyShopBaseSmokeTest.cs`, `Mod06/SmokeTests/System/MyShopSmokeTest.cs`, `Mod05/E2eTests/PlaceOrderPositiveBaseTest.cs`, `Mod06/E2eTests/PlaceOrderPositiveTest.cs`, **and `Mod04/E2eTests/PlaceOrderPositiveApiTest.cs`** (one more than Java had — re-grep `\.XAsync\(` under `SystemTests/` to confirm the full set).

## Steps

- [ ] **D1 — Read one existing DTO** (`Driver.Port/Dtos/PlaceOrderRequest.*`) to lock the record/class convention, then create the 9 new DTO types.
- [ ] **D2 — Update `IMyShopDriver`** — 6 signatures.
- [ ] **D3 — Update `MyShopApiDriver`** — unwrap/wrap; client unchanged.
- [ ] **D4 — Update the UI adapter** — same.
- [ ] **D5 — Update the 6 DSL use cases.**
- [ ] **D6 — Re-grep `\.(GoToMyShop|CancelOrder|DeliverOrder|ViewOrder|PublishCoupon|BrowseCoupons)Async\(` under `SystemTests/` and fix every direct caller** (expected: Mod04/05/06 listed above).
- [ ] **D7 — `dotnet build` green**, then the .NET `--sample` suite (`GH_OPTIVEM_CONFIG=gh-optivem-monolith-dotnet.yaml`, the same start/setup/run --sample/stop flow) — **coordinate container usage with the user first** (they flagged concurrent Docker work).
- [ ] **D8 — Commit `shop` repo, scoped** (`gh optivem commit --yes --include-untracked --repo shop "..."`).

## ▶ Next executable step (resume here)

Execute **D1**: read `system-test/dotnet/Driver.Port/Dtos/PlaceOrderRequest.*` to confirm the DTO convention, then create the 9 new DTO types under `Driver.Port/Dtos/`.

## Non-goals

- The ArchUnit/NetArchTest rule POC itself (parent plan; this only makes the code compliant).
- Java or TypeScript changes (Java already done; TS is the sibling plan `...-typescript.md`).
