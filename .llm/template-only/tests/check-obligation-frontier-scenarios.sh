#!/usr/bin/env bash
# Template-maintenance E2E scenarios for obligation-index / Work Frontier.
#
# This is not an application test for derived projects. It creates synthetic
# repos under /tmp and verifies that DESIGN obligations become typed frontier
# items, then disappear as trace-backed boundary/test coverage is added.
#
# verified-mandates: [MANDATE:M-0019]

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEMPLATE_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
BASE="${TMPDIR:-/tmp}/clojure-polylith-template-obligation-frontier-scenarios"

rm -rf "$BASE"
mkdir -p "$BASE"

copy_scripts() {
  local repo="$1"
  mkdir -p "$repo/.llm/scripts" "$repo/.llm/data"
  cp "$TEMPLATE_ROOT/.llm/scripts/run-clj-tool.sh" "$repo/.llm/scripts/"
  cp "$TEMPLATE_ROOT/.llm/scripts/derivation_manifest.clj" "$repo/.llm/scripts/"
  cp "$TEMPLATE_ROOT/.llm/scripts/check-derived-artifacts.sh" "$repo/.llm/scripts/"
  cp "$TEMPLATE_ROOT/.llm/scripts/gen_design_ir.clj" "$repo/.llm/scripts/"
  cp "$TEMPLATE_ROOT/.llm/scripts/gen-design-ir.sh" "$repo/.llm/scripts/"
  cp "$TEMPLATE_ROOT/.llm/scripts/check-design-ir.sh" "$repo/.llm/scripts/"
  cp "$TEMPLATE_ROOT/.llm/scripts/gen_trace_index.clj" "$repo/.llm/scripts/"
  cp "$TEMPLATE_ROOT/.llm/scripts/gen-trace-index.sh" "$repo/.llm/scripts/"
  cp "$TEMPLATE_ROOT/.llm/scripts/check-trace-index.sh" "$repo/.llm/scripts/"
  cp "$TEMPLATE_ROOT/.llm/scripts/gen_obligation_index.clj" "$repo/.llm/scripts/"
  cp "$TEMPLATE_ROOT/.llm/scripts/gen-obligation-index.sh" "$repo/.llm/scripts/"
  cp "$TEMPLATE_ROOT/.llm/scripts/check-obligation-index.sh" "$repo/.llm/scripts/"
  cp "$TEMPLATE_ROOT/.llm/scripts/derive-work-frontier.sh" "$repo/.llm/scripts/"
  chmod +x "$repo/.llm/scripts"/*.sh
  cat > "$repo/deps.edn" <<'EOF'
{:paths [] :deps {org.clojure/clojure {:mvn/version "1.12.0"}}}
EOF
}

base_repo() {
  local repo="$1"
  copy_scripts "$repo"
  mkdir -p "$repo/components/invoice/src/example/app/invoice"
  cat > "$repo/.llm/repo-context.edn" <<'EOF'
{:repo-kind :project :adoption-mode :complete}
EOF
  cat > "$repo/DESIGN.md" <<'EOF'
# DESIGN

## 1. Purpose

- REQ-001: An invoice can be created.

## 3. Use Cases

### UC-1: Create invoice

- actor: accountant

## 4. Acceptance Criteria

- [ ] AC-001: [UC-1][REQ-001] Creating an invoice returns the created invoice.
EOF
}

add_boundary_trace() {
  local repo="$1"
  cat > "$repo/components/invoice/src/example/app/invoice/interface.clj" <<'EOF'
(ns example.app.invoice.interface)

(defn ^{:trace/requirements ["REQ-001"]
        :trace/use-cases ["UC-1"]}
  create [input]
  input)
EOF
}

add_test_trace() {
  local repo="$1"
  mkdir -p "$repo/components/invoice/test/example/app/invoice"
  cat > "$repo/components/invoice/test/example/app/invoice/interface_test.clj" <<'EOF'
(ns example.app.invoice.interface-test
  (:require [clojure.test :refer [deftest is]]))

(deftest ^{:trace/requirements ["REQ-001"]
           :trace/use-cases ["UC-1"]
           :trace/test-obligations ["AC-001"]}
  create-test
  (is true))
EOF
}

write_question() {
  local repo="$1"
  local state="$2"
  mkdir -p "$repo/.llm/memory"
  cat > "$repo/.llm/memory/QUESTIONS.md" <<EOF
# QUESTIONS

## 2. 未対応の質問

## Q-2026-05-001: Invoice creation rule
- **状態**: $state
- **提起日**: 2026-05-16
- **更新日**: 2026-05-16
- **提起の経路**: synthetic test
- **文脈**: The invoice creation rule is intentionally undecided.
EOF
}

generate_design() {
  local repo="$1"
  (cd "$repo" && ./.llm/scripts/gen-design-ir.sh >/dev/null)
}

generate_trace_if_present() {
  local repo="$1"
  if find "$repo/components" -type f -name '*.clj' | grep -q .; then
    (cd "$repo" && ./.llm/scripts/gen-trace-index.sh >/dev/null)
  fi
}

generate_obligations() {
  local repo="$1"
  (cd "$repo" && ./.llm/scripts/gen-obligation-index.sh >/dev/null)
}

frontier() {
  local repo="$1"
  (cd "$repo" && ./.llm/scripts/derive-work-frontier.sh)
}

expect_frontier() {
  local repo="$1"
  local pattern="$2"
  local label="$3"
  frontier "$repo" > "$BASE/$label.out"
  grep -q "$pattern" "$BASE/$label.out"
}

expect_frontier_before() {
  local repo="$1"
  local first_pattern="$2"
  local second_pattern="$3"
  local label="$4"
  frontier "$repo" > "$BASE/$label.out"
  local first_line
  local second_line
  first_line="$(grep -n "$first_pattern" "$BASE/$label.out" | head -1 | cut -d: -f1)"
  second_line="$(grep -n "$second_pattern" "$BASE/$label.out" | head -1 | cut -d: -f1)"
  if [ -z "$first_line" ] || [ -z "$second_line" ] || [ "$first_line" -ge "$second_line" ]; then
    echo "$label frontier order mismatch" >&2
    cat "$BASE/$label.out" >&2
    exit 1
  fi
}

expect_check_fail() {
  local repo="$1"
  local pattern="$2"
  local label="$3"
  if (cd "$repo" && ./.llm/scripts/check-obligation-index.sh > "$BASE/$label.out" 2>&1); then
    echo "$label unexpectedly passed" >&2
    exit 1
  fi
  grep -q "$pattern" "$BASE/$label.out"
}

expect_frontier_fail() {
  local repo="$1"
  local pattern="$2"
  local label="$3"
  if frontier "$repo" > "$BASE/$label.out" 2>&1; then
    echo "$label unexpectedly passed" >&2
    exit 1
  fi
  grep -q "$pattern" "$BASE/$label.out"
}

expect_check_pass() {
  local repo="$1"
  (cd "$repo" && ./.llm/scripts/check-obligation-index.sh >/dev/null)
}

assert_index() {
  local repo="$1"
  local expr="$2"
  local label="$3"
  (
    cd "$repo"
    clj -M -e "(require '[clojure.edn :as edn])
            (let [data (edn/read-string (slurp \".llm/data/obligation-index.edn\"))]
              (when-not $expr
                (throw (ex-info \"$label\" {}))))" >/dev/null
  )
}

scenario() {
  local label="$1"
  shift
  echo "== $label =="
  "$@"
}

scenario_01_missing_boundary_and_test_are_frontier() {
  local repo="$BASE/01-missing-boundary-and-test"
  base_repo "$repo"
  generate_design "$repo"
  generate_obligations "$repo"
  expect_frontier "$repo" "red missing-boundary REQ-001" "01-frontier-req"
  expect_frontier "$repo" "red missing-test AC-001" "01-frontier-ac"
  expect_check_fail "$repo" "red obligations" "01-check"
}

scenario_02_boundary_moves_frontier_to_tests() {
  local repo="$BASE/02-boundary-only"
  base_repo "$repo"
  add_boundary_trace "$repo"
  generate_design "$repo"
  generate_trace_if_present "$repo"
  generate_obligations "$repo"
  expect_frontier "$repo" "red missing-test REQ-001" "02-frontier-req-test"
  expect_frontier "$repo" "red missing-test AC-001" "02-frontier-ac-test"
  expect_check_fail "$repo" "red obligations" "02-check"
}

scenario_03_boundary_and_test_green() {
  local repo="$BASE/03-green"
  base_repo "$repo"
  add_boundary_trace "$repo"
  add_test_trace "$repo"
  generate_design "$repo"
  generate_trace_if_present "$repo"
  generate_obligations "$repo"
  expect_frontier "$repo" "empty" "03-frontier-empty"
  expect_check_pass "$repo"
}

scenario_04_section_backed_dispositions_are_complete() {
  local repo="$BASE/04-section-backed-dispositions"
  copy_scripts "$repo"
  cat > "$repo/.llm/repo-context.edn" <<'EOF'
{:repo-kind :project :adoption-mode :complete}
EOF
  cat > "$repo/DESIGN.md" <<'EOF'
# DESIGN

## 🔴 2. Scope

### 2.2 Out of Scope

- PAY-001: Payment processing is excluded from this product.

## ⚪ 10. Future Plan

- FUT-001: Recurring invoices may be considered later.
EOF
  generate_design "$repo"
  generate_obligations "$repo"
  assert_index "$repo" '(= :out-of-scope (:state (first (filter #(= "PAY-001" (:id %)) (:obligations data)))))' "PAY-001 should be out-of-scope"
  assert_index "$repo" '(= :deferred (:state (first (filter #(= "FUT-001" (:id %)) (:obligations data)))))' "FUT-001 should be deferred"
  expect_frontier "$repo" "empty" "04-frontier-empty"
  expect_check_pass "$repo"
}

scenario_05_unbacked_disposition_is_red() {
  local repo="$BASE/05-unbacked-disposition"
  copy_scripts "$repo"
  cat > "$repo/.llm/repo-context.edn" <<'EOF'
{:repo-kind :project :adoption-mode :complete}
EOF
  cat > "$repo/DESIGN.md" <<'EOF'
# DESIGN

## 🔴 4. Acceptance Criteria

- [ ] AC-999: [disposition: deferred] This acceptance criterion is postponed without backing.
EOF
  generate_design "$repo"
  generate_obligations "$repo"
  expect_frontier "$repo" "red unbacked-disposition AC-999" "05-frontier-unbacked"
  expect_check_fail "$repo" "AC-999 :unbacked-disposition" "05-check"
}

scenario_06_open_question_blocks_obligation() {
  local repo="$BASE/06-open-question-blocks"
  copy_scripts "$repo"
  cat > "$repo/.llm/repo-context.edn" <<'EOF'
{:repo-kind :project :adoption-mode :complete}
EOF
  write_question "$repo" "未対応(open)"
  cat > "$repo/DESIGN.md" <<'EOF'
# DESIGN

## 🔴 4. Acceptance Criteria

- [ ] AC-001: [Q-2026-05-001] Invoice creation waits for a human decision.
EOF
  generate_design "$repo"
  generate_obligations "$repo"
  expect_frontier "$repo" "accounted blocked-by-question AC-001" "06-frontier-blocked"
  assert_index "$repo" '(= "Q-2026-05-001" (get-in (first (filter #(= "AC-001" (:id %)) (:obligations data))) [:blocker :backing :id]))' "AC-001 should link active Q"
  expect_check_pass "$repo"
}

scenario_07_resolved_question_reactivates_red() {
  local repo="$BASE/07-resolved-question-reactivates-red"
  copy_scripts "$repo"
  cat > "$repo/.llm/repo-context.edn" <<'EOF'
{:repo-kind :project :adoption-mode :complete}
EOF
  write_question "$repo" "解決済み(resolved)"
  cat > "$repo/DESIGN.md" <<'EOF'
# DESIGN

## 🔴 4. Acceptance Criteria

- [ ] AC-001: [Q-2026-05-001] Invoice creation no longer has an active blocker.
EOF
  generate_design "$repo"
  generate_obligations "$repo"
  expect_frontier "$repo" "red missing-test AC-001" "07-frontier-red"
  expect_check_fail "$repo" "AC-001 :missing-test" "07-check"
}

scenario_08_missing_question_ref_is_red() {
  local repo="$BASE/08-missing-question-ref"
  copy_scripts "$repo"
  cat > "$repo/.llm/repo-context.edn" <<'EOF'
{:repo-kind :project :adoption-mode :complete}
EOF
  cat > "$repo/DESIGN.md" <<'EOF'
# DESIGN

## 🔴 4. Acceptance Criteria

- [ ] AC-001: [Q-2026-05-999] This blocker points nowhere.
EOF
  generate_design "$repo"
  generate_obligations "$repo"
  expect_frontier "$repo" "red unresolved-blocker AC-001" "08-frontier-unresolved"
  expect_check_fail "$repo" "AC-001 :unresolved-blocker" "08-check"
}

scenario_09_stale_upstream_blocks_frontier() {
  local repo="$BASE/09-stale-upstream-blocks-frontier"
  base_repo "$repo"
  generate_design "$repo"
  generate_obligations "$repo"
  cat >> "$repo/DESIGN.md" <<'EOF'

- REQ-002: This change intentionally makes design-ir stale.
EOF
  expect_frontier_fail "$repo" "Work Frontier requires fresh derived artifacts" "09-frontier-stale"
  expect_frontier_fail "$repo" "stale .llm/data/design-ir.edn" "09-frontier-stale-design"
}

scenario_10_dag_orders_open_prerequisites_first() {
  local repo="$BASE/10-dag-orders-open-prerequisites"
  copy_scripts "$repo"
  cat > "$repo/.llm/repo-context.edn" <<'EOF'
{:repo-kind :project :adoption-mode :complete}
EOF
  write_question "$repo" "未対応(open)"
  cat > "$repo/DESIGN.md" <<'EOF'
# DESIGN

## 1. Purpose

- REQ-001: [Q-2026-05-001] Invoice creation rules are undecided.

## 4. Acceptance Criteria

- [ ] AC-001: [REQ-001] Creating an invoice follows the resolved rule.
EOF
  generate_design "$repo"
  generate_obligations "$repo"
  expect_frontier_before "$repo" \
    "accounted blocked-by-question REQ-001" \
    "red missing-test AC-001" \
    "10-frontier-dag-order"
  expect_frontier "$repo" "blocked-by: REQ-001" "10-frontier-blocked-by"
  expect_frontier "$repo" "requires: REQ-001" "10-frontier-requires"
  expect_frontier "$repo" "frontier-depth: 1" "10-frontier-depth"
  assert_index "$repo" '(= ["REQ-001"] (get-in (first (filter #(= "AC-001" (:id %)) (:obligations data))) [:frontier :blocked-by]))' "AC-001 should be blocked by unfinished REQ-001"
}

scenario "01 missing boundary/test" scenario_01_missing_boundary_and_test_are_frontier
scenario "02 boundary leaves test frontier" scenario_02_boundary_moves_frontier_to_tests
scenario "03 boundary + test green" scenario_03_boundary_and_test_green
scenario "04 section-backed dispositions complete" scenario_04_section_backed_dispositions_are_complete
scenario "05 unbacked disposition red" scenario_05_unbacked_disposition_is_red
scenario "06 open question blocks obligation" scenario_06_open_question_blocks_obligation
scenario "07 resolved question reactivates red" scenario_07_resolved_question_reactivates_red
scenario "08 missing question ref red" scenario_08_missing_question_ref_is_red
scenario "09 stale upstream blocks frontier" scenario_09_stale_upstream_blocks_frontier
scenario "10 DAG orders open prerequisites first" scenario_10_dag_orders_open_prerequisites_first

echo "All obligation frontier scenarios passed."
