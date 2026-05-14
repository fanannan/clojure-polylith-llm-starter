(ns gen-trace-index
  "Generate trace index from design-ir.edn and :trace/* metadata."
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.pprint :as pprint]
   [clojure.set :as set]
   [clojure.string :as str]))

(def trace-keys
  #{:trace/requirements
    :trace/use-cases
    :trace/test-obligations})

(def generated-header
  "<!-- GENERATED FILE. DO NOT EDIT BY HAND.

Sources:
- .llm/data/design-ir.edn
- components/**/src/**/interface.clj
- bases/**/src/**/{core,handler}.clj
- **/test/**/*.clj

Regenerate with:
  clj -Sdeps '{:paths [\".llm/scripts\"]}' -X gen-trace-index/generate
-->

# Trace Map

このファイルは自動生成物です。直接編集しないでください。
仕様 ID と public boundary / deftest の対応を変える場合は、Clojure metadata を更新してから再生成します。
")

(defn- file? [path]
  (.isFile (io/file path)))

(defn- read-edn-if-exists [path]
  (when (file? path)
    (edn/read-string (slurp path))))

(defn- write-file! [path content]
  (io/make-parents path)
  (spit path content))

(defn- design-ir []
  (or (read-edn-if-exists ".llm/data/design-ir.edn")
      {:requirements []
       :use-cases []
       :test-obligations []}))

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

(defn- sym-name [x]
  (when (symbol? x)
    (name x)))

(defn- form-kind [form]
  (when (seq? form)
    (case (sym-name (first form))
      "defn" :defn
      "deftest" :deftest
      nil)))

(defn- name-symbol [form]
  (second form))

(defn- attr-map [form]
  (let [xs (drop 2 form)
        xs (if (string? (first xs)) (rest xs) xs)]
    (when (map? (first xs))
      (first xs))))

(defn- trace-from-var [form]
  (let [m1 (trace-metadata (name-symbol form))
        m2 (when (trace-map? (attr-map form)) (attr-map form))]
    (merge m1 m2)))

(defn- form-line [form]
  (or (:line (meta form))
      (:line (meta (name-symbol form)))))

(defn- ns-form? [form]
  (and (seq? form) (= 'ns (first form))))

(defn- current-ns-name [forms]
  (some (fn [form]
          (when (ns-form? form)
            (str (second form))))
        forms))

(defn- id-vec [x]
  (->> x
       (filter string?)
       (remove str/blank?)
       distinct
       sort
       vec))

(defn- boundary-kind [path form-kind]
  (cond
    (= :deftest form-kind) :test
    (re-find #"^components/[^/]+/src/.*/interface\.clj$" path) :component-interface
    (re-find #"^bases/[^/]+/src/.*/core\.clj[cs]?$" path) :base-core
    (re-find #"^bases/[^/]+/src/.*/handler\.clj[cs]?$" path) :base-handler
    :else :other))

(defn- trace-entry [path ns-name form]
  (let [trace (trace-from-var form)
        kind (form-kind form)]
    (when (seq trace)
      {:kind (boundary-kind path kind)
       :form-kind kind
       :path path
       :line (form-line form)
       :ns ns-name
       :var (str ns-name "/" (name-symbol form))
       :trace/requirements (id-vec (:trace/requirements trace))
       :trace/use-cases (id-vec (:trace/use-cases trace))
       :trace/test-obligations (id-vec (:trace/test-obligations trace))})))

(defn- file-trace-entries [path]
  (let [forms (read-forms path)
        ns-name (current-ns-name forms)]
    (keep #(trace-entry path ns-name %) forms)))

(defn- trace-entries []
  (->> (clj-files)
       (mapcat file-trace-entries)
       (sort-by (juxt :path :line :var))
       vec))

(defn- by-id [entries k]
  (->> entries
       (mapcat (fn [entry]
                 (map #(vector % entry) (get entry k))))
       (group-by first)
       (map (fn [[id pairs]]
              [id (vec (map second pairs))]))
       (into (sorted-map))))

(defn- obligation-index [ir]
  (into {} (map (juxt :id identity) (:test-obligations ir))))

(defn- sorted-strings [xs]
  (vec (sort (set (remove nil? xs)))))

(defn- coverage [ir entries]
  (let [req-ids (set (map :id (:requirements ir)))
        uc-ids (set (map :id (:use-cases ir)))
        obligation-ids (set (map :id (:test-obligations ir)))
        impl-entries (remove #(= :test (:kind %)) entries)
        test-entries (filter #(= :test (:kind %)) entries)
        impl-reqs (set (mapcat :trace/requirements impl-entries))
        test-reqs (set (mapcat :trace/requirements test-entries))
        impl-ucs (set (mapcat :trace/use-cases impl-entries))
        test-ucs (set (mapcat :trace/use-cases test-entries))
        tested-obligations (set (mapcat :trace/test-obligations test-entries))]
    {:requirements {:defined (sorted-strings req-ids)
                    :implemented (sorted-strings impl-reqs)
                    :tested (sorted-strings test-reqs)
                    :missing-implementation-trace (sorted-strings (set/difference req-ids impl-reqs))
                    :missing-test-trace (sorted-strings (set/difference req-ids test-reqs))}
     :use-cases {:defined (sorted-strings uc-ids)
                 :implemented (sorted-strings impl-ucs)
                 :tested (sorted-strings test-ucs)
                 :missing-implementation-trace (sorted-strings (set/difference uc-ids impl-ucs))
                 :missing-test-trace (sorted-strings (set/difference uc-ids test-ucs))}
     :test-obligations {:defined (sorted-strings obligation-ids)
                        :tested (sorted-strings tested-obligations)
                        :missing-test-trace (sorted-strings (set/difference obligation-ids tested-obligations))}}))

(defn- impact-index [ir entries]
  (let [by-req (by-id entries :trace/requirements)
        by-uc (by-id entries :trace/use-cases)
        by-obligation (by-id entries :trace/test-obligations)
        obligation-by-id (obligation-index ir)]
    {:requirements
     (into (sorted-map)
           (for [req-id (sort (map :id (:requirements ir)))]
             [req-id
              {:implementation (vec (remove #(= :test (:kind %)) (get by-req req-id [])))
               :tests (vec (filter #(= :test (:kind %)) (get by-req req-id [])))
               :test-obligations (->> (:test-obligations ir)
                                      (filter #(contains? (set (:related-requirements %)) req-id))
                                      (map :id)
                                      sorted-strings)}]))
     :use-cases
     (into (sorted-map)
           (for [uc-id (sort (map :id (:use-cases ir)))]
             [uc-id
              {:implementation (vec (remove #(= :test (:kind %)) (get by-uc uc-id [])))
               :tests (vec (filter #(= :test (:kind %)) (get by-uc uc-id [])))
               :test-obligations (->> (:test-obligations ir)
                                      (filter #(contains? (set (:related-use-cases %)) uc-id))
                                      (map :id)
                                      sorted-strings)}]))
     :test-obligations
     (into (sorted-map)
           (for [obligation-id (sort (map :id (:test-obligations ir)))]
             [obligation-id
              {:tests (vec (get by-obligation obligation-id []))
               :related-requirements (:related-requirements (get obligation-by-id obligation-id))
               :related-use-cases (:related-use-cases (get obligation-by-id obligation-id))}]))}))

(defn- trace-index []
  (let [ir (design-ir)
        entries (trace-entries)]
    {:sources {:design-ir ".llm/data/design-ir.edn"
               :code-roots ["components" "bases" "projects" "development/src"]}
     :summary {:trace-entry-count (count entries)
               :implementation-trace-count (count (remove #(= :test (:kind %)) entries))
               :test-trace-count (count (filter #(= :test (:kind %)) entries))}
     :entries entries
     :by-requirement (by-id entries :trace/requirements)
     :by-use-case (by-id entries :trace/use-cases)
     :by-test-obligation (by-id entries :trace/test-obligations)
     :impact (impact-index ir entries)
     :coverage (coverage ir entries)}))

(defn- render-edn [index]
  (str ";; GENERATED - do not edit by hand.\n"
       ";; Source of truth: .llm/data/design-ir.edn and Clojure :trace/* metadata\n"
       ";; Regenerate with: clj -Sdeps '{:paths [\".llm/scripts\"]}' -X gen-trace-index/generate\n"
       (with-out-str (pprint/pprint index))))

(defn- linkish [entry]
  (str "`" (:var entry) "`"
       " (" (:path entry)
       (when (:line entry) (str ":" (:line entry)))
       ")"))

(defn- render-entry-list [entries]
  (if (seq entries)
    (str/join "\n" (map #(str "- " (linkish %)) entries))
    "- none"))

(defn- render-requirement [impact req]
  (let [item (get-in impact [:requirements (:id req)])]
    (str "\n## " (:id req) "\n\n"
         (:text req) "\n\n"
         "### Implementation\n\n"
         (render-entry-list (:implementation item)) "\n\n"
         "### Tests\n\n"
         (render-entry-list (:tests item)) "\n\n"
         "### Test Obligations\n\n"
         (if (seq (:test-obligations item))
           (str/join "\n" (map #(str "- `" % "`") (:test-obligations item)))
           "- none")
         "\n")))

(defn- render-obligation [impact obligation]
  (let [item (get-in impact [:test-obligations (:id obligation)])]
    (str "\n## " (:id obligation) "\n\n"
         (:text obligation) "\n\n"
         "- Related requirements: "
         (if (seq (:related-requirements item))
           (str/join ", " (map #(str "`" % "`") (:related-requirements item)))
           "none")
         "\n"
         "- Related use cases: "
         (if (seq (:related-use-cases item))
           (str/join ", " (map #(str "`" % "`") (:related-use-cases item)))
           "none")
         "\n\n"
         "### Tests\n\n"
         (render-entry-list (:tests item))
         "\n")))

(defn- render-doc [index ir]
  (let [impact (:impact index)
        coverage (:coverage index)]
    (str generated-header
         "\n## Summary\n\n"
         "- Trace entries: `" (get-in index [:summary :trace-entry-count]) "`\n"
         "- Implementation traces: `" (get-in index [:summary :implementation-trace-count]) "`\n"
         "- Test traces: `" (get-in index [:summary :test-trace-count]) "`\n"
         "- Requirements without implementation trace: "
         (if (seq (get-in coverage [:requirements :missing-implementation-trace]))
           (str/join ", " (map #(str "`" % "`") (get-in coverage [:requirements :missing-implementation-trace])))
           "none")
         "\n"
         "- Test obligations without test trace: "
         (if (seq (get-in coverage [:test-obligations :missing-test-trace]))
           (str/join ", " (map #(str "`" % "`") (get-in coverage [:test-obligations :missing-test-trace])))
           "none")
         "\n\n"
         "# Requirements\n"
         (if (seq (:requirements ir))
           (apply str (map #(render-requirement impact %) (:requirements ir)))
           "\nNo requirements are defined yet.\n")
         "\n# Test Obligations\n"
         (if (seq (:test-obligations ir))
           (apply str (map #(render-obligation impact %) (:test-obligations ir)))
           "\nNo test obligations are defined yet.\n"))))

(defn generate
  "Generate docs/TRACE.md and .llm/data/trace-index.edn."
  [{:keys [out-file index-file]}]
  (let [out-file (or out-file "docs/TRACE.md")
        index-file (or index-file ".llm/data/trace-index.edn")
        ir (design-ir)
        index (trace-index)]
    (write-file! out-file (render-doc index ir))
    (write-file! index-file (render-edn index))
    (println "generated" out-file)
    (println "generated" index-file)))

(defn check
  "Compare generated Trace Map with docs/TRACE.md and .llm/data/trace-index.edn."
  [_]
  (let [ir (design-ir)
        index (trace-index)
        expected-doc (render-doc index ir)
        expected-index (render-edn index)
        has-trace? (pos? (get-in index [:summary :trace-entry-count]))
        outputs-exist? (or (file? "docs/TRACE.md")
                           (file? ".llm/data/trace-index.edn"))]
    (cond
      (and (not has-trace?) (not outputs-exist?))
      (println "check-trace-index: OK (no trace metadata)")

      (not (file? "docs/TRACE.md"))
      (throw (ex-info "ERROR: docs/TRACE.md is missing. Run gen-trace-index/generate after adding trace metadata." {}))

      (not (file? ".llm/data/trace-index.edn"))
      (throw (ex-info "ERROR: .llm/data/trace-index.edn is missing. Run gen-trace-index/generate after adding trace metadata." {}))

      (and (= expected-doc (slurp "docs/TRACE.md"))
           (= expected-index (slurp ".llm/data/trace-index.edn")))
      (println "check-trace-index: OK")

      :else
      (throw (ex-info "ERROR: docs/TRACE.md or .llm/data/trace-index.edn is not synchronized with design-ir/trace metadata.\nFix: clj -Sdeps '{:paths [\".llm/scripts\"]}' -X gen-trace-index/generate" {})))))
