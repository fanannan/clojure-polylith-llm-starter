(ns check-instrument-cases
  "Validate Instruction-Following Instrument case catalog invariants.

   This checker protects the instrument from becoming a free-floating synthetic
   suite. Non-exploratory cases must trace to an observed incident or an authored
   mandate, and incident traces must be known to incident-index.edn."
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(def default-cases ".llm/template-only/instrument/cases.edn")
(def default-incident-index ".llm/template-only/instrument/incident-index.edn")

(def allowed-statuses #{:pilot :planned :exploratory :disabled})
(def allowed-target-modes #{:template :project})
(def allowed-project-phases #{:bootstrap :development})

(defn- usage []
  (binding [*out* *err*]
    (println "Usage: .llm/template-only/instrument/check-cases.sh [--cases <path>] [--incident-index <path>]")))

(defn- parse-args [args]
  (loop [m {:cases default-cases
            :incident-index default-incident-index}
         xs args]
    (if (empty? xs)
      m
      (case (first xs)
        "--cases" (recur (assoc m :cases (second xs)) (nnext xs))
        "--incident-index" (recur (assoc m :incident-index (second xs)) (nnext xs))
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

(defn- validate-case [incident-index known-families known-incidents family-seed-incidents
                      seen-counts case-item]
  (let [family-id (:family/id case-item)
        case-id (:case/id case-item)
        case-map (:case/map case-item)
        path (str "case " case-id " in family " family-id)
        status (:status case-map)
        target-mode (:target/mode case-map)
        project-phase (:target/project-phase case-map)
        incidents (set (:trace/incidents case-map))
        mandates (set (:trace/mandates case-map))
        exploratory? (= :exploratory status)
        unknown-incidents (seq (remove known-incidents incidents))
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
      (when-not (or exploratory? (seq incidents) (seq mandates))
        [(error path "non-exploratory case must trace to an incident or authored mandate")])
      (when unknown-incidents
        [(error path (str "unknown :trace/incidents " (pr-str (vec unknown-incidents))))])
      (when incidents-outside-family
        [(error path (str ":trace/incidents are not in this family seed-incidents "
                          (pr-str (vec incidents-outside-family))))])
      (when (and exploratory? (seq incidents))
        [(warning path "exploratory case has incident traces; consider promoting it out of exploratory status")])
      (when-not (or (contains? incident-index :incidents)
                    (contains? incident-index :families))
        [(error "incident-index.edn" "incident index must contain :incidents and :families")])))))

(defn validate [cases incident-index]
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
      (when-not (map? (:families cases))
        [(error "cases.edn" ":families must be a map")])
      (when-not (seq case-items)
        [(error "cases.edn" "at least one case is required")])
      (mapcat #(validate-case incident-index
                              known-families
                              known-incidents
                              family-seed-incidents
                              seen-counts
                              %)
              case-items)))))

(defn- print-diagnostic [{:keys [level path message]}]
  (println (str (str/upper-case (name level)) ": " path ": " message)))

(defn -main [& args]
  (let [{:keys [cases incident-index help unknown]} (parse-args args)]
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
        (let [diagnostics (validate (read-edn-file cases)
                                    (read-edn-file incident-index))
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
