# `dsl-port` layout inconsistency

**Captured:** 2026-05-26

## Problem

In every `gh-optivem-*.yaml` the `system-test.paths:` block carries 8 keys. Across all three languages (TypeScript / Java / .NET), every key except `dsl-port` resolves to a path with a SUT-namespace leaf — `myShop` (TS) / `myshop` (Java) / `MyShop` (.NET) — matching the SUT name visible everywhere else in the template (class prefixes `MyShopApiClient`, package `com.mycompany.myshop`, solution `MyCompany.MyShop.sln`).

`dsl-port` is the exception. Its on-disk tree is structured **by step shape**, not by SUT:

```
src/testkit/dsl/port/
├── assume/
├── given/
├── then/
├── when/
├── channel-mode.ts
├── external-system-mode.ts
└── scenario-dsl.ts
```

Same shape in all three languages (`Dsl.Port/{Assume,Given,Then,When}` for .NET; `dsl/port/{assume,given,then,when}` under `com/mycompany/myshop/testkit/` for Java). So when the rest of the yaml resolves SUT-namespaced paths like `driver/adapter/myShop`, `dsl-port` has to point at the un-namespaced parent (`dsl/port` / `Dsl.Port`).

## How it was discovered

While fixing the `paths:` blocks 2026-05-26 to match the actual on-disk layout — the originally-emitted defaults used a fictional `shop` leaf that doesn't exist on disk for any key. All 12 `gh-optivem-*.yaml` files (monolith × multitier × main × legacy × TS/Java/.NET) were corrected in the same change. `dsl-port` was the only key where "repoint at the real SUT-namespaced location" was impossible because no such location exists.

## Risk

If a future external-system DSL port is introduced at `dsl/port/external/...`, it would silently match the same scope as the SUT's DSL port — because the scope checker's allow-list is the un-namespaced parent `dsl/port`. Other keys (`driver-port`, `dsl-core`, etc.) don't have this collision risk because they carry the SUT-namespace leaf.

## Decision needed

### Option A — bring `dsl-port` in line with the others

Introduce a `dsl/port/myShop/` (resp. `myshop/`, `MyShop/`) layer in all three templates and move the SUT's step interfaces into it. Externals (if any) would live at `dsl/port/external/`.

- **Pro:** symmetric with `driver-port` / `driver-adapter` / `dsl-core`.
- **Con:** moves ~30 step interface files + their imports across TS/Java/.NET; touches every test that imports a step.

### Option B — ratify the current shape

Document that `dsl-port` is intentionally SUT-agnostic: the step *interfaces* (Given/When/Then/Assume) are a reusable vocabulary, only the *implementations* (`dsl-core/usecase/myShop`) are SUT-specific.

- **Pro:** zero code change.
- **Con:** the `paths:` value stays un-namespaced (conflicts with any future "every path key carries the SUT namespace" doctrine), and the scope-collision risk above remains.

## Cross-refs

- `gh-optivem`'s `internal/projectconfig/paths_defaults.go::DefaultPaths` currently emits `dsl-port: .../dsl/port/${sutNamespace}` — i.e. it assumes Option A's layout. Even after the yaml fix lands here, `gh optivem init` will still emit a wrong default for `dsl-port` (and for the other keys too — its `${sutNamespace}` derivation produces `shop` from the `optivem/shop` repo slug, but the template uses `myShop`/`myshop`/`MyShop`). Reconciling `DefaultPaths` is a separate `gh-optivem` change, not part of this template fix.
