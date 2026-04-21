#!/usr/bin/env bash
# scripts/lint-import-hooks.sh
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
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$WORKSPACE_ROOT"

# 備考: ここでは :dev の全 classpath を --lint 対象にする。deps.edn :lint
# エイリアスは "components bases development/src" に限定するが、hook の取り込み
# には brick deps.edn が引き込む依存ライブラリ由来の .clj-kondo/ 設定も
# 参照する必要があるため、対象範囲が意図的に異なる。同期修正しないこと。
CLASSPATH="$(clj -A:dev -Spath)"
echo "clj-kondo hook を取り込み中 (classpath 長: ${#CLASSPATH} chars)..."

clj -M:lint --copy-configs --dependencies --lint "$CLASSPATH"
