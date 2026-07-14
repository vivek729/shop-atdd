# 2026-07-14 11:23:28 UTC — Frontend-react latest component/integration specs: DSL-only, no driver in the test body

## TL;DR

**Why:** `system/multitier/frontend-react/src/test/latest/component/*.test.tsx` still reaches for the driver directly: every test constructs `new PactBackendStubDriver()` / `new BackendStubDsl(...)`, wraps its body in `await backendDriver.runContract(async (baseUrl) => { … })`, then hand-wires `new FrontendDsl(new UiFrontendDriver())` and `frontend.useBackend(baseUrl)` inside the callback. The backend-java `componentTest` twin has no such leak: `AbstractComponentTest` owns the harness lifecycle (WireMock start, Spring boot, per-test reset) and hands the test ready-made `backend` / `erpStub` / `taxStub` / `clockStub` DSL fields, so the test body is *pure DSL*. The frontend spec should read the same way — same student, same shape, both sides of the wire.

**End result:** A frontend component spec is arrange-act-assert in DSL only:

```ts
it('shows coupons when they are returned', async () => {
  backend.returnsCoupons();
  await frontend.browseCoupons().execute().showsCoupon('SAVE10');
});
```

No `runContract`, no `baseUrl`, no driver construction, no `new FrontendDsl(...)` — `backend` and `frontend` come pre-wired from a shared component-test harness (the TS analogue of `AbstractComponentTest`), and the Pact mock-server lifecycle + pact-file write + "all interactions matched" verification happen in the harness's setup/teardown.

## Outcomes

What we get out of this — the goals and deliverables:

- **Zero driver references in `test/latest/component/*` and `test/latest/integration/*`.** The words `PactBackendStubDriver`, `UiFrontendDriver`, `GatewayFrontendDriver`, `runContract`, `useBackend`, and `baseUrl` do not appear in any latest spec. Drivers survive — they just live behind the harness, exactly as WireMock lives behind `AbstractComponentTest`.
- **A shared component-test harness, the TS twin of `AbstractComponentTest`.** One module under `src/test/support/` that owns the Pact mock-server lifecycle, exposes ready-wired `backend` (Backend Stub DSL) and `frontend` (Frontend DSL) handles, resets state per test, and asserts at teardown that every staged interaction was actually exercised.
- **The Pact-callback inversion is solved once, in the harness.** `PactV3.executeTest(cb)` is callback-shaped and demands its interactions staged *before* the mock server boots — the reason the callback leaked into the spec in the first place. The harness inverts it (lazy mock-server start on the first frontend gesture; teardown completes the `executeTest` promise so the pact is written and unmatched-interaction failures still fail the test) so no spec ever has to see it.
- **Symmetric treatment of both latest levels.** The integration specs (`GatewayFrontendDriver`) get the same harness, so component and integration differ only in which frontend driver the harness was configured with — the level boundary the `FrontendDsl` seam already documents.
- **The pact output is unchanged.** The same interactions, from the same shared `interactions/` builders, merged idempotently into the one canonical `contracts/frontend-backend.json`. This refactor is provably behaviour-preserving: the pact file is byte-comparable before and after.
- **`legacy/` stays raw.** The legacy specs are the deliberate "before" of the teaching pair and keep their raw Pact plumbing — the contrast is the lesson.

## ▶ Next executable step (resume here)

Settle the two design decisions in **Open questions** before writing code — they determine the harness's public shape, and guessing means rework. Specifically: (1) how `backend` / `frontend` reach the spec (module-level `let` bindings populated in `beforeEach`, mirroring Java's protected fields — recommended — versus a Vitest `test.extend` fixture that passes them as test args), and (2) whether this plan also adopts the two-layer use case + scenario DSL that the sibling plan `20260714-1118-component-test-scenario-dsl-java.md` introduces on the Java side, or stays a pure de-plumbing of today's one-layer DSL (recommended: de-plumb first, layer second).

This is a **design/refine** step, not a mechanical edit — resume with `/refine-plan` on this file, not `/execute-plan`.

Once settled, the first mechanical unit is Step 1: build the harness in `src/test/support/` with the lazy mock-server inversion, proven by porting the single-test `coupon.component.test.tsx` onto it and diffing the emitted pact against the current one.

## Steps

- [ ] Step 1: **Harness with the Pact inversion.** Add a component-test harness module under `src/test/support/` that owns the `PactV3` lifecycle. On the first frontend gesture it starts `executeTest`, capturing the mock-server URL and handing it to the frontend driver; at teardown it resolves the body so Pact writes the pact and reports unmatched interactions (which must still fail the owning test). Port `coupon.component.test.tsx` (one test) onto it as the proof, and diff `contracts/frontend-backend.json` before/after — it must be identical.
- [ ] Step 2: **Port the rest of the latest component specs.** Move `order.component.test.tsx` (8 tests) onto the harness; delete every `runContract` / `useBackend` / driver construction from the spec bodies.
- [ ] Step 3: **Port the latest integration specs.** Same harness, configured with `GatewayFrontendDriver`, for `order.integration.test.ts` and `coupon.integration.test.ts`.
- [ ] Step 4: **Tighten the seams.** `useBackend` drops off the `FrontendDsl` public surface (it becomes a driver-internal concern the harness calls); `BackendStubDriver.runContract` is no longer a spec-facing verb. Update the header comments in `frontend-dsl.ts` / `pact-backend-stub-driver.ts` — they currently *document* the leak ("the one plumbing leak the driver can't fully hide") and that sentence stops being true.
- [ ] Step 5: **Verify.** `npx tsc --noEmit` plus the frontend test scripts (component + contract, which are the opt-in excluded-from-default scripts) pass; the regenerated pact is byte-identical to the pre-refactor one.

## Open questions

- **How do `backend` / `frontend` reach the spec?** Module-level `let backend, frontend` populated in the harness's `beforeEach` mirrors Java's `protected` fields most closely and keeps the test signature bare `async () => {}` — **recommended**. The alternative, a Vitest `test.extend` fixture (`it('…', async ({ backend, frontend }) => …)`), is more idiomatic TS but changes every test signature and its `use()` callback wraps the body *before* interactions are staged, which is exactly the ordering problem we're removing.
- **Does this plan stop at de-plumbing, or also adopt the two-layer scenario DSL?** The sibling plan `20260714-1118-component-test-scenario-dsl-java.md` reshapes the Java component DSL into use-case + `given()/when()/then()` scenario layers. Doing both here at once conflates two refactors; **recommended** is to land the de-plumbing first (this plan), then evaluate a matching frontend scenario DSL as a follow-on once the Java shape has settled.
- **Interleaved arrange/act.** The lazy-start inversion assumes every `backend.*` staging call happens before the first frontend gesture in a given test. That is true of all current latest specs and is inherent to Pact V3 anyway (interactions must be registered before the mock server boots) — do we enforce it with a loud error from the harness ("cannot stage an interaction after the backend has started"), or leave it as a documented convention?
