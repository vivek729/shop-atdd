# 2026-06-18 07:33:00 UTC — Sonar deferred: Java tests

**Run started:** 2026-06-18 07:33 UTC

Deferred SonarCloud issues — optivem_shop-tests-java

## java:S5786 (15) — Remove this 'public'/'protected' modifier

All 15 S5786 issues are on JUnit5 base test classes (and their `@BeforeEach`
lifecycle methods) that are extended by test classes in **other packages**.

Affected:
- src/test/java/com/mycompany/myshop/systemtest/latest/base/BaseScenarioDslTest.java:15 (class, public)
- src/test/java/com/mycompany/myshop/systemtest/legacy/mod02/base/BaseRawTest.java:15 (class, public)
- src/test/java/com/mycompany/myshop/systemtest/legacy/mod02/base/BaseRawTest.java:30 (setUpConfiguration @BeforeEach, protected)
- src/test/java/com/mycompany/myshop/systemtest/legacy/mod03/base/BaseRawTest.java:14 (class, public)
- src/test/java/com/mycompany/myshop/systemtest/legacy/mod03/base/BaseRawTest.java:28 (lifecycle, protected)
- src/test/java/com/mycompany/myshop/systemtest/legacy/mod04/base/BaseClientTest.java:14 (class, public)
- src/test/java/com/mycompany/myshop/systemtest/legacy/mod04/base/BaseClientTest.java:23 (lifecycle, protected)
- src/test/java/com/mycompany/myshop/systemtest/legacy/mod05/base/BaseDriverTest.java:15 (class, public)
- src/test/java/com/mycompany/myshop/systemtest/legacy/mod05/base/BaseDriverTest.java:23 (lifecycle, protected)
- src/test/java/com/mycompany/myshop/systemtest/legacy/mod06/base/BaseChannelDriverTest.java:20 (class, public)
- src/test/java/com/mycompany/myshop/systemtest/legacy/mod07/base/BaseUseCaseDslTest.java:12 (class, public)
- src/test/java/com/mycompany/myshop/systemtest/legacy/mod08/base/BaseScenarioDslTest.java:14 (class, public)
- src/test/java/com/mycompany/myshop/systemtest/legacy/mod09/base/BaseScenarioDslTest.java:15 (class, public)
- src/test/java/com/mycompany/myshop/systemtest/legacy/mod10/base/BaseScenarioDslTest.java:15 (class, public)
- src/test/java/com/mycompany/myshop/systemtest/legacy/mod11/base/BaseScenarioDslTest.java:15 (class, public)

**What I tried / verified:** Each flagged base class is extended by concrete
test classes living in different packages (e.g.
`latest.base.BaseScenarioDslTest` is extended from `latest.smoke.system`,
`latest.smoke.external`, `latest.e2e.base`, `latest.acceptance.base`,
`latest.contract.base`; the legacy `BaseRawTest`/`BaseClientTest`/etc. are
extended from their module's `smoke.*` and `e2e.base` subpackages). Removing
`public` from the class → package-private would make it inaccessible to those
cross-package subclasses and break compilation. Likewise, demoting the
`protected` `@BeforeEach` lifecycle methods to package-private risks JUnit5 not
discovering/invoking the inherited lifecycle method across package boundaries.

This is the exact technical exception the S5786 rule itself documents:
> "It is generally recommended to omit the public modifier ... unless there is a
> technical reason for doing so — for example, when a test class is extended by a
> test class in another package."

**Open question:** Should these be marked "Won't Fix" / accepted in SonarCloud as
the documented cross-package-inheritance exception, rather than code-changed?
Alternatively, the base classes could be moved into the same package as their
subclasses (large restructuring, behavior-neutral but invasive) — out of scope
for a mechanical Sonar pass.

Note: All these classes were made `abstract` (S2187 fix), which keeps `public`
required for the cross-package subclasses.
