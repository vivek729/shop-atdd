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

### Target end-state (open questions resolved 2026-06-23)

When the work lands:

- **One aggregate command = the gate.** `gh optivem component test run` runs every
  suite × every component and is the **single command that matches CI** — the repo's
  cross-language equivalent of `mvn verify` / `./gradlew check`. CI hardcodes the full
  run; local devs may run *less* (`--suite`, `--component`) for speed, never to weaken
  the gate.
- **Native defaults stay fast and Docker-light.** Bare `npm test` / `./gradlew test`
  remain the quick, no-Docker unit inner loop — `vite.config.ts` keeps excluding
  `src/test/component/**` + `src/test/pact/**`, and `componentTest` stays out of
  Gradle's default `check`. This follows industry norms (fast default + explicit heavy
  suites + one aggregate). **The gap is intentional and documented:** bare native
  commands run less than CI gates on; the command that matches CI is
  `gh optivem component test run`.
- **Every suite is selected by its own explicit positive filter** — never by
  subtraction — in **every** component that splits test types (frontend-react, all
  three backends, monolith ×3). No level is "everything minus the others"; a refactor
  can't silently fold one level into another.
- **Command is `gh optivem component test`** (`run` / `setup` / `compile`) — a
  sibling of `gh optivem test`, deliberately separate (in-process system tier vs
  deployed test tier).
- **`contract` always labels as "Consumer Contract (Pact)"** to keep it distinct from
  `tests.yaml`'s external-system contract suites. **Provider-side Pact verification is
  out of scope** here (cross-component dependency + provider state) — deferred to a
  separate plan.
- **What's observably unchanged:** the system-test path (`gh optivem test`, compose,
  channels), students' default `npm test` / build speed and Docker-free inner loop,
  and the existing OPT-IN comments. What changes: a declarative `component-tests.yaml`
  per component, a new `gh optivem component test` runner, and CI commit-stage steps
  swapped from scattered native commands to the single aggregate.

## ▶ Next executable step (resume here)

**Steps 1–6 are DONE.** All `component-tests.yaml` configs are in place (5 new +
2 pilot), all 7 commit-stage workflows updated (stubs removed, `component-tests`
job added, `summary` gating updated), and `docs/pipeline/commit-stage.md` +
`backend-java/README.md` updated.

**Verified intact post-merge 2026-06-23:** re-audited after a merge conflict — all
7 configs, all 7 commit-stage workflows (job + setup/run steps + `needs` gating, no
`if: false` stubs), and both docs are present; no conflict markers; tree clean
(committed in `ef1b3c7c`). Nothing was dropped by the merge.

Only Step 7 (deferred) remains — see below. No further executable steps.

## Manual verification — `gh optivem component test` on `optivem/shop`

After the pilot configs land (Step 3), verify the runner from the **shop repo root**
(the runner discovers components + their `component-tests.yaml` from the active
`gh-optivem.yaml`). PowerShell:

```pwsh
$env:GH_OPTIVEM_CONFIG = "gh-optivem-multitier-java.yaml"

# 1. Discovery + listing — should show backend (java) + frontend (typescript)
#    and each component's suite ids (integration marked "(pending)").
#    NB: discovery is the `run --list` flag, not a `list` subcommand.
gh optivem component test run --list

# 2. One-time setup (npm ci / gradle warm) for both components.
gh optivem component test setup

# 3. Fast inner loop — unit only, no Docker. Run across both components…
gh optivem component test run --suite unit
#    …or narrow to one component:
gh optivem component test run --suite unit --component frontend

# 4. Sample smoke — one known-good test per selected suite.
gh optivem component test run --suite unit --sample

# 5. Docker-backed Java suites (needs Docker Desktop running) — the runner
#    fails fast with a clear message if Docker is absent.
gh optivem component test run --suite component --component backend
gh optivem component test run --suite contract  --component backend

# 6. THE GATE — bare run = every suite × every component (what CI pins).
#    Pending suites print a notice and pass; Docker suites need a daemon.
gh optivem component test run
```

What to confirm: `run --list`/discovery names components as `backend`/`frontend`; the
`integration` (pending) suite prints "not implemented yet — skipping" and never
fails; a Docker suite with the daemon stopped fails fast with the "requires Docker"
message (not a Testcontainers stack trace); bare `run` exercises all four levels
across both components and the summary table groups by component.

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

`--component` is **optional and narrowing**, not the source of "which component."
The active `gh-optivem-*.yaml` already declares the components (via
`system.{backend,frontend}.path` / monolith path), so the runner always knows the
full set; omitting `--component` fans out to **all** of them — exactly mirroring how
omitting `--suite` runs all suites. Bare `run` (neither flag) = every suite × every
component = the CI gate. Making `--component` mandatory would break that load-bearing
default and the local↔CI equivalence. For monolith configs there is one component, so
the flag is moot. The tree output groups by component so a multi-component run is
visibly labelled.

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

- [ ] **Step 7 — `component test compile` (optional)** — ⏳ Deferred: `run` + `setup`
  landed in `gh-optivem`; `compile` was left out because its semantics for
  in-process component tests are unclear (the language build is already covered by
  `setup` + the suite command). Revisit only if a concrete need appears.

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

## Resolved decisions

- **OQ-local-default-mechanism + OQ-fold-into-local-default → fast native default +
  `gh optivem component test run` as the single aggregate.** Local↔CI equivalence is
  delivered through the `gh optivem` command, **not** by making the everyday native
  defaults run everything. This matches industry best practice (fast default +
  explicitly-named heavy suites + one aggregate that runs all — e.g. Maven's
  `mvn test` unit-only vs `mvn verify` full; Gradle source sets vs `check`).
  - **Keep native defaults fast/Docker-light.** `vite.config.ts` continues to exclude
    `src/test/component/**` + `src/test/pact/**`; `componentTest` stays out of Gradle's
    default `check`/`build`. Bare `npm test` / `./gradlew test` remain the quick,
    no-Docker inner loop, and the OPT-IN layer for students who never opted in is
    preserved. The existing "opt-in / Docker-light default" comments stay valid.
  - **`gh optivem component test run` is the repo's "verify".** It runs the full set
    (all suites × all components) and is the single documented command that matches
    the CI gate — the cross-language equivalent of `mvn verify` / `./gradlew check`.
    `--suite unit` is the fast inner loop within it.
  - **The gap is intentional and must be documented.** Bare native commands run *less*
    than CI gates on. The docs (Step 6) must state plainly that the command which
    matches CI is `gh optivem component test run`, so no one assumes `npm test` /
    `./gradlew build` reflects the gate.

- **OQ-unit-filter → symmetric explicit filters for every suite, in every component
  that splits test types.** This is a cross-cutting rule, not a frontend-only fix:
  **everywhere** the taxonomy splits levels — frontend-react, backend-java /
  backend-dotnet / backend-typescript, and the monolith ×3 — each suite
  (`unit`/`integration`/`component`/`contract`) is selected by its **own explicit
  positive filter**, never by subtraction/leftover.
  - **Frontend:** today `component`/`contract` use positive includes
    (`src/test/component`, `src/test/pact`) while `unit` is the leftover after
    `vite.config.ts` excludes those dirs — asymmetric and fragile (a future
    un-exclude silently bloats `unit`). Fix: give `unit` an explicit positive include
    (e.g. `src/test/unit/**`, or a top-level-only glob like `src/test/*.test.tsx`
    matching the current `harness.test.tsx` layout) so its `command:` collects unit
    specs only, regardless of exclude-config drift.
  - **Java:** `test` and `componentTest` are already separate Gradle source sets, and
    `component`/`contract` already split by `--tests '*Component*'` / `'*Pact*'`. The
    `unit` suite (`./gradlew test`) must likewise resolve to unit specs only — keep
    the source-set / `--tests` boundary explicit so no level is defined by exclusion.
  - **.NET / TS backends + monolith ×3:** apply the same rule as their real suites
    land (most are `pending:` initially) — when a level gets tests, it gets its own
    positive filter (test-name pattern, source set, or path glob), symmetric with the
    others. No suite is "everything minus the others."

  In each `component-tests.yaml`, the per-suite filter is the **authoritative
  boundary** for that level; note this in the config so a refactor can't silently
  fold one level into another.

- **OQ-default-naming → `gh optivem component test`.** Most descriptive; mirrors
  the `gh optivem test` / `tests.yaml` pairing and names the tier explicitly.
  Rejected `system test` (collides with `gh optivem system start/stop` and blurs the
  in-process-vs-deployed boundary) and `ct` (cryptic; reads as "contract test",
  worsening the contract-overload confusion). Subcommands: `run` / `setup` /
  `compile`.

- **OQ-provider-pact → out of scope; deferred.** This plan covers only the four
  in-process, per-component suites. Provider-side Pact verification depends on an
  artifact produced by *another* component (the consumer contract under `contracts/`),
  needs provider-state setup, and introduces cross-component ordering — a different
  shape that breaks the no-cross-component-dependency model. Tracked as a follow-up:
  decide its stage (likely an integration/acceptance concern against a running
  provider, not a single component in isolation) in a separate plan.

- **OQ-location → co-located per component.** Each component carries its own
  `component-tests.yaml` in its dir (e.g. `system/multitier/frontend-react/`),
  mirroring `system-test/<lang>/tests.yaml`. The runner discovers it via the
  `system.{backend,frontend}.path` / monolith paths in `gh-optivem-*.yaml`. Keeps
  the config with the code it tests and matches the existing convention; no central
  registry coupling unrelated components into one edit surface.

- **OQ-Java-granularity → split into `component` + `contract`.** Two suites driven
  by `--tests` filters (`componentTest --tests '*Component*'` and
  `componentTest --tests '*Pact*'`), as already drafted in the Java config above.
  Matches the diagram's separate boxes and the frontend's 4-level pyramid, and
  enables `--suite component` vs `--suite contract` for Java. Caveat: depends on the
  `*Component*` / `*Pact*` test-name conventions staying accurate — note this in the
  config so a rename can't silently drop a suite's tests.

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
