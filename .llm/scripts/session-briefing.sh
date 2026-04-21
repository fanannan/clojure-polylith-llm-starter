#!/usr/bin/env bash
# .llm/scripts/session-briefing.sh
#
# Claude Code の SessionStart hook 経由で毎セッション起動時に呼ばれる状態ブリーフィング。
# `CLAUDE.md §1.2.1` 機械化 / `§8.0` 実装着手前の確認の機械化バックアップとして、
# 初回セッションにはブートストラップ未完了の明示を、2 回目以降には前回からの継続点
# （open Q / 最新 ADR / 直近コミット）を LLM の視界に入れる。
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
# フェーズ判定（ブートストラップ完了か、日常開発か）
# -----------------------------------------------------------------------------

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
# open Q 抽出（QUESTIONS.md §2 オープンな質問 ブロック）
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
    echo "- （open な Q はありません）"
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

# -----------------------------------------------------------------------------
# 出力
# -----------------------------------------------------------------------------

echo "# Session Briefing"
echo ""
echo "LLM はこのブリーフィングを先に読んだ上で \`CLAUDE.md §8.0\` の確認ステップに従うこと。"
echo "生成元: \`.llm/scripts/session-briefing.sh\`（Claude Code は SessionStart hook で自動、"
echo "他エージェントは \`AGENTS.md\` 経由で手動実行）。"
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
echo "## 未解決の判断（.llm/memory/QUESTIONS.md §2 open）"
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
echo "## 着手前チェックリスト（CLAUDE.md §8.0）"
echo ""
echo "- [ ] DESIGN.md の関連節を確認"
echo "- [ ] KNOWLEDGE.md の関連節を確認"
echo "- [ ] QUESTIONS.md の open を確認（上記に同じ）"
echo "- [ ] 関連 ADR を確認"

exit 0
