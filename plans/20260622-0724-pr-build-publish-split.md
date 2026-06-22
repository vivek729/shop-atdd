# 2026-06-22 07:24:00 UTC — PR build behavior for commit-stage workflows

## TL;DR

**Why:** Today PRs skip everything from Sonar onward — including the Docker image build — because a single `on-branch` gate doubles as both "is this a promotable main commit" and "do the expensive work". That means Dockerfile/image breakage isn't caught until after merge, and PRs get no Sonar analysis.
**End result:** PRs build the Docker image (validating the Dockerfile and image build) but never publish it; only `main` commits publish. Separately, Sonar analysis runs on PRs with PR decoration.

## Outcomes

What we get out of this — the goals and deliverables:

- **(a) Build-on-PR, publish-on-main:** On a PR, the Docker image is built but not pushed; on `main`, it is built and pushed. Image/Dockerfile breakage is caught at PR time.
- **(a)** Registry stays clean — no PR-scoped throwaway images/tags are published.
- **(a)** GHCR login and digest-URL recording remain gated on `on-branch` (only run when actually publishing).
- **(a)** Change applied consistently across **all 7 commit-stage workflows** (monolith Java/.NET/TS, multitier-backend Java/.NET/TS, multitier-frontend-react) per the repo's parallel-implementation rule.
- **(b) Sonar on PRs (separate workstream):** Sonar analysis runs on PR builds with PR decoration, not just on `main`.
- **(c) Step/id naming (separate workstream):** The SHA-ancestry step is renamed `Check commit is on main` / `id: check-on-main` (was `Verify Built SHA Is On Main` / `verify-main`) and all `if:` gates that read its output are updated, across affected workflows/languages.
- **(e) Dead output removed (separate workstream):** The unused `component-version` job output (line 62) is removed from all 7 commit-stage workflows; the `read-version` step stays (it feeds the Docker tag).

## ▶ Next executable step (resume here)

Start with workstream **(a)** in `monolith-java-commit-stage.yml`:
- Remove the `if: steps.verify-main.outputs.on-branch == 'true'` gate from the Docker **build** steps: "Set up Docker Buildx", the two "Pre-pull base image" steps, "Extract Docker Metadata", and "Build and Push Docker Image".
- In "Build and Push Docker Image", change `push: true` → `push: ${{ steps.verify-main.outputs.on-branch == 'true' }}`.
- Keep the gate on "Log in to GHCR", "Compose Dev Version", and "Compose Digest URL" (these only matter when publishing).
- Note: `docker/metadata-action` tags reference `steps.dev-version.outputs.version`, which is produced by the gated "Compose Dev Version" step — confirm the metadata step still works on PRs when that output is empty (see Open questions).

Then replicate the same edits in the .NET and TypeScript monolith commit-stage workflows. Verify consistency across the three. Workstream (b) is deferred and should be planned separately before executing.

## Steps

### Workstream (a) — build on PR, publish on main

**Scope (decided): all 7 commit-stage workflows** — `monolith-{java,dotnet,typescript}`, `multitier-backend-{java,dotnet,typescript}`, `multitier-frontend-react`.

- [ ] Step 1: In `monolith-java-commit-stage.yml`, ungate the Docker build steps (buildx, pre-pulls, metadata, build) and set `push: ${{ steps.<check>.outputs.on-branch == 'true' }}`; keep GHCR login, Compose Dev Version, and digest-URL gated.
- [ ] Step 2: **dev-version tag (decided: drop on PRs).** Keep `Compose Dev Version` gated on `on-branch`; the `type=raw,value=${{ steps.dev-version.outputs.version }}` metadata tag is not applied on PR builds (PRs don't push, so the tag is moot). Confirm the metadata step tolerates the empty output on PRs (omit/guard that tag line if needed).
- [ ] Step 3: Apply the equivalent edits to the other 6 workflows: `monolith-{dotnet,typescript}`, `multitier-backend-{java,dotnet,typescript}`, `multitier-frontend-react`. Note `multitier-frontend-react` may differ from the backend build shape — adapt rather than copy blindly.
- [ ] Step 4: Verify all 7 workflows are consistent (diff the relevant blocks); validate YAML.
- [ ] Step 5: Commit via `/commit` after verification.

**Run cost (decided): build on all PRs** — no draft/path restriction beyond the existing workflow `paths:` filters.

### Workstream (b) — Sonar on PRs (deferred, plan separately)

- [ ] Step 7: Draft a separate plan for enabling Sonar PR analysis (PR decoration), accounting for the `SKIP_SONAR` flag and Sonar's PR config. Not part of this plan's execution.

### Workstream (d) — Co-locate the publish-related steps (follow-on to (a)+(b))

After (a), the *build* steps (buildx, pre-pulls, metadata, `docker build`) are ungated and run on PRs; only the publish-only steps stay gated. The goal here is to gather the publish-only steps into one contiguous block near the publish point.

- [ ] Step 10: **Blocked until (b).** A step's outputs are only visible to later steps, so the SHA-check (`check-on-main`) must precede its *first* consumer. Today/after (a) the first consumers are the Sonar block, Compose Dev Version, and GHCR login — so it cannot move below them. Only after (b) ungates Sonar from `on-branch` does the check become a pure publish gate.
- [ ] Step 11: Once it is a pure publish gate, move `check-on-main` to sit immediately before the publish-only cluster (Compose Dev Version → GHCR login → push expression → Digest URL) for locality. Trade-off accepted: loses fail-fast on a bad/indeterminate SHA, but publish is late in the job anyway.
- [ ] Step 12: Consider relocating `Read Base Component Version` (`read-version`) down next to `Compose Dev Version` too — both its outputs ultimately serve publishing (the `dev-version` tag and the `component-version` job output, which only matters when an image is published/promoted). Constraint: it must stay above `Compose Dev Version`; the job output is position-independent. Trade-off: loses early PR-time `VERSION` validation (minor — `VERSION` is automation-bumped). `dev-version` is already adjacent to the cluster — leave it.
- [ ] Step 13: Apply the chosen ordering across all affected languages.

### Workstream (e) — Remove the dead `component-version` job output (deferred, plan separately)

- [ ] Step 14: Confirm `component-version` (job output, line 62 in each commit-stage workflow) has no consumer. Evidence: the only downstream job `summary` reads only `image-digest-url`; no `needs.run.outputs.component-version` reference exists in any workflow; job outputs can't cross workflow runs, and the prod-stages read versions independently via their own `read-component-versions` step. None of these workflows is `workflow_call`, so there's no reusable-workflow caller either.
- [ ] Step 15: If confirmed dead, remove the `component-version:` job-output line from all 7 commit-stage workflows. **Keep the `read-version` step** — it still feeds `dev-version` (the Docker tag). Only the job-output declaration is removed.

### Workstream (c) — Step/id naming review (deferred, plan separately)

- [ ] Step 8: Rename the step to `name: Check commit is on main` / `id: check-on-main`. Rationale: the step is non-enforcing (it emits `on-branch=true/false`, it doesn't fail when false), so "Check" is accurate where "Verify" implies failure; "commit" is the correct noun (not "branch" — this is a `merge-base --is-ancestor` ancestry test, not a ref-name check); matches the action verb `check-sha-on-branch`.
- [ ] Step 9: Update all `steps.verify-main.outputs.on-branch` references to `steps.check-on-main.outputs.on-branch` across every `if:` gate in the affected workflow(s) and languages.

## Open questions

_None — all resolved._

Resolved decisions:
- **Scope:** all 7 commit-stage workflows.
- **dev-version tag on PRs:** dropped (Compose Dev Version stays gated).
- **Run cost:** build on all PRs.
- **Fork PRs:** N/A — this repo receives only same-repo branch PRs, so secrets are always available; no fork-credential handling needed.
- **(c) Naming:** `Check commit is on main` / `id: check-on-main`.
