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

# 1. Live catalog must validate against the real corpus.
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

# A synthetic corpus root. Mandate annotations are scanned only from CLAUDE.md
# and .llm/guide/*.md, so the fixture mandate lives in CLAUDE.md. The fenced
# annotation is a negative control: if fences were not stripped it would join
# as a duplicate M-9001 and fail the good fixture below.
cat > "$BASE/CLAUDE.md" <<'EOF'
# Synthetic corpus

[mandate: M-9001/known-mandate type:workflow tier:kernel]

Run the known mandate before doing the thing.

```md
[mandate: M-9001/fenced-example type:workflow tier:kernel]
```
EOF

cat > "$BASE/good-mandate-traced.edn" <<'EOF'
{:case/schema 1
 :families
 {:mode-and-ownership
  {:cases
   {:good-case
    {:status :pilot
     :target/mode :template
     :trace/mandates #{"M-9001"}
     :prompt "Do the thing."
     :observable-expectations [{:expect :must-stop}]}}}}}
EOF

# 2. A case tracing to an existing M-NNNN annotation passes.
"$TEMPLATE_ROOT/.llm/template-only/instrument/check-cases.sh" \
  --cases "$BASE/good-mandate-traced.edn" \
  --incident-index "$BASE/incidents.edn" \
  --mandate-root "$BASE" > "$BASE/good-mandate-traced.out"
require_grep 'check-instrument-cases: OK' "$BASE/good-mandate-traced.out"

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

# 3. An unknown incident trace fails.
if "$TEMPLATE_ROOT/.llm/template-only/instrument/check-cases.sh" \
  --cases "$BASE/bad-unknown-incident.edn" \
  --incident-index "$BASE/incidents.edn" > "$BASE/bad-unknown-incident.out" 2>&1; then
  fail "unknown incident fixture unexpectedly passed"
fi
require_grep 'unknown :trace/incidents' "$BASE/bad-unknown-incident.out"

cat > "$BASE/bad-unknown-mandate.edn" <<'EOF'
{:case/schema 1
 :families
 {:mode-and-ownership
  {:cases
   {:bad-case
    {:status :pilot
     :target/mode :template
     :trace/mandates #{"M-9999"}
     :prompt "Do the thing."
     :observable-expectations [{:expect :must-stop}]}}}}}
EOF

# 4. A :trace/mandates id with no authored annotation fails.
if "$TEMPLATE_ROOT/.llm/template-only/instrument/check-cases.sh" \
  --cases "$BASE/bad-unknown-mandate.edn" \
  --incident-index "$BASE/incidents.edn" \
  --mandate-root "$BASE" > "$BASE/bad-unknown-mandate.out" 2>&1; then
  fail "unknown mandate fixture unexpectedly passed"
fi
require_grep 'unknown :trace/mandates' "$BASE/bad-unknown-mandate.out"

cat > "$BASE/incident-traced.edn" <<'EOF'
{:case/schema 1
 :families
 {:mode-and-ownership
  {:cases
   {:incident-case
    {:status :pilot
     :target/mode :template
     :trace/incidents #{:md-known}
     :prompt "Do the thing."
     :observable-expectations [{:expect :must-stop}]}}}}}
EOF

mkdir -p "$BASE/bad-root"
cat > "$BASE/bad-root/CLAUDE.md" <<'EOF'
# Synthetic corpus

[mandate: M-9001/known-mandate type:bogus tier:kernel]

Malformed mandate annotation.
EOF

# 5. A malformed authored annotation fails even when the case itself is valid.
if "$TEMPLATE_ROOT/.llm/template-only/instrument/check-cases.sh" \
  --cases "$BASE/incident-traced.edn" \
  --incident-index "$BASE/incidents.edn" \
  --mandate-root "$BASE/bad-root" > "$BASE/bad-mandate-root.out" 2>&1; then
  fail "malformed mandate fixture unexpectedly passed"
fi
require_grep 'type: must be one of' "$BASE/bad-mandate-root.out"

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

# 6. A non-exploratory case with no trace fails.
if "$TEMPLATE_ROOT/.llm/template-only/instrument/check-cases.sh" \
  --cases "$BASE/bad-untraced.edn" \
  --incident-index "$BASE/incidents.edn" > "$BASE/bad-untraced.out" 2>&1; then
  fail "untraced fixture unexpectedly passed"
fi
require_grep 'non-exploratory case must trace' "$BASE/bad-untraced.out"

echo "instrument cases smoke: OK"
