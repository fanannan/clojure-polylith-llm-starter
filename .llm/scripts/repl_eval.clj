(ns repl-eval
  "LLM 向け nREPL client — 稼働中の long-lived JVM に eval / load-file を送る.

   本 namespace は `clj -X:repl-eval` の exec-fn として呼ばれる。通常は
   shell wrapper `.llm/scripts/repl-eval.sh` 経由で起動される（shell quoting
   回避、stdin fallback、port 発見のため）。

   運用方針（CLAUDE.md §9 Live Workbench Protocol）:
     - 人間が 1 つの `clj -M:dev:nrepl` を起動（long-lived JVM）
     - CIDER / Calva / Cursive と LLM が同じ nREPL に attach
     - 永続 session (.nrepl-session) を workspace 単位で保持、(reset) 後の
       段階探索・契約違反再現・mulog 観察・flow-storm trace を支える
     - nREPL 再起動時 (port 変化 or session 失効) は自動で再 clone

   実装上の重要点:
     - process 跨ぎ request-id 永続化 (T1): .nrepl-session に last-request-id を
       記録、--interrupt で直近 eval を正確に中断
     - bounded printing (10000 chars/response): LLM context 保護のため truncate
     - 必須 op 検証: describe で eval/clone/ls-sessions/interrupt/load-file が
       揃うことを確認、cider-nrepl middleware 無効時は明確にエラー終了
     - file/line metadata 常時付与: stack trace が source location を指す

   Exit codes:
     0   成功
     1   eval-error / namespace-not-found / :ex
     2   接続エラー・必須 op 欠落・protocol shape 不正
     130 interrupted (UNIX 慣例)"
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [nrepl.core :as nrepl])
  (:import
   [java.nio.file AtomicMoveNotSupportedException CopyOption Files StandardCopyOption]
   [java.nio.file.attribute FileAttribute]))

;; ---------------------------------------------------------------------------
;; Constants
;; ---------------------------------------------------------------------------

(set! *warn-on-reflection* true)

(def ^:private session-file ".nrepl-session")
(def ^:private max-chunk-chars 10000)
(def ^:private client-timeout-ms 30000)
(def ^:private required-ops #{"eval" "clone" "ls-sessions" "interrupt" "load-file" "describe"})

;; ---------------------------------------------------------------------------
;; Port / session 発見と永続化
;; ---------------------------------------------------------------------------

(defn- exit! [code msgs]
  (binding [*out* *err*]
    (doseq [m msgs] (println m))
    (flush))
  (System/exit code))

(defn- fatal! [& msgs]
  (throw (ex-info "fatal"
                  {:repl-eval/fatal? true
                   :exit-code 2
                   :messages (vec msgs)})))

(defn- parse-port-file []
  (try (-> ".nrepl-port" slurp str/trim Integer/parseInt)
       (catch NumberFormatException _
         (fatal! "FATAL: .nrepl-port の内容が整数でない（malformed）"
                 (str "  内容: " (pr-str (slurp ".nrepl-port")))
                 "HINT: `.nrepl-port` を削除して nREPL を再起動してください"))))

(defn- parse-env-port []
  (when-let [raw (System/getenv "NREPL_PORT")]
    (try (Integer/parseInt raw)
         (catch NumberFormatException _
           (fatal! "FATAL: NREPL_PORT の内容が整数でない（malformed）"
                   (str "  内容: " (pr-str raw))
                   "HINT: NREPL_PORT を unset するか、正しい port を設定してください")))))

(defn- discover-port []
  (let [env-port     (parse-env-port)
        file-exists? (.exists (io/file ".nrepl-port"))
        file-port    (when file-exists? (parse-port-file))]
    (when (and env-port file-port (not= env-port file-port))
      (fatal! "FATAL: nREPL port source が食い違っています"
              (str "  NREPL_PORT: " env-port)
              (str "  .nrepl-port: " file-port)
              "HINT: 古い NREPL_PORT 環境変数で別 JVM に接続する事故を避けるため停止しました。"
              "HINT: NREPL_PORT を unset するか、正しい port で再実行してください。"))
    (cond
      env-port     env-port
      file-port    file-port
      :else
      (fatal! "FATAL: nREPL 未起動 (.nrepl-port 不在、NREPL_PORT 未設定)"
              "HINT: 別ターミナルで `clj -M:dev:nrepl` を起動してください"))))

(defn- load-persistent []
  (when (.exists (io/file session-file))
    (try (edn/read-string (slurp session-file))
         (catch RuntimeException _
           {:repl-eval/session-unreadable? true}))))

(defn- save-persistent [port sid last-req-id]
  (let [target-path (.toPath (io/file session-file))
        tmp-path    (Files/createTempFile (.toPath (io/file "."))
                                          ".nrepl-session."
                                          ".tmp"
                                          (make-array FileAttribute 0))
        content     (pr-str {:port port :session-id sid :last-request-id last-req-id})]
    (try
      (spit (.toFile tmp-path) content)
      (try
        (Files/move tmp-path target-path
                    (into-array CopyOption [StandardCopyOption/REPLACE_EXISTING
                                            StandardCopyOption/ATOMIC_MOVE]))
        (catch AtomicMoveNotSupportedException _
          (Files/move tmp-path target-path
                      (into-array CopyOption [StandardCopyOption/REPLACE_EXISTING]))))
      (finally
        (io/delete-file (.toFile tmp-path) true)))))

;; ---------------------------------------------------------------------------
;; nREPL protocol helpers (全 response 消費 + 必須 op 検証)
;; ---------------------------------------------------------------------------

(defn- consume
  "op を送って全 response を doall。status error は fatal。"
  [client msg]
  (let [resps (doall (nrepl/message client msg))
        statuses (set (mapcat :status resps))]
    (when-not (statuses "done")
      (fatal! (str "FATAL: nREPL op timed out before :done: " (pr-str (:op msg)))
              (str "  timeout-ms: " client-timeout-ms)
              (str "  statuses: " (pr-str statuses))
              (str "  responses: " (pr-str resps))))
    (when (statuses "error")
      (fatal! (str "FATAL: nREPL op failed: " (pr-str (:op msg)))
              (str "  statuses: " (pr-str statuses))
              (str "  responses: " (pr-str resps))))
    resps))

(defn- verification-eval-message [{:keys [session-id code]}]
  {:op "eval"
   :session session-id
   :ns "user"
   :code code
   :file "<repl-eval workbench verification>"
   :line 1})

(defn- verification-failed? [statuses resps]
  (or (statuses "error")
      (statuses "eval-error")
      (statuses "namespace-not-found")
      (some :ex resps)))

(defn- consume-eval-for-verification
  "workbench identity 検証用 eval。失敗時は文脈付き fatal にする。"
  [{:keys [client label] :as opts}]
  (let [resps (doall (nrepl/message client (verification-eval-message opts)))
        statuses (set (mapcat :status resps))]
    (when-not (statuses "done")
      (fatal! (str "FATAL: workbench verification timed out: " label)
              (str "  timeout-ms: " client-timeout-ms)
              (str "  statuses: " (pr-str statuses))
              (str "  responses: " (pr-str resps))))
    (when (verification-failed? statuses resps)
      (fatal! (str "FATAL: invalid live workbench: " label)
              "HINT: nREPL は生きていますが、このテンプレートが期待する dev.user workbench ではありません。"
              "HINT: 別 workspace / 古い JVM に接続している可能性があります。"
              (str "  statuses: " (pr-str statuses))
              (str "  responses: " (pr-str resps))))
    resps))

(defn- first-edn-value [resps]
  (when-let [v (some :value resps)]
    (edn/read-string v)))

(defn- canonical-path [path]
  (.getCanonicalPath (io/file path)))

(defn- verify-workspace-root! [client session-id port]
  (let [expected (canonical-path ".")
        actual (first-edn-value
                (consume-eval-for-verification
                 {:client client
                  :session-id session-id
                  :code "(.getCanonicalPath (clojure.java.io/file (System/getProperty \"user.dir\")))"
                  :label "workspace root"}))]
    (when-not (= expected actual)
      (fatal! "FATAL: wrong nREPL JVM: workspace root が一致しません"
              (str "  port: " port)
              (str "  expected workspace: " expected)
              (str "  actual JVM user.dir: " actual)
              "HINT: .nrepl-port / NREPL_PORT が別 workspace の JVM を指している可能性があります。"
              "HINT: 正しい workspace で `clj -M:dev:nrepl` を起動し直してください。"))))

(defn- verify-dev-user-status! [client session-id port]
  (let [status (first-edn-value
                (consume-eval-for-verification
                 {:client client
                  :session-id session-id
                  :code "(do (require 'dev.user) (dev.user/status))"
                  :label "dev.user/status"}))
        always (-> status :capabilities :always set)
        required '#{status malli-on! probe safe-reset! hard-reset!}
        missing (seq (remove always required))]
    (when (or (not (map? status)) missing)
      (apply fatal!
             (cond-> ["FATAL: invalid live workbench: dev.user/status の shape が期待と異なります"
                      (str "  port: " port)
                      (str "  status: " (pr-str status))]
               missing (conj (str "  missing always capabilities: " (pr-str (vec missing))))
               true (conj "HINT: 古い dev.user を読んだ JVM、または別プロジェクトの JVM に接続している可能性があります。"))))))

(defn- verify-workbench! [client session-id port]
  (verify-workspace-root! client session-id port)
  (verify-dev-user-status! client session-id port))

(defn- op-name [op]
  (cond
    (keyword? op) (name op)
    (symbol? op)  (name op)
    :else         (str op)))

(defn- verify-ops! [client]
  (let [resps (consume client {:op "describe"})
        ops   (->> resps first :ops keys (map op-name) set)
        missing (remove ops required-ops)]
    (when (seq missing)
      (fatal! (str "FATAL: nREPL サーバに必須 op が欠けています: " (pr-str (sort missing)))
              "HINT: cider-nrepl middleware が有効か確認 (:nrepl alias の --middleware)"))))

(defn- existing-sessions [client]
  (->> (consume client {:op "ls-sessions"})
       (mapcat :sessions) set))

(defn- clone-session [client]
  (let [resps (consume client {:op "clone"})
        sid (some :new-session resps)]
    (when-not sid
      (fatal! "FATAL: clone op returned no :new-session"
              (str "  responses: " (pr-str resps))))
    sid))

(defn- reuse-persistent-session? [prior port sessions]
  (and prior
       (= port (:port prior))
       (contains? sessions (:session-id prior))))

(defn- session-context [{:keys [client port prior fresh?]}]
  (if (reuse-persistent-session? prior port (existing-sessions client))
    {:client client :session-id (:session-id prior) :port port :prior prior}
    (let [sid (clone-session client)]
      (when-not fresh?
        (save-persistent port sid nil))
      {:client client :session-id sid :port port})))

(defn- verify-context! [ctx command port]
  ;; interrupt は「直前の eval が詰まっている」場面で使うため、
  ;; 同一 session 上の事前 eval 検証で詰まらせない。
  (when-not (= command :interrupt)
    (verify-workbench! (:client ctx) (:session-id ctx) port)))

;; ---------------------------------------------------------------------------
;; Session lifecycle
;; ---------------------------------------------------------------------------

(defn- ensure-session
  "永続 session の存在を保証。port 変化 or session 失効時は clone。
   :fresh? true のときは常に新 session を作り、永続化しない。"
  [fresh? command]
  (let [port   (discover-port)
        prior  (when-not fresh? (load-persistent))
        conn   (nrepl/connect :port port)
        client (nrepl/client conn client-timeout-ms)]
    (try
      (verify-ops! client)
      (let [ctx (assoc (session-context {:client client
                                         :port port
                                         :prior prior
                                         :fresh? fresh?})
                       :conn conn)]
        (verify-context! ctx command port)
        ctx)
      (catch Throwable t
        (.close ^java.io.Closeable conn)
        (throw t)))))

;; ---------------------------------------------------------------------------
;; Bounded output
;; ---------------------------------------------------------------------------

(defn- bounded-print [s stream]
  (let [s (str s)]
    (binding [*out* stream]
      (if (> (count s) max-chunk-chars)
        (do (print (subs s 0 max-chunk-chars))
            (print "\n…<truncated at 10000 chars>\n"))
        (print s))
      (flush))))

;; ---------------------------------------------------------------------------
;; Response reporting (T4: eval-error / namespace-not-found / interrupted /
;;                    :ex / :root-ex を適切に扱う)
;; ---------------------------------------------------------------------------

(defn- report-response [resps]
  (let [statuses (set (mapcat :status resps))
        exit (cond
               (statuses "eval-error")         1
               (statuses "namespace-not-found") 1
               (statuses "interrupted")        130
               (some :ex resps)                1
               :else                           0)]
    (doseq [m resps]
      (when-let [o (:out m)]        (bounded-print o *out*))
      (when-let [e (:err m)]        (bounded-print e *err*))
      (when-let [v (:value m)]      (println v))
      (when-let [ex (:ex m)]        (binding [*out* *err*] (println "EX:" ex)))
      (when-let [root (:root-ex m)] (binding [*out* *err*] (println "ROOT:" root))))
    (when (statuses "namespace-not-found")
      (binding [*out* *err*]
        (println "HINT: 名前空間が読み込まれていません。--load-file で先に読み込むか (require '[...]) を実行してください。")))
    exit))

(defn- ensure-eval-done! [operation-name req-id resps]
  (let [statuses (set (mapcat :status resps))]
    (when-not (statuses "done")
      (fatal! (str "FATAL: nREPL " operation-name " did not finish before client timeout")
              (str "  timeout-ms: " client-timeout-ms)
              (str "  request-id: " req-id)
              (str "  statuses: " (pr-str statuses))
              "HINT: サーバ側の eval/load-file が継続している可能性があります。"
              "HINT: 必要なら `./.llm/scripts/repl-eval.sh --interrupt` で直近 request を中断してください。"))))

;; ---------------------------------------------------------------------------
;; Dispatch
;; ---------------------------------------------------------------------------

(defn- build-eval-op
  "eval / code-file から nREPL op message を組み立てる。file/line metadata は常時付与。
   引数 key は ns / code-file / load-file（clojure.core と shadow するが destructuring 局所）。"
  [session-id {expr :expr cf :code-file tgt-ns :ns :or {tgt-ns "dev.user"}}]
  (cond
    (and expr cf)
    (throw (ex-info "--expr と --code-file は同時指定できません" {}))

    expr
    {:op "eval" :session session-id :code expr :ns tgt-ns :file "<--expr>" :line 1}

    cf
    (let [f (io/file cf)]
      {:op "eval" :session session-id :code (slurp f) :ns tgt-ns
       :file (.getAbsolutePath f) :line 1})

    :else
    (throw (ex-info "--expr / --code-file / --load-file のいずれか必須" {}))))

(defn- ensure-default-workbench-ns-loaded! [client session-id opts]
  (when (and (not (:ns-explicit opts))
             (= "dev.user" (or (:ns opts) "dev.user")))
    (consume client {:op "eval"
                     :session session-id
                     :ns "user"
                     :code "(require 'dev.user)"
                     :file "<repl-eval bootstrap>"
                     :line 1})))

(defn- dispatch-eval [{:keys [session-id client port]} opts]
  (let [req-id (str (random-uuid))
        _ (ensure-default-workbench-ns-loaded! client session-id opts)
        op (-> (build-eval-op session-id opts) (assoc :id req-id))
        _  (save-persistent port session-id req-id)  ; T1: 送信前に永続化
        resps (doall (nrepl/message client op))
        rc (report-response resps)]
    (ensure-eval-done! "eval" req-id resps)
    rc))

(defn- dispatch-load [{:keys [session-id client port]} {lf :load-file}]
  (let [f (io/file lf)
        _ (when-not (.exists f)
            (fatal! (str "FATAL: load-file 対象が存在しません: " lf)))
        req-id (str (random-uuid))
        op {:op "load-file" :session session-id :id req-id
            :file (slurp f)
            :file-name (.getName f)
            :file-path (.getAbsolutePath f)}
        _  (save-persistent port session-id req-id)
        resps (doall (nrepl/message client op))
        rc (report-response resps)]
    (ensure-eval-done! "load-file" req-id resps)
    rc))

(defn- dispatch-interrupt [{:keys [session-id client]}]
  (let [{:keys [last-request-id]} (load-persistent)]
    (when-not last-request-id
      (fatal! "FATAL: 直前の request-id が記録されていません (eval 未実行 or .nrepl-session 欠落)"))
    (consume client {:op "interrupt" :session session-id :interrupt-id last-request-id})
    0))

(defn- dispatch-describe [{:keys [client]}]
  (let [d (first (consume client {:op "describe"}))]
    (println (pr-str {:versions (:versions d)
                      :ops      (-> d :ops keys sort)}))
    0))

;; ---------------------------------------------------------------------------
;; Entry point
;; ---------------------------------------------------------------------------

(defn- run-command [command ctx opts]
  (case command
    :eval      (dispatch-eval ctx opts)
    :load      (dispatch-load ctx opts)
    :interrupt (dispatch-interrupt ctx)
    :describe  (dispatch-describe ctx)
    (fatal! (str "FATAL: unknown command: " command))))

(defn- handle-reset-session []
  (io/delete-file session-file true)
  (println "session reset")
  0)

(defn- run-with-session [fresh command opts]
  (let [ctx (ensure-session (boolean fresh) command)]
    (try
      (run-command command ctx opts)
      (finally
        (.close ^java.io.Closeable (:conn ctx))))))

(defn run
  "exec-fn entry. opts keys:
     :command  — :eval (default) | :load | :interrupt | :describe | :reset-session
     :fresh    — true で ephemeral session (永続化しない)
     :expr     — eval 対象の code 文字列
     :code-file— eval 対象の code を含むファイルパス
     :load-file— load-file op の対象ファイルパス
     :ns       — eval の context namespace (default: dev.user)"
  [{:keys [command fresh] :or {command :eval} :as opts}]
  (try
    (let [rc (if (= command :reset-session)
               (handle-reset-session)
               (run-with-session fresh command opts))]
      (System/exit rc))
    (catch clojure.lang.ExceptionInfo e
      (let [data (ex-data e)
            fatal? (:repl-eval/fatal? data)
            exit-code (or (:exit-code data) 2)
            messages (:messages data)]
        (if fatal?
          (exit! exit-code messages)
          (exit! 2 [(str "ERROR: " (ex-message e))
                    (str "  data: " (pr-str (ex-data e)))]))))
    (catch java.io.IOException e
      (exit! 2 [(str "ERROR: I/O 失敗: " (ex-message e))]))
    (catch RuntimeException e
      (exit! 2 [(str "ERROR: " (ex-message e))]))))
