package com.mycompany.myshop.backend.support;

import com.mycompany.myshop.backend.core.dtos.PlaceOrderRequest;
import com.mycompany.myshop.backend.core.dtos.PlaceOrderResponse;
import com.mycompany.myshop.backend.core.dtos.ViewOrderDetailsResponse;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

/**
 * Low-level driver for the system under test (the backend itself), wrapping the {@link
 * TestRestTemplate} bound to the random component-test port. Owns the raw HTTP mechanics of the
 * order endpoints — {@code POST /api/orders} and {@code GET /api/orders/{orderNumber}} — mirroring
 * how {@link ErpStubDriver} / {@link TaxStubDriver} / {@link ClockStubDriver} own the WireMock
 * mechanics of the external systems. The calls are byte-identical to the inline {@code restTemplate}
 * calls the tests used before, so routing them through the driver is behaviour-neutral.
 */
public class BackendDriver {

    private final TestRestTemplate restTemplate;

    public BackendDriver(TestRestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public ResponseEntity<PlaceOrderResponse> placeOrder(PlaceOrderRequest request) {
        return restTemplate.postForEntity("/api/orders", request, PlaceOrderResponse.class);
    }

    public ViewOrderDetailsResponse viewOrder(String orderNumber) {
        return restTemplate.getForObject("/api/orders/" + orderNumber, ViewOrderDetailsResponse.class);
    }
}
