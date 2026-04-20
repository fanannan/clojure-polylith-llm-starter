# STACK_GUIDE.md — 技術スタック選定ガイド

本文書は、**プロジェクトで採用する技術スタックの選定論理と実装マッピング**を集約する一次情報源である。
「何の機能をどのライブラリで実現するか」「なぜその選定か」「採用時に deps.edn をどう構成するか」をすべてここで扱う。

## 本文書の位置づけ

| ガイド | 扱う領域 |
|---|---|
| `CODING_GUIDE.md` | Clojure の書き方 |
| `POLYLITH_GUIDE.md` | Polylith の構造運用 |
| `COLLABORATION_GUIDE.md` | LLM と人間の協働 |
| `BOOTSTRAP_GUIDE.md` | プロジェクト初期化の LLM 側手順 |
| `MAINTAINERS_GUIDE.md` | テンプレート保守 |
| **`STACK_GUIDE.md`（本文書）** | **技術スタック選定の論理と実装** |

**技術スタックに関する記述の一次情報源は本文書**。他文書は本文書を参照する（原則 7 文書の自己整合性、原則 9.3 整理優先の姿勢）。

## 読むタイミング

- **ブートストラップ時**（初期 stack 選択）
- **新ライブラリ採用を検討する時**（§7 判定プロセス）
- **stack 構成を変更する時**（Web API だったプロジェクトにバッチ機能を追加する等）
- **技術選定の根拠を確認する時**（なぜ Malli / Integrant / mulog を選んだか）

---

## 1. stack の基本概念と本文書の性格

### 1.1 stack とは何か

**stack** は、**特定の目的に対応する技術構成の一まとまり**を指す。本テンプレートでは**文書上の分類概念**として定義され、実ライブラリ依存は各 brick の deps.edn に書かれる（§1.2 参照）。

- **必須層**: プロジェクト目的に関わらず常に採用されるもの（Clojure、Malli、Polylith、JVM）。**ワークスペースルートの `deps.edn` の `:deps`** で宣言
- **stack 層**: 目的別の推奨構成（Web API stack、batch stack など）。**各 brick (base / component) の `deps.edn`** で宣言
- **横断層**: 任意の stack と組み合わせて使う補助的構成（dev-tools stack など）

**stack は deps.edn のエイリアスではなく、文書的な分類概念**。Polylith の brick 構造と整合するため、実ライブラリ依存は brick の deps.edn に書き、本文書は推奨カタログとして機能する（§1.2 参照）。

**aero の `#profile`（環境プロファイル）とは階層が異なる概念**。stack は「何で作るか」、aero profile は「どの環境で動かすか」。混同しない。

### 1.2 本文書の性格

本文書は**テンプレート設計者の知見を蓄積する中核文書**であり、**判断結果のメモリー**として機能する。以下の性格を持つ：

**判断結果のメモリーとしての機能**  
本文書は、本テンプレートの第一原理（疲労最小化、CLAUDE.md §1）と三基底原則（全域性・不変性・副作用隔離、CLAUDE.md §1.1）に基づいて**予め技術選定を済ませた結果**を記録する。LLM と人間が未知の選定課題に遭遇するたびに第一原理から導出し直すのは、それ自体が疲労を生む。判断結果をメモリーとして保存することで、以後のプロジェクトでは**毎回複雑な判断をする必要がなくなる**。

**利用規律**:
- プロジェクトのゴールが stack の特性と矛盾しないなら、stack 表を信頼して利用してよい（毎回原則から導出し直す必要はない）
- stack 表に未記載の領域（特定 stack に該当しない技術分野）に遭遇した場合、第一原理から最も妥当なライブラリを自律的に導出する。それはテンプレートの欠陥ではなく、**メモリーがまだその領域をカバーしていない**だけのこと。原則からの導出で決まらない固有要件（組織方針、要件優先度等）のみユーザに質疑する
- stack 表の推奨がプロジェクトのゴールと矛盾する場合、原則に照らして判断し、必要なら逸脱（§5.4 ADR 発行）が許される
- stack 表は完全である必要はない（網羅は永久に達成できない性質のもの）。重要なのは記載されている判断が**原則に基づき正しく導出されている**こと

**推奨カタログとしての機能**  
本文書は各 stack（目的別の推奨構成）について「**何の機能をどのライブラリで実現するか**」の推奨を集約する。**実際のライブラリ依存は各 brick の `deps.edn`（Polylith 構造）に書かれ、本文書はそれを決める際の判断基準と推奨リストを提供する**。本文書自体が `deps.edn` を生成するわけではない。

**真実の一箇所化**  
ライブラリ依存の一次情報源は **brick の deps.edn**（Polylith の本番ビルドはここから依存を解決する）。ワークスペースルートの deps.edn には必須層のみ配置し、stack 層の依存は brick に集約する。これにより**二重管理を回避**する。stack 層のライブラリとプロジェクト固有ライブラリ（DB ドライバ等）は、どちらも同じ brick の deps.edn に書かれ、扱いが対称になる。

**継続的な充実の場**  
stack は時間とともに増え、選定根拠は詳細化し、機能領域も拡張される。本文書はその知見を盛る器であり、**テンプレート全体の見直しや新 stack 追加の機会に充実される**。ただし、**網羅を目的化しない**。必要になった領域が原則からの導出を経てユーザ合意に至った時、その判断結果をメモリーに記録する——この順序を守る。ファイルサイズが大きくても問題ない（読むのは開発開始時と保守時のみ）。

**「なぜ」を必ず書く**  
単なるライブラリ一覧ではなく、**選定理由と却下した代替**を書く。これがないと将来の判断（新ライブラリ検討、バージョン更新、stack 追加）の基礎が欠落し、LLM も人間も更新できなくなる。

**主体ごとの扱いの違い**

| 主体 | 扱い |
|---|---|
| **派生プロジェクト（日常運用）** | 参照のみ。更新しない |
| **派生プロジェクト（開始時）** | テンプレート推奨が合わない場合、逸脱を ADR として記録（§5.3） |
| **テンプレート設計者（保守）** | 本文書を継続的に充実させる（MAINTAINERS_GUIDE.md §5.9） |

**更新主体と頻度**  
本文書の更新はテンプレート保守時に発生する。派生プロジェクトの日常運用では発生しない。stack 追加・機能領域追加・ライブラリ変更は MAINTAINERS_GUIDE.md §5.9 の手順に従う。

---

## 2. 技術スタックの階層

### 2.1 必須層（常に採用、stack 非依存）

| 機能 | 採用技術 | バージョン | 採用理由 |
|---|---|---|---|
| 言語 | Clojure | 1.12.0 | 本テンプレートの基盤 |
| ランタイム | JVM | 21 LTS | 長期サポート、パフォーマンス |
| ビルド・依存管理 | tools.deps | deps.edn | Clojure 標準、宣言的 |
| 構造化アーキテクチャ | Polylith | ec92b9b | brick ベースの再利用性 |
| 契約・検証 | Malli | 0.16.4 | 関数契約 `m/=>`、instrumentation |
| Lint | clj-kondo | 2024.11.14 | Clojure 標準、hook 機構 |
| Format | cljfmt | 0.13.0 | Clojure 標準 |
| 依存更新確認 | antq | 2.11.1264 | ライブラリ更新検知 |
| REPL リロード | tools.namespace | 1.5.0 | `(reset)` の基盤 |
| nREPL | nrepl + cider-nrepl + refactor-nrepl | — | エディタ接続 |
| テストランナー | kaocha | 1.91.1392 | 監視モード対応 |

これらは `deps.edn` の `:deps` および必須エイリアス（`:dev`、`:nrepl`、`:test`、`:poly`、`:lint`、`:format`、`:outdated`）で常に有効。

### 2.2 stack 層（目的別の推奨構成、brick deps.edn に反映）

| stack 名 | 目的 | 必要機能 |
|---|---|---|
| **library stack** | ライブラリ配布 | 必須層のみで十分 |
| **cli stack** | CLI ツール | 引数パース、ログ、終了コード管理 |
| **web-api stack** | HTTP API サーバ（REST） | ライフサイクル、HTTP、ルーティング、JSON、検証、ログ |
| **graphql-api stack** | GraphQL API サーバ | ライフサイクル、HTTP、GraphQL スキーマ・解決、検証、ログ |
| **batch stack** | バッチ処理 | ライフサイクル、設定管理、DB、ログ |
| **worker stack** | メッセージワーカ | ライフサイクル、設定管理、DB、ログ、キュークライアント |
| **data-pipeline stack** | データ処理 | ライフサイクル、設定管理、DB、大量データ処理、ログ |
| **bot stack** | チャット bot | ライフサイクル、HTTP クライアント、イベント駆動、ログ |
| **desktop stack** | デスクトップ GUI アプリ（JVM） | ライフサイクル、GUI フレームワーク、ログ |

各 stack で推奨するライブラリは §4.2 参照。採用時は該当 stack の推奨ライブラリを **brick の deps.edn** に記述する（ワークスペースルートの deps.edn ではない）。

### 2.3 横断層（任意併用、brick deps.edn に反映）

| stack 名 | 目的 | 内容 |
|---|---|---|
| **dev-tools stack** | 開発支援 | Portal、test.check、matcher-combinators（Integrant を含む stack 採用時は integrant/repl も） |

stack 層と組み合わせて使う。開発支援ライブラリは通常 **development project のルート `deps.edn` の `:dev` エイリアスの `:extra-deps`** に追加される（本番ビルドに混入させない）。

---

## 3. 機能別の選定根拠

本節は**このテンプレートで採用している技術選定の判断根拠**を機能別に記録する。新規採用・変更時は本節を更新する（対応 ADR も発行）。

### 3.1 ライフサイクル管理

**採用**: Integrant

**検討した代替**:

| 候補 | 却下理由 |
|---|---|
| Component | 関数合成より `defrecord` 依存が強い。Malli との統合がやや冗長 |
| Mount | グローバル状態を生む。純粋性の観点で副作用の隔離が弱い |
| 自作（atom ベース） | テストと再起動のコストが高い、再発明の価値なし |

**採用理由**:
- 純粋な data としてシステムを表現（`ig/init` に渡す map）
- 依存順序が宣言的、`ig/halt!` で確実に逆順停止
- `integrant.repl` で `(go)` `(reset)` `(halt)` が動作し REPL 駆動開発に適する
- Malli instrumentation の起動・停止を他コンポーネントと同列に扱える

**採用 stack**: web-api stack、batch stack、worker stack、data-pipeline stack

### 3.2 設定管理

**採用**: aero

**検討した代替**:

| 候補 | 却下理由 |
|---|---|
| environ | 環境変数のフラット参照中心、構造化設定に弱い |
| cprop | 機能は十分だが aero より宣言性が低い |
| 自作 EDN リーダー | `#env` `#profile` を再実装する価値なし |

**採用理由**:
- `#profile :dev / :staging / :prod` による環境別設定が宣言的
- `#env` で環境変数参照、`#or` でフォールバック
- config.edn を一枚で完結させやすい

**採用 stack**: Integrant と組で使う stack すべて

### 3.3 検証・契約

**採用**: Malli（必須層、stack 非依存）

**検討した代替**:

| 候補 | 却下理由 |
|---|---|
| clojure.spec.alpha | `defn` 外での定義、generator が生 Clojure で冗長。`m/=>` のような関数契約が弱い |
| Plumatic Schema | メンテナンス頻度が低下、Malli が後継 |

**採用理由**:
- `m/=>` による関数契約（引数・返り値の双方向検証）
- instrumentation による境界での自動検証
- Malli スキーマから test.check generator が自動生成（プロパティテストのコストが激減）
- reitit-malli で HTTP レイヤーの検証と統合

### 3.4 HTTP サーバ・ルーティング

**採用**: Ring + Reitit（+ reitit-malli）

**検討した代替**:

| 候補 | 却下理由 |
|---|---|
| Compojure | データ駆動ルーティングではない、Malli 統合が弱い |
| Pedestal | interceptor の学習コストが高い、小〜中規模で過剰 |
| bidi | ルーティング機能は十分だが Malli との統合エコシステムが弱い |

**採用理由**:
- Reitit はルーティングを data（ベクタ）として表現、静的解析しやすい
- reitit-malli により HTTP 層で契約を強制できる
- middleware / interceptor の両対応

**採用 stack**: web-api stack

### 3.5 JSON

**採用**: jsonista

**検討した代替**:

| 候補 | 却下理由 |
|---|---|
| Cheshire | 高速だが jsonista のほうが Jackson の活用が緻密 |
| data.json | 純 Clojure 実装、パフォーマンス劣位 |

**採用理由**:
- Jackson をベースに最適化、高速
- キーワード化オプション等の設定が柔軟

**採用 stack**: web-api stack（Reitit と連動）

### 3.6 永続化

**採用**: next.jdbc + HoneySQL + HikariCP

**検討した代替**:

| 候補 | 却下理由 |
|---|---|
| clojure.java.jdbc | next.jdbc の前身、非推奨 |
| ORM 系（Korma 等） | 関数型と相性が悪い、ブラックボックス化 |
| hugsql | SQL ファイル分離は魅力だが、静的解析・Malli 統合が弱い |

**採用理由**:
- next.jdbc: 純関数的 API、パフォーマンス良好
- HoneySQL: SQL を data として構築（Reitit と同様の data 駆動思想）
- HikariCP: JVM 界のデファクト接続プール

**採用 stack**: batch stack、worker stack、data-pipeline stack、および web-api stack（DB を扱う場合）

### 3.7 構造化ロギング

**採用**: mulog

**検討した代替**:

| 候補 | 却下理由 |
|---|---|
| timbre | 構造化ログの表現力が mulog より弱い |
| clojure.tools.logging + Logback | 構造化ログに追加実装が必要 |

**採用理由**:
- イベント駆動の構造化ログ（`mulog/log ::event :key value`）
- publisher プラグインで出力先を柔軟に切替（コンソール / JSON / ELK / CloudWatch 等）
- context 継承が自動

**採用 stack**: Integrant を採用する stack すべて（web-api stack、batch stack 等）

### 3.8 テスト・検証支援

**採用**: kaocha（必須層） + test.check + matcher-combinators（dev-tools stack）

**採用理由**:
- kaocha: 監視モード、プラグインエコシステム
- test.check: Malli generator と組で使うプロパティテスト
- matcher-combinators: アサーション表現力（部分マッチング等）

### 3.9 開発時データインスペクション

**採用**: Portal（dev-tools stack）

**採用理由**:
- `tap>` の出力先として豊富な表示
- `println` デバッグの代替
- 開発時のみ、プロダクション依存なし

### 3.10 CLI 引数パース

**採用**: tools.cli（cli stack）

**採用理由**:
- Clojure 標準、Malli との統合は各プロジェクトで薄く書く
- 軽量、依存ゼロ

### 3.11 HTTP クライアント

**採用候補**: hato（必要時、stack に追加）

**採用理由**:
- JVM 11+ の java.net.http をラップ
- Ring 風の data 駆動 API

**位置づけ**: 現時点では必須 stack に含めない。外部 API 呼び出しが必要になった時点で個別プロジェクトで採用判断。

### 3.12 認証・認可

**採用**: buddy-sign + buddy-hashers（認証）、権限判定は自作 middleware + Malli で契約化

**検討した代替**:

| 候補 | 却下理由 |
|---|---|
| friend | メンテナンス停滞、Ring 1.11 以降との整合性に懸念 |
| ring-oauth2 | OAuth2 クライアントとしては有効だが、サーバ側認証の全体像を与えない |
| Keycloak + adapter | 重量級、小〜中規模プロジェクトで過剰 |

**採用理由**:
- buddy-sign: JWT 発行・検証。data 駆動で Malli と整合
- buddy-hashers: パスワードハッシュ。bcrypt / argon2 対応
- 認可（権限判定）は Malli スキーマで資格情報を契約化し、middleware で強制
- OAuth2 / OIDC が必要な場合は個別判断（buddy-auth + ring-oauth2 等）

**採用 stack**: web-api stack、graphql-api stack（ユーザ認証を扱う場合の typical 追加）

### 3.13 キャッシュ

**採用候補**: core.cache（メモリ内）、carmine（Redis 連携）

**検討した代替**:

| 候補 | 却下理由 |
|---|---|
| Caffeine + java interop | 高性能だが、core.cache の上位互換を自作する価値が薄い |
| Memcached クライアント | Redis のほうが機能的に優位、二重採用の理由が薄い |

**採用理由**:
- core.cache: 純 Clojure、LRU / TTL / LU 等の戦略が宣言的
- carmine: Redis 連携の標準的ライブラリ、Lua スクリプト対応
- 選択基準: プロセス内で閉じるなら core.cache、プロセス跨ぎや永続化が必要なら carmine

**採用 stack**: プロファイル横断。必要性が生じた時点で該当 stack（典型的には web-api stack / worker stack）に追加

**注意**: Malli instrumentation と組み合わせる時、キャッシュヒット時の契約検証をスキップするか判断が必要（KNOWLEDGE.md に運用規約を書く）

### 3.14 メトリクス・監視

**採用**: mulog の publisher（Prometheus / CloudWatch 等）+ Micrometer（必要時）

**検討した代替**:

| 候補 | 却下理由 |
|---|---|
| metrics-clojure（iapetos 等） | 独立した metrics レイヤは mulog のイベント駆動と重複 |
| OpenTelemetry agent（自動計装） | 自動計装は便利だが、構造化ログとメトリクスが分離し一貫性が下がる |

**採用理由**:
- mulog のイベントを**メトリクスと構造化ログの共通源**にできる
- mulog publisher で Prometheus / CloudWatch / ELK 等へ同一イベントを配信
- JVM メトリクス（GC、heap 等）は Micrometer を補助採用

**採用 stack**: Integrant を伴う stack すべて（web-api stack / graphql-api stack / batch stack / worker stack / data-pipeline stack / bot stack）

**ノウハウ**: `mulog/log` のイベント名（`::http-request`、`::job-executed` 等）を**プロジェクト全体で統一する規約**を KNOWLEDGE.md §アーキテクチャ上の約束に記録することが重要。命名が分散すると集計がバラバラになる

### 3.15 スケジューリング

**採用**: chime

**検討した代替**:

| 候補 | 却下理由 |
|---|---|
| at-at | シンプルだが停止制御が弱い、Integrant と統合しにくい |
| Quartz（java interop） | 重量級、設定が冗長 |
| tea-time | メンテナンス停滞 |

**採用理由**:
- chime: `core.async` ベースで軽量
- cron 式ではなく Clojure の `java.time` で schedule を data として表現
- Integrant component として起動・停止を制御可能

**採用 stack**: batch stack、worker stack、data-pipeline stack（定期実行を含む場合）

**ノウハウ**: バッチ処理のスケジュールは**永続層との排他制御**（同時実行禁止、advisory lock 等）とセットで設計する。chime 自体は排他制御しない

---

## 4. stack 定義

### 4.1 stack 選定基準

プロジェクトの**エントリ種別**（main の性格）で選ぶ：

| プロジェクトの性格 | 推奨 stack |
|---|---|
| 他プロジェクトから依存される配布物 | **library stack** |
| コマンドライン実行ツール | **cli stack** |
| HTTP API サーバ（REST） | **web-api stack** |
| GraphQL API サーバ | **graphql-api stack** |
| 定期実行または手動起動のバッチ処理 | **batch stack** |
| メッセージキューからタスクを取得して処理 | **worker stack** |
| 大量データの ETL・変換処理 | **data-pipeline stack** |
| チャットボット（Telegram / Slack / Discord 等） | **bot stack** |
| デスクトップ GUI アプリ（JVM ネイティブ） | **desktop stack** |

**複数の性格を持つプロジェクト**（例: Web API + バッチ併設）は**複数 stack の併用**で対応する。採用 stack は DESIGN.md §8.3 に記録し、各 brick の deps.edn にそれぞれの推奨ライブラリ（§4.2）を反映する。brick の構造は Polylith の通常通り（components / bases / projects）。

### 4.2 各 stack の詳細定義

以下は**各 stack の推奨カタログ**。採用時は該当 stack の推奨ライブラリを **brick の deps.edn** に記述する（ワークスペースルートの deps.edn には書かない）。**一次情報源は brick の deps.edn**、本節はそれを書く際の判断基準と推奨を提供する。

各 stack は以下の統一フォーマットで記述する：

- **目的**: stack の主たる目的
- **必要機能**: 揃えるべき機能カテゴリ
- **推奨ライブラリ**: 機能と具体ライブラリの対応表（brick deps.edn に書く候補）
- **選定ポイント**: 選定上の留意点、ノウハウ
- **避けるべきライブラリ**: 当該 stack で特に避けるべきもの（詳細は §8 参照）
- **採用時の確認事項**: brick 作成時・動作確認時にチェックすべき項目（推奨の強制ではなく、機能カテゴリ充足性と設定漏れの防止）

#### 4.2.1 library stack

**目的**: 他プロジェクトから依存されるライブラリ配布。

**必要機能**: 追加なし（必須層のみ）。

**推奨ライブラリ**: 追加なし。

**選定ポイント**:
- ライブラリはユーザに依存を強要しないのが作法。Integrant / aero / mulog 等は含めない
- Malli スキーマをライブラリ利用者に公開する場合、`:registry` の公開 API を明示
- Polylith を利用していても、ライブラリ配布時は**単一 project** から 1 つの uberjar または jar を作る

**避けるべきライブラリ**:
- ライフサイクル管理の埋め込み（Integrant / Component / Mount 等）— ライブラリ側でライフサイクルを強制するとユーザの選択肢を奪う
- ロギング実装の強制（mulog / timbre / logback の直接依存）— ロギングは `clojure.tools.logging` 等の facade で抽象化し、実装はユーザに委ねる
- 詳細は §8 参照

**採用時の確認事項**（brick 作成時・動作確認時に確認）:
- [ ] brick の deps.edn は Malli 以外の不要な依存を持たない（ライブラリ利用者への負担最小化）
- [ ] 公開 API（interface.clj）の Malli スキーマが `:registry` で公開されている
- [ ] ライフサイクル管理を埋め込んでいない（ユーザに判断を委ねる）
- [ ] README または docstring で利用者向けのインポート方法が示されている

#### 4.2.2 cli stack

**目的**: コマンドライン実行ツール。

**必要機能**: 引数パース、ログ、終了コード管理、シグナルハンドリング(任意)。

**推奨ライブラリ**:

| 機能 | ライブラリ | バージョン目安 |
|---|---|---|
| 引数パース | `org.clojure/tools.cli` | 1.1.230 |
| 構造化ログ | `com.brunobonacci/mulog` | 0.9.0 |
| ライフサイクル（任意） | `integrant/integrant` | 0.13.1 |

**選定ポイント**:
- 小さな CLI なら Integrant 不要、`-main` の直列処理で十分
- ファイル I/O や DB を扱う場合、シグナルハンドリング(Ctrl+C)での正しいクリーンアップが必要 → Integrant 採用を推奨
- 終了コードは `System/exit` で明示。非ゼロ終了の慣習(0=成功、1=一般的失敗、2=使用方法エラー、64〜78=sysexits)を守る

**避けるべきライブラリ**:
- `docopt` 系の Clojure port — メンテナンス活動が低く、`tools.cli` で十分
- ロギング用の `timbre` — `mulog` の構造化ログに統一（§8.2）
- 詳細は §8 参照

**採用時の確認事項**（brick 作成時・動作確認時に確認）:
- [ ] 引数パースライブラリが base の deps.edn にある（tools.cli 等）
- [ ] 構造化ログライブラリがある（mulog 等）
- [ ] `-main` が終了コードを明示的に返す（`System/exit` または return 値による制御）
- [ ] ファイル I/O / DB / 外部 API を扱う場合、Integrant でリソース管理されている
- [ ] 処理中断時（Ctrl+C）のクリーンアップが設定されている（Integrant 採用時は shutdown hook）

#### 4.2.3 web-api stack

**目的**: HTTP API サーバ（REST）。

**必要機能**: ライフサイクル、HTTP サーバ、ルーティング、JSON 変換、検証、構造化ログ。

**推奨ライブラリ**:

| 機能 | ライブラリ | バージョン目安 |
|---|---|---|
| ライフサイクル | `integrant/integrant` | 0.13.1 |
| ライフサイクル REPL | `integrant/repl`(dev エイリアスへ) | 0.4.0 |
| 設定管理 | `aero/aero` | 1.1.6 |
| HTTP コア | `ring/ring-core` | 1.13.0 |
| HTTP サーバ | `ring/ring-jetty-adapter` | 1.13.0 |
| ルーティング | `metosin/reitit` | 0.7.2 |
| ルーティング (ring) | `metosin/reitit-ring` | 0.7.2 |
| ルーティング (malli) | `metosin/reitit-malli` | 0.7.2 |
| JSON | `metosin/jsonista` | 0.3.11 |
| 構造化ログ | `com.brunobonacci/mulog` | 0.9.0 |
| ログ JSON 出力 | `com.brunobonacci/mulog-json` | 0.9.0 |

**選定ポイント**:
- **HTTP サーバ実装**: Jetty(標準・成熟)が初期推奨。高同時接続性能が重要なら http-kit(NIO、軽量)または aleph(Netty)を検討。プロファイル要件で判断
- **middleware の順序**: Reitit の data 駆動ルーティングで middleware を list として明示。順序依存のバグを契約で防ぐ
- **認証・認可**: §3.12 参照。JWT なら buddy-sign 追加
- **エラーハンドリング**: Reitit の `exception` middleware で例外 → HTTP エラーレスポンスへの変換を中央集権化
- **CORS**: 必要に応じて `ring-cors` または reitit の middleware を追加
- **圧縮**: `ring.middleware.gzip` または Jetty 側で有効化

**避けるべきライブラリ**:
- ルーティング: **Compojure**（data 駆動でなく Malli 統合が弱い、§8.2）、**Pedestal**（小〜中規模で過剰、§8.2）
- JSON: **data.json**（パフォーマンス劣位、§8.2）
- ロギング: **timbre / log4j 1.x**（log4j 1.x は §8.1 禁止）
- 認証: **friend**（メンテ停滞、§8.2）

**採用時の確認事項**（brick 作成時・動作確認時に確認）:
- [ ] HTTP サーバ実装が base の deps.edn にある（ring-jetty-adapter / http-kit / aleph のいずれか）
- [ ] ルーティングライブラリがある（reitit 等）
- [ ] JSON 処理ライブラリがある（jsonista 等）
- [ ] ライフサイクル管理がある（integrant 等）
- [ ] 構造化ログライブラリがある（mulog 等、publisher 設定含む）
- [ ] `projects/<deploy>/resources/config.edn` が作成され、aero の `#profile` / `#env` で環境別設定されている
- [ ] `development/src/dev/user.clj` の Integrant ライフサイクルセクションが有効化されている
- [ ] エラーハンドリング middleware が設定されている（例外 → HTTP エラーレスポンス変換）
- [ ] 必要に応じて認証（buddy-sign 等）、CORS、レートリミットの設定がある
- [ ] **DB を使う場合**: §3.6 永続化の推奨（next.jdbc + HoneySQL + HikariCP + DB ドライバ）が base の deps.edn にある、Integrant key で接続プール管理、config.edn に DB 接続情報（aero `#env`）

#### 4.2.4 graphql-api stack

**目的**: GraphQL API サーバ。

**必要機能**: ライフサイクル、HTTP サーバ、GraphQL スキーマ定義・解決、検証、構造化ログ。

**推奨ライブラリ**:

| 機能 | ライブラリ | バージョン目安 |
|---|---|---|
| ライフサイクル | `integrant/integrant` | 0.13.1 |
| ライフサイクル REPL | `integrant/repl` | 0.4.0 |
| 設定管理 | `aero/aero` | 1.1.6 |
| HTTP コア | `ring/ring-core` | 1.13.0 |
| HTTP サーバ | `ring/ring-jetty-adapter` | 1.13.0 |
| GraphQL 実装 | `com.walmartlabs/lacinia` | 1.2.2 |
| GraphQL-Ring 統合 | `com.walmartlabs/lacinia-pedestal` または自作 middleware | 1.3 |
| JSON | `metosin/jsonista` | 0.3.11 |
| 構造化ログ | `com.brunobonacci/mulog` | 0.9.0 |

**選定ポイント**:
- **Lacinia 採用理由**: Clojure 界のデファクト、スキーマを EDN として宣言、Malli との親和性（変換レイヤーを挟む）
- **N+1 問題対策**: Lacinia の `superlifter` または自作 batching で対応
- **REST との併用**: web-api stack と graphql-api stack の併用可能(同一サーバで両エンドポイント提供)
- **スキーマと Malli の関係**: GraphQL スキーマを source of truth とするか、Malli スキーマを source of truth として GraphQL を生成するかはプロジェクト判断。Malli → GraphQL の自動生成ツールは成熟途上

**避けるべきライブラリ**:
- GraphQL クライアント実装をサーバと同一プロジェクトに入れる構成 — サーバ・クライアントで関心分離を崩す
- 古い `graphql-java` 直接利用 — Lacinia の data 駆動抽象を活かせない
- §4.2.3 web-api stack の避けるべきリストも該当（Compojure、timbre 等）

**採用時の確認事項**（brick 作成時・動作確認時に確認）:
- [ ] GraphQL 実装ライブラリがある（lacinia 等）
- [ ] HTTP サーバ・Ring 統合がある（ring-jetty-adapter + GraphQL エンドポイント handler）
- [ ] ライフサイクル管理・設定管理・JSON・構造化ログが揃っている（web-api stack §4.2.3 と同様）
- [ ] GraphQL スキーマが定義され、resolver が対応している
- [ ] N+1 問題対策が考慮されている（superlifter / batching / DataLoader 相当）
- [ ] エラーハンドリングで Lacinia の例外を GraphQL errors に変換する設定がある

#### 4.2.5 batch stack

**目的**: 定期実行または手動起動のバッチ処理。

**必要機能**: ライフサイクル、設定管理、DB、構造化ログ、スケジューリング(任意)。

**推奨ライブラリ**:

| 機能 | ライブラリ | バージョン目安 |
|---|---|---|
| ライフサイクル | `integrant/integrant` | 0.13.1 |
| ライフサイクル REPL | `integrant/repl` | 0.4.0 |
| 設定管理 | `aero/aero` | 1.1.6 |
| DB | `com.github.seancorfield/next.jdbc` | 1.3.967 |
| SQL ビルダ | `com.github.seancorfield/honeysql` | 2.6.1230 |
| DB 接続プール | `com.zaxxer/HikariCP` | 6.2.1 |
| DB ドライバ | 利用 DB に応じて追加(例: `org.postgresql/postgresql` 42.7.4) | — |
| 構造化ログ | `com.brunobonacci/mulog` | 0.9.0 |
| スケジューリング（定期実行時） | `jarohen/chime` | 0.3.3 |

**選定ポイント**:
- **冪等性**: バッチは再実行安全（冪等）に設計。途中失敗からのリトライを前提
- **排他制御**: 複数インスタンスが同時起動する可能性がある場合、DB advisory lock / Redis SETNX 等で排他
- **進捗記録**: 中断と再開に備え、処理済みの position を DB に保存する設計
- **スケジューリング**: chime(§3.15 参照)。cron 実行は OS 側 / Kubernetes CronJob で管理する構成も妥当

**避けるべきライブラリ**:
- DB: **clojure.java.jdbc**（next.jdbc に移行、§8.2）
- スケジューリング: **at-at / tea-time**（Integrant 統合が弱い、メンテ停滞、§8.2）
- ロギング: **timbre**（§8.2）

**採用時の確認事項**（brick 作成時・動作確認時に確認）:
- [ ] ライフサイクル管理がある（integrant 等）
- [ ] 設定管理がある（aero 等）
- [ ] DB クライアント・SQL ビルダ・接続プールが揃っている（next.jdbc + honeysql + HikariCP 等）
- [ ] DB ドライバが brick の deps.edn に追加されている（PostgreSQL / MySQL 等、利用 DB 固有）
- [ ] 構造化ログライブラリがある（mulog 等）
- [ ] 定期実行が必要な場合、スケジューリング（chime 等）または外部（cron / CronJob）が設定されている
- [ ] 冪等性設計がされている（再実行安全）
- [ ] 複数インスタンス起動時の排他制御が設計されている（DB advisory lock 等）
- [ ] 進捗記録・中断復帰の仕組みが設計されている

#### 4.2.6 worker stack

**目的**: メッセージキューからタスクを取得して処理するワーカ。

**必要機能**: batch stack 全機能 + メッセージキュークライアント。

**推奨ライブラリ**:

batch stack の全要素 +

| 機能 | ライブラリ | 備考 |
|---|---|---|
| メッセージキュークライアント | プロジェクト依存 | 下記選定ポイント参照 |

**キュー別の推奨クライアント**:

| キュー | ライブラリ | 備考 |
|---|---|---|
| AWS SQS | `com.cognitect.aws/api` + `com.cognitect.aws/sqs` | cognitect-labs の純 Clojure |
| Kafka | `fundingcircle/jackdaw` | Confluent Platform 連携可 |
| RabbitMQ | `com.novemberain/langohr` | AMQP |
| Redis Stream / Pub-Sub | `com.taoensso/carmine` | §3.13 と同ライブラリ |
| PostgreSQL LISTEN/NOTIFY | next.jdbc で直接 | 小規模なら有効 |

**選定ポイント**:
- **Exactly-once vs At-least-once**: 多くのキューは at-least-once、ハンドラ側で冪等性を保証
- **poison message 対策**: DLQ(Dead Letter Queue)設計を最初から組み込む
- **並列度**: キュー特性と DB 接続プールサイズを合わせる
- **バックプレッシャ**: 処理速度がキュー流入を下回る時の挙動(prefetch 制限、スケールアウト条件)を設計

**避けるべきライブラリ**:
- §4.2.5 batch stack の避けるべきリストに加えて：
- キューライブラリの独自ラッパを多層に重ねる構成 — 障害時の挙動が追いにくくなる。各キューの公式推奨クライアントを直接使う

**採用時の確認事項**（brick 作成時・動作確認時に確認）:
- [ ] §4.2.5 batch stack の確認事項（ライフサイクル・設定管理・DB・ログ）をすべて満たす
- [ ] メッセージキュークライアントが brick の deps.edn に追加されている（AWS SQS / Kafka / RabbitMQ / Redis 等、利用キュー固有）
- [ ] ハンドラが冪等性を保証している（at-least-once を前提）
- [ ] DLQ（Dead Letter Queue）の扱いが設計されている
- [ ] 並列度の設定が DB 接続プールサイズと整合している
- [ ] バックプレッシャ対策（prefetch 制限等）が設定されている

#### 4.2.7 data-pipeline stack

**目的**: 大量データの ETL・変換処理。

**必要機能**: batch stack 全機能 + 大量データ処理支援。

**推奨ライブラリ**:

batch stack の全要素 +

| 機能 | ライブラリ | 備考 |
|---|---|---|
| バイナリデータ転送 | `com.cognitect/transit-clj` | 必要時 |
| ストリーム処理 | `org.clojure/core.async` | 非同期パイプライン |
| データフレーム | `tech.ml.dataset` | 表形式データの効率的処理 |
| CSV | `org.clojure/data.csv` | 標準 |
| Parquet / Arrow | `org.apache.arrow/arrow-vector` など | 要なら追加 |

**選定ポイント**:
- **メモリ制約**: 大量データは遅延シーケンス / transducer で逐次処理、全件メモリ展開を避ける
- **並列化**: `pmap` / `core.async` / `claypoole` から要件で選択
- **中間結果**: 大きな変換の途中成果は S3 / ローカルファイルに永続化しリトライ可能に
- **進捗可視化**: mulog でステージ別進捗を記録、外部監視へ連携

**避けるべきライブラリ**:
- §4.2.5 batch stack の避けるべきリストに加えて：
- Spark/Flink の Clojure 薄ラッパ系 — 抽象が中途半端、生 Java interop のほうが保守性が高い
- `incanter`（統計処理を多用する場合以外） — `tech.ml.dataset` のほうが現代的

**採用時の確認事項**（brick 作成時・動作確認時に確認）:
- [ ] §4.2.5 batch stack の確認事項をすべて満たす
- [ ] 非同期パイプライン用ライブラリがある（core.async 等）
- [ ] 大量データ処理の形式に応じたライブラリがある（data.csv / transit-clj / tech.ml.dataset 等）
- [ ] メモリ制約を考慮した設計（遅延シーケンス / transducer / バッチ処理）
- [ ] 中間結果の永続化戦略が決まっている（S3 / ローカルファイル等）
- [ ] ステージ別の進捗記録が mulog で構造化されている

#### 4.2.8 bot stack

**目的**: チャット bot(Telegram / Slack / Discord 等)。

**必要機能**: ライフサイクル、HTTP クライアント(polling / webhook)、イベント駆動、構造化ログ、DB(状態保持時)。

**推奨ライブラリ**:

| 機能 | ライブラリ | バージョン目安 |
|---|---|---|
| ライフサイクル | `integrant/integrant` | 0.13.1 |
| ライフサイクル REPL | `integrant/repl` | 0.4.0 |
| 設定管理 | `aero/aero` | 1.1.6 |
| HTTP クライアント | `hato/hato` | 1.0.0 |
| HTTP サーバ（webhook 受信時） | `ring/ring-jetty-adapter` | 1.13.0 |
| JSON | `metosin/jsonista` | 0.3.11 |
| 構造化ログ | `com.brunobonacci/mulog` | 0.9.0 |
| DB（状態保持時） | `next.jdbc` + `HikariCP` | — |

**プラットフォーム別の推奨**:

| プラットフォーム | ライブラリ | 備考 |
|---|---|---|
| Telegram | 自作 HTTP 呼び出し | Telegram Bot API は HTTP REST、純 Clojure で十分実装可 |
| Slack | 自作 HTTP 呼び出し + Slack Events API | webhook 受信が主 |
| Discord | `suskalo/discljord` | Clojure 向け |

**選定ポイント**:
- **Polling vs Webhook**: 開発時は polling が楽(bot stack 内で完結)、本番は webhook(web-api stack と併用)
- **会話状態**: DB 必須の場合 worker stack の要素を追加
- **レートリミット**: 各プラットフォーム固有のレートリミットに対応(token bucket 等)
- **秘匿情報**: bot token は aero の `#env` で環境変数から読み込み、決してコードに埋め込まない

**避けるべきライブラリ**:
- HTTP クライアント: **clj-http**（新規採用は hato へ、§8.2）
- トークンやシークレットを管理する独自 helper の乱立 — aero の `#env` に統一し、プロジェクト全体で方針を一致させる
- 旧世代の bot フレームワーク系（メンテナンス停滞のもの） — 純 Clojure の HTTP 呼び出しで実装するほうが寿命が長い

**採用時の確認事項**（brick 作成時・動作確認時に確認）:
- [ ] ライフサイクル管理がある（integrant 等）
- [ ] HTTP クライアントがある（hato 等）
- [ ] JSON 処理ライブラリがある（jsonista 等）
- [ ] 構造化ログライブラリがある（mulog 等）
- [ ] bot token 等の秘匿情報が aero `#env` 経由で環境変数から読み込まれている（コード埋め込み禁止）
- [ ] webhook 受信を採用する場合、HTTP サーバライブラリがある（ring-jetty-adapter 等）
- [ ] 会話状態を保持する場合、DB / 接続プール（next.jdbc + HikariCP 等）が設定されている
- [ ] プラットフォーム固有のレートリミット対策（token bucket / retry 等）が実装されている

#### 4.2.9 desktop stack

**目的**: デスクトップ GUI アプリ（JVM ネイティブ、ClojureScript/Web は対象外）。

**必要機能**: ライフサイクル、GUI フレームワーク、構造化ログ。

**推奨ライブラリ**:

| 機能 | ライブラリ | バージョン目安 |
|---|---|---|
| ライフサイクル | `integrant/integrant` | 0.13.1 |
| ライフサイクル REPL | `integrant/repl` | 0.4.0 |
| 設定管理 | `aero/aero` | 1.1.6 |
| GUI フレームワーク | `io.github.humbleui/humbleui` | 0.2.0 |
| 構造化ログ | `com.brunobonacci/mulog` | 0.9.0 |

**代替 GUI フレームワーク**:

| 候補 | 位置づけ |
|---|---|
| cljfx (JavaFX ラッパ) | 成熟、宣言的、リソース消費やや大 |
| seesaw (Swing ラッパ) | 軽量、古典的、モダン UI には弱い |
| membrane | 純 Clojure、クロスプラットフォーム挑戦的 |

**選定ポイント**:
- **humbleui 採用理由（暫定）**: Rich Hickey 系エコシステム、Skia ベースで高性能、宣言的 API
- **成熟度への注意**: humbleui は開発途上、API 変更リスクあり。プロダクション投入は慎重に
- **cljfx への切替可能性**: プロダクション要件で安定性優先なら cljfx 採用を判断、ADR として記録
- **配布**: jlink / jpackage でネイティブイメージ化、GraalVM は GUI 用途では制約多い
- **ClojureScript 版が必要な場合**: 本テンプレート対象外。別途 shadow-cljs ベースのテンプレートを検討

**避けるべきライブラリ**:
- 生の Swing / AWT を `proxy` で多用する構成 — 宣言的でなく、テスト性が低い。cljfx や humbleui で吸収
- メンテナンス停滞した古い Clojure GUI ラッパ — 最新 JVM との非整合リスク

**採用時の確認事項**（brick 作成時・動作確認時に確認）:
- [ ] ライフサイクル管理がある（integrant 等）
- [ ] GUI フレームワークが brick の deps.edn にある（humbleui / cljfx / seesaw のいずれか）
- [ ] 構造化ログライブラリがある（mulog 等）
- [ ] 配布形態が決まっている（jlink / jpackage / uberjar + JRE バンドル）
- [ ] GUI スレッドと業務ロジックスレッドの分離が設計されている（EDT / Skia thread 等の扱い）
- [ ] 採用した GUI フレームワークの成熟度リスクが ADR で評価されている（humbleui 採用時は特に）

#### 4.2.10 dev-tools stack（横断）

**目的**: 開発支援。stack 層と組み合わせて使う。

**必要機能**: データインスペクション、プロパティテスト、アサーション拡張。

**推奨ライブラリ**:

| 機能 | ライブラリ | バージョン目安 |
|---|---|---|
| データインスペクション | `djblue/portal` | 0.58.5 |
| プロパティテスト | `org.clojure/test.check` | 1.1.1 |
| アサーション拡張 | `nubank/matcher-combinators` | 3.9.1 |

**選定ポイント**:
- **推奨**: 全 stack で常に併用（開発効率が大幅に向上）
- **Portal**: `tap>` の出力先として運用。プロダクションには配布しない(:dev エイリアス限定)
- **test.check**: Malli スキーマからの generator 自動生成と組み合わせることで最大効果
- **matcher-combinators**: 部分マッチングで assert の可読性向上

**避けるべきライブラリ**:
- dev 専用の重量 UI（Reveal 等の代替）を複数導入 — Portal に一本化。複数ビューアが競合すると混乱
- プロダクションビルドへの dev 依存の混入 — `:dev` エイリアス限定を徹底、uberjar ビルド時に除外確認（§6 整合性チェック）

**採用時の確認事項**（ブートストラップ時・動作確認時に確認）:
- [ ] ワークスペースルート `deps.edn` の `:dev :extra-deps` に配置されている（brick deps.edn ではない）
- [ ] `development/src/dev/user.clj` の Portal セクションが有効化されている
- [ ] Integrant を含む stack と併用する場合、`integrant/repl` が `:dev :extra-deps` にある
- [ ] uberjar ビルドで dev-tools ライブラリが除外されている（`cd projects/<deploy> && clj -T:build uber` の成果物を確認）
- [ ] test.check generator が Malli スキーマから生成できる（動作確認）

---

### 4.3 複数 stack の組み合わせ

stack は排他的ではなく**タグ的な概念**。複数を組み合わせ可能。Polylith の構造では、組み合わせは以下のように実現する：

- **同一 base が複数 stack の性格を持つ**: 当該 base の deps.edn に、採用する全 stack の推奨ライブラリ（§4.2）をマージして記述
- **異なる base が異なる stack の性格を持つ**: 各 base が独立した deps.edn を持ち、それぞれに該当 stack の推奨ライブラリを記述。同一プロジェクト内でも base 単位で stack が異なって良い
- **プロジェクトとして採用する stack の集合**は DESIGN.md §8.3 採用 stack に記録

**組み合わせ時の重複依存**: 同一 base に複数 stack をマージする場合、重複するライブラリ（例: 両 stack が mulog を要求）は一度だけ書く。tools.deps が base の deps.edn を読んで依存解決するので、base レベルで重複排除する。

---

## 5. ブートストラップでの使い方

### 5.1 stack 選択の手順

`BOOTSTRAP_GUIDE.md` §2.1 プロジェクト想定の決定で、以下を決める：

1. プロジェクトの**主たる性格**（§4.1 の表で選択、例: web-api stack）
2. **補助的に必要な性格**があるか（例: web-api + batch）
3. **dev-tools stack** を併用するか（**推奨: はい**）

決定後、DESIGN.md §8.3 採用 stack 欄に記録する。

**ワークスペースルートの `deps.edn` は変更不要**（必須層のみなので stack 選択とは無関係）。実ライブラリ依存は brick 作成時に brick の deps.edn に書く（§5.2）。

### 5.2 brick deps.edn への推奨ライブラリの反映

最初の brick（component / base）を作成したら、採用 stack の推奨ライブラリ（§4.2 該当項）を **brick の deps.edn** に反映する。LLM はユーザ承認後、CLAUDE.md §2 禁止事項（依存追加）に従って進める：

1. `clj -M:poly create component name:<domain>` でドメイン component を作成
2. `clj -M:poly create base name:<entry>` で entry base を作成（ユーザ承認必須）
3. 作成された **base の deps.edn**（`bases/<entry>/deps.edn`）に、採用 stack の §4.2 該当推奨ライブラリを記述
   - 例: web-api stack なら §4.2.3 の推奨ライブラリ表を反映
   - 複数 stack 採用時は、該当 stack の推奨をマージ（重複は一度だけ書く）
4. component の deps.edn（`components/<domain>/deps.edn`）には、ドメイン純粋性を保つため **I/O 系ライブラリは書かない**（I/O は base 側）
5. プロジェクト固有ライブラリ（DB ドライバ等）も同じ brick deps.edn に追加（stack 推奨ライブラリと扱いは対称）
6. **ワークスペースルート `deps.edn` の `:dev :extra-paths` に brick ソースパスを追加**（`components/<domain>/src` 等）、および `:dev :extra-deps` に **brick を `:local/root` 登録**（`poly/<domain> {:local/root "components/<domain>"}` 等）。後者を忘れると brick deps.edn の依存が REPL で解決されない（`ClassNotFoundException`）
7. **dev-tools stack 採用時**: 開発支援ライブラリはワークスペースルートの `deps.edn` の `:dev :extra-deps` に追加（本番ビルドに混入させない）
8. `development/src/dev/user.clj` で採用 stack に応じた `:require` と実装を有効化
   - Integrant を含む stack 採用 → Integrant REPL セクションを有効化
   - dev-tools stack 採用 → Portal / matcher-combinators セクションを有効化
9. `.clj-kondo/config.edn` で該当 stack の `discouraged-var` があれば有効化
10. `clj -M:lint/init` で新依存の clj-kondo hook を取り込む
11. §6 整合性チェック実施
12. 独立したコミット（例: `Adopt web-api stack (base deps.edn + dev/user.clj)`）

### 5.3 後からの stack 追加・変更

プロジェクト進行中に stack を追加・変更する場合：

1. 追加・変更の理由を **ADR として発行**（`adr/NNNN-add-stack-<n>.md` または `adr/NNNN-modify-stack-<n>.md`）
2. §4.2 の該当推奨に従って、影響する brick の deps.edn を更新
3. §6 整合性チェック
4. DESIGN.md §8.3 の採用 stack 欄を更新

stack から離脱する場合：

1. 離脱理由を **ADR として発行**（`adr/NNNN-remove-stack-<n>.md`）
2. 該当 brick の deps.edn から不要依存を削除（tools.namespace / antq / `poly check` で参照不整合を検出）
3. 関連する brick コード（Integrant key 定義等）を削除または修正
4. §6 整合性チェック
5. DESIGN.md §8.3 の採用 stack 欄を更新

### 5.4 推奨から外れる場合（テンプレート推奨の上書き）

§4.2 の推奨がプロジェクト要件に合わない場合（例: mulog を timbre に差し替え、組織方針で特定ライブラリの採用等）：

1. 変更理由と根拠を **ADR として発行**（`adr/NNNN-stack-customization.md`）
2. brick の deps.edn に**プロジェクト固有の選択**として記述
3. DESIGN.md §8.3 採用 stack 欄に「推奨からの逸脱」を明記
4. プロジェクト固有の運用規約は KNOWLEDGE.md に記載
5. **STACK_GUIDE.md は派生プロジェクトで更新しない**（テンプレート側の変更はメンテナに依頼）

---

## 6. 整合性チェック

stack 採用後、以下をすべて確認する。**一次情報源が brick の deps.edn** なので、チェックは brick 単位を中心とする。

### 6.1 基本チェック（brick 単位）

```bash
# 各 brick が依存解決できること
cd bases/<entry> && clj -Spath > /dev/null && echo ok
cd components/<domain> && clj -Spath > /dev/null && echo ok

# workspace 全体の lint・format・Polylith
clj -M:lint
clj -M:format check
clj -M:poly check
clj -M:poly test

# 統合 REPL（brick を :dev の :extra-paths に登録後）
clj -M:dev:nrepl
```

### 6.2 採用 stack と brick deps.edn の整合（文書的チェック）

**STACK_GUIDE.md §4.2 は強制一致ではなく推奨カタログ**。brick の deps.edn が §4.2 と完全一致している必要はない。ただし以下を確認する：

1. 採用した stack の **§4.2.X 採用時の確認事項**を満たしているか（機能カテゴリの充足、設定ファイルの存在等）
2. 推奨からの逸脱がある場合、ADR が発行されているか（§5.4 参照）
3. DESIGN.md §8.3 採用 stack 欄の記載と brick の実装が整合しているか

### 6.3 dev/user.clj との整合

- 採用 stack が Integrant を含むのに `dev/user.clj` の Integrant REPL セクションが無効化 → 不整合
- dev-tools stack 採用なのに Portal 関連コードが無効化 → 不整合

### 6.4 CI への組み込み

ブートストラップ完了時に、以下を CI 対象にする：

```yaml
# 例: GitHub Actions
- run: clj -M:lint
- run: clj -M:format check
- run: clj -M:poly check
- run: clj -M:poly test :all
- run: |
    # 各 brick の依存解決確認
    for brick in bases/* components/*; do
      (cd "$brick" && clj -Spath > /dev/null) || exit 1
    done
- run: cd projects/<deploy> && clj -T:build uber
```

### 6.5 将来の自動検証（継続充実項目）

brick deps.edn が採用 stack の §4.2.X 採用時の確認事項を満たしているかの機械検証は、**将来的に Babashka タスク等でスクリプト化**される予定（MAINTAINERS_GUIDE.md §5.9 継続充実項目）。現時点では文書上の確認事項として運用する。検証の粒度は「機能カテゴリの充足」であり、「具体ライブラリ名の一致」ではない（推奨の強制ではなく、漏れの防止）。

---

## 7. 新ライブラリ採用の判定プロセス

新規ライブラリ採用は**テンプレートの意思決定**に近いため、慎重なプロセスを経る。

### 7.1 採用検討の契機

- 既存 stack で実現困難な機能が必要になった
- 既存採用ライブラリに重大な問題（メンテナンス停止、脆弱性）
- プロダクト要件で新しい機能領域が発生（例: 初めてメッセージキューを扱う）

### 7.2 評価基準

1. **機能カバー**: 要求を満たすか
2. **Clojure との整合**: data 駆動か、副作用の扱いが純粋関数中心と整合するか
3. **Malli との統合**: 契約・instrumentation と併用できるか
4. **保守性**: 最近 1 年の commit、GitHub stars の傾向、メンテナの応答
5. **ライセンス**: プロジェクト方針と整合
6. **代替との比較**: 少なくとも 2 案を比較、却下理由を明示

### 7.3 プロセス

1. `QUESTIONS.md` に Q として起票（選択肢・利点欠点・推奨を提示、§0.3 フォーマット）
2. ユーザと議論
3. 採用決定後、**ADR として記録**（`adr/NNNN-adopt-XXX.md`、検討した代替・却下理由を含む）
4. **本文書 §3 に追記**（機能別選定根拠、採用理由、却下代替）
5. **本文書 §4.2 の該当 stack 定義を更新**（推奨ライブラリ表への追加）
6. 派生プロジェクトの場合、該当 brick の deps.edn を更新（テンプレート保守の場合、本文書の更新のみで完結）
7. §6 整合性チェック
8. DESIGN.md §8.3 の採用 stack 欄を更新（stack 構成が変わった場合）

---

## 8. 禁止・非推奨ライブラリ

本節は**本テンプレート / 派生プロジェクトで採用すべきでないライブラリ**を記録する。強度によって **§8.1 禁止**（絶対に使わない）と **§8.2 非推奨**（新規採用を避ける）の 2 種に分類する。

§3 の各節の「検討した代替」表とは性格が異なる：

- **§3 検討した代替**: 特定機能で採用候補として比較・却下した（他機能では有用かもしれない中立的却下）
- **§8 禁止・非推奨**: **どこでも**採用すべきでない（理由が明確）

### 8.0 理由タグ

本節の表で使用する理由タグ（標準化）：

| タグ | 意味 |
|---|---|
| **セキュリティ** | CVE 報告あり、脆弱性が未修正または修正版が遅い |
| **メンテ停止** | 1 年以上 commit なし、公式にメンテ終了宣言 |
| **ライセンス** | 配布形態との衝突（GPL/SSPL 等を商用配布する場合の問題） |
| **推奨代替あり** | より優れた選択肢が存在し、本テンプレートで採用済み |
| **設計思想不整合** | 本テンプレートの原則（三基底原則、stack 構造等）と衝突 |
| **条件付き** | 特定状況では可（例: レガシー保守は可、新規不可） |

### 8.1 禁止ライブラリ（絶対に使わない）

セキュリティ脆弱性・ライセンス違反リスク・決定的なメンテナンス停止など、**強い理由により採用を禁じる**ライブラリ。新規採用も既存コードでの継続使用も避ける（継続使用は速やかに代替へ移行）。

| 機能領域 | 禁止ライブラリ | 理由タグ | 詳細・代替 | ADR |
|---|---|---|---|---|
| ロギング | log4j 1.x | セキュリティ + メンテ停止 | CVE-2019-17571 等の未修正脆弱性、公式サポート終了。mulog またはやむを得ず logback へ | — |
| XML 処理 | 古い xerces/xalan（bundled 版） | セキュリティ | XXE 脆弱性。JDK 標準 `javax.xml` または最新の `data.xml` を使用 | — |
| 汎用 JSON | org.json（legacy） | セキュリティ + 推奨代替あり | デシリアライズ脆弱性の歴史、`jsonista` へ | — |

**追加する場合の基準**: CVE 報告 / 公式メンテナンス終了宣言 / ライセンス問題が発覚したもの。**事実駆動**で記録し、憶測では追加しない。

### 8.2 非推奨ライブラリ（新規採用を避ける）

新規採用は避けるが、既存コードで使われている場合は段階的移行で可。**推奨代替への移行**を基本方針とする。

| 機能領域 | 非推奨ライブラリ | 理由タグ | 推奨代替・移行方針 | ADR |
|---|---|---|---|---|
| DB | clojure.java.jdbc | メンテ停止 + 推奨代替あり | next.jdbc へ移行 | — |
| ライフサイクル | Component | 設計思想不整合 | Integrant(本テンプレート採用) | — |
| ライフサイクル | Mount | 設計思想不整合 | グローバル状態を生む。Integrant へ | — |
| 設定管理 | environ | 推奨代替あり | 構造化設定に弱い。aero へ | — |
| 検証 | clojure.spec.alpha | 設計思想不整合 + 推奨代替あり | 関数契約・generator の表現力で Malli に劣る | — |
| HTTP | Compojure | 設計思想不整合 + 条件付き | data 駆動でない。新規は Reitit、レガシー保守は可 | — |
| HTTP | Pedestal | 条件付き | 小〜中規模で過剰。大規模で interceptor 機構が必要なら検討可 | — |
| HTTP クライアント | clj-http（新規） | 推奨代替あり + 条件付き | hato へ。既存コードは段階的移行 | — |
| JSON | data.json | 推奨代替あり | パフォーマンスで jsonista に劣る | — |
| ロギング | timbre | 推奨代替あり | 構造化ログの表現力が mulog より弱い | — |
| 認証 | friend | メンテ停止 + 条件付き | Ring 1.11 以降との整合性に懸念。buddy-sign 等へ | — |
| 認証 | Keycloak + adapter | 条件付き | 重量級、小〜中規模で過剰。エンタープライズ認証なら可 | — |
| キャッシュ | Memcached クライアント | 推奨代替あり | Redis(carmine)が機能的に優位 | — |
| メトリクス | metrics-clojure(iapetos 等) | 設計思想不整合 | mulog のイベント駆動と重複。mulog に一元化 | — |
| メトリクス | OpenTelemetry 自動計装 | 設計思想不整合 | 構造化ログとメトリクスが分離し一貫性が下がる | — |
| スケジューリング | at-at | 設計思想不整合 | 停止制御が弱く Integrant と統合しにくい。chime へ | — |
| スケジューリング | Quartz(java interop) | 条件付き | 重量級、設定が冗長。複雑な業務要件があれば検討可 | — |
| スケジューリング | tea-time | メンテ停止 | chime へ | — |
| ビルド・依存管理 | Leiningen(新規プロジェクト) | 推奨代替あり + 条件付き | tools.deps へ。Lein ベースの既存は段階移行 | — |

**追加する場合の基準**: 推奨代替が明確で、本テンプレートの設計思想と衝突し、採用するより避けたほうが**明白に良い**もの。曖昧な「好みの問題」では追加しない（§9.3 整理優先の姿勢）。

### 8.3 新規追加の手順

本節に禁止・非推奨ライブラリを追加する場合：

1. §8.0 理由タグのうちどれに該当するか明確にする（該当しないなら追加しない）
2. **事実根拠を確認**（禁止の場合は CVE 番号・公式声明等を確認、非推奨の場合は代替の実在を確認）
3. 追加経緯を ADR として発行（`adr/NNNN-deprecate-XXX.md`）、本節の ADR 列に番号記載
4. 既存コードで使用中の場合、移行計画を KNOWLEDGE.md に記載

---

## 9. 関連文書

| 文書 | 本文書との関係 |
|---|---|
| `../CLAUDE.md` §3 技術スタック | 必須層の要約。詳細は本文書 §2.1 |
| `../CLAUDE.md` §2 禁止事項 | 依存追加の承認必須を規定。本文書 §5・§7 はそれに従う |
| `BOOTSTRAP_GUIDE.md` §2 | stack 選択後の具体的ファイル操作手順。本文書 §5 から呼ばれる |
| `COLLABORATION_GUIDE.md` §2.3 | 編集権限マトリクス。stack 追加は人間承認必須 |
| `MAINTAINERS_GUIDE.md` §2 配布物の判断 | stack 層・必須層・横断層の配布形態の根拠 |
| `../DESIGN.md` §8 プロジェクト固有情報 | 採用 stack を記録する欄がある |
| `../project-memory/adr/` | stack 採用・削除時は ADR を発行 |
