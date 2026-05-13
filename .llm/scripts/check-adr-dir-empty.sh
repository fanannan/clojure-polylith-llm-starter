#!/usr/bin/env bash
# .llm/scripts/check-adr-dir-empty.sh
#
# TEMPLATE モードでは .llm/memory/adr/ に README.md / template.md 以外の
# 実 ADR を置かない。派生プロジェクトでは ADR は project-owned なので skip。

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$WORKSPACE_ROOT"

repo_kind="$(grep -oE ':repo-kind[[:space:]]+:[a-z-]+' .llm/repo-context.edn 2>/dev/null | head -1 | sed -E 's/.*:repo-kind[[:space:]]+:([a-z-]+).*/\1/' || true)"

if [ "$repo_kind" != "template" ]; then
  echo "check-adr-dir-empty: skipped ($repo_kind mode)"
  exit 0
fi

extra="$(find .llm/memory/adr -maxdepth 1 -type f \
  ! -name 'README.md' \
  ! -name 'template.md' \
  -print 2>/dev/null | sort)"

if [ -n "$extra" ]; then
  echo "ERROR: TEMPLATE モードでは .llm/memory/adr/ に実 ADR を置きません:"
  echo "$extra" | sed 's/^/  /'
  echo "  -> テンプレ保守判断は guide / manifest / scripts へ吸収し、process は maintainer-discussions に一時記録してください"
  exit 1
fi

echo "check-adr-dir-empty: OK"
