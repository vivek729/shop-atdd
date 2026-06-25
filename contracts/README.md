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

## Monorepo: zero-infra default ($0, no service)

Contract distribution in a **monorepo** is deliberately **broker-less**:

1. The **consumer** (frontend Pact tests, `npm run test:pact`) writes the pact
   into this folder.
2. The **provider** (backend, `./gradlew componentTest`) reads it back via
   `@PactFolder("../../../contracts")` and verifies every interaction.
3. The pact is **committed to git**, so both tiers see the same contract with no
   external service, no network call, and no cost.

**Rule of thumb:** the committed pact lives in a `contracts/` folder in *every
repo that runs a Pact tier*, resolved as `../contracts` from the flattened
`backend/` (provider) and `frontend/` (consumer) module dirs.

This is the **best option for a monorepo** — not a consolation, not a budget
workaround. The consumer and provider share one `contracts/` folder, so
distribution is trivial, atomic, and $0.

The template ships with this default so cloning students inherit no cost or
infrastructure.

## Multi-repo: the distribution problem

In a **monorepo**, the consumer writes the pact and the provider reads it from
the same filesystem — trivial and atomic. The moment `*-frontend` and
`*-backend` live in **separate repos**, there is **no shared filesystem**: the
provider repo does not have the pact the consumer repo just generated.

### Why "duplicate in both repos" is a false-green

The naive fix — commit a copy of the pact into **both** repos — looks safe
but creates a silent failure mode:

1. The consumer changes the API (renames a field, adds a parameter).
2. The consumer test suite regenerates the pact and commits it to the consumer
   repo's `contracts/`.
3. The developer forgets to sync the copy in the provider repo (or the sync PR
   is not yet merged).
4. The provider runs its verification against the **stale** copy — **it passes**.
5. The provider is actually incompatible with the current consumer.

The provider's green CI means nothing: it verified a contract the consumer no
longer produces. This is the **false-green** that the consumer-CI-push default
below eliminates.

## Multi-repo $0 default: consumer-CI-push

The chosen default for multi-repo distribution is **consumer-CI-push**:

1. The consumer CI (frontend build) generates the union pact.
2. Consumer CI **commits the freshly-generated pact directly** into the provider
   repo's `contracts/` — no PR gate.
3. Provider CI verifies the just-pushed copy.

**Why it eliminates the false-green:** the provider always verifies *the exact
contract the current consumer produces* — there is no stale copy to drift.

**Local provider-verification still works:** the pact physically lands in the
provider repo, so a developer can run `./gradlew componentTest` offline against
the last-pushed copy without spinning up any service.

**Cross-repo auth:** `GITHUB_TOKEN` cannot push to another repo by default.
The consumer CI workflow needs one explicit setup step — a **scoped GitHub App**
(short-lived installation tokens) or a **fine-grained PAT** scoped to the
provider repo. This is the only infra requirement of the $0 multi-repo mode.

**Known limitation — the honest motivation for the broker:** because the
consumer pushes immediately and has usually already merged, a breaking consumer
change is detected in the **provider** repo's verification run *after* it lands,
not at the consumer's gate before merge. The git mechanism has no pre-merge
"is the provider compatible?" check. That pre-merge guarantee is exactly what a
Pact Broker's **`can-i-deploy`** provides — this limitation is the deliberate
reason to graduate to the opt-in broker.

> **Note:** In the multitier multirepo scaffold, this step is wired by `gh optivem init`.

## Multi-repo options

| Mechanism | $0? | Local offline verify | Pre-merge gate | Standing infra |
|---|---|---|---|---|
| **Consumer-CI-push** ← default | ✅ | ✅ | ❌ | Cross-repo auth only |
| **CI artifact / release asset** | ✅ | ❌ | ❌ | None |
| **Self-hosted OSS Pact Broker** | ✅ license | ✅ | ✅ `can-i-deploy` | Postgres + broker service |
| **PactFlow free tier** | ✅ | ✅ | ❌ no `can-i-deploy` | None (hosted) |
| **PactFlow Team** | ≈ $115/mo | ✅ | ✅ `can-i-deploy` | None (hosted) |
| **Manual duplicate** ⚠️ | ✅ | ✅ | ❌ | None |

**CI artifact / release asset:** the consumer CI publishes the pact as an
artifact or GitHub release asset; the provider downloads it in CI. Works in
CI, but a developer running `./gradlew componentTest` locally has no pact to
read — **local offline verification is lost**. Not the default for this reason.

**Manual duplicate** is labelled ⚠️ as the compromised fallback: it is the
false-green pattern described above. Acceptable only when cross-repo auth cannot
be set up and the team accepts the drift risk consciously.

## Optional: free self-hosted Pact Broker (opt-in, production answer)

For multi-repo setups that need a **pre-merge gate**, the production answer is a
**Pact Broker**. The OSS option is `pactfoundation/pact-broker` + Postgres via
docker-compose — **$0 license**.

What it gives you:

- **Publish / verify / compatibility matrix** — provider verifies all known
  consumer contracts; history survives across runs.
- **`can-i-deploy`** — the broker's headline payoff. Before the consumer merges,
  CI asks the broker "is the provider currently compatible with this pact version?"
  and blocks the merge if not. This is the pre-merge guarantee the git mechanisms
  lack.
- **Webhooks** — provider verification can be triggered automatically when a new
  pact is published.

**Persistent vs ephemeral (important):** the broker only delivers its value when
it is **persistent** — a standing service that accumulates cross-run history.
Standing up the broker *ephemerally* inside a CI job (fresh docker-compose per
run, no persistent volume) throws away the history that justifies a broker; it
is useful as a **demo** but not as a workflow.

**Why it is not the default:** standing infra someone runs, patches, and backs
up — that cost is real. The default scaffold stays $0 + zero-infra so students
who clone the template inherit no operational burden. The broker is an explicit,
labelled opt-in taught as the motivated production answer after the multi-repo
limitation above makes clear what it solves.
