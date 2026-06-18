package com.mycompany.myshop.testkit.dsl.core.usecase.external.tax.usecases.base;

import com.mycompany.myshop.testkit.dsl.core.shared.BaseUseCase;
import com.mycompany.myshop.testkit.dsl.core.shared.UseCaseContext;
import com.mycompany.myshop.testkit.driver.port.external.tax.TaxDriver;

public abstract class BaseTaxUseCase<R, V> extends BaseUseCase<TaxDriver, R, V> {
    protected BaseTaxUseCase(TaxDriver driver, UseCaseContext context) {
        super(driver, context);
    }
}
