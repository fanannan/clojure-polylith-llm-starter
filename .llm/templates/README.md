# `.llm/templates/` — platform-neutral fragment

本ディレクトリは、派生プロジェクトの PR 本文 / MR 本文 / 内部 wiki 等に**そのままコピー可能な platform-neutral fragment** を置く。

| 項目 | 内容 |
|---|---|
| **対象** | 派生プロジェクトの成果物（PR / MR / wiki 等）に貼り付ける fragment |
| **正本性** | 正本ではない。**正本は `.llm/guide/` 配下のガイドと `../../CLAUDE.md`**。本ディレクトリの fragment は規約の運用補助 |
| **更新タイミング** | 規約の正本側が変化した時、fragment の貼り付け先で運用された結果から改善点が観察された時 |
| **扱わないもの** | 規約の根拠説明、決定の経緯、判断保留事項（これらは guide / ADR / QUESTIONS の役割） |

## fragment の規約

- **本文に他文書への参照を含めない**: fragment は派生プロジェクトの成果物にコピーされるため、参照マーカー（`¤` / `∵` / `⚠`）や `<doc>.md §X` 形式の参照を本文に書くと貼り付け先で破綻する。由来文書・適用条件・関連規律は本 README で示す
- **コピーで完結する形にする**: 派生プロジェクトの維持コストを上げないよう、コピー時に書き換える placeholder（`<uc>`、`<entry>` 等）の規則は guide 側で示し、fragment 本文はそのまま貼り付けても成立する状態にする
- **platform 非依存**: GitHub / GitLab / Forgejo / 内部 wiki のいずれにも貼り付けられる素の Markdown で書く。HTML / GitHub 拡張記法（task list の絵文字、collapsible details 等）は最小限

## fragment 一覧

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
