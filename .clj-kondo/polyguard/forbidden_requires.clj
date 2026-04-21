(ns polyguard.forbidden-requires
  "STACK_GUIDE.md §8.1 禁止ライブラリ / §8.2 非推奨ライブラリの **namespace 単位 require 禁止**。

   :discouraged-var では代表的な関数名しか検知できず、ライブラリの別 entry point
   を使われると検知漏れする。本 hook は `ns` form の `:require` 節、および
   `clojure.core/require` 直接呼出しを解析して、禁止 namespace を参照した時点で
   finding を登録する。

   登録は .clj-kondo/config.edn の :hooks {:analyze-call ...} から行う:
     clojure.core/ns      polyguard.forbidden-requires/analyze-ns
     clojure.core/require polyguard.forbidden-requires/analyze-require

   参照先:
     - STACK_GUIDE.md §8.1 禁止ライブラリ（:error）
     - STACK_GUIDE.md §8.2 非推奨ライブラリ（:error、新規採用禁止）
     - 本 hook と :discouraged-var の役割分担:
       :discouraged-var は特定シンボル使用の検知、本 hook は require 段階での検知。
       両者併用で早期検知を実現する。

   偽陽性対策:
     - プロジェクト固有の事情で既存コードに非推奨 namespace が残っている場合、
       呼出側で `{:clj-kondo/config {:linters {:forbidden-require {:level :off}}}}` を
       file-level metadata として付与すればファイル単位で無効化できる。
     - 大規模移行中は `:config-in-ns` で特定 brick のみ :off にする運用も可。"
  (:require
   [clj-kondo.hooks-api :as api]))

;; ---------------------------------------------------------------------------
;; 禁止 namespace カタログ
;; ---------------------------------------------------------------------------

(def ^:private banned
  "namespace 文字列 → {:level <:error|:warning> :message <string>} のマップ。

   STACK_GUIDE.md §8.1 禁止ライブラリ、§8.2 非推奨ライブラリの
   **代表 namespace** を列挙。サブ namespace（例: `taoensso.timbre.appenders.*`）も
   プレフィックス一致で禁止扱い（§check-prefix 参照）。

   ここに追加する時は STACK_GUIDE.md §8 側に必ず項目を追加する（双方向同期）。"
  {;; === §8.1 禁止（セキュリティ・ライセンス） ===
   "org.apache.log4j"           {:level :error
                                 :message "log4j 1.x は §8.1 禁止（CVE-2019-17571 等）。mulog を使う"}
   "javax.xml.parsers.xerces"   {:level :error
                                 :message "xerces 独立版は §8.1 禁止（XXE 脆弱性）。clojure.data.xml / JDK javax.xml を使う"}
   "org.json"                   {:level :error
                                 :message "org.json legacy は §8.1 禁止（デシリアライズ脆弱性）。jsonista を使う"}

   ;; === §8.2 非推奨（設計思想不整合・メンテ停止・推奨代替あり） ===
   ;; ロギング
   "taoensso.timbre"            {:level :error
                                 :message "timbre は §8.2 非推奨。構造化ログは mulog を使う"}

   ;; ライフサイクル管理
   "com.stuartsierra.component" {:level :error
                                 :message "Component は §8.2 非推奨。新規は Integrant を使う"}
   "mount.core"                 {:level :error
                                 :message "mount は §8.2 非推奨（グローバル状態を生む）。Integrant を使う"}

   ;; 設定管理
   "environ.core"               {:level :error
                                 :message "environ は §8.2 非推奨。aero の #env を使う"}
   "immuconf.config"            {:level :error
                                 :message "immuconf は §8.2 非推奨。aero を使う"}

   ;; 検証
   "clojure.spec.alpha"         {:level :error
                                 :message "clojure.spec.alpha は §8.2 非推奨。Malli を使う（m/=> 関数契約が優位）"}

   ;; HTTP / ルーティング
   "compojure.core"             {:level :error
                                 :message "Compojure は §8.2 非推奨。reitit + reitit-malli を使う"}
   "io.pedestal.http"           {:level :error
                                 :message "Pedestal は §8.2 条件付き非推奨。小〜中規模では過剰、reitit を使う"}
   "aleph.http"                 {:level :error
                                 :message "aleph は §8.2 非推奨（設計思想不整合）。Jetty / http-kit を使う"}
   "manifold.deferred"          {:level :error
                                 :message "manifold は §8.2 非推奨（設計思想不整合）。core.async / promise.cljc を使う"}
   "manifold.stream"            {:level :error
                                 :message "manifold は §8.2 非推奨。core.async channel を使う"}
   "org.immutant.web"           {:level :error
                                 :message "immutant は §8.2 非推奨（メンテ停止）。Jetty を使う"}
   "bidi.ring"                  {:level :error
                                 :message "bidi は §8.2 条件付き非推奨（新規採用不可）。reitit を使う"}

   ;; HTTP クライアント
   "clj-http.client"            {:level :error
                                 :message "clj-http は §8.2 条件付き非推奨（新規採用不可）。hato を使う"}

   ;; JSON
   "clojure.data.json"          {:level :error
                                 :message "data.json は §8.2 非推奨。jsonista を使う"}
   "cheshire.core"              {:level :error
                                 :message "Cheshire は §8.2 条件付き非推奨（新規採用不可）。jsonista を使う"}

   ;; DB / ORM
   "clojure.java.jdbc"          {:level :error
                                 :message "clojure.java.jdbc は §8.2 非推奨（メンテ停止）。next.jdbc を使う"}
   "korma.core"                 {:level :error
                                 :message "Korma は §8.2 非推奨（ORM、設計思想不整合）。HoneySQL + next.jdbc を使う"}

   ;; 認証
   "cemerick.friend"            {:level :error
                                 :message "friend は §8.2 非推奨（メンテ停止）。buddy-sign + buddy-hashers を使う"}

   ;; 多言語
   "taoensso.tower"             {:level :error
                                 :message "tower は §8.2 非推奨（メンテ停止）。taoensso/tempura を使う"}

   ;; メトリクス
   "iapetos.core"               {:level :error
                                 :message "metrics-clojure (iapetos) は §8.2 非推奨（設計思想不整合）。mulog に一元化"}

   ;; スケジューリング
   "overtone.at-at"             {:level :error
                                 :message "at-at は §8.2 非推奨（Integrant 統合弱い）。chime を使う"}
   "tea-time.core"              {:level :error
                                 :message "tea-time は §8.2 非推奨（メンテ停止）。chime を使う"}

   ;; リトライ
   "robert.bruce"               {:level :error
                                 :message "robert.bruce は §8.2 非推奨（メンテ停止）。sunng87/diehard を使う"}

   ;; Markdown
   "endophile.core"             {:level :error
                                 :message "endophile は §8.2 非推奨（メンテ停止）。markdown-clj を使う"}

   ;; 全文検索
   "clojurewerkz.elastisch"     {:level :error
                                 :message "elastisch は §8.2 非推奨（メンテ停止）。mpenet/spandex を使う"}

   ;; E2E テスト
   "clj-webdriver.taxi"         {:level :error
                                 :message "clj-webdriver は §8.2 非推奨（メンテ停止）。etaoin を使う"}

   ;; AWS SDK
   "amazonica.core"             {:level :error
                                 :message "Amazonica は §8.2 非推奨（メンテ停止傾向）。com.cognitect.aws/api を使う"}
   "amazonica.aws.s3"           {:level :error
                                 :message "Amazonica は §8.2 非推奨。com.cognitect.aws/s3 を使う"}

   ;; 数値計算
   "incanter.core"              {:level :error
                                 :message "incanter は §8.2 非推奨（メンテ低迷）。scicloj/tablecloth を使う"}
   "incanter.stats"             {:level :error
                                 :message "incanter は §8.2 非推奨。scicloj/scicloj.ml を使う"}

   ;; 深層学習 / LLM
   "dl4clj.nn.api.model"        {:level :error
                                 :message "deeplearning4j Clojure wrapper は §8.2 非推奨（設計思想不整合）。libpython-clj 経由で PyTorch を使う"}
   "cortex.nn.execute"          {:level :error
                                 :message "cortex は §8.2 非推奨（メンテ停止）。libpython-clj 経由を使う"}

   ;; MQTT
   "clojurewerkz.machine-head"  {:level :error
                                 :message "machine-head は §8.2 非推奨（メンテ状態要確認）。org.eclipse.paho.client.mqttv3 直接利用"}

   ;; GUI
   "seesaw.core"                {:level :error
                                 :message "seesaw は §8.2 条件付き非推奨（新規採用不可）。humbleui / cljfx を使う"}

   ;; マイグレーション
   "joplin.core"                {:level :error
                                 :message "joplin は条件付き非推奨。migratus を使う"}

   ;; === 2026-04 拡張分 ===

   ;; シリアライゼーション（§3.41 / §8.2）
   "clojure.data.fressian"      {:level :error
                                 :message "Fressian は §8.2 条件付き非推奨。com.taoensso/nippy を使う（§3.41）"}

   ;; 統合プラットフォーム（§8.2 商用ライセンス）
   "com.rpl.rama"               {:level :error
                                 :message "Rama は §8.2 条件付き非推奨（商用ライセンス、framework 重量）。XTDB + worker + batch の組合せで代替検討"}

   ;; 機械学習（§8.2 ライセンス GPL）
   "smile.classification"       {:level :error
                                 :message "SMILE は §8.2 条件付き非推奨（GPL 3.0、SaaS/商用配布と衝突）。scicloj/tablecloth、libpython-clj を検討"}
   "smile.clustering"           {:level :error
                                 :message "SMILE は §8.2 条件付き非推奨（GPL 3.0）。scicloj/tablecloth 等を検討"}
   "smile.regression"           {:level :error
                                 :message "SMILE は §8.2 条件付き非推奨（GPL 3.0）。scicloj/scicloj.ml を検討"}

   ;; フルスタック DAG（§3.40.1 射程外）
   "hyperfiddle.electric"       {:level :error
                                 :message "Electric Clojure は §3.40.1 射程外（cljs 前提、macro 重依存、API 変動激しい）"}
   "hyperfiddle.electric-dom2"  {:level :error
                                 :message "Electric Clojure は §3.40.1 射程外"}

   ;; Java Serialization（§8.2 セキュリティ）
   "java.io.Serializable"       {:level :error
                                 :message "Java Serialization は §8.2 非推奨（RCE 脆弱性）。com.taoensso/nippy を使う（§3.41）"}})

(def ^:private banned-prefixes
  "banned map の key（namespace 文字列）のベクトル。prefix 一致判定に使う。
   サブ namespace（例: `taoensso.timbre.appenders.core`）も禁止扱いにするため、
   直接一致 + prefix 一致の両方で検査する。"
  (vec (keys banned)))

(defn- lookup-banned
  "namespace 文字列 `ns-str` が banned カタログのいずれかに一致するか判定。
   直接一致（banned の key と完全一致）を優先、次に prefix + `.` 一致を判定。
   見つかれば {:level ... :message ...} を返し、見つからなければ nil。"
  [ns-str]
  (or (get banned ns-str)
      (some (fn [prefix]
              (when (and (.startsWith ^String ns-str (str prefix "."))
                         (get banned prefix))
                (get banned prefix)))
            banned-prefixes)))

;; ---------------------------------------------------------------------------
;; ns form 解析（(ns foo (:require [banned.ns ...])) を検知）
;; ---------------------------------------------------------------------------

(defn- extract-required-ns-symbol
  "`:require` 節の 1 要素（`[ns :as alias]` / `ns` の両形態）から namespace symbol を取り出す。
   見つからなければ nil。"
  [node]
  (cond
    ;; [foo.bar :as fb] 形式
    (= :vector (:tag node))
    (let [first-child (first (remove #(contains? #{:whitespace :comma :newline :comment :uneval} (:tag %))
                                     (:children node)))]
      (when (and first-child (= :token (:tag first-child)))
        (:value first-child)))

    ;; 素のシンボル `foo.bar` 形式
    (= :token (:tag node))
    (:value node)

    :else nil))

(defn- analyze-require-clause
  "`:require` 節の全要素を走査し、banned namespace があれば finding 登録。"
  [require-node]
  (let [children-nodes (remove #(contains? #{:whitespace :comma :newline :comment :uneval} (:tag %))
                               (:children require-node))
        ;; 最初の要素は `:require` キーワード自身なので除外
        specs (rest children-nodes)]
    (doseq [spec specs]
      (when-let [ns-sym (extract-required-ns-symbol spec)]
        (let [ns-str (name ns-sym)]
          (when-let [banned-info (lookup-banned ns-str)]
            (api/reg-finding!
             (merge
              (select-keys (meta spec) [:row :col :end-row :end-col])
              {:type    :forbidden-require
               :level   (:level banned-info)
               :message (:message banned-info)}))))))))

(defn analyze-ns
  "`(ns foo (:require ...))` form を解析し、`:require` 節内の banned namespace を検知。"
  [{:keys [node]}]
  (let [children-nodes (remove #(contains? #{:whitespace :comma :newline :comment :uneval} (:tag %))
                               (:children node))]
    (doseq [child children-nodes]
      ;; `(:require ...)` のような list を探す。最初の子が :require キーワードのもの。
      (when (= :list (:tag child))
        (let [inner (remove #(contains? #{:whitespace :comma :newline :comment :uneval} (:tag %))
                            (:children child))
              first-inner (first inner)]
          (when (and first-inner
                     (= :token (:tag first-inner))
                     (= :require (:k first-inner)))
            (analyze-require-clause child))))))
  ;; hook からは node をそのまま返す（clj-kondo 標準動作を壊さない）
  {:node node})

;; ---------------------------------------------------------------------------
;; (require '...) 呼出解析（REPL で使われることがあるが、プロダクションでも検知）
;; ---------------------------------------------------------------------------

(defn analyze-require
  "`(require 'foo.bar)` / `(require '[foo.bar :as fb])` を解析、banned を検知。"
  [{:keys [node]}]
  (let [children-nodes (remove #(contains? #{:whitespace :comma :newline :comment :uneval} (:tag %))
                               (:children node))
        ;; `require` 関数本体は最初の子、それ以降が引数
        args (rest children-nodes)]
    (doseq [arg args]
      ;; クォート `'foo.bar` を展開
      (let [unquoted (cond
                       (and (= :list (:tag arg))
                            (let [first-child (first (remove #(contains? #{:whitespace :comma :newline} (:tag %))
                                                             (:children arg)))]
                              (and first-child
                                   (= :token (:tag first-child))
                                   (= 'quote (:value first-child)))))
                       (second (remove #(contains? #{:whitespace :comma :newline} (:tag %))
                                       (:children arg)))

                       :else arg)]
        (when unquoted
          (when-let [ns-sym (extract-required-ns-symbol unquoted)]
            (let [ns-str (name ns-sym)]
              (when-let [banned-info (lookup-banned ns-str)]
                (api/reg-finding!
                 (merge
                  (select-keys (meta arg) [:row :col :end-row :end-col])
                  {:type    :forbidden-require
                   :level   (:level banned-info)
                   :message (:message banned-info)})))))))))
  {:node node})
