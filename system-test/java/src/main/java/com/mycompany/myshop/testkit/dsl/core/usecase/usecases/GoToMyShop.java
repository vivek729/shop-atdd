package com.mycompany.myshop.testkit.dsl.core.usecase.usecases;

import com.mycompany.myshop.testkit.driver.port.MyShopDriver;
import com.mycompany.myshop.testkit.driver.port.dtos.GoToMyShopRequest;
import com.mycompany.myshop.testkit.dsl.core.usecase.usecases.base.BaseMyShopUseCase;
import com.mycompany.myshop.testkit.dsl.core.shared.UseCaseResult;
import com.mycompany.myshop.testkit.dsl.core.shared.UseCaseContext;
import com.mycompany.myshop.testkit.dsl.core.shared.VoidVerification;

public class GoToMyShop extends BaseMyShopUseCase<Void, VoidVerification> {
    public GoToMyShop(MyShopDriver driver, UseCaseContext context) {
        super(driver, context);
    }

    @Override
    public UseCaseResult<Void, VoidVerification> execute() {
        var request = GoToMyShopRequest.builder().build();
        var result = driver.goToMyShop(request).mapVoid();
        return new UseCaseResult<>(result, context, VoidVerification::new);
    }
}



