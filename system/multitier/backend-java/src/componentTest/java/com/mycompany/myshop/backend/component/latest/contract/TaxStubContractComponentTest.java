package com.mycompany.myshop.backend.component.latest.contract;

import com.mycompany.myshop.backend.AbstractComponentTest;
import org.junit.jupiter.api.Test;

/**
 * Pins that the component layer's Tax WireMock stub is CONSUMABLE BY THE SUT. The read-back goes
 * through the SUT's production {@code TaxGateway} (real HTTP + real {@code TaxDetailsResponse}
 * parse), so a field drift in {@code TaxStubDriver} fails this test rather than silently yielding an
 * empty tax rate. See {@link ErpStubContractComponentTest} for the full rationale.
 */
class TaxStubContractComponentTest extends AbstractComponentTest {

    @Test
    void stubTaxIsConsumableBySut() {
        scenario
            .given().country().withCode("US").withTaxRate(0.09)
            .then().country("US").hasTaxRate(0.09);
    }
}
