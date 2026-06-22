# 2026-06-18 14:10 UTC — Fix stale monolith Docker images breaking local UI system tests

## TL;DR

**Why:** `gh optivem system start` reuses cached `my-shop-{stub,real}-system:latest` images instead of rebuilding from current source, so local UI system tests can run against a stale frontend and fail with confusing `aria-label`/locator timeouts that look like code regressions.
**End result:** `gh optivem system start` rebuilds the system image from current source before each run (or offers an explicit, documented rebuild path), so local UI tests exercise the code that's actually checked out — no more false stale-image failures.

> **Status (2026-06-22):** Core ask superseded by `gh-optivem afcf00f` (2026-06-19), which added `system start --restart` (incremental `--build --force-recreate --no-deps`, keeps postgres up) plus the pre-existing `system build --rebuild` / `system clean` escape hatches. What remains is documenting the gotcha so people reach for `--restart` — see [Remaining work](#remaining-work).

## Problem

On 2026-06-18, the UI place-order system tests (`PlaceOrderPositiveTest [Channel: UI]` in Java, `shouldPlaceOrder [Channel: UI]` in TypeScript) failed with a 30s Playwright timeout waiting for `[aria-label="SKU"]`. Investigation showed:

- The **running** monolith app served `aria-label="Product SKU"` on `/new-order`, but the current source (since commit `d325a797`/`7ce32f0e`, 2026-05-04) and **all three** language drivers use `aria-label="SKU"`.
- `gh optivem system start` brought up `my-shop-stub-system:latest` / `my-shop-real-system:latest` that were **29 hours old** — it reuses existing images and does not force a rebuild.
- All three language monolith composes share the same compose project name (`my-shop-stub` / `my-shop-real`), so they share one image tag — confusing to reason about.
- .NET passed only because its image happened to be current; Java/TS were stale.
- A manual `docker compose -f docker/typescript/monolith/docker-compose.local.stub.yml build --no-cache system` made the served page show `aria-label="SKU"` and resolved the mismatch.

Net: a pure infrastructure artifact masquerading as a code regression, costing a long investigation.

## Constraints / unknowns

- The `gh optivem` CLI is a `gh` extension that lives **outside** the `shop` repo (likely a sibling workspace repo). The actual `system start` implementation must be located before editing — this plan may touch a different repo than `shop`.
- A plain `docker compose up --build` is cache-aware and usually fast (only changed layers rebuild), so defaulting to `--build` is cheap in the common case; `--no-cache` should remain opt-in for the rare deep-cache problem.
- CI uses the `docker-compose.pipeline.*.yml` variants and builds fresh per run, so this is a **local-dev-only** problem — the fix should target the local (`*.local.*`) start path.

## What the tool now provides (resolved)

The tool side of this is done — `gh-optivem/internal/build/runner/system.go` + `system_commands.go` now expose:

- `gh optivem system start` (default) — `down` + `up -d`, **no build**: reuses the cached image (the stale-image trap is reachable here by design, to keep re-runs fast).
- `gh optivem system start --restart` — `up -d --build --force-recreate --no-deps`, keeps postgres running: rebuilds changed services from current source. **This is the fix for the failure in `## Problem`.**
- `gh optivem system build --rebuild` — `docker compose build --no-cache` for the deep-cache case.
- `gh optivem system clean` — `down -v --rmi local` (drop volumes + locally-built images).

Resolved open question: **opt-in won over a `--build` default.** Defaulting plain `start` to `--build` would tax the common "stack already healthy, just re-run tests" path; the surgical `--restart` recreate is the better trade-off.

## Remaining work

The only gap left is **discoverability** — plain `start` still silently serves a stale image, and nothing tells the user to reach for `--restart` after changing code.

1. Document the stale-image gotcha and `--restart` usage in `CLAUDE.md` (System Test Verification section), next to the existing `d325a797` pre-commit-hook hook.
2. (Optional, decide) Should plain `start` emit a warning when it skips a rebuild against changed source, or is documenting `--restart` sufficient? _Leaning: docs are enough; a warning risks noise on every fast re-run._

## Done when

- The stale-image gotcha and the `--restart` remedy are documented where a contributor running local system tests will see them (`CLAUDE.md`).
- A contributor who changed frontend/app code knows to run `gh optivem system start --restart` (or `system build --rebuild`) rather than silently testing a stale image.
