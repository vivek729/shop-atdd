package com.mycompany.myshop.backend.support.core.usecase.external.tax.usecases.base;

import com.mycompany.myshop.backend.support.TaxStubDriver;
import com.mycompany.myshop.backend.support.core.shared.BaseStubUseCase;

public abstract class BaseTaxUseCase extends BaseStubUseCase<TaxStubDriver> {

    protected BaseTaxUseCase(TaxStubDriver driver) {
        super(driver);
    }
}
