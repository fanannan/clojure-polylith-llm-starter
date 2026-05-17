(ns check-instrument-cases
  "Validate Instruction-Following Instrument case catalog invariants.

   This checker protects the instrument from becoming a free-floating synthetic
   suite. Non-exploratory cases must trace to an observed incident or an authored
   mandate. Incident traces must be known to incident-index.edn, and mandate
   traces must point at authored llm-mandate annotations."
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(def default-cases ".llm/template-only/instrument/cases.edn")
(def default-incident-index ".llm/template-only/instrument/incident-index.edn")
(def default-mandate-root ".")

(def allowed-statuses #{:pilot :planned :exploratory :disabled})
(def allowed-target-modes #{:template :project})
(def allowed-project-phases #{:bootstrap :development})
(def allowed-instruction-kinds #{:mandate
                                 :prohibition
                                 :workflow
                                 :heuristic
                                 :principle
                                 :anti-pattern})
(def allowed-severities #{:hard :soft :advisory})
(def mandate-block-re #"(?s)<!--\s*llm-mandate\s*(.*?)\s*-->")

(defn- usage []
  (binding [*out* *err*]
    (println "Usage: .llm/template-only/instrument/check-cases.sh [--cases <path>] [--incident-index <path>] [--mandate-root <path>]")))

(defn- parse-args [args]
  (loop [m {:cases default-cases
            :incident-index default-incident-index
            :mandate-root default-mandate-root}
         xs args]
    (if (empty? xs)
      m
      (case (first xs)
        "--cases" (recur (assoc m :cases (second xs)) (nnext xs))
        "--incident-index" (recur (assoc m :incident-index (second xs)) (nnext xs))
        "--mandate-root" (recur (assoc m :mandate-root (second xs)) (nnext xs))
        ("-h" "--help") (assoc m :help true)
        (assoc m :unknown (first xs))))))

(defn- read-edn-file [path]
  (let [f (io/file path)]
    (when-not (.isFile f)
      (throw (ex-info (str "missing EDN file: " path) {:path path})))
    (edn/read-string (slurp f))))

(defn- diagnostic [level path message]
  {:level level
   :path path
   :message message})

(defn- error [path message]
  (diagnostic :error path message))

(defn- warning [path message]
  (diagnostic :warning path message))

(defn- non-empty-string? [x]
  (and (string? x) (not (str/blank? x))))

(defn- keyword-set? [x]
  (and (set? x) (every? keyword? x)))

(defn- optional-keyword-set-errors [path field value]
  (when (and (some? value) (not (keyword-set? value)))
    [(error path (str field " must be a set of keywords"))]))

(defn- normalize-trace-set [value]
  (if (keyword-set? value) value #{}))

(defn- relative-path [root file]
  (let [root-path (.toPath (.getCanonicalFile (io/file root)))
        file-path (.toPath (.getCanonicalFile file))]
    (str/replace (str (.relativize root-path file-path)) "\\" "/")))

(defn- ignored-mandate-file? [rel-path]
  (or (str/starts-with? rel-path ".git/")
      (str/starts-with? rel-path ".llm/work/")
      (str/starts-with? rel-path ".llm/evidence/")
      (str/starts-with? rel-path ".llm/template-only/instrument/runs/")))

(defn- strip-fenced-code-blocks [content]
  (let [lines (str/split-lines content)]
    (->> (reduce
          (fn [{:keys [in-code?] :as acc} line]
            (if (str/starts-with? line "```")
              (-> acc
                  (update :lines conj "")
                  (update :in-code? not))
              (update acc :lines conj (if in-code? "" line))))
          {:in-code? false :lines []}
          lines)
         :lines
         (str/join "\n"))))

(defn- markdown-files [root]
  (let [root-file (io/file root)]
    (if-not (.isDirectory root-file)
      []
      (->> (file-seq root-file)
           (filter #(.isFile %))
           (filter #(str/ends-with? (.getName %) ".md"))
           (map (fn [file]
                  {:file file
                   :rel-path (relative-path root-file file)}))
           (remove #(ignored-mandate-file? (:rel-path %)))))))

(defn- mandate-form-errors [path form]
  (if-not (map? form)
    [(error path "llm-mandate annotation must be an EDN map")]
    (vec
     (concat
      (when-not (keyword? (:id form))
        [(error path ":id must be a keyword")])
      (when-not (contains? allowed-instruction-kinds (:kind form))
        [(error path (str ":kind must be one of " (pr-str allowed-instruction-kinds)))])
      (when-not (contains? allowed-severities (:severity form))
        [(error path (str ":severity must be one of " (pr-str allowed-severities)))])
      (when-not (keyword-set? (:binding form))
        [(error path ":binding must be a set of keywords")])
      (when (and (contains? form :applies-to)
                 (not (keyword-set? (:applies-to form))))
        [(error path ":applies-to must be a set of keywords")])
      (when (and (contains? form :instrument/family)
                 (not (keyword? (:instrument/family form))))
        [(error path ":instrument/family must be a keyword")])))))

(defn- parse-mandate-file [{:keys [file rel-path]}]
  (let [content (strip-fenced-code-blocks (slurp file))]
    (reduce
     (fn [acc [idx [_ body]]]
       (let [path (str rel-path " llm-mandate[" idx "]")]
         (try
           (let [form (edn/read-string body)]
             (-> acc
                 (update :annotations conj {:source/file rel-path
                                            :source/index idx
                                            :form form})
                 (update :diagnostics into (mandate-form-errors path form))))
           (catch Exception e
             (update acc :diagnostics conj
                     (error path (str "invalid EDN: " (.getMessage e))))))))
     {:annotations [] :diagnostics []}
     (map-indexed vector (re-seq mandate-block-re content)))))

(defn- collect-mandates [root]
  (let [root-file (io/file root)]
    (if-not (.isDirectory root-file)
      {:mandate-ids #{}
       :diagnostics [(error "llm-mandate" (str "--mandate-root is not a directory: " root))]}
      (let [{:keys [annotations diagnostics]}
            (reduce
             (fn [acc file-item]
               (let [parsed (parse-mandate-file file-item)]
                 (-> acc
                     (update :annotations into (:annotations parsed))
                     (update :diagnostics into (:diagnostics parsed)))))
             {:annotations [] :diagnostics []}
             (markdown-files root-file))
            by-id (->> annotations
                      (filter #(keyword? (get-in % [:form :id])))
                      (group-by #(get-in % [:form :id])))
            duplicate-errors
            (for [[id items] by-id
                  :when (> (count items) 1)]
              (error "llm-mandate"
                     (str "duplicate :id " id " in "
                          (str/join ", " (map :source/file items)))))]
        {:mandate-ids (set (keys by-id))
         :diagnostics (vec (concat diagnostics duplicate-errors))}))))

(defn- case-seq [cases]
  (for [[family-id family] (:families cases)
        [case-id case-map] (:cases family)]
    {:family/id family-id
     :case/id case-id
     :case/map case-map}))

(defn- expectation-errors [path expectations]
  (cond
    (not (vector? expectations))
    [(error path ":observable-expectations must be a vector")]

    (empty? expectations)
    [(error path ":observable-expectations must not be empty")]

    :else
    (vec
     (keep-indexed
      (fn [idx expectation]
        (when-not (and (map? expectation) (keyword? (:expect expectation)))
          (error (str path " :observable-expectations[" idx "]")
                 "expectation must be a map with keyword :expect")))
      expectations))))

(defn- validate-case [known-families known-incidents known-mandates family-seed-incidents
                      seen-counts case-item]
  (let [family-id (:family/id case-item)
        case-id (:case/id case-item)
        case-map (:case/map case-item)
        path (str "case " case-id " in family " family-id)
        status (:status case-map)
        target-mode (:target/mode case-map)
        project-phase (:target/project-phase case-map)
        incidents-value (:trace/incidents case-map)
        mandates-value (:trace/mandates case-map)
        incidents (normalize-trace-set incidents-value)
        mandates (normalize-trace-set mandates-value)
        exploratory? (= :exploratory status)
        unknown-incidents (seq (remove known-incidents incidents))
        unknown-mandates (seq (remove known-mandates mandates))
        family-known? (contains? known-families family-id)
        family-seeds (get family-seed-incidents family-id #{})
        incidents-outside-family (seq (remove family-seeds incidents))]
    (vec
     (concat
      (when-not (map? case-map)
        [(error path "case body must be a map")])
      (when (> (get seen-counts case-id 0) 1)
        [(error path "case id is duplicated across families")])
      (when-not family-known?
        [(error path "family is not declared in incident-index.edn")])
      (when-not (contains? allowed-statuses status)
        [(error path (str ":status must be one of " (pr-str allowed-statuses)))])
      (when-not (contains? allowed-target-modes target-mode)
        [(error path ":target/mode must be :template or :project")])
      (when (and (= :project target-mode)
                 project-phase
                 (not (contains? allowed-project-phases project-phase)))
        [(error path ":target/project-phase must be :bootstrap or :development")])
      (when (and (= :template target-mode) project-phase)
        [(warning path ":target/project-phase is ignored for template targets")])
      (when-not (non-empty-string? (:prompt case-map))
        [(error path ":prompt must be a non-empty string")])
      (expectation-errors path (:observable-expectations case-map))
      (optional-keyword-set-errors path ":trace/incidents" incidents-value)
      (optional-keyword-set-errors path ":trace/mandates" mandates-value)
      (when-not (or exploratory? (seq incidents) (seq mandates))
        [(error path "non-exploratory case must trace to an incident or authored mandate")])
      (when unknown-incidents
        [(error path (str "unknown :trace/incidents " (pr-str (vec unknown-incidents))))])
      (when unknown-mandates
        [(error path (str "unknown :trace/mandates " (pr-str (vec unknown-mandates))))])
      (when incidents-outside-family
        [(error path (str ":trace/incidents are not in this family seed-incidents "
                          (pr-str (vec incidents-outside-family))))])
      (when (and exploratory? (seq incidents))
        [(warning path "exploratory case has incident traces; consider promoting it out of exploratory status")])))))

(defn validate [cases incident-index mandate-ids mandate-diagnostics]
  (let [case-items (vec (case-seq cases))
        seen-counts (frequencies (map :case/id case-items))
        known-families (set (keys (:families incident-index)))
        known-incidents (set (keys (:incidents incident-index)))
        family-seed-incidents (->> (:families incident-index)
                                   (map (fn [[family-id family]]
                                          [family-id (set (:seed-incidents family))]))
                                   (into {}))]
    (vec
     (concat
      (when-not (= 1 (:case/schema cases))
        [(error "cases.edn" ":case/schema must be 1")])
      (when-not (= 1 (:instrument/schema incident-index))
        [(error "incident-index.edn" ":instrument/schema must be 1")])
      (when-not (and (contains? incident-index :incidents)
                     (contains? incident-index :families))
        [(error "incident-index.edn" "incident index must contain :incidents and :families")])
      (when-not (map? (:families cases))
        [(error "cases.edn" ":families must be a map")])
      (when-not (seq case-items)
        [(error "cases.edn" "at least one case is required")])
      mandate-diagnostics
      (mapcat #(validate-case known-families
                              known-incidents
                              mandate-ids
                              family-seed-incidents
                              seen-counts
                              %)
              case-items)))))

(defn- print-diagnostic [{:keys [level path message]}]
  (println (str (str/upper-case (name level)) ": " path ": " message)))

(defn -main [& args]
  (let [{:keys [cases incident-index mandate-root help unknown]} (parse-args args)]
    (cond
      help
      (do (usage) (System/exit 0))

      unknown
      (do (usage)
          (binding [*out* *err*]
            (println "Unknown argument:" unknown))
          (System/exit 2))

      :else
      (try
        (let [mandates (collect-mandates mandate-root)
              diagnostics (validate (read-edn-file cases)
                                    (read-edn-file incident-index)
                                    (:mandate-ids mandates)
                                    (:diagnostics mandates))
              errors (filter #(= :error (:level %)) diagnostics)
              warnings (filter #(= :warning (:level %)) diagnostics)]
          (doseq [d warnings] (print-diagnostic d))
          (doseq [d errors] (print-diagnostic d))
          (if (seq errors)
            (do
              (println (str "check-instrument-cases: FAILED (" (count errors)
                            " errors, " (count warnings) " warnings)"))
              (System/exit 1))
            (do
              (println (if (seq warnings)
                         (str "check-instrument-cases: OK with " (count warnings) " WARN")
                         "check-instrument-cases: OK"))
              (System/exit 0))))
        (catch Exception e
          (binding [*out* *err*]
            (println "check-instrument-cases: FAILED")
            (println (.getMessage e)))
          (System/exit 1))))))
