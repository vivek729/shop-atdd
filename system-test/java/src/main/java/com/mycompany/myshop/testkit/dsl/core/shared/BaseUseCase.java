package com.mycompany.myshop.testkit.dsl.core.shared;

public abstract class BaseUseCase<D, R, V> implements UseCase<UseCaseResult<R, V>> {
    protected final D driver;
    protected final UseCaseContext context;

    protected BaseUseCase(D driver, UseCaseContext context) {
        this.driver = driver;
        this.context = context;
    }
}



