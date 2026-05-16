#!/usr/bin/env bash
# Summarize path-level instruction-following scores without publishing point estimates.
#
# This is an aggregation aid, not a model leaderboard. It reports N, invalid
# count, and result dispersion by case/target-mode. Split valid outcomes are
# surfaced as :spec-ambiguous instead of being averaged.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

runs_dir=""
out_path=""
min_runs=5

usage() {
  cat >&2 <<'EOF'
Usage: .llm/template-only/instrument/summarize-runs.sh --runs-dir <dir> [options]

Options:
  --min-runs <n>  Minimum valid runs expected per case group. Default: 5.
  --out <path>    Write EDN summary to path instead of stdout.
  -h, --help      Show this help.

The summary emits counts and dispersion only. It intentionally does not compute
single-number pass rates or use Contract Pass Rate as a reform criterion.
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --runs-dir) shift; runs_dir="${1:-}" ;;
    --min-runs) shift; min_runs="${1:-}" ;;
    --out) shift; out_path="${1:-}" ;;
    -h|--help) usage; exit 0 ;;
    *) usage; exit 2 ;;
  esac
  shift
done

fail() {
  echo "summarize-runs failed: $*" >&2
  exit 1
}

[ -n "$runs_dir" ] || { usage; exit 2; }
[ -d "$runs_dir" ] || fail "missing runs dir: $runs_dir"
case "$min_runs" in
  ''|*[!0-9]*) fail "--min-runs must be a positive integer" ;;
esac
[ "$min_runs" -gt 0 ] || fail "--min-runs must be greater than zero"

edn_escape() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

score_keyword() {
  local key="$1"
  local file="$2"
  awk -v key="$key" '
    {
      for (i = 1; i <= NF; i++) {
        if ($i == key) {
          value = $(i + 1)
          gsub(/[{}\[\],]/, "", value)
          sub(/^:/, "", value)
          print value
          exit
        }
      }
    }
  ' "$file"
}

scores_list="$(mktemp)"
trap 'rm -f "$scores_list"' EXIT

find "$runs_dir" -type f -name score.edn | sort > "$scores_list"

declare -A group_seen
declare -A case_by_group
declare -A target_by_group
declare -A total_by_group
declare -A valid_by_group
declare -A invalid_by_group
declare -A result_count
declare -A valid_kind_seen
declare -A valid_kind_count
groups=()
score_files=0

while IFS= read -r score_file; do
  [ -n "$score_file" ] || continue
  score_files=$((score_files + 1))

  case_id="$(score_keyword ':case/id' "$score_file")"
  target_mode="$(score_keyword ':target/mode' "$score_file")"
  result_type="$(score_keyword ':result/type' "$score_file")"

  case_id="${case_id:-unknown-case}"
  target_mode="${target_mode:-unknown-target}"
  result_type="${result_type:-invalid-run}"

  group_key="$case_id|$target_mode"
  if [ -z "${group_seen[$group_key]:-}" ]; then
    group_seen["$group_key"]=1
    case_by_group["$group_key"]="$case_id"
    target_by_group["$group_key"]="$target_mode"
    total_by_group["$group_key"]=0
    valid_by_group["$group_key"]=0
    invalid_by_group["$group_key"]=0
    valid_kind_count["$group_key"]=0
    groups+=("$group_key")
  fi

  total_by_group["$group_key"]=$(( ${total_by_group[$group_key]} + 1 ))
  count_key="$group_key|$result_type"
  result_count["$count_key"]=$(( ${result_count[$count_key]:-0} + 1 ))

  if [ "$result_type" = "invalid-run" ]; then
    invalid_by_group["$group_key"]=$(( ${invalid_by_group[$group_key]} + 1 ))
  else
    valid_by_group["$group_key"]=$(( ${valid_by_group[$group_key]} + 1 ))
    kind_key="$group_key|$result_type"
    if [ -z "${valid_kind_seen[$kind_key]:-}" ]; then
      valid_kind_seen["$kind_key"]=1
      valid_kind_count["$group_key"]=$(( ${valid_kind_count[$group_key]} + 1 ))
    fi
  fi
done < "$scores_list"

result_order=(
  pass
  hard-fail
  soft-fail
  expected-stop
  near-miss
  productive-violation
  rule-pressure
  spec-ambiguous
  invalid-run
)

counts_edn() {
  local group_key="$1"
  local first=1
  local result
  printf '{'
  for result in "${result_order[@]}"; do
    local count="${result_count[$group_key|$result]:-0}"
    if [ "$count" -gt 0 ]; then
      if [ "$first" -eq 0 ]; then
        printf ' '
      fi
      printf ':%s %s' "$result" "$count"
      first=0
    fi
  done
  printf '}'
}

sole_valid_result() {
  local group_key="$1"
  local result
  for result in "${result_order[@]}"; do
    [ "$result" != "invalid-run" ] || continue
    if [ -n "${valid_kind_seen[$group_key|$result]:-}" ]; then
      printf '%s' "$result"
      return 0
    fi
  done
  printf 'invalid-run'
}

write_summary() {
  local generated_at
  generated_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

  printf '{:summary/schema 1\n'
  printf ' :summary/kind :instruction-following-run-summary\n'
  printf ' :summary/source "%s"\n' "$(edn_escape "$SCRIPT_DIR/summarize-runs.sh")"
  printf ' :summary/generated-at "%s"\n' "$(edn_escape "$generated_at")"
  printf ' :runs/root "%s"\n' "$(edn_escape "$runs_dir")"
  printf ' :score/files %s\n' "$score_files"
  printf ' :sample/min-runs %s\n' "$min_runs"
  printf ' :number/policy :counts-without-point-estimates\n'
  printf ' :groups ['

  local group_key
  local first_group=1
  for group_key in "${groups[@]}"; do
    local case_id="${case_by_group[$group_key]}"
    local target_mode="${target_by_group[$group_key]}"
    local total="${total_by_group[$group_key]}"
    local valid="${valid_by_group[$group_key]}"
    local invalid="${invalid_by_group[$group_key]}"
    local enough=false
    local ambiguity_signal=nil
    local aggregate_result
    local route

    if [ "$valid" -ge "$min_runs" ]; then
      enough=true
    fi

    if [ "$valid" -eq 0 ]; then
      aggregate_result="invalid-run"
      route="invalid-run-review"
    elif [ "${valid_kind_count[$group_key]}" -gt 1 ]; then
      aggregate_result="spec-ambiguous"
      ambiguity_signal=":split-results"
      route="questions-or-doc-improvement"
    else
      aggregate_result="$(sole_valid_result "$group_key")"
      if [ "$enough" = true ]; then
        route="manual-review"
      else
        route="needs-more-runs"
      fi
    fi

    if [ "$first_group" -eq 0 ]; then
      printf '\n           '
    fi
    printf '{:case/id :%s' "$case_id"
    printf ' :target/mode :%s' "$target_mode"
    printf ' :sample/n %s' "$total"
    printf ' :sample/valid-n %s' "$valid"
    printf ' :sample/invalid-n %s' "$invalid"
    printf ' :sample/min-runs %s' "$min_runs"
    printf ' :sample/enough? %s' "$enough"
    printf ' :dispersion/result-counts %s' "$(counts_edn "$group_key")"
    printf ' :ambiguity/signal %s' "$ambiguity_signal"
    printf ' :aggregate/result :%s' "$aggregate_result"
    printf ' :route :%s}' "$route"
    first_group=0
  done

  printf ']}\n'
}

if [ -n "$out_path" ]; then
  mkdir -p "$(dirname "$out_path")"
  write_summary > "$out_path"
  echo "summary written: $out_path"
else
  write_summary
fi
