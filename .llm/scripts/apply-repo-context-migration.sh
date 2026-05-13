#!/usr/bin/env bash
# .llm/scripts/apply-repo-context-migration.sh
#
# Write .llm/repo-context.edn after a human has reviewed the proposal. This is
# the only migration helper that mutates the manifest, and it asks for an
# explicit confirmation unless --yes is passed by a human-operated command.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$WORKSPACE_ROOT"

assume_yes=0
overwrite=0

while [ "$#" -gt 0 ]; do
  case "$1" in
    --yes)
      assume_yes=1
      ;;
    --overwrite)
      overwrite=1
      ;;
    *)
      echo "Usage: $0 [--yes] [--overwrite]" >&2
      exit 2
      ;;
  esac
  shift
done

echo "Proposed manifest:"
echo ""
clj -Sdeps '{:paths [".llm/scripts"]}' -X propose-repo-context/run
echo ""

if [ "$assume_yes" -ne 1 ]; then
  echo "This will write .llm/repo-context.edn."
  echo "Type APPLY to continue:"
  read -r answer
  if [ "$answer" != "APPLY" ]; then
    echo "aborted"
    exit 0
  fi
fi

if [ "$overwrite" -eq 1 ]; then
  clj -Sdeps '{:paths [".llm/scripts"]}' -X propose-repo-context/run :write true :overwrite true
else
  clj -Sdeps '{:paths [".llm/scripts"]}' -X propose-repo-context/run :write true
fi
