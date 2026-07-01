# 2026-07-01 07:54:00 UTC — Remove the `force-run` workflow input

## TL;DR

**Why:** `force-run` bypasses the "no new artifacts / no test changes" skip-gate in each acceptance-stage workflow's `evaluate-run-gate` step. `_prerelease-pipeline.yml` and `_meta-prerelease-pipeline.yml` always pass `"force-run": "true"` when dispatching the acceptance stage, so a meta-triggered run always executes rather than being skipped. It touches 13 workflow files. The owner wants it removed to cut this surface area.
**End result:** The `force-run` input and its skip-condition usage are gone from every acceptance-stage workflow. The run-gate always evaluates purely on artifact/test-change freshness — meta-dispatched runs are no longer guaranteed to bypass that gate.

## Outcomes

- No workflow file declares or forwards a `force-run` input.
- `evaluate-run-gate`'s `skip-conditions` in each `*-acceptance-stage*.yml` no longer references `inputs.force-run`.
- `_prerelease-pipeline.yml` no longer sends `"force-run": "true"` in its `workflow-inputs` payloads.
- `grep -r "force-run" .github/workflows` returns no results.
- All affected workflows still pass YAML/actionlint validation after edits.

## ▶ Next executable step (resume here)

Step 1: In `.github/workflows/monolith-java-acceptance-stage.yml`, remove the `force-run` input declaration (lines 17-21) and drop the `inputs.force-run != true &&` clause from the skip-condition at line 143 (leaving the artifacts/test-changed check intact).

## Steps

- [ ] Step 1: Remove `force-run` input + skip-condition clause from `monolith-java-acceptance-stage.yml` (reference implementation for the other 5).
- [ ] Step 2: Apply the same removal to the other 5 `*-acceptance-stage.yml` files (monolith-dotnet, monolith-typescript, multitier-java, multitier-dotnet, multitier-typescript).
- [ ] Step 3: Apply the same removal to the 6 `*-acceptance-stage-legacy.yml` files.
- [ ] Step 4: Remove `"force-run": "true"` from both `workflow-inputs` JSON payloads in `.github/workflows/_prerelease-pipeline.yml` (lines ~410, ~445).
- [ ] Step 5: Run `grep -r "force-run" .github/workflows` to confirm zero remaining references.
- [ ] Step 6: Validate edited workflows (actionlint or equivalent) before committing.

## Open questions

- Without `force-run`, a meta-prerelease-dispatched acceptance-stage run can now be silently skipped by its own no-changes gate (e.g. re-running meta-prerelease-dry-run with no new commits since the last RC would no longer force a real exercise of the stage). Is that acceptable, or does something else need to compensate (e.g. the caller resolving `should-run` itself before dispatching)?
