#!/usr/bin/env bash
# Template-maintenance E2E scenarios for trace metadata checker.
#
# This is not an application test for derived projects. It creates synthetic
# repos under /tmp and verifies that specification trace metadata is accepted
# only on stable public boundaries and deftest forms.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEMPLATE_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
BASE="${TMPDIR:-/tmp}/clojure-polylith-template-trace-metadata-scenarios"

rm -rf "$BASE"
mkdir -p "$BASE"

copy_scripts() {
  local repo="$1"
  mkdir -p "$repo/.llm/scripts" "$repo/.llm/data"
  cp "$TEMPLATE_ROOT/.llm/scripts/gen_design_ir.clj" "$repo/.llm/scripts/"
  cp "$TEMPLATE_ROOT/.llm/scripts/gen-design-ir.sh" "$repo/.llm/scripts/"
  cp "$TEMPLATE_ROOT/.llm/scripts/check_trace_metadata.clj" "$repo/.llm/scripts/"
  cp "$TEMPLATE_ROOT/.llm/scripts/check-trace-metadata.sh" "$repo/.llm/scripts/"
  chmod +x "$repo/.llm/scripts"/*.sh
  cat > "$repo/deps.edn" <<'EOF'
{:paths [] :deps {org.clojure/clojure {:mvn/version "1.12.0"}}}
EOF
}

base_design() {
  local repo="$1"
  copy_scripts "$repo"
  cat > "$repo/DESIGN.md" <<'EOF'
# DESIGN

## 1. 目的

- REQ-001: 請求書を作成できる

## 3. 主要ユースケース

### UC-1: 請求書作成

- actor: 経理担当者

## 4. 受入基準

- [ ] AC-001: [UC-1][REQ-001] 請求書を作成できる
EOF
}

write_valid_code() {
  local repo="$1"
  mkdir -p "$repo/components/invoice/src/myorg/myapp/invoice"
  mkdir -p "$repo/components/invoice/test/myorg/myapp/invoice"
  cat > "$repo/components/invoice/src/myorg/myapp/invoice/interface.clj" <<'EOF'
(ns myorg.myapp.invoice.interface)

(defn ^{:trace/requirements ["REQ-001"]
        :trace/use-cases ["UC-1"]}
  create
  [cmd]
  cmd)
EOF
  cat > "$repo/components/invoice/test/myorg/myapp/invoice/interface_test.clj" <<'EOF'
(ns myorg.myapp.invoice.interface-test
  (:require [clojure.test :refer [deftest is]]))

(deftest ^{:trace/test-obligations ["AC-001"]
           :trace/requirements ["REQ-001"]
           :trace/use-cases ["UC-1"]}
  create-test
  (is true))
EOF
}

generate_ir() {
  local repo="$1"
  (cd "$repo" && ./.llm/scripts/gen-design-ir.sh >/dev/null)
}

run_check() {
  local repo="$1"
  (cd "$repo" && ./.llm/scripts/check-trace-metadata.sh)
}

expect_fail() {
  local repo="$1"
  local pattern="$2"
  local label="$3"
  if run_check "$repo" >"$BASE/$label.out" 2>&1; then
    echo "$label unexpectedly passed" >&2
    exit 1
  fi
  grep -q "$pattern" "$BASE/$label.out"
}

scenario() {
  local label="$1"
  shift
  echo "== $label =="
  "$@"
}

scenario_01_valid_trace_metadata() {
  local repo="$BASE/01-valid-trace-metadata"
  base_design "$repo"
  write_valid_code "$repo"
  generate_ir "$repo"
  run_check "$repo" >/dev/null
}

scenario_02_unknown_requirement_fails() {
  local repo="$BASE/02-unknown-requirement-fails"
  base_design "$repo"
  write_valid_code "$repo"
  sed -i 's/REQ-001/REQ-999/' "$repo/components/invoice/src/myorg/myapp/invoice/interface.clj"
  generate_ir "$repo"
  expect_fail "$repo" "unknown requirement id" "02-unknown-requirement"
}

scenario_03_internal_metadata_fails() {
  local repo="$BASE/03-internal-metadata-fails"
  base_design "$repo"
  write_valid_code "$repo"
  cat > "$repo/components/invoice/src/myorg/myapp/invoice/core.clj" <<'EOF'
(ns myorg.myapp.invoice.core)

(defn ^{:trace/requirements ["REQ-001"]}
  internal-create
  [cmd]
  cmd)
EOF
  generate_ir "$repo"
  expect_fail "$repo" "implementation code is forbidden" "03-internal-metadata"
}

scenario_04_test_obligation_on_implementation_fails() {
  local repo="$BASE/04-test-obligation-on-implementation-fails"
  base_design "$repo"
  write_valid_code "$repo"
  cat > "$repo/components/invoice/src/myorg/myapp/invoice/interface.clj" <<'EOF'
(ns myorg.myapp.invoice.interface)

(defn ^{:trace/requirements ["REQ-001"]
        :trace/test-obligations ["AC-001"]}
  create
  [cmd]
  cmd)
EOF
  generate_ir "$repo"
  expect_fail "$repo" "belongs on deftest" "04-test-obligation-on-implementation"
}

scenario_05_unreferenced_obligation_warns() {
  local repo="$BASE/05-unreferenced-obligation-warns"
  base_design "$repo"
  write_valid_code "$repo"
  cat > "$repo/components/invoice/test/myorg/myapp/invoice/interface_test.clj" <<'EOF'
(ns myorg.myapp.invoice.interface-test
  (:require [clojure.test :refer [deftest is]]))

(deftest create-test
  (is true))
EOF
  generate_ir "$repo"
  run_check "$repo" >"$BASE/05-unreferenced-obligation.out"
  grep -q "WARN: test obligation has no deftest trace metadata: AC-001" "$BASE/05-unreferenced-obligation.out"
}

scenario_06_complete_mode_unreferenced_obligation_fails() {
  local repo="$BASE/06-complete-mode-unreferenced-obligation-fails"
  base_design "$repo"
  write_valid_code "$repo"
  cat >> "$repo/DESIGN.md" <<'EOF'

### UC-2: 請求書検索

- actor: 経理担当者
EOF
  cat > "$repo/.llm/repo-context.edn" <<'EOF'
{:adoption-mode :complete}
EOF
  cat > "$repo/components/invoice/test/myorg/myapp/invoice/interface_test.clj" <<'EOF'
(ns myorg.myapp.invoice.interface-test
  (:require [clojure.test :refer [deftest is]]))

(deftest create-test
  (is true))
EOF
  generate_ir "$repo"
  expect_fail "$repo" "ERROR: test obligation has no deftest trace metadata: AC-001" "06-complete-mode-unreferenced-obligation"
}

scenario_07_empty_duplicate_blank_ids_fail() {
  local repo="$BASE/07-empty-duplicate-blank-ids-fail"
  base_design "$repo"
  write_valid_code "$repo"
  cat > "$repo/components/invoice/src/myorg/myapp/invoice/interface.clj" <<'EOF'
(ns myorg.myapp.invoice.interface)

(defn ^{:trace/requirements ["REQ-001" "REQ-001" ""]
        :trace/use-cases []}
  create
  [cmd]
  cmd)
EOF
  generate_ir "$repo"
  expect_fail "$repo" "duplicate requirement id" "07-empty-duplicate-blank-ids"
  grep -q "must not contain blank id" "$BASE/07-empty-duplicate-blank-ids.out"
  grep -q ":trace/use-cases must not be empty" "$BASE/07-empty-duplicate-blank-ids.out"
}

scenario_08_base_internal_system_metadata_fails() {
  local repo="$BASE/08-base-internal-system-metadata-fails"
  base_design "$repo"
  mkdir -p "$repo/bases/api/src/myorg/myapp/api"
  cat > "$repo/bases/api/src/myorg/myapp/api/system.clj" <<'EOF'
(ns myorg.myapp.api.system)

(defn ^{:trace/requirements ["REQ-001"]}
  start-system
  []
  {})
EOF
  generate_ir "$repo"
  expect_fail "$repo" "component interface or base core/handler boundary" "08-base-internal-system-metadata"
}

scenario_09_base_core_boundary_metadata_passes() {
  local repo="$BASE/09-base-core-boundary-metadata-passes"
  base_design "$repo"
  mkdir -p "$repo/bases/api/src/myorg/myapp/api"
  cat > "$repo/bases/api/src/myorg/myapp/api/core.clj" <<'EOF'
(ns myorg.myapp.api.core)

(defn ^{:trace/requirements ["REQ-001"]
        :trace/use-cases ["UC-1"]}
  -main
  [& _args]
  nil)
EOF
  generate_ir "$repo"
  run_check "$repo" >/dev/null
}

scenario_10_complete_mode_related_trace_mismatch_fails() {
  local repo="$BASE/10-complete-mode-related-trace-mismatch-fails"
  base_design "$repo"
  write_valid_code "$repo"
  cat > "$repo/.llm/repo-context.edn" <<'EOF'
{:adoption-mode :complete}
EOF
  cat > "$repo/components/invoice/test/myorg/myapp/invoice/interface_test.clj" <<'EOF'
(ns myorg.myapp.invoice.interface-test
  (:require [clojure.test :refer [deftest is]]))

(deftest ^{:trace/test-obligations ["AC-001"]
           :trace/requirements ["REQ-001"]
           :trace/use-cases ["UC-2"]}
  create-test
  (is true))
EOF
  generate_ir "$repo"
  expect_fail "$repo" "not related from its test obligations" "10-complete-mode-related-trace-mismatch"
}

scenario "01 valid trace metadata" scenario_01_valid_trace_metadata
scenario "02 unknown requirement fails" scenario_02_unknown_requirement_fails
scenario "03 internal metadata fails" scenario_03_internal_metadata_fails
scenario "04 test obligation on implementation fails" scenario_04_test_obligation_on_implementation_fails
scenario "05 unreferenced obligation warns" scenario_05_unreferenced_obligation_warns
scenario "06 complete mode unreferenced obligation fails" scenario_06_complete_mode_unreferenced_obligation_fails
scenario "07 empty duplicate blank ids fail" scenario_07_empty_duplicate_blank_ids_fail
scenario "08 base internal system metadata fails" scenario_08_base_internal_system_metadata_fails
scenario "09 base core boundary metadata passes" scenario_09_base_core_boundary_metadata_passes
scenario "10 complete mode related trace mismatch fails" scenario_10_complete_mode_related_trace_mismatch_fails

echo "trace metadata scenarios: OK"
