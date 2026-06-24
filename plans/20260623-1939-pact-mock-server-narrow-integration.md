# 2026-06-23 19:39:23 UTC — Pact mock server as the stub for frontend narrow integration tests

## TL;DR

**Why:** The frontend already has a Pact mock server wired for the `contract` suite. When adding the `integration` (narrow) suite, the question is whether to reuse that same Pact mock server to stub HTTP responses for direct `orderService` calls — or use a simpler, Pact-unaware stub (MSW/`vi.fn()`). The tradeoffs around pact-file duplication, interaction ownership, and suite boundary blurring are unresolved.

**End result:** Decided — the frontend `integration` (narrow) suite uses the **Pact mock server** (overriding the earlier provisional MSW default). The design is shared-fixture + both-emit-union: the narrow `integration` suite and the `component` suite both drive the Pact mock server via `PactV3` from one shared interaction fixture and both emit into a single union `frontend→backend` contract; a low-level stub-only mode (no `.pact` written) stays available as an explicit opt-out. Settled by the coordination meta-plan `plans/20260624-0653-meta-narrow-integration-cluster.md` (Target state, 2026-06-24) and written back to `[[20260623-1801-narrow-integration-tests]]` OQ 4.

## Outcomes

- A decided stub strategy for the frontend `integration` suite: the Pact mock server (shared-fixture + both-emit-union), with tradeoffs understood. (Done — see Decisions.)
- Clarity on how to use it without `.pact` conflicts: one shared interaction fixture under `src/test/interactions/`, both middle suites emitting into the union `frontend→backend` contract (merge), with a stub-only opt-out for tests that must not touch the contract. (Done.)
- The decision fed directly into plan `[[20260623-1801-narrow-integration-tests]]` (OQ 4). (Done.)

## ▶ Next executable step (resume here)

Decision is settled (see Decisions below) and written back to `[[20260623-1801-narrow-integration-tests]]` OQ 4. No mechanical edits remain in this plan itself: documenting the shared-fixture / both-emit-union pattern in canonical `docs/atdd/` is owned by plan `[[20260623-1957]]`, and the per-component wiring is owned by `[[20260623-1801-narrow-integration-tests]]` + its rollout `[[20260623-1944-narrow-integration-rollout]]`. This plan is closed as decision-resolved.

## Steps

- [x] Decision made and rationale recorded (see Decisions below). The three investigation questions (stub-only mode, interaction ownership, suite-boundary clarity) are answered there.
- [x] Decision written back to `[[20260623-1801-narrow-integration-tests]]` OQ 4.

No further mechanical steps belong to this plan — pattern docs live in `[[20260623-1957]]`, component wiring in 1801 / 1944.

## Decisions (resolved 2026-06-24)

Source of truth: the coordination meta-plan `plans/20260624-0653-meta-narrow-integration-cluster.md` (Target state). Summarized here; not duplicated.

**Stub mechanism — Pact mock server, not MSW/`vi.fn()`.** The frontend `integration` (narrow) suite stubs HTTP via the Pact mock server through `PactV3`, reusing the same stack as the `contract`/`component` suites. The earlier provisional MSW default is overridden; MSW is not added.

**Stub-only mode exists and is an opt-out, not the default.** A low-level path using `@pact-foundation/pact-core`'s `createMockServer` / `cleanupMockServer` without ever calling `writePactFile` runs the mock server without emitting a `.pact`. It is verified and stays available for a narrow test that should deliberately NOT touch the contract — but the default narrow test emits into the contract (see below).

**Shared interaction fixture, parameterized.** Interaction definitions live in ONE shared, test-only fixture of parameterized builders under `src/test/interactions/` (deliberately not named `contracts/`, to avoid colliding with the `.pact` output folder). Each suite supplies only data points; neither suite owns the interactions outright — the fixture does.

**Shared-fixture + both-emit-union design.** BOTH the narrow `integration` suite and the `component` suite use the Pact mock server and BOTH emit interactions into ONE `frontend→backend` contract via `PactV3.executeTest`. The committed `.pact` is the UNION of both suites' interactions: they point at the same `contracts/` dir, write with merge, and run together when regenerating. Building from the shared fixture, identical interactions merge idempotently; narrow MAY add interactions the UI never exercises (e.g. cancel/deliver), which simply append.

**Suite boundary = boot/render discriminator, not stub mechanism.** Pedagogical clarity is preserved by what the test drives, not by the mocking stack: boots/renders the real component → `component`; calls a single adapter (`orderService`) directly → narrow `integration`. The Pact mock server is orthogonal — both middle layers share it. So blurring is not a concern: the layers stay distinct on the boot/render axis.
