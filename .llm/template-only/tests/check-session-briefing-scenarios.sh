#!/usr/bin/env bash
# Template-maintenance scenarios for session-briefing mode and audit output.
#
# These are deterministic fixture tests for the briefing input shown to an LLM.
# They do not call an LLM and do not evaluate model behavior.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEMPLATE_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
BASE="${TMPDIR:-/tmp}/clojure-polylith-template-session-briefing-scenarios"

rm -rf "$BASE"
mkdir -p "$BASE"

copy_briefing() {
  local repo="$1"
  mkdir -p "$repo/.llm/scripts"
  cp "$TEMPLATE_ROOT/.llm/scripts/session-briefing.sh" "$repo/.llm/scripts/"
  chmod +x "$repo/.llm/scripts/session-briefing.sh"
}

write_template_context() {
  local repo="$1"
  cat > "$repo/.llm/repo-context.edn" <<'EOF'
{:repo-kind :template
 :template-name "clojure-polylith-llm-starter"
 :adoption-mode :complete}
EOF
}

write_project_context() {
  local repo="$1"
  cat > "$repo/.llm/repo-context.edn" <<'EOF'
{:repo-kind :project
 :derived-from "clojure-polylith-llm-starter"
 :project-name "example.app"
 :workspace-kind :polylith
 :adoption-mode :complete}
EOF
}

write_workspace() {
  local repo="$1"
  local top_ns="$2"
  cat > "$repo/workspace.edn" <<EOF
{:top-namespace "$top_ns"
 :interface-ns "interface"
 :projects {"development" {:alias "dev"}}}
EOF
}

base_repo() {
  local repo="$1"
  mkdir -p "$repo"
  copy_briefing "$repo"
}

run_briefing() {
  local label="$1"
  local repo="$2"
  shift 2
  (cd "$repo" && ./.llm/scripts/session-briefing.sh "$@" > "$BASE/$label.out")
}

require_line() {
  local label="$1"
  local pattern="$2"
  if ! grep -Fq "$pattern" "$BASE/$label.out"; then
    echo "$label missing expected line: $pattern" >&2
    echo "--- output ---" >&2
    sed -n '1,160p' "$BASE/$label.out" >&2
    exit 1
  fi
}

forbid_line() {
  local label="$1"
  local pattern="$2"
  if grep -Fq "$pattern" "$BASE/$label.out"; then
    echo "$label contained forbidden line: $pattern" >&2
    echo "--- output ---" >&2
    sed -n '1,160p' "$BASE/$label.out" >&2
    exit 1
  fi
}

scenario() {
  local label="$1"
  shift
  echo "== $label =="
  "$@"
}

scenario_manifest_missing() {
  local repo="$BASE/manifest-missing"
  base_repo "$repo"
  run_briefing "manifest-missing" "$repo"

  require_line "manifest-missing" "MODE: UNKNOWN"
  require_line "manifest-missing" "recover or add the repo-context manifest before normal work"
  require_line "manifest-missing" "completion gate: unavailable until repo mode is known"
  forbid_line "manifest-missing" "repo-control.sh"
}

scenario_template_clean() {
  local repo="$BASE/template-clean"
  base_repo "$repo"
  write_template_context "$repo"
  write_workspace "$repo" "myorg.myapp"
  run_briefing "template-clean" "$repo"

  require_line "template-clean" "MODE: TEMPLATE MAINTENANCE"
  require_line "template-clean" "operating intent: template maintenance; keep :project-owned paths untouched"
  require_line "template-clean" "decision log: maintainer archive, not ADR"
  require_line "template-clean" "next action surface: ./.llm/scripts/evidence.sh what-now"
  require_line "template-clean" "L0 Template Test Recommendation（テンプレート保守テスト提言）"
  require_line "template-clean" "テンプレート保守の L0 確認で見る任意提言。repo mode や自動 gate ではない。"
  forbid_line "template-clean" "QUESTIONS / KNOWLEDGE / ADR by decision type"
  forbid_line "template-clean" "repo-control.sh"
}

scenario_template_l0_test_recommendation() {
  local repo="$BASE/template-l0-test-recommendation"
  base_repo "$repo"
  write_template_context "$repo"
  write_workspace "$repo" "myorg.myapp"
  (cd "$repo" && git init -q)
  run_briefing "template-l0-test-recommendation" "$repo"

  require_line "template-l0-test-recommendation" "L0 Template Test Recommendation（テンプレート保守テスト提言）"
  require_line "template-l0-test-recommendation" "現在の差分: template maintenance test 対応表に一致"
  require_line "template-l0-test-recommendation" "close 前の起動候補:"
  require_line "template-l0-test-recommendation" "./.llm/template-only/tests/check-session-briefing-scenarios.sh"
  forbid_line "template-l0-test-recommendation" "repo-control.sh"
}

scenario_template_conflict() {
  local repo="$BASE/template-conflict"
  base_repo "$repo"
  write_template_context "$repo"
  write_workspace "$repo" "example.app"
  run_briefing "template-conflict" "$repo"

  require_line "template-conflict" "MODE: CONFLICT"
  require_line "template-conflict" "state: conflict; do not continue as either pure template or complete project"
  require_line "template-conflict" "next action surface: mode repair first; evidence.sh what-now after conflict is cleared"
  require_line "template-conflict" "completion gate: check-workspace-integrity.sh blocks this state"
  forbid_line "template-conflict" "derived project development"
  forbid_line "template-conflict" "repo-control.sh"
}

scenario_project_bootstrap() {
  local repo="$BASE/project-bootstrap"
  base_repo "$repo"
  write_project_context "$repo"
  write_workspace "$repo" "myorg.myapp"
  run_briefing "project-bootstrap" "$repo"

  require_line "project-bootstrap" "MODE: PROJECT (example.app)"
  require_line "project-bootstrap" "operating intent: derived project bootstrap; finish identity and deploy shape first"
  require_line "project-bootstrap" "decision log: QUESTIONS / KNOWLEDGE / ADR by decision type"
  require_line "project-bootstrap" "Phase: bootstrap"
  forbid_line "project-bootstrap" "maintainer archive, not ADR"
  forbid_line "project-bootstrap" "repo-control.sh"
}

scenario_project_development() {
  local repo="$BASE/project-development"
  base_repo "$repo"
  write_project_context "$repo"
  write_workspace "$repo" "example.app"
  mkdir -p "$repo/projects/api"
  run_briefing "project-development" "$repo"

  require_line "project-development" "MODE: PROJECT (example.app)"
  require_line "project-development" "operating intent: derived project development"
  require_line "project-development" "decision log: QUESTIONS / KNOWLEDGE / ADR by decision type"
  require_line "project-development" "Phase: development"
  forbid_line "project-development" "maintainer archive, not ADR"
  forbid_line "project-development" "repo-control.sh"
}

scenario_audit_edn() {
  local repo="$BASE/audit-edn"
  base_repo "$repo"
  write_template_context "$repo"
  write_workspace "$repo" "myorg.myapp"
  run_briefing "audit-edn" "$repo" --audit --format edn

  require_line "audit-edn" ":briefing/audit"
  require_line "audit-edn" ":control-plane/first-line"
  require_line "audit-edn" ":control-plane/bullets 5"
  require_line "audit-edn" ":next-action-surface \"./.llm/scripts/evidence.sh what-now\""
  require_line "audit-edn" ":forbidden-surfaces []"
  require_line "audit-edn" ":budget :ok"
  forbid_line "audit-edn" "MODE: TEMPLATE MAINTENANCE"
  forbid_line "audit-edn" "repo-control.sh"
}

scenario "manifest missing" scenario_manifest_missing
scenario "template clean" scenario_template_clean
scenario "template L0 test recommendation" scenario_template_l0_test_recommendation
scenario "template conflict" scenario_template_conflict
scenario "project bootstrap" scenario_project_bootstrap
scenario "project development" scenario_project_development
scenario "audit edn" scenario_audit_edn

echo "session briefing scenarios: OK"
