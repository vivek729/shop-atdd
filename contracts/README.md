# Consumer-Driven Contracts (Pact)

This folder holds the **consumer-generated Pact contract** shared between the
multitier tiers:

- `frontend-backend.json` — written by the frontend's consumer Pact
  tests, verified by the backend's provider Pact verification. The
  tech-agnostic name comes from the Pact consumer/provider ids (`frontend`,
  `backend`).

It exists to support the **optional, opt-in** in-process component & contract
test layer in the canonical multitier projects. It is **not** part of the
default build of either tier — see each project's README:

- Backend: [`system/multitier/backend-java/README.md`](../system/multitier/backend-java/README.md) → *Optional: in-process component & contract tests*
- Frontend: [`system/multitier/frontend-react/README.md`](../system/multitier/frontend-react/README.md) → *Optional: in-process component & contract tests*

## Zero-infra default ($0, no service)

Contract distribution here is deliberately **broker-less**:

1. The **consumer** (frontend Pact tests, `npm run test:pact`) writes the pact
   into this folder.
2. The **provider** (backend, `./gradlew componentTest`) reads it back via
   `@PactFolder("../../../contracts")` and verifies every interaction.
3. The pact is **committed to git**, so both tiers see the same contract with no
   external service, no network call, and no cost.

**Rule of thumb:** the committed pact lives in a `contracts/` folder in *every
repo that runs a Pact tier*, resolved as `../contracts` from the flattened
`backend/` (provider) and `frontend/` (consumer) module dirs.

- **Monorepo** (single scaffolded repo) → one `contracts/` at the repo root,
  peer of `backend/` + `frontend/`; both tiers resolve `../contracts`.
- **Multirepo** (separate `*-backend` / `*-frontend` repos) → the pact is
  committed into **both** repos at `contracts/` (broker-less duplication, since
  no single repo owns both tiers). The provider verification runs in the
  **backend** commit stage and reads `../contracts`; the frontend consumer test
  regenerates its own copy at `../contracts`. The two copies can drift between
  runs — that is the accepted cost of the zero-infra, broker-less default.

In this single-repo template the pact lives here at `shop/contracts/`; the
scaffolder copies it into the repo(s) above and rewrites the deep
`../../../contracts` template path to `../contracts`.

## Optional: a Pact Broker (cost-labelled, never the default)

For real **multi-repo** setups where committing the pact across repos is awkward,
the mainstream option is a **Pact Broker**. It is a deliberate, **cost-bearing
opt-in** — never wired into the default path here:

| Option | Cost | Notes |
|---|---|---|
| **PactFlow (free tier)** | $0 | Capped at **2 contracts**, **no `can-i-deploy`**. |
| **PactFlow (Team)** | **≈ $115 / month** | Full features incl. `can-i-deploy`. |
| **Self-hosted OSS Pact Broker** | $0 license | You run/patch/back-up the service — real infra burden. |

Other broker-less multi-repo options (also not the default, documented for
completeness): pass the pact as a **CI artifact / release asset** (consumer
publishes, provider downloads) — works in CI but not for a local
`./gradlew componentTest`.

The template ships with the **$0, zero-infra** `contracts/` + `@PactFolder`
default so cloning students inherit no cost or infrastructure.
