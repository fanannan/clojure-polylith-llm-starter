(ns structural-evidence
  "Derive structural evidence views from local repository structure.

   This namespace is intentionally side-effect light. The derived packet is a
   generated review view, not a new authority source."
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.java.shell :as shell]
   [clojure.pprint :as pprint]
   [clojure.string :as str]))

(def schema-version "vnext.1")

(def default-out-dir ".llm/work")

(def evidence-tiers
  {:mechanical "re-runnable command evidence: tests, Malli, poly check, check-* pass"
   :linkage "machine-derived correspondence evidence: design-ir, brick-map, trace-index"
   :procedural "human/procedure evidence: review, accepted decision, archive absorption"})

(def evidence-requirements
  {:check-doc-references
   {:tier :mechanical
    :label "Markdown reference marker check"
    :command "./.llm/scripts/check-doc-references.sh --all"}
   :check-archive-staleness
   {:tier :mechanical
    :label "Maintainer archive staging check"
    :command "./.llm/scripts/check-archive-staleness.sh"}
   :check-mode-scope
   {:tier :mechanical
    :label "Template/project ownership boundary check"
    :command "./.llm/scripts/check-mode-scope.sh"}
   :script-single-run
   {:tier :mechanical
    :label "Changed script wrapper or implementation runs directly"}
   :check-workspace-integrity
   {:tier :mechanical
    :label "Workspace integrity aggregate check"
    :command "./.llm/scripts/check-workspace-integrity.sh"}
   :check-interface-contracts
   {:tier :mechanical
    :label "Public interface defn has Malli m/=> contract"
    :command "./.llm/scripts/check-interface-contracts.sh"}
   :check-trace-metadata
   {:tier :linkage
    :label "Trace metadata is consistent with DESIGN-derived IR"
    :command "./.llm/scripts/check-trace-metadata.sh"}
   :poly-check
   {:tier :mechanical
    :label "Polylith structural check"
    :command "clj -M:poly check"}
   :relevant-tests
   {:tier :mechanical
    :label "Relevant tests for touched public boundary / brick"}
   :check-design-ir
   {:tier :linkage
    :label "DESIGN-derived IR is synchronized"
    :command "./.llm/scripts/check-design-ir.sh"}
   :check-trace-index
   {:tier :linkage
    :label "Trace index is synchronized"
    :command "./.llm/scripts/check-trace-index.sh"}
   :dependency-resolution
   {:tier :mechanical
    :label "Affected deps.edn classpath resolves"}
   :check-deprecated-libs
   {:tier :mechanical
    :label "No deprecated libraries are adopted"
    :command "./.llm/scripts/check-deprecated-libs.sh"}
   :check-conflicting-libs
   {:tier :mechanical
    :label "No conflicting libraries are co-adopted"
    :command "./.llm/scripts/check-conflicting-libs.sh"}
   :check-brick-map
   {:tier :linkage
    :label "Brick Map generated index is synchronized"
    :command "./.llm/scripts/check-brick-map.sh"}
   :check-workspace-map
   {:tier :linkage
    :label "Project / Workspace generated indices are synchronized"
    :command "./.llm/scripts/check-workspace-map.sh"}
   :source-regeneration-check
   {:tier :linkage
    :label "Generated output matches its source documents / metadata"}})

(def archetype-evidence
  {:template-governance-change [:check-doc-references :check-mode-scope]
   :script-change [:script-single-run :check-workspace-integrity]
   :maintainer-archive-change [:check-archive-staleness :check-doc-references]
   :error-adr-not-in-template [:check-mode-scope]
   :interface-change [:check-interface-contracts :check-trace-metadata :poly-check :relevant-tests]
   :internal-refactor [:poly-check :relevant-tests]
   :base-entrypoint-change [:poly-check :relevant-tests :check-workspace-map]
   :spec-change [:check-design-ir :check-trace-index :check-trace-metadata]
   :dependency-change [:dependency-resolution :check-deprecated-libs :check-conflicting-libs]
   :brick-ownership-change [:check-brick-map :poly-check]
   :project-ownership-change [:check-workspace-map :poly-check]
   :generated-index-change [:source-regeneration-check]
   :docs-only [:check-doc-references]
   :uncategorized [:check-workspace-integrity]})

(def save-required-archetypes
  #{:interface-change
    :spec-change
    :dependency-change
    :template-governance-change
    :brick-ownership-change
    :project-ownership-change})

(defn- now-ms []
  (System/currentTimeMillis))

(defn- elapsed-ms [started]
  (- (now-ms) started))

(defn- file? [path]
  (.isFile (io/file path)))

(defn- ensure-dir! [path]
  (.mkdirs (io/file path)))

(defn- read-edn-if-exists [path]
  (when (file? path)
    (try
      (edn/read-string (slurp path))
      (catch Throwable _ nil))))

(defn- repo-context []
  (or (read-edn-if-exists ".llm/repo-context.edn") {}))

(defn- repo-kind []
  (or (:repo-kind (repo-context)) :project))

(defn- adoption-mode []
  (or (:adoption-mode (repo-context))
      (if (= :template (repo-kind)) :complete :retrofit)))

(defn- shell-lines [& args]
  (let [{:keys [exit out]} (apply shell/sh args)]
    (if (zero? exit)
      (->> (str/split-lines out)
           (remove str/blank?)
           vec)
      [])))

(defn- git-rev []
  (first (shell-lines "git" "rev-parse" "HEAD")))

(defn- default-branch []
  (or (first (shell-lines "git" "symbolic-ref" "--quiet" "--short" "refs/remotes/origin/HEAD"))
      (first (shell-lines "git" "rev-parse" "--abbrev-ref" "HEAD"))
      "HEAD"))

(defn- parse-keyword [s]
  (when (and s (not (str/blank? s)))
    (keyword (str/replace s #"^:" ""))))

(defn- parse-bool [s]
  (contains? #{"1" "true" "yes" "on"} (str/lower-case (str s))))

(defn- parse-args [args]
  (loop [m {:mode :strict}
         [x & xs] args]
    (case x
      nil m
      "--base" (recur (assoc m :base (first xs)) (next xs))
      "--head" (recur (assoc m :head (first xs)) (next xs))
      "--out" (recur (assoc m :out (first xs)) (next xs))
      "--out-dir" (recur (assoc m :out-dir (first xs)) (next xs))
      "--task-id" (recur (assoc m :task/id (first xs)) (next xs))
      "--status" (recur (assoc m :status (parse-keyword (first xs))) (next xs))
      "--strict" (recur (assoc m :mode :strict) xs)
      "--degraded" (recur (assoc m :mode :degraded) xs)
      "--profile" (recur (assoc m :profile true) xs)
      "--format" (recur (assoc m :format (keyword (first xs))) (next xs))
      "--changed-file" (recur (update m :changed-files (fnil conj []) (first xs)) (next xs))
      (recur (update m :extra-args (fnil conj []) x) xs))))

(defn- changed-files [opts]
  (if (seq (:changed-files opts))
    (->> (:changed-files opts) distinct sort vec)
    (let [base (:base opts)
          head (or (:head opts) "HEAD")]
      (if base
        (->> (shell-lines "git" "diff" "--name-only" (str base "..." head))
             distinct
             sort
             vec)
        (->> (concat (shell-lines "git" "diff" "--name-only")
                     (shell-lines "git" "diff" "--cached" "--name-only"))
             distinct
             sort
             vec)))))

(defn- component-name [path]
  (second (re-matches #"components/([^/]+)/.*" path)))

(defn- base-name [path]
  (second (re-matches #"bases/([^/]+)/.*" path)))

(defn- project-name [path]
  (second (re-matches #"projects/([^/]+)/.*" path)))

(defn- read-ns-name [text]
  (second (re-find #"\(ns\s+([^\s\)]+)" text)))

(defn- defn-names [text]
  (->> (re-seq #"\(defn(?:-)?\s+([^\s\)]+)" text)
       (map second)
       vec))

(defn- public-boundaries [path]
  (if (and (str/ends-with? path "interface.clj") (file? path))
    (let [text (slurp path)
          ns-name (read-ns-name text)]
      (->> (defn-names text)
           (remove #(str/starts-with? % "-"))
           (map #(if ns-name (str ns-name "/" %) %))
           sort
           vec))
    []))

(defn- template-rule [path]
  (cond
    (or (= "CLAUDE.md" path)
        (= ".gitignore" path)
        (= ".llm/repo-context.edn" path)
        (str/starts-with? path ".llm/guide/"))
    {:rule :template-governance :archetype :template-governance-change :plane :authority}

    (str/starts-with? path ".llm/scripts/")
    {:rule :script-change :archetype :script-change :plane :verification}

    (str/starts-with? path ".llm/memory/archive/")
    {:rule :maintainer-archive :archetype :maintainer-archive-change :plane :authority}

    (and (str/starts-with? path ".llm/memory/adr/")
         (not (contains? #{"README.md" "template.md"} (.getName (io/file path)))))
    {:rule :template-adr-forbidden :archetype :error-adr-not-in-template :plane :authority :severity :error}

    (str/starts-with? path ".llm/data/")
    {:rule :generated-index :archetype :generated-index-change :plane :index}

    (or (= "README.md" path)
        (str/starts-with? path ".llm/templates/")
        (str/ends-with? path ".md"))
    {:rule :docs-only :archetype :docs-only :plane :authority}

    :else
    {:rule :uncategorized :archetype :uncategorized :plane :unknown}))

(defn- project-rule [path]
  (cond
    (re-matches #"components/[^/]+/src/.*/interface\.clj" path)
    {:rule :component-interface :archetype :interface-change :plane :structure}

    (re-matches #"components/[^/]+/src/.*\.clj[cs]?" path)
    {:rule :component-internal :archetype :internal-refactor :plane :structure}

    (re-matches #"components/[^/]+/brick\.edn" path)
    {:rule :component-brick-intent :archetype :brick-ownership-change :plane :structure}

    (re-matches #"bases/[^/]+/src/.*\.clj[cs]?" path)
    {:rule :base-entrypoint :archetype :base-entrypoint-change :plane :structure}

    (re-matches #"bases/[^/]+/brick\.edn" path)
    {:rule :base-brick-intent :archetype :brick-ownership-change :plane :structure}

    (re-matches #"projects/[^/]+/project\.edn" path)
    {:rule :project-intent :archetype :project-ownership-change :plane :structure}

    (or (= "DESIGN.md" path)
        (str/starts-with? path ".llm/memory/"))
    {:rule :spec-memory :archetype :spec-change :plane :authority}

    (or (= "deps.edn" path)
        (str/ends-with? path "/deps.edn"))
    {:rule :dependency-change :archetype :dependency-change :plane :structure}

    (str/starts-with? path ".llm/data/")
    {:rule :generated-index :archetype :generated-index-change :plane :index}

    (or (str/starts-with? path "docs/")
        (str/ends-with? path ".md"))
    {:rule :docs-only :archetype :docs-only :plane :authority}

    :else
    {:rule :uncategorized :archetype :uncategorized :plane :unknown}))

(defn- classify-path [kind path]
  (let [rule (if (= :template kind)
               (template-rule path)
               (project-rule path))]
    (merge {:path path
            :component (component-name path)
            :base (base-name path)
            :project (project-name path)
            :public-boundaries (public-boundaries path)}
           rule)))

(defn- requirement-ids-for-bricks [bricks]
  ;; Best-effort extraction. Different generated maps may evolve; missing maps
  ;; simply mean no linkage is derived at this stage.
  (let [idx (read-edn-if-exists ".llm/data/brick-map.edn")
        names (set (map name bricks))]
    (->> (tree-seq coll? seq idx)
         (filter map?)
         (filter (fn [m]
                   (let [n (or (:brick/name m) (:name m))]
                     (and n (contains? names (name n))))))
         (mapcat #(or (:brick/requirements %) (:requirements %) []))
         distinct
         sort
         vec)))

(defn- evidence-set [archetypes]
  (->> archetypes
       (mapcat #(get archetype-evidence % []))
       distinct
       (map (fn [id] (assoc (get evidence-requirements id {:tier :procedural :label (name id)})
                            :id id)))
       vec))

(defn- save-policy [archetypes path-count]
  (cond
    (some save-required-archetypes archetypes)
    :required

    (and (= #{:internal-refactor} (set archetypes))
         (< path-count 5))
    :optional

    (every? #{:docs-only :generated-index-change} archetypes)
    :not-required

    :else :optional))

(defn- failure-mode [opts entries]
  (let [errors (filter #(= :error (:severity %)) entries)
        unknown (filter #(= :uncategorized (:archetype %)) entries)
        generated-stale? false]
    (cond
      (seq errors) :cannot-derive
      (and (= :strict (:mode opts)) (seq unknown)) :partial-derive
      generated-stale? :degraded-derive
      :else :ok)))

(defn derive-packet [opts]
  (let [started (now-ms)
        kind (repo-kind)
        files (changed-files opts)
        entries (mapv #(classify-path kind %) files)
        bricks (->> entries
                    (mapcat (fn [{:keys [component base]}] [component base]))
                    (remove nil?)
                    (map keyword)
                    distinct
                    sort
                    vec)
        projects (->> entries (keep :project) (map keyword) distinct sort vec)
        public-boundaries (->> entries (mapcat :public-boundaries) distinct sort vec)
        archetypes (->> entries (map :archetype) distinct sort vec)
        requirements (requirement-ids-for-bricks bricks)
        required-evidence (evidence-set archetypes)
        failure (failure-mode opts entries)]
    {:schema/version schema-version
     :kind :review-fatigue-packet
     :task/id (or (:task/id opts)
                  (str (.toString (java.time.LocalDate/now))
                       "-structural-evidence"))
     :status (or (:status opts) :active)
     :repo-kind kind
     :adoption-mode (adoption-mode)
     :mode (:mode opts)
     :baseline (cond-> {:type (if (:base opts) :git-diff :working-tree)
                        :git-rev (git-rev)
                        :default-branch (default-branch)}
                 (:base opts) (assoc :base (:base opts))
                 (:head opts) (assoc :head (:head opts)))
     :derivation {:status failure
                  :profile-ms {:total (elapsed-ms started)}
                  :rules-version schema-version}
     :actual-scope {:paths files
                    :bricks bricks
                    :projects projects
                    :public-boundaries public-boundaries
                    :requirements requirements
                    :archetypes archetypes}
     :save-policy (save-policy archetypes (count files))
     :required-evidence required-evidence
     :evidence []
     :llm-declared {:semantic-impact-not-derived :none
                    :unknowns-not-captured-by-derivation :none
                    :cross-brick-effects-not-in-trace-index :none
                    :override :none
                    :remaining-fatigue :none}
     :human-attention {:must-review
                       (->> entries
                            (filter #(contains? save-required-archetypes (:archetype %)))
                            (map :path)
                            distinct
                            sort
                            vec)
                       :safe-to-skim
                       (->> entries
                            (filter #(contains? #{:generated-index-change :docs-only} (:archetype %)))
                            (map :path)
                            distinct
                            sort
                            vec)}
     :derivation/audit entries
     :evidence-tiers evidence-tiers}))

(defn- print-edn [x]
  (pprint/pprint x))

(defn- write-edn! [path data]
  (ensure-dir! (.getParent (io/file path)))
  (spit path (with-out-str (pprint/pprint data))))

(defn- bullet-list [items]
  (if (seq items)
    (str/join "\n" (map #(str "- `" % "`") items))
    "- none"))

(defn- evidence-list [items]
  (if (seq items)
    (str/join
     "\n"
     (for [{:keys [id tier label command]} items]
       (str "- `" id "` [" (name tier) "]: " label
            (when command (str "\n  - command: `" command "`")))))
    "- none"))

(defn markdown [packet]
  (str "# Review Fatigue Packet\n\n"
       "- Task: `" (:task/id packet) "`\n"
       "- Status: `" (name (:status packet)) "`\n"
       "- Repo kind: `" (name (:repo-kind packet)) "`\n"
       "- Derivation: `" (name (get-in packet [:derivation :status])) "`\n"
       "- Save policy: `" (name (:save-policy packet)) "`\n\n"
       "## Actual Scope\n\n"
       "### Paths\n"
       (bullet-list (get-in packet [:actual-scope :paths]))
       "\n\n### Bricks\n"
       (bullet-list (map name (get-in packet [:actual-scope :bricks])))
       "\n\n### Public Boundaries\n"
       (bullet-list (get-in packet [:actual-scope :public-boundaries]))
       "\n\n### Archetypes\n"
       (bullet-list (map name (get-in packet [:actual-scope :archetypes])))
       "\n\n## Must Review\n\n"
       (bullet-list (get-in packet [:human-attention :must-review]))
       "\n\n## Safe To Skim\n\n"
       (bullet-list (get-in packet [:human-attention :safe-to-skim]))
       "\n\n## Required Evidence\n\n"
       (evidence-list (:required-evidence packet))
       "\n\n## Semantic Impact Not Derived By Structure\n\n"
       "- none\n\n"
       "## Unknowns Not Captured By Derivation\n\n"
       "- none\n\n"
       "## Cross-Brick Effects Not In Trace Index\n\n"
       "- none\n\n"
       "## Remaining Fatigue\n\n"
       "- none\n"))

(defn- write-markdown! [path packet]
  (ensure-dir! (.getParent (io/file path)))
  (spit path (markdown packet)))

(defn run-derive [opts]
  (let [packet (derive-packet opts)]
    (if-let [out (:out opts)]
      (do
        (write-edn! out packet)
        (println out))
      (print-edn packet))))

(defn propose [opts]
  (let [packet (derive-packet opts)
        out-dir (or (:out-dir opts) default-out-dir)
        task-id (:task/id packet)
        edn-path (str out-dir "/" task-id ".edn")
        md-path (str out-dir "/" task-id ".md")]
    (write-edn! edn-path packet)
    (write-markdown! md-path packet)
    (println "Review Fatigue Packet generated:")
    (println " " edn-path)
    (println " " md-path)))

(defn inspect [opts]
  (let [packet (if-let [path (:out opts)]
                 (edn/read-string (slurp path))
                 (derive-packet opts))]
    (println "Structural Evidence Derivation")
    (println "Task:" (:task/id packet))
    (println "Status:" (get-in packet [:derivation :status]))
    (println)
    (doseq [{:keys [path rule archetype plane component base project public-boundaries severity]} (:derivation/audit packet)]
      (println "Touched path:" path)
      (println "  Matched rule:" rule)
      (println "  Plane:" plane)
      (println "  Archetype:" archetype)
      (when severity (println "  Severity:" severity))
      (when component (println "  Affected component:" component))
      (when base (println "  Affected base:" base))
      (when project (println "  Affected project:" project))
      (when (seq public-boundaries)
        (println "  Public boundaries:" (str/join ", " public-boundaries)))
      (println))
    (println "Required evidence:")
    (if (seq (:required-evidence packet))
      (doseq [{:keys [id tier label]} (:required-evidence packet)]
        (println " -" id "[" (name tier) "]" label))
      (println " - none"))))

(defn- assert! [label pred]
  (if pred
    (println "OK:" label)
    (throw (ex-info (str "Self-test failed: " label) {:label label}))))

(defn self-test [_]
  (let [template-adr (with-redefs [repo-context (constantly {:repo-kind :template :adoption-mode :complete})
                                   git-rev (constantly "fixture-rev")
                                   default-branch (constantly "main")]
                       (derive-packet {:changed-files [".llm/memory/adr/0001-bad.md"]}))
        project-interface (with-redefs [repo-context (constantly {:repo-kind :project :adoption-mode :complete})
                                        git-rev (constantly "fixture-rev")
                                        default-branch (constantly "main")]
                            (derive-packet {:changed-files ["components/foo/src/acme/foo/interface.clj"]}))
        project-design (with-redefs [repo-context (constantly {:repo-kind :project :adoption-mode :complete})
                                     git-rev (constantly "fixture-rev")
                                     default-branch (constantly "main")]
                         (derive-packet {:changed-files ["DESIGN.md"]}))]
    (assert! "template ADR file is forbidden"
             (= :cannot-derive (get-in template-adr [:derivation :status])))
    (assert! "project interface change is classified"
             (contains? (set (get-in project-interface [:actual-scope :archetypes]))
                        :interface-change))
    (assert! "project interface change derives component brick"
             (contains? (set (get-in project-interface [:actual-scope :bricks]))
                        :foo))
    (assert! "DESIGN.md change is spec-change"
             (contains? (set (get-in project-design [:actual-scope :archetypes]))
                        :spec-change))
    (println "structural-evidence self-test: OK")))

(defn -main [& args]
  (let [[cmd & more] args
        opts (parse-args more)]
    (case cmd
      "derive" (run-derive opts)
      "propose" (propose opts)
      "inspect" (inspect opts)
      "self-test" (self-test opts)
      (do
        (binding [*out* *err*]
          (println "Usage:")
          (println "  structural-evidence derive [--base BASE] [--head HEAD] [--out PATH] [--strict|--degraded] [--profile]")
          (println "  structural-evidence propose [--task-id ID] [--out-dir DIR] [--strict|--degraded]")
          (println "  structural-evidence inspect [--base BASE] [--head HEAD] [--out PATH]")
          (println "  structural-evidence self-test"))
        (System/exit 2)))))
