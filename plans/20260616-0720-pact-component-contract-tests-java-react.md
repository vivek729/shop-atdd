# 2026-06-16 07:20:23 UTC — In-process component & Pact contract tests (backend-java + frontend-react)

## TL;DR

**Why:** The multitier backend-java and frontend-react have no fast, in-process test layer. Today the only cross-tier coverage comes from the system tests, which require a deployed stack (docker compose). We want fast feedback that runs in CI and locally without deploying anything.

**End result:** backend-java has component tests (Spring Boot in-process on a random port, external systems stubbed with WireMock, DB on Testcontainers-Postgres) and Pact provider-verification tests. frontend-react has component tests (Vitest + React Testing Library) and Pact consumer tests that generate the contract — where the happy-path component tests and the consumer contract tests are the *same* tests (rendered against the Pact mock server). The frontend↔backend boundary is verified by the shared Pact contract, passed between tiers as a committed file. **Everything runs in-process — no deployment, no docker compose.**

## Outcomes

What we get out of this — the goals and deliverables:

- **Backend component tests** that boot the Spring app in-process via `@SpringBootTest(webEnvironment=RANDOM_PORT)` — real HTTP over a real socket (highest realism, *not* `MockMvc`) — exercising real controller→service→repository flows, with `ErpGateway` / `TaxGateway` / `ClockGateway` external HTTP stubbed by **in-process WireMock** and the database on a **Testcontainers-managed Postgres** (real dialect, auto-started/torn-down per run — not a hand-started compose stack). `build.gradle` already has `testcontainers:junit-jupiter` + `testcontainers:postgresql` on the test classpath. The same `RANDOM_PORT` harness is reused by the Pact provider test.
- **Frontend component tests** (Vitest + React Testing Library) that render pages/hooks (`useOrders`, `useCoupons`, `order-service`, `coupon-service`). Happy-path + contracted-error tests render **against the Pact mock server** (so they *are* the consumer contract tests — one test, two jobs). Pure client-side states (loading spinner, network-down, client-side validation that never fires a request) use a trivial `vi.fn()` fetch stub. **No MSW** — the contract is the single source of truth for stubs.
- **Pact consumer tests** in frontend-react that assert how it calls the backend and **generate the pact contract** (one pact per consumer↔provider pair) — merged with the happy-path component tests above.
- **Pact provider-verification tests** in backend-java that replay that contract against the in-process provider, with external systems stubbed — failing the build if the backend drifts from the contract.
- **No new infra to run the above:** component + contract tests run from `./gradlew test` and `npm test`, in CI and locally, with **no docker compose and no deployment**.
- **Scope is `system/multitier/` only** — `backend-java` + `frontend-react`. The **monolith is explicitly out of scope** (no changes to `system/monolith/**`). A documented follow-up may mirror the pattern into the *other multitier backends* (`backend-dotnet`, `backend-typescript`) per the CLAUDE.md "check all languages" rule — but still never the monolith.

## ▶ Next executable step (resume here)

Frontend seam (Steps 2–4) is **done and committed** — `system/multitier/frontend-react` now has Vitest + RTL + Pact consumer tests (11 tests green via `npm test`, no Docker). The consumer contract is generated at **`system/multitier/frontend-react/pacts/frontend-react-backend-java.json`** (committed; canonical `<consumer>-<provider>` name — the plan's `frontend-react-backend.json` was illustrative). It holds **7 interactions** (place order 201, browse history 200, view details 200, view details 404, place order 422, browse coupons 200, publish coupon 201), each carrying a `providerState` the backend must set up.

Next executable unit: **Step 5 — backend test harness** in `system/multitier/backend-java`. Add WireMock + Pact-JVM provider (`au.com.dius.pact.provider:junit5spring`) to `build.gradle` (Testcontainers-Postgres already on the test classpath), prove one trivial `@SpringBootTest(webEnvironment=RANDOM_PORT)` test green with no compose. **Pact-sharing note for Step 7:** the contract lives under `frontend-react/pacts/` — point the provider's `@PactFolder` at that relative path (`../frontend-react/pacts`) or copy the file into `backend-java/pacts/`. Resume in a fresh session with `/clear` then `/execute-plan plans/20260616-0720-pact-component-contract-tests-java-react.md`.

## Steps

- [x] Step 1: Settle the open questions — done; see **Decisions** below.
- [ ] Step 5: **Backend test harness** — add WireMock + Pact JVM provider (`au.com.dius.pact.provider:junit5spring`) to `build.gradle` (Testcontainers-Postgres already present); prove one trivial in-process Spring test green with no compose.
- [ ] Step 6: **Backend component tests** — boot the app in-process, stub `ErpGateway`/`TaxGateway`/`ClockGateway` HTTP with WireMock, drive real use-case flows (place order with tax + promotion + clock, order history, coupon publish/browse) end-to-end through the API in-process.
- [ ] Step 7: **Backend Pact provider verification** — point the provider test at the consumer pact (per the sharing decision), define provider states, stub externals with WireMock, fail the build on contract drift.
- [ ] Step 8: **Wire into CI** — make `./gradlew test` / `npm test` run these in the existing build jobs (no compose); update `compile-all.sh` / docs as needed.
- [ ] Step 9: **Document + parity** — note the pattern in the ATDD/architecture docs and capture the follow-up to mirror into the other *multitier* backends (backend-dotnet / backend-typescript). **Monolith stays untouched.**

## Decisions

All resolved — recorded here so the executor doesn't re-litigate:

1. **Pact contract sharing → filesystem, no broker.** Consumer test writes the pact to a committed path (e.g. `pacts/frontend-react-backend.json`); provider reads it via `@PactFolder`. Both tiers are one monorepo checkout, so a broker / `can-i-deploy` / webhooks buy nothing here. (Revisit only if dotnet/typescript later need cross-repo verification.)
2. **Backend component-test seam → `@SpringBootTest(webEnvironment=RANDOM_PORT)`** (real socket, highest realism), *not* `MockMvc`. Same harness serves the Pact provider verification.
3. **Frontend runner → Vitest + React Testing Library** (+ `@testing-library/user-event`). Reuses the existing Vite pipeline; no Jest/babel toolchain.
4. **Frontend stubbing → Pact mock server for happy-path + contracted-error tests (these *are* the consumer contract tests), trivial `vi.fn()` fetch stub for pure client-side states. No MSW.** The contract is the single source of truth for network stubs. (Add MSW later only if many non-contract UI-state tests appear.)
5. **Scope → multitier `backend-java` + `frontend-react` only.** Monolith out (no `system/monolith/**` changes). Other multitier backends (backend-dotnet / backend-typescript) are a documented Step-9 follow-up, not part of this plan.

## Appendix — test sketches

Illustrative only (grounds Steps 4 & 6); exact APIs settle during execution. The real place-order flow chains `ClockGateway` → `ErpGateway` (product price + promotion) → `CouponService` (DB) → `TaxGateway`, all behind `POST /api/orders`, read back via `GET /api/orders/{n}`.

### A. Backend component test — place-order calculation (Step 6)

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)   // in-process, real HTTP, no compose
@Testcontainers                                  // Postgres auto-started/torn-down
class PlaceOrderComponentTest {

    @Container static PostgreSQLContainer<?> db = new PostgreSQLContainer<>("postgres:16");
    static WireMockServer erp   = new WireMockServer(options().dynamicPort());
    static WireMockServer tax   = new WireMockServer(options().dynamicPort());
    static WireMockServer clock = new WireMockServer(options().dynamicPort());

    @BeforeAll static void start() { erp.start(); tax.start(); clock.start(); }
    @AfterAll  static void stop()  { erp.stop();  tax.stop();  clock.stop();  }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", db::getJdbcUrl);
        r.add("spring.datasource.username", db::getUsername);
        r.add("spring.datasource.password", db::getPassword);
        r.add("erp.url",   erp::baseUrl);
        r.add("tax.url",   tax::baseUrl);
        r.add("clock.url", clock::baseUrl);
        r.add("external.system-mode", () -> "stub");
    }

    @Autowired TestRestTemplate http;

    @Test
    void computesTotalsFromPricePromotionAndTax() {
        clock.stubFor(get("/api/time")
            .willReturn(okJson("{\"time\":\"2026-03-10T12:00:00Z\"}")));          // not Dec 31 → allowed
        erp.stubFor(get("/api/products/BOOK-123")
            .willReturn(okJson("{\"price\":10.00}")));                            // unitPrice = 10.00
        erp.stubFor(get("/api/promotion")
            .willReturn(okJson("{\"promotionActive\":false,\"discount\":1.0}"))); // no promo
        tax.stubFor(get("/api/countries/US")
            .willReturn(okJson("{\"taxRate\":0.10}")));                           // 10% tax

        var place = http.postForEntity("/api/orders",
            new PlaceOrderRequest("BOOK-123", 2, "US", null), PlaceOrderResponse.class);
        assertThat(place.getStatusCode()).isEqualTo(CREATED);

        var order = http.getForObject("/api/orders/" + place.getBody().getOrderNumber(),
                                      ViewOrderDetailsResponse.class);
        assertThat(order.getBasePrice()).isEqualByComparingTo("20.00");     // 10.00 × 2
        assertThat(order.getSubtotalPrice()).isEqualByComparingTo("20.00"); // no promo, no coupon
        assertThat(order.getTaxAmount()).isEqualByComparingTo("2.00");      // 20.00 × 0.10
        assertThat(order.getTotalPrice()).isEqualByComparingTo("22.00");    // 20.00 + 2.00
    }
}
```

Variations are just different stubs: promotion active → discounted total; coupon seeded in DB → assert `discountAmount`; clock at `2026-12-31T23:59:00Z` → assert the 422 blackout.

### B. Frontend Pact consumer test (= happy-path component test) (Step 4)

```ts
provider.addInteraction({
  state: 'product BOOK-123 exists and US is taxable',
  uponReceiving: 'a place-order request',
  withRequest:  { method: 'POST', path: '/api/orders',
                  body: { sku: 'BOOK-123', quantity: 2, country: 'US' } },
  willRespondWith: { status: 201, body: { orderNumber: like('ORD-X') } },
});
// render <NewOrder/> with order-service pointed at provider.mockService.baseUrl,
// drive the form, assert the UI shows the order number,
// then provider.verify() writes pacts/frontend-react-backend.json
```

Pure client-state tests (loading / network-down / client-side validation) skip Pact and use a trivial `vi.fn()` fetch stub instead.

### C. Backend Pact provider verification (Step 7)

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)   // same harness as the component test
@Provider("backend-java")
@PactFolder("pacts")                            // reads the consumer-generated contract
class BackendPactVerificationTest {
    @State("product BOOK-123 exists and US is taxable")
    void seed() { /* WireMock stubs for erp/tax/clock establish the provider state */ }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verify(PactVerificationContext ctx) { ctx.verifyInteraction(); }
}
```

The component test (A) asserts **behavior/calculation**; the Pact tests (B, C) assert only the **request/response shape** at the boundary — complementary, not redundant.
