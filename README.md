# ＜TODO: プロジェクト名＞

**Clojure + Polylith プロジェクト**（LLM と人間の仕様共同開発フレームワーク）

- 必須技術: Clojure + tools.deps + Polylith + Malli 契約 + clj-kondo + cljfmt（バージョン・JVM LTS は `.llm/guide/STACK_GUIDE.md` §2.1 参照）
- 目的別の追加ライブラリは **stack**（web-api stack / batch stack / cli stack / library stack / worker stack / data-pipeline stack / dev-tools stack）として構成
- stack 選定論理と実装マッピングの一次情報源は `.llm/guide/STACK_GUIDE.md`

> ⚠️ **このファイルはテンプレート配布時のものです。**
>
> プロジェクト初期化完了時に、**プロダクト README として完全に書き換えてください**。
> 書き換え手順は `.llm/guide/BOOTSTRAP_GUIDE.md` §4 を参照。

---

## このテンプレートは何か

**Clojure + tools.deps + Polylith + Malli + clj-kondo + cljfmt** を必須層とし、プロジェクトの性格に応じた **stack 層**（Web API、バッチ、CLI、ライブラリ配布等の目的別推奨構成）を選択できる、**LLM 駆動開発向け**のプロジェクトテンプレート（必須層のバージョンは `STACK_GUIDE.md §2.1` を参照）。Integrant・Ring・DB ドライバ等は採用する stack に応じて brick の deps.edn に追加する（必須層ではない）。

疲労最小化原則（LLM と人間の共同開発における修復コスト最小化）に基づき設計されている。
詳細思想は `CLAUDE.md` §1、設計原則は `.llm/guide/MAINTAINERS_GUIDE.md`、stack 選定は `.llm/guide/STACK_GUIDE.md`。

## 前提ツール

- **JVM LTS**（推奨バージョンは `.llm/guide/STACK_GUIDE.md` §2.1 参照）
- **Clojure CLI**（`clj` コマンド、tools.deps ベース）
- **Git**
- **LLM コーディングエージェント**（Claude Code 等、`CLAUDE.md` を読める LLM）

---

## 開始手順（LLM 駆動ブートストラップ）

### 1 回のキックオフで始める

以下のいずれかのキックオフプロンプトを LLM エージェントに送信する。以降、LLM は `.llm/guide/BOOTSTRAP_GUIDE.md` §2 に従って自走し、**主要バッチゲート 2 箇所** + **個別 L1 承認**（作成時ごと）+ **必要時の ONE BY ONE 曖昧性解消**で人間の判断を求める。

- **完全版**（事前に L0 項目を決めてから送信するタイプ）: 目的・ユースケース・受入基準・エントリ種別・組織名・ドメイン名候補・デプロイ構成・環境別設定を 1 通に収める。往復数最小
- **最小版**（対話しながら埋めるタイプ）: 目的 1-2 行とエントリ種別のみ埋めて送信。残りは LLM が `.llm/guide/COLLABORATION_GUIDE.md` §4 の ONE BY ONE 原則で引き出す

---

#### 完全版キックオフプロンプト

```
このプロジェクトのテンプレートを使ってブートストラップを行う。
まず以下を読んでから着手してほしい：
 - CLAUDE.md §1-§3（特に §1.2.5 失敗早期検知 > 事前承認）
 - DESIGN.md §0 本ファイルの埋め方
 - .llm/guide/BOOTSTRAP_GUIDE.md §2（§2.0 オーケストレーション、§2.1-§2.9 手順）
 - .llm/guide/COLLABORATION_GUIDE.md §2-§4（§2.3.1 特別承認・部分承認不採用、§3.1 ブートストラップモード）
 - .llm/guide/STACK_GUIDE.md §4.1-§4.2

【プロジェクト名】<例: billing-service>
【組織名】<例: acme>
【トップ名前空間】<例: acme.billing>
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

BOOTSTRAP_GUIDE.md §2.0 のゲート位置マップに従って進めてほしい。
DESIGN.md §0.3 の曖昧性点検を実施し、不明点があれば COLLABORATION_GUIDE.md §4 の
ONE BY ONE 原則で 1 点ずつ確認して。
```

#### 最小版キックオフプロンプト

```
このプロジェクトのテンプレートを使ってブートストラップを行う。
まず以下を読んでから着手してほしい：
 - CLAUDE.md §1-§3（特に §1.2.5 失敗早期検知 > 事前承認）
 - DESIGN.md §0 本ファイルの埋め方
 - .llm/guide/BOOTSTRAP_GUIDE.md §2
 - .llm/guide/COLLABORATION_GUIDE.md §2-§4
 - .llm/guide/STACK_GUIDE.md §4.1-§4.2

【目的】<1-2 行で>
【エントリ種別】<Web API / CLI / バッチ / ライブラリ / ワーカ / bot / GUI>

残りの L0 項目（プロジェクト名・組織名・トップ名前空間・ドメイン名候補・
デプロイ構成・主要ユースケース・受入基準・環境別設定）は
COLLABORATION_GUIDE.md §4 の ONE BY ONE 原則で 1 点ずつ確認して。
```

---

### 承認構造

**主要バッチゲート 2 箇所**で実テキストを提示して承認を求める：

| ゲート | 承認対象 | 権限根拠 |
|---|---|---|
| 1. 仕様 + stack | DESIGN.md §1-§4,§8 反映案／workspace.edn :top-namespace 差分／README.md 冒頭差分／採用 stack 提案（根拠付き） | L1 × DESIGN、**stack は L0/L1 ハイブリッド**（STACK_GUIDE.md §4.2 記載有無を LLM が明示、`COLLABORATION_GUIDE.md` §2.2） |
| 2. 構造 + 依存 | `poly create component/base/project` 3 コマンド／brick deps.edn 追加内容（実コード） | L0 × base/project 作成・依存追加（CLAUDE.md §2） |

**ゲート外の個別 L1 承認**（作成時に個別提示）:

| 成果物 | 採用条件 |
|---|---|
| config.edn（Integrant 採用時）／CI 設定／build.clj（uberjar 時）／dev/user.clj 調整／workspace.edn :projects 登録／ルート deps.edn :dev :extra-deps/:extra-paths | いずれも L1、実内容を事前提示して個別承認 |

**ゲート 3 の縮退**: 完了処理のうち LLM が L1 として担うのは **KNOWLEDGE 追加エントリ（実テキスト）** と **README プロダクト版全文** のみ。**ADR 発行は L2**（`COLLABORATION_GUIDE.md` §2.2）として LLM が自動実施、事後報告する（決定はゲート 1/2 で承認済、ADR は形式化に過ぎない。誤記は新 ADR で supersede）。

### 完了時

LLM が最終コミットコマンド（例: `git commit -m "Complete project bootstrap"`）を提示する。ユーザが実行して完了。

**BOOTSTRAP_GUIDE.md の移動や CLAUDE.md 参照表編集は行わない**（機能的に不要、CLAUDE.md §0 の参照指示で「完了後は BOOTSTRAP_GUIDE.md を参照しない」は既に明文化されているため冗長）。CLAUDE.md §2「CLAUDE.md / .llm/guide/ の自動編集は提案のみ可」は例外なく維持される（`COLLABORATION_GUIDE.md` §2.3.1 参照）。

### 曖昧点が見つかったとき

LLM は `COLLABORATION_GUIDE.md` §4 の ONE BY ONE 原則に従い、1 点ずつ人間に確認する（ゲートとは別に発生）。自己解釈で進めない（`CLAUDE.md` §0、§7）。

### 詰まったとき

LLM が自己停止プロトコル（`CLAUDE.md` §7）発動時、`.llm/memory/QUESTIONS.md` に Q を起票して停止する。人間は Q の内容を読んで判断を提示する。

### 詳細を追いたい場合

| 目的 | 参照先 |
|---|---|
| LLM 側の技術手順（ゲート位置マップ + §2.1-§2.9 実手順） | `.llm/guide/BOOTSTRAP_GUIDE.md` §2 |
| 承認権限・ADR L2 規定・特別承認/部分承認不採用 | `.llm/guide/COLLABORATION_GUIDE.md` §2 |
| 曖昧性解消プロトコル（ONE BY ONE） | `.llm/guide/COLLABORATION_GUIDE.md` §4 |
| ブートストラップモードの詳細 | `.llm/guide/COLLABORATION_GUIDE.md` §3.1 |
| 失敗早期検知の原理 | `CLAUDE.md` §1.2.5 |
| 文書間の役割分担 | `CLAUDE.md` §0〜§1 |

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
│   ├── STACK_GUIDE.md           技術スタック選定カタログ（判断結果のメモリー）
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
├── .clj-kondo/config.edn        lint 機械化
├── .gitignore
├── cljfmt.edn                   フォーマッタ
├── deps.edn                     tools.deps（必須層のみ、stack 層は brick deps.edn に）
├── workspace.edn                Polylith 設定
└── development/src/dev/user.clj REPL 駆動開発エントリ
```

## 各文書への導線

| 目的 | 読むべき文書 |
|---|---|
| LLM 作業規約 | `CLAUDE.md` |
| プロダクト仕様の確認・記入 | `DESIGN.md` |
| 初期化手順の詳細 | `.llm/guide/BOOTSTRAP_GUIDE.md` |
| 技術スタック選定・stack の判断 | `.llm/guide/STACK_GUIDE.md` |
| Clojure の書き方で迷った | `.llm/guide/CODING_GUIDE.md` |
| Polylith 構造判断・brick 追加 | `.llm/guide/POLYLITH_GUIDE.md` |
| LLM と人間の協働方針で迷った | `.llm/guide/COLLABORATION_GUIDE.md` |
| テンプレート自体の改修 | `.llm/guide/MAINTAINERS_GUIDE.md` |
| 判断に迷った時（Q を立てる） | `.llm/memory/QUESTIONS.md` |
| 契約・不変条件の記録 | `.llm/memory/KNOWLEDGE.md` |
| 重要な設計判断の記録 | `.llm/memory/adr/README.md`（運用ルール） |

## 設計の基底思想（要約）

- **疲労最小化**: LLM の誤りを構造的に封じる（全域性・不変性・副作用の隔離）
- **機械化**: 規約を人間の注意力ではなくツール（clj-kondo、cljfmt、Polylith の `poly check`、Malli instrumentation）で強制
- **stack 方式の技術スタック**: 必須層（Clojure、tools.deps、Polylith、Malli、clj-kondo、cljfmt）はワークスペースルートで常に採用、目的別の stack 層は各 brick の deps.edn に配置。推奨カタログは `.llm/guide/STACK_GUIDE.md`（真実の一箇所化）
- **4 種の文書分離**: 仕様（DESIGN）/ 知識（KNOWLEDGE）/ 決定履歴（ADR）/ 判断保留（QUESTIONS）
- **自己停止プロトコル**: LLM が時間感覚なく詰まった時、ターン数閾値で停止し Q を立てる

詳細は `CLAUDE.md` §1 と `.llm/guide/MAINTAINERS_GUIDE.md`。

## ライセンス

＜TODO: プロジェクトに応じて設定＞
