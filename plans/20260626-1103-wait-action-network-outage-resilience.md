# 2026-06-26 11:03:00 UTC — Make `trigger-and-wait-for-workflow` survive multi-minute GitHub API outages

## TL;DR

**Why:** A ~2.5-min `api.github.com` `i/o timeout` outage on a GitHub-hosted runner failed `meta-prerelease-stage` run [`28231344559`](https://github.com/optivem/shop/actions/runs/28231344559) — even though the watched run ([`28232431268`](https://github.com/optivem/shop/actions/runs/28232431268)) had **already completed successfully**. The wait phase wraps a single streaming `gh run watch` in `retry_run`, whose fixed 4-attempt / ~2.5-min budget gives up long before the action's documented 1800s `timeout-seconds` window.
**End result:** `actions/trigger-and-wait-for-workflow/wait.sh` polls run status in an outer loop bounded by `TIMEOUT_SECONDS`, where any single failed poll is non-fatal. Transient API outages shorter than the overall timeout no longer fail a pipeline whose dispatched run actually passed; genuine run failures and the hard timeout still fail loudly.

## Outcomes

What we get out of this — the goals and deliverables:

- A multi-minute GitHub API network outage during the wait phase **does not** fail the job as long as the watched run terminates within `TIMEOUT_SECONDS`. The wait honors its own documented 1800s budget instead of the incidental ~2.5-min `retry_run` ceiling.
- A genuine **run failure** (conclusion ≠ `success`) still fails the wait with a loud `::error::` naming the run id and conclusion (no behavioural regression — failed dispatched runs still fail the caller).
- The **hard timeout** behaviour is preserved: if the watched run hasn't terminated within `TIMEOUT_SECONDS`, the step exits 124 with the existing "may still be in progress" message.
- A short transient blip is still absorbed quietly by the existing `retry_run` (4-attempt) treatment on each individual poll; only the *outer* loop adds resilience beyond that.
- Implementation matches the `action.yml` contract, which already advertises "poll for completion" — the streaming `gh run watch` is replaced with actual polling.
- One fix covers every language pipeline (Java / .NET / TypeScript) — this is the single shared composite action; there are no parallel implementations to mirror.

## ▶ Next executable step (resume here)

Edit `actions/trigger-and-wait-for-workflow/wait.sh` in the **sibling `optivem/actions` repo** (not shop — resolve as `$(git rev-parse --show-toplevel)/../actions` or the equivalent academy checkout) to replace the streaming `retry_run gh run watch` (current lines 5–14) with an outer polling loop bounded by `TIMEOUT_SECONDS`:
- Compute `deadline` once from the loop start + `TIMEOUT_SECONDS` (use `SECONDS` or `date +%s`; note `Date.now()` constraints don't apply to bash).
- Each iteration: `retry_run gh run view "$RUN_ID" --repo "$REPOSITORY" --json status,conclusion` and parse with `jq -r`.
- Poll OK + `status == completed` → `exit 0` if `conclusion == success`, else `::error::` naming run id + conclusion and `exit 1`.
- Poll OK + not yet completed → `sleep "$POLL_INTERVAL"` and continue.
- Poll fails even after `retry_run` exhausts → `::warning::` (don't abort), `sleep "$POLL_INTERVAL"`, continue.
- Past `deadline` → keep the existing `::error::` exit-124 timeout message (current lines 15–17) and `exit 124`.
Then run the verification step (shellcheck + dry-run against run `28232431268`). This is a single-file mechanical rewrite — no design work remains.

## Steps

- [ ] Step 1: In `optivem/actions`, rewrite `actions/trigger-and-wait-for-workflow/wait.sh` as an outer polling loop bounded by `TIMEOUT_SECONDS`:
  - Keep `set -euo pipefail` and the opening `echo "Waiting for run $RUN_ID (timeout: ${TIMEOUT_SECONDS}s)..."`.
  - Establish a deadline (`deadline=$(( start + TIMEOUT_SECONDS ))`).
  - Loop while not past deadline; each iteration `source` the shared `retry.sh` and call `retry_run gh run view "$RUN_ID" --repo "$REPOSITORY" --json status,conclusion`.
  - On successful poll: parse `status`/`conclusion` (jq); if `completed`, exit 0 on `success` else `::error::` + exit 1; otherwise sleep `POLL_INTERVAL` and loop.
  - On failed poll (retry_run returned non-zero): `::warning::` that the poll failed transiently, sleep `POLL_INTERVAL`, loop — **do not exit**.
  - On deadline exceeded: emit the existing exit-124 timeout `::error::` and exit 124.
- [ ] Step 2: Confirm `jq` availability / parsing approach (GitHub-hosted Ubuntu runners ship `jq`; the action already targets bash). If preferred, use `--jq` on `gh run view` to avoid a separate `jq` invocation.
- [ ] Step 3: Verify — run `shellcheck actions/trigger-and-wait-for-workflow/wait.sh` (clean), then dry-run the loop logic against known run ids: `28232431268` (completed `success` → expect exit 0) and a known failed run (expect `::error::` + exit 1). Confirm a simulated poll failure logs `::warning::` and continues rather than aborting.
- [ ] Step 4: Commit in `optivem/actions` and confirm the `v1` tag / consumption path picks up the change (the shop pipelines reference `optivem/actions/...@v1`).

## Notes

- **Repo scope:** the fix lives entirely in the sibling `optivem/actions` repo, `actions/trigger-and-wait-for-workflow/wait.sh`. This plan file is recorded in `shop/plans/` (where `/fix-bug` was invoked), but **no shop source changes** are involved. shop's `compile-all.sh` and `--sample` system tests do **not** apply.
- **Why not just bump `retry_run` attempts:** `retry_run` is a global constant used across every external-call wrapper, and streaming `gh run watch` restarts from scratch on each retry — bumping attempts only moves the cliff rather than tying resilience to the documented `timeout-seconds`. The outer polling loop is the correct fix.
- **Root cause (pinned):** `wait.sh:6-12` wraps a single streaming `gh run watch` in `retry_run`; `retry_run` (`shared/retry.sh:34-35`) is fixed at 4 attempts / 5-15-45s backoff (~2.5-min ceiling). `i/o timeout` is in the retryable regex so it did retry, but the outage outlasted the budget. `action.yml:27-30` advertises an 1800s window that is never reached during an outage.

## Open questions

- Preserve the `rate-limit-threshold` input behaviour? It's currently passed to the action but `wait.sh` does not consult it. The rewrite could optionally gate polls on remaining API quota, or leave it as-is (out of scope for the flake fix). **Recommendation:** leave as-is — separate concern, keep this change focused on outage resilience.
