package com.mycompany.myshop.backend.component.latest;

import static org.assertj.core.api.Assertions.assertThat;

import com.mycompany.myshop.backend.AbstractComponentTest;
import com.mycompany.myshop.backend.core.dtos.BrowseCouponsResponse;
import org.junit.jupiter.api.Test;

/**
 * "After" of the SUT-side driver refactor: the publish + browse coupon flow driven through the shared
 * {@code backend} DSL ({@link com.mycompany.myshop.backend.support.BackendDsl}) instead of raw
 * {@code restTemplate} calls. Identical scenario and assertions to the {@code legacy/} twin.
 *
 * <p>Coupon touches no external systems, so — unlike the order twins, whose {@code latest/} vs
 * {@code legacy/} axis is external-stub style (stub DSL vs raw WireMock) — this pair's only contrast
 * is SUT-side: the {@code backend} DSL here vs raw {@code restTemplate} in {@code legacy/}. Publish
 * returns 204 No Content with no body, which {@code publishExpectingSuccess()} asserts.
 */
class CouponComponentTest extends AbstractComponentTest {

    @Test
    void publishReturnsNoContentThenBrowseListsCoupon() {
        backend.publishCoupon()
            .withCode("SAVE10").withDiscountRate("0.20").withUsageLimit(100)
            .publishExpectingSuccess();

        var coupons = backend.browseCoupons();

        assertThat(coupons.getCoupons())
            .extracting(BrowseCouponsResponse.BrowseCouponsItemResponse::getCode)
            .contains("SAVE10");
    }
}
