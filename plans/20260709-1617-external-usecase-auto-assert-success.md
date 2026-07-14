# 2026-07-09 16:17:00 UTC — External use-case `execute()` auto-asserts success (drop boilerplate `shouldSucceed()`)

## Status: REJECTED (2026-07-14)

**Decision:** Rejected before execution. Do not revive without new evidence.

**Why rejected:**

1. **The payoff is tiny.** The dangling `.execute().shouldSucceed();` shape is only *written by a test author* in a handful of legacy test lines (`legacy/mod07/e2e/PlaceOrderPositiveTest`, `legacy/mod0{5,6,7}/smoke/external/{Erp,Tax}SmokeTest`, `legacy/mod07/smoke/system/MyShopSmokeTest`) and their TypeScript/.NET equivalents. Everything else the plan would touch is scenario-DSL *library* code (`AssumeImpl`, `ThenImpl`, `BaseThenStep`, `Given*Impl`), where no test author ever reads the boilerplate.
2. **It trades a uniform law for a memorized taxonomy.** Today every use case obeys one rule: `execute()` → `UseCaseResult` → pick `shouldSucceed()` / `shouldFail()`. No exceptions. Option (A) splits that into arrange vs query families, so you must first *know which family a use case is in* before you can tell what `execute()` returns. For a teaching codebase this is a net loss — and the explicit `.shouldSucceed()` on a stub setup is arguably pedagogically useful, since it shows students that arranging a stub is a call that can fail.
3. **It doubles the divergence surface.** The use-case DSL now exists in two parallel trees — the system-test testkit (`system-test/<lang>/**`) and the component-test support tree (`system/multitier/backend-<lang>/src/testSupport/**`, added after this plan was written). The arrange/query membership list would have to be kept in agreement across 3 languages × 2 trees forever, and every `test-comparator` run would police it.

Net: low value, negative teaching value, high consistency cost.

## TL;DR

**Why:** In the low-level use-case DSL, external *arrange* steps read `app.erp().returnsProduct()...execute().shouldSucceed();`. The `.shouldSucceed()` there is pure boilerplate — an arrange step that failed should just blow up the test anyway, and nothing consumes the returned verification. State-checking steps are different: their `.shouldSucceed()`/`.shouldFail()` is meaningful because it selects the outcome branch and yields the verification object.
**End result:** External arrange use cases (`returns*()`, `goTo*()`) can be written as `...execute();` and fail loudly on error without a hand-written assertion. State-query use cases (`getProduct()`, `viewOrder()`, `placeOrder()`, ...) still require an explicit `.shouldSucceed()`/`.shouldFail()` to reach their verification and pick the success/error path.

## Outcomes

What we get out of this — the goals and deliverables:

- External arrange steps lose the trailing `.shouldSucceed()` noise: `app.erp().returnsProduct().sku(SKU).unitPrice(20.00).execute();` reads as arrange, not assert.
- A failing arrange step still fails fast and loudly (auto-assert on `execute()`), so removing the explicit call loses no safety.
- The distinction is principled and documented: **arrange use cases auto-assert; query use cases keep the explicit outcome selector.** No ambiguity about which style a given use case uses.
- State checks are unchanged: `execute().shouldSucceed().hasX(...)` / `execute().shouldFail()...` still read exactly as today.
- Applied consistently across all three languages (Java, .NET, TypeScript) so the DSLs stay equivalent.

## ▶ Next executable step (resume here)

Design/planning only — no mechanical edit yet. The open questions below (esp. Q1 the mechanism, Q2 the arrange/query boundary) must be settled before any code. Continue refining this plan with the user (`/create-plan` / `/refine-plan`); once Q1–Q3 are decided, the first executable unit will be the Java prototype in Step 3.

## Design sketch (for discussion — not yet decided)

Current shape (`UseCaseResult<R,V>`):
- `execute()` returns `UseCaseResult`.
- `.shouldSucceed()` asserts success, returns verification `V`.
- `.shouldFail()` asserts failure, returns `ErrorVerification`.

Candidate mechanisms for "auto-assert on arrange" (see Q1):
- **(A) Arrange use cases return `void`/self from `execute()`** and assert success internally. Query use cases keep returning `UseCaseResult`. Cleanest reading; requires splitting the two families at the type level (e.g. `ArrangeUseCase` vs `QueryUseCase`).
- **(B) Keep one `execute()` returning `UseCaseResult`, but make `UseCaseResult` auto-assert success on discard** — not feasible/clean in Java; discard isn't observable. Likely rejected.
- **(C) Add a terminal alias** (e.g. `.executeExpectingSuccess()` or arrange steps expose no `.execute()` but an `.apply()`/`.arrange()` that asserts). Explicit, but reintroduces a word per line — smaller win.

Note: internal scenario-DSL callers (`GivenProductImpl`, `AssumeImpl`, `ThenImpl`, `BaseThenStep`, etc.) already chain `.execute().shouldSucceed()` on these same use cases — the mechanism must keep those working (or simplify them too).

## Steps

- [ ] Step 1: Settle Q1 (mechanism) and Q2 (arrange vs query boundary) with the user; lock the chosen shape into this plan.
- [ ] Step 2: Enumerate every external/arrange use case vs every query use case in Java (`usecase/external/**`, `usecase/usecases/**`) and their call sites (tests + scenario-DSL impls). Produce the concrete before/after list.
- [ ] Step 3: Prototype the chosen mechanism in Java on one arrange use case (`ReturnsProduct`) + its call sites; confirm arrange failure still fails the test.
- [ ] Step 4: Roll out across all Java arrange use cases and update call sites (legacy e2e/smoke tests + internal scenario-DSL impls).
- [ ] Step 5: Mirror the change in .NET and TypeScript use-case DSLs for equivalence.
- [ ] Step 6: Update ATDD docs (`docs/atdd/code/**`, language-equivalents) describing the arrange-auto-assert vs query-explicit convention.
- [ ] Step 7: Compile all (`./compile-all.sh`) and run `--sample` system tests per language before committing.

## Open questions

- **Q1 — Mechanism.** Which of (A)/(B)/(C) above? *Recommendation: (A) type-split arrange vs query* — it's the only option that makes `...execute();` read as arrange with zero extra words while keeping query semantics intact. Confirm before enumerating.
- **Q2 — Boundary.** Which use cases are "arrange" (auto-assert) vs "query" (explicit)? Proposed arrange: `returnsProduct`, `returnsPromotion`, `returnsTaxRate`, `returnsTime`, `goToErp/Tax/Clock/MyShop` (smoke reachability). Proposed query: `getProduct`, `getTaxRate`, `getTime`, `viewOrder`, `placeOrder`, `cancelOrder`, `browseCoupons`, `publishCoupon`, `deliverOrder`. Does `placeOrder` in an e2e *arrange-ish* context (setting up an order to later view) count as query? (It returns a verification used for `orderNumberStartsWith` — so query.)
- **Q3 — State checks & error paths.** Confirm the rule for *checking state*: the user noted "when checking state, maybe do need to assert success or error." Intended rule: **any step whose result you inspect (state or error) keeps the explicit `.shouldSucceed()`/`.shouldFail()`; only fire-and-forget arrange loses it.** Agree?
- **Q4 — Scope.** Does this also touch the high-level `scenario` DSL's `.then().shouldSucceed()` (the acceptance tests), or only the low-level use-case DSL used by legacy e2e/smoke + internal impls? *Recommendation: low-level DSL only* — the `scenario` `.then().shouldSucceed()` is a deliberate readable assertion in the Gherkin-style flow, not boilerplate.
