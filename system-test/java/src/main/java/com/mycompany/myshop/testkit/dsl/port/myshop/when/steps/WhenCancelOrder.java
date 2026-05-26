package com.mycompany.myshop.testkit.dsl.port.myshop.when.steps;

import com.mycompany.myshop.testkit.dsl.port.myshop.when.steps.base.WhenStep;

public interface WhenCancelOrder extends WhenStep {
    WhenCancelOrder withOrderNumber(String orderNumber);
}
