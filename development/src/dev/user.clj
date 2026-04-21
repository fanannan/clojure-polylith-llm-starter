(ns dev.user
  "Polylith development project の REPL エントリ。

   本ファイルは REPL 駆動開発の補助を提供する。Integrant によるライフサイクル管理と
   Portal によるデータ可視化を使う構成を想定した完成例として、3 セクション（Malli
   instrumentation、Integrant、Portal）を同梱している。Integrant や Portal を使わない
   プロジェクトでは、該当セクションを削除する。

   含まれるもの:
     - Malli instrumentation セットアップ（必須層、全プロジェクトで有効化したまま使う）
     - Integrant ライフサイクル制御 (go / reset / halt)（Integrant を使う場合のみ）
     - Portal ヘルパー (portal / portal-clear / portal-close)（Portal を使う場合のみ）

   扱い指針:
     - **Integrant を使わないプロジェクト**（ライブラリ配布・単発 CLI 等）では、
       Integrant セクション（ns :require の該当行、ig-state / config / go / reset /
       halt / system の定義）を削除する。Malli instrumentation は REPL 起動後に
       明示的に (malli-on!) を呼んで有効化する
     - **Portal を使わないプロジェクト**では、Portal セクション（portal-instance /
       portal-tap-fn / portal / portal-clear / portal-close の定義）を削除する
     - 上記を削除しても try-catch で壊れないが、**未使用コードは残さない**のが本テンプレート
       の方針（YAGNI、疲労最小化、原則 5 LLM は削除が苦手）。try-catch は依存がまだ導入
       されていない起動直後の防御であって、未使用コードの放置を正当化するものではない
     - Malli instrumentation セクションはすべてのプロジェクトで有効化したまま使う
       （必須層、削除不可）

   主要コマンド:
     (malli-on!)   Malli instrumentation を起動（全プロジェクト共通、必須層の活用）
     (malli-off!)  Malli instrumentation を停止
     (go)          システム起動 + Malli instrumentation（Integrant を使う場合）
     (reset)       リロード + 再起動（Integrant を使う場合）
     (halt)        停止（Integrant を使う場合）
     (system)      起動中システム参照（Integrant を使う場合）
     (portal)      Portal 起動（Portal を使う場合）"
  (:require
   [clojure.tools.namespace.repl :as tn]
   ;; --- Malli instrumentation（必須層、全プロジェクトで有効化）---
   [malli.dev :as mdev]
   [malli.dev.pretty :as mpretty]
   ;; --- Integrant を使う場合のみ（使わないなら以下 3 行を削除）---
   ;; [integrant.core :as ig]
   ;; [integrant.repl :as ig-repl]
   ;; [integrant.repl.state :as ig-state]
   ))

;; tools.namespace の refresh 対象
;; dev は対象から外す（ここをリロードすると user が消える事故を防ぐ）
;; 新規 brick 種別を追加したらここにも追加
(tn/set-refresh-dirs "components" "bases")

;; ---------------------------------------------------------------------------
;; Malli instrumentation — §1.1.1 全域性の動的検証
;;
;; 【このセクションの扱い】
;;   - Malli は必須層。全プロジェクトで有効化したまま使う（削除不可）
;;   - REPL 起動後に (malli-on!) を呼ぶ、または (go) 内で自動的に呼ばれる
;;     （Integrant を使う場合）
;; ---------------------------------------------------------------------------

(defn malli-on!
  "Malli instrumentation を起動。全 m/=> 契約が REPL 評価時にチェックされる。"
  []
  (mdev/start! {:report (mpretty/reporter)}))

(defn malli-off! []
  (mdev/stop!))

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
;;   "Integrant 設定を返す。採用 stack と用途に応じて実装する。
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
;; Portal — データインスペクタ
;;
;; 【このセクションの扱い】
;;   - Portal を使う場合: deps.edn の :dev に djblue/portal を追加し、そのまま使う
;;   - Portal を使わない場合: 本セクション全体を削除する（portal-instance と
;;     portal-tap-fn の atom、portal / portal-clear / portal-close の定義すべて）
;;
;; Portal は tap> で値を流し込んで GUI で可視化するインスペクタ。REPL 駆動開発で
;; 中間データ構造やトランザクション内容を可視化する用途に強い。
;;
;; 実装上の防御: Portal 依存がまだ導入されていない起動直後でも REPL が落ちないよう
;; try-catch でラップしているが、これは未使用コードを残し続ける言い訳ではない。
;; 使わないことが確定したら削除する。
;;
;; tap> ハンドラの増殖と残存を防ぐため、submit 関数を atom で保持し、
;; portal 呼び出し時は前回 submit があれば remove-tap してから新たに add-tap、
;; portal-close 時も必ず remove-tap してから atom をクリアする。
;; ---------------------------------------------------------------------------

(def ^:private portal-instance (atom nil))
(def ^:private portal-tap-fn   (atom nil))

(defn portal
  "Portal を起動し、tap> の出力先として登録する。
   既に起動済みなら再起動せず、tap ハンドラも重複登録しない。
   Portal 依存が存在しない環境では :portal-not-available を返す。"
  []
  (try
    (require '[portal.api])
    (let [open   (resolve 'portal.api/open)
          submit (resolve 'portal.api/submit)]
      (when-not @portal-instance
        (reset! portal-instance (open)))
      ;; 前回の tap 登録があれば外してから新たに登録（重複防止）
      (when-let [prev @portal-tap-fn]
        (remove-tap prev))
      (add-tap @submit)
      (reset! portal-tap-fn @submit)
      @portal-instance)
    (catch Exception _
      :portal-not-available)))

(defn portal-clear
  "Portal ウィンドウの表示を消去する。成功時 :cleared、未接続時 :portal-not-available。"
  []
  (try
    (require '[portal.api])
    ((resolve 'portal.api/clear))
    :cleared
    (catch Exception _ :portal-not-available)))

(defn portal-close
  "Portal を閉じ、tap> への登録も解除する。
   成功時 :closed、Portal 依存が存在しない等で呼び出せない時は :portal-not-available を返す。"
  []
  (try
    (require '[portal.api])
    ;; tap 登録解除を先に（close 後に submit 呼び出しが走らないように）
    (when-let [prev @portal-tap-fn]
      (remove-tap prev)
      (reset! portal-tap-fn nil))
    ((resolve 'portal.api/close))
    (reset! portal-instance nil)
    :closed
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
