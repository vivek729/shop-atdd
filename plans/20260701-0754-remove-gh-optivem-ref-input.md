# 2026-07-01 07:54:00 UTC — Remove the `gh-optivem-ref` workflow input

## TL;DR

**Why:** `gh-optivem-ref` lets acceptance/prerelease workflows build & install `gh-optivem` from an arbitrary branch/tag/SHA instead of the latest release, for testing shop against unreleased `gh-optivem` changes. It touches 23 workflow files. The owner wants it removed to cut this surface area.
**End result:** The `gh-optivem-ref` input, its forwarding through the pipeline chain, its `install-gh-optivem` usage, and its associated safety-gate logic in `meta-prerelease-dry-run.yml` are all gone. Every workflow always installs the latest released `gh-optivem`.

## Outcomes

- No workflow file declares or forwards a `gh-optivem-ref` input.
- `install-gh-optivem@v1` steps no longer pass a `ref:` — CLI is always installed from the latest release.
- `meta-prerelease-dry-run.yml`'s `trigger-stage` job condition and run-name segment that reference `gh-optivem-ref` are simplified/removed accordingly.
- `grep -r "gh-optivem-ref" .github/workflows` returns no results.
- All affected workflows still pass YAML/actionlint validation after edits.

## ▶ Next executable step (resume here)

Step 1: In `.github/workflows/_prerelease-pipeline.yml`, remove the `gh-optivem-ref` input declaration (line ~53-57) and its two forwarding sites in `workflow-inputs` JSON (lines ~410, ~445).

## Steps

- [ ] Step 1: Remove `gh-optivem-ref` input + forwarding from `.github/workflows/_prerelease-pipeline.yml`.
- [ ] Step 2: Remove `gh-optivem-ref` input declaration and the `ref: ${{ inputs.gh-optivem-ref }}` line from the `install-gh-optivem` step in each of the 6 `*-acceptance-stage.yml` files (monolith/multitier × java/dotnet/typescript).
- [ ] Step 3: Same removal in each of the 6 `*-acceptance-stage-legacy.yml` files.
- [ ] Step 4: Remove from `.github/workflows/_meta-prerelease-pipeline.yml` (input declaration + forwarding to acceptance/legacy stage dispatches).
- [ ] Step 5: Remove from `.github/workflows/meta-prerelease-dry-run.yml` — this is the trickiest file: also update the `run-name` expression (drop the `[gh-optivem-ref:…]` segment) and the `trigger-stage` job's `if:` condition (currently gated on `inputs.gh-optivem-ref == ''`; decide what — if anything — replaces that safety gate once the input no longer exists).
- [ ] Step 6: Remove from the 6 `prerelease-pipeline-<flavor>.yml` files.
- [ ] Step 7: Remove from `.github/workflows/drift.yml` (input decl at ~L44/L55, usage at ~L118/L298) and `.github/workflows/cross-lang-system-verification.yml` (input decl ~L25, usage ~L149).
- [ ] Step 8: Run `grep -r "gh-optivem-ref" .github/workflows` to confirm zero remaining references.
- [ ] Step 9: Validate edited workflows (actionlint or equivalent) before committing.

## Open questions

- Removing this input also removes the only mechanism for testing shop's acceptance/prerelease pipelines against an unreleased `gh-optivem` branch/tag/SHA before it ships — and removes the safety gate in `meta-prerelease-dry-run.yml` that prevents such a dry-run from auto-cascading into a real meta-RC release. Confirmed acceptable?
- Once `gh-optivem-ref` is gone, should the `trigger-stage` job in `meta-prerelease-dry-run.yml` simply always run when `auto-trigger-stage` is true (i.e. drop the extra condition entirely), or should some other guard take its place?
