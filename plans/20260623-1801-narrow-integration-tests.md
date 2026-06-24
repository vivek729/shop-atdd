# 2026-06-23 18:01:27 UTC — Fill the pending `integration` (narrow integration) suites

## TL;DR

**Why:** Every component's `component-tests.yaml` declares an `integration`
("Narrow Integration") suite but it is `pending: true` everywhere — the level
exists in the taxonomy with **zero tests**. The 4-level pyramid (unit → narrow
integration → component → contract) has a hole exactly at the layer that proves a
single adapter talks to a single real dependency.

**End result:** Each component gains a small, real **narrow-integration** suite —
one component's edge exercised against a real adapter/dependency in isolation (a
repository against a real Postgres via Testcontainers; an HTTP client against a
stubbed server) — wired into its `component-tests.yaml` (`pending` removed,
`command` + `sampleTest` + explicit positive filter added). `gh optivem component
test run --suite integration` then runs real tests, and the `all` gate covers the
full pyramid.

## Outcomes

What we get out of this — the goals and deliverables:

- The `integration` suite is **no longer `pending`** in the components in scope:
  `command`, `sampleTest`, and (where the stack splits by name/path) an explicit
  positive filter are filled in, matching the symmetric-filter rule from
  `20260623-1154-component-test-suite-config.md`.
- At least one **real narrow-integration test per in-scope component**, testing one
  edge against one real dependency in isolation — *not* a full component boot, *not*
  a system test. Java reuses the existing `AbstractIntegrationTest`
  (Testcontainers-Postgres) anchor.
- `gh optivem component test run --suite integration` runs green locally (Docker up)
  and the suite participates in the `all` gate that CI pins.
- `--sample` works for the new suite (each has a known-good `sampleTest`).
- Cross-OS command convention honored (`.\gradlew.bat`, not `./gradlew`) so the new
  Java commands run on Windows and Linux CI alike.
- Docs note (where relevant) what "narrow integration" means here vs `component` /
  `contract`, so the pyramid level is pedagogically legible.

## ▶ Next executable step (resume here)

**Step 6 — Docs** (Wave 2, joint pass with 1941 Step 4 on `docs/pipeline/commit-stage.md`). Owned by the final Batch B doc pass in Wave 2 of the meta-plan. Do not execute standalone.

## Steps

- [ ] **Step 5 — Roll out to remaining in-scope components** — ⏳ Deferred to `plans/20260623-1944-narrow-integration-rollout.md` (Wave 2).
- [ ] **Step 6 — Docs.** Note the narrow-integration level in `docs/pipeline/commit-stage.md` — joint pass with `1941` Step 4 (Wave 2 Batch B).

## Decisions (resolved 2026-06-23)

- **OQ 1 — Scope:** Pilot first — backend-java + frontend-react only. Rollout to the
  remaining 5 components tracked in `plans/20260623-1944-narrow-integration-rollout.md`.
- **OQ 2 — Java source set:** Option (b) — dedicated `integrationTest` Gradle source set
  parallel to `componentTest`. The existing `test` source set gets a simple `SimpleArithmeticTest`
  (1 + 1 = 2, no Docker) so the `unit` suite has real unit tests and no longer needs
  `requiresDocker: true`. `BackendApplicationTests` (contextLoads) moves to `integrationTest`.
- **OQ 3 — Java canonical edge:** `OrderRepository` ↔ real Postgres via Testcontainers —
  save an order and read it back. Uses the existing `AbstractIntegrationTest` anchor.
- **OQ 4 — Frontend stub mechanism (re-decided 2026-06-24):** The frontend
  `integration` (narrow) suite uses the **Pact mock server** via `PactV3` and a
  shared interaction fixture under `src/test/interactions/`, emitting into the
  union `frontend→backend` contract alongside the `component` suite (the committed
  `.pact` is the union of both suites' interactions). This **flips** the earlier
  MSW provisional default. The two middle suites differ by the **boot/render
  discriminator** (boots/renders the real component → `component`; calls a single
  adapter directly → narrow `integration`), not by stub mechanism — both share the
  Pact mock server. A low-level stub-only mode (no `.pact` written) stays available
  as an opt-out for a narrow test that must deliberately not touch the contract.
  Settled in `[[20260623-1939-pact-mock-server-narrow-integration]]` on 2026-06-24
  (per the Target state in
  `plans/20260624-0653-meta-narrow-integration-cluster.md`).
- **OQ 5 — Provider-side Pact:** Out of scope here. Provider verification is already
  implemented in `BackendPactVerificationTest` (wired into the `contract` suite). Gaps and
  rollout tracked in `plans/20260623-1941-provider-pact-verification.md`.
- **OQ 6 — Which components stay `pending`:** Deferred to the rollout plan
  `plans/20260623-1944-narrow-integration-rollout.md` — audit each component's adapter
  surface during that pass, not before.
