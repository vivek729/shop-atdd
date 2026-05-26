package com.mycompany.myshop.testkit.dsl.port.myshop.then.steps;

import com.mycompany.myshop.testkit.dsl.port.myshop.then.steps.base.ThenStep;

public interface ThenFailure extends ThenStep<ThenFailure> {
    ThenFailure errorMessage(String expectedMessage);

    ThenFailure fieldErrorMessage(String expectedField, String expectedMessage);
}

