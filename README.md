# ＜TODO: プロジェクト名＞

**Clojure + Polylith プロジェクト**（LLM と人間の仕様共同開発フレームワーク）

- 必須技術: Clojure 1.12 + tools.deps + Polylith + Malli 契約 + clj-kondo + cljfmt（JVM 21 LTS）
- 目的別の追加ライブラリは **stack**（web-api stack / batch stack / cli stack / library stack / worker stack / data-pipeline stack / dev-tools stack）として構成
- stack 選定論理と実装マッピングの一次情報源は `project-guide/STACK_GUIDE.md`

> ⚠️ **このファイルはテンプレート配布時のものです。**
>
> プロジェクト初期化完了時に、**プロダクト README として完全に書き換えてください**。
> 書き換え手順は `project-guide/BOOTSTRAP_GUIDE.md` §4 を参照。

---

## このテンプレートは何か

**Clojure 1.12 + tools.deps + Polylith + Malli + clj-kondo + cljfmt** を必須層とし、プロジェクトの性格に応じた **stack 層**（Web API、バッチ、CLI、ライブラリ配布等の目的別推奨構成）を選択できる、**LLM 駆動開発向け**のプロジェクトテンプレート。Integrant・Ring・DB ドライバ等は採用する stack に応じて brick の deps.edn に追加する（必須層ではない）。

疲労最小化原則（LLM と人間の共同開発における修復コスト最小化）に基づき設計されている。
詳細思想は `CLAUDE.md` §1、設計原則は `project-guide/MAINTAINERS_GUIDE.md`、stack 選定は `project-guide/STACK_GUIDE.md`。

## 前提ツール

- **JVM 21 LTS**（または最新 LTS）
- **Clojure CLI**（`clj` コマンド、tools.deps ベース）
- **Git**
- **LLM コーディングエージェント**（Claude Code 等、`CLAUDE.md` を読める LLM）

---

## 開始手順（ブートストラップ）

**原則**: 人間は**意思決定・承認・最終確認**に専念し、それ以外の作業は LLM に委譲する。
各ステップに **🧑 人間** と **🤖 LLM への指示** の区別を明記した。LLM への指示は**コピペして `<...>` を埋めるだけ**で使える形で提示する。

### ステップ 0: テンプレートの取得と LLM セッション開始

#### 🧑 人間が行うこと

1. リポジトリを clone またはテンプレートとして新規作成
2. LLM エージェント（Claude Code 等）をプロジェクトディレクトリで起動
3. LLM に**初回プロンプト**を送る：

```
このプロジェクトのテンプレートを使ってブートストラップを行いたい。
まず `CLAUDE.md` §1 第一原理と §2 禁止事項を読んで、
理解した内容を 3〜5 行で要約して教えて。
その後、`README.md` の開始手順を参照して、ステップ 1 の質問をしてほしい。
```

#### 🤖 LLM が行うこと

- CLAUDE.md §1, §2, §3 を読む
- 理解を要約して人間に提示
- ステップ 1 の意思決定質問を人間に投げる

---

### ステップ 1: プロジェクトの基本情報を決定（人間の意思決定）

本ステップの**詳細な埋め方・担当分担・曖昧性の取り扱いは `DESIGN.md` §0 本ファイルの埋め方に一元化**されている。本節は最小のガイドのみ示す。

#### 🧑 人間が決めること

事前にメモしておくと LLM との対話が最小回数で済む項目：

| 決定項目 | 例 |
|---|---|
| **プロジェクト名** | `billing-service` |
| **トップ名前空間** | `acme.billing`（`<組織>.<プロダクト>` の 2 階層推奨） |
| **ドメイン名** | `invoice`（最初の component 名になる） |
| **エントリ種別** | Web API / CLI / バッチ / ライブラリ |
| **デプロイ構成** | 単一 uberjar / 複数 uberjar / Docker / Lambda |
| **目的**（1〜2 行） | DESIGN.md §1 の核心 |
| **主要ユースケース**（3〜5 個） | DESIGN.md §3 の材料 |
| **受入基準**（3〜5 個） | DESIGN.md §4 の材料 |

詳細な記入基準・セクション区分（必須 🔴 / 推奨 🟡 / 任意 ⚪）は `DESIGN.md` §0 を参照。

#### 🤖 LLM への指示プロンプト

人間が上記を決めたら、以下のプロンプトで LLM に伝える：

```
プロジェクトの基本情報が決まった。
DESIGN.md §0 本ファイルの埋め方に従って、以下を反映してほしい：
 - DESIGN.md §1〜§4、§8 の必須項目を埋める
 - workspace.edn の :top-namespace を更新
 - README.md 冒頭の <TODO: プロジェクト名> を更新

【プロジェクト名】<例: billing-service>
【トップ名前空間】<例: acme.billing>
【最初のドメイン名】<例: invoice>
【エントリ種別】<例: Web API>
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

DESIGN.md §0.3 の曖昧性点検を実施し、不明点があれば
ONE BY ONE で質問して。編集後は変更点を diff 形式で報告。
```

#### 🧑 人間の最終確認

- LLM が報告した diff を確認
- 埋めた内容が意図通りか、曖昧・矛盾がないかをチェック
- 問題があれば修正指示、OK なら次のステップへ

---

### ステップ 2: 採用 stack の決定と deps.edn の整理

本ステップの**詳細手順・選定論理は `project-guide/STACK_GUIDE.md`** に一次情報源として集約されている。本節は最小のガイドのみ示す。

#### 🧑 人間が決めること

以下を決定：

- **主たる stack**: プロジェクトの主目的に対応するもの（`STACK_GUIDE.md` §4.1 選定基準の表を参照）
  - Web API → web-api stack、GraphQL API → graphql-api stack
  - CLI → cli stack、ライブラリ配布 → library stack
  - バッチ → batch stack、ワーカ → worker stack、データパイプライン → data-pipeline stack
  - bot → bot stack、デスクトップ GUI → desktop stack
- **補助 stack**（必要時）: 複数性格を持つプロジェクト（例: Web API + バッチ併設）
- **dev-tools stack の併用**（強く推奨）
- **推奨構成からの逸脱**（必要時）: STACK_GUIDE.md §4.2 の推奨が合わない場合の変更内容

#### 🤖 LLM への指示プロンプト

```
ステップ 2 の採用 stack を決定したい。
BOOTSTRAP_GUIDE.md §2.1〜§2.3 の手順に従って進めてほしい。

【採用 stack】
- 主 stack: <例: web-api stack>
- 補助 stack: <例: なし / batch stack>
- 横断: <例: dev-tools stack（併用）>

【推奨構成からの逸脱】
<例: なし / mulog を timbre に差し替え（組織方針のため、ADR で記録希望）>

【プロジェクト固有の追加ライブラリ】
<例: PostgreSQL ドライバ / AWS SQS クライアント 等>

以下を順に実施してほしい:
 1. STACK_GUIDE.md §4.2 の該当 stack 推奨ライブラリを確認
 2. 最初の brick 作成後、brick の deps.edn に推奨ライブラリを反映
 3. プロジェクト固有の追加ライブラリも brick の deps.edn に追加
 4. dev-tools stack 採用時はワークスペースルート deps.edn の :dev :extra-deps に追加
 5. development/src/dev/user.clj を採用 stack に合わせて調整
 6. 推奨構成から逸脱する場合は ADR を起案
 7. DESIGN.md §8.3 に採用 stack を記録
 8. 整合性チェック（STACK_GUIDE.md §6、BOOTSTRAP_GUIDE.md §2.9、採用各 stack の §4.2.X 確認事項）を実施
 9. 編集対象ファイルと変更概要を先に提示して承認を求めてから実行

CLAUDE.md §2 禁止事項により、依存変更はすべてユーザ承認後に実施。
```

#### 🧑 人間の最終確認

- LLM が提示した変更プラン（brick deps.edn の記述内容 / 追加ライブラリ）を確認
- 逸脱がある場合、ADR の内容を確認
- CLAUDE.md §2 の「brick deps.edn へのライブラリ追加・変更」は承認必須事項。**明示的に「承認します、進めてください」と伝える**
- 整合性チェック結果を確認

---

### ステップ 3: 最初の brick 作成

#### 🧑 人間が決めること

ステップ 1 で決めた以下を再確認：

- **ドメイン名**（最初の component 名）
- **エントリ名**（最初の base 名、例: `api`, `cli`, `worker`）
- **デプロイ単位名**（最初の project 名、例: `api-server`, `batch-worker`）

#### 🤖 LLM への指示プロンプト

```
最初の brick を作成したい。以下の名前で進めてほしい。

【component 名】<例: invoice>
【base 名】<例: api>
【project 名】<例: api-server>

CLAUDE.md §2 により base と project の作成は承認必須なので、
以下を進めていい：
 1. `clj -M:poly create component name:<component 名>`
 2. `clj -M:poly create base name:<base 名>`
 3. `clj -M:poly create project name:<project 名>`
 4. project-guide/POLYLITH_GUIDE.md §2 のコード例を参照して、
    生成された brick の中身を最小動作版として実装
 5. workspace.edn の `:projects` に新 project を追加
 6. deps.edn の `:dev` `:extra-paths` に新 brick を追加
 7. `clj -M:poly check` と `clj -M:poly test :all` を実行して結果を報告

不明な実装判断があれば、自己解釈で進めず
project-memory/QUESTIONS.md に Q を立てて質問してほしい。
```

#### 🧑 人間の最終確認

- 生成された brick の構造を確認
- `poly check` / `poly test :all` の結果を確認
- LLM が立てた Q があれば回答

---

### ステップ 4: Integrant 設定と動作確認（Integrant 採用時のみ）

**本ステップは Integrant を採用するプロジェクトでのみ実施する**。ライブラリ配布や CLI 単発実行など、I/O ライフサイクル管理を必要としないプロジェクトではスキップし、ステップ 5 に進む。Integrant は Web サービス・バッチ・ワーカ等で I/O リソース（HTTP サーバ・DB 接続・外部 API クライアント等）の起動順序と停止順序を制御する用途で採用する。

#### 🧑 人間が決めること

- 環境別設定が必要な項目（DB 接続情報、ポート番号、外部 API URL 等）
- `:dev` / `:staging` / `:prod` のどのプロファイルを用意するか

#### 🤖 LLM への指示プロンプト

```
Integrant の設定ファイルを作成したい。
以下の環境別設定を含めてほしい：

【ポート】
 - :dev → <例: 3000>
 - :prod → <例: 環境変数 PORT、デフォルト 8080>

【DB 接続】（DB 採用時のみ）
 - :dev → <例: postgresql://localhost:5432/billing_dev>
 - :prod → <例: 環境変数 DATABASE_URL>

【その他の外部 API 連携】
<例: Stripe API キーは環境変数 STRIPE_API_KEY から読む>

以下を進めてほしい：
 1. `projects/<project名>/resources/config.edn` を作成
    （aero の `#profile` / `#env` を使った環境別設定、
     project-guide/POLYLITH_GUIDE.md §2.3 のコード例を参照）
 2. `development/src/dev/user.clj` の `config` 関数を実装
 3. `clj -M:dev:nrepl` で REPL を起動し、`(go)` が完走することを
    確認するコマンドを示してほしい（実行は人間が行う）

実行前に config.edn の内容を提示して承認を求めて。
```

#### 🧑 人間の最終確認

- config.edn の内容確認（特に PII / 機密情報の扱い）
- LLM が提示したコマンドを実行して REPL で `(go)` を確認
- 成功したら次のステップへ、失敗したら LLM に状況を共有

---

### ステップ 5: uberjar ビルド確認（配布形態が uberjar の場合）

#### 🤖 LLM への指示プロンプト

```
uberjar ビルド環境を整えたい。以下を進めてほしい：

 1. `projects/<project名>/build.clj` を
    project-guide/POLYLITH_GUIDE.md §2.3 のコード例を参照して作成
 2. `lib` 名を `<組織>/<プロダクト>` の形式で設定
    （例: `acme/billing-service`）
 3. `cd projects/<project名> && clj -T:build uber` を実行する
    コマンドを示す（実行は人間が行う）
 4. ビルド成功後、生成された jar ファイルの位置と起動コマンド例を
    報告してほしい
```

#### 🧑 人間の最終確認

- LLM が示したビルドコマンドを実行
- jar 生成を確認
- 必要に応じて起動テストを実施

---

### ステップ 6: CI 設定

#### 🧑 人間が決めること

- CI プラットフォーム（GitHub Actions / GitLab CI / CircleCI 等）

#### 🤖 LLM への指示プロンプト

```
CI 設定を作成したい。プラットフォームは <例: GitHub Actions>。

以下を含む CI ワークフローを作成してほしい：
 - `actions/checkout@v4` で `fetch-depth: 0` 指定（poly 差分判定のため必須）
 - `clj -M:lint` が通ること
 - `clj -M:format check` が通ること
 - `clj -M:poly check` が通ること
 - `clj -M:poly test :all` が通ること
 - `cd projects/<project名> && clj -T:build uber` が成功すること
 - 全通過時に `stable-<timestamp>` タグを自動付与

ファイルを作成したら、変更点を報告してほしい。
```

#### 🧑 人間の最終確認

- CI 設定ファイルをレビュー
- コミット・プッシュして実際の CI 実行を確認
- 通過したら初回の `stable` タグが付与されることを確認

---

### ステップ 7: ブートストラップ完了処理

#### 🤖 LLM への指示プロンプト

```
ブートストラップが完了したので、仕上げの作業を進めたい。
project-guide/BOOTSTRAP_GUIDE.md §4 に従って以下を実施してほしい：

 1. `project-guide/BOOTSTRAP_GUIDE.md` を
    `project-guide/archived/BOOTSTRAP_GUIDE.md` に移動
 2. `CLAUDE.md` の文書参照表から BOOTSTRAP_GUIDE.md の行を削除
    （または「完了済み、archived に移動」と追記）
 3. **`README.md` をプロダクト向け README として完全に書き換える**：
    - DESIGN.md §1 目的と §3 主要ユースケースをベースに
    - 外部利用者向けの説明・ビルド手順・API 概要等を含める
    - テンプレート配布時の内容は全て削除
 4. `project-memory/QUESTIONS.md` に残っている open Q を点検して報告
 5. 重要な設計判断（例: Polylith 採用、技術スタック選定）を
    `project-memory/adr/NNNN-topic.md` として ADR 発行を提案
 6. 「Complete project bootstrap」というコミットメッセージで
    コミットするコマンドを示す（実行は人間が行う）

README.md の書き換え内容は、実行前に提示して承認を求めてほしい。
ADR 発行候補も先に一覧で提示して、どれを発行するかの指示を待って。
```

#### 🧑 人間の最終確認

- 新しい README.md の内容をレビュー・承認
- ADR 発行候補を選定（CLAUDE.md §11.1 に照らしてどれが ADR 相当か判断）
- コミットを実行
- **ブートストラップ完了**

---

## ファイル構成

```
<project-root>/
├── README.md                    ← 本ファイル（書き換え対象）
├── CLAUDE.md                    ← LLM 向け作業規約（毎セッション必読）
├── DESIGN.md                    ← プロダクト仕様（ブートストラップ時に埋める）
│
├── project-guide/               ← プロジェクト運営ガイド
│   ├── CODING_GUIDE.md          Clojure 書き方詳細
│   ├── POLYLITH_GUIDE.md        Polylith 運用・brick コード例
│   ├── STACK_GUIDE.md           技術スタック選定カタログ（判断結果のメモリー）
│   ├── COLLABORATION_GUIDE.md   LLM と人間の協働プロトコル
│   ├── BOOTSTRAP_GUIDE.md       初期化手順詳細（LLM 向け、完了後 archived/ へ）
│   ├── MAINTAINERS_GUIDE.md     テンプレート自体の保守・設計原則
│   └── archived/                完了した手順書の保管先
│
├── project-memory/              ← プロジェクトの記憶（実装中に蓄積）
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
| 初期化手順の詳細 | `project-guide/BOOTSTRAP_GUIDE.md` |
| 技術スタック選定・stack の判断 | `project-guide/STACK_GUIDE.md` |
| Clojure の書き方で迷った | `project-guide/CODING_GUIDE.md` |
| Polylith 構造判断・brick 追加 | `project-guide/POLYLITH_GUIDE.md` |
| LLM と人間の協働方針で迷った | `project-guide/COLLABORATION_GUIDE.md` |
| テンプレート自体の改修 | `project-guide/MAINTAINERS_GUIDE.md` |
| 判断に迷った時（Q を立てる） | `project-memory/QUESTIONS.md` |
| 契約・不変条件の記録 | `project-memory/KNOWLEDGE.md` |
| 重要な設計判断の記録 | `project-memory/adr/README.md`（運用ルール） |

## 設計の基底思想（要約）

- **疲労最小化**: LLM の誤りを構造的に封じる（全域性・不変性・副作用の隔離）
- **機械化**: 規約を人間の注意力ではなくツール（clj-kondo、cljfmt、Polylith の `poly check`、Malli instrumentation）で強制
- **stack 方式の技術スタック**: 必須層（Clojure、tools.deps、Polylith、Malli、clj-kondo、cljfmt）はワークスペースルートで常に採用、目的別の stack 層は各 brick の deps.edn に配置。推奨カタログは `project-guide/STACK_GUIDE.md`（真実の一箇所化）
- **4 種の文書分離**: 仕様（DESIGN）/ 知識（KNOWLEDGE）/ 決定履歴（ADR）/ 判断保留（QUESTIONS）
- **自己停止プロトコル**: LLM が時間感覚なく詰まった時、ターン数閾値で停止し Q を立てる

詳細は `CLAUDE.md` §1 と `project-guide/MAINTAINERS_GUIDE.md`。

## ライセンス

＜TODO: プロジェクトに応じて設定＞
