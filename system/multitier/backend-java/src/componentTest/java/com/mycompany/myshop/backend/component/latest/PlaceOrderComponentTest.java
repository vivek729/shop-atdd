package com.mycompany.myshop.backend.component.latest;

import static org.assertj.core.api.Assertions.assertThat;

import com.mycompany.myshop.backend.AbstractComponentTest;
import com.mycompany.myshop.backend.core.entities.Coupon;
import com.mycompany.myshop.backend.core.entities.OrderStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * "After" of the external-systems contract-tests refactor: identical scenarios to the {@code legacy/}
 * twin, but the ERP / Tax / Clock stubs are declared through the shared fluent DSL under
 * {@code support/} (e.g. {@code erpStub.returnsProduct().withSku(...).withUnitPrice(...).execute()}).
 * Same stubbed responses, same assertions; the WireMock plumbing lives in the drivers.
 *
 * <p>The system under test is driven the same way in both twins — through the shared {@code backend}
 * DSL ({@link com.mycompany.myshop.backend.support.BackendDsl}) — so the {@code latest/} vs
 * {@code legacy/} contrast stays purely about external-stub style (stub DSL vs raw WireMock).
 */
class PlaceOrderComponentTest extends AbstractComponentTest {

    @Test
    void computesTotalsFromPricePromotionAndTax() {
        clockStub.returnsTime("2026-03-10T12:00:00Z");
        erpStub.returnsProduct().withSku("BOOK-123").withUnitPrice("10.00").execute();
        erpStub.returnsPromotion().withActive(false).withDiscount("1.0").execute();
        taxStub.returnsRate().withCountry("US").withRate("0.10").execute();

        var order = backend.placeOrder()
            .withSku("BOOK-123").withQuantity(2).withCountry("US").placeExpectingSuccess();

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

        var order = backend.placeOrder()
            .withSku("BOOK-123").withQuantity(2).withCountry("US").placeExpectingSuccess();

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

        var order = backend.placeOrder()
            .withSku("BOOK-123").withQuantity(2).withCountry("US").withCoupon("SAVE20")
            .placeExpectingSuccess();

        assertThat(order.getDiscountAmount()).isEqualByComparingTo("4.00");  // 20.00 x 0.20
        assertThat(order.getSubtotalPrice()).isEqualByComparingTo("16.00");
        assertThat(order.getTaxAmount()).isEqualByComparingTo("1.60");       // 16.00 x 0.10
        assertThat(order.getTotalPrice()).isEqualByComparingTo("17.60");
        assertThat(order.getAppliedCouponCode()).isEqualTo("SAVE20");
    }

    @Test
    void rejectsOrderDuringNewYearBlackout() {
        clockStub.returnsTime("2026-12-31T23:59:00Z");

        assertThat(backend.placeOrder()
            .withSku("BOOK-123").withQuantity(2).withCountry("US").placeExpectingRejection())
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void rejectsUnknownProduct() {
        clockStub.returnsTime("2026-03-10T12:00:00Z");
        erpStub.returnsNoProduct().withSku("MISSING-1").execute();

        assertThat(backend.placeOrder()
            .withSku("MISSING-1").withQuantity(1).withCountry("US").placeExpectingRejection())
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
