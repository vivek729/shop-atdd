# 2026-06-24 12:21 UTC — Symmetric `gh optivem` tier taxonomy (kill ambiguous `test` and ambiguous `compile`)

> 🤖 **Picked up by agent (refine)** — `Valentina_Desk` at `2026-06-24T12:27:38Z`

## TL;DR

**Why:** The `gh optivem` command surface names its two test tiers on two different
patterns, and its compile verb is partly bare. Both are ambiguity sources:

- **Ambiguous `test`:** the *system-test* tier is the bare top-level `gh optivem test`,
  while the *component-test* tier is the nested `gh optivem component test`. Two test
  tiers, two naming patterns — a reader can't tell from `test` alone which tier they're in.
- **Ambiguous `compile`:** `gh optivem compile` is a bare verb that *walks* tiers
  (`system compile` then `test compile`). With a third tier (component tests) about to get
  its own compile, a bare `compile` no longer says *what* it compiles.

**End result:** Every tier is a flat, parallel **noun**; every scoped action is a tier-scoped
verb; and the bare top-level `compile` / `test` survive — redefined as unambiguous **"for-all"
aggregate verbs** that span every tier. A reader always knows the tier from the noun, and the
bare word always means "everywhere":

```
# tier nouns — scoped
gh optivem system          build | start | status | stop | clean | compile
gh optivem system-test     setup | run | compile
gh optivem component-test  setup | run | compile

# aggregate verbs — for ALL tiers
gh optivem compile   # compile every tier: system (prod+unit) + component-test source sets + system-test project
gh optivem test      # run every test tier in pyramid order: all component-test suites, then the system tests (fail-fast)
```

`test` is no longer a *tier* (the source of the old ambiguity) — it is the run-everything verb;
the tiers are `system-test` / `component-test`. `compile` keeps the tier-walk semantics it
**already** has today (it walks `system` → `test`), now generalized to all three tiers. Bare =
for-all, qualified = scoped — the `make` / `make test` mental model.

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

Make the taxonomy **symmetric** on two axes:
- **Tier nouns** are flat siblings (`system`, `system-test`, `component-test`), each carrying
  the same scoped verbs. `test` as a bare *tier noun* **no longer exists** — the system-test
  tier becomes `system-test`; the component tier becomes `component-test`.
- **Aggregate verbs** `compile` and `test` survive as the bare top-level shortcuts, redefined
  as unambiguous "do this for **all** tiers" — `compile` already walks tiers today; `test`
  gains the symmetric behavior. Bare = for-all, qualified = scoped.

## Target tree

```
gh optivem
├── system          build | start | status | stop | clean | compile   ← tier noun
├── system-test     setup | run | compile                             ← tier noun
├── component-test  setup | run | compile                             ← tier noun (compile lands via plan 1203)
├── compile         (aggregate verb) compile ALL tiers, halt on first failure
└── test            (aggregate verb) run ALL test tiers in pyramid order, halt on first failure
```

- Both test tiers are now `*-test` siblings with the **same** verb set (`setup`/`run`/`compile`).
- `component` as a bare parent noun collapses into `component-test` (it only ever hosted
  `test`); revisit only if a non-test component verb ever appears.
- The bare verbs `compile` / `test` are kept as ergonomic for-all aggregates (see **OQ-B**), not
  removed. `compile` extends its existing two-tier walk to all three; `test` is new behavior
  (see **OQ-E** for the system-lifecycle question it raises).

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
- [ ] Resolve **OQ-A** (rename mechanism: hard rename vs hidden alias), **OQ-B** (bare
  `compile` walk: rename to `compile-all`, keep as deprecated alias, or drop), **OQ-C**
  (sequencing vs plan 1203 and the 0916 wave).

### Phase 1 — gh-optivem CLI
- [ ] Re-parent: `newTestCmd` → registered as `system-test`; `component test` → `component-test`
  (flatten the `component` parent). Update `Use:`, `Short`, `Long`, `Example` strings.
- [ ] Update the bare-`compile` walk per OQ-B.
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

## Open questions
- **OQ-B — Bare `compile` / `test` as for-all aggregates.** *Decided (with author, 2026-06-24):*
  **keep both bare verbs as "for-all" aggregates**, not removed and not renamed to `compile-all`.
  `gh optivem compile` compiles every tier (system prod+unit + component-test source sets +
  system-test project); `gh optivem test` runs every test tier in pyramid order (all
  component-test suites, then the system tests), halting on first failure. Rationale: bare =
  for-all is unambiguous (the bare word is a *verb spanning tiers*, never a tier), keeps the
  ergonomic shortcut, and just generalizes the tier-walk `compile` **already** does. The tiers
  stay the only nouns. Remaining sub-question for OQ-E: the `test` aggregate's system lifecycle.
- **OQ-C — Sequencing with plan 1203 + the 0916 wave.** Plan 1203 adds `component-test compile`
  and re-routes the 7 commit-stage `Compile Code` steps; the 0916 wave edits the same
  workflows for gating. *Recommend:* land **this rename first** (or fold 1203's compile verb
  in under the already-symmetric name), so the workflows are rewritten **once** to the final
  verbs rather than to `component test compile` then again to `component-test compile`.
  [[feedback_plan_over_parallel_tickets]]
- **OQ-D — Hyphen vs nested.** `system-test` (single hyphenated noun) vs `system test` (nested
  under `system`)? *Recommend:* **hyphenated siblings** — nesting `test` under `system` would
  wrongly imply the component tier nests too, and the tiers are genuinely parallel, not
  parent/child.
- **OQ-E — `gh optivem test` aggregate and the system lifecycle.** Component-tests need no
  running system; system-tests need `system start` + `system stop`. Should the aggregate
  **orchestrate** the lifecycle (run component-tests → `system start` → `system-test run` →
  `system stop`), or **assume the system is already up** and just run the tiers in order
  (leaving start/stop to the caller, as CI does today)? *Recommend:* **orchestrate, but make
  it opt-out** — bare `gh optivem test` does the full cheap→expensive sequence including
  start/stop so a developer gets "run everything" in one command; a flag (e.g.
  `--no-system-lifecycle` / `--assume-running`) skips start/stop for CI, which already manages
  the system explicitly. Bare `compile` has no analog — it is a pure source build. Confirm this
  doesn't duplicate/repeat `system start` work the acceptance-stage workflows already do.

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

Draft, **awaiting refinement** — run `/refine-plan` to settle **OQ-A…OQ-D** (especially OQ-C
sequencing). Do **not** start renaming until OQ-C is decided, so the commit-stage workflows are
rewritten the minimum number of times. Sibling plans:
[[20260624-1203-compile-component-and-test-sources-via-gh-optivem]] (adds the component-tier
compile verb this taxonomy renames) and [[20260622-0916-gate-component-contract-tests]] (same
workflows, gating).
