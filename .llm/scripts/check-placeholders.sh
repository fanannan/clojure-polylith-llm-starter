#!/usr/bin/env bash
# scripts/check-placeholders.sh
#
# 目的:
#   workspace.edn / deps.edn / brick ソースにプレースホルダ `myorg.myapp` が
#   残っていないか検査する。
#   `CLAUDE.md §5.5` 完了条件の一部として `check-workspace-integrity.sh` 経由で呼ばれる。
#   ただしテンプレート repo では配布用 placeholder を保持する必要があるため、
#   `.llm/repo-context.edn :repo-kind :template` の場合は残存を許容する。
#
# 対象:
#   設定ファイル（workspace.edn / deps.edn）と brick ソース
#   （components/*/ と bases/*/ の src/** および test/** の .clj/.cljc/.cljs）。
#   brick ソース検査は、bootstrap が workspace.edn の :top-namespace だけ書き換え、
#   brick の (ns ...) 宣言を placeholder のまま残す取りこぼしを検出するためにある。
#   test ファイルの (ns ...) も同じ取りこぼしの対象になるため src/ と同様に走査する。
#   サンプルコード（POLYLITH_GUIDE.md / KNOWLEDGE.md 内のコード例）は対象外。
#
# 運用タイミング:
#   - ブートストラップの早い段階（workspace.edn 編集直後）
#   - 完了条件として全ビルド通過前に必ず通る
#
# 終了コード:
#   0: プレースホルダなし、または template repo の配布用 placeholder
#   1: プレースホルダ残存、または対象ファイル欠落

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$WORKSPACE_ROOT"

placeholder_found=0
template_distribution=0

is_template_distribution_state() {
  [ -f ".llm/repo-context.edn" ] || return 1
  grep -qE ':repo-kind[[:space:]]+:template([^[:alnum:]_-]|$)' .llm/repo-context.edn
}

if is_template_distribution_state; then
  template_distribution=1
fi

check_file() {
  local file="$1"
  if [ ! -f "$file" ]; then
    echo "ERROR: $file が存在しません"
    placeholder_found=1
    return
  fi
  # コメント行（先頭 ;;）を除いて myorg.myapp を検索
  if grep -n 'myorg\.myapp' "$file" | grep -v '^[0-9]*:[[:space:]]*;;' > /dev/null; then
    if [ "$template_distribution" -eq 1 ]; then
      echo "INFO: $file の配布用プレースホルダ 'myorg.myapp' を許容（template repo）"
      grep -n 'myorg\.myapp' "$file" | grep -v '^[0-9]*:[[:space:]]*;;' | sed 's/^/  /'
      return
    fi
    echo "ERROR: $file にプレースホルダ 'myorg.myapp' が残存:"
    grep -n 'myorg\.myapp' "$file" | grep -v '^[0-9]*:[[:space:]]*;;' | sed 's/^/  /'
    placeholder_found=1
  fi
}

# brick ソース（components/*/ と bases/*/ の src/** および test/**）の (ns ...)
# 宣言などに placeholder が残っていないか検査する。template repo は brick を
# 同梱しないため通常は対象ファイルが存在せず空検査になる。万一存在する場合は
# check_file と同じく template repo では INFO 扱いとし、placeholder_found を立てない。
check_brick_sources() {
  local base file
  for base in components bases; do
    [ -d "$base" ] || continue
    while IFS= read -r file; do
      [ -z "$file" ] && continue
      grep -n 'myorg\.myapp' "$file" | grep -v '^[0-9]*:[[:space:]]*;;' > /dev/null || continue
      if [ "$template_distribution" -eq 1 ]; then
        echo "INFO: $file の配布用プレースホルダ 'myorg.myapp' を許容（template repo）"
        grep -n 'myorg\.myapp' "$file" | grep -v '^[0-9]*:[[:space:]]*;;' | sed 's/^/  /'
        continue
      fi
      echo "ERROR: $file にプレースホルダ 'myorg.myapp' が残存:"
      grep -n 'myorg\.myapp' "$file" | grep -v '^[0-9]*:[[:space:]]*;;' | sed 's/^/  /'
      placeholder_found=1
    done < <(find "$base" -type f \
               \( -name '*.clj' -o -name '*.cljc' -o -name '*.cljs' \) \
               \( -path "$base/*/src/*" -o -path "$base/*/test/*" \) 2>/dev/null)
  done
}

check_file "workspace.edn"
check_file "deps.edn"
check_brick_sources

if [ "$template_distribution" -eq 1 ]; then
  echo ""
  echo "check-placeholders: OK (template distribution placeholders retained)"
  exit 0
fi

if [ "$placeholder_found" -eq 1 ]; then
  echo ""
  echo "プレースホルダを実プロジェクト名に置換してください（BOOTSTRAP_GUIDE.md §2.1）。"
  exit 1
fi

echo "check-placeholders: OK"
exit 0
