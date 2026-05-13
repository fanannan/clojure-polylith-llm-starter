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
#   - 依存なし（coreutils + git のみ。Babashka / Clojure ランタイム不要）
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
echo "生成元: \`.llm/scripts/session-briefing.sh\`（Claude Code は SessionStart hook で自動、"
echo "他エージェントは \`AGENTS.md\` 経由で手動実行）。"
echo ""

# モード判定（conflict 最優先）
REPO_KIND="$(read_repo_kind || true)"
PROJECT_NAME="$(read_project_name || true)"

# 1. manifest 不在
if [ -z "$REPO_KIND" ]; then
  echo "## MODE: UNKNOWN（manifest 不在）"
  echo ""
  echo "**ERROR (non-blocking)**: \`.llm/repo-context.edn\` が見つかりません。"
  echo "既存派生プロジェクトへ本テンプレ更新を持ち込んだ直後なら、"
  echo "\`:repo-kind :project\` の manifest を追加してください。"
  echo ""
  echo "派生プロジェクトでの作成例（\`:project-name\` を実値に置換し、\`:ownership\` は"
  echo "テンプレ最新版の \`.llm/repo-context.edn\` からコピー）:"
  echo ""
  cat <<'EOF'
{:repo-kind :project
 :derived-from "clojure-polylith-llm-starter"
 :project-name "myorg.myapp"
 :ownership {;; copy from the latest template .llm/repo-context.edn
             }}
EOF
  echo ""
  echo "詳細: \`.llm/guide/MAINTAINERS_GUIDE.md\` §7.6"
  exit 0
fi

# 2. conflict: manifest が :template かつ bootstrap 完了痕跡
if [ "$REPO_KIND" = "template" ] && has_bootstrap_traces; then
  echo "## MODE: CONFLICT"
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
  echo "## 直近のコミット（git log -5 --oneline）"
  echo ""
  recent_commits
  echo ""
else
  # 4. PROJECT モード
  if [ -n "$PROJECT_NAME" ]; then
    echo "## MODE: PROJECT ($PROJECT_NAME)"
  else
    echo "## MODE: PROJECT"
  fi
  echo ""

  if is_bootstrap_phase; then
    echo "## Phase: bootstrap（初期化未完了）"
    echo ""
    echo "### 未完了の指標"
    describe_bootstrap_gaps
    echo ""
    echo "### 次に読む文書"
    echo "- \`.llm/guide/BOOTSTRAP_GUIDE.md\` §2（初期化手順）"
    echo "- \`DESIGN.md\`（§1-§4, §8 の埋め込み対象）"
  else
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
fi

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
