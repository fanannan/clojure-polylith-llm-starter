#!/usr/bin/env bash
# .llm/scripts/install-llm-template.sh
#
# Retrofit the LLM template files into an existing Clojure/Polylith repository.
# Default mode is a dry run. Passing --apply copies files, but never overwrites
# existing project files; conflicts are written as *.candidate.<timestamp>.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEMPLATE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

target=""
apply=0

usage() {
  echo "Usage: $0 --target <repo-path> [--apply]" >&2
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --target)
      shift
      target="${1:-}"
      ;;
    --apply)
      apply=1
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage
      exit 2
      ;;
  esac
  shift
done

if [ -z "$target" ]; then
  usage
  exit 2
fi

if [ ! -d "$target" ]; then
  echo "ERROR: target directory does not exist: $target" >&2
  exit 1
fi

timestamp="$(date +%Y%m%d%H%M%S)"

copy_one() {
  local src="$1"
  local dst="$2"

  if [ ! -e "$src" ]; then
    return 0
  fi

  if [ "$apply" -ne 1 ]; then
    if [ -e "$dst" ]; then
      echo "PLAN: copy $src -> $dst.candidate.$timestamp (target exists)"
    else
      echo "PLAN: copy $src -> $dst"
    fi
    return 0
  fi

  mkdir -p "$(dirname "$dst")"
  if [ -e "$dst" ]; then
    cp -R "$src" "$dst.candidate.$timestamp"
    echo "copied candidate: $dst.candidate.$timestamp"
  else
    cp -R "$src" "$dst"
    echo "copied: $dst"
  fi
}

echo "Target: $target"
if [ "$apply" -ne 1 ]; then
  echo "Mode: dry-run (pass --apply after human approval)"
else
  echo "Mode: apply"
fi
echo ""

copy_one "$TEMPLATE_ROOT/CLAUDE.md" "$target/CLAUDE.md"
copy_one "$TEMPLATE_ROOT/AGENTS.md" "$target/AGENTS.md"
copy_one "$TEMPLATE_ROOT/DESIGN.md" "$target/DESIGN.md"
copy_one "$TEMPLATE_ROOT/README.md" "$target/README.md"
copy_one "$TEMPLATE_ROOT/.llm/guide" "$target/.llm/guide"
copy_one "$TEMPLATE_ROOT/.llm/scripts" "$target/.llm/scripts"
copy_one "$TEMPLATE_ROOT/.llm/data" "$target/.llm/data"
copy_one "$TEMPLATE_ROOT/.llm/templates" "$target/.llm/templates"
copy_one "$TEMPLATE_ROOT/.llm/template-version.edn" "$target/.llm/template-version.edn"
copy_one "$TEMPLATE_ROOT/.llm/migrations" "$target/.llm/migrations"
copy_one "$TEMPLATE_ROOT/.llm/memory/KNOWLEDGE.md" "$target/.llm/memory/KNOWLEDGE.md"
copy_one "$TEMPLATE_ROOT/.llm/memory/QUESTIONS.md" "$target/.llm/memory/QUESTIONS.md"
copy_one "$TEMPLATE_ROOT/.llm/memory/adr/README.md" "$target/.llm/memory/adr/README.md"
copy_one "$TEMPLATE_ROOT/.llm/memory/adr/template.md" "$target/.llm/memory/adr/template.md"
copy_one "$TEMPLATE_ROOT/.llm/memory/archive/maintainer-discussions/README.md" "$target/.llm/memory/archive/maintainer-discussions/README.md"
copy_one "$TEMPLATE_ROOT/.clj-kondo/config.edn" "$target/.clj-kondo/config.edn"
copy_one "$TEMPLATE_ROOT/.clj-kondo/imports" "$target/.clj-kondo/imports"
copy_one "$TEMPLATE_ROOT/.clj-kondo/polyguard" "$target/.clj-kondo/polyguard"

echo ""
echo "Next:"
echo "  cd \"$target\""
echo "  ./.llm/scripts/propose-repo-context.sh"
echo "  ./.llm/scripts/apply-repo-context-migration.sh"
