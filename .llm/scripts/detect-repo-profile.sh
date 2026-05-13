#!/usr/bin/env bash
# .llm/scripts/detect-repo-profile.sh
#
# Side-effect free repository profile detection for template adoption/migration.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$WORKSPACE_ROOT"

clj -Sdeps '{:paths [".llm/scripts"]}' -X detect-repo-profile/run
