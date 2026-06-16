package com.mycompany.myshop.backend.component;

import static org.assertj.core.api.Assertions.assertThat;

import com.mycompany.myshop.backend.AbstractComponentTest;
import com.mycompany.myshop.backend.core.dtos.BrowseCouponsResponse;
import com.mycompany.myshop.backend.core.dtos.PublishCouponRequest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Publish + browse coupon flows. Note publish returns 204 No Content with no body — this is the
 * real contract the system tests verify, and the reason the frontend consumer pact's publish-coupon
 * interaction (which expects 201 + {code}) is excluded from provider verification pending a
 * frontend fix.
 */
class CouponComponentTest extends AbstractComponentTest {

    @Test
    void publishReturnsNoContentThenBrowseListsCoupon() {
        var request = new PublishCouponRequest();
        request.setCode("SAVE10");
        request.setDiscountRate(new BigDecimal("0.20"));
        request.setUsageLimit(100);

        ResponseEntity<Void> publish =
            restTemplate.postForEntity("/api/coupons", request, Void.class);
        assertThat(publish.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<BrowseCouponsResponse> browse =
            restTemplate.getForEntity("/api/coupons", BrowseCouponsResponse.class);
        assertThat(browse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(browse.getBody()).isNotNull();
        assertThat(browse.getBody().getCoupons())
            .extracting(BrowseCouponsResponse.BrowseCouponsItemResponse::getCode)
            .contains("SAVE10");
    }
}
