# 2026-06-20 08:15 UTC — Coordinator: uniform driver req/response across 3 languages + ArchUnit enforcement

🤖 **Picked up by agent** — `ValentinaLaptop` at `2026-06-20T16:38:08Z`

This is an **orchestration plan**. Its items each drive one child plan to completion. Execute it with `/execute-plan` and work the items top-to-bottom; for token efficiency you may `/clear` and re-run `/execute-plan` on this file between items (the resume block always names the next child).

## TL;DR

**Why:** The MyShopDriver test-kit contract — "every operation takes a `*Request` and returns a `*Response`" — was made uniform in **Java** and we want (a) the same uniformity in **.NET** and **TypeScript** so the three parallel implementations don't drift, and (b) the original objective: an **ArchUnit POC** proving which of these structural rules CI can enforce mechanically.
**End result:** All three languages share the uniform req/response driver shape **and enforce the same A1/A2/A7/A10 structural rules** — Java via ArchUnit (done), .NET via ArchUnitNET, TS via ts-arch/ts-morph (full parity; overrides parent-plan Q4). Java also has the filled-in feasibility matrix.

## Children & status

| Child plan | Scope | Status | Needs Docker? |
|------------|-------|--------|---------------|
| `20260620-0758-...refactor.md` | **Java** driver req/resp refactor | ✅ **DONE** — committed `ba409b5c`, sample suite green | — |
| `20260620-0741-...investigation.md` | **ArchUnit POC** (the original goal) | ✅ **DONE** — Step 8 decided 2026-06-20: keep the 4 POC rules as-is; broader-suite decision deferred to C4 | **No** (static bytecode analysis) |
| `20260620-0810-...refactor-dotnet.md` | **.NET** refactor **+ ArchUnitNET A1/A2/A7/A10 rules** | ✅ **code DONE & committed** 2026-06-20 (`dotnet build` green, 4 arch rules green + red-green) — only D7 `--sample` run ⏳ deferred (Docker busy) | Yes (`--sample`, deferred) |
| `20260620-0810-...refactor-typescript.md` | **TypeScript** refactor **+ ts-morph A1/A2/A7/A10 rules** | ✅ **code DONE** 2026-06-20 (`tsc --noEmit` green; 4 ts-morph rules green + each red-then-green via `npm run test:architecture`) — only T7 `--sample` run ⏳ deferred (Docker busy) | Yes (`--sample`, deferred) |

**Dependencies:** the three pending children are mutually independent. The ArchUnit POC depends only on the Java refactor (done), so it can run immediately and needs no containers — do it first while Docker is busy with concurrent work.

## Items (execute in this order)

- [ ] **C2 — .NET `--sample` run only — ⏳ Deferred** (2026-06-20, Docker busy). The .NET refactor (D1–D6) **and** ArchUnitNET A1/A2/A7/A10 tests (D8) are **done & committed** (`dotnet build` green; 4 arch rules green + each red-green). Only the `--sample` runtime verification (child D7) remains — run it when Docker is free.
- [ ] **C3 — TypeScript `--sample` run only — ⏳ Deferred** (2026-06-20, Docker busy). The TS refactor (T1–T6) **and** ts-morph A1/A2/A7/A10 rules (T8) are **done & committed** (`tsc --noEmit` green; 4 rules green + each red-then-green). Only the `--sample` runtime verification (child T7) remains — run it when Docker is free.
- [ ] **C4 — Close-out.** Once C1–C3 are done, confirm all three languages share the uniform driver shape **and enforce A1/A2/A7/A10** (Java ArchUnit, .NET ArchUnitNET, TS ts-arch/ts-morph). Decide with the user whether to graduate beyond the 4 proven rules into a broader committed suite (parent investigation Step 8). Then delete this coordinator and its completed children.

## How each item is executed

For each `C*` item, the executor opens the named child plan and runs **its** steps (the child carries the detailed touch points, decisions, and verification). When the child is fully done and committed, mark the coordinator item complete (delete it) and move to the next. Prefer a `/clear` between items so each child runs with a small context.

## ▶ Next executable step (resume here)

**All three languages' code is done & committed** (Java done; .NET committed; TypeScript committed 2026-06-20 — uniform req/resp + A1/A2/A7/A10 enforced via ArchUnit / ArchUnitNET / ts-morph). Remaining mechanical work is **two deferred `--sample` runs that need Docker** — C2 (.NET child D7) and C3 (TS child T7); **coordinate container usage with the user first**. After both pass, do **C4 — Close-out**: confirm parity and decide with the user whether to graduate beyond the 4 proven rules (parent investigation Step 8), then delete this coordinator and its children.

## Non-goals

- Re-doing the Java refactor (done).
- Committing a full production ArchUnit rule suite beyond the POC (that's the parent investigation's Step 8 decision, surfaced in C4).
- Running all three sample suites without coordinating Docker usage with the user.
