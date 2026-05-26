package com.mycompany.myshop.testkit.dsl.port.myshop.assume;

import com.mycompany.myshop.testkit.dsl.port.myshop.assume.steps.AssumeRunning;

public interface AssumeStage {
    AssumeRunning myShop();

    AssumeRunning erp();

    AssumeRunning tax();

    AssumeRunning clock();
}
