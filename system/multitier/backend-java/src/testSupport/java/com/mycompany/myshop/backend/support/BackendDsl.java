package com.mycompany.myshop.backend.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.mycompany.myshop.backend.core.dtos.PlaceOrderRequest;
import com.mycompany.myshop.backend.core.dtos.PlaceOrderResponse;
import com.mycompany.myshop.backend.core.dtos.ViewOrderDetailsResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

/**
 * Fluent facade over {@link BackendDriver} for driving the system under test (place + view order):
 *
 * <pre>{@code
 * ViewOrderDetailsResponse order = backend.placeOrder()
 *     .withSku("BOOK-123").withQuantity(2).withCountry("US").withCoupon("SAVE20")
 *     .placeExpectingSuccess();
 *
 * HttpStatusCode status = backend.placeOrder()
 *     .withSku("MISSING-1").withQuantity(1).withCountry("US")
 *     .placeExpectingRejection();
 * }</pre>
 *
 * <p>Symmetric with the ERP / Tax / Clock stub DSLs under {@code support/}: those drive the external
 * collaborators, this drives the backend itself, so every party in a component test is reached
 * through the same four-layer abstraction. Two explicit terminals mirror the two real scenarios —
 * the rejection terminal never fetches or parses an order body it does not need.
 */
public class BackendDsl {

    private final BackendDriver driver;

    public BackendDsl(BackendDriver driver) {
        this.driver = driver;
    }

    public PlaceOrder placeOrder() {
        return new PlaceOrder();
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
         * Places the order, asserts it was accepted ({@code 201 CREATED}), then fetches and returns
         * the persisted order. Replaces the old {@code placeAndFetch(orderRequest(...))} helper.
         */
        public ViewOrderDetailsResponse placeExpectingSuccess() {
            ResponseEntity<PlaceOrderResponse> placed = driver.placeOrder(buildRequest());
            assertThat(placed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(placed.getBody()).isNotNull();
            return driver.viewOrder(placed.getBody().getOrderNumber());
        }

        /**
         * Places the order and returns the HTTP status without fetching, for rejection scenarios that
         * only assert the status (e.g. {@code 422 UNPROCESSABLE_ENTITY}).
         */
        public HttpStatusCode placeExpectingRejection() {
            return driver.placeOrder(buildRequest()).getStatusCode();
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
}
