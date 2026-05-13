#!/usr/bin/env bash
# scripts/check-test-instrumentation.sh
#
# 目的:
#   `interface_test.clj` を Clojure form として読み、`:once` fixture と
#   その実体の対応を辿って Malli instrumentation (`malli.dev/start!`) の
#   有効化を検査する。

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$WORKSPACE_ROOT"

clj -Sdeps '{:paths [".llm/scripts"]}' -X check-test-instrumentation/run
