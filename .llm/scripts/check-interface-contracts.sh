#!/usr/bin/env bash
# scripts/check-interface-contracts.sh
#
# _POSSIBLE_ISSUES.md A-1 の実装。
#
# 目的:
#   各 components/*/src/**/interface.clj について、`defn` で宣言された公開関数すべてに
#   対応する `m/=>` 契約が同一ファイル内（または同一 namespace 内）に存在するかを検査する。
#   本テンプレートの最重要原則 §1.1.1 全域性の守護機構。
#
# 実装方針:
#   - AST 解析は clj-kondo custom hook の領分だが、A-1 は「複数 form 間の対応関係」で、
#     file-level 解析に該当する。clj-kondo hook は per-call で file-level 解析が
#     難しいため、shell script で実装する。
#   - `defn fn-name` / `defn- fn-name` / `m/=> fn-name` を grep で抽出、集合比較。
#   - defn- は private なので m/=> 対応は任意（検査対象外）。defn のみ必須。
#
# 検査対象:
#   components/*/src/**/interface.clj（interface のみ、core.clj 等は免除）
#
# 終了コード:
#   0: 全 defn に対応する m/=> あり、または interface.clj なし
#   1: 対応する m/=> がない defn あり

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$WORKSPACE_ROOT"

missing=0

if [ ! -d components ]; then
  echo "check-interface-contracts: no components/ directory, skipped"
  exit 0
fi

while IFS= read -r ifile; do
  [ -z "$ifile" ] && continue
  # defn と defn- 両方から function name を抽出（defn- は検査対象外）
  defns="$(grep -oE '^\(defn[[:space:]]+[a-zA-Z0-9_?!*><=+-]+' "$ifile" \
           | grep -v '^(defn-' \
           | awk '{print $2}' \
           | sort -u || true)"
  contracts="$(grep -oE '\(m/=>[[:space:]]+[a-zA-Z0-9_?!*><=+-]+' "$ifile" \
              | awk '{print $2}' \
              | sort -u || true)"

  for fn in $defns; do
    if ! echo "$contracts" | grep -qx "$fn"; then
      echo "ERROR: $ifile の公開関数 \`$fn\` に対応する (m/=> $fn ...) 契約がありません"
      echo "  → interface.clj 内で (m/=> $fn [:=> [:cat <入力>] <出力>]) を追加してください"
      echo "  → CLAUDE.md §4.1 / CODING_GUIDE.md §2.1.1 / _POSSIBLE_ISSUES.md A-1"
      missing=1
    fi
  done
done < <(find components -type f -name 'interface.clj' 2>/dev/null)

if [ "$missing" -eq 1 ]; then
  exit 1
fi

echo "check-interface-contracts: OK"
exit 0
