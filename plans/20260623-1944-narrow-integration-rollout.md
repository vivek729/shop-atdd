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

**Prerequisite:** the pilot plan `20260623-1801-narrow-integration-tests.md` must be fully executed first. Once the pilot lands, the first executable unit here is **Step 1** — read each remaining component's `component-tests.yaml` and source tree to determine whether a real adapter exists, then produce a per-component decision list before writing any tests.

## Steps

- [ ] Step 1 — Audit each remaining component. For backend-dotnet, backend-typescript, monolith-java, monolith-dotnet, monolith-typescript: check whether a real adapter (DB repository, outbound HTTP client, etc.) exists in the source tree. Produce a decision: `add test` or `stay pending (reason)`.
- [ ] Step 2 — Add narrow integration tests to components flagged `add test` in Step 1, following the same shape as the pilot (dedicated source set or script, positive name filter, `requiresDocker` if needed).
- [ ] Step 3 — Wire each new test into its `component-tests.yaml`: remove `pending`, add `command`, `sampleTest`, and (where needed) `requiresDocker`.
- [ ] Step 4 — For components staying `pending`, add an inline `# reason` comment to `component-tests.yaml` explaining why.
- [ ] Step 5 — Verify locally: `gh optivem component test run --suite integration` passes for all newly wired components; `--sample` works for each.

## Open questions

- Do the three monoliths share the same adapter pattern as the multitier backends, or do they have a different persistence/HTTP-client structure that needs a different test shape?
- Should the rollout happen in one PR per component, or one PR for all 5 together?
