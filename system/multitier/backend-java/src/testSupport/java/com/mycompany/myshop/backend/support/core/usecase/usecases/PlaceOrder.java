package com.mycompany.myshop.backend.support.core.usecase.usecases;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mycompany.myshop.backend.core.dtos.PlaceOrderRequest;
import com.mycompany.myshop.backend.core.dtos.PlaceOrderResponse;
import com.mycompany.myshop.backend.support.BackendDriver;
import com.mycompany.myshop.backend.support.core.shared.ResponseParser;
import com.mycompany.myshop.backend.support.core.shared.UseCaseResult;
import com.mycompany.myshop.backend.support.core.usecase.usecases.base.BaseMyShopUseCase;
import org.springframework.http.HttpStatus;

/**
 * {@code POST /api/orders} — accepted with {@code 201 CREATED} carrying the order number, rejected
 * with {@code 422 UNPROCESSABLE_ENTITY} carrying a {@code ProblemDetail}. Placing returns only the
 * order number; to inspect the persisted totals, read the order back with {@link ViewOrder}.
 */
public class PlaceOrder extends BaseMyShopUseCase<PlaceOrderResponse, PlaceOrderVerification> {

    private final ObjectMapper objectMapper;
    private String sku;
    private int quantity;
    private String country;
    private String couponCode;

    public PlaceOrder(BackendDriver driver, ObjectMapper objectMapper) {
        super(driver);
        this.objectMapper = objectMapper;
    }

    public PlaceOrder sku(String sku) {
        this.sku = sku;
        return this;
    }

    public PlaceOrder quantity(int quantity) {
        this.quantity = quantity;
        return this;
    }

    public PlaceOrder country(String country) {
        this.country = country;
        return this;
    }

    public PlaceOrder couponCode(String couponCode) {
        this.couponCode = couponCode;
        return this;
    }

    @Override
    public UseCaseResult<PlaceOrderResponse, PlaceOrderVerification> execute() {
        var request = new PlaceOrderRequest();
        request.setSku(sku);
        request.setQuantity(quantity);
        request.setCountry(country);
        request.setCouponCode(couponCode);

        var response = driver.placeOrder(request);

        return new UseCaseResult<>(
            response.getStatusCode(),
            HttpStatus.CREATED,
            HttpStatus.UNPROCESSABLE_ENTITY,
            () -> ResponseParser.parseSuccess(response, PlaceOrderResponse.class, objectMapper),
            () -> ResponseParser.parseRejection(response, objectMapper),
            PlaceOrderVerification::new);
    }
}
