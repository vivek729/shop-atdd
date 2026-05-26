package com.mycompany.myshop.testkit.dsl.port.myshop.when;

import com.mycompany.myshop.testkit.dsl.port.myshop.when.steps.WhenBrowseCoupons;
import com.mycompany.myshop.testkit.dsl.port.myshop.when.steps.WhenCancelOrder;
import com.mycompany.myshop.testkit.dsl.port.myshop.when.steps.WhenPlaceOrder;
import com.mycompany.myshop.testkit.dsl.port.myshop.when.steps.WhenPublishCoupon;
import com.mycompany.myshop.testkit.dsl.port.myshop.when.steps.WhenViewOrder;

public interface WhenStage {
    WhenPlaceOrder placeOrder();

    WhenCancelOrder cancelOrder();

    WhenViewOrder viewOrder();

    WhenPublishCoupon publishCoupon();

    WhenBrowseCoupons browseCoupons();
}
