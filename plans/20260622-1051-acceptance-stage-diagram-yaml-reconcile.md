# 2026-06-22 10:51:00 UTC — Reconcile acceptance-stage diagram with real YAML

## TL;DR

**Why:** The acceptance-stage diagram (`docs/pipeline/acceptance-stage.md`) and its
Diagram ↔ YAML mapping table are already **content-accurate** — every conceptual box maps
to real YAML steps and the ordering matches. But unlike the commit-stage YAML, the
acceptance-stage YAML carries **no marker comments**, so there is nothing in the YAML that
lets a reader (or a future diff) tie a step back to its diagram box. The commit-stage got
`# === <Stage> ===` and `# <> <Decision?> <>` anchors; acceptance-stage has none yet.

**What:** Add the same anchoring convention to `monolith-java-acceptance-stage.yml` as a
pilot, adapted for the fact that acceptance-stage spans **5 jobs** (not 1), then propagate
to the other 5 acceptance-stage workflows once approved.

**Outcome:** Each diagram box has a greppable anchor in the YAML; the YAML can be diffed
against the diagram the same way the commit-stage now can; convention stays consistent
across both stages and all 6 acceptance workflows.

## Key finding — acceptance-stage is already clean

The commit-stage reconcile fixed two real defects along the way: **false-green stub tests**
(`if: false`) and a **redundant `./gradlew build`**. Neither exists here:

- All test steps are **real** (`gh optivem test run --suite …`) — no stubs, no false greens.
- The `sonar` job's Gradle setup is **not** redundant — it is a separate job on a separate
  runner, so it legitimately needs its own toolchain setup.

So this reconcile is **anchoring only** — no behavioural YAML changes. Lower risk than the
commit-stage one.

## Differences vs the commit-stage reconcile (decide convention first)

1. **Multi-job scope.** Commit-stage alignment was scoped to the **`run` job only**.
   Acceptance-stage's mapping table already claims alignment across **5 jobs**: `preflight`
   (Gate), `check` (Should Run?), `run` (Checkout → Tag RC), `publish-tag`, `sonar`. Four of
   the boxes are *whole jobs*, not step-groups inside one job.

2. **Gates are jobs, not inline diamonds.** In commit-stage, "Should Publish?" was an inline
   step decision → got a `# <> … <>` marker. Here both gates ("artifacts exist?",
   "Should Run?") are **separate jobs** gated by `if:` / `needs:`. There is no inline
   decision step to mark, so the `# <>` convention does not transfer 1:1.

3. **`debug-skip-tests` is invisible in the diagram.** The YAML guards nearly every run-job
   step (and the whole `sonar` job) with `if: ${{ !inputs.debug-skip-tests }}`. The diagram
   only mentions it in a prose bullet — there is no dashed branch like commit-stage's opt-in.

→ **Recommended convention (Option B below):** give each whole-job box a one-line
job-level header `# === <Box> ===` directly under the job key, and use step-group
`# === <Stage> ===` headers inside the `run` job exactly as commit-stage does. Skip `# <>`
entirely (no inline decisions to anchor). This makes *every* mapping-table row resolve to a
greppable anchor, which is what the acceptance doc already promises.

## ▶ Next executable step (resume here)

Resolve the **convention open question** (Option A run-job-only vs Option B all-jobs —
recommend B), then apply it to **`monolith-java-acceptance-stage.yml` only** as the pilot:
add the headers per the mapping table below, `grep '# ==='` to verify count, then **stop at
the approval gate** and show the Java diff. Do not touch the other 5 workflows until approved.

## Steps

- [x] Step 1: Diagram + mapping table exist and are content-accurate (`docs/pipeline/acceptance-stage.md`, committed `1a52e9b9`). Verified box-by-box against `monolith-java-acceptance-stage.yml`.
- [x] Step 2: Confirm no false-green stubs / redundant builds to fix (none — all tests real, sonar setup non-redundant). This reconcile is anchoring-only.
- [ ] Step 3: **Decide convention** (Open Question 1). Recommended: Option B — job-level `# === <Box> ===` headers for whole-job boxes + step-group `# ===` headers inside `run`.
- [ ] Step 4: Apply chosen convention to `monolith-java-acceptance-stage.yml` only (pilot):
  - `preflight` job → `# === Gate: Artifacts Exist? ===`
  - `check` job → `# === Should Run? ===`
  - inside `run`: `# === Checkout Code ===`, `# === Deploy: Real External Systems ===`, `# === Deploy: Stub External Systems ===`, `# === Setup Test Harness ===`, `# === Run Smoke Tests ===`, `# === Run Acceptance Tests ===`, `# === Run Contract Tests ===`, `# === Run E2E Tests ===`, `# === Tag Release Candidate ===`
  - `publish-tag` job → `# === Publish Git Tag ===`
  - `sonar` job → `# === Run Static Code Analysis ===`
  - Verify: `grep -c '# ===' ` equals 12 (one per diagram box).
- [ ] Step 5: Sync the doc mapping table if anchoring surfaces any small wording drift (e.g. add the `check`-job `Checkout Repository` step and the `sonar`-job toolchain-setup steps, currently omitted from the table). Doc-only.

### ⛔ Approval gate — Java pilot first, then propagate

Apply Steps 3–4 **only to `monolith-java-acceptance-stage.yml`** first. Stop and show the
user the diff. **Do not touch the other 5 acceptance workflows, and do not commit, until the
user approves the Java result.**

- [ ] Step 6: After approval, propagate to the other 5 acceptance-stage workflows
  (`monolith-{dotnet,typescript}-acceptance-stage.yml`,
  `multitier-{dotnet,java,typescript}-acceptance-stage.yml`), adapting per language but
  keeping anchor text identical where the box is identical. Note the multitier workflows
  deploy more services (backend + frontend) so the run-job step set differs — anchor to the
  same boxes, not the same step count.
- [ ] Step 7: Verify — no app code touched, so `./compile-all.sh` is unaffected. Lint the
  workflows (`actionlint` / `lint-workflows.yml`) and confirm `# ===` headers ↔ diagram boxes
  agree across all 6 files.

## Diagram ↔ YAML mapping (monolith-java pilot)

| Diagram box | Anchor target | YAML job/steps |
|---|---|---|
| Gate: artifacts exist? | `preflight` job header | Check Container Packages Exist |
| Should Run? | `check` job header | Ensure Env Vars, Checkout, Read Base Version, Check Tag Exists, GHCR login, Resolve Digests, Get Last Run, Check Artifacts Changed, Detect Test Changes, Evaluate Run Gate |
| Checkout Code | `# ===` in `run` | Checkout Repository, Docker Hub login, GHCR login |
| Deploy: Real External Systems | `# ===` in `run` | Simulate Deployment (Real), Wait for Systems |
| Deploy: Stub External Systems | `# ===` in `run` | Simulate Deployment (Stub), Wait for Systems |
| Setup Test Harness | `# ===` in `run` | Setup Java, Setup Gradle, Pre-warm, Cache Playwright, Install gh-optivem, Setup Test Harness |
| Run Smoke Tests | `# ===` in `run` | smoke-stub, smoke-real |
| Run Acceptance Tests | `# ===` in `run` | acceptance-parallel-api/ui, acceptance-isolated-api/ui |
| Run Contract Tests | `# ===` in `run` | contract-stub, contract-stub-isolated, contract-real |
| Run E2E Tests | `# ===` in `run` | e2e-api, e2e-ui |
| Tag Release Candidate | `# ===` in `run` | Read Base Version, Compose Prerelease Version, Tag Docker Images |
| Publish Git Tag | `publish-tag` job header | Publish Git Tag |
| Run Static Code Analysis | `sonar` job header | Setup Java, Setup Gradle, Pre-warm, Run Sonar Analysis |

Deliberately not anchored: the `summary` job (orchestration, explicitly out of alignment per
the doc).

## Open questions

1. **Anchor scope / convention?**
   - **Option A (commit-stage parity):** anchor the `run` job only with `# ===`; leave
     `preflight`/`check`/`publish-tag`/`sonar` as self-evident whole jobs. Most consistent
     with the commit-stage precedent.
   - **Option B (recommended):** also add a job-level `# === <Box> ===` header to each
     whole-job box, so every mapping-table row resolves to a greppable anchor. Most
     consistent with the acceptance doc, which already claims all 5 jobs are in alignment.
   - Recommendation: **B**.

2. **Surface `debug-skip-tests` in the diagram?** Currently prose-only. Option: add a dashed
   annotation (like commit-stage's opt-in branch) showing it bypasses Deploy→Tests→Sonar.
   Recommendation: **leave prose-only** for now — it is a debug-only path, and adding a
   branch clutters the happy-path diagram. Revisit only if it confuses readers.
