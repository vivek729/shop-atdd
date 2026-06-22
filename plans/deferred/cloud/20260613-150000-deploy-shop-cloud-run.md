# Deploy `shop` for real — Cloud Run + Neon

**Goal:** Run the shop as a live, reachable demo (not just `gh optivem system start` locally), cheaply enough that an idle month costs ~nothing.
**Target:** GCP Cloud Run (compute) + Neon (Postgres). Region `us-central1` (Terraform default; Tier-1, includes free tier).
**Scope (chosen):** Stand up **one** variant end-to-end first — `multitier/java` — then template across the other five. Legacy variants out of scope for v1.
**Status:** Plan. No infra applied yet.

## Decision & rationale

**Cloud Run + Neon, not Cloudflare / AWS / Azure.** Three reasons, in order of weight:

1. **Scale-to-zero is the whole game for a demo.** A teaching demo is idle most of the month and live during sessions. Cloud Run scales to zero natively and has a *perpetual* free tier (180K vCPU-s, 360K GiB-s, 2M requests/month). A demo this size almost certainly never leaves the free tier → compute ≈ $0. AWS Fargate is ~2× cheaper *per always-on unit* but has no scale-to-zero and no compute free tier, so a mostly-idle demo bills like a full-time service. Azure Container Apps is priced like Cloud Run but buys us nothing here.
2. **You're already 90% tooled for it.** `terraform/` provisions the project, Artifact Registry, Secret Manager, and GitHub Actions OIDC (Workload Identity Federation) with exactly the right roles (`run.admin`, `artifactregistry.writer`, `secretmanager.secretAccessor`, `iam.serviceAccountUser`). Picking any other provider throws that away to save nothing meaningful.
3. **The DB is the part none of the serverless container platforms host well.** The `postgres:16-alpine` service in the compose files is fine locally but wrong for production — ephemeral container storage, no managed backups. Neon (serverless Postgres, scales to zero, free tier) is the right split, and `terraform/variables.tf` already has a `neon_database_url` variable wired into Secret Manager. Keep it; it's provider-agnostic.

Cloudflare Containers is technically capable now but the wrong fit: the shop is multi-service with a relational DB, and Cloudflare wants each container fronted by a Worker + Durable Object with no native orchestration and weak persistence — more plumbing, and you'd still need external Postgres.

## What's already in place (don't rebuild)

- **Terraform** — project, Artifact Registry repo `app-images`, Secret Manager secret `db-connection-string`, WIF pool + `github-deployer` SA with deploy roles. `setup-gcp.sh` / `teardown-gcp.sh` wrap it.
- **Dockerfiles** — every service already has one: `system/multitier/backend-{java,dotnet,typescript}/Dockerfile`, `system/multitier/frontend-react/Dockerfile`, `system/monolith/{java,dotnet,typescript}/Dockerfile`, `external-systems/simulators/Dockerfile`. No image work needed beyond build+push.
- **Variant model** — `gh-optivem-<arch>-<lang>.yaml` already describes each variant's service paths. The deploy can read these so it stays DRY across the six.
- **Migrations** — `system/db/migrations` (Flyway), already used by the `db-migrate` compose service.

## Architecture: compose → Cloud Run mapping

`multitier/java` "real" stack (from `docker/java/multitier/docker-compose.local.real.yml`):

| Compose service | Cloud Run target | Notes |
|---|---|---|
| `frontend` (React, :3000) | Cloud Run **service** `shop-mt-java-frontend`, public | Needs `BACKEND_API_URL` — see gotcha |
| `backend` (:8081) | Cloud Run **service** `shop-mt-java-backend` | Reads DB + simulator URLs from env/secrets |
| `external-system-simulators` (:9000) | Cloud Run **service** `shop-mt-java-sim` | "real" mode uses simulators for clock/erp/tax |
| `db-migrate` (Flyway, one-shot) | Cloud Run **job** `shop-mt-java-migrate` | Runs once per deploy; not a service |
| `postgres` | **Neon** (external) | Drop the in-cluster Postgres entirely |

Service naming prefix `shop-<arch>-<lang>-<role>` lets all six variants live in **one** project without collision.

## Phases

### Phase 0 — Prove one variant
Do `multitier/java` fully before touching the others. Rationale: multitier is the harder case (frontend + backend + simulators + DB), so anything that works here templates trivially down to the monolith (single service + simulators + DB). Java is the slowest cold-start case, so if its UX is acceptable, the TS/.NET variants only get better.

### Phase 1 — Provision (already built)
```bash
./setup-gcp.sh   # terraform apply: project, Artifact Registry, Secret Manager, WIF
```
Confirm the `github-deployer` SA and `app-images` repo exist. No new Terraform needed for v1.

### Phase 2 — Database (Neon)
1. Create a Neon project; create a **branch/database per variant** (e.g. `shop-mt-java`) so the shared Flyway schema can't collide across live variants.
2. Put the connection string in the existing secret: `gcloud secrets versions add db-connection-string --data-file=-` (or via `terraform.tfvars` → `neon_database_url`).
3. **Gotcha — URL vs discrete vars:** the backend wants `POSTGRES_DB_HOST/PORT/NAME/USER/PASSWORD` (see compose), but Neon hands you one URL. Either (a) store the five discrete values as separate secrets, or (b) add a tiny env-mapping step in the backend entrypoint that parses the URL. Pick (a) for v1 — no code change, and it keeps the same env contract the app already has.

### Phase 3 — Build & push images
Dockerfiles exist, so per service:
```bash
REG=us-central1-docker.pkg.dev/$PROJECT_ID/app-images
docker build -t $REG/shop-mt-java-backend system/multitier/backend-java
docker push  $REG/shop-mt-java-backend
# repeat for frontend-react and external-systems/simulators
```
Tag with the repo `VERSION` (currently `1.0.117`) so deploys are traceable.

### Phase 4 — Deploy services
Order matters because of the frontend→backend URL dependency:
1. `gcloud run deploy shop-mt-java-sim` (no deps).
2. `gcloud run deploy shop-mt-java-backend` — set `EXTERNAL_SYSTEM_MODE=real`, `ERP/CLOCK/TAX_API_URL` = the sim service URL, DB vars from secrets, `ALLOWED_ORIGINS` = frontend URL (filled in pass 2).
3. `gcloud run deploy shop-mt-java-frontend --allow-unauthenticated` with `BACKEND_API_URL` = backend URL.
4. Re-deploy backend with the now-known frontend origin in `ALLOWED_ORIGINS`.

**Gotcha — frontend env timing:** confirm `frontend-react` reads `BACKEND_API_URL` at **runtime** (its Node server) and not baked at build. If it's build-time, the backend URL must be known before `docker build`, which means deploying backend first and rebuilding the frontend image with the URL — not a runtime env. Verify before scripting.

### Phase 5 — Migrations as a Cloud Run Job
The compose `db-migrate` mounts `system/db/migrations` as a volume; **Cloud Run Jobs can't mount host volumes.** Build a one-shot migration image instead:
```dockerfile
FROM flyway/flyway:10.21.0-alpine
COPY system/db/migrations /flyway/sql
```
Deploy as a job, run it against the Neon URL on each release (in CI, after build, before the service deploy that needs the new schema).

### Phase 6 — CI/CD (use the existing OIDC)
Add a GitHub Actions workflow that authenticates via the `github-deployer` WIF (already provisioned), builds+pushes the three images, runs the migration job, then deploys the three services. **Trigger on tag, not every push to `main`** — controls churn and cost, and matches a release cadence for a demo. (Per `CLAUDE.md`: no GitHub Pages; if you want a status/URL page, it's `docs/*.md`.)

### Phase 7 — Template across variants
Parameterize Phases 3–6 by `<arch>` and `<lang>`, reading service paths from the `gh-optivem-<arch>-<lang>.yaml` files so there's a single source of truth. Monolith collapses backend+frontend into one service. Roll out TS and .NET multitier next (faster cold starts), then the three monoliths.

### Phase 8 — Cost guardrails
- Set a **GCP budget alert** at e.g. €5 and €20 so a misconfigured `min-instances` can't surprise you.
- Default `min-instances=0` everywhere. Only set `min-instances=1` on a *single* showcased variant if JVM cold start hurts a live session — and only for the session.
- `teardown-gcp.sh` already exists for full reset between cohorts if you want zero standing footprint.

## Cost rationale

For the demo usage shape (idle most of the month, bursts during sessions):

- **Compute:** all services at `min-instances=0`. A few thousand requests and tens of vCPU-seconds per session stays inside Cloud Run's free tier (180K vCPU-s / 360K GiB-s / 2M req per month, account-wide, never expires) → **~$0**.
- **DB:** Neon free tier covers a demo, and scales to zero between sessions → **~$0**.
- **Storage/egress:** Artifact Registry image storage is cents; ingress is free; demo egress is well under any threshold → **negligible**.
- **The only way this costs real money** is pinning `min-instances>0` (always-warm) — a small Cloud Run instance kept warm runs on the order of a few €/month *each*, which is why warm-pinning is per-session and per-variant, not a default.

Net: a parked demo is ~free; a busy month is single-digit euros.

## OPEN DECISIONS (pick before executing)

1. **Live surface area:** keep only 1–2 variants live and deploy the rest on-demand, **or** keep all six warm-able? (Recommend: 1–2 live, rest deployable via CI tag.)
2. **Backend exposure:** backend public, **or** `ingress=internal` + frontend-only via Cloud Run service-to-service auth? (Recommend: internal for backend; only the frontend and sim need to be reachable — sim only if you want to show stub responses.)
3. **DB isolation:** Neon branch per variant **or** one shared DB? (Recommend: branch per variant — same Flyway schema, but no cross-variant interference.)
4. **Region:** stay on `us-central1`, or `europe-west1` for EU-session latency? (Both Tier-1 / free-tier eligible.)
5. **CI trigger:** tag-based release **or** push-to-`main` auto-deploy? (Recommend: tag.)
