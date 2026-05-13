# POLYLITH_GUIDE.md — Polylith 構造運用ガイド

本文書は **`../CLAUDE.md` §1 原理のうち「機械化」（§1.2.1）を Polylith という構造で実装する**ための詳細資料である。
CLAUDE.md が常時制約、CODING_GUIDE.md が Clojure の書き方を規定するのに対し、本文書は**Polylith 特有の構造判断と手順**を扱う。

| 項目 | 内容 |
|---|---|
| **対象** | Polylith 構造を触る人間と LLM |
| **使うタイミング** | brick 追加時、境界判断時、`poly check` 失敗時 |
| **正本性** | Polylith 構造判断と手順の正本 |
| **読み方** | まず冒頭の位置づけと前提を読み、その後に該当手順節だけ読む |

## いつ本文書を読むか

- 新規 brick（component / base / project）を追加する時
- `poly check` / `poly test` でエラーが出て対処に迷う時
- コンポーネント境界の設計判断（切るか、統合するか、共通化するか）に迷う時
- `workspace.edn` や `tag-patterns` の運用を変更する時

## Polylith の位置づけ（原理との接続）

Polylith は **../CLAUDE.md §1.1.3 副作用の隔離 + §1.2.1 機械化** の実装である：

- **`interface.clj` による境界**: ドメインとアダプタの依存方向を構造で強制
- **`poly check` による機械検証**: 規約違反を人間の注意力ではなく CLI で検出
- **`poly test` による影響範囲テスト**: §1.2.2 ループ短縮の実現（変更箇所に応じて必要最小限のテストだけ実行）

---

## 越境ユースケースの機械化（上位原理）

複数 entity / 複数 entrypoint をまたぐ処理（以下「越境ユースケース」）は、Polylith 構造の上で**見落とされやすく再発見コストの高い領域**である。本テンプレートはこれを `../CLAUDE.md §1.2.1 機械化` と `§1.2.2 ループ短縮` の合成として最優先の機械化対象に位置づける。

**上位原理**: 越境ユースケースは、人間の記憶ではなく **ns 配置（抽出）・REPL helper（起動）・境界テスト（検証）** の 3 つの機械化手段で再現可能にする。新しい越境ユースケースに遭遇した時は、以下の 3 派生それぞれに該当する作業が完了しているか自己点検する。

### 派生 1: 抽出 — orchestration ns

越境処理は handler.clj に書かず、`<uc>-orchestration` という名前付き ns へ抽出する。配置基準は entrypoint の共有度に依存する条件型判定（§2.2.1）：

- **単一 entrypoint 専用**（HTTP API だけが呼ぶ等）→ `bases/<base>/src/.../<uc>_orchestration.clj` に sub-ns 配置
- **複数 entrypoint 共有**（API + CLI + UI が同じ処理を呼ぶ）→ `components/<uc>-orchestration/` として component 昇格

handler.clj は入出力変換のみに薄く保ち、実処理は orchestration へ委譲する。

### 派生 2: 起動 — `(safe-reset!) → (seed-all!)` 2 行原則

越境ユースケースの開発・PR smoke test では、その境界を **2 行で立ち上げられる状態**を維持する：

```clojure
(safe-reset!)   ; refresh / lifecycle reset
(seed-all!)     ; seed helper による fixture 投入
```

- 配置: `development/src/dev/fixtures.clj`（派生プロジェクト側で実体化）
- 命名: 全体を担う `(seed-all!)` と、UC 単位の `(seed-<uc>!)` の 2 階層
- `seed-all!` は `(doseq [f [seed-<uc1>! seed-<uc2>! ...]] (f))` で組み立てる

本テンプレート配布時には `dev/fixtures.clj` は置かない（brick 未配置と整合）。派生プロジェクトが brick を生やしたタイミングで本規約に従って実体化する。詳細手順は §7.4。

### 派生 3: 検証 — 越境 tx の原子性 assert

越境 tx を持つ orchestration には、**原子性を主張する境界テストを 1 つ以上置く**。これは `../CLAUDE.md §1.1.1 全域性` の境界契約の延長として独立に成立する規律であり、「越境処理を書いた」という事実から自動的に発生する義務である。

検証**手段**は採用 DB に依存する。テンプレートは中立を保ち、規約化するのは **「assert する」** 規律のみ：

| 採用 DB | 原子性 assert の典型実装 |
|---|---|
| next.jdbc（RDBMS） | tx handle を 1 つに固定し、orchestration 内で取得 connection の `System/identityHashCode` 一致を assert、または `with-transaction` ネスト検出 |
| XTDB | 各 entity の `_system_from` metadata 一致を assert（`(= 1 (count (set system-froms)))`） |
| Datomic | 単一 tx report に期待 datom がすべて含まれることを assert、または期待 datom の `:tx` が同一であることを assert |

派生プロジェクトはこの 3 派生を「越境ユースケースを書く時のチェックリスト」として運用する。**該当する派生をすべて満たす**。具体的には、抽出（派生 1）と起動（派生 2）はすべての越境ユースケース（read-only な集計や外部 API 連携を含む）に適用する。検証（派生 3）は**越境 tx を持つ orchestration**（複数 entity の write を 1 tx で行う処理）にのみ必須。

### 派生間の運用順序

派生 3（test）は派生 2（fixture）が提供する境界 state を前提とするため、**fixture を REPL で実際に観察してから test の precondition を確定する**。fixture 未観察の想像 state で Test Plan を書くと、後追いの fixture 変更が既書 test の設計を反復させる（`../CLAUDE.md §1.2.2 ループ短縮`違反）。派生 1（orchestration 配置）は派生 2 / 3 の観察後に調整可能。具体的な手順は §7.4.1。

---

## 1. brick 種別と責務

| 種別 | 責務 | §1.1 原則のどれを担うか |
|---|---|---|
| **component** | 再利用可能な機能ブロック。`interface.clj` で公開し、`core.clj` で実装。ドメインロジック・共通ユーティリティ・外部システム向けアダプタとしても成立 | ドメイン実装は **不変性 + 全域性**、副作用を持つ実装は **副作用隔離** |
| **base** | アプリ外部に対する公開 API（REST / CLI / Lambda / gRPC / GraphQL など）を担う入口。内部実装は薄く、実体の処理は component に委譲する（公式: base は public API を公開する special brick） | **副作用隔離**（副作用の入口・配線を集約） |
| **project** | デプロイ単位。components + bases の組合せを `:local/root` で参照 | **機械化**（配線のみ、ライブラリ依存禁止） |
| **development** | 開発用の統合 REPL。全 brick を単一プロセスで読み込む | **ループ短縮**（§1.2.2） |

---

## 2. brick のコード例（**この節が書き方の正本**）

本テンプレートには初期状態で brick サンプルは**配布されない**（特定ドメイン仮定を避けるため）。
代わりに、各種別の**標準的な書き方**を以下にコード例として示す。
`poly create` で生成した brick を埋める時、**この節を参照して同じ流儀で書く**。独自の流儀を発明しない。

### 2.1 component — 純粋ドメイン

#### `components/<domain>/deps.edn`

```clojure
;; ドメイン系 component の deps.edn（基本方針）
;; ★ このテンプレートでは、コンポーネントは再利用と置換が目的の単位として扱う。
;;   まずは I/O を持たない実装寄せを基本とする。
;;   ただし外部連携をまとめて差し替え可能にする目的で“アダプタ component”を
;;   置く場合は、next.jdbc や hato 等の依存追加を許容する（公式の位置づけと整合）。
;; ※ バージョンは参考値。正本は STACK_GUIDE.md §2.1 / §3 機能別節（コピペ時に要確認）
{:paths ["src" "resources"]
 :deps  {org.clojure/clojure {:mvn/version "1.12.0"}
         metosin/malli       {:mvn/version "0.16.4"}}
 :aliases
 {:test
  {:extra-paths ["test"]
   :extra-deps  {org.clojure/test.check     {:mvn/version "1.1.1"}
                 nubank/matcher-combinators {:mvn/version "3.9.1"}}}}}
```

#### `components/<domain>/src/myorg/myapp/<domain>/interface.clj`

```clojure
(ns myorg.myapp.<domain>.interface
  "<domain> コンポーネントの公開 API。
   外部からはこの名前空間のみが require 可能。
   実装は core 以下に配置。`m/=>` 関数契約は境界である
   ここに集約する（§1.1.1 全域性）。"
  (:require
   [malli.core :as m]
   [myorg.myapp.<domain>.core :as core]))

;; --- 型 ---
(def Entity       core/Entity)
(def CreateInput  core/CreateInput)

;; --- 関数契約（境界契約の集約）---
(m/=> create   [:=> [:cat CreateInput] Entity])
(m/=> validate [:=> [:cat :any] :boolean])

;; --- API（委譲）---
(defn create [input] (core/create input))
(defn validate [entity] (core/validate entity))
```

#### `components/<domain>/src/myorg/myapp/<domain>/core.clj`

```clojure
(ns myorg.myapp.<domain>.core
  "<domain> コンポーネントの実装本体。
   他コンポーネントから require 禁止（poly check で検出）。
   `m/=>` 契約は interface.clj 側に集約するため、本ファイルでは
   スキーマ定義と純粋関数のみを置く。"
  (:require
   [malli.core :as m]))

;; --- スキーマ（§1.1.1 全域性の境界契約）---
(def Entity
  [:map
   [:<domain>/id    :uuid]
   [:<domain>/name  [:string {:min 1 :max 100}]]
   [:<domain>/created-at inst?]])

(def CreateInput
  [:map
   [:name [:string {:min 1 :max 100}]]])

;; --- 純粋関数 ---
(defn create
  "Entity を構築する純粋関数。永続化は行わない（§1.1.3 副作用の隔離）。"
  [{:keys [name]}]
  {:<domain>/id         (random-uuid)
   :<domain>/name       name
   :<domain>/created-at (java.time.Instant/now)})

(defn validate
  "Entity がスキーマに適合するかを返す。"
  [entity]
  (m/validate Entity entity))

;; --- リッチコメント ---
(comment
  (create {:name "test"})
  (validate (create {:name "test"})))
```

#### `components/<domain>/test/myorg/myapp/<domain>/interface_test.clj`

```clojure
(ns myorg.myapp.<domain>.interface-test
  "テストは原則 interface 経由で書く（実装変更に頑健）。"
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [clojure.test.check.clojure-test :refer [defspec]]
   [clojure.test.check.properties :as prop]
   [malli.core :as m]
   [malli.dev :as mdev]
   [malli.dev.pretty :as mpretty]
   [malli.generator :as mg]
   [matcher-combinators.test]
   [myorg.myapp.<domain>.interface :as d]))

(defn with-malli-instrumentation [f]
  (mdev/start! {:report (mpretty/reporter)})
  (try
    (f)
    (finally
      (mdev/stop!))))

(use-fixtures :once with-malli-instrumentation)

(deftest create-test
  (testing "create は必須項目から Entity を構築する"
    (let [e (d/create {:name "Alice"})]
      (is (match? {:<domain>/name       "Alice"
                   :<domain>/id         uuid?
                   :<domain>/created-at inst?}
                  e))
      (is (m/validate d/Entity e)))))

;; プロパティテスト: Malli スキーマから自動生成される generator を活用
(defspec create-always-yields-valid-entity 100
  (prop/for-all [input (mg/generator d/CreateInput)]
    (m/validate d/Entity (d/create input))))
```

`poly test` の通過は高速な回帰確認であり、契約検証完了そのものではない。
`m/=>` 契約をテスト実行中にも有効化したい場合、test fixture で Malli instrumentation を `:once` で有効化する。
この fixture は `with-malli-instrumentation` のような named `defn` として定義し、`use-fixtures :once` から参照する。匿名 fixture はテンプレート規約外。
fixture の形は `mdev/start!` で開始し、`(try (f) (finally (mdev/stop!)))` で必ず停止する完全形を使う。
テンプレート側では `.llm/scripts/check-test-instrumentation.sh` が `interface_test.clj` の fixture 欠落を検査し、`check-workspace-integrity.sh` から実行される。

### 2.2 base — HTTP API エントリの例

以下は **HTTP API エントリの具体例**。採用ライブラリは STACK_GUIDE の機能別節で確認し、HTTP API 以外の entry base では必要な機能カテゴリに合わせて読み替える。ライフサイクル管理が不要なライブラリ配布や単発 CLI では lifecycle helper 不要、`-main` 直列起動で構成する。
∵ STACK_GUIDE.md §3

#### `bases/<entry>/deps.edn`

```clojure
;; HTTP エントリ base の deps.edn
;; HTTP API entry base の構成例（Ring + Reitit + Integrant + Malli + mulog を束ねる）
;; ※ バージョンは参考値。正本は STACK_GUIDE.md §2.1 / §3 機能別節（コピペ時に要確認）
{:paths ["src" "resources"]
 :deps  {org.clojure/clojure         {:mvn/version "1.12.0"}
         metosin/malli               {:mvn/version "0.16.4"}
         ring/ring-core              {:mvn/version "1.13.0"}
         ring/ring-jetty-adapter     {:mvn/version "1.13.0"}
         metosin/reitit              {:mvn/version "0.7.2"}
         metosin/reitit-ring         {:mvn/version "0.7.2"}
         metosin/reitit-malli        {:mvn/version "0.7.2"}
         metosin/muuntaja            {:mvn/version "0.6.10"}   ; reitit の format-middleware で使用（§2.3 参照）
         metosin/jsonista            {:mvn/version "0.3.11"}
         integrant/integrant         {:mvn/version "0.13.1"}
         aero/aero                   {:mvn/version "1.1.6"}
         com.brunobonacci/mulog      {:mvn/version "0.9.0"}
         com.brunobonacci/mulog-json {:mvn/version "0.9.0"}   ; :console-json publisher 実装（§2.3 config.edn 参照）
         ;; 使う component への依存
         poly/<domain>               {:local/root "../../components/<domain>"}}}
```

#### `bases/<entry>/src/myorg/myapp/<entry>/core.clj`（エントリ）

```clojure
(ns myorg.myapp.<entry>.core
  "<entry> ベースのエントリポイント。uberjar 実行時の -main。"
  (:require
   [aero.core :as aero]
   [clojure.java.io :as io]
   [com.brunobonacci.mulog :as mulog]
   [integrant.core :as ig]
   [myorg.myapp.<entry>.system])   ; defmethod 登録のため require
  (:gen-class))

(defn -main [& _args]
  (let [profile (keyword (or (System/getenv "APP_PROFILE") "prod"))
        cfg     (aero/read-config (io/resource "config.edn") {:profile profile})
        system  (ig/init cfg)]
    (mulog/log ::started :profile profile)
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. ^Runnable #(do (mulog/log ::stopping)
                                              (ig/halt! system))))
    @(promise)))
```

#### `bases/<entry>/src/myorg/myapp/<entry>/system.clj`（Integrant 配線）

```clojure
(ns myorg.myapp.<entry>.system
  "Integrant defmethod 集約。config.edn の各キーに対応する init-key / halt-key! を定義。"
  (:require
   [com.brunobonacci.mulog :as mulog]
   [integrant.core :as ig]
   [myorg.myapp.<entry>.handler :as handler]
   [ring.adapter.jetty :as jetty]))

(defmethod ig/init-key ::logger [_ {:keys [type] :as cfg}]
  (mulog/start-publisher! (assoc cfg :type type)))

(defmethod ig/halt-key! ::logger [_ stop-fn]
  (when stop-fn (stop-fn)))

(defmethod ig/init-key ::handler [_ deps]
  (handler/build-app deps))

(defmethod ig/init-key ::server [_ {:keys [handler port]}]
  (mulog/log ::starting-server :port port)
  (jetty/run-jetty handler {:port port :join? false}))

(defmethod ig/halt-key! ::server [_ ^org.eclipse.jetty.server.Server server]
  (.stop server))
```

#### `bases/<entry>/src/myorg/myapp/<entry>/handler.clj`（Reitit ルータ）

```clojure
(ns myorg.myapp.<entry>.handler
  "★ 他コンポーネントは必ず interface 経由で require する。"
  (:require
   [muuntaja.core :as muuntaja]
   [myorg.myapp.<domain>.interface :as d]
   [reitit.coercion.malli]
   [reitit.ring :as ring]
   [reitit.ring.coercion :as rrc]
   [reitit.ring.middleware.muuntaja :as muuntaja-mw]
   [reitit.ring.middleware.parameters :as parameters]))

(defn- create-handler [_deps]
  (fn [{:keys [parameters]}]
    {:status 201
     :body   (d/create (:body parameters))}))

(defn- health-handler [_req]
  {:status 200 :body {:status "ok"}})

(defn routes [deps]
  [["/health" {:get health-handler}]
   ["/<domain>"
    {:post {:summary    "<domain> を作成する"
            :parameters {:body d/CreateInput}
            :responses  {201 {:body d/Entity}}
            :handler    (create-handler deps)}}]])

(defn build-app [deps]
  (ring/ring-handler
   (ring/router
    (routes deps)
    {:data {:coercion   reitit.coercion.malli/coercion
            :muuntaja   muuntaja/instance
            :middleware [parameters/parameters-middleware
                         muuntaja-mw/format-middleware
                         rrc/coerce-exceptions-middleware
                         rrc/coerce-request-middleware
                         rrc/coerce-response-middleware]}})
   (ring/create-default-handler)))
```

#### 2.2.1 越境処理の抽出 — `<uc>-orchestration` ns

複数 entity を跨ぐ処理や、複数 entrypoint（HTTP / CLI / UI 等）から共有される処理を handler.clj に直書きしない。**`<uc>-orchestration` という名前付き ns に抽出**する（上位原理「越境ユースケースの機械化」派生 1）。

**配置基準（条件型）**:

- **単一 entrypoint 専用**: orchestration が 1 つの base からしか呼ばれないことが明らかなら `bases/<entry>/src/myorg/myapp/<entry>/<uc>_orchestration.clj` に sub-ns で置く
- **複数 entrypoint 共有**: 2 つ以上の base / entrypoint から同じ orchestration を呼ぶなら、即座に `components/<uc>-orchestration/` として component に昇格させる

「将来 UI からも呼ぶかもしれない」という推測で先に component 化しない（YAGNI）。実際に 2 つ目の呼び出し元が発生した時点で昇格させる。逆に、最初から複数 entrypoint で呼ぶ予定が明確なら、最初から component に置く。

**抽象例（base 内 sub-ns 版）**:

```clojure
;; bases/<entry>/src/myorg/myapp/<entry>/<uc>_orchestration.clj
(ns myorg.myapp.<entry>.<uc>-orchestration
  "<uc> ユースケース：複数 entity を跨ぐ orchestration。
   本 base の handler.clj から呼ばれる（単一 entrypoint 専用）。
   別 entrypoint からも呼ぶ必要が生じた時点で components/<uc>-orchestration/ へ昇格する。"
  (:require
   [myorg.myapp.<domain-a>.interface :as a]
   [myorg.myapp.<domain-b>.interface :as b]))

(defn process [deps inputs]
  ;; 越境 tx の組み立て：1 つの tx で a と b を更新
  ;; 検証側（派生 3）でこの原子性を境界テストにより assert する
  ...)
```

**handler 側**:

```clojure
;; handler.clj は薄く保つ：入出力変換のみ
(:require
 [myorg.myapp.<entry>.<uc>-orchestration :as uc])

(defn- <uc>-handler [deps]
  (fn [{:keys [parameters]}]
    {:status 201
     :body   (uc/process deps (:body parameters))}))
```

`refactor → feature` の 2 コミット分割（既存 handler から orchestration を抽出するコミット → orchestration を拡張するコミット）は一般原則に従う。本節では追加規約化しない。
∵ ../CLAUDE.md §8.3
∵ ../CLAUDE.md §1.2.3

### 2.3 project — デプロイ単位

#### `projects/<deploy>/deps.edn`（★ `:local/root` のみ、ライブラリ依存禁止）

```clojure
{:paths []
 :deps  {poly/<entry>  {:local/root "../../bases/<entry>"}
         poly/<domain> {:local/root "../../components/<domain>"}}
 :aliases
 {:build
  {:deps      {io.github.clojure/tools.build {:git/tag "v0.10.5" :git/sha "2a21b7a"}}
   :ns-default build}}}
```

#### `projects/<deploy>/build.clj`（uberjar 構築）

```clojure
(ns build
  "<deploy> project の uberjar 構築。`clj -T:build uber` で実行。"
  (:require [clojure.tools.build.api :as b]))

(def lib       'myorg/<deploy>)
(def main-ns   'myorg.myapp.<entry>.core)
(def version   (format "0.1.%s" (b/git-count-revs nil)))
(def class-dir "target/classes")
(def uber-file (format "target/%s-%s-standalone.jar" (name lib) version))

(defn- basis [] (b/create-basis {:project "deps.edn"}))

(defn clean [_] (b/delete {:path "target"}))

(defn uber [_]
  (clean nil)
  (b/copy-dir
   {:src-dirs   (-> (basis) :paths
                    (concat ["resources"
                             "../../components/<domain>/resources"
                             "../../bases/<entry>/resources"]))
    :target-dir class-dir})
  (b/compile-clj {:basis (basis) :ns-compile [main-ns] :class-dir class-dir})
  (b/uber {:class-dir class-dir :uber-file uber-file :basis (basis) :main main-ns})
  (println "Built:" uber-file))
```

#### `projects/<deploy>/resources/config.edn`（aero 経由の Integrant 設定）

```clojure
{:myorg.myapp.<entry>.system/logger
 {:type :console-json}

 :myorg.myapp.<entry>.system/handler
 {}

 :myorg.myapp.<entry>.system/server
 {:handler #ig/ref :myorg.myapp.<entry>.system/handler
  :port    #profile {:dev  3000
                     :test 0
                     :prod #long #or [#env PORT 8080]}}}
```

### 2.4 development/src/dev/user.clj の config 関数実装例

初期配布の `dev/user.clj` では `config` 関数が placeholder。最初の project を作成後、以下のように実装：

```clojure
(defn config []
  (aero/read-config (io/resource "config.edn") {:profile :dev}))
```

`:require` に `[aero.core :as aero]` と `[clojure.java.io :as io]` を追加する。

**前提**: `projects/<deploy>/resources` がワークスペースルート `deps.edn` の `:dev :extra-paths` に追加されていること（BOOTSTRAP_GUIDE.md §2.5）。これにより `io/resource "config.edn"` が classpath 経由で読める。本番（`core.clj` の `-main`）と開発で読み込み方法が統一される。

**代替**: 何らかの理由で `:dev :extra-paths` に追加できない場合、`(io/file "projects/<deploy>/resources/config.edn")` でファイルパス直接指定も動作する（ただし本番と開発で読み込み方法が違う点に留意）。

---

## 3. 新規 brick 追加手順

### 3.1 新規コンポーネント追加（**ユーザ承認必須**）

**根拠**: component はドメイン境界または再利用境界を 1 単位追加する操作であり、局所的ではあるが公開境界と依存方向に影響する。base / project ほど配備構成全体へは波及しないため、権限は承認必須 (L1) に置く。

```bash
clj -M:poly create component name:<name>
```

これで以下が自動生成される：

```
components/<name>/
├── deps.edn
├── resources/
├── src/myorg/myapp/<name>/
│   ├── interface.clj
│   └── core.clj
└── test/myorg/myapp/<name>/
    └── interface_test.clj
```

その後の手順：

1. `deps.edn` に必要な依存を追加（**ライブラリ依存はここに書く**、project には書かない）
2. 本文書 §2.1 component コード例を参照して以下を埋める：
   - `core.clj`: Malli スキーマ + 純粋関数（`m/=>` 契約は置かない）
   - `interface.clj`: core への薄い委譲 + `m/=>` 契約集約（境界契約、§1.1.1）（100 行超えたら実装漏れ、core に戻す）
   - `interface_test.clj`: clojure.test + プロパティテスト
3. Integrant key を提供する場合は entry base の `system.clj`（§2.2 のコード例）の defmethod 集約に追加
4. project の `deps.edn` に `:local/root` で登録
5. development の `deps.edn` の `:dev` エイリアス `:extra-paths` にソースパスを追加（`components/<name>/src` 等）
6. **development の `deps.edn` の `:dev` エイリアス `:extra-deps` に `:local/root` で登録**（`poly/<name> {:local/root "components/<name>"}`）。これにより brick deps.edn の `:deps` が推移的解決され、REPL で利用可能になる
7. `clj -M:poly check` で構造検証
8. `clj -M:poly test project:<project-name>` で特定 project 配下の brick テストを実行（全 project・全 brick を流すなら `clj -M:poly test :all`）

### 3.2 新規ベース追加（**人間専権**）

**根拠**: base の追加は entrypoint・起動経路・I/O 配線の単位を増やす操作であり、component 追加より構造影響が大きい。実装追加ではなく配備・運用構成の判断を含むため、人間専権 (L0) とする。

```bash
clj -M:poly create base name:<name>
```

用途の例：HTTP API（既存 entry base と）は別の CLI、Lambda 関数、バッチジョブなど。

その後、`bases/<name>/deps.edn` に必要なライブラリを追加、本文書 §2.2 base コード例を雛形として `core.clj`/`system.clj` などを実装。

### 3.3 新規プロジェクト追加（**人間専権**）

**根拠**: project の追加はデプロイ単位・ビルド単位・CI 単位を新設する操作であり、ワークスペース全体の配備構成を変える。不可逆性と波及範囲が大きいため、人間専権 (L0) とする。

```bash
clj -M:poly create project name:<name>
```

デプロイ単位を分ける時に使う（例: Web API と worker を別の uberjar にする等）。

その後：

1. `projects/<name>/deps.edn` に `:local/root` で components / bases を参照
2. `projects/<name>/build.clj` を新規作成（本文書 §2.3 の build.clj 例を雛形として使う）
3. `workspace.edn` の `:projects` に登録
4. CI に組込（lint / format / poly check / test / build uber）

---

## 4. コンポーネント境界の判断（**最重要の判断領域**）

**自己判断禁止**。迷ったら必ず `../.llm/memory/QUESTIONS.md` に Q を立てる。

### 4.1 新規コンポーネントを切るべき兆候

- **ドメインの言葉で名前が付けられる**（`user`, `order`, `inventory` など、業務用語で呼べる）
- **外部 API を公開しないが、再利用可能な共通ロジックを持つ**（複数 base/component で使う）
- 複数の base / component から同じロジックを参照する
- I/O の種類が独立している（別の DB、別の外部サービス）
- 責務が独立しており、変更理由が別々
- 外部サービス向けのラッパーを「置換可能なアダプタ」として共有したい（外部依存の抽象化）

補足（公式との整合）: コンポーネントはドメイン寄りが基本だが、認証・DB/外部API などの
"integration point" を component として切り出す設計も公式文書は許容している。

### 4.2 切るべきでない兆候

- 機能単位で切ろうとしている（`create-user`, `delete-user` を別 component にしたい等） → それはドメインとして一つの component
- interface が 1〜2 関数しかない → まだ切るには早い
- 他 component から呼ばれる予定がない → base の内部で関数として切り出すだけで足りる
- 「将来使うかもしれない」という予感だけ → YAGNI

### 4.3 迷う場合の原則（公式観点）

- **外部公開 API の責務を持つなら base**（HTTP/Lambda/CLI など）
- **再利用可能なロジックや交換可能な連携層なら component**
- **base から見て delegate が自然なら component、base 自身がプロトコルを構成するなら base**

### 4.4 Polylith の最大の罠：切りすぎ

Polylith の例題は教育目的で粒度が細かい傾向がある。それを真似ると小さな interface が乱立し、逆に保守性が下がる。実用の判定軸は：

- **ドメインモデルの一塊**
- **外部システムとのアダプタ一単位**
- **横断的関心事一単位**

この 3 分類で粒度を判定する。

### 4.5 コンポーネントの統合・分割（**承認必須**）

既存コンポーネントの統合・分割は **../CLAUDE.md §2 で禁止事項**。影響が甚大なので必ず人間承認を得る。
判断が必要な場合は Q を立てる：

```
## Q-YYYY-MM-NNN: <component-a> と <component-b> の統合検討

**文脈**: 両コンポーネントの interface が X と Y を相互参照する実装になってきた
**選択肢**:
 A. 統合する（共通性が高い）
 B. 共通部分を新規 component に抽出（関心事分離）
 C. 現状維持（結合は許容範囲）
**推奨**: B（Polylith 流儀に沿う）
**影響範囲**: ...
```

---

## 5. Polylith 特有の頻出誤りと対処

| 誤り | 兆候 | 対処 |
|---|---|---|
| 内部 NS への直接 require | `poly check`: `Cannot reference internal namespaces from another brick` | `interface.clj` に関数追加し、interface 経由に変更 |
| 循環参照 | `poly check`: circular dependency | 共通部分を新規 component に抽出（`poly create component`）。既存 2 component を直接合流させない |
| project の deps が肥大化 | `projects/*/deps.edn` にライブラリが直書きされている | components / bases に移動。project の `deps.edn` は `:local/root` のみにする（本文書 §2.3 参照） |
| base 同士の参照 | `poly check`: base→base 依存 | 共通ロジックを component に抽出 |
| `interface.clj` の肥大化 | 100 行超 | 実装が interface に漏れている。`core.clj` に切り出して interface は薄い委譲に戻す |
| `poly test` が走らない | 変更検知が効かない | `git status` で未コミット変更を確認。Polylith は git 基準で diff を取る |
| development project が壊れる | `(reset)` で循環エラー | Integrant `defmethod` の重複・キー名衝突を確認 |
| brick 作成を手作業で行う | `poly check`: brick 認識されず | 削除して `poly create` で作り直す |
| library 依存が development にだけある | 本番ビルドで依存不足 | 本来の所属 component / base の `deps.edn` に移動 |
| `stable` タグが動かない | `poly info` で stable point が出ない | `workspace.edn` の `:tag-patterns` を確認。CI で `git tag stable-<timestamp>` が打たれているか確認（Polylith 公式では `poly create tag` のような専用コマンドは存在せず、git tag を使う） |

---

## 6. workspace.edn の運用

### 6.1 最重要キー

```clojure
{:top-namespace "myorg.myapp"            ; 全 brick の名前空間プレフィックス
 :interface-ns  "interface"              ; interface ファイル名（固定）
 :projects      {"development" {:alias "dev"}
                 "<deploy>"    {:alias "<deploy>"}}  ; DESIGN.md §8.4 で定めた project / deploy 名
 :vcs           {:name "git" :auto-add false}
 :tag-patterns  {:stable  "^stable-.*"     ; CI 通過時に打つ安定タグ
                 :release "^v[0-9].*"}}    ; 本番リリースタグ
```

### 6.2 tag-patterns の役割

`poly test` は `stable` タグからの diff で影響範囲を判定する。
したがって：

- **CI が通ったら必ず `stable-<timestamp>` タグを打つ**（人間操作または自動化）
- タグを打たないと、古い影響範囲判定に基づいて過小なテストしか走らない

### 6.3 CI の `fetch-depth`

GitHub Actions などの CI では `fetch-depth: 0` を指定する。
さもないと `poly test` が git 履歴を取れず、影響範囲判定が壊れる。また `build.clj` の `git-count-revs` も機能しない。

```yaml
- uses: actions/checkout@v4
  with:
    fetch-depth: 0
```

---

## 7. development project の運用

### 7.1 役割

- **全 brick を単一 REPL で触れる**環境（§1.2.2 ループ短縮の実装）
- `clj -M:dev:nrepl` で起動
- Integrant を使うプロジェクトでは、`development/src/dev/user.clj` のライフサイクル管理セクションを有効化した後、`(go)` で Integrant system + Malli instrumentation を一括起動する
- Integrant を使わないプロジェクトでは REPL 起動後に明示的に `(malli-on!)` を呼んで Malli instrumentation を有効化する

### 7.2 新規 brick 追加時の development 更新

新規 component / base を追加したら、workspace ルート `deps.edn` の `:dev` エイリアスを 2 箇所更新する：

**`:extra-paths` に追加**（ソースパス、REPL で namespace を読むため）:

```clojure
"components/<name>/src"
"components/<name>/resources"
"components/<name>/test"
```

**`:extra-deps` に追加**（`:local/root` 登録、brick deps.edn の依存を推移的解決するため）:

```clojure
poly/<name> {:local/root "components/<name>"}
```

これを忘れると、新規 brick は以下のいずれかの問題を起こす:

- `:extra-paths` 忘れ → `(reset)` しても REPL から namespace が見えない
- `:extra-deps` 忘れ → brick の依存ライブラリが `ClassNotFoundException`

### 7.3 development の :extra-deps への追加は開発ツール + brick :local/root のみ

プロダクションで使うライブラリ依存は**本来の所属 brick の `deps.edn`** に追加する（選択肢 H、真実の一箇所化、STACK_GUIDE.md §1.2）。development の `:dev :extra-deps` に直接追加してよいのは：

- **開発ツール**: nrepl, portal, integrant.repl, tools.namespace, test.check, matcher-combinators など（本番ビルドに混入させない）
- **brick の `:local/root` 登録**: `poly/<name> {:local/root "..."}` の形（brick deps.edn の依存を REPL で使うため）

### 7.4 開発フィードバック規約 — `(safe-reset!) → (seed-all!)` 2 行原則

越境ユースケースの開発・PR smoke test では、`development` プロセス上で**境界状態を 2 行で立ち上げられる**ように維持する（上位原理「越境ユースケースの機械化」派生 2）。これは `../CLAUDE.md §1.2.2 ループ短縮` を smoke test レベルで実装する規律である。

**配置と命名**:

- 配置: `development/src/dev/fixtures.clj`
- 命名:
  - 全体 fixture: `(seed-all!)`
  - UC 単位 fixture: `(seed-<uc>!)`（例: `(seed-order-cancellation!)`, `(seed-monthly-report!)`）

**抽象実装例**:

```clojure
;; development/src/dev/fixtures.clj
(ns dev.fixtures
  "派生プロジェクトの smoke test / REPL 駆動開発用 fixture 集約。
   (safe-reset!) → (seed-all!) の 2 行で全 UC を試せる状態にする。"
  (:require
   [myorg.myapp.<domain-a>.interface :as a]
   [myorg.myapp.<domain-b>.interface :as b]))

(defn seed-<uc1>!
  "<uc1> ユースケースの境界状態を投入する。
   越境 tx で a と b に minimum viable な値を書く。"
  []
  ...)

(defn seed-<uc2>! [] ...)

(defn seed-all!
  "全 UC の seed helper を順に呼ぶ。
   2 行 smoke test の右側。`(safe-reset!) (seed-all!)` で完全な開発状態。"
  []
  (doseq [f [seed-<uc1>! seed-<uc2>!]]
    (f)))
```

**運用規律**:

- 新しい越境 UC を追加したら、対応する `seed-<uc>!` を `dev/fixtures.clj` に追加し、`seed-all!` の vector にも登録する
- seed helper は冪等にする（`(seed-all!)` を連続呼び出ししてもエラーにならない）
- 本テンプレート配布時には `dev/fixtures.clj` は置かない（brick 未配置と整合、YAGNI）。派生プロジェクトが最初の越境 UC を実装した時点で本節に従って作る

**今後の拡張候補**: 派生プロジェクトが立てた `seed-<uc>!` を `dev.user/status` の `:capabilities` に自動検出させる機構は、helper 命名規約・配置・capability shape が安定した後に検討する。fixture-first 規律（§7.4.1）と肥大化抑制（§7.4.2）が定着するほど、`(status)` から「現在どの `seed-<uc>!` が利用可能か」を確認する需要が高まり、自動検出の動機が強化される。
∵ ../memory/QUESTIONS.md §2

#### 7.4.1 実装順序: fixture 観察ファースト

越境 UC を実装する時、fixture を REPL で実際に観察してから test の precondition を確定する。fixture 未観察の想像 state で Test Plan を書く反パターンは、後追いの fixture 変更が既書 test の設計を反復させる（`../CLAUDE.md §1.2.2 ループ短縮`違反）。

**推奨手順**:

1. 越境 UC の orchestration interface を仮置きする（派生 1、シグネチャと Malli schema）
2. UC が要求する境界 state を `seed-<uc>!` に実装する（派生 2）
3. REPL で `(safe-reset!)` → `(seed-<uc>!)` を実行し、UC を呼び出して **実 state を観察する**（`(probe ...)` や `(dev.user/status)` を使う）
4. 観察した state に基づいて test の precondition を確定し、test を書く（派生 3）
5. Test Plan / PR 本文は 4 完了後に書く（観察済 state を Fixture state summary として記載、`.llm/templates/fixture-state-summary.md` の fragment を使う）
6. 必要なら派生 1 の配置を調整する（複数 entrypoint で必要なら component 昇格、§2.2.1）

ステップ 3 が肝心。fixture を REPL で観察してから test を設計することで、想像 state と実 state の乖離による設計反復を防ぐ。

**許容される事前作業**:

派生 1 の orchestration interface 仮置きと、test の受入条件・テスト観点の**粗いスケッチ**は fixture 観察前でも書いてよい。禁止されるのは fixture 未観察の想像 state に基づいて **concrete な test 本体 / Test Plan を確定する**こと。

#### 7.4.2 fixture 肥大化の抑制

fixture-first 規律の副作用として、`seed-all!` がすべての UC state を混ぜて肥大化し、UC 間で意図しない state 干渉が発生するリスクがある（例: 「全 entity が同時刻 attendance」になり double-booking 判定で候補が消える等の観察事例）。

**規約**:

- `seed-<uc>!` は **UC 単位で独立・最小**にする（minimum viable boundary state）。他 UC の state に依存しない
- `seed-all!` は convenience であり、**test の前提は原則 `seed-<uc>!`** を使う。`(use-fixtures :once (fn [f] (seed-<uc>!) (f)))` のように個別 seed を選んで呼ぶ
- shared `seed-all!` 由来の偶然 state に依存する test を避ける。`seed-all!` 全体に依存すると、新規 UC 追加で既存 test が影響を受ける
- 「複数 UC を組み合わせた state が必要」な場合は、専用の `seed-<scenario>!` を別途定義し、`seed-all!` には混ぜない。複合シナリオは独立した名前を持つ

**判定の目安**: ある test が `seed-all!` でしか動かない場合、その test は暗黙の state 依存を持っている疑いがある。必要な seed を `seed-<uc1>! seed-<uc2>!` のように明示的に列挙できるかを点検する。

---

## 8. CI に組込むべき最小セット

完了条件を CI で自動化する：
¤ ../CLAUDE.md §5.5

```bash
clj -M:lint                                          # clj-kondo
clj -M:format check                                  # cljfmt
clj -M:poly check                                    # Polylith 構造
clj -M:poly test :all                                # 全テスト
cd projects/<deploy> && clj -T:build uber            # ビルド（<deploy> は DESIGN.md §8.4 で定めた project / deploy 名）
```

通過したら `stable-<timestamp>` タグを打つ（§6.2）。

CI 通過時以外にも、人間が `git tag stable-$(date +%Y%m%d-%H%M%S)` を手動で打つのは問題ない。ただし CI が通らない状態でタグを打つと、以降の `poly test` が誤った安定点を参照する。Polylith 公式では `poly create tag` のような専用コマンドは存在せず、安定タグの付与は git tag で行う（`workspace.edn` の `:tag-patterns {:stable "^stable-.*"}` にマッチするタグ名にする）。

---

## 9. 関連文書へのリンク

- **../CLAUDE.md §1**: Polylith を採用する原理的根拠（機械化 + 副作用隔離）
- **../CLAUDE.md §6.1**: poly コマンド早見表
- **../CLAUDE.md §8.2**: 新規コンポーネント追加の作業プロトコル（概要）
- **CODING_GUIDE.md §2.3**: 副作用の隔離（Polylith の構造原理と整合）
- **Polylith 公式**: https://polylith.gitbook.io/
