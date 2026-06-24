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

**Step 3 — Add missing provider verification tests** for backend-dotnet and backend-typescript (Wave 2). Monoliths confirmed deferred (no consumer). See audit findings below.

## Audit findings (Wave 1 — 2026-06-24)

- **backend-dotnet**: `contract` suite `pending: true`, no provider verification test — **needs it** (frontend-react is the consumer).
- **backend-typescript**: `contract` suite `pending: true`, no provider verification test — **needs it** (same consumer).
- **monolith-java / monolith-dotnet / monolith-typescript**: `contract` suite `pending: true`, no in-process consumer — ⏳ deferred (mark with reason when wiring Wave 2).
- **contracts/**: committed at repo root (`contracts/frontend-backend.json`), CI ordering is correct (backends read committed copy, no race).

## Steps

- [ ] Step 3 — Add provider verification tests for backend-dotnet (PactNet) and backend-typescript (@pact-foundation/pact Verifier). Wire `contract` suite in each `component-tests.yaml`. Explicitly mark monolith ×3 deferred-with-reason.
- [ ] Step 4 — Docs. Add a short section to `docs/pipeline/commit-stage.md` explaining the consumer → committed `contracts/` → provider verification flow (joint pass with `1801` Step 6).

## Decisions (resolved 2026-06-24)

These resolve the plan's former open questions per the locked Target state in the coordination meta-plan `[[20260624-0653-meta-narrow-integration-cluster]]`.

**OQ1 — Which backends need provider verification?** Rule decided; concrete per-component list confirmed by the Step 1 audit. The rule is **provider verification follows the consumer**: wherever a consumer emits a `.pact` against a backend, that backend needs provider verification. The multitier backends (backend-java, backend-dotnet, backend-typescript) are consumed by frontend-react → they need it. The monoliths need it only if they have an in-process consumer that emits a `.pact`; otherwise they are marked deferred-with-reason. Step 1's audit confirms the concrete list — this decision sets the rule, not the per-component answer.

**OQ2 — Committed `contracts/` folder or CI artifact passing?** **Committed to the repo.** The neutral `contracts/` folder is checked in; CI reads the committed copy, so no inter-job artifact passing is needed in the monorepo. Monorepo → one `contracts/` at root; multi-repo → each repo commits its own copy. (The `.pact` *location* is already settled by `contracts/README.md` and is not re-litigated here.)

**OQ3 — Should a Pact Broker replace the repo-owned `contracts/` folder?** **No.** Repo-owned `contracts/` is the intended long-term design for this teaching repo. A Pact Broker / PactFlow is a cost-labelled **opt-in** for real multi-repo only — never the default. Multi-repo contract transport (and the free Dockerized OSS broker) is spun out to its own follow-up plan, `[[20260624-0814-multirepo-contract-transport-dockerized-broker]]`; reference it there rather than expanding it here.

**Naming note (tracked, not done by this plan):** the suite this plan calls `contract` is being renamed to `provider-verification` in a separate plan (a cross-cutting `id` rename across all `component-tests.yaml`, `suiteGroups.all`, suite-pinning CI workflows, and docs — its own `/create-plan` per the Target state). All "contract suite" references above become "provider-verification suite" references once that rename lands.
