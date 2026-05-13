#!/usr/bin/env bash
# .llm/scripts/propose-repo-context.sh
#
# Print a candidate .llm/repo-context.edn for existing template-derived projects
# or retrofit adoption of existing Clojure/Polylith repositories. This script is
# side-effect free; use apply-repo-context-migration.sh after human approval.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$WORKSPACE_ROOT"

clj -Sdeps '{:paths [".llm/scripts"]}' -X propose-repo-context/run
