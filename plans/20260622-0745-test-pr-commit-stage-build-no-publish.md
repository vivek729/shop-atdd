# 2026-06-22 07:45:53 UTC — Test plan: PR builds-but-does-not-publish in commit stage

## TL;DR

**Why:** Workstream (a) of `20260622-0724-pr-build-publish-split.md` is committed — commit-stage workflows now build the Docker image on PRs but only publish on `main`. We need to prove that behaviour end-to-end on a real PR before trusting it: the build must run and catch Dockerfile/image breakage, but nothing PR-scoped may reach the registry.
**End result:** A throwaway PR demonstrates that the commit-stage workflow runs, compiles, tests, and **builds** the Docker image with `push: false`, while every publish-only step (GHCR login, Compose Dev Version, Sonar, Digest URL) is **skipped** — and the matching `main` build after merge **builds + publishes**. Verified via the Actions run logs and the GHCR registry (no new PR-scoped tag/digest).

## Outcomes

What we get out of this — the goals and deliverables:

- **PR triggers the workflow:** opening a PR that touches the watched paths starts the commit-stage workflow (both `push`-to-branch is irrelevant; the `pull_request` trigger fires).
- **Build runs on PR:** Compile, unit tests, linter, and `docker build` all execute — Dockerfile/image breakage would now fail the PR.
- **No publish on PR:** `Build and Push Docker Image` runs with `push: false`; `Log in to GHCR`, `Compose Dev Version`, the Sonar block, and `Compose Digest URL` are all **skipped** (greyed out) because `on-branch == false`.
- **Registry stays clean:** no new `sha-…`, `latest`, or `dev` tag/digest appears in GHCR for the PR commit.
- **Metadata tolerates the empty dev-version:** `Extract Docker Metadata` succeeds on the PR even though `dev-version` produced no output (the `enable=` guard drops that tag).
- **Publish happens on merge:** after the PR merges to `main`, the same workflow re-runs on the `main` SHA, `on-branch == true`, and the image is built **and pushed** (digest URL recorded, registry updated).

## ▶ Next executable step (resume here)

Pick the workflow to exercise (recommend **monolith-dotnet** as the representative shape) and open a throwaway PR that touches its watched path. Concretely: branch off `main`, make a trivial no-op edit under `system/monolith/dotnet/**` (e.g. a whitespace/comment change that still compiles), push the branch, open a PR against `main`, then watch the `monolith-dotnet-commit-stage` run and record which steps ran vs. skipped. This is a verification-only exercise — no production code changes.

## Steps

**Scope (decided): `monolith-dotnet` only** — the representative build shape; fastest to exercise.

### Phase 1 — Prepare the test PR

- [ ] Step 1: Create a throwaway branch off `main` (e.g. `test/pr-build-no-publish`).
- [ ] Step 2: Make a trivial **source** change under `system/monolith/dotnet/**` so the workflow's `paths:` filter fires and the code still compiles (whitespace/comment) — a source edit, not a workflow-YAML touch, so the Dockerfile/image path is genuinely exercised.
- [ ] Step 3: Push the branch and open a PR against `main` (`gh pr create`).

### Phase 2 — Verify build-but-no-publish on the PR

- [ ] Step 4: Confirm the commit-stage workflow started for the PR (`gh run list` / PR checks).
- [ ] Step 5: In the run, confirm these **ran**: Compile, Run Unit Tests, Run Linter, Set up Docker Buildx, base-image pre-pulls, Extract Docker Metadata, **Build and Push Docker Image**.
- [ ] Step 6: Confirm `Build and Push Docker Image` used `push: false` — the build-push-action log should show it built but did not push (no "pushing manifest" / no digest publish).
- [ ] Step 7: Confirm these were **skipped** (gated on `on-branch == true`): Install dotnet-sonarscanner + the 3 Sonar steps, Compose Dev Version, Log in to GHCR, Compose Digest URL.
- [ ] Step 8: Confirm `Extract Docker Metadata` did not error on the empty `dev-version` output (the `enable=` guard dropped the dev tag).
- [ ] Step 9: Confirm GHCR has **no** new tag/digest for the PR commit (check the package's tags, or that no `sha-<pr-sha>` exists).

### Phase 3 — Verify publish-on-main after merge

- [ ] Step 10: Merge the PR to `main`.
- [ ] Step 11: Confirm the commit-stage workflow re-runs on the `main` SHA and `on-branch == true`.
- [ ] Step 12: Confirm `Build and Push Docker Image` ran with `push: true`, the GHCR login + Compose Dev Version + Digest URL steps ran, and the image/digest now appears in GHCR.

### Phase 4 — Clean up

- [ ] Step 13: Delete the throwaway branch. If the merge produced a stray `latest`/`dev` tag you don't want to keep, note it (the bump automation / normal main flow will overwrite it).

## Open questions

_None — all resolved._

Resolved decisions:
- **Scope:** `monolith-dotnet` only (representative build shape, fastest).
- **Merge-to-main half:** yes — run Phase 3 once on `monolith-dotnet`; publishing a dev image from a no-op change is low-risk and proves the gate flips.
- **Triggering change:** trivial source edit under `system/monolith/dotnet/**` (not a workflow-YAML touch), so the Dockerfile/image path is genuinely exercised.
