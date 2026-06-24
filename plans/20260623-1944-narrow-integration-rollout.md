# 2026-06-23 19:44:57 UTC — Narrow integration test rollout to remaining 5 components

## TL;DR

**Why:** The pilot plan (`20260623-1801-narrow-integration-tests.md`) adds narrow integration tests to backend-java and frontend-react only. The remaining 5 components (backend-dotnet, backend-typescript, monolith-java, monolith-dotnet, monolith-typescript) still have `integration: pending: true` with no tests. The pyramid has a hole in those components until this rollout lands.

**End result:** Every component either has a real narrow integration test wired into its `integration` suite, or has `pending: true` with an explicit comment explaining why no real adapter exists yet. The `all` gate covers the full pyramid across the entire repo.

## Outcomes

- Each of the 5 remaining components is audited: does it have a real adapter (repository↔DB, HTTP client↔stub) worth testing at the narrow integration layer?
- Components with a real adapter have a working narrow integration test and a wired `integration` suite (`pending` removed, `command` + `sampleTest` filled in).
- Components without a real adapter keep `pending: true` with an inline comment explaining the gap (e.g. "no persistence adapter yet").
- `gh optivem component test run --suite integration` runs green across all non-pending components.
- The pattern established by the pilot (dedicated source set / script, positive name filter, `sampleTest`) is applied consistently.

## ▶ Next executable step (resume here)

**All steps done (Wave 2, 2026-06-24).** monolith-java, backend-dotnet, monolith-dotnet wired with real narrow-integration tests; backend-typescript and monolith-typescript marked pending with reason (see `plans/20260624-0950-typescript-narrow-integration-testcontainers.md`). CI-verified.
