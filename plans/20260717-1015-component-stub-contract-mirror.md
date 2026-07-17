# Component stub-contract tests — mirror system-test (PASS 1)

**Status:** Ready to implement.
**Scope:** Add three POSITIVE stub-contract tests to the multitier backend-java **component** layer,
one per external system (ERP product, Tax, Clock), using the same scenario-DSL surface system-test's
contract tests use — and **nothing beyond** what system-test already has. Everything past this
(negative cases, promotion, interaction verification) is in the deferred follow-up:
`plans/deferred/20260717-1015-component-stub-contract-beyond.md`.

## Why

The component layer stubs ERP/Tax/Clock with **in-process WireMock** (`ErpStubDriver`,
`TaxStubDriver`, `ClockStubDriver`), whose JSON is **hand-written inline** and independent from
system-test's stub. Verified during design:

| | shape |
|---|---|
| component `ErpStubDriver` | `{"id","price"}` (2 fields, inline string) |
| system-test `ExtProductDetailsResponse` | `id,title,description,price,category,brand` (6 fields, typed DTO) |

They share no source, so system-test's contract tests (`ErpStubContractTest` etc.) do **not**
transitively prove the component WireMock stub is consumable by the SUT. Nothing today pins that:
the SUT's `ProductDetailsResponse` is `id`+`price` with `@JsonIgnoreProperties(ignoreUnknown=true)`,
so a field-name drift in the component stub (`price`→`cost`) silently yields null and place-order
tests can still pass. These tests close that gap.

## The one design decision that keeps it honest

System-test's `then().product()/clock()/country()` read the external through a **test-side client**,
which is meaningful only because system-test also runs the same test in **REAL** mode. The component
layer has **no REAL mode**, so a test-side read of the WireMock stub would be a tautology (it would
re-assert the value the test just planted).

**Therefore the component `then().<external>()` reads through the SUT's PRODUCTION gateway**
(`ErpGateway` / `TaxGateway` / `ClockGateway`), so the stub's bytes travel through the SUT's real
HTTP call + real Jackson parse. That is what makes the assertion able to fail on a real drift.

Consequence — **exact assertions, not REAL-safe loose ones.** Because the stub is deterministic and
there is no REAL twin forcing looseness, the component tests assert exact values using
system-test's existing DSL methods:

| external | system-test contract asserts (REAL-safe) | component asserts (exact, same DSL method) |
|---|---|---|
| Tax | `hasTaxRateIsPositive()` | `hasTaxRate(0.09)` |
| Clock | `hasTime()` (no-arg) | `hasTime("2026-01-15T10:30:00Z")` |
| ERP | `hasSku(...).hasPrice(...)` | `hasSku("BOOK-123").hasPrice(12.0)` (already exact) |

`hasTaxRate(double)` and `hasTime(String)` both already exist in system-test's `ThenCountry`/
`ThenClock`, so this stays inside "only DSL methods system-test has." No new assertion verbs
(no `doesNotExist` — that's deferred).

## End result — the three tests

Location: `system/multitier/backend-java/src/componentTest/java/com/mycompany/myshop/backend/component/latest/contract/`

```java
// ClockStubContractComponentTest
scenario.given().clock().withTime("2026-01-15T10:30:00Z")
        .then().clock().hasTime("2026-01-15T10:30:00Z");

// TaxStubContractComponentTest
scenario.given().country().withCode("US").withTaxRate(0.09)
        .then().country("US").hasTaxRate(0.09);

// ErpStubContractComponentTest
scenario.given().product().withSku("BOOK-123").withUnitPrice(12.0)
        .then().product("BOOK-123").hasSku("BOOK-123").hasPrice(12.0);
```

All three: STUB-only (no REAL twin), positive-only, deterministic-exact.

## Steps

- [ ] Step 1: **SUT readers** — add three thin wrappers in `testSupport/.../support/` (next to the
  stub drivers): `SutErpReader(ErpGateway)` → `Optional<ProductDetailsResponse> readProduct(sku)`;
  `SutTaxReader(TaxGateway)` → `Optional<TaxDetailsResponse> readCountry(code)`;
  `SutClockReader(ClockGateway)` → `Instant readTime()`. Each just delegates to the production
  gateway method (`getProductDetails` / `getTaxDetails` / `getCurrentTime`).

- [ ] Step 2: **Wire readers into `UseCaseDsl`** — extend the constructor with the three readers and
  add accessors `sutErp()`, `sutTax()`, `sutClock()`. **Enumerate every `new UseCaseDsl(` call site**
  (component `AbstractComponentTest` + any narrow-integration harness that reuses it) and update each.
  In `AbstractComponentTest.resetComponentState()`, autowire `ErpGateway`/`TaxGateway`/`ClockGateway`
  (`@Service` beans) and pass `new Sut*Reader(...)` in.

- [ ] Step 3: **`ThenProduct` step** — port interface `then/steps/ThenProduct` (`hasSku`, `hasPrice`
  only — the subset the test uses) + `ThenProductImpl` that calls `app.sutErp().readProduct(sku)` in
  its constructor and asserts `getId()`/`getPrice()` (AssertJ, `isEqualByComparingTo` for price).

- [ ] Step 4: **`ThenCountry` step** — port `then/steps/ThenCountry` (`hasTaxRate(double)`; add
  `hasCountry(String)` only if used) + `ThenCountryImpl` reading `app.sutTax().readCountry(code)`,
  asserting `TaxDetailsResponse.getTaxRate()`.

- [ ] Step 5: **`ThenClock` step** — port `then/steps/ThenClock` (`hasTime(String)`) + `ThenClockImpl`
  reading `app.sutClock().readTime()` and comparing to `Instant.parse(expected)`.

- [ ] Step 6: **Expose the steps on the chain** — add `product(String sku)`, `clock()`,
  `country(String code)` to BOTH `port/then/ThenStage` + `core/.../ThenImpl` (the `given().then()`
  branch) AND `port/then/steps/base/ThenStep` + `core/.../BaseThenStep` (the post-action chain), each
  `new`-ing the impl from Step 3–5.

- [ ] Step 7: **Update the `ThenStage` javadoc** — it currently says clock/product/country are
  "dropped here … reading them back would assert the stub." Rewrite to: they are back, but backed by
  the **SUT's production gateway** (not the stub client), so they read the SUT's *view* of the
  external — honest, and used by the stub-contract tests. Keep the note that a test-side read WOULD
  be tautological (that's why the backing differs from system-test).

- [ ] Step 8: **Write the three test classes** (see End result) under `component/latest/contract/`.

- [ ] Step 9: **Compile + run** — `./gradlew build` in `system/multitier/backend-java` (or targeted
  `componentTest`). Confirm all three pass. Deliberately break one stub field (e.g. `price`→`cost` in
  `ErpStubDriver`) and confirm `ErpStubContractComponentTest` fails — proves the test is honest, not a
  tautology — then revert.

- [ ] Step 10: **Remove this plan file** per plan-processing rules once all steps are done; delete the
  `plans/` dir only if it also empties (the deferred follow-up keeps living under `plans/deferred/`).

## Notes / risks

- **`UseCaseDsl` constructor fan-out** (Step 2) is the main risk — a missed call site won't compile.
  Grep `new UseCaseDsl(` across the module before editing.
- **White-box coupling:** `then().<external>()` reaches SUT `@Service` beans directly, unlike every
  other component `then()` step (which goes through the SUT HTTP API). This is unavoidable — the SUT
  exposes no endpoint that surfaces a raw external read — and is confined to the three `Sut*Reader`
  classes. Documented, accepted (design discussion 2026-07-17).
- **Cross-language parity:** this pass is Java-only. .NET and TypeScript multitier backends have the
  same component/stub structure; mirroring there is noted in the deferred follow-up.
- **Reference implementation sketch** is inlined in the appendix below (ERP as the exemplar; clock
  and tax follow the same shape with their own gateway + DTO).

## Appendix — reference implementation sketch (ERP exemplar)

Clock and Tax are structurally identical: swap `ErpGateway.getProductDetails` →
`TaxGateway.getTaxDetails` / `ClockGateway.getCurrentTime`, and the DTO/assertions accordingly.

### SUT reader (Step 1) — `testSupport/.../support/SutErpReader.java`

```java
public class SutErpReader {
    private final ErpGateway gateway;
    public SutErpReader(ErpGateway gateway) { this.gateway = gateway; }

    /** The product AS THE SUT SEES IT: real HTTP to the stub URL + real ProductDetailsResponse parse. */
    public Optional<ProductDetailsResponse> readProduct(String sku) {
        return gateway.getProductDetails(sku);
    }
}
```

### Wiring (Step 2) — `UseCaseDsl` + `AbstractComponentTest`

```java
// UseCaseDsl: extend ctor with the readers, add accessors
public UseCaseDsl(BackendDriver backendDriver, ObjectMapper objectMapper,
        ErpStubDriver erpStubDriver, TaxStubDriver taxStubDriver, ClockStubDriver clockStubDriver,
        SutErpReader sutErp, SutTaxReader sutTax, SutClockReader sutClock) { ... }
public SutErpReader sutErp() { return sutErp; }   // + sutTax(), sutClock()

// AbstractComponentTest.resetComponentState(): autowire the @Service gateways, pass readers in
@Autowired ErpGateway erpGateway;   // + TaxGateway, ClockGateway
app = new UseCaseDsl(
    new BackendDriver(restTemplate), objectMapper,
    new ErpStubDriver(new WireMock("localhost", ERP.port())),   // WRITES the stub (given)
    new TaxStubDriver(...), new ClockStubDriver(...),
    new SutErpReader(erpGateway),                                // READS via the SUT (then)
    new SutTaxReader(taxGateway), new SutClockReader(clockGateway));
```

### Then step (Steps 3) — port + impl

```java
// port/then/steps/ThenProduct.java
public interface ThenProduct extends ThenStep<ThenProduct> {
    ThenProduct hasSku(String expectedSku);
    ThenProduct hasPrice(double expectedPrice);
}

// core/scenario/then/steps/ThenProductImpl.java — the honest part: reads via the SUT adapter
public class ThenProductImpl<R, V extends ResponseVerification<R>> extends BaseThenStep<R, V>
        implements ThenProduct {
    private final Optional<ProductDetailsResponse> product;
    private final String sku;

    public ThenProductImpl(UseCaseDsl app, ExecutionResultContext ctx, String sku, V v) {
        super(app, ctx, v);
        this.sku = sku;
        this.product = app.sutErp().readProduct(sku);   // SUT gateway, NOT the stub client
    }
    @Override public ThenProductImpl<R, V> hasSku(String expected) {
        assertThat(product).as("product %s parsed by SUT", sku).isPresent();
        assertThat(product.get().getId()).isEqualTo(expected);        // catches {"id"}->{"productId"} drift
        return this;
    }
    @Override public ThenProductImpl<R, V> hasPrice(double expected) {
        assertThat(product).isPresent();
        assertThat(product.get().getPrice()).isEqualByComparingTo(BigDecimal.valueOf(expected));
        return this;
    }
    @Override public ThenProductImpl<R, V> and() { return this; }
}
```

### Expose on the chain (Step 6) — one line in each of the four files

```java
// port/then/ThenStage.java  and  port/then/steps/base/ThenStep.java
    ThenProduct product(String sku);      // + ThenClock clock();  ThenCountry country(String code);
// core/.../ThenImpl.java  and  core/.../BaseThenStep.java  ->  new ThenProductImpl<>(app, ctx/empty, sku, v)
```

### Finished test (Step 8)

```java
class ErpStubContractComponentTest extends AbstractComponentTest {
    @Test
    void stubProductIsConsumableBySut() {
        scenario
            .given().product().withSku("BOOK-123").withUnitPrice(12.0)
            .then().product("BOOK-123").hasSku("BOOK-123").hasPrice(12.0);
    }
}
```

Note: a non-DSL variant (autowire `ErpGateway` directly into the test) was considered and rejected in
favour of the DSL surface above, to match system-test's `given().product()…then().product()` shape.
