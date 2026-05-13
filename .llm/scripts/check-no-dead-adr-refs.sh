#!/usr/bin/env bash
# .llm/scripts/check-no-dead-adr-refs.sh
#
# TEMPLATE モードの live tree に、撤去済みテンプレ ADR slug への参照が
# 残っていないか検査する。派生プロジェクトの ADR-0001 と衝突しないよう、
# 汎用的な ADR-0001 文字列は検査対象にしない。
#
# ここで禁止するのは旧 ADR ファイルへの slug 参照であり、guide に吸収済みの
# 概念名（例: 越境ユースケースの機械化）そのものではない。

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$WORKSPACE_ROOT"

repo_kind="$(grep -oE ':repo-kind[[:space:]]+:[a-z-]+' .llm/repo-context.edn 2>/dev/null | head -1 | sed -E 's/.*:repo-kind[[:space:]]+:([a-z-]+).*/\1/' || true)"

if [ "$repo_kind" != "template" ]; then
  echo "check-no-dead-adr-refs: skipped ($repo_kind mode)"
  exit 0
fi

tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT

git ls-files \
  | grep -v '^.llm/memory/archive/' \
  | grep -v '^.git/' \
  | grep -v '^.llm/scripts/check-no-dead-adr-refs.sh$' \
  | while IFS= read -r f; do
      [ -f "$f" ] && printf '%s\n' "$f"
    done > "$tmp"

if [ ! -s "$tmp" ]; then
  echo "check-no-dead-adr-refs: OK (no tracked files)"
  exit 0
fi

dead_id="0001"
dead_mid="cross"
dead_rest="entity"
dead_pattern="${dead_id}-${dead_mid}-${dead_rest}(-mechanization)?"

if xargs -a "$tmp" rg -n "$dead_pattern" > "$tmp.matches"; then
  echo "ERROR: 撤去済みテンプレ ADR slug への live reference が残っています:"
  sed 's/^/  /' "$tmp.matches"
  rm -f "$tmp.matches"
  exit 1
else
  rm -f "$tmp.matches"
fi

echo "check-no-dead-adr-refs: OK"
