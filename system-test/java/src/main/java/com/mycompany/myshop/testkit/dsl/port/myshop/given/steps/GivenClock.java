package com.mycompany.myshop.testkit.dsl.port.myshop.given.steps;

import com.mycompany.myshop.testkit.dsl.port.myshop.given.steps.base.GivenStep;

public interface GivenClock extends GivenStep {
    GivenClock withTime();
    GivenClock withTime(String time);
    GivenClock withWeekday();
    GivenClock withWeekend();
}

