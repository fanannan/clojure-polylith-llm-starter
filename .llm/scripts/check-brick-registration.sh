#!/usr/bin/env bash
# scripts/check-brick-registration.sh
#
# _POSSIBLE_ISSUES.md D-4 の実装（「機械化」ではなく「不完全さの機械検知」）。
#
# 目的:
#   `poly create` は brick ディレクトリのみを作成し、ルート deps.edn と
#   workspace.edn への登録は行わない。本スクリプトは `components/` / `bases/`
#   配下の brick をすべて走査し、deps.edn の :dev :extra-paths と
#   :dev :extra-deps (:local/root) に登録されているか検査する。
#
# 検査対象:
#   - components/<name>/ （.gitkeep を除く実在 brick ディレクトリ）
#   - bases/<name>/      （同上）
#
# 終了コード:
#   0: 全 brick が適切に登録されている、または brick ゼロ
#   1: 登録漏れあり

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$WORKSPACE_ROOT"

if [ ! -f deps.edn ]; then
  echo "ERROR: deps.edn が存在しません"
  exit 1
fi

missing=0

scan_dir() {
  local kind="$1"  # "components" or "bases"
  local dir="$WORKSPACE_ROOT/$kind"
  if [ ! -d "$dir" ]; then
    return
  fi
  for brick_path in "$dir"/*/; do
    [ -d "$brick_path" ] || continue
    local brick
    brick="$(basename "$brick_path")"
    # .gitkeep 等の非ディレクトリは除外済（ループ条件）
    local src_path="$kind/$brick/src"
    local local_root="$kind/$brick"

    # :extra-paths への src 登録を検査（行コメントを除外）
    if ! grep -v '^[[:space:]]*;;' deps.edn | grep -Fq "\"$src_path\""; then
      echo "ERROR: deps.edn の :dev :extra-paths に \"$src_path\" が登録されていません"
      missing=1
    fi
    # :extra-deps への :local/root 登録を検査
    if ! grep -v '^[[:space:]]*;;' deps.edn | grep -Fq "\"$local_root\""; then
      echo "ERROR: deps.edn の :dev :extra-deps に :local/root \"$local_root\" が登録されていません"
      missing=1
    fi
  done
}

scan_dir "components"
scan_dir "bases"

if [ "$missing" -eq 1 ]; then
  echo ""
  echo "brick 追加後は deps.edn と workspace.edn を同時更新してください（BOOTSTRAP_GUIDE.md §2.5）。"
  exit 1
fi

echo "check-brick-registration: OK"
exit 0
