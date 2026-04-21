#!/usr/bin/env bash
# scripts/check-forbidden-requires.sh
#
# 目的:
#   STACK_GUIDE.md §8.1（禁止）/ §8.2（非推奨）ライブラリの namespace が
#   `.clj` / `.cljc` / `.cljs` ファイルの (ns ... (:require ...)) 句で使われていないか検査する。
#
# 設計背景:
#   当初は .clj-kondo/polyguard/forbidden_requires.clj の `:analyze-call` hook で検査していたが、
#   clj-kondo の hook 機構は `clojure.core/ns` special form に対して発火しないため、
#   require 宣言時の検査が事実上無効化されていた。shell script ベースで grep により同等の
#   機械化を実現する。
#
#   コード内の関数呼び出しレベルの検知は `clj-kondo :discouraged-var` が担当（config.edn）。
#   deps.edn の採用宣言レベルは check-deprecated-libs.sh が担当。
#   本スクリプトは「(:require [org.apache.log4j :as log4j])」のような形を検知する。
#
# 終了コード:
#   0: 禁止 namespace の require なし
#   1: 禁止 namespace の require あり

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$WORKSPACE_ROOT"

# 禁止 namespace の接頭辞（STACK_GUIDE.md §8 と同期）
# パターン: 各行「接頭辞|§区分 理由」
FORBIDDEN_PREFIXES=(
  # === §8.1 セキュリティ・ライセンス禁止 ===
  'org\.apache\.log4j|§8.1 log4j 1.x CVE。推奨: mulog'
  'javax\.xml\.parsers\.xerces|§8.1 XXE 脆弱性。推奨: clojure.data.xml'
  'org\.json|§8.1 デシリアライズ脆弱性。推奨: jsonista'
  'java\.io\.Serializable|§8.2 RCE 脆弱性。推奨: com.taoensso/nippy'

  # === §8.2 非推奨（設計思想不整合・メンテ停止） ===
  'taoensso\.timbre|§8.2 推奨: com.brunobonacci/mulog'
  'com\.stuartsierra\.component|§8.2 推奨: integrant'
  'mount\.core|§8.2 推奨: integrant'
  'environ\.core|§8.2 推奨: aero'
  'immuconf\.config|§8.2 推奨: aero'
  'clojure\.spec\.alpha|§8.2 推奨: malli'
  'compojure\.core|§8.2 推奨: reitit-ring'
  'io\.pedestal\.http|§8.2 推奨: reitit-ring'
  'aleph\.http|§8.2 推奨: ring-jetty-adapter / http-kit'
  'manifold\.(deferred|stream)|§8.2 推奨: core.async'
  'org\.immutant\.web|§8.2 推奨: ring-jetty-adapter'
  'bidi\.ring|§8.2 推奨: reitit'
  'clj-http\.client|§8.2 推奨: hato'
  'clojure\.data\.json|§8.2 推奨: jsonista'
  'cheshire\.core|§8.2 推奨: jsonista'
  'clojure\.java\.jdbc|§8.2 推奨: next.jdbc'
  'korma\.core|§8.2 推奨: HoneySQL + next.jdbc'
  'cemerick\.friend|§8.2 推奨: buddy-sign'
  'taoensso\.tower|§8.2 推奨: tempura'
  'iapetos\.core|§8.2 推奨: mulog'
  'overtone\.at-at|§8.2 推奨: chime'
  'tea-time\.core|§8.2 推奨: chime'
  'robert\.bruce|§8.2 推奨: diehard'
  'endophile\.core|§8.2 推奨: markdown-clj'
  'clojurewerkz\.elastisch|§8.2 推奨: mpenet/spandex'
  'clj-webdriver\.taxi|§8.2 推奨: etaoin'
  'amazonica\.|§8.2 推奨: com.cognitect.aws'
  'incanter\.(core|stats)|§8.2 推奨: scicloj/tablecloth'
  'dl4clj\.|§8.2 推奨: libpython-clj'
  'cortex\.|§8.2 推奨: libpython-clj'
  'clojurewerkz\.machine-head|§8.2 推奨: org.eclipse.paho 直接'
  'seesaw\.core|§8.2 推奨: humbleui / cljfx'
  'joplin\.core|§8.2 推奨: migratus'
  'clojure\.data\.fressian|§8.2 推奨: com.taoensso/nippy'
  'com\.rpl\.rama|§8.2 商用ライセンス。代替: XTDB + worker + batch stack'
  'smile\.(classification|clustering|regression)|§8.2 GPL。代替: scicloj/tablecloth'
  'hyperfiddle\.electric|§3.40.1 射程外'
)

# 検査対象: components/, bases/ 配下の clj* ファイル
# development/src は一時デバッグ用なので検査対象外。
found=0
declare -a SRC_FILES=()
while IFS= read -r f; do
  SRC_FILES+=("$f")
done < <(find components bases 2>/dev/null \
  -type f \( -name '*.clj' -o -name '*.cljc' -o -name '*.cljs' \) || true)

if [ "${#SRC_FILES[@]}" -eq 0 ]; then
  echo "check-forbidden-requires: no source files, skipped"
  exit 0
fi

for src in "${SRC_FILES[@]}"; do
  for entry in "${FORBIDDEN_PREFIXES[@]}"; do
    prefix="${entry%%|*}"
    reason="${entry#*|}"
    # (:require [<ns> ...]) または (:require <ns>) のパターンを検知
    if grep -Eq "\[${prefix}(\.[a-zA-Z0-9._-]+)?[[:space:]\]]" "$src" 2>/dev/null; then
      echo "ERROR: $src で禁止 namespace の require を検知:"
      echo "  接頭辞: ${prefix}"
      echo "  $reason"
      grep -nE "\[${prefix}(\.[a-zA-Z0-9._-]+)?[[:space:]\]]" "$src" | sed 's/^/    /'
      found=1
    fi
  done
done

if [ "$found" -eq 1 ]; then
  echo ""
  echo "STACK_GUIDE.md §8 を参照し、推奨代替に置き換えてください。"
  exit 1
fi

echo "check-forbidden-requires: OK"
exit 0
