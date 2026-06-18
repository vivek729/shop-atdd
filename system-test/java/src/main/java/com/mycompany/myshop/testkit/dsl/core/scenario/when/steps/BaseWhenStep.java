package com.mycompany.myshop.testkit.dsl.core.scenario.when.steps;

import com.mycompany.myshop.testkit.dsl.core.shared.ResponseVerification;
import com.mycompany.myshop.testkit.dsl.core.usecase.UseCaseDsl;
import com.mycompany.myshop.testkit.dsl.core.scenario.ExecutionResult;
import com.mycompany.myshop.testkit.dsl.core.scenario.then.ThenResultImpl;

public abstract class BaseWhenStep<R, V extends ResponseVerification<R>> {
    private final UseCaseDsl app;

    protected BaseWhenStep(UseCaseDsl app) {
        this.app = app;
    }
    public ThenResultImpl<R, V> then() {
        var result = execute(app);
        return new ThenResultImpl<>(app, result);
    }

    protected abstract ExecutionResult<R, V> execute(UseCaseDsl app);
}



