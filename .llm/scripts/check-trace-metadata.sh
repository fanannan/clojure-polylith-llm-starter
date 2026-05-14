#!/usr/bin/env bash
# scripts/check-trace-metadata.sh
#
# 目的:
#   Clojure コード / テストコードに付与された :trace/* metadata を DESIGN 由来の
#   design-ir.edn と照合する。
#
# 方針:
#   - 実装コード側の trace は stable public boundary のみ許可する。
#     component は interface.clj の public defn、base は core.clj / handler.clj の public boundary defn。
#   - component core.clj / private helper / base system.clj / adapter 内部への trace metadata は drift の温床なので禁止。
#   - AC-* / TO-* は実装コードではなく deftest 側の :trace/test-obligations に置く。
#
# 終了コード:
#   0: ERROR なし（:retrofit / :partial の未カバー test obligation は WARN）
#   1: unknown ID、内部実装 metadata、誤配置などの ERROR あり

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$WORKSPACE_ROOT"

clj -Sdeps '{:paths [".llm/scripts"]}' -X check-trace-metadata/run
