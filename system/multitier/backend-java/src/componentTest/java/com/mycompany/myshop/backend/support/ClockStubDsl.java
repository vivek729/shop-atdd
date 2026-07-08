package com.mycompany.myshop.backend.support;

/**
 * Fluent facade over {@link ClockStubDriver} for stubbing the Clock external system:
 *
 * <pre>{@code
 * clockStub.returnsTime("2026-03-10T12:00:00Z");
 * }</pre>
 */
public class ClockStubDsl {

    private final ClockStubDriver driver;

    public ClockStubDsl(ClockStubDriver driver) {
        this.driver = driver;
    }

    public void returnsTime(String isoInstant) {
        driver.stubTime(isoInstant);
    }
}
