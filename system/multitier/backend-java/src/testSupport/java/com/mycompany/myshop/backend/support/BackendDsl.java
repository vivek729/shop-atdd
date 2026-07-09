package com.mycompany.myshop.backend.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.mycompany.myshop.backend.core.dtos.BrowseCouponsResponse;
import com.mycompany.myshop.backend.core.dtos.PlaceOrderRequest;
import com.mycompany.myshop.backend.core.dtos.PublishCouponRequest;
import com.mycompany.myshop.backend.core.dtos.ViewOrderDetailsResponse;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Fluent facade over {@link BackendDriver} for driving the system under test (place + view order,
 * publish + browse coupon):
 *
 * <pre>{@code
 * ViewOrderDetailsResponse order = backend.placeOrder()
 *     .withSku("BOOK-123").withQuantity(2).withCountry("US").withCoupon("SAVE20")
 *     .placeExpectingSuccess();
 *
 * backend.placeOrder()
 *     .withSku("MISSING-1").withQuantity(1).withCountry("US")
 *     .placeExpectingRejection(HttpStatus.UNPROCESSABLE_ENTITY);
 *
 * backend.publishCoupon().withCode("SAVE10").withDiscountRate("0.20").withUsageLimit(100)
 *     .publishExpectingSuccess();
 * BrowseCouponsResponse coupons = backend.browseCoupons();
 * }</pre>
 *
 * <p>Symmetric with the ERP / Tax / Clock stub DSLs under {@code support/}: those drive the external
 * collaborators, this drives the backend itself, so every party in a component test is reached
 * through the same four-layer abstraction. Two explicit place-order terminals mirror the two real
 * scenarios — the rejection terminal never fetches or parses an order body it does not need.
 */
public class BackendDsl {

    private final BackendDriver driver;

    public BackendDsl(BackendDriver driver) {
        this.driver = driver;
    }

    public PlaceOrder placeOrder() {
        return new PlaceOrder();
    }

    public PublishCoupon publishCoupon() {
        return new PublishCoupon();
    }

    /** Browses coupons, asserts {@code 200 OK}, and returns the parsed body. */
    public BrowseCouponsResponse browseCoupons() {
        var response = driver.browseCoupons();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    /**
     * Hits the dedicated liveness endpoint ({@code GET /health}) and asserts {@code 200 OK} with
     * {@code status: UP} — the SUT-side equivalent of the system-test's {@code myShop().shouldBeRunning()}.
     * Used by the harness smoke test so the canary depends on the liveness probe, not a feature endpoint.
     */
    public void checkHealth() {
        var response = driver.checkHealth();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP");
    }

    public final class PlaceOrder {
        private String sku;
        private int quantity;
        private String country;
        private String couponCode;

        public PlaceOrder withSku(String sku) {
            this.sku = sku;
            return this;
        }

        public PlaceOrder withQuantity(int quantity) {
            this.quantity = quantity;
            return this;
        }

        public PlaceOrder withCountry(String country) {
            this.country = country;
            return this;
        }

        public PlaceOrder withCoupon(String couponCode) {
            this.couponCode = couponCode;
            return this;
        }

        /**
         * Places the order, asserts it was accepted ({@code 201 CREATED}), then reads it back,
         * asserts the read returned {@code 200 OK}, and returns the persisted order. Replaces the old
         * {@code placeAndFetch(orderRequest(...))} helper.
         */
        public ViewOrderDetailsResponse placeExpectingSuccess() {
            var placed = driver.placeOrder(buildRequest());
            assertThat(placed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(placed.getBody()).isNotNull();
            var view = driver.viewOrder(placed.getBody().getOrderNumber());
            assertThat(view.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(view.getBody()).isNotNull();
            return view.getBody();
        }

        /**
         * Places the order and asserts it was rejected with {@code expectedStatus} (e.g.
         * {@code 422 UNPROCESSABLE_ENTITY}), without fetching a body it does not need. The system
         * under test owns its HTTP contract, so the exact rejection status is asserted here in the
         * DSL — symmetric with {@link #placeExpectingSuccess()} — rather than leaked to the test.
         */
        public void placeExpectingRejection(HttpStatus expectedStatus) {
            assertThat(driver.placeOrder(buildRequest()).getStatusCode()).isEqualTo(expectedStatus);
        }

        private PlaceOrderRequest buildRequest() {
            var request = new PlaceOrderRequest();
            request.setSku(sku);
            request.setQuantity(quantity);
            request.setCountry(country);
            request.setCouponCode(couponCode);
            return request;
        }
    }

    public final class PublishCoupon {
        private String code;
        private BigDecimal discountRate;
        private Integer usageLimit;

        public PublishCoupon withCode(String code) {
            this.code = code;
            return this;
        }

        public PublishCoupon withDiscountRate(String discountRate) {
            this.discountRate = new BigDecimal(discountRate);
            return this;
        }

        public PublishCoupon withUsageLimit(int usageLimit) {
            this.usageLimit = usageLimit;
            return this;
        }

        /**
         * Publishes the coupon and asserts the real {@code 204 No Content} contract — publish returns
         * no body, so there is nothing to fetch or parse here.
         */
        public void publishExpectingSuccess() {
            var response = driver.publishCoupon(buildRequest());
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }

        private PublishCouponRequest buildRequest() {
            var request = new PublishCouponRequest();
            request.setCode(code);
            request.setDiscountRate(discountRate);
            request.setUsageLimit(usageLimit);
            return request;
        }
    }
}
