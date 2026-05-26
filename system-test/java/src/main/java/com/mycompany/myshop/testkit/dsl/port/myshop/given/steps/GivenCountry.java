package com.mycompany.myshop.testkit.dsl.port.myshop.given.steps;

import com.mycompany.myshop.testkit.dsl.port.myshop.given.steps.base.GivenStep;

public interface GivenCountry extends GivenStep {
    GivenCountry withCode(String country);

    GivenCountry withTaxRate(String taxRate);

    GivenCountry withTaxRate(double taxRate);
}
