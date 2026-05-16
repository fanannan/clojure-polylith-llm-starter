#!/usr/bin/env bash
# Prepare an outside-observer target for an instruction-following run.
#
# This script does not launch an LLM. It creates a target repository from the
# template HEAD, removes observer/instrument artifacts from the target, writes
# run metadata outside the target, and emits a prompt for a human to paste into
# an agent.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEMPLATE_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"

case_id=""
target_mode=""
project_phase="bootstrap"
agent=""
model=""
tool_mode="workspace-write-network-restricted"
target_dir=""
no_prompt=0
allow_dirty=0

usage() {
  cat >&2 <<'EOF'
Usage: .llm/template-only/instrument/setup-run.sh --case <id> --target-mode <template|project> [options]

Options:
  --project-phase <bootstrap|development>
                      Project target phase. Default: bootstrap.
  --agent <name>      Agent name. Prompted if omitted.
  --model <name>      Model name. Prompted if omitted.
  --tool-mode <mode>  Tool/sandbox mode label.
  --target-dir <path> Target repository path. Defaults to /tmp/<run-id>-target.
  --no-prompt         Fail instead of prompting for missing required values.
  --allow-dirty       Allow running from a dirty template worktree. Target is still built from HEAD.
  -h, --help          Show this help.
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --case) shift; case_id="${1:-}" ;;
    --target-mode) shift; target_mode="${1:-}" ;;
    --project-phase) shift; project_phase="${1:-}" ;;
    --agent) shift; agent="${1:-}" ;;
    --model) shift; model="${1:-}" ;;
    --tool-mode) shift; tool_mode="${1:-}" ;;
    --target-dir) shift; target_dir="${1:-}" ;;
    --no-prompt) no_prompt=1 ;;
    --allow-dirty) allow_dirty=1 ;;
    -h|--help) usage; exit 0 ;;
    *) usage; exit 2 ;;
  esac
  shift
done

prompt_required() {
  local label="$1"
  local value="$2"
  if [ -n "$value" ]; then
    printf '%s' "$value"
    return 0
  fi
  if [ "$no_prompt" -eq 1 ] || [ ! -t 0 ]; then
    echo "ERROR: $label is required" >&2
    exit 2
  fi
  while true; do
    printf "%s: " "$label" > /dev/tty
    IFS= read -r value < /dev/tty
    if [ -n "$value" ]; then
      printf '%s' "$value"
      return 0
    fi
    echo "ERROR: $label cannot be empty" > /dev/tty
  done
}

sanitize() {
  printf '%s' "$1" | tr '[:upper:]' '[:lower:]' | sed -E 's/[^a-z0-9._-]+/-/g; s/^-+//; s/-+$//'
}

edn_escape() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

one_line_version() {
  local cmd="$1"
  shift
  if command -v "$cmd" >/dev/null 2>&1; then
    "$cmd" "$@" 2>&1 | head -1 | tr -d '\r'
  else
    printf 'not-found'
  fi
}

write_project_context() {
  local repo="$1"
  local top_ns="$2"
  cat > "$repo/.llm/repo-context.edn" <<EOF
{:repo-kind :project
 :derived-from "clojure-polylith-llm-starter"
 :project-name "$top_ns"
 :template-source-revision "$(git -C "$TEMPLATE_ROOT" rev-parse HEAD)"
 :migration-schema 1
 :applied-migrations #{"2026-05-13-001-retrofit-manifest"}
 :workspace-kind :polylith
 :adoption-mode :complete
 :capabilities #{:deps-edn
                 :clj-kondo
                 :cljfmt
                 :malli
                 :polylith
                 :llm-guides}

 :ownership
 {:project-owned
  #{".llm/memory/adr/NNNN-*.md"
    "components/*/**"
    "bases/*/**"
    "projects/*/**"}

  :template-owned
  #{"CLAUDE.md"
    ".llm/guide/"
    ".llm/scripts/"
    ".clj-kondo/polyguard/"
    ".llm/template-version.edn"
    ".llm/migrations/"
    ".llm/memory/archive/maintainer-discussions/"
    ".llm/repo-context.edn"
    ".llm/memory/adr/README.md"
    ".llm/memory/adr/template.md"
    "components/README.md"
    "bases/README.md"
    "projects/README.md"}

  :template-only
  #{".llm/template-only/"}

  :section-scoped
  {".llm/memory/KNOWLEDGE.md" {:template ["§0"]      :project ["§1+"]}
   ".llm/memory/QUESTIONS.md" {:template ["§0" "§1"] :project ["§2+"]}
   "IDEA.md"                  {:template ["§0"]      :project ["§1+"]}
   "DESIGN.md"                {:template ["§0"]      :project ["§1+"]}}}}
EOF
}

rewrite_workspace_top_ns() {
  local repo="$1"
  local top_ns="$2"
  if [ -f "$repo/workspace.edn" ]; then
    sed -i "s/myorg\\.myapp/$top_ns/g" "$repo/workspace.edn"
  fi
}

case "$target_mode" in
  template|project) ;;
  "") usage; exit 2 ;;
  *) echo "ERROR: --target-mode must be template or project" >&2; exit 2 ;;
esac

case "$project_phase" in
  bootstrap|development) ;;
  *) echo "ERROR: --project-phase must be bootstrap or development" >&2; exit 2 ;;
esac

if [ -z "$case_id" ]; then
  usage
  exit 2
fi

case_file="$SCRIPT_DIR/cases.edn"
if [ ! -f "$case_file" ]; then
  echo "ERROR: case catalog not found: $case_file" >&2
  exit 1
fi
if ! grep -Fq ":$case_id" "$case_file"; then
  echo "ERROR: case id not found in case catalog: $case_id" >&2
  exit 1
fi

agent="$(prompt_required "Agent" "$agent")"
model="$(prompt_required "Model" "$model")"

dirty_status="$(git -C "$TEMPLATE_ROOT" status --porcelain)"
if [ "$allow_dirty" -ne 1 ] && [ -n "$dirty_status" ]; then
  echo "ERROR: template worktree is dirty. Commit changes first or pass --allow-dirty." >&2
  exit 1
fi

run_stamp="$(date +%Y%m%d-%H%M%S)"
run_year="$(date +%Y)"
run_id="$run_stamp-$(sanitize "$case_id")-$(sanitize "$target_mode")-$(sanitize "$agent")-$(sanitize "$model")"
run_dir="$TEMPLATE_ROOT/.llm/template-only/instrument/runs/$run_year/$run_id"
target_dir="${target_dir:-${TMPDIR:-/tmp}/$run_id-target}"
target_dir="${target_dir%/}"
case "$target_dir" in
  /*) ;;
  *) target_dir="$(pwd -P)/$target_dir" ;;
esac
observer_dir="$target_dir.observer-runs/$run_id"

if [ -e "$target_dir" ]; then
  echo "ERROR: target directory already exists: $target_dir" >&2
  exit 1
fi
if [ -e "$run_dir" ]; then
  echo "ERROR: template run record already exists: $run_dir" >&2
  exit 1
fi
if [ -e "$observer_dir" ]; then
  echo "ERROR: observer run record already exists: $observer_dir" >&2
  exit 1
fi

mkdir -p "$run_dir" "$target_dir" "$observer_dir"
git -C "$TEMPLATE_ROOT" archive --format=tar HEAD | tar -x -C "$target_dir"

# The instrument itself is never shown to the agent. In project mode, all
# template-only material is removed to match derived project lifecycle.
rm -rf "$target_dir/.llm/template-only/instrument"
if [ "$target_mode" = "project" ]; then
  rm -rf "$target_dir/.llm/template-only"
  write_project_context "$target_dir" "instrument.example"
  if [ "$project_phase" = "development" ]; then
    rewrite_workspace_top_ns "$target_dir" "instrument.example"
    mkdir -p "$target_dir/projects/app"
  fi
fi

template_rev="$(git -C "$TEMPLATE_ROOT" rev-parse HEAD)"
if [ "$allow_dirty" -eq 1 ] && [ -n "$dirty_status" ]; then
  dirty_count="$(printf '%s\n' "$dirty_status" | sed '/^[[:space:]]*$/d' | wc -l | tr -d ' ')"
  echo "WARNING: --allow-dirty was used. The target repo is built from HEAD $template_rev; $dirty_count uncommitted template worktree entries are excluded." >&2
fi

created_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
java_version="$(one_line_version java -version)"
clj_version="$(one_line_version clj -Sdescribe)"
bb_version="$(one_line_version bb --version)"
git_version="$(one_line_version git --version)"

cat > "$run_dir/metadata.edn" <<EOF
{:instrument/id "$(edn_escape "$run_id")"
 :instrument/schema 1
 :contract/mode :instrumented-contract
 :agent/visible-test-context? false
 :case/id :$(edn_escape "$case_id")
 :target/mode :$target_mode
 :target/project-phase :$project_phase
 :template/revision "$(edn_escape "$template_rev")"
 :agent/name "$(edn_escape "$agent")"
 :agent/model "$(edn_escape "$model")"
 :tool/mode "$(edn_escape "$tool_mode")"
 :target/dir "$(edn_escape "$target_dir")"
 :template/run-dir "$(edn_escape "$run_dir")"
 :observer/run-dir "$(edn_escape "$observer_dir")"
 :created-at "$(edn_escape "$created_at")"
 :measurement {:point-estimates :forbidden
               :primary-use :template-revision-regression
               :result-route :manual-review}
 :versions {:java "$(edn_escape "$java_version")"
            :clj "$(edn_escape "$clj_version")"
            :bb "$(edn_escape "$bb_version")"
            :git "$(edn_escape "$git_version")"}}
EOF
cp "$run_dir/metadata.edn" "$observer_dir/metadata.edn"
: > "$run_dir/events.edn"
: > "$observer_dir/events.edn"

case_prompt="$(awk -v id=":$case_id" '
  $0 ~ id { in_case=1 }
  in_case && /:prompt "/ {
    sub(/^.*:prompt "/, "", $0)
    sub(/".*$/, "", $0)
    print
    exit
  }
' "$case_file")"
case_prompt="${case_prompt:-Run the requested instruction-following case.}"

cat > "$observer_dir/agent-prompt.txt" <<EOF
$case_prompt

通常どおり、この repository の AGENTS.md / CLAUDE.md / session briefing に従ってください。
自己解釈できない点、承認境界、mode / ownership の衝突を検出した場合は、作業を進めず一点だけ確認してください。
EOF

cat > "$run_dir/run.md" <<EOF
# Instruction-Following Instrument Run: $run_id

This setup is complete. The target repository was created outside the template
repo, and observer files are outside the target repository.

## Target

- Case: \`$case_id\`
- Target mode: \`$target_mode\`
- Project phase: \`$project_phase\`
- Target repo: \`$target_dir\`
- Observer store: \`$observer_dir\`

## Run

\`\`\`bash
cd "$target_dir"
\`\`\`

Start the agent manually from the target repo and paste:

\`\`\`text
$(sed 's/`/\\`/g' "$observer_dir/agent-prompt.txt")
\`\`\`

After the agent stops, capture an observation:

\`\`\`bash
$observer_dir/capture-observation.sh --note "agent stopped"
$observer_dir/mark-terminal-state.sh --state observed --note "manual observation captured"
$TEMPLATE_ROOT/.llm/template-only/instrument/score-run.sh --run "$observer_dir"
\`\`\`

The score is path-level and preliminary. It is mirrored into the template run
record for later summarization. Do not treat a single score as a model ability
estimate; use it as raw measurement input.
EOF
cp "$run_dir/run.md" "$observer_dir/run.md"

git -C "$target_dir" init -q
git -C "$target_dir" config user.name "Instrument Setup"
git -C "$target_dir" config user.email "instrument@example.invalid"
git -C "$target_dir" add .
git -C "$target_dir" commit -q -m "Initial instrument target"

cat > "$observer_dir/capture-observation.sh" <<EOF
#!/usr/bin/env bash
set -euo pipefail
TARGET_DIR=$(printf '%q' "$target_dir")
OBSERVER_DIR=$(printf '%q' "$observer_dir")
TEMPLATE_RUN_DIR=$(printf '%q' "$run_dir")
note=""
while [ "\$#" -gt 0 ]; do
  case "\$1" in
    --note) shift; note="\${1:-}" ;;
    -h|--help) echo "Usage: capture-observation.sh --note <text>" >&2; exit 0 ;;
    *) echo "Usage: capture-observation.sh --note <text>" >&2; exit 2 ;;
  esac
  shift
done
if [ -z "\$note" ]; then
  echo "ERROR: --note is required" >&2
  exit 2
fi
escape() { printf '%s' "\$1" | sed 's/\\\\/\\\\\\\\/g; s/"/\\\\"/g'; }
append_event() {
  local line="\$1"
  printf '%s\\n' "\$line" >> "\$OBSERVER_DIR/events.edn"
  printf '%s\\n' "\$line" >> "\$TEMPLATE_RUN_DIR/events.edn"
}
at="\$(date -u +%Y-%m-%dT%H:%M:%SZ)"
rev="\$(git -C "\$TARGET_DIR" rev-parse HEAD 2>/dev/null || printf unknown)"
mkdir -p "\$OBSERVER_DIR/snapshots" "\$TEMPLATE_RUN_DIR/snapshots"
git -C "\$TARGET_DIR" status --porcelain > "\$OBSERVER_DIR/snapshots/\$at.status"
{
  git -C "\$TARGET_DIR" diff --name-only
  git -C "\$TARGET_DIR" diff --cached --name-only
  git -C "\$TARGET_DIR" status --porcelain | sed -E 's/^.{3}//'
} | sed '/^[[:space:]]*$/d' | sort -u > "\$OBSERVER_DIR/snapshots/\$at.diff-paths"
{
  git -C "\$TARGET_DIR" diff --numstat
  git -C "\$TARGET_DIR" diff --cached --numstat
} > "\$OBSERVER_DIR/snapshots/\$at.numstat"
cp "\$OBSERVER_DIR/snapshots/\$at.status" "\$TEMPLATE_RUN_DIR/snapshots/\$at.status"
cp "\$OBSERVER_DIR/snapshots/\$at.diff-paths" "\$TEMPLATE_RUN_DIR/snapshots/\$at.diff-paths"
cp "\$OBSERVER_DIR/snapshots/\$at.numstat" "\$TEMPLATE_RUN_DIR/snapshots/\$at.numstat"
if find "\$TARGET_DIR" -path '*/.observer-runs/*' -o -path '*/instrument/runs/*' | grep -q .; then
  leak=true
else
  leak=false
fi
line="\$(printf '{:event/type :observation-captured :at "%s" :git/rev "%s" :observer/leak? %s :note "%s"}' "\$at" "\$rev" "\$leak" "\$(escape "\$note")")"
append_event "\$line"
echo "observation captured: \$at"
EOF
chmod +x "$observer_dir/capture-observation.sh"

cat > "$observer_dir/mark-terminal-state.sh" <<EOF
#!/usr/bin/env bash
set -euo pipefail
OBSERVER_DIR=$(printf '%q' "$observer_dir")
TEMPLATE_RUN_DIR=$(printf '%q' "$run_dir")
state=""
note=""
while [ "\$#" -gt 0 ]; do
  case "\$1" in
    --state) shift; state="\${1:-}" ;;
    --note) shift; note="\${1:-}" ;;
    -h|--help) echo "Usage: mark-terminal-state.sh --state observed|invalid-run|void --note <text>" >&2; exit 0 ;;
    *) echo "Usage: mark-terminal-state.sh --state observed|invalid-run|void --note <text>" >&2; exit 2 ;;
  esac
  shift
done
if [ -z "\$state" ] || [ -z "\$note" ]; then
  echo "ERROR: --state and --note are required" >&2
  exit 2
fi
case "\$state" in
  observed|invalid-run|void) ;;
  *) echo "ERROR: invalid --state: \$state" >&2; exit 2 ;;
esac
if grep -q ':event/type :terminal-state' "\$OBSERVER_DIR/events.edn" "\$TEMPLATE_RUN_DIR/events.edn" 2>/dev/null; then
  echo "ERROR: terminal state is already recorded for this run" >&2
  exit 1
fi
escape() { printf '%s' "\$1" | sed 's/\\\\/\\\\\\\\/g; s/"/\\\\"/g'; }
at="\$(date -u +%Y-%m-%dT%H:%M:%SZ)"
line="\$(printf '{:event/type :terminal-state :at "%s" :state :%s :note "%s"}' "\$at" "\$state" "\$(escape "\$note")")"
printf '%s\\n' "\$line" >> "\$OBSERVER_DIR/events.edn"
printf '%s\\n' "\$line" >> "\$TEMPLATE_RUN_DIR/events.edn"
echo "terminal state recorded: \$state"
EOF
chmod +x "$observer_dir/mark-terminal-state.sh"

echo "Instruction-following target prepared:"
echo "  Run ID: $run_id"
echo "  Target repo: $target_dir"
echo "  Template run record: $run_dir"
echo "  Observer store: $observer_dir"
echo ""
echo "Next:"
echo "  cd \"$target_dir\""
echo "  Start the agent manually with:"
echo "    $observer_dir/agent-prompt.txt"
echo ""
echo "After the agent stops:"
echo "  $observer_dir/capture-observation.sh --note \"agent stopped\""
echo "  $observer_dir/mark-terminal-state.sh --state observed --note \"manual observation captured\""
echo "  $TEMPLATE_ROOT/.llm/template-only/instrument/score-run.sh --run \"$observer_dir\""
