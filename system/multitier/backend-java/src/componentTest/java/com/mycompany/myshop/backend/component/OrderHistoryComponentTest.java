package com.mycompany.myshop.backend.component;

import static org.assertj.core.api.Assertions.assertThat;

import com.mycompany.myshop.backend.AbstractComponentTest;
import com.mycompany.myshop.backend.core.dtos.BrowseOrderHistoryResponse;
import com.mycompany.myshop.backend.core.dtos.PlaceOrderRequest;
import com.mycompany.myshop.backend.core.dtos.PlaceOrderResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Browse order history and view-details flows, including the 404 path for a missing order.
 */
class OrderHistoryComponentTest extends AbstractComponentTest {

    private String placeOrder() {
        stubClock("2026-03-10T12:00:00Z");
        stubProduct("BOOK-123", "10.00");
        stubPromotion(false, "1.0");
        stubTax("US", "0.10");

        var request = new PlaceOrderRequest();
        request.setSku("BOOK-123");
        request.setQuantity(2);
        request.setCountry("US");

        ResponseEntity<PlaceOrderResponse> placed =
            restTemplate.postForEntity("/api/orders", request, PlaceOrderResponse.class);
        assertThat(placed.getBody()).isNotNull();
        return placed.getBody().getOrderNumber();
    }

    @Test
    void browseReturnsPlacedOrders() {
        String orderNumber = placeOrder();

        ResponseEntity<BrowseOrderHistoryResponse> response =
            restTemplate.getForEntity("/api/orders", BrowseOrderHistoryResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getOrders())
            .extracting(BrowseOrderHistoryResponse.BrowseOrderHistoryItemResponse::getOrderNumber)
            .contains(orderNumber);
    }

    @Test
    void viewMissingOrderReturnsNotFound() {
        ResponseEntity<String> response =
            restTemplate.getForEntity("/api/orders/UNKNOWN", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
