#!/usr/bin/env bash
# Generate a Review Fatigue Packet view under .llm/work/.
#
# The generated packet is a review aid, not an authority source.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$WORKSPACE_ROOT"

exec clj -Sdeps '{:paths [".llm/scripts"]}' -M -m structural-evidence propose "$@"
