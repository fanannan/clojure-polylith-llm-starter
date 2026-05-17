# Clojure Polylith LLM Starter

**IDEA.md にアイデアを書き、LLM と一緒に Clojure + Polylith のプロダクションコードへ落とすためのテンプレート。**

_A Clojure + Polylith template for building production code from a free-form idea, with an LLM as your primary collaborator._

この README は入口であり、作業規約の正本ではない。派生プロジェクトでは初期化完了時にプロダクト README として完全置換する。テンプレート由来の説明は guide と README 補助文書に残す。

¤ .llm/guide/TEMPLATE_USAGE_GUIDE.md

---

## 30 秒で見る全体像

```text
IDEA.md（自由記述の着想）
   ↓ LLM が翻案
DESIGN.md（仕様正本: 目的・ユースケース・受入基準）
   ↓ 機械抽出
.llm/data/design-ir.edn（要件・使用例・テスト義務）
   ↓ Polylith 構造へ落とす
components / bases / projects（brick 単位の物理境界）
   ↓ Malli 契約と REPL で検証
interface + m/=> + test + trace metadata
   ↓ 構造検査と evidence gate
commit → release
```

このテンプレートの主眼は、LLM の出力を人間が常時監視し続ける構造を避けることにある。仕様、境界、契約、テスト義務、検証記録を repo 内で分離し、機械が検査できるものは script / hook / Polylith / Malli / lint に寄せる。

## 何が楽になるか

- `IDEA.md` は完成仕様でなくてよい。LLM が矛盾、質問候補、受入基準、テスト義務へ分解する
- `DESIGN.md` から requirement / use case / test obligation を生成し、実装側の trace metadata と照合できる
- Polylith の brick / interface で、LLM が触る物理範囲を小さく保てる
- Malli の `m/=>` と REPL で、動的言語のまま境界違反を短いループで顕在化できる
- `evidence.sh what-now` が、staged diff と生成 index から次の 1 action を返す
- commit 前には pre-commit hook と Structural Evidence gate が、必要な検証漏れを止める

## 読む順番

| 状況 | 入口 |
|---|---|
| 初めてテンプレートを見る | `README.md` → `README_WORKFLOW_TOOLCHAIN.md` |
| 作業ループとツールチェーンの全体像を見る | `README_WORKFLOW_TOOLCHAIN.md` |
| 初期化を実行する | `.llm/guide/BOOTSTRAP_GUIDE.md` |
| LLM が日常開発を始める | `CLAUDE.md` |
| IDEA から DESIGN へ翻案する | `.llm/guide/SPEC_GUIDE.md` |
| Clojure の書き方で迷う | `.llm/guide/CODING_GUIDE.md` |
| Polylith の brick / base / project 判断をする | `.llm/guide/POLYLITH_GUIDE.md` |
| 技術選定を決める | `.llm/guide/STACK_GUIDE.md` |
| Structural Evidence workflow を実行する | `.llm/guide/RUNBOOK_STRUCTURAL_EVIDENCE.md` |
| テンプレート自体を保守する | `.llm/guide/MAINTAINERS_GUIDE.md` |
| 派生後にテンプレート由来の情報を読み返す | `.llm/guide/TEMPLATE_USAGE_GUIDE.md` |

## 最小開始手順

1. 前提ツールを確認する。

```bash
./.llm/scripts/check-toolchain.sh
```

2. `IDEA.md` に目的、背景、避けたいこと、制約を自由に書く。すでに仕様を書ける場合は `DESIGN.md` の §1/§2/§3/§4/§8 を先に埋めてもよい。

3. LLM エージェントへ次のように依頼する。

```text
このテンプレートを使って初期化を行う。
まず CLAUDE.md、IDEA.md、DESIGN.md、.llm/guide/SPEC_GUIDE.md、.llm/guide/BOOTSTRAP_GUIDE.md を読んでから着手してほしい。

IDEA.md の内容を整理し、DESIGN.md への反映案を作ってください。
矛盾、保留、質問候補、受入基準、テスト義務を分けて提示してください。
自己解釈で埋めるべきでない点は 1 点ずつ確認してください。
```

4. LLM が仕様、構造、依存、README 置換案を提示する。base / project 作成、依存追加、公開 API、仕様変更などは承認 gate を挟む。

5. 初期化完了時に、ルート README はプロダクト README へ完全置換する。テンプレート説明をプロダクト README に残さない。

¤ .llm/guide/BOOTSTRAP_GUIDE.md
¤ .llm/templates/PROJECT_README.md
¤ .llm/guide/COLLABORATION_GUIDE.md

## 日常作業の最短ループ

どの作業でも、最初に現在状態を機械から読む。

```bash
bash .llm/scripts/session-briefing.sh
./.llm/scripts/evidence.sh what-now
```

派生プロジェクトの実装では、DESIGN / KNOWLEDGE / QUESTIONS / ADR を確認し、trace / brick map / workspace map で影響範囲を見てから、小さく REPL で確認する。編集後は対象 test、`poly check`、必要なら全体 test、workspace integrity、Structural Evidence close へ進む。

テンプレート本体の保守では、配布物、guide、script、生成器、template-only E2E が壊れていないかを見る。保守判断は project ADR ではなく maintainer archive に流す。

作業ループの詳細:
¤ README_WORKFLOW_TOOLCHAIN.md

## 文書の種類

この repo では、文書の置き場所と名前で用途を分ける。

| 種別 | 例 | 役割 |
|---|---|---|
| 入口 | `README.md` | テンプレート配布時の入口。派生後はプロダクト README に置換 |
| ユーザー向け補助説明 | `README_*.md` | 初見ユーザーの理解を補う。作業規約や生成物ではない |
| LLM / 運用 guide | `.llm/guide/*_GUIDE.md` | 日常作業、仕様翻案、協働、技術選定などの正本 |
| テンプレート保守手順 | `.llm/guide/MAINTAINER_*.md` | テンプレート保守者が使う移行・整理手順 |
| 実行手順 | `.llm/guide/RUNBOOK_*.md` | 派生プロジェクト実行時に使う操作手順 |
| 生成 view | `docs/GENERATED_VIEW_*.md`, `.llm/data/*.edn` | script から生成される閲覧用 / 機械可読 index。直接編集しない |

## 向いているプロジェクト

- 新規 Clojure プロジェクト
- Polylith による明示的な境界設計を採用したいプロジェクト
- 単独開発者または小規模チーム
- LLM と長期的に共同開発する前提のプロジェクト
- 要件変更があり、仕様・知識・判断履歴を保守し続けたいプロジェクト
- REPL 駆動、Malli 契約、データ指向設計を積極的に使うプロジェクト

向いていないもの:

- 多言語汎用の SDD テンプレートを探している場合
- サンプルアプリや全部入り Web フレームワークを期待している場合
- Polylith を採用しない Clojure プロジェクト
- 大規模チームで dashboard / 外部 issue tracker が一次作業面である場合
- 既存の独自規約を温存したまま運用する必要がある場合

## 主要な支え

- **疲労最小化**: LLM と人間の共同開発における修復コストを下げる
- **4 種の記憶分離**: 仕様、知識、決定履歴、判断保留を混ぜない
- **REPL as Primary Workbench**: 編集から観察までを同一ターンで閉じる
- **Malli 契約**: public boundary の入出力を fail-closed に寄せる
- **Polylith 境界**: component / base / project の責務を物理構造で区切る
- **Structural Evidence View**: staged diff から必要な検証と残った未知を導出する
- **生成 index**: DESIGN、brick、project、trace の情報を `.llm/data/` と `docs/` に派生させる
- **共通 script / hook**: Claude、Codex、人間が同じ gate を通る

詳細:
¤ CLAUDE.md
¤ .llm/scripts/README.md
¤ .llm/guide/MAINTAINERS_GUIDE.md

## ライセンス

＜TODO: プロジェクトに応じて設定＞
