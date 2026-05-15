#!/usr/bin/env bash
# Install repository-local git hooks.
#
# This works for Claude Code, Codex, humans, and CI images that run git from
# the repository root. The hooks themselves are thin wrappers around .llm/scripts
# primitives; they do not contain agent-specific logic.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$WORKSPACE_ROOT"

git config core.hooksPath .githooks
echo "core.hooksPath set to .githooks"
echo "pre-commit now runs ./.llm/scripts/check-evidence-gate.sh --staged"
