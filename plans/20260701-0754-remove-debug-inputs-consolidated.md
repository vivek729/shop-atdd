# 2026-07-01 07:54:00 UTC — Remove the `debug-skip-tests`, `gh-optivem-ref`, and `force-run` workflow inputs

## TL;DR

**Why:** Three sibling debug/bypass inputs (`debug-skip-tests`, `gh-optivem-ref`, `force-run`) each add surface area to the acceptance/prerelease pipeline, and all three were independently slated for removal by the owner. They overlap heavily on the same workflow files (the 6 `*-acceptance-stage.yml`, 6 `*-acceptance-stage-legacy.yml`, and `_prerelease-pipeline.yml`), so removing them in one consolidated pass avoids opening the same files three separate times.
**End result:** None of the three inputs exist anywhere under `.github/workflows`. Acceptance tests always run in full with no debug bypass; `gh-optivem` is always installed from the latest release with no ref override; the run-gate always evaluates purely on artifact/test-change freshness with no force override.

## Outcomes

- No workflow file declares or forwards `debug-skip-tests`, `gh-optivem-ref`, or `force-run`.
- Every `if: ${{ !inputs.debug-skip-tests }}` (and inverse) guard is removed, with the previously-conditional steps now unconditional (or deleted, per what each guard protected).
- `meta-prerelease-stage.yml`'s run-name segment (`[debug-skip-tests]`) and its `debug-skip-tests=...` debug echo are removed.
- `_meta-prerelease-pipeline.yml`'s poll-interval tuning (`60` vs `180`) collapses to a single fixed value.
- `install-gh-optivem@v1` steps no longer pass a `ref:` — CLI is always installed from the latest release.
- `meta-prerelease-dry-run.yml`'s `trigger-stage` job condition and run-name segment that reference `gh-optivem-ref` are simplified/removed accordingly.
- `evaluate-run-gate`'s `skip-conditions` in each `*-acceptance-stage*.yml` no longer references `inputs.force-run`.
- `_prerelease-pipeline.yml` no longer sends `"force-run": "true"` in its `workflow-inputs` payloads, and no longer forwards `gh-optivem-ref` or `debug-skip-tests`.
- `grep -rE "debug-skip-tests|gh-optivem-ref|force-run" .github/workflows` returns no results.
- All affected workflows still pass YAML/actionlint validation after edits.

## ▶ Next executable step (resume here)

Step 1: In `.github/workflows/monolith-java-acceptance-stage.yml`, remove all three input declarations (`debug-skip-tests` lines 12-16, `force-run` lines 17-21, `gh-optivem-ref` on the `install-gh-optivem` step) — unconditionalize every `if: ${{ !inputs.debug-skip-tests }}` guard, drop the `inputs.force-run != true &&` clause from the skip-condition at line 143, and drop the `ref: ${{ inputs.gh-optivem-ref }}` line from the `install-gh-optivem` step. This file is the reference implementation for the other 5 acceptance-stage files.

## Steps

- [ ] Step 1: Remove all three inputs (`debug-skip-tests`, `force-run`, `gh-optivem-ref`) + their gated logic from `monolith-java-acceptance-stage.yml` (reference implementation for the other 5 acceptance-stage files).
- [ ] Step 2: Apply the same triple removal to the other 5 `*-acceptance-stage.yml` files (monolith-dotnet, monolith-typescript, multitier-java, multitier-dotnet, multitier-typescript).
- [ ] Step 3: Apply the same triple removal to the 6 `*-acceptance-stage-legacy.yml` files (also remove the "tag the RC anyway" bypass behavior tied to `debug-skip-tests` in the legacy variant).
- [ ] Step 4: In `.github/workflows/_prerelease-pipeline.yml` remove all three inputs — `debug-skip-tests` (poll-interval ternary → collapse to fixed `180`), `force-run` (`"force-run": "true"` in both `workflow-inputs` JSON payloads, ~L410/~L445), and `gh-optivem-ref` (input decl ~L53-57, forwarding in both `workflow-inputs` payloads ~L410/~L445).
- [ ] Step 5: Remove from `.github/workflows/_meta-prerelease-pipeline.yml` — `debug-skip-tests` (input decl ~L31, debug echo ~L108, `!inputs.debug-skip-tests` clause in local-stage `if:` ~L481, forwarded `workflow-inputs` payload ~L564, two `!inputs.debug-skip-tests` clauses gating manifest/summary jobs ~L577/~L603) and `gh-optivem-ref` (input declaration + forwarding to acceptance/legacy stage dispatches). (`force-run` is not declared in this file.)
- [ ] Step 6: Remove `debug-skip-tests` from `.github/workflows/meta-prerelease-stage.yml` — input declaration (~L12), run-name segment (~L4), debug echo (~L42), forwarding at ~L164/~L172.
- [ ] Step 7: Remove `gh-optivem-ref` from `.github/workflows/meta-prerelease-dry-run.yml` — this is the trickiest file: also update the `run-name` expression (drop the `[gh-optivem-ref:…]` segment) and the `trigger-stage` job's `if:` condition (currently gated on `inputs.gh-optivem-ref == ''`; decide what — if anything — replaces that safety gate once the input no longer exists, per Open Question 4).
- [ ] Step 8: Remove `gh-optivem-ref` from the 6 `prerelease-pipeline-<flavor>.yml` files.
- [ ] Step 9: Remove `gh-optivem-ref` from `.github/workflows/drift.yml` (input decl at ~L44/L55, usage at ~L118/L298) and `.github/workflows/cross-lang-system-verification.yml` (input decl ~L25, usage ~L149).
- [ ] Step 10: Run `grep -rE "debug-skip-tests|gh-optivem-ref|force-run" .github/workflows` to confirm zero remaining references to any of the three inputs.
- [ ] Step 11: Validate all edited workflows (actionlint or equivalent) before committing.

## Open questions

1. **(debug-skip-tests)** This removes the only fast-iteration path for testing meta-prerelease/prerelease pipeline *plumbing* changes without paying for a full acceptance test run every time. Is that development workflow being replaced by something else, or is the extra CI time simply accepted going forward?
2. **(debug-skip-tests)** The legacy acceptance-stage variant uses `debug-skip-tests` to still tag the RC while skipping tests — removing it means that behavior disappears entirely (no way to mint a legacy RC tag without running tests). Confirm this is intended, not just "remove the input but keep an equivalent bypass."
3. **(gh-optivem-ref)** Removing this input also removes the only mechanism for testing shop's acceptance/prerelease pipelines against an unreleased `gh-optivem` branch/tag/SHA before it ships — and removes the safety gate in `meta-prerelease-dry-run.yml` that prevents such a dry-run from auto-cascading into a real meta-RC release. Confirmed acceptable?
4. **(gh-optivem-ref)** Once `gh-optivem-ref` is gone, should the `trigger-stage` job in `meta-prerelease-dry-run.yml` simply always run when `auto-trigger-stage` is true (i.e. drop the extra condition entirely), or should some other guard take its place?
5. **(force-run)** Without `force-run`, a meta-prerelease-dispatched acceptance-stage run can now be silently skipped by its own no-changes gate (e.g. re-running meta-prerelease-dry-run with no new commits since the last RC would no longer force a real exercise of the stage). Is that acceptable, or does something else need to compensate (e.g. the caller resolving `should-run` itself before dispatching)?
