# 2026-06-22 07:24:00 UTC — Commit-stage PR build/publish initiative (coordinator)

> **Coordinator plan.** This is the parent that sequences the child plans for the commit-stage PR-build initiative. Run `/execute-plan plans/20260622-0724-pr-build-publish-split.md` and it drives the children in order. Each step below is "run a child plan via its own `/execute-plan`, then return here and mark it done". **Executor instructions:** treat this as a delegating plan — do **not** edit any workflow files directly from this plan; for each step, invoke `/execute-plan <child path>` (which runs that child's own gates/commits), then check the step off here. Honor the dependency notes (resolve a child's open questions with `/refine-plan` first if it has any).

## TL;DR

**Why:** A single `on-branch` gate in the 7 commit-stage workflows doubled as both "is this a promotable main commit" and "do the expensive work", so PRs skipped the Docker build *and* Sonar, and the workflows accumulated naming cruft and dead steps.
**End result:** PRs build the image (catching Dockerfile/image breakage) but only `main` publishes (done); Sonar runs on PRs with decoration; the SHA-check is co-located with the publish cluster; and the workflows are cleaned of misleading names and dead outputs.

## Outcomes

- **(a) Build-on-PR, publish-on-main** — ✅ shipped (commit `ba04682c`), all 7 commit-stage workflows.
- **(b) Sonar on PRs** with PR decoration — pending (child plan 1).
- **(d) Co-locate publish steps** once the SHA-check is a pure publish gate — pending (child plan 1, blocked until (b)).
- **(c) Rename** `verify-main` → `check-on-main` — pending (child plan 2).
- **(e) Remove dead** `component-version` job output + dead `check`-job checkout — pending (child plan 2).

## ▶ Next executable step (resume here)

Run **Step 1** below: resolve child plan 1's open questions, then `/execute-plan` it. Step 2 (cleanup) can run before, after, or in parallel — it has no dependency on Step 1.

## Steps

- [ ] **Step 1 — Run child plan 1: Sonar on PRs + co-locate publish steps** (workstreams b + d). Path: `plans/20260622-0748-sonar-on-prs-and-colocate-publish.md`.
  - It **has open questions** (PR-decoration mechanism, quality-gate behavior, scope, delete `SKIP_SONAR`?, decouple metadata from `dev-version`?). **First** run `/refine-plan plans/20260622-0748-sonar-on-prs-and-colocate-publish.md` to resolve them, **then** `/execute-plan plans/20260622-0748-sonar-on-prs-and-colocate-publish.md`.
  - Internal dependency: (d) is blocked until (b) ungates Sonar from the `on-branch` gate — the child plan enforces this itself.
  - When the child plan finishes (its file is deleted), check this step off.
- [ ] **Step 2 — Run child plan 2: Commit-stage workflow cleanup** (workstreams c + e). Path: `plans/20260622-0748-commit-stage-cleanup.md`.
  - No open questions; mechanical. Just `/execute-plan plans/20260622-0748-commit-stage-cleanup.md`.
  - Independent of Step 1 (see the child's cross-plan note about the `verify-main`→`check-on-main` rename vs (d)'s step move).
  - When the child plan finishes, check this step off.

When both steps are done (both child files deleted), delete this coordinator.

## History

- **(a) shipped** — commit `ba04682c`: ungated the Docker build steps and switched `push: true` → `push: ${{ steps.verify-main.outputs.on-branch == 'true' }}` across all 7 commit-stage workflows; guarded the `dev-version` metadata tag with `enable=`; kept GHCR login / Compose Dev Version / Compose Digest URL gated.

## Resolved decisions (apply to all child plans)

- **Scope:** all 7 commit-stage workflows (monolith Java/.NET/TS, multitier-backend Java/.NET/TS, multitier-frontend-react).
- **Fork PRs:** N/A — same-repo branch PRs only, so secrets (`SONAR_TOKEN`, `GHCR_TOKEN`) are always available.
- **(a) dev-version tag on PRs:** dropped (Compose Dev Version stays gated).
- **(a) run cost:** build on all PRs.
- **(c) naming:** `Check commit is on main` / `id: check-on-main`.
