#!/usr/bin/env bash
# .llm/scripts/check-mode-scope.sh
#
# `.llm/repo-context.edn` を SSOT として、テンプレート保守 vs 派生プロジェクトの
# モード境界違反を検査する。実装本体は EDN を直接読む Clojure script。

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$WORKSPACE_ROOT"

exec "$SCRIPT_DIR/run-clj-tool.sh" exec check-mode-scope/run
