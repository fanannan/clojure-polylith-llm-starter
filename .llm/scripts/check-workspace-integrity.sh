#!/usr/bin/env bash
# scripts/check-workspace-integrity.sh
#
# 目的:
#   他の script 群を束ね、ワークスペース全体の整合性を一括検査する。
#   `CLAUDE.md §5.5` 完了条件にこの 1 行を追加するだけで、以下が必須通過ゲートに入る:
#     - プレースホルダ残存（check-placeholders.sh）
#     - brick 登録漏れ（check-brick-registration.sh）
#     - 非推奨ライブラリ採用（check-deprecated-libs.sh）
#     - interface_test.clj の Malli instrumentation fixture 欠落
#     - 追加: deps.edn :local/root 実在、workspace.edn :projects 実在、.gitkeep と brick 併存
#
# 終了コード:
#   0: 全検査通過
#   1: いずれかの検査が失敗

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$WORKSPACE_ROOT"

failures=0

run_step() {
  local label="$1"
  shift
  echo ""
  echo "=== $label ==="
  if "$@"; then
    :
  else
    failures=$((failures + 1))
  fi
}

# --- プレースホルダ ---
run_step "プレースホルダ残存検査" \
  "$SCRIPT_DIR/check-placeholders.sh"

# --- brick 登録整合 ---
run_step "brick 登録整合検査" \
  "$SCRIPT_DIR/check-brick-registration.sh"

# --- lib-catalog 生成物の同期検証 ---
# STACK_GUIDE.md §8 の ;; lib-catalog EDN block と .llm/data/ 配下の生成物が
# ずれていないか検証する（生成後の commit 忘れを早期検知）。
echo ""
echo "=== lib-catalog 生成物の同期検証 ==="
if [ -f ".llm/scripts/gen_lib_catalog.clj" ] && [ -d ".llm/data" ]; then
  lc_tmpdir="$(mktemp -d)"
  # trap を使わず、各 diff の後に手動で clean up（check 内 exit 時に lc_tmpdir を残さない）
  if clj -X:gen-lib-catalog :out-dir "\"$lc_tmpdir\"" >/dev/null 2>&1; then
    lc_fail=0
    for artifact in libs.edn deprecated-libs.patterns forbidden-requires.patterns conflicts.patterns; do
      if ! diff -u ".llm/data/$artifact" "$lc_tmpdir/$artifact" >/dev/null 2>&1; then
        echo "ERROR: .llm/data/$artifact が STACK_GUIDE.md §8 と同期していません"
        echo "  Fix: clj -X:gen-lib-catalog && diff を確認して commit"
        diff -u ".llm/data/$artifact" "$lc_tmpdir/$artifact" | head -40 | sed 's/^/    /'
        lc_fail=1
      fi
    done
    rm -rf "$lc_tmpdir"
    if [ "$lc_fail" -eq 1 ]; then
      failures=$((failures + 1))
    else
      echo "check-lib-catalog-sync: OK"
    fi
  else
    rm -rf "$lc_tmpdir"
    echo "ERROR: clj -X:gen-lib-catalog が失敗しました（STACK_GUIDE.md §8 の EDN block にエラーがある可能性）"
    failures=$((failures + 1))
  fi
else
  echo "check-lib-catalog-sync: skipped (generator or data dir missing)"
fi

# --- 非推奨ライブラリ ---
run_step "非推奨ライブラリ検査" \
  "$SCRIPT_DIR/check-deprecated-libs.sh"

# --- 禁止 namespace の require 検知（polyguard hook の置き換え） ---
run_step "禁止 namespace require 検査" \
  "$SCRIPT_DIR/check-forbidden-requires.sh"

# --- 併用禁止ライブラリペア検査（:conflicts-with） ---
run_step "併用禁止ライブラリペア検査" \
  "$SCRIPT_DIR/check-conflicting-libs.sh"

# --- interface.clj の m/=> 契約付与 ---
run_step "interface 契約検査" \
  "$SCRIPT_DIR/check-interface-contracts.sh"

# --- interface_test.clj の Malli instrumentation fixture ---
run_step "test instrumentation 検査" \
  "$SCRIPT_DIR/check-test-instrumentation.sh"

# --- Markdown 参照マーカー検査 ---
run_step "Markdown 参照マーカー検査" \
  "$SCRIPT_DIR/check-doc-references.sh"

# --- モード境界検査（template vs project の所有権違反検出） ---
run_step "モード境界検査 (check-mode-scope)" \
  "$SCRIPT_DIR/check-mode-scope.sh"

# --- 1 ファイル 1 ns ---
run_step "1 ファイル 1 ns 検査" \
  "$SCRIPT_DIR/check-single-ns-per-file.sh"

# --- 追加: .gitkeep と brick 併存検査 ---
echo ""
echo "=== .gitkeep と brick 併存検査 ==="
keep_conflict=0
for d in components bases; do
  if [ -d "$d" ]; then
    has_brick=0
    has_keep=0
    for entry in "$d"/*; do
      [ -e "$entry" ] || continue
      local_base="$(basename "$entry")"
      if [ "$local_base" = ".gitkeep" ]; then
        has_keep=1
      elif [ -d "$entry" ]; then
        has_brick=1
      fi
    done
    if [ "$has_brick" -eq 1 ] && [ "$has_keep" -eq 1 ]; then
      echo "WARNING: $d/ に brick と .gitkeep が併存。brick が存在するなら .gitkeep は削除してよい"
      keep_conflict=1
    fi
  fi
done
if [ "$keep_conflict" -eq 0 ]; then
  echo "check-keep-coexistence: OK"
fi

# --- 追加: deps.edn :local/root パスの実在検査 ---
#
# 備考: コメント行（先頭 ;;）は除外する。配布物には例として
# `;;   poly/<domain> {:local/root "components/<domain>"}` が同梱されており、
# これを拾うと常に false positive が出る。
echo ""
echo "=== deps.edn :local/root 実在検査 ==="
if [ -f deps.edn ]; then
  root_missing=0
  while read -r root_path; do
    [ -z "$root_path" ] && continue
    if [ ! -d "$root_path" ]; then
      echo "ERROR: deps.edn :local/root \"$root_path\" に対応するディレクトリが存在しません"
      root_missing=1
    fi
  done < <(grep -v '^[[:space:]]*;;' deps.edn \
           | grep -oE ':local/root[[:space:]]+"[^"]+"' \
           | sed -E 's/.*"([^"]+)".*/\1/')
  if [ "$root_missing" -eq 1 ]; then
    failures=$((failures + 1))
  else
    echo "check-local-root: OK"
  fi
fi

# --- 追加: workspace.edn :projects 実在検査 ---
#
# 実装方針: `:projects` の値はネスト付き EDN で shell から正確にパースするのは
# 困難。false positive を避けるため、実在検査は Polylith 本体の `poly check`
# に委譲する。本スクリプトでは「:projects 内の各 project キーに対応する
# ディレクトリがあるか」を `poly info` の代わりに grep ベースで軽く見るに留める。
# development は特別扱い（ソースを直接持たないので projects/development は不要）。
echo ""
echo "=== workspace.edn :projects 実在検査 ==="
if [ -f workspace.edn ]; then
  # :projects ブロックを抽出（コメント行除外）
  project_missing=0
  projects_block="$(grep -v '^[[:space:]]*;;' workspace.edn \
                   | awk 'BEGIN{flag=0; depth=0}
                          /:projects[[:space:]]*$/{flag=1; next}
                          flag{
                            for(i=1;i<=length($0);i++){
                              ch=substr($0,i,1)
                              if(ch=="{")depth++
                              else if(ch=="}"){depth--; if(depth==0){flag=0; exit}}
                            }
                            print
                          }' \
                   || true)"
  while read -r project_name; do
    [ -z "$project_name" ] && continue
    # development はソースを持たない特別扱い
    [ "$project_name" = "development" ] && continue
    if [ ! -d "projects/$project_name" ]; then
      echo "ERROR: workspace.edn :projects \"$project_name\" に対応する projects/$project_name ディレクトリが存在しません"
      project_missing=1
    fi
  done < <(echo "$projects_block" | grep -oE '"[^"]+"[[:space:]]*\{:alias' | sed -E 's/^"([^"]+)".*/\1/')
  if [ "$project_missing" -eq 1 ]; then
    failures=$((failures + 1))
  else
    echo "check-projects-existence: OK"
  fi
fi

echo ""
if [ "$failures" -gt 0 ]; then
  echo "=== workspace 整合性検査: FAILED ($failures 件) ==="
  exit 1
fi
echo "=== workspace 整合性検査: OK ==="
exit 0
