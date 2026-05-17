#!/usr/bin/env bash
# Manage the .llm/reference-repos.edn allowlist of local Polylith repos that
# may be read as design comparison material (POLYLITH_GUIDE.md §9).
#
# Usage:
#   reference-repos.sh add <path>   参照可能 repo を allowlist に追加（出自検証付き）
#   reference-repos.sh list         登録済みの参照 repo を一覧
#   reference-repos.sh check        登録エントリの有効性と brick 一覧を表示
#
# 実体は reference_repos.clj。本スクリプトは run-clj-tool.sh 経由の薄いラッパー。

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

usage() {
  echo "Usage: $0 add <path> | list | check" >&2
  exit 2
}

[ "$#" -ge 1 ] || usage
sub="$1"
shift

case "$sub" in
  add)
    [ "$#" -ge 1 ] || { echo "ERROR: add は <path> 引数が必要です" >&2; usage; }
    # ユーザの cwd を基準に絶対パスへ正規化してから clj に渡す
    # （run-clj-tool.sh は workspace root へ cd するため相対パスは渡せない）。
    abs="$(realpath -m -- "$1")"
    exec "$SCRIPT_DIR/run-clj-tool.sh" exec reference-repos/add :path "\"$abs\""
    ;;
  list)
    exec "$SCRIPT_DIR/run-clj-tool.sh" exec reference-repos/list-entries
    ;;
  check)
    exec "$SCRIPT_DIR/run-clj-tool.sh" exec reference-repos/check
    ;;
  *)
    echo "ERROR: 未知のサブコマンド: $sub" >&2
    usage
    ;;
esac
