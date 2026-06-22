# Stderr-swallow sweep — issue #74 (deferred: actions, gh-optivem, hub)

> **STATUS (2026-06-22): GENUINE FIXES DONE — defensibles + `?` rows deferred.**
> Fix bar = HYBRID. Hard-fixed all genuine `G` silent-failure sites and pushed one commit per repo:
> `hub` `9bf2dfc` (4: clone/commit/push in `pipeline-setup.sh`, token scrubbed),
> `gh-optivem` `2e2e664` (1: `gh extension remove` in `install.sh`),
> `actions` `c489e4a` (9: 6 `retry_run` delete/post sites just needed the call-site `2>&1`
> dropped — `retry_run` already logs stderr; plus `git push --delete`, `gh api …/commits`, and
> `gh extension remove` capture-and-surface).
> **Deferred (not yet done):** the ~26 defensible `D` lines were NOT annotated (the `# stderr-ok(#74)`
> marker isn't honored by the sweep tooling yet — annotating buys nothing until that lands), and the
> **4 `?` paginate rows** in `actions/cleanup-*` await a decision (drop `2>/dev/null` to surface the
> *why* on failure vs. leave — non-destructive reads whose `if !` guard already aborts).
> Findings drifted vs. the 2026-06-09 line numbers, esp. in `gh-optivem` — re-locate by content.

## TL;DR

**Why:** The recurring stderr-swallow sweep (issue #74) found 51 findings across the sibling repos `actions`, `gh-optivem`, and `hub` — and unlike the shop scope, these include the genuinely dangerous cases (the `gh repo delete` incident class: destructive `gh api --method DELETE`, `git push --delete`, credentialed `git clone`, all discarding stderr).
**End result:** Every genuine silent-failure site in the three repos captures and surfaces stderr (blessed pattern, shared helper where a call shape repeats); defensible probe/fallback/rate-limit sites are annotated `# stderr-ok(#74)`; each repo is committed separately referencing #74, and the next sweep shows RESOLVED > 0.

**Source:** https://github.com/optivem/shop/issues/74 — "Stderr-swallow sweep 2026-06-08"
**Scope (this file):** `actions`, `gh-optivem`, `hub` — 51 findings. **Deferred** out of the shop plan.
**Shop part:** tracked separately in `plans/20260609-215710-stderr-swallow-sweep-issue-74.md` (8 findings).
**Counts from the sweep (all repos):** NEW 1 · PERSISTING 58 · RESOLVED 0 (59 findings total).
**Skipped by the sweep:** `optivem/courses` (private/inaccessible); 6 `command -v <cli> &>/dev/null`
existence probes (correct pattern, excluded).

> ⚠️ These findings live in **sibling repos**, not in `shop`. Executing this plan means editing
> `optivem/actions`, `optivem/gh-optivem`, and `optivem/hub` checkouts and committing each repo
> separately (per execution step 4).

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
  vanishes from the log. Examples: the NEW `gh extension remove optivem 2>/dev/null || true`,
  `gh api ... --method DELETE >/dev/null 2>&1`, `git push origin main >/dev/null 2>&1`.
- **DEFENSIBLE by design (leave, or convert only if "resolve every finding" chosen):** non-zero exit
  *is* the control-flow signal, so stderr on the expected path is correct. Examples:
  `if gh release view ... >/dev/null 2>&1; then` (existence probe),
  `git rev-parse "${tag}^{}" 2>/dev/null || git rev-parse "${tag}"` (deref fallback),
  `gh api rate_limit ... 2>/dev/null || echo "999"` (rate-limit read with safe default).

## DECISION — fix bar = HYBRID (2026-06-22)

Same call as the shop plan (executed there, commit `c118e3bf`): hard-fix where a real
(auth/network/permission/destructive-op) failure genuinely vanishes; allowlist the lines where
non-zero exit *is* the control-flow signal, annotating each with `# stderr-ok(#74)` + rationale so
the recurring sweep drops to zero and stays there. Silence only the named expected error; never an
unexpected one.

Unlike shop (which was all-defensible), this scope has **~13 genuine `G` fixes** — the real value
of #74 lives here. The `?` paginate rows must be triaged per-line during execution (the
`if ! var=$(...)` guards detect failure but discard *why* — lean toward fixing the destructive/
unexpected ones, allowlisting pure existence reads).

Allowlist mechanism: the in-repo `# stderr-ok(#74): <reason>` annotation (no tracked sweep-config
file exists), identical to shop. Original options, for the record:

1. **Genuine risks only** — fix real-vanish lines; list probes/fallbacks with rationale.
2. **Resolve every finding** — capture-and-surface all 51; most churn.
3. **Triage first** — full per-line table then decide. ← HYBRID does this per repo at execution.

---

## Findings by repo

Paths are relative to each repo root. Preliminary class: **G** = genuine risk, **D** = defensible.
Confirm class per line during triage.

### `actions` (≈38 findings — the bulk)

| Class | Location | Line |
|---|---|---|
| **G** 🆕 | `install-gh-optivem/build-and-install.sh:7` | `gh extension remove optivem 2>/dev/null \|\| true` |
| D | `bump-patch-versions/bump.sh:17` | `if ! output=$(git ls-remote --tags … 2>/dev/null); then` |
| D | `check-changes-since-tag/check.sh:7` | `git fetch --tags --force origin >/dev/null 2>&1 \|\| true` |
| D | `check-tag-exists/check.sh:11` | `if ! output=$(git ls-remote --tags … 2>/dev/null); then` |
| D | `cleanup-deployments/cleanup-deployments.sh:140` | `sha=$(git rev-list -n 1 "$rc_tag" 2>/dev/null \|\| true)` |
| ? | `cleanup-deployments/cleanup-deployments.sh:158` | `deployments_json=$(retry_run gh api … --paginate 2>/dev/null \| jq -s 'add // []' \|\| echo '[]')` |
| **G** | `cleanup-deployments/cleanup-deployments.sh:223` | `if retry_run gh api --method DELETE … >/dev/null 2>&1; then` |
| D | `cleanup-deployments/cleanup-deployments.sh:71` | `remaining=$(gh api rate_limit … 2>/dev/null \|\| echo "")` |
| D | `cleanup-deployments/cleanup-deployments.sh:74` | `reset=$(gh api rate_limit … 2>/dev/null \|\| echo "")` |
| **G** | `cleanup-ghcr-orphan-manifests/cleanup-ghcr-orphan-manifests.sh:151` | `if retry_run gh api --method DELETE … >/dev/null 2>&1; then` |
| D | `cleanup-ghcr-orphan-manifests/cleanup-ghcr-orphan-manifests.sh:57` | `remaining=$(gh api rate_limit … 2>/dev/null \|\| echo "")` |
| D | `cleanup-ghcr-orphan-manifests/cleanup-ghcr-orphan-manifests.sh:60` | `reset=$(gh api rate_limit … 2>/dev/null \|\| echo "")` |
| ? | `cleanup-ghcr-orphan-manifests/cleanup-ghcr-orphan-manifests.sh:93` | `if ! versions_raw=$(retry_run gh api … --paginate 2>/dev/null); then` |
| ? | `cleanup-prereleases/cleanup-prereleases.sh:108` | `if versions_raw=$(retry_run gh api … --paginate 2>/dev/null); then` |
| D | `cleanup-prereleases/cleanup-prereleases.sh:133` | `iso=$(git log -1 --format=%aI "$tag" 2>/dev/null \|\| true)` |
| **G** | `cleanup-prereleases/cleanup-prereleases.sh:162` | `if ! git push origin --delete "refs/tags/$tag" 2>/dev/null; then` |
| D | `cleanup-prereleases/cleanup-prereleases.sh:165` | `git tag -d "$tag" 2>/dev/null \|\| true` |
| **G** | `cleanup-prereleases/cleanup-prereleases.sh:193` | `if retry_run gh release delete … --cleanup-tag >/dev/null 2>&1; then` |
| **G** | `cleanup-prereleases/cleanup-prereleases.sh:235` | `if retry_run gh api --method DELETE … >/dev/null 2>&1; then` |
| **G** | `cleanup-prereleases/cleanup-prereleases.sh:482` | `if retry_run gh api --method DELETE "repos/…/releases/$rel_id" >/dev/null 2>&1; then` |
| D | `cleanup-prereleases/cleanup-prereleases.sh:63` | `remaining=$(gh api rate_limit … 2>/dev/null \|\| echo "")` |
| D | `cleanup-prereleases/cleanup-prereleases.sh:66` | `reset=$(gh api rate_limit … 2>/dev/null \|\| echo "")` |
| ? | `cleanup-prereleases/cleanup-prereleases.sh:85` | `if all_releases_raw=$(retry_run gh api … --paginate 2>/dev/null); then` |
| D | `resolve-latest-prerelease-tag/resolve.sh:22` | `TAG=$(git ls-remote --tags … 2>/dev/null …` |
| D | `resolve-latest-prerelease-with-status/resolve.sh:26` | `sha=$(git rev-parse "${tag}^{}" 2>/dev/null \|\| git rev-parse "${tag}")` |
| D | `scripts/sync-shared.sh:59` | `sha=$(git hash-object "$src" 2>/dev/null \|\| echo "unknown")` |
| D | `shared/clear-persisted-credentials.sh:20` | `git config --local --unset-all … 2>/dev/null \|\| true` |
| D | `shared/clear-persisted-credentials.sh:22` | `[ -n "$key" ] && git config --local --unset-all "$key" 2>/dev/null \|\| true` |
| D | `shared/clear-persisted-credentials.sh:23` | `done < <(git config --local --name-only --get-regexp … 2>/dev/null \|\| true)` |
| D | `shared/gh-rate-limit.sh:32` | `remaining=$(gh api rate_limit … 2>/dev/null \|\| echo "")` |
| D | `shared/gh-rate-limit.sh:40` | `reset_ts=$(gh api rate_limit … 2>/dev/null \|\| echo "0")` |
| D | `trigger-and-wait-for-workflow/trigger.sh:10` | `reset=$(gh api rate_limit … 2>/dev/null \|\| echo "0")` |
| **G** | `trigger-and-wait-for-workflow/trigger.sh:31` | `ref_sha=$(gh api "repos/…/commits/$REF" --jq .sha 2>/dev/null)` |
| D | `trigger-and-wait-for-workflow/trigger.sh:8` | `remaining=$(gh api rate_limit … 2>/dev/null \|\| echo "999")` |
| D | `validate-tag-exists/validate.sh:11` | `if git ls-remote --tags … 2>/dev/null \| grep -q …; then` |
| D | `wait-for-endpoints/wait.sh:38` | `if curl -f "$URL" > /dev/null 2>&1; then` (poll loop) |
| D | `wait-for-workflow/wait.sh:18` | `remaining=$(gh api rate_limit … 2>/dev/null \|\| echo "999")` |
| D | `wait-for-workflow/wait.sh:20` | `reset=$(gh api rate_limit … 2>/dev/null \|\| echo "0")` |

### `gh-optivem` (9 findings)

| Class | Location | Line |
|---|---|---|
| D | `scripts/atdd-rehearsal-cleanup.sh:223` | `ahead="$(git -C … rev-list --count "$base..$br" 2>/dev/null \|\| echo 0)"` |
| D | `scripts/atdd-rehearsal-cleanup.sh:298` | `git -C … branch -D "$b" 2>/dev/null \|\| log "  ! branch -D failed for $b"` |
| D | `scripts/atdd-rehearsal-cleanup.sh:302` | `git -C … worktree prune 2>/dev/null \|\| true` |
| D | `scripts/atdd-rehearsal.sh:302` | `git -C … branch -D "$BRANCH" 2>/dev/null \|\| true` |
| D | `scripts/atdd-rehearsal.sh:307` | `git -C … worktree prune 2>/dev/null \|\| true` |
| D | `scripts/install.sh:37` | `gh auth status >/dev/null 2>&1 \|\| die "…"` (probe → explicit die) |
| D | `scripts/install.sh:39` | `SHA=$(git rev-parse --short HEAD 2>/dev/null \|\| echo unknown)` |
| D | `scripts/install.sh:40` | `if ! git diff --quiet 2>/dev/null \|\| ! git diff --cached --quiet 2>/dev/null; then` |
| **G** | `scripts/install.sh:48` | `gh extension remove optivem 2>/dev/null \|\| true` (same shape as NEW) |

### `hub` (4 findings)

| Class | Location | Line |
|---|---|---|
| **G** | `scripts/pipeline-setup.sh:224` | `git clone "https://x-access-token:…@github.com/…" repo 2>/dev/null` |
| **G** | `scripts/pipeline-setup.sh:228` | `git clone "https://github.com/${TEMPLATE_REPO}.git" template 2>/dev/null` |
| **G** | `scripts/pipeline-setup.sh:285` | `git commit -m "…" > /dev/null 2>&1` |
| **G** | `scripts/pipeline-setup.sh:286` | `git push origin main > /dev/null 2>&1` |

> `?` rows are `gh api ... --paginate 2>/dev/null` capture-into-var-then-test patterns — borderline;
> the `if ! var=$(...)` guards detect failure but still discard *why*. Decide during triage.

---

## Execution plan (once fix bar is chosen)

1. **Triage** each `G`/`?`/`D` line in context against the criteria above; lock the final set.
2. **Apply** the blessed capture-and-surface pattern to the chosen set, per repo.
   - Reuse/extract a shared helper where a repo has many identical call shapes (e.g. the
     `gh api --method DELETE` deletes in `cleanup-*`).
3. **Verify** per repo:
   - `bash -n <script>` syntax check on every edited script; `shellcheck` if available.
4. **Commit** each dirty repo via the user's skills (`/commit` per repo or `/github-commit-push-all`)
   — never raw git. One focused commit per repo referencing issue #74.
5. **Close the loop:** comment on issue #74 with resolved counts (and which were intentionally left
   as defensible, if fix bar = "genuine risks only"), so the next sweep shows RESOLVED > 0.

## Notes / risks

- `actions` composite-action `.yml` files embed bash in `run:` blocks — edits must respect YAML
  block scalar indentation.
- `retry_run` wrappers (in `cleanup-*`) already re-run on failure; surfacing stderr must not break
  the retry loop's exit-code expectations — capture inside, emit on final failure.
- Don't convert the 6 `command -v <cli> &>/dev/null` probes (already excluded by the sweep as correct).
