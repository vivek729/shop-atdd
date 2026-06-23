# 2026-06-23 19:41:32 UTC — Provider-side Pact verification: audit and complete

## TL;DR

**Why:** The backend-java provider verification (`BackendPactVerificationTest`) already exists and is wired into the `contract` suite, but it was deferred in plan `1154` without a child plan to track remaining gaps. The other backends (.NET, TypeScript) and the monoliths may be missing provider verification entirely, and CI ordering (consumer must write the `.pact` file before the provider reads it) has not been explicitly confirmed.

**End result:** Provider-side Pact verification is audited across all in-scope backends, any missing verifications are added, CI job ordering is confirmed correct, and the setup is documented so the consumer-driven contract loop is complete and legible.

## Outcomes

- Confirmed that backend-java's `BackendPactVerificationTest` covers all current consumer interactions (no missing `@State` handlers).
- Provider verification present (or explicitly deferred with justification) for each in-scope backend: backend-java, backend-dotnet, backend-typescript, and the three monoliths.
- CI ordering confirmed: frontend consumer Pact tests (writes `.pact`) always run before backend provider verification (reads `.pact`) — no race condition.
- Documented in `docs/` what "provider verification" means in this repo's contract testing flow, and where the neutral `contracts/` folder fits.

## ▶ Next executable step (resume here)

Audit first — read the other backends' `component-tests.yaml` and `src/componentTest` (or equivalent) to check whether provider verification exists there. This is a research step, not a mechanical edit. Once the gap list is known, Step 2 becomes executable.

## Steps

- [ ] Step 1 — Audit all backends for provider verification. For each of: backend-dotnet, backend-typescript, monolith-java, monolith-dotnet, monolith-typescript — check whether a provider Pact verification test exists and is wired into the `contract` suite in `component-tests.yaml`.
- [ ] Step 2 — Confirm CI ordering. Check the GitHub Actions workflow to verify consumer Pact (frontend) runs and commits the `.pact` file before the backend provider verification job reads it. Fix any ordering gap.
- [ ] Step 3 — Add missing provider verification tests for any backends identified in Step 1 (or explicitly mark them deferred with a reason if they have no REST API surface to verify).
- [ ] Step 4 — Docs. Add a short section to `docs/pipeline/commit-stage.md` (or equivalent) explaining the consumer → neutral `contracts/` folder → provider verification flow.

## Open questions

- Do backend-dotnet, backend-typescript, and the three monoliths expose the same REST API surface as backend-java and therefore need the same provider verification? Or do only the multitier backends have a frontend consumer?
- Is the neutral `contracts/` folder committed to the repo (so CI can read it without a prior job), or does it rely on CI artifact passing between jobs?
- Should a Pact Broker replace the repo-owned `contracts/` folder eventually, or is the repo-owned approach the intended long-term design for this teaching repo?
