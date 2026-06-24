# 2026-06-24 12:00 UTC — Fix two TypeScript narrow-integration bugs surfaced by the `local`-superset dry-run

## TL;DR

**Why:** The `level=local` meta dry-run
([28096291916](https://github.com/optivem/shop/actions/runs/28096291916)) that
verified the `local`-stage-superset wiring went red on **two pre-existing
TypeScript bugs** introduced by the narrow-integration Testcontainers work
(`92cecc6c`). Both would independently red their `commit-stage` today; the
new `local` checks simply surfaced them at the fail-fast gate (working as
designed). They are out of scope for the superset plan and tracked here.

**End result:** Both TS narrow-integration suites pass `npm run lint` and
`npm run test:integration` on a bare CI runner; a re-run of the `level=local`
dry-run (with `fail-fast=false`) is green across all six `local` configs.

## Bugs (root-caused)

### Bug 1 — lint: `pg` is untyped in `multitier/backend-typescript`
- **File:** `system/multitier/backend-typescript/src/core/repositories/order.repository.integration.spec.ts`
- **Symptom:** 8 × `@typescript-eslint/no-unsafe-*` errors on the `new Client(...)`
  / `client.connect()` / `client.query()` / `client.end()` calls (lines 23–41) —
  eslint's type-aware rules resolve `pg`'s `Client` to `any`.
- **Root cause:** `pg` (and/or `@types/pg`) is not a typed dependency of
  `backend-typescript`. The byte-identical migration helper in
  `system/monolith/typescript/src/__tests__/db.integration.spec.ts` lints **clean**,
  because monolith-typescript has `pg` properly typed.
- **Why it slipped:** `git log` shows the file landed in `92cecc6c`; the
  superset plan only *now* runs `npm run lint` in `local`, so this was the first
  CI lint of the file outside its own commit-stage.

### Bug 2 — teardown race: monolith-typescript `lib/db` pool never closed
- **File:** `system/monolith/typescript/src/__tests__/db.integration.spec.ts`
  (+ `system/monolith/typescript/src/lib/db.ts`)
- **Symptom:** `Test suite failed to run — terminating connection due to
  administrator command`. `afterAll` calls `postgres.stop()` while the
  module-level `pg` `Pool` in `lib/db.ts` still holds a live connection; the
  container shutdown surfaces as an unhandled pool error.
- **Root cause:** `lib/db.ts` builds `const pool = new Pool(...)` at module load
  and exports query functions but **no close function**, so the test cannot
  release the pool before stopping the container. The multitier spec avoids this
  because `app.close()` disposes the TypeORM DataSource's pool first.

## Steps

- [ ] **Step 4 (verification — ⏳ needs user approval to trigger CI / run tests).**
  Re-trigger the `level=local` dry-run on `HEAD` with `fail-fast=false` and
  `auto-trigger-stage=false`
  (`gh workflow run meta-prerelease-dry-run.yml --ref main -f level=local
  -f variant=all -f skip-local=false -f auto-trigger-stage=false -f fail-fast=false`)
  and confirm all six `local` configs go green — proving both fixes hold and the
  superset gate is clean end-to-end. **Ask before triggering** (memory rule: this
  runs the component/system harness on CI).

## ▶ Next executable step (resume here)

All code fixes (Steps 1–3) are landed and compile/lint-clean. Only **Step 4**
remains: the **gated CI re-run** to prove all six `local` configs go green.
Resume = get user approval, then
`gh workflow run meta-prerelease-dry-run.yml --ref main -f level=local
-f variant=all -f skip-local=false -f auto-trigger-stage=false -f fail-fast=false`
on `HEAD`; confirm every `local` config is green. When green, delete Step 4 and
this plan.

## Done (committed)

- **Bug 1 (lint)** — added `@types/pg ^8.11.11` to
  `system/multitier/backend-typescript` devDependencies; `npm run lint` +
  `tsc --noEmit` now clean.
- **Bug 2 (teardown race)** — added `closePool()` to
  `system/monolith/typescript/src/lib/db.ts`; the integration spec's `afterAll`
  now calls `db.closePool()` before `postgres.stop()`.
- **Step 3 (cross-language audit)** — no fix needed: Java uses
  `@Testcontainers`/`@ServiceConnection` (framework-managed pool); .NET's
  `DisposeAsync()` already disposes `DbContext` before the container. The bug was
  TS-only.

## Open questions

None — both bugs are root-caused; fixes are mechanical. Step 3 may turn up a
third (Java/.NET) instance, handled inline per the CLAUDE.md cross-language rule.

## Risks

- **Local Testcontainers blocked on this dev machine** — Step 2's
  `npm run test:integration` spins up a Postgres container, which 400s on Engine
  29 here (standing constraint). If it can't run locally, verify the teardown fix
  by code inspection + rely on the Step 4 CI re-run.
- **`pg` version drift** — if `backend-typescript` pulls `pg` transitively via
  TypeORM, pin `@types/pg` to a compatible major to avoid a type mismatch.
