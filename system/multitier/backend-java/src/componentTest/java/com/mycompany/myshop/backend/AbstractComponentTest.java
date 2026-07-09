package com.mycompany.myshop.backend;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.mycompany.myshop.backend.core.repositories.CouponRepository;
import com.mycompany.myshop.backend.core.repositories.OrderRepository;
import com.mycompany.myshop.backend.support.BackendDriver;
import com.mycompany.myshop.backend.support.BackendDsl;
import com.mycompany.myshop.backend.support.ClockStubDriver;
import com.mycompany.myshop.backend.support.ClockStubDsl;
import com.mycompany.myshop.backend.support.ErpStubDriver;
import com.mycompany.myshop.backend.support.ErpStubDsl;
import com.mycompany.myshop.backend.support.TaxStubDriver;
import com.mycompany.myshop.backend.support.TaxStubDsl;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * In-process component-test harness: boots the Spring app on a random port (real HTTP over a real
 * socket), backs it with a Testcontainers-managed Postgres (real dialect, Flyway-migrated) and
 * stubs the ERP / Tax / Clock external HTTP systems with in-process WireMock. No docker compose,
 * no deployment. The same harness is reused by the Pact provider-verification test.
 *
 * <p>Postgres is supplied by a {@code @ServiceConnection} bean ({@link TestcontainersConfiguration},
 * shared with the narrow-integration layer): the container's lifecycle is tied to the Spring
 * context, which is cached and shared across every subclass and the Pact verifier, so it stays up
 * for as long as any test can reach it. WireMock uses the singleton-container pattern instead
 * (started once in a static initializer, never stopped by JUnit) rather than {@code @Container}/
 * {@code @Testcontainers}: it is not a Spring bean, so a per-class server that JUnit stopped after
 * the first class would leave the reused, cached context pointing at a dead port.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class AbstractComponentTest {

    protected static final WireMockServer ERP = new WireMockServer(options().dynamicPort());
    protected static final WireMockServer TAX = new WireMockServer(options().dynamicPort());
    protected static final WireMockServer CLOCK = new WireMockServer(options().dynamicPort());

    static {
        ERP.start();
        TAX.start();
        CLOCK.start();
    }

    @DynamicPropertySource
    static void externalSystemProperties(DynamicPropertyRegistry registry) {
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

    /**
     * Fluent DSL for driving the system under test (place + view order), symmetric with the ERP /
     * Tax / Clock stub DSLs. Wired here — rather than as a field initializer like the stub DSLs —
     * because {@code restTemplate} is an autowired instance field not yet populated at field-init.
     */
    protected BackendDsl backend;

    /**
     * Fluent DSLs for driving the ERP / Tax / Clock external-system stubs, symmetric with the
     * {@code backend} DSL. Field-initialized (unlike {@code backend}) because the {@code ERP/TAX/
     * CLOCK} WireMock servers are static and started in the class's static initializer, so their
     * ports are available at instance-field-init time. Registered mappings are cleared per test by
     * {@code resetComponentState()}'s {@code resetAll()}; the client instances survive the reset.
     */
    protected final ErpStubDsl erpStub =
        new ErpStubDsl(new ErpStubDriver(new WireMock("localhost", ERP.port())));
    protected final TaxStubDsl taxStub =
        new TaxStubDsl(new TaxStubDriver(new WireMock("localhost", TAX.port())));
    protected final ClockStubDsl clockStub =
        new ClockStubDsl(new ClockStubDriver(new WireMock("localhost", CLOCK.port())));

    @BeforeEach
    void resetComponentState() {
        backend = new BackendDsl(new BackendDriver(restTemplate));
        ERP.resetAll();
        TAX.resetAll();
        CLOCK.resetAll();
        orderRepository.deleteAll();
        couponRepository.deleteAll();
    }
}
