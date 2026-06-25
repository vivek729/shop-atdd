package com.mycompany.myshop.backend.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.reset;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mycompany.myshop.backend.core.dtos.external.GetPromotionResponse;
import com.mycompany.myshop.backend.core.dtos.external.ProductDetailsResponse;
import com.mycompany.myshop.backend.core.services.external.ErpGateway;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class ErpGatewayIntegrationTest {

    @Container
    static GenericContainer<?> wireMock =
        new GenericContainer<>(DockerImageName.parse("wiremock/wiremock:3.9.0"))
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/__admin/mappings").forStatusCode(200));

    private ErpGateway erpGateway;

    @BeforeEach
    void setUp() {
        configureFor("localhost", wireMock.getMappedPort(8080));
        reset();

        erpGateway = new ErpGateway();
        ReflectionTestUtils.setField(erpGateway, "erpUrl",
            "http://localhost:" + wireMock.getMappedPort(8080));
    }

    @Test
    void getProductDetailsReturnsDetailsWhenFound() {
        stubFor(get("/api/products/BOOK-123")
            .willReturn(okJson("{\"id\":\"BOOK-123\",\"price\":10.00}")));

        Optional<ProductDetailsResponse> result = erpGateway.getProductDetails("BOOK-123");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo("BOOK-123");
        assertThat(result.get().getPrice()).isEqualByComparingTo("10.00");
    }

    @Test
    void getProductDetailsReturnsEmptyWhenNotFound() {
        stubFor(get("/api/products/UNKNOWN")
            .willReturn(aResponse().withStatus(404)));

        assertThat(erpGateway.getProductDetails("UNKNOWN")).isEmpty();
    }

    @Test
    void getProductDetailsThrowsOnServerError() {
        stubFor(get("/api/products/BAD-SKU")
            .willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));

        assertThatThrownBy(() -> erpGateway.getProductDetails("BAD-SKU"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("500");
    }

    @Test
    void getPromotionDetailsReturnsPromotion() {
        stubFor(get("/api/promotion")
            .willReturn(okJson("{\"promotionActive\":true,\"discount\":0.15}")));

        GetPromotionResponse result = erpGateway.getPromotionDetails();

        assertThat(result.isPromotionActive()).isTrue();
        assertThat(result.getDiscount()).isEqualByComparingTo("0.15");
    }

    @Test
    void getPromotionDetailsThrowsOnServerError() {
        stubFor(get("/api/promotion")
            .willReturn(aResponse().withStatus(503).withBody("Service Unavailable")));

        assertThatThrownBy(() -> erpGateway.getPromotionDetails())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("503");
    }
}
