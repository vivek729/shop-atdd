# 2026-06-18 07:33:00 UTC — Sonar deferred: Java backend

**Run started:** 2026-06-18 07:33 UTC

## Target state

The single deferred issue (`java:S107`, the 15-parameter `Order` constructor) is resolved by **suppression with rationale**, not a structural refactor. When the work is done:

- Both mirrored `Order` entities carry `@SuppressWarnings("java:S107")` on the constructor, with a one-line comment stating the justification: the constructor maps one argument per persisted column of the `orders` table, so a wide parameter list is intrinsic to the entity and a builder/parameter-object would add indirection without removing inputs.
- No public-API change, no call-site change in `OrderService`, no test churn, no persistence-mapping change.
- The `.NET` and TypeScript `Order` equivalents are left unchanged (no S107 reported there); a parity note records that if they ever flag the same rule, the same suppression applies.
- On the next SonarCloud analysis the S107 issue clears via the in-code suppression.

## Resolved decisions

- **S107 — 15-parameter `Order` constructor → suppress with rationale.** Chosen over a `@Builder` (changes validation semantics / no clean cross-language parity) and over an `@Embeddable OrderPricing` value object (real persistence + call-site + test change across all three languages — too heavy for a mechanical Sonar pass). For a JPA entity mirroring a 15-column table, S107 is a weak signal and a documented exception is the proportionate response.

## java:S107 — Order constructor has 15 parameters

- **Files:**
  - `system/monolith/java/src/main/java/com/mycompany/myshop/core/entities/Order.java:81`
  - `system/multitier/backend-java/src/main/java/com/mycompany/myshop/backend/core/entities/Order.java:81`
- **Message:** Constructor has 15 parameters, which is greater than 7 authorized.
- **Context:** Public constructor on a JPA `@Entity` that maps every persisted column (orderNumber, orderTimestamp, country, sku, quantity, unitPrice, basePrice, discountRate, discountAmount, subtotalPrice, taxRate, taxAmount, totalPrice, status, appliedCouponCode) and null-validates each. Sole call site is `OrderService.placeOrder`. **Decision: suppress with rationale** (see Resolved decisions).
- **Steps:**
  1. Add `@SuppressWarnings("java:S107")` to the `Order` constructor in `system/monolith/java/.../core/entities/Order.java:81`, with a one-line comment: `// one arg per persisted orders column — wide list is intrinsic to the entity mapping`.
  2. Apply the identical change to the byte-identical mirror `system/multitier/backend-java/.../backend/core/entities/Order.java:81`.
  3. Verify with `./compile-all.sh` (or `./gradlew compileJava` in each of the two project dirs) — no behavior change expected; this is annotation-only.
  4. Parity check: confirm `.NET`/TypeScript `Order` equivalents do **not** currently flag the analogous rule; leave them untouched. If a future run flags them, apply the same suppression idiom.
  5. Commit as part of the combined Sonar run; next SonarCloud analysis auto-clears S107.
