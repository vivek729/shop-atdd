# 2026-07-14 15:16:27 UTC — Fix meta-prerelease-stage orchestration failures (false wait timeouts + commit-stage concurrency evictions)

🤖 **Picked up by agent** — `Valentina_Desk` at `2026-07-14T15:37:04Z`

## TL;DR

**Why:** The scheduled `meta-prerelease-stage` run [29336276564](https://github.com/optivem/shop/actions/runs/29336276564) failed with 5 `local` jobs at exit 124 and a cancelled `multitier-backend-java` commit stage. Neither is a product bug — every downstream run that was allowed to finish succeeded. Both defects are in the GitHub Actions orchestration: one job inherits a 30-minute wait default that is far shorter than the runs it waits on, and the commit-stage workflows let an unrelated push to `main` cancel the pipeline's SHA-pinned dispatched run.

**End result:** `meta-prerelease-stage` no longer fails spuriously when `main` is busy. The `local` and `commit` jobs wait as long as the meta pipeline's own declared budget allows, and pipeline-dispatched commit-stage runs are never evicted by concurrent pushes to `main`.

## Outcomes

- The `local` and `commit` jobs in `_meta-prerelease-pipeline.yml` wait on a deliberate `stage-timeout-seconds` budget (default 5400s / 90 min) instead of silently inheriting the action's 1800s default — a downstream run that queues for runners no longer reports a false failure in the parent, while a genuinely hung stage still fails in 90 minutes rather than dragging the meta run out for hours.
- A push to `main` during a scheduled meta run can no longer cancel that run's dispatched commit-stage workflow; push-triggered runs still supersede each other as before.
- All seven commit-stage workflows (monolith java/dotnet/typescript, multitier-backend java/dotnet/typescript, multitier-frontend-react) carry the same corrected concurrency shape — no language left behind.
- The scheduled `meta-prerelease-stage` passes even when the repo is under an active push burst, which is precisely the condition that broke run 29336276564.

## Evidence (root causes, pinned)

**Root cause 1 — false timeout.** `.github/workflows/_meta-prerelease-pipeline.yml:480-483`: the `local` job's `optivem/actions/trigger-and-wait-for-workflow@v1` step omits `timeout-seconds`, so it inherits the action's 1800s default. The sibling `pipeline` job at line 551 correctly passes `${{ inputs.pipeline-timeout-seconds }}` (14400s). The `commit` job at lines 523-527 has the same omission (it sets only `poll-interval`). Proof: downstream run [29336631848](https://github.com/optivem/shop/actions/runs/29336631848) ran 13:28:36 → 14:05:49 (37m13s) and **succeeded**; the parent gave up at the 30-minute mark while it was still queueing for runners.

**Root cause 2 — concurrency collision.** `.github/workflows/multitier-backend-java-commit-stage.yml:25-27` sets `group: ${{ github.workflow }}-${{ github.head_ref || github.ref }}` with `cancel-in-progress: true`. The meta pipeline dispatched the workflow at 13:53:22 (run 29338402634); a push to `main` triggered the same workflow at 13:55:26 (run 29338554576). Both map to the identical group `…-refs/heads/main`, so the push evicted the pipeline's SHA-pinned run: *"Canceling since a higher priority waiting request for multitier-backend-java-commit-stage-refs/heads/main exists"*. All seven commit-stage workflows have both `push:` and `workflow_dispatch:` triggers plus this group and `cancel-in-progress: true`, so all seven are vulnerable.

## ▶ Next executable step (resume here)

The code changes are done and `actionlint` passes clean. The only remaining unit is Step 4 — the live verification, which needs the fix pushed first: `gh workflow run meta-prerelease-stage.yml --repo optivem/shop`, then confirm no `local` job exits 124 and no commit-stage run is cancelled with "higher priority waiting request". This burns real CI minutes and is the user's call, so ask before dispatching.

## Steps

- [ ] Step 4: After the fix is committed and pushed, `workflow_dispatch` one `meta-prerelease-stage` run and confirm it goes green — ideally while pushing something to `main` during the window, to exercise the exact collision that broke run 29336276564. Watch for: no `local` job exiting 124, and no commit-stage run cancelled with "higher priority waiting request".

## Non-goals

- No product or test code changes. `compile-all.sh` and the `--sample` system-test sweep are **not** required for this plan — no `system/**` or `system-test/**` file is touched.
- Not fixing the underlying runner-queue contention that made the downstream run take 37 minutes; the fix is to wait properly, not to make the pipeline faster.
