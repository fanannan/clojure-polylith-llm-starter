(ns check-brick-deps-self-contained
  "Verify each brick's src requires are satisfied by the brick's OWN deps.edn.

   Polylith は brick の deps.edn を依存の一次情報源とする（BOOTSTRAP_GUIDE.md
   §2.4）。しかし development の :dev alias は全 brick 依存を統合するため、
   brick の src が external library を require していても deps.edn にその依存が
   無いまま REPL では通ってしまう（例: malli が dev alias 経由でだけ解決される）。
   uberjar build や CI の brick 単位解決で初めて壊れる。

   本検査は各 brick について:
     - src/**/*.clj / *.cljc の (ns ...) 宣言から require namespace を収集
     - workspace 内 brick の namespace（:top-namespace 配下）は除外
       （inter-brick 依存は poly check が検証する）
     - 残った external namespace を、その brick 自身の deps.edn 由来 classpath
       （brick ディレクトリで clj -Spath、:dev alias を含まない）で解決可能か照合
     - 解決不能なら ERROR

   exit code: 0 = 全 brick 自己完結 / 1 = 解決不能な require あり、または brick の
   deps.edn 解決失敗。"
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str])
  (:import
   [java.io PushbackReader]
   [java.util.zip ZipFile]))

(def ^:private path-separator (System/getProperty "path.separator"))

(defn- slurp-if-exists [path]
  (let [f (io/file path)]
    (when (.isFile f)
      (slurp f))))

(defn- top-namespace
  "workspace.edn の :top-namespace（文字列）。無ければ nil。"
  []
  (when-let [s (slurp-if-exists "workspace.edn")]
    (try
      (:top-namespace (edn/read-string s))
      (catch Exception _ nil))))

(defn- brick-dirs
  "components/* / bases/* のうち deps.edn を持つディレクトリのパス一覧。"
  []
  (->> ["components" "bases"]
       (mapcat (fn [root]
                 (let [d (io/file root)]
                   (when (.isDirectory d)
                     (filter #(.isDirectory %) (.listFiles d))))))
       (filter #(.isFile (io/file % "deps.edn")))
       (map #(.getPath %))
       sort))

(defn- clj-source-files
  "brick の src/ 配下の .clj / .cljc ファイル。"
  [brick-dir]
  (let [src (io/file brick-dir "src")]
    (when (.isDirectory src)
      (->> (file-seq src)
           (filter #(.isFile %))
           (filter #(re-find #"\.cljc?$" (.getName %)))))))

(defn- read-ns-form
  "ファイル先頭から (ns ...) フォームを 1 つ読む。.cljc の reader conditional は
   :clj feature で解決する。読めなければ nil。"
  [file]
  (with-open [r (PushbackReader. (io/reader file))]
    (binding [*read-eval* false]
      (loop [guard 0]
        (if (> guard 200)
          nil
          (let [form (try
                       (read {:read-cond :allow :features #{:clj} :eof ::eof} r)
                       (catch Exception _ ::eof))]
            (cond
              (= form ::eof) nil
              (and (seq? form) (= 'ns (first form))) form
              :else (recur (inc guard)))))))))

(defn- spec->namespaces
  "1 つの :require spec から namespace symbol を抽出する。
   対応: 素の symbol / [ns & opts] / prefix list [prefix sub ...]。"
  [spec]
  (cond
    (symbol? spec) [spec]
    (vector? spec)
    (let [[head & more] spec]
      (cond
        (not (symbol? head)) []
        ;; [ns :as alias ...] — opts が続く、または単独
        (or (empty? more) (keyword? (first more))) [head]
        ;; prefix list — [clojure.data [json :as j] csv ...]
        :else (mapcat (fn [sub]
                        (map #(symbol (str head "." %))
                             (spec->namespaces sub)))
                      more)))
    :else []))

(defn- ns-requires
  "(ns ...) フォームから :require された namespace symbol を全列挙する。"
  [ns-form]
  (->> ns-form
       (filter #(and (sequential? %) (= :require (first %))))
       (mapcat rest)
       (mapcat spec->namespaces)
       (filter symbol?)))

(defn- workspace-internal?
  "workspace 内 brick の namespace か（:top-namespace 配下）。"
  [ns-sym top-ns]
  (boolean
   (when top-ns
     (let [s (str ns-sym)]
       (or (= s (str top-ns))
           (str/starts-with? s (str top-ns ".")))))))

(defn- brick-classpath
  "brick ディレクトリで `clj -Spath` を実行し、classpath エントリのベクタを返す。
   :dev alias は付けない（brick deps.edn 由来の classpath を得る）。
   失敗時は {:error <stderr>}。"
  [brick-dir]
  (let [pb (doto (ProcessBuilder. ["clj" "-Spath"])
             (.directory (io/file brick-dir)))
        proc (.start pb)
        out (slurp (.getInputStream proc))
        err (slurp (.getErrorStream proc))
        code (.waitFor proc)]
    (if (zero? code)
      {:ok (->> (str/split (str/trim out) (re-pattern (java.util.regex.Pattern/quote path-separator)))
                (remove str/blank?)
                vec)}
      {:error (str/trim err)})))

(defn- ns->paths
  "namespace symbol を classpath 上の候補ファイルパスへ変換する。"
  [ns-sym]
  (let [base (-> (str ns-sym)
                 (str/replace "." "/")
                 (str/replace "-" "_"))]
    [(str base ".clj") (str base ".cljc")]))

(defn- ns-resolvable?
  "namespace が classpath エントリ（ディレクトリ / jar）のいずれかに存在するか。"
  [cp-entries ns-sym]
  (let [paths (ns->paths ns-sym)]
    (boolean
     (some (fn [entry]
             (let [f (io/file entry)]
               (cond
                 (.isDirectory f)
                 (some #(.isFile (io/file f %)) paths)

                 (and (.isFile f) (str/ends-with? entry ".jar"))
                 (try
                   (with-open [zf (ZipFile. f)]
                     (boolean (some #(.getEntry zf %) paths)))
                   (catch Exception _ false))

                 :else false)))
           cp-entries))))

(defn- check-brick
  "1 brick の自己完結性を検査し、ERROR 行のベクタを返す。"
  [brick-dir top-ns]
  (let [required (->> (clj-source-files brick-dir)
                      (keep read-ns-form)
                      (mapcat ns-requires)
                      (remove #(workspace-internal? % top-ns))
                      distinct
                      sort)
        cp (brick-classpath brick-dir)]
    (if (:error cp)
      [(str "ERROR: " brick-dir "/deps.edn の依存解決に失敗しました（clj -Spath）")
       (str "  " (or (first (str/split-lines (:error cp))) "(詳細なし)"))]
      (vec
       (for [ns-sym required
             :when (not (ns-resolvable? (:ok cp) ns-sym))]
         (str "ERROR: " brick-dir " が `" ns-sym "` を require していますが、"
              brick-dir "/deps.edn 由来の classpath が供給しません"
              "（brick deps.edn に該当ライブラリ、または該当 brick の :local/root を追加してください）"))))))

(defn run
  "clj -X エントリポイント。全 brick の deps.edn 自己完結性を検査し、
   解決不能な require があれば exit 1。"
  [_]
  (let [bricks (brick-dirs)]
    (if (empty? bricks)
      (do
        (println "check-brick-deps-self-contained: OK (no bricks)")
        (System/exit 0))
      (let [top-ns (top-namespace)
            errors (vec (mapcat #(check-brick % top-ns) bricks))]
        (doseq [line errors] (println line))
        (if (seq errors)
          (do
            (println (str "check-brick-deps-self-contained: FAILED ("
                           (count errors) " 件)"))
            (System/exit 1))
          (do
            (println (str "check-brick-deps-self-contained: OK ("
                           (count bricks) " bricks)"))
            (System/exit 0)))))))
