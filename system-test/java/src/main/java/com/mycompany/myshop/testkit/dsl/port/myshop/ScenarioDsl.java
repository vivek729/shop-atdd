package com.mycompany.myshop.testkit.dsl.port.myshop;

import com.mycompany.myshop.testkit.dsl.port.myshop.assume.AssumeStage;
import com.mycompany.myshop.testkit.dsl.port.myshop.given.GivenStage;
import com.mycompany.myshop.testkit.dsl.port.myshop.when.WhenStage;

public interface ScenarioDsl {
    AssumeStage assume();

    GivenStage given();

    WhenStage when();
}