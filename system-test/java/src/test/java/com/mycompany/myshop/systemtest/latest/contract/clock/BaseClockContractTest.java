package com.mycompany.myshop.systemtest.latest.contract.clock;

import com.mycompany.myshop.systemtest.latest.contract.base.BaseExternalSystemContractTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public abstract class BaseClockContractTest extends BaseExternalSystemContractTest {
    @Test
    void shouldBeAbleToGetTime() {
        assertDoesNotThrow(() -> scenario
                .given()
                .then().clock().hasTime());
    }
}
