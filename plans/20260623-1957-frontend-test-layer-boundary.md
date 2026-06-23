# 2026-06-23 19:57:00 UTC — Frontend test-layer boundary: component vs. Pact consumer contract

## TL;DR

**Why:** The division of labour between the frontend component tests (`order.component.test.tsx`) and the Pact consumer contract tests (`coupon.pact.test.tsx`, `order.pact.test.tsx`) is implicit — it lives only in file-header comments. It is easy to misread the design (e.g. assuming the component tests use "some kind of mock server that isn't Pact", when in fact they use no server at all — just a `vi.fn()` fetch stub). This causes confusion about what is tested where and risks accidental duplication as the suites grow.
**End result:** The boundary is explicitly documented in one canonical place: component tests = pure client-side states with a `vi.fn()` fetch stub (no server, no network); Pact tests = real-request flows against a real HTTP mock server, doubling as the happy-path/contracted-error component tests. The rule for "which file does a new frontend test go in?" is unambiguous.

## Outcomes

What we get out of this — the goals and deliverables:

- A clear, written statement of the partition: the deciding question is **"does a request go over the wire?"** — No → component file (`vi.fn()` stub); Yes → Pact file (real mock server).
- Explicit correction of the "mock server" misconception: component tests stub `fetch` itself at the JS level — there is **no** mock server, not even a non-Pact one. Only Pact boots a real HTTP server on a URL.
- A documented statement that the happy paths are **not duplicated**: Pact tests fold the happy-path component assertions in ("one test, two jobs"), so the standalone component file only covers what Pact *cannot* model.
- An enumerated list of which states live where (validation short-circuit / loading / network-down → component; 200/201/204/404/422 flows → Pact).
- A decision on **where** this documentation lives (see Open questions) and the doc actually written there.

## ▶ Next executable step (resume here)

This is a **documentation-only** plan. Each Open question now carries a recommendation; the next move is to confirm those four recommendations with the user (or accept them as-is), then `/execute-plan` this file. The recommended path: write a short canonical page under `docs/atdd/` describing the frontend test-layer boundary — the "does a request go over the wire?" rule, the no-server (`vi.fn()`) vs. real-HTTP-mock-server distinction, the no-duplication / "one test, two jobs" point, and the per-state table — then reduce the three test-file header comments to one-line pointers at that page. No code/test behaviour changes.

## Steps

- [ ] Step 1: Confirm the factual boundary against the current code (re-read the three test files + `test-utils.tsx` `routeApiTo`/`renderWithProviders`) so the documented rule matches reality.
- [ ] Step 2: Decide the canonical home for the documentation (see Open questions).
- [ ] Step 3: Write the boundary description there: the "over the wire?" rule, the no-server-vs-real-server distinction, the no-duplication / "one test, two jobs" point, and the per-state table.
- [ ] Step 4: Cross-link — point the file-header comments at the canonical doc (or vice versa) so they don't drift.
- [ ] Step 5: Sanity-check the equivalent split exists (or note its absence) for the coupon flows and any future frontend pages, so the rule generalises.

## Open questions

Each carries a recommendation (`→`); none is locked until the user confirms.

- **Where does this doc live?** Options: (a) keep it in the test-file header comments only and just sharpen them; (b) a dedicated page under `docs/atdd/` (e.g. a frontend testing-strategy doc); (c) a `system/multitier/frontend-react` README section.
  → **Recommend (b):** a short canonical page under `docs/atdd/` that the test-header comments point at. Matches the repo convention of docs-as-markdown-under-`docs/` with a single source of truth, and stops the explanation living only in comments that drift. Keep header comments to a one-line pointer at the doc.
- **Scope:** frontend-only, or document the same boundary symmetrically for the backend component/Pact layers (the backend rows in the test summary) too?
  → **Recommend: frontend-first, structured to generalise.** Write the frontend boundary now, but frame the "does a request go over the wire?" rule language-neutrally so the backend section can be added to the same page later without a rewrite. Don't block this on backend.
- **Is this purely documentation, or also a light refactor?**
  → **Recommend: documentation-only.** The current code is correct — the partition already holds (validation/loading/network-down in the component file; 200/201/204/404/422 in Pact). It is only under-documented. No renames/moves in scope; if a clarity refactor surfaces, capture it as a separate plan.
- **Audience:** academy students cloning the template, or internal maintainer notes?
  → **Recommend: cloning students (primary), maintainer-friendly (secondary).** Tone = teaching-the-pattern, explaining *why* the split exists. Must stay $0 / zero-infra-friendly per repo conventions (no implication that Pact/broker infra is required on the default path).
