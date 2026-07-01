# 2026-07-01 07:58:00 UTC — Fix Java contract-suite testCountPath misconfiguration

## TL;DR

**Why:** CI run [28501238493](https://github.com/optivem/shop/actions/runs/28501238493/job/84478859865) (`monolith-java-acceptance-stage`, step `contract-stub`) failed with `ERROR: 0 tests executed for the given selection`, even though Gradle itself reported `Total: 11, Passed: 11, Failed: 0`. Root cause, reproduced locally: `system-test/java/tests.yaml:98,105,112` sets `testCountPath: build/test-results/test` for the `contract-stub`, `contract-stub-isolated`, and `contract-real` suites, but none of their `command` fields (`:95`, `:102`, `:109`) pass `-PtestResultsDir`, so Gradle falls back to its real default JUnit XML location — which is `build/test-results` (no `/test` suffix; confirmed via a Gradle init-script probe and by finding the actual `TEST-*.xml` files there). `build/test-results/test/` only ever contains an empty `binary/` subfolder, so `gh-optivem` counts 0 XML files at the configured path and now fails loud on the mismatch (a recent upstream `gh-optivem` release made this check stricter instead of silently treating a missing report as zero tests — the same defect class already fixed for TypeScript in commit `874d91dd`). Prior commit `99be3659` wired the `-PtestResultsDir` override (added in `system-test/java/build.gradle:81-84`) into the 4 `acceptance-*` suites but never into these 3 `contract-*` suites, leaving them on the mismatched implicit default. .NET (`system-test/dotnet/tests.yaml:110-130`, explicit `--logger 'trx;LogFileName=...'` per suite) and TypeScript (already fixed in `874d91dd`) were checked and are correctly wired — only Java needs this fix.
**End result:** `contract-stub`, `contract-stub-isolated`, and `contract-real` each pass an explicit `-PtestResultsDir` matching their `testCountPath`, mirroring the pattern already used by the 4 `acceptance-*` suites. `gh optivem system-test run --suite <name>` exits 0 for all three and reports the correct test count instead of erroring on a phantom "0 tests executed".

## Outcomes

- `system-test/java/tests.yaml`'s 3 contract suites (`contract-stub`, `contract-stub-isolated`, `contract-real`) write their JUnit XML to a suite-specific directory that matches their declared `testCountPath`, exactly like the 4 acceptance suites already do.
- The `monolith-java-acceptance-stage` workflow's `contract-stub` step (and the two suites after it that never got to run) pass end-to-end instead of halting the job on a false "0 tests executed" error.
- No changes needed to `system-test/java/build.gradle` (the `-PtestResultsDir` mechanism already exists and works — verified locally) or to the .NET/TypeScript suites (both confirmed already correctly wired).

## ▶ Next executable step (resume here)

Step 1 below: edit `system-test/java/tests.yaml` to add `-PtestResultsDir=test-results/stub` to the `contract-stub` suite's `command` (line 95) and change its `testCountPath` (line 98) to `build/test-results/stub`.

## Steps

- [ ] Step 1: In `system-test/java/tests.yaml`, suite `contract-stub` (~line 93-99): append `-PtestResultsDir=test-results/stub` to the `command` on line 95; change `testCountPath` on line 98 from `build/test-results/test` to `build/test-results/stub`.
- [ ] Step 2: In `system-test/java/tests.yaml`, suite `contract-stub-isolated` (~line 100-106): append `-PtestResultsDir=test-results/stub-isolated` to the `command` on line 102; change `testCountPath` on line 105 from `build/test-results/test` to `build/test-results/stub-isolated`.
- [ ] Step 3: In `system-test/java/tests.yaml`, suite `contract-real` (~line 107-113): append `-PtestResultsDir=test-results/real` to the `command` on line 109; change `testCountPath` on line 112 from `build/test-results/test` to `build/test-results/real`.
- [ ] Step 4: Run `./gradlew build` in `system-test/java/` (or repo-root `compile-all.sh`) to confirm the YAML edit didn't break anything and the project still compiles.
- [ ] Step 5: From the repo root, verify against a deployed environment: `$env:GH_OPTIVEM_CONFIG = "gh-optivem-monolith-java.yaml"`, `gh optivem system start`, `gh optivem system-test setup`, then run `gh optivem system-test run --suite contract-stub`, `--suite contract-stub-isolated`, and `--suite contract-real` individually — confirm each exits 0 and reports the correct non-zero test count (11 for `contract-stub`, based on this run's log). Then run `gh optivem system-test run --sample` for the full Java suite sweep to catch regressions, and `gh optivem system stop` to tear down.
- [ ] Step 6: Commit and push the fix via the `/commit` skill (per repo convention — never raw `git`).

## Open questions

- None — root cause reproduced locally (Gradle init-script probe of `tasks.test.reports.junitXml.outputLocation`, cross-checked against actual `TEST-*.xml` file locations), and the fix mirrors an already-proven-working pattern in the same file (the 4 `acceptance-*` suites) and the same defect class already fixed for TypeScript in `874d91dd`.
