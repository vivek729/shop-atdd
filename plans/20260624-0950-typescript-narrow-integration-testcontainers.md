# 2026-06-24 09:50 UTC — TypeScript narrow-integration tests via testcontainers-node

## TL;DR

**Why:** `backend-typescript` (TypeORM) and `monolith-typescript` (raw `pg` driver) both have real DB adapters but no narrow-integration test infrastructure. The `integration` suite is `pending: true` in both `component-tests.yaml` files. The Java and .NET backends were handled in the narrow-integration rollout (plan `1944`, since completed and removed); the TypeScript components were deferred here because they need the `testcontainers` npm package wired up — a one-time setup that is the same shape for both.

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

> **All code is wired and verified Docker-free (2026-06-24).** Both components have `jest.integration.config.ts`, a real `*.integration.spec.ts` (seeding the canonical `system/db/migrations` DDL into a throwaway `postgres:16-alpine` container), a `test:integration` npm script, integration specs excluded from the default unit run, and their `component-tests.yaml` `integration` suite unwired from `pending`. `tsc --noEmit`, `jest --listTests` (unit excludes integration; integration finds its spec), and the unit suites all pass locally without Docker.

**Only remaining item: Step 7 — confirm the live-container runs are green in CI** (local Testcontainers is blocked on this machine). Watch the component-tests workflow for `backend-typescript` and `monolith/typescript`, suite `integration`. If green, delete Step 7 and this plan file. If red, reproduce against the CI logs and fix the spec/migration wiring.

## Audit findings (2026-06-24)

Discovered during the narrow-integration rollout (plan `1944`, since completed and removed) pre-execution audit.

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

- [ ] **Step 7 — Verify both in CI.** ⏳ Deferred: local Testcontainers is blocked on this machine (Docker Engine returns 400), so the live-container runs were not executed locally. Confirm `gh optivem component test run --suite integration` (and `--sample`) passes in CI for both `backend-typescript` and `monolith/typescript`. Everything that can be verified Docker-free passes locally: `tsc --noEmit`, `jest --listTests` (unit excludes integration; the integration config finds its spec), and both unit suites. Once CI is green, delete this item and the plan file.

### Done (committed 2026-06-24)

Steps 1–6 are implemented and verified Docker-free. Actual layout differs from the original draft in two ways worth recording:
- **monolith path** is `system/monolith/typescript/` (not `system/monolith/monolith-typescript/`).
- **Schema seeding:** because the real `AppModule` uses `synchronize: false` and `db.ts` uses a raw `pg` Pool, neither auto-creates the schema. Both specs apply the canonical `system/db/migrations/*.sql` (the same files Flyway runs for the Java backend) into the container via a short-lived `pg` `Client` in `beforeAll` — faithful to "real schema, no production override". The monolith integration config sets `forceExit: true` because `db.ts`'s module-load Pool has no close seam (left untouched on purpose).

## Resolved decisions

- **NestJS TypeORM wiring → reuse the real `AppModule`.** `AppModule` already declares `TypeOrmModule.forRootAsync` with a `useFactory` that reads `POSTGRES_DB_HOST` / `POSTGRES_DB_PORT` / `POSTGRES_DB_NAME` / `POSTGRES_DB_USER` / `POSTGRES_DB_PASSWORD` from `process.env` (via `ConfigService`). So the integration test does **not** need a custom override or a hand-built `DataSource`: set those env vars to the container's values *before* bootstrapping, then `Test.createTestingModule({ imports: [AppModule] })` and the existing factory connects to the container. This exercises the real production wiring with minimal new code. (Note for Step 1: the DB-name env var is `POSTGRES_DB_NAME`, not `POSTGRES_DB`.)

- **`pg` Pool re-init → dynamic import, no production change.** Confirmed: `system/monolith/typescript/src/lib/db.ts` builds `const pool = new Pool({ host: process.env.POSTGRES_DB_HOST || 'localhost', ... })` at module-load time, freezing the connection details on first import. Resolution: the integration test starts the container and sets `process.env.POSTGRES_DB_*` in a `beforeAll`, **then** `await import('../lib/db')` so the Pool is constructed with the container values. `db.ts` is left untouched — the test exercises the real adapter as students ship it (no lazy-init, no `resetPool` seam).
- **Container starts in `beforeAll`, not Jest `globalSetup`.** Setting `process.env` inside a Jest `globalSetup` does not reliably propagate to test-worker processes, so for **both** components the container is started and env vars are set in a `beforeAll`/`afterAll` inside the test file (or a shared `AbstractIntegrationTest`-style helper imported by the spec). `jest.integration.config.ts` still exists to scope the integration suite (`testRegex`, `testEnvironment: node`), but does **not** carry a `globalSetup` that mutates env.

## Dependencies

- **Prerequisite (satisfied):** the narrow-integration rollout (Java + .NET components, plan `1944`, completed and removed 2026-06-24) established the `component-tests.yaml` `integration` pattern to follow.
- **No hard dependency** on any other open plan — this is self-contained.

## Cross-references

- Narrow-integration rollout (plan `1944`, completed and removed 2026-06-24) — parent rollout; this plan covers the two TypeScript components deferred from it.
- Narrow-integration cluster meta-plan (`0653`, completed and removed 2026-06-24) — the coordination parent that deferred the TypeScript components here.
