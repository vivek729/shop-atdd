# 2026-07-01 09:16:00 UTC — Fix .NET Sonar analysis plugin-fetch failure

## TL;DR

**Why:** CI run [28503997600](https://github.com/optivem/shop/actions/runs/28503997600) failed the `sonar` job in `monolith-dotnet-acceptance-stage.yml` (run [28504639990](https://github.com/optivem/shop/actions/runs/28504639990)) and `multitier-dotnet-acceptance-stage.yml` (run [28504581359](https://github.com/optivem/shop/actions/runs/28504581359)), both at the "Run Sonar Analysis" step (`system-test/dotnet/run-sonar.sh`), with:
  ```
  Unhandled exception. System.IO.FileNotFoundException: Plugin resource not found: securitycsharpfrontend, version 11.35.0.47322. Resource: SonarAnalyzer.Security.CSharp-11.35.0.47322.zip.
  ```
  during SonarScanner's pre-processing/plugin-provisioning phase (before any C# code is analyzed). Root cause pinned to `system-test/dotnet/run-sonar.sh:24` — `dotnet tool install --global dotnet-sonarscanner` installs whatever the *latest* scanner version is at run time with no version pin, so the scanner version (and the plugin catalog it requests from SonarCloud) can drift CI-run-to-CI-run. The failure is a SonarCloud-server-side resource lookup miss for a specific analyzer-plugin version, not a defect in this repo's C# code — classified as an external SonarCloud/scanner version-catalog issue (flake or transient skew), not a genuine product bug.
**End result:** The `dotnet-sonarscanner` tool version used in CI is pinned to a known-good version (or the failure is confirmed to no longer reproduce after a retry/re-run), so `sonar` jobs in `monolith-dotnet-acceptance-stage.yml` and `multitier-dotnet-acceptance-stage.yml` no longer fail on a transient plugin-provisioning mismatch.

## Outcomes

- `system-test/dotnet/run-sonar.sh` installs a pinned `dotnet-sonarscanner` version instead of implicitly floating to "latest", so the scanner/plugin-catalog combination is reproducible and doesn't drift between CI runs.
- The specific `securitycsharpfrontend` plugin-resource-not-found failure is confirmed either resolved by the pin, or (if still present with the pinned version) documented as a genuine SonarCloud-side outage to retry rather than something fixable in-repo.
- `sonar` jobs in both `monolith-dotnet-acceptance-stage.yml` and `multitier-dotnet-acceptance-stage.yml` pass on the next CI run.

## ▶ Next executable step (resume here)

Step 1 below: check the current stable `dotnet-sonarscanner` NuGet package version and pin `system-test/dotnet/run-sonar.sh:24`'s `dotnet tool install --global dotnet-sonarscanner` to that explicit version with `--version <x.y.z>`.

## Steps

- [ ] Step 1: Determine the latest stable, non-preview `dotnet-sonarscanner` NuGet package version (check `https://www.nuget.org/packages/dotnet-sonarscanner` or `dotnet tool search dotnet-sonarscanner` — note: SonarScanner for .NET 11.2.1 is what CI's unpinned "latest" resolved to at failure time, so confirm whether a newer stable release exists that has a consistent plugin catalog). Edit `system-test/dotnet/run-sonar.sh:24` to add `--version <pinned-version>` to the `dotnet tool install --global dotnet-sonarscanner` command.
- [ ] Step 2: Check whether CI's `sonar` job in `monolith-dotnet-acceptance-stage.yml` / `multitier-dotnet-acceptance-stage.yml` installs `dotnet-sonarscanner` via a separate step rather than solely through `run-sonar.sh` (the CI comment in `run-sonar.sh` says CI runs "the same analysis... from monolith-dotnet-acceptance-stage.yml and multitier-dotnet-acceptance-stage.yml after tests finish" via `optivem/actions` retry wrapper) — confirm the pin in Step 1 actually takes effect in the CI path, not just the local manual-run path.
- [ ] Step 3: Check whether the Java and TypeScript system-test suites run an equivalent SonarScanner step with the same unpinned-version pattern (`system-test/java`, `system-test/typescript` — look for their own `run-sonar.sh`/equivalent and CI sonar jobs) and whether they've hit the same or a different plugin-fetch issue. If they pin their scanner tool version already, mirror that approach for consistency; if they don't, decide whether to pin them too or scope this fix to .NET only (the failure so far is .NET-specific).
- [ ] Step 4: Locally run `system-test/dotnet/run-sonar.sh` with a valid `SONAR_TOKEN` (per its own usage instructions) to confirm the pinned scanner version successfully completes analysis end-to-end without the plugin-fetch error.
- [ ] Step 5: Commit and push the fix via the `/commit` skill (per repo convention — never raw `git`).
- [ ] Step 6: Monitor the next `monolith-dotnet-acceptance-stage.yml` / `multitier-dotnet-acceptance-stage.yml` CI run (or trigger the meta-prerelease pipeline) to confirm the `sonar` job passes.

## Open questions

- Whether this is a one-off SonarCloud-side transient issue (in which case simply re-running CI might have already resolved it, independent of any repo change) or a persistent version-catalog mismatch that only a pin fixes. Step 1's version research and Step 4's local repro should disambiguate — if the currently-unpinned "latest" version still fails locally on a fresh install, it's persistent and the pin is necessary; if a fresh local install now succeeds, the CI failure may have been transient and the pin is a preventive hardening rather than a strict fix.
