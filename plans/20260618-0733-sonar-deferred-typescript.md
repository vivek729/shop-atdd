# 2026-06-18 07:33:00 UTC — Sonar deferred: TypeScript

**Run started:** 2026-06-18 07:33 UTC

## typescript:S4325 — system-test/typescript/src/testkit/driver/adapter/ui/client/MyShopUiClient.ts:28

**Message:** This assertion is unnecessary since the receiver accepts the original type of the expression.

**What I tried:** Removed the non-null assertion `!` from `new HomePage(this.currentPage!)` (offset 36-53 on line 28).

**Result:** `npx tsc --noEmit` fails with:
`error TS2345: Argument of type 'Page | null' is not assignable to parameter of type 'Page'. Type 'null' is not assignable to type 'Page'.`

`HomePage`/`BasePage` constructor requires a non-null `Page`, and `this.currentPage` is typed `Page | null`. TypeScript's control-flow analysis does not narrow `currentPage` to non-null at this point (the `await ... .goto()` call between assignment and use breaks narrowing), so the `!` is required to compile.

**Open question:** Sonar flags the assertion as redundant but the TS compiler disagrees. The assertion is genuinely needed for compilation. Could be addressed by restructuring so the page is held in a local non-null variable after `newPage()`, but that is a larger change and risks behavior drift. Left the `!` in place. Recommend marking this issue as "Won't Fix" / false-positive in SonarCloud.
