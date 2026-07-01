# 2026-07-01 06:00:00 UTC — Gate stateful acceptance-stage steps to main-branch runs only

## TL;DR

**Why:** The acceptance-stage workflows (`*-acceptance-stage.yml` and `*-acceptance-stage-cloud.yml`, 12 files across monolith/multitier × java/dotnet/typescript) can be run against any commit — including a PR branch's commit-sha via `workflow_dispatch` — but unconditionally tag Docker images and publish a git tag as if every run were an official release candidate. Running the stage against a PR branch to validate a build should be safe to do freely; it should never mutate GHCR tags or push git tags.
**End result:** The acceptance stage can be executed from a PR branch to exercise deploy + test suites (smoke/acceptance/contract/e2e) + sonar with zero side effects, while RC image tagging (`Tag Docker Images for Prerelease`) and git tag publishing (`publish-tag` job) execute only when the run is against `main`. All 12 acceptance-stage workflow files behave consistently.

## Outcomes

- Running the acceptance stage against a PR branch (e.g. dispatching with `commit-sha` set to a PR branch's HEAD) runs deployment + all test suites + sonar exactly as today, with no image tags pushed to GHCR and no git tag published.
- Running the acceptance stage against `main` (scheduled cron, or manual dispatch with no `commit-sha` / a main commit-sha) behaves exactly as it does today — RC image tagging and git tag publishing still happen.
- The main-vs-PR distinction is computed once per run and consumed as a single boolean gate, not re-derived ad hoc at each stateful step.
- The gating logic and its `if:` placement are identical across all 12 acceptance-stage files (monolith + multitier, java/dotnet/typescript, latest + cloud variant) — no per-language drift.
- Non-stateful steps (deploy-docker-compose, wait-for-endpoints, all `gh optivem system-test run` suites, sonar analysis) are unaffected and continue to run regardless of branch. Sonar stays ungated — it's already keyed per-branch/PR in SonarCloud and isn't release-tagging state. `Compose Prerelease Version` IS gated alongside the tagging/publish steps (a deliberate deviation from the original draft, decided during execution) for consistency with `monolith-java-commit-stage.yml`'s precedent of gating its equivalent `Compose Dev Version` step — there's no value in computing an RC version number that will never be published.
- Legacy acceptance-stage variants (`*-acceptance-stage-legacy.yml`) are untouched — they don't do RC tagging or git tag publishing at all, so the gate doesn't apply to them.
- README status badges for commit-stage, acceptance-stage, acceptance-stage-legacy, qa-stage, qa-signoff, and prod-stage (all 6 language/tier blocks) reflect `main`-branch runs only — a manual PR-branch validation dispatch of acceptance-stage never flips these badges, keeping them a trustworthy signal of the actual release pipeline's health.

## ▶ Next executable step (resume here)

All 7 steps are implemented: the `check-on-main` gate landed identically in all 12 acceptance-stage files (verified via diff + YAML-parse review, zero drift), and the README badge sweep (39 badges) is done. Nothing left to execute — this plan is awaiting the end-of-run review/commit gate. If resumed fresh, just confirm with the user whether to commit (the file paths are the 12 `.github/workflows/*-acceptance-stage*.yml` files + `README.md`, all currently uncommitted).

## Steps

(none remaining — all steps executed, pending review/commit)

