# 2026-07-09 17:49:00 UTC — Harden Java Dockerfiles' Gradle-wrapper download against transient CDN 504s

## TL;DR

**Why:** The scaffolded Java backend's Docker build downloads the Gradle distribution via the wrapper with no retry, so a transient HTTP 504 from the Gradle/GitHub release CDN kills the whole build — which just flaked the gh-optivem smoke matrix (`TestValidMultitierConfigurations/multitier_multirepo_java_ts_typescript`).
**End result:** Both Java Dockerfiles retry the Gradle-wrapper distribution download (3 attempts, 10s backoff) before `bootJar`, matching the retry hardening already present on the TypeScript/React Dockerfiles and across the host pipeline. A single CDN blip no longer fails the commit stage.

## Outcomes

What we get out of this — the goals and deliverables:

- A transient HTTP 5xx (e.g. 504) while downloading `gradle-8.14.3-bin.zip` inside the Docker build retries instead of failing the build.
- `shop/system/multitier/backend-java/Dockerfile` and `shop/system/monolith/java/Dockerfile` carry identical, self-documented retry logic around the wrapper bootstrap.
- The Java Docker build stage reaches parity with the already-hardened TS/React Dockerfiles (`npm_config_fetch_retries=5`) — no remaining un-retried in-Docker network fetch.
- Clean builds are unchanged: `bootJar` still runs against the (now reliably fetched) cached distribution and produces the same jar.

## ▶ Next executable step (resume here)

Edit `shop/system/multitier/backend-java/Dockerfile` and `shop/system/monolith/java/Dockerfile`: replace the single `RUN --mount=type=cache,target=/root/.gradle chmod +x gradlew && ./gradlew bootJar --no-daemon` line with a version that runs a retried `./gradlew --version --no-daemon` wrapper bootstrap (3 attempts, `sleep 10` between) *before* `./gradlew bootJar --no-daemon`, so the flake-prone distribution download retries. Both files get the identical edit. Then `docker build` each image locally to confirm the RUN is valid shell and a clean build still emits the bootJar.

## Steps

- [ ] Step 1: In `shop/system/multitier/backend-java/Dockerfile`, replace the build-stage `RUN --mount=type=cache,target=/root/.gradle chmod +x gradlew && ./gradlew bootJar --no-daemon` with:

  ```dockerfile
  RUN --mount=type=cache,target=/root/.gradle \
      chmod +x gradlew && \
      for i in 1 2 3; do ./gradlew --version --no-daemon && break || \
        { echo "Gradle wrapper download failed (attempt $i/3), retrying in 10s…"; sleep 10; }; done && \
      ./gradlew bootJar --no-daemon
  ```

  Add a one-line comment above it noting the retry guards the wrapper's distribution download against transient CDN 5xx (mirrors the TS Dockerfiles' `npm_config_fetch_retries`).

- [ ] Step 2: Apply the identical edit + comment to `shop/system/monolith/java/Dockerfile` (same original line, same replacement).

- [ ] Step 3: Verify locally — from each Java system dir, `docker build .` (BuildKit enabled) both images to confirm the retry-wrapped RUN parses as valid shell and a clean build still produces `build/libs/*.jar`. Note: the retry loop's `else` branch only fires on a real failure, so a healthy build takes the `--version && break` fast path once.

## Verification

- `docker build` both Java images locally (multitier backend + monolith) — RUN is valid shell, clean build still yields the bootJar.
- (Operator, out of agent scope) Cut a new shop release tag and re-run the gh-optivem smoke matrix; confirm the multitier and monolith Java configs go green.

## Notes

- Root cause pinned: `shop/system/multitier/backend-java/Dockerfile:10-11` and `shop/system/monolith/java/Dockerfile:10-11`. Failure evidence: gh-optivem run `29032050146` → downstream backend commit-stage run `29032309634`, step "Build and Push Docker Image": `java.io.IOException: Server returned HTTP response code: 504 for URL: https://github.com/gradle/gradle-distributions/releases/download/v8.14.3/gradle-8.14.3-bin.zip` from `org.gradle.wrapper.Install.forceFetch`.
- Precedent: TS/React Dockerfiles already set `npm_config_fetch_retries=5` with explicit "5xx" comments; the host pipeline pre-warms the wrapper with `nick-fields/retry@v4` ("Pre-warm Gradle Wrapper (retry transient CDN flakes)"), pre-pulls base images with retry, and retries Sonar. The Java Docker build was the only un-retried network fetch. The host pre-warm does not help the Docker build because BuildKit runs in an isolated container with its own `/root/.gradle` cache mount.
- Approach chosen (retry loop) over pinning a distribution mirror or raising a socket timeout: retry is the established codebase pattern and matches the TS fix; a mirror swap adds no resilience since the 504 came from the redirect target itself.
