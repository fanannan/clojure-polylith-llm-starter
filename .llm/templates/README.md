# `.llm/templates/` — Markdown 雛形 / プラットフォーム非依存断片

本ディレクトリは、派生プロジェクトへコピーまたは貼り付ける Markdown 雛形 / プラットフォーム非依存断片を置く。

| 項目 | 内容 |
|---|---|
| **対象** | 派生プロジェクトの README 雛形、PR / MR / wiki 等に貼り付ける断片 |
| **正本性** | 正本ではない。**正本は `.llm/guide/` 配下のガイドと `../../CLAUDE.md`**。本ディレクトリの断片は規約の運用補助 |
| **更新タイミング** | 規約の正本側が変化した時、断片の貼り付け先で運用された結果から改善点が観察された時 |
| **扱わないもの** | 規約の根拠説明、決定の経緯、判断保留事項（これらは guide / ADR / QUESTIONS の役割） |

`.llm/templates/` は成果物に貼り付ける断片と派生プロジェクト用雛形の置き場であり、`.llm/memory/adr/template.md` は派生プロジェクトが ADR を発行する時の文書雛形である。名前は似ているが、前者はコピー用部品、後者は決定記録のフォーマットであり、相互に代替しない。

`PROJECT_README.md` は例外的に、初期化完了時のプロダクト README 半自動生成の雛形として使う。LLM は DESIGN、design-ir、workspace / brick / project map、採用済み技術選定、実際の起動手順を材料にして全文案を作り、人間は外向け説明としてレビューする。

派生プロジェクトでルート README を完全置換した後にテンプレートの由来を読み返す入口は guide 側に置く。プロダクト README にテンプレート説明を残さない。
∵ ../guide/TEMPLATE_USAGE_GUIDE.md

## 断片の規約

- **本文に他文書への参照を含めない**: 断片は派生プロジェクトの成果物にコピーされるため、参照マーカー（`¤` / `∵` / `⚠`）や `<doc>.md §X` 形式の参照を本文に書くと貼り付け先で破綻する。由来文書・適用条件・関連規律は本 README で示す
- **コピーで完結する形にする**: 派生プロジェクトの維持コストを上げないよう、コピー時に書き換える placeholder（`<uc>`、`<entry>` 等）の規則は guide 側で示し、断片本文はそのまま貼り付けても成立する状態にする
- **platform 非依存**: GitHub / GitLab / Forgejo / 内部 wiki のいずれにも貼り付けられる素の Markdown で書く。HTML / GitHub 拡張記法（task list の絵文字、collapsible details 等）は最小限

プロダクト README 生成雛形は貼り付け断片ではなく README 雛形なので、生成後にプロジェクト固有のリンク・起動コマンド・公開 API 説明へ置換してよい。ただし雛形本体には、コピー先で壊れる固定パス参照を置かない。
∵ PROJECT_README.md

## 雛形 / 断片一覧

### `PROJECT_README.md`

- **用途**: 初期化完了時に `README.md` をプロダクト向けへ完全置換するための雛形
- **生成材料**: DESIGN、design-ir、workspace / brick / project map、採用済み技術選定、実際の起動手順
- **扱い**: LLM が半自動生成し、人間がプロダクトの入口文書としてレビューする。テンプレート説明を残さない
- **派生後の参照入口**: テンプレート由来の読み返しは guide 側に置く
∵ ../guide/TEMPLATE_USAGE_GUIDE.md

### `fixture-state-summary.md`

- **用途**: 越境ユースケースを扱う PR / MR の本文に貼り付け、fixture の境界 state と test の precondition を共有する
- **由来**: `fanannan/clojure-polylith-llm-starter#13` の観察事例（Test Plan を fixture 前に書いて手戻りした reschedule 候補・double-booking 再現失敗）
- **適用条件**: 越境 UC（複数 entity / 複数 entrypoint をまたぐ処理）を含む変更
- **関連規律**: 以下の文書に由来する。
∵ ../guide/POLYLITH_GUIDE.md §7.4.1
∵ ../guide/POLYLITH_GUIDE.md §7.4.2
∵ ../guide/COLLABORATION_GUIDE.md §7.9
∵ ../guide/COLLABORATION_GUIDE.md §7.10
∵ ../../CLAUDE.md §10.1
- **貼り付け先の例**: GitHub `.github/PULL_REQUEST_TEMPLATE.md` / GitLab `.gitlab/merge_request_templates/` / Forgejo issue template / 内部 wiki の PR チェックリスト
