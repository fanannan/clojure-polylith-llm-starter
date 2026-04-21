#!/usr/bin/env bash
# scripts/check-single-ns-per-file.sh
#
# 目的:
#   1 つの .clj / .cljc / .cljs ファイル内に (ns ...) 宣言が複数ないかを検査する。
#   CODING_GUIDE.md §14.1 の機械化（false positive なし、単純な構文判定）。
#
# 実装方針:
#   - 複数 ns 宣言は文法的には可能だが、Clojure の慣用として 1 ファイル 1 namespace。
#   - clj-kondo には file-level hook がないため shell script で実装。
#   - 行頭が `(ns ` で始まる行を各ファイルで数え、2 以上を報告。
#
# 検査対象:
#   components/ / bases/ / development/src/ 配下の .clj / .cljc / .cljs ファイル
#
# 終了コード:
#   0: 全ファイルが 1 ns 以下
#   1: 複数 ns を持つファイルあり

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$WORKSPACE_ROOT"

found=0

while IFS= read -r f; do
  [ -z "$f" ] && continue
  count="$(grep -cE '^\(ns[[:space:]]' "$f" || true)"
  if [ "$count" -gt 1 ]; then
    echo "ERROR: $f に (ns ...) 宣言が $count 個あります（CODING_GUIDE.md §14.1）"
    grep -nE '^\(ns[[:space:]]' "$f" | sed 's/^/  /'
    found=1
  fi
done < <(find components bases development/src \
  -type f \( -name '*.clj' -o -name '*.cljc' -o -name '*.cljs' \) 2>/dev/null)

if [ "$found" -eq 1 ]; then
  exit 1
fi

echo "check-single-ns-per-file: OK"
exit 0
