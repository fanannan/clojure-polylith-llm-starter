#!/usr/bin/env bash
# .llm/scripts/check-mode-scope.sh
#
# 目的:
#   `.llm/repo-context.edn` manifest を SSOT とし、テンプレート保守 vs 派生プロジェクトの
#   モード境界違反を機械的に検出する。CLAUDE.md §1.2.1 機械化原則の実装。
#
# 検査内容:
#   - TEMPLATE モード: :project-owned 配下に「テンプレ保守マーカー」混入を検出 → WARN
#     :section-scoped の template 区間にプロジェクトコンテンツマーカー → WARN
#     :section-scoped の project 区間にテンプレ保守マーカー → WARN
#   - PROJECT モード: :template-owned 配下の変更を検出 → WARN（git diff 比較）
#   - CONFLICT 検出時: hard error
#
# 設計規律:
#   - SSOT: 所有権情報は .llm/repo-context.edn のみが持つ。本スクリプトは manifest を読む
#   - heuristic: 完全機械化は意味解析が必要。複合マーカーで false positive を抑制
#   - WARN 運用: 既存検査群と整合（block しない）
#
# 終了コード:
#   0: 検査通過（WARN は許容）
#   1: CONFLICT 検出（hard error）

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$WORKSPACE_ROOT"

MANIFEST=".llm/repo-context.edn"

# -----------------------------------------------------------------------------
# モード判定（session-briefing.sh と同一ロジック、conflict 最優先）
# -----------------------------------------------------------------------------

if [ ! -f "$MANIFEST" ]; then
  echo "check-mode-scope: SKIP（$MANIFEST 不在）"
  exit 0
fi

REPO_KIND="$(grep -oE ':repo-kind[[:space:]]+:[a-z-]+' "$MANIFEST" 2>/dev/null \
  | head -1 \
  | sed -E 's/.*:repo-kind[[:space:]]+:([a-z-]+).*/\1/' || true)"

has_bootstrap_traces() {
  if [ -f "workspace.edn" ] && ! grep -q 'myorg\.myapp' workspace.edn 2>/dev/null; then
    return 0
  fi
  if [ -d "projects" ] && [ -n "$(find projects -maxdepth 1 -mindepth 1 -type d 2>/dev/null)" ]; then
    return 0
  fi
  return 1
}

# 1. CONFLICT 検出
if [ "$REPO_KIND" = "template" ] && has_bootstrap_traces; then
  echo "check-mode-scope: CONFLICT (hard error)"
  echo "  manifest が :repo-kind :template を主張するが bootstrap 完了痕跡あり"
  echo "  対処: BOOTSTRAP_GUIDE.md に従い manifest を :project に transform"
  exit 1
fi

# -----------------------------------------------------------------------------
# マーカー検出 helper
# -----------------------------------------------------------------------------
#
# テンプレ保守マーカー: 複合語または MAINTAINERS_GUIDE / .llm/guide/ への参照
# プロジェクトコンテンツマーカー: Q-YYYY-MM-NNN 形式の番号、ADR-NNNN への参照

TEMPLATE_MARKER_PATTERN='本テンプレート|テンプレ自身|テンプレート自身|MAINTAINERS_GUIDE'
PROJECT_CONTENT_PATTERN='Q-[0-9]{4}-[0-9]{2}-[0-9]{3}|ADR-[0-9]{4}'

# .llm/memory/adr/NNNN-*.md ファイル群を列挙
list_project_adrs() {
  find .llm/memory/adr -maxdepth 1 -type f -name '[0-9][0-9][0-9][0-9]-*.md' 2>/dev/null
}

# section_extract <file> <start-pattern> <end-pattern>
# markdown 見出しベースで section 範囲を抽出
section_extract() {
  local file="$1"
  local start_pat="$2"
  local end_pat="$3"
  awk -v sp="$start_pat" -v ep="$end_pat" '
    $0 ~ sp { in_section=1; next }
    in_section && $0 ~ ep { in_section=0 }
    in_section { print }
  ' "$file"
}

# -----------------------------------------------------------------------------
# 検査本体
# -----------------------------------------------------------------------------

warn_count=0

warn() {
  echo "WARN: $1"
  warn_count=$((warn_count + 1))
}

case "$REPO_KIND" in
  template)
    # TEMPLATE モード: :project-owned 配下にテンプレ保守マーカー混入を検出

    # 1. .llm/memory/adr/NNNN-*.md
    while IFS= read -r adr_file; do
      [ -z "$adr_file" ] && continue
      if grep -qE "$TEMPLATE_MARKER_PATTERN" "$adr_file" 2>/dev/null; then
        warn "$adr_file: テンプレ保守マーカー検出（派生プロジェクト ADR 領域にテンプレ自身の決定が混入の疑い）"
        warn "  -> テンプレ保守決定は .llm/memory/archive/maintainer-discussions/ へ"
      fi
    done < <(list_project_adrs)

    # 2. DESIGN.md §1+（§0 は scaffolding で template-owned）
    if [ -f "DESIGN.md" ]; then
      content="$(section_extract DESIGN.md '^## .*1\.' '^## .*[0-9][0-9]?\.')"
      if [ -n "$content" ] && echo "$content" | grep -qE "$TEMPLATE_MARKER_PATTERN"; then
        warn "DESIGN.md §1+: テンプレ保守マーカー検出（プロダクト仕様にテンプレ自身の方針が混入の疑い）"
        warn "  -> テンプレ自身の方針は .llm/guide/MAINTAINERS_GUIDE.md や CLAUDE.md に書く"
      fi
    fi

    # 3. KNOWLEDGE.md §1+ / QUESTIONS.md §2+ にテンプレ保守マーカー
    if [ -f ".llm/memory/KNOWLEDGE.md" ]; then
      # §1 以降を抽出（## 1. で始まり、§0 セクション外）
      content="$(section_extract .llm/memory/KNOWLEDGE.md '^## 1\.' '^## [A-Z]')"
      if [ -n "$content" ] && echo "$content" | grep -qE "$TEMPLATE_MARKER_PATTERN"; then
        warn ".llm/memory/KNOWLEDGE.md §1+: テンプレ保守マーカー検出（プロジェクト知識領域に混入の疑い）"
      fi
    fi
    if [ -f ".llm/memory/QUESTIONS.md" ]; then
      content="$(section_extract .llm/memory/QUESTIONS.md '^## 2\.' '^## [A-Z]')"
      if [ -n "$content" ] && echo "$content" | grep -qE "$TEMPLATE_MARKER_PATTERN"; then
        warn ".llm/memory/QUESTIONS.md §2+: テンプレ保守マーカー検出（プロジェクト Q 領域に混入の疑い）"
      fi
    fi

    # 4. components/<brick>/, bases/<brick>/, projects/<deploy>/ 配下
    for area in components bases projects; do
      if [ -d "$area" ]; then
        while IFS= read -r subfile; do
          [ -z "$subfile" ] && continue
          if grep -qE "$TEMPLATE_MARKER_PATTERN" "$subfile" 2>/dev/null; then
            warn "$subfile: テンプレ保守マーカー検出（派生プロジェクト brick/deploy 領域に混入の疑い）"
          fi
        done < <(find "$area" -mindepth 2 -type f \( -name '*.clj' -o -name '*.edn' -o -name '*.md' \) 2>/dev/null)
      fi
    done
    ;;

  project)
    # PROJECT モード: :template-owned 配下の変更を git diff で検出
    # 派生プロジェクト側でテンプレ規約を勝手に変更している兆候

    if git rev-parse --git-dir >/dev/null 2>&1; then
      # HEAD と比較。staged + unstaged の変更を検出
      template_paths=(
        "CLAUDE.md"
        ".llm/guide/"
        ".llm/scripts/"
        ".clj-kondo/polyguard/"
        ".llm/repo-context.edn"
        ".llm/memory/adr/README.md"
        ".llm/memory/adr/template.md"
      )
      for p in "${template_paths[@]}"; do
        if git status --porcelain "$p" 2>/dev/null | grep -q .; then
          warn "$p に変更あり（派生プロジェクトでテンプレ規約を変更している兆候）"
          warn "  -> 変更が必要ならテンプレ側に PR/Issue で還元するのが原則"
        fi
      done
    fi
    ;;

  "")
    echo "check-mode-scope: SKIP（manifest の :repo-kind が読めません）"
    exit 0
    ;;

  *)
    echo "check-mode-scope: SKIP（未知の :repo-kind '$REPO_KIND'）"
    exit 0
    ;;
esac

if [ "$warn_count" -gt 0 ]; then
  echo "check-mode-scope: $warn_count WARN ($REPO_KIND モード)"
else
  echo "check-mode-scope: OK ($REPO_KIND モード)"
fi

exit 0
