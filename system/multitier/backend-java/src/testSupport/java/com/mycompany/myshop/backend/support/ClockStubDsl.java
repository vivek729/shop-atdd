package com.mycompany.myshop.backend.support;

/**
 * Fluent facade over {@link ClockStubDriver} for stubbing the Clock external system:
 *
 * <pre>{@code
 * clockStub.returnsTime("2026-03-10T12:00:00Z").execute();
 * }</pre>
 */
public class ClockStubDsl {

    private final ClockStubDriver driver;

    public ClockStubDsl(ClockStubDriver driver) {
        this.driver = driver;
    }

    public TimeStub returnsTime(String isoInstant) {
        return new TimeStub(isoInstant);
    }

    public final class TimeStub {
        private final String isoInstant;

        private TimeStub(String isoInstant) {
            this.isoInstant = isoInstant;
        }

        public void execute() {
            driver.stubTime(isoInstant);
        }
    }
}
