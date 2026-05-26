package com.mycompany.myshop.testkit.dsl.port.myshop.then.steps.base;

import com.mycompany.myshop.testkit.dsl.port.myshop.then.steps.ThenClock;
import com.mycompany.myshop.testkit.dsl.port.myshop.then.steps.ThenCountry;
import com.mycompany.myshop.testkit.dsl.port.myshop.then.steps.ThenCoupon;
import com.mycompany.myshop.testkit.dsl.port.myshop.then.steps.ThenOrder;
import com.mycompany.myshop.testkit.dsl.port.myshop.then.steps.ThenProduct;

public interface ThenStep<TThen> {
    TThen and();

    ThenOrder order();

    ThenOrder order(String orderNumber);

    ThenCoupon coupon();

    ThenCoupon coupon(String couponCode);

    ThenClock clock();

    ThenProduct product(String skuAlias);

    ThenCountry country(String countryAlias);
}
