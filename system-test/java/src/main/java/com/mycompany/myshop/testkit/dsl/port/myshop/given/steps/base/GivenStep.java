package com.mycompany.myshop.testkit.dsl.port.myshop.given.steps.base;

import com.mycompany.myshop.testkit.dsl.port.myshop.given.GivenStage;
import com.mycompany.myshop.testkit.dsl.port.myshop.then.ThenStage;
import com.mycompany.myshop.testkit.dsl.port.myshop.when.WhenStage;

public interface GivenStep {
    GivenStage and();

    WhenStage when();

    ThenStage then();
}


