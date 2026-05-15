#!/usr/bin/env bash
# Run a local Clojure script with optional Babashka acceleration.
#
# Usage:
#   run-clj-tool.sh main <namespace> [args...]
#   run-clj-tool.sh exec <namespace/function> [edn-args...]
#
# Runtime selection:
#   LLM_CLJ_RUNTIME=auto  Use bb when available, otherwise clj. Default.
#   LLM_CLJ_RUNTIME=bb    Require bb.
#   LLM_CLJ_RUNTIME=clj   Require Clojure CLI.
#
# Babashka is an optional accelerator, not a template prerequisite. If bb is
# selected and the script is not bb-compatible, the failure is intentional and
# should be made explicit by re-running with LLM_CLJ_RUNTIME=clj.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$WORKSPACE_ROOT"

if [ "$#" -lt 2 ]; then
  echo "Usage: $0 main <namespace> [args...] | exec <namespace/function> [edn-args...]" >&2
  exit 2
fi

kind="$1"
target="$2"
shift 2

runtime="${LLM_CLJ_RUNTIME:-auto}"

select_runtime() {
  case "$runtime" in
    auto)
      if command -v bb >/dev/null 2>&1; then
        echo "bb"
      else
        echo "clj"
      fi
      ;;
    bb|clj)
      echo "$runtime"
      ;;
    *)
      echo "Unknown LLM_CLJ_RUNTIME: $runtime" >&2
      exit 2
      ;;
  esac
}

selected="$(select_runtime)"
export LLM_CLJ_RUNTIME_SELECTED="$selected"

if [ "$selected" = "bb" ] && ! command -v bb >/dev/null 2>&1; then
  echo "LLM_CLJ_RUNTIME=bb was requested, but bb is not on PATH." >&2
  exit 127
fi

if [ "$selected" = "clj" ] && ! command -v clj >/dev/null 2>&1; then
  echo "Clojure CLI (clj) is required but not on PATH." >&2
  exit 127
fi

case "$kind:$selected" in
  main:bb)
    exec bb --classpath ".llm/scripts" -m "$target" "$@"
    ;;
  main:clj)
    exec clj -Sdeps '{:paths [".llm/scripts"]}' -M -m "$target" "$@"
    ;;
  exec:bb)
    exec bb --classpath ".llm/scripts" -x "$target" "$@"
    ;;
  exec:clj)
    exec clj -Sdeps '{:paths [".llm/scripts"]}' -X "$target" "$@"
    ;;
  *)
    echo "Unknown run kind: $kind (expected main or exec)" >&2
    exit 2
    ;;
esac
