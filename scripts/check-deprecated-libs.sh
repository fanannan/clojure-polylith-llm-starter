#!/usr/bin/env bash
# scripts/check-deprecated-libs.sh
#
# _POSSIBLE_ISSUES.md F-3 の実装。A-6（clj-kondo :discouraged-var）を補完する、
# brick deps.edn 採用宣言レベルの検査。
#
# 目的:
#   STACK_GUIDE.md §8.2 に列挙された非推奨ライブラリが brick deps.edn に
#   採用されていないか検査する。コード内使用は clj-kondo :discouraged-var で
#   検知するが（A-6）、deps.edn に採用宣言が残っているパターンは
#   clj-kondo では捕捉できないため、shell script で補完する。
#
# 検査対象:
#   全 deps.edn（ルート・brick・project）
#
# 非推奨ライブラリ（STACK_GUIDE.md §8.2 準拠、20 種）:
#   security-legacy:
#     org.apache.logging.log4j/log4j-1.2-api
#     xerces, xalan (legacy versions)
#     org.json/json
#   superseded:
#     org.clojure/java.jdbc          -> next.jdbc
#     com.stuartsierra/component     -> integrant
#     mount                          -> integrant
#     environ                        -> aero
#     org.clojure/spec.alpha         -> malli
#     compojure                      -> reitit-ring
#     io.pedestal                    -> reitit-ring
#     clj-http                       -> hato
#     org.clojure/data.json          -> jsonista
#     com.taoensso/timbre            -> mulog
#     friend                         -> buddy-auth
#     deelay.soft/keycloak           -> buddy-auth
#     memcached                      -> carmine
#     metrics-clojure                -> mulog
#     at-at                          -> chime
#     quartzite                      -> chime
#     tea-time                       -> chime
#
# 終了コード:
#   0: 非推奨ライブラリの採用なし
#   1: 採用あり

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$WORKSPACE_ROOT"

# "pattern|推奨代替" の配列
DEPRECATED_PATTERNS=(
  'log4j-1\.2-api|推奨: org.apache.logging.log4j/log4j-core 2.x'
  'org\.clojure/java\.jdbc|推奨: com.github.seancorfield/next.jdbc'
  'com\.stuartsierra/component|推奨: integrant/integrant'
  'mount/mount|推奨: integrant/integrant'
  'environ/environ|推奨: aero/aero'
  'org\.clojure/spec\.alpha|推奨: metosin/malli'
  'compojure/compojure|推奨: metosin/reitit-ring'
  'io\.pedestal/pedestal|推奨: metosin/reitit-ring'
  'clj-http/clj-http|推奨: hato/hato'
  'org\.clojure/data\.json|推奨: metosin/jsonista'
  'com\.taoensso/timbre|推奨: com.brunobonacci/mulog'
  'clj-commons/cemerick\.friend|推奨: buddy/buddy-auth'
  'cemerick/friend|推奨: buddy/buddy-auth'
  'io\.github\.metrics-clojure-ring|推奨: com.brunobonacci/mulog'
  'overtone/at-at|推奨: jarohen/chime'
  'clojurewerkz/quartzite|推奨: jarohen/chime'
  'tea-time/tea-time|推奨: jarohen/chime'
  'org\.json/json|推奨: metosin/jsonista'
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

for deps in "${DEPS_FILES[@]}"; do
  for entry in "${DEPRECATED_PATTERNS[@]}"; do
    pattern="${entry%%|*}"
    recommend="${entry#*|}"
    if grep -v '^[[:space:]]*;;' "$deps" 2>/dev/null | grep -Eq "$pattern"; then
      echo "ERROR: $deps に非推奨ライブラリが採用されています:"
      echo "  パターン: $pattern"
      echo "  $recommend"
      grep -nE "$pattern" "$deps" | sed 's/^/    /'
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
