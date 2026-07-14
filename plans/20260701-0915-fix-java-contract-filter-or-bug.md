# 2026-07-01 09:15:00 UTC — Fix Java contract-suite over-broad test filter (OR-semantics `includeTestsMatching` bug)

## TL;DR

**Why:** CI run [28503997600](https://github.com/optivem/shop/actions/runs/28503997600) failed `monolith-java-acceptance-stage.yml` (run [28504547069](https://github.com/optivem/shop/actions/runs/28504547069)) and `multitier-java-acceptance-stage.yml` (run [28504442707](https://github.com/optivem/shop/actions/runs/28504442707)) at the `contract-stub` step with `ERROR: 0 tests executed for the given selection`, despite Gradle itself reporting `Total: 11, Passed: 11`. Reproduced locally: `system-test/java/build.gradle:86-102`'s `filter { }` block calls `includeTestsMatching` TWICE for `contract-stub` — once for `"*latest*contract*"`, once for `"*Stub*"` — and Gradle's `TestFilter` ORs multiple registered patterns rather than ANDing them. So `contract-stub` actually selects "any latest contract test (real OR stub)" OR "any Stub-named contract test (latest OR legacy)" — 11 tests instead of the ~4 intended (latest, contract, stub, non-isolated). Confirmed via `gh optivem system-test run --suite contract-stub` against a local monolith-java system: it ran ClockRealContractTest, TaxRealContractTest, ErpRealContractTest (latest but real, shouldn't match "stub") and legacy.mod11's ClockStubContractTest, ErpStubContractTest, TaxStubContractTest×2 (stub but legacy, shouldn't match "latest") alongside the intended 4 latest-stub tests — 11 total, matching the CI log's cross-contaminated test list exactly, confirmed via `system-test/java/build/test-results/stub/*.xml`.
**Locally the run still exits 0** (Suite Results: PASSED, countPath directory has the right XML files) — the exact CI-only "0 tests executed" zero-count symptom did not reproduce on this local pass. This is a second occurrence of the same defect class the just-merged commit `7adc6bb2` ("Fix Java contract-suite testCountPath misconfiguration") tried to close, so either that fix is incomplete or there's a CI/Linux-specific interaction layered on top (e.g. Gradle parallel test forks racing on the shared output directory now that 11 tests run instead of ~4). Fixing the confirmed OR-semantics bug is correct regardless of whether it's the full explanation — narrowing test selection back to the intended ~4 per suite removes the cross-contamination and is the natural next attempt; CI must re-run to confirm the zero-count symptom is actually gone, since it cannot be conclusively verified locally.
**End result:** `contract-stub`, `contract-stub-isolated`, and `contract-real` in `system-test/java/build.gradle` each select only their intended, narrowly-scoped test set (no cross-contamination between real/stub or latest/legacy), verified by an exact test-count check locally. `monolith-java-acceptance-stage.yml` and `multitier-java-acceptance-stage.yml` pass end-to-end in CI on the next run.

## Outcomes

- `system-test/java/build.gradle`'s test `filter { }` block selects tests using AND semantics across version/type/mode instead of OR, so `contract-stub` runs only latest+contract+stub+non-isolated tests, `contract-real` only latest+contract+real tests, and `contract-stub-isolated` only latest+contract+stub+isolated tests.
- Each of the 3 contract suites reports the exact expected test count locally (verified by listing `Running: ...` lines and cross-checking against `build/test-results/<partition>/*.xml`) instead of an inflated cross-contaminated count.
- `monolith-java-acceptance-stage.yml` and `multitier-java-acceptance-stage.yml` CI workflows pass their `contract-stub` step (and the suites after it) instead of halting on a false "0 tests executed" error.
- .NET and TypeScript system-test filter mechanisms confirmed unaffected by this defect class (different, non-OR-prone filtering already in place) — verified structurally, not assumed.

## ▶ Next executable step (resume here)

Step 6 below: the fix is committed and pushed. What remains is to watch the next `monolith-java-acceptance-stage.yml` / `multitier-java-acceptance-stage.yml` CI run and confirm the "0 tests executed" symptom is gone — the only conclusive check, since it never reproduced locally. If it recurs, pivot to the Open question below (Gradle parallel-fork / report-write timing on the CI runner).

## Verified so far (Steps 1-4, done)

`system-test/java/build.gradle`'s `filter { }` block now folds every active criterion into a single `includeTestsMatching` pattern (`'*' + [version, type, Stub|Real].join('*') + '*'`), so the criteria AND instead of OR. Verified by listing the classes each suite selects (via the `Running:` lines, with no SUT deployed — selection is what was under test):

- `contract-stub` → `latest…{Clock,Erp,Tax}StubContractTest` (3 classes; was 11 tests incl. legacy.mod11 and `*RealContractTest`)
- `contract-stub-isolated` → `latest…ClockStubContractIsolatedTest`
- `contract-real` → `latest…{Clock,Erp,Tax}RealContractTest`
- Non-contract suites unaffected: `smoke-stub` still selects `MyShopSmokeTest` (no "Stub" in its name), proving the mode segment is appended only when `type == 'contract'`. For any `type != 'contract'` the generated pattern is character-for-character identical to the old one.
- `./gradlew build -x test` passes — Groovy DSL valid.

End-to-end against a deployed monolith-java system (Step 4, done — all green, `gh optivem system` started, `system-test setup`, then per-suite runs, then teardown):

- `contract-stub` → Total: 4, Passed: 4 (`{Clock,Erp,Tax}StubContractTest`; Tax contributes 2 methods) — was 11 cross-contaminated tests.
- `contract-stub-isolated` → Total: 1, Passed: 1 (`ClockStubContractIsolatedTest`).
- `contract-real` → Total: 3, Passed: 3 (`{Clock,Erp,Tax}RealContractTest`).
- `gh optivem system-test run --sample` → all 11 suites PASSED (smoke stub/real, acceptance parallel/isolated × API/UI, all 3 contract suites, e2e API/UI). No regression in the suites sharing the filter block; `--sample`'s CLI `--tests` pattern still ANDs correctly with the new single build-script pattern.

Cross-language check (Step 2, done — no change needed): .NET partitions contract suites with a single `dotnet test --filter` expression AND-ing fragments with `&` (`FullyQualifiedName~.Latest.ExternalSystemContractTests&FullyQualifiedName~Stub&Category!=isolated`). TypeScript uses a single positional path regex (`tests/latest/contract/.*-stub-.*\.spec\.ts`) AND-ed with a separate `--grep`/`--grep-invert` on the `@isolated` tag. Neither registers multiple same-set patterns, so neither is exposed to the OR-semantics defect — Java's repeated `includeTestsMatching` was the only instance.

## Steps

- [ ] Step 6: After push, monitor the next `monolith-java-acceptance-stage.yml` / `multitier-java-acceptance-stage.yml` CI run (or trigger the meta-prerelease pipeline) to confirm the "0 tests executed" symptom is gone — this is the only conclusive verification, since it did not reproduce locally.

## Open questions

- Whether the CI-only "0 tests executed" zero-count symptom is *fully* explained by this OR-semantics bug, or whether a second, CI/Linux-specific factor (e.g. Gradle parallel-fork write timing on the shared output directory) also contributes. Not resolvable without a CI re-run after Step 1's fix lands — Step 6 is the conclusive check. If the symptom recurs after this fix, the next investigation should look at Gradle's `maxParallelForks` / report-writing timing on the CI runner specifically.
