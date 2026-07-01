# 2026-07-01 07:54:00 UTC — Remove the `debug-skip-tests` workflow input

## TL;DR

**Why:** `debug-skip-tests` is a documented DEBUG-ONLY flag that bypasses acceptance tests (and, in the legacy sample, tags the RC anyway) so pipeline plumbing can be iterated on quickly. It gates dozens of `if:` conditions across 21 workflow files, including run-name display and poll-interval tuning. The owner wants it removed to cut this surface area.
**End result:** The `debug-skip-tests` input and every step/condition gated on it are gone. Acceptance tests always run in full; there is no way to mint an RC tag without actually exercising the test suites.

## Outcomes

- No workflow file declares or forwards a `debug-skip-tests` input.
- Every `if: ${{ !inputs.debug-skip-tests }}` (and inverse) guard is removed, with the previously-conditional steps now unconditional (or the previously-skipped steps deleted, per what each guard protected).
- `meta-prerelease-stage.yml`'s run-name segment (`[debug-skip-tests]`) and its `debug-skip-tests=...` debug echo are removed.
- `_meta-prerelease-pipeline.yml`'s poll-interval tuning (`60` vs `180`) collapses to a single fixed value.
- `grep -r "debug-skip-tests" .github/workflows` returns no results.
- All affected workflows still pass YAML/actionlint validation after edits.

## ▶ Next executable step (resume here)

Step 1: In `.github/workflows/monolith-java-acceptance-stage.yml`, remove the `debug-skip-tests` input declaration (lines 12-16), then walk every `if: ${{ !inputs.debug-skip-tests }}` guard in the `run` job (test/harness steps) and make each step unconditional.

## Steps

- [ ] Step 1: Remove `debug-skip-tests` input + unconditionalize gated steps in `monolith-java-acceptance-stage.yml` (reference implementation for the other 5 acceptance-stage files).
- [ ] Step 2: Apply the same removal to the other 5 `*-acceptance-stage.yml` files.
- [ ] Step 3: Apply the same removal to the 6 `*-acceptance-stage-legacy.yml` files (also remove the "tag the RC anyway" bypass behavior documented on this input).
- [ ] Step 4: Remove `debug-skip-tests` from `.github/workflows/_prerelease-pipeline.yml` — input declaration, the `poll-interval` ternary (collapse to fixed `180`), and both `workflow-inputs` JSON payloads.
- [ ] Step 5: Remove from `.github/workflows/_meta-prerelease-pipeline.yml` — input declaration (~L31), debug echo (~L108), the `!inputs.debug-skip-tests` clause in the local-stage `if:` (~L481), the forwarded `workflow-inputs` payload (~L564), and the two `!inputs.debug-skip-tests` clauses gating manifest/summary jobs (~L577, ~L603).
- [ ] Step 6: Remove from `.github/workflows/meta-prerelease-stage.yml` — input declaration (~L12), run-name segment (~L4), debug echo (~L42), and forwarding at ~L164/~L172.
- [ ] Step 7: Run `grep -r "debug-skip-tests" .github/workflows` to confirm zero remaining references.
- [ ] Step 8: Validate edited workflows (actionlint or equivalent) before committing.

## Open questions

- This removes the only fast-iteration path for testing meta-prerelease/prerelease pipeline *plumbing* changes without paying for a full acceptance test run every time. Is that development workflow being replaced by something else, or is the extra CI time simply accepted going forward?
- The legacy acceptance-stage variant uses `debug-skip-tests` to still tag the RC while skipping tests — removing it means that behavior disappears entirely (there will be no way to mint a legacy RC tag without running tests). Confirm this is intended, not just "remove the input but keep an equivalent bypass."
