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

/**
 * "Before" of the component-test refactor: the place-order flow driven entirely by raw plumbing — the
 * ERP / Tax / Clock externals stubbed with inlined WireMock ({@code ERP/TAX/CLOCK.stubFor(...)}) and
 * the system under test hit with raw {@code restTemplate} calls ({@code placeAndFetch(orderRequest(
 * ...))}). The {@code latest/} twin runs the identical scenarios entirely through the DSLs — the stub
 * DSL for the externals and the {@code backend} DSL for the SUT. Same stubbed responses, same
 * assertions; for this pair, legacy is all-raw and latest is all-DSL.
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

        var placed = place(orderRequest("BOOK-123", 2, "US", null));
        var order = viewOrder(placed.getOrderNumber());

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

        var placed = place(orderRequest("BOOK-123", 2, "US", null));
        var order = viewOrder(placed.getOrderNumber());

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

        var placed = place(orderRequest("BOOK-123", 2, "US", "SAVE20"));
        var order = viewOrder(placed.getOrderNumber());

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

        var response = restTemplate.postForEntity(
            "/api/orders", orderRequest("BOOK-123", 2, "US", null), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void rejectsUnknownProduct() {
        CLOCK.stubFor(get(urlEqualTo("/api/time"))
            .willReturn(okJson("{\"time\":\"2026-03-10T12:00:00Z\"}")));
        ERP.stubFor(get(urlEqualTo("/api/products/MISSING-1"))
            .willReturn(aResponse().withStatus(404)));

        var response = restTemplate.postForEntity(
            "/api/orders", orderRequest("MISSING-1", 1, "US", null), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private PlaceOrderResponse place(PlaceOrderRequest request) {
        var placed = restTemplate.postForEntity("/api/orders", request, PlaceOrderResponse.class);
        assertThat(placed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(placed.getBody()).isNotNull();
        return placed.getBody();
    }

    private ViewOrderDetailsResponse viewOrder(String orderNumber) {
        var view = restTemplate.getForEntity(
            "/api/orders/" + orderNumber, ViewOrderDetailsResponse.class);
        assertThat(view.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(view.getBody()).isNotNull();
        return view.getBody();
    }

    private PlaceOrderRequest orderRequest(
            String sku, int quantity, String country, String couponCode) {
        var request = new PlaceOrderRequest();
        request.setSku(sku);
        request.setQuantity(quantity);
        request.setCountry(country);
        request.setCouponCode(couponCode);
        return request;
    }
}
