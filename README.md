# ＜TODO: プロジェクト名＞

**Clojure + Polylith プロジェクト**（LLM と人間の仕様共同開発フレームワーク）

- 必須技術: Clojure + tools.deps + Polylith + Malli 契約 + clj-kondo + cljfmt + Splint + clj-watson + `.llm/scripts/`
- 追加ライブラリは必要な用途別機能カテゴリごとに最小選択し、各 brick の `deps.edn` に記録する
- 技術選定の判断済み推奨集は `.llm/guide/STACK_GUIDE.md`

> ⚠️ **このファイルはテンプレート配布時のものです。**
>
> プロジェクト初期化完了時に、**プロダクト README として完全に書き換えてください**。
¤ .llm/guide/BOOTSTRAP_GUIDE.md §4

## まず読む場所

| あなたの状況 | 最初に読む文書 | この README の役割 |
|---|---|---|
| **初めてテンプレートを開いた人** | 本ファイル | 入口と索引 |
| **これから初期化する人** | 本ファイル → `BOOTSTRAP_GUIDE.md` | キックオフとゲート確認 |
| **日常開発に入った LLM** | `CLAUDE.md` | 初期化後は索引だけ残る |
| **仕様を埋める人** | `DESIGN.md` | どこを埋めるかの導線 |
| **技術選定で迷う人** | `STACK_GUIDE.md` | 参照先の案内 |

**優先順位**:
- 初期化フローの入口は **README**
- 日常作業の正本は別紙
¤ CLAUDE.md
- 権限と承認の正本は別紙
¤ .llm/guide/COLLABORATION_GUIDE.md
- 初期化の詳細手順の正本は別紙
¤ .llm/guide/BOOTSTRAP_GUIDE.md

初期化完了後は、本ファイルを運用ルールの正本として使わない。プロダクト README に置き換える。

---

## このテンプレートは何か

**Clojure + tools.deps + Polylith + Malli + clj-kondo + cljfmt + Splint + clj-watson + `.llm/scripts/`** を必須技術基盤とする、**LLM 駆動開発向け**のプロジェクトテンプレート。HTTP、永続化、ライフサイクル管理などの追加技術は、必要になった用途別機能カテゴリごとに選び、brick の `deps.edn` に追加する。

疲労最小化原則（LLM と人間の共同開発における修復コスト最小化）に基づき設計されている。
詳細思想と技術選定の判断済み推奨集は別紙に置く。
∵ CLAUDE.md
∵ .llm/guide/STACK_GUIDE.md

## 読者別索引

| 目的 | 読む文書 | どこまで読めばよいか |
|---|---|---|
| 初期化を始める | `README.md` | 本ファイルの「開始手順」まで |
| 初期化の詳細手順を実行する | `.llm/guide/BOOTSTRAP_GUIDE.md` | ゲートと対象節だけ |
| 日常開発を進める | `CLAUDE.md` | 毎セッション最初から |
| 仕様を埋める・直す | `DESIGN.md` | §0 と該当節 |
| 技術選定を決める | `.llm/guide/STACK_GUIDE.md` | 冒頭の位置づけ + 該当機能節 |
| Polylith 構造を決める | `.llm/guide/POLYLITH_GUIDE.md` | 冒頭の前提 + 該当手順節 |
| 権限や承認で迷う | `.llm/guide/COLLABORATION_GUIDE.md` | §2 を正本として読む |
| 何を記録するか迷う | `.llm/memory/QUESTIONS.md` / `.llm/memory/KNOWLEDGE.md` / `.llm/memory/adr/README.md` | 各文書冒頭の更新トリガー表 |

## 前提ツール

- **JVM LTS**
- **Clojure CLI**（`clj` コマンド、tools.deps ベース）
- **Git**
- **LLM コーディングエージェント**（Claude Code 等、`CLAUDE.md` を読める LLM）

---

## 開始手順（LLM 駆動初期化）

### 1 回のキックオフで始める

以下のいずれかのキックオフプロンプトを LLM エージェントに送信する。以降、LLM は `.llm/guide/BOOTSTRAP_GUIDE.md` に従い、仕様確定・構造作成・依存追加の承認を求めながら進める。

- **完全版**（事前に人間専権 (L0) 項目を決めてから送信するタイプ）: 目的・ユースケース・受入基準・エントリ種別・組織名・ドメイン名候補・デプロイ構成・環境別設定を 1 通に収める。往復数最小
- **最小版**（対話しながら埋めるタイプ）: 目的 1-2 行のみ記載して送信。残りは LLM が 1 点ずつ確認する

---

#### 完全版キックオフプロンプト

```
このプロジェクトのテンプレートを使って初期化を行う。
まず CLAUDE.md、DESIGN.md、.llm/guide/BOOTSTRAP_GUIDE.md を読んでから着手してほしい。

【プロジェクト名】<例: billing-service>
【組織名】<例: gugenkoubou>
【トップ名前空間】<例: gugenkoubou.billing>
【最初のドメイン名】<例: invoice>
【エントリ種別】<例: Web API / CLI / バッチ / ライブラリ / ワーカ / bot / GUI>
【デプロイ構成】<例: 単一 uberjar を Docker イメージに同梱して ECS で実行>
【目的】
<例: 社内経理部門の請求書発行・追跡業務を自動化する。
現状 Excel での手作業で月 100 時間の工数がかかっており、これを 10 時間以下にする>
【主要ユースケース】
1. <例: 請求書の発行と PDF 生成>
2. <例: 未払い検知と督促メール送信>
3. <例: 月次締め処理と会計データエクスポート>
【受入基準】
1. <例: 上記 3 ユースケースが全て動作する>
2. <例: 月次締め処理が 1000 件に対して 10 秒以内に完了する>
3. <例: PII を DB に平文保存しない>
【環境別設定】<任意>
<例: DB 接続情報（dev/prod）・ポート番号・外部 API キー等>

不明点は 1 点ずつ確認し、自己解釈で埋めないでほしい。
```

#### 最小版キックオフプロンプト

```
このプロジェクトのテンプレートを使って初期化を行う。
まず CLAUDE.md、DESIGN.md、.llm/guide/BOOTSTRAP_GUIDE.md を読んでから着手してほしい。

【目的】<1-2 行で>
【エントリ種別】<Web API / CLI / バッチ / ライブラリ / ワーカ / bot / GUI>

残りの人間専権 (L0) 項目（プロジェクト名・組織名・トップ名前空間・ドメイン名候補・
デプロイ構成・主要ユースケース・受入基準・環境別設定）は 1 点ずつ確認して。
```

---

### 承認構造

**主要バッチゲート 2 箇所**で実テキストを提示して承認を求める：

| ゲート | 承認対象 | 権限根拠 |
|---|---|---|
| 1. 仕様 + 技術選定 | DESIGN.md 反映案／workspace.edn :top-namespace 差分／README.md 冒頭差分／必要な用途別機能カテゴリと推奨ライブラリ案 | 承認必須 (L1)。未記載領域の技術採用は人間専権 (L0) |
| 2. 構造 + 依存 | `poly create component/base/project` 3 コマンド／brick deps.edn 追加内容（実コード） | 承認必須 (L1) / 人間専権 (L0)。component 作成は承認必須 (L1)、base/project 作成・依存追加は人間専権 (L0) |

**条件付き承認必須 (L1) 成果物**（まとめて提示し、全承認または全修正指示で受ける）:

| 成果物 | 採用条件 |
|---|---|
| config.edn（必要時）／CI 設定／build.clj（uberjar 時）／dev/user.clj 調整／workspace.edn :projects 登録／ルート deps.edn :dev :extra-deps/:extra-paths | いずれも承認必須 (L1)。実内容をまとめて提示し、全承認または全修正指示で受ける |

**ゲート 3 の縮退**: 完了処理のうち LLM が承認必須 (L1) として担うのは **KNOWLEDGE 追加エントリ** と **README プロダクト版全文** のみ。ADR は承認済み判断の記録として LLM が発行し、事後報告する。

### 完了時

LLM が最終コミットコマンド（例: `git commit -m "Complete project bootstrap"`）を提示する。ユーザが実行して完了。

**BOOTSTRAP_GUIDE の移動や CLAUDE の参照表編集は行わない**。初期化完了後は通常開発フローに移る。

### 曖昧点が見つかったとき

LLM は 1 点ずつ人間に確認する。自己解釈で進めない。

### 詰まったとき

LLM が詰まった時は QUESTIONS に Q を起票して停止する。人間は Q の内容を読んで判断を提示する。
¤ .llm/memory/QUESTIONS.md

### 詳細を追いたい場合

| 目的 | 参照先 |
|---|---|
| LLM 側の技術手順 | `.llm/guide/BOOTSTRAP_GUIDE.md` |
| 承認権限・対話ルール | `.llm/guide/COLLABORATION_GUIDE.md` |
| 技術選定の判断済み推奨集 | `.llm/guide/STACK_GUIDE.md` |
| 作業原則 | `CLAUDE.md` |

---

## ファイル構成

```
<project-root>/
├── README.md                    ← 本ファイル（書き換え対象）
├── CLAUDE.md                    ← LLM 向け作業規約（毎セッション必読）
├── DESIGN.md                    ← プロダクト仕様（初期化時に埋める）
│
├── .llm/guide/               ← プロジェクト運営ガイド
│   ├── CODING_GUIDE.md          Clojure 書き方詳細
│   ├── POLYLITH_GUIDE.md        Polylith 運用・brick コード例
│   ├── STACK_GUIDE.md           技術選定の判断済み推奨集（判断結果の記録）
│   ├── COLLABORATION_GUIDE.md   LLM と人間の協働プロトコル
│   ├── BOOTSTRAP_GUIDE.md       初期化手順詳細（LLM 向け、完了後は CLAUDE.md §0 の参照指示で自然にスキップ）
│   └── MAINTAINERS_GUIDE.md     テンプレート自体の保守・設計原則
│
├── .llm/memory/              ← プロジェクトの記憶（実装中に蓄積）
│   ├── QUESTIONS.md             判断保留トラッカー
│   ├── KNOWLEDGE.md             現時点で有効な知識（契約・不変条件）
│   └── adr/                     アーキテクチャ決定記録
│       ├── README.md            ADR とは何か、運用ルール
│       ├── template.md          ADR 雛形
│       └── NNNN-topic.md        発行された ADR（テンプレートには含まれない）
│
├── .llm/scripts/             ← ワークスペース整合性検査・EDN 生成スクリプト
│   ├── README.md                スクリプト一覧・機械化 5 層構造
│   ├── check-workspace-integrity.sh  総合検査（完了条件から起動、§5.5）
│   ├── check-placeholders.sh         workspace.edn / deps.edn プレースホルダ残存
│   ├── check-brick-registration.sh   brick と deps.edn の登録整合
│   ├── check-deprecated-libs.sh      非推奨ライブラリの採用宣言検知
│   ├── check-forbidden-requires.sh   非推奨 namespace の require 検知
│   ├── check-conflicting-libs.sh     併用禁止ライブラリペアの検知
│   ├── check-interface-contracts.sh  interface.clj の m/=> 契約網羅
│   ├── check-single-ns-per-file.sh   1 ファイル 1 ns
│   ├── check-vulnerabilities.sh      clj-watson による脆弱性スキャン（release 前）
│   ├── gen_lib_catalog.clj           技術選定の判断済み推奨集の EDN block から生成物を生成
│   ├── lint-import-hooks.sh          依存ライブラリ提供の clj-kondo hook 取込
│   ├── session-briefing.sh           SessionStart 時の状態ブリーフィング（REPL 状態含む）
│   ├── repl-eval.sh                  稼働中 nREPL へ eval 送信（LLM 向け、CLAUDE.md §9）
│   └── repl_eval.clj                 repl-eval.sh の Clojure 実装（clj -X:repl-eval）
│
├── .llm/data/                ← gen-lib-catalog が生成する生成物（単一の正本から生成される成果物）
│   ├── libs.edn                      lib-catalog 全 entry（Malli 検証済）
│   ├── deprecated-libs.patterns      deps.edn 採用検知用パターン
│   ├── forbidden-requires.patterns   require 検知用パターン
│   └── conflicts.patterns            併用禁止ペアパターン
│
├── .clj-kondo/config.edn        lint 機械化（polyguard hook 同梱）
├── .clj-kondo/polyguard/        custom hook（機械化第 2 層: 本テンプレート固有パターン）
├── .gitignore
├── cljfmt.edn                   フォーマッタ
├── deps.edn                     tools.deps（必須技術基盤のみ，本番依存は brick deps.edn に）
├── workspace.edn                Polylith 設定
└── development/src/dev/user.clj REPL 駆動開発エントリ
```

## 各文書への導線

| 目的 | 読むべき文書 |
|---|---|
| LLM 作業規約 | `CLAUDE.md` |
| プロダクト仕様の確認・記入 | `DESIGN.md` |
| 初期化手順の詳細 | `.llm/guide/BOOTSTRAP_GUIDE.md` |
| 技術選定 | `.llm/guide/STACK_GUIDE.md` |
| Clojure の書き方で迷った | `.llm/guide/CODING_GUIDE.md` |
| Polylith 構造判断・brick 追加 | `.llm/guide/POLYLITH_GUIDE.md` |
| LLM と人間の協働方針で迷った | `.llm/guide/COLLABORATION_GUIDE.md` |
| テンプレート自体の改修 | `.llm/guide/MAINTAINERS_GUIDE.md` |
| 判断に迷った時（Q を立てる） | `.llm/memory/QUESTIONS.md` |
| 契約・不変条件の記録 | `.llm/memory/KNOWLEDGE.md` |
| 重要な設計判断の記録 | `.llm/memory/adr/README.md`（運用ルール） |
| ワークスペース整合性検査スクリプト・機械化 5 層構造 | `.llm/scripts/README.md` |

## 設計の基底思想（要約）

- **疲労最小化**: LLM の誤りを構造的に封じる（全域性・不変性・副作用の隔離）
- **機械化 5 層**: 第 1 層 clj-kondo 組込 linter / 第 2 層 `.clj-kondo/polyguard/` custom hook / 第 3 層 Splint / 第 4 層 `.llm/scripts/check-*.sh`（設定・構造検査）+ Polylith `poly check` + Malli instrumentation / 第 5 層 clj-watson（時間軸脆弱性）。規約を人間の注意力ではなくツールで強制（詳細は `MAINTAINERS_GUIDE.md` §5.10）
- **単一の正本（SSOT）生成**: `.llm/scripts/gen_lib_catalog.clj` が `STACK_GUIDE` の `;; lib-catalog` EDN block 群を検証・合成し `.llm/data/` 配下に生成物を出力する。shell script はその生成物を読む
- **REPL as Primary Workbench**: `.llm/scripts/repl-eval.sh` により LLM が稼働中 nREPL に eval / load-file を送信。永続 session で状態を継続し、編集から検証までを同一ターンで閉じる
- **技術選定の判断済み推奨集**: 必須技術基盤はワークスペースルートで常に採用し、追加ライブラリは必要な brick の `deps.edn` に配置する。判断済み推奨集は `.llm/guide/STACK_GUIDE.md`
- **4 種の文書分離**: 仕様（DESIGN）/ 知識（KNOWLEDGE）/ 決定履歴（ADR）/ 判断保留（QUESTIONS）
- **自己停止プロトコル**: LLM が時間感覚なく詰まった時、ターン数閾値で停止し Q を立てる

詳細は `CLAUDE.md` §1 と `.llm/guide/MAINTAINERS_GUIDE.md`。

## ライセンス

＜TODO: プロジェクトに応じて設定＞
