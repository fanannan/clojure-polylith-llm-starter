#!/usr/bin/env bash
# Run fixture-level self-tests for Structural Evidence derivation rules.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$WORKSPACE_ROOT"

exec "$SCRIPT_DIR/run-clj-tool.sh" main structural-evidence self-test "$@"
