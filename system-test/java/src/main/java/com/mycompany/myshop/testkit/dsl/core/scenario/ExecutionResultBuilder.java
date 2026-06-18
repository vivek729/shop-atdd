package com.mycompany.myshop.testkit.dsl.core.scenario;

import com.mycompany.myshop.testkit.dsl.core.shared.UseCaseResult;
import com.mycompany.myshop.testkit.dsl.core.shared.ResponseVerification;

public class ExecutionResultBuilder<R, V extends ResponseVerification<R>> {
    private final UseCaseResult<R, V> result;
    private String orderNumber;
    private String couponCode;

    public ExecutionResultBuilder(UseCaseResult<R, V> result) {
        this.result = result;
    }

    public ExecutionResultBuilder<R, V> orderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
        return this;
    }

    public ExecutionResultBuilder<R, V> couponCode(String couponCode) {
        this.couponCode = couponCode;
        return this;
    }

    public ExecutionResult<R, V> build() {
        return new ExecutionResult<>(result, orderNumber, couponCode);
    }
}
