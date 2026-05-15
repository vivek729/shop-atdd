#!/usr/bin/env bash
# Run `gh optivem config migrate` against every gh-optivem-*.yaml in the
# repo root.
#
# `config migrate` back-fills required fields onto an existing
# gh-optivem.yaml (today: adds project.provider, inferred from
# project.url). It is idempotent — yamls already on the current schema
# are reported as "no migration needed".
#
# Unlike compile-all.sh this iterates *every* yaml (including
# `*-legacy.yaml`), because each yaml is its own file on disk and needs
# its own migration pass.
#
# Exits non-zero on any failure (zero-failures policy). Continues past
# the first failure so a single run reports every broken yaml.

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$REPO_ROOT"

shopt -s nullglob
configs=(gh-optivem-*.yaml)
shopt -u nullglob

if [ ${#configs[@]} -eq 0 ]; then
  echo "ERROR: no gh-optivem-*.yaml files found in $REPO_ROOT" >&2
  exit 1
fi

# Result rows: "config|status|duration_seconds"
declare -a RESULTS=()
OVERALL_START=$(date +%s)

for cfg in "${configs[@]}"; do
  echo
  echo "=================================================================="
  echo "  $cfg"
  echo "=================================================================="

  start=$(date +%s)
  status="PASSED"
  gh optivem -c "$cfg" config migrate || status="FAILED"
  end=$(date +%s)
  RESULTS+=("$cfg|$status|$((end - start))")
done

OVERALL_END=$(date +%s)

# Summary
printf "\n==================================================================\n"
printf "  SUMMARY\n"
printf "==================================================================\n\n"
printf "%-44s %-10s %s\n" "Config" "Result" "Duration"
printf -- "------------------------------------------------------------------\n"

failures=0
for row in "${RESULTS[@]}"; do
  IFS='|' read -r cfg status dur <<< "$row"
  printf "%-44s %-10s %02d:%02d\n" "$cfg" "$status" $((dur/60)) $((dur%60))
  if [ "$status" = "FAILED" ]; then failures=$((failures+1)); fi
done

printf -- "------------------------------------------------------------------\n"
total_dur=$((OVERALL_END - OVERALL_START))
printf "Total duration: %02d:%02d\n" $((total_dur/60)) $((total_dur%60))

if [ "$failures" -gt 0 ]; then
  printf "\n%d yaml(s) FAILED to migrate.\n" "$failures" >&2
  exit 1
fi
printf "\nAll yamls migrated cleanly.\n"
