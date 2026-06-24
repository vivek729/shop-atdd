# 2026-06-24 14:26:45 UTC — Fix multitier-typescript stub-backend startup (Cannot find module '/app/dist/main')

## TL;DR

**Why:** The `verify / local` stage for multitier-typescript fails: the stub-backend container exits (1) at startup with `Error: Cannot find module '/app/dist/main'`. A stray top-level `jest.integration.config.ts` (added in `92cecc6c`) makes `nest build` infer `rootDir` as the project root, shifting the entrypoint from `dist/main.js` to `dist/src/main.js`, which the Dockerfile `CMD ["node", "dist/main"]` can't find.
**End result:** `nest build` emits `dist/main.js` at the top level again (no stray `dist/jest.integration.config.js`), the stub-backend container starts, and the multitier-typescript local stage passes. The build is also immune to any future stray top-level `.ts`.

## Outcomes

What we get out of this — the goals and deliverables:

- `cd system/multitier/backend-typescript && npm run build` produces `dist/main.js` at the top level (not `dist/src/main.js`), and no `dist/jest.integration.config.js`.
- The `my-shop-stub-backend-1` container starts cleanly under `docker-compose.local.stub.yml`; the multitier-typescript `verify / local` stage is green again.
- The build is hardened: adding any future top-level `.ts` file (test config, script, etc.) can no longer shift the compiled output path.

## ▶ Next executable step (resume here)

Fix applied and build verified (Steps 1–2 done): `tsconfig.build.json` now has `"include": ["src/**/*"]`; `npm run build` emits `dist/main.js` at top level, no `dist/src/` nesting, no stray `dist/jest.integration.config.js`. Next: commit via `/commit` (Step 4) once the user approves, then optionally re-run the multitier-typescript `level=local` pipeline / `--sample` tests (Step 3) to confirm the stub-backend container boots.

## Steps

- [x] Step 1: In `system/multitier/backend-typescript/tsconfig.build.json`, add `"include": ["src/**/*"]` (NestJS-idiomatic) so compilation is pinned to `src/` regardless of stray top-level `.ts` files. (Keep the existing `exclude` list as belt-and-suspenders.)
- [x] Step 2: Verify the build — `cd system/multitier/backend-typescript && npm run build`; confirm `dist/main.js` exists at top level and `dist/jest.integration.config.js` is absent. ✓ verified: `dist/main.js` present, no `dist/src/` nesting, no stray config.
- [ ] Step 3: Verify the container/stage — bring up the stub stack for multitier-typescript (`GH_OPTIVEM_CONFIG=gh-optivem-multitier-typescript.yaml`, `gh optivem system start`) and confirm `my-shop-stub-backend-1` starts; or run the multitier-typescript `--sample` system tests. (Requires explicit user go-ahead before running local system tests.)
- [ ] Step 4: Commit via the `/commit` skill once verification passes and the user approves.

## Open questions

- None. Parallel implementations were checked and are not affected (monolith-typescript is Next.js → standalone `server.js`; frontend-react is Vite; Java/.NET compile to jar/dll), so this is a TypeScript-only, single-file fix.
