package com.mycompany.myshop.testkit.dsl.port.myshop.then.steps;

import com.mycompany.myshop.testkit.dsl.port.myshop.then.steps.base.ThenStep;

public interface ThenCountry extends ThenStep<ThenCountry> {
    ThenCountry hasCountry(String country);

    ThenCountry hasTaxRate(double taxRate);

    ThenCountry hasTaxRateIsPositive();
}
