package com.mycompany.myshop.backend.component.latest;

import static org.assertj.core.api.Assertions.assertThat;

import com.mycompany.myshop.backend.AbstractComponentTest;
import com.mycompany.myshop.backend.core.dtos.BrowseOrderHistoryResponse;
import com.mycompany.myshop.backend.core.dtos.PlaceOrderRequest;
import com.mycompany.myshop.backend.core.dtos.PlaceOrderResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * "After" of the external-systems contract-tests refactor: identical scenarios to the {@code legacy/}
 * twin, but the ERP / Tax / Clock stubs are declared through the shared fluent DSL under
 * {@code support/}. Same stubbed responses, same assertions.
 */
class OrderHistoryComponentTest extends AbstractComponentTest {

    private String placeOrder() {
        clockStub.returnsTime("2026-03-10T12:00:00Z").execute();
        erpStub.returnsProduct().withSku("BOOK-123").withUnitPrice("10.00").execute();
        erpStub.returnsPromotion().withActive(false).withDiscount("1.0").execute();
        taxStub.returnsRate().withCountry("US").withRate("0.10").execute();

        var request = new PlaceOrderRequest();
        request.setSku("BOOK-123");
        request.setQuantity(2);
        request.setCountry("US");

        var placed = restTemplate.postForEntity("/api/orders", request, PlaceOrderResponse.class);
        assertThat(placed.getBody()).isNotNull();
        return placed.getBody().getOrderNumber();
    }

    @Test
    void browseReturnsPlacedOrders() {
        var orderNumber = placeOrder();

        var response = restTemplate.getForEntity("/api/orders", BrowseOrderHistoryResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getOrders())
            .extracting(BrowseOrderHistoryResponse.BrowseOrderHistoryItemResponse::getOrderNumber)
            .contains(orderNumber);
    }

    @Test
    void viewMissingOrderReturnsNotFound() {
        var response = restTemplate.getForEntity("/api/orders/UNKNOWN", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
