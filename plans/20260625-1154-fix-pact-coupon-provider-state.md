# 2026-06-25 11:54:00 UTC — Fix Pact coupon provider state missing across all 3 languages

## TL;DR

**Why:** The consumer Pact interaction builder (`order.interactions.ts:22–24`) always declares only `product BOOK-123 exists and US is taxable` as the provider state, even when a coupon code is included in the request. This means the contract records no coupon state for the "place-order with coupon SAVE10" interaction, so all three backends seed only product+tax, fail to find coupon SAVE10, and return 422 instead of 201.

**End result:** The contract records a `coupon SAVE10 exists` state for the coupon place-order interaction; all three backend provider verification tests (Java, .NET, TypeScript) pass locally and in CI.

## Outcomes

- `contracts/frontend-backend.json` correctly declares `['product BOOK-123 exists and US is taxable', 'coupon SAVE10 exists']` as the provider states for "a place-order request for BOOK-123 qty 2 from US with coupon SAVE10".
- All three backend Pact provider verification suites (Java, .NET, TypeScript) pass locally before commit.
- CI multitier-backend-java-commit-stage, multitier-backend-dotnet-commit-stage, and multitier-backend-typescript-commit-stage are green.

## ▶ Next executable step (resume here)

Fix the consumer interaction builder: in `system/multitier/frontend-react/src/test/interactions/order.interactions.ts:22–24`, append `{ description: \`coupon ${couponCode} exists\` }` to the `states` array when `couponCode` is set. Then regenerate the contract by running the frontend consumer tests.

## Steps

- [ ] Step 1 — Fix the consumer interaction builder (`order.interactions.ts:22–24`): when `couponCode` is non-null, append `{ description: \`coupon ${couponCode} exists\` }` to the `states` array.
- [ ] Step 2 — Regenerate `contracts/frontend-backend.json` by running the frontend consumer Pact tests (runs the integration suite that calls `placeOrderInteraction` with `couponCode: 'SAVE10'`).
- [ ] Step 3 — Add TypeScript provider state handler: in `system/multitier/backend-typescript/test/pact/backend.pact.spec.ts`, add a `'coupon SAVE10 exists'` entry to `stateHandlers` that seeds SAVE10 into the coupon repo (same shape as the existing `'at least one coupon exists'` handler).
- [ ] Step 4 — Add .NET provider state handler: in `system/multitier/backend-dotnet/Tests/Contract/BackendPactVerificationTest.cs`, add `case "coupon SAVE10 exists":` in `ApplyState()` that inserts SAVE10 into `_db.Coupons` (same shape as `at least one coupon exists`).
- [ ] Step 5 — Add Java provider state handler: in `system/multitier/backend-java/src/contractTest/java/com/mycompany/myshop/backend/contract/BackendPactVerificationTest.java`, add `@State("coupon SAVE10 exists")` method that saves SAVE10 via `couponRepository` (same shape as `atLeastOneCouponExists`).
- [ ] Step 6 — Test locally (TypeScript): `GH_OPTIVEM_CONFIG=gh-optivem-multitier-typescript.yaml gh optivem component-test run --component backend --suite provider-verification` — confirm all interactions pass.
- [ ] Step 7 — Test locally (.NET): `GH_OPTIVEM_CONFIG=gh-optivem-multitier-dotnet.yaml gh optivem component-test run --component backend --suite provider-verification` — confirm all interactions pass.
- [ ] Step 8 — Test locally (Java): `GH_OPTIVEM_CONFIG=gh-optivem-multitier-java.yaml gh optivem component-test run --component backend --suite provider-verification` — confirm all interactions pass.
- [ ] Step 9 — Run `./compile-all.sh` from the repo root; confirm all projects compile.
- [ ] Step 10 — Run sample system tests for all three languages and confirm they pass.
- [ ] Step 11 — Commit via `/commit`.
