#!/usr/bin/env bash
# Template-maintenance E2E scenarios for DESIGN IR generator/checker.
#
# This is not an application test for derived projects. It creates synthetic
# repos under /tmp and verifies DESIGN.md extraction, generated design-ir drift,
# and joins with existing .llm/data analysis EDN.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEMPLATE_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
BASE="${TMPDIR:-/tmp}/clojure-polylith-template-design-ir-scenarios"

rm -rf "$BASE"
mkdir -p "$BASE"

copy_scripts() {
  local repo="$1"
  mkdir -p "$repo/.llm/scripts" "$repo/.llm/data"
  cp "$TEMPLATE_ROOT/.llm/scripts/gen_design_ir.clj" "$repo/.llm/scripts/"
  cp "$TEMPLATE_ROOT/.llm/scripts/gen-design-ir.sh" "$repo/.llm/scripts/"
  cp "$TEMPLATE_ROOT/.llm/scripts/check-design-ir.sh" "$repo/.llm/scripts/"
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
- [ ] [REQ-001][NFR-001] 金額が負ならエラーにする

## 6. 非機能要件

- NFR-001: 1000 件を 10 秒以内に処理する

## 7. 外部インターフェース

- API-001: HTTP API で請求書作成を受け付ける

## 9. 技術的制約

- TECH-001: 永続化方式は承認後に確定する

```markdown
- REQ-999: code block example must be ignored
```
EOF
}

run_generate() {
  local repo="$1"
  (cd "$repo" && ./.llm/scripts/gen-design-ir.sh >/dev/null)
}

run_check() {
  local repo="$1"
  (cd "$repo" && ./.llm/scripts/check-design-ir.sh >/dev/null)
}

assert_ir() {
  local repo="$1"
  local expr="$2"
  local label="$3"
  (
    cd "$repo"
    clj -M -e "(require '[clojure.edn :as edn])
            (let [data (edn/read-string (slurp \".llm/data/design-ir.edn\"))]
              (when-not $expr
                (throw (ex-info \"$label\" {}))))" >/dev/null
  )
}

expect_fail() {
  local repo="$1"
  local cmd="$2"
  local pattern="$3"
  local label="$4"
  if (cd "$repo" && eval "$cmd" >"$BASE/$label.out" 2>&1); then
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

scenario_01_extract_design_ir() {
  local repo="$BASE/01-extract-design-ir"
  base_design "$repo"
  run_generate "$repo"
  assert_ir "$repo" '(contains? (set (map :id (:requirements data))) "REQ-001")' "REQ-001 missing"
  assert_ir "$repo" '(contains? (set (map :id (:use-cases data))) "UC-1")' "UC-1 missing"
  assert_ir "$repo" '(= 2 (count (:test-obligations data)))' "test obligations missing"
  assert_ir "$repo" '(contains? (set (map :id (:test-obligations data))) "AC-001")' "AC-001 obligation missing"
  assert_ir "$repo" '(not (contains? (set (map :id (:test-obligations data))) "TO-001"))' "position-based TO-001 obligation generated"
  assert_ir "$repo" '(some #(re-matches #"TO-[0-9A-F]{8}" %) (map :id (:test-obligations data)))' "stable hash obligation missing"
  assert_ir "$repo" '(contains? (set (:related-use-cases (first (filter #(= "AC-001" (:id %)) (:test-obligations data))))) "UC-1")' "AC-001 related UC-1 missing"
  assert_ir "$repo" '(contains? (set (:related-requirements (first (filter #(= "AC-001" (:id %)) (:test-obligations data))))) "REQ-001")' "AC-001 related REQ-001 missing"
  assert_ir "$repo" '(some #(contains? (set (:related-requirements %)) "NFR-001") (:test-obligations data))' "NFR-001 related requirement missing"
  assert_ir "$repo" '(empty? (get-in data [:diagnostics :unknown-related-requirements]))' "unexpected unknown related requirements"
  assert_ir "$repo" '(empty? (get-in data [:diagnostics :unknown-related-use-cases]))' "unexpected unknown related use cases"
  assert_ir "$repo" '(contains? (set (map :id (:constraints data))) "NFR-001")' "NFR-001 constraint missing"
  assert_ir "$repo" '(contains? (set (map :id (:constraints data))) "API-001")' "API-001 constraint missing"
  assert_ir "$repo" '(contains? (set (map :id (:constraints data))) "TECH-001")' "TECH-001 constraint missing"
  assert_ir "$repo" '(contains? (set (get-in data [:coverage :implementation-requirements])) "REQ-001")' "REQ-001 implementation requirement missing"
  assert_ir "$repo" '(contains? (set (get-in data [:coverage :constraint-requirements])) "NFR-001")' "NFR-001 constraint coverage missing"
  assert_ir "$repo" '(contains? (set (get-in data [:coverage :constraint-requirements])) "API-001")' "API-001 constraint coverage missing"
  assert_ir "$repo" '(contains? (set (get-in data [:coverage :constraint-requirements])) "TECH-001")' "TECH-001 constraint coverage missing"
  assert_ir "$repo" '(not (contains? (set (get-in data [:coverage :unassigned-implementation-requirements])) "NFR-001"))' "NFR-001 should not be unassigned implementation"
  assert_ir "$repo" '(not (contains? (set (get-in data [:coverage :unassigned-implementation-requirements])) "API-001"))' "API-001 should not be unassigned implementation"
  assert_ir "$repo" '(not (contains? (set (get-in data [:coverage :unassigned-implementation-requirements])) "TECH-001"))' "TECH-001 should not be unassigned implementation"
  assert_ir "$repo" '(not (contains? (set (map :id (:requirements data))) "REQ-999"))' "code block ID was extracted"
  run_check "$repo"
}

scenario_02_duplicate_design_id_fails() {
  local repo="$BASE/02-duplicate-design-id-fails"
  base_design "$repo"
  cat >> "$repo/DESIGN.md" <<'EOF'
- REQ-001: duplicate requirement
EOF
  expect_fail "$repo" "./.llm/scripts/gen-design-ir.sh" "duplicate requirement ids" "02-duplicate"
}

scenario_03_analysis_edn_coverage() {
  local repo="$BASE/03-analysis-edn-coverage"
  base_design "$repo"
  cat > "$repo/.llm/data/brick-map.edn" <<'EOF'
{:bricks [{:brick/path "components/invoice"
           :brick/type :component
           :brick/provides #{:invoice/create}
           :brick/requirements ["REQ-001"]}]
 :capabilities {:invoice/create "components/invoice"}
 :entrypoints {:http-api "bases/web-api"}}
EOF
  cat > "$repo/.llm/data/workspace-map.edn" <<'EOF'
{:projects [{:project/path "projects/api"
             :project/requirements ["API-001" "OLD-001"]}]
 :bricks [{:brick/path "bases/web-api"
           :brick/requirements ["API-001"]}]}
EOF
  cat > "$repo/.llm/data/libs.edn" <<'EOF'
[{:purpose [:db :jdbc]}
 {:purpose [:http-server]}]
EOF
  run_generate "$repo"
  assert_ir "$repo" '(contains? (set (get-in data [:coverage :implemented-requirements])) "REQ-001")' "REQ-001 not implemented"
  assert_ir "$repo" '(not (contains? (set (get-in data [:coverage :implemented-requirements])) "API-001"))' "API-001 should not be implementation coverage"
  assert_ir "$repo" '(contains? (set (get-in data [:coverage :constraint-implementation-references])) "API-001")' "API-001 constraint reference missing"
  assert_ir "$repo" '(contains? (set (get-in data [:coverage :unknown-implementation-requirements])) "OLD-001")' "OLD-001 not unknown"
  assert_ir "$repo" '(contains? (set (get-in data [:implementation-index :capabilities])) :invoice/create)' "capability missing"
  assert_ir "$repo" '(contains? (set (get-in data [:implementation-index :entrypoints])) :http-api)' "entrypoint missing"
  assert_ir "$repo" '(contains? (set (get-in data [:implementation-index :library-categories])) :jdbc)' "library purpose missing"
  run_check "$repo"
}

scenario_04_stale_ir_detection_and_repair() {
  local repo="$BASE/04-stale-ir-detection-and-repair"
  base_design "$repo"
  run_generate "$repo"
  cat >> "$repo/DESIGN.md" <<'EOF'

- REQ-002: 追加要求
EOF
  expect_fail "$repo" "./.llm/scripts/check-design-ir.sh" "not synchronized" "04-stale"
  run_generate "$repo"
  run_check "$repo"
}

scenario_05_duplicate_test_obligation_id_fails() {
  local repo="$BASE/05-duplicate-test-obligation-id-fails"
  base_design "$repo"
  cat >> "$repo/DESIGN.md" <<'EOF'

## 4. 受入基準の追記

- [ ] AC-001: duplicate acceptance
EOF
  expect_fail "$repo" "./.llm/scripts/gen-design-ir.sh" "duplicate test obligation ids" "05-duplicate-obligation"
}

scenario_06_unknown_related_references_are_diagnostics() {
  local repo="$BASE/06-unknown-related-references-are-diagnostics"
  base_design "$repo"
  cat >> "$repo/DESIGN.md" <<'EOF'

## 4. 受入基準の追記

- [ ] AC-002: [UC-999][REQ-999] unknown references
EOF
  run_generate "$repo"
  assert_ir "$repo" '(contains? (set (get-in data [:diagnostics :unknown-related-requirements])) "REQ-999")' "REQ-999 unknown related requirement missing"
  assert_ir "$repo" '(contains? (set (get-in data [:diagnostics :unknown-related-use-cases])) "UC-999")' "UC-999 unknown related use case missing"
  run_check "$repo"
}

scenario "01 extract DESIGN IR" scenario_01_extract_design_ir
scenario "02 duplicate DESIGN id fails" scenario_02_duplicate_design_id_fails
scenario "03 analysis EDN coverage" scenario_03_analysis_edn_coverage
scenario "04 stale IR detection and repair" scenario_04_stale_ir_detection_and_repair
scenario "05 duplicate test obligation id fails" scenario_05_duplicate_test_obligation_id_fails
scenario "06 unknown related references are diagnostics" scenario_06_unknown_related_references_are_diagnostics

echo "All DESIGN IR scenarios passed."
