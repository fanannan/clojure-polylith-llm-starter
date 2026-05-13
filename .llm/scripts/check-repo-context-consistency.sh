#!/usr/bin/env bash
# .llm/scripts/check-repo-context-consistency.sh
#
# Validate manifest invariants that should not be left to convention:
# capability dependencies, adoption mode shape, and migration ledger references.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$WORKSPACE_ROOT"

clj -Sdeps '{:paths [".llm/scripts"]}' -X check-repo-context-consistency/run
