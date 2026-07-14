package com.mycompany.myshop.backend.component.latest;

import com.mycompany.myshop.backend.AbstractComponentTest;
import com.mycompany.myshop.backend.core.entities.OrderStatus;
import org.junit.jupiter.api.Test;

/**
 * "After" of the component-test refactor: identical scenarios to the {@code legacy/} twin, written
 * on the scenario DSL ({@link com.mycompany.myshop.backend.support.core.ScenarioDslImpl}). The raw
 * WireMock and {@code restTemplate} plumbing of the {@code legacy/} twin lives two layers down, in
 * the drivers.
 *
 * <p>Each test states only what its scenario actually depends on; everything unstated is filled from
 * {@code ScenarioDefaults}. {@link #rejectsOrderDuringNewYearBlackout()} is the clearest case — it
 * names the clock and nothing else, because the blackout is the only thing it is about.
 */
class PlaceOrderComponentTest extends AbstractComponentTest {

    @Test
    void computesTotalsFromPricePromotionAndTax() {
        scenario.given()
                .clock().withTime("2026-03-10T12:00:00Z")
            .and().product().withSku("BOOK-123").withUnitPrice("10.00")
            .and().promotion().withActive(false).withDiscount("1.0")
            .and().country().withCode("US").withTaxRate("0.10")
            .when().placeOrder().withSku("BOOK-123").withQuantity(2).withCountry("US")
            .then().shouldSucceed()
            .and().order()
                .hasBasePrice("20.00")       // 10.00 x 2
                .hasSubtotalPrice("20.00")   // no promo, no coupon
                .hasTaxAmount("2.00")        // 20.00 x 0.10
                .hasTotalPrice("22.00")      // 20.00 + 2.00
                .hasStatus(OrderStatus.PLACED)
                .hasNoAppliedCoupon();
    }

    @Test
    void appliesActivePromotionDiscount() {
        scenario.given()
                .clock().withTime("2026-03-10T12:00:00Z")
            .and().product().withSku("BOOK-123").withUnitPrice("10.00")
            .and().promotion().withActive(true).withDiscount("0.9")
            .and().country().withCode("US").withTaxRate("0.10")
            .when().placeOrder().withSku("BOOK-123").withQuantity(2).withCountry("US")
            .then().shouldSucceed()
            .and().order()
                .hasSubtotalPrice("18.00")   // 20.00 x 0.9
                .hasTaxAmount("1.80")        // 18.00 x 0.10
                .hasTotalPrice("19.80");
    }

    @Test
    void appliesCouponDiscount() {
        scenario.given()
                .clock().withTime("2026-03-10T12:00:00Z")
            .and().product().withSku("BOOK-123").withUnitPrice("10.00")
            .and().promotion().withActive(false).withDiscount("1.0")
            .and().country().withCode("US").withTaxRate("0.10")
            .and().coupon().withCouponCode("SAVE20").withDiscountRate("0.20").withUsageLimit(100)
            .when().placeOrder()
                .withSku("BOOK-123").withQuantity(2).withCountry("US").withCouponCode("SAVE20")
            .then().shouldSucceed()
            .and().order()
                .hasDiscountAmount("4.00")   // 20.00 x 0.20
                .hasSubtotalPrice("16.00")
                .hasTaxAmount("1.60")        // 16.00 x 0.10
                .hasTotalPrice("17.60")
                .hasAppliedCoupon("SAVE20");
    }

    @Test
    void rejectsOrderDuringNewYearBlackout() {
        scenario.given()
                .clock().withTime("2026-12-31T23:59:00Z")
            .when().placeOrder()
            .then().shouldFail()
                .errorMessage("Orders cannot be placed between 23:59 and 00:00 on December 31st");
    }

    @Test
    void rejectsUnknownProduct() {
        scenario.given()
                .clock().withTime("2026-03-10T12:00:00Z")
            .and().product().withSku("MISSING-1").doesNotExist()
            .when().placeOrder().withSku("MISSING-1").withQuantity(1).withCountry("US")
            .then().shouldFail()
                .fieldErrorMessage("sku", "Product does not exist for SKU: MISSING-1");
    }
}
