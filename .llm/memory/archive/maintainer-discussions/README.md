# Maintainer Discussions Archive

このディレクトリは、テンプレート保守に関する過去の議論・検討過程のアーカイブである。
ここにある内容は**現在有効なルールではない**。

現行ルールは `.llm/guide/MAINTAINERS_GUIDE.md` を参照する。
確定した重要判断は `.llm/memory/adr/` に記録する。

## 役割

| 種別 | 記録先 |
|---|---|
| 現在有効な保守規則 | `.llm/guide/MAINTAINERS_GUIDE.md` |
| 今後も参照すべき決定理由 | `.llm/memory/adr/NNNN-topic.md` |
| 議論の流れ、却下案、失敗分析、会話要約 | 本ディレクトリ |
| 未決事項 | `.llm/memory/QUESTIONS.md` |
| 現時点で有効な運用知識 | `.llm/memory/KNOWLEDGE.md` |

## 配置

月次ファイルに追記する。

```text
.llm/memory/archive/maintainer-discussions/
  README.md
  2026/
    2026-04.md
    2026-05.md
```

## 記録フォーマット

```markdown
## MD-YYYY-MM-NNN: <1 行要約>
- **日付**: YYYY-MM-DD
- **発端**: <ユーザ質問 / 外部レビュー / 実装中の発見など>
- **議論対象**: <何を検討したか>
- **結論**: <最終判断>
- **現行ルールへの反映**:
  - `<file>` `<section>`
- **ADR**: なし / ADR-NNNN
- **補足**: この記録は議論経緯であり、現行ルールではない
```

## 運用規則

- 現行ルール文書に過去の暫定判断を混ぜない。
- アーカイブは削除しない。ただし冗長な会話ログは要約してよい。
- 重要な設計判断は、アーカイブだけで済ませず ADR を発行する。
- `MAINTAINERS_GUIDE.md` からは、必要な月次アーカイブへの短いリンクだけを置く。
