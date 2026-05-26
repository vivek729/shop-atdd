package com.mycompany.myshop.testkit.dsl.port.myshop.then;

import com.mycompany.myshop.testkit.dsl.port.myshop.then.steps.ThenFailure;
import com.mycompany.myshop.testkit.dsl.port.myshop.then.steps.ThenSuccess;

public interface ThenResultStage extends ThenStage {
    ThenSuccess shouldSucceed();

    ThenFailure shouldFail();
}
