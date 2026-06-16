package com.mycompany.myshop.backend;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.mycompany.myshop.backend.core.repositories.CouponRepository;
import com.mycompany.myshop.backend.core.repositories.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * In-process component-test harness: boots the Spring app on a random port (real HTTP over a real
 * socket), backs it with a Testcontainers-managed Postgres (real dialect, Flyway-migrated) and
 * stubs the ERP / Tax / Clock external HTTP systems with in-process WireMock. No docker compose,
 * no deployment. The same harness is reused by the Pact provider-verification test.
 *
 * <p>Postgres and WireMock use the singleton-container pattern (started once in a static
 * initializer, never stopped by JUnit) rather than {@code @Container}/{@code @Testcontainers}: the
 * Spring context is cached and shared across every subclass, so a per-class container that JUnit
 * stopped after the first class would leave the reused context pointing at a dead port.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractComponentTest {

    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("app")
            .withUsername("app")
            .withPassword("app");

    protected static final WireMockServer ERP = new WireMockServer(options().dynamicPort());
    protected static final WireMockServer TAX = new WireMockServer(options().dynamicPort());
    protected static final WireMockServer CLOCK = new WireMockServer(options().dynamicPort());

    static {
        POSTGRES.start();
        ERP.start();
        TAX.start();
        CLOCK.start();
    }

    @DynamicPropertySource
    static void externalSystemProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Drive the ClockGateway through HTTP (rather than Instant.now()) so time is controllable.
        registry.add("external.system-mode", () -> "stub");
        registry.add("erp.url", ERP::baseUrl);
        registry.add("tax.url", TAX::baseUrl);
        registry.add("clock.url", CLOCK::baseUrl);
    }

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected OrderRepository orderRepository;

    @Autowired
    protected CouponRepository couponRepository;

    @BeforeEach
    void resetComponentState() {
        ERP.resetAll();
        TAX.resetAll();
        CLOCK.resetAll();
        orderRepository.deleteAll();
        couponRepository.deleteAll();
    }

    // --- External-system stub helpers (shared by component tests and provider states) ---

    protected static void stubClock(String isoInstant) {
        CLOCK.stubFor(get(urlEqualTo("/api/time"))
            .willReturn(okJson("{\"time\":\"" + isoInstant + "\"}")));
    }

    protected static void stubProduct(String sku, String price) {
        ERP.stubFor(get(urlEqualTo("/api/products/" + sku))
            .willReturn(okJson("{\"id\":\"" + sku + "\",\"price\":" + price + "}")));
    }

    protected static void stubProductMissing(String sku) {
        ERP.stubFor(get(urlEqualTo("/api/products/" + sku))
            .willReturn(aResponse().withStatus(404)));
    }

    protected static void stubPromotion(boolean active, String discount) {
        ERP.stubFor(get(urlEqualTo("/api/promotion"))
            .willReturn(okJson("{\"promotionActive\":" + active + ",\"discount\":" + discount + "}")));
    }

    protected static void stubTax(String country, String rate) {
        TAX.stubFor(get(urlEqualTo("/api/countries/" + country))
            .willReturn(okJson("{\"id\":\"" + country + "\",\"countryName\":\"" + country
                + "\",\"taxRate\":" + rate + "}")));
    }
}
