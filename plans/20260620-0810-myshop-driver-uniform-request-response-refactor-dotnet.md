# 2026-06-20 08:10 UTC — .NET refactor: uniform `*Request` / `*Response` for every `IMyShopDriver` method

> Sibling of the Java refactor (`plans/20260620-0758-...`, committed `ba409b5c`). Brings the **.NET** test-kit driver contract into line with Java so all three languages share the uniform req/response shape.
> Child of the parent investigation plan `plans/20260620-0741-archunit-enforce-dsl-driver-rules-investigation.md` (the cross-language consistency follow-up).

## TL;DR

**Why:** Java's `MyShopDriver` now gives every operation a `*Request` and `*Response`. .NET's `IMyShopDriver` still has the **identical 6-method violation** (verified), so the two languages have drifted. This plan applies the same refactor to .NET.
**End result:** Every `IMyShopDriver` method has the shape `Task<Result<XResponse, SystemError>> XAsync(XRequest request)`. Payload-free operations carry **empty** `*Request`/`*Response` types (no rule exceptions, matching the Java Q2(b) decision). Both adapters, the 6 DSL use cases, and the legacy direct-caller tests compile and the .NET `--sample` suite stays green. **Then ArchUnitNET tests enforce the A1/A2/A7/A10 structural rules — full parity with Java's `ArchitectureRulesTest`** (this overrides parent-plan Q4, which had scoped .NET to a written note only).

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

> **D1–D6 + D8 DONE & verified** (this session): 9 DTOs created; `IMyShopDriver` + both adapters + 6 DSL use cases + 4 legacy driver callers refactored; ArchUnitNET A1/A2/A7/A10 tests added (`SystemTests/Latest/ArchitectureTests/ArchitectureRulesTest.cs`, pkg `TngTech.ArchUnitNET.xUnit 0.13.3`) — all 4 green and each demonstrated red-then-green; full solution `dotnet build` green. **Mod04 `PlaceOrderPositiveApiTest` needed no change** (it calls the raw API client, which stays raw). Remaining: D7 sample run (Docker) + D9 commit.

- [ ] **D7 — sample suite (Docker). ⏳ Deferred** (2026-06-20, user: Docker busy with concurrent work). `dotnet build` is green and the code was committed on that basis; the `--sample` verification was **not** run this session (consciously overriding the pre-commit `--sample` rule). Run later: `GH_OPTIVEM_CONFIG=gh-optivem-monolith-dotnet.yaml`, start → test setup → test run --sample → stop.

## ▶ Next executable step (resume here)

Only **D7** remains and it is **deferred** (Docker busy). When Docker is free, run the .NET `--sample` suite (`gh-optivem-monolith-dotnet.yaml`, start → test setup → test run --sample → stop) to verify the committed refactor at runtime. All code (D1–D6, D8) is done, committed, and `dotnet build`-green.

## Non-goals

- ~~The architecture-rule tests~~ — **now in scope** (D8, full A1/A2/A7/A10 parity with Java via ArchUnitNET; overrides parent Q4).
- Rules beyond the 4 proven in Java (A3/A4/A5/A6/A8/A9, B-tier, C-tier) — match Java's committed set only.
- Java or TypeScript changes (Java already done; TS is the sibling plan `...-typescript.md`).
