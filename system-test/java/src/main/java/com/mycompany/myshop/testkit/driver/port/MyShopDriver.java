package com.mycompany.myshop.testkit.driver.port;

import com.mycompany.myshop.testkit.driver.port.dtos.error.SystemError;
import com.mycompany.myshop.testkit.driver.port.dtos.BrowseCouponsResponse;
import com.mycompany.myshop.testkit.driver.port.dtos.PlaceOrderRequest;
import com.mycompany.myshop.testkit.driver.port.dtos.PlaceOrderResponse;
import com.mycompany.myshop.testkit.driver.port.dtos.PublishCouponRequest;
import com.mycompany.myshop.testkit.driver.port.dtos.ViewOrderResponse;
import com.mycompany.myshop.testkit.common.Result;

public interface MyShopDriver extends AutoCloseable {
    Result<Void, SystemError> goToMyShop();

    Result<PlaceOrderResponse, SystemError> placeOrder(PlaceOrderRequest request);

    Result<Void, SystemError> cancelOrder(String orderNumber);

    Result<Void, SystemError> deliverOrder(String orderNumber);

    Result<ViewOrderResponse, SystemError> viewOrder(String orderNumber);

    Result<Void, SystemError> publishCoupon(PublishCouponRequest request);

    Result<BrowseCouponsResponse, SystemError> browseCoupons();
}
