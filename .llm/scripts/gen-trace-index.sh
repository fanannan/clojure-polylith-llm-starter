#!/usr/bin/env bash
# scripts/gen-trace-index.sh
#
# Generate docs/TRACE.md and .llm/data/trace-index.edn from design-ir.edn and
# Clojure :trace/* metadata.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$WORKSPACE_ROOT"

clj -Sdeps '{:paths [".llm/scripts"]}' -X gen-trace-index/generate
