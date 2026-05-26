package com.mycompany.myshop.testkit.dsl.port.myshop.then.steps;

import com.mycompany.myshop.testkit.dsl.port.myshop.then.steps.base.ThenStep;

public interface ThenProduct extends ThenStep<ThenProduct> {
    ThenProduct hasSku(String sku);

    ThenProduct hasPrice(double price);
}
