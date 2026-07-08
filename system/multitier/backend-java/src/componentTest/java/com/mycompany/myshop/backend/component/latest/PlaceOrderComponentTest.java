package com.mycompany.myshop.backend.component.latest;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.mycompany.myshop.backend.AbstractComponentTest;
import com.mycompany.myshop.backend.core.dtos.PlaceOrderRequest;
import com.mycompany.myshop.backend.core.dtos.PlaceOrderResponse;
import com.mycompany.myshop.backend.core.dtos.ViewOrderDetailsResponse;
import com.mycompany.myshop.backend.core.entities.Coupon;
import com.mycompany.myshop.backend.core.entities.OrderStatus;
import com.mycompany.myshop.backend.support.ClockStubDriver;
import com.mycompany.myshop.backend.support.ClockStubDsl;
import com.mycompany.myshop.backend.support.ErpStubDriver;
import com.mycompany.myshop.backend.support.ErpStubDsl;
import com.mycompany.myshop.backend.support.TaxStubDriver;
import com.mycompany.myshop.backend.support.TaxStubDsl;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * "After" of the external-systems contract-tests refactor: identical scenarios to the {@code legacy/}
 * twin, but the ERP / Tax / Clock stubs are declared through the shared fluent DSL under
 * {@code support/} (e.g. {@code erpStub.returnsProduct().withSku(...).withUnitPrice(...).execute()}).
 * Same stubbed responses, same assertions; the WireMock plumbing lives in the drivers.
 */
class PlaceOrderComponentTest extends AbstractComponentTest {

    private final ErpStubDsl erpStub =
        new ErpStubDsl(new ErpStubDriver(new WireMock("localhost", ERP.port())));
    private final TaxStubDsl taxStub =
        new TaxStubDsl(new TaxStubDriver(new WireMock("localhost", TAX.port())));
    private final ClockStubDsl clockStub =
        new ClockStubDsl(new ClockStubDriver(new WireMock("localhost", CLOCK.port())));

    @Test
    void computesTotalsFromPricePromotionAndTax() {
        clockStub.returnsTime("2026-03-10T12:00:00Z");
        erpStub.returnsProduct().withSku("BOOK-123").withUnitPrice("10.00").execute();
        erpStub.returnsPromotion().withActive(false).withDiscount("1.0").execute();
        taxStub.returnsRate().withCountry("US").withRate("0.10").execute();

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
        clockStub.returnsTime("2026-03-10T12:00:00Z");
        erpStub.returnsProduct().withSku("BOOK-123").withUnitPrice("10.00").execute();
        erpStub.returnsPromotion().withActive(true).withDiscount("0.9").execute();
        taxStub.returnsRate().withCountry("US").withRate("0.10").execute();

        ViewOrderDetailsResponse order = placeAndFetch(orderRequest("BOOK-123", 2, "US", null));

        assertThat(order.getSubtotalPrice()).isEqualByComparingTo("18.00");  // 20.00 x 0.9
        assertThat(order.getTaxAmount()).isEqualByComparingTo("1.80");       // 18.00 x 0.10
        assertThat(order.getTotalPrice()).isEqualByComparingTo("19.80");
    }

    @Test
    void appliesCouponDiscount() {
        couponRepository.save(new Coupon("SAVE20", new BigDecimal("0.20"), null, null, 100, 0));

        clockStub.returnsTime("2026-03-10T12:00:00Z");
        erpStub.returnsProduct().withSku("BOOK-123").withUnitPrice("10.00").execute();
        erpStub.returnsPromotion().withActive(false).withDiscount("1.0").execute();
        taxStub.returnsRate().withCountry("US").withRate("0.10").execute();

        ViewOrderDetailsResponse order = placeAndFetch(orderRequest("BOOK-123", 2, "US", "SAVE20"));

        assertThat(order.getDiscountAmount()).isEqualByComparingTo("4.00");  // 20.00 x 0.20
        assertThat(order.getSubtotalPrice()).isEqualByComparingTo("16.00");
        assertThat(order.getTaxAmount()).isEqualByComparingTo("1.60");       // 16.00 x 0.10
        assertThat(order.getTotalPrice()).isEqualByComparingTo("17.60");
        assertThat(order.getAppliedCouponCode()).isEqualTo("SAVE20");
    }

    @Test
    void rejectsOrderDuringNewYearBlackout() {
        clockStub.returnsTime("2026-12-31T23:59:00Z");

        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/orders", orderRequest("BOOK-123", 2, "US", null), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void rejectsUnknownProduct() {
        clockStub.returnsTime("2026-03-10T12:00:00Z");
        erpStub.returnsNoProduct().withSku("MISSING-1").execute();

        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/orders", orderRequest("MISSING-1", 1, "US", null), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

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
}
