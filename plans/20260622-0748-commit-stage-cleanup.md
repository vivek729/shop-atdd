# 2026-06-22 07:48:00 UTC — Commit-stage workflow cleanup

> Split out of `20260622-0724-pr-build-publish-split.md` (workstream (a) shipped in commit `ba04682c`). This plan covers the deferred mechanical cleanups **(c)** and **(e)**. Both are independent of the Sonar/co-locate plan and can run in any order relative to it (but see the cross-plan note below).

## TL;DR

**Why:** Two bits of cruft remain in the 7 commit-stage workflows: (c) the SHA-ancestry step is misleadingly named `Verify Built SHA Is On Main` / `id: verify-main` ("Verify" implies failure, but the step only emits `on-branch=true/false`); (e) a dead `component-version` job output and a dead `Checkout Repository` step in the `check` job that nothing consumes.
**End result:** The SHA-check step is renamed to `Check commit is on main` / `id: check-on-main` (all `if:` references updated), and the dead `component-version` output + dead `check`-job checkout are removed — across all 7 commit-stage workflows.

## Outcomes

- **(c)** Step renamed `name: Check commit is on main` / `id: check-on-main`; every `steps.verify-main.outputs.on-branch` reference updated to `steps.check-on-main.outputs.on-branch`, across all 7 workflows.
- **(e)** Dead `component-version:` job-output line removed from all 7 workflows; `read-version` step **kept** (it still feeds the `dev-version` Docker tag).
- **(e)** Dead `Checkout Repository` step removed from the `check` job in all 7 workflows.

## ▶ Next executable step (resume here)

Start with **(c)** in `monolith-java-commit-stage.yml`: rename the `Verify Built SHA Is On Main` step to `name: Check commit is on main` / `id: check-on-main`, then update every `if: steps.verify-main.outputs.on-branch ...` gate to `steps.check-on-main.outputs.on-branch` (including the `push:` and `enable=` expressions added by workstream (a)). Then replicate across the other 6 workflows. Then do (e). Both are mechanical — batch-then-review is the right mode.

## Steps

### Workstream (c) — rename `verify-main` → `check-on-main`

- [ ] Step c1: Rename the step `name: Verify Built SHA Is On Main` → `name: Check commit is on main` and `id: verify-main` → `id: check-on-main` in all 7 commit-stage workflows. Rationale: the step is non-enforcing (emits `on-branch`, doesn't fail when false), so "Check" beats "Verify"; "commit" is the right noun (it's a `merge-base --is-ancestor` ancestry test, not a ref-name check); matches the action verb `check-sha-on-branch`.
- [ ] Step c2: Update **every** `steps.verify-main.outputs.on-branch` reference to `steps.check-on-main.outputs.on-branch` — all `if:` gates **plus** the `push: ${{ ... }}` and `enable=${{ ... }}` expressions added in workstream (a). Grep each file to confirm zero `verify-main` references remain.
- [ ] Step c3: Validate YAML; verify consistency across all 7.

### Workstream (e) — remove dead `component-version` output + dead `check`-job checkout

- [ ] Step e1: Confirm `component-version` (job output, ~line 62 in each commit-stage workflow) has no consumer. Evidence from parent: the only downstream job `summary` reads only `image-digest-url`; no `needs.run.outputs.component-version` reference exists in any workflow; job outputs can't cross workflow runs; prod-stages read versions independently via their own `read-component-versions` step; none of these workflows is `workflow_call`, so there's no reusable-workflow caller. Re-grep to confirm before removing.
- [ ] Step e2: Remove the `component-version:` job-output line from all 7 commit-stage workflows. **Keep the `read-version` step** — it still feeds `dev-version` (the Docker tag). Only the job-output declaration is removed.
- [ ] Step e3: Confirm the `check`-job `Checkout Repository` step is dead. Evidence: the `check` job's only real step is `Ensure Environment Variables Defined` (reads only `env:` values); the subsequent `actions/checkout` is the last step and nothing reads the working tree. The `run` job does its own checkout (`fetch-depth: 0`) for the build. Re-confirm per file before removing.
- [ ] Step e4: Remove the dead `Checkout Repository` step from the `check` job in all 7 commit-stage workflows.
- [ ] Step e5: Validate YAML; verify consistency across all 7; commit via `/commit`.

## Open questions

_None — both workstreams are decided. (c) naming was resolved in the parent; (e) removals are backed by the evidence above (re-grep to confirm before deleting)._

**Cross-plan note:** Workstream (d) in `20260622-0748-sonar-on-prs-and-colocate-publish.md` moves the SHA-check step. If **this** plan's (c) rename runs first, the step is `check-on-main` when (d) runs; if (d) runs first, it's still `verify-main`. Neither blocks the other — just use whatever id is current. No coordination needed beyond awareness.
