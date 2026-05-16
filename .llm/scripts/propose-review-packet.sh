#!/usr/bin/env bash
# Generate a Review Fatigue Packet derived view under .llm/work/views/.
#
# The generated packet is a review aid, not an authority source.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$WORKSPACE_ROOT"

exec "$SCRIPT_DIR/run-clj-tool.sh" main structural-evidence propose "$@"
