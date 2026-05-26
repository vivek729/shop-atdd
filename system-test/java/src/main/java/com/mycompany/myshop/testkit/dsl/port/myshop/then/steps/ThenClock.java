package com.mycompany.myshop.testkit.dsl.port.myshop.then.steps;

import com.mycompany.myshop.testkit.dsl.port.myshop.then.steps.base.ThenStep;

public interface ThenClock extends ThenStep<ThenClock> {
    ThenClock hasTime(String time);

    ThenClock hasTime();
}

