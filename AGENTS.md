# AGENTS.md — 非 Claude エージェント向け起動リダイレクト

Codex 等の非 Claude エージェントが、規約上の慣習として起動時に読むエントリ。
ポリシー文書ではなく、運用規則は CLAUDE.md 側にある。

On session start, run `bash .llm/scripts/session-briefing.sh` and read its output first.
Then follow the instructions written on CLAUDE.md.
For all workflow rules (approval levels, 4-document discipline, commit conventions), CLAUDE.md is authoritative.
For Structural Evidence, call the shared `.llm/scripts/` primitives (`check-evidence-gate.sh`, `evidence.sh`) instead of implementing agent-specific logic.
After the briefing, use `./.llm/scripts/evidence.sh what-now` when the next evidence action is unclear; it returns the single next command to run.
