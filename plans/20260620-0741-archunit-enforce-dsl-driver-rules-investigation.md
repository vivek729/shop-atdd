# 2026-06-20 07:41 UTC — Investigation: can ArchUnit enforce the DSL/driver rules we currently hand to Claude?

> 🤖 **Picked up by agent (refine)** — `ValentinaLaptop` at `2026-06-20T06:30:08Z`

## TL;DR

**Why:** Today the `system-test` architecture rules (DSL-core, DSL-port, driver-port, driver-adapter, test-file conventions) live as **prose guidance for Claude** — in the archived reference docs and the runtime agent prompts. Nothing fails the build when a rule is broken; we rely on the LLM remembering and on review. We want to know **which of these rules are mechanically enforceable in CI** (so a violation goes red on its own) and which genuinely need an LLM or a different tool. The user's two motivating examples: *(1)* requests and responses across DSL and drivers should be identical, and *(2)* every DSL/driver method should have a request and a response.

**End result of this investigation:** A **feasibility matrix** classifying every documented DSL/driver rule into `ArchUnit-native` / `ArchUnit custom-condition` / `partial (body inspection)` / `not-ArchUnit (needs AST / string-literal / LLM)`, **backed by a runnable proof-of-concept** `ArchitectureRulesTest` in `system-test/java` that implements ~4 representative rules across that spectrum and is shown going red against a deliberate violation. Plus a recommendation on rollout (which rules to adopt as ArchUnit tests, which to keep as Claude/`CLAUDE.md` guidance) and a note on the .NET/TypeScript equivalents. This is **Java-first**: investigation + the one `MyShopDriver` refactor that Q2(b) requires (Step 3b). No *production rule suite* is committed until the matrix is reviewed; the POC tests and the Q2(b) refactor live on a branch.

> ArchUnit is already an accepted tool in this repo — the `backend-clean-java` exemplar plan (`20260616-0734-...`) adds an `ArchitectureRulesTest` for the Dependency Rule. This investigation reuses the same dependency and pattern, applied to the **test-kit** (`system-test/java`) rather than the system.

## Context: what the code actually looks like

The Java test-kit lives under `system-test/java/src/main/java/com/mycompany/myshop/testkit/`:

- `driver/port/MyShopDriver.java` — the port interface. Methods mix shapes:
  - `placeOrder(PlaceOrderRequest) → Result<PlaceOrderResponse, SystemError>` — request **and** response DTO.
  - `viewOrder(String orderNumber) → Result<ViewOrderResponse, SystemError>` — raw `String` in, response out.
  - `cancelOrder(String) → Result<Void, SystemError>` · `deliverOrder(String) → Result<Void, SystemError>` — raw `String` in, **no** response.
  - `goToMyShop() → Result<Void, SystemError>` — neither.
- `driver/port/dtos/` — `PlaceOrderRequest` (all `String` fields), `PlaceOrderResponse` (`String orderNumber` only), etc. Lombok `@Data @Builder`.
- `dsl/core/usecase/usecases/PlaceOrder.java` — **imports `driver.port.dtos.PlaceOrderRequest`/`PlaceOrderResponse`**. The DSL layer does **not** define its own request/response DTOs; it shares the driver-port ones (`find dsl -iname '*request*'` → none).
- `dsl/port/...` — fluent step interfaces (`WhenPlaceOrder` with `with*` returning the same interface), verification classes, etc.

**Two findings that shape the investigation up-front:**

1. **"Requests/responses identical across DSL and drivers" is currently achieved by *sharing*, not duplicating.** The DSL use case imports the driver-port DTO. So the enforceable rule is most naturally *"DSL core must not declare its own `*Request`/`*Response` DTOs — it must depend on `driver.port.dtos`"* (a clean ArchUnit dependency rule), **not** a field-by-field equality check between two parallel DTO sets. If the user actually wants two parallel sets that must stay in sync, that's a different (also enforceable) rule — **open question Q1**.

2. **"Every method must have a request and a response" is already violated by the current code.** `cancelOrder`, `deliverOrder`, `viewOrder`, `goToMyShop` do not take a `*Request` and/or do not return a `*Response`. ArchUnit *can* check this. The real question was never "can ArchUnit do it" (it can) but **"refactor the code to comply, or relax the rule with documented exceptions?"** — **Q2, now resolved to (b): enforce strictly and refactor the 4 methods.** This is the single most important decision in the investigation, and it expands the plan from investigation-only to investigation + refactor (see Resolved decisions).

## Outcomes

What we get out of this:

- A **feasibility matrix** (markdown table) over all documented rules — one row per rule, columns: *rule · source doc · ArchUnit feasibility · mechanism · current-code compliant? · recommendation*.
- A **runnable POC** `ArchitectureRulesTest` (JUnit 5 + ArchUnit) under `system-test/java/src/test/...` implementing ~4 representative rules spanning the feasibility spectrum, each demonstrated red-then-green.
- A **rollout recommendation**: which rules graduate to committed ArchUnit tests, which stay as Claude/`CLAUDE.md` prose, which need a separate AST/lint tool.
- A short **multi-language note**: the Java→.NET→TypeScript enforcement story (ArchUnit is JVM-only; .NET and TS need different tools).
- **No committed production rules** and **no code refactors** until the matrix + Q1/Q2 are reviewed. Investigation output is this plan (filled in) + the POC test, kept on a branch.

## Rule inventory & first-pass ArchUnit feasibility

Drawn from the archived reference docs (`gh-optivem/archive/references/atdd/architecture/*.md`) and the user's two examples. Feasibility is a hypothesis to be confirmed by the POC.

### A. Cleanly ArchUnit-enforceable (native rule or custom `ArchCondition`)

| # | Rule | Source | Mechanism |
|---|------|--------|-----------|
| A1 | Request DTOs (`*Request`, `Ext*Request`) must have **only `String` fields** | driver-port, dsl-core, driver-adapter | custom condition over fields of classes named `*Request` in `*.dtos` |
| A2 | Verification classes: every public method **returns its own type** (no getters) | dsl-core | methods in `*Verification` should have raw return type == owning class |
| A3 | DSL-port `with*()` / stage methods **return the same interface type** (fluent) | dsl-port | custom condition: `with*` return type == declaring interface |
| A4 | Every configurable field has a matching `withFieldName(String)` method | dsl-core, dsl-port | custom condition correlating fields ↔ `with` methods |
| A5 | Adapter external DTOs use the **`Ext*` prefix** | driver-adapter | naming rule on `driver/adapter/**/dtos` |
| A6 | `Ext*Request` String-only; `Ext*Response` may be typed | driver-adapter | per-suffix field-type condition |
| A7 | **DSL core must depend on `driver.port.dtos`, not declare its own `*Request`/`*Response`** (the real form of "identical req/resp") | user example #1 | `noClasses().that().resideIn(dsl..).should().beNamed *Request/*Response` + dependency check |
| A8 | Driver interfaces split `external/` vs `shop/` by package | driver-port | package-location rules |
| A9 | Response DTO field set must **not repeat** request DTO fields | driver-port | cross-class custom condition comparing `XRequest`↔`XResponse` field names |
| A10 | "Every method has a request and a response" (strict) | user example #2 | custom condition on `MyShopDriver` method signatures — **Q2 resolved to (b): enforce strictly; the 4 violating methods are refactored to comply** |

### B. Partial — needs method-body inspection (ArchUnit *can* see calls/field-accesses, lower confidence)

| # | Rule | Source | Why partial |
|---|------|--------|-------------|
| B1 | DSL step classes: defaults set **only in constructor**; `execute()` must not reference constants directly | dsl-core | ArchUnit exposes `getFieldAccessesFromSelf()` — can assert `execute()` does not read `static final` constant fields. Detecting "constant" = `static final` is doable; confidence medium |
| B2 | `getX(id)` methods take **only that resource's own id** | driver-port | checkable on interface signatures (param count/type), but "own id" semantics is partly naming |

### C. Not ArchUnit — needs AST / string-literal analysis / LLM review

| # | Rule | Source | Why not |
|---|------|--------|---------|
| C1 | Response DTO: **ID field declared first** | driver-port | field *declaration order* is not reliably exposed by bytecode reflection — fragile |
| C2 | UI driver must **never navigate directly to a URL** (`page.navigate(baseUrl + "/orders/" + id)`) | driver-adapter | ArchUnit sees the `navigate()` *call* but not the *argument value*; can't tell home-page nav from deep-link. Needs JavaParser/PMD |
| C3 | Endpoint URLs encoded as **constants**; `aria-label` / notification selectors | driver-adapter | string-literal *values*, invisible to ArchUnit |
| C4 | Positive/Negative test files: `Then` asserts success vs failure; correct TODO placement | test.md | semantic/control-flow — LLM or naming-only |
| C5 | "Start from home page and click through" | driver-adapter | control-flow intent — LLM only |

**Cross-cutting caveat for the POC:** ArchUnit analyses **compiled bytecode**, so Lombok-generated getters/setters/builders **are present** in the class model. Conditions over "public methods" (A2) and "fields" (A1, A9) must explicitly exclude generated members (builder classes, `$`-named synthetics, getters) or they will produce false positives. Confirming the right exclusion predicate is part of Step 3.

## Open questions (resolve before/within the POC)

- **Q4 — scope of this investigation:** Java only, or also scope the .NET/TS enforcement story now? **Recommendation:** Java POC + a *written* note on .NET (`NetArchTest` / `ArchUnitNET`) and TS (`ts-arch`, `dependency-cruiser`, custom ESLint) — no POC in those languages this round.

## Steps

- [ ] **Step 1 — Confirm the rule inventory & resolve Q1/Q2.** Re-read the five reference docs + the runtime agent prompts that restate these rules; reconcile with the user on Q1 and Q2 (these change *what* the POC asserts). Lock the matrix's rule list.
- [ ] **Step 2 — Add ArchUnit to `system-test/java` + wire the tag task (Q3(a)).** Add `com.tngtech.archunit:archunit-junit5` (test scope) to `system-test/java/build.gradle`. Confirm `./gradlew :system-test:java:test` still green. (Mirror the version the `backend-clean-java` plan settles on, to keep one ArchUnit version in the repo.) Add a dedicated Gradle task (e.g. `architectureTest`) that runs **only** JUnit-tagged `architecture` tests (`useJUnitPlatform { includeTags 'architecture' }`); the normal `test` task continues to run everything (tagged tests included), so CI is unchanged while the structural checks become separately runnable.
- [ ] **Step 3 — Write the POC `ArchitectureRulesTest`** under `src/test/.../architecture/`, carrying `@Tag("architecture")` (Q3(a)), covering four representative rules, one per feasibility tier:
  - A1 (request DTOs String-only) — native-ish custom condition, with the Lombok-exclusion predicate worked out here.
  - A2 (verification methods return own type) — custom condition over public methods.
  - A7 (DSL core depends on driver-port DTOs / declares no own `*Request`/`*Response`) — dependency rule = the "identical req/resp" answer.
  - A10/Q2 (every `MyShopDriver` method takes a `*Request` and returns a `*Response`) — the strict form from Q2(b). The 4 violating methods are refactored to comply first (see new Step 3b), then the rule is asserted green.
- [ ] **Step 3b — Refactor `MyShopDriver` to satisfy strict A10 (Q2(b)).** Give the 4 violating methods request/response DTOs: `cancelOrder` → `CancelOrderRequest`/`CancelOrderResponse`, `deliverOrder` → `DeliverOrderRequest`/`DeliverOrderResponse`, `viewOrder` → `ViewOrderRequest` (keeps `ViewOrderResponse`), `goToMyShop` → `GoToMyShopRequest`/`GoToMyShopResponse` (or a documented exception — settle in Step 1). Update the port interface, the adapter(s), the DSL use cases that call them, and the new DTO classes; keep `./gradlew :system-test:java:test` green. **This is the one code refactor this plan now owns** (Q2(b)); it is no longer a follow-up.
- [ ] **Step 4 — Demonstrate red-then-green.** For each POC rule, introduce a deliberate violation on a scratch class, show the test fails with a clear message, revert, show green. Capture the failure output in the plan (this is the evidence the mechanism works).
- [ ] **Step 5 — Probe a Tier-B rule (B1).** Spike whether `execute()`-must-not-read-constants is reliably expressible via `getFieldAccessesFromSelf()`. Record confidence; this calibrates how far ArchUnit reaches into method bodies.
- [ ] **Step 6 — Fill in the feasibility matrix** with confirmed results (promote/demote rows based on Steps 3–5), and write the **rollout recommendation**: which rules to commit as ArchUnit tests now, which to keep as `CLAUDE.md`/agent-prompt guidance, which need a separate AST/lint tool (C2 → JavaParser/PMD candidates).
- [ ] **Step 7 — Multi-language note.** One paragraph each: .NET (`NetArchTest`/`ArchUnitNET`) and TypeScript (`ts-arch`/`dependency-cruiser`/ESLint) — feasibility of porting the committed Java rules, no implementation.
- [ ] **Step 8 — Decision gate.** Review matrix + recommendation with the user. *Out of scope for this plan:* committing the full production rule set (the broader suite beyond the POC rules). The Q2(b) `MyShopDriver` refactor is now **in scope** (Step 3b), so it is no longer deferred.

## Resolved decisions

- **Q3 — where do the committed rules live / how do they run? → (a) Co-locate + `@Tag`, separate task.** ArchUnit tests sit under `src/test/.../architecture/` alongside the unit and component tests, each carrying `@Tag("architecture")`. A dedicated Gradle task runs only that tag, so the structural checks can be run on their own — the motivating distinction is that ArchUnit guards *structure* (fails on structural drift) while unit/component tests guard *behavior* (change when behavior changes), and the user wants to run the two independently. The tagged tests **still run inside the normal `test` task by default**, so CI's acceptance stage always catches them. No separate source set (b) and no all-in-one task (c).
- **Q1 — "identical req/resp" = share or duplicate? → (a) Share.** The intended rule is that DSL core must reuse the driver-port DTOs and declare no own `*Request`/`*Response` — encoded as the clean ArchUnit dependency rule **A7**. Matches current code (the DSL use case already imports `driver.port.dtos.*`) and is simpler. The duplicate/parallel-sets variant (b) is only relevant if a deliberate DSL/driver DTO split is later wanted; it is out of scope here.
- **Q2 — "every method has req+resp": enforce or relax? → (b) Enforce strictly + refactor.** Every `MyShopDriver` method must take a `*Request` and return a `*Response`. The 4 currently-violating methods get refactored to comply: `cancelOrder` → `CancelOrderRequest`/`CancelOrderResponse`, `deliverOrder` → `DeliverOrderRequest`/`DeliverOrderResponse`, `viewOrder` → `ViewOrderRequest` (keeps `ViewOrderResponse`), `goToMyShop` → `GoToMyShopRequest`/`GoToMyShopResponse`. **This expands the plan beyond investigation-only into investigation + refactor**: rule **A10** is asserted in its strict form, and the code is made to comply rather than the rule relaxed to fit the code. Open sub-point carried into Step 1: whether the pure-navigation `goToMyShop()` truly gets a (likely empty) request/response pair or a single documented exception — settle with the user before refactoring. The non-breaking conditional variant (a) was considered and rejected: the user wants the uniform req/resp contract enforced, not accommodated.

## Related follow-up (separate plan)

- **Per-operation handler decomposition.** Once Q2(b) gives every operation a `*Request`/`*Response`, a natural next step is to split the single `MyShopDriver` port into per-operation units (e.g. `PlaceOrderRequest` / `PlaceOrderUseCase` / `PlaceOrderResponse` triads) rather than methods on one shared interface. This is a structural decomposition of the port — large blast radius (interface, every adapter, DSL wiring, test-kit shape) — and is **explicitly out of scope here**. Spin it up as its own plan after this investigation lands; the req/resp DTOs from Step 3b are its prerequisite.

## Non-goals

- Committing a production architecture-rule suite (this is investigation; commit decision is Step 8).
- ~~Refactoring `MyShopDriver` to give every method a request/response DTO~~ — **now in scope** via Q2(b)/Step 3b.
- Any .NET or TypeScript implementation (Java-first; other languages get a written note only).
- Replacing LLM review for the genuinely semantic rules (C-tier) — the point is to find the boundary, not to over-claim it.
