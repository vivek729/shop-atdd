# 2026-06-23 11:54 UTC — Component-level test suites via `gh optivem component test`

## TL;DR

**Why:** Today the commit-stage (component-level) tests — unit, narrow
integration, component, consumer-contract (Pact) — are run by hand-wired,
language-specific commands scattered across three places per component
(`vite.config.ts` excludes / npm scripts, Gradle source sets, and the workflow
step bodies). There is **no single source of truth** for "what test levels exist
and how to run them," and the **local default diverges from what CI runs** (local
`npm test` / `./gradlew test` exclude component+contract; CI runs them in a
separate non-gating job). This is the same class of problem `tests.yaml` already
solved for **system** tests.

**End result:** A declarative **`component-tests.yaml` per component** (mirroring
the existing `system-test/<lang>/tests.yaml` schema) plus a new
**`gh optivem component test`** command that runs selectable suites in-process —
no running system, no compose. One config drives **both** local dev and CI:

```bash
gh optivem component test run                     # all suites (default) — what CI runs
gh optivem component test run --suite unit        # just unit (fast inner loop)
gh optivem component test run --suite integration # narrow integration
gh optivem component test run --suite component
gh optivem component test run --suite contract    # consumer Pact
gh optivem component test run --suite unit --sample
gh optivem component test setup                   # setupCommands (npm ci / gradle warm)
```

CI pins the **full** run (bare `run` / `--suite all`); local `--suite`/`--component`
selection is a **convenience for feedback speed only** — it never weakens the gate.

## Motivation / value

- **Local↔CI equivalence by construction.** Both invoke the *same*
  `gh optivem component test run`. Kills "passes locally, blocked in CI" without
  per-language fiddling (no more editing `vite.config.ts` excludes *and* Gradle
  `check` wiring *and* the workflow step).
- **Single source of truth.** Add a suite once in the config → both local `all`
  and the CI gate pick it up automatically. No three-place wiring.
- **Uniform UX with system tests.** Same `--suite` / `--sample` / suite-group
  affordances developers already know from `gh optivem test`.
- **First-class, selectable test pyramid** at the component level — pedagogically
  valuable for an ATDD course.
- **Normalised vocabulary** across 3 languages (today: `npm test` vs
  `./gradlew test`; `test:component` vs `componentTest` — inconsistent per stack).

## Two axes

The CLI selects over **suite** (which level) × **component** (which codebase):

- A **multitier** config (`gh-optivem-multitier-java.yaml`) points at **two**
  components — `backend-java` *and* `frontend-react` — each with its own
  `component-tests.yaml`.
- `--suite <id>` **fans out across every component** in the active config by
  default (CI wants to gate everything).
- `--component <name>` narrows to one component for local iteration.
- **Monolith** configs have a single component, so the component axis is moot there.

## `component-tests.yaml` schema (mirrors `tests.yaml`)

Per-suite fields (familiar from `system-test/<lang>/tests.yaml`):
- `id`, `name` — how the user/CI selects and labels the suite
- `command` — the language-native runner invocation
- `path` (optional) — cwd relative to the config file (defaults to component root)
- `env` (optional) — extra env vars for the suite process
- `sampleTest` — canonical known-good test name for `--sample`
- `testReportPath` (optional) — where the runner drops the HTML report

New fields component-tests need that system-tests don't:
- `requiresDocker: true` — suite needs Docker (Java Testcontainers-Postgres). The
  runner fails fast with a clear message if Docker is absent, instead of a cryptic
  Testcontainers stack trace. (System tests express this via `EXTERNAL_SYSTEM_MODE`.)
- `pending: true` — the level exists in the taxonomy but has no tests yet (replaces
  the `if: false` "not yet implemented" stub steps). `--suite <pending>` prints a
  "not implemented yet" notice and passes; `--suite all` skips it without failing.

Shared mechanics: `setupCommands`, `suiteGroups`, `testFilter` (`<test>` placeholder
for `--test <name>`) — same semantics as `tests.yaml`.

### Frontend (`system/multitier/frontend-react/component-tests.yaml`)

```yaml
setupCommands:
  - name: Install Dependencies
    command: npm ci
suites:
  - id: unit
    name: Unit
    command: npm test                    # harness.test.tsx — fast, no network
    sampleTest: "test harness"
  - id: integration
    name: Narrow Integration
    pending: true                        # no narrow-integration suite yet
  - id: component
    name: Component
    command: npm run test:component       # order.component.test.tsx
    sampleTest: "NewOrder — client-side validation"
  - id: contract
    name: Consumer Contract (Pact)
    command: npm run test:pact            # in-process Pact mock, writes contracts/
    sampleTest: "places an order (POST /api/orders -> 201)"
suiteGroups:
  all: [unit, integration, component, contract]
```

### Backend Java (`system/multitier/backend-java/component-tests.yaml`)

```yaml
setupCommands:
  - name: Pre-warm Gradle
    command: ./gradlew --version
suites:
  - id: unit
    name: Unit
    command: ./gradlew test
    sampleTest: "*OrderServiceTest*"      # confirm an actual unit test name
  - id: integration
    name: Narrow Integration
    pending: true
  - id: component
    name: Component
    command: ./gradlew componentTest --tests '*Component*'   # see OQ-Java-granularity
    requiresDocker: true                  # Testcontainers-Postgres
  - id: contract
    name: Consumer Contract (Pact)
    command: ./gradlew componentTest --tests '*Pact*'
    requiresDocker: true
suiteGroups:
  all: [unit, integration, component, contract]
```

## CLI surface (`gh optivem component test`)

Sibling of `gh optivem test`, NOT folded into it — component-level tests are part
of the **system tier** (in-process, white-box, no running system), whereas
`gh optivem test` drives the **test tier** against an *already-running, deployed*
system (compose, channels, external-system stub/real). Keeping them separate
preserves the inside-vs-outside-the-SUT boundary the course teaches.

- `run [--suite <id>] [--component <name>] [--sample] [--test <name>]` — bare `run`
  = all suites, all components. Lighter runner than `test run`: no compose, no
  system start/stop; just `cd <component-path>` → run the suite `command`.
- `setup [--component <name>]` — run `setupCommands` (npm ci / gradle warm).
- `compile [--component <name>]` — optional, mirrors `gh optivem test compile`.

## Guardrail (non-negotiable)

**CI always runs the full set** (bare `run` / `--suite all`, all components),
hardcoded in the commit-stage workflow. The developer's local `--suite`/`--component`
selection is a **fast-feedback convenience** — freedom to run *less* locally, never
to make *CI* run less. A gate you can opt out of is not a gate. Because the CI gate
== `suiteGroups.all`, **edits to the `all` group are gate changes** and must be
reviewed like a workflow edit.

## Naming: "contract" is overloaded — disambiguate

`tests.yaml` already has `contract-*` suites meaning **external-system** contract
tests (clock/erp/tax, stub-vs-real). The component-level `contract` suite means
**consumer Pact** (frontend↔backend). Same word, different boundary. Keep the
suite `id: contract` but always label it **"Consumer Contract (Pact)"** in `name:`
and CLI output so students don't conflate the two.

## Sequencing vs the gating plan (`20260622-0916`)

This is a **`gh optivem` (Go) tool change** — the config alone executes nothing
until the runner reads it. Do **not** block the already-decided gating work on it:

1. **Now (plan `0916`):** gate component+contract with **native commands** in the
   commit-stage workflows (`npm run test:component` / `test:pact`,
   `./gradlew componentTest`). Small, no tool change.
2. **Later (this plan):** build `component-tests.yaml` + the `gh optivem component
   test` runner, then **replace** the native workflow steps with
   `gh optivem component test run`. The native command strings written in step 1
   become the exact `command:` values in the config — nothing is wasted.

## Steps

- [ ] **Step 1 — schema.** Finalise the `component-tests.yaml` schema (fields above,
  incl. `pending`, `requiresDocker`, two-axis selection). Decide config location:
  co-located per component (recommended, mirrors `tests.yaml`) vs a central block.
- [ ] **Step 2 — Go runner.** Implement `gh optivem component test run/setup` in the
  `gh optivem` tool: discover each component's `component-tests.yaml` via the
  `system.{backend,frontend}.path` / monolith paths in `gh-optivem-*.yaml`; resolve
  `--suite` × `--component`; bare `run` = fan out all suites × all components;
  honor `pending` (skip+notice) and `requiresDocker` (fail-fast preflight).
- [ ] **Step 3 — pilot configs.** Author `component-tests.yaml` for
  `frontend-react` + `backend-java` (the two with real tests). Verify each
  `--suite` and bare `run` locally (frontend needs nothing; Java needs Docker).
- [ ] **Step 4 — roll out.** Add configs for monolith ×3 and backend-dotnet/ts
  (mostly `pending` suites until real tests exist).
- [ ] **Step 5 — migrate CI.** Replace the native commit-stage steps (from plan
  `0916`) with `gh optivem component test run` pinned to all suites. Remove the
  scattered `if: false` stubs — `pending` in the config now holds those slots.
- [ ] **Step 6 — docs.** Update READMEs / pipeline diagram: the four levels are a
  declarative, selectable suite set; document the CI-runs-`all` guardrail and the
  consumer-vs-external "contract" distinction.

## Decisions (from this conversation, 2026-06-23)

1. **Gate the component + contract suites** (carried from plan `0916`).
2. **Local must be equivalent to CI** — the same suites that gate must be runnable
   locally with one command.
3. **Adopt a declarative per-component test config** (`component-tests.yaml`)
   mirroring `tests.yaml`, driven by a new `gh optivem component test` command.
4. **Bare `run` = all suites** (default); CI pins the full run.
5. **Local suite selection is convenience-only; CI always runs `all`** (guardrail).
6. **Separate command namespace** (`component test`) from system `test` — different
   tier (in-process system tier vs deployed test tier).

## Open questions

- **OQ-location** — co-locate `component-tests.yaml` in each component dir
  (recommended) or a central registry block? *Recommend:* co-located, mirrors
  `tests.yaml`.
- **OQ-Java-granularity** — `componentTest` is one Gradle task covering component +
  Pact. Split into two suites via `--tests` filter (matches the diagram's separate
  boxes, enables `--suite component` vs `--suite contract`) or one `component`
  suite? *Recommend:* split — the config makes it cheap and declarative.
- **OQ-unit-filter (frontend)** — `npm test` currently collects only top-level
  `src/test/*.test.tsx`. If component/contract dirs are later un-excluded for some
  other reason, the `unit` suite needs an explicit unit-only filter to avoid overlap.
- **OQ-default-naming** — `gh optivem component test` vs `gh optivem system test`
  vs `gh optivem ct`. *Recommend:* `component test` (most descriptive).
- **OQ-provider-pact** — provider-side Pact verification (Java backend verifying the
  consumer contract in `contracts/`) — is it a fifth suite here, or does it belong
  to a different stage? Out of scope unless confirmed.
- **OQ-local-default-mechanism** — independent of this config, should the *native*
  local defaults be made equivalent too — i.e. remove the `src/test/component/**` +
  `src/test/pact/**` exclusions in `frontend-react/vite.config.ts` and wire
  `componentTest` into Gradle `check` so `npm test` / `./gradlew build` themselves
  run everything? Or is equivalence delivered *solely* through
  `gh optivem component test run` (leaving the raw native defaults fast/Docker-light)?
  Affects whether the "opt-in / Docker-light default" comments in the configs,
  `build.gradle`, and the frontend README get reframed. (Carried from plan `0916`.)
- **OQ-fold-into-local-default** — if the native defaults are NOT changed, devs who
  run bare `npm test` / `./gradlew test` (instead of the `gh optivem` command) still
  won't run what CI gates on. Accept that gap (equivalence only via the `gh optivem`
  entrypoint), or fold the suites into the native defaults so every local path
  matches the gate? *Recommend:* decide explicitly — the split is surprising.
  (Carried from plan `0916`.)

## Risks

- **Tool-change scope** — this is Go work in `gh optivem`, larger than a YAML/
  workflow edit. Mitigated by sequencing (native gating first, migrate later).
- **Gate-definition drift** — since CI's gate == `suiteGroups.all`, a careless edit
  to that group silently changes what gates. Mitigation: treat `all` edits as
  reviewed changes; the guardrail note documents this.
- **Docker in the gate (Java)** — `requiresDocker` suites add Testcontainers startup
  to every gated run; a flaky start blocks publish. (Ran clean in 38s locally on
  2026-06-23.) Carried risk from plan `0916`.
- **"Contract" confusion** — consumer-Pact vs external-system contract. Mitigated by
  the labelling rule above.
