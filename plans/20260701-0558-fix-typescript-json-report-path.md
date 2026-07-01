# 2026-07-01 05:58:00 UTC — Fix TypeScript system-test JSON report path override

## TL;DR

**Why:** Commit `99be3659` ("Fixes") gave each TypeScript acceptance suite (`acceptance-parallel-api/ui`, `acceptance-isolated-api/ui`) its own `testCountPath` and a `PLAYWRIGHT_JSON_OUTPUT_NAME` env var meant to steer Playwright's JSON reporter to a suite-specific file — mirroring what the same commit did correctly for .NET (`--logger` CLI override) and Java (`-PtestResultsDir` Gradle property). But `playwright.config.ts:9` still hardcodes `outputFile: 'playwright-report/results.json'` on the `json` reporter, and Playwright's `resolveOutputFile()` resolves that config value before ever checking `PLAYWRIGHT_JSON_OUTPUT_NAME` — so the env var is silently ignored and every suite writes to `results.json`. This broke `gh optivem system-test run` (see run [28493676604](https://github.com/optivem/shop/actions/runs/28493676604) → [28493702505](https://github.com/optivem/shop/actions/runs/28493702505), suite "latest - Acceptance Parallel (stub) - API"), which correctly fails loud rather than silently treating the missing report as zero tests (`gh-optivem`'s `internal/build/runner/tests.go:482`).
**End result:** Every TypeScript system-test suite that declares a `testCountPath` explicitly controls its own JSON report path via `PLAYWRIGHT_JSON_OUTPUT_NAME`, with no config-level default silently overriding it — matching the .NET/Java pattern from the same commit. `gh optivem system-test run --sample` passes for all affected suites.

## Outcomes

- `playwright-report/parallel-api.json`, `parallel-ui.json`, `isolated-api.json`, `isolated-ui.json` are actually created by their respective suites (previously always overwritten by `results.json`, so these never existed).
- `contract-stub`, `contract-stub-isolated`, `contract-real` continue to write and read `playwright-report/results.json` as before (no `testCountPath` behavior change), now via an explicit `PLAYWRIGHT_JSON_OUTPUT_NAME` rather than an implicit config default.
- `gh optivem system-test run --sample` (and the full run) exits 0 for the TypeScript monolith and multitier flavors — the original CI failure stops reproducing.
- .NET and Java verified as already correct — no changes needed there.

## ▶ Next executable step (resume here)

Edit `system-test/typescript/playwright.config.ts:9` — remove the hardcoded `outputFile: 'playwright-report/results.json'` from the `json` reporter entry, leaving `['json']` with no options. This is Step 1 below; it unblocks Step 2 (adding the explicit env var to the 3 contract suites) and Step 3 (verification).

## Steps

- [ ] Step 1: In `system-test/typescript/playwright.config.ts`, change the reporter line from
      `reporter: [['./channel-list-reporter.ts'], ['html', { open: 'never' }], ['json', { outputFile: 'playwright-report/results.json' }]],`
      to
      `reporter: [['./channel-list-reporter.ts'], ['html', { open: 'never' }], ['json']],`
      so Playwright's `resolveOutputFile()` has no config-level `outputFile` to short-circuit on, and falls through to checking `PLAYWRIGHT_JSON_OUTPUT_NAME`.
- [ ] Step 2: In `system-test/typescript/tests.yaml`, add `PLAYWRIGHT_JSON_OUTPUT_NAME: playwright-report/results.json` to the `env:` block of the 3 suites whose `testCountPath` is `playwright-report/results.json` but don't yet set the env var: `contract-stub`, `contract-stub-isolated`, `contract-real`. (Leave `smoke-stub`, `smoke-real`, `e2e-api`, `e2e-ui` untouched — they declare no `testCountPath`, so the reporter's output path doesn't matter to them.)
- [ ] Step 3: Verify locally from `system-test/typescript/`: run each affected suite's command directly (or `GH_OPTIVEM_CONFIG=gh-optivem-monolith-typescript.yaml gh optivem system-test run --sample` from the shop repo root) and confirm each of `parallel-api.json`, `parallel-ui.json`, `isolated-api.json`, `isolated-ui.json`, `results.json` (for the 3 contract suites) is actually created at the path its `testCountPath` expects, and the run exits 0.
- [ ] Step 4: Confirm no equivalent bug exists in .NET (`system-test/dotnet/`) or Java (`system-test/java/`) — re-check that their per-suite report-path mechanisms (the `--logger 'trx;LogFileName=...'` CLI flag and the `-PtestResultsDir` Gradle property added in the same commit `99be3659`) are wired correctly and don't have a shared config default that could silently override them the way `playwright.config.ts` did. No code change expected here — this step is a confirmation, not a fix.

## Open questions

- None — root cause reproduced locally (Playwright's `resolveOutputFile()` called directly with the shop's exact config value and env var, confirmed it always resolves to `results.json`), and the fix scope was derived directly from that reproduction plus a side-by-side reading of the .NET/Java equivalents in the same commit.
