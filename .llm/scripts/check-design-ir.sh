#!/usr/bin/env bash
# scripts/check-design-ir.sh
#
# Validates DESIGN.md extraction and generated .llm/data/design-ir.edn drift.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$WORKSPACE_ROOT"

exec "$SCRIPT_DIR/run-clj-tool.sh" exec gen-design-ir/check
