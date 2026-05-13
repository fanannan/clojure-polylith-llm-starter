# Maintainer Discussions Archive

このディレクトリは、テンプレート保守中の一時的な process log を置く staging area である。
ここにある内容は**現在有効なルールではない**。

運用の正本は `.llm/guide/MAINTAINERS_GUIDE.md` §7 に置く。
吸収済みの entry は原則として削除されるため、テンプレート配布時に月次 entry が存在しない場合がある。

## 使い方

- 議論中の保守判断だけを一時的に置く
- 決定後は `MAINTAINERS_GUIDE.md` / 各 guide / `.llm/repo-context.edn` / `.llm/scripts/` へ吸収する
- 吸収後の entry は削除または圧縮する
- 派生プロジェクトの ADR は `.llm/memory/adr/` を使う

詳細な entry schema、状態遷移、削除条件は正本を参照する。
∵ ../../../guide/MAINTAINERS_GUIDE.md §7
