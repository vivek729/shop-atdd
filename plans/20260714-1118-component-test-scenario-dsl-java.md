# 2026-07-14 11:18:00 UTC — Two-layer DSL (use case + scenario) for backend-java componentTest

## TL;DR

**Why:** The backend-java component tests currently sit on a *single* DSL layer — `BackendDsl` plus the `ErpStubDsl` / `TaxStubDsl` / `ClockStubDsl` stub DSLs under `support/`. That layer is imperative and stub-shaped: every test spells out each external stub, every field, every status. The system-test testkit solved this exact problem with **two** layers — a **use case DSL** (`UseCaseDsl` → `MyShopDsl` / `ErpDsl` / `ClockDsl` / `TaxDsl`, one class per use case) and a **scenario DSL** on top (`ScenarioDsl` → `given()` / `when()` / `then()` with defaults auto-filled). Component tests should read the same way, so the same student sees the same shape at both test levels.

**End result:** `system/multitier/backend-java/src/componentTest` latest tests are written as `scenario.given().product().withUnitPrice(...).and().country().withTaxRate(...).when().placeOrder().withQuantity(...).then().shouldSucceed().and().order().hasTotalPrice(...)`, backed by a componentTest-local two-layer DSL in `testSupport/` whose use-case layer is the current `support/*Dsl` classes reshaped into per-actor use case DSLs. Same given/when/then vocabulary as the system-test scenario DSL, same defaults behaviour, same one-scenario-per-test guard — but wired to the in-process SUT and its WireMock stubs instead of a deployed system.

## Outcomes

What we get out of this — the goals and deliverables:

- **A scenario DSL for component tests.** `scenario.given()…when()…then()` available in `AbstractComponentTest`, with the same stage vocabulary as the system-test scenario DSL (`given().product()/.country()/.coupon()/.clock()/.promotion()`, `when().placeOrder()/.viewOrder()/.publishCoupon()/.browseCoupons()/.browseOrderHistory()`, `then().shouldSucceed()/.shouldFail()…and().order().hasTotalPrice(…)`).
- **A use case DSL underneath it.** The existing `BackendDsl` / `ErpStubDsl` / `TaxStubDsl` / `ClockStubDsl` become the use case layer, reshaped to mirror the system-test structure (a root `UseCaseDsl` exposing `myShop()`, `erp()`, `clock()`, `tax()`, with one use case class per operation). Tests may still drop to it directly when a scenario needs surgical control.
- **Defaults, so tests state only what matters.** A componentTest `ScenarioDefaults` fills in unstated product / country / coupon / clock values, so `scenario.when().placeOrder().then().shouldSucceed()` works with no `given()` at all — the same affordance system-test tests enjoy today.
- **Latest component tests rewritten on the scenario DSL.** `component/latest/{PlaceOrder,Coupon,OrderHistory}ComponentTest` express the same scenarios through given/when/then; the `legacy/` twins stay raw (they are the "before" of the teaching pair), and `latest/harness/HarnessSmokeTest` keeps using the low-level health check.
- **Structural parity with system-test, documented.** The package layout under `testSupport/` mirrors `testkit/dsl/{port,core/{usecase,scenario}}` closely enough that a reader can put the two side by side, and the divergences (in-process SUT, WireMock stubs, no channel/UI, `assume()` reduced to `myShop()` only) are called out explicitly rather than left implicit.
- **Self-contained: no dependency on `system-test/java`.** The port interfaces are copied, not shared. `backend-java` stays independently cloneable, and the two DSLs are free to diverge where the test level genuinely differs.
- **Java only.** `backend-java` is the only project with a `componentTest` source set today; .NET and TypeScript are explicitly out of scope until this shape is proven.
- **The build stays green and the layer stays opt-in.** `componentTest` remains off the default build; `./gradlew compileComponentTestJava` (and the componentTest task where runnable) passes.

## ▶ Next executable step (resume here)

All design questions are settled (see **Decisions**). Start at Step 1.

**Step 1 — the use case layer.** Under `system/multitier/backend-java/src/testSupport/java/com/mycompany/myshop/backend/support/`, reshape `BackendDsl`, `ErpStubDsl`, `TaxStubDsl` and `ClockStubDsl` into a use case DSL rooted at a new `UseCaseDsl` exposing `myShop()`, `erp()`, `clock()`, `tax()`, with one use case class per operation (mirroring `system-test/java`'s `testkit/dsl/core/usecase/`). The `*Driver` classes are untouched — this is purely a re-layering above them. Update the call sites in `component/latest/*` and `AbstractComponentTest` mechanically; behaviour must not change, and `./gradlew compileComponentTestJava` must pass.

This unblocks Steps 2–6, which build the scenario DSL on top of that use case layer.

## Steps

- [ ] Step 1: **Use case layer.** Reshape `support/{BackendDsl,ErpStubDsl,TaxStubDsl,ClockStubDsl}` into a use case DSL rooted at a `UseCaseDsl` (`myShop()`, `erp()`, `clock()`, `tax()`), one use case class per operation, mirroring `testkit/dsl/core/usecase/`. Keep the drivers (`*Driver`) exactly as they are — this is a re-layering above them. Latest component tests keep passing against the new surface (mechanical call-site update only).
- [ ] Step 2: **Scenario ports.** Copy the scenario-stage interfaces (`ScenarioDsl`, `AssumeStage`, `GivenStage`, `WhenStage`, `ThenStage`, and the per-entity step ports) from `system-test/java`'s `testkit/dsl/port` into a `port` package under `backend/support`. Copied, not shared — `backend-java` gains no dependency on `system-test/java`. Drop the ports that have no meaning at this level (channels, UI).
- [ ] Step 3: **Given stage + defaults.** Implement `GivenImpl` with `product()`, `country()`, `coupon()`, `clock()`, `promotion()` steps and a componentTest `ScenarioDefaults`, translating each given into use case calls (ERP/Tax/Clock stub programming and direct `couponRepository` seeding). `promotion()` is always executed, defaulting to inactive, as in system-test.
- [ ] Step 4: **When stage.** Implement `WhenImpl` with `placeOrder()`, `viewOrder()`, `publishCoupon()`, `browseCoupons()`, `browseOrderHistory()`, capturing outcome into an `ExecutionResult` (success payload or rejection) rather than asserting inline.
- [ ] Step 5: **Then stage, preserving the rejection split.** Implement `ThenImpl` / `ThenResultImpl` with `shouldSucceed()` / `shouldFail()` and the entity assertions (`order().hasBasePrice(…)`, `.hasTotalPrice(…)`, `.hasStatus(…)`, `.hasAppliedCoupon(…)`, `coupon(code).hasUsedCount(…)`). `shouldFail()` **must keep** the two-shape split that `BackendDsl.Rejection` encodes today — a whole-request failure asserted through its `detail` message vs. a field-scoped failure asserted through `errors[]` — so a test still cannot assert the wrong error shape and silently pass against the generic validation string. The scenario DSL surfaces that split; it does not collapse it.
- [ ] Step 6: **Wire into the base test.** Expose `scenario` on `AbstractComponentTest` (fresh per test), with the one-scenario-per-test guard from `ScenarioDslImpl`. Add `assume().myShop()` — the `/health` liveness probe, and the *only* assume step (see below) — and move `latest/harness/HarnessSmokeTest` onto it so the harness canary reads like the system-test smoke test.
- [ ] Step 7: **Rewrite the latest component tests** (`PlaceOrder`, `Coupon`, `OrderHistory`) on the scenario DSL. Leave the `legacy/` twins raw — they are the "before" half of the teaching pair and must not change.
- [ ] Step 8: **Verify.** Compile (`./gradlew compileComponentTestJava`) and run the componentTest suite — noting that local Testcontainers runs are blocked on this machine, so CI is the gate for the run.
- [ ] Step 9: **Document the parity.** Update the DSL javadoc / `docs/atdd/**` where the four-layer component-test model is described, so the two-layer split and its divergences from system-test are stated, not inferred.

## Decisions

- **Copy the ports, don't share them.** `backend-java` takes no dependency on `system-test/java`. Self-containment beats DRY in a teaching repo, and the two DSLs will legitimately diverge (no channels, no UI, WireMock stubs instead of deployed externals).
- **Java only.** `backend-java` is the only project with a `componentTest` source set. .NET and TypeScript are out of scope for this plan.
- **`assume()` is reduced to `myShop()` alone.** In system-test, `assume()` gates on separately-deployed processes actually being reachable — `myShop()`, `erp()`, `tax()`, `clock()`. At component level the externals are WireMock servers the test itself started and the SUT is an in-process Spring context, so probes for the three stubs could never legitimately fail; shipping them would teach a ceremony with no meaning behind it. `assume().myShop()` survives because it has real content: the HTTP `/health` liveness probe that `HarnessSmokeTest` calls today as `backend.checkHealth()`. Document the divergence in the DSL javadoc so it reads as deliberate.
- **The rejection split is preserved.** `then().shouldFail()` keeps `BackendDsl.Rejection`'s whole-request (`detail`) vs. field-scoped (`errors[]`) distinction rather than collapsing it into one assertion. See Step 5.
- **Package layout mirrors system-test verbatim.** `port/`, `core/usecase/`, `core/scenario/{given,when,then,assume}` under `com.mycompany.myshop.backend.support`, matching `testkit/dsl/`. It is more structure than four use cases strictly need; the point is that a reader can put the two DSLs side by side and see one shape, which is the deliverable.
- **`given().promotion()` is always executed, defaulting to inactive**, exactly as system-test's `GivenImpl` does. A component test that doesn't care about promotions stays silent about them, and the default keeps the ERP stub consistent.
