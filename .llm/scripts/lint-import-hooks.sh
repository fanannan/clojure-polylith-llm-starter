#!/usr/bin/env bash
# scripts/lint-import-hooks.sh
#
# _POSSIBLE_ISSUES.md D-5 の実装。
#
# 目的:
#   依存ライブラリ（Malli、Polylith 等）が提供する clj-kondo hook を
#   .clj-kondo/configs/ に取り込む。tools.deps の :main-opts は
#   シェル展開されないため、:lint エイリアスに埋め込めず本スクリプトで包む。
#
# 運用タイミング:
#   - 初回セットアップ（BOOTSTRAP_GUIDE.md §2.9）
#   - brick deps.edn に新ライブラリ追加後
#   - STACK_GUIDE.md §4.2 推奨ライブラリ採用後
#   - `clj -M:outdated` で最新化した後
#
# 終了コード: clj -M:lint の終了コードを引き継ぐ

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$WORKSPACE_ROOT"

CLASSPATH="$(clojure -A:dev -Spath)"
echo "clj-kondo hook を取り込み中 (classpath 長: ${#CLASSPATH} chars)..."

clj -M:lint --copy-configs --dependencies --lint "$CLASSPATH"
