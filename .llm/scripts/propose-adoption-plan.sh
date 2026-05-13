#!/usr/bin/env bash
# .llm/scripts/propose-adoption-plan.sh
#
# Side-effect free adoption plan for existing Clojure/Polylith repositories.
# It does not research new recommendations; it orders local migration work.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$WORKSPACE_ROOT"

clj -Sdeps '{:paths [".llm/scripts"]}' -X propose-adoption-plan/run
