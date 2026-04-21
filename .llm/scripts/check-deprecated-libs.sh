#!/usr/bin/env bash
# scripts/check-deprecated-libs.sh
#
# clj-kondo :discouraged-var を補完する、
# brick deps.edn 採用宣言レベルの検査。
#
# 目的:
#   STACK_GUIDE.md §8 に列挙された非推奨ライブラリが brick deps.edn に
#   採用されていないか検査する。コード内使用は clj-kondo :discouraged-var で
#   検知するが、deps.edn に採用宣言が残っているパターンは
#   clj-kondo では捕捉できないため、shell script で補完する。
#
# 検査対象:
#   全 deps.edn（ルート・brick・project）
#
# パターンソース:
#   `.llm/data/deprecated-libs.patterns`（`.llm/scripts/gen_lib_catalog.clj` が
#   STACK_GUIDE.md §8 の `;; lib-catalog` EDN block から生成）。
#   生成物と STACK_GUIDE.md の同期は `check-workspace-integrity.sh` の diff
#   検証で自動確認される。
#
# パターンファイルフォーマット:
#   `<regex-pattern>|<reason>` の行列（`#` で始まる行と空行は無視）。
#
# 終了コード:
#   0: 非推奨ライブラリの採用なし
#   1: 採用あり
#   2: パターンファイル不在（`clj -X:gen-lib-catalog` 未実行）

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$WORKSPACE_ROOT"

PATTERNS_FILE=".llm/data/deprecated-libs.patterns"

if [ ! -f "$PATTERNS_FILE" ]; then
  echo "ERROR: $PATTERNS_FILE が存在しません" >&2
  echo "       \`clj -X:gen-lib-catalog\` を実行して生成してください" >&2
  exit 2
fi

# パターンファイルから有効行を読み取る (コメント・空行を除外)。
declare -a DEPRECATED_PATTERNS=()
while IFS= read -r line; do
  [ -z "$line" ] && continue
  case "$line" in \#*) continue ;; esac
  DEPRECATED_PATTERNS+=("$line")
done < "$PATTERNS_FILE"

if [ "${#DEPRECATED_PATTERNS[@]}" -eq 0 ]; then
  echo "check-deprecated-libs: パターン 0 件、skipped"
  exit 0
fi

# deps.edn を workspace ルート・全 brick・全 project で探す
declare -a DEPS_FILES=()
while IFS= read -r f; do
  DEPS_FILES+=("$f")
done < <(find . \
  -name deps.edn \
  -not -path "./.cpcache/*" \
  -not -path "./.clj-kondo/*" \
  -not -path "*/target/*" \
  -not -path "*/.cpcache/*")

if [ "${#DEPS_FILES[@]}" -eq 0 ]; then
  echo "check-deprecated-libs: no deps.edn found, skipped"
  exit 0
fi

found=0

for deps in "${DEPS_FILES[@]}"; do
  for entry in "${DEPRECATED_PATTERNS[@]}"; do
    pattern="${entry%%|*}"
    recommend="${entry#*|}"
    if grep -v '^[[:space:]]*;;' "$deps" 2>/dev/null | grep -Eq "$pattern"; then
      echo "ERROR: $deps に非推奨ライブラリが採用されています:"
      echo "  パターン: $pattern"
      echo "  $recommend"
      grep -nE "$pattern" "$deps" | sed 's/^/    /'
      found=1
    fi
  done
done

if [ "$found" -eq 1 ]; then
  echo ""
  echo "STACK_GUIDE.md §8 を参照し、推奨代替に置き換えてください。"
  exit 1
fi

echo "check-deprecated-libs: OK"
exit 0
