# 2026-07-08 10:39 UTC — Unify the component vs narrow-integration Postgres harness (backend-java)

> **Follow-up** spun out of `20260708-0902-backend-external-systems-component-tests-legacy-latest.md` (decision #8). The external-systems (ERP/Tax/Clock) stubs were unified onto one in-process WireMock mechanism + shared DSL across the component and narrow-integration layers. The **Postgres** mechanism was deliberately left un-unified there because it touches the shared component harness + the Pact verifier and is **not** behaviour-neutral. This plan does that unification on its own.

## TL;DR

**Why:** `backend-java` boots Postgres two different ways across its opt-in test layers — the component/contract harness uses a **singleton static `PostgreSQLContainer` + `@DynamicPropertySource`**, while the narrow-integration layer uses Spring Boot's **`@ServiceConnection` `@Bean`** (`TestcontainersConfiguration`). Two mechanisms for the same dependency is a maintenance and teaching wart.
**End result:** Both layers start Postgres via **one** mechanism, so the harness reads the same way regardless of layer. The Pact provider-verification test still passes (it shares the component harness). Behaviour-preserving for what each test asserts; only the container wiring changes.

## Problem

- Component + contract layers: `AbstractComponentTest` holds `static final PostgreSQLContainer<?> POSTGRES`, started once in a static initializer (singleton pattern — the Spring context is cached and shared across subclasses, so a JUnit-managed `@Container` that stopped after the first class would leave the reused context pointing at a dead port), wired via `@DynamicPropertySource`.
- Narrow-integration layer: `TestcontainersConfiguration` exposes `@Bean @ServiceConnection PostgreSQLContainer<?>`, imported by `AbstractIntegrationTest`.
- The two mechanisms diverge in lifecycle, wiring, and how a reader reasons about them. `ErpGatewayIntegrationTest` (now split legacy/latest) does not touch Postgres at all, so the divergence currently only bites `OrderRepositoryIntegrationTest` / `OrderControllerIntegrationTest` vs the component/contract tests.

## Goal

One Postgres harness mechanism across component, contract, and narrow-integration layers, chosen deliberately, with the Pact verifier still green.

## Open questions (resolve before executing)

1. **Which mechanism wins?**
   - **(a) `@ServiceConnection` everywhere (recommended)** — the idiomatic Spring Boot 3 approach; least custom code. Blocker to check: the component harness shares a *cached* context across subclasses + the Pact verifier; confirm a `@ServiceConnection` bean survives that sharing without the dead-port problem the singleton pattern was written to avoid. If it does, this is the cleanest.
   - (b) Singleton static container + `@DynamicPropertySource` everywhere — proven against the context-sharing constraint; more boilerplate, less idiomatic.
   - (c) Leave as-is — reject; that is the status quo this plan exists to remove.
2. **Does unifying interact with the narrow-integration taxonomy work?** Cross-check `project_narrow_integration_target_taxonomy` and sub-plans 1801-OQ4 / 1957 before committing to a mechanism, so this doesn't have to be redone when the 4-layer model lands.

## ▶ Next executable step (resume here)

**This plan is design-not-yet-mechanical.** Resolve the two open questions first (run `/refine-plan` on this file): pick the winning mechanism (recommend `@ServiceConnection` everywhere, pending the cached-context/dead-port check) and reconcile with the narrow-integration taxonomy. Only once the mechanism is chosen do the Steps below become concrete edits.

## Steps (provisional — firm up after open questions resolve)

- [ ] **Step 1 — Spike the cached-context check:** confirm whether a `@ServiceConnection` Postgres bean survives the component harness's shared/cached Spring context (used by every component subclass + the Pact verifier) without the dead-port failure that motivated the singleton static container.
- [ ] **Step 2 — Converge the mechanism** in `AbstractComponentTest` (+ `TestcontainersConfiguration` / `AbstractIntegrationTest`) onto the chosen approach. Keep the ERP/Tax/Clock in-process WireMock wiring untouched.
- [ ] **Step 3 — Verify:** `./gradlew componentTest contractTest integrationTest` on CI (Docker not available locally — see `project_local_testcontainers_blocked`). Pact provider-verification must stay green; `OrderRepository` / `OrderController` integration tests must stay green.
- [ ] **Step 4 — Sync docs** if the harness section of the architecture docs describes the Postgres mechanism per layer.
- [ ] **Step 5 — Commit via `/commit`; delete this plan.**

## Notes

- Scope: **backend-java only**, consistent with the parent plan. .NET / TS not in scope.
- Not behaviour-neutral in mechanism (container lifecycle/wiring changes), but behaviour-preserving for every assertion each test makes — that is the bar.
