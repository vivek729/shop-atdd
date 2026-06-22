# 2026-06-22 07:48:00 UTC — Sonar on PRs + co-locate publish steps

> Split out of `20260622-0724-pr-build-publish-split.md` (workstream (a) shipped in commit `ba04682c`). This plan covers the deferred workstreams **(b)** and **(d)**, which are sequential: (d) is blocked until (b).

## TL;DR

**Why:** After the build/publish split, the Docker *build* runs on PRs but Sonar still doesn't — the Sonar steps remain gated on `steps.verify-main.outputs.on-branch == 'true'`, so PRs get no static analysis or PR decoration. Separately, now that build steps are ungated, the `verify-main` SHA-check sits far above the only steps that still consume it (the publish-only cluster), which reads awkwardly.
**End result:** Sonar analysis runs on PRs with PR decoration (quality feedback before merge), via SonarCloud's automatic PR detection, report-only (a failing quality gate does not block merge). The `SKIP_SONAR` toggle is deleted entirely (all 13 workflows + the GitHub variable) so Sonar gates are clean. Once Sonar no longer reads the `on-branch` gate, the SHA-check becomes a pure publish gate; the publish-only cluster (including `read-version`) is co-located at the end of the job, and `Extract Docker Metadata` is decoupled from `dev-version` (sha/latest stay common; the dev tag is applied at the push step on main only).

## Outcomes

- **(b) ✅ SHIPPED** — Sonar steps ungated → run on PRs (auto-detected PR decoration, report-only); `SKIP_SONAR` clause deleted from all 13 workflows (7 commit + 6 acceptance). The GitHub `SKIP_SONAR` **variable** itself is not at repo level and org-level deletion needs admin (HTTP 403) — handed to the user to delete manually if it exists org-side; harmless either way since no workflow references it.
- **(d)** The SHA-check step is the *only* `on-branch` consumer left, so it's relocated to sit immediately before the publish-only cluster.
- **(d)** `Extract Docker Metadata` is decoupled from `dev-version` (option i): metadata keeps only the common `sha`/`latest` tags; the `dev-version` tag is applied at the push step on `main` only.
- **(d)** `read-version` is moved down into the publish-only cluster (immediately above `Compose Dev Version`, which consumes it).
- **(d)** **Achievable target order** (a *fully* contiguous publish block "at the very end" is impossible — `Build and Push` is one step that builds on PRs and pushes on main, and GHCR login + the dev tag must precede it): `[common: checkout → setup/compile/test/lint → sonar → metadata(sha,latest) → buildx → pre-pull]` then `[publish cluster, main-gated: check-on-main → read-version → Compose Dev Version → GHCR login → Build and Push (builds always; pushes + dev tag on main) → Compose Digest URL]`.
- **(d)** Change applied consistently across all 7 commit-stage workflows (the repo's parallel-implementation rule).

## ▶ Next executable step (resume here)

**(b) is shipped.** Only **(d)** remains — the publish-cluster reorder + metadata decouple.

- **Prototype first on `monolith-java-commit-stage.yml`**: apply the decouple (metadata = sha/latest only; dev tag applied at `Build and Push` on main) + the achievable target order (see Outcomes / d2). Get the diff reviewed.
- Then **replicate the exact same transformation** across the other 6 commit-stage workflows (fan-out), validate YAML, commit via `/commit`.

## Steps

### Workstream (b) — Sonar on PRs — ✅ SHIPPED (uncommitted at time of writing)

Done via per-file fan-out: Sonar steps ungated (`if:` dropped), `SKIP_SONAR` clause removed from all 13 workflows. PR decoration relies on SonarCloud auto-detection; quality gate is report-only (SonarCloud default — no extra config). The `SKIP_SONAR` **variable** is not at repo level; org-level deletion needs admin — handed to the user.

### Workstream (d) — Co-locate the publish-related steps

**Goal:** the job should read top-to-bottom as **common steps first (PR + main), then the publish-related steps clustered at the bottom (main-gated).**

**Reality check (important):** a *fully* contiguous publish block "at the very end" is **not achievable** — `Build and Push Docker Image` is a single step that **builds on PRs** (push:false) and **builds + pushes on main**, and GHCR login + the dev-version tag must run *before* it. So the build-push step unavoidably lives *inside* the bottom cluster, and only `Compose Digest URL` legitimately follows it.

**Decouple — option (i) (resolved):** drop the `dev-version` tag from the common `Extract Docker Metadata` step (keep `sha` + `latest`) and apply the `dev-version` tag at the `Build and Push` step on `main` only. This frees `Compose Dev Version` to move down below metadata.

**Achievable target order:**
```
common (PR + main):  checkout → setup/compile/test/lint → sonar
                     → Extract Docker Metadata (sha, latest) → buildx → pre-pull
publish cluster      check-on-main → read-version → Compose Dev Version → Log in to GHCR
(main-gated):        → Build and Push (builds always; pushes + dev tag on main) → Compose Digest URL
```

- [ ] Step d1: **Prototype on `monolith-java-commit-stage.yml`.** Apply the decouple (metadata = sha/latest only) + the achievable target order above. Implement the dev-version tag at `Build and Push` — append it to `tags:` only on main, taking care that no empty/blank tag is emitted on PRs (e.g. an `on-branch`-gated `format(...)` that yields no extra line when false). Validate YAML. **Get the diff reviewed before replicating.**
- [ ] Step d2: **Fan out the exact same transformation** to the other 6 commit-stage workflows (`monolith-{dotnet,typescript}`, `multitier-backend-{java,dotnet,typescript}`, `multitier-frontend-react`), using the approved java diff as the reference pattern. Mind per-language image names and Sonar step sets.
- [ ] Step d3: Validate YAML across all 7; confirm consistency; commit via `/commit`.

## Resolved decisions

- **(b) PR decoration mechanism:** **Auto-detect first.** Rely on SonarCloud's automatic PR detection from GitHub Actions env; add explicit `sonar.pullrequest.*` params only if a PR run shows branch analysis instead of PR analysis.
- **(b) Quality gate on PRs:** **Report-only** initially — decoration + visibility, no merge blocking. Tighten to blocking once the gate is calibrated.
- **(b) Scope:** **All 7 commit-stage workflows** — all of them have Sonar steps today, so Sonar-on-PR applies to all 7.
- **(b) Delete `SKIP_SONAR`:** **Delete entirely.** Investigation showed it's a recent (2026-06-04, commit `19506e95`), deliberate toggle spanning 13 workflows — not stale cruft. Decision: remove the `vars.SKIP_SONAR != 'true'` clause from all 13 workflows (7 commit-stage + 6 acceptance-stage) and delete the GitHub repo/org variable, in one clean sweep — no half-deleted concept. Trade-off accepted: loses the break-glass kill-switch for Sonar/token outages (handled by a workflow edit if it ever happens).
- **(d) Decouple metadata from `dev-version`:** **Option (i)** — drop the `dev-version` tag from the common `Extract Docker Metadata` step (keeps `sha`/`latest`); apply the `dev-version` tag at the push step on `main` only. Enables the clean common-then-publish ordering.
- **(d) `read-version` relocation:** **Move it down** into the publish-only cluster (immediately above `Compose Dev Version`). It only serves publishing, so it belongs with the publish block; the `component-version` job output is position-independent.

Resolved decisions (inherited from parent):
- **Fork PRs:** N/A — same-repo branch PRs only, so `SONAR_TOKEN` is always available.
- **Sequencing:** (d) is blocked until (b) ungates Sonar from `on-branch`.
