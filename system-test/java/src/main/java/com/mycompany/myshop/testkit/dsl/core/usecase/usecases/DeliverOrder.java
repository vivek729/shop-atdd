package com.mycompany.myshop.testkit.dsl.core.usecase.usecases;

import com.mycompany.myshop.testkit.driver.port.MyShopDriver;
import com.mycompany.myshop.testkit.driver.port.dtos.DeliverOrderRequest;
import com.mycompany.myshop.testkit.dsl.core.usecase.usecases.base.BaseMyShopUseCase;
import com.mycompany.myshop.testkit.dsl.core.shared.UseCaseResult;
import com.mycompany.myshop.testkit.dsl.core.shared.UseCaseContext;
import com.mycompany.myshop.testkit.dsl.core.shared.VoidVerification;

public class DeliverOrder extends BaseMyShopUseCase<Void, VoidVerification> {
    private String orderNumberResultAlias;

    public DeliverOrder(MyShopDriver driver, UseCaseContext context) {
        super(driver, context);
    }

    public DeliverOrder orderNumber(String orderNumberResultAlias) {
        this.orderNumberResultAlias = orderNumberResultAlias;
        return this;
    }

    @Override
    public UseCaseResult<Void, VoidVerification> execute() {
        var orderNumber = context.getResultValue(orderNumberResultAlias);
        var request = DeliverOrderRequest.builder().orderNumber(orderNumber).build();
        var result = driver.deliverOrder(request).mapVoid();
        return new UseCaseResult<>(result, context, VoidVerification::new);
    }
}
