package com.mycompany.myshop.backend.support.port.then;

import com.mycompany.myshop.backend.support.port.then.steps.ThenCoupon;
import com.mycompany.myshop.backend.support.port.then.steps.ThenOrder;
import com.mycompany.myshop.backend.support.port.then.steps.ThenOrderHistory;

/**
 * State the scenario can assert without having executed an action — reached from {@code
 * given().then()}. Every step here reads the SUT back through its own API.
 *
 * <p>System-test's {@code ThenStage} instead offers {@code clock()}, {@code product()} and {@code
 * country()}: read-backs of the external systems. Those are dropped here — at component level the
 * externals are WireMock stubs the test just programmed, so reading them back would assert the stub,
 * not the system.
 */
public interface ThenStage {
    ThenOrder order(String orderNumber);

    ThenCoupon coupon(String couponCode);

    ThenOrderHistory orderHistory();
}
