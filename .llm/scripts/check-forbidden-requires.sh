#!/usr/bin/env bash
# scripts/check-forbidden-requires.sh
#
# 目的:
#   STACK_GUIDE.md §8.1（禁止）/ §8.2（非推奨）ライブラリの namespace が
#   `.clj` / `.cljc` / `.cljs` ファイルの (ns ... (:require ...)) 句で使われていないか検査する。
#
# 設計背景:
#   当初は .clj-kondo/polyguard/forbidden_requires.clj の `:analyze-call` hook で検査していたが、
#   clj-kondo の hook 機構は `clojure.core/ns` special form に対して発火しないため、
#   require 宣言時の検査が事実上無効化されていた。shell script ベースで grep により同等の
#   機械化を実現する。
#
#   コード内の関数呼び出しレベルの検知は `clj-kondo :discouraged-var` が担当（config.edn）。
#   deps.edn の採用宣言レベルは check-deprecated-libs.sh が担当。
#   本スクリプトは「(:require [org.apache.log4j :as log4j])」のような形を検知する。
#
# パターンソース:
#   `.llm/data/forbidden-requires.patterns`（`.llm/scripts/gen_lib_catalog.clj` が
#   STACK_GUIDE.md §8 の `;; lib-catalog` EDN block から生成）。
#   生成物と STACK_GUIDE.md の同期は `check-workspace-integrity.sh` の diff
#   検証で自動確認される。
#
# パターンファイルフォーマット:
#   `<ns-regex-pattern>|<reason>` の行列（`#` で始まる行と空行は無視）。
#
# 終了コード:
#   0: 禁止 namespace の require なし
#   1: 禁止 namespace の require あり
#   2: パターンファイル不在（`clj -X:gen-lib-catalog` 未実行）

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$WORKSPACE_ROOT"

PATTERNS_FILE=".llm/data/forbidden-requires.patterns"

if [ ! -f "$PATTERNS_FILE" ]; then
  echo "ERROR: $PATTERNS_FILE が存在しません" >&2
  echo "       \`clj -X:gen-lib-catalog\` を実行して生成してください" >&2
  exit 2
fi

declare -a FORBIDDEN_PREFIXES=()
while IFS= read -r line; do
  [ -z "$line" ] && continue
  case "$line" in \#*) continue ;; esac
  FORBIDDEN_PREFIXES+=("$line")
done < "$PATTERNS_FILE"

if [ "${#FORBIDDEN_PREFIXES[@]}" -eq 0 ]; then
  echo "check-forbidden-requires: パターン 0 件、skipped"
  exit 0
fi

# 検査対象: components/, bases/ 配下の clj* ファイル
# development/src は一時デバッグ用なので検査対象外。
declare -a SRC_FILES=()
while IFS= read -r f; do
  SRC_FILES+=("$f")
done < <(find components bases 2>/dev/null \
  -type f \( -name '*.clj' -o -name '*.cljc' -o -name '*.cljs' \) || true)

if [ "${#SRC_FILES[@]}" -eq 0 ]; then
  echo "check-forbidden-requires: no source files, skipped"
  exit 0
fi

found=0

for src in "${SRC_FILES[@]}"; do
  for entry in "${FORBIDDEN_PREFIXES[@]}"; do
    prefix="${entry%%|*}"
    reason="${entry#*|}"
    # (:require [<ns> ...]) または (:require <ns>) のパターンを検知
    if grep -Eq "\[${prefix}(\.[a-zA-Z0-9._-]+)?[[:space:]\]]" "$src" 2>/dev/null; then
      echo "ERROR: $src で禁止 namespace の require を検知:"
      echo "  接頭辞: ${prefix}"
      echo "  $reason"
      grep -nE "\[${prefix}(\.[a-zA-Z0-9._-]+)?[[:space:]\]]" "$src" | sed 's/^/    /'
      found=1
    fi
  done
done

if [ "$found" -eq 1 ]; then
  echo ""
  echo "STACK_GUIDE.md §8 を参照し、推奨代替に置き換えてください。"
  exit 1
fi

echo "check-forbidden-requires: OK"
exit 0
