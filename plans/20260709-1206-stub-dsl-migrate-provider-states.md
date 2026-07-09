# 2026-07-09 12:06:04 UTC — Retire the inline `stub*` helpers: migrate provider states onto the stub DSLs (collaborator-side symmetry)

## TL;DR

**Why:** The ERP / Tax / Clock stub Driver+DSL pairs already exist under `support/` and the `latest/` component tests already drive the collaborators through them. But `AbstractComponentTest` still carries five inline `stub*` static helpers (`stubClock`, `stubProduct`, `stubProductMissing`, `stubPromotion`, `stubTax`) doing raw WireMock — byte-identical duplicates of what the stub drivers own (their own Javadoc says so). The **only** remaining consumer is `BackendPactVerificationTest`'s `@State` methods; `stubProductMissing` has **no** consumer at all (dead code). Meanwhile each `latest/` test **self-wires** its own `erpStub`/`taxStub`/`clockStub` private fields — the same three-line block duplicated per class. This is the collaborator-side twin of the just-completed SUT-side extraction (`20260709-1104`): the SUT is now driven through the shared `backend` DSL wired into the base, but the stub DSLs are neither wired into the base nor used by the contract test.

**End result:** The stub DSLs are wired once into `AbstractComponentTest` (`protected final erpStub/taxStub/clockStub`, field-initialized from the static `ERP/TAX/CLOCK` ports — symmetric with how `backend` is wired, minus the `@BeforeEach` since the WireMock ports are available at field-init unlike the autowired `restTemplate`). `BackendPactVerificationTest`'s provider states drive the collaborators through those DSLs; the `latest/` tests drop their duplicated private stub-DSL fields and inherit the shared ones. All five `stub*` helpers are deleted from `AbstractComponentTest`. `legacy/` tests keep their raw inline WireMock untouched (the pedagogical "before"). Every party in a component/contract test — SUT and all three collaborators — is now reached through the same four-layer support abstraction, except the deliberately-raw `legacy/` twin.

**Scope:** Java backend only (`system/multitier/backend-java`). TS / .NET parity out of scope (possible follow-up).

## Outcomes

- `AbstractComponentTest` exposes `protected final ErpStubDsl erpStub`, `TaxStubDsl taxStub`, `ClockStubDsl clockStub`, each field-initialized from `ERP.port()` / `TAX.port()` / `CLOCK.port()`. Shared by every subclass and the Pact verifier.
- `BackendPactVerificationTest`'s `@State` methods call `erpStub.returnsProduct()...` / `taxStub.returnsRate()...` / `clockStub.returnsTime(...)` instead of the inline `stub*` helpers.
- `latest/PlaceOrderComponentTest` and `latest/OrderHistoryComponentTest` delete their private `erpStub`/`taxStub`/`clockStub` field declarations and use the inherited ones (no other test-body changes).
- All five `stub*` static helpers — including the dead `stubProductMissing` — are removed from `AbstractComponentTest`, along with the now-unused WireMock static imports (`aResponse`, `get`, `okJson`, `urlEqualTo`) **if** no remaining code in that file references them.
- `legacy/` twins are byte-for-byte unchanged — raw inline WireMock preserved as the contrast.
- Behaviour-neutral: same stubbed responses, same provider states, same assertions. `./gradlew build` in `backend-java` green (component + contract/Pact verification).

## ▶ Next executable step (resume here)

In `AbstractComponentTest`, add the three `protected final` stub-DSL fields initialized from the static `ERP/TAX/CLOCK` ports (mirror the field-init block currently in `latest/PlaceOrderComponentTest:31-36`, moved up to the base). Gate: `./gradlew build` still green with the helpers still present (additive step). Then migrate consumers and delete the helpers.

## Steps

- [ ] Step 1: Wire the stub DSLs into `AbstractComponentTest` — add `protected final ErpStubDsl erpStub = new ErpStubDsl(new ErpStubDriver(new WireMock("localhost", ERP.port())));` and the `taxStub` / `clockStub` equivalents, with the needed `support.*` and `com.github.tomakehurst.wiremock.client.WireMock` imports. Keep the `stub*` helpers in place for now (additive). Add a short Javadoc note mirroring the `backend` field's, explaining these are field-initialized (ports available at class load) unlike `backend`.
- [ ] Step 2: Migrate `BackendPactVerificationTest` `@State` methods off the helpers:
  - `stubClock(t)` → `clockStub.returnsTime(t)`
  - `stubProduct(sku, price)` → `erpStub.returnsProduct().withSku(sku).withUnitPrice(price).execute()`
  - `stubPromotion(active, discount)` → `erpStub.returnsPromotion().withActive(active).withDiscount(discount).execute()`
  - `stubTax(country, rate)` → `taxStub.returnsRate().withCountry(country).withRate(rate).execute()`
- [ ] Step 3: Delete the private `erpStub`/`taxStub`/`clockStub` field declarations from `latest/PlaceOrderComponentTest` and `latest/OrderHistoryComponentTest` (and their now-unused `support.*StubDriver` / `WireMock` imports); the test bodies already call `erpStub.`/`taxStub.`/`clockStub.` and now resolve them from the base.
- [ ] Step 4: Delete all five `stub*` helpers from `AbstractComponentTest`, plus the now-orphaned WireMock static imports (`aResponse`, `get`, `okJson`, `urlEqualTo`) — verify nothing else in the file uses them before removing.
- [ ] Step 5: Confirm `legacy/PlaceOrderComponentTest` and `legacy/OrderHistoryComponentTest` are untouched (raw inline WireMock intact) and their class Javadoc still reads correctly.
- [ ] Step 6: Compile + test: `./gradlew build` in `system/multitier/backend-java`. All component tests and the Pact provider verification (7 interactions) green.

## Decisions (settled)

- **Wiring:** hoist the stub DSLs into `AbstractComponentTest` as `protected final` field initializers (not `@BeforeEach`) — the `ERP/TAX/CLOCK` WireMock servers are static and started in the class's static initializer, so their ports are available at instance-field-init time. This differs from `backend` (which needs the autowired `restTemplate`, only populated by `@BeforeEach`); the asymmetry is intrinsic and already noted on the `backend` field. This removes the per-class field-declaration duplication in the `latest/` tests as a bonus.
- **Legacy stays raw:** `legacy/` twins keep inline WireMock — the whole point of the twin is the raw-vs-DSL contrast. Not migrated.
- **Scope:** Java backend only.

## Open questions

- **Reset ordering.** The stub DSLs register mappings via a `WireMock` client; `AbstractComponentTest.resetComponentState()` calls `ERP/TAX/CLOCK.resetAll()` in `@BeforeEach`. Field-initialized DSL instances are constructed once per test instance and reused across the reset — confirm during Step 1 that a client created at field-init still registers correctly after `resetAll()` (the `latest/` tests already do exactly this, so this should be a non-issue; verify, don't assume).

## Follow-ups (out of scope here)

- **TS / .NET parity.** Check whether the TypeScript and .NET component/contract harnesses have the same inline-stub-helper duplication and, if so, apply the equivalent migration. (See the component/Pact harness notes — TS uses a shared in-process harness with real HTTP stub servers.)
- **Assert GET status on the happy path** (carried over from `20260709-1104`) — unrelated to this migration; still tracked separately.
