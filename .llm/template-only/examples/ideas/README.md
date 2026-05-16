# Demo IDEA Files

このディレクトリには、repo root の `IDEA.md` にコピーして使う demo /
benchmark 用の着想メモを置く。完成済みアプリではなく、LLM に渡す初期入力である。

## 使い方

template repo を clone した demo 用ディレクトリで、試したい IDEA を 1 つ選んで
root の `IDEA.md` にコピーする。

```bash
cp .llm/template-only/examples/ideas/IDEA.webhook-idempotency-processor.md IDEA.md
rm -rf .llm/template-only
```

その後、LLM agent に通常の初期化手順を依頼する。

```text
このテンプレートを使って、この IDEA.md から初期化してください。
README.md、CLAUDE.md、DESIGN.md、.llm/guide/SPEC_GUIDE.md、
.llm/guide/BOOTSTRAP_GUIDE.md を読んでから進めてください。

まず IDEA.md を DESIGN.md への反映案、質問候補、受入基準案、
Polylith 構造案に分解してください。
自己解釈で確定できない点は 1 点ずつ確認してください。
```

benchmark で使う場合は、直接 agent を起動する前に `benchmark/setup-run.sh` で
demo repo を作る。setup は agent / model 情報、IDEA hash、baseline commit、
post-commit hook、承認マーカー入口を用意する。benchmark setup は observer record
を demo repo の外側に置くため、agent は benchmark protocol を読めない。

## Files

| File | 主な観測軸 |
|---|---|
| `IDEA.subscription-billing-engine.md` | 純粋関数コア、Malli 契約、過剰設計抑制 |
| `IDEA.webhook-idempotency-processor.md` | 外部入力、冪等性、承認境界、監査 |
| `IDEA.approval-workflow-engine.md` | 状態遷移、権限、期限、監査 |
| `IDEA.data-import-validation-pipeline.md` | 不正データ、正規化、エラー報告、再実行 |
| `IDEA.demand-forecast-replenishment.md` | Python 連携、バッチ、評価指標、業務ルール分離 |

## 注意

IDEA は仕様正本ではない。ここには `AC-001` のような完成済み受入基準や、
評価意図を書き込まない。LLM が `DESIGN.md` へ構造化する余地を残す。

benchmark での評価意図は `benchmark/SCENARIOS.md` に置く。demo repo で agent を
起動する前に `.llm/template-only/` を削除するため、agent はその catalog を読めない。
手動 demo でも同じ前提にしたい場合は、IDEA コピー後に `.llm/template-only/` を削除した
clone / copy 上で agent を起動する。template 保守用の本体 checkout では削除しない。
