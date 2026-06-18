# 2026-06-18 10:08:00 UTC — Sonar deferred: execute all (super plan)

**Run started:** 2026-06-18 10:08 UTC

Umbrella execution plan that applies the four refined `2026-06-18 07:33` Sonar-deferred
plans in one pass, with a single combined verification + commit at the end. Run this
with `/execute-plan plans/20260618-1008-sonar-deferred-execute-all.md`.

Source plans (detail + rationale live here — this file is the executable consolidation):
- [`20260618-0733-sonar-deferred-java-backend.md`](20260618-0733-sonar-deferred-java-backend.md) — `java:S107`
- [`20260618-0733-sonar-deferred-java-tests.md`](20260618-0733-sonar-deferred-java-tests.md) — `java:S5786` ×15
- [`20260618-0733-sonar-deferred-dotnet-tests.md`](20260618-0733-sonar-deferred-dotnet-tests.md) — 5× roslyn (legacy)
- [`20260618-0733-sonar-deferred-typescript.md`](20260618-0733-sonar-deferred-typescript.md) — `typescript:S4325`

## Target state

All deferred SonarCloud issues across the four 2026-06-18 runs are cleared by one
coherent set of edits — two in-code suppressions, two analysis exclusions for the
frozen legacy trees, and one genuine restructure — verified by a full compile + sample
system tests, then landed as a **single combined commit** for the `shop` repo. After
the next SonarCloud analysis: S107 (×2 mirrors), S5786 (×15), the 5 .NET legacy roslyn
issues, and S4325 are all gone, and the legacy trees no longer generate new noise.

What is explicitly **unchanged**: no public API, no `Order` call site or persistence
mapping, no `Legacy/`/`legacy/` source file, and no behavior on any reachable path.

## Steps

### 1. Java backend — suppress `java:S107` (2 mirrored files)
1. Add `@SuppressWarnings("java:S107")` to the `Order` constructor in
   `system/monolith/java/src/main/java/com/mycompany/myshop/core/entities/Order.java:81`,
   with comment `// one arg per persisted orders column — wide list is intrinsic to the entity mapping`.
2. Apply the identical change to the byte-identical mirror
   `system/multitier/backend-java/src/main/java/com/mycompany/myshop/backend/core/entities/Order.java:81`.

### 2. Java tests — exclude legacy + suppress the one live `java:S5786`
3. In `system-test/java/build.gradle`, inside the `sonar { properties { … } }` block, add:
   `property 'sonar.exclusions', '**/legacy/**'`  (clears the 14 legacy S5786 issues).
4. Add `@SuppressWarnings("java:S5786")` to
   `system-test/java/src/test/java/com/mycompany/myshop/systemtest/latest/base/BaseScenarioDslTest.java:15`,
   comment `// abstract base extended by concrete tests in sibling packages — public required`.

### 3. .NET tests — exclude legacy from analysis
5. In `system-test/dotnet/run-sonar.sh`, add `/d:sonar.exclusions="**/Legacy/**"` to the
   `dotnet sonarscanner begin` invocation (alongside the existing `/k:` `/n:` `/o:` `/d:` flags).
   Single source of truth — both CI acceptance-stage workflows call `bash ./run-sonar.sh`.

### 4. TypeScript — restructure `openHomePage` via existing `requirePage()` (`S4325`)
6. In `system-test/typescript/src/testkit/driver/adapter/ui/client/MyShopUiClient.ts`,
   edit `openHomePage()`:
   - After the `if (!this.context) { … }` block, add `const page = this.requirePage();`
   - `await this.currentPage!.goto(this.baseUrl)` → `await page.goto(this.baseUrl)`
   - `new HomePage(this.currentPage!)` → `new HomePage(page)`

### 5. Verify (all projects — mandatory before commit)
7. From the repo root, run `./compile-all.sh` — must pass for every system + system-test
   project across all three languages. (Steps 1–6 are annotation/config-only except the
   TS restructure, which `tsc` covers here.)
8. Run sample system tests for each affected language (per `CLAUDE.md`), substituting
   `<language>` ∈ {java, dotnet, typescript}:
   ```pwsh
   $env:GH_OPTIVEM_CONFIG = "gh-optivem-monolith-<language>.yaml"
   gh optivem system start
   gh optivem test setup
   gh optivem test run --sample
   gh optivem system stop
   ```
   All sample tests must pass. (The TS restructure is the only behavioral edit — pay
   attention to the UI smoke/e2e sample for `openHomePage`.)

### 6. Commit (single combined commit, via the commit skill — never raw git)
9. One combined commit for the whole run with a per-project rule breakdown:
   ```
   fix: resolve deferred SonarCloud issues across 5 projects

   - optivem_shop-monolith-java (1): java:S107×1 (suppress)
   - optivem_shop-multitier-backend-java (1): java:S107×1 (suppress)
   - optivem_shop-tests-java (15): java:S5786×14 (exclude **/legacy/**), java:S5786×1 (suppress)
   - optivem_shop-tests-dotnet (5): exclude **/Legacy/** — CS8604×2, CA1822×2, SYSLIB1045×1
   - optivem_shop-tests-typescript (1): typescript:S4325×1 (restructure via requirePage())
   ```
   Run via `/commit` (which commits + pulls + pushes). Do **not** split into per-project commits.

## Done when
- `./compile-all.sh` is green and all `--sample` suites pass.
- One combined commit is pushed.
- Next SonarCloud analysis shows the 4 runs' deferred issues resolved (S107×2, S5786×15,
  5 .NET roslyn, S4325) and no new legacy-tree issues.
