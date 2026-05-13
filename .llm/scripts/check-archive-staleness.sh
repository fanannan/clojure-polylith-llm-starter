#!/usr/bin/env bash
# .llm/scripts/check-archive-staleness.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$WORKSPACE_ROOT"

clj -Sdeps '{:paths [".llm/scripts"]}' -X check-archive-staleness/run
