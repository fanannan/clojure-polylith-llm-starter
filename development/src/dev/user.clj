(ns dev.user
  "Polylith development project の REPL エントリ。

   本ファイルは必須層（Malli）と stack 層（Integrant / Portal 等）の構成に応じて調整する。
   採用 stack 層に応じて、対応するセクションを有効化する。

   採用 stack 別の調整:
     - library stack のみ        → Malli instrumentation セクションのみ有効化
     - Integrant を含む stack     → Malli + Integrant ライフサイクルセクションを有効化
        （web-api stack / graphql-api stack / batch stack / worker stack /
         data-pipeline stack / bot stack / desktop stack、および Integrant を
         有効化した cli stack）
     - dev-tools stack           → Portal セクション（本ファイル末尾、デフォルト有効）

   stack 層の採用・変更は採用 stack を決定し、各 brick（base / component）の deps.edn に
   STACK_GUIDE.md §4.2 の推奨ライブラリを反映することで行う。
   選定論理は project-guide/STACK_GUIDE.md 参照。

   主要コマンド（Integrant を含む stack 採用後）:
     (go)     システム起動 + Malli instrumentation
     (reset)  リロード + 再起動
     (halt)   停止
     (system) 起動中システム参照
     (portal) Portal 起動"
  (:require
   [clojure.tools.namespace.repl :as tn]
   ;; --- Integrant を含む stack 採用時、以下を有効化 ---
   ;; [integrant.core :as ig]
   ;; [integrant.repl :as ig-repl]
   ;; [integrant.repl.state :as ig-state]

   ;; --- Malli instrumentation を使う場合（全 stack で推奨）、以下を有効化 ---
   ;; [malli.dev :as mdev]
   ;; [malli.dev.pretty :as mpretty]
   ))

;; tools.namespace の refresh 対象
;; dev は対象から外す（ここをリロードすると user が消える事故を防ぐ）
;; 新規 brick 種別を追加したらここにも追加
(tn/set-refresh-dirs "components" "bases")

;; ---------------------------------------------------------------------------
;; Malli instrumentation — §1.1.1 全域性の動的検証
;; 全 stack で有効化を強く推奨（Malli は必須層）
;; ---------------------------------------------------------------------------

;; (defn malli-on!
;;   "Malli instrumentation を起動。全 m/=> 契約が REPL 評価時にチェックされる。"
;;   []
;;   (mdev/start! {:report (mpretty/reporter)}))

;; (defn malli-off! []
;;   (mdev/stop!))

;; ---------------------------------------------------------------------------
;; Integrant ライフサイクル — §1.1.3 副作用の隔離
;; Integrant を含む stack 採用時に有効化
;;   対象 stack: web-api stack / graphql-api stack / batch stack /
;;              worker stack / data-pipeline stack / bot stack /
;;              desktop stack、および Integrant を有効化した cli stack
;; 起動順序: Malli instrumentation → Integrant system
;; ---------------------------------------------------------------------------

;; (defn config
;;   "Integrant 設定を返す。採用 stack と用途に応じて実装する。
;;
;;    Web サービス（aero + #profile）:
;;      (aero/read-config (io/resource \"config.edn\") {:profile :dev})
;;
;;    CLI ツール:
;;      環境変数またはローカルファイルから読む
;;
;;    実装パターンは POLYLITH_GUIDE.md §2.3 参照。"
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
;;   "システム停止 + Malli instrumentation OFF。"
;;   []
;;   (ig-repl/halt)
;;   (malli-off!))

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
;; Portal — データインスペクタ（dev-tools stack 採用時、強く推奨）
;; dev-tools stack 未採用の環境でも壊れないよう try でラップ
;; ---------------------------------------------------------------------------

(def ^:private portal-instance (atom nil))

(defn portal
  "Portal を起動し、tap> の出力先として登録する。
   dev-tools stack 未採用で Portal 依存が存在しない環境では :portal-not-available を返す。"
  []
  (try
    (require '[portal.api])
    (let [open   (resolve 'portal.api/open)
          submit (resolve 'portal.api/submit)]
      (when-not @portal-instance
        (reset! portal-instance (open)))
      (add-tap @submit)
      @portal-instance)
    (catch Exception _
      :portal-not-available)))

(defn portal-clear []
  (try
    (require '[portal.api])
    ((resolve 'portal.api/clear))
    (catch Exception _ :portal-not-available)))

(defn portal-close []
  (try
    (require '[portal.api])
    ((resolve 'portal.api/close))
    (reset! portal-instance nil)
    (catch Exception _ :portal-not-available)))

;; ---------------------------------------------------------------------------
;; リッチコメント — 典型操作
;; ---------------------------------------------------------------------------

(comment
  ;; ブートストラップ後、採用 stack に応じて各セクションを有効化してから:

  ;; --- 立ち上げ（Integrant を含む stack 採用時）---
  ;; (go)

  ;; --- 開発サイクル ---
  ;; (reset)

  ;; --- 検査 ---
  ;; (system)
  ;; (keys (system))

  ;; --- Portal（dev-tools stack 採用時）---
  (portal)
  (tap> {:check :hello})
  (portal-clear)

  ;; --- 終了 ---
  ;; (halt)
  )
