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
- **The Pact-callback inversion is solved once, in the driver + harness.** `PactV3.executeTest(cb)` is callback-shaped and demands its interactions staged *before* the mock server boots — the reason the callback leaked into the spec in the first place. `PactBackendStubDriver` inverts it: `backendUrl()` starts `executeTest` lazily on the first frontend gesture (by which point the arrange phase has staged everything) and holds the callback open; the harness's `afterEach` calls `finish()`, which releases it, awaits the verification, and rethrows Pact's unmatched-interaction failure so it still fails the owning test. Staging an interaction *after* the first gesture is a hard error with a message that says why — Pact requires arrange-before-act regardless, so this turns a silent misuse into a loud one.

- **No third driver concept.** The lazy "boot the backend, point the frontend driver at it" binding lives inside `FrontendDsl` (which is already the sole caller of `FrontendDriver`, and already owned the `useBackend` verb), not in a new decorator class. The codebase keeps exactly the two driver roles it has today: `FrontendDriver` (drives the system under test — `UiFrontendDriver` / `GatewayFrontendDriver`) and `BackendStubDriver` (stubs the external backend — `PactBackendStubDriver`), mirroring Java's `BackendDriver` vs. `ErpStubDriver` / `TaxStubDriver` / `ClockStubDriver`.
- **Symmetric treatment of both latest levels.** The integration specs (`GatewayFrontendDriver`) get the same harness, so component and integration differ only in which frontend driver the harness was configured with — the level boundary the `FrontendDsl` seam already documents.
- **The pact output is unchanged.** The same interactions, from the same shared `interactions/` builders, merged idempotently into the one canonical `contracts/frontend-backend.json`. This refactor is provably behaviour-preserving: the pact file is byte-comparable before and after.
- **`legacy/` stays raw.** The legacy specs are the deliberate "before" of the teaching pair and keep their raw Pact plumbing — the contrast is the lesson.

## Design (settled)

The end state, in four files. `BackendStubDsl`, `UiFrontendDriver`, `GatewayFrontendDriver`, the `interactions/` builders, and the `legacy/` specs are all untouched.

**A spec is arrange-act-assert in DSL only** — `componentHarness()` for the component level (drives the rendered UI), `integrationHarness()` for the narrow-integration level (drives the gateway directly). Nothing else distinguishes the two suites:

```tsx
import { componentHarness } from '../../support';

const { backend, frontend } = componentHarness();

describe('AdminCoupons', () => {
  it('shows coupons when they are returned', async () => {
    backend.returnsCoupons();

    await frontend.browseCoupons().execute().showsCoupon('SAVE10');
  });
});
```

**`src/test/support/component-harness.ts` (new)** — the TS twin of `AbstractComponentTest`. Handles are created once per spec file and reset per test, exactly like Java's field-initialized `erpStub`/`taxStub` + `resetComponentState()`:

```ts
export function componentHarness()   { return harness(() => new UiFrontendDriver()); }
export function integrationHarness() { return harness(() => new GatewayFrontendDriver()); }

function harness(newFrontendDriver: () => FrontendDriver) {
  const backendStub = new PactBackendStubDriver();
  const backend = new BackendStubDsl(backendStub);
  const frontend = new FrontendDsl(newFrontendDriver, () => backendStub.backendUrl());

  beforeEach(() => { backendStub.reset(); frontend.reset(); });
  afterEach(async () => { await backendStub.finish(); vi.unstubAllGlobals(); });

  return { backend, frontend };
}
```

**`pact-backend-stub-driver.ts`** — `runContract(cb)` is replaced by `backendUrl()` / `finish()` / `reset()`. `backendUrl()` lazily starts `executeTest`, resolves the mock-server URL, and holds the callback open on a deferred; `finish()` releases it and `await`s the run so Pact's verification failure still surfaces on the owning test. `stub()` throws if called after `backendUrl()` has started.

**`frontend-dsl.ts`** — takes a driver *factory* plus a `() => Promise<string>` backend-URL supplier instead of a live driver. A private `ready()` boots the backend on first use, constructs a fresh driver, calls `driver.useBackend(url)`, and memoizes; `reset()` clears it per test. The command classes hold `() => Promise<FrontendDriver>` instead of `FrontendDriver` — a mechanical `this.driver.x()` → `(await this.driver()).x()` change. `useBackend` stays on the `FrontendDriver` interface (drivers still need telling) but leaves the spec-facing surface.

## ▶ Next executable step (resume here)

Step 1: build `src/test/support/component-harness.ts` and rework `pact-backend-stub-driver.ts` (`runContract` → `backendUrl`/`finish`/`reset`, with the late-staging hard error) and `frontend-dsl.ts` (driver factory + lazy `ready()` binding + `reset()`) per the settled design above. Prove it by porting the single-test `coupon.component.test.tsx` onto the harness and diffing the regenerated `contracts/frontend-backend.json` against the current one — it must be identical. That unblocks Steps 2–3, which are then mechanical ports of the remaining three specs.

## Steps

- [ ] Step 1: **Harness + the Pact inversion.** Add `src/test/support/component-harness.ts` (`componentHarness()` / `integrationHarness()`, per-test reset, `finish()` in `afterEach`); rework `pact-backend-stub-driver.ts` to `backendUrl()` / `finish()` / `reset()` with the late-staging hard error; rework `frontend-dsl.ts` to take a driver factory + backend-URL supplier, with the lazy `ready()` binding and `reset()`. Export the harness from `src/test/support/index.ts`. Port `coupon.component.test.tsx` (one test) onto it as the proof, and diff `contracts/frontend-backend.json` before/after — it must be identical.
- [ ] Step 2: **Port the rest of the latest component specs.** Move `order.component.test.tsx` (8 tests) onto `componentHarness()`; delete every `runContract` / `useBackend` / driver construction / `afterEach(vi.unstubAllGlobals)` from the spec bodies.
- [ ] Step 3: **Port the latest integration specs.** `order.integration.test.ts` and `coupon.integration.test.ts` onto `integrationHarness()`.
- [ ] Step 4: **Tighten the comments.** The header comments in `frontend-dsl.ts` and `pact-backend-stub-driver.ts` currently *document* the leak — "the one plumbing leak the driver can't fully hide", "the executeTest mock-server lifecycle" as a spec-facing verb. Those sentences stop being true; rewrite them to describe the inversion and the level boundary instead.
- [ ] Step 5: **Verify.** `npx tsc --noEmit` plus the frontend component + contract test scripts (the opt-in, excluded-from-default ones) pass; the regenerated pact is byte-identical to the pre-refactor one.

## Open questions

- **Does this plan stop at de-plumbing, or also adopt the two-layer scenario DSL?** The sibling plan `20260714-1118-component-test-scenario-dsl-java.md` reshapes the Java component DSL into use-case + `given()/when()/then()` scenario layers. Doing both at once conflates two refactors, so this plan is scoped to the de-plumbing only; a matching frontend scenario DSL is a follow-on to evaluate once the Java shape has settled. Left open only as a reminder to revisit — it does not block execution.
