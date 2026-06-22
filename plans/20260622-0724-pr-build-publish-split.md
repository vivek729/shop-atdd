# 2026-06-22 07:24:00 UTC — Commit-stage PR build/publish initiative (coordinator)

> **Coordinator plan.** This is the parent that sequences the child plans for the commit-stage PR-build initiative. Call this plan with `/execute-plan` and it drives the children in order. Each "step" below is a child plan — execute it via its own `/execute-plan`, then return here.

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

Execute **child plan 1** (Sonar on PRs + co-locate) — it has open design questions to resolve first, so start there:

```
/execute-plan plans/20260622-0748-sonar-on-prs-and-colocate-publish.md
```

Then execute **child plan 2** (cleanup) — fully decided, mechanical:

```
/execute-plan plans/20260622-0748-commit-stage-cleanup.md
```

Child plan 2 has no dependency on child plan 1; the two can run in either order or in parallel sessions (see each plan's cross-plan note). Child plan 1 is listed first only because (b) is the higher-value, decision-bearing work.

## Child plans

- [ ] **Plan 1 — Sonar on PRs + co-locate publish steps** (workstreams b + d): `plans/20260622-0748-sonar-on-prs-and-colocate-publish.md`
  - Has **open questions** (PR-decoration mechanism, quality-gate behavior, scope, and whether to delete `SKIP_SONAR`) — resolve via `/refine-plan` before executing.
  - (d) is blocked until (b) ungates Sonar from the `on-branch` gate.
- [ ] **Plan 2 — Commit-stage workflow cleanup** (workstreams c + e): `plans/20260622-0748-commit-stage-cleanup.md`
  - No open questions; mechanical. Renames the SHA-check step and removes two dead bits across all 7 workflows.

When a child plan is fully executed, its file is deleted (per `/execute-plan`); mark its checkbox here done (or delete the line). When both children are gone, delete this coordinator.

## History

- **(a) shipped** — commit `ba04682c`: ungated the Docker build steps and switched `push: true` → `push: ${{ steps.verify-main.outputs.on-branch == 'true' }}` across all 7 commit-stage workflows; guarded the `dev-version` metadata tag with `enable=`; kept GHCR login / Compose Dev Version / Compose Digest URL gated.

## Resolved decisions (apply to all child plans)

- **Scope:** all 7 commit-stage workflows (monolith Java/.NET/TS, multitier-backend Java/.NET/TS, multitier-frontend-react).
- **Fork PRs:** N/A — same-repo branch PRs only, so secrets (`SONAR_TOKEN`, `GHCR_TOKEN`) are always available.
- **(a) dev-version tag on PRs:** dropped (Compose Dev Version stays gated).
- **(a) run cost:** build on all PRs.
- **(c) naming:** `Check commit is on main` / `id: check-on-main`.
