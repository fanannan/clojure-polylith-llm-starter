# Benchmark Scenarios

この文書はテンプレート保守者向けの scenario catalog である。
agent に渡す入力ではない。agent に渡すのは `examples/ideas/IDEA.*.md` を
コピーした root `IDEA.md` だけである。

benchmark setup は、agent 起動前に demo repo から `.llm/template-only/` を削除する。
そのため agent はこの文書を読めない。

## subscription-billing-engine

- IDEA file: `../examples/ideas/IDEA.subscription-billing-engine.md`
- Stress axes:
  - pure-function-core
  - Malli contract boundary
  - overgeneration control
  - future entrypoint reuse without premature Web/API scope
- Do not optimize for:
  - a specific payment provider
  - a full accounting product
  - a generic rule engine
- Generalization requirement:
  - 反映は、純粋関数コア、契約、過剰生成抑制の一般ルールとして説明できる場合に限る。

## webhook-idempotency-processor

- IDEA file: `../examples/ideas/IDEA.webhook-idempotency-processor.md`
- Stress axes:
  - authority boundary for base / project / dependency decisions
  - external input validation
  - idempotency state
  - auditability
- Do not optimize for:
  - a specific payment provider
  - a specific database
  - a full retry platform
- Generalization requirement:
  - 反映は、承認境界、外部入力処理、冪等処理の一般ルールとして説明できる場合に限る。

## demand-forecast-replenishment

- IDEA file: `../examples/ideas/IDEA.demand-forecast-replenishment.md`
- Stress axes:
  - Python kernel integration
  - batch orchestration
  - evaluation metrics without turning IDEA into DESIGN
  - ML output and business rule separation
- Do not optimize for:
  - a specific ML framework
  - GPU or external ML services
  - a full inventory SaaS
- Generalization requirement:
  - 反映は、外部 kernel 境界、評価指標、業務ルール分離の一般ルールとして説明できる場合に限る。

## approval-workflow-engine

- IDEA file: `../examples/ideas/IDEA.approval-workflow-engine.md`
- Stress axes:
  - state transition modeling
  - authority and permission boundaries
  - audit history
  - overgeneration control around notifications and UI
- Do not optimize for:
  - a specific organization chart
  - a full BPM product
  - immediate UI/auth implementation
- Generalization requirement:
  - 反映は、状態遷移、権限境界、監査履歴の一般ルールとして説明できる場合に限る。

## data-import-validation-pipeline

- IDEA file: `../examples/ideas/IDEA.data-import-validation-pipeline.md`
- Stress axes:
  - validation vs normalization separation
  - partial success and error reporting
  - rerunnable batch behavior
  - schema and fixture design
- Do not optimize for:
  - every spreadsheet format
  - a specific partner data contract
  - immediate DB persistence
- Generalization requirement:
  - 反映は、取り込み検証、正規化、部分成功、再実行性の一般ルールとして説明できる場合に限る。
