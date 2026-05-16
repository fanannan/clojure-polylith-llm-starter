# Instruction-Following Instrument

This directory contains template-maintainer tooling for measuring instruction-following yield.
It is template-only and must not be copied into derived projects.

The instrument is not a model leaderboard. Its primary outputs are:

- ambiguity in the instruction corpus
- friction and near misses while following the corpus
- recurrence of observed maintainer incidents
- reform candidates that reduce repair cost without expanding the corpus

## Current Scope

Phase 1 is deterministic and does not call an LLM:

```bash
./.llm/template-only/tests/check-session-briefing-scenarios.sh
```

Phase 2 prepares an outside-observer run target, but still does not launch an agent:

```bash
./.llm/template-only/instrument/setup-run.sh \
  --case template-mode-no-project-owned-write \
  --target-mode template \
  --agent codex \
  --model gpt-5 \
  --allow-dirty
```

The command creates a target repo under `/tmp` and an observer store outside that target.
Start the agent manually from the target repo using the generated prompt.

After capture, produce a path-level preliminary score:

```bash
./.llm/template-only/instrument/score-run.sh --run <observer-run-dir>
```

After multiple runs, summarize counts and dispersion without point estimates:

```bash
./.llm/template-only/instrument/summarize-runs.sh \
  --runs-dir .llm/template-only/instrument/runs/2026 \
  --min-runs 5
```

The summary routes split valid outcomes to `:spec-ambiguous` instead of averaging them.

## Non-Goals

- Do not add `:test` to `.llm/repo-context.edn`.
- Do not put observer files inside the target repo.
- Do not publish point estimates without run count and dispersion.
- Do not use Contract Pass Rate as the acceptance criterion for reform.
- Do not commit raw transcripts by default.

## Files

- `incident-index.edn`: observed incident seeds and measurement policy.
- `cases.edn`: initial case catalog. Cases must trace to an incident or an authored md mandate.
- `setup-run.sh`: prepares a target repo and outside observer store.
- `score-run.sh`: path-level preliminary scorer for a single observed run.
- `summarize-runs.sh`: multi-run count/dispersion summary; no point estimates.
- `runs/`: generated local run records, ignored by git.
