#!/usr/bin/env bash
# scripts/check-conflicting-libs.sh
#
# 目的:
#   同一 deps.edn 内で併用禁止のライブラリペア（:relations :conflicts-with
#   で宣言されたもの）が両方宣言されていないか検査する。
#
# 設計背景:
#   lib-catalog の `:relations :conflicts-with` は lib 間の併用不可関係を
#   宣言的に記録する（例: Biff は Integrant と競合、Biff 内部で独自 lifecycle
#   管理を行うため）。本スクリプトは gen_lib_catalog.clj が生成した
#   `.llm/data/conflicts.patterns` を読み、各 deps.edn に両 coord が同時に
#   宣言されていないかを regex マッチで検査する。
#
#   コード内使用は clj-kondo :discouraged-var で検知、deps.edn の採用宣言
#   レベルは check-deprecated-libs.sh / check-forbidden-requires.sh が担当。
#   本スクリプトは「採用自体は個別に許容されるが組合せが禁止」を検知する
#   補完層（MAINTAINERS_GUIDE.md §5.10 L4）。
#
# パターンソース:
#   `.llm/data/conflicts.patterns`（`.llm/scripts/gen_lib_catalog.clj` が
#   STACK_GUIDE.md §3 の `;; lib-catalog` block から生成）。
#   生成物と STACK_GUIDE.md の同期は `check-workspace-integrity.sh` の diff
#   検証で自動確認される。
#
# パターンファイルフォーマット:
#   `<coord-a-regex>|<coord-b-regex>|<reason>` の行列。
#   coord-a / coord-b はアルファベット順に正規化される（`#` で始まる行と
#   空行は無視）。
#
# 終了コード:
#   0: 競合なし
#   1: 競合あり
#   2: パターンファイル不在（`clj -X:gen-lib-catalog` 未実行）

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$WORKSPACE_ROOT"

PATTERNS_FILE=".llm/data/conflicts.patterns"

if [ ! -f "$PATTERNS_FILE" ]; then
  echo "ERROR: $PATTERNS_FILE が存在しません" >&2
  echo "       \`clj -X:gen-lib-catalog\` を実行して生成してください" >&2
  exit 2
fi

declare -a CONFLICT_PAIRS=()
while IFS= read -r line; do
  [ -z "$line" ] && continue
  case "$line" in \#*) continue ;; esac
  CONFLICT_PAIRS+=("$line")
done < "$PATTERNS_FILE"

if [ "${#CONFLICT_PAIRS[@]}" -eq 0 ]; then
  echo "check-conflicting-libs: 競合ペア 0 件、skipped"
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
  echo "check-conflicting-libs: no deps.edn found, skipped"
  exit 0
fi

found=0

for deps in "${DEPS_FILES[@]}"; do
  # コメント行（先頭 ;;）は除外して検査
  deps_content=$(grep -v '^[[:space:]]*;;' "$deps" 2>/dev/null || true)
  [ -z "$deps_content" ] && continue

  for entry in "${CONFLICT_PAIRS[@]}"; do
    # "coord-a|coord-b|reason" を分解
    # IFS を一時変更して 3 トークンに分割
    pattern_a="${entry%%|*}"
    rest="${entry#*|}"
    pattern_b="${rest%%|*}"
    reason="${rest#*|}"

    if echo "$deps_content" | grep -Eq "$pattern_a" \
       && echo "$deps_content" | grep -Eq "$pattern_b"; then
      echo "ERROR: $deps に併用禁止の組合せが宣言されています:"
      echo "  $pattern_a  と  $pattern_b  は併用不可"
      echo "  $reason"
      echo "  該当行:"
      grep -nE "$pattern_a|$pattern_b" "$deps" | sed 's/^/    /'
      found=1
    fi
  done
done

if [ "$found" -eq 1 ]; then
  echo ""
  echo "STACK_GUIDE.md §3 の :relations :conflicts-with を参照し、片方を削除してください。"
  exit 1
fi

echo "check-conflicting-libs: OK"
exit 0
