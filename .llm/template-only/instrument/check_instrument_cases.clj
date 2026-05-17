(ns check-instrument-cases
  "Validate Instruction-Following Instrument case catalog invariants.

   This checker protects the instrument from becoming a free-floating synthetic
   suite. Non-exploratory cases must trace to an observed incident or an authored
   mandate. Incident traces must be known to incident-index.edn, and mandate
   traces must point at authored [mandate: ...] annotations in CLAUDE.md or
   .llm/guide/*.md.

   It also runs the reverse mandate-binding audit (plan §10.4): each mandate in
   .llm/data/mandates.edn should name an enforcer, and every gate script run by
   check-workspace-integrity.sh must be a mandate enforced-by or a backed
   pure-infrastructure classification."
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(def default-cases ".llm/template-only/instrument/cases.edn")
(def default-incident-index ".llm/template-only/instrument/incident-index.edn")
(def default-mandate-root ".")
(def default-mandates-index ".llm/data/mandates.edn")
(def default-integrity-script ".llm/scripts/check-workspace-integrity.sh")
(def gate-script-re #"check-[a-z0-9-]+\.sh")

;; Gate scripts run by check-workspace-integrity.sh that enforce no corpus
;; mandate. Each carries backing (plan §5.7): a reason and the maintainer
;; archive entry where the classification was reviewed. A classification
;; without backing, or one for a script the gate no longer runs, is flagged.
(def pure-infrastructure-gate-scripts
  [{:script "check-workspace-integrity.sh"
    :reason "umbrella aggregator; enforces mandates only through the sub-checks it runs"
    :reviewed-in :md-2026-05-018}
   {:script "check-structural-evidence-self-test.sh"
    :reason "fixture self-test of Structural Evidence derivation rules; verifies tooling, enforces no corpus mandate"
    :reviewed-in :md-2026-05-018}
   {:script "check-adoption-mode-scenarios.sh"
    :reason "fixture self-test of :adoption-mode staged behavior; verifies tooling, enforces no corpus mandate"
    :reviewed-in :md-2026-05-018}])

(def allowed-statuses #{:pilot :planned :exploratory :disabled})
(def allowed-target-modes #{:template :project})
(def allowed-project-phases #{:bootstrap :development})
;; Mandate annotation format (plan §5.5 / §5.6): a visible one-line directive
;; `[mandate: M-NNNN/hint type:<type> tier:<tier>]`. The checker validates shape
;; only; the rule meaning lives in the nearby prose.
(def allowed-instruction-types #{:workflow :prohibition :invariant :derived-artifact})
(def allowed-mandate-tiers #{:kernel :extended})
(def mandate-id-re #"M-\d{4}")
(def mandate-hint-re #"[a-z0-9-]+")
(def mandate-annotation-re #"(?m)^\[mandate:\s*(.+?)\]\s*$")

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

(defn- string-set? [x]
  (and (set? x) (every? string? x)))

(defn- optional-string-set-errors [path field value]
  (when (and (some? value) (not (string-set? value)))
    [(error path (str field " must be a set of strings"))]))

(defn- normalize-trace-set [pred value]
  (if (pred value) value #{}))

(defn- relative-path [root file]
  (let [root-path (.toPath (.getCanonicalFile (io/file root)))
        file-path (.toPath (.getCanonicalFile file))]
    (str/replace (str (.relativize root-path file-path)) "\\" "/")))

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

(defn- corpus-mandate-files [root]
  ;; Mandate annotations are authored only in CLAUDE.md and .llm/guide/*.md
  ;; (plan §5.8). AGENTS.md, the maintainer archive, and .llm/memory are out of
  ;; scan range so that quoted examples never join as real mandates.
  (let [root-file (io/file root)
        claude (io/file root-file "CLAUDE.md")
        guide-dir (io/file root-file ".llm/guide")
        guide-files (when (.isDirectory guide-dir)
                      (->> (.listFiles guide-dir)
                           (filter #(.isFile %))
                           (filter #(str/ends-with? (.getName %) ".md"))))]
    (->> (cons claude guide-files)
         (filter (fn [f] (and f (.isFile f))))
         (map (fn [file]
                {:file file
                 :rel-path (relative-path root-file file)})))))

(defn- parse-mandate-annotation [body]
  ;; body: "M-0001/session-start-briefing-first type:workflow tier:kernel"
  (let [tokens (str/split (str/trim body) #"\s+")
        id-token (first tokens)
        [id hint] (when id-token (str/split id-token #"/" 2))
        attrs (into {}
                    (keep (fn [token]
                            (let [[k v] (str/split token #":" 2)]
                              (when (and k v (seq k) (seq v)) [k v])))
                          (rest tokens)))]
    {:id id
     :hint hint
     :type (some-> (get attrs "type") keyword)
     :tier (some-> (get attrs "tier") keyword)}))

(defn- mandate-annotation-errors [path {:keys [id hint type tier]}]
  (vec
   (concat
    (when-not (and (string? id) (re-matches mandate-id-re id))
      [(error path "mandate id must match M-NNNN")])
    (when-not (and (string? hint) (re-matches mandate-hint-re hint))
      [(error path "mandate hint after the slash must match [a-z0-9-]+")])
    (when-not (contains? allowed-instruction-types type)
      [(error path (str "type: must be one of " (pr-str allowed-instruction-types)))])
    (when-not (contains? allowed-mandate-tiers tier)
      [(error path (str "tier: must be one of " (pr-str allowed-mandate-tiers)))]))))

(defn- parse-mandate-file [{:keys [file rel-path]}]
  (let [content (strip-fenced-code-blocks (slurp file))]
    (reduce
     (fn [acc [idx [_ body]]]
       (let [path (str rel-path " [mandate:" idx "]")
             annotation (parse-mandate-annotation body)]
         (-> acc
             (update :annotations conj (assoc annotation
                                              :source/file rel-path
                                              :source/index idx))
             (update :diagnostics into (mandate-annotation-errors path annotation)))))
     {:annotations [] :diagnostics []}
     (map-indexed vector (re-seq mandate-annotation-re content)))))

(defn- collect-mandates [root]
  (let [{:keys [annotations diagnostics]}
        (reduce
         (fn [acc file-item]
           (let [parsed (parse-mandate-file file-item)]
             (-> acc
                 (update :annotations into (:annotations parsed))
                 (update :diagnostics into (:diagnostics parsed)))))
         {:annotations [] :diagnostics []}
         (corpus-mandate-files root))
        by-id (->> annotations
                   (filter #(and (string? (:id %))
                                 (re-matches mandate-id-re (:id %))))
                   (group-by :id))
        duplicate-errors
        (for [[id items] by-id
              :when (> (count items) 1)]
          (error "mandate"
                 (str "duplicate mandate id " id " in "
                      (str/join ", " (map :source/file items)))))]
    {:mandate-ids (set (keys by-id))
     :diagnostics (vec (concat diagnostics duplicate-errors))}))

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
        incidents (normalize-trace-set keyword-set? incidents-value)
        mandates (normalize-trace-set string-set? mandates-value)
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
      (optional-string-set-errors path ":trace/mandates" mandates-value)
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

(defn- script-basename [path]
  (last (str/split path #"/")))

(defn- mandate-binding-diagnostics
  "Reverse-direction mandate-binding drift audit (plan §10.4 b/c). (b) warns on a
   mandate stated in prose but named by no enforcer. (c) errors when a gate
   script run by check-workspace-integrity.sh is neither a mandate enforced-by
   nor a backed pure-infrastructure classification.

   The audit applies to a full template repo. When the generated mandate index
   or the integrity script is absent (e.g. a synthetic --mandate-root fixture),
   it does not apply and yields no diagnostics."
  [root]
  (let [index-file (io/file root default-mandates-index)
        integrity-file (io/file root default-integrity-script)]
    (cond
      (not (.isFile index-file)) []
      (not (.isFile integrity-file)) []

      :else
      (let [mandates (:mandates (edn/read-string (slurp index-file)))
            prayer (for [[id m] (sort mandates)
                         :when (empty? (:enforced-by m))]
                     (warning (str "mandate " id)
                              "stated in prose but named by no enforcer (祈りの規約)"))
            bound (set (for [[_ m] mandates
                             e (:enforced-by m)
                             :when (str/ends-with? (str e) ".sh")]
                         (script-basename e)))
            classified (into {} (map (juxt :script identity)) pure-infrastructure-gate-scripts)
            gate (set (re-seq gate-script-re (slurp integrity-file)))
            unbound (for [g (sort gate)
                          :when (not (bound g))
                          :when (not (contains? classified g))]
                      (error "mandate-binding"
                             (str "gate script " g " is run by check-workspace-integrity.sh"
                                  " but is neither a mandate enforced-by nor a backed"
                                  " pure-infrastructure classification")))
            unbacked (for [{:keys [script reason reviewed-in]} pure-infrastructure-gate-scripts
                           :when (or (str/blank? (str reason)) (nil? reviewed-in))]
                       (warning "mandate-binding"
                                (str "pure-infrastructure classification of " script
                                     " lacks backing (:reason / :reviewed-in)")))
            stale (for [{:keys [script]} pure-infrastructure-gate-scripts
                        :when (not (contains? gate script))]
                    (warning "mandate-binding"
                             (str "pure-infrastructure classification of " script
                                  " is stale: not run by check-workspace-integrity.sh")))]
        (vec (concat prayer unbound unbacked stale))))))

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
              diagnostics (concat (validate (read-edn-file cases)
                                            (read-edn-file incident-index)
                                            (:mandate-ids mandates)
                                            (:diagnostics mandates))
                                  (mandate-binding-diagnostics mandate-root))
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
