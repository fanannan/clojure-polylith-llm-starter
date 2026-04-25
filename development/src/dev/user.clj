(ns dev.user
  "Polylith development project の REPL エントリ。

   本ファイルは REPL 駆動開発の補助を提供する。必須の Malli instrumentation と、
   任意の lifecycle helper・trace helper・GUI helper を同梱している。任意セクションは
   行コメント `;;` で無効化されている。採用時に `;;` を一括除去して有効化する。

   含まれるもの:
     - Malli instrumentation セットアップ（必須層、全プロジェクトで有効化したまま使う）
     - lifecycle helper (go / reset / halt)（コメントアウト配布、採用時に解除）
     - trace helper（FlowStorm 導入時のみ）
     - GUI helper（Portal 導入時のみ）

   扱い指針:
     - 配布時点では Malli instrumentation のみが有効。任意セクションは
       セクションヘッダごと `;;` で無効化されている。
     - **lifecycle helper を使うプロジェクト**: 対応セクションを有効化する。
       1. ns :require の `[integrant.core :as ig]` などの `;;` を除去
       2. `config` / `go` / `halt` / `reset` / `reset-all` / `system` の各 defn の
          `;;` を除去（IDE で複数行選択 → `;;` 一括除去）
       3. `config` 関数の中身を実装（必要機能カテゴリに応じた読み込みロジック）
     - **GUI helper を使うプロジェクト**: 対応セクションを同様に有効化する。
       1. `portal-instance` / `portal-tap-fn` の atom 定義の `;;` を除去
       2. `portal-open!` / `portal-clear!` / `portal-close!` の defn の `;;` を除去
     - 任意 helper を使わないプロジェクト: そのまま放置してよい。
       コメントアウトされているため評価されず、依存が未解決でも REPL は壊れない。
     - Malli instrumentation セクションは全プロジェクトで有効化したまま使う（必須層、削除不可）

   常時利用できるコマンド:
     (status)          REPL 環境の状態確認（CLAUDE.md §9）
     (malli-on!)       Malli instrumentation を起動（停止は (malli.dev/stop!)）
     (probe x)         tap> + 値保持（println の代わり）
     (safe-reset!)     reset を構造化エラー返却で包む
     (hard-reset!)     stale-state recovery

   プロジェクトが有効化した lifecycle helper:
     (go)/(reset)/(halt)/(system)

   LLM 向け trace helper:
     (fs-start!)/(fs-record-ns! 'ns)/(fs-clear!)

   人間向け GUI helper:
     (portal-open!)/(portal-clear!)/(portal-close!)"
  (:require
   [clojure.tools.namespace.repl :as tn]
   [malli.dev :as mdev]
   [malli.dev.pretty :as mpretty]))

;; ライフサイクル管理採用時に追加する :require（上記 ns 宣言の :require 内にコピー）:
;;
;;   [integrant.core :as ig]
;;   [integrant.repl :as ig-repl]
;;   [integrant.repl.state :as ig-state]
;;
;; cljfmt の :sort-ns-references? により、コピー後も自動的にアルファベット順に並ぶ。

;; tools.namespace の refresh 対象（SSOT）
;; dev は対象から外す（ここをリロードすると user が消える事故を防ぐ）
;; 新規 brick 種別を追加したらここに追加（set-refresh-dirs と `(status)` の両方に反映される）
(def ^:private refresh-dirs ["components" "bases"])
(apply tn/set-refresh-dirs refresh-dirs)

;; ---------------------------------------------------------------------------
;; Malli instrumentation — §1.1.1 全域性の動的検証
;;
;; 【このセクションの扱い】
;;   - Malli は必須層。全プロジェクトで有効化したまま使う（削除不可）
;;   - REPL 起動後に (malli-on!) を呼ぶ、または (go) 内で自動的に呼ばれる
;;     （ライフサイクル管理を使う場合）
;; ---------------------------------------------------------------------------

(defonce ^:private malli-running? (atom false))

(defn malli-on!
  "Malli instrumentation を起動。全 m/=> 契約が REPL 評価時にチェックされる。
   停止する必要が生じた場合は `(malli.dev/stop!)` を直接呼ぶ
   （停止 helper は持たない）。"
  []
  (mdev/start! {:report (mpretty/reporter)})
  (reset! malli-running? true))

;; ---------------------------------------------------------------------------
;; REPL Workbench Helpers（LLM と人間の共通 primary 面、CLAUDE.md §9）
;;
;; 【このセクションの扱い】
;;   - 配布時点で active（削除不要）。optional 依存は try/require で遅延検査、
;;     未導入でも壊れない
;;   - 接続時に最初に `(status)` を呼んで環境確認、以降は helper で操作
;;   - LLM は `.llm/scripts/repl-eval.sh` 経由で同じ helper を叩く
;;
;; 提供する helper:
;;   (status)           capability と環境状態を 1 map で返す（最初に呼ぶ）
;;   (probe x)          tap> + 戻り値保持（println の代わり）
;;   (safe-reset!)      lifecycle helper / tools.namespace refresh を try/catch で包む
;;   (hard-reset!)      stale-state recovery: halt → refresh-all → restart
;;   (fs-start!) / (fs-record-ns! 'ns) / (fs-clear!)   trace helper 導入時のみ動作
;;
;; Reload 規律（CLAUDE.md §9.4）:
;;   - 関数追加・変更: --load-file or (safe-reset!)
;;   - ns graph 変更 (追加・削除・rename): (safe-reset!) で tools.namespace が解決
;;   - 依存追加 (deps.edn 変更): fresh JVM 再起動必須
;;   - lifecycle 定義変更: (safe-reset!) で反映
;;   - defrecord shape / protocol method 追加: (hard-reset!) or fresh JVM
;;   - multimethod 再定義: (hard-reset!) 推奨
;;   - m/=> 契約追加: --load-file 後に (malli-on!) 再実行
;;
;; capability の見方:
;;   :always      配布時点で有効な helper
;;   :lifecycle   対応セクション有効化 + 必要依存追加後に使える helper
;;   :trace       FlowStorm 導入時のみ使える helper
;;   :gui         Portal 導入時のみ使える helper
;; ---------------------------------------------------------------------------

(defonce ^:private dev-tools-cap (atom nil))

(defn- try-require
  "optional dependency probe。require が成功したら true、classpath 非在は false。
   意図: optional 依存は不在が正常系（未採用プロジェクトで fail fast にしない）。
   FileNotFoundException のみ catch（classpath missing の signal）。コンパイル
   エラー等の他例外は上位に re-throw して露出させる（silent 握り潰し回避）。"
  [ns-sym]
  (try (require ns-sym) true
       (catch java.io.FileNotFoundException _
         (tap> {:workbench/probe-miss ns-sym})
         false)))

(defn ensure-dev-tools!
  "optional 依存を lazy require し、capability map を返す。キャッシュ済み。
   初回 status 呼び出し時に自動実行される。"
  []
  (or @dev-tools-cap
      (reset! dev-tools-cap
              {:integrant  (and (try-require 'integrant.repl)
                                (try-require 'integrant.repl.state))
               :portal     (try-require 'portal.api)
               :flow-storm (try-require 'flow-storm.api)})))

(defn probe
  "(tap> x) + x をそのまま返す diagnosis primitive。
   Portal 起動中なら UI に値が流れる。`->` の中間挿入で値を観察できる。
   println の代わりに使う。"
  [x]
  (tap> x)
  x)

(defn- safe-system-keys
  "Integrant system が起動中なら key 一覧を返す。未起動・未採用時は nil。
   integrant.repl.state/system は Var で root value が nil か map。
   非 map（バージョン差で shape が変わった場合）は nil にして壊さない。"
  []
  (try
    (let [v   (resolve 'integrant.repl.state/system)
          sys (when v @v)]
      (when (map? sys) (keys sys)))
    (catch IllegalStateException _
      (tap> {:workbench/integrant-not-running true})
      nil)))

(defn- lifecycle-helper-state
  "lifecycle helper が導入済みなら状態 map を返す。未導入なら nil。
   現行テンプレートでは Integrant ベースの helper を想定するが、
   呼び出し側には capability としてのみ露出する。"
  []
  (when (:integrant (ensure-dev-tools!))
    {:available? true
     :impl       :integrant
     :system-keys (safe-system-keys)}))

(defn status
  "接続時に最初に実行する。helper capability と環境状態を 1 map で返す。"
  []
  (let [cap             (ensure-dev-tools!)
        lifecycle-state (lifecycle-helper-state)]
    {:malli-on?    @malli-running?
     :capabilities {:always    '[status malli-on! probe safe-reset! hard-reset!]
                    :lifecycle {:available? (boolean lifecycle-state)
                                :impl       (:impl lifecycle-state)
                                :commands   '[go reset halt system]}
                    :trace     {:available? (:flow-storm cap)
                                :commands   '[fs-start! fs-record-ns! fs-clear!]}
                    :gui       {:available? (:portal cap)
                                :commands   '[portal-open! portal-clear! portal-close!]}}
     :lifecycle    lifecycle-state
     :refresh-dirs refresh-dirs
     :current-ns   (ns-name *ns*)}))

(defn- do-reset
  "Integrant 採用時は (ig-repl/reset)、非採用時は (tn/refresh)。"
  []
  (if (:integrant (ensure-dev-tools!))
    ((resolve 'integrant.repl/reset))
    (tn/refresh)))

(defn safe-reset!
  "Integrant 採用時は (ig-repl/reset)、非採用時は (tn/refresh) を呼ぶ。
   失敗時は raw stacktrace を投げず、構造化 map を返す。"
  []
  (try (do-reset)
       (catch clojure.lang.ExceptionInfo t
         {:status :refresh-failed :ex (ex-message t) :data (ex-data t)})
       (catch java.io.FileNotFoundException t
         {:status :refresh-failed :ex (ex-message t)
          :hint "namespace file が見つかりません（set-refresh-dirs / extra-paths を確認）"})
       (catch RuntimeException t
         {:status :refresh-failed :ex (ex-message t)
          :root (some-> t Throwable->map :cause)})))

(defn- try-halt
  "halt は既に停止中なら IllegalStateException を投げる。hard-reset の文脈では
   これは期待通りで、どのみち次の go で再起動するため tap> で signal のみ残す。"
  []
  (try ((resolve 'integrant.repl/halt))
       (catch IllegalStateException _
         (tap> {:workbench/halt-noop "not running"})
         :already-halted)))

(defn hard-reset!
  "stale-state recovery の sanctioned 手順。
   halt → refresh-all → go (Integrant 採用時)、または refresh-all のみ (非採用時)。
   phantom vars / 半 reload / defrecord shape 変更時に使う。"
  []
  (let [cap (ensure-dev-tools!)]
    (when (:integrant cap) (try-halt))
    (tn/refresh-all)
    (when (:integrant cap)
      ((resolve 'integrant.repl/go)))
    :hard-reset-done))

(defn- resolve-first
  "optional API のバージョン差を吸収するため、候補 symbol を順に probe する。"
  [syms]
  (some resolve syms))

(defn fs-start!
  "FlowStorm debugger を起動。未導入時は :flow-storm-not-available、
   採用バージョンで start 関数が見つからない時は :flow-storm-api-unknown。
   起動後は fs-record-ns! で個別 ns を instrument する。"
  []
  (cond
    (not (:flow-storm (ensure-dev-tools!)))
    :flow-storm-not-available

    :else
    (if-let [start-fn (resolve-first '[flow-storm.api/local-connect
                                       flow-storm.api/connect
                                       flow-storm.api/start-debugger])]
      (try
        (start-fn)
        :started
        (catch clojure.lang.ArityException _
          :flow-storm-api-unknown))
      :flow-storm-api-unknown)))

(defn fs-record-ns!
  "(fs-record-ns! 'poly.user.core) — load-file 直後に instrument。
   変更した ns の forms を FlowStorm trace 対象にして、挙動・binding 変遷を観察。"
  [ns-sym]
  (if (:flow-storm (ensure-dev-tools!))
    (if-let [instrument-fn (resolve-first '[flow-storm.api/instrument-forms-for-namespaces
                                            flow-storm.api/instrument-namespaces])]
      (try
        (instrument-fn #{(str ns-sym)} {})
        :instrumented
        (catch clojure.lang.ArityException _
          :flow-storm-api-unknown))
      :flow-storm-api-unknown)
    :flow-storm-not-available))

(defn fs-clear!
  "過去の FlowStorm trace を消去、stale trace による誤読を防ぐ。"
  []
  (if (:flow-storm (ensure-dev-tools!))
    (if-let [clear-fn (resolve-first '[flow-storm.api/clear-recordings
                                       flow-storm.api/clear])]
      (try
        (clear-fn)
        :cleared
        (catch clojure.lang.ArityException _
          :flow-storm-api-unknown))
      :flow-storm-api-unknown)
    :flow-storm-not-available))

;; ---------------------------------------------------------------------------
;; Integrant ライフサイクル — §1.1.3 副作用の隔離
;;
;; 【このセクションの扱い】
;;   - Integrant を使う場合: コメント解除して config 関数を実装し、各関数を有効化
;;   - Integrant を使わない場合: 本セクション全体を削除する（require の Integrant 関連行も）
;;
;; Integrant は I/O リソース（HTTP サーバ・DB 接続・外部 API クライアント等）の起動順序と
;; 停止順序を制御する用途で採用される。ライブラリ配布や単発 CLI 実行では不要。
;; 起動順序: Malli instrumentation → Integrant system
;; ---------------------------------------------------------------------------

;; (defn config
;;   "ライフサイクル設定を返す。用途に応じて実装する。
;;
;;    Web サービス（aero + #profile）:
;;      (aero/read-config (io/resource \"config.edn\") {:profile :dev})
;;
;;    CLI ツール:
;;      環境変数またはローカルファイルから読む
;;
;;    実装パターンは POLYLITH_GUIDE.md §2.4 参照。"
;;   []
;;   (throw (ex-info "config not yet implemented. See BOOTSTRAP_GUIDE.md §2.6."
;;                   {:type ::not-bootstrapped})))

;; (ig-repl/set-prep! config)

;; (defn go
;;   "システム起動 + Malli instrumentation ON。起動順序重要。"
;;   []
;;   (malli-on!)
;;   (ig-repl/go))

;; (defn halt
;;   "システム停止。"
;;   []
;;   (ig-repl/halt)
;;   (mdev/stop!)
;;   (reset! malli-running? false))

;; (defn reset
;;   "名前空間リロード + システム再起動。"
;;   []
;;   (ig-repl/reset))

;; (defn reset-all
;;   "リソースの依存も含めた完全リフレッシュ。reset で解決しない時のみ。"
;;   []
;;   (ig-repl/reset-all))

;; (defn system
;;   "起動中の Integrant システムを返す。"
;;   []
;;   ig-state/system)

;; ---------------------------------------------------------------------------
;; Portal — データインスペクタ
;;
;; 【このセクションの扱い】
;;   - 配布時点ではセクション全体が `;;` で無効化されている
;;   - Portal を使う場合: deps.edn の :dev に djblue/portal を追加し、
;;     以下の `(def` / `(defn` の行頭 `;;` を一括除去して有効化する
;;     （IDE で複数行選択 → `;;` 除去）
;;   - Portal を使わない場合: そのまま放置してよい。評価されないのでエラーにならない
;;
;; Portal は tap> で値を流し込んで GUI で可視化するインスペクタ。REPL 駆動開発で
;; 中間データ構造やトランザクション内容を可視化する用途に強い。
;;
;; tap> ハンドラの増殖と残存を防ぐため、submit 関数を atom で保持し、
;; portal-open! 呼び出し時は前回 submit があれば remove-tap してから新たに add-tap、
;; portal-close! 時も必ず remove-tap してから atom をクリアする。
;; ---------------------------------------------------------------------------

;; (def ^:private portal-instance (atom nil))
;; (def ^:private portal-tap-fn   (atom nil))

;; (defn portal-open!
;;   "Portal を起動し、tap> の出力先として登録する。
;;    既に起動済みなら再起動せず、tap ハンドラも重複登録しない。
;;    Portal 依存が存在しない環境では :portal-not-available を返す。"
;;   []
;;   (try
;;     (require '[portal.api])
;;     (let [open   (resolve 'portal.api/open)
;;           submit (resolve 'portal.api/submit)]
;;       (when-not @portal-instance
;;         (reset! portal-instance (open)))
;;       ;; 前回の tap 登録があれば外してから新たに登録（重複防止）
;;       (when-let [prev @portal-tap-fn]
;;         (remove-tap prev))
;;       (add-tap @submit)
;;       (reset! portal-tap-fn @submit)
;;       @portal-instance)
;;     (catch Exception _
;;       :portal-not-available)))

;; (defn portal-clear!
;;   "Portal ウィンドウの表示を消去する。成功時 :cleared、未接続時 :portal-not-available。"
;;   []
;;   (try
;;     (require '[portal.api])
;;     ((resolve 'portal.api/clear))
;;     :cleared
;;     (catch Exception _ :portal-not-available)))

;; (defn portal-close!
;;   "Portal を閉じ、tap> への登録も解除する。
;;    成功時 :closed、Portal 依存が存在しない等で呼び出せない時は :portal-not-available を返す。"
;;   []
;;   (try
;;     (require '[portal.api])
;;     ;; tap 登録解除を先に（close 後に submit 呼び出しが走らないように）
;;     (when-let [prev @portal-tap-fn]
;;       (remove-tap prev)
;;       (reset! portal-tap-fn nil))
;;     ((resolve 'portal.api/close))
;;     (reset! portal-instance nil)
;;     :closed
;;     (catch Exception _ :portal-not-available)))

;; ---------------------------------------------------------------------------
;; リッチコメント — 典型操作
;;
;; 以下は lifecycle helper / GUI helper セクションのコメントを解除した後に使える例。
;; 配布状態のままでは (go) / (portal-open!) 等は未定義のため評価するとエラーになる。
;; clj-kondo の :skip-comments true により lint 対象外。
;; ---------------------------------------------------------------------------

(comment
  ;; ブートストラップ後、必要機能カテゴリに応じて上記セクションを有効化してから:

  ;; --- 立ち上げ（ライフサイクル管理に Integrant を採用する場合）---
  ;; (go)

  ;; --- 開発サイクル ---
  ;; (reset)

  ;; --- 検査 ---
  ;; (system)
  ;; (keys (system))

  ;; --- GUI helper（採用時）---
  ;; (portal-open!)
  ;; (tap> {:check :hello})
  ;; (portal-clear!)

  ;; --- 終了 ---
  ;; (halt)
  )
