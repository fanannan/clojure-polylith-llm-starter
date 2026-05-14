#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${REPO_ROOT}"

tmp="$(mktemp)"
trap 'rm -f "${tmp}"' EXIT

mode="${1:-default}"

if [[ "${mode}" == "--all" ]]; then
  find . -type f -name '*.md' \
    ! -path './.git/*' \
    ! -path './.llm/memory/archive/*' \
    -print0 > "${tmp}.files"
else
  printf '%s\0' \
    "./CLAUDE.md" \
    "./README.md" \
    "./IDEA.md" \
    "./DESIGN.md" \
    "./.llm/guide/SPEC_GUIDE.md" \
    "./.llm/guide/BOOTSTRAP_GUIDE.md" \
    "./.llm/memory/QUESTIONS.md" \
    "./.llm/memory/KNOWLEDGE.md" \
    "./.llm/memory/adr/README.md" \
    "./.llm/scripts/README.md" \
    "./.llm/templates/README.md" \
    "./.llm/templates/fixture-state-summary.md" > "${tmp}.files"
fi

violations=0

while IFS= read -r -d '' file; do
  if ! awk -v file="$file" -v out="$tmp" '
    BEGIN {
      in_code = 0
      in_ignore = 0
      fail = 0
      ref_re = "([.][.]?/)?[A-Za-z0-9_./-]+\\.md( §[0-9][0-9.]*)?"
      marked_re = "^[¤∵⚠] "
      cue_re = "(参照|従う|読む|読ん|確認|正本|一次情報源|完了条件|障害時|問題時)"
    }

    /^```/ {
      in_code = !in_code
      next
    }

    /<!-- ref-lint: ignore-begin -->/ {
      in_ignore = 1
      next
    }

    /<!-- ref-lint: ignore-end -->/ {
      in_ignore = 0
      next
    }

    in_code || in_ignore { next }
    /^[[:space:]]*$/ { next }
    /^[|]/ { next }
    /^[[:space:]]*[#>]/ { next }
    /^[[:space:]]*[├└│]/ { next }

    {
      line = $0
      gsub(/https?:\/\/[^ )`"]+/, "", line)
      has_ref = (line ~ ref_re)
      is_marked = ($0 ~ marked_re)
      has_cue = ($0 ~ cue_re)
    }

    is_marked && !has_ref {
      printf "%s:%d: marker without markdown reference: %s\n", file, NR, $0 >> out
      fail = 1
      next
    }

    is_marked {
      marked_line = $0
      gsub(/https?:\/\/[^ )`"]+/, "", marked_line)
      ref_count = gsub(ref_re, "&", marked_line)
    }

    is_marked && ref_count > 1 {
      printf "%s:%d: multiple markdown references on marked line: %s\n", file, NR, $0 >> out
      fail = 1
      next
    }

    !is_marked && has_ref && has_cue {
      printf "%s:%d: unmarked actionable markdown reference: %s\n", file, NR, $0 >> out
      fail = 1
    }

    END { exit fail ? 1 : 0 }
  ' "$file"; then
    violations=1
  fi
done < "${tmp}.files"

rm -f "${tmp}.files"

if [[ "${violations}" -eq 1 || -s "${tmp}" ]]; then
  cat "${tmp}"
  exit 1
fi

echo "doc reference markers: ok"
