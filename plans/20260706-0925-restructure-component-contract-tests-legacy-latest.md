# 2026-07-06 09:25:00 UTC — Restructure component (frontend↔backend) contract tests into legacy/latest

## TL;DR

**Why:** The PAID-TDD contract-tests article series (`substack/articles/drafts/PAID-TDD-*contract-tests-components.md`) teaches a plain→maintainable refactor of the frontend↔backend Pact test using **Drivers & DSL**. The "after" snippet in the *maintainable* draft (`frontend.placeOrder()…hasConfirmation('Order Number ORD-1')` + a `backendStub` DSL) has **no backing code in the repo** — it's currently invented for the article. We want the article's before/after to point at real, runnable code in `optivem/shop`.

**What we settled (design discussion, this conversation):**

1. **Two folders, not three.** `legacy` (plain/brittle) vs `latest` (maintainable Drivers+DSL). We considered `v1/v2/v3` (simple → drivers → dsl) and rejected it for now in favour of the simpler binary split. Note this introduces a *second* progression convention alongside system-test's existing `legacy/latest` — that's fine (different kind of progression) but be deliberate.
2. **Inside the component, not `system-test`.** Component contract tests are owned by the component (`system/multitier/frontend-react`), not the standalone `system-test` project (which owns e2e/acceptance/smoke and the *external-system* ERP/tax/clock contracts). Ownership boundary must not be crossed.
3. **The subject is the COMPONENT test, not the narrow integration test.** Key finding: `src/test/component/order.component.test.tsx` AND `src/test/integration/order.integration.test.ts` BOTH already use `PactV3` with the same `{consumer:'frontend', provider:'backend', dir:'…/contracts'}` and feed the **same merged** `contracts/frontend-backend.json` (the `interactions.ts` header says *"both suites import here so interactions merge idempotently"*). The component test renders `<NewOrder/>`, types, clicks, and asserts UI text — line 42 `expect(await screen.findByText(/Order Number ORD-1/))` IS the article snippet's `hasConfirmation('Order Number ORD-1')`, and its `routeApiTo(mockserver.url)` is the snippet's `routeApiTo(baseUrl)`. The verbose render/type/click/findByText mechanics are exactly what a Driver should absorb, so the Drivers & DSL payoff is on the **component** test. The narrow integration test is already ~3 clean lines and is NOT the subject — it stays untouched.
4. **`latest` is live/canonical; `legacy` is a frozen reference.** Only one version can own the canonical `contracts/frontend-backend.json`. `latest` (DSL) generates it; `legacy` (the current verbose plain test) is frozen as the article's "before", kept compiling/runnable via an opt-in config pointed at a **scratch pact dir** so it never pollutes the canonical contract.
5. **`interactions/` stays put** — shared fixtures imported by both the integration and component suites; moving them would break imports and they aren't part of "the plain test" per se.
6. **Provider side (`backend-typescript`) needs NO split** — `test/pact/backend.pact.spec.ts` just verifies whatever pact sits at `contracts/frontend-backend.json`; it is agnostic to legacy/latest.

**End result:** `frontend-react/src/test/` contains a `legacy` (plain, verbose, render-based) and a `latest` (Drivers & DSL) version of the **place-order component contract test**. `latest` is the live pact generator and covers every interaction the current component test contributes; `legacy` still compiles and passes but writes to a throwaway pact dir. The narrow integration test and the provider verifier are unchanged. The *maintainable* article's snippet now points at real `latest` code; the *plain* article's snippet points at real `legacy` code.

## Context — current state (verified this conversation)

- Consumer pact tests (both merge into one pact):
  - `system/multitier/frontend-react/src/test/integration/order.integration.test.ts` — narrow, calls `OrderGateway` directly, no render. **Not the subject; leave as-is.**
  - `system/multitier/frontend-react/src/test/component/order.component.test.tsx` — renders `<NewOrder/>`, PactV3, asserts UI text. **This is the subject.** Contributes extra interactions the integration test doesn't (blackout / view-details / missing-order) — `latest` MUST preserve those or canonical pact coverage drops.
  - `system/multitier/frontend-react/src/test/interactions/{order,coupon}.interactions.ts` — shared builders (`like()` baseline; exact match only on branched fields like `status`). Imported by BOTH suites. **Stays put.**
- Provider: `system/multitier/backend-typescript/test/pact/backend.pact.spec.ts` verifies `../../../../../contracts/frontend-backend.json`. **Agnostic; no change.**
- Test wiring: `vitest.opt-in.config.ts` already gates component+integration/pact suites (default `npm test` excludes them; `test:component` / `test:pact` opt in). This is the seam for excluding `legacy` from canonical pact generation.

## Outcomes

- `frontend-react/src/test/component/legacy/` holds the current verbose place-order component contract test (plain "before"), compiling and passing but writing its pact to a scratch dir, excluded from canonical `frontend-backend.json` generation.
- `frontend-react/src/test/component/latest/` holds the Drivers & DSL version (`FrontendDsl` + `FrontendDriver` + `BackendStubDsl` + Pact stub driver) that reads like the *maintainable* article snippet, is the live pact generator, and covers every interaction the current component test covered (place-order success, coupon, blackout/error — parity check required).
- Canonical `contracts/frontend-backend.json` is unchanged in content after the swap (same interactions), verified by diffing the generated pact before vs after.
- The narrow integration test and the backend provider verifier are byte-for-byte unchanged and still pass.
- `interactions/` is untouched and still imported by the integration suite (and by whichever of legacy/latest still consumes raw builders).
- Both article drafts' snippets map to real files: *plain* → `legacy/`, *maintainable* → `latest/` (article-sync step below).

## ▶ Next executable step (resume here)

Start with **Step 1** — the safe mechanical relocation of the current component test into `legacy/` and confirm nothing breaks — because it needs no new DSL code and no A/B-style decision. Author `latest/` (Steps 3–5) only after `legacy/` is green.

## Steps

- [ ] **Step 1 — Relocate the plain test into `legacy/` (mechanical, safe).** Move `src/test/component/order.component.test.tsx` → `src/test/component/legacy/order.component.test.tsx`. Fix its relative imports (interactions become `../../interactions/…`; `test-utils`, pages, etc. shift one level). Do NOT change its logic. Run `npm run test:component` (opt-in config) and confirm it still passes. (Coupon component test can move in the same step or be deferred — see Open questions.)
- [ ] **Step 2 — Point `legacy/`'s pact output at a scratch dir so it stops owning the canonical contract.** In the `legacy/` test, change the `PactV3` `dir` to a throwaway path (e.g. `…/contracts-legacy` or an OS temp dir) so running it does not write/merge into `contracts/frontend-backend.json`. Confirm: after running only `legacy/`, `git status` shows no change to `contracts/frontend-backend.json`. Decide whether `legacy/` stays in the opt-in run at all or is excluded entirely (recommend: keep runnable in opt-in, excluded from the canonical-pact CI job).
- [ ] **Step 3 — Build the Driver/DSL support code for `latest/`.** Author, under `src/test/component/latest/` (self-contained snapshot): a consumer-side `FrontendDriver` (encapsulates `renderWithProviders` + `getByLabelText`/`type`/`click`/`findByText`) and `FrontendDsl` (`placeOrder().withSku().withQuantity().execute().hasConfirmation()`); and a backend-stub side `BackendStubDsl` (`returnsPlacedOrder().withSku().withOrderNumber().execute()`) over a Pact stub driver that wraps `PactV3.addInteraction` + `executeTest` and exposes `runContract(baseUrl => …)` + `routeApiTo`. Reuse the shared `interactions/` builders inside `BackendStubDsl` where practical (don't re-hand-roll matcher policy — keep `like()` baseline + exact-on-branched-fields).
- [ ] **Step 4 — Write the `latest/` place-order component contract test** using the DSL, matching the *maintainable* article snippet. It MUST cover every interaction the old component test covered (success, coupon, blackout/error, and any view-details/missing-order cases that live in the component suite) so canonical pact coverage is preserved.
- [ ] **Step 5 — Make `latest/` the canonical pact generator.** Its `PactV3` `dir` points at `…/contracts` (the real location). Run `npm run test:component`, then diff the regenerated `contracts/frontend-backend.json` against the pre-change version — it must be equivalent (same interactions/matchers). Include `latest/` in the opt-in include globs; ensure `legacy/` is not double-generating.
- [ ] **Step 6 — Verify the provider still passes unchanged.** Run the backend provider verification (`test/pact/backend.pact.spec.ts`) against the regenerated pact. No provider code changes expected.
- [ ] **Step 7 — Full green + no stray artifacts.** Run the frontend opt-in suite and the narrow integration test; confirm all pass, `contracts-legacy`/scratch output is git-ignored or cleaned, and `git status` is clean except the intended moves/additions.
- [ ] **Step 8 — Sync the article drafts (per series-sync rule).** Point the *maintainable-contract-tests-components* draft snippet at the real `latest/` code, and the *plain contract-tests-components* draft at `legacy/`. Propagate any wording/matcher changes across all four contract-tests drafts (they are one series). Keep the running example on `APPLE1001` per recent commits.
- [ ] **Step 9 — Commit via the `/commit` skill** (never raw git), then remove this plan file (and the `plans/` dir if it becomes empty) per the Plan Processing rule.

## Open questions

- **Coupon as well as order?** Start with the place-order test (the `APPLE1001` running example) only, and repeat the same legacy/latest split for the coupon component test in a follow-up — or do both now? Recommend order-only first to keep the first pass small; coupon repeats mechanically once the DSL exists.
- **Does `legacy/` run in CI at all, or is it source-only reference?** Recommend: keep it runnable under the opt-in config (proves the "before" actually worked) but excluded from the canonical-pact-generating CI job. Confirm which npm script / CI stage generates the published pact before finalising Step 2.
- **A vs B is settled as B (component), but it's the author's call and reversible.** If the author later decides the article should teach the DSL on the *narrow integration* test instead (A), the subject moves to `integration/` and the component test stays as-is. Recorded as decided=component because that's where the Drivers & DSL payoff is and it's what the snippet already shows.
- **`latest` matcher/DSL boundary** — the §3 heading fuses "Drivers & DSL"; with the binary legacy/latest split there's no need to separate a drivers-only step from a dsl step. If the article is ever re-split into two refactor increments, revisit whether the repo should show an intermediate drivers-only state (the rejected `v2`).
