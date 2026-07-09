package com.mycompany.myshop.backend.support;

/**
 * Fluent facade over {@link TaxStubDriver} for stubbing the Tax external system:
 *
 * <pre>{@code
 * taxStub.returnsRate().withCountry("US").withRate("0.10").execute();
 * }</pre>
 *
 * <p>Rates are passed as {@code String} so the stubbed JSON is byte-identical to the raw WireMock the
 * {@code legacy/} tests inline.
 */
public class TaxStubDsl {

    private final TaxStubDriver driver;

    public TaxStubDsl(TaxStubDriver driver) {
        this.driver = driver;
    }

    public RateStub returnsRate() {
        return new RateStub();
    }

    public final class RateStub {
        private String country;
        private String rate;

        public RateStub withCountry(String country) {
            this.country = country;
            return this;
        }

        public RateStub withRate(String rate) {
            this.rate = rate;
            return this;
        }

        public void execute() {
            driver.stubTax(country, rate);
        }
    }
}
