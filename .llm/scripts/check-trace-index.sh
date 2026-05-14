#!/usr/bin/env bash
# scripts/check-trace-index.sh
#
# Validate generated docs/TRACE.md and .llm/data/trace-index.edn drift.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$WORKSPACE_ROOT"

clj -Sdeps '{:paths [".llm/scripts"]}' -X gen-trace-index/check
