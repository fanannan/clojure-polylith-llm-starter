#!/usr/bin/env bash
# Smoke test for Instruction-Following Instrument score aggregation.
#
# This does not launch an LLM. It verifies that multiple path-level score files
# are summarized as counts/dispersion and that split outcomes route to
# :spec-ambiguous instead of becoming a point estimate.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEMPLATE_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
BASE="${TMPDIR:-/tmp}/clojure-polylith-template-instrument-summary-smoke"
RUNS="$BASE/runs"
SUMMARY="$BASE/summary.edn"

cleanup() {
  rm -rf "$BASE"
}
trap cleanup EXIT

fail() {
  echo "instrument summary smoke failed: $*" >&2
  exit 1
}

require_grep() {
  local pattern="$1"
  local path="$2"
  grep -q "$pattern" "$path" || fail "missing pattern '$pattern' in $path"
}

write_score() {
  local name="$1"
  local case_id="$2"
  local target_mode="$3"
  local result_type="$4"
  local dir="$RUNS/$name"

  mkdir -p "$dir"
  cat > "$dir/score.edn" <<EOF
{:score/schema 1
 :score/kind :path-level-preliminary
 :case/id :$case_id
 :target/mode :$target_mode
 :result/type :$result_type
 :assessment/confidence :path-only}
EOF
}

rm -rf "$BASE"
mkdir -p "$RUNS"

write_score split-1 template-mode-no-project-owned-write template hard-fail
write_score split-2 template-mode-no-project-owned-write template expected-stop

write_score stable-1 project-bootstrap-prioritizes-identity project expected-stop
write_score stable-2 project-bootstrap-prioritizes-identity project expected-stop
write_score stable-3 project-bootstrap-prioritizes-identity project expected-stop
write_score stable-4 project-bootstrap-prioritizes-identity project expected-stop
write_score stable-5 project-bootstrap-prioritizes-identity project expected-stop
write_score stable-invalid project-bootstrap-prioritizes-identity project invalid-run

"$TEMPLATE_ROOT/.llm/template-only/instrument/summarize-runs.sh" \
  --runs-dir "$RUNS" \
  --min-runs 5 \
  --out "$SUMMARY" >/dev/null

require_grep ':summary/kind :instruction-following-run-summary' "$SUMMARY"
require_grep ':number/policy :counts-without-point-estimates' "$SUMMARY"
require_grep ':score/files 8' "$SUMMARY"
require_grep ':case/id :template-mode-no-project-owned-write' "$SUMMARY"
require_grep ':aggregate/result :spec-ambiguous' "$SUMMARY"
require_grep ':ambiguity/signal :split-results' "$SUMMARY"
require_grep ':route :questions-or-doc-improvement' "$SUMMARY"
require_grep ':sample/enough? false' "$SUMMARY"
require_grep ':dispersion/result-counts {:hard-fail 1 :expected-stop 1}' "$SUMMARY"
require_grep ':case/id :project-bootstrap-prioritizes-identity' "$SUMMARY"
require_grep ':sample/valid-n 5' "$SUMMARY"
require_grep ':sample/invalid-n 1' "$SUMMARY"
require_grep ':sample/enough? true' "$SUMMARY"
require_grep ':dispersion/result-counts {:expected-stop 5 :invalid-run 1}' "$SUMMARY"
require_grep ':aggregate/result :expected-stop' "$SUMMARY"

echo "instrument summary smoke: OK"
