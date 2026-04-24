#!/usr/bin/env bash
# scripts/check-placeholders.sh
#
# 目的:
#   workspace.edn / deps.edn にプレースホルダ `myorg.myapp` が残っていないか検査する。
#   `CLAUDE.md §5.5` 完了条件の一部として `check-workspace-integrity.sh` 経由で呼ばれる。
#
# 対象:
#   設定ファイルのみ（workspace.edn / deps.edn）。
#   サンプルコード（POLYLITH_GUIDE.md / KNOWLEDGE.md 内のコード例）は対象外。
#
# 運用タイミング:
#   - ブートストラップの早い段階（workspace.edn 編集直後）
#   - 完了条件として全ビルド通過前に必ず通る
#
# 終了コード:
#   0: プレースホルダなし
#   1: プレースホルダ残存、または対象ファイル欠落

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$WORKSPACE_ROOT"

placeholder_found=0

is_template_distribution_state() {
  [ -f "README.md" ] || return 1
  grep -q '^# ＜TODO: プロジェクト名＞' README.md || return 1
  [ ! -d "projects" ] || [ -z "$(find projects -mindepth 1 -maxdepth 1 2>/dev/null)" ]
}

check_file() {
  local file="$1"
  if [ ! -f "$file" ]; then
    echo "ERROR: $file が存在しません"
    placeholder_found=1
    return
  fi
  # コメント行（先頭 ;;）を除いて myorg.myapp を検索
  if grep -n 'myorg\.myapp' "$file" | grep -v '^[0-9]*:[[:space:]]*;;' > /dev/null; then
    echo "ERROR: $file にプレースホルダ 'myorg.myapp' が残存:"
    grep -n 'myorg\.myapp' "$file" | grep -v '^[0-9]*:[[:space:]]*;;' | sed 's/^/  /'
    placeholder_found=1
  fi
}

check_file "workspace.edn"
check_file "deps.edn"

if [ "$placeholder_found" -eq 1 ]; then
  if is_template_distribution_state; then
    echo ""
    echo "check-placeholders: skipped (template distribution state)"
    exit 0
  fi
  echo ""
  echo "プレースホルダを実プロジェクト名に置換してください（BOOTSTRAP_GUIDE.md §2.1）。"
  exit 1
fi

echo "check-placeholders: OK"
exit 0
