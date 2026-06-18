**Run started:** 2026-06-18 07:33 UTC

## external_roslyn:CS8604 — SystemTests/Legacy/Mod04/E2eTests/PlaceOrderNegativeUiTest.cs:31

- **Message:** Possible null reference argument for parameter 'actual' in 'void ShouldBeEnumerableTestExtensions.ShouldContain<FieldError>(IEnumerable<FieldError> actual, ...)'.
- **What I tried:** Nothing — file lives under `SystemTests/Legacy/`.
- **Open question:** `system-test/dotnet/CLAUDE.md` declares `Legacy/` read-only course reference material ("Do NOT add new tests to Legacy/ — that folder is read-only course reference material"). Editing legacy source for a Sonar INFO/MAJOR cleanup conflicts with that rule. Should legacy be exempt from Sonar fixes, or is editing acceptable here? Deferring per the read-only rule.

## external_roslyn:CS8604 — SystemTests/Legacy/Mod06/E2eTests/PlaceOrderNegativeTest.cs:32

- **Message:** Possible null reference argument for parameter 'actual' in 'void ShouldBeEnumerableTestExtensions.ShouldContain<FieldError>(IEnumerable<FieldError> actual, ...)'.
- **What I tried:** Nothing — file lives under `SystemTests/Legacy/`.
- **Open question:** Same as above — `Legacy/` is read-only course reference material per CLAUDE.md. Deferred.

## external_roslyn:CA1822 — SystemTests/Legacy/Mod02/Base/BaseRawTest.cs:71

- **Message:** Member 'CreateObjectMapper' does not access instance data and can be marked as static.
- **What I tried:** Nothing — file lives under `SystemTests/Legacy/`.
- **Open question:** Same as above — `Legacy/` is read-only course reference material per CLAUDE.md. Deferred.

## external_roslyn:CA1822 — SystemTests/Legacy/Mod03/Base/BaseRawTest.cs:69

- **Message:** Member 'CreateObjectMapper' does not access instance data and can be marked as static.
- **What I tried:** Nothing — file lives under `SystemTests/Legacy/`.
- **Open question:** Same as above — `Legacy/` is read-only course reference material per CLAUDE.md. Deferred.

## external_roslyn:SYSLIB1045 — SystemTests/Legacy/Mod03/E2eTests/PlaceOrderPositiveUiTest.cs:37

- **Message:** Use 'GeneratedRegexAttribute' to generate the regular expression implementation at compile-time.
- **What I tried:** Nothing — file lives under `SystemTests/Legacy/`. (Also would require making the containing test class `partial`.)
- **Open question:** Same as above — `Legacy/` is read-only course reference material per CLAUDE.md. Deferred.
