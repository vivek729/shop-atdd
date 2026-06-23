# Commit Stage

The commit stage runs on every push and pull request. It compiles the code, runs
the fast test layers, checks quality, and builds a Docker image — publishing it
only when the commit is on `main`.

This diagram shows the **conceptual** stages. The real workflow YAML has more steps
(setup, pre-warm, retry, registry login, metadata), each of which belongs to the
conceptual box it supports — see [Diagram ↔ YAML mapping](#diagram--yaml-mapping).

## Pipeline

```mermaid
flowchart TD
    gate([Gate: env vars]):::gate

    gate --> checkout[Checkout Code]
    checkout --> compile[Compile Code]
    compile --> unit[Run Unit Tests]
    unit --> linter[Run Linter]
    linter --> analysis[Run Static Code Analysis]
    analysis --> build[Build Docker Image]
    build --> shouldpublish{Should Publish Docker Image?}:::conditional
    shouldpublish -->|on main| publish[Publish Docker Image]
    shouldpublish -->|pull request| done([No publish])

    gate --> ct["gh optivem component test run<br/>(unit · integration · component · contract)"]:::component

    publish --> summary([Summary]):::gate
    done --> summary
    ct --> summary

    classDef gate fill:#eee,stroke:#999,stroke-dasharray:3 3,color:#333;
    classDef conditional fill:#e8f0ff,stroke:#4070c0,color:#1a3a6a;
    classDef component fill:#e8f4e8,stroke:#408040,color:#1a4a1a;
```

- **Gate** and **Summary** are orchestration jobs, not pipeline stages.
- **Publish Docker Image** runs only on `main`; pull requests build the image but do not push it.
- **`gh optivem component test run`** runs all four suites (unit · narrow integration · component · contract) via the declarative `component-tests.yaml` per component. It runs in a parallel `component-tests` job alongside `run` and gates the `summary` — failing it blocks the pipeline. Pending suites print a notice and pass; Docker-backed suites (Java Testcontainers) require the Docker daemon (provided on `ubuntu-latest`).
- **"contract" in the component tier means consumer Pact** (frontend↔backend, in-process). This is distinct from the external-system contract suites in `tests.yaml` (clock/erp/tax, stub-vs-real). Both use the word "contract"; only the label in `component-tests.yaml` (`name: Consumer Contract (Pact)`) disambiguates them.
- **Local vs CI:** `gh optivem component test run` is the command that matches the CI gate. Bare `npm test` / `./gradlew test` / `dotnet test` run a fast, Docker-light subset and intentionally run *less* than CI. Use `--suite unit` for the fast inner loop, bare `run` to match CI.

## Diagram ↔ YAML mapping

Alignment covers the **`run` job only** — each conceptual box absorbs the supporting
YAML steps below it so the diagram can be diffed against the YAML. Two marker styles:
stage boxes use `# === <Stage> ===` headers; decision diamonds (gates) use
`# <> <Decision?> <>`. The `check` (env-vars) and `summary` jobs are orchestration and
are not part of the alignment.

| Diagram box | YAML steps |
|---|---|
| Checkout Code | Checkout Repository (`run` job) |
| Compile Code | Setup toolchain, pre-warm, Compile Code (`run` job) |
| Run Unit Tests | Run Unit Tests (`run` job; present for languages that also need it for Sonar coverage) |
| Run Linter | Run Linter (`run` job) |
| Run Static Code Analysis | Run Code Analysis (`run` job; reuses Compile Code's build output) |
| Build Docker Image | Setup Buildx, pre-pull base images, read/compose version, extract metadata (`run` job) |
| Publish Docker Image | Registry login, Build and Push (gated on `main` via Check Commit on Main), Compose Digest URL (`run` job) |
| gh optivem component test run | `component-tests` job: Install gh-optivem CLI Extension, Set Up Component Test Harness, Run Component Tests |

Workflows: `monolith-{dotnet,java,typescript}-commit-stage.yml`,
`multitier-backend-{dotnet,java,typescript}-commit-stage.yml`,
`multitier-frontend-react-commit-stage.yml`.
