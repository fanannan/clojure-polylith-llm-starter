#!/usr/bin/env bash
# Template-maintenance smoke test for the benchmark setup harness.
#
# This checks that setup-run.sh can prepare a demo repo, install its observer
# hook, and drive the generated marker commands without a human reviewer. The
# resulting run is simulation smoke only; it is not benchmark evidence.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEMPLATE_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
BASE="${TMPDIR:-/tmp}/clojure-polylith-template-benchmark-smoke"
DEMO_DIR="$BASE/demo"
OUT="$BASE/setup.out"
run_record=""

cleanup() {
  rm -rf "$BASE"
  if [[ -n "$run_record" && "$run_record" == "$TEMPLATE_ROOT/.llm/template-only/benchmark/runs/"* ]]; then
    local year_dir
    year_dir="$(dirname "$run_record")"
    rm -rf "$run_record"
    rmdir "$year_dir" "$TEMPLATE_ROOT/.llm/template-only/benchmark/runs" 2>/dev/null || true
  fi
}
trap cleanup EXIT

fail() {
  echo "benchmark setup smoke failed: $*" >&2
  exit 1
}

require_file() {
  local path="$1"
  [ -f "$path" ] || fail "missing file: $path"
}

require_absent() {
  local path="$1"
  [ ! -e "$path" ] || fail "unexpected retained path: $path"
}

require_grep() {
  local pattern="$1"
  local path="$2"
  grep -q "$pattern" "$path" || fail "missing pattern '$pattern' in $path"
}

rm -rf "$BASE"
mkdir -p "$BASE"

(
  cd "$TEMPLATE_ROOT"
  ./.llm/template-only/benchmark/setup-run.sh \
    --scenario webhook-idempotency-processor \
    --agent simulation-smoke \
    --model harness-smoke \
    --tool-mode simulation-smoke \
    --demo-dir "$DEMO_DIR" \
    --no-prompt \
    --allow-dirty > "$OUT"
)

run_record="$(awk -F': ' '/Template run record:/ {print $2}' "$OUT")"
[ -n "$run_record" ] || fail "could not read template run record path"

demo_run_dir="$(find "$DEMO_DIR/.llm/benchmark-runs" -mindepth 1 -maxdepth 1 -type d | head -1)"
[ -n "$demo_run_dir" ] || fail "could not find generated demo run dir"

require_absent "$DEMO_DIR/.llm/template-only"
require_file "$run_record/metadata.edn"
require_file "$run_record/run.md"
require_file "$run_record/git-snapshots.edn"
require_file "$demo_run_dir/metadata.edn"
require_file "$demo_run_dir/run.md"
require_file "$demo_run_dir/approve-next-segment.sh"
require_file "$demo_run_dir/simulate-approval.sh"
require_file "$demo_run_dir/mark-terminal-state.sh"

require_grep ':agent/name "simulation-smoke"' "$run_record/metadata.edn"
require_grep ':agent/model "harness-smoke"' "$run_record/metadata.edn"
require_grep ':post-commit' "$run_record/git-snapshots.edn"

(
  cd "$DEMO_DIR"
  "$demo_run_dir/simulate-approval.sh" --level L1 --note "simulated approval for harness smoke"
  "$demo_run_dir/mark-terminal-state.sh" --state void --note "simulation smoke completed"
)

require_grep ':approval/source :simulated-llm' "$demo_run_dir/events.edn"
require_grep ':approval/source :simulated-llm' "$run_record/events.edn"
require_grep ':state :void' "$demo_run_dir/events.edn"
require_grep ':state :void' "$run_record/events.edn"

echo "benchmark setup smoke: OK"
