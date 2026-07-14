package com.mycompany.myshop.backend.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mycompany.myshop.backend.core.dtos.BrowseCouponsResponse;
import com.mycompany.myshop.backend.core.dtos.BrowseOrderHistoryResponse;
import com.mycompany.myshop.backend.core.dtos.PlaceOrderRequest;
import com.mycompany.myshop.backend.core.dtos.PlaceOrderResponse;
import com.mycompany.myshop.backend.core.dtos.PublishCouponRequest;
import com.mycompany.myshop.backend.core.dtos.ViewOrderDetailsResponse;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Fluent facade over {@link BackendDriver} for driving the system under test (place + view order,
 * browse order history, publish + browse coupon):
 *
 * <pre>{@code
 * PlaceOrderResponse placed = backend.placeOrder()
 *     .withSku("BOOK-123").withQuantity(2).withCountry("US").withCoupon("SAVE20")
 *     .execute().expectSuccess();
 * ViewOrderDetailsResponse order = backend.viewOrder(placed.getOrderNumber()).expectSuccess();
 *
 * backend.placeOrder()
 *     .withSku("MISSING-1").withQuantity(1).withCountry("US")
 *     .execute().expectRejection(HttpStatus.UNPROCESSABLE_ENTITY)
 *     .withFieldError("sku", "Product does not exist for SKU: MISSING-1");
 *
 * BrowseOrderHistoryResponse history = backend.browseOrderHistory();
 *
 * backend.publishCoupon().withCode("SAVE10").withDiscountRate("0.20").withUsageLimit(100)
 *     .execute().expectSuccess();
 * BrowseCouponsResponse coupons = backend.browseCoupons();
 * }</pre>
 *
 * <p>Symmetric with the ERP / Tax / Clock stub DSLs under {@code support/}: those drive the external
 * collaborators, this drives the backend itself, so every party in a component test is reached
 * through the same four-layer abstraction. Unlike a stub's fire-and-forget {@code execute()}, driving
 * the system under test produces an outcome, so a call with two possible outcomes hands back a result
 * on which the test states its expectation: {@code expectSuccess()} asserts acceptance and returns
 * the payload, {@code expectRejection(status)} asserts the rejection status and hands back a {@link
 * Rejection} for asserting the error itself. Calls that can only ever succeed (browse order history,
 * browse coupons) skip the result and return the payload directly. Place and view stay separate
 * operations, mirroring the system-test's {@code PlaceOrderResponse} / {@code
 * ViewOrderDetailsResponse} split.
 *
 * <p>The system under test owns its HTTP contract, so the exact accepted / rejected statuses and the
 * RFC 7807 {@code ProblemDetail} shape are asserted here in the DSL rather than leaked to the tests.
 */
public class BackendDsl {

    private final BackendDriver driver;
    private final ObjectMapper objectMapper;

    public BackendDsl(BackendDriver driver, ObjectMapper objectMapper) {
        this.driver = driver;
        this.objectMapper = objectMapper;
    }

    public PlaceOrder placeOrder() {
        return new PlaceOrder();
    }

    /** Reads an order back by number, handing back the outcome for the test to state its expectation on. */
    public ViewOrderResult viewOrder(String orderNumber) {
        return new ViewOrderResult(driver.viewOrder(orderNumber), objectMapper);
    }

    /** Browses order history, asserts {@code 200 OK}, and returns the parsed body. */
    public BrowseOrderHistoryResponse browseOrderHistory() {
        var response = driver.browseOrderHistory();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
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

        /** Places the order and hands back the outcome for the test to state its expectation on. */
        public PlaceOrderResult execute() {
            return new PlaceOrderResult(driver.placeOrder(buildRequest()), objectMapper);
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

    /** Outcome of a place-order call, awaiting the test's expectation. */
    public static final class PlaceOrderResult {
        private final ResponseEntity<String> response;
        private final ObjectMapper objectMapper;

        private PlaceOrderResult(ResponseEntity<String> response, ObjectMapper objectMapper) {
            this.response = response;
            this.objectMapper = objectMapper;
        }

        /**
         * Asserts the order was accepted ({@code 201 CREATED}) and returns the {@code
         * PlaceOrderResponse} (the order number) — the same contract the real {@code POST /api/orders}
         * endpoint returns. To inspect the persisted totals, read the order back with {@link
         * BackendDsl#viewOrder(String)}, mirroring the system-test's place / view split.
         */
        public PlaceOrderResponse expectSuccess() {
            return expectPayload(response, HttpStatus.CREATED, PlaceOrderResponse.class, objectMapper);
        }

        /**
         * Asserts the order was rejected with {@code expectedStatus} (e.g. {@code 422
         * UNPROCESSABLE_ENTITY}) and hands back the parsed error for the test to pin down <em>which</em>
         * rule fired — the status alone cannot tell a blackout apart from an unknown product.
         */
        public Rejection expectRejection(HttpStatus expectedStatus) {
            return assertRejection(response, expectedStatus, objectMapper);
        }
    }

    /** Outcome of a view-order call, awaiting the test's expectation. */
    public static final class ViewOrderResult {
        private final ResponseEntity<String> response;
        private final ObjectMapper objectMapper;

        private ViewOrderResult(ResponseEntity<String> response, ObjectMapper objectMapper) {
            this.response = response;
            this.objectMapper = objectMapper;
        }

        /** Asserts the order was found ({@code 200 OK}) and returns the persisted details. */
        public ViewOrderDetailsResponse expectSuccess() {
            return expectPayload(
                response, HttpStatus.OK, ViewOrderDetailsResponse.class, objectMapper);
        }

        /**
         * Asserts the read was refused with {@code expectedStatus} (e.g. {@code 404 NOT_FOUND}) and
         * hands back the parsed error.
         */
        public Rejection expectRejection(HttpStatus expectedStatus) {
            return assertRejection(response, expectedStatus, objectMapper);
        }
    }

    /**
     * A rejected call's RFC 7807 {@code ProblemDetail} body, on which the test states what it expects
     * the error to say. The backend raises validation failures in two shapes, and this exposes one
     * expectation per shape so a test cannot accidentally assert the wrong one:
     *
     * <ul>
     *   <li>{@link #withMessage(String)} — a whole-request failure (no field), whose message the
     *       handler puts in {@code detail}. Example: the New Year blackout.
     *   <li>{@link #withFieldError(String, String)} — a field-scoped failure, whose {@code detail} is
     *       only the generic "The request contains one or more validation errors" and whose real
     *       message lives in {@code errors[]}. Example: an unknown SKU.
     * </ul>
     *
     * <p>Asserting {@code detail} on a field-scoped failure would pass against that generic string
     * while verifying nothing, which is exactly the trap the split avoids.
     */
    public static final class Rejection {
        private static final String GENERIC_VALIDATION_DETAIL =
            "The request contains one or more validation errors";

        private final JsonNode problemDetail;

        private Rejection(JsonNode problemDetail) {
            this.problemDetail = problemDetail;
        }

        /** Asserts the whole-request failure message carried in {@code detail}. */
        public Rejection withMessage(String expectedMessage) {
            assertThat(problemDetail.path("detail").asText())
                .as("ProblemDetail.detail")
                .isEqualTo(expectedMessage);
            return this;
        }

        /**
         * Asserts {@code errors[]} carries a failure for {@code expectedField} with {@code
         * expectedMessage}, and that {@code detail} is the generic validation string that a
         * field-scoped failure is supposed to carry.
         */
        public Rejection withFieldError(String expectedField, String expectedMessage) {
            assertThat(problemDetail.path("detail").asText())
                .as("ProblemDetail.detail of a field-scoped failure")
                .isEqualTo(GENERIC_VALIDATION_DETAIL);

            var errors = problemDetail.path("errors");
            assertThat(errors.isArray()).as("ProblemDetail.errors is an array").isTrue();
            assertThat(errors)
                .as("ProblemDetail.errors")
                .anySatisfy(error -> {
                    assertThat(error.path("field").asText()).isEqualTo(expectedField);
                    assertThat(error.path("message").asText()).isEqualTo(expectedMessage);
                });
            return this;
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

        /** Publishes the coupon and hands back the outcome for the test to state its expectation on. */
        public PublishCouponResult execute() {
            return new PublishCouponResult(driver.publishCoupon(buildRequest()));
        }

        private PublishCouponRequest buildRequest() {
            var request = new PublishCouponRequest();
            request.setCode(code);
            request.setDiscountRate(discountRate);
            request.setUsageLimit(usageLimit);
            return request;
        }
    }

    /** Outcome of a publish-coupon call, awaiting the test's expectation. */
    public static final class PublishCouponResult {
        private final ResponseEntity<Void> response;

        private PublishCouponResult(ResponseEntity<Void> response) {
            this.response = response;
        }

        /**
         * Asserts the real {@code 204 No Content} contract — publish returns no body, so there is
         * nothing to fetch or parse here.
         */
        public void expectSuccess() {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }
    }

    private static <T> T expectPayload(
            ResponseEntity<String> response,
            HttpStatus expectedStatus,
            Class<T> payloadType,
            ObjectMapper objectMapper) {
        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
        assertThat(response.getBody()).isNotNull();
        return parse(response.getBody(), payloadType, objectMapper);
    }

    private static Rejection assertRejection(
            ResponseEntity<String> response, HttpStatus expectedStatus, ObjectMapper objectMapper) {
        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
        assertThat(response.getBody()).as("rejection body").isNotNull();
        return new Rejection(parse(response.getBody(), JsonNode.class, objectMapper));
    }

    private static <T> T parse(String body, Class<T> type, ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(body, type);
        } catch (Exception e) {
            throw new AssertionError(
                "Could not parse response body as " + type.getSimpleName() + ": " + body, e);
        }
    }
}
