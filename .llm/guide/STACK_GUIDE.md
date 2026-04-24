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

- **必須層**: プロジェクト目的に関わらず常に採用されるもの（Clojure、tools.deps、Polylith、Malli、clj-kondo、cljfmt、Splint、clj-watson、`.llm/scripts/`、JVM）。**ワークスペースルートの `deps.edn` の `:deps`** および必須エイリアスで宣言。version 情報の正本は §2.1
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
- stack 表は完全である必要はない（網羅は永久に達成できない性質のもの）。重要なのは記載されている判断が**原則に基づき正しく導出されている**ことである。
- 本文書においては、自動生成させた判断も人間が判断したものもある。不正確な判断の是正や、時間の経過による情報の劣化に対処するため、適宜更新することが望ましい。開発者は、独自の判断で随時 stack 表を変更することができるが、LLMは将来の変更の可能性に惑わされず、一義的にこの文書を信頼しなくてはならない。

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

**本節は必須層・stack 層・横断層の正本**（による正本化）。`CLAUDE.md §3` と `BOOTSTRAP_GUIDE.md §1` は LLM 日常参照と初期化導線のための概念的抜粋であり、**version 情報は本節のみに記載**する。version drift 防止の保守規律は `MAINTAINERS_GUIDE.md §5.1` を参照。

### 2.1 必須層（常に採用、stack 非依存）

**version 情報の SSOT は下記 `;; lib-catalog` EDN block**（deps.edn の実体はこれを参照して記述される）。version 値が複数箇所に分散すると drift するため、本 block の値が一次情報源。

**ランタイム（非 lib）**:
- **JVM 21 LTS**（長期サポート、パフォーマンス）
- **tools.deps**（Clojure CLI 組込、`deps.edn` による宣言的依存管理）

```edn
;; lib-catalog
[;; === 言語・ランタイム ===
 {:purpose  [:language]
  :ids      {:coord org.clojure/clojure}
  :judgment {:status :recommended :version "1.12.0"}
  :reasons  {:text "本テンプレートの基盤"}}

 ;; === 構造化アーキテクチャ ===
 ;; Polylith は git 参照（master 最新、2026-04 時点の sha）。deps.edn の
 ;; :poly alias で :git/url + :git/sha により取り込む。
 {:purpose  [:workspace]
  :ids      {:coord polyfy/polylith}
  :judgment {:status :recommended :version "c804c2c"}
  :reasons  {:text "brick ベースの再利用性、poly check が構造違反を検知"}}

 ;; === 契約・検証 ===
 {:purpose   [:validation]
  :ids       {:coord metosin/malli :ns "malli.core"}
  :judgment  {:status :recommended :version "0.16.4"}
  :reasons   {:text "関数契約 m/=>、instrumentation。§1.1.1 全域性の実装"}
  :relations {:conflicts-with [[org.clojure/spec.alpha "検証ライブラリは片方に統一、spec.alpha は §3.3 で :deprecated"]
                               [prismatic/schema "検証ライブラリは片方に統一、prismatic/schema は §3.3 で :deprecated"]]}}

 ;; === Lint / Format（§1.2.1 機械化） ===
 ;; clj-kondo は .clj-kondo/config.edn + custom hook が配布時点で同梱され、
 ;; LLM の悪手を error で機械的に封じる（§1.2.1 機械化の実装の柱）。
 {:purpose  [:lint :ast]
  :ids      {:coord clj-kondo/clj-kondo}
  :judgment {:status :recommended :version "2024.11.14"}
  :reasons  {:text "構文・型・未使用の検知、LLM の悪手を error で封じる"}}

 ;; Splint は clj-kondo の補完（スタイル・イディオム）。
 {:purpose  [:lint :style]
  :ids      {:coord io.github.noahtheduke/splint}
  :judgment {:status :recommended :version "1.19.0"}
  :reasons  {:text "(= 0 x) → (zero? x) 等のイディオム違反検知、clj-kondo 補完"}}

 {:purpose  [:format]
  :ids      {:coord dev.weavejester/cljfmt}
  :judgment {:status :recommended :version "0.13.0"}
  :reasons  {:text "cljfmt.edn 同梱でフォーマット議論を排除"}}

 ;; === セキュリティ（時間軸を跨いだ機械化） ===
 ;; clj-watson は git 参照。:version には git/tag を記載。
 {:purpose  [:security :deps-scan]
  :ids      {:coord io.github.clj-holmes/clj-watson}
  :judgment {:status :recommended :version "v6.0.1"}
  :reasons  {:text "NIST NVD + GitHub Advisory 照合、承認済み依存の脆弱化を検知。release 前必須"}}

 ;; === 依存管理・リロード ===
 {:purpose  [:tooling :deps-update]
  :ids      {:coord com.github.liquidz/antq}
  :judgment {:status :recommended :version "2.11.1264"}
  :reasons  {:text "ライブラリ更新検知、定期実行"}}

 {:purpose  [:dev :reload]
  :ids      {:coord org.clojure/tools.namespace :ns "clojure.tools.namespace.repl"}
  :judgment {:status :recommended :version "1.5.0"}
  :reasons  {:text "(reset) の基盤、REPL 駆動開発の前提"}}

 ;; === nREPL（エディタ接続） ===
 {:purpose  [:dev :nrepl :server]
  :ids      {:coord nrepl/nrepl :ns "nrepl.server"}
  :judgment {:status :recommended :version "1.3.0"}
  :reasons  {:text "nREPL サーバ、エディタ接続の基盤"}}

 {:purpose  [:dev :nrepl :cider]
  :ids      {:coord cider/cider-nrepl}
  :judgment {:status :recommended :version "0.50.2"}
  :reasons  {:text "Cider 統合 middleware"}}

 {:purpose  [:dev :nrepl :refactor]
  :ids      {:coord refactor-nrepl/refactor-nrepl}
  :judgment {:status :recommended :version "3.10.0"}
  :reasons  {:text "リファクタリング middleware"}}]
```

配布物として `.clj-kondo/polyguard/hooks.clj`（AST 解析型の custom hook）と `.llm/scripts/*.sh`（設定ファイル・ディレクトリ構造の機械的検査）も必須層の一部。役割分担は `MAINTAINERS_GUIDE.md §5.10`。

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

各 stack で推奨するライブラリは §3 機能別節を参照。採用時は該当 stack の推奨ライブラリを **brick の deps.edn** に記述する（ワークスペースルートの deps.edn ではない）。

### 2.3 横断層（任意併用、brick deps.edn に反映）

| stack 名 | 目的 | 参照先 |
|---|---|---|
| **dev-tools stack** | 開発支援 | §3.8 テスト・検証支援（test.check / matcher-combinators）、§3.9 データインスペクション / REPL デバッガ（Portal / flow-storm）。Integrant を含む stack 採用時は §3.1 の `integrant/repl` も |

stack 層と組み合わせて使う。開発支援ライブラリは通常 **development project のルート `deps.edn` の `:dev` エイリアスの `:extra-deps`** に追加される（本番ビルドに混入させない）。具体 lib と version は §3.1 / §3.8 / §3.9 の `;; lib-catalog` を SSOT として参照する。

---

## 3. 機能別の選定根拠

本節は**このテンプレートで採用している技術選定の判断根拠**を機能別に記録する。新規採用・変更時は本節を更新する（対応 ADR も発行）。

### 3.0 機械可読ライブラリカタログ（lib-catalog）の仕組み

**本節が lib 情報の唯一の source of truth**（narrative + 機械可読 EDN の同居）。各 §3.X に採用 lib の `;; lib-catalog` EDN block を埋め込み、採用エントリと不採用（代替と却下）エントリをまとめて置く。これにより「どの lib を採用し、代替として何を却下したか」が 1 箇所で一望でき、maintainer は narrative と data を同時に更新する。

**EDN block 構造**:

```clojure
;; lib-catalog
[;; === 採用 ===
 {:purpose  [:function :subcategory]        ; 機能分類 vec
  :ids      {:coord library/artifact
             :ns    "namespace.prefix"}
  :judgment {:status :recommended :version "x.y.z"}
  :reasons  {:text "1 行要約"}}

 ;; === 代替と却下 ===
 ;; Alternative1: 却下理由の詳細（収まらない場合は ;; コメントで複数行）
 {:purpose  [:function :subcategory]
  :ids      {:coord alt/library}
  :judgment {:status      :deprecated
             :severity    :superseded   ; :forbidden も可
             :replacement library/artifact}
  :reasons  {:text "却下理由 1 行"
             :tags [:philosophy-mismatch]}}]  ; :tags は §8.0 の標準理由タグから
```

**要点**:

- **`:reasons :text` は 1 行要約**（grep しやすい）。判定根拠の全文は EDN 直前の `;;` コメントで保持（旧「検討した代替」table の却下理由は完全にここに移植される）
- **考え方 / 採用理由の全体像**は EDN の外側の prose（**採用理由**、**採用 stack** 等の markdown 節）で表現
- **`:purpose` vec**: `[:lifecycle]`, `[:db :jdbc]`, `[:web :routing]` のような階層 keyword。uniqueness key は `[[:ids :coord] :purpose]` pair
- **`:status`**: `:recommended` / `:acceptable` / `:conditional` / `:deprecated` / `:scope-excluded`（詳細は §8.0 理由タグと Malli schema）
- **`;; lib-catalog` は完全一致マーカ**。`.llm/scripts/gen_lib_catalog.clj` が本文書を走査、各 block を収集し `.llm/data/libs.edn` / `.patterns` を生成。`check-workspace-integrity.sh` が diff で drift を自動検知
- **cross-cutting lib の多重記述は許容**（構造完全一致時のみ dedup、矛盾は error）。ある lib が複数 §3.X に現れる場合は、全て同一内容で記述する必要がある

**詳細 schema**: `.llm/scripts/gen_lib_catalog.clj` の `entry-schema`、運用規約は `MAINTAINERS_GUIDE.md §5.9`。

### 3.1 ライフサイクル管理

**採用**: Integrant

**採用理由**:
- 純粋な data としてシステムを表現（`ig/init` に渡す map）
- 依存順序が宣言的、`ig/halt!` で確実に逆順停止
- `integrant.repl` で `(go)` `(reset)` `(halt)` が動作し REPL 駆動開発に適する
- Malli instrumentation の起動・停止を他コンポーネントと同列に扱える

```edn
;; lib-catalog
[;; === 採用 ===
 {:purpose   [:lifecycle]
  :ids       {:coord integrant/integrant :ns "integrant.core"}
  :judgment  {:status :recommended :version "0.13.1"}
  :reasons   {:text "data 駆動 DI、本テンプレート採用"}
  :relations {:conflicts-with [[com.stuartsierra/component "lifecycle 管理は片方に統一、component は defrecord 依存で :deprecated"]
                               [mount/mount "lifecycle 管理は片方に統一、mount はグローバル状態で :deprecated"]]
              :pairs-with     {:repl    integrant/repl
                               :config  aero/aero
                               :logging com.brunobonacci/mulog}}}

 ;; REPL 駆動開発用。dev エイリアスへ配置し、本番 uberjar には混入させない
 {:purpose   [:lifecycle :repl]
  :ids       {:coord integrant/repl :ns "integrant.repl"}
  :judgment  {:status :recommended :version "0.4.0"}
  :reasons   {:text "REPL での go/reset/halt サイクル"}
  :relations {:pairs-with {:core integrant/integrant}}}

 ;; === 代替と却下 ===
 ;; Component (stuartsierra/component): Integrant より古い世代の lifecycle 管理。
 ;; 関数合成より defrecord への依存が強く、Malli との統合でラップ層が冗長化する。
 ;; 設計思想として defrecord 中心で、値ベース data 駆動との相性が弱い。
 {:purpose  [:lifecycle]
  :ids      {:coord com.stuartsierra/component :ns "com.stuartsierra.component"}
  :judgment {:status :deprecated :severity :superseded :replacement integrant/integrant}
  :reasons  {:text "defrecord 依存が強く Malli 統合が冗長、data 駆動と相性悪"
             :tags [:philosophy-mismatch]}}

 ;; Mount: defstate でグローバル状態を生む実装。副作用の隔離（§1.1.3）の観点で、
 ;; 純粋性を保ちにくく、依存注入の明示性に劣る。
 {:purpose  [:lifecycle]
  :ids      {:coord mount/mount :ns "mount.core"}
  :judgment {:status :deprecated :severity :superseded :replacement integrant/integrant}
  :reasons  {:text "グローバル状態を生み、副作用の隔離が弱い"
             :tags [:philosophy-mismatch]}}

 ;; 自作（atom ベース）: テストと再起動のコストが高い、既成ライブラリの再発明は
 ;; 価値なし。採用しない。coord を持たないため EDN エントリ化はせず narrative のみ。
 ]
```

**採用 stack**: web-api stack、batch stack、worker stack、data-pipeline stack、saas stack、llm-app stack、cli stack（ファイル I/O や DB を扱う場合）、bot stack、desktop stack、edge stack

### 3.2 設定管理

**採用**: aero

**採用理由**:
- `#profile :dev / :staging / :prod` による環境別設定が宣言的
- `#env` で環境変数参照、`#or` でフォールバック
- config.edn を一枚で完結させやすい

```edn
;; lib-catalog
[;; === 採用 ===
 {:purpose   [:config]
  :ids       {:coord aero/aero :ns "aero.core"}
  :judgment  {:status :recommended :version "1.1.6"}
  :reasons   {:text "tagged literal (#env #profile) で環境別設定"}
  :relations {:conflicts-with [[environ/environ "設定ライブラリは片方に統一、environ は構造化設定に弱く :deprecated"]
                               [immuconf/immuconf "設定ライブラリは片方に統一、immuconf は aero より普及度劣り :deprecated"]]
              :pairs-with     {:lifecycle integrant/integrant}}}

 ;; === 代替と却下 ===
 ;; environ: 環境変数のフラット参照中心。構造化設定（profile 切替、ネスト、
 ;; 複数環境変数の合成）が弱く、config.edn を宣言的に表現しにくい。
 {:purpose  [:config]
  :ids      {:coord environ/environ :ns "environ.core"}
  :judgment {:status :deprecated :severity :superseded :replacement aero/aero}
  :reasons  {:text "構造化設定に弱い、aero の tagged literal が表現力で優位"
             :tags [:replacement-available]}}

 ;; immuconf: 機能は aero に近いが、aero がコミュニティ事実上の標準として広く採用済み。
 ;; 生態系の広さと #env / #profile / #or の組み合わせで aero 優位。
 {:purpose  [:config]
  :ids      {:coord immuconf/immuconf :ns "immuconf.config"}
  :judgment {:status :deprecated :severity :superseded :replacement aero/aero}
  :reasons  {:text "aero が事実上のコミュニティ標準"
             :tags [:replacement-available]}}

 ;; cprop: 機能は十分だが aero より宣言性が低い。採用しない。
 ;; 自作 EDN リーダー: `#env` `#profile` を再実装する価値なし。採用しない。
 ;; (cprop / 自作は coord としてエントリ化しないが却下理由は保持)
 ]
```

**採用 stack**: Integrant と組で使う stack すべて（web-api, batch, worker, saas, bot, llm-app, edge 等）

### 3.3 検証・契約

**採用**: Malli（必須層、stack 非依存）

**採用理由**:
- `m/=>` による関数契約（引数・返り値の双方向検証）
- instrumentation による境界での自動検証
- Malli スキーマから test.check generator が自動生成（プロパティテストのコストが激減）
- reitit-malli で HTTP レイヤーの検証と統合

```edn
;; lib-catalog
[;; === 採用（Malli は必須層、§2.1 で登録済） ===
 ;; 本節では :validation :recommended の採用エントリは §2.1 Malli を参照。
 ;; ここでは :validation の代替と却下のみを記録。

 ;; === 代替と却下 ===
 ;; clojure.spec.alpha: `defn` 外での定義、generator が生 Clojure で冗長。
 ;; `m/=>` のような関数契約が弱く、instrumentation も Malli より弱い。
 ;; 設計思想として Malli（data 駆動 schema + registry）と衝突。
 {:purpose  [:validation]
  :ids      {:coord org.clojure/spec.alpha :ns "clojure.spec.alpha"}
  :judgment {:status :deprecated :severity :superseded :replacement metosin/malli}
  :reasons  {:text "関数契約・generator の表現力で Malli に劣る"
             :tags [:philosophy-mismatch :replacement-available]}}

 ;; Prismatic / Plumatic Schema (prismatic/schema): Malli の前身的存在。
 ;; メンテは継続だが頻度低下。record ベースで data 駆動度も Malli に劣り、
 ;; registry・instrumentation・generator 統合で Malli が優位。新規不可。
 ;; (Clojars は prismatic/schema のまま、プロジェクト名は plumatic に改名した経緯あり)
 {:purpose  [:validation]
  :ids      {:coord prismatic/schema :ns "schema.core"}
  :judgment {:status :deprecated :severity :superseded :replacement metosin/malli}
  :reasons  {:text "Malli の前身的存在、registry/instrumentation 統合で Malli が優位"
             :tags [:philosophy-mismatch :replacement-available]}}
 ]
```

**採用 stack**: 全 stack（必須層、stack 非依存）

### 3.4 HTTP サーバ・ルーティング

**採用**: Ring + Reitit（+ reitit-malli）+ ring-jetty-adapter（HTTP サーバ実装）+ muuntaja（content negotiation）+ ring-anti-forgery（CSRF）

**採用理由**:
- Reitit はルーティングを data（ベクタ）として表現、静的解析しやすい
- reitit-malli により HTTP 層で契約を強制できる
- middleware / interceptor の両対応
- Jetty（標準・成熟）が初期推奨、高同時接続性能が重要なら http-kit（NIO、軽量）
- middleware の順序は Reitit の data 駆動ルーティングで list として明示、順序依存のバグを契約で防ぐ
- エラーハンドリングは Reitit の `exception` middleware で例外 → HTTP エラーレスポンス変換を中央集権化
- CORS は reitit の middleware、圧縮は `ring.middleware.gzip` または Jetty 側で有効化

```edn
;; lib-catalog
[;; === 採用 ===
 {:purpose   [:web :http-core]
  :ids       {:coord ring/ring-core :ns "ring.core"}
  :judgment  {:status :recommended :version "1.13.0"}
  :reasons   {:text "Ring 仕様の標準実装"}
  :relations {:pairs-with {:server  ring/ring-jetty-adapter
                           :routing metosin/reitit-ring}}}

 {:purpose   [:web :http-server]
  :ids       {:coord ring/ring-jetty-adapter :ns "ring.adapter.jetty"}
  :judgment  {:status :recommended :version "1.13.0"}
  :reasons   {:text "Jetty ベース、初期推奨。成熟・枯れている"}
  :relations {:conflicts-with [[http-kit/http-kit "HTTP サーバは片方に統一、http-kit は :acceptable（高同時接続時のみ）"]
                               [aleph/aleph "HTTP サーバは片方に統一、aleph は Netty 依存重く :deprecated"]
                               [org.immutant/web "HTTP サーバは片方に統一、immutant はメンテ停止 :deprecated"]]
              :pairs-with     {:core    ring/ring-core
                               :routing metosin/reitit-ring}}}

 ;; http-kit: NIO ベースの軽量 HTTP サーバ。高同時接続性能が重要なプロジェクト向け。
 ;; ring-jetty-adapter と代替関係、どちらかを選択する。
 {:purpose   [:web :http-server]
  :ids       {:coord http-kit/http-kit :ns "org.httpkit.server"}
  :judgment  {:status :acceptable :version "2.8.0"}
  :reasons   {:text "NIO 軽量、高同時接続性能が要るなら検討"}
  :relations {:conflicts-with [[ring/ring-jetty-adapter "HTTP サーバは片方に統一、jetty が :recommended（初期推奨）"]
                               [aleph/aleph "HTTP サーバは片方に統一、aleph は :deprecated"]
                               [org.immutant/web "HTTP サーバは片方に統一、immutant は :deprecated"]]
              :pairs-with     {:routing metosin/reitit-ring}}}

 {:purpose   [:web :routing]
  :ids       {:coord metosin/reitit :ns "reitit.core"}
  :judgment  {:status :recommended :version "0.7.2"}
  :reasons   {:text "data 駆動ルーティング、Malli 統合"}
  :relations {:conflicts-with [[compojure/compojure "ルーティングは片方に統一、compojure は data 駆動でなく :deprecated"]
                               [io.pedestal/pedestal "ルーティングは片方に統一、pedestal は :conditional（大規模 interceptor 機構時のみ）"]
                               [bidi/bidi "ルーティングは片方に統一、bidi は :conditional（Malli 統合不要な保守時のみ）"]
                               [metosin/compojure-api "ルーティングは片方に統一、compojure-api は :conditional（Swagger 既存保守時のみ）"]]
              :pairs-with     {:ring     metosin/reitit-ring
                               :coercion metosin/reitit-malli}}}

 {:purpose   [:web :routing :ring]
  :ids       {:coord metosin/reitit-ring :ns "reitit.ring"}
  :judgment  {:status :recommended :version "0.7.2"}
  :reasons   {:text "Ring handler integration、CORS middleware も同梱"}
  :relations {:pairs-with {:core                metosin/reitit
                           :coercion            metosin/reitit-malli
                           :content-negotiation metosin/muuntaja
                           :csrf                ring/ring-anti-forgery}}}

 {:purpose   [:web :routing :malli]
  :ids       {:coord metosin/reitit-malli :ns "reitit.coercion.malli"}
  :judgment  {:status :recommended :version "0.7.2"}
  :reasons   {:text "Malli coercion for reitit"}
  :relations {:pairs-with {:core       metosin/reitit
                           :validation metosin/malli}}}

 {:purpose   [:web :content-negotiation]
  :ids       {:coord metosin/muuntaja :ns "muuntaja.core"}
  :judgment  {:status :recommended :version "0.6.10"}
  :reasons   {:text "Accept/Content-Type に基づく自動変換"}
  :relations {:pairs-with {:json    metosin/jsonista
                           :routing metosin/reitit-ring}}}

 {:purpose   [:web :csrf]
  :ids       {:coord ring/ring-anti-forgery :ns "ring.middleware.anti-forgery"}
  :judgment  {:status :recommended :version "1.3.0"}
  :reasons   {:text "公開 Web API 必須の CSRF 対策"}
  :relations {:pairs-with {:routing metosin/reitit-ring}}}

 ;; === 代替と却下 ===
 ;; Compojure: ルーティングを関数として表現。data 駆動ルーティングではなく、
 ;; Malli との統合が弱く静的解析が効かない。新規採用不可、レガシー保守は可。
 {:purpose  [:web :routing]
  :ids      {:coord compojure/compojure :ns "compojure.core"}
  :judgment {:status :deprecated :severity :superseded :replacement metosin/reitit-ring}
  :reasons  {:text "data 駆動でない、新規は reitit-ring。レガシー保守は可"
             :tags [:philosophy-mismatch]}}

 ;; Pedestal: interceptor 機構が強力だが、小〜中規模で過剰。学習コストが高い。
 ;; 大規模で interceptor が必要なら条件付き採用可。
 {:purpose   [:web :routing]
  :ids       {:coord io.pedestal/pedestal :ns "io.pedestal.http"}
  :judgment  {:status          :conditional
              :applicable-when "大規模 interceptor 機構が必要"
              :replacement     metosin/reitit-ring}
  :reasons   {:text "小〜中規模で過剰、interceptor 機構が要るなら検討可"
              :tags [:conditional]}
  :relations {:conflicts-with [[metosin/reitit "ルーティングは片方に統一、reitit が :recommended"]
                               [compojure/compojure "ルーティングは片方に統一、compojure は :deprecated"]
                               [bidi/bidi "ルーティングは片方に統一、bidi は :conditional"]
                               [metosin/compojure-api "ルーティングは片方に統一、compojure-api は :conditional"]]}}

 ;; bidi: ルーティング機能は十分だが Malli との統合エコシステムが弱い。
 ;; reitit のほうが生態系で優位、新規採用不可。
 {:purpose   [:web :routing]
  :ids       {:coord bidi/bidi :ns "bidi.ring"}
  :judgment  {:status          :conditional
              :applicable-when "Malli 統合が不要な既存プロジェクトの保守"
              :replacement     metosin/reitit}
  :reasons   {:text "Malli 統合で reitit が優位、新規採用不可"
              :tags [:conditional :replacement-available]}
  :relations {:conflicts-with [[metosin/reitit "ルーティングは片方に統一、reitit が :recommended"]
                               [compojure/compojure "ルーティングは片方に統一、compojure は :deprecated"]
                               [io.pedestal/pedestal "ルーティングは片方に統一、pedestal は :conditional"]
                               [metosin/compojure-api "ルーティングは片方に統一、compojure-api は :conditional"]]}}

 ;; compojure-api (metosin): metosin 自身が reitit を後継として推奨しており、
 ;; 新規は reitit-ring + reitit-malli。ただし Swagger UI 統合の既存設定が
 ;; 簡潔なため、swagger 周辺のレガシー保守や小規模用途で条件付き採用可。
 ;; metosin リポジトリ自体はメンテ継続中。
 {:purpose   [:web :routing]
  :ids       {:coord metosin/compojure-api :ns "compojure.api.core"}
  :judgment  {:status          :conditional
              :applicable-when "Swagger UI 既存設定の保守、新規は reitit-ring"
              :replacement     metosin/reitit-ring}
  :reasons   {:text "metosin 自身が reitit を後継と位置づけ、新規は reitit-ring"
              :tags [:conditional :replacement-available]}
  :relations {:conflicts-with [[metosin/reitit "ルーティングは片方に統一、reitit が :recommended"]
                               [compojure/compojure "ルーティングは片方に統一、compojure は :deprecated"]
                               [io.pedestal/pedestal "ルーティングは片方に統一、pedestal は :conditional"]
                               [bidi/bidi "ルーティングは片方に統一、bidi は :conditional"]]}}

 ;; aleph: Netty 基盤の HTTP サーバ。依存が重く、manifold を抱え込む。
 ;; 設計思想として core.async / Jetty / http-kit と衝突、採用しない。
 {:purpose  [:web :http-server]
  :ids      {:coord aleph/aleph :ns "aleph.http"}
  :judgment {:status      :deprecated
             :severity    :superseded
             :replacement [ring/ring-jetty-adapter http-kit/http-kit]}
  :reasons  {:text "Netty 基盤で依存重い、manifold を抱え込む。Jetty / http-kit が軽量"
             :tags [:philosophy-mismatch]}}

 ;; immutant: WildFly 系列、公式メンテナンス停止。Jetty へ移行。
 {:purpose  [:web :http-server]
  :ids      {:coord org.immutant/web :ns "org.immutant.web"}
  :judgment {:status :deprecated :severity :superseded :replacement ring/ring-jetty-adapter}
  :reasons  {:text "WildFly 系列はメンテ停止"
             :tags [:maintenance-stopped]}}]
```

**採用 stack**: web-api stack、graphql-api stack、saas stack、bot stack（webhook 受信時）

### 3.5 JSON

**採用**: jsonista

**採用理由**:
- Jackson をベースに最適化、高速
- キーワード化オプション等の設定が柔軟

```edn
;; lib-catalog
[;; === 採用 ===
 {:purpose   [:json]
  :ids       {:coord metosin/jsonista :ns "jsonista.core"}
  :judgment  {:status :recommended :version "0.3.11"}
  :reasons   {:text "Jackson 直叩きで高速、cheshire の代替"}
  :relations {:conflicts-with [[cheshire/cheshire "JSON ライブラリは片方に統一、cheshire は :conditional（既存段階移行のみ）"]
                               [org.clojure/data.json "JSON ライブラリは片方に統一、data.json はパフォーマンス劣位で :deprecated"]
                               [org.json/json "JSON ライブラリは片方に統一、org.json は脆弱性歴で :forbidden"]]
              :pairs-with     {:content-negotiation metosin/muuntaja}}}

 ;; === 代替と却下 ===
 ;; Cheshire: 高速で広く使われているが、jsonista のほうが Jackson の活用が緻密で
 ;; より高速。既存プロジェクトの段階移行なら可、新規は jsonista。
 {:purpose   [:json]
  :ids       {:coord cheshire/cheshire :ns "cheshire.core"}
  :judgment  {:status          :conditional
              :applicable-when "既存コードの段階移行、新規は jsonista"
              :replacement     metosin/jsonista}
  :reasons   {:text "jsonista が Jackson 直叩きで高速"
              :tags [:conditional :replacement-available]}
  :relations {:conflicts-with [[metosin/jsonista "JSON ライブラリは片方に統一、jsonista が :recommended"]
                               [org.clojure/data.json "JSON ライブラリは片方に統一、data.json は :deprecated"]
                               [org.json/json "JSON ライブラリは片方に統一、org.json は :forbidden"]]}}

 ;; data.json: 純 Clojure 実装、パフォーマンス劣位。
 {:purpose  [:json]
  :ids      {:coord org.clojure/data.json :ns "clojure.data.json"}
  :judgment {:status :deprecated :severity :superseded :replacement metosin/jsonista}
  :reasons  {:text "パフォーマンスで jsonista に劣る"
             :tags [:replacement-available]}}

 ;; org.json (legacy): デシリアライズ脆弱性の歴史あり、禁止レベル（§8.1）。
 ;; jsonista へ移行必須。
 {:purpose  [:json]
  :ids      {:coord org.json/json :ns "org.json"}
  :judgment {:status :deprecated :severity :forbidden :replacement metosin/jsonista}
  :reasons  {:text "デシリアライズ脆弱性の歴史、推奨代替あり"
             :tags [:security :replacement-available]}}]
```

**採用 stack**: web-api stack、graphql-api stack、bot stack、llm-app stack、saas stack（Reitit / content negotiation と連動）

### 3.6 永続化

**採用**: next.jdbc + HoneySQL + HikariCP

**採用理由**:
- next.jdbc: 純関数的 API、パフォーマンス良好
- HoneySQL: SQL を data として構築（Reitit と同様の data 駆動思想）
- HikariCP: JVM 界のデファクト接続プール

```edn
;; lib-catalog
[;; === 採用 ===
 {:purpose   [:db :jdbc]
  :ids       {:coord com.github.seancorfield/next.jdbc :ns "next.jdbc"}
  :judgment  {:status :recommended :version "1.3.967"}
  :reasons   {:text "モダン JDBC ラッパ、transducer 対応"}
  :relations {:conflicts-with [[org.clojure/java.jdbc "JDBC ラッパは片方に統一、java.jdbc はメンテ停止 :deprecated"]]
              :pairs-with     {:sql-builder com.github.seancorfield/honeysql
                               :pool        com.zaxxer/HikariCP
                               :migration   migratus/migratus}}}

 {:purpose   [:db :sql-builder]
  :ids       {:coord com.github.seancorfield/honeysql :ns "honey.sql"}
  :judgment  {:status :recommended :version "2.6.1230"}
  :reasons   {:text "data 駆動 SQL DSL、next.jdbc と組み合わせる"}
  :relations {:pairs-with {:jdbc com.github.seancorfield/next.jdbc}}}

 {:purpose   [:db :connection-pool]
  :ids       {:coord com.zaxxer/HikariCP}
  :judgment  {:status :recommended :version "6.2.1"}
  :reasons   {:text "最速の JDBC コネクションプール"}
  :relations {:pairs-with {:jdbc com.github.seancorfield/next.jdbc}}}

 ;; === 代替と却下 ===
 ;; clojure.java.jdbc: next.jdbc の前身。メンテ停止、next.jdbc へ移行。
 {:purpose  [:db :jdbc]
  :ids      {:coord org.clojure/java.jdbc :ns "clojure.java.jdbc"}
  :judgment {:status      :deprecated
             :severity    :superseded
             :replacement com.github.seancorfield/next.jdbc}
  :reasons  {:text "メンテ停止、推奨代替あり"
             :tags [:maintenance-stopped :replacement-available]}}

 ;; Korma (ORM 系): 関数型と相性が悪く、SQL 生成がブラックボックス化する。
 ;; data 駆動の HoneySQL + next.jdbc で SQL を組み立てるほうが透明性が高い。
 {:purpose  [:db :orm]
  :ids      {:coord korma/korma :ns "korma.core"}
  :judgment {:status      :deprecated
             :severity    :superseded
             :replacement [com.github.seancorfield/honeysql com.github.seancorfield/next.jdbc]}
  :reasons  {:text "data 駆動でない。HoneySQL + next.jdbc で SQL を組み立てる"
             :tags [:philosophy-mismatch]}}

 ;; hugsql: SQL ファイル分離は魅力だが、静的解析・Malli 統合が弱い。採用しない。
 ;; coord エントリ化せず narrative のみ。
 ]
```

**採用 stack**: batch stack、worker stack、data-pipeline stack、および web-api stack（DB を扱う場合）

### 3.7 構造化ロギング

**採用**: mulog

**採用理由**:
- イベント駆動の構造化ログ（`mulog/log ::event :key value`）
- publisher プラグインで出力先を柔軟に切替（コンソール / JSON / ELK / CloudWatch 等）
- context 継承が自動

```edn
;; lib-catalog
[;; === 採用 ===
 {:purpose   [:logging]
  :ids       {:coord com.brunobonacci/mulog :ns "com.brunobonacci.mulog"}
  :judgment  {:status :recommended :version "0.9.0"}
  :reasons   {:text "イベント駆動の構造化ログ、publisher 切替可能"}
  :relations {:conflicts-with [[com.taoensso/timbre "ロギングは片方に統一、timbre は構造化表現力劣り :deprecated"]
                               [org.apache.logging.log4j/log4j-1.2-api "ロギングは片方に統一、log4j 1.x は CVE 未修正で :forbidden"]]
              :pairs-with     {:json-output com.brunobonacci/mulog-json
                               :lifecycle   integrant/integrant}}}

 {:purpose   [:logging :json-output]
  :ids       {:coord com.brunobonacci/mulog-json}
  :judgment  {:status :recommended :version "0.9.0"}
  :reasons   {:text "mulog の JSON publisher"}
  :relations {:pairs-with {:core com.brunobonacci/mulog}}}

 ;; === 代替と却下 ===
 ;; timbre: 構造化ログの表現力が mulog より弱い。イベント駆動（μ/log）的な発想がなく、
 ;; 文字列ベースに寄りがちで、downstream の処理（JSON 出力・aggregation）が冗長化する。
 {:purpose  [:logging]
  :ids      {:coord com.taoensso/timbre :ns "taoensso.timbre"}
  :judgment {:status :deprecated :severity :superseded :replacement com.brunobonacci/mulog}
  :reasons  {:text "構造化ログの表現力が mulog より弱い"
             :tags [:replacement-available]}}

 ;; log4j 1.x: CVE-2019-17571 等の未修正脆弱性あり、公式サポート終了（2015）。
 ;; 使用禁止レベル（§8.1）、mulog 統一が第一選択。互換制約で段階移行する場合のみ
 ;; logback-classic で受ける。
 {:purpose  [:logging]
  :ids      {:coord   org.apache.logging.log4j/log4j-1.2-api
             :aliases [log4j/log4j]
             :ns      "org.apache.log4j"}
  :judgment {:status      :deprecated
             :severity    :forbidden
             :replacement [com.brunobonacci/mulog ch.qos.logback/logback-classic]}
  :reasons  {:text "log4j 1.x CVE-2019-17571 等、公式サポート終了"
             :tags [:security :maintenance-stopped]}}

 ;; clojure.tools.logging + Logback: 構造化ログに追加実装が必要、mulog の
 ;; イベント駆動構造化を持たない。採用しない（coord エントリ化せず narrative のみ）。
 ]
```

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

```edn
;; lib-catalog
[{:purpose  [:dev :property-testing]
  :ids      {:coord org.clojure/test.check :ns "clojure.test.check"}
  :judgment {:status :recommended :version "1.1.1"}
  :reasons  {:text "Malli generator と組み合わせて最大効果"}}

 {:purpose  [:dev :assert]
  :ids      {:coord nubank/matcher-combinators :ns "matcher-combinators.core"}
  :judgment {:status :recommended :version "3.9.1"}
  :reasons  {:text "部分マッチングで assert の可読性向上"}}]
```

**採用 stack**: 全 stack（dev-tools 横断）

### 3.9 開発時データインスペクション / REPL デバッガ

**採用**:
- **Portal** (`djblue/portal`): 人間向け tap> UI インスペクタ
- **flow-storm** (`com.github.flow-storm/flow-storm-dbg`): **LLM 協働時に有効な REPL-native トレース・デバッガ**

**採用理由**:

両者は competing でなく**補完関係**にある:

- **Portal**: `tap>` の出力先として UI 表示が豊富。人間開発者が対話的にデータ構造を観察するのに適する。ただし UI ベースのため **LLM 協働時は LLM が画面を見られず効果が薄い**。
- **flow-storm**: trace / step / explore API が text / EDN ベースで取り出せ、**REPL-native に evaluation 履歴・値変遷を LLM が読める**。本テンプレートの第一原理（LLM と人間の共同開発における修復コスト最小化）と整合し、Portal の盲点（LLM から UI が不可視）を埋める。
- **使い分け**: 人間が 値の構造を観察したいとき → Portal / tap>。複数段の変換・非同期・Integrant 起動連鎖の評価履歴を LLM が追うとき → flow-storm。
- 両者とも**開発時のみ、プロダクションには混入させない**（`:dev :extra-deps` 限定）

```edn
;; lib-catalog
[;; === 採用 ===
 {:purpose   [:dev :inspect]
  :ids       {:coord djblue/portal :ns "portal.api"}
  :judgment  {:status :recommended :version "0.58.5"}
  :reasons   {:text "tap> 出力先、data インスペクション。人間向け UI、ワークスペースルート :dev エイリアス専用"}
  :relations {:pairs-with {:repl-debugger com.github.flow-storm/flow-storm-dbg}}}

 ;; flow-storm: trace / step / REPL-native explore を提供。LLM 協働時に
 ;; evaluation 履歴を text / EDN で取り出せるため、LLM が画面を見られない
 ;; 本テンプレートの使用文脈で Portal の盲点を補う。
 {:purpose   [:dev :repl-debugger]
  :ids       {:coord com.github.flow-storm/flow-storm-dbg :ns "flow-storm.api"}
  :judgment  {:status :recommended :version "4.5.9"}
  :reasons   {:text "REPL-native trace / step、LLM 協働で evaluation 履歴を追える"}
  :relations {:pairs-with {:inspect djblue/portal}}}

 ;; === 代替と却下 ===
 ;; Reveal / 他の dev UI inspector 複数同時導入: Portal に一本化、複数ビューア
 ;; 競合を避ける。§3.8 採用時の確認事項と整合。narrative のみ。
 ]
```

**採用 stack**: 全 stack（dev-tools 横断）

### 3.10 CLI 引数パース

**採用**: tools.cli（cli stack）

**採用理由**:
- Clojure 標準、Malli との統合は各プロジェクトで薄く書く
- 軽量、依存ゼロ

```edn
;; lib-catalog
[;; === 採用 ===
 {:purpose  [:cli :arg-parse]
  :ids      {:coord org.clojure/tools.cli}
  :judgment {:status :recommended :version "1.1.230"}
  :reasons  {:text "CLI 引数パースの標準、docopt 系より軽量"}}

 ;; === 代替と却下 ===
 ;; docopt 系 Clojure port: メンテナンス活動が低く、tools.cli で十分。
 ;; 採用しない（coord エントリ化せず narrative のみ）。
 ]
```

**採用 stack**: cli stack

### 3.11 HTTP クライアント

**採用候補**: hato（必要時、stack に追加）

**採用理由**:
- JVM 11+ の java.net.http をラップ
- Ring 風の data 駆動 API

**位置づけ**: 現時点では必須 stack に含めない。外部 API 呼び出しが必要になった時点で個別プロジェクトで採用判断。

```edn
;; lib-catalog
[;; === 採用 ===
 {:purpose   [:web :http-client]
  :ids       {:coord hato/hato :ns "hato.client"}
  :judgment  {:status :recommended :version "1.0.0"}
  :reasons   {:text "Java 11+ HttpClient ベース、HTTP/2 対応"}
  :relations {:conflicts-with [[clj-http/clj-http "HTTP クライアントは片方に統一、clj-http は :conditional（既存段階移行のみ）"]]
              :pairs-with     {:retry sunng87/diehard
                               :json  metosin/jsonista}}}

 ;; === 代替と却下 ===
 ;; clj-http: 古くから広く使われているが、hato が Java 11+ HttpClient ベースで第一選択。
 ;; 新規採用は避け、既存コードは段階移行。
 {:purpose   [:web :http-client]
  :ids       {:coord clj-http/clj-http :ns "clj-http.client"}
  :judgment  {:status          :conditional
              :applicable-when "既存コードの段階移行、新規は hato"
              :replacement     hato/hato}
  :reasons   {:text "hato が Java 11+ HttpClient ベースで第一選択"
              :tags [:conditional :replacement-available]}
  :relations {:conflicts-with [[hato/hato "HTTP クライアントは片方に統一、hato が :recommended"]]}}]
```

**採用 stack**: web-api stack、bot stack、llm-app stack、必要に応じて全 stack

### 3.12 認証・認可

**採用**: buddy-sign + buddy-hashers（認証）、権限判定は自作 middleware + Malli で契約化

**採用理由**:
- buddy-sign: JWT 発行・検証。data 駆動で Malli と整合
- buddy-hashers: パスワードハッシュ。bcrypt / argon2 対応

```edn
;; lib-catalog
[;; === 採用 ===
 {:purpose   [:auth :jwt]
  :ids       {:coord buddy/buddy-sign :ns "buddy.sign.jwt"}
  :judgment  {:status :recommended}
  :reasons   {:text "JWT / JWS / JWE"}
  :relations {:pairs-with {:password-hashing buddy/buddy-hashers}}}

 {:purpose   [:auth :password-hashing]
  :ids       {:coord buddy/buddy-hashers :ns "buddy.hashers"}
  :judgment  {:status :recommended}
  :reasons   {:text "パスワードハッシュ bcrypt/argon2"}
  :relations {:pairs-with {:jwt buddy/buddy-sign}}}

 ;; === 代替と却下 ===
 ;; friend: メンテナンス停滞、Ring 1.11 以降との整合性に懸念。buddy 系へ移行。
 {:purpose  [:auth]
  :ids      {:coord   cemerick/friend
             :aliases [clj-commons/cemerick.friend]
             :ns      "cemerick.friend"}
  :judgment {:status      :deprecated
             :severity    :superseded
             :replacement [buddy/buddy-sign buddy/buddy-hashers]}
  :reasons  {:text "メンテ停止、Ring 1.11+ 整合性に懸念"
             :tags [:maintenance-stopped]}}

 ;; ring-oauth2: OAuth2 クライアントとしては有効だが、サーバ側認証の全体像を
 ;; 与えない。プロジェクト要件次第で追加採用可（本 block に載せず narrative のみ）。
 ;; Keycloak + adapter: 重量級、小〜中規模プロジェクトで過剰。エンタープライズ
 ;; 認証で必須なら ADR 付き採用可（narrative のみ）。
 ]
```
- 認可（権限判定）は Malli スキーマで資格情報を契約化し、middleware で強制
- OAuth2 / OIDC が必要な場合は個別判断（buddy-auth + ring-oauth2 等）

**採用 stack**: web-api stack、graphql-api stack（ユーザ認証を扱う場合の typical 追加）

### 3.13 キャッシュ

**採用候補**: core.cache（メモリ内）、carmine（Redis 連携）

**採用理由**:
- core.cache: 純 Clojure、LRU / TTL / LU 等の戦略が宣言的
- carmine: Redis 連携の標準的ライブラリ、Lua スクリプト対応
- 選択基準: プロセス内で閉じるなら core.cache、プロセス跨ぎや永続化が必要なら carmine

```edn
;; lib-catalog
[;; === 採用 ===
 {:purpose  [:cache :redis]
  :ids      {:coord com.taoensso/carmine :ns "taoensso.carmine"}
  :judgment {:status :recommended :version "3.3.2"}
  :reasons  {:text "Redis クライアント、Cookie 認証スケールアウト時の session store 兼用"}}

 ;; === 代替と却下 ===
 ;; Caffeine + java interop: 高性能だが、core.cache の上位互換を自作する価値が
 ;; 薄い。採用しない (coord 化せず narrative)。
 ;; Memcached クライアント: Redis のほうが機能的に優位（Lua、Stream、Pub-Sub）、
 ;; 二重採用の理由が薄い。coord を持たず narrative のみ。
 ]
```

**採用 stack**: プロファイル横断。必要性が生じた時点で該当 stack（典型的には web-api stack / worker stack / saas stack）に追加

**注意**: Malli instrumentation と組み合わせる時、キャッシュヒット時の契約検証をスキップするか判断が必要（KNOWLEDGE.md に運用規約を書く）

### 3.14 メトリクス・監視

**採用**: mulog の publisher（Prometheus / CloudWatch 等）+ Micrometer（必要時）

**採用理由**:
- mulog のイベントを**メトリクスと構造化ログの共通源**にできる
- mulog publisher で Prometheus / CloudWatch / ELK 等へ同一イベントを配信
- JVM メトリクス（GC、heap 等）は Micrometer を補助採用

```edn
;; lib-catalog
[;; === 採用 ===
 ;; mulog 本体は §3.7 構造化ロギングで採用済み（:purpose [:logging]）
 ;; 本節では :metrics purpose として重複採用しない（同一 lib で complementary）。

 ;; === 代替と却下 ===
 ;; metrics-clojure Ring wrapper: 独立した metrics レイヤは mulog のイベント駆動と重複、
 ;; 二重管理になる。mulog に一元化。
 {:purpose  [:metrics :dropwizard-ring]
  :ids      {:coord metrics-clojure-ring/metrics-clojure-ring}
  :judgment {:status :deprecated :severity :superseded :replacement com.brunobonacci/mulog}
  :reasons  {:text "mulog のイベント駆動と重複、mulog に一元化"
             :tags [:philosophy-mismatch]}}

 ;; metrics-clojure 本体: 同上。
 {:purpose  [:metrics :dropwizard]
  :ids      {:coord metrics-clojure/metrics-clojure}
  :judgment {:status :deprecated :severity :superseded :replacement com.brunobonacci/mulog}
  :reasons  {:text "mulog のイベント駆動と重複、mulog に一元化"
             :tags [:philosophy-mismatch]}}

 ;; iapetos (Prometheus): mulog publisher で Prometheus 出力可、独立 lib 不要。
 {:purpose  [:metrics :prometheus]
  :ids      {:coord iapetos/iapetos :ns "iapetos.core"}
  :judgment {:status :deprecated :severity :superseded :replacement com.brunobonacci/mulog}
  :reasons  {:text "mulog のイベント駆動と重複、mulog に一元化"
             :tags [:philosophy-mismatch]}}

 ;; OpenTelemetry agent (自動計装): 自動計装は便利だが、構造化ログとメトリクスが
 ;; 分離し一貫性が下がる。mulog のイベント駆動で統合する。coord エントリ化せず。
 ]
```

**採用 stack**: Integrant を伴う stack すべて（web-api stack / graphql-api stack / batch stack / worker stack / data-pipeline stack / bot stack）

**ノウハウ**: `mulog/log` のイベント名（`::http-request`、`::job-executed` 等）を**プロジェクト全体で統一する規約**を KNOWLEDGE.md §アーキテクチャ上の約束に記録することが重要。命名が分散すると集計がバラバラになる

### 3.15 スケジューリング

**採用**: chime

**採用理由**:
- chime: `core.async` ベースで軽量
- cron 式ではなく Clojure の `java.time` で schedule を data として表現
- Integrant component として起動・停止を制御可能

```edn
;; lib-catalog
[;; === 採用 ===
 {:purpose   [:scheduling]
  :ids       {:coord jarohen/chime :ns "chime.core"}
  :judgment  {:status :recommended :version "0.3.3"}
  :reasons   {:text "core.async ベース、Integrant 統合容易"}
  :relations {:conflicts-with [[overtone/at-at "スケジューラは片方に統一、at-at は停止制御弱く :deprecated"]
                               [clojurewerkz/quartzite "スケジューラは片方に統一、quartzite は :conditional（cron/永続化時のみ）"]
                               [tea-time/tea-time "スケジューラは片方に統一、tea-time はメンテ停止 :deprecated"]]
              :pairs-with     {:lifecycle integrant/integrant
                               :async     org.clojure/core.async}}}

 ;; === 代替と却下 ===
 ;; at-at: シンプルだが停止制御が弱く、Integrant 統合しにくい。chime へ。
 {:purpose  [:scheduling]
  :ids      {:coord overtone/at-at :ns "overtone.at-at"}
  :judgment {:status :deprecated :severity :superseded :replacement jarohen/chime}
  :reasons  {:text "停止制御が弱く Integrant 統合しにくい"
             :tags [:philosophy-mismatch]}}

 ;; Quartz (java interop): 重量級、設定が冗長。複雑な業務要件 (cron 式・永続化)
 ;; が必要なら条件付きで採用可。
 {:purpose   [:scheduling]
  :ids       {:coord clojurewerkz/quartzite}
  :judgment  {:status          :conditional
              :applicable-when "複雑な業務要件 (cron 式・永続化) が必要"
              :replacement     jarohen/chime}
  :reasons   {:text "重量級、設定が冗長。単純用途は chime"
              :tags [:conditional]}
  :relations {:conflicts-with [[jarohen/chime "スケジューラは片方に統一、chime が :recommended"]
                               [overtone/at-at "スケジューラは片方に統一、at-at は :deprecated"]
                               [tea-time/tea-time "スケジューラは片方に統一、tea-time は :deprecated"]]}}

 ;; tea-time: メンテナンス停滞、chime へ。
 {:purpose  [:scheduling]
  :ids      {:coord tea-time/tea-time :ns "tea-time.core"}
  :judgment {:status :deprecated :severity :superseded :replacement jarohen/chime}
  :reasons  {:text "メンテ停止、chime へ"
             :tags [:maintenance-stopped]}}]
```

**採用 stack**: batch stack、worker stack、data-pipeline stack（定期実行を含む場合）

**ノウハウ**: バッチ処理のスケジュールは**永続層との排他制御**（同時実行禁止、advisory lock 等）とセットで設計する。chime 自体は排他制御しない

### 3.16 WebSocket / Server-Sent Events

**採用**: `ring-websocket`（Ring 1.11+ 組込み、Jetty adapter 経由）。大規模同時接続時は `http-kit`。SSE は Ring の chunked response で自作。

**採用理由**:
- ring-websocket は Ring 仕様の一部、既存 middleware と統合容易
- handshake / onmessage / onclose を純粋データ（map）で扱える
- SSE は Ring の chunked response で自作可、外部依存不要

ring-core / ring-jetty-adapter / http-kit は §3.4 で採用済。本節では代替却下のみ記載。

```edn
;; lib-catalog
[;; === 代替と却下 ===
 ;; aleph / manifold: §3.4 HTTP サーバで採用不可と判定済（Netty 基盤で重い、
 ;; data 駆動でなく manifold promise/stream が純粋関数と相性悪い）。
 ;; WebSocket 用途でも同理由で採用しない（§3.4 の EDN 参照）。
 ;; immutant: §3.4 HTTP サーバで採用不可（メンテ停止、§3.4 EDN 参照）。
 ;; sente: 独自プロトコル層で抽象が厚い、ring-websocket で十分。
 ;; narrative のみ、coord 化せず（§3.4 で関連エントリは既に収録）。
 ]
```

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

**採用理由**:
- diehard は `{:retry-on ... :max-retries ...}` の map で policy 指定、data 駆動
- Malli スキーマで policy を契約化可能
- 分散 rate limit は Redis で小さく解け、専用ライブラリ不要

```edn
;; lib-catalog
[;; === 採用 ===
 {:purpose   [:resilience :retry]
  :ids       {:coord sunng87/diehard :ns "diehard.core"}
  :judgment  {:status :recommended}
  :reasons   {:text "Retry / Circuit Breaker、外部 API 呼出時に必須化"}
  :relations {:conflicts-with [[robert/robert.bruce "リトライは片方に統一、robert.bruce はメンテ停止 :deprecated"]]
              :pairs-with     {:http-client hato/hato}}}

 ;; === 代替と却下 ===
 ;; robert.bruce: メンテ停止、diehard が後継。
 {:purpose  [:resilience :retry]
  :ids      {:coord robert/robert.bruce :ns "robert.bruce"}
  :judgment {:status :deprecated :severity :superseded :replacement sunng87/diehard}
  :reasons  {:text "メンテ停止、diehard が後継"
             :tags [:maintenance-stopped]}}

 ;; resilience4j 直接 interop: Java fluent API、data 駆動でない。採用しない。
 ;; failsafe (Java): 同上。採用しない（diehard は failsafe ラッパで十分）。
 ;; ring-ratelimit: メンテ停滞。採用しない。
 ;; 以上 3 件は coord 化せず narrative のみ。
 ]
```

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

**採用理由**:
- Ring 標準 middleware は副作用が明示的で、middleware 順序を data として記述可能（Reitit と統合）
- session store は環境に応じて差替可能な依存注入パターン
- CSRF / CORS は security header 規約化で済む、フレームワーク不要
- ring-anti-forgery は §3.4 で EDN 登録済、carmine（session store 用）は §3.13 で登録済

**検討して却下した代替（narrative のみ、coord エントリ化せず）**:
- buddy-auth 全体採用: 認証と session/CSRF が混在、関心分離の観点で避ける
- 自作 CSRF token 管理: 再発明禁止
- in-memory session store（本番）: スケールアウトで session 消失（設計思想）

**採用 stack**: web-api stack、graphql-api stack、saas stack

**適用条件**:
- 公開 Web API → ring-anti-forgery + ring-cors を常に適用
- JWT のみ認証 → session store 不要
- Cookie 認証 → Redis session store（carmine）
- 同一オリジン SPA → CSRF 不要、CORS 緩め可（ADR 記録）

### 3.19 分散トレーシング（手動計装）

**採用**: `mulog` の `with-context` による trace ID / span ID 伝播 + 自作 middleware。外部送信は mulog publisher で Jaeger / Tempo / OpenTelemetry Collector へ。

**採用理由**:
- mulog `with-context` は map を継承する仕組みで、data 駆動
- 同一イベント源（mulog）から logs / metrics / traces を配信、一貫性保持（§3.14 と整合）
- 手動計装は境界（HTTP request、DB query、外部 API）に限定可能で副作用が明示

**採用 stack**: Integrant を採用する全 stack（web-api / graphql-api / batch / worker / data-pipeline / bot / saas / llm-app）

**適用条件**:
- 単一サービス → correlation ID（request ID）のみ、trace ID 不要
- マイクロサービス → W3C Trace Context ヘッダ（`traceparent`）で trace ID を引き回し
- 本規約は KNOWLEDGE.md §アーキテクチャ上の約束 に明記必須（§3.14 イベント名統一と同格の規約）

**検討して却下した代替（narrative のみ、coord エントリ化せず）**:
- OpenTelemetry Java Agent（自動計装）: §3.14 で既に却下（構造化ログと分離）
- Micrometer Tracing: Spring 文脈が強く、Clojure との親和性低い
- 独自 trace ID 実装: mulog context で既に可能、再発明禁止

### 3.20 i18n / l10n

**採用**: `taoensso/tempura`

**採用理由**:
- tempura は辞書も data（map）、参照も純粋関数
- Malli スキーマで翻訳キーの型安全化が可能
- Taoensso 作者は安定的にメンテ継続

```edn
;; lib-catalog
[;; === 採用 ===
 {:purpose   [:i18n]
  :ids       {:coord com.taoensso/tempura :ns "taoensso.tempura"}
  :judgment  {:status :recommended}
  :reasons   {:text "tower の後継、同作者"}
  :relations {:conflicts-with [[com.taoensso/tower "i18n は片方に統一、tower はメンテ停止 :deprecated"]]
              :pairs-with     {:lifecycle integrant/integrant}}}

 ;; === 代替と却下 ===
 ;; tower: 開発停止、tempura が後継。
 {:purpose  [:i18n]
  :ids      {:coord com.taoensso/tower :ns "taoensso.tower"}
  :judgment {:status :deprecated :severity :superseded :replacement com.taoensso/tempura}
  :reasons  {:text "メンテ停止、同作者の tempura が後継"
             :tags [:maintenance-stopped]}}

 ;; ICU4J 直接: 重量、tempura で包める範囲では不要。採用しない。
 ;; Java ResourceBundle: data 駆動でない、Clojure 慣用外。採用しない。
 ]
```

**採用 stack**: web-api stack（多言語 UI 時）、bot stack（複数言語サポート時）

**適用条件**:
- 単一言語 → 採用不要
- 2 言語以上 → tempura、辞書は `resources/i18n/*.edn` 配置
- 日付・通貨・数値 → `java.time` + JDK 標準 NumberFormat（tempura と直交）

### 3.21 Feature Flag

**採用**: **自作**（EDN フラグ + aero `#profile` + DB 動的フラグの組合せ）

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

**検討して却下した代替（narrative のみ、coord 化せず）**:
- LaunchDarkly Java SDK: コスト、外部サービス依存、クローズドソース
- Flagsmith: OSS 代替、自作で足りる規模では過剰
- feature-flag（Clojure 純）: メンテ不明

### 3.22 マルチテナント（Biff + XTDB パターン）

**採用方針**: **Biff + XTDB** の組合せを SaaS 向け framework として導入。RDBMS 併用時は tenant-id カラム + row-level isolation パターン。

**採用理由**:
- Biff は Polylith brick と流儀が異なるが、data 駆動・純 Clojure・副作用隔離で本テンプレート整合
- XTDB の valid-time + tenant-id 属性で自然に multi-tenant 実現
- Datalog クエリに tenant-id 条件を middleware で強制注入可能

```edn
;; lib-catalog
[;; === 採用 ===
 ;; Biff は XTDB/Rum/HTMX を同梱した opinionated SaaS framework。
 ;; Integrant とは衝突するため併用不可（Biff 内部で独自 lifecycle 管理）。
 {:purpose   [:saas :framework]
  :ids       {:coord com.biffweb/biff}
  :judgment  {:status :recommended :version "0.9.0"}
  :reasons   {:text "opinionated SaaS framework、XTDB/Rum/HTMX 同梱"}
  :relations {:bundles        [com.xtdb/xtdb-api rum/rum]
              :conflicts-with [[integrant/integrant "Biff uses its own lifecycle manager"]
                               [duct/core "§3.43 代替プラットフォームは片方に統一、Biff と Duct は framework 全体性で衝突"]
                               [com.rpl/rama "§3.43 代替プラットフォームは片方に統一、Biff と Rama は framework 全体性で衝突"]]}}

 ;; === 代替と却下 ===
 ;; 独自テナント分離フレームワーク: Biff + XTDB で完結、再発明不要。
 ;; schema-per-tenant (RDBMS): 運用複雑化、row-level で十分なケースが多い。
 ;; 条件付き採用は可（コンプライアンス要件明確時のみ、ADR 必須）。
 ;; 以上 2 件は coord 化せず narrative のみ。
 ]
```

**採用 stack**: saas stack

**適用条件**:
- SaaS・社内サービス → saas stack（Biff + XTDB）
- 既存 RDBMS 継続 → web-api stack + tenant-id middleware（row-level）
- schema-per-tenant → コンプライアンス要件明確時のみ、ADR 必須

### 3.23 NoSQL 系

**採用**:
- **Key-Value / Cache / Pub-Sub**: `com.taoensso/carmine`（既採用、primary 用途も公式化）
- **ドキュメント型（MongoDB）**: `com.novemberain/monger`
- **AWS DynamoDB**: `com.cognitect.aws/dynamodb`（aws-api 系列）

**採用理由**:
- carmine は EDN-native、Redis コマンドを data で記述
- monger は map で document を表現、Clojure 慣用
- aws-api は全 AWS サービスで同じパターン、学習コスト低

```edn
;; lib-catalog
[;; === 採用 ===
 ;; carmine は §3.13 キャッシュで採用済（同一 lib を重複登録せず、参照のみ）。
 {:purpose  [:db :mongodb]
  :ids      {:coord com.novemberain/monger :ns "monger.core"}
  :judgment {:status :recommended}
  :reasons  {:text "MongoDB の Clojure 慣用ラッパ、document を map で扱える"}}

 {:purpose  [:db :dynamodb]
  :ids      {:coord com.cognitect.aws/dynamodb}
  :judgment {:status :recommended}
  :reasons  {:text "aws-api 系列、全 AWS サービスで同じパターン"}}

 ;; === 代替と却下 ===
 ;; Cassandra Java driver 直接: Clojure 慣用ラッパなし、必要時 ADR で個別採用。
 ;; MongoDB Java Driver 直接: monger がシンプル data 駆動ラッパ。
 ;; faraday (DynamoDB): aws-api に統一 (worker stack と整合)。
 ;; 以上 3 件は coord 化せず narrative のみ。
 ]
```

**採用 stack**: web-api / worker / batch / saas（必要性に応じ）

**適用条件**:
- Primary DB として NoSQL → ADR 必須（RDBMS 比較検討）
- キャッシュ・セッション・レートリミット → carmine
- ドキュメント型が明確に有利（スキーマレス + 集計不要）→ monger
- AWS native 環境 → aws-api/dynamodb

**検討して却下した代替（narrative のみ）**:
- Cassandra Java driver 直接: Clojure 慣用ラッパなし、必要時 ADR で個別採用
- MongoDB Java Driver 直接: monger がシンプル data 駆動ラッパ
- faraday (DynamoDB): aws-api に統一（worker stack と整合）

### 3.24 XTDB / Datomic / グラフ DB

**採用**: **XTDB**（`com.xtdb/xtdb-api`）を第一選択。ライセンス（MIT/Apache）・Clojure 親和性・bitemporal が揃う。

**採用理由**:
- XTDB は EDN-native、クエリは Datalog（data 駆動）、Malli と整合
- bitemporal で「現在の事実 + 歴史」を扱え、audit log 機能が自然に得られる
- ライセンスが OSS で SaaS 展開にも制約なし

```edn
;; lib-catalog
[;; === 採用 ===
 {:purpose   [:db :document-db]
  :ids       {:coord com.xtdb/xtdb-api :ns "xtdb.api"}
  :judgment  {:status :recommended :version "2.0.0"}
  :reasons   {:text "bitemporal document DB、Biff 同梱 / 単体採用いずれも可"}
  :relations {:conflicts-with [[com.datomic/local "document DB は片方に統一、datomic は :acceptable（商用条件要確認）"]]}}

 ;; === 代替（許容）===
 ;; Datomic Pro: 商用ライセンス、新規採用時のコスト・ロックインリスク。
 ;; 既存運用プロジェクトは継続可、Clojure コミュニティ実績あり。
 {:purpose   [:db :document-db]
  :ids       {:coord com.datomic/local :ns "datomic.api"}
  :judgment  {:status :acceptable}
  :reasons   {:text "選択肢として許容、商用条件要確認"
              :tags [:license]}
  :relations {:conflicts-with [[com.xtdb/xtdb-api "document DB は片方に統一、xtdb が :recommended（OSS）"]]}}

 ;; === 代替と却下 ===
 ;; Datomic Free/Solo: 機能制限あり、本番用途なら XTDB が自然。narrative のみ。
 ;; Neo4j (neo4j-clj): 複雑グラフ特化用途のみ、一般 Web API では RDBMS/XTDB で
 ;; 足りる。coord 化せず。
 ;; Datahike: XTDB のサブセット的位置づけ、XTDB 優先。coord 化せず。
 ]
```

**採用 stack**: saas stack（既定）、web-api stack（RDBMS の代替）、batch stack（イベントソーシング用途）

**適用条件**:
- 新規プロジェクトで RDBMS と迷う → XTDB を第一検討
- bitemporal が要件 → XTDB 一択
- 複雑な JOIN 中心 → RDBMS + next.jdbc 継続
- グラフ深掘り（6 hop 以上等） → Neo4j を ADR で検討

### 3.25 マイグレーション

**採用**: `migratus/migratus`

**採用理由**:
- SQL ファイルをそのまま書ける、学習コスト最小
- up/down 管理、履歴テーブル、Clojure から直接起動可能
- next.jdbc と同じ接続情報を使える

```edn
;; lib-catalog
[;; === 採用 ===
 {:purpose   [:db :migration]
  :ids       {:coord migratus/migratus :ns "migratus.core"}
  :judgment  {:status :recommended}
  :reasons   {:text "SQL ベース、純 Clojure、新規 Clojure プロジェクトの第一選択"}
  :relations {:conflicts-with [[org.flywaydb/flyway-core "マイグレーションは片方に統一、flyway は :conditional（Java 資産保守のみ）"]
                               [org.liquibase/liquibase-core "マイグレーションは片方に統一、liquibase は :conditional（Java 資産保守のみ）"]
                               [joplin/joplin "マイグレーションは片方に統一、joplin はメンテ低迷 :deprecated"]]
              :pairs-with     {:jdbc com.github.seancorfield/next.jdbc}}}

 ;; === 代替と却下 ===
 ;; Flyway (Java): XML/Java 設定が冗長、data 駆動ではない。既存 Java 資産の
 ;; 互換保守なら条件付き採用可、新規 Clojure プロジェクトは migratus。
 {:purpose   [:db :migration]
  :ids       {:coord org.flywaydb/flyway-core}
  :judgment  {:status          :conditional
              :applicable-when "既存 Java 資産の互換保守、新規 Clojure プロジェクトは migratus"
              :replacement     migratus/migratus}
  :reasons   {:text "新規 Clojure プロジェクトでは migratus が自然"
              :tags [:conditional]}
  :relations {:conflicts-with [[migratus/migratus "マイグレーションは片方に統一、migratus が :recommended"]
                               [org.liquibase/liquibase-core "マイグレーションは片方に統一、liquibase は :conditional"]
                               [joplin/joplin "マイグレーションは片方に統一、joplin は :deprecated"]]}}

 ;; Liquibase: 同上、XML DSL。条件付き採用。
 {:purpose   [:db :migration]
  :ids       {:coord org.liquibase/liquibase-core}
  :judgment  {:status          :conditional
              :applicable-when "既存 Java 資産の互換保守、新規 Clojure プロジェクトは migratus"
              :replacement     migratus/migratus}
  :reasons   {:text "新規 Clojure プロジェクトでは migratus が自然"
              :tags [:conditional]}
  :relations {:conflicts-with [[migratus/migratus "マイグレーションは片方に統一、migratus が :recommended"]
                               [org.flywaydb/flyway-core "マイグレーションは片方に統一、flyway は :conditional"]
                               [joplin/joplin "マイグレーションは片方に統一、joplin は :deprecated"]]}}

 ;; joplin: メンテ低迷、migratus へ。
 {:purpose  [:db :migration]
  :ids      {:coord joplin/joplin :ns "joplin.core"}
  :judgment {:status :deprecated :severity :superseded :replacement migratus/migratus}
  :reasons  {:text "メンテ低迷、migratus へ"
             :tags [:maintenance-stopped]}}

 ;; ragtime: 互角だが、migratus の方が普及、data 駆動度も十分。採用しない。
 ;; coord 化せず narrative のみ。
 ]
```

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

**採用理由**:
- 既存永続化層（PostgreSQL）の拡張で済む
- XTDB の bitemporal は EventStore の主要機能を内包

**検討して却下した代替（narrative のみ、特定 coord なし）**:
- InfluxDB Clojure wrapper: 成熟度低、hato で直接 HTTP 叩けば十分
- EventStoreDB (Kurrent) gRPC クライアント: 純 Clojure wrapper なし、XTDB で代替可
- 専用時系列 DB 採用: プロジェクト規模で正当化されるケースは稀

**採用 stack**: batch / worker / data-pipeline（時系列要件時）

**適用条件**:
- メトリクス保存 → Prometheus / CloudWatch（mulog 経由、独自時系列 DB 不要）
- ドメイン時系列（IoT センサ等）→ TimescaleDB
- イベントソーシング → XTDB

### 3.27 E2E テスト

**採用**: `etaoin/etaoin`（WebDriver）

**採用理由**:
- etaoin は 100% Clojure、map で driver 設定、data 駆動
- matcher-combinators と組で assert を書ける

```edn
;; lib-catalog
[;; === 採用 ===
 {:purpose   [:testing :e2e]
  :ids       {:coord etaoin/etaoin :ns "etaoin.api"}
  :judgment  {:status :recommended}
  :reasons   {:text "100% Clojure、map で driver 設定、data 駆動"}
  :relations {:conflicts-with [[clj-webdriver/clj-webdriver "E2E WebDriver は片方に統一、clj-webdriver はメンテ停止 :deprecated"]]}}

 ;; === 代替と却下 ===
 ;; clj-webdriver: メンテ停止、etaoin が後継。
 {:purpose  [:testing :e2e]
  :ids      {:coord clj-webdriver/clj-webdriver :ns "clj-webdriver.taxi"}
  :judgment {:status :deprecated :severity :superseded :replacement etaoin/etaoin}
  :reasons  {:text "メンテ停止、etaoin が現役"
             :tags [:maintenance-stopped]}}

 ;; Playwright: Node.js / Python 経由、JVM 統合しにくい。
 ;; Selenium 直接: 低レベル、etaoin で包める範囲。
 ;; 以上 2 件は coord 化せず narrative のみ。
 ]
```

**採用 stack**: dev-tools stack 拡張、必要プロジェクトのみ任意

**適用条件**:
- ブラウザ UI（SSR or SPA）→ etaoin
- API-only → 既存 clojure.test + matcher-combinators で十分、E2E 不要
- CI 実行 → Docker Selenium hub 併用

### 3.28 負荷テスト

**採用**: **Gatling**（Scala DSL、JVM 同居）

**採用理由**:
- Gatling は JVM 内で走る、HTML レポート自動生成

**検討して却下した代替（narrative のみ）**:
- k6: JavaScript、Clojure と別スタック
- Apache Bench / wrk: 単純な HTTP 用途のみ、シナリオ書けない
- JMeter: XML 設定、data 駆動でない
- 純 Clojure 負荷テスト: 成熟ライブラリなし

**Gatling 本体は JVM（Scala）ライブラリ、`;; lib-catalog` への登録は現時点で見送り（採用 stack なし、プロジェクト個別採用）。**
- シナリオが data として書ける（Scala DSL だが構造は map）

**採用 stack**: 負荷要件があるプロジェクトに任意併用（stack 化しない、別 project として追加）

**適用条件**:
- パフォーマンス SLO あり → Gatling
- 単純スループット計測 → wrk 等（外部ツール）
- **必須層には含めない**。DESIGN.md §8 に「パフォーマンス要件」を明記したプロジェクトのみ

### 3.29 契約テスト

**採用方針**: **Pact（pact-jvm）を Java interop で利用**、純 Clojure ラッパは採用しない。

**採用理由**:
- Pact は業界標準、broker と連携可能
- Consumer-Driven Contract を Malli スキーマから生成可能（自作ブリッジ）

**検討して却下した代替（narrative のみ）**:
- 純 Clojure 契約テストライブラリ: 成熟品なし
- 自作: 契約テストの標準化効果が失われる

**採用 stack**: マイクロサービス構成時のみ（saas stack の兄弟として）

**適用条件**:
- サービス 3 つ以上で契約保証が必要 → pact-jvm、ADR 必須
- 単独サービス → 不要

### 3.30 gRPC / Protocol Buffers

**採用方針**: **積極採用しない**。社内で gRPC 必須の場合のみ `protojure/protojure`。

**採用理由（protojure）**:
- 純 Clojure 風 API、`.proto` 定義から Clojure 関数生成
- ただし抽象の厚みは大きい（採用は慎重）

```edn
;; lib-catalog
[;; === 採用（条件付き）===
 {:purpose  [:grpc]
  :ids      {:coord protojure/protojure}
  :judgment {:status          :conditional
             :applicable-when "社内で gRPC 規約がある、ADR 発行"}
  :reasons  {:text "純 Clojure 風 API、.proto から Clojure 関数生成。抽象の厚みに注意"
             :tags [:conditional]}}]
```

**採用 stack**: なし（プロジェクト固有採用、ADR 必須）

**適用条件**:
- 社内で gRPC 規約がある → protojure、ADR 発行
- 性能要件のみ → HTTP/2 + http-kit で十分

**検討して却下した代替（narrative のみ）**:
- io.grpc/grpc-java 直接: OOP 重い、生成コードが data 駆動でない
- lambdaisland 系 gRPC: 新興、成熟度不足
- HTTP/2 + JSON: ほとんどのユースケースでこちらで十分

### 3.31 PDF / Excel / ドキュメント生成

**採用**:
- **PDF (純 Clojure)**: `clj-pdf/clj-pdf`（data 駆動 DSL）
- **HTML → PDF**: `org.xhtmlrenderer/flying-saucer-pdf-openpdf`（複雑レイアウト時）
- **Excel**: `dk.ative/docjure`（Apache POI の data 駆動ラッパ）
- **CSV**: `org.clojure/data.csv`（公式）

**採用理由**:
- clj-pdf は hiccup 風 data で PDF 構造を記述、Malli で構造契約化可
- docjure はセル編集を map で、副作用は関数末尾に集約可能

```edn
;; lib-catalog
[;; === 採用 ===
 {:purpose  [:report :pdf]
  :ids      {:coord clj-pdf/clj-pdf}
  :judgment {:status :recommended}
  :reasons  {:text "PDF 生成、iText ベース (iText 直接利用は narrative で回避)"}}

 {:purpose  [:report :excel]
  :ids      {:coord dk.ative/docjure}
  :judgment {:status :recommended}
  :reasons  {:text "Apache POI ラッパ、Excel 読み書き"}}

 {:purpose  [:csv]
  :ids      {:coord org.clojure/data.csv :ns "clojure.data.csv"}
  :judgment {:status :recommended}
  :reasons  {:text "CSV の標準"}}

 ;; === 代替と却下 ===
 ;; iText 直接: Java fluent API、data 駆動でない。また AGPL ライセンスで
 ;; SaaS/商用配布と衝突。clj-pdf は iText 経由だが DSL 層で隔離される。
 ;; 直接利用は避ける（narrative のみ、coord 化せず）。
 ;; Apache POI 直接: docjure が Clojure 慣用ラッパ、直接利用は冗長。
 ;; pandoc（外部）: 外部プロセス、本格要件では使うが内部生成には過剰。
 ]
```

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

**採用理由**:
- markdown-clj は data 駆動（parsed tree を vector で扱える）
- data.xml は公式、XXE 対策済み
- YAML は aero 経由で扱うので clj-yaml を使う場面は限定的

```edn
;; lib-catalog
[;; === 採用 ===
 {:purpose   [:markdown]
  :ids       {:coord markdown-clj/markdown-clj :ns "markdown.core"}
  :judgment  {:status :recommended}
  :reasons   {:text "endophile の代替、メンテ継続中"}
  :relations {:conflicts-with [[endophile/endophile "markdown ライブラリは片方に統一、endophile はメンテ停止 :deprecated"]]}}

 ;; === 代替と却下 ===
 ;; endophile: 開発停止、markdown-clj へ移行。
 {:purpose  [:markdown]
  :ids      {:coord endophile/endophile :ns "endophile.core"}
  :judgment {:status :deprecated :severity :superseded :replacement markdown-clj/markdown-clj}
  :reasons  {:text "メンテ停止、markdown-clj へ"
             :tags [:maintenance-stopped]}}

 ;; xerces (独立版) / xalan (独立版): XXE 脆弱性、JDK 付属を使う。
 ;; 禁止レベル（cross-cutting security）。
 {:purpose  [:xml]
  :ids      {:coord xerces/xercesImpl :ns "javax.xml.parsers.xerces"}
  :judgment {:status :deprecated :severity :forbidden :replacement org.clojure/data.xml}
  :reasons  {:text "XXE 脆弱性、bundled 版は JDK 付属のほうが安全"
             :tags [:security]}}

 {:purpose  [:xml]
  :ids      {:coord xalan/xalan}
  :judgment {:status :deprecated :severity :forbidden :replacement org.clojure/data.xml}
  :reasons  {:text "XXE 脆弱性、bundled 版は JDK 付属のほうが安全"
             :tags [:security]}}

 ;; snake-yaml 直接: clj-yaml がラップ済み、直接利用の価値なし。coord 化せず。
 ]
```

**適用条件**:
- 設定ファイル → EDN + aero（YAML/TOML 不要）
- Markdown → markdown-clj（軽量） or Flexmark（拡張必要時）
- 外部 XML API 連携 → data.xml

**採用 stack**: web-api stack（拡張、Markdown レンダリング等）、外部 XML 連携を持つ stack

### 3.33 メール / SMS / プッシュ通知

**採用**:
- **SMTP**: `draines/postal`
- **Transactional Email（SendGrid / SES）**: `hato` 直接 + `jsonista`
- **SMS（Twilio 等）**: `hato` 直接
- **FCM（プッシュ通知）**: `hato` 直接 + Firebase Admin Java SDK（認証のみ）

**採用理由**:
- bot stack と同じ思想（HTTP API は hato 直接が最も寿命長い）
- SMTP は java mail ベースの postal が実績十分

```edn
;; lib-catalog
[{:purpose  [:email :smtp]
  :ids      {:coord draines/postal :ns "postal.core"}
  :judgment {:status :recommended}
  :reasons  {:text "SMTP メール送信、軽量"}}]
```

**検討して却下した代替（narrative のみ）**:
- 各サービス専用 Clojure wrapper: 多くはメンテ停滞
- Spring Mail 等 Java フレームワーク: 重い、Clojure 慣用外

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

**採用理由**:
- aws-api の呼出しパターンを流用、学習コスト最小
- SDK 直接でもブリッジ層を Clojure で薄く書ける

```edn
;; lib-catalog
[;; === 採用 ===
 {:purpose  [:aws :core]
  :ids      {:coord com.cognitect.aws/api :ns "cognitect.aws.api"}
  :judgment {:status :recommended}
  :reasons  {:text "Cognitect aws-api 本体、SDK v2 ベース・純 Clojure"}}

 {:purpose  [:aws :s3]
  :ids      {:coord com.cognitect.aws/s3}
  :judgment {:status :recommended}
  :reasons  {:text "ファイルストレージ、aws-api 系列、サービスごと分割"}}

 ;; === 代替と却下 ===
 ;; Amazonica: メンテ停止傾向、aws-api が後継。
 {:purpose  [:aws]
  :ids      {:coord amazonica/amazonica :ns "amazonica"}
  :judgment {:status :deprecated :severity :superseded :replacement com.cognitect.aws/api}
  :reasons  {:text "Cognitect aws-api が SDK v2 ベース、サービスごと分割で軽量"
             :tags [:replacement-available]}}

 ;; 自作クラウド抽象レイヤー: 疎結合の名目で抽象が漏れる、各クラウドを直接叩く。
 ;; narrative のみ。
 ]
```

**採用 stack**: web-api / worker / batch に S3 要件が出た時

**適用条件**:
- ファイル upload → presigned URL 生成（S3 の場合 aws-api で対応）
- 大容量 → ストリーミング（InputStream）、全メモリ展開禁止
- マルチクラウド抽象 → 採用しない（各クラウドを直接叩く、ADR）

### 3.35 フルテキスト検索

**採用**:
- **PostgreSQL 全文検索**（`tsvector`）→ 既存 next.jdbc で完結、**第一選択**
- **Elasticsearch / OpenSearch**: `mpenet/spandex`（中〜大規模時）

**採用理由**:
- PostgreSQL の tsvector は既存 DB で完結、追加インフラ不要
- spandex は data 駆動クエリ（map で ES クエリを記述）

```edn
;; lib-catalog
[;; === 採用 ===
 {:purpose   [:search :elasticsearch]
  :ids       {:coord mpenet/spandex :ns "qbits.spandex"}
  :judgment  {:status :recommended}
  :reasons   {:text "Elasticsearch クライアント、elastisch の代替"}
  :relations {:conflicts-with [[clojurewerkz/elastisch "Elasticsearch クライアントは片方に統一、elastisch はメンテ停止 :deprecated"]]
              :pairs-with     {:json metosin/jsonista}}}

 ;; === 代替と却下 ===
 ;; elastisch: メンテナンス停止、spandex が現役。
 {:purpose  [:search :elasticsearch]
  :ids      {:coord clojurewerkz/elastisch :ns "clojurewerkz.elastisch"}
  :judgment {:status :deprecated :severity :superseded :replacement mpenet/spandex}
  :reasons  {:text "メンテ停止、spandex が Elasticsearch の現役クライアント"
             :tags [:maintenance-stopped]}}]
```

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

**数値最適化・線形代数（必要時）**:
- `uncomplicate/neanderthal`（EPL 1.0、Dragan Djuric、高性能 BLAS wrapper、GPU/CPU 両対応、Clojure-native data 駆動 API）
- 用途: 行列演算、線形代数、科学技術計算で BLAS 呼出が必要な時。tablecloth では足りない場面

**深層学習（必要時）**:
- **Python 側に委譲**を第一選択。`clj-python/libpython-clj` で PyTorch / TensorFlow を呼び出し、モデル学習は Python、推論境界を Clojure で包む

**遺伝的プログラミング / 自動プログラム合成（研究・特殊用途、opt-in）**:
- `lspector/Clojush`（EPL 1.0、Lee Spector 維持、Push 言語 GP）
- 用途: 進化計算、プログラム自動生成、探索型最適化
- **明示的 opt-in**、§8.2 禁止扱いにしない。通常プロジェクトでは採用しない

**採用理由**:
- scicloj 系は data 駆動が徹底、dataset は map/vector の集合として扱える
- Malli スキーマで dataset 構造を契約化可能
- libpython-clj は Python モデルを関数としてラップ、境界で副作用を隔離できる

```edn
;; lib-catalog
[;; === 採用 ===
 {:purpose  [:ml :dataset]
  :ids      {:coord scicloj/tablecloth :ns "tablecloth.api"}
  :judgment {:status :recommended}
  :reasons  {:text "tech.ml.dataset 上の高レベル API、data 駆動で scicloj 系"}}

 {:purpose  [:ml :pipeline]
  :ids      {:coord scicloj/scicloj.ml}
  :judgment {:status :recommended}
  :reasons  {:text "ML pipeline、scicloj エコシステム"}}

 {:purpose  [:ml :notebook]
  :ids      {:coord scicloj/clay}
  :judgment {:status :recommended}
  :reasons  {:text "Markdown + Clojure + 結果のノートブック、可視化にも使う"}}

 {:purpose  [:ml :integration]
  :ids      {:coord scicloj/noj}
  :judgment {:status :acceptable}
  :reasons  {:text "scicloj 系の integration helper、任意"}}

 {:purpose  [:data :pipeline-dataset]
  :ids      {:coord techascent/tech.ml.dataset :ns "tech.v3.dataset"}
  :judgment {:status :recommended}
  :reasons  {:text "表形式データの効率処理、tablecloth の基盤"}}

 {:purpose  [:python-interop]
  :ids      {:coord clj-python/libpython-clj :ns "libpython-clj.python"}
  :judgment {:status :recommended}
  :reasons  {:text "Python interop、PyTorch/TensorFlow を境界で扱う"}}

 ;; === 代替と却下 ===
 ;; incanter: メンテ低迷、scicloj エコシステムが活発。
 {:purpose  [:ml :data]
  :ids      {:coord incanter/incanter :ns "incanter.core"}
  :judgment {:status      :deprecated
             :severity    :superseded
             :replacement [scicloj/tablecloth scicloj/scicloj.ml]}
  :reasons  {:text "メンテ低迷、scicloj エコシステムが活発"
             :tags [:maintenance-stopped :philosophy-mismatch]}}

 ;; deeplearning4j Clojure wrapper: OOP 重い、mutation 多用、Clojure 慣用外。
 {:purpose  [:ml :dl]
  :ids      {:coord dl4clj/dl4clj :ns "dl4clj"}
  :judgment {:status :deprecated :severity :superseded :replacement clj-python/libpython-clj}
  :reasons  {:text "libpython-clj 経由で PyTorch/TensorFlow が現実的"
             :tags [:philosophy-mismatch]}}

 ;; cortex: 開発停止、libpython-clj 経由へ。
 {:purpose  [:ml :dl]
  :ids      {:coord thinktopic/cortex :ns "cortex"}
  :judgment {:status :deprecated :severity :superseded :replacement clj-python/libpython-clj}
  :reasons  {:text "メンテ停止、libpython-clj 経由へ"
             :tags [:maintenance-stopped]}}

 ;; SMILE (Haifeng Li の ML エンジン): 機能は広範（分類・クラスタリング・NLP 他）
 ;; だが GPL 3.0 ライセンスで SaaS/商用配布と衝突。研究・社内閉じ利用のみ
 ;; ADR 発行の上で条件付き採用可。
 {:purpose  [:ml]
  :ids      {:coord   com.github.haifengl/smile-core
             :aliases [haifengl/smile]
             :ns      "smile.classification"}
  :judgment {:status          :conditional
             :applicable-when "研究・社内閉じ利用のみ、SaaS / 商用配布不可、ADR 必須"
             :replacement     [scicloj/tablecloth clj-python/libpython-clj]}
  :reasons  {:text "GPL 3.0 ライセンスで SaaS 配布と衝突"
             :tags [:license :conditional]}}

 ;; PyTorch 直接 JNI: libpython-clj で包める範囲、直接 JNI の価値薄い。
 ;; narrative のみ、coord 化せず。
 ]
```

**採用 stack**: ml stack または data-pipeline stack 拡張

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

**採用理由**:
- LLM プロバイダ API は REST であり、hato 直接で足りる
- プロンプトは Clojure の map で表現、data 駆動
- Malli で request/response スキーマ契約化が自然
- プロバイダ切替（OpenAI ↔ Anthropic ↔ Ollama）は wrapper 層で吸収

```edn
;; lib-catalog
[;; === 採用 ===
 {:purpose  [:llm :openai]
  :ids      {:coord wkok/openai-clojure}
  :judgment {:status :acceptable}
  :reasons  {:text "OpenAI 互換 API 向け wrapper、hato 直接実装も可"}}

 {:purpose  [:templating :text]
  :ids      {:coord selmer/selmer :ns "selmer.parser"}
  :judgment {:status :recommended :version "1.12.0"}
  :reasons  {:text "Django 風テンプレート、プロンプト管理等"}}

 ;; === 代替と却下 ===
 ;; langchain4j Clojure 移植: OOP/継承多用、LLM 界隈は変動早く、薄い HTTP の
 ;; 方が長寿命。採用しない (coord 化せず narrative のみ)。
 ;; llama-clj (native binding): native 依存、ビルド複雑化。条件付き採用は可。
 ;; 専用 agent framework (LangGraph 等 Clojure 移植): 2025 時点で成熟品なし。
 ;; narrative のみ。
 ]
```

**採用 stack**: llm-app stack

### 3.38 画像・映像・音声処理

**採用**:
- **画像処理（軽）**: JDK 標準 `javax.imageio` + 薄い純 Clojure ヘルパ
- **画像処理（複雑）**: `org.bytedeco/opencv-platform`（OpenCV Java wrapper）
- **SVG 生成**: `dali/dali`（data 駆動、hiccup 風）
- **動画トランスコード**: `com.github.kokorin.jaffree/jaffree`（FFmpeg shell out wrapper）
- **OCR**: Tesseract（`net.sourceforge.tess4j`）を **shell out で呼び出し**、インプロセス JNI は避ける
- **音声**: JDK 標準 + 必要時 Java ライブラリ（`overtone` は創作用途のみ）

**採用理由**:
- JDK 標準で済む軽量処理は追加依存を入れない
- OpenCV は Java wrapper 経由が実績十分（JavaCV より stable）
- dali は SVG を hiccup 風 data で記述、Clojure 慣用で Malli 統合も自然
- shell out（jaffree / Tesseract）は JNI 同居によるクラッシュリスクを回避

**検討して却下した代替（narrative のみ、coord 化せず）**:
- JavaCV 直接: OpenCV-Clojure 経由が慣用
- Imaging 系の古い Clojure wrapper: メンテ停滞
- overtone（プロダクション）: 創作用途のみ、副作用が内側に漏れる

```edn
;; lib-catalog
[;; === 採用 ===
 {:purpose  [:media :opencv]
  :ids      {:coord org.bytedeco/opencv-platform}
  :judgment {:status :recommended}
  :reasons  {:text "OpenCV Java wrapper、コンピュータビジョン用途"}}

 {:purpose  [:media :svg]
  :ids      {:coord dali/dali}
  :judgment {:status :recommended}
  :reasons  {:text "SVG 生成、data 駆動・hiccup 風 DSL"}}

 {:purpose  [:media :ffmpeg]
  :ids      {:coord com.github.kokorin.jaffree/jaffree}
  :judgment {:status :recommended}
  :reasons  {:text "FFmpeg shell out wrapper、動画トランスコード"}}

 {:purpose  [:media :ocr]
  :ids      {:coord net.sourceforge.tess4j/tess4j}
  :judgment {:status :acceptable}
  :reasons  {:text "Tesseract、shell out 推奨（インプロセス JNI はクラッシュリスク）"}}]
```

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

**採用理由**:
- GraalVM Native Image で起動時間 100ms 以下、メモリ 50MB 程度に削減可能
- Pi4J は Java 界のデファクト、薄く Clojure で包む

```edn
;; lib-catalog
[;; === 採用 ===
 {:purpose   [:edge :gpio]
  :ids       {:coord com.pi4j/pi4j-core}
  :judgment  {:status :recommended :version "2.x"}
  :reasons   {:text "Raspberry Pi GPIO の標準、Pi4J v2"}
  :relations {:pairs-with {:backend com.pi4j/pi4j-plugin-pigpio
                           :mqtt    org.eclipse.paho/org.eclipse.paho.client.mqttv3}}}

 {:purpose   [:edge :gpio :pigpio]
  :ids       {:coord com.pi4j/pi4j-plugin-pigpio}
  :judgment  {:status :recommended :version "2.x"}
  :reasons   {:text "pigpio バックエンド、Pi4J v2 のネイティブ実装"}
  :relations {:pairs-with {:core com.pi4j/pi4j-core}}}

 {:purpose   [:messaging :mqtt]
  :ids       {:coord org.eclipse.paho/org.eclipse.paho.client.mqttv3
              :ns    "org.eclipse.paho.client.mqttv3"}
  :judgment  {:status :recommended :version "1.2.5"}
  :reasons   {:text "Paho MQTT Java 公式、machine-head の代替"}
  :relations {:conflicts-with [[clojurewerkz/machine_head "MQTT クライアントは片方に統一、machine-head は :deprecated"]]
              :pairs-with     {:edge com.pi4j/pi4j-core}}}

 ;; === 代替と却下 ===
 ;; machine-head (clojurewerkz): Paho MQTT Java を直接使うほうが依存が薄い。
 {:purpose  [:messaging :mqtt]
  :ids      {:coord   clojurewerkz/machine_head
             :aliases [clojurewerkz/machine-head]
             :ns      "clojurewerkz.machine-head"}
  :judgment {:status :deprecated :severity :superseded
             :replacement org.eclipse.paho/org.eclipse.paho.client.mqttv3}
  :reasons  {:text "Paho MQTT Java を直接使うほうが依存が薄い"
             :tags [:replacement-available]}}

 ;; Babashka: 本テンプレート方針で不採用（shell script 優先）。narrative のみ。
 ;; ESP32 等の非 JVM 環境: 射程外（ADR で Rust/Go 推奨を記録）。narrative のみ。
 ]
```

**採用 stack**: edge stack

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

#### 3.40.1 Electric Clojure（射程外）

**採用方針**: **射程外**。本テンプレートの哲学と部分整合するが、以下 3 点で採用不可:

1. **ClojureScript 前提**: `hyperfiddle/electric` は full-stack DAG reactive 設計で、ブラウザ側で cljs ランタイムが動く。本テンプレートは JVM 単独
2. **macro 重依存**: Electric DSL は macro で DAG を合成する設計、`CODING_GUIDE §1.2 過剰な defprotocol` / `§11 マクロ 3 条件` の境界を超える
3. **API 変動が激しい**: 2024-2026 時点で v2→v3 など大幅改訂が続き、長期安定性が未確保（G4 寄り）

**採用時の扱い**: Electric を使う要件が出たら、本テンプレート外の別フレームワークとして扱い、ADR で明示的に射程外と記録。

```edn
;; lib-catalog
[;; === 射程外 ===
 {:purpose  [:web :fullstack]
  :ids      {:coord   com.hyperfiddle/electric
             :aliases [hyperfiddle/electric]
             :ns      "hyperfiddle.electric"}
  :judgment {:status :scope-excluded}
  :reasons  {:text "§3.40.1 射程外、cljs 前提・macro 重依存・API 変動"
             :tags [:philosophy-mismatch :conditional]}}]
```

### 3.41 シリアライゼーション

**採用**:
- **EDN（既定）**: Clojure 標準、設定・永続化・IPC 全般で第一選択
- **Nippy**: `com.taoensso/nippy`（EPL、Taoensso 安定メンテ、高速 binary、Clojure data 構造をそのまま serialize）

**採用理由**:
- EDN は Clojure data の自然な serialize 形式、human-readable で debug 容易
- Nippy は Redis payload / ファイルスナップショット / IPC で EDN より高速（binary）、Clojure の record / regex / date 等も透過的に扱える
- Taoensso ライブラリは Clojure エコシステムで信頼性が高い

```edn
;; lib-catalog
[;; === 採用 ===
 {:purpose   [:serialization :nippy]
  :ids       {:coord com.taoensso/nippy :ns "taoensso.nippy"}
  :judgment  {:status :recommended}
  :reasons   {:text "高速バイナリシリアライザ、Fressian の代替"}
  :relations {:conflicts-with [[org.clojure/data.fressian "バイナリストレージは片方に統一、fressian は :conditional（既存継続のみ）"]]
              :pairs-with     {:wire-format com.cognitect/transit-clj}}}

 {:purpose   [:serialization :transit]
  :ids       {:coord com.cognitect/transit-clj :ns "cognitect.transit"}
  :judgment  {:status :recommended}
  :reasons   {:text "Cognitect Transit、Clojure 標準のバイナリ転送"}
  :relations {:pairs-with {:storage com.taoensso/nippy}}}

 ;; === 代替と却下 ===
 ;; data.fressian: 使えるが Nippy の方が速度・対応型で優位。既存採用プロジェクトは継続可。
 {:purpose   [:serialization]
  :ids       {:coord org.clojure/data.fressian :ns "clojure.data.fressian"}
  :judgment  {:status          :conditional
              :applicable-when "既存採用プロジェクトは継続可、新規は nippy"
              :replacement     com.taoensso/nippy}
  :reasons   {:text "nippy が速度・型対応で優位"
              :tags [:conditional :replacement-available]}
  :relations {:conflicts-with [[com.taoensso/nippy "バイナリストレージは片方に統一、nippy が :recommended"]]}}

 ;; ProtoBuf: IDL 駆動で data 駆動でない、§3.30 gRPC で言及済。narrative のみ。
 ;; Kryo 直接: OOP 寄り、Nippy が Clojure 向けに最適化済。narrative のみ。
 ;; Java Serialization 直接: セキュリティリスク (RCE 脆弱性)、設計思想不整合。
 ;; 特定 coord なし (JDK 標準クラスの直接利用)、§8 narrative で注意喚起。
 ]
```

**採用 stack**: web-api / worker / batch / saas / llm-app（必要性に応じ）

**適用条件**:
- 設定ファイル、小〜中規模データ → EDN（aero / pr-str / read-string）
- Redis payload、キャッシュスナップショット、IPC → Nippy
- 外部公開 API → JSON（jsonista）、内部利用は EDN/Nippy
- 暗号化・認証付きシリアライズ → Nippy の `:password` オプション

### 3.42 パフォーマンス最適化補助

**採用**:
- **ベンチマーク**: `criterium/criterium`（§9.1 で既言及、JIT warm-up 込み統計計測）
- **JVM ヒープ計測**: `clj-memory-meter/clj-memory-meter`（オブジェクトサイズ実測）
- **順序保持 map/set**: `org.flatland/ordered`（insertion-order を保つ map/set、Clojure 標準 map は順序保証なし）
- **Transducer**（既に標準）: 中間 seq 削減、§9.3 で既言及
- **VisualVM / JFR**（JDK 付属）: プロファイリング

**採用理由**:
- 「測ってから最適化」（CODING_GUIDE §9.1）を機械化するための最小セット
- `org.flatland/ordered` は順序保証が必要な場面（`insertion-order` キーが意味を持つシリアライズ等）で有用
- clj-memory-meter は heap profiling の計測を 1 行で、性能ボトルネック特定に必須

```edn
;; lib-catalog
[{:purpose  [:dev :benchmark]
  :ids      {:coord criterium/criterium :ns "criterium.core"}
  :judgment {:status :recommended}
  :reasons  {:text "JIT warm-up 込み統計計測、測ってから最適化"}}

 {:purpose  [:dev :memory-measure]
  :ids      {:coord com.clojure-goes-fast/clj-memory-meter :ns "clj-memory-meter.core"}
  :judgment {:status :recommended}
  :reasons  {:text "オブジェクトサイズ実測、heap profiling を 1 行で"}}

 {:purpose  [:data :ordered-map]
  :ids      {:coord org.flatland/ordered :ns "flatland.ordered.map"}
  :judgment {:status :recommended}
  :reasons  {:text "insertion-order を保つ map/set、Clojure 標準 map は順序保証なし"}}]
```

**採用 stack**: dev-tools stack 拡張（常時併用推奨）

**適用条件**:
- 性能問題発生時 → criterium で計測、JFR で全体プロファイル
- 大量データで GC 過剰 → clj-memory-meter で object size 計測
- 設定ファイルで key 順序が意味を持つ（文書生成等）→ `org.flatland/ordered`
- **鉄則**: 推測で最適化しない。criterium / JFR で証跡を取ってから変更（§1.2.5 失敗早期検知）

**検討して却下した代替（narrative のみ）**:
- メモリ計測の自作 JVM reflection: clj-memory-meter が薄いラッパで十分
- LinkedHashMap interop 自作: ordered が Clojure 慣用ラッパ
- YourKit / JProfiler（商用プロファイラ）: 有償、JFR + VisualVM で足りる範囲では不要

### 3.43 代替アーキテクチャプラットフォーム（条件付き採用）

**採用方針**: 本テンプレートは **Integrant + Reitit + next.jdbc + mulog** の組合せを標準とするが、以下の代替フレームワークを**条件付きで採用可**とする。いずれも ADR 必須。

**採用理由（条件付き採用を許容する立場）**:
- 本テンプレート標準で対応困難な要件（convention-over-configuration を好むチーム、DB + streaming + queue + ML の超大規模統合要件）が存在し得る
- 哲学と部分整合する代替は ADR 付きで採用可、ただし **Polylith brick 構造との統合**は個別に検証が必要
- 標準脱却の判断は新規チームより経験あるチームで行うのが安全

**条件付き採用候補**:

```edn
;; lib-catalog
[;; === 条件付き採用 ===
 ;; Biff: §3.22 マルチテナントで採用済、saas stack 向け framework。
 ;; 本節では重複登録しない（§3.22 参照）。

 ;; Duct: MIT ライセンス、Integrant ベースで本テンプレート哲学と部分整合。
 ;; ただし duct.core/module 機構は直接 Integrant より抽象が厚く、Polylith brick
 ;; 構造との統合でレイヤが過剰になりやすい。convention-over-configuration を好む
 ;; チーム向け、新規チームには直接 Integrant 推奨。
 {:purpose   [:platform :framework]
  :ids       {:coord duct/core}
  :judgment  {:status          :conditional
              :applicable-when "convention-over-configuration を好むチーム、直接 Integrant より抽象が欲しい場合、ADR 必須"
              :replacement     integrant/integrant}
  :reasons   {:text "直接 Integrant より抽象が厚い、Polylith brick と重なる"
              :tags [:conditional]}
  :relations {:conflicts-with [[com.biffweb/biff "代替プラットフォームは片方に統一、Biff と Duct は framework 全体性で衝突"]
                               [com.rpl/rama "代替プラットフォームは片方に統一、Duct と Rama は framework 全体性で衝突"]]}}

 ;; Rama: 商用ライセンス (community edition は小規模無料)、framework 重量、
 ;; ベンダーロックイン。DB + streaming + queue + ML 統合要件の超大規模のみ。
 ;; 多くは XTDB + worker + batch で代替可。
 {:purpose   [:platform]
  :ids       {:coord   com.rpl/rama
              :aliases [rama/rama]
              :ns      "com.rpl.rama"}
  :judgment  {:status          :conditional
              :applicable-when "商用ライセンス取得済み、不可避な大規模要件で採用判断、ADR 必須"
              :replacement     [com.xtdb/xtdb-api]}
  :reasons   {:text "商用ライセンス、ベンダーロックイン。XTDB + worker + batch stack で多くは代替可"
              :tags [:license :conditional :philosophy-mismatch]}
  :relations {:conflicts-with [[com.biffweb/biff "代替プラットフォームは片方に統一、Biff と Rama は framework 全体性で衝突"]
                               [duct/core "代替プラットフォームは片方に統一、Duct と Rama は framework 全体性で衝突"]
                               [integrant/integrant "Rama は独自 lifecycle を持ち、Integrant と全体 framework として衝突"]]}}

 ;; === 代替（採用しない） ===
 ;; Electric Clojure: §3.40.1 で射程外宣言済、同節で登録済。
 ;; Fulcro: cljs 前提、本テンプレート射程外。narrative のみ。
 ;; re-frame: cljs 前提、本テンプレート射程外。narrative のみ。
 ;; Kit (io.github.kit-clj/kit): 類似位置づけだが、brick 構造との流儀差が大きい。
 ;; narrative のみ。
 ]
```

**Duct の扱い詳細**:
- Integrant ベースで本テンプレート哲学と部分整合（data 駆動、副作用隔離）
- ただし `duct.core/module` 機構は本テンプレートが採用する直接 Integrant より抽象が厚く、Polylith brick 構造との統合でレイヤが過剰になりやすい
- 採用時は ADR で「直接 Integrant ではなく Duct を採用する理由」を明記、brick 内部で Duct モジュールを component 化して包む

**Rama の扱い詳細**:
- 設計は data 駆動で本テンプレート哲学と部分整合（EDN-like、Nathan Marz の一貫性）
- ただし: (1) 商用ライセンス（本番大規模では有償）、(2) framework 重量、(3) ベンダーロックイン、(4) 学習コスト、(5) Polylith brick 構造との統合例が少ない
- **§8.2 条件付き非推奨**として扱い、採用する場合は ADR + `DESIGN.md §8.3` に「Rama 採用の不可避性」を明記
- 代替可能性: XTDB（DB + bitemporal） + worker stack（queue） + batch stack（streaming 的）の組合せで多くのケースを代替可

**採用 stack**: 代替として採用した場合、既存 stack（web-api 等）を置き換える形。複数 stack 併用はしない（framework の全体性と衝突）。

### 3.44 GraphQL API

**採用**: `com.walmartlabs/lacinia`（Clojure 界デファクト、スキーマを EDN で宣言）

**採用理由**:
- Clojure コミュニティでのデファクト GraphQL 実装
- スキーマを EDN として宣言、data 駆動で Malli と親和
- Ring 統合は `lacinia-pedestal` または自作 middleware

```edn
;; lib-catalog
[;; === 採用 ===
 {:purpose  [:graphql]
  :ids      {:coord com.walmartlabs/lacinia :ns "com.walmartlabs.lacinia"}
  :judgment {:status :recommended :version "1.2.2"}
  :reasons  {:text "Clojure 界デファクト、スキーマを EDN 宣言、Malli 親和性"}}

 {:purpose  [:graphql :ring-integration]
  :ids      {:coord com.walmartlabs/lacinia-pedestal}
  :judgment {:status :acceptable :version "1.3"}
  :reasons  {:text "Lacinia-Ring 統合、または自作 middleware でも可"}}

 ;; === 代替と却下 ===
 ;; graphql-java 直接: Lacinia の data 駆動抽象を活かせない、OOP 重量。
 ;; narrative のみ、coord 化せず。
 ]
```

**採用 stack**: graphql-api stack（他 stack で REST と併設する場合も本 §3.44 参照）

**適用条件**:
- GraphQL 主体の API → lacinia + lacinia-pedestal
- REST と併設 → web-api stack + lacinia
- N+1 問題 → Lacinia の superlifter / batching で対応

### 3.45 デスクトップ GUI

**採用**: `io.github.humbleui/humbleui`（暫定、開発途上）、安定性優先なら `cljfx/cljfx`

**採用理由**:
- humbleui: Skia ベースで高性能、宣言的 API、Rich Hickey 系エコシステム
- cljfx: JavaFX ラッパ、成熟、宣言的、リソース消費やや大
- **暫定採用**の humbleui は API 変動リスク、プロダクション投入は慎重に

```edn
;; lib-catalog
[;; === 採用 ===
 {:purpose   [:desktop :gui]
  :ids       {:coord io.github.humbleui/humbleui}
  :judgment  {:status :recommended :version "0.2.0"}
  :reasons   {:text "Skia ベース、宣言的 API、API 変動リスクあり (暫定採用)"}
  :relations {:conflicts-with [[cljfx/cljfx "デスクトップ GUI は片方に統一、cljfx は :acceptable（安定性優先時）"]
                               [seesaw/seesaw "デスクトップ GUI は片方に統一、seesaw は :conditional（レガシー保守のみ）"]]}}

 {:purpose   [:desktop :gui :javafx]
  :ids       {:coord cljfx/cljfx :ns "cljfx.api"}
  :judgment  {:status :acceptable}
  :reasons   {:text "JavaFX 宣言的ラッパ、成熟。安定性優先なら採用を判断し ADR 記録"}
  :relations {:conflicts-with [[io.github.humbleui/humbleui "デスクトップ GUI は片方に統一、humbleui が :recommended"]
                               [seesaw/seesaw "デスクトップ GUI は片方に統一、seesaw は :conditional"]]}}

 ;; === 代替と却下 ===
 ;; seesaw (Swing ラッパ): 軽量・古典的だが、モダン UI には弱い。
 ;; 新規採用不可、レガシー保守のみ条件付き採用。
 {:purpose   [:desktop :gui]
  :ids       {:coord seesaw/seesaw :ns "seesaw.core"}
  :judgment  {:status          :conditional
              :applicable-when "レガシー保守"
              :replacement     [io.github.humbleui/humbleui cljfx/cljfx]}
  :reasons   {:text "Swing ベースで古い、新規は humbleui / cljfx"
              :tags [:conditional]}
  :relations {:conflicts-with [[io.github.humbleui/humbleui "デスクトップ GUI は片方に統一、humbleui が :recommended"]
                               [cljfx/cljfx "デスクトップ GUI は片方に統一、cljfx は :acceptable"]]}}

 ;; membrane: 純 Clojure、クロスプラットフォーム挑戦的。ADR を伴う採用可。
 ;; 生 Swing / AWT を proxy で多用する構成: 宣言的でなく、テスト性が低い。
 ;; メンテナンス停滞した古い Clojure GUI ラッパ: 最新 JVM との非整合リスク。
 ;; 以上は coord 化せず narrative のみ。
 ]
```

**採用 stack**: desktop stack

**適用条件**:
- 新規プロジェクト、モダン UI → humbleui（API 変動注意）
- 安定性優先（業務アプリ等）→ cljfx
- クロスプラットフォーム challenging → membrane（ADR 必須）
- レガシー保守 → seesaw（条件付き採用、新規不可）

### 3.46 メッセージキュー

**採用**: キュー選択はインフラ要件に依存するが、各キューに対する推奨クライアントを揃える。

**採用理由**:
- AWS SQS: aws-api 系列で worker stack の他 AWS サービスと一貫性
- Kafka: Confluent Platform 連携可、業界標準
- RabbitMQ: AMQP 標準実装
- Redis Stream / Pub-Sub: carmine を流用、小規模に有効
- PostgreSQL LISTEN/NOTIFY: next.jdbc で直接、小規模なら有効（専用 lib 不要）

```edn
;; lib-catalog
[;; === 採用 ===
 {:purpose  [:messaging :queue :sqs]
  :ids      {:coord com.cognitect.aws/sqs}
  :judgment {:status :recommended}
  :reasons  {:text "AWS SQS キュー、aws-api 系列"}}

 {:purpose  [:messaging :queue :kafka]
  :ids      {:coord fundingcircle/jackdaw}
  :judgment {:status :recommended}
  :reasons  {:text "Kafka、Confluent Platform 連携可"}}

 {:purpose  [:messaging :queue :rabbitmq]
  :ids      {:coord com.novemberain/langohr}
  :judgment {:status :recommended}
  :reasons  {:text "RabbitMQ AMQP"}}

 {:purpose  [:messaging :queue :redis]
  :ids      {:coord com.taoensso/carmine :ns "taoensso.carmine"}
  :judgment {:status :acceptable}
  :reasons  {:text "Redis Stream / Pub-Sub。cache と同ライブラリ"}}

 ;; === 代替と却下 ===
 ;; キューライブラリの独自ラッパを多層に重ねる構成: 障害時の挙動が追いにくい、
 ;; 各キューの公式推奨クライアントを直接使う。narrative のみ。
 ]
```

**採用 stack**: worker stack

**適用条件**:
- AWS 環境で SQS → com.cognitect.aws/sqs
- 高スループット・順序保証 → Kafka (jackdaw)
- RabbitMQ 既存インフラ → langohr
- 小規模（Redis 既存）→ carmine
- PostgreSQL 中心 → LISTEN/NOTIFY を next.jdbc で

### 3.47 Bot プラットフォーム

**採用方針**: プラットフォーム別 Clojure クライアント（Discord）または純 Clojure HTTP 呼び出し（Telegram / Slack）

**採用理由**:
- Telegram / Slack: Bot API は HTTP REST、hato 直接で十分実装可（wrapper 不要）
- Discord: リアルタイム voice / event gateway が必要なため、専用 Clojure lib（discljord）が有効

```edn
;; lib-catalog
[;; === 採用 ===
 {:purpose  [:bot :discord]
  :ids      {:coord org.suskalo/discljord}
  :judgment {:status :recommended}
  :reasons  {:text "Discord Bot 向け、Clojure 特化"}}

 ;; === 代替と却下 ===
 ;; Telegram / Slack 向け: 自作 HTTP 呼び出し（hato 直接）で十分。
 ;; 専用 Clojure wrapper は多くはメンテ停滞（narrative のみ）。
 ;; 旧世代の bot フレームワーク系: メンテナンス停滞のもの多数、純 Clojure の
 ;; HTTP 呼び出しで実装するほうが寿命が長い。narrative のみ。
 ]
```

**採用 stack**: bot stack

**適用条件**:
- Telegram / Slack → hato 直接実装（§3.11 採用）
- Discord → discljord
- 独自プロトコル → hato + manual WebSocket（§3.16 ring-websocket で可）

### 3.48 SSR / HTML テンプレート

**採用**: `rum/rum`（React 互換）、軽量用途は `hiccup/hiccup`、テンプレートエンジンは `selmer/selmer`

**採用理由**:
- rum: React 互換コンポーネントを Clojure で書ける、Biff と組み合わせ標準
- hiccup: Clojure data として HTML を組み立てる最小 DSL、軽量
- selmer: Django 風テンプレート、プロンプト・メール文面等のテンプレート管理に適合

```edn
;; lib-catalog
[;; === 採用 ===
 {:purpose  [:web :ssr]
  :ids      {:coord rum/rum :ns "rum.core"}
  :judgment {:status :recommended}
  :reasons  {:text "SSR テンプレート、React 互換の概念"}}

 {:purpose  [:web :html-dsl]
  :ids      {:coord hiccup/hiccup :ns "hiccup.core"}
  :judgment {:status :acceptable}
  :reasons  {:text "Clojure data として HTML を組み立てる DSL。rum の代替軽量版"}}

 ;; enlive: HTML 走査・変換（テンプレーティングというより scraping・selector 駆動）。
 ;; 既存 HTML を CSS selector 風に変換する用途で有効、templating の代替ではない。
 {:purpose  [:web :html-transform]
  :ids      {:coord enlive/enlive :ns "net.cgrand.enlive-html"}
  :judgment {:status :acceptable}
  :reasons  {:text "HTML 走査・selector 変換、scraping / HTML after-edit 用途"}}

 ;; selmer は §3.37 LLM で採用済、本節では重複登録せず narrative のみで言及。
 ]
```

**採用 stack**: saas stack（rum）、web-api stack（必要時 hiccup）、llm-app stack（selmer）

**適用条件**:
- Biff ベース SaaS → rum
- 軽量 HTML 生成 → hiccup
- テキストテンプレート（プロンプト・メール）→ selmer

### 3.49 その他（stack 未分類）

**採用**: core.async（非同期チャネル、cross-cutting）

**採用理由**:
- 明確な機能分類（§3.1〜§3.48）に属さないが、本テンプレートの運用で言及される lib を集約
- 多くは cross-cutting or 基盤（core.async の CSP、leiningen の非推奨扱い等）
- 独立節を立てるには個別 lib が少ない・再帰的に分類困難なものを「その他」にまとめる

```edn
;; lib-catalog
[;; === 採用 ===
 ;; core.async: Clojure の CSP チャネル、非同期パイプライン。
 ;; manifold の代替として data-pipeline / web-api / worker で活用。
 {:purpose   [:async]
  :ids       {:coord org.clojure/core.async :ns "clojure.core.async"}
  :judgment  {:status :recommended}
  :reasons   {:text "CSP チャネル、manifold の代替"}
  :relations {:conflicts-with [[manifold/manifold "非同期抽象は片方に統一、manifold は設計思想不整合"]]
              :pairs-with     {:scheduling jarohen/chime}}}

 ;; === 代替と却下 ===
 ;; manifold: aleph と同系統の設計不整合、core.async / promise.cljc へ。
 {:purpose  [:async]
  :ids      {:coord manifold/manifold :ns "manifold.deferred"}
  :judgment {:status :deprecated :severity :superseded :replacement org.clojure/core.async}
  :reasons  {:text "aleph と同系統の設計不整合、core.async / promise.cljc へ"
             :tags [:philosophy-mismatch]}}

 ;; leiningen (新規プロジェクト): tools.deps 移行。既存 Lein ベースは条件付き継続可。
 {:purpose  [:build]
  :ids      {:coord leiningen/leiningen}
  :judgment {:status          :conditional
             :applicable-when "既存 Lein ベースプロジェクトの段階移行"
             :replacement     org.clojure/tools.deps}
  :reasons  {:text "新規プロジェクトは tools.deps"
             :tags [:conditional :replacement-available]}}

 ;; instaparse: BNF / PEG ベースのパーサジェネレータ。独自 DSL や設定言語の
 ;; パーサを書くときの第一選択、Clojure で代替少ない。
 {:purpose  [:parser]
  :ids      {:coord instaparse/instaparse :ns "instaparse.core"}
  :judgment {:status :acceptable}
  :reasons  {:text "BNF/PEG パーサジェネレータ、独自 DSL / 設定言語用途"}}

 ;; === 代替と却下 ===
 ;; slingshot (throw+/try+): 例外に map を載せる拡張だが、ex-info / ex-data が
 ;; Clojure 標準として整備された今は冗長。副作用深化を伴い関数合成を阻害する。
 ;; 設計思想としても ex-info に統一（CODING_GUIDE §7.3 例外処理パターン）。
 {:purpose  [:exception]
  :ids      {:coord slingshot/slingshot :ns "slingshot.slingshot"}
  :judgment {:status :deprecated :severity :superseded :replacement org.clojure/clojure}
  :reasons  {:text "ex-info / ex-data で代替可、throw+/try+ は副作用深化を伴う"
             :tags [:philosophy-mismatch :replacement-available]}}
 ]
```

**採用 stack**: 全 stack（cross-cutting / 基盤）

### 3.50 時刻処理

**採用**: `tick/tick` を第一選択、`clojure.java-time` を :acceptable な代替。

**採用理由**:
- tick: data 駆動 API、Instant / Duration / Period が map-friendly で Malli 統合容易。JDK `java.time` の上に薄い Clojure 慣用 DSL を被せる
- clojure.java-time: JDK `java.time` の直接薄ラッパ。依存最小、学習コスト低。tick 特有 DSL を導入しない選択肢
- JDK 8 以降は `java.time` が標準、Joda-Time 依存（clj-time）は不要
- どちらを採用するかはプロジェクト方針（data 駆動重視なら tick、依存最小なら java-time）。両立は避ける（同一プロジェクトで混在すると読み手が混乱）

```edn
;; lib-catalog
[;; === 採用 ===
 {:purpose   [:time]
  :ids       {:coord tick/tick :ns "tick.core"}
  :judgment  {:status :recommended :version "1.0"}
  :reasons   {:text "data 駆動 java.time ラッパ、Malli 統合容易"}
  :relations {:conflicts-with [[clojure.java-time/clojure.java-time "時刻処理は片方に統一"]
                               [clj-time/clj-time "時刻処理は片方に統一、clj-time は Joda-Time ベースで非推奨"]]}}

 {:purpose   [:time :jdk-wrapper]
  :ids       {:coord clojure.java-time/clojure.java-time :ns "java-time.api"}
  :judgment  {:status :acceptable}
  :reasons   {:text "JDK java.time 薄ラッパ、依存最小・学習コスト低"}
  :relations {:conflicts-with [[tick/tick "時刻処理は片方に統一"]
                               [clj-time/clj-time "時刻処理は片方に統一、clj-time は Joda-Time ベースで非推奨"]]}}

 ;; === 代替と却下 ===
 ;; clj-time (Joda-Time ベース): JDK 8+ で java.time が標準化された現在は不要。
 ;; Joda-Time 依存が重く、timezone 処理や生成 API が java.time に劣る局面もある。
 ;; 既存コードでの継続使用は可能だが、新規採用は避ける。
 {:purpose  [:time]
  :ids      {:coord clj-time/clj-time :ns "clj-time.core"}
  :judgment {:status      :deprecated
             :severity    :superseded
             :replacement [tick/tick clojure.java-time/clojure.java-time]}
  :reasons  {:text "Joda-Time ベース、JDK java.time で不要。tick / java-time へ"
             :tags [:replacement-available :maintenance-stopped]}}
 ]
```

**採用 stack**: 全 stack（時刻処理は cross-cutting）

**適用条件**:
- data 駆動を徹底したい / Malli 統合 → tick
- 依存最小 / JDK 直接利用の発想 → clojure.java-time
- レガシー Joda-Time 保守のみ → clj-time（新規不可）
- tick と java-time を同プロジェクトで混用しない（片方に統一）

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

**複数の性格を持つプロジェクト**（例: Web API + バッチ併設）は**複数 stack の併用**で対応する。採用 stack は DESIGN.md §8.3 に記録し、各 brick の deps.edn にそれぞれの推奨ライブラリ（§3 機能別節）を反映する。brick の構造は Polylith の通常通り（components / bases / projects）。


### 4.2 stack 記載有無の判定

本節は `COLLABORATION_GUIDE.md` §2.2 の「記載あり / 記載なし」判定の参照先である。LLM は stack 選定時に本節で分類してから、承認レベルを決める。

| 状況 | 判定 | 次の行動 |
|---|---|---|
| §4.1 の「プロジェクトの性格」に該当し、必要機能が §3 機能別節の推奨カタログにある | **記載あり** | 該当 stack と §3 節を引用して L1 提案。承認後に brick deps.edn へ反映 |
| §4.1 の stack 名には該当しないが、必要な機能カテゴリが §3 機能別節にある | **記載あり（機能別採用）** | stack 名を新設せず、該当 §3 節の推奨ライブラリを L1 提案。採用 stack 欄には主 stack + 補足として記録 |
| §4.1 にも §3 機能別節にも対応するカテゴリがない | **記載なし（未記載領域）** | `CLAUDE.md` §6.3 に従って第一原理から判断材料を整理。採用可否は人間が決定し、採用後は ADR を発行 |
| §3 に推奨があるが、プロジェクト固有理由で別ライブラリを使いたい | **推奨からの逸脱** | `STACK_GUIDE.md` §5.4 に従い、逸脱理由を ADR と DESIGN.md §8.3 に記録 |
| §3 で deprecated / scope-excluded / 採用不可と明示されている | **記載あり（非推奨・対象外）** | 推奨として扱わない。必要なら未記載領域ではなく「非推奨からの逸脱」として人間判断を求める |

「記載なし」は「LLM が自由に決めてよい」という意味ではない。本文書のメモリーが未整備な領域なので、LLM は判断材料・代替・疲労最小化との関係を整理し、人間の L0 判断に渡す。

### 4.3 複数 stack の組み合わせ

stack は排他的ではなく**タグ的な概念**。複数を組み合わせ可能。Polylith の構造では、組み合わせは以下のように実現する：

- **同一 base が複数 stack の性格を持つ**: 当該 base の deps.edn に、採用する全 stack の推奨ライブラリ（§3 各機能節）をマージして記述
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

最初の brick（component / base）を作成したら、採用 stack の推奨ライブラリ（§3 の該当機能節の `;; lib-catalog` block）を **brick の deps.edn** に反映する。LLM はユーザ承認後、CLAUDE.md §2 禁止事項（依存追加）に従って進める：

1. `clj -M:poly create component name:<domain>` でドメイン component を作成
2. `clj -M:poly create base name:<entry>` で entry base を作成（ユーザ承認必須）
3. 作成された **base の deps.edn**（`bases/<entry>/deps.edn`）に、採用 stack の §3 該当推奨ライブラリを記述
   - 例: web-api stack なら §3.4 HTTP サーバ・ルーティング / §3.5 JSON / §3.6 永続化 / §3.7 構造化ロギング 等の採用エントリを反映
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

1. 追加・変更の理由を **ADR として発行**（`adr/NNNN-add-stack-<name>.md` または `adr/NNNN-modify-stack-<name>.md`）
2. §3 の該当機能節の採用推奨に従って、影響する brick の deps.edn を更新
3. §6 整合性チェック
4. DESIGN.md §8.3 の採用 stack 欄を更新

stack から離脱する場合：

1. 離脱理由を **ADR として発行**（`adr/NNNN-remove-stack-<name>.md`）
2. 該当 brick の deps.edn から不要依存を削除（tools.namespace / antq / `poly check` で参照不整合を検出）
3. 関連する brick コード（Integrant key 定義等）を削除または修正
4. §6 整合性チェック
5. DESIGN.md §8.3 の採用 stack 欄を更新

### 5.4 推奨から外れる場合（テンプレート推奨の上書き）

§3 の推奨がプロジェクト要件に合わない場合（例: mulog を timbre に差し替え、組織方針で特定ライブラリの採用等）：

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

**STACK_GUIDE.md §3 は強制一致ではなく推奨カタログ**。brick の deps.edn が §3 と完全一致している必要はない。ただし以下を確認する：

1. 採用した stack に対応する **§3 機能別節の「採用時の確認事項」相当**を満たしているか（機能カテゴリの充足、設定ファイルの存在等）
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

brick deps.edn が採用 stack に対応する §3 機能別推奨を満たしているかの機械検証は、**将来的にスクリプト化**される予定（MAINTAINERS_GUIDE.md §5.9 継続充実項目）。現時点では文書上の確認事項として運用する。検証の粒度は「機能カテゴリの充足」であり、「具体ライブラリ名の一致」ではない（推奨の強制ではなく、漏れの防止）。

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
5. **本文書 §3 の該当機能節の `;; lib-catalog` block に採用エントリを追加**
6. 派生プロジェクトの場合、該当 brick の deps.edn を更新（テンプレート保守の場合、本文書の更新のみで完結）
7. §6 整合性チェック
8. DESIGN.md §8.3 の採用 stack 欄を更新（stack 構成が変わった場合）

---

## 8. 禁止・非推奨ライブラリ（理由タグ定義と追加手順のみ）

本節は禁止・非推奨ライブラリの**理由タグ定義**（§8.0）と**追加手順**（§8.3）を扱う。具体的な禁止・非推奨エントリは **§3 各機能節の `;; lib-catalog` block「代替と却下」節**に分散配置済（一覧索引は §8.1/§8.2 を参照）。

§3 の「代替と却下」と §8 の性格:

- **§3 「代替と却下」節**: 特定機能で採用候補として比較・却下した（他機能では有用かもしれない中立的却下）。機能文脈を持つ。
- **§8 禁止・非推奨**: **どこでも**採用すべきでない（理由が明確）。cross-cutting。本節は理由タグと手順のみ残し、エントリは §3 の機能節内に記載する（採否を機能ごとに一望できる SSOT のため）。

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

### 8.1 / 8.2 への記載は §3 各節へ移行済

禁止・非推奨ライブラリの lib-catalog エントリは、**すべて §3 各機能節の `;; lib-catalog` block に分配済**。本節は §8.0 理由タグ定義と §8.3 追加手順のみ残す。

具体 lib の採否 / 却下理由を確認する場合の索引:

- ロギング (log4j 禁止 / timbre 非推奨) → §3.7
- XML (xerces / xalan 禁止) → §3.32
- JSON (org.json 禁止 / data.json / cheshire 非推奨) → §3.5
- 検証 (spec.alpha 非推奨) → §3.3
- 設定管理 (environ / immuconf 非推奨) → §3.2
- ライフサイクル (Component / Mount 非推奨) → §3.1
- HTTP (compojure / pedestal / bidi / aleph / immutant 非推奨・条件付き) → §3.4
- HTTP クライアント (clj-http 条件付き) → §3.11
- 認証 (friend 非推奨) → §3.12
- DB (java.jdbc / Korma 非推奨) → §3.6
- マイグレーション (Flyway / Liquibase / joplin 条件付き・非推奨) → §3.25
- メトリクス (metrics-clojure 系非推奨) → §3.14
- スケジューリング (at-at / Quartz / tea-time 非推奨・条件付き) → §3.15
- リトライ (robert.bruce 非推奨) → §3.17
- i18n (tower 非推奨) → §3.20
- E2E (clj-webdriver 非推奨) → §3.27
- Markdown (endophile 非推奨) → §3.32
- 全文検索 (elastisch 非推奨) → §3.35
- AWS (amazonica 非推奨) → §3.34
- ML (incanter / dl4clj / cortex / SMILE 非推奨・条件付き) → §3.36
- LLM (langchain4j narrative) → §3.37
- MQTT (machine-head 非推奨) → §3.39
- シリアライゼーション (data.fressian 条件付き / Java Serialization narrative) → §3.41
- 代替プラットフォーム (Rama / Duct 条件付き / Electric 射程外) → §3.43
- GUI (seesaw 条件付き) → §3.45（新設）

**narrative のみ（特定 coord を持たない注意喚起）**:
- **Keycloak + adapter**（:auth）: エンタープライズ認証ならば可、重量級で小〜中規模は過剰
- **Memcached クライアント**（:cache）: 特定 coord なし、Redis (`com.taoensso/carmine`) が機能的に優位
- **OpenTelemetry 自動計装**（:metrics）: mulog とメトリクスを分離し一貫性が下がる
- **Babashka 本番基盤**（:scripting）: shell script 代替のみ可、本番コードは uberjar + GraalVM Native Image
- **iText 直接利用**（:pdf）: AGPL ライセンスで SaaS/商用配布と衝突、`clj-pdf` や Flying Saucer (openpdf 版) が安全
- **langchain4j Clojure 移植**（:llm）: hato + wkok/openai-clojure で直接実装推奨
- **Java Serialization 直接** (`java.io.Serializable`)（:serialization）: RCE 脆弱性歴、com.taoensso/nippy へ
- **leiningen（新規プロジェクト、:build）**: 条件付き、tools.deps 推奨

これらは特定 coord を持たない（カテゴリ / 技術 / 方針レベル）ため EDN からは外し、人間向け注意喚起として narrative にとどめる。

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
| `../.llm/memory/adr/` | stack 採用・削除時は ADR を発行 |
