# 2026-07-09 11:57:27 UTC — Fix stage summary misreporting cancelled/failed acceptance stages as "Skipped"

## TL;DR

**Why:** When the `check` job of an acceptance-stage workflow is cancelled or fails (e.g. a GitHub-hosted runner is never acquired), the stage summary renders "⏭️ Skipped" with no reason instead of "🛑 Cancelled" / "❌ Failed" — a genuine failure painted as a benign skip. The `summary` job's `stage-result` expression only inspects `publish-tag`, so when `run` is skipped-due-to-cancelled-upstream it falls through to `'skipped'`.
**End result:** All six latest acceptance-stage workflows (and, via the consistency sweep, the legacy/prod/qa non-cloud stages) report `cancelled` and `failure` from any pipeline-spine job as 🛑/❌ in the summary, while legitimate gated skips still render ⏭️ Skipped with their reason.

## Outcomes

What we get out of this — the goals and deliverables:

- A cancelled `check`/`run`/`preflight`/`publish-tag` job in an acceptance stage renders **🛑 Cancelled** in the stage summary, not "⏭️ Skipped".
- A failure in any pipeline-spine job renders **❌ Failed**, closing the regression where the latest acceptance stages only guarded `publish-tag` failure.
- A legitimate gated skip (gate says `should-run=false` → `check` succeeds, `run` skipped) still renders **⏭️ Skipped** with its human-readable reason — unchanged.
- The same `cancelled` blind spot is closed consistently across the legacy, prod, and qa non-cloud stages (they already guard `failure`).
- No change to the `optivem/actions` repo — its `render-stage-summary` engine already supports `cancelled`/`failure` outcomes; only the workflows needed to feed them.

**Out of scope (documented, not fixed):** the original trigger — a GitHub-hosted runner never being acquired for run `29012601166` — is a transient infra flake that self-heals on the next hourly scheduled run. No in-repo change can prevent it; this plan only fixes how such an event is *reported*.

## ▶ Next executable step (resume here)

Apply the primary fix: in each of the six latest acceptance-stage workflows, replace the single `stage-result:` line in the `summary` job with the failure-or-cancelled expression below. Files and current line numbers:
- `.github/workflows/monolith-dotnet-acceptance-stage.yml:377`
- `.github/workflows/monolith-java-acceptance-stage.yml:385`
- `.github/workflows/monolith-typescript-acceptance-stage.yml:368`
- `.github/workflows/multitier-dotnet-acceptance-stage.yml:378`
- `.github/workflows/multitier-java-acceptance-stage.yml:387`
- `.github/workflows/multitier-typescript-acceptance-stage.yml:368`

Each currently reads:
```yaml
        stage-result: ${{ needs.publish-tag.result == 'failure' && 'failure' || needs.run.result }}
```
Replace with:
```yaml
        stage-result: >-
          ${{ (needs.preflight.result == 'failure' || needs.check.result == 'failure' || needs.run.result == 'failure' || needs.publish-tag.result == 'failure') && 'failure'
              || (needs.preflight.result == 'cancelled' || needs.check.result == 'cancelled' || needs.run.result == 'cancelled' || needs.publish-tag.result == 'cancelled') && 'cancelled'
              || needs.run.result }}
```
This unblocks the consistency sweep (Step 2) and verification (Step 3).

## Steps

- [ ] **Step 1 — Primary fix (required): 6 latest acceptance stages.** Replace the `stage-result` expression in the `summary` job of each of the six files listed in *Next executable step* with the failure-or-cancelled expression. All six share the identical job graph (`preflight → check → run → publish-tag`, plus `sonar`, with `summary` needing all five), so the expression's job names are valid in every file. Behavior after fix:
  - legitimate gate skip (`should-run=false` → `check` succeeds, `run` skipped) → **⏭️ Skipped** with reason (unchanged);
  - cancelled `check` (this incident) → **🛑 Cancelled**;
  - any spine failure → **❌ Failed**.
- [ ] **Step 2 — Consistency sweep (recommended, second-tier): add `cancelled` detection to legacy/prod/qa non-cloud stages.** These already guard `== 'failure'` but share the `cancelled` blind spot. For each, insert a parallel `... == 'cancelled' ... && 'cancelled'` branch immediately before the final `|| needs.run.result`, reusing the exact job set that file already references (do not add job names a given file doesn't declare).
  - Legacy acceptance (6 files, `*-acceptance-stage-legacy.yml`) — current: `(needs.preflight.result == 'failure' || needs.check.result == 'failure' || needs.run.result == 'failure') && 'failure' || needs.run.result`. Add the matching preflight/check/run `cancelled` branch.
  - Prod (`*-prod-stage.yml`) and QA (`*-qa-stage.yml`) non-cloud (12 files) — current: `(needs.check.result == 'failure' || needs.run.result == 'failure') && 'failure' || needs.run.result`. Add the matching check/run `cancelled` branch.
  - **Do NOT touch:** `*-cloud.yml` (different `promote`-based job structure), `*-commit-stage.yml` (single `run` job), `*-qa-signoff.yml` (uses `run.outputs.stage-result`). No changes to the `optivem/actions` repo.
- [ ] **Step 3 — Verify.** These are workflow YAML expression changes, not covered by `compile-all.sh` or `--sample` system tests. (a) Run `actionlint` on the changed workflows if available; otherwise manually confirm each edited expression references only job names that file actually declares and that YAML block-scalar indentation is valid. (b) Optionally trigger one `workflow_dispatch` run (or observe the next hourly scheduled run) and confirm a cancelled/failed upstream now renders 🛑/❌ rather than ⏭️.
- [ ] **Step 4 — Commit** via the repo-scoped commit flow once verification passes (ask before committing, per standing preference).

## Notes

- The `render-stage-summary` engine (`optivem/actions/render-stage-summary/render.sh`) already maps `cancelled → "🛑 Cancelled"` and `failure → "❌ Failed"`, and `validate.sh` only requires `success-version` when result is `success` — so feeding `cancelled`/`failure` is fully supported with no action-repo change.
- The legacy acceptance stages guard `failure` but **not** `cancelled` — so the fix is not merely "restore the legacy guard"; it adds `cancelled` detection that even legacy lacked (and which is what this specific incident needed).
