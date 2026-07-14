package com.mycompany.myshop.backend.support.port.when.steps;

import com.mycompany.myshop.backend.support.port.when.steps.base.WhenStep;

public interface WhenPlaceOrder extends WhenStep {
    WhenPlaceOrder withSku(String sku);

    WhenPlaceOrder withQuantity(int quantity);

    WhenPlaceOrder withCountry(String country);

    WhenPlaceOrder withCouponCode(String couponCode);

    /** Uses the default coupon code — the one {@code given().coupon()} publishes when left unstated. */
    WhenPlaceOrder withCouponCode();
}
