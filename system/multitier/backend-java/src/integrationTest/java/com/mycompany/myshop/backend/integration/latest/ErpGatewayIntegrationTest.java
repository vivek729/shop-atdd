package com.mycompany.myshop.backend.integration.latest;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.mycompany.myshop.backend.core.dtos.external.GetPromotionResponse;
import com.mycompany.myshop.backend.core.dtos.external.ProductDetailsResponse;
import com.mycompany.myshop.backend.core.services.external.ErpGateway;
import com.mycompany.myshop.backend.support.ErpStubDriver;
import com.mycompany.myshop.backend.support.ErpStubDsl;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * "After" of the external-systems contract-tests refactor at the narrow-integration layer: identical
 * scenarios to the {@code legacy/} twin, but the ERP happy/404 stubs are declared through the shared
 * fluent DSL under {@code support/} — the same {@link ErpStubDsl} the component {@code latest/} tests
 * reuse. Uses the same in-process {@link WireMockServer} mechanism (no Docker). The 500/503
 * error-injection cases have no DSL vocabulary and stay raw, matching the {@code legacy/} twin.
 */
class ErpGatewayIntegrationTest {

    static final WireMockServer WIRE_MOCK = new WireMockServer(options().dynamicPort());

    private ErpStubDsl erpStub;
    private ErpGateway erpGateway;

    @BeforeAll
    static void startWireMock() {
        WIRE_MOCK.start();
    }

    @AfterAll
    static void stopWireMock() {
        WIRE_MOCK.stop();
    }

    @BeforeEach
    void setUp() {
        WIRE_MOCK.resetAll();

        erpStub = new ErpStubDsl(new ErpStubDriver(new WireMock("localhost", WIRE_MOCK.port())));

        erpGateway = new ErpGateway();
        ReflectionTestUtils.setField(erpGateway, "erpUrl", WIRE_MOCK.baseUrl());
    }

    @Test
    void getProductDetailsReturnsDetailsWhenFound() {
        erpStub.returnsProduct().withSku("BOOK-123").withUnitPrice("10.00").execute();

        Optional<ProductDetailsResponse> result = erpGateway.getProductDetails("BOOK-123");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo("BOOK-123");
        assertThat(result.get().getPrice()).isEqualByComparingTo("10.00");
    }

    @Test
    void getProductDetailsReturnsEmptyWhenNotFound() {
        erpStub.returnsNoProduct().withSku("UNKNOWN").execute();

        assertThat(erpGateway.getProductDetails("UNKNOWN")).isEmpty();
    }

    @Test
    void getProductDetailsThrowsOnServerError() {
        WIRE_MOCK.stubFor(get("/api/products/BAD-SKU")
            .willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));

        assertThatThrownBy(() -> erpGateway.getProductDetails("BAD-SKU"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("500");
    }

    @Test
    void getPromotionDetailsReturnsPromotion() {
        erpStub.returnsPromotion().withActive(true).withDiscount("0.15").execute();

        GetPromotionResponse result = erpGateway.getPromotionDetails();

        assertThat(result.isPromotionActive()).isTrue();
        assertThat(result.getDiscount()).isEqualByComparingTo("0.15");
    }

    @Test
    void getPromotionDetailsThrowsOnServerError() {
        WIRE_MOCK.stubFor(get("/api/promotion")
            .willReturn(aResponse().withStatus(503).withBody("Service Unavailable")));

        assertThatThrownBy(() -> erpGateway.getPromotionDetails())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("503");
    }
}
