# 2026-06-18 14:10 UTC — Fix stale monolith Docker images breaking local UI system tests

## TL;DR

**Why:** `gh optivem system start` reuses cached `my-shop-{stub,real}-system:latest` images instead of rebuilding from current source, so local UI system tests can run against a stale frontend and fail with confusing `aria-label`/locator timeouts that look like code regressions.
**End result:** `gh optivem system start` rebuilds the system image from current source before each run (or offers an explicit, documented rebuild path), so local UI tests exercise the code that's actually checked out — no more false stale-image failures.

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

## Open questions (resolve before executing)

1. **Where does `gh optivem system start` live, and how does it invoke compose?** — Need to locate the extension source (sibling repo) and confirm whether it calls `docker compose up` with or without `--build`. _Recommendation: locate it first; this gates every other step._
2. **Default to `--build`, or add an opt-in `--rebuild` flag?** — _Recommendation: make `--build` the default for the `*.local.*` path (correctness by default; cache keeps it fast) and add `--no-cache` as an explicit escape hatch. Re-confirm once start-time impact is measured._
3. **Scope: fix the tool, or just document + provide a helper?** — _Recommendation: fix the tool (default `--build`); fall back to documenting a pre-test rebuild step in `CLAUDE.md` only if the tool lives in a repo we shouldn't modify._

## Proposed steps (draft — pending open-question resolution)

1. Locate the `gh optivem system start` implementation and read how it runs docker compose for the local path.
2. Change the local `system start` to pass `--build` to `docker compose up` (rebuild from current source by default).
3. Add an opt-in `--no-cache` / `--rebuild-clean` flag for the deep-cache case.
4. Verify: with a deliberately stale image, `gh optivem system start` now rebuilds and the UI place-order sample passes for Java + TypeScript.
5. If the tool can't be modified, instead document the rebuild step in `CLAUDE.md` (System Test Verification section) and add a `rebuild-images.sh` helper.

## Done when

- `gh optivem system start` (local) serves an app built from the currently checked-out source, verified by the previously-failing UI place-order sample passing in Java + TypeScript without a manual rebuild.
- The stale-image gotcha is no longer reachable via the normal local test workflow.
