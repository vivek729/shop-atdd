# 2026-06-24 12:03 UTC — Compile the component **and all its test sources** up front, ideally via `gh optivem`

## TL;DR

**Why:** The commit-stage `Compile Code` step compiles main + the **unit** test source only.
In backend-java the `integrationTest` / `componentTest` source sets are **not** compiled up
front — they compile lazily when their suite task runs, so a *compilation* error in
component/integration test code surfaces mid-suite (after the slow Docker spin-up) instead of
in `Compile Code`. Separately, compilation is still a **raw per-language command** baked into
each workflow's YAML, unlike test execution which plan 0916 routed through
`gh optivem component test run --suite` with `component-tests.yaml` as the single source of
truth. Two gaps: a **fail-fast coverage gap** (mostly Java) and a **single-source-of-truth
gap** (all three languages).

**End result:** Every commit-stage `run` job compiles **all** of a component's production +
test sources *before* the gating suites, failing fast on any compile error ahead of the Docker
suites — and (if Option B) it does so via a single `gh optivem component test compile` verb
reading the compile command from each `component-tests.yaml`, uniform across Java/.NET/TS, so
the suite/compile command mapping has one home.

## Problem

Plan 0916 moved suite **execution** into the gating `run` job via the CLI. Compilation was left
as-is — a raw build-tool call per language:

| Workflow | `Compile Code` today | Compiles test sources? |
|---|---|---|
| `*-java` (monolith + backend) | `./gradlew compileJava compileTestJava` | **unit only** — `integrationTest`/`componentTest` source sets compile lazily at suite time (build.gradle:31–46) |
| `*-dotnet` | `dotnet build` | whole solution incl. test projects → **likely all** (verify the `.slnx` includes the component/contract test projects) |
| `*-typescript` | `npm run build` (`tsc`) | src only? test files may be type-checked only at test time → **verify per tsconfig** |
| `frontend-react` | (verify) | (verify) |

Consequences:
- **Slow feedback on a test-compile error (Java):** a typo in a `componentTest` class is not
  caught by `Compile Code`; it fails during `Run Component Tests`, after Testcontainers/WireMock
  startup. Fail-fast is lost for exactly the slowest suites.
- **No single source of truth for compilation:** the compile command is duplicated/forked across
  7 workflows and 3 languages, drifting independently of `component-tests.yaml`.

## Goal

1. **Coverage:** `Compile Code` (or a new compile step) compiles **every** source set/project a
   component's suites need, so all compile errors surface before any suite runs.
2. **(Option B) Single source of truth:** the compile command lives in `component-tests.yaml`
   and is invoked via `gh optivem component test compile [--component <c>]`, mirroring
   `setup` / `run`.

## Options considered

**Option A — widen the raw gradle/tsc step (per workflow, no CLI change).**
- Java: `./gradlew testClasses integrationTestClasses componentTestClasses` (compiles main +
  all three test source sets) in `Compile Code`.
- TS: ensure `npm run build` (or an added `tsc --noEmit` over the test tsconfig) covers test
  files.
- .NET: confirm `dotnet build` already covers the test projects (probably a no-op).
- *Pro:* tiny, immediate, no CLI work. *Con:* language-specific strings re-duplicated in YAML;
  no single source of truth; drifts from `component-tests.yaml`.

**Option B — CLI owns compile (single source of truth).**
- Add a `compileCommands:` (list) or `buildCommand:` field to each `component-tests.yaml`, and a
  `gh optivem component test compile` verb that runs it per selected component (sibling to
  `RunSetup`).
- Workflows replace the raw `Compile Code` body with
  `gh optivem component test compile --component <c>` (monolith: no `--component`).
- *Pro:* uniform across languages, one home for the compile command, matches the 0916
  philosophy. *Con:* a `gh-optivem` repo change + release before the workflows can use it.

**Option B is the better long-term fit; Option A is the quick fail-fast win.** A viable path is
**A first** (close the Java coverage gap now) then **B** (fold compile into the CLI) — but doing
both means touching each workflow twice. See OQ4 (coordination with the 0916 wave).

## Steps

### Phase 0 — Decide & verify reality (do first)
- [ ] Resolve **OQ1–OQ4**.
- [ ] Verify the per-language coverage claims: does `dotnet build` compile the component/contract
  test projects (inspect `MyCompany.MyShop.Backend.slnx`)? does the TS `npm run build` /
  tsconfig include the component/integration test files? Record findings in this plan.

### Phase 1 — Coverage (Option A, or the A-part of A→B)
- [ ] **Java (all 4 java commit stages):** widen `Compile Code` to compile every test source set
  (`./gradlew compileJava compileTestJava compileIntegrationTestJava compileComponentTestJava`,
  or the `*Classes` task form). `actionlint` each.
- [ ] **TS / .NET:** add the minimal compile coverage only where Phase 0 proved a gap.

### Phase 2 — (Option B, only if chosen) CLI compile verb
- [ ] `gh-optivem`: add the `component-tests.yaml` compile field + `component test compile` verb
  + tests (mirror `RunSetup` in `internal/build/componenttest/run.go`). Cut a release.
- [ ] Populate `compileCommands` in all 7 `component-tests.yaml`.
- [ ] Replace each workflow's raw `Compile Code` body with the CLI call.

### Phase 3 — Verify
- [ ] `actionlint` every changed workflow; one green CI run per workflow (Docker suites can't be
  verified locally — [[project_local_testcontainers_blocked]]).

## Decisions
1. **Compilation must fail fast before the gating suites** — all test sources compile in (or
   before) the gating block, ahead of the slow Docker suites.

## Resolved decisions
- **OQ1 → Option B only.** Skip the interim raw widening (Option A); go straight to the CLI
  compile verb reading from `component-tests.yaml`. *Rationale:* the Java fail-fast gap has been
  open all along — no urgency justifies a throwaway raw `…Classes` step that B immediately
  replaces. B-only gives single source of truth from day one and edits each of the 7 workflows
  exactly once (vs. twice for A→B). The one tradeoff — workflows can't adopt the verb until a
  gh-optivem release ships it — is a sequencing matter, not a blocker (the release was needed for
  B regardless).

  **Update (2026-06-24): the "single wave" coordination is moot.** Plan 0916's gating wave has
  already shipped and the plan was deleted (commit `9d6f5caf`, "all phases landed"). The 7
  commit-stage workflows have therefore *already* been edited once (for gating), so adopting the
  CLI compile verb is now unavoidably a **second** edit to the same compile/test region. That is a
  sunk cost, not a blocker — B remains the chosen path; it simply no longer rides along with 0916.
  See revised OQ4.

- **OQ2 → Reuse the existing compile architecture; do NOT add a `compileCommands` field to
  `component-tests.yaml`.** Codebase check (2026-06-24) showed `gh optivem` already has a compile
  surface whose per-language command is centralized in `internal/build/compiler/commandsFor(lang)`
  — *not* stored as a YAML string (`system compile` / `test compile` / bare `compile` all dispatch
  from there). The Java gap is literally that function returning `compileJava compileTestJava`
  (main + unit only). So both plan-OQ2 options were inconsistent: a `compileCommands` list in
  `component-tests.yaml` (or folding it into `setupCommands`) would create a **second** home for
  the compile command, the opposite of single-source-of-truth. *Resolution:* (a) extend the
  `compiler` package so it also compiles the component's integration/component test source sets,
  and (b) expose it as a **tier-scoped compile verb** — `gh optivem component-test compile`, a
  sibling of `component-test run`/`setup` and parallel to `system compile` / `system-test compile`.
  The verb's final (symmetric) name comes from the naming follow-up
  [[20260624-1221-symmetric-gh-optivem-tier-noun-taxonomy]]; land that rename first (OQ4/OQ-C) so
  the workflows are rewritten to the final verb once. Then (c) route the 7 workflows' raw
  `Compile Code` step to the CLI compile verb, closing the single-source gap by *using* the
  existing compile home rather than inventing a new one.

## Open questions
- **OQ3 — Java task form.** `compile*Java` tasks vs `*Classes` tasks (the latter also process
  resources)? *Recommend:* `*Classes` (matches what the suite tasks actually depend on).
- **OQ4 — ~~Coordinate with plan 0916's propagation wave.~~ RESOLVED / MOOT.** 0916 has already
  shipped (all phases landed; plan deleted in commit `9d6f5caf`), so its gating steps are already
  in the 7 commit-stage workflows. The "edit each workflow once by folding compile into the 0916
  wave" optimization is no longer available — adopting the CLI compile verb is a standalone second
  edit to those workflows. No coordination remains; proceed with Option B independently once the
  gh-optivem release ships. [[feedback_plan_over_parallel_tickets]]
- **OQ5 — `frontend-react`.** Confirm whether the frontend even has a separable compile step and
  test-source coverage gap; it may be out of scope.

## Risks
- **CLI release coupling (Option B):** workflows can't adopt the compile verb until a gh-optivem
  release ships it — sequence the release ahead of the workflow edits.
- **Double-edit churn:** the 7 workflows were already edited once by 0916's (now-shipped) gating
  wave, so adopting the CLI compile verb is a second edit regardless (the fold-into-0916 option is
  gone — see OQ4). Doing A *then* B would add a third. Going straight to B (OQ1) keeps it to that
  one remaining edit.
- **Over-compiling:** widening Java compile adds a little wall-clock, but it's strictly cheaper
  than discovering the same error after Testcontainers startup.

## ▶ Next executable step (resume here)

This plan is **draft, awaiting refinement** — OQ1, OQ2, and OQ4 are now resolved (Option B;
reuse the existing `compiler` package; 0916 coordination is moot). The remaining open questions
are **OQ3** (Java task form) and **OQ5** (`frontend-react` scope), plus the Phase 0 per-language
coverage verification. The hard blocker before any workflow edit is sequencing: land the verb
rename in [[20260624-1221-symmetric-gh-optivem-tier-noun-taxonomy]], then ship the gh-optivem
compile verb release, then route the 7 workflows to it. Plan 0916 (gating, same workflows) has
already shipped and been deleted — no longer a live dependency.
