#!/usr/bin/env bash
# scripts/propose-brick-edn.sh
#
# Prints brick.edn skeleton proposals for existing bricks that do not yet have
# brick.edn. This is a migration aid; it does not write files.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$WORKSPACE_ROOT"

clj -Sdeps '{:paths [".llm/scripts"]}' -X gen-brick-map/propose-missing
