#!/usr/bin/env bash
# Template-maintenance smoke test for the Instruction-Following Instrument setup.
#
# This verifies target creation and outside-observer isolation only. It does not
# launch an LLM and does not score model behavior.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEMPLATE_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
BASE="${TMPDIR:-/tmp}/clojure-polylith-template-instrument-smoke"
TEMPLATE_TARGET="$BASE/template-target"
PROJECT_TARGET="$BASE/project-target"
TEMPLATE_OUT="$BASE/template.out"
PROJECT_OUT="$BASE/project.out"
template_run_record=""
project_run_record=""

cleanup() {
  rm -rf "$BASE"
  for run_record in "$template_run_record" "$project_run_record"; do
    if [[ -n "$run_record" && "$run_record" == "$TEMPLATE_ROOT/.llm/template-only/instrument/runs/"* ]]; then
      local year_dir
      year_dir="$(dirname "$run_record")"
      rm -rf "$run_record"
      rmdir "$year_dir" "$TEMPLATE_ROOT/.llm/template-only/instrument/runs" 2>/dev/null || true
    fi
  done
}
trap cleanup EXIT

fail() {
  echo "instrument setup smoke failed: $*" >&2
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

extract_value() {
  local label="$1"
  local path="$2"
  awk -F': ' -v label="$label" '$0 ~ label {print $2}' "$path"
}

rm -rf "$BASE"
mkdir -p "$BASE"

(
  cd "$TEMPLATE_ROOT"
  ./.llm/template-only/instrument/setup-run.sh \
    --case template-mode-no-project-owned-write \
    --target-mode template \
    --agent simulation-smoke \
    --model harness-smoke \
    --tool-mode simulation-smoke \
    --target-dir "$TEMPLATE_TARGET" \
    --no-prompt \
    --allow-dirty > "$TEMPLATE_OUT"
)

template_run_record="$(extract_value "Template run record:" "$TEMPLATE_OUT")"
template_observer="$(extract_value "Observer store:" "$TEMPLATE_OUT")"
[ -n "$template_run_record" ] || fail "could not read template run record path"
[ -n "$template_observer" ] || fail "could not read template observer path"

case "$template_observer" in
  "$TEMPLATE_TARGET"/*) fail "observer store must not be inside template target: $template_observer" ;;
esac

require_file "$template_run_record/metadata.edn"
require_file "$template_run_record/run.md"
require_file "$template_observer/metadata.edn"
require_file "$template_observer/agent-prompt.txt"
require_file "$template_observer/capture-observation.sh"
require_file "$template_observer/mark-terminal-state.sh"
require_absent "$TEMPLATE_TARGET/.llm/template-only/instrument"
require_grep ':contract/mode :instrumented-contract' "$template_run_record/metadata.edn"
require_grep ':agent/visible-test-context? false' "$template_run_record/metadata.edn"
require_grep ':target/mode :template' "$template_run_record/metadata.edn"
require_grep 'components/sample' "$template_observer/agent-prompt.txt"

if git -C "$TEMPLATE_TARGET" ls-files | grep -q 'observer-runs\|instrument/runs'; then
  fail "observer or instrument run files were committed into template target"
fi

"$template_observer/capture-observation.sh" --note "template target smoke"
"$template_observer/mark-terminal-state.sh" --state observed --note "template target smoke complete"
require_grep ':event/type :observation-captured' "$template_run_record/events.edn"
require_grep ':state :observed' "$template_run_record/events.edn"

(
  cd "$TEMPLATE_ROOT"
  ./.llm/template-only/instrument/setup-run.sh \
    --case project-bootstrap-prioritizes-identity \
    --target-mode project \
    --project-phase bootstrap \
    --agent simulation-smoke \
    --model harness-smoke \
    --tool-mode simulation-smoke \
    --target-dir "$PROJECT_TARGET" \
    --no-prompt \
    --allow-dirty > "$PROJECT_OUT"
)

project_run_record="$(extract_value "Template run record:" "$PROJECT_OUT")"
project_observer="$(extract_value "Observer store:" "$PROJECT_OUT")"
[ -n "$project_run_record" ] || fail "could not read project run record path"
[ -n "$project_observer" ] || fail "could not read project observer path"

case "$project_observer" in
  "$PROJECT_TARGET"/*) fail "observer store must not be inside project target: $project_observer" ;;
esac

require_file "$project_run_record/metadata.edn"
require_file "$project_observer/agent-prompt.txt"
require_file "$project_observer/capture-observation.sh"
require_absent "$PROJECT_TARGET/.llm/template-only"
require_grep ':target/mode :project' "$project_run_record/metadata.edn"
require_grep ':target/project-phase :bootstrap' "$project_run_record/metadata.edn"
require_grep ':repo-kind :project' "$PROJECT_TARGET/.llm/repo-context.edn"
require_grep 'webhook processor' "$project_observer/agent-prompt.txt"

if git -C "$PROJECT_TARGET" ls-files | grep -q 'observer-runs\|template-only'; then
  fail "observer or template-only files were committed into project target"
fi

"$project_observer/capture-observation.sh" --note "project target smoke"
"$project_observer/mark-terminal-state.sh" --state observed --note "project target smoke complete"
require_grep ':event/type :observation-captured' "$project_run_record/events.edn"
require_grep ':state :observed' "$project_run_record/events.edn"

echo "instrument setup smoke: OK"
