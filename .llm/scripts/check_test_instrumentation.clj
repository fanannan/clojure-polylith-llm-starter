(ns check-test-instrumentation
  "Parse `interface_test.clj` as Clojure forms and verify that at least one
   `use-fixtures :once ...` fixture actually enables Malli instrumentation via
   `malli.dev/start!`."
  (:require
   [clojure.java.io :as io]))

(defn- read-forms [file]
  (with-open [rdr (java.io.PushbackReader. (io/reader file))]
    (loop [forms []]
      (let [form (read {:eof ::eof} rdr)]
        (if (= ::eof form)
          forms
          (recur (conj forms form)))))))

(defn- require-spec->alias [spec]
  (cond
    (symbol? spec)
    {(name spec) spec}

    (vector? spec)
    (let [lib  (first spec)
          opts (rest spec)
          amap (apply hash-map opts)
          alias (or (:as amap) lib)]
      {(name lib) alias})

    :else
    {}))

(defn- malli-dev-alias [forms]
  (let [ns-form (some #(when (and (seq? %) (= 'ns (first %))) %) forms)
        clauses (drop 2 ns-form)
        aliases (->> clauses
                     (filter seq?)
                     (filter #(= :require (first %)))
                     (mapcat rest)
                     (map require-spec->alias)
                     (apply merge {}))]
    (or (get aliases "malli.dev")
        'malli.dev)))

(defn- defn-form? [form]
  (and (seq? form)
       (contains? #{'defn 'defn-} (first form))
       (symbol? (second form))))

(defn- fixture-defs [forms]
  (into {}
        (keep (fn [form]
                (when (defn-form? form)
                  [(second form) form])))
        forms))

(defn- direct-call? [x target]
  (and (seq? x)
       (= target (first x))))

(defn- contains-call? [form target]
  (boolean
   (some #(direct-call? % target)
         (tree-seq coll? seq form))))

(defn- once-fixtures [forms]
  (->> forms
       (filter seq?)
       (filter #(= 'use-fixtures (first %)))
       (mapcat (fn [form]
                 (let [[_ scope & fns] form]
                   (when (= :once scope)
                     (filter symbol? fns)))))
       distinct))

(defn- instrumentation-enabled? [form mdev-alias]
  (let [start-sym   (symbol (str mdev-alias) "start!")
        stop-sym    (symbol (str mdev-alias) "stop!")
        has-start?  (contains-call? form start-sym)
        has-stop?   (contains-call? form stop-sym)
        has-try?    (contains-call? form 'try)
        has-finally? (contains-call? form 'finally)]
    {:has-start? has-start?
     :has-stop? has-stop?
     :has-try? has-try?
     :has-finally? has-finally?}))

(defn- check-file [file]
  (let [forms         (read-forms file)
        mdev-alias    (malli-dev-alias forms)
        defs-by-name  (fixture-defs forms)
        once-fns      (once-fixtures forms)
        once-defs     (keep defs-by-name once-fns)
        fixture-checks (map #(instrumentation-enabled? % mdev-alias) once-defs)
        enabled?      (some #(and (:has-start? %)
                                  (:has-stop? %)
                                  (:has-try? %)
                                  (:has-finally? %))
                            fixture-checks)
        missing-once? (empty? once-fns)]
    (cond
      missing-once?
      [(str "ERROR: " file " に (use-fixtures :once ...) がありません")
       "  → Malli instrumentation は :once fixture で有効化してください"
       "  → POLYLITH_GUIDE.md §2.1 の interface_test.clj 雛形を参照"]

      (empty? once-defs)
      [(str "ERROR: " file " の (use-fixtures :once ...) が defn fixture を参照していません")
       "  → `with-malli-instrumentation` のような named fixture を defn で定義してください"
       "  → 匿名 fixture はテンプレート規約外です"
       "  → POLYLITH_GUIDE.md §2.1 の interface_test.clj 雛形を参照"]

      (not enabled?)
      [(str "ERROR: " file " の :once fixture で Malli instrumentation fixture の完全形が確認できません")
       (str "  → " (pr-str once-fns) " のいずれかで " mdev-alias "/start! と " mdev-alias "/stop! を使ってください")
       "  → fixture は (try (f) (finally (mdev/stop!))) の形にしてください"
       "  → POLYLITH_GUIDE.md §2.1 の interface_test.clj 雛形を参照"]

      :else
      nil)))

(defn- target-files []
  (->> ["components" "bases"]
       (map io/file)
       (filter #(.exists %))
       (mapcat #(file-seq %))
       (filter #(.isFile %))
       (filter #(= "interface_test.clj" (.getName %)))
       (map #(.getPath %))
       sort))

(defn run [_]
  (let [files   (target-files)
        errors  (mapcat check-file files)]
    (cond
      (empty? files)
      (do
        (println "check-test-instrumentation: no interface_test.clj files, skipped")
        (System/exit 0))

      (seq errors)
      (do
        (doseq [line errors] (println line))
        (System/exit 1))

      :else
      (do
        (println "check-test-instrumentation: OK")
        (System/exit 0)))))
