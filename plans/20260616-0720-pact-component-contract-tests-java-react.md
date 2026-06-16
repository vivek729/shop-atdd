# 2026-06-16 07:20:23 UTC — In-process component & Pact contract tests (backend-java + frontend-react)

## TL;DR

**Why:** The multitier backend-java and frontend-react have no fast, in-process test layer. Today the only cross-tier coverage comes from the system tests, which require a deployed stack (docker compose). We want fast feedback that runs in CI and locally without deploying anything.

**End result:** backend-java has component tests (Spring Boot in-process, external systems stubbed with WireMock, DB in-memory) and Pact provider-verification tests. frontend-react has component tests (Vitest + React Testing Library, HTTP stubbed) and Pact consumer tests that generate the contract. The frontend↔backend boundary is verified by the shared Pact contract. **Everything runs in-process — no deployment, no docker compose.**

## Outcomes

What we get out of this — the goals and deliverables:

- **Backend component tests** that boot the Spring app in-process (no deployed stack), exercise real controller→service→repository flows, with `ErpGateway` / `TaxGateway` / `ClockGateway` external HTTP stubbed by **in-process WireMock** and the database on a **Testcontainers-managed Postgres** (real dialect, auto-started/torn-down per run — not a hand-started compose stack). `build.gradle` already has `testcontainers:junit-jupiter` + `testcontainers:postgresql` on the test classpath.
- **Frontend component tests** (Vitest + React Testing Library) that render pages/hooks (`useOrders`, `useCoupons`, `order-service`, `coupon-service`) against a stubbed HTTP layer — no running backend.
- **Pact consumer tests** in frontend-react that assert how it calls the backend and **generate the pact contract** (one pact per consumer↔provider pair).
- **Pact provider-verification tests** in backend-java that replay that contract against the in-process provider, with external systems stubbed — failing the build if the backend drifts from the contract.
- **No new infra to run the above:** component + contract tests run from `./gradlew test` and `npm test`, in CI and locally, with **no docker compose and no deployment**.
- **Scope is `system/multitier/` only** — `backend-java` + `frontend-react`. The **monolith is explicitly out of scope** (no changes to `system/monolith/**`). A documented follow-up may mirror the pattern into the *other multitier backends* (`backend-dotnet`, `backend-typescript`) per the CLAUDE.md "check all languages" rule — but still never the monolith.

## ▶ Next executable step (resume here)

Design is not yet finished — a few decisions in **Open questions** (pact sharing mechanism, where the component-test seam sits, frontend HTTP stubbing) should be settled before code is written. (DB is decided: Testcontainers-Postgres.) Resume by running `/refine-plan` on this file to lock those choices, **then** `/execute-plan`. The first executable unit will be: add the test-runner + Pact/WireMock dependencies to `system/multitier/backend-java/build.gradle` and `system/multitier/frontend-react/package.json` and prove one trivial in-process test runs green in each — but do **not** start until the open questions are resolved.

## Steps

- [ ] Step 1: Settle the remaining open questions (pact sharing, component-test seam, frontend HTTP stubbing) — via `/refine-plan`. (DB decided: Testcontainers-Postgres.)
- [ ] Step 2: **Frontend test harness** — add Vitest + React Testing Library + `@pact-foundation/pact` to `frontend-react`; wire an `npm test` script; prove one trivial render test green.
- [ ] Step 3: **Frontend component tests** — render the order + coupon flows (pages/hooks/services) against a stubbed `fetch`; cover loading / success / error / validation states.
- [ ] Step 4: **Frontend Pact consumer tests** — for `order-service` and `coupon-service`, write Pact interactions (place order, browse order history, view order details, browse/publish coupons) that generate the pact contract file.
- [ ] Step 5: **Backend test harness** — add WireMock + Pact JVM provider (`au.com.dius.pact.provider:junit5spring`) to `build.gradle` (Testcontainers-Postgres already present); prove one trivial in-process Spring test green with no compose.
- [ ] Step 6: **Backend component tests** — boot the app in-process, stub `ErpGateway`/`TaxGateway`/`ClockGateway` HTTP with WireMock, drive real use-case flows (place order with tax + promotion + clock, order history, coupon publish/browse) end-to-end through the API in-process.
- [ ] Step 7: **Backend Pact provider verification** — point the provider test at the consumer pact (per the sharing decision), define provider states, stub externals with WireMock, fail the build on contract drift.
- [ ] Step 8: **Wire into CI** — make `./gradlew test` / `npm test` run these in the existing build jobs (no compose); update `compile-all.sh` / docs as needed.
- [ ] Step 9: **Document + parity** — note the pattern in the ATDD/architecture docs and capture the follow-up to mirror into the other *multitier* backends (backend-dotnet / backend-typescript). **Monolith stays untouched.**

## Open questions

- **Pact contract sharing:** filesystem (both tiers in the same monorepo → provider reads the consumer-generated pact directly from a known path) vs. a Pact Broker. Filesystem is simpler and matches the monorepo; confirm no broker is wanted.
- **Component-test seam on the backend:** full in-process HTTP (`@SpringBootTest(webEnvironment=RANDOM_PORT)` hitting real endpoints) vs. `MockMvc` (no socket). Both are "in-process"; which is the intended component boundary?
- **Frontend test runner:** Vitest assumed (Vite project already). Confirm Vitest + React Testing Library (not Jest).
- **Frontend HTTP stubbing for component tests:** plain `fetch` mock / MSW vs. reuse the Pact mock server. Pact mock for the consumer tests is a given; what about the non-contract component tests?
- **Scope of this plan:** **multitier `backend-java` + `frontend-react` only** — monolith is out of scope (decided). Remaining question is just whether the other multitier backends (backend-dotnet / backend-typescript) are a documented follow-up or folded into this same plan.
