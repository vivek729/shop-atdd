package com.mycompany.myshop.testkit.dsl.port.myshop.then;

import com.mycompany.myshop.testkit.dsl.port.myshop.then.steps.ThenClock;
import com.mycompany.myshop.testkit.dsl.port.myshop.then.steps.ThenCountry;
import com.mycompany.myshop.testkit.dsl.port.myshop.then.steps.ThenProduct;

public interface ThenStage {
    ThenClock clock();

    ThenProduct product(String skuAlias);

    ThenCountry country(String countryAlias);
}
