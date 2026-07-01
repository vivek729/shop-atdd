# 2026-07-01 08:12:00 UTC — Rename the `on-branch` output to `on-main` for commit-stage and acceptance-stage

## TL;DR

**Why:** After landing the acceptance-stage main-vs-PR gate, the boolean that answers "is this commit on `main`?" is called `on-branch` everywhere (the shared `check-sha-on-branch@v1` action's own output field, and the job-level output we promoted it to in acceptance-stage). `on-branch` reads as "which branch is this," not "is this main" — `on-main` is the clearer name for what the value actually answers.
**End result:** The boolean is called `on-main` consistently wherever commit-stage and acceptance-stage reference it, with no functional change to gating behavior — same semantics (`true` = commit is an ancestor of `main`), new name only.

## Outcomes

- Acceptance-stage's locally-defined job output (`on-branch: ${{ steps.check-on-main.outputs.on-branch }}` in the `check` job, consumed as `needs.check.outputs.on-branch` in `run`/`promote`/`publish-tag`/`summary`) is renamed to `on-main` across all 12 acceptance-stage files, without changing gating behavior.
- **Decided: Option A (acceptance-stage only).** Commit-stage's reference (`steps.check-on-main.outputs.on-branch`, used inline in `if:` conditions across 7 commit-stage files) stays as `on-branch` — that's the shared action's own raw output field, and renaming it would require a cross-repo change to `optivem/actions` with floating-`v1`-tag breakage risk for ~11 stale rehearsal branches. Out of scope for this plan.
- No other academy repo is affected — confirmed via workspace-wide grep that `check-sha-on-branch@v1` has exactly one real consumer: the `shop` repo. This plan only touches `shop`'s 12 acceptance-stage files.

## ▶ Next executable step (resume here)

Step 1: rename the `on-branch` job output to `on-main` in `shop/.github/workflows/monolith-java-acceptance-stage.yml` first (as the reference edit), then port identically to the other 11 acceptance-stage files — same fan-out shape as the prior gating plan (1 reference file + parallel subagents for the rest).

## Steps

- [ ] Step 1: In `shop/.github/workflows/monolith-java-acceptance-stage.yml`, rename the `check` job's output line from `on-branch: ${{ steps.check-on-main.outputs.on-branch }}` to `on-main: ${{ steps.check-on-main.outputs.on-branch }}` (the action's own raw output field, `steps.check-on-main.outputs.on-branch`, stays unchanged — only our job-output name changes), and update every downstream consumer: the two gated steps in `run` (`if: needs.check.outputs.on-branch == 'true'` → `on-main`), the `publish-tag` job's `if:`, and the `summary` job's `success-version` expression.
- [ ] Step 2: Port the identical rename to the remaining 11 acceptance-stage files (monolith-java-cloud, monolith-dotnet × latest+cloud, monolith-typescript × latest+cloud, multitier-java/dotnet/typescript × latest+cloud) — pure find/replace of `needs.check.outputs.on-branch` → `needs.check.outputs.on-main` and the `check` job's `on-branch:` output key → `on-main:`, no other lines touched.
- [ ] Step 3: Verify — diff-review across all 12 affected files to confirm the only change is the output name (zero drift, zero behavior change). Workflow-only change, `./compile-all.sh` not applicable.
