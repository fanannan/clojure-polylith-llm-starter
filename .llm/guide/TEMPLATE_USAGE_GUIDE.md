# TEMPLATE_USAGE_GUIDE.md — テンプレート利用後の参照先

本文書は、派生プロジェクトでルート README をプロダクト README に完全置換した後も、テンプレートの由来・初期化思想・参照導線を読み返せるようにするための案内である。

## 位置づけ

- テンプレート repo のルート `README.md` は、テンプレート利用開始時の入口である。
- 派生プロジェクトのルート `README.md` は、プロダクト利用者向けの入口である。
- 派生後にテンプレート説明を読み返す必要がある場合は、ルート `README.md` ではなく本文書を入口にする。
- 本文書はテンプレート利用・保守の索引であり、日常開発の正本ではない。

## いつ読むか

| 状況 | 読む場所 |
|---|---|
| 初期化手順を確認したい | BOOTSTRAP_GUIDE |
| 日常作業のルールを確認したい | CLAUDE |
| 仕様から実装へ展開する規律を確認したい | SPEC_GUIDE |
| Clojure / Polylith の書き方を確認したい | CODING_GUIDE / POLYLITH_GUIDE |
| 技術選定の推奨を確認したい | STACK_GUIDE |
| テンプレート自体の設計原則を確認したい | MAINTAINERS_GUIDE |

¤ BOOTSTRAP_GUIDE.md
¤ ../../CLAUDE.md
¤ SPEC_GUIDE.md
¤ CODING_GUIDE.md
¤ POLYLITH_GUIDE.md
¤ STACK_GUIDE.md
¤ MAINTAINERS_GUIDE.md

## README の扱い

派生プロジェクトでは、初期化完了時にルート `README.md` をプロダクト README として半自動生成し、完全置換する。

このとき、テンプレート説明をプロダクト README に残さない。テンプレート利用後に必要な情報は、本文書および各 guide に残す。

プロダクト README の生成雛形:
¤ ../templates/PROJECT_README.md

## テンプレート更新時

テンプレートの新しい版を派生プロジェクトへ取り込む場合は、ルート README ではなく migration / adoption plan を確認する。

関連スクリプト:

```bash
./.llm/scripts/propose-template-migrations.sh
./.llm/scripts/propose-adoption-plan.sh
```

関連文書:
¤ MAINTAINERS_GUIDE.md §7
