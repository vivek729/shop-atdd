package com.mycompany.myshop.testkit.dsl.core.usecase.usecases.base;

import com.mycompany.myshop.testkit.driver.port.MyShopDriver;
import com.mycompany.myshop.testkit.dsl.core.shared.BaseUseCase;
import com.mycompany.myshop.testkit.dsl.core.shared.UseCaseContext;

public abstract class BaseMyShopUseCase<R, V> extends BaseUseCase<MyShopDriver, R, V> {
    protected BaseMyShopUseCase(MyShopDriver driver, UseCaseContext context) {
        super(driver, context);
    }
}



