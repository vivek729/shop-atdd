# Stderr-swallow sweep — issue #74 (shop)

> **STATUS (2026-06-16): NOT STARTED — blocked on the fix-bar decision below.**
> Only the plan exists (commits `6727ce67` create, `58a283e1` split out other-repos). No fix
> commits. All 8 shop findings verified unchanged in the working tree. The `## OPEN DECISION —
> fix bar` has not been made, so no edits have been applied. Reminder: all 8 shop findings are
> preliminarily **D (defensible)** → under fix bar #1 this repo is a no-op (document rationale +
> close the loop on #74); real code edits only happen under fix bar #2 or if triage reclassifies
> a line. The genuine `gh repo delete` silent-failure risk lives in the **deferred** other-repos
> plan, not here.

**Source:** https://github.com/optivem/shop/issues/74 — "Stderr-swallow sweep 2026-06-08"
**Scope (this file):** `shop` repo only — 8 findings.
**Other repos deferred:** `actions`, `gh-optivem`, `hub` (51 findings) split out to
`plans/deferred/20260609-215710-stderr-swallow-sweep-issue-74-other-repos.md`.
**Counts from the sweep (all repos):** NEW 1 · PERSISTING 58 · RESOLVED 0 (59 findings total).
**Skipped by the sweep:** `optivem/courses` (private/inaccessible); 6 `command -v <cli> &>/dev/null`
existence probes (correct pattern, excluded).

## The principle

When an external CLI (`gh`, `git`, `docker`, `curl`, `gcloud`, …) fails, its stderr must reach the
workflow log. The real incident: `optivem/gh-optivem` cleanup workflow run 25091710528 exited 1
**silently** because `gh repo delete`'s stderr was discarded — hours of guesswork that 30 seconds of
stderr would have answered.

## Blessed pattern (from `shop/teardown-gcp.sh` + `gh-optivem/scripts/cleanup-orphans.sh`)

Capture stderr (`2>&1`), and on failure surface it with an explanatory message. Quietly swallow
**only** the specific expected error class (e.g. "not found"); everything else goes to the log.

```bash
delete_gh_thing() {
  local kind="$1" name="$2" err rc
  err=$(gh "$kind" delete "$name" --yes 2>&1) && return 0
  rc=$?
  if [[ "$err" == *"could not find"* || "$err" == *"not found"* || "$err" == *"HTTP 404"* ]]; then
    echo "  (skipped $kind $name: not present)"
  else
    echo "  ⚠️  failed to delete $kind $name (exit $rc): $err" >&2
  fi
}
```

For best-effort calls that should soft-fail, capture to a temp file and `cat "$err" >&2` plus an
explanatory message, rather than redirecting to `/dev/null`.

## Triage criteria

- **GENUINE silent-failure risk (fix):** an external command whose failure is *unexpected*
  (auth/network/permission/destructive-op failure) and whose stderr is discarded, so a real failure
  vanishes from the log.
- **DEFENSIBLE by design (leave, or convert only if "resolve every finding" chosen):** non-zero exit
  *is* the control-flow signal, so stderr on the expected path is correct. Examples:
  `if gh release view ... >/dev/null 2>&1; then` (existence probe),
  `git rev-parse "${tag}^{}" 2>/dev/null || git rev-parse "${tag}"` (deref fallback).

## OPEN DECISION — fix bar (blocking)

Pick before applying edits:

1. **Genuine risks only** — fix the lines where a real failure would silently vanish; leave
   probe/fallback patterns as-is but list them with rationale. Fewer, higher-value edits.
2. **Resolve every finding** — apply capture-and-surface to all, including existence probes and
   deref fallbacks (surfacing only the unexpected error class). Literal "resolve each finding"
   reading; more churn, more helper functions.
3. **Triage first, then decide** — produce a full per-line triage table, approve the set, then apply.

> Note: all 8 shop findings are preliminarily **D (defensible)** — under fix bar #1 this repo is a
> no-op (document rationale only). Real edits here only happen under fix bar #2 or if triage
> reclassifies a line.

---

## Findings — `shop` (8 findings)

Paths relative to repo root. Preliminary class: **G** = genuine risk, **D** = defensible.

| Class | Location | Line |
|---|---|---|
| D | `.github/actions/build-flavor-rc-manifest/action.yml:78` | `sha=$(git rev-parse "${tag}^{}" 2>/dev/null \|\| git rev-parse "${tag}")` |
| D | `.github/actions/create-meta-release-tag/action.yml:69` | `if gh release view "$RELEASE_TAG" >/dev/null 2>&1; then` (existence probe) |
| D | `.github/actions/find-flavor-rcs/action.yml:86` | `git fetch origin "refs/tags/${rc}:refs/tags/${rc}" 2>/dev/null \|\| true` |
| D | `.github/actions/find-flavor-rcs/action.yml:92` | `sha=$(git rev-parse "${rc}^{}" 2>/dev/null \|\| git rev-parse "${rc}")` |
| D | `scripts/atdd-rehearsal-end.sh:61` | `if commit_count=$(git rev-list --count "$branch" "^HEAD" 2>/dev/null); then` |
| D | `system-test/dotnet/run-sonar.sh:28` | `dotnet tool install --global dotnet-sonarscanner 2>/dev/null \|\| true` |
| D | `system/monolith/dotnet/run-sonar.sh:27` | `dotnet tool install --global dotnet-sonarscanner 2>/dev/null \|\| true` |
| D | `system/multitier/backend-dotnet/run-sonar.sh:27` | `dotnet tool install --global dotnet-sonarscanner 2>/dev/null \|\| true` |

---

## Execution plan (once fix bar is chosen)

1. **Triage** each line in context against the criteria above; lock the final set.
2. **Apply** the blessed capture-and-surface pattern to the chosen set.
3. **Verify:**
   - `bash -n <script>` syntax check on every edited script; `shellcheck` if available.
   - changed `.github/actions/*.yml` are `run:` blocks — `bash -n` the extracted snippet;
     run `./compile-all.sh` only if code (not just scripts) changed (it won't be here).
4. **Commit** via the user's skills (`/commit`) — never raw git. One focused commit referencing
   issue #74.
5. **Close the loop:** comment on issue #74 with resolved counts for shop (and which were
   intentionally left as defensible, if fix bar = "genuine risks only").

## Notes / risks

- `.github/actions/*.yml` files embed bash in `run:` blocks — edits must respect YAML block scalar
  indentation.
- Don't convert the 6 `command -v <cli> &>/dev/null` probes (already excluded by the sweep as correct).
