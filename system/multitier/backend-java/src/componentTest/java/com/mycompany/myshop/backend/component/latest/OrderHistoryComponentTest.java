package com.mycompany.myshop.backend.component.latest;

import static org.assertj.core.api.Assertions.assertThat;

import com.mycompany.myshop.backend.AbstractComponentTest;
import com.mycompany.myshop.backend.core.dtos.BrowseOrderHistoryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * "After" of the component-test refactor: identical scenarios to the {@code legacy/} twin, driven
 * entirely through the DSLs — the ERP / Tax / Clock externals via the shared stub DSL under
 * {@code support/} and the system under test via the {@code backend} DSL
 * ({@link com.mycompany.myshop.backend.support.BackendDsl}). Same stubbed responses, same assertions;
 * the raw WireMock and {@code restTemplate} plumbing of the {@code legacy/} twin lives in the drivers
 * here.
 */
class OrderHistoryComponentTest extends AbstractComponentTest {

    @Test
    void browseReturnsPlacedOrders() {
        var orderNumber = placeOrder();

        var browseOrderHistoryResponse = backend.browseOrderHistory();

        assertThat(browseOrderHistoryResponse.getOrders())
            .extracting(BrowseOrderHistoryResponse.BrowseOrderHistoryItemResponse::getOrderNumber)
            .contains(orderNumber);
    }

    @Test
    void viewMissingOrderReturnsNotFound() {
        backend.viewOrder("UNKNOWN")
            .expectRejection(HttpStatus.NOT_FOUND)
            .withMessage("Order UNKNOWN does not exist.");
    }

    private String placeOrder() {
        clockStub.returnsTime("2026-03-10T12:00:00Z").execute();
        erpStub.returnsProduct().withSku("BOOK-123").withUnitPrice("10.00").execute();
        erpStub.returnsPromotion().withActive(false).withDiscount("1.0").execute();
        taxStub.returnsRate().withCountry("US").withRate("0.10").execute();

        return backend.placeOrder()
            .withSku("BOOK-123").withQuantity(2).withCountry("US")
            .execute().expectSuccess().getOrderNumber();
    }
}
