#!/usr/bin/env bash
# scripts/check-deprecated-libs.sh
#
# clj-kondo :discouraged-varを補完する# brick deps.edn 採用宣言レベルの検査。
#
# 目的:
# STACK_GUIDE.md §8.2 に列挙された非推奨ライブラリが brick deps.edn に
# 採用されていないか検査する。コード内使用は clj-kondo :discouraged-var で
# 検知するが、deps.edn に採用宣言が残っているパターンは
# clj-kondo では捕捉できないため、shell script で補完する。
#
# 検査対象:
# 全 deps.edn（ルート・brick・project）
#
# 非推奨ライブラリ（STACK_GUIDE.md §8.1 禁止 + §8.2 非推奨、2026-04 拡張版）:
# §8.1 security/license-legacy:
# org.apache.logging.log4j/log4j-1.2-api (log4j 1.x CVE)
# xerces/xercesImpl, xalan/xalan (XXE)
# org.json/json (legacy, deserialization CVE)
# §8.2 superseded (既定):
# org.clojure/java.jdbc -> next.jdbc
# com.stuartsierra/component -> integrant
# mount -> integrant
# environ -> aero
# org.clojure/spec.alpha -> malli
# compojure -> reitit-ring
# io.pedestal -> reitit-ring
# clj-http -> hato
# org.clojure/data.json -> jsonista
# com.taoensso/timbre -> mulog
# cemerick/friend -> buddy-sign
# clojurewerkz/elastisch -> mpenet/spandex
# metrics-clojure系 -> mulog
# at-at, quartzite, tea-time -> chime
# §8.2 superseded (2026-04 拡張):
# aleph, manifold, immutant -> Jetty / core.async
# bidi -> reitit
# cheshire -> jsonista (新規採用禁止)
# korma -> HoneySQL + next.jdbc
# immuconf -> aero
# tower -> tempura
# endophile -> markdown-clj
# clj-webdriver -> etaoin
# amazonica -> cognitect aws-api
# incanter -> scicloj/tablecloth
# dl4clj, cortex -> libpython-clj (Python 委譲)
# machine-head -> paho.mqtt.java 直接
# seesaw -> humbleui / cljfx (新規採用禁止)
# robert.bruce -> sunng87/diehard
# Flyway / Liquibase (Clojure 新規) -> migratus
# leiningen -> tools.deps
# org.clojure/data.fressian -> com.taoensso/nippy (§3.41)
# §8.2 license-restricted:
# Rama (Red Planet Labs, 商用)
# SMILE (GPL 3.0)
# §3.40.1 scope-excluded:
# hyperfiddle/electric (cljs 前提、射程外)
#
# 終了コード:
# 0: 非推奨ライブラリの採用なし
# 1: 採用あり

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$WORKSPACE_ROOT"

# "pattern|推奨代替" の配列
# §8.1 禁止（絶対使用禁止）と §8.2 非推奨（新規採用禁止）を併記。
# STACK_GUIDE.md §8 と同期必須。追加時は hook (.clj-kondo/polyguard/forbidden_requires.clj) も更新する。
DEPRECATED_PATTERNS=(
 # === §8.1 禁止ライブラリ ===
 'log4j-1\.2-api|§8.1 log4j 1.x CVE-2019-17571。推奨: org.apache.logging.log4j/log4j-core 2.x または mulog 統一'
 'xerces/xercesImpl|§8.1 XXE 脆弱性。推奨: org.clojure/data.xml'
 'xalan/xalan|§8.1 XXE 脆弱性。推奨: org.clojure/data.xml'
 'org\.json/json|§8.1 デシリアライズ脆弱性。推奨: metosin/jsonista'

 # === §8.2 非推奨（既定分） ===
 'org\.clojure/java\.jdbc|§8.2 推奨: com.github.seancorfield/next.jdbc'
 'com\.stuartsierra/component|§8.2 推奨: integrant/integrant'
 'mount/mount|§8.2 推奨: integrant/integrant'
 'environ/environ|§8.2 推奨: aero/aero'
 'org\.clojure/spec\.alpha|§8.2 推奨: metosin/malli'
 'compojure/compojure|§8.2 推奨: metosin/reitit-ring'
 'io\.pedestal/pedestal|§8.2 条件付き。推奨: metosin/reitit-ring'
 'clj-http/clj-http|§8.2 新規採用禁止。推奨: hato/hato'
 'org\.clojure/data\.json|§8.2 推奨: metosin/jsonista'
 'com\.taoensso/timbre|§8.2 推奨: com.brunobonacci/mulog'
 'clj-commons/cemerick\.friend|§8.2 推奨: buddy/buddy-sign + buddy/buddy-hashers'
 'cemerick/friend|§8.2 推奨: buddy/buddy-sign + buddy/buddy-hashers'
 'io\.github\.metrics-clojure-ring|§8.2 推奨: com.brunobonacci/mulog'
 'metrics-clojure/metrics-clojure|§8.2 推奨: com.brunobonacci/mulog'
 'clj-commons/iapetos|§8.2 推奨: com.brunobonacci/mulog'
 'overtone/at-at|§8.2 推奨: jarohen/chime'
 'clojurewerkz/quartzite|§8.2 chime 優先。推奨: jarohen/chime'
 'tea-time/tea-time|§8.2 推奨: jarohen/chime'

 # === §8.2 非推奨（2026-04 拡張分） ===
 'aleph/aleph|§8.2 推奨: ring/ring-jetty-adapter または http-kit/http-kit'
 'manifold/manifold|§8.2 推奨: org.clojure/core.async'
 'org\.immutant/web|§8.2 メンテ停止。推奨: ring/ring-jetty-adapter'
 'bidi/bidi|§8.2 新規採用禁止。推奨: metosin/reitit'
 'cheshire/cheshire|§8.2 新規採用禁止。推奨: metosin/jsonista'
 'korma/korma|§8.2 推奨: com.github.seancorfield/honeysql + next.jdbc'
 'immuconf/immuconf|§8.2 推奨: aero/aero'
 'com\.taoensso/tower|§8.2 メンテ停止。推奨: com.taoensso/tempura'
 'endophile/endophile|§8.2 メンテ停止。推奨: markdown-clj/markdown-clj'
 'clojurewerkz/elastisch|§8.2 メンテ停止。推奨: mpenet/spandex'
 'clj-webdriver/clj-webdriver|§8.2 メンテ停止。推奨: etaoin/etaoin'
 'amazonica/amazonica|§8.2 推奨: com.cognitect.aws/api + com.cognitect.aws/<service>'
 'incanter/incanter|§8.2 メンテ低迷。推奨: scicloj/tablecloth、scicloj/scicloj.ml'
 'dl4clj/dl4clj|§8.2 推奨: clj-python/libpython-clj 経由で PyTorch / TensorFlow'
 'thinktopic/cortex|§8.2 メンテ停止。推奨: clj-python/libpython-clj'
 'clojurewerkz/machine_head|§8.2 推奨: org.eclipse.paho/org.eclipse.paho.client.mqttv3 直接'
 'clojurewerkz/machine-head|§8.2 推奨: org.eclipse.paho/org.eclipse.paho.client.mqttv3 直接'
 'seesaw/seesaw|§8.2 新規採用禁止。推奨: io.github.humbleui/humbleui または cljfx/cljfx'
 'robert/robert\.bruce|§8.2 メンテ停止。推奨: sunng87/diehard'
 'org\.flywaydb/flyway-core|§8.2 Clojure 新規プロジェクトでは migratus/migratus 推奨'
 'org\.liquibase/liquibase-core|§8.2 Clojure 新規プロジェクトでは migratus/migratus 推奨'
 'leiningen/leiningen|§8.2 新規プロジェクトでは tools.deps 推奨'
 'org\.clojure/data\.fressian|§8.2 推奨: com.taoensso/nippy（§3.41）'

 # === §8.2 ライセンス制約 / 商用ライセンス ===
 'com\.rpl/rama|§8.2 商用ライセンス（Red Planet Labs）。代替: XTDB + worker stack + batch stack'
 'rama/rama|§8.2 商用ライセンス。代替: XTDB + worker stack + batch stack'
 'com\.github\.haifengl/smile|§8.2 GPL 3.0 ライセンス（SaaS/商用配布と衝突）。代替: scicloj/tablecloth、libpython-clj'
 'haifengl/smile|§8.2 GPL 3.0 ライセンス。代替: scicloj/tablecloth、libpython-clj'

 # === §3.40.1 射程外 ===
 'com\.hyperfiddle/electric|§3.40.1 射程外（cljs 前提、macro 重依存、API 変動激しい）'
 'hyperfiddle/electric|§3.40.1 射程外（cljs 前提、macro 重依存、API 変動激しい）'
)

found=0

# deps.edn を workspace ルート・全 brick・全 project で探す
declare -a DEPS_FILES
while IFS= read -r f; do
 DEPS_FILES+=("$f")
done < <(find . \
 -name deps.edn \
 -not -path "./.cpcache/*" \
 -not -path "./.clj-kondo/*" \
 -not -path "*/target/*" \
 -not -path "*/.cpcache/*")

if [ "${#DEPS_FILES[@]}" -eq 0 ]; then
 echo "check-deprecated-libs: no deps.edn found, skipped"
 exit 0
fi

for deps in "${DEPS_FILES[@]}"; do
 for entry in "${DEPRECATED_PATTERNS[@]}"; do
 pattern="${entry%%|*}"
 recommend="${entry#*|}"
 if grep -v '^[[:space:]]*;;' "$deps" 2>/dev/null | grep -Eq "$pattern"; then
 echo "ERROR: $deps に非推奨ライブラリが採用されています:"
 echo " パターン: $pattern"
 echo " $recommend"
 grep -nE "$pattern" "$deps" | sed 's/^/ /'
 found=1
 fi
 done
done

if [ "$found" -eq 1 ]; then
 echo ""
 echo "STACK_GUIDE.md §8.2 を参照し、推奨代替に置き換えてください。"
 exit 1
fi

echo "check-deprecated-libs: OK"
exit 0
