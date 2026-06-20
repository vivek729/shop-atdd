# 2026-06-20 08:15 UTC — Coordinator: uniform driver req/response across 3 languages + ArchUnit enforcement

This is an **orchestration plan**. Its items each drive one child plan to completion. Execute it with `/execute-plan` and work the items top-to-bottom; for token efficiency you may `/clear` and re-run `/execute-plan` on this file between items (the resume block always names the next child).

## TL;DR

**Why:** The MyShopDriver test-kit contract — "every operation takes a `*Request` and returns a `*Response`" — was made uniform in **Java** and we want (a) the same uniformity in **.NET** and **TypeScript** so the three parallel implementations don't drift, and (b) the original objective: an **ArchUnit POC** proving which of these structural rules CI can enforce mechanically.
**End result:** All three languages share the uniform req/response driver shape, and a green `:system-test:java:architectureTest` demonstrates the ArchUnit rules (incl. strict A10) red-then-green, with a filled-in feasibility matrix.

## Children & status

| Child plan | Scope | Status | Needs Docker? |
|------------|-------|--------|---------------|
| `20260620-0758-...refactor.md` | **Java** driver req/resp refactor | ✅ **DONE** — committed `ba409b5c`, sample suite green | — |
| `20260620-0741-...investigation.md` | **ArchUnit POC** (the original goal) | ⬜ pending — Java refactor (its Step 3b) already satisfied by 0758 | **No** (static bytecode analysis) |
| `20260620-0810-...refactor-dotnet.md` | **.NET** driver req/resp refactor | ⬜ pending | Yes (`--sample`) |
| `20260620-0810-...refactor-typescript.md` | **TypeScript** driver req/resp refactor | ⬜ pending | Yes (`--sample`) |

**Dependencies:** the three pending children are mutually independent. The ArchUnit POC depends only on the Java refactor (done), so it can run immediately and needs no containers — do it first while Docker is busy with concurrent work.

## Items (execute in this order)

- [ ] **C1 — ArchUnit POC.** `plans/20260620-0741-...investigation.md` — **Steps 1–7 DONE & committed** (POC `ArchitectureRulesTest` for A1/A2/A7/A10 green + red-green demo; B1 spiked & demoted; feasibility matrix + rollout + multi-lang note filled in). **Only Step 8 (decision gate) remains — needs the user** (graduate to production rule suite? write next-batch rules A5/A8/A3/A4/A6/A9? move to C2/C3?).
- [ ] **C2 — .NET refactor.** Execute `plans/20260620-0810-myshop-driver-uniform-request-response-refactor-dotnet.md`. **Coordinate Docker with the user before the D7 sample run** (concurrent container work). Commit `shop` scoped.
- [ ] **C3 — TypeScript refactor.** Execute `plans/20260620-0810-myshop-driver-uniform-request-response-refactor-typescript.md`. **Coordinate Docker before the T7 sample run.** Commit `shop` scoped.
- [ ] **C4 — Close-out.** Once C1–C3 are done, confirm all three languages share the uniform driver shape and the ArchUnit matrix is filled in; decide with the user whether to graduate any POC rules into a committed production rule suite (parent investigation Step 8 decision gate) and whether to port the rules to NetArchTest / ts-arch. Then delete this coordinator and its completed children.

## How each item is executed

For each `C*` item, the executor opens the named child plan and runs **its** steps (the child carries the detailed touch points, decisions, and verification). When the child is fully done and committed, mark the coordinator item complete (delete it) and move to the next. Prefer a `/clear` between items so each child runs with a small context.

## ▶ Next executable step (resume here)

**C1 reached its Step 8 decision gate** (POC done, committed). Awaiting the user's call on: graduate A1/A2/A7/A10 to a production rule suite, and/or write the feasible next-batch rules (A5/A8 then A3/A4/A6/A9). Once decided, the next *mechanical* work is **C2** (.NET refactor, `...-dotnet.md`) then **C3** (TS refactor, `...-typescript.md`) — both need Docker for their sample runs, so **coordinate container usage with the user first**.

## Non-goals

- Re-doing the Java refactor (done).
- Committing a full production ArchUnit rule suite beyond the POC (that's the parent investigation's Step 8 decision, surfaced in C4).
- Running all three sample suites without coordinating Docker usage with the user.
