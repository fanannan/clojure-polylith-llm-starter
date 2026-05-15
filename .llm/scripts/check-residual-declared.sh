#!/usr/bin/env bash
# Check that LLM-declared residual fields are explicit before a packet is closed.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$WORKSPACE_ROOT"

exec clj -Sdeps '{:paths [".llm/scripts"]}' -M -m structural-evidence check-residual "$@"
