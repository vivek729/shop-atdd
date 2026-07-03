# 2026-07-03 13:39:00 UTC — Fix missing .NET/TypeScript Pact provider-state handlers for per-status ORD-1 states

## TL;DR

**Why:** Commit `87828dbd` split the single `order ORD-1 exists` Pact provider state into three status-specific states (`order ORD-1 is placed/cancelled/delivered`) and updated the matching `@State` handlers only in the Java backend, leaving the .NET and TypeScript multitier backends' provider-state handlers out of sync with the contract. This breaks their Pact provider-verification (component) tests: `GET /api/orders/ORD-1` 404s because no order is ever seeded for the new state names.
**End result:** The .NET and TypeScript multitier backend Pact provider-verification tests seed order `ORD-1` correctly for all three status states and pass, matching the already-correct Java implementation. `prerelease-pipeline-multitier-dotnet.yml` and `multitier-backend-typescript-commit-stage.yml` (and the scheduled `meta-prerelease-stage` run that wraps them) go green again.

## Outcomes

- `system/multitier/backend-dotnet` Pact contract test (`dotnet test Tests --filter "FullyQualifiedName~MyCompany.MyShop.Backend.Tests.Contract"`) passes with 0 pact failures.
- `system/multitier/backend-typescript` Pact contract spec (`test/pact/backend.pact.spec.ts`) passes.
- Both backends seed order `ORD-1` with the correct `OrderStatus` (`PLACED` / `CANCELLED` / `DELIVERED`) for their respective provider states, mirroring the Java handler at `BackendPactVerificationTest.java:60-68` and `:98-104`.
- `./compile-all.sh` passes from the repo root.
- Java is untouched — it was already correct.

## ▶ Next executable step (resume here)

Step 1: edit `system/multitier/backend-dotnet/Tests/Contract/BackendPactVerificationTest.cs` — replace the `case "order ORD-1 exists":` branch (around line 210) and add the two missing cases, plus a status-aware `SampleOrder` overload, as detailed in Steps below.

## Steps

- [ ] Step 1: In `system/multitier/backend-dotnet/Tests/Contract/BackendPactVerificationTest.cs`:
  - Replace `case "order ORD-1 exists":` (line ~210) with `case "order ORD-1 is placed":`, still seeding `SampleOrder("ORD-1")` (default status `PLACED`).
  - Add `case "order ORD-1 is cancelled":` seeding an order via a new `SampleOrder("ORD-1", OrderStatus.CANCELLED)` overload.
  - Add `case "order ORD-1 is delivered":` seeding via `SampleOrder("ORD-1", OrderStatus.DELIVERED)`.
  - Add a `SampleOrder(string orderNumber, OrderStatus status)` overload (mirroring Java's `sampleOrder(orderNumber, status)` at `BackendPactVerificationTest.java:98-104`); keep the existing `SampleOrder(string orderNumber)` as a convenience wrapper defaulting to `OrderStatus.PLACED`, replacing the hardcoded `Status = OrderStatus.PLACED` at line 288.
- [ ] Step 2: In `system/multitier/backend-typescript/test/pact/backend.pact.spec.ts`:
  - Replace the `'order ORD-1 exists'` handler (lines 72-80) with `'order ORD-1 is placed'`, unchanged body (status defaults to `OrderStatus.PLACED` via `sampleOrder()`).
  - Add `'order ORD-1 is cancelled'` handler: `harness.resetState()` then save `{ ...sampleOrder(), orderNumber: 'ORD-1', status: OrderStatus.CANCELLED }`.
  - Add `'order ORD-1 is delivered'` handler: same pattern with `status: OrderStatus.DELIVERED`.
- [ ] Step 3: Run `dotnet test Tests --filter "FullyQualifiedName~MyCompany.MyShop.Backend.Tests.Contract"` in `system/multitier/backend-dotnet`; confirm all Pact interactions pass (previously `Failed: 1, Passed: 0` due to 3 sub-failures inside the single xUnit `Verify` test).
- [ ] Step 4: Run the TypeScript Pact spec (`test/pact/backend.pact.spec.ts`) via the project's test command in `system/multitier/backend-typescript`; confirm it passes.
- [ ] Step 5: Run `./compile-all.sh` from the repo root; confirm it passes.
- [ ] Step 6: Confirm `system/multitier/backend-java` was not modified (already correct, out of scope for this fix).
