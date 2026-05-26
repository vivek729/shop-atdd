package com.mycompany.myshop.testkit.dsl.port.myshop.when.steps;

import com.mycompany.myshop.testkit.dsl.port.myshop.when.steps.base.WhenStep;

public interface WhenViewOrder extends WhenStep {
    WhenViewOrder withOrderNumber(String orderNumber);
}

