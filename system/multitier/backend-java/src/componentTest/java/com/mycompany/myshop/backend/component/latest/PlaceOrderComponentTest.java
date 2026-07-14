package com.mycompany.myshop.backend.component.latest;

import static org.assertj.core.api.Assertions.assertThat;

import com.mycompany.myshop.backend.AbstractComponentTest;
import com.mycompany.myshop.backend.core.entities.Coupon;
import com.mycompany.myshop.backend.core.entities.OrderStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * "After" of the component-test refactor: identical scenarios to the {@code legacy/} twin, driven
 * entirely through the DSLs — the ERP / Tax / Clock externals via the shared stub DSL under
 * {@code support/} (e.g. {@code erpStub.returnsProduct().withSku(...).withUnitPrice(...).execute()})
 * and the system under test via the {@code backend} DSL
 * ({@link com.mycompany.myshop.backend.support.BackendDsl},
 * {@code backend.placeOrder()...execute().expectSuccess()}). Same stubbed responses, same assertions;
 * the raw WireMock and {@code restTemplate} plumbing of the {@code legacy/} twin lives in the drivers
 * here. For this pair, legacy is all-raw and latest is all-DSL.
 */
class PlaceOrderComponentTest extends AbstractComponentTest {

    @Test
    void computesTotalsFromPricePromotionAndTax() {
        clockStub.returnsTime("2026-03-10T12:00:00Z").execute();
        erpStub.returnsProduct().withSku("BOOK-123").withUnitPrice("10.00").execute();
        erpStub.returnsPromotion().withActive(false).withDiscount("1.0").execute();
        taxStub.returnsRate().withCountry("US").withRate("0.10").execute();

        var placeOrderResponse = backend.placeOrder()
            .withSku("BOOK-123").withQuantity(2).withCountry("US").execute().expectSuccess();
        var viewOrderResponse =
            backend.viewOrder(placeOrderResponse.getOrderNumber()).expectSuccess();

        assertThat(viewOrderResponse.getBasePrice()).isEqualByComparingTo("20.00");      // 10.00 x 2
        assertThat(viewOrderResponse.getSubtotalPrice()).isEqualByComparingTo("20.00");  // no promo, no coupon
        assertThat(viewOrderResponse.getTaxAmount()).isEqualByComparingTo("2.00");       // 20.00 x 0.10
        assertThat(viewOrderResponse.getTotalPrice()).isEqualByComparingTo("22.00");     // 20.00 + 2.00
        assertThat(viewOrderResponse.getStatus()).isEqualTo(OrderStatus.PLACED);
        assertThat(viewOrderResponse.getAppliedCouponCode()).isNull();
    }

    @Test
    void appliesActivePromotionDiscount() {
        clockStub.returnsTime("2026-03-10T12:00:00Z").execute();
        erpStub.returnsProduct().withSku("BOOK-123").withUnitPrice("10.00").execute();
        erpStub.returnsPromotion().withActive(true).withDiscount("0.9").execute();
        taxStub.returnsRate().withCountry("US").withRate("0.10").execute();

        var placeOrderResponse = backend.placeOrder()
            .withSku("BOOK-123").withQuantity(2).withCountry("US").execute().expectSuccess();
        var viewOrderResponse =
            backend.viewOrder(placeOrderResponse.getOrderNumber()).expectSuccess();

        assertThat(viewOrderResponse.getSubtotalPrice()).isEqualByComparingTo("18.00");  // 20.00 x 0.9
        assertThat(viewOrderResponse.getTaxAmount()).isEqualByComparingTo("1.80");       // 18.00 x 0.10
        assertThat(viewOrderResponse.getTotalPrice()).isEqualByComparingTo("19.80");
    }

    @Test
    void appliesCouponDiscount() {
        couponRepository.save(new Coupon("SAVE20", new BigDecimal("0.20"), null, null, 100, 0));

        clockStub.returnsTime("2026-03-10T12:00:00Z").execute();
        erpStub.returnsProduct().withSku("BOOK-123").withUnitPrice("10.00").execute();
        erpStub.returnsPromotion().withActive(false).withDiscount("1.0").execute();
        taxStub.returnsRate().withCountry("US").withRate("0.10").execute();

        var placeOrderResponse = backend.placeOrder()
            .withSku("BOOK-123").withQuantity(2).withCountry("US").withCoupon("SAVE20")
            .execute().expectSuccess();
        var viewOrderResponse =
            backend.viewOrder(placeOrderResponse.getOrderNumber()).expectSuccess();

        assertThat(viewOrderResponse.getDiscountAmount()).isEqualByComparingTo("4.00");  // 20.00 x 0.20
        assertThat(viewOrderResponse.getSubtotalPrice()).isEqualByComparingTo("16.00");
        assertThat(viewOrderResponse.getTaxAmount()).isEqualByComparingTo("1.60");       // 16.00 x 0.10
        assertThat(viewOrderResponse.getTotalPrice()).isEqualByComparingTo("17.60");
        assertThat(viewOrderResponse.getAppliedCouponCode()).isEqualTo("SAVE20");
    }

    @Test
    void rejectsOrderDuringNewYearBlackout() {
        clockStub.returnsTime("2026-12-31T23:59:00Z").execute();

        backend.placeOrder()
            .withSku("BOOK-123").withQuantity(2).withCountry("US")
            .execute().expectRejection(HttpStatus.UNPROCESSABLE_ENTITY)
            .withMessage("Orders cannot be placed between 23:59 and 00:00 on December 31st");
    }

    @Test
    void rejectsUnknownProduct() {
        clockStub.returnsTime("2026-03-10T12:00:00Z").execute();
        erpStub.returnsNoProduct().withSku("MISSING-1").execute();

        backend.placeOrder()
            .withSku("MISSING-1").withQuantity(1).withCountry("US")
            .execute().expectRejection(HttpStatus.UNPROCESSABLE_ENTITY)
            .withFieldError("sku", "Product does not exist for SKU: MISSING-1");
    }
}
