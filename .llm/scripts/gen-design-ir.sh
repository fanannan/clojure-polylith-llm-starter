#!/usr/bin/env bash
# scripts/gen-design-ir.sh
#
# Generates .llm/data/design-ir.edn from DESIGN.md and existing analysis EDN.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$WORKSPACE_ROOT"

clj -Sdeps '{:paths [".llm/scripts"]}' -X gen-design-ir/generate
