# 2026-06-24 13:08 UTC — Fix multitier-backend-typescript Component + Provider Verification suites

## TL;DR

The scheduled `meta-prerelease-stage` run [28099424434](https://github.com/optivem/shop/actions/runs/28099424434) showed the `multitier-backend-typescript` commit stage with **Provider Verification (Pact) FAILED** and **Component PENDING**. Investigation:

- **Provider Verification** is **already fixed on `main`** (commit `52e47ac5`). The failing run executed an older sha (`8207e27b`) that predates the fix. Action: re-run the commit stage on current `main` to get CI proof — no code change.
- **Component** is a `pending: true` stub in `system/multitier/backend-typescript/component-tests.yaml`. It is the only multitier backend missing a real Component suite (Java + .NET both have one). Action: implement a real Component suite reusing the Pact spec's in-process Testcontainers + nock harness.

Decisions taken (see [Resolved decisions](#resolved-decisions)): verify Pact via a fresh CI re-run; build a **full-fidelity** Component suite (Testcontainers + nock), scoped to **multitier-backend-typescript only**.

## Problem

`gh optivem component test run --component backend --suite provider-verification` failed in the commit stage:

```
Could not find a working container runtime strategy
  at getContainerRuntimeClient (testcontainers/.../client.js:67:11)
  at PostgreSqlContainer.start (...)
  at Object.<anonymous> (pact/backend.pact.spec.ts:49:16)
```

and the Component suite reported `PENDING` (skipped, not implemented).

### Root cause — Provider Verification (TWO stacked bugs)

**Bug 1 (nock/Testcontainers ordering) — already fixed.** At sha `8207e27b`, `backend.pact.spec.ts` called `nock.enableNetConnect('127.0.0.1')` **before** `new PostgreSqlContainer(...).start()`. nock patches Node's HTTP client; restricting net-connect to `127.0.0.1` blocks Testcontainers from reaching the Docker daemon socket (host `localhost`, not `127.0.0.1`), so container-runtime detection fails with *"Could not find a working container runtime strategy."* Commit `52e47ac5` reordered it (container → then nock). The re-run on current `main` confirmed the container now starts and the test reaches the Verifier — Bug 1 is genuinely fixed.

**Bug 2 (wrong contract-file path) — the real remaining failure.** Re-running the commit stage on current `main` (run [28101011683](https://github.com/optivem/shop/actions/runs/28101011683)) surfaced a *different* error, previously masked by Bug 1:

```
the required ffi function 'pactffiVerifierAddDirectorySource' failed validation:
'/home/runner/work/shop/shop/system/contracts/frontend-backend.json' does not exist
```

The spec resolved the contract with `path.resolve(__dirname, '../../../../contracts/frontend-backend.json')`. `__dirname` is `system/multitier/backend-typescript/test/pact` — **5 levels** below the repo root — so reaching the repo-owned `contracts/` folder needs **5** `../`, not 4. Four lands at `system/contracts/` (nonexistent). Java reads it correctly via `@PactFolder("../../../contracts")` and .NET via 7 `../` from its bin dir. Fix: change `../../../../` → `../../../../../`. The contract file is git-tracked at `contracts/frontend-backend.json` and declares all 7 provider states the spec handles.

**Lesson:** the "already fixed, just re-run" assumption in the first draft of this plan was wrong — Bug 1's fix unmasked Bug 2. Phase 0's empirical re-run is what caught it.

### Root cause — Component

`component` is declared `pending: true` in `system/multitier/backend-typescript/component-tests.yaml`, so the CLI skips it. Cross-language survey:

| Implementation | Component suite | DB | Externals |
|---|---|---|---|
| multitier backend-java | `componentTest` source set; `PlaceOrder`/`Coupon`/`OrderHistory` + harness smoke | Testcontainers Postgres | in-process WireMock |
| multitier backend-dotnet | `Tests/Component/BackendApplicationTests.cs` | EF in-memory | (none) |
| **multitier backend-typescript** | **pending stub** | — | — |
| monolith java / dotnet / frontend-react | real suites | — | — |
| monolith typescript | pending stub (out of scope here) | — | — |

The TS Pact spec already constructs exactly the harness a Component suite needs (Nest app on a real port, Testcontainers Postgres, nock-stubbed ERP/Tax/Clock). Java explicitly shares its harness between component tests and Pact verification (`AbstractComponentTest`).

## Goal

1. Get a green CI run of `multitier-backend-typescript-commit-stage` on current `main`, proving Provider Verification passes.
2. Replace the `component` pending stub with a real, gating Component suite for `multitier-backend-typescript`, at parity with the Java component tests.
3. Keep the default path $0 / zero-extra-infra: the suite reuses the Docker that Narrow Integration + Provider Verification already require (`requiresDocker: true`), adds no new dependency or external service.

## Steps

### Phase 0 — Verify Provider Verification is actually fixed (do first)
- [ ] Re-run the commit stage on current `main`: dispatch `multitier-backend-typescript-commit-stage.yml` (or push a trivial touch under `system/multitier/backend-typescript/**`). **Requires explicit user go-ahead per repo convention.**
- [ ] Confirm the `Run Provider Verification (Pact)` step is green. If it still fails → the reorder was insufficient; fall back to investigating jest worker env / Docker socket exposure in the `run` job.

### Phase 1 — Extract a shared Component/Pact harness
- [ ] Factor the in-process boot from `test/pact/backend.pact.spec.ts` (Testcontainers Postgres start, Nest module wiring, global pipes/filters/interceptors, nock external stubs, `resetState`, stub helpers) into a reusable helper, e.g. `test/support/component-harness.ts`, mirroring Java's `AbstractComponentTest`.
- [ ] Re-point `backend.pact.spec.ts` at the shared harness so behavior is unchanged (run `npm run test:pact` to confirm — local Docker permitting, else rely on CI).

### Phase 2 — Implement the Component suite
- [ ] Add `test/jest-component.json` (mirror `jest-pact.json`; `testRegex: component/.*\.component\.spec\.ts$`).
- [ ] Add `"test:component": "jest --config ./test/jest-component.json"` to `package.json` scripts.
- [ ] Add component specs under `test/component/` porting the Java scenarios via real HTTP (supertest/`app.getHttpServer()`):
  - `place-order.component.spec.ts` — totals from price/promotion/tax; active promotion discount; coupon discount; New-Year blackout → 422; unknown product → 422.
  - `coupon.component.spec.ts`, `order-history.component.spec.ts` — match `CouponComponentTest` / `OrderHistoryComponentTest`.
  - optional harness smoke (`bootsInProcessAndServesHttp` analog) hitting `/health`.

### Phase 3 — Wire it into the CLI config + workflow
- [ ] In `system/multitier/backend-typescript/component-tests.yaml`, replace the `component` `pending: true` block with:
  ```yaml
  - id: component
    name: Component
    command: npm run test:component
    sampleTest: "computes totals from price, promotion and tax"   # match a real test name
    requiresDocker: true
  ```
- [ ] No workflow edit needed — `multitier-backend-typescript-commit-stage.yml` already calls `gh optivem component test run --component backend --suite component`; it currently no-ops on the pending stub and will start gating once the suite is real.

### Phase 4 — Verify
- [ ] `npm run build` + `npx tsc --noEmit` (compile gate per CLAUDE.md).
- [ ] Local sample run if Docker available, else push and watch CI: `Run Component Tests` and `Run Provider Verification (Pact)` both green.
- [ ] Confirm the Docker image still builds/publishes (suite gates the image but must not break it).

## Resolved decisions
- **Verify Pact via fresh CI re-run on `main`** (not "trust the fix"). The reorder is already merged; we want a green run on the new sha before closing.
- **Full-fidelity Component harness** (Testcontainers Postgres + nock), matching Java rather than .NET's in-memory approach — the TS Pact spec already pays for this harness, so reuse it; keeps fidelity and avoids a second DB code path.
- **Scope: multitier-backend-typescript only.** `monolith-typescript` (also a pending Component stub) is explicitly out of scope for this plan; track separately if parity there is wanted. Note monolith TS Provider Verification is intentionally deferred (no in-process consumer).

## Open questions
- OQ1: Does the `run` job's `ubuntu-latest` reliably expose the Docker socket to the jest worker the way the standalone `component-tests` job did at `8207e27b`? Phase 0 answers this empirically. (Narrow Integration passing in the old job suggests yes.)
- OQ2: Should the shared harness live under `test/support/` or be co-located? Pick during Phase 1; prefer `test/support/` to keep `test/pact` and `test/component` symmetric.

## Risks
- **Tension with the "Component/Pact layer is opt-in, off the default build" principle.** Plan `0916` (landed) deliberately gates all four suites in the commit-stage `run` job, so this plan follows the *current* gating design. If the opt-in principle is reaffirmed, gating Component would need to be revisited repo-wide, not just here — out of scope.
- **Docker flakiness in CI** — both Component and Provider Verification now `requiresDocker`; a runner without Docker fails both. Mitigated by the integration suite already depending on Docker successfully.
- **Harness refactor regressing Pact** — Phase 1 keeps `backend.pact.spec.ts` behavior identical; verify before adding Component specs.

## Status (2026-06-24 ~13:30 UTC)
- **Phase 0 done** — re-run on `main` confirmed Bug 1 fixed and surfaced Bug 2 (contract path).
- **Phases 1–3 done (uncommitted)**: shared `test/support/component-harness.ts` extracted; `backend.pact.spec.ts` re-pointed at it AND contract path fixed (`../../../../../`); `test/jest-component.json` + `test:component` script added; three component specs under `test/component/` (place-order, coupon, order-history); `component-tests.yaml` stub flipped to a real gating suite.
- **Verified locally**: `npx tsc --noEmit`, `npm run build`, `npx eslint test/**` all clean. The suites themselves can't run locally (Testcontainers/Docker blocked here) — CI is the gate.

## ▶ Next executable step (resume here)
Commit + push the changes (needs explicit user go-ahead per repo convention). The push to `system/multitier/backend-typescript/**` triggers the commit stage, which is the only way to verify Component + Provider Verification green (local Docker blocked). Watch run, confirm both suites pass and the image builds.
