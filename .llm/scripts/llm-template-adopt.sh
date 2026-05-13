#!/usr/bin/env bash
# .llm/scripts/llm-template-adopt.sh
#
# Orchestrate the side-effect-free adoption workflow for existing repositories.
# This script intentionally does not write .llm/repo-context.edn. It prints the
# judgment material in the intended order, then tells the human-approved apply
# command to run next.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$WORKSPACE_ROOT"

repo_kind=""
if [ -f ".llm/repo-context.edn" ]; then
  repo_kind="$(grep -oE ':repo-kind[[:space:]]+:[a-z-]+' .llm/repo-context.edn 2>/dev/null \
    | head -1 \
    | sed -E 's/.*:repo-kind[[:space:]]+:([a-z-]+).*/\1/' || true)"
fi

if [ "$repo_kind" = "template" ]; then
  echo "llm-template-adopt: SKIP"
  echo "This repository is the template itself. Adoption workflow is for derived or retrofitted repositories."
  echo "Use MAINTAINERS_GUIDE.md for template maintenance."
  exit 0
fi

echo "== 1. Detect repo profile =="
"$SCRIPT_DIR/detect-repo-profile.sh"

echo ""
echo "== 2. Propose repo-context manifest =="
"$SCRIPT_DIR/propose-repo-context.sh"

echo ""
echo "== 3. Propose template migrations =="
"$SCRIPT_DIR/propose-template-migrations.sh"

echo ""
echo "== 4. Propose adoption plan =="
"$SCRIPT_DIR/propose-adoption-plan.sh"

echo ""
echo "== 5. Next human-approved action =="
echo "Review the output above. If accepted, run:"
echo "  ./.llm/scripts/apply-repo-context-migration.sh"
echo ""
echo "After applying, run:"
echo "  ./.llm/scripts/check-workspace-integrity.sh"
