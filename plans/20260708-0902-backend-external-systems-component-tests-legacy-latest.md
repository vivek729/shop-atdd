# 2026-07-08 09:02:00 UTC — Backend contract/component tests: single-source-set legacy/latest split for the external-systems (ERP/tax) stubs

🤖 **Picked up by agent** — `Valentina_Desk` at `2026-07-08T09:32:04Z`

> **Provider-side mirror** of the frontend contract-tests refactor (committed `fad5d866`, "Restructure frontend component/integration contract tests into legacy/latest with shared test-kit"). Where the frontend split its **consumer** contract tests, this plan splits the **backend external-systems tests** — the ones that stub the ERP/tax external systems with WireMock — into the same `legacy` (raw WireMock) / `latest` (stub DSL) progression, across **both** the component and the narrow-integration layers. Matches the `PAID-TDD-*-contract-tests-external-systems` article drafts.

## TL;DR

**Why:** The `backend-java` component **and** narrow-integration tests stub the ERP/tax external systems with hand-written WireMock, but the article's fluent-DSL "after" snippet (`erpStub.returnsProduct().withSku(...).withUnitPrice(...).execute()`) has **no backing code** in the repo.
**End result:** `backend-java` tests collapse into a **single `test` source set** structured exactly like the frontend — `src/test/.../{legacy,latest}/{component,integration}/` — where `legacy/` is raw `WireMock.stubFor(...)` (article "before") and `latest/` uses a shared `ErpStubDsl`/`TaxStubDsl`/`ClockStubDsl` over swappable WireMock drivers (article "after"), reused by both the component and integration layers. Opt-in/Docker-light default build is preserved via JUnit `@Tag` + filtered Gradle tasks. Behaviour-neutral; the Pact verifier relocates unchanged.

## Scope — backend-java ONLY

- **`backend-dotnet`** — its component test is health-check-only (no ERP/tax WireMock); no before/after to refactor. **Deferred.**
- **`backend-typescript`** — has a genuine before/after but is **deferred** to keep this session focused.
- **`frontend-react`** — consumer-side split already committed (`fad5d866`); nothing to do.

⚠️ Departs from full 3-language symmetry (repo CLAUDE.md) — intentional. Do not "re-symmetrize" .NET/TS back in without their own build-out.

## Decisions settled this session (2026-07-08)

1. **Single source set for the refactored layers (chosen over keeping the opt-in source sets).** Collapse `componentTest` + `integrationTest` + the default `test`/unit into **one `test` source set**, so the backend structure mirrors the frontend and `system-test` (both single-source-set, folder-based progression). This **reverses** the earlier "nesting inverts vs frontend" decision — with one source set we now use the **same progression-outer** layout as the frontend: `{legacy,latest}/{component,integration}/`. **`contractTest` stays its own separate source set** (see decision #6) — the verifier is a different, legacy/latest-agnostic concern and stays isolated + untouched.
2. **Opt-in preserved via JUnit `@Tag`, not source sets.** The three slow layers must stay **off the default `./gradlew build`** (template stays fast + Docker-light — a standing requirement). Tag each slow test `@Tag("component")` / `@Tag("integration")` / `@Tag("contract")`; the default `test` task `excludeTags` all three (→ unit-only); keep the **same-named** `componentTest` / `integrationTest` / `contractTest` tasks as `includeTags` filters so **CI is unchanged**.
3. **Both component AND integration get the split** (chosen: symmetric with the frontend). Only tests that actually stub the ERP/tax externals have a before/after, so only those are split:
   - **component:** `PlaceOrderComponentTest`, `OrderHistoryComponentTest` (stub ERP/tax/clock).
   - **integration:** `ErpGatewayIntegrationTest` (stubs ERP via a Testcontainers WireMock container).
   - Tests with **no** external stubs are **not** split (no story): `CouponComponentTest`, `ComponentHarnessSmokeTest`, `OrderRepositoryIntegrationTest`, `OrderControllerIntegrationTest`, `BackendApplicationTests` — they just relocate into the single source set, tagged, in neutral `component/` / `integration/` folders.
4. **Shared stub DSL + swappable driver (RESOLVED naming, per the article draft):**
   - `ErpStubDriver` (interface) + `ErpStubDsl` — `returnsProduct().withSku(...).withUnitPrice(...).execute()`, `returnsNoProduct().withSku(...).execute()`, `returnsPromotion().withActive(...).withDiscount(...).execute()`.
   - `TaxStubDriver` + `TaxStubDsl` — `returnsRate().withCountry(...).withRate(...).execute()`.
   - `ClockStubDriver` + `ClockStubDsl` — `returnsTime(...)`.
   - **One DSL surface, two driver impls** (the frontend's "swappable driver" pattern): the component layer's driver targets the **in-process `WireMockServer`**; the integration layer's driver targets the **Testcontainers WireMock container** (via a `WireMock` admin client pointed at its host:port). Both emit **byte-identical** mappings — same URLs (`/api/products/{sku}`, `/api/promotion`, `/api/countries/{country}`, `/api/time`) and JSON as today's raw blocks (prices/rates passed as `String` to preserve exact numeric formatting).
5. **`legacy` = raw WireMock inlined; `latest` = the DSL.** `legacy/` tests inline `WireMock.stubFor(...)` directly (article "before"); `latest/` import the shared DSL (article "after"). Behaviour-neutral: same stubbed responses, same assertions.
6. **Pact verifier stays separate and untouched (chosen over relocating it in).** `BackendPactVerificationTest` stays in its own `contractTest` source set + `contractTest` task. To keep it **zero-touch**, the shared harness keeps its current package `com.mycompany.myshop.backend` (it just physically moves into the `test` source set), so the verifier's `import ...AbstractComponentTest` is still valid. The `contractTest` source set is rewired to depend on the `test` source-set output (for the harness) instead of the removed `componentTest` output; `contractTestImplementation.extendsFrom testImplementation` + its own `pact` dep. It keeps using the harness's `stub*` helpers via `@State` handlers, so those helpers survive on the harness.

## Target layout

```
src/test/java/com/mycompany/myshop/backend/                 ← the ONE test source set
  AbstractComponentTest.java   ← harness; KEEPS package ...backend so the verifier import is unchanged
  AbstractIntegrationTest.java
  TestcontainersConfiguration.java
  support/            ← NEW: ErpStubDsl/TaxStubDsl/ClockStubDsl + driver interfaces + WireMockServer/Container impls  (pkg ...backend.support)
  unit/               ← OrderServiceTest (untagged → default build)
  component/          ← CouponComponentTest, ComponentHarnessSmokeTest        @Tag("component")  (no external stubs → not split)
  integration/        ← OrderRepositoryIntegrationTest, OrderControllerIntegrationTest, BackendApplicationTests  @Tag("integration")
  legacy/
    component/        ← PlaceOrder, OrderHistory — raw WireMock.stubFor(...)   @Tag("component")
    integration/      ← ErpGateway — raw WireMock                             @Tag("integration")
  latest/
    component/        ← PlaceOrder, OrderHistory — shared stub DSL            @Tag("component")
    integration/      ← ErpGateway — shared stub DSL                          @Tag("integration")

src/contractTest/java/com/mycompany/myshop/backend/contract/
  BackendPactVerificationTest.java   ← UNTOUCHED; its own contractTest source set + task, depends on `test` output for the harness
```

## Current state (backend-java, before)

- 3 opt-in source sets in `build.gradle` (`componentTest`, `contractTest` [extends componentTest], `integrationTest`) + default `test`; each a separate Gradle task kept off `build`.
- `src/componentTest/.../AbstractComponentTest.java` — Spring random-port + Testcontainers Postgres + in-process WireMock `ERP`/`TAX`/`CLOCK` + `stub*` helpers (shared with the verifier).
- `src/componentTest/.../component/`: PlaceOrder, OrderHistory, Coupon, Smoke.
- `src/contractTest/.../contract/BackendPactVerificationTest.java` (extends AbstractComponentTest).
- `src/integrationTest/.../integration/`: ErpGateway (WireMock container), OrderRepository, OrderController, + AbstractIntegrationTest, BackendApplicationTests, TestcontainersConfiguration.

## ▶ Next executable step (resume here)

**Step 1 — Restructure `build.gradle`: collapse `componentTest`+`integrationTest` into the default `test` source set; keep `contractTest` separate.** Remove the `componentTest` + `integrationTest` source sets; fold `wiremock-standalone` into `testImplementation`. Keep the `contractTest` source set but rewire it: `compileClasspath/runtimeClasspath += sourceSets.test.output` (was `componentTest.output`), `contractTestImplementation.extendsFrom testImplementation` + keep its own `pact junit5spring` dep. Make default `test` `useJUnitPlatform { excludeTags 'component','integration' }` (unit-only). Re-register same-named `componentTest`/`integrationTest` tasks over the `test` source set with `includeTags 'component'` / `'integration'`; leave the `contractTest` task as-is. Gate: `./gradlew testClasses` compiles once files are moved (Step 2). Unblocks everything else.

## Steps

- [ ] **Step 1 — `build.gradle`: collapse component+integration into `test`, keep `contractTest` separate, `@Tag`-filtered tasks.** As in Next-step above. Preserve task names so CI is unchanged; keep the slow layers off default `build`.
- [ ] **Step 2 — Relocate the component + integration + unit tests into `src/test/java/.../` per the target layout**, adding `@Tag(...)` to every slow test. Move the harness, `AbstractIntegrationTest`, `TestcontainersConfiguration` into the `test` source set — harness **keeps package `com.mycompany.myshop.backend`** so the verifier import stays valid (verifier file untouched). New DSL/drivers go in `support/`. No behaviour change yet — pure move + tag.
- [ ] **Step 3 — Build the shared stub DSL + drivers** in `support/`: `ErpStubDsl`/`TaxStubDsl`/`ClockStubDsl` over `ErpStubDriver`/`TaxStubDriver`/`ClockStubDriver` interfaces, with a `WireMockServer` impl (component) and a `WireMock`-admin-client impl (integration container). Byte-identical mappings to the current raw blocks.
- [ ] **Step 4 — Split the external-systems tests into `legacy/` (raw WireMock inlined) + `latest/` (DSL)** for component (PlaceOrder, OrderHistory) and integration (ErpGateway). Keep the harness `stub*` helpers intact (verifier depends on them).
- [ ] **Step 5 — Verify.** `./gradlew testClasses` compiles; `./gradlew test` runs unit-only (no Docker) and `build` stays Docker-light; confirm `componentTest`/`integrationTest`/`contractTest` collect the right tagged tests (`--dry-run` or test-count). Full run relies on CI (local Testcontainers is blocked here — see `project_local_testcontainers_blocked`). No change to the pact or verifier behaviour.
- [ ] **Step 6 — Cross-link the article** (coordinate with the substack article-sync plan): "before" → `legacy/`, "after" → `latest/` + the stub DSL.
- [ ] **Step 7 — Commit via `/commit`; delete this plan.**

## Notes

- SKU standard **`BOOK-123`**.
- CI check: confirm no shop workflow references the removed source-set dirs directly (it should only call the Gradle task names, which are preserved).
- Behaviour-neutrality is the acceptance bar: same stubbed responses, same assertions, pact unchanged.
