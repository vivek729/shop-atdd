# 2026-06-24 12:21 UTC — Symmetric `gh optivem` tier taxonomy (kill ambiguous `test` and ambiguous `compile`)

## TL;DR

**Why:** The `gh optivem` command surface names its two test tiers on two different
patterns, and its compile verb is partly bare. Both are ambiguity sources:

- **Ambiguous `test`:** the *system-test* tier is the bare top-level `gh optivem test`,
  while the *component-test* tier is the nested `gh optivem component test`. Two test
  tiers, two naming patterns — a reader can't tell from `test` alone which tier they're in.
- **Ambiguous `compile`:** `gh optivem compile` is a bare verb that *walks* tiers
  (`system compile` then `test compile`). With a third tier (component tests) about to get
  its own compile, a bare `compile` no longer says *what* it compiles.

**End result:** Every tier is a flat, parallel noun, and every action is a tier-scoped verb.
No bare `test`, no bare `compile`. A reader always knows the tier from the noun:

```
gh optivem system          <verb>   # the SUT
gh optivem system-test     <verb>   # outside-in tests vs a deployed system
gh optivem component-test  <verb>   # in-process commit-stage suites
```

with `compile` / `run` / `setup` living symmetrically under each tier that has them.

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

Make the taxonomy **symmetric**: every tier is a sibling noun, every applicable action is a
tier-scoped verb. After this:
- `test` as a bare noun **no longer exists** — it becomes `system-test`.
- `compile` is **always** tier-scoped (`system compile`, `system-test compile`,
  `component-test compile`); the bare aggregate, if kept, is renamed to something
  unambiguous (e.g. `compile-all`) or dropped.

## Target tree

```
gh optivem
├── system          build | start | status | stop | clean | compile
├── system-test     setup | run | compile
└── component-test  setup | run | compile        (compile lands via plan 1203)
```

- Both test tiers are now `*-test` siblings with the **same** verb set (`setup`/`run`/`compile`).
- `component` as a bare parent noun collapses into `component-test` (it only ever hosted
  `test`); revisit only if a non-test component verb ever appears.
- Compile is uniformly tier-scoped. See **OQ-B** for the fate of the bare `compile` walk.

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
- [ ] (If OQ-A = alias) register hidden deprecated aliases `test` / `component test` that warn
  and forward, so old call sites keep working during migration.
- [ ] Cut a gh-optivem release.

### Phase 2 — shop call sites
- [ ] Rewrite the 15 workflows + 0916-wave workflows to the new verbs. `actionlint` each.
- [ ] Update `docs/pipeline/acceptance-stage.md`, `CLAUDE.md`, `CONTRIBUTING.md`.

### Phase 3 — Verify
- [ ] `actionlint` every changed workflow; one green CI run per pipeline.
- [ ] Grep the workspace for any residual `gh optivem test ` / bare `gh optivem compile`.
- [ ] (If aliases were added with a deprecation window) schedule alias removal as a later plan.

## Open questions
- **OQ-A — Hard rename or aliased migration?** *Recommend:* **hidden deprecated aliases** for
  one release — register `test`/`component test` as aliases that forward + warn, flip all call
  sites, then remove the aliases in a follow-up. Avoids a flag-day where a stale workflow
  breaks. A hard rename is simpler but couples the CLI release and every call-site edit into
  one atomic change.
- **OQ-B — Fate of the bare `compile` walk.** Rename to `gh optivem compile-all` (unambiguous
  aggregate), keep `compile` as a deprecated alias, or drop it and let callers list tiers?
  *Recommend:* **rename to `compile-all`** — keeps the one-shot convenience the structural
  cycle relies on while removing the bare-`compile` ambiguity. Confirm the `compile_all`
  action / structural-cycle caller is updated in lockstep.
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

## Risks
- **Flag-day breakage** if hard-renamed without aliases and a call site is missed (OQ-A
  mitigates).
- **Triple-edit churn** on the commit-stage workflows if this, plan 1203, and the 0916 wave
  each rewrite them separately (OQ-C mitigates by sequencing this first / folding 1203 in).
- **Muscle-memory / docs drift** — `gh optivem test` is entrenched in habit and CLAUDE.md;
  the deprecation warning (OQ-A) and a workspace-wide grep (Phase 3) catch stragglers.

## ▶ Next executable step (resume here)

Draft, **awaiting refinement** — run `/refine-plan` to settle **OQ-A…OQ-D** (especially OQ-C
sequencing). Do **not** start renaming until OQ-C is decided, so the commit-stage workflows are
rewritten the minimum number of times. Sibling plans:
[[20260624-1203-compile-component-and-test-sources-via-gh-optivem]] (adds the component-tier
compile verb this taxonomy renames) and [[20260622-0916-gate-component-contract-tests]] (same
workflows, gating).
