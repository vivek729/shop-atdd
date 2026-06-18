# 2026-06-18 07:33:00 UTC — Sonar deferred: Java tests

**Run started:** 2026-06-18 07:33 UTC

Deferred SonarCloud issues — optivem_shop-tests-java

## Target state

All 15 deferred issues are `java:S5786` ("remove this `public`/`protected` modifier") on JUnit5 base test classes that are **extended from other packages** — the exact technical exception the rule itself documents. They split two ways:

- **14 in the legacy tree** (`systemtest/legacy/mod02…mod11/base/**`) — frozen course-reference material, mirrored on the .NET side. Resolved the same way as the .NET legacy tree: **excluded from SonarCloud analysis**, not code-changed.
- **1 in live `latest` code** (`latest/base/BaseScenarioDslTest.java`) — resolved by **in-code suppression with rationale**, consistent with the `java:S107` decision in `20260618-0733-sonar-deferred-java-backend.md`.

When the work is done:

- `system-test/java/build.gradle` has `property 'sonar.exclusions', '**/legacy/**'` in its `sonar {}` block, so the `optivem_shop-tests-java` analysis stops scanning the legacy tree — clearing the 14 legacy S5786 issues and any future legacy noise.
- `BaseScenarioDslTest` (latest) carries `@SuppressWarnings("java:S5786")` with a one-line comment noting it is `abstract` and extended by concrete tests in sibling packages, so `public` is required.
- No base class is moved or made package-private; cross-package subclass compilation and JUnit5 lifecycle discovery are unaffected.
- Both Java mirrors stay consistent (this is the system-test project; the backend S107 plan covers the entity).

## Resolved decisions

- **Legacy S5786 (14) → exclude legacy tree from analysis.** Same policy as the .NET legacy tree (`20260618-0733-sonar-deferred-dotnet-tests.md`). Frozen reference code should not be under the quality gate.
- **Latest `BaseScenarioDslTest` S5786 (1) → suppress in code with rationale.** Chosen over per-issue "Won't Fix" (inconsistent with the S107 in-code-suppression precedent) and over moving base classes into subclass packages (invasive restructuring, explicitly out of scope for a mechanical Sonar pass). The rule documents this cross-package-inheritance case as a legitimate exception.

## Steps

1. **Legacy exclusion** — edit `system-test/java/build.gradle`, add to the `sonar { properties { … } }` block:
   `property 'sonar.exclusions', '**/legacy/**'`
   (covers all 14 legacy S5786 issues below).
2. **Latest suppression** — add `@SuppressWarnings("java:S5786")` to `src/test/java/com/mycompany/myshop/systemtest/latest/base/BaseScenarioDslTest.java:15`, with comment `// abstract base extended by concrete tests in sibling packages — public required`.
3. Verify: `./gradlew compileTestJava` (or `./compile-all.sh`) in `system-test/java` — annotation-only + config-only, no behavior change.
4. Optionally run `./gradlew sonar` locally to confirm the legacy components drop out and the latest issue is suppressed.
5. Commit with the combined Sonar run; next analysis auto-clears all 15.

## Issues (inventory)

**Covered by the legacy exclusion (step 1) — 14 issues:**
- `systemtest/legacy/mod02/base/BaseRawTest.java:15` (class), `:30` (`setUpConfiguration` `@BeforeEach`)
- `systemtest/legacy/mod03/base/BaseRawTest.java:14` (class), `:28` (lifecycle)
- `systemtest/legacy/mod04/base/BaseClientTest.java:14` (class), `:23` (lifecycle)
- `systemtest/legacy/mod05/base/BaseDriverTest.java:15` (class), `:23` (lifecycle)
- `systemtest/legacy/mod06/base/BaseChannelDriverTest.java:20` (class)
- `systemtest/legacy/mod07/base/BaseUseCaseDslTest.java:12` (class)
- `systemtest/legacy/mod08/base/BaseScenarioDslTest.java:14` (class)
- `systemtest/legacy/mod09/base/BaseScenarioDslTest.java:15` (class)
- `systemtest/legacy/mod10/base/BaseScenarioDslTest.java:15` (class)
- `systemtest/legacy/mod11/base/BaseScenarioDslTest.java:15` (class)

**Covered by the latest suppression (step 2) — 1 issue:**
- `systemtest/latest/base/BaseScenarioDslTest.java:15` (class, public) — extended from `latest.smoke.system`, `latest.smoke.external`, `latest.e2e.base`, `latest.acceptance.base`, `latest.contract.base`.

**Rule's own documented exception (for reference):**
> "It is generally recommended to omit the public modifier … unless there is a technical reason for doing so — for example, when a test class is extended by a test class in another package."

Note: all these classes were made `abstract` (S2187 fix), which keeps `public` required for the cross-package subclasses.
