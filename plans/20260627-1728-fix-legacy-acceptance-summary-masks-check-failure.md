# Fix: stage summary masks a failed `check` job as a clean "Skipped"

## Why

Run [28293153478](https://github.com/valentinajemuovic/test-app-ea272ffe-cda491f6817cfd4b/actions/runs/28293153478)
of `acceptance-stage-legacy` died in the `check` job at **"Log in to GHCR"** with a
transient runner DNS timeout (`lookup ghcr.io on 127.0.0.53:53: i/o timeout`,
all 4 `docker-login` retries exhausted). Yet the job summary reported:

> ⏭️ Acceptance Stage Skipped — No new artifacts since last run AND no test/workflow changes since last RC tag.

A hard infrastructure failure was rendered to operators as a legitimate, benign
skip. This violates the fail-loud rule: a `check-*`/gate outcome must never
report a non-answer (job crashed) as a definitive answer (nothing to do).

**Root cause** (pinned):

- `monolith-java-acceptance-stage-legacy.yml:298` (and the 5 twins):
  `stage-result: ${{ needs.run.result }}` — the `summary` job derives the stage
  result **only** from the `run` job. When `check` hard-fails, `run` is skipped,
  so `needs.run.result == 'skipped'` and the summary renders a skip, blind to the
  `check` failure.
- `…-legacy.yml:62` — the `check` job's `skip-reason` output uses
  `steps.artifacts-changed.outputs.newer != 'true' && steps.test-changes.outputs.changed != 'true'`.
  When those gate steps never ran (job died at login) their outputs are empty,
  which satisfies the `!= 'true'` checks, so the reason falsely resolves to
  "No new artifacts…". The `!= 'true'` semantics conflate *ran-no-change* with
  *never-ran*. (Mooted by the fix below, which routes failures away from the
  skip path — left as-is.)

The GHCR DNS timeout itself is **transient infra, not a code bug**:
`actions/shared/retry.sh` correctly classifies `i/o timeout` as retryable and
retried 4× over ~65s; the outage simply outlasted the window. A re-run clears it.
No retry change is warranted. This plan fixes only the masking so the next such
flake surfaces as a loud failure instead of a silent green skip.

## Items

- [ ] **Fix the 6 legacy acceptance-stage workflows.** In each file below, replace
  the `summary` job's `stage-result: ${{ needs.run.result }}` line with:
  ```yaml
  stage-result: ${{ (needs.preflight.result == 'failure' || needs.check.result == 'failure' || needs.run.result == 'failure') && 'failure' || needs.run.result }}
  ```
  (these summaries have `needs: [preflight, check, run]`):
  - `.github/workflows/monolith-dotnet-acceptance-stage-legacy.yml`
  - `.github/workflows/monolith-java-acceptance-stage-legacy.yml`
  - `.github/workflows/monolith-typescript-acceptance-stage-legacy.yml`
  - `.github/workflows/multitier-dotnet-acceptance-stage-legacy.yml`
  - `.github/workflows/multitier-java-acceptance-stage-legacy.yml`
  - `.github/workflows/multitier-typescript-acceptance-stage-legacy.yml`

- [ ] **Sweep the same masking in the 12 qa/prod stage workflows** (same bug class;
  their summaries have `needs: [check, run]`, no `preflight`). Replace
  `stage-result: ${{ needs.run.result }}` with:
  ```yaml
  stage-result: ${{ (needs.check.result == 'failure' || needs.run.result == 'failure') && 'failure' || needs.run.result }}
  ```
  in each of:
  - `.github/workflows/{monolith,multitier}-{dotnet,java,typescript}-qa-stage.yml` (6 files)
  - `.github/workflows/{monolith,multitier}-{dotnet,java,typescript}-prod-stage.yml` (6 files)

  Rationale: a failed `check` job in qa/prod likewise skips `run` and is currently
  rendered as a skip. Same one-line, fail-loud fix; keeps the family consistent.

## Notes

- `stage-result: 'failure'` is already a first-class value in
  `actions/render-stage-summary/render.sh` → renders "❌ <stage> Failed — check the
  job logs". `render-system-stage-summary/validate.sh` only requires
  `success-version` when result is `success`, so `failure` passes validation.
- Legitimate skips are unaffected: a *skipped* (not *failed*) gate job leaves
  `needs.<gate>.result == 'skipped'`, so the expression falls through to
  `needs.run.result` and still renders the normal skip + reason.
- Only `failure` is promoted (not `cancelled`): a cancelled run rendering as a
  skip is low-harm and out of scope for this fail-loud fix.

## Verification

- After editing, re-dispatch one legacy stage (e.g. `monolith-java-acceptance-stage-legacy`)
  via `force-run: true` so it exercises the `run` path, and confirm a green run
  still summarizes correctly (not falsely "Failed").
- To validate the failure path, confirm that the next real transient `check`-job
  failure (or a deliberately broken dispatch) now renders "❌ … Failed" instead of
  "⏭️ … Skipped — No new artifacts".
