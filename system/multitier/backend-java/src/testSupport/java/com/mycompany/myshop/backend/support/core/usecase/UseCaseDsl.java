package com.mycompany.myshop.backend.support.core.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mycompany.myshop.backend.support.BackendDriver;
import com.mycompany.myshop.backend.support.ClockStubDriver;
import com.mycompany.myshop.backend.support.ErpStubDriver;
import com.mycompany.myshop.backend.support.TaxStubDriver;
import com.mycompany.myshop.backend.support.core.usecase.external.clock.ClockDsl;
import com.mycompany.myshop.backend.support.core.usecase.external.erp.ErpDsl;
import com.mycompany.myshop.backend.support.core.usecase.external.tax.TaxDsl;

/**
 * Root of the use case layer: one entry per actor in a component test — the system under test
 * ({@link #myShop()}) and the three external systems it talks to ({@link #erp()}, {@link #tax()},
 * {@link #clock()}).
 *
 * <p>This is the layer the scenario DSL is built on, and it stays exposed: a test that needs
 * surgical control can drop to it directly instead of bending a scenario around an edge case.
 */
public class UseCaseDsl {

    private final MyShopDsl myShop;
    private final ErpDsl erp;
    private final TaxDsl tax;
    private final ClockDsl clock;

    public UseCaseDsl(
            BackendDriver backendDriver,
            ObjectMapper objectMapper,
            ErpStubDriver erpStubDriver,
            TaxStubDriver taxStubDriver,
            ClockStubDriver clockStubDriver) {
        this.myShop = new MyShopDsl(backendDriver, objectMapper);
        this.erp = new ErpDsl(erpStubDriver);
        this.tax = new TaxDsl(taxStubDriver);
        this.clock = new ClockDsl(clockStubDriver);
    }

    public MyShopDsl myShop() {
        return myShop;
    }

    public ErpDsl erp() {
        return erp;
    }

    public TaxDsl tax() {
        return tax;
    }

    public ClockDsl clock() {
        return clock;
    }
}
