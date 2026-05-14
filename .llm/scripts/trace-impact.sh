#!/usr/bin/env bash
# scripts/trace-impact.sh
#
# Query trace-index for requirement / use-case / test-obligation impact.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$WORKSPACE_ROOT"

clj -Sdeps '{:paths [".llm/scripts"]}' -M -m trace-impact "$@"
