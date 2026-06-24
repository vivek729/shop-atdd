# 2026-06-24 08:14 UTC — Multi-repo contract transport: $0 strategy + free dockerized Pact Broker

> **Follow-up to the narrow-integration cluster** (completed and removed 2026-06-24).
> Spun out of that cluster's target-state discussion: under the union-emission design the
> frontend `integration` + `component` suites jointly produce one `frontend-backend` contract, and the
> question *"where does that contract live / how does it reach the provider once repos are split?"* is
> **out of scope** for the cluster's five plans. This plan owns it. **No code changes are committed by
> drafting this — it is a decision/design plan.**

## TL;DR

**Why:** In the **monorepo** scaffold, the consumer and provider share one `contracts/` folder, so contract
distribution is trivial, atomic, and $0 — genuinely the best option for that shape. The moment gh-optivem
scaffolds a **multi-repo** layout (`*-frontend` / `*-backend` as separate repos) there is **no shared
filesystem**: the provider repo does not have the pact the consumer repo just generated. Today
`contracts/README.md` resolves this by committing a **duplicate copy into both repos** and calling the
divergence an "accepted cost." That cost is not cosmetic — it is a **false-green**: the provider can pass
`provider-verification` against a **stale** contract while actually being incompatible with the current
consumer, which defeats the entire point of consumer-driven contracts.

**End result:** A decided, documented **$0 multi-repo contract-transport strategy** for the teaching
project that does **not** silently drift, plus an honest framing of the **free self-hosted (Dockerized) OSS
Pact Broker** as the production answer — both kept as **opt-in / labelled**, never on the default `$0`
zero-infra path. `contracts/README.md` is revised to match.

**Decided end-state (all open questions resolved — see `## Resolved decisions`):**
- **$0 multi-repo default = consumer-CI-push.** Consumer CI commits the freshly-generated union pact
  **directly** (no PR gate) and **immediately** into the provider repo's `contracts/`; the provider verifies
  the just-pushed copy. Git-only, no standing infra, kills the stale-copy false-green, and keeps local offline
  provider-verification. Manual-duplicate is demoted to a labelled compromised fallback; CI-artifact hand-off
  is documented but not default (loses offline verify). **Known limitation:** breakage is caught *after the
  fact* in the provider's verification run, not at the consumer's pre-merge gate — the documented reason to
  graduate to the opt-in broker's `can-i-deploy`.
- **Pact Broker = the labelled opt-in production answer**, taught as **persistent** (ephemeral = demo only),
  including a **`can-i-deploy`** lesson — never on the $0 default path.
- **Cross-repo auth = scoped GitHub App** (fine-grained PAT as the simpler alternative), surfaced as the one
  explicit setup step the $0 multi-repo mode needs.
- **This is forward-looking:** the repo is a monorepo today, so the plan *designs in* the target transport
  for when multi-repo scaffolding lands rather than retrofitting it.

**Effort:** as scoped, this is **mostly a documentation/design task** (easy–medium): the committed deliverable
is the `contracts/README.md` revision plus confirming the false-green failure mode. The genuinely harder work
— the consumer-CI-push CI workflow + cross-repo auth, and any broker infra — is **gated behind multi-repo
scaffolding actually existing** and is deferred to that point (Step 6), so it is not part of doing this now.

**Execution risk & scope (what's safe to do now):**
- **Low-risk, execute now — Steps 1, 4, 5 (documentation).** Confirm the false-green, evaluate CI-artifact,
  and revise `contracts/README.md`. Single markdown file, no code, doesn't compile, reversible. One caveat:
  `contracts/README.md` is live teaching material that the scaffolder copies into generated repos, so the
  default-mechanism change is real guidance — but it is framed as the *forward-looking target*, which is
  honest since multi-repo does not exist yet.
- **Premature / blocked — defer Steps 2, 6.** consumer-CI-push CI + scaffolder support need separate
  `*-frontend`/`*-backend` repos and cross-repo auth that do not exist today (confirmed monorepo). Design only;
  do not wire or "test" plumbing that can't be exercised.
- **Do not run locally — Step 3 (broker spike).** Standing up Postgres + the OSS broker via Docker hits the
  no-self-initiated-local-docker rule and local Docker/Testcontainers is already flaky on this machine; it is
  opt-in anyway. Keep it as a write-up.
- **Recommended scope for `/execute-plan` now:** Steps 1, 4, 5, 5a (README revision + local verify); leave 2/3/6 deferred.

## Outcomes

- A chosen **default $0 multi-repo mechanism** that eliminates the false-green (recommendation:
  **consumer-CI-pushes-the-pact-to-the-provider-repo** — a git-only "poor-man's broker").
- A clear-eyed write-up of the **free Dockerized OSS Pact Broker** (`pactfoundation/pact-broker` + Postgres):
  what it gives you ($0 license, **`can-i-deploy` included**, full publish/verify/matrix/webhooks), and the
  **persistent-vs-ephemeral** catch (ephemeral docker-compose-in-CI = $0 + zero-standing-infra but throws
  away the cross-run history that justifies a broker; persistent = real value but standing infra someone
  runs/patches/backs-up).
- A ranked options table (consumer-CI-push / CI artifact / self-hosted OSS broker / PactFlow free) with the
  $0 and operational trade-offs explicit.
- `contracts/README.md` revised: drop "duplicate-and-accept-drift" as the recommended multi-repo mode;
  prefer the consumer-CI-push mechanism; frame the broker as the **multi-repo production answer**, not a
  generic upgrade.
- The teaching narrative preserved: monorepo-committed is **best for a monorepo** (not a budget
  consolation), and the broker-less multi-repo wall is used **deliberately** to motivate the broker.

## ▶ Next executable step (resume here)

Remaining steps are deferred (blocked on multi-repo scaffolding existing). No mechanical edits remain
for the current monorepo. Only deferred items below — see `## Deferred steps`.

## Deferred steps

- [ ] **Step 2 — Consumer-CI-push CI workflow + cross-repo auth.** ⏳ Deferred: blocked on separate
  `*-frontend` / `*-backend` repos existing (confirmed monorepo today). Design the workflow and
  cross-repo GitHub App auth at that time.
- [ ] **Step 3 — Spike the free Dockerized OSS broker.** ⏳ Deferred: opt-in only; local Docker flaky
  on this machine (no-self-initiated-local-docker rule). Keep as a write-up until broker lesson is built.
- [ ] **Step 6 — Scaffolder / CI follow-on.** ⏳ Deferred: blocked on multi-repo scaffolding existing.
  Capture as concrete edits or a further plan when `*-frontend` / `*-backend` scaffold lands.

## Resolved decisions

- **Default $0 multi-repo mechanism → consumer-CI-push.** It is the git-only option that best mimics the
  Pact Broker's pull model: the provider always verifies *the contract the consumer just produced*, which
  eliminates the false-green. It also keeps local provider-verification working (the pact physically lands in
  the provider repo, so the backend verifies offline). CI-artifact hand-off was rejected as default because
  it loses local offline verification; manual-duplicate is demoted to the explicitly-compromised fallback.
  Mainstream industry practice for this transport is actually a **Pact Broker** (publish + webhook + pull +
  `can-i-deploy`) — consumer-CI-push is the deliberate $0 approximation, and the broker is carried as the
  labelled opt-in production answer (see the broker decision below).

- **Dockerized OSS broker → persistent when adopted, but neither on the default path.** The default scaffold
  stays $0 + zero-infra (`feedback_templates_propagate_cost_to_students`), so no broker is wired in. When the
  broker *is* taught as the opt-in production answer it must be **persistent** — that is where `can-i-deploy`
  and the compatibility matrix actually live. An ephemeral CI-only broker throws away the cross-run history
  that justifies a broker, so it is documented as a demo, not a workflow.
- **Cross-repo push auth → scoped GitHub App (fine-grained PAT as the simpler alternative).** `GITHUB_TOKEN`
  cannot push to another repo by default; the mainstream cross-repo-automation choice is a scoped GitHub App
  (short-lived installation tokens). This is documented as the one explicit setup step the multi-repo $0 mode
  requires — surfaced, not hidden.
- **Scaffolder multi-repo support → forward-looking (confirmed).** The repo is a monorepo today
  (`system/multitier/{backend-java,frontend-react}` in one repo); `contracts/README.md` already describes the
  `*-frontend`/`*-backend` split as a target, but no separate repos exist. This plan therefore documents the
  *target* transport so it is designed-in when multi-repo scaffolding lands, rather than retrofitted.
- **Teach `can-i-deploy` → yes, inside the opt-in broker lesson.** It is the broker's headline payoff and the
  concrete reason to adopt a broker over the git mechanisms, so it belongs in the broker lesson rather than
  the $0 default path.
- **Consumer-CI-push delivery → direct commit, pushed immediately.** The consumer CI commits the freshly
  generated union pact **directly** into the provider repo's `contracts/` (no PR gate) and does so
  immediately on the consumer build — it does not wait for provider verification. This keeps the $0 mechanism
  simple and lockstep with the consumer.
- **Known limitation (the honest motivation for the broker): breakage is caught after the fact.** Because the
  consumer pushes immediately and has usually already merged, a breaking consumer change is detected in the
  **provider** repo's verification run *after* it lands — not at the consumer's gate before merge. The git
  mechanism has no pre-merge "is the provider compatible?" check. That pre-merge guarantee is exactly what a
  Pact Broker's **`can-i-deploy`** provides, so this limitation is documented as the deliberate reason to
  graduate to the opt-in broker (ties to the `can-i-deploy` decision above).

## Out of scope

- The narrow-integration suites themselves (owned by the cluster: `1801` / `1944` / `1957` / `1939` / `1941`).
- The `contract` → `provider-verification` suite rename (its own task, flagged in the meta-plan).
- Forcing any broker/infra onto the default build — the default stays monorepo-committed, $0, zero-infra.
