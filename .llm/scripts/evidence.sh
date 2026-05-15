#!/usr/bin/env bash
# Structural Evidence workflow command.
#
# Main subcommands:
#   status  - query current evidence state
#   predict - bind task intent before implementation
#   close   - compare predicted and actual scope before close

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$WORKSPACE_ROOT"

exec clj -Sdeps '{:paths [".llm/scripts"]}' -M -m structural-evidence "$@"
