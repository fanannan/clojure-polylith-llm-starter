#!/usr/bin/env bash
# .llm/scripts/session-briefing.sh
#
# Claude Code の SessionStart hook 経由で毎セッション起動時に呼ばれる状態ブリーフィング。
# `CLAUDE.md §1.2.1` 機械化 / `§8.0` 実装着手前の確認の機械化バックアップとして、
# 初回セッションには初期化未完了の明示を、2 回目以降には前回からの継続点
# （未対応(open) Q / 最新 ADR / 直近コミット）を LLM の視界に入れる。
#
# 設計規律:
#   - 副作用なし（stdout のみ、ファイル改変せず）
#   - 原則は coreutils + git のみ。trace health だけは clj が存在する時に 5 秒上限で実行
#   - 終了コードは常に 0（失敗でセッション起動をブロックしない）
#   - 軽量チェックのみ実施（`check-workspace-integrity.sh` のような重検査は含まない）
#
# 手動実行:
#   Codex 等 SessionStart hook 機構のないエージェント向けに、`AGENTS.md` 経由で
#   `bash .llm/scripts/session-briefing.sh` の手動実行を案内する。
#
# 運用タイミング:
#   - Claude Code セッション起動時（自動、`.claude/settings.json` の hook）
#   - 非 Claude エージェント起動時（手動、AGENTS.md の指示）
#   - 状態確認したい任意のタイミング（ユーザが手動実行）

# -e は外す（briefing は途中失敗しても最後まで出す。検知と情報提示が分離）
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$WORKSPACE_ROOT"

AUDIT=0
AUDIT_FORMAT="text"

while [ "$#" -gt 0 ]; do
  case "$1" in
    --audit)
      AUDIT=1
      shift
      ;;
    --format)
      if [ "$#" -lt 2 ]; then
        echo "ERROR: --format requires a value" >&2
        exit 2
      fi
      AUDIT_FORMAT="${2:-}"
      shift 2
      ;;
    *)
      echo "ERROR: unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

edn_escape_string() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

collect_briefing_audit() {
  local file="$1"

  AUDIT_TOTAL_LINES="$(wc -l < "$file" | tr -d ' ')"
  AUDIT_CONTROL_LINE="$(awk '/^## Control Plane$/ { print NR; exit }' "$file")"
  AUDIT_CONTROL_LINE="${AUDIT_CONTROL_LINE:-0}"
  AUDIT_BULLET_COUNT="$(awk '
    /^## Control Plane$/ { in_control=1; next }
    in_control && /^$/ { if (seen_bullet) exit; next }
    in_control && /^## / { exit }
    in_control && /^- / { seen_bullet=1; count++ }
    END { print count + 0 }
  ' "$file")"
  AUDIT_NEXT_ACTION="$(awk -F': ' '
    /^## Control Plane$/ { in_control=1; next }
    in_control && /^## / { exit }
    in_control && /^- next action surface:/ {
      sub(/^- next action surface: /, "", $0)
      print
      exit
    }
  ' "$file")"
  AUDIT_FORBIDDEN_SURFACES="$(grep -E 'repo-control\.sh|system-health|philosophy command|benchctl' "$file" 2>/dev/null \
    | sed 's/^[[:space:]]*//' \
    | sort -u || true)"

  if [ "$AUDIT_CONTROL_LINE" -gt 0 ] \
    && [ "$AUDIT_CONTROL_LINE" -le 15 ] \
    && [ "$AUDIT_BULLET_COUNT" -le 6 ] \
    && printf '%s\n' "$AUDIT_NEXT_ACTION" | grep -q 'evidence.sh what-now' \
    && [ -z "$AUDIT_FORBIDDEN_SURFACES" ]; then
    AUDIT_BUDGET="ok"
  else
    AUDIT_BUDGET="warn"
  fi
}

print_briefing_audit_text() {
  local file="$1"
  collect_briefing_audit "$file"

  echo ""
  echo "## Briefing Audit"
  echo ""
  echo "- total lines: $AUDIT_TOTAL_LINES"
  echo "- Control Plane first line: $AUDIT_CONTROL_LINE"
  echo "- Control Plane bullet count: $AUDIT_BULLET_COUNT"
  echo "- next-action surface: ${AUDIT_NEXT_ACTION:-unknown}"
  if [ -n "$AUDIT_FORBIDDEN_SURFACES" ]; then
    echo "- forbidden surfaces:"
    printf '%s\n' "$AUDIT_FORBIDDEN_SURFACES" | sed 's/^/  - /'
  else
    echo "- forbidden surfaces: none"
  fi
  echo "- budget: $AUDIT_BUDGET"
}

print_briefing_audit_edn() {
  local file="$1"
  local forbidden_edn
  collect_briefing_audit "$file"

  if [ -n "$AUDIT_FORBIDDEN_SURFACES" ]; then
    forbidden_edn="["
    while IFS= read -r line; do
      [ -n "$line" ] || continue
      forbidden_edn="${forbidden_edn}\"$(edn_escape_string "$line")\" "
    done <<EOF
$AUDIT_FORBIDDEN_SURFACES
EOF
    forbidden_edn="${forbidden_edn% }]"
  else
    forbidden_edn="[]"
  fi

  printf '{:briefing/audit {:total-lines %s\n' "$AUDIT_TOTAL_LINES"
  printf '                  :control-plane/first-line %s\n' "$AUDIT_CONTROL_LINE"
  printf '                  :control-plane/bullets %s\n' "$AUDIT_BULLET_COUNT"
  printf '                  :next-action-surface "%s"\n' "$(edn_escape_string "$AUDIT_NEXT_ACTION")"
  printf '                  :forbidden-surfaces %s\n' "$forbidden_edn"
  printf '                  :budget :%s}}\n' "$AUDIT_BUDGET"
}

if [ "$AUDIT" -eq 1 ]; then
  tmp="$(mktemp)"
  trap 'rm -f "$tmp"' EXIT
  bash "$SCRIPT_DIR/session-briefing.sh" > "$tmp"
  case "$AUDIT_FORMAT" in
    text|"")
      cat "$tmp"
      print_briefing_audit_text "$tmp"
      ;;
    edn)
      print_briefing_audit_edn "$tmp"
      ;;
    *)
      echo "ERROR: unsupported --format: $AUDIT_FORMAT" >&2
      exit 2
      ;;
  esac
  exit 0
fi

# -----------------------------------------------------------------------------
# モード判定（manifest .llm/repo-context.edn の :repo-kind を読む）
# -----------------------------------------------------------------------------
#
# 判定順序（conflict 最優先）:
#   1. manifest が :template + bootstrap 完了痕跡  → non-blocking ERROR を表示
#   2. manifest が :template                        → TEMPLATE MAINTENANCE モード
#   3. manifest が :project                         → PROJECT モード
#   4. manifest 不在                                → non-blocking ERROR + migration 案内

read_repo_kind() {
  # `.llm/repo-context.edn` から :repo-kind の値を抽出。:template または :project を返す。
  # 失敗時は空文字。
  if [ ! -f ".llm/repo-context.edn" ]; then
    return 0
  fi
  grep -oE ':repo-kind[[:space:]]+:[a-z-]+' .llm/repo-context.edn 2>/dev/null \
    | head -1 \
    | sed -E 's/.*:repo-kind[[:space:]]+:([a-z-]+).*/\1/'
}

read_project_name() {
  # :project モード時の :project-name を抽出
  if [ ! -f ".llm/repo-context.edn" ]; then
    return 0
  fi
  grep -oE ':project-name[[:space:]]+"[^"]+"' .llm/repo-context.edn 2>/dev/null \
    | head -1 \
    | sed -E 's/.*"([^"]+)".*/\1/'
}

read_workspace_kind() {
  if [ ! -f ".llm/repo-context.edn" ]; then
    return 0
  fi
  grep -oE ':workspace-kind[[:space:]]+:[a-z-]+' .llm/repo-context.edn 2>/dev/null \
    | head -1 \
    | sed -E 's/.*:workspace-kind[[:space:]]+:([a-z-]+).*/\1/'
}

read_adoption_mode() {
  if [ ! -f ".llm/repo-context.edn" ]; then
    return 0
  fi
  grep -oE ':adoption-mode[[:space:]]+:[a-z-]+' .llm/repo-context.edn 2>/dev/null \
    | head -1 \
    | sed -E 's/.*:adoption-mode[[:space:]]+:([a-z-]+).*/\1/'
}

has_bootstrap_traces() {
  # bootstrap 完了痕跡:
  #   - workspace.edn が 'myorg.myapp' プレースホルダから書き換わっている、または
  #   - projects/ 配下に deploy project が存在する
  if [ -f "workspace.edn" ] && ! grep -q 'myorg\.myapp' workspace.edn 2>/dev/null; then
    return 0
  fi
  if [ -d "projects" ] && [ -n "$(find projects -maxdepth 1 -mindepth 1 -type d 2>/dev/null)" ]; then
    return 0
  fi
  return 1
}

# 旧 is_bootstrap_phase: PROJECT モードかつ bootstrap 未完了痕跡を持つ場合に true
# （:project モードで初期化未完了の派生プロジェクトを briefing が detect するため）
is_bootstrap_phase() {
  # workspace.edn に配布時プレースホルダ myorg.myapp が残っていれば bootstrap
  if [ -f "workspace.edn" ] && grep -q 'myorg\.myapp' workspace.edn 2>/dev/null; then
    return 0
  fi
  # projects/ が存在しないか空（development 以外の deploy project 未作成）なら bootstrap
  if [ ! -d "projects" ]; then
    return 0
  fi
  if [ -z "$(find projects -maxdepth 1 -mindepth 1 -type d 2>/dev/null)" ]; then
    return 0
  fi
  return 1
}

describe_bootstrap_gaps() {
  local gaps=()

  if [ -f "workspace.edn" ] && grep -q 'myorg\.myapp' workspace.edn 2>/dev/null; then
    gaps+=("workspace.edn に配布時プレースホルダ 'myorg.myapp' が残存（BOOTSTRAP_GUIDE.md §2.1）")
  fi

  if [ ! -d "projects" ]; then
    gaps+=("projects/ ディレクトリが未作成（poly create project 未実行、BOOTSTRAP_GUIDE.md §2.9）")
  elif [ -z "$(find projects -maxdepth 1 -mindepth 1 -type d 2>/dev/null)" ]; then
    gaps+=("projects/ ディレクトリが空（deploy project 未作成、BOOTSTRAP_GUIDE.md §2.9）")
  fi

  local brick_count=0
  if [ -d "components" ]; then
    brick_count=$((brick_count + $(find components -maxdepth 1 -mindepth 1 -type d 2>/dev/null | wc -l | tr -d ' ')))
  fi
  if [ -d "bases" ]; then
    brick_count=$((brick_count + $(find bases -maxdepth 1 -mindepth 1 -type d 2>/dev/null | wc -l | tr -d ' ')))
  fi
  if [ "$brick_count" -eq 0 ]; then
    gaps+=("components/ bases/ に brick が未登録（poly create component|base 未実行）")
  fi

  if [ "${#gaps[@]}" -eq 0 ]; then
    echo "- （プレースホルダ・projects・brick の軽量判定では未完了項目なし）"
  else
    local g
    for g in "${gaps[@]}"; do
      echo "- $g"
    done
  fi
}

# -----------------------------------------------------------------------------
# 未対応(open) Q 抽出（QUESTIONS.md §2 未対応 ブロック）
# -----------------------------------------------------------------------------

extract_open_questions() {
  local file=".llm/memory/QUESTIONS.md"
  if [ ! -f "$file" ]; then
    echo "- （QUESTIONS.md が存在しません）"
    return
  fi
  local output
  # §2 と §3 の間にある ## Q-YYYY-MM-NNN: <title> 行のみ抽出（コメントアウトされた
  # サンプルは '<!--' の内側にあるため、awk で HTML コメントを無視する）
  output=$(awk '
    /<!--/ { in_comment=1 }
    /-->/  { in_comment=0; next }
    in_comment { next }
    /^## 2\./ { in_section=1; next }
    /^## 3\./ { in_section=0 }
    in_section && /^## Q-[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9][0-9]:/ {
      sub(/^## /, "- ", $0)
      print
    }
  ' "$file")
  if [ -z "$output" ]; then
    echo "- （未対応(open) の Q はありません）"
  else
    echo "$output"
  fi
}

# -----------------------------------------------------------------------------
# 最新 ADR 抽出
# -----------------------------------------------------------------------------

latest_adr() {
  local adr_dir=".llm/memory/adr"
  if [ ! -d "$adr_dir" ]; then
    echo "- （adr/ ディレクトリが存在しません）"
    return
  fi
  local latest
  latest=$(ls "$adr_dir"/[0-9][0-9][0-9][0-9]-*.md 2>/dev/null | sort | tail -1)
  if [ -z "$latest" ]; then
    echo "- （ADR はまだ発行されていません）"
    return
  fi
  local name
  name=$(basename "$latest" .md)
  local status
  status=$(grep -m1 '^- \*\*Status\*\*:' "$latest" 2>/dev/null \
    | sed 's/^- \*\*Status\*\*:[[:space:]]*//' \
    | sed 's/[[:space:]]*<!--.*-->[[:space:]]*$//' \
    | sed 's/[[:space:]]*$//')
  if [ -n "$status" ]; then
    echo "- $name (Status: $status)"
  else
    echo "- $name"
  fi
}

# -----------------------------------------------------------------------------
# 直近コミット
# -----------------------------------------------------------------------------

recent_commits() {
  if ! git rev-parse --git-dir >/dev/null 2>&1; then
    echo "- （git リポジトリではありません）"
    return
  fi
  local log
  log=$(git log -5 --oneline --no-color 2>/dev/null || true)
  if [ -z "$log" ]; then
    echo "- （コミット履歴なし）"
    return
  fi
  echo "$log" | sed 's/^/- /'
}

git_hooks_brief() {
  if ! git rev-parse --git-dir >/dev/null 2>&1; then
    echo "- （git リポジトリではありません）"
    return
  fi

  local hooks_path
  hooks_path="$(git config --get core.hooksPath 2>/dev/null || true)"
  if [ "$hooks_path" = ".githooks" ]; then
    echo "- core.hooksPath: .githooks (OK)"
  elif [ -z "$hooks_path" ]; then
    echo "- core.hooksPath: 未設定"
    echo "  → ./.llm/scripts/install-git-hooks.sh"
  else
    echo "- core.hooksPath: $hooks_path (expected .githooks)"
    echo "  → ./.llm/scripts/install-git-hooks.sh"
  fi

  if [ -x ".githooks/pre-commit" ]; then
    echo "- pre-commit hook: .githooks/pre-commit (executable)"
  elif [ -f ".githooks/pre-commit" ]; then
    echo "- pre-commit hook: .githooks/pre-commit (not executable)"
  else
    echo "- pre-commit hook: missing"
  fi
}

reference_repos_brief() {
  local f=".llm/reference-repos.edn"
  if [ ! -f "$f" ]; then
    echo "- 未登録（任意機能）。別 repo の brick 設計を比較参照する場合は \`reference-repos.sh add <path>\`"
    return
  fi
  # 軽量カウント: コメント行を除き、引用符付きパスを持つ行数を数える
  # （briefing は重検査を呼ばない。有効性検証は reference-repos.sh check に委譲）。
  local n
  n="$(grep -vE '^[[:space:]]*;;' "$f" 2>/dev/null | grep -cE '"[^"]+"' || true)"
  if [ "${n:-0}" -gt 0 ]; then
    echo "- 登録済み: $n 件（\`reference-repos.sh list\` で詳細 / \`reference-repos.sh check\` で有効性検証）"
  else
    echo "- 未登録（任意機能）。別 repo の brick 設計を比較参照する場合は \`reference-repos.sh add <path>\`"
  fi
}

add_template_test_command() {
  local command="$1"
  case "
$TEMPLATE_TEST_COMMANDS
" in
    *"
$command
"*) ;;
    *) TEMPLATE_TEST_COMMANDS="${TEMPLATE_TEST_COMMANDS}${command}
" ;;
  esac
}

collect_template_test_commands_for_path() {
  local path="$1"

  case "$path" in
    .llm/scripts/session-briefing.sh|.llm/template-only/tests/check-session-briefing-scenarios.sh)
      add_template_test_command "./.llm/template-only/tests/check-session-briefing-scenarios.sh"
      ;;
    .llm/scripts/gen_brick_map.clj|.llm/scripts/gen_workspace_map.clj|\
.llm/scripts/check-brick-map.sh|.llm/scripts/check-workspace-map.sh|\
.llm/scripts/ensure-brick-map.sh|.llm/scripts/ensure-workspace-map.sh|\
.llm/scripts/propose-brick-edn.sh|.llm/scripts/propose-project-edn.sh|\
.llm/template-only/tests/check-map-scenarios.sh)
      add_template_test_command "./.llm/template-only/tests/check-map-scenarios.sh"
      ;;
    .llm/scripts/gen-design-ir.sh|.llm/scripts/gen_design_ir.clj|\
.llm/scripts/check-design-ir.sh|.llm/template-only/tests/check-design-ir-scenarios.sh)
      add_template_test_command "./.llm/template-only/tests/check-design-ir-scenarios.sh"
      ;;
    .llm/scripts/check-trace-metadata.sh|.llm/scripts/check_trace_metadata.clj|\
.llm/scripts/check-trace-index.sh|.llm/scripts/gen-trace-index.sh|\
.llm/scripts/gen_trace_index.clj|.llm/template-only/tests/check-trace-metadata-scenarios.sh)
      add_template_test_command "./.llm/template-only/tests/check-trace-metadata-scenarios.sh"
      ;;
    .llm/scripts/gen-obligation-index.sh|.llm/scripts/gen_obligation_index.clj|\
.llm/scripts/check-obligation-index.sh|.llm/scripts/derive-work-frontier.sh|\
.llm/template-only/tests/check-obligation-frontier-scenarios.sh)
      add_template_test_command "./.llm/template-only/tests/check-obligation-frontier-scenarios.sh"
      ;;
    .llm/template-only/instrument/cases.edn|\
.llm/template-only/instrument/incident-index.edn|\
.llm/template-only/instrument/check-cases.sh|\
.llm/template-only/instrument/check_instrument_cases.clj|\
.llm/template-only/tests/check-instrument-cases-smoke.sh)
      add_template_test_command "./.llm/template-only/tests/check-instrument-cases-smoke.sh"
      ;;
    .llm/template-only/instrument/setup-run.sh|\
.llm/template-only/instrument/score-run.sh|\
.llm/template-only/tests/check-instrument-setup-smoke.sh)
      add_template_test_command "./.llm/template-only/tests/check-instrument-setup-smoke.sh"
      ;;
    .llm/template-only/instrument/summarize-runs.sh|\
.llm/template-only/tests/check-instrument-summary-smoke.sh)
      add_template_test_command "./.llm/template-only/tests/check-instrument-summary-smoke.sh"
      ;;
    .llm/template-only/benchmark/setup-run.sh|.llm/template-only/benchmark/README.md|\
.llm/template-only/tests/check-benchmark-setup-smoke.sh)
      add_template_test_command "./.llm/template-only/tests/check-benchmark-setup-smoke.sh"
      ;;
  esac
}

template_test_recommendation_brief() {
  echo "## L0 Template Test Recommendation"
  echo ""
  echo "- scope: advisory L0 prompt for template maintenance; this is not a repo mode or automatic gate"

  if ! git rev-parse --git-dir >/dev/null 2>&1; then
    echo "- current diff: unavailable (not a git repository)"
    echo "- suggested template-only checks before close: consult .llm/template-only/tests/README.md"
    return
  fi

  local changed_paths
  changed_paths="$(git status --porcelain=v1 --untracked-files=all 2>/dev/null \
    | sed -E 's/^...//' \
    | sed -E 's/^.* -> //' \
    | sort -u)"

  if [ -z "$changed_paths" ]; then
    echo "- current diff: none"
    echo "- suggested template-only checks before close: none from current diff"
    echo "- note: after editing template-owned scripts, re-run briefing or consult .llm/template-only/tests/README.md"
    return
  fi

  TEMPLATE_TEST_COMMANDS=""
  local path
  while IFS= read -r path; do
    [ -n "$path" ] || continue
    collect_template_test_commands_for_path "$path"
  done <<EOF
$changed_paths
EOF

  if [ -z "$TEMPLATE_TEST_COMMANDS" ]; then
    echo "- current diff: present, but no template-only E2E mapping matched"
    echo "- suggested template-only checks before close: choose task-specific checks manually"
    return
  fi

  echo "- current diff: matched template-maintenance test mapping"
  echo "- suggested template-only checks before close:"
  printf '%s' "$TEMPLATE_TEST_COMMANDS" | sed '/^$/d; s/^/  - /'
}

trace_health_brief() {
  if [ ! -x ".llm/scripts/trace-impact.sh" ]; then
    echo "- trace health: skipped（trace-impact.sh がありません）"
    return
  fi
  if ! command -v clj >/dev/null 2>&1; then
    echo "- trace health: skipped（clj コマンドがありません）"
    return
  fi
  if command -v timeout >/dev/null 2>&1; then
    timeout 5 ./.llm/scripts/trace-impact.sh --health --brief 2>/dev/null \
      || echo "- trace health: skipped（5 秒以内に取得できませんでした）"
  else
    ./.llm/scripts/trace-impact.sh --health --brief 2>/dev/null \
      || echo "- trace health: skipped（取得に失敗しました）"
  fi
}

work_frontier_brief() {
  echo "## Work Frontier"
  echo ""
  if [ ! -x ".llm/scripts/derive-work-frontier.sh" ]; then
    echo "- derive-work-frontier.sh がありません"
    return
  fi
  if command -v timeout >/dev/null 2>&1; then
    timeout 5 ./.llm/scripts/derive-work-frontier.sh 2>/dev/null \
      || echo "- work frontier: skipped（5 秒以内に取得できませんでした）"
  else
    ./.llm/scripts/derive-work-frontier.sh 2>/dev/null \
      || echo "- work frontier: skipped（取得に失敗しました）"
  fi
}

evidence_plane_brief() {
  echo "## Evidence Plane"
  echo ""

  echo "### Active Review Fatigue Views (.llm/work/views/)"
  local found_active=0
  if [ -d ".llm/work/views" ]; then
    local f
    for f in .llm/work/views/*.edn; do
      [ -e "$f" ] || continue
      case "$f" in
        *.predict.edn) continue ;;
      esac
      if grep -qE '(^|[[:space:]]):status[[:space:]]+:(clean-close|closed)' "$f" 2>/dev/null; then
        continue
      fi
      found_active=1
      local name
      name="$(basename "$f" .edn)"
      local save_policy
      save_policy="$(grep -m1 -oE ':save-policy[[:space:]]+:[a-z-]+' "$f" 2>/dev/null | sed -E 's/.*:([a-z-]+)$/\1/')"
      local view_status
      view_status="$(grep -m1 -oE ':status[[:space:]]+:[a-z-]+' "$f" 2>/dev/null | sed -E 's/.*:([a-z-]+)$/\1/')"
      local declaration=".llm/work/declarations/$name.edn"
      local run_result=".llm/work/runs/$name.edn"
      local residual
      if [ ! -f "$declaration" ]; then
        residual="residual: pending"
      elif grep -qE ':semantic-impact-not-derived[[:space:]]+nil|:unknowns-not-captured-by-derivation[[:space:]]+nil|:cross-brick-effects-not-in-trace-index[[:space:]]+nil|:override[[:space:]]+nil|:remaining-fatigue[[:space:]]+nil' "$declaration" 2>/dev/null; then
        residual="residual: pending"
      else
        residual="residual: declared"
      fi
      local run_status="evidence: pending"
      if [ -f "$run_result" ]; then
        run_status="evidence: recorded"
      fi
      echo "- $name (${save_policy:-save:?}, ${view_status:-view:?}, $residual, $run_status)"
      echo "  → next: 下の What Now で current fingerprint との対応を確認する"
      echo "    （view は捨てられる生成物、declaration は人間宣言として保持されます）"
    done
  fi
  if [ "$found_active" -eq 0 ]; then
    echo "- （active view はありません）"
  fi

  echo ""
  echo "### Human Declarations (.llm/work/declarations/)"
  local found_declaration=0
  if [ -d ".llm/work/declarations" ]; then
    local d
    for d in .llm/work/declarations/*.edn; do
      [ -e "$d" ] || continue
      found_declaration=1
      local decl_name
      decl_name="$(basename "$d" .edn)"
      if [ -f ".llm/work/views/$decl_name.edn" ] || [ -f ".llm/work/views/$decl_name.predict.edn" ]; then
        echo "- $decl_name (attached-or-fingerprint-checked-by what-now)"
      else
        echo "- $decl_name (orphan: no derived view)"
      fi
    done
  fi
  if [ "$found_declaration" -eq 0 ]; then
    echo "- （human declaration はありません）"
  fi

  echo ""
  echo "### Closed Evidence Records (.llm/evidence/closed/)"
  local found_closed=0
  if [ -d ".llm/evidence/closed" ]; then
    local c
    for c in $(find .llm/evidence/closed -maxdepth 1 -type f -name '*.edn' 2>/dev/null | sort | tail -5); do
      found_closed=1
      echo "- $(basename "$c" .edn)"
    done
  fi
  if [ "$found_closed" -eq 0 ]; then
    echo "- （closed record はありません）"
  fi

  echo ""
  echo "### What Now"
  if [ -x ".llm/scripts/evidence.sh" ]; then
    local what_now_output
    if what_now_output="$(./.llm/scripts/evidence.sh what-now 2>/dev/null)"; then
      printf '%s\n' "$what_now_output" | sed 's/^/- /'
    else
      echo "- evidence.sh what-now は取得できませんでした"
    fi
  else
    echo "- evidence.sh がありません"
  fi

  echo ""
  echo "### Staged Evidence Gate"
  if [ -x ".llm/scripts/check-evidence-gate.sh" ]; then
    ./.llm/scripts/check-evidence-gate.sh --staged --advisory --no-write 2>/dev/null \
      | sed 's/^/- /'
  else
    echo "- check-evidence-gate.sh がありません"
  fi

  echo ""
  echo "### Last Commit Evidence Gate"
  if [ -x ".llm/scripts/check-evidence-gate.sh" ] && git rev-parse --verify HEAD~1 >/dev/null 2>&1; then
    ./.llm/scripts/check-evidence-gate.sh --base HEAD~1 --head HEAD --advisory --no-write 2>/dev/null \
      | sed 's/^/- /'
  else
    echo "- skipped"
  fi

  echo ""
  echo "### Stale / Expired Evidence"
  echo "- what-now と status は closed record の invalidated-by から stale candidate を surface する。"
}

# Display-only salience layer. This does not introduce a new authority, planner,
# or task system; it summarizes existing repo-context / what-now / gate surfaces.
control_plane_brief() {
  local state="$1"
  local phase="${2:-}"

  echo "## Control Plane"
  echo ""

  case "$state" in
    unknown)
      echo "- mode source: .llm/repo-context.edn (missing)"
      echo "- primary action: recover or add the repo-context manifest before normal work"
      echo "- next action surface: ./.llm/scripts/evidence.sh what-now after manifest recovery"
      echo "- completion gate: unavailable until repo mode is known"
      ;;
    conflict)
      echo "- mode source: .llm/repo-context.edn (:repo-kind :template) plus bootstrap traces"
      echo "- state: conflict; do not continue as either pure template or complete project"
      echo "- primary action: complete BOOTSTRAP_GUIDE manifest transform, then re-run briefing"
      echo "- next action surface: mode repair first; evidence.sh what-now after conflict is cleared"
      echo "- completion gate: check-workspace-integrity.sh blocks this state"
      ;;
    template)
      echo "- mode source: .llm/repo-context.edn (:repo-kind :template)"
      echo "- operating intent: template maintenance; keep :project-owned paths untouched"
      echo "- decision log: maintainer archive, not ADR"
      echo "- next action surface: ./.llm/scripts/evidence.sh what-now"
      echo "- completion gate: task-specific checks + Structural Evidence gate + check-workspace-integrity.sh"
      ;;
    project)
      echo "- mode source: .llm/repo-context.edn (:repo-kind :project)"
      if [ "$phase" = "bootstrap" ]; then
        echo "- operating intent: derived project bootstrap; finish identity and deploy shape first"
      else
        echo "- operating intent: derived project development"
      fi
      echo "- decision log: QUESTIONS / KNOWLEDGE / ADR by decision type"
      echo "- next action surface: ./.llm/scripts/evidence.sh what-now"
      echo "- completion gate: CLAUDE.md §5.5 + Structural Evidence gate + task-specific checks"
      ;;
  esac
}

tcp_port_open() {
  local host="$1"
  local port="$2"
  if command -v nc >/dev/null 2>&1; then
    nc -z -w 1 "$host" "$port" >/dev/null 2>&1
    return $?
  fi
  (echo > "/dev/tcp/$host/$port") >/dev/null 2>&1
}

# -----------------------------------------------------------------------------
# 出力
# -----------------------------------------------------------------------------

echo "# Session Briefing"
echo ""
echo "LLM はこのブリーフィングを先に読んだ上で \`CLAUDE.md §8.0\` の確認ステップに従うこと。"
echo "関連文書だけでなく、触るファイル周辺の docstring / comment / 近接 test も関連文脈として確認すること。"
echo "生成元: \`.llm/scripts/session-briefing.sh\`（Claude Code は SessionStart hook で自動、"
echo "他エージェントは \`AGENTS.md\` 経由で手動実行）。"
echo ""

# モード判定（conflict 最優先）
REPO_KIND="$(read_repo_kind || true)"
PROJECT_NAME="$(read_project_name || true)"
WORKSPACE_KIND="$(read_workspace_kind || true)"
ADOPTION_MODE="$(read_adoption_mode || true)"

# 1. manifest 不在
if [ -z "$REPO_KIND" ]; then
  echo "## MODE: UNKNOWN（manifest 不在）"
  echo ""
  control_plane_brief "unknown"
  echo ""
  echo "**ERROR (non-blocking)**: \`.llm/repo-context.edn\` が見つかりません。"
  echo "既存派生プロジェクトへ本テンプレ更新を持ち込んだ直後なら、"
  echo "\`:repo-kind :project\` の manifest を追加してください。"
  echo "\`.llm/scripts/llm-template-adopt.sh\` が利用可能なら、まず統合入口で detect / propose / plan を表示してください。"
  echo "個別に進める場合は \`.llm/scripts/propose-repo-context.sh\` で manifest 候補を表示してください。"
  echo ""
  echo "派生プロジェクトでの作成例（\`:project-name\` を実値に置換し、\`:ownership\` は"
  echo "テンプレ最新版の \`.llm/repo-context.edn\` からコピー）:"
  echo ""
  cat <<'EOF'
{:repo-kind :project
 :derived-from "clojure-polylith-llm-starter"
 :project-name "myorg.myapp"
 :workspace-kind :polylith
 :adoption-mode :retrofit
 :capabilities #{:deps-edn :polylith :llm-guides}
 :ownership {;; copy from the latest template .llm/repo-context.edn
             }}
EOF
  echo ""
  echo "詳細: \`.llm/guide/MAINTAINERS_GUIDE.md\` §7.6 / §7.7"
  exit 0
fi

# 2. conflict: manifest が :template かつ bootstrap 完了痕跡
if [ "$REPO_KIND" = "template" ] && has_bootstrap_traces; then
  echo "## MODE: CONFLICT"
  echo ""
  control_plane_brief "conflict"
  echo ""
  echo "**ERROR (non-blocking)**: bootstrap finalization missed manifest transform"
  echo ""
  echo "manifest は \`:repo-kind :template\` を主張していますが、bootstrap 完了痕跡"
  echo "（workspace.edn がプレースホルダから書き換わっている、または projects/ 配下に"
  echo "deploy project が存在）が検出されました。"
  echo ""
  echo "**対処**: BOOTSTRAP_GUIDE.md の完了処理を再実施し、\`.llm/repo-context.edn\` を"
  echo "transform（\`:repo-kind :template\` → \`:project\`、\`:template-name\` → \`:derived-from\`、"
  echo "\`:project-name\` を追加）してください。"
  exit 0
fi

# 3. TEMPLATE MAINTENANCE モード
if [ "$REPO_KIND" = "template" ]; then
  echo "## MODE: TEMPLATE MAINTENANCE"
  echo ""
  control_plane_brief "template"
  echo ""
  echo "**本リポジトリはテンプレート自身**（clojure-polylith-llm-starter）。"
  echo "テンプレート保守作業を行うモード。派生プロジェクトの記録領域（manifest の"
  echo "\`:project-owned\`）には触れない。"
  echo ""
  echo "### 次に読む文書"
  echo "- \`.llm/guide/MAINTAINERS_GUIDE.md\` §5（保守作業の手順）"
  echo "- \`.llm/memory/archive/maintainer-discussions/\` 配下（過去のテンプレ保守議論）"
  echo "- 所有権の正本: \`.llm/repo-context.edn\`"
  echo ""
  echo "### テンプレ保守決定の記録先"
  echo "- 議論経緯: \`.llm/memory/archive/maintainer-discussions/YYYY/YYYY-MM.md\`"
  echo "- \`.llm/memory/adr/\` は派生プロジェクト専用のため使用しない"
  echo ""
  template_test_recommendation_brief
  echo ""
  echo "## 直近のコミット（git log -5 --oneline）"
  echo ""
  recent_commits
  echo ""
  echo "## Git Hooks"
  echo ""
  git_hooks_brief
  echo ""
  work_frontier_brief
  echo ""
  evidence_plane_brief
  echo ""
else
  # 4. PROJECT モード
  if [ -n "$PROJECT_NAME" ]; then
    echo "## MODE: PROJECT ($PROJECT_NAME)"
  else
    echo "## MODE: PROJECT"
  fi
  if [ -n "$WORKSPACE_KIND" ] || [ -n "$ADOPTION_MODE" ]; then
    echo ""
    echo "- workspace-kind: ${WORKSPACE_KIND:-unknown}"
    echo "- adoption-mode: ${ADOPTION_MODE:-unspecified}"
  fi
  echo ""

  if is_bootstrap_phase; then
    control_plane_brief "project" "bootstrap"
    echo ""
    echo "## Phase: bootstrap（初期化未完了）"
    echo ""
    echo "### 未完了の指標"
    describe_bootstrap_gaps
    echo ""
    echo "### 次に読む文書"
    echo "- \`.llm/guide/BOOTSTRAP_GUIDE.md\` §2（初期化手順）"
    echo "- \`DESIGN.md\`（§1-§4, §8 の埋め込み対象）"
  else
    control_plane_brief "project" "development"
    echo ""
    echo "## Phase: development（ブートストラップ完了済）"
    echo ""
    echo "### 次に読む文書（作業内容に応じて）"
    echo "- \`DESIGN.md\` の関連節（仕様確認）"
    echo "- \`.llm/memory/KNOWLEDGE.md\` の関連節（契約・不変条件）"
    echo "- \`CLAUDE.md §8\` 作業プロトコル"
  fi

  echo ""
  echo "## 未解決の判断（.llm/memory/QUESTIONS.md §2 未対応(open)）"
  echo ""
  extract_open_questions
  echo ""
  echo "## 最新の決定（ADR）"
  echo ""
  latest_adr
  echo ""
  echo "## 直近のコミット（git log -5 --oneline）"
  echo ""
  recent_commits
  echo ""
  echo "## Git Hooks"
  echo ""
  git_hooks_brief
  echo ""
  work_frontier_brief
  echo ""

  evidence_plane_brief
  echo ""

  echo "## Trace Health"
  echo ""
  trace_health_brief
  echo ""
fi

echo "## 参照可能 repo（POLYLITH_GUIDE.md §9）"
echo ""
reference_repos_brief
echo ""

# 以下の section は PROJECT モード時のみ表示する（テンプレ保守では非該当）
if [ "$REPO_KIND" != "project" ]; then
  exit 0
fi

echo "## REPL 状態（CLAUDE.md §9 Live Workbench Protocol）"
echo ""
# .nrepl-port 存在 + 実際の TCP 接続可否で判定（crash 後の stale file を見誤らない）
nrepl_status="not-running"
nrepl_port=""
if [ -f ".nrepl-port" ]; then
  nrepl_port="$(tr -d '[:space:]' < .nrepl-port 2>/dev/null)"
  if [ -n "$nrepl_port" ] && tcp_port_open "127.0.0.1" "$nrepl_port"; then
    nrepl_status="running"
  elif [ -n "$nrepl_port" ]; then
    nrepl_status="stale"   # file あるが接続不能（crash 等）
  fi
fi

case "$nrepl_status" in
  running)
    echo "- nREPL サーバ: 起動中 (port $nrepl_port、TCP 接続確認済、identity は repl-eval.sh 実行時に検証)"
    if [ -f ".nrepl-session" ]; then
      echo "- 永続 session (.nrepl-session): 存在"
    else
      echo "- 永続 session (.nrepl-session): 未作成（初回 eval 時に clone）"
    fi
    echo ""
    echo "**LLM への指示**: **REPL を primary workbench として自発的に使用する**。"
    echo "  ./.llm/scripts/repl-eval.sh --expr '(dev.user/status)'   # 最初の状態確認"
    echo "  ./.llm/scripts/repl-eval.sh --load-file <編集したファイル>  # 即反映"
    echo "  ./.llm/scripts/repl-eval.sh --ns <ns> --expr '(<fn> <args>)'  # 評価"
    echo "  CLAUDE.md §8.0.0 trigger matrix で REPL 必須条件を確認、§9 Live Diagnosis Loop に従う。"
    ;;
  stale)
    echo "- nREPL サーバ: **stale** (.nrepl-port は port $nrepl_port を示すが TCP 接続不能)"
    echo ""
    echo "**LLM への指示**: nREPL プロセスが終了している可能性。以下をユーザに依頼:"
    echo "  1. 残存 .nrepl-port を削除: rm .nrepl-port"
    echo "  2. 別ターミナルで再起動: clj -M:dev:nrepl"
    ;;
  *)
    echo "- nREPL サーバ: 未起動"
    echo ""
    echo "**LLM への指示**: REPL 駆動開発（CLAUDE.md §9）の前提として、ユーザに"
    echo "次の起動を依頼する（1 度だけ）:"
    echo "  別ターミナルで: clj -M:dev:nrepl"
    echo "  起動後は ./.llm/scripts/repl-eval.sh が自動で .nrepl-port を読む。"
    ;;
esac

echo ""
echo "## 上位文脈（該当時のみ）"
echo ""
echo "本リポジトリが上位プロジェクト・親 Issue・外部設計合意の下で動いている場合、"
echo "**KNOWLEDGE / ADR を本リポジトリに書く前に上位の方針を確認する**。"
echo "判定基準: README に「このリポジトリは XXX の一部」記述、直近対話で上位言及、"
echo "またはユーザが session 開始時に上位プロジェクトを示した。"
echo "該当する場合: 上位 README / Issue を参照し、scope（implementation-project /"
echo "parent-project / both）を明示してから提案する（COLLABORATION_GUIDE.md §6.4）。"
echo "単独利用の場合: 本節は無視してよい。"
echo "詳細: CLAUDE.md §8.0 第 5 項、アンチパターン §7.8。"
echo ""
echo "## 着手前チェックリスト（CLAUDE.md §8.0）"
echo ""
echo "- [ ] DESIGN.md の関連節を確認"
echo "- [ ] KNOWLEDGE.md の関連節を確認"
echo "- [ ] QUESTIONS.md の未対応(open) を確認（上記に同じ）"
echo "- [ ] 関連 ADR を確認"
echo "- [ ] 上位文脈の有無を確認（該当時のみ scope 明示）"
echo "- [ ] REPL 状態を確認（起動中なら live workbench として使用開始）"

exit 0
