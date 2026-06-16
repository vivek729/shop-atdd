# Frontend

This is the frontend for MyShop, built with React + Vite and served via Nginx.

## Building

```shell
npm ci
npm run build
```

## Running Locally

```shell
npm run dev
```

## Testing

```shell
npm test
```

`npm test` runs **only** the fast, no-network unit tests (e.g. the top-level
harness tests). This is the default build and is unchanged for anyone who has not
opted into the component/contract layer below.

## Optional: in-process component & contract tests

This project ships with an **opt-in** in-process component + consumer Pact
contract test layer that is **deliberately excluded from the default `npm test`**.
The default config (`vite.config.ts`) ignores `src/test/component/**` and
`src/test/pact/**`; these suites run only via a separate opt-in config
(`vitest.opt-in.config.ts`).

### Running it

```shell
npm run test:component   # in-process component tests (jsdom, RTL)
npm run test:pact        # consumer Pact tests — (re)generate the contract
```

These suites need **no Docker, no compose, and no broker** — the consumer Pact
tests run an in-process mock server and write the contract to the repo-owned
`contracts/` folder.

### Removing it

There is intentionally **no generation flag** to exclude this layer — it is
already off the default build, so it is "present but dormant". If you don't want
it, ignore it or delete `src/test/component/`, `src/test/pact/`, and
`vitest.opt-in.config.ts`.

> Note: `npm install` still pulls the Pact / Testing-Library devDependencies even
> if you never run these suites (an accepted residue of keeping the layer in the
> same project rather than a duplicate one). They have no effect on the default
> build or the shipped bundle.

### Contract distribution

The consumer Pact tests write the contract to the repo-owned `contracts/` folder,
which the backend provider verifies against — a **$0, zero-infra** default
(committed pact, no service). A Pact Broker is a separate, **cost-labelled** opt-in
for multi-repo setups only. See [`contracts/README.md`](../../../contracts/README.md)
for the distribution rule and broker cost details.
