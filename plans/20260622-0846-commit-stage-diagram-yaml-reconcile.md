# 2026-06-22 08:46:00 UTC — Reconcile commit-stage diagram with real YAML (+ YAML cleanups)

> 🤖 **Picked up by agent** — `ValentinaLaptop` at `2026-06-22T09:14:12Z`

## TL;DR

**Why:** The commit-stage diagram has 8 conceptual boxes, but the real workflow YAML has ~21 steps across 3 jobs. The diagram is also missing two real test stages, and the YAML has accumulated stub tests that always pass plus a redundant Gradle build — drift that makes the diagram untrustworthy and the pipeline misleadingly green.
**End result:** A stable conceptual diagram (10 boxes) that maps cleanly to the YAML via grouping comments, with the false-green stubs and redundant build addressed, applied consistently across all 7 commit-stage workflows.

## Outcomes

What we get out of this — the goals and deliverables:

- A single **language-independent** 10-box conceptual commit-stage diagram (Checkout → Compile → Unit → Narrow Integration → Component → Contract → Linter → Static Analysis → Build → Publish) covering all 7 workflows — no per-language diagrams. Stays stable as YAML plumbing churns.
- The two missing test stages (Unit Tests, Narrow Integration Tests) are represented, completing the test pyramid.
- Each diagram box is traceable to its YAML steps via `# === <Stage> ===` header comments, so diagram ↔ YAML can be diffed mechanically.
- TODO stub test steps no longer report false green — they are visibly skipped/pending rather than `echo`-and-pass.
- The redundant second `./gradlew build` ("Build for Sonar") is eliminated or justified, so the commit stage doesn't build the project twice.
- All 7 commit-stage workflows (monolith ×3, multitier-backend ×3, multitier-frontend-react ×1) are consistent per the cross-language rule.

## ▶ Next executable step (resume here)

Java pilot (Steps 3–5) is **done and awaiting user approval at the gate below**. Once approved, do **Step 6**: propagate the approved Steps 3–5 to the other 6 commit-stage workflows (monolith .NET/TS, multitier-backend ×3, multitier-frontend-react), adapting per language and keeping stage names identical. Re-confirm the redundant-build question for .NET (Java confirmed redundant; React clean). Then Step 7: lint the workflows and confirm grouping comments ↔ diagram agree.

## Steps

- [x] Step 1: Locate or create the diagram source. Created at `docs/pipeline/commit-stage.md` as a Mermaid `flowchart` (renders natively on GitHub).
- [x] Step 2: Author the 10-box conceptual diagram, adding the missing **Run Unit Tests** and **Run Narrow Integration Tests** stages between Compile and Component. Includes gate/summary notes and the conditional-publish annotation.
- [x] Step 2b: Keep **one language-independent diagram** (no per-language diagrams — the conceptual stages are the same across Java/.NET/TS/React; the committed diagram is already toolchain-agnostic). Fold React's only structural difference — the opt-in `component-contract-tests` job — into the single diagram as an **optional dashed parallel branch** off Checkout, annotated "optional opt-in, where wired up", rather than a separate React diagram.
### ⛔ Approval gate — Java pilot done, awaiting approval before propagating

Steps 3–5 applied to `monolith-java-commit-stage.yml` only (pilot). Awaiting user review of the Java diff. **Do not touch the other 6 workflows, and do not commit, until the user approves the Java result.**

- [ ] Step 6: After approval, propagate the approved Steps 3–5 to the other 6 commit-stage workflows, adapting per language (.NET: `dotnet build`/test/sonarscanner; TypeScript: `tsc`/`npm test`/eslint; React: also handle the parallel opt-in `component-contract-tests` job). Re-verify the redundant-build question per language — it is Java-specific so far (React is clean; .NET unconfirmed). Keep stage names identical across languages where the concept is identical.
- [ ] Step 7: Verify — `./compile-all.sh` is not affected (no app code changes), but lint the workflows (`lint-workflows.yml` locally if possible) and confirm grouping comments + diagram agree.

## Reference: conceptual box → YAML step mapping (monolith-java)

| Diagram box | Real YAML steps absorbed |
|---|---|
| *(gate, not a box)* | check job: Ensure Env Vars; Check Commit on Main |
| Checkout Code | Checkout Repository |
| Compile Code | Setup Java, Setup Gradle, Pre-warm Gradle Wrapper, Compile Code |
| Run Unit Tests | Run Unit Tests |
| Run Narrow Integration Tests | Run Narrow Integration Tests (stub) |
| Run Component Tests | Run Component Tests (stub) |
| Run Contract Tests | Run Contract Tests (stub) |
| Run Linter | Run Linter |
| Run Static Code Analysis | Run Code Analysis (redundant "Build for Sonar" removed — see Step 5 finding) |
| Build Docker Image | Setup Buildx, Pre-pull base images ×2, Read/Compose Version, Extract Metadata |
| Publish Docker Image | Log in to GHCR, Build and Push (push gated on `on-branch`), Compose Digest URL |
| *(orchestration, not a box)* | summary job |

Deliberately left out of the linear diagram: the `check` gate job, the `summary` job, and the fact that Publish only runs on `main` (PRs build but don't push).

## Open questions

- ~~**Where does the diagram live?**~~ Resolved: checked-in Mermaid at `docs/pipeline/commit-stage.md`.
- ~~**How to fix the stubs (Step 4)?**~~ Resolved: **skip** via `if: false` + a one-line "pending" comment (option a).
- ~~**Redundant build (Step 5)?**~~ Resolved during execution: **deleted.** `test` is `finalizedBy jacocoTestReport` and `build` depends on `test`, so the line-88 `./gradlew build` ("Compile Code") already produces the jacoco XML (`build/reports/jacoco/test/jacocoTestReport.xml`) that `sonar` reads, and compiled classes persist in the job workspace. `Run Code Analysis` reuses both, so "Build for Sonar" was fully redundant.
- ~~**Frontend-react scope?**~~ Resolved: **tailored diagram.** React shares the 10-box main line (it is containerized → nginx image, so Build/Publish applies, and it has the same 4 inline stub test steps), but differs in two ways: (1) a second **parallel opt-in `component-contract-tests` job** runs the *real* component (`npm run test:component`) and Pact contract (`npm run test:pact`) tests off Checkout, not gating build/push — the backends have nothing equivalent; (2) **no redundant build** (Sonar runs `run-sonar.sh`, no second `npm run build`). **Per user: the diagram stays language-independent — no separate React diagram.** The opt-in job is folded into the single diagram as an optional dashed parallel branch (Step 2b).

- **Note:** the redundant-build issue (Step 5) is **Java-specific** — React is clean. Still need to confirm .NET.
