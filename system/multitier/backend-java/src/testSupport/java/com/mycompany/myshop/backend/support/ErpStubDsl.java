package com.mycompany.myshop.backend.support;

/**
 * Fluent facade over {@link ErpStubDriver} for stubbing the ERP external system:
 *
 * <pre>{@code
 * erpStub.returnsProduct().withSku("BOOK-123").withUnitPrice("10.00").execute();
 * erpStub.returnsNoProduct().withSku("MISSING-1").execute();
 * erpStub.returnsPromotion().withActive(true).withDiscount("0.9").execute();
 * }</pre>
 *
 * <p>Prices and discounts are passed as {@code String} so the stubbed JSON is byte-identical to the
 * raw WireMock the {@code legacy/} tests inline.
 */
public class ErpStubDsl {

    private final ErpStubDriver driver;

    public ErpStubDsl(ErpStubDriver driver) {
        this.driver = driver;
    }

    public ProductStub returnsProduct() {
        return new ProductStub();
    }

    public MissingProductStub returnsNoProduct() {
        return new MissingProductStub();
    }

    public PromotionStub returnsPromotion() {
        return new PromotionStub();
    }

    public final class ProductStub {
        private String sku;
        private String unitPrice;

        public ProductStub withSku(String sku) {
            this.sku = sku;
            return this;
        }

        public ProductStub withUnitPrice(String unitPrice) {
            this.unitPrice = unitPrice;
            return this;
        }

        public void execute() {
            driver.stubProduct(sku, unitPrice);
        }
    }

    public final class MissingProductStub {
        private String sku;

        public MissingProductStub withSku(String sku) {
            this.sku = sku;
            return this;
        }

        public void execute() {
            driver.stubProductMissing(sku);
        }
    }

    public final class PromotionStub {
        private boolean active;
        private String discount;

        public PromotionStub withActive(boolean active) {
            this.active = active;
            return this;
        }

        public PromotionStub withDiscount(String discount) {
            this.discount = discount;
            return this;
        }

        public void execute() {
            driver.stubPromotion(active, discount);
        }
    }
}
