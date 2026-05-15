(ns check-trace-metadata
  "Validate :trace/* metadata on stable public boundaries and tests."
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.set :as set]))

(def trace-keys
  #{:trace/requirements
    :trace/use-cases
    :trace/test-obligations})

(defn- slurp-if-exists [path]
  (when (.isFile (io/file path))
    (slurp path)))

(defn- read-edn-if-exists [path]
  (when-let [s (slurp-if-exists path)]
    (edn/read-string s)))

(defn- design-ir []
  (or (read-edn-if-exists ".llm/data/design-ir.edn")
      {:requirements []
       :use-cases []
       :test-obligations []}))

(defn- repo-context []
  (or (read-edn-if-exists ".llm/repo-context.edn")
      {}))

(defn- adoption-mode []
  (or (:adoption-mode (repo-context))
      :retrofit))

(defn- known-ids [ir k]
  (set (map :id (get ir k))))

(defn- obligation-index [ir]
  (into {} (map (juxt :id identity) (:test-obligations ir))))

(defn- clj-files []
  (let [roots ["components" "bases" "projects" "development/src"]]
    (->> roots
         (map io/file)
         (filter #(.exists %))
         (mapcat file-seq)
         (filter #(.isFile %))
         (map #(.getPath %))
         (filter #(re-find #"\.clj[cs]?$" %))
         sort
         vec)))

(defn- line-numbering-reader [path]
  (clojure.lang.LineNumberingPushbackReader.
   (io/reader path)))

(defn- read-forms [path]
  (let [eof (Object.)]
    (with-open [r (line-numbering-reader path)]
      (loop [forms []]
        (let [form (binding [*read-eval* false]
                     (read {:eof eof} r))]
          (if (identical? eof form)
            forms
            (recur (conj forms form))))))))

(defn- trace-map? [x]
  (and (map? x)
       (boolean (some trace-keys (keys x)))))

(defn- trace-metadata [x]
  (when (instance? clojure.lang.IObj x)
    (let [m (meta x)]
      (when (trace-map? m)
        m))))

(declare form-contains-trace?)

(defn- form-contains-trace? [form]
  (or (boolean (trace-metadata form))
      (cond
        (seq? form) (some form-contains-trace? form)
        :else false)))

(defn- sym-name [x]
  (when (symbol? x)
    (name x)))

(defn- form-kind [form]
  (when (seq? form)
    (case (sym-name (first form))
      "defn" :defn
      "defn-" :defn-private
      "deftest" :deftest
      nil)))

(defn- name-symbol [form]
  (second form))

(defn- attr-map [form]
  (let [xs (drop 2 form)
        xs (if (string? (first xs)) (rest xs) xs)]
    (when (map? (first xs))
      (first xs))))

(defn- public-defn? [form]
  (and (= :defn (form-kind form))
       (not (:private (meta (name-symbol form))))
       (not (:private (attr-map form)))))

(defn- trace-from-var [form]
  (let [m1 (trace-metadata (name-symbol form))
        m2 (when (trace-map? (attr-map form)) (attr-map form))]
    (merge m1 m2)))

(defn- boundary-code-file? [path]
  (or (boolean (re-find #"^components/[^/]+/src/.*/interface\.clj$" path))
      (boolean (re-find #"^bases/[^/]+/src/.*/(core|handler)\.clj[cs]?$" path))))

(defn- test-file? [path]
  (or (boolean (re-find #"(^|/)test/" path))
      (boolean (re-find #"_test\.clj[cs]?$" path))))

(defn- normalize-ids [x]
  (cond
    (nil? x) []
    (sequential? x) (vec x)
    :else ::invalid))

(defn- line [form]
  (or (:line (meta form))
      (:line (meta (name-symbol form)))
      "?"))

(defn- error [msg]
  {:level :error :message msg})

(defn- warn [msg]
  {:level :warn :message msg})

(defn- warn-or-error [strict? msg]
  (if strict?
    (error msg)
    (warn msg)))

(defn- blank-id? [x]
  (and (string? x)
       (= "" (.trim x))))

(defn- duplicate-ids [ids]
  (->> ids
       frequencies
       (filter (fn [[_ n]] (> n 1)))
       (map first)
       vec))

(defn- key-present? [m k]
  (contains? m k))

(defn- validate-id-list [path form k ids known label]
  (cond
    (= ::invalid ids)
    [(error (str path ":" (line form) " " k " must be a vector/list of strings"))]

    (and (key-present? (trace-from-var form) k) (empty? ids))
    [(error (str path ":" (line form) " " k " must not be empty"))]

    :else
    (vec
     (concat
      (->> ids
           (remove string?)
           (map #(error (str path ":" (line form) " " k " must contain only strings: " (pr-str %)))))
      (->> ids
           (filter blank-id?)
           (map (fn [_]
                  (error (str path ":" (line form) " " k " must not contain blank id")))))
      (->> (duplicate-ids ids)
           (map #(error (str path ":" (line form) " duplicate " label " id in " k ": " %))))
      (->> ids
           (filter string?)
           (remove blank-id?)
           (remove known)
           (map #(error (str path ":" (line form) " unknown " label " id in " k ": " %))))))))

(defn- valid-id-set [ids]
  (if (= ::invalid ids)
    #{}
    (->> ids
         (filter string?)
         (remove blank-id?)
         set)))

(defn- validate-defn-trace [path form trace ir]
  (let [reqs (normalize-ids (:trace/requirements trace))
        ucs (normalize-ids (:trace/use-cases trace))
        tos (normalize-ids (:trace/test-obligations trace))
        known-reqs (known-ids ir :requirements)
        known-ucs (known-ids ir :use-cases)]
    (vec
     (concat
      (when-not (public-defn? form)
        [(error (str path ":" (line form) " trace metadata is allowed only on public boundary defn, not private/internal defn"))])
      (when-not (boundary-code-file? path)
        [(error (str path ":" (line form) " trace metadata on implementation code is forbidden; put it on component interface or base core/handler boundary"))])
      (when (seq (remove nil? [(:trace/test-obligations trace)]))
        [(error (str path ":" (line form) " :trace/test-obligations belongs on deftest, not implementation code"))])
      (when (and (empty? (valid-id-set reqs))
                 (empty? (valid-id-set ucs))
                 (not= ::invalid reqs)
                 (not= ::invalid ucs))
        [(error (str path ":" (line form) " implementation trace metadata must include at least one requirement or use-case id"))])
      (validate-id-list path form :trace/requirements reqs known-reqs "requirement")
      (validate-id-list path form :trace/use-cases ucs known-ucs "use-case")
      (when (= ::invalid tos)
        [(error (str path ":" (line form) " :trace/test-obligations must be a vector/list of strings"))])))))

(defn- validate-deftest-related-trace [path form trace ir tos reqs ucs strict?]
  (let [idx (obligation-index ir)
        obligation-maps (keep idx (valid-id-set tos))
        expected-reqs (set (mapcat :related-requirements obligation-maps))
        expected-ucs (set (mapcat :related-use-cases obligation-maps))
        actual-reqs (valid-id-set reqs)
        actual-ucs (valid-id-set ucs)]
    (vec
     (concat
      (when (and (seq actual-reqs)
                 (seq expected-reqs)
                 (not (set/subset? expected-reqs actual-reqs)))
        [(warn-or-error strict?
                        (str path ":" (line form) " test trace requirements do not cover related requirements from test obligations: "
                             (pr-str (sort (set/difference expected-reqs actual-reqs)))))])
      (when (and (seq actual-ucs)
                 (seq expected-ucs)
                 (not (set/subset? expected-ucs actual-ucs)))
        [(warn-or-error strict?
                        (str path ":" (line form) " test trace use-cases do not cover related use-cases from test obligations: "
                             (pr-str (sort (set/difference expected-ucs actual-ucs)))))])
      (when (and (seq actual-reqs)
                 (seq expected-reqs)
                 (seq (set/difference actual-reqs expected-reqs)))
        [(warn-or-error strict?
                        (str path ":" (line form) " test trace requirements include ids not related from its test obligations: "
                             (pr-str (sort (set/difference actual-reqs expected-reqs)))))])
      (when (and (seq actual-ucs)
                 (seq expected-ucs)
                 (seq (set/difference actual-ucs expected-ucs)))
        [(warn-or-error strict?
                        (str path ":" (line form) " test trace use-cases include ids not related from its test obligations: "
                             (pr-str (sort (set/difference actual-ucs expected-ucs)))))])))))

(defn- validate-deftest-trace [path form trace ir strict?]
  (let [reqs (normalize-ids (:trace/requirements trace))
        ucs (normalize-ids (:trace/use-cases trace))
        tos (normalize-ids (:trace/test-obligations trace))
        known-reqs (known-ids ir :requirements)
        known-ucs (known-ids ir :use-cases)
        known-tos (known-ids ir :test-obligations)]
    (vec
     (concat
      (when-not (test-file? path)
        [(error (str path ":" (line form) " deftest trace metadata must live in a test file"))])
      (when (and (or (key-present? trace :trace/requirements)
                     (key-present? trace :trace/use-cases))
                 (not (key-present? trace :trace/test-obligations)))
        [(error (str path ":" (line form) " deftest trace metadata must include :trace/test-obligations when requirements/use-cases are present"))])
      (validate-id-list path form :trace/requirements reqs known-reqs "requirement")
      (validate-id-list path form :trace/use-cases ucs known-ucs "use-case")
      (validate-id-list path form :trace/test-obligations tos known-tos "test-obligation")
      (validate-deftest-related-trace path form trace ir tos reqs ucs strict?)))))

(defn- trace-obligations [form trace]
  (when (= :deftest (form-kind form))
    (let [ids (normalize-ids (:trace/test-obligations trace))]
      (when-not (= ::invalid ids)
        ids))))

(defn- validate-form [path ir strict? form]
  (let [kind (form-kind form)
        trace (trace-from-var form)
        contains? (or (seq trace)
                      (form-contains-trace? form))]
    (cond
      (not contains?) {:messages [] :test-obligations []}

      (empty? trace)
      {:messages [(error (str path ":" (line form) " trace metadata must be attached to the defn/deftest var metadata or attr-map"))]
       :test-obligations []}

      (= :defn kind)
      {:messages (validate-defn-trace path form trace ir)
       :test-obligations []}

      (= :defn-private kind)
      {:messages [(error (str path ":" (line form) " trace metadata is not allowed on private defn"))]
       :test-obligations []}

      (= :deftest kind)
      {:messages (validate-deftest-trace path form trace ir strict?)
       :test-obligations (or (trace-obligations form trace) [])}

      :else
      {:messages [(error (str path ":" (line form) " trace metadata is allowed only on public boundary defn or deftest"))]
       :test-obligations []})))

(defn- validate-file [ir strict? path]
  (try
    (let [results (map #(validate-form path ir strict? %) (read-forms path))]
      {:messages (vec (mapcat :messages results))
       :test-obligations (vec (mapcat :test-obligations results))})
    (catch Throwable e
      {:messages [(error (str path " could not be read as Clojure: " (.getMessage e)))]
       :test-obligations []})))

(defn run [& _]
  (let [ir (design-ir)
        mode (adoption-mode)
        strict? (= :complete mode)
        results (map #(validate-file ir strict? %) (clj-files))
        messages (vec (mapcat :messages results))
        referenced-obligations (set (mapcat :test-obligations results))
        known-obligations (known-ids ir :test-obligations)
        untested (sort (set/difference known-obligations referenced-obligations))
        messages (into messages
                       (map #(warn-or-error strict?
                                            (str "test obligation has no deftest trace metadata: " %))
                            untested))
        errors (filter #(= :error (:level %)) messages)
        warnings (filter #(= :warn (:level %)) messages)]
    (doseq [m errors]
      (println "ERROR:" (:message m)))
    (doseq [m warnings]
      (println "WARN:" (:message m)))
    (if (seq errors)
      (do
        (println (str "check-trace-metadata: FAILED (" (count errors) " errors, " (count warnings) " warnings)"))
        (System/exit 1))
      (do
        (println (str "check-trace-metadata: OK"
                      (when (seq warnings)
                        (str " (" (count warnings) " warnings)"))))
        (System/exit 0)))))
