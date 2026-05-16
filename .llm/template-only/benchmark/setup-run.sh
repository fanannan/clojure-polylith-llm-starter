#!/usr/bin/env bash
# Prepare a benchmark demo repository without changing the agent workflow.
#
# This script does not launch Codex or Claude. It creates a demo repo, copies
# one IDEA file into IDEA.md, removes .llm/template-only/ from the demo repo,
# installs an outside-observer post-commit hook, and creates an approval marker
# command for humans to run between autonomous segments.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEMPLATE_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"

scenario=""
agent=""
model=""
tool_mode="workspace-write-network-restricted"
demo_dir=""
no_prompt=0
allow_dirty=0

usage() {
  cat >&2 <<'EOF'
Usage: .llm/template-only/benchmark/setup-run.sh --scenario <slug> [options]

Options:
  --agent <name>       Agent name. Prompted if omitted.
  --model <name>       Model name. Prompted if omitted.
  --tool-mode <mode>   Tool/sandbox mode label.
  --demo-dir <path>    Demo repository path. Defaults to /tmp/<run-id>-demo.
  --no-prompt          Fail instead of prompting for missing required values.
  --allow-dirty        Allow running from a dirty template worktree.
  -h, --help           Show this help.
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --scenario) shift; scenario="${1:-}" ;;
    --agent) shift; agent="${1:-}" ;;
    --model) shift; model="${1:-}" ;;
    --tool-mode) shift; tool_mode="${1:-}" ;;
    --demo-dir) shift; demo_dir="${1:-}" ;;
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

if [ -z "$scenario" ]; then
  usage
  exit 2
fi

agent="$(prompt_required "Agent" "$agent")"
model="$(prompt_required "Model" "$model")"

idea_file="$TEMPLATE_ROOT/.llm/template-only/examples/ideas/IDEA.$scenario.md"
if [ ! -f "$idea_file" ]; then
  echo "ERROR: scenario IDEA file not found: $idea_file" >&2
  exit 1
fi

if [ "$allow_dirty" -ne 1 ] && [ -n "$(git -C "$TEMPLATE_ROOT" status --porcelain)" ]; then
  echo "ERROR: template worktree is dirty. Commit changes first or pass --allow-dirty." >&2
  exit 1
fi

run_stamp="$(date +%Y%m%d-%H%M%S)"
run_year="$(date +%Y)"
run_id="$run_stamp-$(sanitize "$scenario")-$(sanitize "$agent")-$(sanitize "$model")"
run_dir="$TEMPLATE_ROOT/.llm/template-only/benchmark/runs/$run_year/$run_id"
demo_dir="${demo_dir:-${TMPDIR:-/tmp}/$run_id-demo}"
demo_run_dir="$demo_dir/.llm/benchmark-runs/$run_id"

if [ -e "$demo_dir" ]; then
  echo "ERROR: demo directory already exists: $demo_dir" >&2
  exit 1
fi

mkdir -p "$run_dir" "$demo_dir"
git -C "$TEMPLATE_ROOT" archive --format=tar HEAD | tar -x -C "$demo_dir"
cp "$idea_file" "$demo_dir/IDEA.md"
rm -rf "$demo_dir/.llm/template-only"

mkdir -p "$demo_run_dir"

template_rev="$(git -C "$TEMPLATE_ROOT" rev-parse HEAD)"
idea_hash="$(sha256sum "$idea_file" | awk '{print $1}')"
created_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
java_version="$(one_line_version java -version)"
clj_version="$(one_line_version clj -Sdescribe)"
bb_version="$(one_line_version bb --version)"
git_version="$(one_line_version git --version)"

cat > "$run_dir/metadata.edn" <<EOF
{:benchmark/id "$(edn_escape "$run_id")"
 :scenario "$(edn_escape "$scenario")"
 :idea/file ".llm/template-only/examples/ideas/IDEA.$(edn_escape "$scenario").md"
 :idea/sha256 "$(edn_escape "$idea_hash")"
 :template/revision "$(edn_escape "$template_rev")"
 :agent/name "$(edn_escape "$agent")"
 :agent/model "$(edn_escape "$model")"
 :tool/mode "$(edn_escape "$tool_mode")"
 :demo/dir "$(edn_escape "$demo_dir")"
 :created-at "$(edn_escape "$created_at")"
 :versions {:java "$(edn_escape "$java_version")"
            :clj "$(edn_escape "$clj_version")"
            :bb "$(edn_escape "$bb_version")"
            :git "$(edn_escape "$git_version")"}}
EOF
cp "$run_dir/metadata.edn" "$demo_run_dir/metadata.edn"
: > "$run_dir/events.edn"
: > "$run_dir/git-snapshots.edn"
: > "$demo_run_dir/events.edn"
: > "$demo_run_dir/git-snapshots.edn"

cat > "$run_dir/run.md" <<EOF
# Benchmark Run: $run_id

## Start Here

This setup is complete. The benchmark runner has prepared the demo repository,
copied the selected IDEA into \`IDEA.md\`, removed \`.llm/template-only/\`, installed
a post-commit snapshot hook, and created marker commands for human approvals and
terminal state.

Run the agent manually from the demo repo:

\`\`\`bash
cd "$demo_dir"
\`\`\`

Then paste the prompt in the "Agent Prompt" section below into the agent.

## Identity

- Scenario: \`$scenario\`
- IDEA hash: \`$idea_hash\`
- Template revision: \`$template_rev\`
- Agent: \`$agent\`
- Model: \`$model\`
- Tool mode: \`$tool_mode\`
- Demo repo: \`$demo_dir\`

## Protocol

This run is one observation point, not a verdict.

The agent should follow the normal template rules. It must stop at L0/L1 gates.
When the agent reaches a gate, review the proposal outside the benchmark timing.
If you approve the next segment, record the approval marker:

\`\`\`bash
$demo_run_dir/approve-next-segment.sh --level L1 --source manual-human --note "approved <what>"
\`\`\`

Then tell the agent:

\`\`\`text
承認を runner 側に記録しました。承認済み範囲だけ続行してください。
\`\`\`

Human decision time is not measured. If you stop for scheduling or availability
reasons, do not treat the run as benchmark evidence. Mark it as void:

\`\`\`bash
$demo_run_dir/mark-terminal-state.sh --state void --note "human stopped before protocol completion"
\`\`\`

When the run reaches a terminal state, record exactly one terminal marker:

\`\`\`bash
$demo_run_dir/mark-terminal-state.sh --state first-commit-ready --note "agent reached first commit ready"
$demo_run_dir/mark-terminal-state.sh --state blocked-at-segment-2 --note "checks could not be made green"
\`\`\`

Post-commit snapshots are recorded automatically by the demo repo git hook.

## Simulation Smoke

If you only need to check whether the benchmark protocol can flow end-to-end
without a human reviewer, use simulated approval markers. This is not a valid
benchmark observation point.

\`\`\`bash
$demo_run_dir/simulate-approval.sh --level L1 --note "simulated approval for smoke"
$demo_run_dir/mark-terminal-state.sh --state void --note "simulation smoke completed"
\`\`\`

Runs containing \`:approval/source :simulated-llm\` must not be mixed into
cross-run template evaluation. Mark pure harness smoke runs as \`void\`.

## Agent Prompt

\`\`\`text
このテンプレートを使って、この IDEA.md から初期化してください。
README.md、CLAUDE.md、DESIGN.md、.llm/guide/SPEC_GUIDE.md、
.llm/guide/BOOTSTRAP_GUIDE.md を読んでから進めてください。

まず IDEA.md を DESIGN.md への反映案、質問候補、受入基準案、
Polylith 構造案に分解してください。
自己解釈で確定できない点は 1 点ずつ確認してください。
\`\`\`
EOF
cp "$run_dir/run.md" "$demo_run_dir/run.md"

git -C "$demo_dir" init -q
git -C "$demo_dir" config user.name "Template Benchmark"
git -C "$demo_dir" config user.email "benchmark@example.invalid"

mkdir -p "$demo_dir/.git/hooks"
{
  echo '#!/usr/bin/env bash'
  echo 'set -euo pipefail'
  printf 'RUN_ID=%q\n' "$run_id"
  printf 'DEMO_RUN_DIR=%q\n' "$demo_run_dir"
  printf 'TEMPLATE_RUN_DIR=%q\n' "$run_dir"
  cat <<'HOOK'

write_snapshot() {
  local root="$1"
  local rev="$2"
  local at="$3"
  mkdir -p "$root/snapshots"
  git diff-tree --no-commit-id --name-only -r "$rev" > "$root/snapshots/$rev.paths"
  git diff-tree --no-commit-id --numstat -r "$rev" > "$root/snapshots/$rev.numstat"
  printf '{:event/type :post-commit :at "%s" :git/rev "%s" :paths-file "snapshots/%s.paths" :numstat-file "snapshots/%s.numstat"}\n' \
    "$at" "$rev" "$rev" "$rev" >> "$root/git-snapshots.edn"
}

rev="$(git rev-parse HEAD)"
at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
write_snapshot "$DEMO_RUN_DIR" "$rev" "$at"
write_snapshot "$TEMPLATE_RUN_DIR" "$rev" "$at"
HOOK
} > "$demo_dir/.git/hooks/post-commit"
chmod +x "$demo_dir/.git/hooks/post-commit"

{
  echo '#!/usr/bin/env bash'
  echo 'set -euo pipefail'
  printf 'DEMO_RUN_DIR=%q\n' "$demo_run_dir"
  printf 'TEMPLATE_RUN_DIR=%q\n' "$run_dir"
  cat <<'APPROVE'
level=""
note=""
source="manual-human"

while [ "$#" -gt 0 ]; do
  case "$1" in
    --level) shift; level="${1:-}" ;;
    --note) shift; note="${1:-}" ;;
    --source) shift; source="${1:-}" ;;
    -h|--help)
      echo "Usage: approve-next-segment.sh --level L0|L1 [--source manual-human|scripted-human|simulated-llm] --note <text>" >&2
      exit 0
      ;;
    *)
      echo "Usage: approve-next-segment.sh --level L0|L1 [--source manual-human|scripted-human|simulated-llm] --note <text>" >&2
      exit 2
      ;;
  esac
  shift
done

if [ -z "$level" ] || [ -z "$note" ]; then
  echo "ERROR: --level and --note are required" >&2
  exit 2
fi

case "$level" in
  L0|L1) ;;
  *) echo "ERROR: --level must be L0 or L1" >&2; exit 2 ;;
esac

case "$source" in
  manual-human|scripted-human|simulated-llm) ;;
  *) echo "ERROR: --source must be manual-human, scripted-human, or simulated-llm" >&2; exit 2 ;;
esac

escape() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
rev="$(git rev-parse HEAD 2>/dev/null || printf unknown)"
line="$(printf '{:event/type :approval-marker :at "%s" :level :%s :approval/source :%s :git/rev "%s" :note "%s"}' \
  "$at" "$level" "$source" "$rev" "$(escape "$note")")"
printf '%s\n' "$line" >> "$DEMO_RUN_DIR/events.edn"
printf '%s\n' "$line" >> "$TEMPLATE_RUN_DIR/events.edn"
echo "approval marker recorded: $level ($source)"
APPROVE
} > "$demo_run_dir/approve-next-segment.sh"
chmod +x "$demo_run_dir/approve-next-segment.sh"

cat > "$demo_run_dir/simulate-approval.sh" <<EOF
#!/usr/bin/env bash
exec "$(printf '%s' "$demo_run_dir/approve-next-segment.sh")" --source simulated-llm "\$@"
EOF
chmod +x "$demo_run_dir/simulate-approval.sh"

{
  echo '#!/usr/bin/env bash'
  echo 'set -euo pipefail'
  printf 'DEMO_RUN_DIR=%q\n' "$demo_run_dir"
  printf 'TEMPLATE_RUN_DIR=%q\n' "$run_dir"
  cat <<'TERMINAL'
state=""
note=""

while [ "$#" -gt 0 ]; do
  case "$1" in
    --state) shift; state="${1:-}" ;;
    --note) shift; note="${1:-}" ;;
    -h|--help)
      echo "Usage: mark-terminal-state.sh --state first-commit-ready|blocked-at-segment-N|void --note <text>" >&2
      exit 0
      ;;
    *)
      echo "Usage: mark-terminal-state.sh --state first-commit-ready|blocked-at-segment-N|void --note <text>" >&2
      exit 2
      ;;
  esac
  shift
done

if [ -z "$state" ] || [ -z "$note" ]; then
  echo "ERROR: --state and --note are required" >&2
  exit 2
fi

case "$state" in
  first-commit-ready|void|blocked-at-segment-[0-9]*) ;;
  *) echo "ERROR: invalid --state: $state" >&2; exit 2 ;;
esac

escape() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
rev="$(git rev-parse HEAD 2>/dev/null || printf unknown)"
line="$(printf '{:event/type :terminal-state :at "%s" :state :%s :git/rev "%s" :note "%s"}\n' \
  "$at" "$state" "$rev" "$(escape "$note")")"
printf '%s\n' "$line" >> "$DEMO_RUN_DIR/events.edn"
printf '%s\n' "$line" >> "$TEMPLATE_RUN_DIR/events.edn"
echo "terminal state recorded: $state"
TERMINAL
} > "$demo_run_dir/mark-terminal-state.sh"
chmod +x "$demo_run_dir/mark-terminal-state.sh"

git -C "$demo_dir" add .
git -C "$demo_dir" commit -q -m "Start benchmark demo"

echo "Benchmark run prepared:"
echo "  Run ID: $run_id"
echo "  Demo repo: $demo_dir"
echo "  Template run record: $run_dir"
echo ""
echo "Next:"
echo "  cd \"$demo_dir\""
echo "  Open the run guide:"
echo "    $demo_run_dir/run.md"
echo "  Start your agent manually with the Agent Prompt from that file."
echo ""
echo "At each approved L0/L1 gate, record:"
echo "  $demo_run_dir/approve-next-segment.sh --level L1 --source manual-human --note \"approved <what>\""
echo ""
echo "For simulation smoke only (not valid benchmark evidence):"
echo "  $demo_run_dir/simulate-approval.sh --level L1 --note \"simulated approval for smoke\""
echo "  $demo_run_dir/mark-terminal-state.sh --state void --note \"simulation smoke completed\""
echo ""
echo "At the end, record one terminal state:"
echo "  $demo_run_dir/mark-terminal-state.sh --state first-commit-ready --note \"agent reached first commit ready\""
echo "  $demo_run_dir/mark-terminal-state.sh --state blocked-at-segment-2 --note \"checks could not be made green\""
echo "  $demo_run_dir/mark-terminal-state.sh --state void --note \"human stopped before protocol completion\""
