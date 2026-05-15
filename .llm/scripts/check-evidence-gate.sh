#!/usr/bin/env bash
# Structural Evidence gate.
#
# This is the agent-invariant gate primitive. Git hooks, CI, workspace checks,
# and LLMs should call this script instead of reimplementing evidence checks.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$WORKSPACE_ROOT"

exec "$SCRIPT_DIR/evidence.sh" gate "$@"
