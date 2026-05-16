# `.llm/template-only/`

このディレクトリは、テンプレート repo 専用の配布外領域である。
派生プロジェクトの通常運用物ではなく、bootstrap 完了後の project repo には残さない。

ここに置くもの:

- `examples/ideas/`: demo / benchmark 用に `IDEA.md` へコピーする着想メモ
- `tests/`: テンプレート自身の保守 E2E
- `benchmark/`: `IDEA.md` 起点の開発体験を観測し、テンプレート改善へ戻すための仕組み

## Lifecycle

```text
template repo
  keep .llm/template-only/

demo run repo
  copy an IDEA file first
  then remove .llm/template-only/ before starting the agent

derived project
  must not keep .llm/template-only/
```

`template-only/` の内容は、テンプレート保守・評価・配布前検証のために存在する。
派生プロジェクトで参照される workflow script、guide、memory とは性質が違う。

## Ownership

所有権の正本は `.llm/repo-context.edn` である。`template-only/` は
`template-owned` ではなく、派生後削除対象を表す専用区分として扱う。

¤ ../repo-context.edn

