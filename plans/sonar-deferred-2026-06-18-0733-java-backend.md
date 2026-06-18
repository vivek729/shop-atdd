**Run started:** 2026-06-18 07:33 UTC

## java:S107 — Order constructor has 15 parameters

- **Files:**
  - `system/monolith/java/src/main/java/com/mycompany/myshop/core/entities/Order.java:81`
  - `system/multitier/backend-java/src/main/java/com/mycompany/myshop/backend/core/entities/Order.java:81`
- **Message:** Constructor has 15 parameters, which is greater than 7 authorized.
- **What I tried:** Reviewed the constructor. It is a public constructor on a JPA `@Entity` that maps every persisted column (orderNumber, orderTimestamp, country, sku, quantity, unitPrice, basePrice, discountRate, discountAmount, subtotalPrice, taxRate, taxAmount, totalPrice, status, appliedCouponCode) and performs null-validation on each.
- **Open question:** Reducing the parameter count means a public-API change — either introduce a builder, a parameter object (e.g. an `OrderPricing` / `OrderDetails` value object), or split construction. This affects the call site in `OrderService` (and mirrored backend) plus any unit tests, and must be applied identically across both mirrored projects (and likely the .NET/TypeScript equivalents to keep parity). Deferred because the prompt says not to change public API lightly and this needs a design decision on the preferred refactor shape.
