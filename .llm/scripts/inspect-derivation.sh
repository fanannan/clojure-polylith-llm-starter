#!/usr/bin/env bash
# Explain why Structural Evidence derivation classified changed paths as it did.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$WORKSPACE_ROOT"

exec "$SCRIPT_DIR/run-clj-tool.sh" main structural-evidence inspect "$@"
