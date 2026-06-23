# 2026-06-23 19:39:23 UTC — Pact mock server as the stub for frontend narrow integration tests

## TL;DR

**Why:** The frontend already has a Pact mock server wired for the `contract` suite. When adding the `integration` (narrow) suite, the question is whether to reuse that same Pact mock server to stub HTTP responses for direct `orderService` calls — or use a simpler, Pact-unaware stub (MSW/`vi.fn()`). The tradeoffs around pact-file duplication, interaction ownership, and suite boundary blurring are unresolved.

**End result:** A clear decision on the stub mechanism for the `integration` suite — Pact mock server, MSW, or something else — with rationale documented so the choice is repeatable across components and languages.

## Outcomes

- A decided stub strategy for the frontend `integration` suite, with tradeoffs understood.
- If Pact mock server is chosen: clarity on how to use it without generating duplicate `.pact` files or conflicting with the `contract` suite's interaction definitions.
- If MSW (or simpler stub) is chosen: confirmation that the extra dependency is justified and how it differs from the `contract` suite's role.
- The decision feeds directly into plan `20260623-1801-narrow-integration-tests.md` (OQ 4).

## ▶ Next executable step (resume here)

This is a design/decision plan — no mechanical edits yet. Run `/refine-plan` on this file to resolve the open questions below, then take the decision back to `20260623-1801-narrow-integration-tests.md` OQ 4.

## Steps

- [ ] Step 1 — Investigate Pact "stub-only" mode. Does `@pact-foundation/pact` support spinning up the mock server without writing a `.pact` file at the end (e.g. a `MockServer` class, a flag, or a separate package)?
- [ ] Step 2 — Assess interaction ownership. If integration tests define Pact interactions, who owns them — the integration suite or the contract suite? Can both suites share the same interaction definitions without duplicating them?
- [ ] Step 3 — Assess suite boundary blurring. Does using the Pact mock server in the integration suite make it harder to explain the difference between `integration` and `contract` to a learner?
- [ ] Step 4 — Evaluate MSW as an alternative. Check if MSW is already a devDependency; if not, assess the cost of adding it vs. the complexity of Pact stub-only mode.
- [ ] Step 5 — Make the decision and document the rationale. Update OQ 4 in `20260623-1801-narrow-integration-tests.md`.

## Open questions

- Does `@pact-foundation/pact` expose a stub-only mode that skips pact file generation?
- If we share interaction definitions between suites, where do they live (a shared fixture file)?
- Is the pedagogical clarity cost of blurring integration/contract acceptable, or is it a blocker for a teaching repo?
