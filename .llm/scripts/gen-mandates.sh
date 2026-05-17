#!/usr/bin/env bash
# scripts/gen-mandates.sh
#
# Generate .llm/data/mandates.edn — the derived join index of [mandate: ...]
# annotations in CLAUDE.md and .llm/guide/*.md. The index is a derived value;
# the prose is the source of truth. Regenerate after editing any mandate
# annotation or its surrounding prose.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$WORKSPACE_ROOT"

exec "$SCRIPT_DIR/run-clj-tool.sh" exec gen-mandates/generate
