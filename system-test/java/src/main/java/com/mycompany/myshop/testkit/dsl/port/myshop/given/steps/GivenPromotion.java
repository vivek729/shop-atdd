package com.mycompany.myshop.testkit.dsl.port.myshop.given.steps;

import com.mycompany.myshop.testkit.dsl.port.myshop.given.steps.base.GivenStep;

public interface GivenPromotion extends GivenStep {
    GivenPromotion withActive(boolean promotionActive);
    GivenPromotion withDiscount(double discount);
    GivenPromotion withDiscount(String discount);
}
