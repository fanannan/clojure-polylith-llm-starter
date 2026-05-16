#!/usr/bin/env bash
# Structural Evidence workflow command.
#
# Main subcommands:
#   what-now     - return the next evidence workflow action
#   status  - query current evidence state
#   search  - search closed evidence records
#   is-verified - query requirement / public boundary verification state
#   why     - explain the evidence chain for a claim
#   stale   - list stale or unknown closed evidence records
#   gate    - enforce staged/range evidence gate
#   predict - bind task intent before implementation
#   declare - fill residual fields safely
#   run     - record command-backed evidence results
#   close   - compare predicted and actual scope before close
#   backfill-invalidated-by - migrate older closed records to event staleness metadata
#   prune-work - dry-run or confirm pruning of generated work artifacts without deleting declarations

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$WORKSPACE_ROOT"

exec "$SCRIPT_DIR/run-clj-tool.sh" main structural-evidence "$@"
