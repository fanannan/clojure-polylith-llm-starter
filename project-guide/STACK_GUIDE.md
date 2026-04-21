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

- **必須層**: プロジェクト目的に関わらず常に採用されるもの（Clojure、tools.deps、Polylith、Malli、clj-kondo、cljfmt、JVM）。**ワークスペースルートの `deps.edn` の `:deps`** および必須エイリアスで宣言
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

**本節は必須層・stack 層・横断層の正本**（`_POSSIBLE_ISSUES.md` C-2 による正本化）。`CLAUDE.md §3` と `BOOTSTRAP_GUIDE.md §1` は LLM 日常参照と初期化導線のための概念的抜粋であり、**version 情報は本節のみに記載**する。version drift 防止の保守規律は `MAINTAINERS_GUIDE.md §5.1` を参照。

### 2.1 必須層（常に採用、stack 非依存）

| 機能 | 採用技術 | バージョン | 採用理由 |
|---|---|---|---|
| 言語 | Clojure | 1.12.0 | 本テンプレートの基盤 |
| ランタイム | JVM | 21 LTS | 長期サポート、パフォーマンス |
| ビルド・依存管理 | tools.deps | deps.edn | Clojure 標準、宣言的 |
| 構造化アーキテクチャ | Polylith | ec92b9b | brick ベースの再利用性 |
| 契約・検証 | Malli | 0.16.4 | 関数契約 `m/=>`、instrumentation |
| Lint（構文・型） | clj-kondo | 2024.11.14 | §1.2.1 機械化の実装の柱。`.clj-kondo/config.edn` + custom hook が配布時点で同梱され、LLM の悪手を error で機械的に封じる |
| Lint（スタイル・イディオム） | Splint | 1.19.0 | clj-kondo 補完。`(= 0 x)` → `(zero? x)` のようなイディオム違反を検知（`_POSSIBLE_ISSUES.md` F 拡張で必須層化） |
| Format | cljfmt | 0.13.0 | §1.2.1 機械化の実装。`cljfmt.edn` が配布時点で同梱され、フォーマット議論を排除する |
| 依存脆弱性スキャン | clj-watson | v6.0.1 | NIST NVD + GitHub Advisory Database を照合。時間軸を跨いだ機械化（承認済み依存の脆弱化検知）。release 前必須（`_POSSIBLE_ISSUES.md` F 拡張で必須層化） |
| 依存更新確認 | antq | 2.11.1264 | ライブラリ更新検知 |
| REPL リロード | tools.namespace | 1.5.0 | `(reset)` の基盤 |
| nREPL | nrepl + cider-nrepl + refactor-nrepl | — | エディタ接続 |

配布物として `.clj-kondo/polyguard/hooks.clj`（AST 解析型の custom hook）と `scripts/*.sh`（設定ファイル・ディレクトリ構造の機械的検査）も必須層の一部。役割分担は `MAINTAINERS_GUIDE.md §5.10`。

これらは `deps.edn` の `:deps` および必須エイリアス（`:dev`、`:nrepl`、`:poly`、`:lint`、`:format`、`:outdated`）で常に有効。

テスト実行は Polylith の `poly test`（`clj -M:poly test` / `clj -M:poly test :all`）で行う。Polylith は組み込みの clojure.test ランナーで brick テストを実行し、stable タグからの diff で影響範囲を自動判定する。kaocha 等の追加テストランナーは本テンプレートでは採用しない（詳細は §3.8）。

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
| **saas stack** | SaaS / マルチテナント Web アプリ | Biff + XTDB、bitemporal DB、認証、HTMX、テナント分離 |
| **ml stack** | データサイエンス・機械学習 | dataset 操作、ML pipeline、ノートブック、Python interop |
| **llm-app stack** | LLM / AI 組込アプリ | LLM API 呼出し、embedding、ベクトルストア、プロンプト管理 |
| **edge stack** | IoT / エッジ | GraalVM Native Image、GPIO、MQTT、軽量ログ |

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

**採用**: Polylith の組み込みテストランナー（`poly test`） + clojure.test + test.check + matcher-combinators（dev-tools stack）

**採用理由**:
- `poly test`: Polylith ネイティブ。stable タグからの diff で影響範囲を自動判定し、変更された brick とその依存先のみテストを実行する（§1.2.2 ループ短縮の実装）。内部で clojure.test ランナーを使う
- clojure.test: Clojure 標準、追加依存なし
- test.check: Malli generator と組で使うプロパティテスト
- matcher-combinators: アサーション表現力（部分マッチング等）

**テスト実行コマンド**:
- 日常作業中の変更影響範囲テスト: `clj -M:poly test`
- 完了報告前の全体テスト（§5.5 完了条件）: `clj -M:poly test :all`
- 特定 project 配下のテスト: `clj -M:poly test project:<name>`
- 特定 brick のテスト: `clj -M:poly test brick:<name>`

kaocha 等の追加テストランナーは本テンプレートでは採用しない。派生プロジェクトで追加機能（カバレッジ測定、CI 統合用 JUnit XML 出力等）が必要な場合は、派生プロジェクト側の判断で導入する。

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

### 3.16 WebSocket / Server-Sent Events

**採用**: `ring-websocket`（Ring 1.11+ 組込み、Jetty adapter 経由）。大規模同時接続時は `http-kit`。SSE は Ring の chunked response で自作。

**検討した代替**:

| 候補 | 却下理由 |
|---|---|
| aleph | Netty 基盤で依存重い、data 駆動でない（G6） |
| manifold（aleph 同梱） | promise/stream が純粋関数と相性悪い、core.async で代替可（G3） |
| immutant | 開発停止（G4） |
| sente | 独自プロトコル層で抽象が厚い、ring-websocket で十分（G6） |

**採用理由**:
- ring-websocket は Ring 仕様の一部、既存 middleware と統合容易
- handshake / onmessage / onclose を純粋データ（map）で扱える
- SSE は Ring の chunked response で自作可、外部依存不要

**採用 stack**: web-api stack（拡張）、bot stack（webhook サーバ側）

**適用条件**:
- リアルタイム更新が必要 → ring-websocket（Jetty）
- 10k+ 同時接続 → http-kit（ADR 発行、スレッドモデル違いの記録）
- SSE のみ → Ring chunked + jsonista、追加依存なし

### 3.17 Rate Limiting / Circuit Breaker / リトライ

**採用**:
- **Retry / Circuit Breaker**: `sunng87/diehard`（純 Clojure、data 駆動 policy）
- **Rate Limit (in-process)**: 自作 token-bucket（`atom` + `reduce`、依存ゼロ）
- **Rate Limit (分散)**: `com.taoensso/carmine` + Redis Lua script

**検討した代替**:

| 候補 | 却下理由 |
|---|---|
| resilience4j 直接 interop | Java fluent API、data 駆動でない（G1） |
| failsafe (Java) | 同上 |
| ring-ratelimit | メンテ停滞（G4） |
| robert.bruce | メンテ停止（G4） |

**採用理由**:
- diehard は `{:retry-on ... :max-retries ...}` の map で policy 指定、data 駆動
- Malli スキーマで policy を契約化可能
- 分散 rate limit は Redis で小さく解け、専用ライブラリ不要

**採用 stack**: web-api stack、worker stack、bot stack、llm-app stack（外部 API 呼び出しを伴う全 stack）

**適用条件**:
- 外部 API 呼び出し → diehard 必須（§1.1.1 全域性: 失敗を契約に持ち上げる）
- 同一プロセス内の rate limit → 自作 token-bucket
- 水平スケール時の rate limit → carmine + Redis

### 3.18 CSRF / セッション管理 / CORS

**採用**:
- **CSRF**: `ring/ring-anti-forgery`
- **セッション**: Ring 標準 `ring.middleware.session`（cookie 認証時）。store は Redis（`carmine`）、JWT 認証なら store 不要
- **CORS**: `metosin/reitit-ring` の CORS middleware、または `jumblerg/ring.middleware.cors`

**検討した代替**:

| 候補 | 却下理由 |
|---|---|
| buddy-auth 全体採用 | 認証とsession/CSRF が混在、関心分離の観点で避ける（G7） |
| 自作 CSRF token 管理 | 再発明禁止（G6） |
| in-memory session store（本番） | スケールアウトで session 消失（G3 設計思想） |

**採用理由**:
- Ring 標準 middleware は副作用が明示的で、middleware 順序を data として記述可能（Reitit と統合）
- session store は環境に応じて差替可能な依存注入パターン
- CSRF / CORS は security header 規約化で済む、フレームワーク不要

**採用 stack**: web-api stack、graphql-api stack、saas stack

**適用条件**:
- 公開 Web API → ring-anti-forgery + ring-cors を常に適用
- JWT のみ認証 → session store 不要
- Cookie 認証 → Redis session store（carmine）
- 同一オリジン SPA → CSRF 不要、CORS 緩め可（ADR 記録）

### 3.19 分散トレーシング（手動計装）

**採用**: `mulog` の `with-context` による trace ID / span ID 伝播 + 自作 middleware。外部送信は mulog publisher で Jaeger / Tempo / OpenTelemetry Collector へ。

**検討した代替**:

| 候補 | 却下理由 |
|---|---|
| OpenTelemetry Java Agent（自動計装） | §8.2 既に却下（構造化ログと分離）|
| Micrometer Tracing | Spring 文脈が強く、Clojure との親和性低い（G7） |
| 独自 trace ID 実装 | mulog context で既に可能、再発明禁止（G6） |

**採用理由**:
- mulog `with-context` は map を継承する仕組みで、data 駆動
- 同一イベント源（mulog）から logs / metrics / traces を配信、一貫性保持（§3.14 と整合）
- 手動計装は境界（HTTP request、DB query、外部 API）に限定可能で副作用が明示

**採用 stack**: Integrant を採用する全 stack（web-api / graphql-api / batch / worker / data-pipeline / bot / saas / llm-app）

**適用条件**:
- 単一サービス → correlation ID（request ID）のみ、trace ID 不要
- マイクロサービス → W3C Trace Context ヘッダ（`traceparent`）で trace ID を引き回し
- 本規約は KNOWLEDGE.md §アーキテクチャ上の約束 に明記必須（§3.14 イベント名統一と同格の規約）

### 3.20 i18n / l10n

**採用**: `taoensso/tempura`

**検討した代替**:

| 候補 | 却下理由 |
|---|---|
| tower | 開発停止、tempura が後継（G4） |
| ICU4J 直接 | 重量、tempura で包める範囲では不要（G6） |
| Java ResourceBundle | data 駆動でない、Clojure 慣用外（G1） |

**採用理由**:
- tempura は辞書も data（map）、参照も純粋関数
- Malli スキーマで翻訳キーの型安全化が可能
- Taoensso 作者は安定的にメンテ継続

**採用 stack**: web-api stack（多言語 UI 時）、bot stack（複数言語サポート時）

**適用条件**:
- 単一言語 → 採用不要
- 2 言語以上 → tempura、辞書は `resources/i18n/*.edn` 配置
- 日付・通貨・数値 → `java.time` + JDK 標準 NumberFormat（tempura と直交）

### 3.21 Feature Flag

**採用**: **自作**（EDN フラグ + aero `#profile` + DB 動的フラグの組合せ）

**検討した代替**:

| 候補 | 却下理由 |
|---|---|
| LaunchDarkly Java SDK | コスト、外部サービス依存、クローズドソース（G5/G6） |
| Flagsmith | OSS 代替、自作で足りる規模では過剰（G6） |
| feature-flag（Clojure 純） | メンテ不明（G4） |

**採用理由**:
- フラグは本質的に map。`{:feature/new-checkout true :feature/ab-variant :b}`
- 静的フラグは aero profile、動的フラグは DB 1 テーブル（`feature_flags`）で十分
- Malli スキーマで全フラグを契約化、未知フラグは起動時エラー（§1.1.1 全域性）

**採用 stack**: web-api stack、worker stack、saas stack（リリース制御を伴うもの）

**適用条件**:
- 静的フラグのみ → aero profile、追加依存ゼロ
- 非開発者がフラグを変更 → LaunchDarkly 採用を ADR で検討
- A/B テスト → 自作（user-id ハッシュ→variant）、分析基盤は別
- **規約**: フラグ定義は 1 つの EDN ファイルに集約、Malli スキーマで全網羅

### 3.22 マルチテナント（Biff + XTDB パターン）

**採用方針**: **Biff + XTDB** の組合せを saas stack（§4.2.11）として導入。RDBMS 併用時は tenant-id カラム + row-level isolation パターン。

**検討した代替**:

| 候補 | 却下理由 |
|---|---|
| 独自テナント分離フレームワーク | Biff + XTDB で完結、再発明不要（G6） |
| schema-per-tenant（RDBMS、migratus で切替） | 運用複雑化、row-level で十分なケースが多い（条件付き採用は可） |

**採用理由**:
- Biff は Polylith brick と流儀が異なるが、data 駆動・純 Clojure・副作用隔離で本テンプレート整合
- XTDB の valid-time + tenant-id 属性で自然に multi-tenant 実現
- Datalog クエリに tenant-id 条件を middleware で強制注入可能

**採用 stack**: saas stack（§4.2.11）

**適用条件**:
- SaaS・社内サービス → saas stack（Biff + XTDB）
- 既存 RDBMS 継続 → web-api stack + tenant-id middleware（row-level）
- schema-per-tenant → コンプライアンス要件明確時のみ、ADR 必須

### 3.23 NoSQL 系

**採用**:
- **Key-Value / Cache / Pub-Sub**: `com.taoensso/carmine`（既採用、primary 用途も公式化）
- **ドキュメント型（MongoDB）**: `com.novemberain/monger`
- **AWS DynamoDB**: `com.cognitect.aws/dynamodb`（aws-api 系列）

**検討した代替**:

| 候補 | 却下理由 |
|---|---|
| Cassandra Java driver 直接 | Clojure 慣用ラッパなし、必要時 ADR で個別採用 |
| MongoDB Java Driver 直接 | monger がシンプル data 駆動ラッパ（G6） |
| faraday (DynamoDB) | aws-api に統一（worker stack と整合） |

**採用理由**:
- carmine は EDN-native、Redis コマンドを data で記述
- monger は map で document を表現、Clojure 慣用
- aws-api は全 AWS サービスで同じパターン、学習コスト低

**採用 stack**: web-api / worker / batch / saas（必要性に応じ）

**適用条件**:
- Primary DB として NoSQL → ADR 必須（RDBMS 比較検討）
- キャッシュ・セッション・レートリミット → carmine
- ドキュメント型が明確に有利（スキーマレス + 集計不要）→ monger
- AWS native 環境 → aws-api/dynamodb

### 3.24 XTDB / Datomic / グラフ DB

**採用**: **XTDB**（`com.xtdb/xtdb-api`）を第一選択。ライセンス（MIT/Apache）・Clojure 親和性・bitemporal が揃う。

**検討した代替**:

| 候補 | 却下理由 |
|---|---|
| Datomic Pro | 商用ライセンス、新規採用時のコスト・ロックインリスク（G5）。既存運用プロジェクトは継続可 |
| Datomic Free/Solo | 機能制限あり、本番用途なら XTDB が自然 |
| Neo4j（neo4j-clj） | 複雑グラフ特化用途のみ、一般 Web API では RDBMS/XTDB で足りる（G6） |
| Datahike | XTDB のサブセット的位置づけ、XTDB 優先 |

**採用理由**:
- XTDB は EDN-native、クエリは Datalog（data 駆動）、Malli と整合
- bitemporal で「現在の事実 + 歴史」を扱え、audit log 機能が自然に得られる
- ライセンスが OSS で SaaS 展開にも制約なし

**採用 stack**: saas stack（既定）、web-api stack（RDBMS の代替）、batch stack（イベントソーシング用途）

**適用条件**:
- 新規プロジェクトで RDBMS と迷う → XTDB を第一検討
- bitemporal が要件 → XTDB 一択
- 複雑な JOIN 中心 → RDBMS + next.jdbc 継続
- グラフ深掘り（6 hop 以上等） → Neo4j を ADR で検討

### 3.25 マイグレーション

**採用**: `migratus/migratus`

**検討した代替**:

| 候補 | 却下理由 |
|---|---|
| ragtime | 互角だが、migratus の方が普及、data 駆動度も十分（G6） |
| Flyway（Java） | XML/Java 設定が冗長（G1） |
| Liquibase | 同上、XML DSL |

**採用理由**:
- SQL ファイルをそのまま書ける、学習コスト最小
- up/down 管理、履歴テーブル、Clojure から直接起動可能
- next.jdbc と同じ接続情報を使える

**採用 stack**: next.jdbc を採用する全 stack（batch / worker / data-pipeline / web-api で DB 使用時）

**適用条件**:
- RDBMS 採用 → migratus 必須（CLAUDE.md §2: 生成は LLM 可、実行は人間）
- XTDB → スキーマレスのためマイグレーション不要
- `resources/migrations/` にバージョン付き SQL 配置、命名 `YYYYMMDDHHMMSS-description.{up,down}.sql`

### 3.26 時系列 / EventStore

**採用方針**:
- **時系列データ** → **TimescaleDB**（PostgreSQL 拡張）+ next.jdbc（追加 Clojure 依存ゼロ）
- **メトリクス** → mulog publisher（§3.14 既採用）
- **イベントソーシング** → **XTDB**（bitemporal で代替）

**検討した代替**:

| 候補 | 却下理由 |
|---|---|
| InfluxDB Clojure wrapper | 成熟度低、hato で直接 HTTP 叩けば十分（G6） |
| EventStoreDB (Kurrent) gRPC クライアント | 純 Clojure wrapper なし、XTDB で代替可（G6） |
| 専用時系列 DB 採用 | プロジェクト規模で正当化されるケースは稀（G6） |

**採用理由**:
- 既存永続化層（PostgreSQL）の拡張で済む
- XTDB の bitemporal は EventStore の主要機能を内包

**採用 stack**: batch / worker / data-pipeline（時系列要件時）

**適用条件**:
- メトリクス保存 → Prometheus / CloudWatch（mulog 経由、独自時系列 DB 不要）
- ドメイン時系列（IoT センサ等）→ TimescaleDB
- イベントソーシング → XTDB

### 3.27 E2E テスト

**採用**: `etaoin/etaoin`（WebDriver）

**検討した代替**:

| 候補 | 却下理由 |
|---|---|
| clj-webdriver | メンテ停止、etaoin が後継（G4） |
| Playwright | Node.js / Python 経由、JVM 統合しにくい（G7） |
| Selenium 直接 | 低レベル、etaoin で包める範囲（G6） |

**採用理由**:
- etaoin は 100% Clojure、map で driver 設定、data 駆動
- matcher-combinators と組で assert を書ける

**採用 stack**: dev-tools stack 拡張、必要プロジェクトのみ任意

**適用条件**:
- ブラウザ UI（SSR or SPA）→ etaoin
- API-only → 既存 clojure.test + matcher-combinators で十分、E2E 不要
- CI 実行 → Docker Selenium hub 併用

### 3.28 負荷テスト

**採用**: **Gatling**（Scala DSL、JVM 同居）

**検討した代替**:

| 候補 | 却下理由 |
|---|---|
| k6 | JavaScript、Clojure と別スタック（G7） |
| Apache Bench / wrk | 単純な HTTP 用途のみ、シナリオ書けない |
| JMeter | XML 設定、data 駆動でない（G1） |
| 純 Clojure 負荷テスト | 成熟ライブラリなし（G4） |

**採用理由**:
- Gatling は JVM 内で走る、HTML レポート自動生成
- シナリオが data として書ける（Scala DSL だが構造は map）

**採用 stack**: 負荷要件があるプロジェクトに任意併用（stack 化しない、別 project として追加）

**適用条件**:
- パフォーマンス SLO あり → Gatling
- 単純スループット計測 → wrk 等（外部ツール）
- **必須層には含めない**。DESIGN.md §8 に「パフォーマンス要件」を明記したプロジェクトのみ

### 3.29 契約テスト

**採用方針**: **Pact（pact-jvm）を Java interop で利用**、純 Clojure ラッパは採用しない。

**検討した代替**:

| 候補 | 却下理由 |
|---|---|
| 純 Clojure 契約テストライブラリ | 成熟品なし（G4） |
| 自作 | 契約テストの標準化効果が失われる |

**採用理由**:
- Pact は業界標準、broker と連携可能
- Consumer-Driven Contract を Malli スキーマから生成可能（自作ブリッジ）

**採用 stack**: マイクロサービス構成時のみ（saas stack の兄弟として）

**適用条件**:
- サービス 3 つ以上で契約保証が必要 → pact-jvm、ADR 必須
- 単独サービス → 不要

### 3.30 gRPC / Protocol Buffers

**採用方針**: **積極採用しない**。社内で gRPC 必須の場合のみ `protojure/protojure`。

**検討した代替**:

| 候補 | 却下理由 |
|---|---|
| io.grpc/grpc-java 直接 | OOP 重い、生成コードが data 駆動でない（G1） |
| lambdaisland 系 gRPC | 新興、成熟度不足（G4） |
| HTTP/2 + JSON | ほとんどのユースケースでこちらで十分（G6） |

**採用理由（protojure）**:
- 純 Clojure 風 API、`.proto` 定義から Clojure 関数生成
- ただし抽象の厚みは大きい（採用は慎重）

**採用 stack**: なし（プロジェクト固有採用、ADR 必須）

**適用条件**:
- 社内で gRPC 規約がある → protojure、ADR 発行
- 性能要件のみ → HTTP/2 + http-kit で十分

### 3.31 PDF / Excel / ドキュメント生成

**採用**:
- **PDF (純 Clojure)**: `clj-pdf/clj-pdf`（data 駆動 DSL）
- **HTML → PDF**: `org.xhtmlrenderer/flying-saucer-pdf-openpdf`（複雑レイアウト時）
- **Excel**: `dk.ative/docjure`（Apache POI の data 駆動ラッパ）
- **CSV**: `org.clojure/data.csv`（公式）

**検討した代替**:

| 候補 | 却下理由 |
|---|---|
| iText 直接 | Java fluent API、data 駆動でない。ライセンス（AGPL）注意（G5） |
| Apache POI 直接 | docjure が Clojure 慣用ラッパ（G1） |
| pandoc（外部）| 外部プロセス、本格要件では使うが内部生成には過剰（G7） |

**採用理由**:
- clj-pdf は hiccup 風 data で PDF 構造を記述、Malli で構造契約化可
- docjure はセル編集を map で、副作用は関数末尾に集約可能

**採用 stack**: web-api stack 拡張（帳票機能時）、batch stack（レポート生成時）

**適用条件**:
- シンプル PDF → clj-pdf
- 複雑レイアウト（CSS 必要）→ Flying Saucer + hiccup→HTML
- Excel 出力 → docjure
- Word (docx) → Apache POI 直接 interop（稀、ADR）

### 3.32 Markdown / YAML / TOML / XML

**採用**:
- **Markdown**: `markdown-clj/markdown-clj`（軽量）または `com.vladsch.flexmark/flexmark-all`（高機能）
- **YAML**: `clj-commons/clj-yaml`
- **TOML**: **採用しない**（EDN 推奨）
- **XML**: `org.clojure/data.xml`

**検討した代替**:

| 候補 | 却下理由 |
|---|---|
| endophile (markdown) | 開発停止（G4） |
| xerces / xalan（独立版） | §8.1 既に禁止（XXE 脆弱性） |
| snake-yaml 直接 | clj-yaml がラップ（G6） |

**採用理由**:
- markdown-clj は data 駆動（parsed tree を vector で扱える）
- data.xml は公式、XXE 対策済み
- YAML は aero 経由で扱うので clj-yaml を使う場面は限定的

**適用条件**:
- 設定ファイル → EDN + aero（YAML/TOML 不要）
- Markdown → markdown-clj（軽量） or Flexmark（拡張必要時）
- 外部 XML API 連携 → data.xml

### 3.33 メール / SMS / プッシュ通知

**採用**:
- **SMTP**: `draines/postal`
- **Transactional Email（SendGrid / SES）**: `hato` 直接 + `jsonista`
- **SMS（Twilio 等）**: `hato` 直接
- **FCM（プッシュ通知）**: `hato` 直接 + Firebase Admin Java SDK（認証のみ）

**検討した代替**:

| 候補 | 却下理由 |
|---|---|
| 各サービス専用 Clojure wrapper | 多くはメンテ停滞（G4） |
| Spring Mail 等 Java フレームワーク | 重い、Clojure 慣用外（G7） |

**採用理由**:
- bot stack と同じ思想（HTTP API は hato 直接が最も寿命長い）
- SMTP は java mail ベースの postal が実績十分

**採用 stack**: web-api / worker / batch に通知機能を追加する時

**適用条件**:
- 通知送信 → worker stack に配信ジョブとして隔離（web リクエストスレッドで同期送信しない）
- テンプレート管理 → selmer や tempura で map → text
- 認証情報 → aero `#env` のみ（コード埋込禁止、既定）

### 3.34 ファイルストレージ

**採用**:
- **AWS S3**: `com.cognitect.aws/s3`（aws-api 系列、既採用）
- **GCS**: `com.google.cloud/google-cloud-storage`（Google Java SDK 直接、薄く利用）
- **Azure Blob**: Azure Java SDK 直接（稀）

**検討した代替**:

| 候補 | 却下理由 |
|---|---|
| Amazonica | メンテ停止傾向、aws-api が後継（G4） |
| 自作クラウド抽象レイヤー | 疎結合の名目で抽象が漏れる（G6） |

**採用理由**:
- aws-api の呼出しパターンを流用、学習コスト最小
- SDK 直接でもブリッジ層を Clojure で薄く書ける

**採用 stack**: web-api / worker / batch に S3 要件が出た時

**適用条件**:
- ファイル upload → presigned URL 生成（S3 の場合 aws-api で対応）
- 大容量 → ストリーミング（InputStream）、全メモリ展開禁止
- マルチクラウド抽象 → 採用しない（各クラウドを直接叩く、ADR）

### 3.35 フルテキスト検索

**採用**:
- **PostgreSQL 全文検索**（`tsvector`）→ 既存 next.jdbc で完結、**第一選択**
- **Elasticsearch / OpenSearch**: `mpenet/spandex`（中〜大規模時）

**検討した代替**:

| 候補 | 却下理由 |
|---|---|
| clojurewerkz/elastisch | メンテ停止（G4） |
| Apache Lucene 直接 | 組込み用途で稀に有用だが、通常過剰（G6） |
| Algolia / Meilisearch 専用 Clojure wrapper | ほぼ無い、HTTP 直接で十分 |

**採用理由**:
- PostgreSQL の tsvector は既存 DB で完結、追加インフラ不要
- spandex は data 駆動クエリ（map で ES クエリを記述）

**採用 stack**: web-api stack（検索要件時）

**適用条件**:
- 数万〜数十万件、日本語形態素解析不要 → PostgreSQL 全文検索
- 数百万件以上、複雑スコアリング、日本語形態素 → spandex + OpenSearch
- 組込み（ローカルファイル検索等） → Lucene 直接（ADR）

### 3.36 機械学習・数値計算

**採用**: **scicloj 系**
- `scicloj/tablecloth`（data.frame 風、data 駆動の頂点）
- `scicloj/scicloj.ml`（ML pipeline）
- `scicloj/clay`（ノートブック生成、Portal と共存可）
- `scicloj/noj`（統合パッケージ、複数採用時）

**深層学習（必要時）**:
- **Python 側に委譲**を第一選択。`clj-python/libpython-clj` で PyTorch / TensorFlow を呼び出し、モデル学習は Python、推論境界を Clojure で包む

**検討した代替**:

| 候補 | 却下理由 |
|---|---|
| incanter | メンテ低迷、tablecloth が後継（G4） |
| deeplearning4j Clojure wrapper | OOP 重い、mutation 多用（G1/G2） |
| cortex | 開発停止（G4） |
| PyTorch 直接 JNI | libpython-clj で包める範囲（G6） |

**採用理由**:
- scicloj 系は data 駆動が徹底、dataset は map/vector の集合として扱える
- Malli スキーマで dataset 構造を契約化可能
- libpython-clj は Python モデルを関数としてラップ、境界で副作用を隔離できる

**採用 stack**: ml stack（§4.2.12）または data-pipeline stack 拡張

**適用条件**:
- データ分析・特徴量エンジニアリング → tablecloth
- 古典 ML → scicloj.ml
- 深層学習 → Python（PyTorch）側で学習、Clojure は推論境界のみ
- ノートブック駆動 → clay + Portal
- **純 Clojure で深層学習**→ 射程外、ADR で外部委託判断

### 3.37 LLM / AI API 連携

**採用方針**:
- **プロバイダ API は hato で直接叩く**を第一選択
- ラッパとして `wkok/openai-clojure`（OpenAI / Ollama / Azure OpenAI 互換）採用可
- **ベクトルストア**: **PostgreSQL + pgvector**（追加インフラ不要時）/ pinecone-api（ADR 必須）

**検討した代替**:

| 候補 | 却下理由 |
|---|---|
| langchain4j Clojure 移植 | OOP/継承多用、LLM 界隈は変動早く、薄い HTTP の方が長寿命（G1/G6） |
| llama-clj (native binding) | native 依存、ビルド複雑化（G6） |
| 専用 agent framework（LangGraph 等 Clojure 移植） | 2025 時点で成熟品なし（G4） |

**採用理由**:
- LLM プロバイダ API は REST であり、hato 直接で足りる
- プロンプトは Clojure の map で表現、data 駆動
- Malli で request/response スキーマ契約化が自然
- プロバイダ切替（OpenAI ↔ Anthropic ↔ Ollama）は wrapper 層で吸収

**採用 stack**: llm-app stack（§4.2.13）

**適用条件**:
- 単発 LLM 呼び出し → hato 直接、または `wkok/openai-clojure`
- RAG → pgvector + `wkok/openai-clojure` (embeddings)
- ローカル LLM → Ollama HTTP endpoint + hato
- 複雑 agent → ADR 必須、独自実装、外部ラッパ採用は避ける
- **規約**: プロンプトテンプレートは EDN、運用変更は ADR（プロンプトもコード扱い）

### 3.38 画像・映像・音声処理

**採用**:
- **画像処理（軽）**: JDK 標準 `javax.imageio` + 薄い純 Clojure ヘルパ
- **画像処理（複雑）**: `org.bytedeco/opencv-platform`（OpenCV Java wrapper）
- **SVG 生成**: `rm-hull/dali`（data 駆動、hiccup 風）
- **動画トランスコード**: `com.github.kokorin/jaffree`（FFmpeg shell out wrapper）
- **OCR**: Tesseract（`net.sourceforge.tess4j`）を **shell out で呼び出し**、インプロセス JNI は避ける
- **音声**: JDK 標準 + 必要時 Java ライブラリ（`overtone` は創作用途のみ）

**検討した代替**:

| 候補 | 却下理由 |
|---|---|
| JavaCV 直接 | OpenCV-Clojure 経由が慣用（G7） |
| Imaging 系の古い Clojure wrapper | メンテ停滞（G4） |
| overtone（プロダクション） | 創作用途のみ、副作用が内側に漏れる（G3） |

**採用 stack**: 画像・動画処理を扱うプロジェクトに任意追加（web-api / batch / worker 拡張）

**適用条件**:
- サムネイル生成 → javax.imageio
- コンピュータビジョン → OpenCV
- SVG → dali
- 動画トランスコード → jaffree (FFmpeg shell out)
- OCR → Tesseract shell out（JVM 内の JNI 同居はクラッシュリスク）

### 3.39 IoT / エッジ

**採用**:
- **GraalVM Native Image**（必須、起動時間とメモリ削減）
- **GPIO 制御（Raspberry Pi 等）**: `Pi4J`（Java、純 Clojure ラッパなし）
- **MQTT**: `eclipse/paho.mqtt.java`（Java 直接、data 駆動ラッパ薄く）

**検討した代替**:

| 候補 | 却下理由 |
|---|---|
| Babashka | 本テンプレート方針で不採用（shell script 優先） |
| 旧 Clojure MQTT ラッパ | メンテ停滞（G4） |
| ESP32 等の非 JVM 環境 | 射程外（ADR で Rust/Go 推奨を記録） |

**採用理由**:
- GraalVM Native Image で起動時間 100ms 以下、メモリ 50MB 程度に削減可能
- Pi4J は Java 界のデファクト、薄く Clojure で包む

**採用 stack**: edge stack（§4.2.14）

**適用条件**:
- Raspberry Pi クラス → edge stack
- センサー多数 → MQTT + worker stack の組合せ
- リアルタイム制御（ms 単位） → 射程外

### 3.40 ゲーム・グラフィクス（射程外）

**採用方針**: **本テンプレート射程外**と明示。

**採用ライブラリ**: なし（参考情報として `quil` / `play-cljc` を挙げるのみ）

**採用理由**:
- ゲームループは副作用の中心、純粋関数型と相性が悪い
- 本テンプレートはビジネスアプリ / バッチ / CLI 向け
- ゲーム開発は別テンプレートを検討

**stack 追加なし**。ゲーム要件プロジェクトは ADR 発行 + 本テンプレート以外の枠組み選択。

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
| SaaS / マルチテナント Web アプリ（Biff + XTDB ベース） | **saas stack** |
| データサイエンス・機械学習（tabular data、古典 ML、ノートブック） | **ml stack** |
| LLM / AI を組み込んだアプリ（RAG、チャット、Agent） | **llm-app stack** |
| IoT / エッジ（Raspberry Pi 等、GraalVM Native Image） | **edge stack** |

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
| コンテント折衝 | `metosin/muuntaja` | 0.6.10 |
| 構造化ログ | `com.brunobonacci/mulog` | 0.9.0 |
| ログ JSON 出力 | `com.brunobonacci/mulog-json` | 0.9.0 |
| WebSocket（リアルタイム必要時） | ring-websocket（Jetty 組込、§3.16） | — |
| CSRF（公開 Web API 必須） | `ring/ring-anti-forgery` | 1.3.0 |
| CORS（ブラウザクライアント時） | `metosin/reitit-ring` の CORS middleware | 0.7.2 |
| Session Store（Cookie 認証 + スケールアウト時） | `com.taoensso/carmine`（Redis） | 3.3.2 |
| Retry / Circuit Breaker（外部 API 呼出時） | `sunng87/diehard` | 最新 |
| 全文検索（検索機能時） | PostgreSQL tsvector（next.jdbc）or `mpenet/spandex`（大規模、§3.35） | — |
| 帳票 PDF（帳票機能時） | `clj-pdf/clj-pdf`（§3.31） | 最新 |
| i18n（多言語対応時） | `taoensso/tempura`（§3.20） | 最新 |

**選定ポイント**:
- **HTTP サーバ実装**: Jetty(標準・成熟)が初期推奨。高同時接続性能が重要なら http-kit(NIO、軽量)を検討。aleph は §8.2 非推奨（manifold 依存、過剰）。プロファイル要件で判断
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
| マイグレーション | `migratus/migratus`（§3.25） | 最新 |
| 構造化ログ | `com.brunobonacci/mulog` | 0.9.0 |
| スケジューリング（定期実行時） | `jarohen/chime` | 0.3.3 |
| Retry（外部 API 呼出時） | `sunng87/diehard`（§3.17） | 最新 |
| レポート Excel（Excel 出力時） | `dk.ative/docjure`（§3.31） | 最新 |
| レポート PDF（PDF 出力時） | `clj-pdf/clj-pdf`（§3.31） | 最新 |
| メール送信（通知時） | `draines/postal`（SMTP）または hato 直接（§3.33） | — |

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
| Retry / Circuit Breaker（必須化） | `sunng87/diehard`（§3.17） | 外部 API 呼出時は常に |
| S3 等ファイルストレージ（必要時） | `com.cognitect.aws/s3`（§3.34） | 同 aws-api 系列 |

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

#### 4.2.11 saas stack

**目的**: SaaS・社内サービス向け、Biff + XTDB を軸にしたマルチテナント対応完結ソリューション。

**必要機能**: ライフサイクル、bitemporal DB、認証、HTMX or reitit、セッション、マルチテナント、構造化ログ。

**推奨ライブラリ**:

| 機能 | ライブラリ | バージョン目安 |
|---|---|---|
| 統合フレームワーク | `com.biffweb/biff` | 最新 |
| bitemporal DB | `com.xtdb/xtdb-api` | 2.x |
| 設定管理 | `aero/aero` | 1.1.6 |
| HTTP サーバ | `ring/ring-jetty-adapter` | 1.13.0 |
| ルーティング | `metosin/reitit`（Biff 内蔵と共存） | 0.7.2 |
| SSR テンプレート | `rum/rum` または `hiccup2/hiccup` | — |
| 認証 | `buddy/buddy-sign` + `buddy/buddy-hashers` | — |
| 構造化ログ | `com.brunobonacci/mulog` | 0.9.0 |

**選定ポイント**:
- Biff は Polylith brick 構造と流儀が異なるため、**Biff 側の helpers を component 化して包む**パターン（ADR 必須）
- XTDB の tenant 分離は `tenant-id` 属性 + valid-time で実現
- 認証セッションは XTDB に直接保存可能、別セッションストア不要
- SSR + HTMX で SPA を避ける設計（ClojureScript 不要）

**避けるべきライブラリ**:
- ClojureScript フロントエンド全面採用（HTMX で済むなら不要、G6）
- 別 ORM / 別 query builder（XTDB の Datalog で十分）
- §4.2.3 web-api stack の避けるべきリストも該当

**採用時の確認事項**:
- [ ] XTDB submit log / document store が設定されている（in-memory / RocksDB / PostgreSQL backing）
- [ ] tenant-id 属性が全 document に必須化されている（Malli スキーマで契約）
- [ ] 認証 middleware が全ルートで tenant-id 検証を実施
- [ ] Biff の auto-reload が開発時有効、production で無効
- [ ] バックアップ戦略（XTDB log/docs の対象化）が設計済み

#### 4.2.12 ml stack

**目的**: データサイエンス・機械学習、ノートブック駆動開発。

**必要機能**: dataset 操作、ML pipeline、可視化、ノートブック。

**推奨ライブラリ**:

| 機能 | ライブラリ | バージョン目安 |
|---|---|---|
| dataset | `scicloj/tablecloth` | 最新 |
| ML pipeline | `scicloj/scicloj.ml` | 最新 |
| ノートブック | `scicloj/clay` | 最新 |
| 統合（任意） | `scicloj/noj` | 最新 |
| 可視化 | `djblue/portal` + `scicloj/clay` | — |
| Python interop（必要時） | `clj-python/libpython-clj` | 最新 |
| 構造化ログ | `com.brunobonacci/mulog` | 0.9.0 |

**選定ポイント**:
- data 駆動の頂点、scicloj 系は本テンプレート哲学に最も整合
- Python 依存は境界に限定、Clojure コアは純粋保持
- ノートブック（clay）は Markdown + Clojure コード + 結果の data 駆動記録
- Portal で tap> ベースのインスペクションを活用

**避けるべきライブラリ**:
- `deeplearning4j`（OOP 重い、§8.2）
- `incanter`（メンテ低迷、§8.2）
- 自作 dataset 構造（tablecloth で十分）

**採用時の確認事項**:
- [ ] tablecloth で dataset 操作が書ける
- [ ] Malli スキーマで dataset 構造を契約化
- [ ] Python 連携時は libpython-clj の仮想環境が固定（`requirements.txt` 管理）
- [ ] 学習済みモデルの serialize / deserialize 経路が明確
- [ ] ノートブック（clay）の生成先・公開先（HTML）が決まっている

#### 4.2.13 llm-app stack

**目的**: LLM / AI を組み込んだアプリケーション（RAG、チャット、Agent）。

**必要機能**: LLM プロバイダ呼出し、embedding 生成、ベクトルストア、プロンプト管理、リトライ、構造化ログ。

**推奨ライブラリ**:

| 機能 | ライブラリ | バージョン目安 |
|---|---|---|
| HTTP クライアント | `hato/hato` | 1.0.0 |
| LLM wrapper（任意） | `wkok/openai-clojure` | 最新 |
| JSON | `metosin/jsonista` | 0.3.11 |
| ベクトルストア（標準） | PostgreSQL + pgvector（next.jdbc 経由） | — |
| プロンプトテンプレート | `selmer/selmer` または自作 | 1.12.x |
| リトライ | `sunng87/diehard` | 最新 |
| 構造化ログ | `com.brunobonacci/mulog` | 0.9.0 |
| ライフサイクル | `integrant/integrant` | 0.13.1 |

**選定ポイント**:
- プロンプトは EDN 管理、git 追跡対象（コードと同等）
- プロバイダ切替は wrapper 層で、core は純粋保持
- ベクトル検索は PostgreSQL で完結を第一選択、Pinecone 等は ADR
- token 使用量ログは mulog event `::llm-call` で統一

**避けるべきライブラリ**:
- `langchain4j` Clojure 移植（OOP 重い、変動早い、§8.2）
- 専用 agent framework（2025 時点で成熟品なし）
- `llama-clj`（native 依存、ビルド複雑、条件付き）

**採用時の確認事項**:
- [ ] API key が aero `#env` 管理、コード埋込なし
- [ ] token 使用量が mulog で記録、コスト追跡可能
- [ ] リトライポリシーが diehard で定義、rate limit 対応
- [ ] プロンプトテンプレート全てに Malli 入出力スキーマ
- [ ] embedding ベクトル次元が pgvector スキーマと一致（起動時検証）
- [ ] プロバイダ切替時の wrapper 層インタフェースが明確

#### 4.2.14 edge stack

**目的**: Raspberry Pi / 組込み JVM 環境、GraalVM Native Image による軽量化。

**必要機能**: GraalVM Native Image、GPIO、MQTT、設定管理、軽量構造化ログ。

**推奨ライブラリ**:

| 機能 | ライブラリ | バージョン目安 |
|---|---|---|
| Native Image | GraalVM 21 | — |
| GPIO | `com.pi4j/pi4j-core` + `com.pi4j/pi4j-plugin-pigpio` | 2.x |
| MQTT | `org.eclipse.paho/org.eclipse.paho.client.mqttv3` | 1.2.5 |
| 設定管理 | `aero/aero` | 1.1.6 |
| 構造化ログ | `com.brunobonacci/mulog`（軽量 publisher） | 0.9.0 |

**選定ポイント**:
- `clj -T:build native-image` を build.clj に追加、GraalVM 必須
- reflection 回避、`*warn-on-reflection* true` 必須
- mulog publisher は console または軽量ネットワーク（CloudWatch 等）
- MQTT 再接続・バックオフ戦略を必須実装

**避けるべきライブラリ**:
- reflection を伴う Java interop 多用ライブラリ（Native Image ビルド時警告）
- Babashka（shell script 優先方針、§8.2）
- 重量 GUI フレームワーク

**採用時の確認事項**:
- [ ] GraalVM Native Image ビルドが成功する（`reflect-config.json` 必要時）
- [ ] 起動時間 < 500ms、メモリ < 100MB（計測証跡）
- [ ] Pi4J の native lib（libpigpio）がデプロイ先にインストール済み
- [ ] MQTT 再接続戦略が実装済み（ネットワーク断耐性）
- [ ] 更新配布方法が決まっている（ota / 手動デプロイ）

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
10. clj-kondo の hook 取り込み（shell で実行、tools.deps の :main-opts はシェル展開されないためエイリアス化できない）: `clj -M:lint --copy-configs --dependencies --lint "$(clojure -A:dev -Spath)"`
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
| HTTP サーバ | aleph | 設計思想不整合 | Jetty / http-kit に移行。Netty 基盤で依存重く、manifold を抱え込む | — |
| 並行処理 | manifold | 設計思想不整合 | core.async / promise.cljc に移行。aleph と同系統の設計不整合 | — |
| HTTP サーバ | immutant | メンテ停止 | Jetty へ。WildFly 系はもはやメンテされていない | — |
| 多言語対応 | tower | メンテ停止 | `taoensso/tempura` へ（§3.20） | — |
| E2E テスト | clj-webdriver | メンテ停止 | `etaoin/etaoin` へ（§3.27） | — |
| Markdown | endophile | メンテ停止 | `markdown-clj/markdown-clj` へ（§3.32） | — |
| 全文検索 | clojurewerkz/elastisch | メンテ停止 | `mpenet/spandex` へ（§3.35） | — |
| AWS SDK | amazonica | メンテ停止 | `com.cognitect.aws/*` aws-api へ | — |
| 数値計算 | incanter | メンテ低迷 + 設計思想不整合 | `scicloj/tablecloth` / `scicloj/scicloj.ml` へ（§3.36） | — |
| 深層学習 | deeplearning4j Clojure wrapper | 設計思想不整合 | libpython-clj 経由で PyTorch/TensorFlow（§3.36） | — |
| 深層学習 | cortex | メンテ停止 | libpython-clj 経由へ | — |
| LLM ラッパ | langchain4j Clojure 移植 | 設計思想不整合 | hato 直接 + `wkok/openai-clojure`（§3.37） | — |
| 音声（プロダクション） | overtone（創作用途以外） | 条件付き | プロダクションでは採用しない。創作・ライブコーディング用途のみ可 | — |
| PDF | iText 直接利用 | ライセンス（AGPL） + 設計思想不整合 | `clj-pdf/clj-pdf` / Flying Saucer（openpdf 版）へ（§3.31） | — |
| ORM | Korma 等の ORM | 設計思想不整合 | `HoneySQL` + `next.jdbc` へ | — |
| ルーティング | bidi（新規採用） | 条件付き | `metosin/reitit` へ。Malli 統合で優位 | — |
| リトライ | robert.bruce | メンテ停止 | `sunng87/diehard` へ（§3.17） | — |
| MQTT | machine-head | メンテ状態要確認 | `org.eclipse.paho/org.eclipse.paho.client.mqttv3` 直接へ（§3.39） | — |
| GUI | seesaw（新規採用） | 条件付き | `humbleui` / `cljfx` へ。レガシー保守は可 | — |
| マイグレーション | Flyway / Liquibase（Clojure 新規） | 設計思想不整合 | `migratus/migratus` へ（§3.25） | — |
| 設定管理 | immuconf | 推奨代替あり | `aero/aero` へ | — |
| JSON | Cheshire（新規採用） | 条件付き | `metosin/jsonista` へ。既存コードは段階移行 | — |
| スクリプト言語 | Babashka を本番コード基盤として利用 | 設計思想不整合 | 必要な CLI は uberjar + GraalVM Native Image。Babashka は shell script 代替としての本番外利用のみ可 | — |
| XML パーサ | xerces / xalan（bundled 版） | 既に §8.1 禁止 | `org.clojure/data.xml` へ | — |

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
