# 2026-06-18 07:33:00 UTC — Sonar deferred: .NET tests

**Run started:** 2026-06-18 07:33 UTC

## Target state

All 5 deferred issues live in `SystemTests/Legacy/**`, which `system-test/dotnet/CLAUDE.md` declares **read-only course-reference material**. Rather than edit frozen code (against the rule) or click "Won't Fix" per issue in the UI (manual, doesn't stop new legacy flags), the legacy tree is **excluded from SonarCloud analysis at source**. When the work is done:

- `system-test/dotnet/run-sonar.sh` passes `/d:sonar.exclusions="**/Legacy/**"` on the `dotnet sonarscanner begin` command, so the `optivem_shop-tests-dotnet` analysis no longer scans the legacy tree.
- Because both CI acceptance-stage workflows (`monolith-dotnet-acceptance-stage.yml`, `multitier-dotnet-acceptance-stage.yml`) invoke the scan via `bash ./run-sonar.sh`, this single edit covers local **and** CI — no workflow change needed.
- All 5 deferred issues (2× CS8604, 2× CA1822, 1× SYSLIB1045) clear on the next analysis, and no future legacy-tree issue is ever raised again.
- No `.cs` file under `Legacy/` is touched; the read-only rule is honoured.

## Resolved decisions

- **Legacy tree → exclude from SonarCloud analysis** (chosen over editing the read-only code, and over per-issue "Won't Fix"). Legacy is frozen course-reference material; subjecting it to the quality gate produces only noise. Same policy is applied symmetrically to the Java legacy tree in `20260618-0733-sonar-deferred-java-tests.md`.

## Steps

1. Edit `system-test/dotnet/run-sonar.sh`: add `/d:sonar.exclusions="**/Legacy/**"` to the `dotnet sonarscanner begin` invocation (alongside the existing `/k:` `/n:` `/o:` `/d:` flags).
2. Run `bash ./run-sonar.sh` locally (with `SONAR_TOKEN`) to confirm the scan succeeds and the legacy components drop out of the analysis. *(Optional — CI will also re-run it.)*
3. No code edits, no workflow edits, no commit-time compile impact (config-only change to a shell script).
4. Confirm on the next SonarCloud analysis that the 5 issues below are gone.

## Issues covered by the exclusion (inventory)

All five are resolved by step 1 above — no per-issue action.

- `external_roslyn:CS8604` — `SystemTests/Legacy/Mod04/E2eTests/PlaceOrderNegativeUiTest.cs:31` — possible null reference argument for `actual` in `ShouldContain<FieldError>`.
- `external_roslyn:CS8604` — `SystemTests/Legacy/Mod06/E2eTests/PlaceOrderNegativeTest.cs:32` — same.
- `external_roslyn:CA1822` — `SystemTests/Legacy/Mod02/Base/BaseRawTest.cs:71` — `CreateObjectMapper` can be marked static.
- `external_roslyn:CA1822` — `SystemTests/Legacy/Mod03/Base/BaseRawTest.cs:69` — same.
- `external_roslyn:SYSLIB1045` — `SystemTests/Legacy/Mod03/E2eTests/PlaceOrderPositiveUiTest.cs:37` — use `GeneratedRegexAttribute` (would also need the class made `partial`).
