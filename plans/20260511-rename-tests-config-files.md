# Rename `tests-latest.yaml` → `tests.yaml`, `tests-legacy.yaml` → `tests.legacy.yaml`

🤖 **Picked up by agent** — `ValentinaLaptop` at `2026-05-11T11:37:21Z`

**Status:** Not started.
**Created:** 2026-05-11.
**Scope:** Single repo — `shop` only. No `gh-optivem` changes required (gh-optivem already defaults to `tests.yaml` when `--test-config` is omitted; we currently always pass it explicitly).

---

## Goal

Align the test-config filenames with reality:

- `tests.yaml` becomes the canonical filename (matches the gh-optivem default — in practice there is only one).
- `tests.legacy.yaml` is a clearly-labelled course-only variant (legacy implementation walkthrough), using the dot-separator convention seen in `package.json` / `package-lock.json`.

The `-latest` suffix is misleading: it implies an alternate "non-latest" variant exists as a peer, when in fact "latest" *is* the default and "legacy" is the one-off.

---

## Inventory of changes

### 1. File renames (6 files)

| Old | New |
| --- | --- |
| `system-test/dotnet/tests-latest.yaml` | `system-test/dotnet/tests.yaml` |
| `system-test/dotnet/tests-legacy.yaml` | `system-test/dotnet/tests.legacy.yaml` |
| `system-test/java/tests-latest.yaml` | `system-test/java/tests.yaml` |
| `system-test/java/tests-legacy.yaml` | `system-test/java/tests.legacy.yaml` |
| `system-test/typescript/tests-latest.yaml` | `system-test/typescript/tests.yaml` |
| `system-test/typescript/tests-legacy.yaml` | `system-test/typescript/tests.legacy.yaml` |

Use `git mv` so history follows.

### 2. Code references (3 files — configuration loaders)

These read the test-config path from CLI args / env, so they don't hard-code filenames; the only references are in code comments that name the file for context.

- `system-test/java/src/main/java/com/mycompany/myshop/systemtest/configuration/ConfigurationLoader.java:22` — comment `// Suffix matches externalSystemMode so stub/real suites in one tests-latest.yaml`
- `system-test/dotnet/SystemTests/TestInfrastructure/Configuration/SystemConfigurationLoader.cs:18` — same comment
- `system-test/typescript/config/configuration-loader.ts:46` — same comment

Update the comment text in all three to reference `tests.yaml`.

### 3. Workflow references (13 files)

All six acceptance-stage workflows + their legacy counterparts, plus the cross-lang verifier and the pre-release pipeline:

- `.github/workflows/monolith-dotnet-acceptance-stage.yml`
- `.github/workflows/monolith-dotnet-acceptance-stage-legacy.yml`
- `.github/workflows/monolith-java-acceptance-stage.yml`
- `.github/workflows/monolith-java-acceptance-stage-legacy.yml`
- `.github/workflows/monolith-typescript-acceptance-stage.yml`
- `.github/workflows/monolith-typescript-acceptance-stage-legacy.yml`
- `.github/workflows/multitier-dotnet-acceptance-stage.yml`
- `.github/workflows/multitier-dotnet-acceptance-stage-legacy.yml`
- `.github/workflows/multitier-java-acceptance-stage.yml`
- `.github/workflows/multitier-java-acceptance-stage-legacy.yml`
- `.github/workflows/multitier-typescript-acceptance-stage.yml`
- `.github/workflows/multitier-typescript-acceptance-stage-legacy.yml`
- `.github/workflows/cross-lang-system-verification.yml`
- `.github/workflows/_prerelease-pipeline.yml`

Mechanical find/replace:
- `tests-latest.yaml` → `tests.yaml`
- `tests-legacy.yaml` → `tests.legacy.yaml`

Note: the `*-legacy.yml` workflow filenames stay as-is — those name the workflow's purpose, not the config file. Only the `--test-config` argument values change.

### 4. Docs + scripts (5 files)

- `CLAUDE.md:35` — sample-test snippet under "System Test Verification"
- `test-all.sh:73-74` — `run_phase` calls
- `docs/operations/running-system-tests.md`
- `system-test/dotnet/README.md`
- `system-test/java/README.md`
- `system-test/typescript/README.md`
- `.claude/agents/test-comparator.md`

Same mechanical rename.

### 5. Out of scope — historical plan files

Leave these untouched (they describe past work; rewriting them would falsify history):
- `plans/20260427-130100-cross-language-verification-workflow.md`
- `plans/20260430-1837-workflow-comparator.md`
- `plans/20260430-160000-consolidate-stage-workflows.md`

---

## Execution order

1. **Update all references** first (code comments, workflows, docs, scripts) so the post-rename grep is clean. Many small `Edit`s, not `Write`.
2. **Rename the 6 files** via `git mv` (one mv per old → new).
3. **Verify**: `grep -rn "tests-latest\|tests-legacy"` across the repo — should return only the three `plans/*.md` historical entries.
4. **Compile check**: `./compile-all.sh` (per repo CLAUDE.md). The configuration-loader changes are comment-only, so this is a sanity pass.
5. **Sample test sweep** per repo CLAUDE.md — three languages, `--sample`:

   ```bash
   for lang in dotnet java typescript; do
     gh optivem run system --system-config docker/$lang/monolith/systems.yaml
     gh optivem test system --system-config docker/$lang/monolith/systems.yaml --test-config system-test/$lang/tests.yaml --sample
     gh optivem stop system --system-config docker/$lang/monolith/systems.yaml
   done
   ```

6. **Commit + push** via `/commit` (single repo) — *not* `/github-commit-push-all` since only `shop` is touched.

---

## Risks / things to double-check

- **gh-optivem default**: confirmed by the user that `tests.yaml` is the gh-optivem default `--test-config` filename. If it's actually something else, this whole rename is built on a false premise — worth a 30-second check before starting.
- **CI cache keys / artifact names**: none of the affected workflows key caches on the config filename (the filename appears only in `--test-config` args), but skim the diff during review to confirm.
- **External references**: any external doc, blog post, or course material outside this repo that links to `tests-latest.yaml` by URL will break. Acceptable if known; flag any course material the user wants kept stable.
- **`.legacy.` dot vs `-legacy.` hyphen**: the dot reads as a variant suffix (`package-lock.json` style). Alternative `tests-legacy.yaml` keeps the existing hyphen convention. Plan uses dot per the user's proposal — easy to switch if preferred.

---

## Rollback

Single commit, so `git revert <sha>` undoes the lot. No data migration, no schema, no external state.
