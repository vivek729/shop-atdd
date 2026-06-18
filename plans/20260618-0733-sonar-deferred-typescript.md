# 2026-06-18 07:33:00 UTC — Sonar deferred: TypeScript

**Run started:** 2026-06-18 07:33 UTC

## Target state

The single deferred issue (`typescript:S4325` on the `this.currentPage!` assertion in `MyShopUiClient.openHomePage`) is resolved by the **real fix, not a suppression**: route the page access through the class's *existing* `requirePage()` helper (lines 63–68), which already returns a non-null `Page` and is used by every other method. `openHomePage()` is the only place that bypasses it with a raw `!`. When the work is done:

- `openHomePage()` captures `const page = this.requirePage()` once the context/page is ensured, and uses `page` for both `await page.goto(...)` and `new HomePage(page)`.
- Both non-null assertions (`this.currentPage!` on lines 26 and 28) are gone; `page` is statically `Page`, so `npx tsc --noEmit` passes without the assertion S4325 flagged.
- `requirePage()` throws inside the existing `try { … } catch (e) { return failure(...) }`, so the error contract is unchanged on every reachable path (and the null path was already unreachable — `currentPage` and `context` are set and cleared together).
- The fix is idiomatic to this exact file (matches `newOrderPage()`, `orderHistoryPage()`, etc.), so there is no behavior drift — the concern that originally deferred it.

## Resolved decisions

- **S4325 → restructure via `requirePage()`** (chosen over `// NOSONAR` suppression and over a UI "Won't Fix"). The earlier deferral feared restructuring would risk behavior drift, but the file already contains the exact non-null helper for this, making the restructure trivial, idiomatic, and behavior-equivalent. Preferring the genuine fix over silencing a real-but-locally-required assertion.

## Steps

1. In `system-test/typescript/src/testkit/driver/adapter/ui/client/MyShopUiClient.ts`, edit `openHomePage()`:
   - After the `if (!this.context) { … }` block, add `const page = this.requirePage();`
   - Change `await this.currentPage!.goto(this.baseUrl)` → `await page.goto(this.baseUrl)`
   - Change `new HomePage(this.currentPage!)` → `new HomePage(page)`
2. Verify with `npx tsc --noEmit` in `system-test/typescript` — must pass with no `!` on those lines.
3. (Optional) Run a UI smoke/e2e sample to confirm `openHomePage` still behaves identically.
4. Commit with the combined Sonar run; next analysis clears S4325.

## Issue (inventory)

- `typescript:S4325` — `system-test/typescript/src/testkit/driver/adapter/ui/client/MyShopUiClient.ts:28` — "This assertion is unnecessary since the receiver accepts the original type of the expression." Resolved by step 1 (the `!` on line 26 is removed in the same edit).
