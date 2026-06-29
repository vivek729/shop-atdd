# 2026-06-29 08:21:00 UTC — Migrate acceptance-stage GHCR auth from `GHCR_TOKEN` PAT to `GITHUB_TOKEN`

## TL;DR

**Why:** The org PAT `secrets.GHCR_TOKEN` expired/was revoked between ~05:42 and ~07:48 UTC on 2026-06-29, breaking the `preflight` GHCR-existence gate (and downstream docker-login) in all 18 acceptance-stage workflows across all 3 languages and both architectures. The probed/pulled images are public, same-org, and every job already grants the GHCR permissions it needs — so the custom expiring PAT is unnecessary.
**End result:** Every `*acceptance-stage*.yml` authenticates to GHCR with the automatic, never-expiring `secrets.GITHUB_TOKEN` instead of `secrets.GHCR_TOKEN`. The recurring PAT-expiry single point of failure is gone for this workflow family, and the workflows work in clones that lack the optivem org secret.

## Outcomes

What we get out of this — the goals and deliverables:

- All 18 `.github/workflows/*acceptance-stage*.yml` files use `secrets.GITHUB_TOKEN` (not `secrets.GHCR_TOKEN`) for every GHCR auth point: the `check-ghcr-packages-exist` preflight `token:` input and the `docker-login` `password:` inputs in the `check`/`run`/cloud jobs.
- The acceptance-stage workflows no longer fail when the `GHCR_TOKEN` PAT expires — that failure mode is structurally removed for this family.
- No behavioural regression: the existence gate still resolves correctly (public same-org images, `packages: read` already declared) and pulls/pushes still authenticate (`packages: write` already declared on the push jobs).
- Greater template portability: a student/clone repo with no `GHCR_TOKEN` org secret can run these workflows against its own GHCR namespace using the built-in token.

## Prerequisite (manual — NOT part of `/execute-plan`)

**Rotate the org secret `GHCR_TOKEN` now** to immediately green the currently-red workflows. This plan's code change does not refresh the secret, and `GHCR_TOKEN` is still consumed by **other** workflows outside this plan's scope (commit stages, `_meta-prerelease-pipeline.yml`). Rotation is the only thing that unblocks those, and it unblocks all 18 acceptance-stage workflows instantly without waiting for this PR to land.

```bash
gh secret set GHCR_TOKEN --org optivem --visibility all   # paste a fresh PAT with read:packages (+ write:packages where the commit stage pushes)
```

This step is operational (a `gh secret set` / GitHub UI action by the user); `/execute-plan` cannot perform it.

## ▶ Next executable step (resume here)

Replace `secrets.GHCR_TOKEN` → `secrets.GITHUB_TOKEN` at all GHCR auth sites in the 18 `.github/workflows/*acceptance-stage*.yml` files (81 references total): the `check-ghcr-packages-exist` step's `token:` input and the `optivem/actions/docker-login@v1` steps' `password:` input. This is a mechanical find/replace within these 18 files only — confirm with `grep -rn "GHCR_TOKEN" .github/workflows/*acceptance-stage*.yml` returns nothing afterward. Do NOT touch commit-stage or `_meta-prerelease-pipeline.yml` files. Then validate YAML and (after user confirms) dispatch the legacy java workflow to verify.

## Steps

- [ ] Step 1: In each of the 18 `.github/workflows/*acceptance-stage*.yml` files, replace every `secrets.GHCR_TOKEN` with `secrets.GITHUB_TOKEN`. Two kinds of site:
  - `check-ghcr-packages-exist@v1` step → `token: ${{ secrets.GITHUB_TOKEN }}` (set explicitly rather than dropping the line, for clarity/consistency — the action defaults to `github.token` anyway).
  - `optivem/actions/docker-login@v1` (registry `ghcr.io`) → `password: ${{ secrets.GITHUB_TOKEN }}`.

  The 18 files: `{monolith,multitier}` × `{dotnet,java,typescript}` × `{-acceptance-stage,-acceptance-stage-legacy,-acceptance-stage-cloud}.yml`. Per-file `GHCR_TOKEN` counts: legacy = 3, non-legacy = 4, cloud = 6–7.
- [ ] Step 2: Confirm no `secrets.GHCR_TOKEN` remains in the acceptance-stage family: `grep -rn "GHCR_TOKEN" .github/workflows/*acceptance-stage*.yml` must return nothing. Confirm nothing outside the family was touched (`git diff --name-only` lists only `*acceptance-stage*.yml`).
- [ ] Step 3: Validate the edited YAML (`actionlint` if available, else a YAML parse check). No app code changed, so `compile-all.sh` and `system-test --sample` are N/A.
- [ ] Step 4 (verify in CI — confirm with user first per "ask before running system tests"): dispatch `gh workflow run multitier-java-acceptance-stage-legacy.yml -f force-run=true`, wait, and confirm the `preflight` AND `check` jobs pass (these are the jobs that exercise the migrated `token:` and the `check`-job docker-login). Spot-check one sibling language the same way, e.g. `multitier-typescript-acceptance-stage-legacy.yml`.

## Scope boundary

- **In scope:** only the 18 `*acceptance-stage*.yml` workflows.
- **Out of scope (follow-up, covered by the secret rotation above):** commit-stage workflows and `_meta-prerelease-pipeline.yml`, which also reference `GHCR_TOKEN`. Migrating those is a separate decision (some push images and may have been intentionally given a PAT); do not change them here.

## Notes / rationale

- Root cause is operational, not a code defect: `optivem/actions/check-ghcr-packages-exist/check.sh:36-39` correctly fails loud when `actions/shared/ghcr-probe.sh:38-49` gets an empty bearer, which is exactly what GHCR returns (`HTTP 403 {"errors":[{"code":"DENIED"}]}`, no `.token`) for an invalid credential. This is the repo's `check-*` no-swallow rule working as designed — the action is not what we're fixing.
- Verified the images are public and present: an anonymous GHCR token exchange + manifest probe for `optivem/shop/multitier-frontend-react` returns HTTP 200. So `GITHUB_TOKEN` (with the already-declared `packages: read`) is more than sufficient for the existence gate, and authenticated pulls/pushes work because the push jobs already declare `packages: write`.
- Fixing only the preflight `token:` is insufficient: the `check` job's docker-login (e.g. `multitier-java-acceptance-stage-legacy.yml:89`) runs on every scheduled run and would still fail on the expired PAT — hence the docker-login sites must migrate too.
