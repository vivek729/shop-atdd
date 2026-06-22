# 2026-06-22 07:48:00 UTC — Sonar on PRs + co-locate publish steps

> Split out of `20260622-0724-pr-build-publish-split.md` (workstream (a) shipped in commit `ba04682c`). This plan covers the deferred workstreams **(b)** and **(d)**, which are sequential: (d) is blocked until (b).

## TL;DR

**Why:** After the build/publish split, the Docker *build* runs on PRs but Sonar still doesn't — the Sonar steps remain gated on `steps.verify-main.outputs.on-branch == 'true'`, so PRs get no static analysis or PR decoration. Separately, now that build steps are ungated, the `verify-main` SHA-check sits far above the only steps that still consume it (the publish-only cluster), which reads awkwardly.
**End result:** Sonar analysis runs on PRs with PR decoration (quality feedback before merge), still honoring `SKIP_SONAR`. Once Sonar no longer reads the `on-branch` gate, the SHA-check becomes a pure publish gate and is moved down next to the publish-only cluster for locality.

## Outcomes

- **(b)** Sonar runs on PR builds across all affected commit-stage workflows, with PR decoration (analysis attached to the PR), not just on `main`.
- **(b)** `SKIP_SONAR` still works as an escape hatch on both PRs and `main`.
- **(b)** No regression on `main`: branch analysis keeps working.
- **(d)** The SHA-check step is the *only* `on-branch` consumer left, so it's relocated to sit immediately before the publish-only cluster (Compose Dev Version → GHCR login → push expression → Digest URL).
- **(d)** Change applied consistently across all affected commit-stage workflows (the repo's parallel-implementation rule).

## ▶ Next executable step (resume here)

This plan opens with **design decisions for (b)** — resolve the Open questions below first (via `/refine-plan` or inline), because how Sonar gets PR context (auto-detect vs explicit `sonar.pullrequest.*` params) changes the edits. Once (b)'s questions are closed:

- Start (b) in `monolith-java-commit-stage.yml`: ungate `Build for Sonar` and `Run Code Analysis` from `on-branch` (keep the `vars.SKIP_SONAR != 'true'` half), verify PR decoration, then replicate across the other languages/workflows.
- (d) is **blocked until (b)** lands — do not start it until Sonar no longer reads `on-branch`.

## Steps

### Workstream (b) — Sonar on PRs

- [ ] Step b1: For each affected commit-stage workflow, change the Sonar step gates from `steps.verify-main.outputs.on-branch == 'true' && vars.SKIP_SONAR != 'true'` to just `vars.SKIP_SONAR != 'true'`. Affected steps per language: Java `Build for Sonar` + `Run Code Analysis`; .NET `Begin Sonar Analysis` + `Build for Sonar` + `End Sonar Analysis`; TS/frontend `Run Code Analysis` (`run-sonar.sh`).
- [ ] Step b2: Ensure PR decoration works. SonarCloud auto-detects PR context from GitHub Actions env on same-repo PRs (`SONAR_TOKEN` is available — fork handling is N/A per the parent plan). Confirm the gradle `sonar` task, the .NET `sonarscanner begin`, and `run-sonar.sh` each pass / auto-derive `sonar.pullrequest.key|branch|base` — add explicit params only if auto-detection doesn't fire (see Open questions).
- [ ] Step b3: Decide and apply quality-gate behavior on PRs (fail the PR check vs report-only) — see Open questions.
- [ ] Step b4: **Investigate `SKIP_SONAR` — consider deleting it.** Its original purpose is forgotten. Trace where the `SKIP_SONAR` repo/org variable is set and why (git history of the workflows, repo/org variables, any incident notes). Hypothesis: it was an escape hatch for when Sonar/the token was flaky or unconfigured. Decide: (i) keep as-is, (ii) keep but document, or (iii) remove the variable and the `&& vars.SKIP_SONAR != 'true'` clause from every Sonar gate across all languages. If removing, do it in the same sweep as b1 so the gates end up clean (`if:` dropped entirely where SKIP_SONAR was the only remaining condition). See Open questions.
- [ ] Step b5: Verify across all affected languages/workflows; validate YAML; run a PR to confirm decoration appears and `main` still analyzes.

### Workstream (d) — Co-locate the publish-related steps (blocked until (b))

**Goal (clarified):** the job should read top-to-bottom as **common steps first (run on both PR and main), then a single contiguous publish-only block at the end (main only).** Today after (a) the order is *not* clean — `Compose Dev Version` and `Log in to GHCR` (publish-only) sit *before* the common build steps (metadata, buildx, pre-pull, docker build). (d) fixes that.

**Ordering constraint to resolve (the wrinkle):** `Extract Docker Metadata` is common (must run on PRs) but reads `steps.dev-version.outputs.version`, and `Compose Dev Version` is publish-only. So as written, a publish-only step is forced *above* a common step, which blocks clean co-location. Resolve one of:
- **(i)** Drop the `dev-version` tag from the common metadata step and append it to the push step's `tags:` only on main (the push step is the natural publish point) — fully decouples metadata from dev-version; cleanest for ordering.
- **(ii)** Keep dev-version feeding metadata but accept it sits just above metadata (least churn, ordering stays slightly mixed).
- _Recommendation: (i) — it lets the entire publish cluster (Compose Dev Version → GHCR login → push → Digest URL) drop to the end while metadata stays common with `sha`/`latest` only._

- [ ] Step d1: **Gate audit.** Confirm that after (b), the SHA-check (`verify-main`, or `check-on-main` if the cleanup plan's rename already ran) is read *only* by the publish-only cluster: Compose Dev Version, GHCR login, the `push:`/`enable=` expressions, Compose Digest URL. (A step's outputs are visible only to later steps, so the check must still precede its first consumer.) Note: the metadata `dev-version` tag is the one wrinkle — resolve it per the ordering constraint above before relocating.
- [ ] Step d2: Apply the decoupling decision (Open question) so the **target order** is achievable: `[common: checkout → read-version → setup/compile/test/lint → sonar → metadata(sha,latest) → buildx → pre-pull → docker build]` then `[publish-only block: SHA-check → Compose Dev Version → GHCR login → (push/dev-version tag applied here) → Compose Digest URL]`.
- [ ] Step d3: Move the SHA-check step to sit immediately before the publish-only cluster. Trade-off accepted: loses fail-fast on a bad/indeterminate SHA, but publish is late in the job anyway.
- [ ] Step d4: Consider relocating `Read Base Component Version` (`read-version`) down next to `Compose Dev Version` — both outputs ultimately serve publishing. Constraint: it must stay above `Compose Dev Version`; the job output is position-independent. Trade-off: loses early PR-time `VERSION` validation (minor — `VERSION` is automation-bumped). `dev-version` is already adjacent — leave it.
- [ ] Step d5: Apply the chosen ordering across all affected languages; validate YAML.
- [ ] Step d6: Commit via `/commit`.

## Open questions

- [ ] **(b) PR decoration mechanism:** Rely on SonarCloud's automatic PR detection from GitHub Actions env, or pass explicit `sonar.pullrequest.*` params? _Recommendation: try auto-detection first (least code); only add explicit params if a PR run shows branch analysis instead of PR analysis._
- [ ] **(b) Quality gate on PRs:** Should a failing Sonar quality gate fail the PR check (block merge) or report-only? _Recommendation: report-only initially (decoration + visibility) to avoid surprise red checks; tighten to blocking once the gate is calibrated._
- [ ] **(b) Scope:** All 7 commit-stage workflows, or only those with Sonar wired up today? _Recommendation: all workflows that currently have Sonar steps — match existing coverage, don't add Sonar where it isn't yet._
- [ ] **(d) Decouple metadata from `dev-version`?** `Extract Docker Metadata` (common) currently reads the publish-only `dev-version` output, forcing it above a common step. Drop the `dev-version` tag from metadata and apply it at the push step on main (option i), or leave the coupling (option ii)? _Recommendation: option (i) — enables the clean common-then-publish ordering._
- [ ] **(b) Delete `SKIP_SONAR`?** Its purpose is forgotten (see Step b4). Keep, document, or remove entirely? _Recommendation: investigate origin first; if it's a stale flaky-Sonar escape hatch with no current consumer/setter, remove it — fewer conditions, clearer gates. Hold if it's still actively toggled (e.g. to save Sonar minutes during heavy iteration)._
- [ ] **(d) `read-version` relocation:** Move it down (d3) or leave it where it is? _Recommendation: leave it — early VERSION read is harmless and moving it is churn for little gain; revisit only if it bothers the eye._

Resolved decisions (inherited from parent):
- **Fork PRs:** N/A — same-repo branch PRs only, so `SONAR_TOKEN` is always available.
- **Sequencing:** (d) is blocked until (b) ungates Sonar from `on-branch`.
