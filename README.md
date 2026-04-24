# ＜TODO: プロジェクト名＞

**Clojure + Polylith プロジェクト**（LLM と人間の仕様共同開発フレームワーク）

- 必須技術: Clojure + tools.deps + Polylith + Malli 契約 + clj-kondo + cljfmt + Splint + clj-watson + `.llm/scripts/`
- 追加ライブラリは必要な機能カテゴリごとに最小選択し、各 brick の `deps.edn` に記録する
- 技術選定の推奨カタログは `.llm/guide/STACK_GUIDE.md`

> ⚠️ **このファイルはテンプレート配布時のものです。**
>
> プロジェクト初期化完了時に、**プロダクト README として完全に書き換えてください**。
> 書き換え手順は `.llm/guide/BOOTSTRAP_GUIDE.md` §4 を参照。

---

## このテンプレートは何か

**Clojure + tools.deps + Polylith + Malli + clj-kondo + cljfmt + Splint + clj-watson + `.llm/scripts/`** を必須層とする、**LLM 駆動開発向け**のプロジェクトテンプレート。HTTP、永続化、ライフサイクル管理などの追加技術は、必要になった機能カテゴリごとに選び、brick の `deps.edn` に追加する。

疲労最小化原則（LLM と人間の共同開発における修復コスト最小化）に基づき設計されている。
詳細思想は `CLAUDE.md`、技術選定の推奨カタログは `.llm/guide/STACK_GUIDE.md` を参照。

## 前提ツール

- **JVM LTS**
- **Clojure CLI**（`clj` コマンド、tools.deps ベース）
- **Git**
- **LLM コーディングエージェント**（Claude Code 等、`CLAUDE.md` を読める LLM）

---

## 開始手順（LLM 駆動ブートストラップ）

### 1 回のキックオフで始める

以下のいずれかのキックオフプロンプトを LLM エージェントに送信する。以降、LLM は `.llm/guide/BOOTSTRAP_GUIDE.md` に従い、仕様確定・構造作成・依存追加の承認を求めながら進める。

- **完全版**（事前に L0 項目を決めてから送信するタイプ）: 目的・ユースケース・受入基準・エントリ種別・組織名・ドメイン名候補・デプロイ構成・環境別設定を 1 通に収める。往復数最小
- **最小版**（対話しながら埋めるタイプ）: 目的 1-2 行のみ記載して送信。残りは LLM が 1 点ずつ確認する

---

#### 完全版キックオフプロンプト

```
このプロジェクトのテンプレートを使ってブートストラップを行う。
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
このプロジェクトのテンプレートを使ってブートストラップを行う。
まず CLAUDE.md、DESIGN.md、.llm/guide/BOOTSTRAP_GUIDE.md を読んでから着手してほしい。

【目的】<1-2 行で>
【エントリ種別】<Web API / CLI / バッチ / ライブラリ / ワーカ / bot / GUI>

残りの L0 項目（プロジェクト名・組織名・トップ名前空間・ドメイン名候補・
デプロイ構成・主要ユースケース・受入基準・環境別設定）は 1 点ずつ確認して。
```

---

### 承認構造

**主要バッチゲート 2 箇所**で実テキストを提示して承認を求める：

| ゲート | 承認対象 | 権限根拠 |
|---|---|---|
| 1. 仕様 + 技術選定 | DESIGN.md 反映案／workspace.edn :top-namespace 差分／README.md 冒頭差分／必要な機能カテゴリと推奨ライブラリ案 | L1。未記載領域の技術採用は L0 |
| 2. 構造 + 依存 | `poly create component/base/project` 3 コマンド／brick deps.edn 追加内容（実コード） | L1/L0 × component 作成は L1、base/project 作成・依存追加は L0（CLAUDE.md §2） |

**ゲート外の個別 L1 承認**（作成時に個別提示）:

| 成果物 | 採用条件 |
|---|---|
| config.edn（必要時）／CI 設定／build.clj（uberjar 時）／dev/user.clj 調整／workspace.edn :projects 登録／ルート deps.edn :dev :extra-deps/:extra-paths | いずれも L1、実内容を事前提示して個別承認 |

**ゲート 3 の縮退**: 完了処理のうち LLM が L1 として担うのは **KNOWLEDGE 追加エントリ** と **README プロダクト版全文** のみ。ADR は承認済み判断の記録として LLM が発行し、事後報告する。

### 完了時

LLM が最終コミットコマンド（例: `git commit -m "Complete project bootstrap"`）を提示する。ユーザが実行して完了。

**BOOTSTRAP_GUIDE.md の移動や CLAUDE.md 参照表編集は行わない**。初期化完了後は通常開発フローに移る。

### 曖昧点が見つかったとき

LLM は 1 点ずつ人間に確認する。自己解釈で進めない。

### 詰まったとき

LLM が詰まった時は `.llm/memory/QUESTIONS.md` に Q を起票して停止する。人間は Q の内容を読んで判断を提示する。

### 詳細を追いたい場合

| 目的 | 参照先 |
|---|---|
| LLM 側の技術手順 | `.llm/guide/BOOTSTRAP_GUIDE.md` |
| 承認権限・対話ルール | `.llm/guide/COLLABORATION_GUIDE.md` |
| 技術選定の推奨カタログ | `.llm/guide/STACK_GUIDE.md` |
| 作業原則 | `CLAUDE.md` |

---

## ファイル構成

```
<project-root>/
├── README.md                    ← 本ファイル（書き換え対象）
├── CLAUDE.md                    ← LLM 向け作業規約（毎セッション必読）
├── DESIGN.md                    ← プロダクト仕様（ブートストラップ時に埋める）
│
├── .llm/guide/               ← プロジェクト運営ガイド
│   ├── CODING_GUIDE.md          Clojure 書き方詳細
│   ├── POLYLITH_GUIDE.md        Polylith 運用・brick コード例
│   ├── STACK_GUIDE.md           技術選定カタログ（判断結果のメモリー）
│   ├── COLLABORATION_GUIDE.md   LLM と人間の協働プロトコル
│   ├── BOOTSTRAP_GUIDE.md       初期化手順詳細（LLM 向け、完了後は CLAUDE.md §0 の参照指示で自然にスキップ）
│   └── MAINTAINERS_GUIDE.md     テンプレート自体の保守・設計原則
│
├── .llm/memory/              ← プロジェクトの記憶（実装中に蓄積）
│   ├── QUESTIONS.md             判断保留トラッカー
│   ├── KNOWLEDGE.md             生きた知識（契約・不変条件）
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
│   ├── gen_lib_catalog.clj           技術選定カタログの EDN block から artifact 生成
│   ├── lint-import-hooks.sh          依存ライブラリ提供の clj-kondo hook 取込
│   ├── session-briefing.sh           SessionStart 時の状態ブリーフィング（REPL 状態含む）
│   ├── repl-eval.sh                  稼働中 nREPL へ eval 送信（LLM 向け、CLAUDE.md §9）
│   └── repl_eval.clj                 repl-eval.sh の Clojure 実装（clj -X:repl-eval）
│
├── .llm/data/                ← gen-lib-catalog が生成する artifact（SSOT 生成物）
│   ├── libs.edn                      lib-catalog 全 entry（Malli 検証済）
│   ├── deprecated-libs.patterns      deps.edn 採用検知用パターン
│   ├── forbidden-requires.patterns   require 検知用パターン
│   └── conflicts.patterns            併用禁止ペアパターン
│
├── .clj-kondo/config.edn        lint 機械化（polyguard hook 同梱）
├── .clj-kondo/polyguard/        custom hook（L2 本テンプレート固有パターン）
├── .gitignore
├── cljfmt.edn                   フォーマッタ
├── deps.edn                     tools.deps（必須層のみ、本番依存は brick deps.edn に）
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
- **機械化 5 層**: L1 clj-kondo 組込 linter / L2 `.clj-kondo/polyguard/` custom hook / L3 Splint / L4 `.llm/scripts/check-*.sh`（設定・構造検査）+ Polylith `poly check` + Malli instrumentation / L5 clj-watson（時間軸脆弱性）。規約を人間の注意力ではなくツールで強制（詳細は `MAINTAINERS_GUIDE.md` §5.10）
- **SSOT 生成**: `.llm/scripts/gen_lib_catalog.clj` が `STACK_GUIDE.md` の `;; lib-catalog` EDN block 群を検証・合成し `.llm/data/` 配下に artifact を emit。shell script は artifact を参照して検査する
- **REPL as Primary Workbench**: `.llm/scripts/repl-eval.sh` により LLM が稼働中 nREPL に eval / load-file を送信。永続 session で state 継続、編集から検証までを同一ターンで閉じる
- **技術選定カタログ**: 必須層はワークスペースルートで常に採用し、追加ライブラリは必要な brick の `deps.edn` に配置する。推奨カタログは `.llm/guide/STACK_GUIDE.md`
- **4 種の文書分離**: 仕様（DESIGN）/ 知識（KNOWLEDGE）/ 決定履歴（ADR）/ 判断保留（QUESTIONS）
- **自己停止プロトコル**: LLM が時間感覚なく詰まった時、ターン数閾値で停止し Q を立てる

詳細は `CLAUDE.md` §1 と `.llm/guide/MAINTAINERS_GUIDE.md`。

## ライセンス

＜TODO: プロジェクトに応じて設定＞
