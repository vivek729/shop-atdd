package com.mycompany.myshop.backend.component;

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
 * Drives the real place-order flow end-to-end through the in-process API: ClockGateway ->
 * ErpGateway (price + promotion) -> CouponService (DB) -> TaxGateway, all behind POST /api/orders
 * and read back via GET /api/orders/{n}. Externals are WireMock-stubbed; DB is Testcontainers.
 */
class PlaceOrderComponentTest extends AbstractComponentTest {

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

    @Test
    void computesTotalsFromPricePromotionAndTax() {
        stubClock("2026-03-10T12:00:00Z");
        stubProduct("BOOK-123", "10.00");
        stubPromotion(false, "1.0");
        stubTax("US", "0.10");

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
        stubClock("2026-03-10T12:00:00Z");
        stubProduct("BOOK-123", "10.00");
        stubPromotion(true, "0.9");
        stubTax("US", "0.10");

        ViewOrderDetailsResponse order = placeAndFetch(orderRequest("BOOK-123", 2, "US", null));

        assertThat(order.getSubtotalPrice()).isEqualByComparingTo("18.00");  // 20.00 x 0.9
        assertThat(order.getTaxAmount()).isEqualByComparingTo("1.80");       // 18.00 x 0.10
        assertThat(order.getTotalPrice()).isEqualByComparingTo("19.80");
    }

    @Test
    void appliesCouponDiscount() {
        couponRepository.save(new Coupon("SAVE20", new BigDecimal("0.20"), null, null, 100, 0));

        stubClock("2026-03-10T12:00:00Z");
        stubProduct("BOOK-123", "10.00");
        stubPromotion(false, "1.0");
        stubTax("US", "0.10");

        ViewOrderDetailsResponse order = placeAndFetch(orderRequest("BOOK-123", 2, "US", "SAVE20"));

        assertThat(order.getDiscountAmount()).isEqualByComparingTo("4.00");  // 20.00 x 0.20
        assertThat(order.getSubtotalPrice()).isEqualByComparingTo("16.00");
        assertThat(order.getTaxAmount()).isEqualByComparingTo("1.60");       // 16.00 x 0.10
        assertThat(order.getTotalPrice()).isEqualByComparingTo("17.60");
        assertThat(order.getAppliedCouponCode()).isEqualTo("SAVE20");
    }

    @Test
    void rejectsOrderDuringNewYearBlackout() {
        stubClock("2026-12-31T23:59:00Z");

        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/orders", orderRequest("BOOK-123", 2, "US", null), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void rejectsUnknownProduct() {
        stubClock("2026-03-10T12:00:00Z");
        stubProductMissing("MISSING-1");

        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/orders", orderRequest("MISSING-1", 1, "US", null), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
