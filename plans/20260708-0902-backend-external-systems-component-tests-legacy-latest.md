# 2026-07-08 09:02:00 UTC — Backend component tests: legacy/latest split for the external-systems (ERP/tax) stubs

> **Provider-side mirror** of the frontend contract-tests refactor (committed `fad5d866`, "Restructure frontend component/integration contract tests into legacy/latest with shared test-kit"). Where the frontend split its **consumer** contract tests, this plan splits the **backend component tests** — the ones that stub the ERP/tax **external systems** — into the same `legacy` (raw WireMock) / `latest` (stub DSL) progression. Matches the `PAID-TDD-*-contract-tests-external-systems` article drafts.

## Why

The backend component tests stub external systems (ERP product lookup, tax service) with **hand-written WireMock blocks**. The external-systems article drafts teach the plain→maintainable refactor of exactly this: raw `WireMock.stubFor(...)` → a fluent **stub DSL** (`erpStub.returnsProduct().withSku(...).withUnitPrice(...).execute()`). Today that "after" snippet has no backing code in the repo. This plan makes the article's before/after point at real, runnable backend code — the symmetric counterpart to what the frontend refactor did for the consumer side.

## Design decisions

1. **Axis composition, not competition.** Backend tests are organised by Gradle/dotnet **source set** (the test *layer*: componentTest / integrationTest / contractTest / unit). `legacy`/`latest` is the orthogonal *progression* axis. They **compose**: `legacy`/`latest` lives **inside** a source set as a package/folder split, e.g. `componentTest/.../component/{legacy,latest}/`.
2. **Only source sets with a real before/after story get the split.**
   - **componentTest** (WireMock ERP/tax stubs → stub DSL) — **YES**, this plan's subject.
   - **contractTest** (provider pact verifier) — **NO**, agnostic to legacy/latest; nothing to refactor (see the committed frontend plan's decision #6).
   - **integrationTest / unit** — **NO** for now; revisit only if a specific refactor is worth teaching.
3. **Nesting inverts vs frontend — intentionally.** Frontend: `test/{legacy,latest}/{component,integration}/` (progression outer). Backend: `src/<sourceSet>/.../{legacy,latest}/` (layer outer). This is a necessary consequence of Gradle/dotnet fixed source-set roots, **not** an inconsistency — document it so nobody "aligns" it away.
4. **Shared stub DSL + driver, one per language**, absorbing the WireMock scaffolding; `legacy` keeps raw WireMock verbatim (the article "before"), `latest` imports the DSL (the "after"). Behaviour-neutral: same stubbed responses, same assertions.
5. **All 3 languages** (Java, .NET, TypeScript) — parallel implementations must stay symmetric (per repo CLAUDE.md).

## Current state (to confirm at Step 0)

- **Java** — `system/multitier/backend-java/src/componentTest/java/com/mycompany/myshop/backend/component/`: `PlaceOrderComponentTest.java`, `OrderHistoryComponentTest.java`, `CouponComponentTest.java`, `ComponentHarnessSmokeTest.java`, plus `AbstractComponentTest.java` base.
- **.NET** — `system/multitier/backend-dotnet/Tests/Component/BackendApplicationTests.cs`.
- **TypeScript** — `system/multitier/backend-typescript/test/component/`: `place-order.component.spec.ts`, `order-history.component.spec.ts`, `coupon.component.spec.ts`.

## Steps

- [ ] **Step 0 — Enumerate concretely (per the consistency rule).** For each language, list every component test and every external-system stub call (ERP `GET /api/products/{sku}`, tax service) it makes. Produce a side-by-side table so the `legacy`→`latest` extraction is symmetric across languages. Identify what already exists as a shared harness (`AbstractComponentTest`, dotnet test fixtures, TS harness) vs. what must be added.
- [ ] **Step 1 — Build the shared stub DSL + driver per language.** A `ErpStubDsl` / `TaxStubDsl` (naming TBD) over a WireMock driver that wraps `stubFor(...)`. Keep it behaviour-neutral: same URLs, same JSON bodies as the current raw blocks.
- [ ] **Step 2 — Split each component test into `legacy/` + `latest/`.** `legacy` = today's raw WireMock, kept verbatim (the article "before"). `latest` = thin test importing the stub DSL (the "after"). Wire the build (Gradle source-set include/exclude, dotnet, vitest) so both run.
- [ ] **Step 3 — Verify behaviour-neutral.** Compile all three backends; run the component suites (`--sample` at least) and confirm identical pass/fail. No change to the pact or provider verifier.
- [ ] **Step 4 — Cross-link the article.** The external-systems drafts' "after" snippet points at real `latest/` + stub-DSL code; "before" points at `legacy/`. (Coordinate with the substack article-sync plan.)
- [ ] **Step 5 — Commit via `/commit`; delete this plan.**

## Notes

- SKU alignment: use the repo standard **`BOOK-123`** for any new/rewritten example (settled in the frontend plan session).
- Do **not** touch `contractTest/.../BackendPactVerificationTest.*` — the provider verifier stays agnostic.
