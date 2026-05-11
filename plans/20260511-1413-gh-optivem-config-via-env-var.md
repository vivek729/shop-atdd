# Plan — Migrate workflows to `GH_OPTIVEM_CONFIG`, drop `--system-config` / `--test-config` flags

**Date:** 2026-05-11
**Scope:** shop repo only — no changes needed in `gh-optivem` CLI.

## Background

Today every `gh optivem run|build|test|stop|clean system` invocation in the workflows passes both `--system-config docker/<lang>/<arch>/systems.yaml` and `--test-config system-test/<lang>/tests.yaml`. That's ~227 flag pairs across 14 workflow files. The latest/legacy split is encoded by which `tests.*.yaml` the flag points at — there is no single variant identifier.

Meanwhile the repo root has 6 variant yamls (`gh-optivem-<arch>-<lang>.yaml`) consumed only by `gh optivem compile` (via `compile-all.sh`). They describe each variant's system + system-test paths but do **not** carry the docker or tests config paths.

## Key finding from `gh-optivem` CLI inspection

The CLI already supports the full chain end-to-end (`internal/projectconfig/config.go`, `runner_commands.go`):

- **`GH_OPTIVEM_CONFIG` env var** — already defined (`projectconfig.EnvVar`), resolved by `ResolvePath` with precedence: `--config` flag > `$GH_OPTIVEM_CONFIG` > default `./gh-optivem.yaml`.
- **Top-level `system_config:` field** — already in the schema (line 85), optional, falls back to `./systems.yaml` or explicit `--system-config`.
- **Top-level `test_config:` field** — same (line 91).
- **Runner commands** (`build|run|stop|clean|test system`) already resolve `--system-config`/`--test-config` via flag > yaml field > default.

**No CLI work needed.** The whole migration is in this repo.

## Correction to the work already done

The 12 variant yamls created earlier today put the config fields at the **wrong level**:

```yaml
system:
    config: docker/.../systems.yaml      # WRONG — nested
system_test:
    config: system-test/.../tests.yaml   # WRONG — nested
```

The CLI expects them **top-level**:

```yaml
system_config: docker/.../systems.yaml
test_config: system-test/.../tests.yaml
```

All 12 files need to be fixed before the workflow migration starts. Without this, the env var would point at a yaml the CLI loads cleanly but ignores the config fields in (because they're at the wrong path), and runner commands would silently fall back to `./systems.yaml` defaults that don't exist.

## Plan items

### Phase 1 — fix variant yamls (in this repo)

**1.1** Move `system.config` → top-level `system_config` and `system_test.config` → top-level `test_config` in all 12 files:
- `gh-optivem-monolith-dotnet.yaml`
- `gh-optivem-monolith-dotnet-legacy.yaml`
- `gh-optivem-monolith-java.yaml`
- `gh-optivem-monolith-java-legacy.yaml`
- `gh-optivem-monolith-typescript.yaml`
- `gh-optivem-monolith-typescript-legacy.yaml`
- `gh-optivem-multitier-dotnet.yaml`
- `gh-optivem-multitier-dotnet-legacy.yaml`
- `gh-optivem-multitier-java.yaml`
- `gh-optivem-multitier-java-legacy.yaml`
- `gh-optivem-multitier-typescript.yaml`
- `gh-optivem-multitier-typescript-legacy.yaml`

**1.2** Smoke-test one variant locally to confirm the CLI picks up the fields:

```pwsh
$env:GH_OPTIVEM_CONFIG = "gh-optivem-monolith-dotnet.yaml"
gh optivem run  system
gh optivem test system --sample
gh optivem stop system
```

If these succeed without `--system-config` / `--test-config`, the schema is wired correctly.

### Phase 2 — migrate workflows

**2.1** Set `GH_OPTIVEM_CONFIG` once per workflow (at job level), drop both flags from every `gh optivem ... system` line.

Pattern:
```yaml
jobs:
  acceptance:
    runs-on: ubuntu-latest
    env:
      GH_OPTIVEM_CONFIG: gh-optivem-monolith-dotnet.yaml      # or -legacy
    steps:
      - run: gh optivem test system --suite smoke-stub --no-build --no-start
      - run: gh optivem test system --suite smoke-real --no-setup --no-build --no-start
      ...
```

**2.2** Files to update (14 total — ~227 flag-pair removals):

| Workflow | Variant yaml | Flag-pair removals |
|---|---|---|
| `monolith-dotnet-acceptance-stage.yml` | `gh-optivem-monolith-dotnet.yaml` | 11 |
| `monolith-dotnet-acceptance-stage-legacy.yml` | `gh-optivem-monolith-dotnet-legacy.yaml` | 26 |
| `monolith-java-acceptance-stage.yml` | `gh-optivem-monolith-java.yaml` | 11 |
| `monolith-java-acceptance-stage-legacy.yml` | `gh-optivem-monolith-java-legacy.yaml` | 26 |
| `monolith-typescript-acceptance-stage.yml` | `gh-optivem-monolith-typescript.yaml` | 12 |
| `monolith-typescript-acceptance-stage-legacy.yml` | `gh-optivem-monolith-typescript-legacy.yaml` | 26 |
| `multitier-dotnet-acceptance-stage.yml` | `gh-optivem-multitier-dotnet.yaml` | 11 |
| `multitier-dotnet-acceptance-stage-legacy.yml` | `gh-optivem-multitier-dotnet-legacy.yaml` | 26 |
| `multitier-java-acceptance-stage.yml` | `gh-optivem-multitier-java.yaml` | 11 |
| `multitier-java-acceptance-stage-legacy.yml` | `gh-optivem-multitier-java-legacy.yaml` | 26 |
| `multitier-typescript-acceptance-stage.yml` | `gh-optivem-multitier-typescript.yaml` | 11 |
| `multitier-typescript-acceptance-stage-legacy.yml` | `gh-optivem-multitier-typescript-legacy.yaml` | 26 |
| `cross-lang-system-verification.yml` | varies — needs case-by-case look | 2 |
| `_prerelease-pipeline.yml` | varies — needs case-by-case look | 3 |

**2.3** For each workflow, verify the `runs-on`/job topology fits a single env var (i.e. one workflow → one variant). If a workflow runs multiple variants in a matrix, the env var goes at the matrix step level instead of the job level.

### Phase 3 — docs cleanup

**3.1** Update `CLAUDE.md` "System Test Verification" block — replace the three example commands with the env-var form. Current text:

```bash
gh optivem run system --system-config docker/<language>/monolith/systems.yaml
gh optivem test system --system-config docker/<language>/monolith/systems.yaml --test-config system-test/<language>/tests.yaml --sample
gh optivem stop system --system-config docker/<language>/monolith/systems.yaml
```

New text (PowerShell idiom since shop is Windows-first):

```pwsh
$env:GH_OPTIVEM_CONFIG = "gh-optivem-monolith-<language>.yaml"
gh optivem run  system
gh optivem test system --sample
gh optivem stop system
```

**3.2** Check `docs/operations/running-system-tests.md` and `test-all.sh` for the same flag usage. Migrate to the env var pattern there too.

### Phase 4 — compile-all.sh decision (deferred from earlier discussion)

`compile-all.sh` globs `gh-optivem-*.yaml`, which now picks up 12 files instead of 6 — and each `*-legacy.yaml` compiles the same code as its latest sibling. Options:

- **A.** Leave as-is. Cost: ~50% extra compile time, but the summary table has a row per yaml so you can see all 12 results.
- **B.** Exclude `*-legacy.yaml` from the glob. Cheapest fix, no information loss because the latest yaml already covers each project.
- **C.** Dedupe by hashing `system` fields. Most "correct" but over-engineered for one duplicate per variant.

**Recommendation:** B. The legacy yamls exist to select a test-config, not to change the system code. The compile step is system-only — duplicating it is pure waste.

## Validation

After all phases:
1. Run `./compile-all.sh` from repo root → all 6 variants compile clean (or 12 if option A chosen).
2. Locally trigger one latest + one legacy acceptance workflow path via `gh optivem` smoke commands (Phase 1.2 pattern). Confirm both pick up the right tests config.
3. Push to a branch, watch CI for one latest + one legacy acceptance-stage workflow. Confirm green.
4. Once one pair passes, the other 10 workflows are mechanical follow-ups (same edit pattern, different variant).

## Risk

- **Low** — the CLI already supports this end-to-end; no new code paths exercised.
- The flag-removal in workflows is mechanical (sed-able). Easy to revert per-workflow if any specific job has a hidden dependency on the flag form.
- `--system-config` / `--test-config` stay supported by the CLI, so this migration is additive — old workflows / docs that miss the migration keep working.

## Open question for the user

- Should `cross-lang-system-verification.yml` and `_prerelease-pipeline.yml` also migrate, or are they intentionally flag-driven (e.g. because they run multiple variants and the variant selection itself is the input)? Plan currently says "case-by-case look" — needs your call before Phase 2 executes those two.
