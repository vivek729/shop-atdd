# Stderr-swallow sweep — issue #74 (shop)

> **STATUS (2026-06-22): DONE — shop fixes shipped; sweep routine disabled; issue #74 closed.**
> Fix bar = HYBRID. Shop executed in commit `c118e3bf`: **3 hard-fixes** (`dotnet tool install`
> best-effort lines) + **5 annotated-defensible** (deref fallbacks, best-effort `git fetch`,
> rev-list count, already-safe existence probe). Sibling repos done too (see deferred plan):
> `hub` `9bf2dfc`, `gh-optivem` `2e2e664`, `actions` `c489e4a`.
> **This plan is now the standalone record** — the weekly automated detector that produced #74 has
> been **disabled** (see `## Detection routine` below), so no further tickets will be generated;
> #74 was closed as an acted-on snapshot.

## TL;DR

**Why:** A real incident (gh-optivem cleanup run exited 1 silently because `gh repo delete`'s stderr was discarded) prompted a repo-wide sweep for places where an external CLI's failure stderr is swallowed. Issue #74 tracks the 8 `shop`-repo findings.
**End result:** Every genuine silent-failure site in `shop` captures and surfaces stderr using the blessed capture-and-surface pattern, with defensible probe/fallback patterns documented as intentionally left; issue #74's loop is closed with resolved-vs-defensible counts for the repo.

**Source:** https://github.com/optivem/shop/issues/74 — "Stderr-swallow sweep 2026-06-08"
**Scope (this file):** `shop` repo only — 8 findings.
**Other repos deferred:** `actions`, `gh-optivem`, `hub` (51 findings) split out to
`plans/deferred/20260609-215710-stderr-swallow-sweep-issue-74-other-repos.md`.
**Counts from the sweep (all repos):** NEW 1 · PERSISTING 58 · RESOLVED 0 (59 findings total).
**Skipped by the sweep:** `optivem/courses` (private/inaccessible); 6 `command -v <cli> &>/dev/null`
existence probes (correct pattern, excluded).

## Detection routine (DISABLED 2026-06-22)

Issue #74 was **not hand-filed** — it was the output of a weekly scheduled cloud routine
(`stderr-swallow-sweep`, `trig_017M2gynFB5ppY25HTMaXUau`, cron `0 7 * * 1` = Mondays 07:00 UTC).
Each run scanned the academy repos, diffed against the prior tracking issue, and opened a new dated
`Stderr-swallow sweep <date>` issue when there were NEW or RESOLVED entries. It is **report-only**
(never edits files / opens PRs). It has been **disabled** (not deleted — the API can't delete;
re-enable at https://claude.ai/code/routines) so the plan files, not a recurring ticket, are the
record. To resume detection, re-enable the routine or run its logic manually.

Detector spec (preserved here so this plan is self-contained):
- **Targets:** `*.sh` anywhere; `.github/workflows/**/*.{yml,yaml}`; `.github/actions/**/action.{yml,yaml}`.
  Skips `gradlew*`/`mvnw*` and anything under `.claude/`.
- **Repos:** shop, gh-optivem, eshop, eshop-tests, hub, courses, github-utils, optivem-testing, actions.
- **Redirect patterns flagged:** `2>/dev/null`, `>/dev/null 2>&1`, `&>/dev/null`, `2>&1 >/dev/null`.
- **In-scope CLIs (must appear on the same line):** `gh`, `git`, `docker`, `curl`, `gcloud`,
  `sonar-scanner`, `gradle`, `dotnet`, `npm`, `aws`, `az`, `kubectl`, `./gradlew`, `./mvnw`, `mvn`, `terraform`.
- **Excluded idioms:** lines starting `command -v ` or `ls `; redirects on `printf|echo|cat|find|grep|sed|awk|tr|wc|head|tail|sort|uniq`.

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

## DECISION — fix bar = HYBRID (2026-06-22)

Chosen over the three original options below because the sweep is **recurring**: the long-term win
is driving the sweep to **zero standing findings** so future runs are pure signal — but without
rewriting correct one-liners into verbose defensive blocks where the safety gain is negligible.

**The rule:** hard-fix only where a real (auth/network/permission) failure genuinely vanishes;
allowlist the lines where non-zero exit *is* the control-flow signal (and silence only the named
expected error). This honors the standing "never swallow stderr" intent — no *unexpected* failure
ever vanishes — while documenting the few legitimately-silenced *expected* errors explicitly
instead of hiding them.

Disposition (5 allowlist / 3 fix) is locked in the findings table. Original options, for the
record:

1. **Genuine risks only** — fix real-vanish lines; leave probes/fallbacks listed with rationale.
2. **Resolve every finding** — capture-and-surface everywhere; most churn, verbose deref blocks.
3. **Triage first** — full per-line table then decide. ← effectively what HYBRID did.

### Allowlist mechanism

There is **no tracked sweep-config file in `shop`** — the sweep runs outside this repo and excluded
the 6 `command -v` probes by judgment, not via a list. So the durable, in-repo allowlist is an
**inline annotation comment** on each defensible line — convention modeled on `# shellcheck
disable=` — that both future sweep runs and humans can see and respect:

```bash
# stderr-ok(#74): <reason — which expected error is silenced and why a real failure can't hide here>
```

Teaching the sweep tooling to programmatically honor `# stderr-ok(...)` markers is a **sweep-side
change that lives outside `shop`** (track against issue #74 / the deferred other-repos plan). Until
then the markers are documentation + the rationale recorded here and in the #74 close-out comment.

---

## Findings — `shop` (8 findings)

Paths relative to repo root. **Disposition** (HYBRID): **FIX** = hard-fix (capture-and-surface);
**ALLOW** = annotate `# stderr-ok(#74)` and leave (non-zero exit is the control-flow signal).

| Disposition | Location | Line | Why |
|---|---|---|---|
| ALLOW | `.github/actions/build-flavor-rc-manifest/action.yml:78` | `sha=$(git rev-parse "${tag}^{}" 2>/dev/null \|\| git rev-parse "${tag}")` | deref fallback: error = "lightweight tag", expected; `\|\|` peels via plain ref |
| ALLOW | `.github/actions/create-meta-release-tag/action.yml:69` | `if gh release view "$RELEASE_TAG" >/dev/null 2>&1; then` | existence probe — failure **already surfaced** at lines 74–78 (`::error::` + `cat`) |
| ALLOW | `.github/actions/find-flavor-rcs/action.yml:86` | `git fetch origin "refs/tags/${rc}:refs/tags/${rc}" 2>/dev/null \|\| true` | best-effort fetch; line 87 re-verifies the tag and errors loudly if still missing |
| ALLOW | `.github/actions/find-flavor-rcs/action.yml:92` | `sha=$(git rev-parse "${rc}^{}" 2>/dev/null \|\| git rev-parse "${rc}")` | deref fallback, same as :78 |
| ALLOW | `scripts/atdd-rehearsal-end.sh:61` | `if commit_count=$(git rev-list --count "$branch" "^HEAD" 2>/dev/null); then` | informational warning only; non-zero = branch gone, the expected skip path |
| **FIX** | `system-test/dotnet/run-sonar.sh:28` | `dotnet tool install --global dotnet-sonarscanner 2>/dev/null \|\| true` | best-effort install hides real network/feed/perms failure → capture-and-surface |
| **FIX** | `system/monolith/dotnet/run-sonar.sh:27` | `dotnet tool install --global dotnet-sonarscanner 2>/dev/null \|\| true` | same |
| **FIX** | `system/multitier/backend-dotnet/run-sonar.sh:27` | `dotnet tool install --global dotnet-sonarscanner 2>/dev/null \|\| true` | same |

---

## Execution plan (HYBRID — ready)

### Step 1 — FIX the 3 `run-sonar.sh` tool-install lines

`system-test/dotnet/run-sonar.sh:28`, `system/monolith/dotnet/run-sonar.sh:27`,
`system/multitier/backend-dotnet/run-sonar.sh:27` — identical line in each. Replace:

```bash
dotnet tool install --global dotnet-sonarscanner 2>/dev/null || true
```

with capture-and-surface (silences only the expected "already installed", logs anything else,
stays non-fatal):

```bash
if ! install_err=$(dotnet tool install --global dotnet-sonarscanner 2>&1); then
  if [[ "$install_err" == *"already installed"* ]]; then
    :  # expected — tool present from a prior run
  else
    echo "⚠️  dotnet-sonarscanner install failed (continuing): $install_err" >&2
  fi
fi
```

### Step 2 — ALLOW the 5 defensible lines (annotate, don't rewrite)

Add a trailing/preceding `# stderr-ok(#74): <reason>` marker (mind YAML block-scalar indentation in
the `.github/actions/*.yml` `run:` blocks) so the next sweep skips them. Reasons per the findings
table. No behavior change.

### Step 3 — Verify

- `bash -n <script>` on every edited `run-sonar.sh`; `shellcheck` if available.
- For the annotated `.github/actions/*.yml`: extract the `run:` snippet and `bash -n` it to confirm
  indentation/comment placement didn't break the block scalar.
- No `./compile-all.sh` needed — scripts only, no application code changes.

### Step 4 — Commit

Via `/commit` (never raw git; **ask first** per repo convention). One focused commit referencing
issue #74: "fix 3 best-effort installs, annotate 5 defensible stderr sites".

### Step 5 — Close the loop on #74

Comment on issue #74 with shop disposition: **3 fixed, 5 documented-defensible** (with the
one-line reasons), and flag the follow-up: teach the sweep tooling to honor `# stderr-ok(#74)`
markers (lives outside `shop` — track on the deferred other-repos plan).

## Notes / risks

- `.github/actions/*.yml` files embed bash in `run:` blocks — edits must respect YAML block scalar
  indentation.
- Don't convert the 6 `command -v <cli> &>/dev/null` probes (already excluded by the sweep as correct).
