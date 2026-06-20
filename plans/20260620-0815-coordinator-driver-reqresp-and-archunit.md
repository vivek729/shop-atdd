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

- [ ] **C1 — ArchUnit POC.** Execute `plans/20260620-0741-archunit-enforce-dsl-driver-rules-investigation.md` to completion. **In progress:** Steps 1, 2 (ArchUnit `1.3.0` dep + `architectureTest` task, green), 3b (done by 0758) complete and committed. **Resume at Step 3** (write the 4-rule `ArchitectureRulesTest` — see that plan's `▶ Next executable step` for the worked-out rule designs), then Steps 4–8. No Docker.
- [ ] **C2 — .NET refactor.** Execute `plans/20260620-0810-myshop-driver-uniform-request-response-refactor-dotnet.md`. **Coordinate Docker with the user before the D7 sample run** (concurrent container work). Commit `shop` scoped.
- [ ] **C3 — TypeScript refactor.** Execute `plans/20260620-0810-myshop-driver-uniform-request-response-refactor-typescript.md`. **Coordinate Docker before the T7 sample run.** Commit `shop` scoped.
- [ ] **C4 — Close-out.** Once C1–C3 are done, confirm all three languages share the uniform driver shape and the ArchUnit matrix is filled in; decide with the user whether to graduate any POC rules into a committed production rule suite (parent investigation Step 8 decision gate) and whether to port the rules to NetArchTest / ts-arch. Then delete this coordinator and its completed children.

## How each item is executed

For each `C*` item, the executor opens the named child plan and runs **its** steps (the child carries the detailed touch points, decisions, and verification). When the child is fully done and committed, mark the coordinator item complete (delete it) and move to the next. Prefer a `/clear` between items so each child runs with a small context.

## ▶ Next executable step (resume here)

**C1 in progress** — ArchUnit dependency + `architectureTest` task are in (Step 2 done, committed). Resume by executing `plans/20260620-0741-...investigation.md` from its **Step 3** (write the 4-rule `ArchitectureRulesTest`); that plan's `▶ Next executable step` block carries the worked-out rule designs (A1/A2/A7/A10) and the plain-`@Test`+`@Tag` approach. No Docker. After C1 reaches its Step 8 gate, proceed to C2/C3 (Docker — coordinate first).

## Non-goals

- Re-doing the Java refactor (done).
- Committing a full production ArchUnit rule suite beyond the POC (that's the parent investigation's Step 8 decision, surfaced in C4).
- Running all three sample suites without coordinating Docker usage with the user.
