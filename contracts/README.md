# Consumer-Driven Contracts (Pact)

This folder holds the **consumer-generated Pact contract** shared between the
multitier tiers:

- `frontend-react-backend-java.json` — written by the frontend's consumer Pact
  tests, verified by the backend's provider Pact verification.

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

**Rule of thumb:** the contract lives in the *nearest repo that owns both the
consumer and the provider*.

- **Monolith** → `shop/contracts/` (this folder; peer of `system/` + `system-test/`).
- **Multitier (3-repo gh-optivem model:** `shop-backend`, `shop-frontend`,
  `shop-tests`**)** → the contract lives in **`shop-tests/pacts/`**, the only repo
  where both tiers' artifacts coexist and where provider verification runs
  alongside the existing `ct-test`. It is named `pacts/` (not `contracts/`) to
  stay distinct from the *external-system* `ct-test` contracts (Clock / ERP /
  Tax) that same repo already hosts. This split is realized only at generation
  time; in this single-repo template the pact lives here.

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
