# 2026-06-24 09:50 UTC — TypeScript narrow-integration tests via testcontainers-node

## TL;DR

**Why:** `backend-typescript` (TypeORM) and `monolith-typescript` (raw `pg` driver) both have real DB adapters but no narrow-integration test infrastructure. The `integration` suite is `pending: true` in both `component-tests.yaml` files. The Java and .NET backends were handled in `[[20260623-1944-narrow-integration-rollout]]`; the TypeScript components were deferred here because they need the `testcontainers` npm package wired up — a one-time setup that is the same shape for both.

**End result:** Both TypeScript components have a real `OrderRepository` / `insertOrder` integration test against a live Postgres container (via `testcontainers` + `@testcontainers/postgresql`), a separate Jest integration config, and their `component-tests.yaml` `integration` suite unwired from `pending: true`.

**Resolved shape (no production-code changes in either component):**
- **backend-typescript** reuses the real `AppModule`: the spec sets `POSTGRES_DB_HOST/PORT/NAME/USER/PASSWORD` to the container's values in `beforeAll`, then `Test.createTestingModule({ imports: [AppModule] })` — the existing `forRootAsync` factory connects to the container. No custom TypeORM/`DataSource` override.
- **monolith-typescript** leaves `src/lib/db.ts` untouched: the spec sets the same `POSTGRES_DB_*` env vars in `beforeAll`, **then** `await import('../lib/db')` so the module-load-time `pg` Pool is constructed against the container. No lazy-init, no `resetPool` seam.
- For **both**, the container is started/stopped in `beforeAll`/`afterAll` inside the spec — **not** a Jest `globalSetup` (env mutated there does not reliably reach test workers). `jest.integration.config.ts` only scopes the suite (`testRegex`, `testEnvironment: node`).

## Outcomes

- `testcontainers` + `@testcontainers/postgresql` npm packages added to both projects.
- A separate Jest integration config (`jest.integration.config.ts` or equivalent) in each project so integration tests are not mixed with unit tests.
- `AbstractIntegrationTest` (or a `beforeAll`/`afterAll` Jest setup) that spins up a real Postgres container and tears it down after the suite.
- One real narrow-integration test per component:
  - **backend-typescript**: `OrderRepository` ↔ Postgres (save + find, via TypeORM `Repository<Order>`).
  - **monolith-typescript**: `insertOrder` + `findByOrderNumber` ↔ Postgres (via raw `pg` Pool — connection string overridden via env vars to point at the container).
- `component-tests.yaml` `integration` suite updated: `pending` removed, `command` + `sampleTest` + `requiresDocker: true` filled in.
- `gh optivem component test run --suite integration` passes for both components.

## ▶ Next executable step (resume here)

**Step 1 — backend-typescript setup.** Add `testcontainers` + `@testcontainers/postgresql` to `devDependencies`, create `jest.integration.config.ts` with `testRegex: .*\.integration\.spec\.ts$` and `testEnvironment: node` (no env-mutating `globalSetup`). In the spec's `beforeAll`, start the Postgres container and set `POSTGRES_DB_HOST` / `POSTGRES_DB_PORT` / `POSTGRES_DB_NAME` / `POSTGRES_DB_USER` / `POSTGRES_DB_PASSWORD` into `process.env`, then bootstrap the real `AppModule` via `Test.createTestingModule` — its existing `forRootAsync` factory already reads those env vars, so no TypeORM override is needed.

## Audit findings (2026-06-24)

Discovered during the `[[20260623-1944-narrow-integration-rollout]]` pre-execution audit.

| Component | ORM / DB driver | Test framework | Testcontainers? | Existing test infra |
|---|---|---|---|---|
| `backend-typescript` | TypeORM v0.3.28 + `pg` v8.20 | Jest + `ts-jest` + `@nestjs/testing` | ❌ not installed | `src/app.controller.spec.ts` unit test only |
| `monolith-typescript` | Raw `pg` driver (`src/lib/db.ts` — `insertOrder`, `findByOrderNumber`, etc.) | Jest (`package.json` scripts) | ❌ not installed | `src/__tests__/app.spec.ts` placeholder only |

**Why testcontainers-node is the right choice (not docker-compose or manual):**
- `testcontainers` npm package (`node.testcontainers.org`) is the idiomatic Node.js equivalent of the Java `testcontainers` library — same lifecycle model (`GenericContainer`, `PostgreSqlContainer`, `start()`/`stop()` in `beforeAll`/`afterAll`).
- No separate `docker-compose.test.yml` to maintain; the container lifecycle is owned by the test.
- Consistent with the Java and .NET patterns already in this repo (both use Testcontainers).
- `requiresDocker: true` in `component-tests.yaml` — same opt-in Docker gate as the other backends.

**Why `monolith-typescript` is simpler than `backend-typescript`:**
- Raw `pg` Pool reads connection config from env vars (`POSTGRES_DB_*`). Override those to the container values in a Jest `globalSetup` — no ORM/DI config to wrestle with.
- `backend-typescript` (NestJS + TypeORM) requires overriding the TypeORM `DataSource` config inside the NestJS test module — more wiring but well-established pattern.

## Steps

- [ ] **Step 1 — backend-typescript setup.** Add `testcontainers` + `@testcontainers/postgresql` to `devDependencies`. Create `jest.integration.config.ts` (`testRegex: .*\.integration\.spec\.ts$`, `testEnvironment: node`) — **no** env-mutating `globalSetup`. Write a `beforeAll` (or a shared helper imported by the spec) that starts a `PostgreSqlContainer` and sets the env vars the existing `AppModule` factory already reads: `POSTGRES_DB_HOST`, `POSTGRES_DB_PORT`, `POSTGRES_DB_NAME`, `POSTGRES_DB_USER`, `POSTGRES_DB_PASSWORD` (note: name var is `POSTGRES_DB_NAME`, not `POSTGRES_DB`). No custom TypeORM override is needed — set the env vars *before* `Test.createTestingModule({ imports: [AppModule] })` and the real `forRootAsync` factory connects to the container. Tear the container down in `afterAll`.
- [ ] **Step 2 — backend-typescript test.** Write `src/core/repositories/order.repository.integration.spec.ts` — in `beforeAll` start the container + set env (Step 1) then bootstrap `Test.createTestingModule({ imports: [AppModule] })`; in the test, save an order via `Repository<Order>`, read it back, assert. Tag with a marker (e.g. `describe('OrderRepository [integration]', ...)`) so the Jest filter catches it. Add `"test:integration": "jest --config jest.integration.config.ts"` to `package.json`. Confirm `npm run test:integration` passes locally (Docker up) and `npm run test:unit` still passes without Docker.
- [ ] **Step 3 — backend-typescript YAML.** In `system/multitier/backend-typescript/component-tests.yaml`: remove `pending: true` from the `integration` suite, add `command: npm run test:integration`, `sampleTest: <test name>`, `requiresDocker: true`.
- [ ] **Step 4 — monolith-typescript setup.** Same packages. Create `jest.integration.config.ts` (`testRegex`, `testEnvironment: node`; **no** env-mutating `globalSetup`). The container is started in a `beforeAll` inside the spec (Step 5). Env var names must match `src/lib/db.ts`'s Pool config: `POSTGRES_DB_HOST`, `POSTGRES_DB_PORT`, `POSTGRES_DB_NAME`, `POSTGRES_DB_USER`, `POSTGRES_DB_PASSWORD`.
- [ ] **Step 5 — monolith-typescript test.** Write `src/__tests__/db.integration.spec.ts` — in `beforeAll`: start `PostgreSqlContainer`, set the `POSTGRES_DB_*` env vars, **then** `const db = await import('../lib/db')` so the module-load Pool is built against the container (do **not** static-import `db.ts` at the top of the file). Call `db.insertOrder(...)`, `db.findByOrderNumber(...)`, assert the round-trip. Stop the container in `afterAll`. Add `"test:integration": "jest --config jest.integration.config.ts"` to `package.json`. Confirm `npm run test:integration` passes locally (Docker up).
- [ ] **Step 6 — monolith-typescript YAML.** In `system/monolith/monolith-typescript/component-tests.yaml`: remove `pending: true`, add `command`, `sampleTest`, `requiresDocker: true`.
- [ ] **Step 7 — Verify both.** `gh optivem component test run --suite integration` for both components. `--sample` works for each.

## Resolved decisions

- **NestJS TypeORM wiring → reuse the real `AppModule`.** `AppModule` already declares `TypeOrmModule.forRootAsync` with a `useFactory` that reads `POSTGRES_DB_HOST` / `POSTGRES_DB_PORT` / `POSTGRES_DB_NAME` / `POSTGRES_DB_USER` / `POSTGRES_DB_PASSWORD` from `process.env` (via `ConfigService`). So the integration test does **not** need a custom override or a hand-built `DataSource`: set those env vars to the container's values *before* bootstrapping, then `Test.createTestingModule({ imports: [AppModule] })` and the existing factory connects to the container. This exercises the real production wiring with minimal new code. (Note for Step 1: the DB-name env var is `POSTGRES_DB_NAME`, not `POSTGRES_DB`.)

- **`pg` Pool re-init → dynamic import, no production change.** Confirmed: `system/monolith/typescript/src/lib/db.ts` builds `const pool = new Pool({ host: process.env.POSTGRES_DB_HOST || 'localhost', ... })` at module-load time, freezing the connection details on first import. Resolution: the integration test starts the container and sets `process.env.POSTGRES_DB_*` in a `beforeAll`, **then** `await import('../lib/db')` so the Pool is constructed with the container values. `db.ts` is left untouched — the test exercises the real adapter as students ship it (no lazy-init, no `resetPool` seam).
- **Container starts in `beforeAll`, not Jest `globalSetup`.** Setting `process.env` inside a Jest `globalSetup` does not reliably propagate to test-worker processes, so for **both** components the container is started and env vars are set in a `beforeAll`/`afterAll` inside the test file (or a shared `AbstractIntegrationTest`-style helper imported by the spec). `jest.integration.config.ts` still exists to scope the integration suite (`testRegex`, `testEnvironment: node`), but does **not** carry a `globalSetup` that mutates env.

## Dependencies

- **Prerequisite:** `[[20260623-1944-narrow-integration-rollout]]` Steps 1–4 (Java + .NET components) must be done, or at least the `component-tests.yaml` `integration` pattern must be established for comparison.
- **No hard dependency** on any other open plan — this is self-contained.

## Cross-references

- `[[20260623-1944-narrow-integration-rollout]]` — parent rollout plan; this plan covers the two TypeScript components deferred from it.
- `[[20260624-0653-meta-narrow-integration-cluster]]` — coordination meta-plan; deferred TypeScript note lives in U4's wave-2 entry.
