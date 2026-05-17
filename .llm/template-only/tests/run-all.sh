#!/usr/bin/env bash
# Run every template-maintenance E2E test in this directory.
#
# These are the heavy template-only tests. They are not part of the daily
# completion gate; run them when generators / checkers / migration scripts
# change, or before a template release.
#
# The test set is the `check-*.sh` glob itself, so there is no hand-maintained
# list that can drift from the actual files.
#
#   ./run-all.sh           run every check-*.sh in this directory
#   ./run-all.sh --list    list the tests without running them

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

mapfile -t tests < <(find "$SCRIPT_DIR" -maxdepth 1 -name 'check-*.sh' -type f | sort)

if [[ "${1:-}" == "--list" ]]; then
  for t in "${tests[@]}"; do
    echo "${t#"$SCRIPT_DIR"/}"
  done
  exit 0
fi

if [[ $# -gt 0 ]]; then
  echo "Usage: run-all.sh [--list]" >&2
  exit 2
fi

if [[ ${#tests[@]} -eq 0 ]]; then
  echo "run-all: no check-*.sh tests found in $SCRIPT_DIR" >&2
  exit 1
fi

failed=()
for t in "${tests[@]}"; do
  name="${t#"$SCRIPT_DIR"/}"
  echo "=== $name ==="
  if bash "$t"; then
    :
  else
    failed+=("$name")
  fi
done

echo
if [[ ${#failed[@]} -gt 0 ]]; then
  echo "run-all: FAILED (${#failed[@]}/${#tests[@]} tests)"
  for f in "${failed[@]}"; do
    echo "  - $f"
  done
  exit 1
fi
echo "run-all: OK (${#tests[@]}/${#tests[@]} tests)"
