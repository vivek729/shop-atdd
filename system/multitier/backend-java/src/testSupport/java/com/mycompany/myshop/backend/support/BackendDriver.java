package com.mycompany.myshop.backend.support;

import com.mycompany.myshop.backend.core.dtos.BrowseCouponsResponse;
import com.mycompany.myshop.backend.core.dtos.PlaceOrderRequest;
import com.mycompany.myshop.backend.core.dtos.PlaceOrderResponse;
import com.mycompany.myshop.backend.core.dtos.PublishCouponRequest;
import com.mycompany.myshop.backend.core.dtos.ViewOrderDetailsResponse;
import java.util.Map;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

/**
 * Low-level driver for the system under test (the backend itself), wrapping the {@link
 * TestRestTemplate} bound to the random component-test port. Owns the raw HTTP mechanics of the
 * order endpoints — {@code POST /api/orders} and {@code GET /api/orders/{orderNumber}} — and the
 * coupon endpoints — {@code POST /api/coupons} and {@code GET /api/coupons} — mirroring how {@link
 * ErpStubDriver} / {@link TaxStubDriver} / {@link ClockStubDriver} own the WireMock mechanics of the
 * external systems. The calls are byte-identical to the inline {@code restTemplate} calls the tests
 * used before, so routing them through the driver is behaviour-neutral. Also owns {@code GET /health}
 * — the dedicated liveness probe used by the harness smoke test, mirroring how the system-test estate
 * resolves "is it running?" to the same endpoint.
 */
public class BackendDriver {

    private final TestRestTemplate restTemplate;

    public BackendDriver(TestRestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public ResponseEntity<PlaceOrderResponse> placeOrder(PlaceOrderRequest request) {
        return restTemplate.postForEntity("/api/orders", request, PlaceOrderResponse.class);
    }

    public ResponseEntity<ViewOrderDetailsResponse> viewOrder(String orderNumber) {
        return restTemplate.getForEntity(
            "/api/orders/" + orderNumber, ViewOrderDetailsResponse.class);
    }

    public ResponseEntity<Void> publishCoupon(PublishCouponRequest request) {
        return restTemplate.postForEntity("/api/coupons", request, Void.class);
    }

    public ResponseEntity<BrowseCouponsResponse> browseCoupons() {
        return restTemplate.getForEntity("/api/coupons", BrowseCouponsResponse.class);
    }

    public ResponseEntity<Map<String, String>> checkHealth() {
        return restTemplate.exchange(
            "/health", HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
    }
}
