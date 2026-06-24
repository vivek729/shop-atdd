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

Decision/design plan — no mechanical edits yet. Run `/refine-plan` on this file to settle the Open
questions (esp. the default $0 multi-repo mechanism and ephemeral-vs-persistent broker), then `/execute-plan`
to revise `contracts/README.md` (and any scaffolder/CI follow-on).

## Steps

- [ ] **Step 1 — Confirm the failure mode.** Reproduce/confirm that the current "duplicate copy in both
  repos" model lets the provider verify a stale contract (false-green) when the consumer changes and the
  copy isn't re-synced. Document it as the motivating problem.
- [ ] **Step 2 — Evaluate the $0 git-transport mechanism (recommended default).** Design
  consumer-CI-pushes-pact-to-provider-repo: consumer CI generates the union pact, opens a PR / commits it to
  the provider repo's `contracts/`, provider CI verifies on that PR. Settle the cross-repo auth (PAT vs
  GitHub App token) and the local-dev story (backend verifies the last-pushed copy offline).
- [ ] **Step 3 — Spike the free Dockerized OSS broker.** Stand up `pactfoundation/pact-broker` + Postgres
  via docker-compose; publish the frontend pact, verify from the backend, try `can-i-deploy`. Record
  ephemeral (per-CI-run) vs persistent (standing service) trade-offs and what each costs.
- [ ] **Step 4 — Evaluate the CI artifact / release-asset hand-off** as the alternative $0 CI-only option;
  note it loses local `provider-verification`.
- [ ] **Step 5 — Decide + document.** Pick the default $0 multi-repo mechanism; frame the broker as the
  labelled opt-in production answer. Revise `contracts/README.md` accordingly.
- [ ] **Step 6 — Scaffolder / CI follow-on (if needed).** If the chosen mechanism needs gh-optivem
  scaffolder or CI-workflow support (e.g. the cross-repo push workflow), capture that as concrete edits or a
  further plan — do not bake broker infra into the default path.

## Open questions

Each carries a recommendation (`→`).

- **Default $0 multi-repo mechanism?** consumer-CI-push vs CI-artifact vs manual-duplicate.
  → **Recommend consumer-CI-push** — git-only, $0, no standing infra, and (unlike manual duplication)
  eliminates the false-green because the provider always verifies the contract the consumer just pushed.
  Manual duplication stays documented only as the explicitly-compromised fallback.
- **Dockerized OSS broker: ephemeral or persistent — or neither, for the teaching default?**
  → **Recommend: neither on the default path.** Present the persistent self-hosted broker as the
  **opt-in production answer** (it's where `can-i-deploy` and the compatibility matrix actually live); call
  out that an ephemeral CI-only broker is a demo, not a workflow. Keep cost/infra off the default per
  `feedback_templates_propagate_cost_to_students`.
- **Cross-repo push auth?** PAT vs GitHub App vs `GITHUB_TOKEN` (can't push to another repo by default).
  → **Recommend: a scoped GitHub App / fine-grained PAT**, documented as the one piece of setup the
  multi-repo $0 mode requires — and surfaced as such (not hidden).
- **Does the gh-optivem scaffolder support a multi-repo layout today, or is this forward-looking?**
  → **Confirm during Step 1.** If multi-repo scaffolding isn't built yet, this plan documents the *target*
  transport so it's designed-in when it lands, rather than retrofitted.
- **Teach `can-i-deploy`?** It's the broker's headline feature and free on the OSS broker.
  → **Recommend: yes, as part of the opt-in broker lesson** — it's the concrete payoff that explains *why*
  you'd adopt a broker over the git mechanisms.

## Out of scope

- The narrow-integration suites themselves (owned by the cluster: `1801` / `1944` / `1957` / `1939` / `1941`).
- The `contract` → `provider-verification` suite rename (its own task, flagged in the meta-plan).
- Forcing any broker/infra onto the default build — the default stays monorepo-committed, $0, zero-infra.
