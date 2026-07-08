# 2026-07-08 09:02:00 UTC — Backend external-systems tests: legacy/latest split (component + narrow-integration), shared stub DSL

🤖 **Picked up by agent** — `Valentina_Desk` at `2026-07-08T09:32:04Z`

> **Provider-side mirror** of the frontend contract-tests refactor (`fad5d866`). Splits the **backend external-systems tests** — the ones that stub the ERP/tax external systems with WireMock — into `legacy` (raw WireMock) / `latest` (shared stub DSL), across **both** the component and the narrow-integration layers. Matches the `PAID-TDD-*-contract-tests-external-systems` article drafts.

## TL;DR

**Why:** The `backend-java` component **and** narrow-integration tests stub the ERP/tax externals with hand-written WireMock, but the article's fluent-DSL "after" (`erpStub.returnsProduct().withSku(...).withUnitPrice(...).execute()`) has **no backing code**.
**End result:** A shared `ErpStubDsl`/`TaxStubDsl`/`ClockStubDsl` (home: `componentTest/support/`) is reused by both the component and narrow-integration `latest/` tests; each split test also has a `legacy/` twin with raw `WireMock.stubFor(...)`. Both layers stub externals via the **same in-process WireMock mechanism**. Behaviour-neutral (except the deliberate, flagged switch of the narrow ERP test off its Docker WireMock container onto in-process WireMock). Pact verifier untouched.

## Scope

- **backend-java ONLY** — .NET (health-check-only component test, no stubs) and TS deferred; frontend-react already done (`fad5d866`).
- Departs from full 3-language symmetry (repo CLAUDE.md) — intentional.

## Decisions settled this session (2026-07-08)

1. **Separate source sets (chosen over a single source set + `@Tag`).** Keep the existing `componentTest` / `integrationTest` / `contractTest` / `test` source sets. They already give opt-in / Docker-light-default-build gating **for free** — no tags, no task changes, no resource merge, and it matches the standing "component/Pact layer stays opt-in via source sets" rule. Nesting is therefore **layer-outer** (`component/{legacy,latest}`, `integration/{legacy,latest}`) — a necessary consequence of the physically-separate source-set roots, not an inconsistency.
2. **Shared stub DSL lives in `componentTest/support/`, reused by integration + contract (Option 1).** The article frames it this way ("reuse the ERP Stub DSL from the Component Tests"), and the repo already shares this way (`contractTest` already depends on `componentTest.output`). `integrationTest` gets the same one-line dependency so its `latest/` test imports the same `com.mycompany.myshop.backend.support.ErpStubDsl`.
3. **Both component AND integration split** — but only the tests that actually stub ERP/tax:
   - **component:** `PlaceOrderComponentTest`, `OrderHistoryComponentTest`.
   - **integration:** `ErpGatewayIntegrationTest`.
   - Not split (no external stubs → relocate nothing, leave as-is): `CouponComponentTest`, `ComponentHarnessSmokeTest`, `OrderRepositoryIntegrationTest`, `OrderControllerIntegrationTest`, `BackendApplicationTests`, `OrderServiceTest`.
4. **Shared stub DSL + driver (naming per the article draft):**
   - `ErpStubDriver` + `ErpStubDsl` — `returnsProduct().withSku(...).withUnitPrice(...).execute()`, `returnsNoProduct().withSku(...).execute()`, `returnsPromotion().withActive(...).withDiscount(...).execute()`.
   - `TaxStubDriver` + `TaxStubDsl` — `returnsRate().withCountry(...).withRate(...).execute()`.
   - `ClockStubDriver` + `ClockStubDsl` — `returnsTime(...)`.
   - Each `*StubDriver` wraps a `com.github.tomakehurst.wiremock.client.WireMock` client (points at whichever in-process `WireMockServer`), so **one driver type serves both layers**. Byte-identical URLs + JSON to today's `AbstractComponentTest` `stub*` helpers (prices/rates passed as `String`).
5. **`legacy` = raw WireMock inlined; `latest` = the DSL.** Behaviour-neutral: same stubbed responses, same assertions.
6. **Same external-stub mechanism across both layers → in-process WireMock (deliberate, flagged).** Switch `ErpGatewayIntegrationTest` off its Testcontainers **WireMock container** onto an in-process `WireMockServer` (as the component tests use). Consistency + drops Docker from that test + one uniform driver. NOT strictly behaviour-neutral (mechanism change), so it's called out. The gateway logic under test is unchanged.
7. **Pact verifier untouched.** `contractTest` source set + `BackendPactVerificationTest` unchanged; keeps using `AbstractComponentTest`'s `stub*` helpers via `@State` handlers, so those helpers stay.
8. **Postgres harness NOT unified here.** Component (singleton static container + `@DynamicPropertySource`) and narrow-integration (`@ServiceConnection`) keep their different Postgres mechanisms — unifying that touches the shared component harness + verifier and isn't behaviour-neutral. Tracked as a **follow-up** (see Steps), aligned with the narrow-integration-taxonomy work.

## Target layout

```
src/test/…/core/services/OrderServiceTest.java                 UNIT — unchanged
src/componentTest/…/backend/
  AbstractComponentTest.java                                   harness — unchanged (keeps stub* helpers)
  support/                                                     ★ NEW shared DSL home
    ErpStubDriver.java ErpStubDsl.java TaxStubDriver.java TaxStubDsl.java ClockStubDriver.java ClockStubDsl.java
  component/
    CouponComponentTest.java  ComponentHarnessSmokeTest.java   unchanged (not split)
    legacy/  PlaceOrderComponentTest.java  OrderHistoryComponentTest.java     raw WireMock
    latest/  PlaceOrderComponentTest.java  OrderHistoryComponentTest.java     shared DSL
src/integrationTest/…/backend/
  AbstractIntegrationTest.java TestcontainersConfiguration.java BackendApplicationTests.java  unchanged
  integration/
    OrderRepositoryIntegrationTest.java OrderControllerIntegrationTest.java   unchanged (not split)
    legacy/  ErpGatewayIntegrationTest.java     raw WireMock (in-process)
    latest/  ErpGatewayIntegrationTest.java     shared DSL (in-process)
src/contractTest/…/backend/contract/BackendPactVerificationTest.java         UNTOUCHED
```

`build.gradle` change: `integrationTest` classpath `+= sourceSets.componentTest.output` (so it sees `support/`). That's it.

## ▶ Next executable step (resume here)

**Step 1 — `build.gradle`:** add `sourceSets.componentTest.output` to `integrationTest`'s compile+runtime classpath. Then **Step 2** build the 6 `support/` DSL+driver classes. Gate: `./gradlew componentTestClasses` compiles.

## Steps

- [ ] **Step 1 — `build.gradle`:** `integrationTest { compileClasspath/runtimeClasspath += sourceSets.main.output + sourceSets.componentTest.output }`. Nothing else changes (task names, tags, verifier, deps all as-is; integrationTest already has its own wiremock dep).
- [ ] **Step 2 — Shared DSL + drivers** in `componentTest/…/support/` (6 classes, decision #4). Byte-identical mappings to `AbstractComponentTest`'s `stub*` helpers.
- [ ] **Step 3 — Split component tests** (`PlaceOrderComponentTest`, `OrderHistoryComponentTest`) into `component/legacy/` (raw `ERP/TAX/CLOCK.stubFor(...)` inlined) + `component/latest/` (DSL via `new ErpStubDsl(new ErpStubDriver(new WireMock("localhost", ERP.port())))`). `git rm` the originals. Leave `AbstractComponentTest` helpers intact.
- [ ] **Step 4 — Split `ErpGatewayIntegrationTest`** into `integration/legacy/` + `integration/latest/`, BOTH switched to an in-process `WireMockServer` (drop the container; decision #6). Happy/404 shapes via DSL in `latest/`; the 500/503 error-injection tests stay raw in both. `git rm` the original.
- [ ] **Step 5 — Verify (compile + style; Docker not available locally):** `./gradlew componentTestClasses integrationTestClasses contractTestClasses checkstyleComponentTest checkstyleIntegrationTest checkstyleContractTest`. Full run relies on CI (see `project_local_testcontainers_blocked`). Verifier behaviour unchanged.
- [ ] **Step 6 — Cross-link the article** (coordinate with substack article-sync): "before" → `legacy/`, "after" → `latest/` + the DSL.
- [ ] **Step 7 — Follow-up plan:** create `plans/…-unify-component-integration-postgres-harness.md` (decision #8 — unify the component singleton-container vs integration `@ServiceConnection` Postgres mechanism; touches the shared harness + verifier, do deliberately).
- [ ] **Step 8 — Commit via `/commit`; delete this plan.**

## Notes

- SKU standard **`BOOK-123`**.
- Behaviour-neutrality is the bar (except the flagged ERP-container→in-process switch): same stubbed responses, same assertions, pact unchanged.
