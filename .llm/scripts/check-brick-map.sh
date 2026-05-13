#!/usr/bin/env bash
# scripts/check-brick-map.sh
#
# Validates Polylith brick metadata and generated docs/BRICKS.md drift.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$WORKSPACE_ROOT"

clj -Sdeps '{:paths [".llm/scripts"]}' -X gen-brick-map/check
