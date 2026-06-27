# 2026-06-27 11:15:23 UTC — Migrate shop ticket corpus + issue forms to the canonical-section whitelist

## TL;DR

**Why:** gh-optivem commit c5836592 made `PARSE_TICKET` enforce a closed canonical-section whitelist (`Description`, `Acceptance Criteria`, `Steps to Reproduce`, `Checklist`, `External System Contract Criteria`), but the shop rehearsal ticket corpus and the three issue forms were never migrated. Rehearsal of #72 crashes at parse: `section "User story" is not an allowed heading`. The whitelist is correct and must not change — the shop artifacts are out of sync with it.

**End result:** Every rehearsal-corpus ticket (61, 65, 68, 69, 70, 71, 72, 76, 79, 80, 81) parses cleanly under the whitelist, and the three issue forms (`story.yml`, `bug.yml`, `task.yml`) no longer emit a non-canonical `Legacy Acceptance Criteria` section. No gh-optivem code changes.

## Outcomes

- The 5 tickets carrying non-canonical headings (#65, #68, #72, #76, #80) are rewritten so every H2 is a canonical section; the 6 already-clean tickets (#61, #69, #70, #71, #79, #81) are confirmed to parse as-is.
- Narrative framing (`User story`, `Context`, `Bug report`, `No production change`) lives inside `## Description`; scoping commentary (`Out of scope`) and implementation hints (`Why this forces ERP DSL changes` + its file list) are dropped.
- Business rules (`Shipping rule` #72, `Discount rule` #68) are expressed as Gherkin `Rule:` blocks nested in the Acceptance Criteria, not as prose.
- #80's near-miss heading `External system contract acceptance criteria` is renamed to the canonical `External System Contract Criteria` (it would otherwise crash identically to #72).
- The `Legacy Acceptance Criteria` field is removed from all three issue forms; the whitelist and the `External System Contract Criteria` / ESCC naming are left unchanged.

## ▶ Next executable step (resume here)

Start with **Step 1 (#72 — the trigger)**: `gh issue view 72 --repo optivem/shop --json body`, then `gh issue edit 72 --repo optivem/shop --body-file <tmp>` applying the worked transformation in Step 1. #72 exercises every move (fold, drop, Rule:-conversion, ESCC-kept), so getting it right templates the rest.

## Steps

Each ticket edit: `gh issue view <n> --repo optivem/shop --json body -q .body` → transform → `gh issue edit <n> --repo optivem/shop --body-file <tmpfile>`. Apply the disposition rules below uniformly:
- `## User story` / `## Context` / `## Bug report` / `## No production change` → merge prose into `## Description` body (no subheading).
- `## Out of scope` → delete. `## Why this forces ERP DSL changes` (+ `### Java — files to change`) → delete.
- `## Shipping rule` / `## Discount rule` → convert to a Gherkin `Rule:` block nested in the AC `Feature:` (scenarios indent one level under the `Rule:`).
- Canonical headings (`## Acceptance criteria`, `## Checklist`, `## External System Contract Criteria`) → leave as-is.

- [ ] **Step 1 — #72 (worked example, the trigger).** Headings today: `User story`, `Context`, `Shipping rule`, `Acceptance criteria`, `External System Contract Criteria`, `Why this forces ERP DSL changes`, `Out of scope`. Target body:
  - `## Description` ← User story prose (As a/I want/So that) + Context paragraph, as flowing prose, no subheadings.
  - `## Acceptance criteria` ← wrap the two existing scenarios under `Rule: Shipping fee is $0.10 per kg per unit` (keep `Feature:` line; indent scenarios one level under the Rule). Optionally keep the "tax is out of scope (0% tax country)" note as a one-line Rule description; otherwise drop it.
  - `## External System Contract Criteria` ← unchanged.
  - Delete `## Why this forces ERP DSL changes` (incl. `### Java — files to change`) and `## Out of scope`.
- [ ] **Step 2 — #80.** Headings: `User story`, `Context`, `Acceptance criteria`, `External system contract acceptance criteria`, `No production change`, `Out of scope`. Fold `User story` + `Context` + `No production change` into `## Description`; **rename `## External system contract acceptance criteria` → `## External System Contract Criteria`** (mandatory); delete `## Out of scope`.
- [ ] **Step 3 — #68.** Headings: `User story`, `Discount rule`, `Acceptance criteria`, `Out of scope`. Fold `User story` into `## Description`; convert `## Discount rule` to a `Rule:` block in the AC (read the rule text + scenarios, nest scenarios under the Rule); delete `## Out of scope`.
- [ ] **Step 4 — #65.** Headings: `User story`, `Acceptance criteria`. Fold `User story` into `## Description`. (Smallest edit.)
- [ ] **Step 5 — #76 (bug).** Headings: `Bug report`, `Acceptance criteria`, `Out of scope`. Fold `Bug report` into `## Description`; delete `## Out of scope`. Note: if the `Bug report` prose contains reproduction steps, consider splitting them into a canonical `## Steps to Reproduce` section (the bug form offers it) rather than burying them in Description — executor's judgment.
- [ ] **Step 6 — confirm the 6 clean tickets.** #61, #69, #70, #71, #79, #81 have only canonical H2s, but verify each has no stray prose before the first heading and no other whitelist violation (only blank lines / a single H1 title tolerated outside sections). Body-edit only if a violation is found.
- [ ] **Step 7 — issue forms.** Remove the `Legacy Acceptance Criteria` textarea block from `.github/ISSUE_TEMPLATE/story.yml`, `.github/ISSUE_TEMPLATE/bug.yml`, and `.github/ISSUE_TEMPLATE/task.yml`. Leave the existing `Acceptance Criteria` (story/bug) and `Checklist` (task) fields untouched; do not add an AC field to task.yml.

## Migration invariants (parser rules — do not violate)

- **No stray prose outside a canonical section** (`parse.go:360`): every body line must sit under a canonical heading, be blank, an HTML comment, or a single leading H1 title.
- **AC XOR Checklist** are mutually exclusive at intake. Corpus is currently clean (tasks 61/79/81 = Checklist only; stories/bugs = AC only) — don't introduce a ticket with both.
- **Checklist must be a bulleted/numbered list** (`validateChecklistIsList`).
- Heading match is **case-insensitive** (`## Acceptance criteria` is fine), and a canonical H2 **owns nested `###` subheadings** — so subheadings under `## Description` are allowed, but the user's decision is to fold *without* subheadings.

## Verification (operator-run — not agent work)

- Re-run `gh-optivem/scripts/atdd-rehearsal-loop.sh` over the affected tickets — at minimum `72 80 68 76` (the ones with non-canonical headings), plus a clean one (e.g. `65`) — and confirm each clears `PARSE_TICKET`.
- A full-corpus pass (`bash atdd-rehearsal-loop.sh`) confirms no remaining parse regressions across all 11.

## Open questions

- **#72 / #68 Rule description text:** keep each rule's clarifying note (e.g. #72's "tax is out of scope, 0% tax country") as a Gherkin `Rule:` description line, or drop it as redundant commentary? Leaning drop where the scenarios already make it self-evident.
- **#76 repro steps:** fold the whole `Bug report` into Description (user's stated decision), or split out a `## Steps to Reproduce` if the prose contains discrete repro steps? Resolve when the executor reads #76's body.
