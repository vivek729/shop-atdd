# Consolidate per-(arch, lang) stage workflows into reusable workflows

## Decision

Replace each family of 6 near-duplicate stage workflows (3 languages × 2 architectures) with **one reusable workflow + 6 thin caller files**. Apply this to every stage family that is currently duplicated 6×: acceptance-stage, acceptance-stage-cloud, acceptance-stage-legacy, qa-stage, qa-stage-cloud, prod-stage, prod-stage-cloud, qa-signoff (8 families × 6 ≈ 48 files → 8 reusables + 48 thin callers, plus ~2 composite actions for runtime setup).

The rollout is **parallel-then-cutover**: phases 1–3 ADD `new-{arch}-{lang}-{stage}.yml` thin callers + `_<stage>.yml` reusables alongside the existing 48 workflows without touching them. After author approval, Phase 4 deletes the existing 48 workflows and renames the `new-*` files to drop the prefix. This keeps shop CI on the known-good schedule throughout validation.

The pattern is the one shop already uses for `_prerelease-pipeline.yml`: a single `_<stage>.yml` reusable takes `architecture`, `language`, `prefix` (and a few other) inputs, with per-language steps gated by `if: inputs.language == ...`.

## Rationale

### Genuine vs incidental divergence

Comparing `monolith-{java,dotnet,typescript}-acceptance-stage.yml`:

| Difference | Type | Lines |
|---|---|---|
| Concurrency group, env name, image name, paths, ports, tag prefix | Incidental — pure substitution | ~80% of diff |
| Runtime setup (Java+Gradle vs .NET+NuGet vs Node+npm) | Genuine — only language-specific block | ~25–35 lines |
| Suite step list (`smoke-stub`, `acceptance-api`, …) | Should be identical, in practice drifts | ~30 lines |

Comparing `monolith-java-…` vs `multitier-java-…`:

| Difference | Lines |
|---|---|
| Image count: 1 (`monolith-system-java`) vs 2 (`multitier-frontend-react` + `multitier-backend-java`) | ~4 |
| Compose `service-names`: `system` vs `frontend\nbackend` | ~6 |
| Working-directory: `docker/java/monolith` vs `docker/java/multitier` | substitution |
| Base-version path: `system/monolith/java/VERSION` vs `system/multitier/java/VERSION` | substitution |
| Tag prefix, env name, concurrency group | substitution |

**Runtime setup is identical between monolith and multitier of the same language** — system-tests live at `system-test/{lang}` regardless of architecture.

So the variation factors are orthogonal:

- Per-**language** (3): runtime setup block → composite action `setup-language-toolchain`
- Per-**architecture** (2): image-urls list, service-names list, working-directory, base-version path → reusable inputs
- Per-**(lang, arch)**: tag prefix, env name, ports → derived inside reusable from inputs

### Drift bug found while planning

`monolith-typescript-acceptance-stage.yml:310` calls the suite `contract-isolated-stub`, while `monolith-{java,dotnet}-acceptance-stage.yml` call the same suite `contract-stub-isolated`. This is exactly the kind of bug that 6-fold duplication produces. Consolidation eliminates the surface area.

### Existing precedent

Shop already uses this pattern: `_prerelease-pipeline.yml` (workflow_call, takes `architecture` + `language` + `prefix` inputs, `if:`-gated per-language steps). The proposal is to apply that same pattern to the stage-stage workflows, not invent a new convention.

### What we're NOT doing

- **Not** moving stage workflows to `.github/workflows/templates/` like the bump-patch-version scaffold-only files. Stage workflows are dual-role (active in shop CI + scaffold sources for student repos), so they must remain top-level.
- **Not** consolidating commit-stage workflows. They're already nearly-distinct per language (different build tools, different test runners), and there are 7 of them (3 monolith × 1 + 3 multitier-backend × 1 + 1 multitier-frontend), not 6 — different shape.
- **Not** consolidating bump-patch-version files in this plan. Those have their own scaffold-vs-active dual role and are tracked under `20260430-055950-move-scaffold-workflows-to-templates-subdir.md`.

## Tradeoff

**Cost**: a regression in `_acceptance-stage.yml` would break all 6 callers simultaneously instead of one. Today's drift is a benefit in disguise — bugs stay localized to one language. Mitigation: the reusable's logic is mostly identity-preserving boilerplate already; the per-language differences live in `setup-language-toolchain`, which can be unit-tested via the composite-action test harness in `optivem/actions`.

**Cost during rollout**: the parallel approach temporarily doubles the stage-workflow file count in shop (48 → 96) until Phase 4 cutover. Scheduled CI cost is unchanged because the `new-*` callers run only on `workflow_dispatch` during the transition; the existing workflows keep their hourly schedule until they are deleted at cutover.

**Cost for student repos**: depends on the gh-optivem strategy chosen below. The naive "scaffold both files" approach gives students 2 files per stage (caller + reusable) instead of 1. The "scaffold-time inline" approach keeps students at 1 file per stage but adds tooling complexity to gh-optivem.

## Scope (files that change in shop)

### New files

- `.github/workflows/_acceptance-stage.yml` — reusable with inputs: `architecture`, `language`, `prefix`, `image-base-names` (multi-line list), `service-names` (multi-line list), `endpoint-base-port` (e.g. `3111`), plus the `commit-sha` / `debug-skip-tests` triggers passed through from callers.
- `.github/workflows/_acceptance-stage-cloud.yml` — same inputs, cloud-deploy variant.
- `.github/workflows/_acceptance-stage-legacy.yml` — same inputs, legacy sample variant.
- `.github/workflows/_qa-stage.yml`, `_qa-stage-cloud.yml`, `_prod-stage.yml`, `_prod-stage-cloud.yml`, `_qa-signoff.yml` — same pattern.
- `optivem/actions/setup-language-toolchain/` — composite action that switches on `language: java|dotnet|typescript` and installs the appropriate toolchain + Playwright system deps + caches. Replaces the per-language ~25–35-line setup block.
- `optivem/actions/install-gh-optivem/` — composite action for `gh extension install optivem/gh-optivem` (currently duplicated in every workflow).

### New thin callers (added alongside existing files; ~20 lines each)

48 new files, all matching `new-{arch}-{lang}-{stage}.yml`:

```
new-monolith-{dotnet,java,typescript}-{acceptance-stage,acceptance-stage-cloud,acceptance-stage-legacy,qa-stage,qa-stage-cloud,prod-stage,prod-stage-cloud,qa-signoff}.yml
new-multitier-{dotnet,java,typescript}-{… same 8 stages …}.yml
```

The `new-` prefix is intentional and lives only during the rollout. At cutover (Phase 4) the existing unprefixed files are deleted and these are renamed to drop the prefix.

Each thin caller looks like:

```yaml
name: new-monolith-java-acceptance-stage
on:
  workflow_dispatch:
    inputs:
      commit-sha: { required: false, type: string }
      debug-skip-tests: { required: false, type: boolean, default: false }
permissions: {}
concurrency: { group: new-monolith-java-acceptance-stage }
jobs:
  stage:
    uses: ./.github/workflows/_acceptance-stage.yml
    secrets: inherit
    with:
      architecture: monolith
      language: java
      prefix: monolith-java
      image-base-names: |
        ghcr.io/${{ github.repository_owner }}/${{ github.event.repository.name }}/monolith-system-java
      service-names: system
      endpoint-base-port: 3111
      commit-sha: ${{ inputs.commit-sha }}
      debug-skip-tests: ${{ inputs.debug-skip-tests }}
```

The `schedule:` trigger is intentionally absent during the rollout: scheduled runs stay on the existing unprefixed workflow until cutover, after which the renamed file inherits the schedule. Note the distinct `name:` and `concurrency.group` (`new-monolith-java-acceptance-stage`) — they must NOT collide with the existing `monolith-java-acceptance-stage` workflow that runs concurrently throughout the rollout.

### Files that are unchanged

- **All 48 existing `{arch}-{lang}-{stage}.yml` workflows** — untouched during phases 1–3. They keep running on their hourly schedule. Deleted in Phase 4 (cutover) and replaced by the renamed `new-*` callers.
- `_prerelease-pipeline.yml`, `_meta-prerelease-pipeline.yml` — already reusable. The update to call the new `_qa-stage.yml` etc. instead of the per-(arch,lang) callers happens at Phase 4 (cutover), not before.
- `prerelease-pipeline-{monolith,multitier}-{lang}.yml` — already thin callers around `_prerelease-pipeline.yml`.
- `meta-bump-all.yml`, `meta-prerelease-stage.yml`, `meta-release-stage.yml`, `meta-prerelease-dry-run.yml` — meta-level, unaffected.
- `bump-patch-version-*.yml` — out of scope for this plan.
- `_*-commit-stage.yml` — out of scope (genuinely per-language, see Rationale).
- `cleanup.yml`, `lint-workflows.yml`, `cross-lang-system-verification.yml`, `move-tickets-to-qa.yml` — single workflows, not duplicated.

## gh-optivem coordination

The scaffolder (`gh-optivem/internal/steps/apply_template.go` and `internal/templates/templates.go`) currently:

1. Picks one shop file matching `{arch}-{testLang}-{stage}.yml` from `.github/workflows/`.
2. Renames it to `{stage}.yml` in the student repo.
3. Runs ~20 text replacements (paths, image names, env names, tag prefixes, prefix-drops).

**Timing**: gh-optivem's scaffolder changes land at Phase 4 (cutover), not during phases 2/3. Until cutover, the scaffolder keeps reading the existing unprefixed `{arch}-{lang}-{stage}.yml` files unchanged — student repos are unaffected by the parallel rollout. Optionally a feature branch in gh-optivem can scaffold from the `new-*` files for end-to-end validation before the cutover PR; production scaffolding switches in Phase 4.

After consolidation, the per-(arch, lang) caller is a 20-line file that `uses: ./.github/workflows/_acceptance-stage.yml`. The scaffolder must still produce a working `acceptance-stage.yml` in the student repo. Three options:

### Option A — Naive: copy both files, fix paths in both (RECOMMENDED for v1)

Scaffolder copies the chosen caller AND the matching `_<stage>.yml` reusable. Renames caller to `{stage}.yml`. Applies path/image-name fixups to both files. The `uses: ./.github/workflows/_acceptance-stage.yml` line in the caller stays unchanged (the reusable is also at `.github/workflows/_acceptance-stage.yml` in the student repo).

**Student repo file count for stages**: 8 stage callers (`acceptance-stage.yml`, `qa-stage.yml`, …) + 8 reusables (`_acceptance-stage.yml`, …) = 16 files instead of today's 8.

**Implementation effort in gh-optivem**: small. Extend `monolithPipelineWorkflows` / `multitierPipelineWorkflows` to also include `{src: "_acceptance-stage.yml", dst: "_acceptance-stage.yml"}` etc. Most existing fixup rules apply unchanged because the substring patterns (`monolith-system-java`, `system/monolith/java`, …) only appear in the reusable, which is still copied verbatim into the student repo.

**Pedagogy concern**: students see twice as many workflow files. Mitigation: documentation pattern (`.github/workflows/_*.yml = shared logic, .github/workflows/<stage>.yml = entry point`) is teachable and consistent with shop itself.

### Option B — Inline at scaffold time: produce one file per stage in student repo

Scaffolder reads both shop files, parses the reusable's `jobs:` block, inlines it into the caller (mapping `inputs.architecture` → the literal value, etc.), and writes a single `{stage}.yml` to the student repo. Student count stays at 8 stage files.

**Implementation effort in gh-optivem**: large. Requires a YAML-aware transform (parse, walk, substitute, serialize) instead of textual replace. Risk of subtle YAML re-formatting drift between scaffold runs.

**Recommendation**: defer. Worth doing only if Option A's two-files-per-stage proves to be a real teaching obstacle.

### Option C — Cross-repo `uses:` to shop's reusable

Scaffolder rewrites `uses: ./.github/workflows/_acceptance-stage.yml` to `uses: optivem/shop/.github/workflows/_acceptance-stage.yml@v1`. Student repo gets only the caller; the reusable lives in shop.

**Pros**: cleanest student repo (1 file per stage). Bug fixes in shop's reusable propagate automatically via the next `@v1` tag bump.

**Cons**: student CI now depends on shop's release stability. A breaking change in `_acceptance-stage.yml` immediately breaks every student repo on the next workflow run. Defeats the pedagogical principle of "students own their CI". Also requires shop's reusable workflows to be public (currently shop is public, so this is fine, but it's a coupling.)

**Recommendation**: do not adopt. Pedagogy concerns outweigh the file-count savings.

### gh-optivem changes required (Option A)

In `gh-optivem/internal/steps/apply_template.go`:

- `monolithPipelineWorkflows()` and `multitierPipelineWorkflows()` extend the returned map to include the shared reusables alongside the per-(arch,lang) callers:

  ```go
  return map[string]string{
      p + suffixAcceptanceStage + stageSuffix + ".yml": acceptStageYml,
      "_acceptance-stage" + stageSuffix + ".yml":       "_acceptance-stage" + stageSuffix + ".yml",
      // … same for qa-stage, prod-stage, etc.
  }
  ```

- The fixup-replacement helpers (`monolithContentReplacements`, `multitierContentReplacements`) need no changes — the substrings they target (`monolith-system-java`, `system/monolith/java/VERSION`, etc.) only appear in the reusable's body, which gets copied verbatim into the student repo and then text-replaced. The thin caller has only short literal values (e.g. `language: java`) which are also covered by the existing rules.

- `forbiddenTemplateRefs()` (the post-scaffold validator) needs no changes for the same reason — the substrings remain forbidden, and they should still appear nowhere in the scaffolded output.

- New plan needed in `gh-optivem` repo to track its side of this work, including a manual-test-runner-shop run that scaffolds all 6 (arch, lang) combos and verifies actionlint passes on each.

### Coordination with the existing `templates/` move

Plan `20260430-055950-move-scaffold-workflows-to-templates-subdir.md` moves 8 scaffold-only workflows to `.github/workflows/templates/`. The new `_<stage>.yml` reusables proposed here must stay top-level (they are referenced by `uses: ./.github/workflows/_acceptance-stage.yml` from the active per-(arch,lang) callers — moving them to a subdir would break those references). No conflict, but worth noting in both plans.

## Phased rollout

Each phase ends in a green CI run on shop and a successful `gh-optivem/scripts/manual-test-runner-shop.sh` (which scaffolds and validates a sample student repo).

### Phase 0 — Verification baseline

1. Run `./test-all.sh` and capture which stages pass today.
2. Run `gh-optivem/scripts/manual-test-runner-shop.sh` to scaffold all 6 (arch, lang) combos and capture baseline (e.g. actionlint, sample test pass).
3. Document the suite-name drift bug (`contract-isolated-stub` vs `contract-stub-isolated`) as a separate ticket — fix it BEFORE consolidation so the consolidated reusable inherits the canonical name without question.

### Phase 1 — Create `setup-language-toolchain` composite action (in `optivem/actions`)

Build the composite action that the new `_*.yml` reusables will consume. **Does not edit the existing 48 stage workflows** — they keep their inline setup blocks until they are deleted at cutover.

- Implement composite action with branches for `java`, `dotnet`, `typescript`.
- Inputs: `language`, `working-directory` (where to cache key off), `playwright` (boolean, default true).
- Encapsulates: `Setup .NET` / `Setup Java` / `Setup Node` / `Setup Gradle` / `Pre-warm Gradle Wrapper` / `Cache NuGet` / `Cache Playwright` / `Compile System Tests` / `Install Playwright System Dependencies` blocks.
- Same phase: create `install-gh-optivem` composite action.
- Validate the composite actions via the test harness in `optivem/actions` before any consumer exists.

End of Phase 1: two new composite actions exist in `optivem/actions`. Shop is unchanged.

### Phase 2 — Pilot one stage family (acceptance-stage), parallel rollout

Add the new reusable + 6 new thin callers alongside the existing 6 acceptance-stage workflows. **Existing `{arch}-{lang}-acceptance-stage.yml` files are untouched.**

1. Author `_acceptance-stage.yml` mirroring `monolith-java-acceptance-stage.yml`, with inputs, consuming `setup-language-toolchain`.
2. Add 6 `new-{arch}-{lang}-acceptance-stage.yml` thin callers (`workflow_dispatch` only — no `schedule:` yet).
3. Trigger each `new-*` caller manually via `workflow_dispatch` and confirm a green run. The 6 existing unprefixed workflows continue running on their hourly schedule in parallel; compare outcomes.
4. Optional: in a gh-optivem feature branch, run `manual-test-runner-shop.sh` against the `new-*` files to validate the scaffolder side end-to-end. Do NOT merge gh-optivem changes yet.

End of Phase 2: 7 new files in shop (1 reusable + 6 callers); existing acceptance-stage workflows unchanged and still authoritative.

### Phase 3 — Roll out remaining stage families (parallel)

Apply the same parallel-add pattern to: `acceptance-stage-cloud`, `acceptance-stage-legacy`, `qa-stage`, `qa-stage-cloud`, `prod-stage`, `prod-stage-cloud`, `qa-signoff`. One stage family per PR; each PR adds 1 reusable + 6 `new-*` callers and validates them via `workflow_dispatch`. Existing files remain untouched.

End of Phase 3: 8 reusables + 48 `new-*` callers exist alongside the 48 existing unprefixed workflows. Shop now has ~96 stage workflow files temporarily.

### Phase 4 — Cutover (gated on author approval)

After all 8 stage families have been validated end-to-end via `workflow_dispatch` runs and (optionally) end-to-end gh-optivem scaffolder runs, request author approval to cut over.

Per stage family (one PR each, or one mega-PR — author choice):

1. Delete the 6 existing `{arch}-{lang}-{stage}.yml` files.
2. Rename the 6 `new-{arch}-{lang}-{stage}.yml` files to `{arch}-{lang}-{stage}.yml`. In each renamed file, drop the `new-` prefix from `name:` and `concurrency.group`, and add the `schedule:` trigger that the deleted workflow had.
3. In the same PR (or as the next PR before any new schedule fires), update `_prerelease-pipeline.yml` and `_meta-prerelease-pipeline.yml` to call the new `_qa-stage.yml` etc. via `uses:` (replacing today's calls into the per-(arch,lang) workflows).
4. Land the `gh-optivem` Option-A changes (extend `monolithPipelineWorkflows` / `multitierPipelineWorkflows` to include the `_<stage>.yml` reusables). Run `manual-test-runner-shop.sh` for all 6 (arch, lang) combos to confirm scaffolded student repos still produce green CI.

End of Phase 4: 8 reusables + 48 thin callers, no `new-` prefix anywhere, and the existing 48 workflows are gone.

### Phase 5 — Documentation

- Update `docs/operations/*` (or wherever the workflow architecture is described) to describe the reusable pattern.
- Update `gh-optivem/MAPPING.md` / `NAMING.md` to reflect the source-file split (caller + reusable).
- Add a short README in `.github/workflows/` describing the underscore-prefix convention (`_*.yml` = reusable, called by `<stage>.yml` siblings).

## Open questions for the author

1. **Pedagogy weight on Option A vs B.** If 16 stage files in student repos (8 callers + 8 reusables) is acceptable for teaching, Option A is clearly preferred. If you want students to see exactly one workflow per stage as a teaching simplification, Option B (scaffold-time inline) is needed and the gh-optivem effort is meaningfully larger.
2. **Suite-name canonicalisation.** Before consolidating, decide canonical names: is it `contract-stub-isolated` (Java/.NET) or `contract-isolated-stub` (TypeScript)? The TypeScript drift suggests the rest of the codebase agrees on `…-stub-isolated`; pick that and rename TypeScript before consolidating.
3. **Composite action vs reusable workflow for runtime setup.** Phase 1 proposes a composite action; another option is to put the setup steps inside `_acceptance-stage.yml` directly, gated by `if: inputs.language == ...` (the same pattern `_prerelease-pipeline.yml` uses today). Composite action is more reusable across workflows that are NOT stages (e.g. one-off lint/verify workflows that also need a toolchain), but is one more layer. Recommendation: composite action, because at least 8 stage families will reuse it, and any new ad-hoc workflow gets the same setup path.
4. **Whether to also migrate `_prerelease-pipeline.yml`'s embedded compile blocks** (lines 113–179) to call the same composite action. Yes — same Phase 1 scope, just don't forget it.
