# 2026-07-06 09:25:00 UTC — Restructure component (frontend↔backend) contract tests into legacy/latest

🤖 **Picked up by agent** — `Valentina_Desk` at `2026-07-06T15:17:31Z`

> **Scope confirmed (this session):** ALL 4 consumer contract specs — order + coupon, both component + integration — get the `legacy`/`latest` split against one shared test-kit (8 specs total). Provider verifier + system-test external-system contracts stay untouched. Mode: batch-then-review.

## TL;DR

**Why:** The PAID-TDD contract-tests article series (`substack/articles/drafts/PAID-TDD-*contract-tests-components.md`) teaches a plain→maintainable refactor of the frontend↔backend Pact test using **Drivers & DSL**. The "after" snippet in the *maintainable* draft (`frontend.placeOrder()…hasConfirmation('Order Number ORD-1')` + a `backendStub` DSL) has **no backing code in the repo** — it's currently invented for the article. We want the article's before/after to point at real, runnable code in `optivem/shop`.

**What we settled (design discussion, this conversation):**

1. **Two folders, not three.** `legacy` (plain/brittle) vs `latest` (maintainable Drivers+DSL). We considered `v1/v2/v3` (simple → drivers → dsl) and rejected it for now in favour of the simpler binary split. Note this introduces a *second* progression convention alongside system-test's existing `legacy/latest` — that's fine (different kind of progression) but be deliberate.
2. **Inside the component, not `system-test`.** Component contract tests are owned by the component (`system/multitier/frontend-react`), not the standalone `system-test` project (which owns e2e/acceptance/smoke and the *external-system* ERP/tax/clock contracts). Ownership boundary must not be crossed.
3. **Symmetric structure: one shared test-kit + `legacy`/`latest` for BOTH subjects (decision: Option A).** Both consumer contract tests — the **component** test (`order.component.test.tsx`, renders UI) and the **narrow integration** test (`order.integration.test.ts`, calls `OrderGateway` directly) — today use raw inline `PactV3`, share the same `{consumer, provider, dir}`, and merge into one `contracts/frontend-backend.json` (per the `interactions.ts` header: *"both suites import here so interactions merge idempotently"*). Each gets the **same two-state treatment** against **one shared test-kit**:
   - **Shared infrastructure** (`src/test/support/`): a **backend-stub DSL + Pact stub driver** (absorbs `new PactV3`/`addInteraction`/`executeTest`/routing) and a **frontend DSL + Driver** (absorbs the consumer-driving). The frontend DSL runs against a **swappable driver** — a **UI driver** for the component test, a **gateway driver** for the integration test — so one DSL reads the same at both levels (`hasConfirmation` = screen text for UI vs `result.orderNumber` for gateway). Keep `hasConfirmation` semantic, not tied to literal UI formatting.
   - **`legacy`** — both tests *without* DSL/Driver: today's raw inline `PactV3`, kept verbatim as the article's "before". Still imports the shared `interactions/` builders (only the *driving* is raw), so it co-generates into the canonical pact by idempotent merge alongside `latest`.
   - **`latest`** — both tests *with* DSL/Driver: thin specs importing the shared test-kit; also generate into the canonical pact.

   The **component** test stays the article's *showcase* (biggest visible cleanup — line 42 `expect(await screen.findByText(/Order Number ORD-1/))` is the snippet's `hasConfirmation('Order Number ORD-1')`, `routeApiTo(mockserver.url)` is its `routeApiTo(baseUrl)`), but the pattern is applied uniformly to both subjects.
4. **Both `legacy` and `latest` generate the canonical `contracts/frontend-backend.json` — via idempotent merge.** They don't conflict because both source their interactions from the same shared `interactions/` builders, so they emit *identical* interactions that Pact dedupes on merge — exactly how the current component and integration suites already co-generate one pact (per the `interactions.ts` header). Guard rail: `legacy` may **re-drive** the contract with raw `PactV3` (that's the "before"), but must **not redefine** interactions — it keeps importing the shared builders, so it can never drift from `latest`. `latest` still covers the full interaction set on its own (parity), so `legacy` is removable without changing the contract; co-generation is a bonus consistency check that the refactor preserved the pact, not a dependency. No scratch dir / opt-in isolation needed.
5. **`interactions/` stays put** — shared fixtures imported by both the integration and component suites; moving them would break imports and they aren't part of "the plain test" per se.
6. **Provider side (`backend-typescript`) needs NO split** — `test/pact/backend.pact.spec.ts` just verifies whatever pact sits at `contracts/frontend-backend.json`; it is agnostic to legacy/latest.

**End result:** a shared contract test-kit under `frontend-react/src/test/support/` holds the reusable **backend-stub DSL + Pact stub driver** and the **frontend DSL + swappable Driver** (UI + gateway). Both subjects get a `legacy`/`latest` split: `src/test/component/{legacy,latest}/order.component.test.tsx` and `src/test/integration/{legacy,latest}/order.integration.test.ts`. `legacy` = today's raw inline `PactV3` tests, verbatim; `latest` = thin specs importing the shared test-kit. All four specs generate into `contracts/frontend-backend.json` via idempotent merge (identical interactions from the same shared builders), so the pact is unchanged in content. The provider verifier is unchanged. The *maintainable* article's snippet points at real `latest` + test-kit code; the *plain* article's snippet points at real `legacy` code.

## Context — current state (verified this conversation)

- Consumer pact tests (both merge into one pact):
  - `system/multitier/frontend-react/src/test/integration/order.integration.test.ts` — narrow, calls `OrderGateway` directly, no render. **Not the subject; leave as-is.**
  - `system/multitier/frontend-react/src/test/component/order.component.test.tsx` — renders `<NewOrder/>`, PactV3, asserts UI text. **This is the subject.** Contributes extra interactions the integration test doesn't (blackout / view-details / missing-order) — `latest` MUST preserve those or canonical pact coverage drops.
  - `system/multitier/frontend-react/src/test/interactions/{order,coupon}.interactions.ts` — shared builders (`like()` baseline; exact match only on branched fields like `status`). Imported by BOTH suites. **Stays put.**
- Provider: `system/multitier/backend-typescript/test/pact/backend.pact.spec.ts` verifies `../../../../../contracts/frontend-backend.json`. **Agnostic; no change.**
- Test wiring: `vitest.opt-in.config.ts` already gates component+integration/pact suites (default `npm test` excludes them; `test:component` / `test:pact` opt in). This is the seam for excluding `legacy` from canonical pact generation.

## Outcomes

- `frontend-react/src/test/support/` holds the **shared** test-kit — a `BackendStubDsl` + Pact stub driver (wraps `PactV3`/`addInteraction`/`executeTest`/routing) and a **frontend DSL + swappable Driver** (UI driver + gateway driver) — imported by every `latest` spec, with **no duplication** of Pact scaffolding or driving mechanics.
- `component/{legacy,latest}/order.component.test.tsx` and `integration/{legacy,latest}/order.integration.test.ts` all exist: `legacy` = today's raw inline `PactV3` tests kept verbatim (raw *driving*, but still importing the shared `interactions/` builders); `latest` = thin specs importing the shared test-kit. All four co-generate into the canonical pact via idempotent merge.
- Canonical `contracts/frontend-backend.json` is unchanged in content after the change (same interactions/matchers), verified by diffing the generated pact before vs after; `latest` alone still covers every interaction the current tests contribute (place-order success, coupon, blackout/error, view-details/missing — parity check), so `legacy` co-generates as a consistency check, not a dependency.
- The backend provider verifier (`test/pact/backend.pact.spec.ts`) is byte-for-byte unchanged and still passes.
- `interactions/` is untouched and still imported (by the `legacy` specs and reused inside the shared `BackendStubDsl`).
- Both article drafts' snippets map to real files: *plain* → `legacy/`, *maintainable* → `latest/` (article-sync step below).

## ▶ Next executable step (resume here)

**Steps 1–7 are DONE and verified** (all 8 specs green; pact byte-for-byte unchanged; `latest` alone reproduces the full pact — parity proven; provider + pact untouched). **Layout = `legacy`/`latest` on top, then `component`/`integration`** — i.e. `src/test/{legacy,latest}/{component,integration}/…`; shared kit in `src/test/support/`. Opt-in globs (`vitest.opt-in.config.ts` include, `vite.config.ts` exclude) and `package.json` scripts (`test:component`/`test:integration`/`test:legacy`/`test:latest`) updated to match. **SKU settled (this session):** `system/**` and the canonical pact are uniform on `BOOK-123`; `APPLE1001` is used nowhere in shop; system-test's `DEFAULT-SKU` placeholder is intentionally left distinct. **Shop side is DONE — committed (`fad5d866`) and pushed to origin.** The remaining article-sync and backend-symmetry work has been extracted into two follow-up plans (see Steps below).

## Steps

_Shop refactor complete — committed `fad5d866` and pushed to origin. The remaining work lives in three follow-up plans:_

- **Article sync** → `substack` repo, `plans/20260708-0903-sync-contract-tests-article-drafts-to-repo.md` (the former Step 8). Points the four contract-tests drafts at the real `legacy/`/`latest/` code; settled: `BOOK-123`, semantic `hasConfirmation('ORD-1')`, `useBackend(baseUrl)`.
- **Backend provider-side split** → this repo, `plans/20260708-0902-backend-external-systems-component-tests-legacy-latest.md`. The symmetric `legacy`/`latest` split for the backend component tests (WireMock ERP/tax stubs → stub DSL), all 3 languages.
- **Fix the `gh optivem commit` push path** → `gh-optivem` repo (multi-worktree repos make the skill pass multiple refs to `git pull --rebase`, which fails with "Cannot rebase onto multiple branches"; the frontend commit had to be pushed with a one-off `git push`).

This plan can be deleted once the three follow-ups are tracked/underway.

## Open questions

- **Coupon as well as order?** Start with the place-order test (the `APPLE1001` running example) only, and repeat the same legacy/latest split for the coupon component test in a follow-up — or do both now? Recommend order-only first to keep the first pass small; coupon repeats mechanically once the DSL exists.
- **Does `legacy/` run in CI?** Yes — it runs in the same opt-in suite as `latest` and co-generates the canonical pact by idempotent merge (no isolation, no scratch dir). Confirm which npm script / CI stage generates the published pact so both `legacy` and `latest` specs are included in it (Step 5 verifies the merged pact is unchanged).
- **Frontend DSL on the narrow integration test — SETTLED: yes, swappable-driver model.** One shared frontend DSL runs against a swappable Driver (UI driver for component, gateway driver for integration), so both `latest` specs read the same and the pattern applies uniformly. Watch the one caveat: keep `hasConfirmation` semantic (screen-text vs `result.orderNumber`), never asserting literal UI formatting in the DSL, or the cross-level abstraction leaks.
- **Subject = component test is the article showcase** (biggest visible cleanup), but the DSL/Driver refactor is applied to both subjects. Reversible only in the sense of which test the article foregrounds.
- **DSL boundary** — the §3 heading fuses "Drivers & DSL"; with the binary legacy/latest split there's no need to separate a drivers-only step from a dsl step. If the article is ever re-split into two refactor increments, revisit whether the repo should show an intermediate drivers-only state (the rejected `v2`).
