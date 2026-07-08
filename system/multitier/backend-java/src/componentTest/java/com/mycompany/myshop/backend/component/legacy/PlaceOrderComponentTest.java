package com.mycompany.myshop.backend.component.legacy;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.mycompany.myshop.backend.AbstractComponentTest;
import com.mycompany.myshop.backend.core.dtos.PlaceOrderRequest;
import com.mycompany.myshop.backend.core.dtos.PlaceOrderResponse;
import com.mycompany.myshop.backend.core.dtos.ViewOrderDetailsResponse;
import com.mycompany.myshop.backend.core.entities.Coupon;
import com.mycompany.myshop.backend.core.entities.OrderStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * "Before" of the external-systems contract-tests refactor: the place-order flow with the ERP / Tax
 * / Clock externals stubbed by raw, inlined WireMock ({@code ERP/TAX/CLOCK.stubFor(...)}). The
 * {@code latest/} twin drives the identical scenarios through the shared stub DSL. Same stubbed
 * responses, same assertions.
 */
class PlaceOrderComponentTest extends AbstractComponentTest {

    @Test
    void computesTotalsFromPricePromotionAndTax() {
        CLOCK.stubFor(get(urlEqualTo("/api/time"))
            .willReturn(okJson("{\"time\":\"2026-03-10T12:00:00Z\"}")));
        ERP.stubFor(get(urlEqualTo("/api/products/BOOK-123"))
            .willReturn(okJson("{\"id\":\"BOOK-123\",\"price\":10.00}")));
        ERP.stubFor(get(urlEqualTo("/api/promotion"))
            .willReturn(okJson("{\"promotionActive\":false,\"discount\":1.0}")));
        TAX.stubFor(get(urlEqualTo("/api/countries/US"))
            .willReturn(okJson("{\"id\":\"US\",\"countryName\":\"US\",\"taxRate\":0.10}")));

        ViewOrderDetailsResponse order = placeAndFetch(orderRequest("BOOK-123", 2, "US", null));

        assertThat(order.getBasePrice()).isEqualByComparingTo("20.00");      // 10.00 x 2
        assertThat(order.getSubtotalPrice()).isEqualByComparingTo("20.00");  // no promo, no coupon
        assertThat(order.getTaxAmount()).isEqualByComparingTo("2.00");       // 20.00 x 0.10
        assertThat(order.getTotalPrice()).isEqualByComparingTo("22.00");     // 20.00 + 2.00
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PLACED);
        assertThat(order.getAppliedCouponCode()).isNull();
    }

    @Test
    void appliesActivePromotionDiscount() {
        CLOCK.stubFor(get(urlEqualTo("/api/time"))
            .willReturn(okJson("{\"time\":\"2026-03-10T12:00:00Z\"}")));
        ERP.stubFor(get(urlEqualTo("/api/products/BOOK-123"))
            .willReturn(okJson("{\"id\":\"BOOK-123\",\"price\":10.00}")));
        ERP.stubFor(get(urlEqualTo("/api/promotion"))
            .willReturn(okJson("{\"promotionActive\":true,\"discount\":0.9}")));
        TAX.stubFor(get(urlEqualTo("/api/countries/US"))
            .willReturn(okJson("{\"id\":\"US\",\"countryName\":\"US\",\"taxRate\":0.10}")));

        ViewOrderDetailsResponse order = placeAndFetch(orderRequest("BOOK-123", 2, "US", null));

        assertThat(order.getSubtotalPrice()).isEqualByComparingTo("18.00");  // 20.00 x 0.9
        assertThat(order.getTaxAmount()).isEqualByComparingTo("1.80");       // 18.00 x 0.10
        assertThat(order.getTotalPrice()).isEqualByComparingTo("19.80");
    }

    @Test
    void appliesCouponDiscount() {
        couponRepository.save(new Coupon("SAVE20", new BigDecimal("0.20"), null, null, 100, 0));

        CLOCK.stubFor(get(urlEqualTo("/api/time"))
            .willReturn(okJson("{\"time\":\"2026-03-10T12:00:00Z\"}")));
        ERP.stubFor(get(urlEqualTo("/api/products/BOOK-123"))
            .willReturn(okJson("{\"id\":\"BOOK-123\",\"price\":10.00}")));
        ERP.stubFor(get(urlEqualTo("/api/promotion"))
            .willReturn(okJson("{\"promotionActive\":false,\"discount\":1.0}")));
        TAX.stubFor(get(urlEqualTo("/api/countries/US"))
            .willReturn(okJson("{\"id\":\"US\",\"countryName\":\"US\",\"taxRate\":0.10}")));

        ViewOrderDetailsResponse order = placeAndFetch(orderRequest("BOOK-123", 2, "US", "SAVE20"));

        assertThat(order.getDiscountAmount()).isEqualByComparingTo("4.00");  // 20.00 x 0.20
        assertThat(order.getSubtotalPrice()).isEqualByComparingTo("16.00");
        assertThat(order.getTaxAmount()).isEqualByComparingTo("1.60");       // 16.00 x 0.10
        assertThat(order.getTotalPrice()).isEqualByComparingTo("17.60");
        assertThat(order.getAppliedCouponCode()).isEqualTo("SAVE20");
    }

    @Test
    void rejectsOrderDuringNewYearBlackout() {
        CLOCK.stubFor(get(urlEqualTo("/api/time"))
            .willReturn(okJson("{\"time\":\"2026-12-31T23:59:00Z\"}")));

        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/orders", orderRequest("BOOK-123", 2, "US", null), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void rejectsUnknownProduct() {
        CLOCK.stubFor(get(urlEqualTo("/api/time"))
            .willReturn(okJson("{\"time\":\"2026-03-10T12:00:00Z\"}")));
        ERP.stubFor(get(urlEqualTo("/api/products/MISSING-1"))
            .willReturn(aResponse().withStatus(404)));

        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/orders", orderRequest("MISSING-1", 1, "US", null), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private PlaceOrderRequest orderRequest(String sku, int quantity, String country, String couponCode) {
        var request = new PlaceOrderRequest();
        request.setSku(sku);
        request.setQuantity(quantity);
        request.setCountry(country);
        request.setCouponCode(couponCode);
        return request;
    }

    private ViewOrderDetailsResponse placeAndFetch(PlaceOrderRequest request) {
        ResponseEntity<PlaceOrderResponse> placed =
            restTemplate.postForEntity("/api/orders", request, PlaceOrderResponse.class);
        assertThat(placed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(placed.getBody()).isNotNull();

        String orderNumber = placed.getBody().getOrderNumber();
        return restTemplate.getForObject("/api/orders/" + orderNumber, ViewOrderDetailsResponse.class);
    }
}
