# 2026-07-08 08:45:00 UTC — Align system-test example SKUs to the repo standard (`BOOK-123`)

> **Follow-up split off from** `20260706-0925-restructure-component-contract-tests-legacy-latest.md` (this session). That plan settled that `system/**` and the canonical pact `contracts/frontend-backend.json` are uniform on **`BOOK-123`**, while `system-test/` uses per-test illustrative SKU tokens. This plan aligns the *concrete example* SKUs in `system-test/` to `BOOK-123`, deliberately kept OUT of the contract-tests-restructure plan to keep that one focused on the frontend↔backend component/contract test split.

## Why

`system-test/` acceptance and contract tests use ad-hoc SKU tokens (`ABC`, `SKU-123`) that don't match the `BOOK-123` used everywhere in `system/**` and the pact. Aligning the concrete example SKUs to `BOOK-123` makes the running example consistent across the whole repo. The DSL's generic `DEFAULT-SKU` placeholder and the deliberately-invalid `NON-EXISTENT-SKU-12345` stay as-is — they play distinct roles (unspecified-product default; missing-product negative case) and must not be collapsed into the example SKU.

## Decisions (settled this session)

- `ABC` → **`BOOK-123`** (acceptance: `PlaceOrderPositiveTest`, all 3 languages).
- `SKU-123` → **`BOOK-123`** (ERP external-system contract tests: `BaseErpContractTest`, all 3 languages, **both `latest/` and `legacy/mod11/`**).
- `NON-EXISTENT-SKU-12345` → **leave as-is** (missing-product negative case).
- `DEFAULT-SKU` → **leave as-is** (DSL default in `ScenarioDefaults` / `defaults.ts`).

## Target state

Every *concrete example* SKU in `system-test/` reads `BOOK-123`, matching `system/**` and the pact. No behavioural change — each test rewrites its own SKU consistently (the ERP contract tests keep their paired `withUnitPrice(12.0)` / `hasPrice(12.0)`; only the SKU token changes).

## Steps

- [ ] **Step 1 — Rename `ABC` → `BOOK-123` in the positive place-order acceptance test (3 languages).**
  - `system-test/dotnet/SystemTests/Latest/AcceptanceTests/PlaceOrderPositiveTest.cs` (lines 17, 19)
  - `system-test/java/src/test/java/com/mycompany/myshop/systemtest/latest/acceptance/PlaceOrderPositiveTest.java` (lines 16, 22)
  - `system-test/typescript/tests/latest/acceptance/place-order-positive-test.spec.ts` (lines 9, 17)
- [ ] **Step 2 — Rename `SKU-123` → `BOOK-123` in the ERP external-system contract tests (3 languages, both `latest/` AND `legacy/mod11/` — confirmed this session).** Each file has 3 occurrences (given `withSku`, then `product(...)`, `hasSku(...)`).
  - `system-test/dotnet/SystemTests/Latest/ExternalSystemContractTests/Erp/BaseErpContractTest.cs`
  - `system-test/dotnet/SystemTests/Legacy/Mod11/ExternalSystemContractTests/Erp/BaseErpContractTest.cs`
  - `system-test/java/src/test/java/com/mycompany/myshop/systemtest/latest/contract/erp/BaseErpContractTest.java`
  - `system-test/java/src/test/java/com/mycompany/myshop/systemtest/legacy/mod11/contract/erp/BaseErpContractTest.java`
  - `system-test/typescript/tests/latest/contract/erp/BaseErpContractTest.ts`
  - `system-test/typescript/tests/legacy/mod11/contract/erp/BaseErpContractTest.ts`
- [ ] **Step 3 — Verify.** Compile the three system-test projects; run the affected suites (`--sample` at least) to confirm the SKU rename is behaviour-neutral. Then commit via the `/commit` skill and delete this plan file.

## Notes

- **`SKU-123` is in ERP *contract* tests, not an acceptance test** (my initial labelling was inaccurate). The rename therefore also touches `legacy/mod11/` — **confirmed this session: rename both `latest` and `legacy` for uniformity** (the legacy variant is a maintained parallel, not a frozen snapshot).
