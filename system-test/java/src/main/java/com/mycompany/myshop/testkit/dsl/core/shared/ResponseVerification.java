package com.mycompany.myshop.testkit.dsl.core.shared;

public class ResponseVerification<R> {
    private final R response;
    private final UseCaseContext context;

    public ResponseVerification(R response, UseCaseContext context) {
        this.response = response;
        this.context = context;
    }

    protected R getResponse() {
        return response;
    }

    protected UseCaseContext getContext() {
        return context;
    }
}



