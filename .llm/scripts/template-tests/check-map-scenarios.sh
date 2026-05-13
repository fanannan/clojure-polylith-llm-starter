#!/usr/bin/env bash
# Template-maintenance E2E scenarios for map generators/checkers.
#
# This is not an application test for derived projects. It creates synthetic
# Polylith-like repos under /tmp and verifies migration/generation/check/repair
# behavior for brick.edn, project.edn, and generated map files.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEMPLATE_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
BASE="${TMPDIR:-/tmp}/clojure-polylith-template-map-scenarios"

rm -rf "$BASE"
mkdir -p "$BASE"

copy_scripts() {
  local repo="$1"
  mkdir -p "$repo/.llm/scripts" "$repo/components" "$repo/bases"
  cp "$TEMPLATE_ROOT/.llm/scripts/gen_brick_map.clj" "$repo/.llm/scripts/"
  cp "$TEMPLATE_ROOT/.llm/scripts/gen_workspace_map.clj" "$repo/.llm/scripts/"
  cp "$TEMPLATE_ROOT/.llm/scripts/check-brick-map.sh" "$repo/.llm/scripts/"
  cp "$TEMPLATE_ROOT/.llm/scripts/check-workspace-map.sh" "$repo/.llm/scripts/"
  cp "$TEMPLATE_ROOT/.llm/scripts/ensure-brick-map.sh" "$repo/.llm/scripts/"
  cp "$TEMPLATE_ROOT/.llm/scripts/ensure-workspace-map.sh" "$repo/.llm/scripts/"
  cp "$TEMPLATE_ROOT/.llm/scripts/propose-brick-edn.sh" "$repo/.llm/scripts/"
  cp "$TEMPLATE_ROOT/.llm/scripts/propose-project-edn.sh" "$repo/.llm/scripts/"
  chmod +x "$repo/.llm/scripts"/*.sh
  cat > "$repo/deps.edn" <<'EOF'
{:paths [] :deps {org.clojure/clojure {:mvn/version "1.12.0"}}}
EOF
}

base_files() {
  local repo="$1"
  local mode="$2"
  copy_scripts "$repo"
  mkdir -p \
    "$repo/components/invoice/src/example/app/invoice" \
    "$repo/bases/web-api/src/example/app/web_api" \
    "$repo/projects/api"
  cat > "$repo/.llm/repo-context.edn" <<EOF
{:repo-kind :project :adoption-mode :$mode}
EOF
  cat > "$repo/workspace.edn" <<'EOF'
{:top-namespace "example.app" :interface-ns "interface" :projects {"development" {:alias "dev"} "api" {:alias "api"}}}
EOF
  cat > "$repo/DESIGN.md" <<'EOF'
# DESIGN

- INV-01 create invoice
- API-01 expose API
- BILL-01 create billing record
EOF
  cat > "$repo/components/invoice/src/example/app/invoice/interface.clj" <<'EOF'
(ns example.app.invoice.interface)
(defn create [input] input)
(defn validate [invoice] true)
EOF
  cat > "$repo/bases/web-api/src/example/app/web_api/interface.clj" <<'EOF'
(ns example.app.web-api.interface)
(defn routes [] [])
EOF
  cat > "$repo/projects/api/deps.edn" <<'EOF'
{:paths ["src"]
 :deps {poly/web-api {:local/root "../../bases/web-api"}
        poly/invoice {:local/root "../../components/invoice"}}}
EOF
}

complete_metadata() {
  local repo="$1"
  cat > "$repo/components/invoice/brick.edn" <<'EOF'
{:brick/name :invoice
 :brick/type :component
 :brick/purpose "Invoice creation and validation"
 :brick/provides #{:invoice/create :invoice/validate}
 :brick/not-for #{:http/response}
 :brick/requirements ["INV-01"]}
EOF
  cat > "$repo/bases/web-api/brick.edn" <<'EOF'
{:brick/name :web-api
 :brick/type :base
 :brick/purpose "HTTP API entrypoint delegating to component capabilities"
 :brick/entrypoint :http-api
 :brick/uses #{:invoice/create}
 :brick/requirements ["API-01"]}
EOF
  cat > "$repo/projects/api/project.edn" <<'EOF'
{:project/name :api
 :project/type :app
 :project/runtime :service
 :project/purpose "HTTP API deploy unit"
 :project/entrypoints #{:http-api}
 :project/includes {:bases #{:web-api} :components #{:invoice}}
 :project/requirements ["API-01"]
 :project/build {:kind :uberjar}}
EOF
}

run_generate_all() {
  local repo="$1"
  (
    cd "$repo"
    clj -Sdeps '{:paths [".llm/scripts"]}' -X gen-brick-map/generate >/dev/null
    clj -Sdeps '{:paths [".llm/scripts"]}' -X gen-workspace-map/generate >/dev/null
  )
}

run_check_all() {
  local repo="$1"
  (
    cd "$repo"
    ./.llm/scripts/check-brick-map.sh >/dev/null
    ./.llm/scripts/check-workspace-map.sh >/dev/null
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

scenario_01_new_complete() {
  local repo="$BASE/01-new-complete"
  base_files "$repo" complete
  complete_metadata "$repo"
  run_generate_all "$repo"
  run_check_all "$repo"
}

scenario_02_retrofit_skeleton() {
  local repo="$BASE/02-retrofit-skeleton"
  base_files "$repo" retrofit
  (
    cd "$repo"
    ./.llm/scripts/ensure-brick-map.sh >/dev/null
    ./.llm/scripts/ensure-workspace-map.sh >/dev/null
    test -f components/invoice/brick.edn
    test -f bases/web-api/brick.edn
    test -f projects/api/project.edn
    ./.llm/scripts/check-brick-map.sh >/dev/null
    ./.llm/scripts/check-workspace-map.sh >/dev/null
  )
}

scenario_03_partial_todo() {
  local repo="$BASE/03-partial-todo"
  base_files "$repo" partial
  (
    cd "$repo"
    ./.llm/scripts/ensure-brick-map.sh >/dev/null
    ./.llm/scripts/ensure-workspace-map.sh >/dev/null
    ./.llm/scripts/check-brick-map.sh >/dev/null
    ./.llm/scripts/check-workspace-map.sh >/dev/null
  )
}

scenario_04_complete_todo_repair() {
  local repo="$BASE/04-complete-todo-repair"
  base_files "$repo" complete
  (
    cd "$repo"
    ./.llm/scripts/ensure-brick-map.sh >/dev/null
    ./.llm/scripts/ensure-workspace-map.sh >/dev/null
  )
  expect_fail "$repo" "./.llm/scripts/check-brick-map.sh" "unresolved migration-quality" "04-brick-todo"
  expect_fail "$repo" "./.llm/scripts/check-workspace-map.sh" "unresolved migration-quality" "04-project-todo"
  complete_metadata "$repo"
  run_generate_all "$repo"
  run_check_all "$repo"
}

scenario_05_unknown_req_repair() {
  local repo="$BASE/05-unknown-req-repair"
  base_files "$repo" complete
  complete_metadata "$repo"
  sed -i 's/"INV-01"/"INV-99"/' "$repo/components/invoice/brick.edn"
  expect_fail "$repo" "./.llm/scripts/check-brick-map.sh" "references requirement ids not found" "05-unknown-req"
  cat >> "$repo/DESIGN.md" <<'EOF'
- INV-99 legacy imported requirement
EOF
  run_generate_all "$repo"
  run_check_all "$repo"
}

scenario_06_unassigned_design_warn() {
  local repo="$BASE/06-unassigned-design-warn"
  base_files "$repo" complete
  complete_metadata "$repo"
  cat >> "$repo/DESIGN.md" <<'EOF'
- FUT-01 future feature
EOF
  run_generate_all "$repo"
  run_check_all "$repo"
}

scenario_07_missing_use_repair() {
  local repo="$BASE/07-missing-use-repair"
  base_files "$repo" complete
  complete_metadata "$repo"
  sed -i 's|:invoice/create|:payment/capture|' "$repo/bases/web-api/brick.edn"
  expect_fail "$repo" "./.llm/scripts/check-brick-map.sh" "uses capabilities that no component provides" "07-missing-use"
  sed -i 's|:invoice/validate|:invoice/validate :payment/capture|' "$repo/components/invoice/brick.edn"
  cat >> "$repo/components/invoice/src/example/app/invoice/interface.clj" <<'EOF'
(defn capture [payment] payment)
EOF
  run_generate_all "$repo"
  run_check_all "$repo"
}

scenario_08_not_for_conflict_repair() {
  local repo="$BASE/08-not-for-conflict-repair"
  base_files "$repo" complete
  complete_metadata "$repo"
  cat > "$repo/components/invoice/brick.edn" <<'EOF'
{:brick/name :invoice
 :brick/type :component
 :brick/purpose "Invoice creation and validation"
 :brick/provides #{:invoice/create :invoice/validate}
 :brick/not-for #{:invoice/create}
 :brick/requirements ["INV-01"]}
EOF
  expect_fail "$repo" "./.llm/scripts/check-brick-map.sh" "not-for conflicts" "08-not-for"
  complete_metadata "$repo"
  run_generate_all "$repo"
  run_check_all "$repo"
}

scenario_09_generic_allowed() {
  local repo="$BASE/09-generic-allowed"
  base_files "$repo" complete
  complete_metadata "$repo"
  mkdir -p "$repo/components/customer/src/example/app/customer"
  cat > "$repo/components/customer/brick.edn" <<'EOF'
{:brick/name :customer
 :brick/type :component
 :brick/purpose "Customer creation"
 :brick/provides #{:customer/create}
 :brick/requirements []}
EOF
  cat > "$repo/components/customer/src/example/app/customer/interface.clj" <<'EOF'
(ns example.app.customer.interface)
(defn create [input] input)
EOF
  run_generate_all "$repo"
  (cd "$repo" && ./.llm/scripts/check-brick-map.sh >/dev/null)
}

scenario_10_generic_ambiguous_repair() {
  local repo="$BASE/10-generic-ambiguous-repair"
  base_files "$repo" complete
  complete_metadata "$repo"
  mkdir -p "$repo/components/customer/src/example/app/customer"
  cat > "$repo/components/customer/brick.edn" <<'EOF'
{:brick/name :customer
 :brick/type :component
 :brick/purpose "Customer creation"
 :brick/provides #{:customer/register}
 :brick/requirements []}
EOF
  cat > "$repo/components/customer/src/example/app/customer/interface.clj" <<'EOF'
(ns example.app.customer.interface)
(defn create [input] input)
EOF
  expect_fail "$repo" "./.llm/scripts/check-brick-map.sh" "unresolved migration-quality" "10-generic-ambiguous"
  sed -i 's|:customer/register|:customer/create|' "$repo/components/customer/brick.edn"
  run_generate_all "$repo"
  (cd "$repo" && ./.llm/scripts/check-brick-map.sh >/dev/null)
}

scenario_11_project_external_dep_repair() {
  local repo="$BASE/11-project-external-dep-repair"
  base_files "$repo" complete
  complete_metadata "$repo"
  cat > "$repo/projects/api/deps.edn" <<'EOF'
{:paths ["src"]
 :deps {cheshire/cheshire {:mvn/version "5.12.0"}
        poly/web-api {:local/root "../../bases/web-api"}
        poly/invoice {:local/root "../../components/invoice"}}}
EOF
  expect_fail "$repo" "./.llm/scripts/check-workspace-map.sh" "must contain :local/root deps only" "11-project-external"
  cat > "$repo/projects/api/deps.edn" <<'EOF'
{:paths ["src"]
 :deps {poly/web-api {:local/root "../../bases/web-api"}
        poly/invoice {:local/root "../../components/invoice"}}}
EOF
  run_generate_all "$repo"
  (cd "$repo" && ./.llm/scripts/check-workspace-map.sh >/dev/null)
}

scenario_12_missing_project_edn_repair() {
  local repo="$BASE/12-missing-project-edn-repair"
  base_files "$repo" complete
  complete_metadata "$repo"
  rm "$repo/projects/api/project.edn"
  expect_fail "$repo" "./.llm/scripts/check-workspace-map.sh" "workspace.edn registers projects without project.edn" "12-missing-project"
  (cd "$repo" && ./.llm/scripts/ensure-workspace-map.sh >/dev/null)
  test -f "$repo/projects/api/project.edn"
}

scenario_13_entrypoint_repair() {
  local repo="$BASE/13-entrypoint-repair"
  base_files "$repo" complete
  complete_metadata "$repo"
  sed -i 's/:http-api/:grpc-api/' "$repo/projects/api/project.edn"
  expect_fail "$repo" "./.llm/scripts/check-workspace-map.sh" "entrypoint" "13-entrypoint"
  sed -i 's/:grpc-api/:http-api/' "$repo/projects/api/project.edn"
  run_generate_all "$repo"
  (cd "$repo" && ./.llm/scripts/check-workspace-map.sh >/dev/null)
}

scenario_14_workspace_drift_repair() {
  local repo="$BASE/14-workspace-drift-repair"
  base_files "$repo" complete
  complete_metadata "$repo"
  run_generate_all "$repo"
  printf '\nmanual drift\n' >> "$repo/docs/WORKSPACE.md"
  expect_fail "$repo" "./.llm/scripts/check-workspace-map.sh" "not synchronized" "14-workspace-drift"
  (
    cd "$repo"
    clj -Sdeps '{:paths [".llm/scripts"]}' -X gen-workspace-map/generate >/dev/null
    ./.llm/scripts/check-workspace-map.sh >/dev/null
  )
}

scenario_15_duplicate_design_id_repair() {
  local repo="$BASE/15-duplicate-design-id-repair"
  base_files "$repo" complete
  complete_metadata "$repo"
  cat >> "$repo/DESIGN.md" <<'EOF'
- INV-01 duplicated accidental requirement id
EOF
  expect_fail "$repo" "./.llm/scripts/check-brick-map.sh" "duplicate requirement ids" "15-duplicate-design-brick"
  expect_fail "$repo" "./.llm/scripts/check-workspace-map.sh" "duplicate requirement ids" "15-duplicate-design-project"
  sed -i 's/INV-01 duplicated accidental requirement id/INV-02 duplicated accidental requirement id/' "$repo/DESIGN.md"
  run_generate_all "$repo"
  run_check_all "$repo"
}

scenario_16_broken_brick_edn_repair() {
  local repo="$BASE/16-broken-brick-edn-repair"
  base_files "$repo" complete
  complete_metadata "$repo"
  printf '{:brick/name :invoice\n' > "$repo/components/invoice/brick.edn"
  expect_fail "$repo" "./.llm/scripts/check-brick-map.sh" "Invalid EDN" "16-broken-brick-edn"
  complete_metadata "$repo"
  run_generate_all "$repo"
  run_check_all "$repo"
}

scenario_17_broken_project_edn_repair() {
  local repo="$BASE/17-broken-project-edn-repair"
  base_files "$repo" complete
  complete_metadata "$repo"
  printf '{:project/name :api\n' > "$repo/projects/api/project.edn"
  expect_fail "$repo" "./.llm/scripts/check-workspace-map.sh" "Invalid EDN" "17-broken-project-edn"
  complete_metadata "$repo"
  run_generate_all "$repo"
  run_check_all "$repo"
}

scenario_18_missing_interface_repair() {
  local repo="$BASE/18-missing-interface-repair"
  base_files "$repo" complete
  complete_metadata "$repo"
  rm "$repo/components/invoice/src/example/app/invoice/interface.clj"
  expect_fail "$repo" "./.llm/scripts/check-brick-map.sh" "no public API in interface.clj" "18-missing-interface"
  cat > "$repo/components/invoice/src/example/app/invoice/interface.clj" <<'EOF'
(ns example.app.invoice.interface)
(defn create [input] input)
(defn validate [invoice] true)
EOF
  run_generate_all "$repo"
  run_check_all "$repo"
}

scenario_19_empty_interface_repair() {
  local repo="$BASE/19-empty-interface-repair"
  base_files "$repo" complete
  complete_metadata "$repo"
  cat > "$repo/components/invoice/src/example/app/invoice/interface.clj" <<'EOF'
(ns example.app.invoice.interface)
EOF
  expect_fail "$repo" "./.llm/scripts/check-brick-map.sh" "no public API in interface.clj" "19-empty-interface"
  cat > "$repo/components/invoice/src/example/app/invoice/interface.clj" <<'EOF'
(ns example.app.invoice.interface)
(defn create [input] input)
(defn validate [invoice] true)
EOF
  run_generate_all "$repo"
  run_check_all "$repo"
}

scenario_20_project_capability_ownership_repair() {
  local repo="$BASE/20-project-capability-ownership-repair"
  base_files "$repo" complete
  complete_metadata "$repo"
  sed -i 's/:project\/build/:project\/provides #{:invoice\/create} :project\/build/' "$repo/projects/api/project.edn"
  expect_fail "$repo" "./.llm/scripts/check-workspace-map.sh" "must not own capabilities" "20-project-capability-ownership"
  complete_metadata "$repo"
  run_generate_all "$repo"
  run_check_all "$repo"
}

scenario_21_legacy_project_type_repair() {
  local repo="$BASE/21-legacy-project-type-repair"
  base_files "$repo" complete
  complete_metadata "$repo"
  sed -i 's/:project\/type :app/:project\/type :service/' "$repo/projects/api/project.edn"
  expect_fail "$repo" "./.llm/scripts/check-workspace-map.sh" ":project/type must be one of" "21-legacy-project-type"
  complete_metadata "$repo"
  run_generate_all "$repo"
  run_check_all "$repo"
}

scenario "01 new complete" scenario_01_new_complete
scenario "02 retrofit skeleton" scenario_02_retrofit_skeleton
scenario "03 partial todo" scenario_03_partial_todo
scenario "04 complete todo repair" scenario_04_complete_todo_repair
scenario "05 unknown requirement repair" scenario_05_unknown_req_repair
scenario "06 unassigned design warning" scenario_06_unassigned_design_warn
scenario "07 missing capability use repair" scenario_07_missing_use_repair
scenario "08 not-for conflict repair" scenario_08_not_for_conflict_repair
scenario "09 generic function allowed" scenario_09_generic_allowed
scenario "10 generic ambiguity repair" scenario_10_generic_ambiguous_repair
scenario "11 project external dep repair" scenario_11_project_external_dep_repair
scenario "12 missing project.edn repair" scenario_12_missing_project_edn_repair
scenario "13 entrypoint repair" scenario_13_entrypoint_repair
scenario "14 workspace drift repair" scenario_14_workspace_drift_repair
scenario "15 duplicate DESIGN id repair" scenario_15_duplicate_design_id_repair
scenario "16 broken brick.edn repair" scenario_16_broken_brick_edn_repair
scenario "17 broken project.edn repair" scenario_17_broken_project_edn_repair
scenario "18 missing interface repair" scenario_18_missing_interface_repair
scenario "19 empty interface repair" scenario_19_empty_interface_repair
scenario "20 project capability ownership repair" scenario_20_project_capability_ownership_repair
scenario "21 legacy project type repair" scenario_21_legacy_project_type_repair

echo "ALL TEMPLATE MAP SCENARIOS PASSED"
