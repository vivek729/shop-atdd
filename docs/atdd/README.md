# ATDD documentation

The ATDD process docs that used to live here have moved into the
[gh-optivem](https://github.com/optivem/gh-optivem) tool's embedded asset
tree. They are no longer duplicated per-consumer-repo.

## Where to find them

When you run any `gh optivem` command, the docs are synced to your home
directory:

- `~/.gh-optivem/docs/atdd/architecture/` — diagrams, layers, port rules
- `~/.gh-optivem/docs/atdd/process/` — per-phase cycle documentation
- `~/.gh-optivem/docs/atdd/code/` — language equivalents, testkit rules

Open them in any markdown viewer (GitHub renders them natively, as does
VS Code's preview pane).

## Editing the docs

The canonical source lives at
`internal/assets/global/docs/atdd/` in the
[gh-optivem](https://github.com/optivem/gh-optivem) repo. Edit there and
publish a new gh-optivem release; the next `gh optivem` invocation in any
consumer repo will sync the updated docs to `~/.gh-optivem/docs/`.

## Why the move

Three reasons:

1. **Single source of truth.** The same content lived in shop's
   `docs/atdd/`, in gh-optivem's embedded prompts (inlined verbatim),
   and in every scaffolded consumer repo. Edits drifted between the
   three; nine prompts had to be hand-synced.
2. **Smaller prompt payloads.** Embedded prompts no longer inline 20+ KB
   of reference docs — they point at the synced docs and let the agent
   read on demand.
3. **Atomic upgrades.** A gh-optivem release ships methodology updates
   along with the tool that consumes them; consumer repos pull the new
   docs automatically on next invocation.
