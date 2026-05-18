#!/usr/bin/env bash
# Validate that cross-lang gh-optivem template renderings pass schema
# validation.
#
# The cross-lang CI workflow (cross-lang-system-verification.yml) generates
# gh-optivem.yaml configs at runtime by rendering
# `.github/workflows/templates/cross-lang-{monolith,multitier}.yaml.tmpl`
# with envsubst. Because these templates aren't valid yamls on their own
# (they contain `${SYSTEM_LANG}` / `${TEST_LANG}` placeholders), they're
# missed by every other local check — `compile-all.sh` and
# `migrate-all-gh-optivem-config.sh` both glob `gh-optivem-*.yaml` in the
# repo root. Schema drift between the templates and the gh-optivem CLI's
# projectconfig schema only surfaces when CI runs the cross-lang job,
# which is the most expensive place to discover it.
#
# This script renders each template with sample (SYSTEM_LANG, TEST_LANG,
# TEST_CONFIG) values and runs `gh optivem config validate` on each
# output. Catches schema drift in seconds without standing up any SUT.
#
# One rendering per (arch, variant) is sufficient: the placeholders only
# affect string substitutions, not the schema shape, so a single combo
# exercises every required key.
#
# Exits non-zero on any failure (zero-failures policy). Continues past
# the first failure so one run reports every broken template.

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$REPO_ROOT"

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

# Result rows: "label|status|duration_seconds"
declare -a RESULTS=()
OVERALL_START=$(date +%s)

export SYSTEM_LANG=java
export TEST_LANG=dotnet

for arch in monolith multitier; do
  template=".github/workflows/templates/cross-lang-${arch}.yaml.tmpl"
  if [ ! -f "$template" ]; then
    echo "ERROR: template not found: $template" >&2
    RESULTS+=("${arch}/missing|FAILED|0")
    continue
  fi
  for variant in latest legacy; do
    label="${arch}/${variant}"
    out="$TMP_DIR/${arch}-${variant}.yaml"

    echo
    echo "=================================================================="
    echo "  $label"
    echo "=================================================================="

    if [ "$variant" = legacy ]; then
      export TEST_CONFIG="system-test/${TEST_LANG}/tests.legacy.yaml"
    else
      export TEST_CONFIG="system-test/${TEST_LANG}/tests.yaml"
    fi

    start=$(date +%s)
    status="PASSED"
    # shellcheck disable=SC2016
    envsubst '${SYSTEM_LANG} ${TEST_LANG} ${TEST_CONFIG}' \
      < "$template" \
      > "$out" \
      && gh optivem -c "$out" config validate \
      || status="FAILED"
    end=$(date +%s)
    RESULTS+=("$label|$status|$((end - start))")
  done
done

OVERALL_END=$(date +%s)

# Summary
printf "\n==================================================================\n"
printf "  SUMMARY\n"
printf "==================================================================\n\n"
printf "%-30s %-10s %s\n" "Template" "Result" "Duration"
printf -- "------------------------------------------------------------------\n"

failures=0
for row in "${RESULTS[@]}"; do
  IFS='|' read -r label status dur <<< "$row"
  printf "%-30s %-10s %02d:%02d\n" "$label" "$status" $((dur/60)) $((dur%60))
  if [ "$status" = "FAILED" ]; then failures=$((failures+1)); fi
done

printf -- "------------------------------------------------------------------\n"
total_dur=$((OVERALL_END - OVERALL_START))
printf "Total duration: %02d:%02d\n" $((total_dur/60)) $((total_dur%60))

if [ "$failures" -gt 0 ]; then
  printf "\n%d template(s) FAILED to validate.\n" "$failures" >&2
  exit 1
fi
printf "\nAll cross-lang templates validate cleanly.\n"
