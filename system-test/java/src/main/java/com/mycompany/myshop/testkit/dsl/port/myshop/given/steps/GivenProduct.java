package com.mycompany.myshop.testkit.dsl.port.myshop.given.steps;

import com.mycompany.myshop.testkit.dsl.port.myshop.given.steps.base.GivenStep;

public interface GivenProduct extends GivenStep {
    GivenProduct withSku(String sku);

    GivenProduct withUnitPrice(String unitPrice);

    GivenProduct withUnitPrice(double unitPrice);
}
