# 2026-06-16 07:34:45 UTC — `backend-clean-java`: a Clean Architecture exemplar to contrast with the existing backend

## TL;DR

**Why:** The academy wants to *illustrate the difference between a Big Ball of Mud and Clean Architecture in real, runnable code* — not just in a diagram. The existing `system/multitier/backend-java` is a pragmatic Spring-layered app whose `core` already leaks the framework (`OrderRepository extends JpaRepository`, JPA-annotated entities, a 240-line `OrderService` that mixes orchestration + queries + DTO mapping). It is the realistic "messy" pole. We add **one** new project that is the textbook opposite, structured strictly by the Dependency Rule, passing the **same** acceptance/contract/e2e suite.

**End result:** A new Gradle project `system/multitier/backend-clean-java` exposing the **identical** MyShop REST API against the **same** Postgres + ERP/Clock/Tax externals, wired so `gh optivem system start` + the existing Java system tests run green against it. Its `domain` and `application` packages have **zero** Spring/JPA imports, enforced by an **ArchUnit** test that fails the build on any inward framework dependency. The contrast — same external behavior, same green tests, opposite internal structure, *only one side mechanically enforceable* — is the teaching artifact.

## Outcomes

What we get out of this — the goals and deliverables:

- **One new project** `system/multitier/backend-clean-java` (own `VERSION`, own Gradle build), behaviourally identical to `backend-java`: same endpoints (`POST /api/orders`, `GET /api/orders/{n}`, order history, coupon publish/browse, `/health`), same request/response DTOs on the wire, same Postgres schema (`system/db/migrations`), same ERP/Clock/Tax HTTP contracts.
- **Clean Architecture internals** organised by layer with the Dependency Rule pointing strictly inward:
  - `domain` — `Order`, `Coupon`, value objects, and domain policies (the Dec-31 place/cancel blackout rules live here, not in a controller `if`). No Spring, no JPA, no Jackson.
  - `application` — use-case interactors (`PlaceOrderUseCase`, `CancelOrderUseCase`, `DeliverOrderUseCase`, `BrowseOrderHistoryUseCase`, `ViewOrderDetailsUseCase`, coupon use cases) depending only on **ports**: `OrderRepositoryPort`, `CouponRepositoryPort`, `ErpPort`, `ClockPort`, `TaxPort`. No framework imports.
  - `adapter/in/web` — controllers + request/response models + exception mapping (the only place that knows Spring MVC).
  - `adapter/out/persistence` — JPA entities (`OrderJpaEntity`), Spring Data repos, mappers, and `*RepositoryAdapter` classes implementing the repository ports.
  - `adapter/out/erp` · `/clock` · `/tax` — HTTP clients implementing the external ports.
  - `config` — Spring `@Configuration` wiring ports to adapters (the composition root).
- **An ArchUnit test** (`ArchitectureRulesTest`) that fails the build if anything in `domain`/`application` imports `org.springframework..`, `jakarta.persistence..`, `com.fasterxml.jackson..`, or any `adapter..` package — making the Dependency Rule **verifiable**, which the messy backend's structure inherently cannot be. This test *is* the punchline.
- **Same system-test suite green** against the new project: a new `gh-optivem-multitier-java-clean.yaml` config + a docker compose/`systems.yaml` entry stand it up; `gh optivem test run` (the existing `system-test/java`) passes unmodified, proving behaviour parity.
- **Docs**: a short `docs/design/multitier/architecture-clean.md` (or a section in the existing architecture doc) explaining the contrast, linked from the README — markdown only, **no GitHub Pages** (repo rule).
- **Scope is additive and Java-only**: the existing `backend-java` is **not modified**; `.NET`/`TypeScript`/monolith are out of scope (a documented follow-up may mirror the exemplar later).

## ▶ Next executable step (resume here)

Design is settled (see **Decisions** below). First executable unit — **Step 2**: scaffold the empty `backend-clean-java` Gradle project (copy build wiring + `VERSION` from `backend-java`, empty layered `src`, placeholder `BackendApplication` + `/health`, `./gradlew build` green). Resume with `/execute-plan` on this file.

## Steps

- [x] Step 1: **Settle the open questions** — done; see **Decisions** below.
- [ ] Step 2: **Scaffold `backend-clean-java`** — new Gradle project copied from `backend-java`'s build/`VERSION`/`scripts`, empty layered source tree, placeholder `BackendApplication` + `/health`, `./gradlew build` green. No business logic yet.
- [ ] Step 3: **Domain layer** — port `Order`, `Coupon`, `OrderStatus` into a framework-free `domain` package; move the Dec-31 place/cancel blackout and status-transition rules into domain policies/methods. Pure POJOs, unit-tested with no Spring context.
- [ ] Step 4: **Application layer + ports** — define `*Port` interfaces and the use-case interactors orchestrating them (mirroring today's `OrderService`/`CouponService` behaviour but depending only on ports). Unit-test each use case with hand-written fakes (no DB, no HTTP).
- [ ] Step 5: **Persistence adapter** — JPA entities + Spring Data repos + mappers + `*RepositoryAdapter implements *RepositoryPort`. Same tables/migrations as `backend-java`.
- [ ] Step 6: **External adapters** — `ErpPort`/`ClockPort`/`TaxPort` HTTP-client implementations (reuse the existing gateway request/response shapes so the simulators/stubs work unchanged).
- [ ] Step 7: **Web adapter + composition root** — controllers (same routes/DTOs/status codes as `backend-java`), `GlobalExceptionHandler` equivalent, and `@Configuration` wiring ports→adapters.
- [ ] Step 8: **ArchUnit enforcement** — add `ArchitectureRulesTest` asserting the Dependency Rule; confirm it goes red if a Spring import is added to `application`, green otherwise.
- [ ] Step 9: **System-test wiring** — add `gh-optivem-multitier-java-clean.yaml`, a docker compose + `systems.yaml` label for the clean backend; run `gh optivem system start` + `gh optivem test run` (existing `system-test/java`) green. (Ask before running locally — see memory.)
- [ ] Step 10: **CI + compile-all** — add `backend-clean-java` to `compile-all.sh` and any per-project CI matrix so it builds alongside the others.
- [ ] Step 11: **Docs** — write the contrast doc (BBoM vs Clean, with the ArchUnit point), link from README via a relative path. No Pages.

## Decisions

All resolved — recorded here so the executor doesn't re-litigate:

1. **Framing → "realistic framework-coupled layering → Clean Architecture" (honest labelling).** The existing `backend-java` is pragmatic layered with framework leakage (`core` imports `JpaRepository`, JPA-annotated entities, a leaky `OrderService`), **not** a literal tangle. We do **not** mutate it to manufacture mud (that would be out of scope and risk the shared system tests). Instead the docs present a *spectrum* — "Big Ball of Mud" as the described endpoint, `backend-java` as the slippery realistic middle that slides toward it, `backend-clean-java` as the enforced-clean pole. The `OrderRepository extends JpaRepository` + 240-line `OrderService` are the concrete talking points.
2. **Project name → `backend-clean-java`.** Language stays at the end (matching `backend-java` / `backend-dotnet` / `backend-typescript`), with the `clean` variant qualifier in the middle. Config keys, sonar-project ids (`optivem_shop-multitier-backend-clean-java`), and container names follow this form.
3. **ArchUnit → yes, add it** (`com.tngtech.archunit:archunit-junit5`, test scope). It is the mechanism that makes the Dependency Rule *enforced* rather than aspirational — the core of the exemplar's value (Sketch C).
4. **Code sharing → copy, don't share a module.** The clean project copies the request/response DTO shapes (the wire contract and migration schema are shared by contract anyway). Self-contained exemplar; the academy favours readable standalone projects over shared modules.
5. **System-test parity depth → full Java suite once in CI to prove parity; `--sample` for routine local checks.**

## Appendix — structure sketches

Illustrative; exact APIs settle during execution.

### A. Package layout (`backend-clean-java`)

```
com.mycompany.myshop.backend
├── domain
│   ├── order/        Order, OrderStatus, OrderPlacementPolicy (Dec-31 rule), Money
│   └── coupon/       Coupon, CouponPolicy
├── application
│   ├── port/in/      PlaceOrder, CancelOrder, BrowseOrderHistory, ...   (use-case interfaces)
│   ├── port/out/     OrderRepositoryPort, ErpPort, ClockPort, TaxPort, CouponRepositoryPort
│   └── usecase/      PlaceOrderUseCase, CancelOrderUseCase, ...          (interactors; NO framework imports)
├── adapter
│   ├── in/web/       OrderController, CouponController, request/response models, ExceptionHandler
│   └── out/
│       ├── persistence/  OrderJpaEntity, OrderJpaRepository, OrderRepositoryAdapter, mappers
│       ├── erp/          ErpHttpClient implements ErpPort
│       ├── clock/        ClockHttpClient implements ClockPort
│       └── tax/          TaxHttpClient implements TaxPort
└── config/           BeanConfig (composition root), Application
```

### B. The interactor depends only on ports (the inward-arrow contract)

```java
// application/usecase/PlaceOrderUseCase.java — zero Spring/JPA imports
public class PlaceOrderUseCase implements PlaceOrder {
    private final OrderRepositoryPort orders;
    private final ErpPort erp;
    private final ClockPort clock;
    private final TaxPort tax;
    private final CouponRepositoryPort coupons;
    // ctor injection of ports only

    public PlaceOrderResult handle(PlaceOrderCommand cmd) {
        var now = clock.now();
        OrderPlacementPolicy.assertPlaceable(now);          // domain rule, not a controller if
        var price = erp.productPrice(cmd.sku());            // port, not RestTemplate
        // ... pricing via domain Money, persist via orders.save(...) ...
    }
}
```

### C. The punchline — ArchUnit makes the rule enforceable

```java
@AnalyzeClasses(packages = "com.mycompany.myshop.backend")
class ArchitectureRulesTest {

    @ArchTest
    static final ArchRule domain_and_application_are_framework_free =
        noClasses().that().resideInAnyPackage("..domain..", "..application..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta.persistence..",
                                "com.fasterxml.jackson..", "..adapter..");

    @ArchTest
    static final ArchRule dependencies_point_inward =
        layeredArchitecture().consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy("..domain..")
            .layer("Application").definedBy("..application..")
            .layer("Adapters").definedBy("..adapter..")
            .whereLayer("Adapters").mayNotBeAccessedByAnyLayer()
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Adapters");
}
```

The messy `backend-java` *cannot* host this test (its `core` imports `JpaRepository`) — that asymmetry is exactly what the academy is illustrating: in Clean Architecture the boundary is mechanically checkable; in the mud it is only a hope.
