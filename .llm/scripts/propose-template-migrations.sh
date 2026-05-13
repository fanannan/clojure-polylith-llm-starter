#!/usr/bin/env bash
# .llm/scripts/propose-template-migrations.sh
#
# Side-effect free template migration proposal. Compares the local migration
# ledger with .llm/repo-context.edn :applied-migrations.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$WORKSPACE_ROOT"

clj -Sdeps '{:paths [".llm/scripts"]}' -X propose-template-migrations/run
