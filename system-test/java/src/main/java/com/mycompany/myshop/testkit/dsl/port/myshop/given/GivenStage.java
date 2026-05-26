package com.mycompany.myshop.testkit.dsl.port.myshop.given;

import com.mycompany.myshop.testkit.dsl.port.myshop.given.steps.GivenClock;
import com.mycompany.myshop.testkit.dsl.port.myshop.given.steps.GivenCoupon;
import com.mycompany.myshop.testkit.dsl.port.myshop.given.steps.GivenCountry;
import com.mycompany.myshop.testkit.dsl.port.myshop.given.steps.GivenOrder;
import com.mycompany.myshop.testkit.dsl.port.myshop.given.steps.GivenProduct;
import com.mycompany.myshop.testkit.dsl.port.myshop.given.steps.GivenPromotion;
import com.mycompany.myshop.testkit.dsl.port.myshop.then.ThenStage;
import com.mycompany.myshop.testkit.dsl.port.myshop.when.WhenStage;

public interface GivenStage {
    GivenClock clock();

    GivenProduct product();

    GivenPromotion promotion();

    GivenOrder order();

    GivenCountry country();

    GivenCoupon coupon();

    WhenStage when();

    ThenStage then();
}
