#!/usr/bin/env bash
# scripts/ensure-brick-map.sh
#
# Creates missing brick.edn skeletons, regenerates docs/GENERATED_VIEW_BRICKS.md and
# .llm/data/brick-map.edn, and reports TODO placeholders as warnings.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$WORKSPACE_ROOT"

clj -Sdeps '{:paths [".llm/scripts"]}' -X gen-brick-map/ensure
