(ns trace-impact
  "Query .llm/data/trace-index.edn for specification impact."
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.java.shell :as shell]
   [clojure.string :as str]
   [gen-trace-index :as gen-trace-index]))

(def trace-index-path ".llm/data/trace-index.edn")

(defn- file? [path]
  (.isFile (io/file path)))

(defn- read-edn-if-exists [path]
  (when (file? path)
    (edn/read-string (slurp path))))

(defn- repo-context []
  (or (read-edn-if-exists ".llm/repo-context.edn") {}))

(defn- adoption-mode []
  (or (:adoption-mode (repo-context)) :retrofit))

(defn- trace-index []
  (read-edn-if-exists trace-index-path))

(defn- die! [& lines]
  (binding [*out* *err*]
    (doseq [line lines]
      (println line)))
  (System/exit 1))

(defn- ensure-index! []
  (or (trace-index)
      (die! "ERROR: .llm/data/trace-index.edn is missing."
            "Run: ./.llm/scripts/gen-trace-index.sh")))

(defn- stale-status []
  (try
    (with-out-str (gen-trace-index/check nil))
    :ok
    (catch Throwable e
      {:error (.getMessage e)})))

(defn- entry-line [entry]
  (str "  - " (:var entry)
       " [" (name (:kind entry)) "]"
       "\n    " (:path entry)
       (when (:line entry) (str ":" (:line entry)))))

(defn- print-list [title entries]
  (println (str title ":"))
  (if (seq entries)
    (doseq [entry entries]
      (println (entry-line entry)))
    (println "  - none")))

(defn- print-id-list [title ids]
  (println (str title ":"))
  (if (seq ids)
    (doseq [id ids]
      (println "  -" id))
    (println "  - none")))

(defn- text-by-id [idx coll id]
  (some (fn [x] (when (= id (:id x)) (:text x))) (get idx coll)))

(defn- missing-section [items]
  (when (seq items)
    (doseq [item items]
      (println "  -" item))))

(defn- impact-requirement [idx id]
  (let [item (get-in idx [:impact :requirements id])]
    (println "Trace Impact:" id)
    (println)
    (println "Requirement:")
    (println " " id (or (text-by-id idx :requirements id) ""))
    (println)
    (print-list "Implementation" (:implementation item))
    (println)
    (print-list "Tests" (:tests item))
    (println)
    (print-id-list "Test obligations" (:test-obligations item))
    (println)
    (println "Missing:")
    (let [missing (cond-> []
                    (empty? (:implementation item)) (conj "implementation trace")
                    (empty? (:tests item)) (conj "test trace"))]
      (if (seq missing)
        (missing-section missing)
        (println "  - none")))))

(defn- impact-use-case [idx id]
  (let [item (get-in idx [:impact :use-cases id])]
    (println "Trace Impact:" id)
    (println)
    (println "Use case:")
    (println " " id (or (text-by-id idx :use-cases id) ""))
    (println)
    (print-list "Implementation" (:implementation item))
    (println)
    (print-list "Tests" (:tests item))
    (println)
    (print-id-list "Test obligations" (:test-obligations item))
    (println)
    (println "Missing:")
    (let [missing (cond-> []
                    (empty? (:implementation item)) (conj "implementation trace")
                    (empty? (:tests item)) (conj "test trace"))]
      (if (seq missing)
        (missing-section missing)
        (println "  - none")))))

(defn- impact-obligation [idx id]
  (let [item (get-in idx [:impact :test-obligations id])]
    (println "Trace Impact:" id)
    (println)
    (println "Test obligation:")
    (println " " id (or (text-by-id idx :test-obligations id) ""))
    (println)
    (print-id-list "Related requirements" (:related-requirements item))
    (println)
    (print-id-list "Related use cases" (:related-use-cases item))
    (println)
    (print-list "Tests" (:tests item))
    (println)
    (println "Missing:")
    (if (seq (:tests item))
      (println "  - none")
      (println "  - test trace"))))

(defn- query-id [idx id]
  (cond
    (contains? (get-in idx [:impact :requirements]) id)
    (impact-requirement idx id)

    (contains? (get-in idx [:impact :use-cases]) id)
    (impact-use-case idx id)

    (contains? (get-in idx [:impact :test-obligations]) id)
    (impact-obligation idx id)

    :else
    (do
      (println "Trace Impact:" id)
      (println)
      (println "No trace-index entry found for ID."))))

(defn- entries-for-path [idx path]
  (filter #(= path (:path %)) (:entries idx)))

(defn- entries-for-var [idx var-name]
  (filter #(= var-name (:var %)) (:entries idx)))

(defn- print-entry-impact [idx entry]
  (println (entry-line entry))
  (when (seq (:trace/requirements entry))
    (print-id-list "    Requirements" (:trace/requirements entry)))
  (when (seq (:trace/use-cases entry))
    (print-id-list "    Use cases" (:trace/use-cases entry)))
  (when (seq (:trace/test-obligations entry))
    (print-id-list "    Test obligations" (:trace/test-obligations entry))))

(defn- query-path-or-var [idx target]
  (let [entries (if (or (file? target)
                        (str/includes? target ".clj"))
                  (entries-for-path idx target)
                  (entries-for-var idx target))]
    (println "Trace Impact:" target)
    (println)
    (if (seq entries)
      (doseq [entry entries]
        (print-entry-impact idx entry)
        (println))
      (println "No direct trace metadata found for target."))))

(defn- git-diff-names [& args]
  (let [{:keys [exit out]} (apply shell/sh "git" args)]
    (if (zero? exit)
      (->> (str/split-lines out)
           (remove str/blank?))
      [])))

(defn- changed-files []
  (->> (concat (git-diff-names "diff" "--name-only")
               (git-diff-names "diff" "--cached" "--name-only"))
       distinct
       sort
       vec))

(defn- ids-from-entries [entries]
  (->> entries
       (mapcat (fn [entry]
                 (concat (:trace/requirements entry)
                         (:trace/use-cases entry)
                         (:trace/test-obligations entry))))
       distinct
       sort
       vec))

(defn- query-changed [idx]
  (let [files (changed-files)
        entries (filter #(contains? (set files) (:path %)) (:entries idx))
        ids (ids-from-entries entries)]
    (println "Trace Impact: --changed")
    (println)
    (print-id-list "Changed files" files)
    (println)
    (if (seq entries)
      (do
        (println "Direct trace entries:")
        (doseq [entry entries]
          (println (entry-line entry))))
      (println "Direct trace entries:\n  - none"))
    (println)
    (print-id-list "Related IDs" ids)
    (when (seq ids)
      (println)
      (println "Run:")
      (println "  ./.llm/scripts/trace-impact.sh" (str/join " " ids)))))

(defn- health [brief?]
  (let [status (stale-status)
        idx (trace-index)
        coverage (:coverage idx)
        missing-impl (count (get-in coverage [:requirements :missing-implementation-trace]))
        missing-test-req (count (get-in coverage [:requirements :missing-test-trace]))
        missing-obligation (count (get-in coverage [:test-obligations :missing-test-trace]))]
    (println "Trace Health:")
    (println "  trace-index:" (if (= :ok status) "OK" "STALE"))
    (println "  adoption-mode:" (name (adoption-mode)))
    (println "  missing implementation trace:" missing-impl)
    (println "  missing requirement test trace:" missing-test-req)
    (println "  missing test obligation trace:" missing-obligation)
    (when (and (not= :ok status) (not brief?))
      (println)
      (println "Repair:")
      (println "  ./.llm/scripts/gen-trace-index.sh")
      (println "  ./.llm/scripts/check-workspace-integrity.sh"))
    (when (and idx (not brief?))
      (println)
      (println "Explore:")
      (println "  ./.llm/scripts/trace-impact.sh REQ-001")
      (println "  ./.llm/scripts/trace-impact.sh --changed"))))

(defn- usage []
  (println "Usage:")
  (println "  ./.llm/scripts/trace-impact.sh --health [--brief]")
  (println "  ./.llm/scripts/trace-impact.sh --changed")
  (println "  ./.llm/scripts/trace-impact.sh REQ-001 [UC-1 AC-001 ...]")
  (println "  ./.llm/scripts/trace-impact.sh path/to/file.clj")
  (println "  ./.llm/scripts/trace-impact.sh my.ns/name"))

(defn -main [& args]
  (cond
    (empty? args)
    (usage)

    (= ["--health"] args)
    (health false)

    (= ["--health" "--brief"] args)
    (health true)

    (= ["--changed"] args)
    (query-changed (ensure-index!))

    :else
    (let [idx (ensure-index!)]
      (doseq [arg args]
        (if (re-matches #"[A-Z][A-Z0-9]+-[0-9A-F]+" arg)
          (query-id idx arg)
          (query-path-or-var idx arg))
        (println)))))
