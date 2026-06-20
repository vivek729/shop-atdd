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

- [ ] **D1 — Read one existing DTO** (`Driver.Port/Dtos/PlaceOrderRequest.*`) to lock the record/class convention, then create the 9 new DTO types.
- [ ] **D2 — Update `IMyShopDriver`** — 6 signatures.
- [ ] **D3 — Update `MyShopApiDriver`** — unwrap/wrap; client unchanged.
- [ ] **D4 — Update the UI adapter** — same.
- [ ] **D5 — Update the 6 DSL use cases.**
- [ ] **D6 — Re-grep `\.(GoToMyShop|CancelOrder|DeliverOrder|ViewOrder|PublishCoupon|BrowseCoupons)Async\(` under `SystemTests/` and fix every direct caller** (expected: Mod04/05/06 listed above).
- [ ] **D7 — `dotnet build` green**, then the .NET `--sample` suite (`GH_OPTIVEM_CONFIG=gh-optivem-monolith-dotnet.yaml`, the same start/setup/run --sample/stop flow) — **coordinate container usage with the user first** (they flagged concurrent Docker work).
- [ ] **D8 — Architecture-rule tests (ArchUnitNET) — parity with Java (overrides Q4).** Add an xUnit test (`[Trait("Category","Architecture")]`) using **ArchUnitNET** (IL-based, closest ArchUnit parity, supports custom conditions) asserting the A1/A2/A7/A10 equivalents, mirroring `system-test/java/.../architecture/ArchitectureRulesTest.java`:
  - **A1** — `*Request` types in `Driver.Port.Dtos` expose only `string` members.
  - **A2** — public methods of `*Verification` types return their own type (fluent) or `void` (terminal).
  - **A7** — no type in `Dsl.Core` is named `*Request`/`*Response` (it shares `Driver.Port.Dtos`).
  - **A10** — every `IMyShopDriver` method takes a single `*Request` and returns `Task<Result<*Response, …>>` (read the generic args via ArchUnitNET's type model).
  Demonstrate one red-then-green per rule (as Java did). Same IL constant-inlining caveat applies to B1 → out of scope. Add a `dotnet`-side "architecture-only" run if a category filter is easy; otherwise the rules run in the normal test pass.
- [ ] **D9 — Commit `shop` repo, scoped** (`gh optivem commit --yes --include-untracked --repo shop "..."`).

## ▶ Next executable step (resume here)

Execute **D1**: read `system-test/dotnet/Driver.Port/Dtos/PlaceOrderRequest.*` to confirm the DTO convention, then create the 9 new DTO types under `Driver.Port/Dtos/`.

## Non-goals

- ~~The architecture-rule tests~~ — **now in scope** (D8, full A1/A2/A7/A10 parity with Java via ArchUnitNET; overrides parent Q4).
- Rules beyond the 4 proven in Java (A3/A4/A5/A6/A8/A9, B-tier, C-tier) — match Java's committed set only.
- Java or TypeScript changes (Java already done; TS is the sibling plan `...-typescript.md`).
