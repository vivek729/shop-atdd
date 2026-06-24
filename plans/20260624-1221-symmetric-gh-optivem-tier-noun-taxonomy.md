# 2026-06-24 12:21 UTC — Symmetric `gh optivem` tier taxonomy (kill ambiguous `test` and ambiguous `compile`)

🤖 **Picked up by agent** — `Valentina_Desk` at `2026-06-24T13:22:29Z`

## TL;DR

**Why:** The `gh optivem` command surface names its two test tiers on two different
patterns, and its compile verb is partly bare. Both are ambiguity sources:

- **Ambiguous `test`:** the *system-test* tier is the bare top-level `gh optivem test`,
  while the *component-test* tier is the nested `gh optivem component test`. Two test
  tiers, two naming patterns — a reader can't tell from `test` alone which tier they're in.
- **Ambiguous `compile`:** `gh optivem compile` is a bare verb that *walks* tiers
  (`system compile` then `test compile`). With a third tier (component tests) about to get
  its own compile, a bare `compile` no longer says *what* it compiles.

**End result:** Each tier is addressed by a **noun**; every scoped action is a tier-scoped
verb; and the bare top-level `compile` / `test` survive — redefined as unambiguous **"for-all"
aggregate verbs** that span every tier. A reader always knows the tier from the noun, and the
bare word always means "everywhere". (The noun set spans two axes, not one flat list — see
**Goal** / **OQ-F**.)

```
# tier nouns — scoped
gh optivem system          build | start | status | stop | clean | compile
gh optivem system-test     setup | run | compile
gh optivem component-test  setup | run | compile

# aggregate verbs — for ALL tiers
gh optivem compile   # compile every tier: system (prod+unit) + component-test source sets + system-test project
gh optivem test      # run every test tier in pyramid order: component-test suites → system start →
                     #   system-test run → system stop (fail-fast). --assume-running skips start/stop for CI.
```

`test` is no longer a *tier* (the source of the old ambiguity) — it is the run-everything verb;
the tiers are `system-test` / `component-test`. `compile` keeps the tier-walk semantics it
**already** has today (it walks `system` → `test`), now generalized to all three tiers. Bare =
for-all, qualified = scoped — the `make` / `make test` mental model.

**Decisions baked in (refine 2026-06-24):** hard rename, **no aliases** — the old
`gh optivem test …` / `component test …` invocations **break** (loud "unknown command", not a
silent change); a workspace-wide grep is the safety net (OQ-A). Hyphenated sibling nouns
`system-test` / `component-test`, not nested/concatenated (OQ-D). The bare `test` aggregate
**orchestrates** the system lifecycle by default with a `--assume-running` opt-out for CI (OQ-E).
Plan 1203's `component-test compile` verb is **folded in here** so the shared commit-stage
workflows are rewritten exactly once (OQ-C). Test levels stay **nouns**; per-component selection
is a `--component` **flag** on `system`, never a `component` noun (OQ-F). See **Resolved
decisions** for full rationale.

## Problem

Current tree (verified 2026-06-24):

```
gh optivem
├── system                ← the SUT (system tier)
│   ├── build  (docker compose build)
│   ├── start / status / stop / clean
│   └── compile           (compile SUT source)
├── test                  ← SYSTEM-TEST tier (top-level, sibling of system)  ⚠ ambiguous
│   ├── run
│   ├── setup
│   └── compile           (compile system-test source)
├── component
│   └── test              ← component tier (nested)                          ⚠ asymmetric
│       ├── run
│       └── setup         (no compile verb yet — see sibling plan 1203)
└── compile               ← bare tier-walk: system compile → test compile    ⚠ ambiguous
```

Two ambiguities:
1. **`test` doesn't say which tier.** System-tests are `test`; component-tests are
   `component test`. The asymmetry is deliberate today (the docstring in
   `component_commands.go` argues `test` drives a *deployed* system from outside, so it
   earned the short top-level name), but it reads as inconsistent and forces the reader to
   already know the boundary.
2. **`compile` doesn't say which tier.** Bare `compile` walks two tiers; it predates the
   component tier. Adding `component-test compile` (plan 1203) makes a bare `compile`
   strictly more confusing.

## Goal

Make the taxonomy symmetric **within each of two genuinely distinct axes** (not by pretending
`system` and the `*-test` tiers are one flat noun list — see **OQ-F**):
- **Runtime axis (one noun: `system`).** The deployable SUT, carrying lifecycle + compile verbs
  (`build | start | status | stop | clean | compile`). A component in its own repo is just
  *another* `system` from that repo's vantage; per-component selection inside a monorepo is a
  `--component` **flag** on `system`, never a top-level `component` noun.
- **Test-pyramid axis (parallel `*-test` nouns).** `system-test` and `component-test` are flat
  sibling **levels**, each carrying test verbs (`setup | run | compile`). They are pyramid
  *levels* (like unit / integration / e2e), not "the test of a subject noun" — which is exactly
  why `component-test` exists but `component` does not. `test` as a bare *level* **no longer
  exists**.
- **Aggregate verbs** `compile` and `test` survive as the bare top-level shortcuts, redefined
  as unambiguous "do this for **all** levels" — `compile` already walks tiers today; `test`
  gains the symmetric behavior. Bare = for-all, qualified (noun and/or `--component` flag) =
  scoped along that axis.

## Target tree

```
gh optivem
├── system          build | start | status | stop | clean | compile   ← runtime noun  [--component <name> selects a subset; OQ-F]
├── system-test     setup | run | compile                             ← test-level noun
├── component-test  setup | run | compile                             ← test-level noun (compile folded in here from plan 1203 — OQ-C)
├── compile         (aggregate verb) compile ALL tiers, halt on first failure
└── test            (aggregate verb) run ALL test levels in pyramid order, halt on first failure
```

Two axes (full rationale in **Goal** + **OQ-F**): **runtime** = the single `system` noun + a
`--component` flag; **test pyramid** = the parallel `*-test` level nouns sharing `setup | run |
compile`. There is no `component` noun (flag-axis, not noun-axis). The bare `compile` / `test`
are for-all aggregates (**OQ-B** / **OQ-E**).

## Scope / blast radius (verified)

- **shop workflows:** 15 files reference `gh optivem test ` (acceptance-stage ×N, drift,
  cross-lang-system-verification, _prerelease-pipeline). Plus the 0916-wave workflows that
  call `gh optivem component test`.
- **shop docs:** `docs/pipeline/acceptance-stage.md`.
- **shop root:** `CLAUDE.md`, `CONTRIBUTING.md` (the system-test verification snippet).
- **gh-optivem:** `test_commands.go`, `system_commands.go`, `component_commands.go`,
  `compile_commands.go`, `runner_helpers.go` (command wiring + docstrings + `Use:` strings +
  examples). Plus their `_test.go` expectations.
- **Out of scope (note, don't touch):** the `component-tests.yaml` config filename and the
  internal `SystemTest` config struct names — these are config/data, not the CLI verb surface.

## Steps

### Phase 0 — Decide
- [x] All open questions resolved (refine, 2026-06-24) — see **Resolved decisions**: hard rename
  with no aliases (OQ-A), keep bare `compile`/`test` as for-all aggregates (OQ-B), fold plan 1203
  in under this rename (OQ-C), hyphenated sibling tier nouns (OQ-D), `test` aggregate orchestrates
  the system lifecycle with a CI opt-out flag (OQ-E), test levels are nouns + component selection
  is a `--component` flag (two-axis model, OQ-F).

### Phase 1 — gh-optivem CLI
- [ ] Re-parent: `newTestCmd` → registered as `system-test`; `component test` → `component-test`
  (flatten the `component` parent). Update `Use:`, `Short`, `Long`, `Example` strings.
- [ ] Add the `component-test compile` verb (folded in from plan 1203 — OQ-C), so the
  component tier carries the same `setup | run | compile` set as `system-test`.
- [ ] Update the bare-`compile` walk per OQ-B to span all three tiers (system + component-test
  + system-test), and re-route the 7 commit-stage `Compile Code` steps plan 1203 owned.
- [ ] Update `_test.go` command-tree assertions and any golden help output.
- [ ] Cut a gh-optivem release (hard rename, no aliases — OQ-A). The shop call-site rewrite
  (Phase 2) must land together with consumers picking up this release; a stale call site
  breaks until flipped, so do not merge Phase 2 piecemeal.

### Phase 2 — shop call sites
- [ ] Rewrite the 15 workflows + 0916-wave workflows to the new verbs. `actionlint` each.
- [ ] Update `docs/pipeline/acceptance-stage.md`, `CLAUDE.md`, `CONTRIBUTING.md`.

### Phase 3 — Verify
- [ ] `actionlint` every changed workflow; one green CI run per pipeline.
- [ ] Grep the workspace for any residual `gh optivem test ` / bare `gh optivem compile`
  (hard-rename gate — there are no forwarding aliases, so any straggler is a live breakage).

## Resolved decisions
- **OQ-A — Hard rename (no aliases).** *Decided (refine, 2026-06-24):* **hard rename** — the CLI
  is renamed and every call site is flipped atomically; no hidden forwarding aliases and no
  deprecation window. Accepts coupling the gh-optivem release to the shop call-site edits in
  exchange for no alias cruft and no follow-up removal plan. Flag-day breakage from a missed
  call site is mitigated by the Phase 3 workspace-wide grep gate (no stale `gh optivem test ` /
  bare `gh optivem compile` may remain), not by a deprecation warning.
  - *Backward compatibility (consequence of the hard rename):* **partial — the bare top-level
    words survive, the qualified subcommands break.** Precisely:
    - ✅ `gh optivem compile` (bare) is **fully backward compatible** — same command, *superset*
      behavior (it already walked `system → test`; now walks all three tiers). Every existing
      `compile` call still works and just does more; nothing breaks.
    - ⚠️ `gh optivem test` (bare) is **repurposed, not breaking** — it previously only printed
      help (needed a subverb); now it runs the for-all aggregate. Nothing that did work before
      stops working.
    - ❌ `gh optivem test <run|setup|compile>` and `gh optivem component test <run|setup>`
      **break** (→ `system-test …` / `component-test …`), failing loudly with "unknown command"
      (not a silent misbehavior). The `--component` flag (OQ-F) is purely additive — zero new
      breakage.
    - `gh optivem system …` is unchanged. The loud failure on the broken subverbs plus the
      Phase 3 grep is the safety net that replaces the deprecation warning a hard rename forgoes.
- **OQ-B — Bare `compile` / `test` as for-all aggregates.** *Decided (with author, 2026-06-24):*
  **keep both bare verbs as "for-all" aggregates**, not removed and not renamed to `compile-all`.
  `gh optivem compile` compiles every tier (system prod+unit + component-test source sets +
  system-test project); `gh optivem test` runs every test tier in pyramid order (all
  component-test suites, then the system tests), halting on first failure. Rationale: bare =
  for-all is unambiguous (the bare word is a *verb spanning tiers*, never a tier), keeps the
  ergonomic shortcut, and just generalizes the tier-walk `compile` **already** does. The tiers
  stay the only nouns. Remaining sub-question for OQ-E: the `test` aggregate's system lifecycle.
- **OQ-C — Fold plan 1203 in under this rename.** *Decided (refine, 2026-06-24):* land **this
  taxonomy first and absorb plan 1203's `component-test compile` verb into it**, so the shared
  commit-stage workflows (and the 7 `Compile Code` steps 1203 re-routes) are rewritten **once**
  to the final verbs — never to an interim `component test compile` that this rename would then
  flip again. The 0916 gating wave layers on top afterward against the now-final names. Plan 1203
  is therefore subsumed here (its component-tier compile verb is no longer a separate landing);
  close it out pointing at this plan. [[feedback_plan_over_parallel_tickets]]

- **OQ-D — Hyphenated sibling tier nouns.** *Decided (refine, 2026-06-24):* **hyphenated
  siblings** `system-test` / `component-test`, not nested (`system test`) and not concatenated
  (`systemtest`). Rationale: the tiers form a *flat taxonomy of test kinds*, which siblings
  express and nesting (parent/child) misrepresents — nesting also needs a hollow `component`
  parent for symmetry, makes `system` a hybrid, and revives bare-`test` ambiguity. A hyphen is
  the conventional separator for two-word command names (`git cherry-pick`, `rev-parse`,
  `apt-get`); concatenation is reserved for lexicalized single tokens (`gofmt`, `systemctl`) and
  would produce the awkward `componenttest` `tt`-seam plus a third spelling vs the repo's
  existing `component-tests.yaml`.

- **OQ-E — `test` aggregate orchestrates the system lifecycle, opt-out for CI.** *Decided
  (refine, 2026-06-24):* **orchestrate by default, opt-out flag.** Bare `gh optivem test` runs
  the full cheap→expensive sequence and manages the system itself: component-test suites →
  `system start` → `system-test run` → `system stop`, halting on first failure. A flag
  (`--assume-running`, alias `--no-system-lifecycle`) skips the start/stop so CI — which already
  starts and stops the system explicitly around the acceptance stage — does not double-manage it.
  The default favors the developer "run everything in one command" case; CI opts out. Bare
  `compile` has no lifecycle analog — it is a pure source build, no flag.
  - *Execution caveat to verify during Phase 2:* confirm the acceptance-stage workflows pass
    `--assume-running` (or otherwise don't invoke the bare aggregate) so the aggregate's
    `system start` doesn't duplicate/clash with the `system start` those workflows already run.

- **OQ-F — Test levels are nouns; component selection is a flag (two-axis model).** *Decided
  (refine, 2026-06-24):* keep **nouns per test level** (`system-test` / `component-test`) and add
  a **`--component` flag on `system`** for per-component selection — rather than collapsing
  everything into a flag matrix (`gh optivem test --suite {unit|component|system} --component …`).
  The taxonomy has two genuinely distinct axes and each gets the right tool:
  - **Runtime axis → one noun + a flag.** `system` is the sole deployable noun; `--component
    backend` selects a subset within a monorepo, and a component split into its own repo simply
    *is* a `system` from that repo's vantage (the repo boundary is the selector — no flag needed
    there). Uniform selection ⇒ a flag composes cleanly.
  - **Test-pyramid axis → nouns.** `system-test` / `component-test` are pyramid *levels* with
    **divergent** setup, lifecycle, and compile targets (system-test needs `system start`/`stop`
    and `--assume-running`; component-test needs none; each compiles a different source
    set/project). A noun bundles each level's exact legal verb set and makes illegal states
    unrepresentable — `component-test` simply has no `--assume-running`. A flag matrix would
    scatter that divergence into conditional behavior behind flag combinations with invalid
    tuples (`--assume-running` only valid with the system level, etc.), plus weaker
    discoverability and worse CI-YAML readability.
  - *Why this resolves the "why `component-test` but no `component`" objection:* the two were
    never parallel nouns. `component` is a **flag-axis** (uniform selector); test level is a
    **noun-axis** (divergent behavior). The earlier "three flat tier nouns" framing was the
    error — corrected in **Goal** to the two-axis model.
  - *Bonus:* this is why bare `gh optivem test` cleanly means "everything" — with `test` no
    longer a *level*, the word is free as the for-all aggregate; you subtract scope with a noun
    (`system-test`) and/or the `--component` flag. The flag matrix would have left bare `test`
    ambiguous (run-all vs demand `--suite`).
  - *Scope note:* the `--component` flag on `system` is **net-new capability**, purely additive
    (no backward-compat impact). It need not land in the first cut — it can be a fast-follow once
    the rename is in — but the taxonomy reserves the flag-axis for it so a `component` noun is
    never introduced.

## Risks
- **Flag-day breakage** — OQ-A chose a hard rename with no aliases, so a missed call site is a
  live breakage, not a warned deprecation. Mitigated by landing the gh-optivem release and the
  Phase 2 call-site rewrite together and by the Phase 3 workspace-wide grep gate.
- **Triple-edit churn** on the commit-stage workflows if this, plan 1203, and the 0916 wave
  each rewrite them separately (OQ-C mitigates by sequencing this first / folding 1203 in).
- **Muscle-memory / docs drift** — `gh optivem test` is entrenched in habit and CLAUDE.md.
  With no deprecation warning (hard rename, OQ-A), the workspace-wide grep (Phase 3) is the
  only safety net for stragglers — so it is a hard gate, not advisory.

## ▶ Next executable step (resume here)

**Refined 2026-06-24 — all open questions resolved.** Ready to execute: run `/execute-plan` on
this file. Start with **Phase 1 (gh-optivem CLI)** — re-parent to `system-test` / `component-test`,
add `component-test compile` (folded in from 1203 — OQ-C), generalize the bare `compile` walk and
add the bare `test` aggregate with `--assume-running` (OQ-E), update `_test.go` assertions, cut a
hard-rename release (no aliases — OQ-A). Then **Phase 2** rewrites the shop call sites in lockstep
with that release, and **Phase 3** gates on `actionlint` + a workspace-wide grep for stragglers.

Related plans: [[20260624-1203-compile-component-and-test-sources-via-gh-optivem]] is **subsumed
here** (its component-tier compile verb is folded in — OQ-C); close it pointing at this plan.
[[20260622-0916-gate-component-contract-tests]] (same workflows, gating) layers on **after** this
rename, against the now-final verb names.
