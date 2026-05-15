On session start, run `bash .llm/scripts/session-briefing.sh` and read its output first.
Then follow the instructions written on CLAUDE.md.
For all workflow rules (approval levels, 4-document discipline, commit conventions), CLAUDE.md is authoritative.
For Structural Evidence, call the shared `.llm/scripts/` primitives (`check-evidence-gate.sh`, `evidence.sh`) instead of implementing agent-specific logic.
