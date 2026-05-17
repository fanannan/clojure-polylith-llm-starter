#!/usr/bin/env bash
# Smoke test for Instruction-Following Instrument case catalog validation.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEMPLATE_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
BASE="${TMPDIR:-/tmp}/clojure-polylith-template-instrument-cases-smoke"

cleanup() {
  rm -rf "$BASE"
}
trap cleanup EXIT

fail() {
  echo "instrument cases smoke failed: $*" >&2
  exit 1
}

require_grep() {
  local pattern="$1"
  local path="$2"
  grep -q "$pattern" "$path" || fail "missing pattern '$pattern' in $path"
}

rm -rf "$BASE"
mkdir -p "$BASE"

"$TEMPLATE_ROOT/.llm/template-only/instrument/check-cases.sh" > "$BASE/live.out"
require_grep 'check-instrument-cases: OK' "$BASE/live.out"

cat > "$BASE/incidents.edn" <<'EOF'
{:instrument/schema 1
 :families
 {:mode-and-ownership
  {:seed-incidents #{:md-known}}}
 :incidents
 {:md-known {:archive "MD-KNOWN"}}}
EOF

cat > "$BASE/bad-unknown-incident.edn" <<'EOF'
{:case/schema 1
 :families
 {:mode-and-ownership
  {:cases
   {:bad-case
    {:status :pilot
     :target/mode :template
     :trace/incidents #{:md-missing}
     :prompt "Do the thing."
     :observable-expectations [{:expect :must-stop}]}}}}}
EOF

if "$TEMPLATE_ROOT/.llm/template-only/instrument/check-cases.sh" \
  --cases "$BASE/bad-unknown-incident.edn" \
  --incident-index "$BASE/incidents.edn" > "$BASE/bad-unknown-incident.out" 2>&1; then
  fail "unknown incident fixture unexpectedly passed"
fi
require_grep 'unknown :trace/incidents' "$BASE/bad-unknown-incident.out"

cat > "$BASE/bad-untraced.edn" <<'EOF'
{:case/schema 1
 :families
 {:mode-and-ownership
  {:cases
   {:bad-case
    {:status :pilot
     :target/mode :template
     :prompt "Do the thing."
     :observable-expectations [{:expect :must-stop}]}}}}}
EOF

if "$TEMPLATE_ROOT/.llm/template-only/instrument/check-cases.sh" \
  --cases "$BASE/bad-untraced.edn" \
  --incident-index "$BASE/incidents.edn" > "$BASE/bad-untraced.out" 2>&1; then
  fail "untraced fixture unexpectedly passed"
fi
require_grep 'non-exploratory case must trace' "$BASE/bad-untraced.out"

echo "instrument cases smoke: OK"
