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

**Design is not finished — resolve the Open questions first** (which components are
in scope for the first pass, and what concrete "edge" each narrow-integration test
exercises per stack). This is a planning step, not a mechanical edit: run
`/refine-plan` on this file to settle the Open questions, then the first executable
unit becomes **Step 2 (Java pilot)** — author a `*IntegrationTest` on top of the
existing `AbstractIntegrationTest`, wire `backend-java/component-tests.yaml`'s
`integration` suite (`command: .\gradlew.bat test --tests '*IntegrationTest'` or a
dedicated source set — see OQ), remove `pending`, add `sampleTest`, and verify with
`gh optivem component test run --suite integration --component backend`.

## Steps

- [ ] **Step 1 — Decide scope + per-stack "edge".** Resolve Open questions: which
  components are in the first pass (recommend pilot = backend-java + frontend-react,
  mirroring plan `1154`), and the concrete edge each test exercises (repo↔Postgres,
  HTTP client↔stub, etc.). Confirm the Java unit suite's existing
  `requiresDocker`/`contextLoads` boundary vs. a distinct narrow-integration suite.
- [ ] **Step 2 — Java pilot (backend-java).** Add a `*IntegrationTest` extending
  `AbstractIntegrationTest` (e.g. an order repository against real Postgres). Wire the
  `integration` suite in `system/multitier/backend-java/component-tests.yaml`:
  `pending` removed, `command` with an explicit `--tests '*IntegrationTest'` positive
  filter, `requiresDocker: true`, `sampleTest`. Verify locally.
- [ ] **Step 3 — Frontend pilot (frontend-react).** Add a narrow-integration spec
  (e.g. the API client against a stubbed HTTP server) under an explicit dir/script.
  Add `npm run test:integration` (or equivalent) + wire the suite (positive include,
  `sampleTest`), remove `pending`. Verify locally (no Docker).
- [ ] **Step 4 — Run the pilots through `--suite integration` and the `all` gate;**
  confirm `--sample` and the multi-component fan-out group correctly.
- [ ] **Step 5 — Roll out to remaining in-scope components** (backend-dotnet,
  backend-typescript, monolith ×3) using the same shape; leave any still-genuinely-
  empty levels `pending` rather than faking tests.
- [ ] **Step 6 — Docs.** Note the narrow-integration level's meaning/boundary where
  the component-test docs already describe the pyramid (`docs/pipeline/commit-stage.md`
  and/or component READMEs).

## Open questions

- **Scope of the first pass.** All 7 components at once, or pilot (backend-java +
  frontend-react) then roll out — mirroring how plan `1154` was sequenced?
  *(Recommend: pilot first.)*
- **Java: same `test` source set or a dedicated one?** The `unit` suite is already
  `.\gradlew.bat test` with `requiresDocker: true` and `sampleTest: contextLoads` —
  i.e. it already boots a Spring context against Postgres, which is arguably itself a
  narrow-integration concern. Do we (a) carve narrow-integration out by `--tests`
  name filter within the existing `test` source set, (b) add an `integrationTest`
  source set parallel to `componentTest`, or (c) re-label what's there? This affects
  the symmetric-positive-filter guarantee.
- **What is each component's canonical "edge"?** Per stack, name the one adapter +
  one real dependency the narrow-integration test pins (repository↔Postgres? outbound
  HTTP client↔stub? message/DB migration?). Needed before writing any test.
- **Frontend stub mechanism.** What does the frontend use to stand up a stubbed HTTP
  server for a narrow client test (MSW, a local fake, the existing Pact mock)? Should
  it reuse existing test infra rather than add a dependency.
- **Provider-side Pact** stays out of scope (already deferred in plan `1154`); confirm
  narrow-integration here does **not** quietly absorb provider verification.
- **Which components legitimately stay `pending`** after this pass (e.g. backends with
  no persistence adapter yet) vs. must get a real test now.
