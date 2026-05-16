#!/usr/bin/env bash
# .llm/scripts/check-brick-deps-self-contained.sh
#
# 各 brick の src が require する external namespace を、その brick 自身の
# deps.edn 由来 classpath で解決できるか検証する。development の :dev alias
# 経由でだけ通る依存（例: malli が dev alias でのみ解決される）を構造検出する。
#
# 実体は check_brick_deps_self_contained.clj（require 解析のため Clojure）。
# 本 wrapper は workspace root へ移動して clj -X を呼ぶだけ。

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

cd "${WORKSPACE_ROOT}"

clj -Sdeps '{:paths [".llm/scripts"]}' -X check-brick-deps-self-contained/run
