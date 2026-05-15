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

(def schema-version "structural-evidence.1")

(def default-out-dir ".llm/work")

(def residual-fields
  [:semantic-impact-not-derived
   :unknowns-not-captured-by-derivation
   :cross-brick-effects-not-in-trace-index
   :override
   :remaining-fatigue])

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
   :no-format-drift
   {:tier :mechanical
    :label "No formatting drift"
    :command "clj -M:format check"}
   :no-new-lint
   {:tier :mechanical
    :label "No new clj-kondo lint findings"
    :command "clj -M:lint"}
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
   :check-vulnerabilities
   {:tier :mechanical
    :label "No known dependency vulnerabilities"
    :command "./.llm/scripts/check-vulnerabilities.sh"}
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
   :script-change [:script-single-run :check-workspace-integrity :no-format-drift :no-new-lint]
   :maintainer-archive-change [:check-archive-staleness :check-doc-references]
   :error-adr-not-in-template [:check-mode-scope]
   :interface-change [:check-interface-contracts :check-trace-metadata :poly-check :relevant-tests :no-format-drift :no-new-lint]
   :internal-refactor [:poly-check :relevant-tests :no-format-drift :no-new-lint]
   :base-entrypoint-change [:poly-check :relevant-tests :check-workspace-map :no-format-drift :no-new-lint]
   :spec-change [:check-design-ir :check-trace-index :check-trace-metadata]
   :dependency-change [:dependency-resolution :check-deprecated-libs :check-conflicting-libs :check-vulnerabilities]
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

(defn- scalars [x]
  (->> (tree-seq coll? seq x)
       (remove coll?)
       (keep (fn [v]
               (cond
                 (keyword? v) (name v)
                 (symbol? v) (str v)
                 (string? v) v
                 (number? v) (str v)
                 :else nil)))))

(defn- as-coll [x]
  (cond
    (nil? x) []
    (and (coll? x) (not (map? x))) x
    :else [x]))

(defn- normalize-token [x]
  (some-> x str str/lower-case))

(defn- intersects-tokens? [x tokens]
  (let [token-set (set (map normalize-token tokens))]
    (boolean
     (some token-set (map normalize-token (scalars x))))))

(defn- maps-matching-tokens [x tokens]
  (when (seq tokens)
    (->> (tree-seq coll? seq x)
         (filter map?)
         (filter #(intersects-tokens? % tokens)))))

(defn- values-for-keys [maps keys]
  (->> maps
       (mapcat (fn [m]
                 (mapcat #(as-coll (get m %)) keys)))
       (mapcat scalars)
       distinct
       sort
       vec))

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

(defn- tail-lines [s n]
  (->> (str/split-lines (str s))
       (take-last n)
       (str/join "\n")))

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

(defn- split-scope [s]
  (->> (str/split (str s) #"[,\s]+")
       (map str/trim)
       (remove str/blank?)
       vec))

(defn- parse-args [args]
  (loop [m {:mode :strict}
         [x & xs] args]
    (case x
      nil m
      "--base" (recur (assoc m :base (first xs)) (next xs))
      "--head" (recur (assoc m :head (first xs)) (next xs))
      "--out" (recur (assoc m :out (first xs)) (next xs))
      "--from" (recur (assoc m :from (first xs)) (next xs))
      "--packet" (recur (assoc m :packet (first xs)) (next xs))
      "--intent" (recur (assoc m :intent (first xs)) (next xs))
      "--task" (recur (assoc m :task/id (first xs)) (next xs))
      "--scope" (recur (update m :scope-terms (fnil into []) (split-scope (first xs))) (next xs))
      "--all-none" (recur (assoc m :all-none true) xs)
      "--semantic-impact" (recur (assoc-in m [:declare :semantic-impact-not-derived] (first xs)) (next xs))
      "--unknowns" (recur (assoc-in m [:declare :unknowns-not-captured-by-derivation] (first xs)) (next xs))
      "--cross-brick-effects" (recur (assoc-in m [:declare :cross-brick-effects-not-in-trace-index] (first xs)) (next xs))
      "--override" (recur (assoc-in m [:declare :override] (first xs)) (next xs))
      "--remaining-fatigue" (recur (assoc-in m [:declare :remaining-fatigue] (first xs)) (next xs))
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
                     (shell-lines "git" "diff" "--cached" "--name-only")
                     (shell-lines "git" "ls-files" "--others" "--exclude-standard"))
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

(defn- brick-context-for-bricks [bricks]
  (let [idx (read-edn-if-exists ".llm/data/brick-map.edn")
        maps (maps-matching-tokens idx (map name bricks))]
    {:available (boolean idx)
     :provides (values-for-keys maps [:brick/provides :provides :capabilities])
     :groups (values-for-keys maps [:brick/group :group])
     :not-for (values-for-keys maps [:brick/not-for :not-for])}))

(defn- trace-context-for-boundaries [public-boundaries]
  (let [idx (read-edn-if-exists ".llm/data/trace-index.edn")
        maps (maps-matching-tokens idx public-boundaries)]
    {:available (boolean idx)
     :matched-boundaries (vec (sort public-boundaries))
     :requirements (values-for-keys maps [:requirements :requirement :requirement-id :trace/requirements])
     :tests (values-for-keys maps [:tests :test :test-var :test-vars :implementation-tests])
     :test-obligations (values-for-keys maps [:test-obligations
                                              :test-obligation
                                              :trace/test-obligations])
     :matched-records (count maps)}))

(defn- workspace-projects-for-bricks [bricks]
  (let [idx (read-edn-if-exists ".llm/data/workspace-map.edn")
        maps (maps-matching-tokens idx (map name bricks))]
    (values-for-keys maps [:project/name :project :project-id :name])))

(defn- in-scope-items [items requirements]
  (let [tokens (set requirements)]
    (if (seq tokens)
      (->> items
           (filter #(intersects-tokens? % tokens))
           vec)
      [])))

(defn- design-coverage-context [requirements]
  (let [ir (read-edn-if-exists ".llm/data/design-ir.edn")
        coverage (:coverage ir)
        gap-keys [:unassigned-requirements
                  :unassigned-implementation-requirements
                  :unknown-implementation-requirements]]
    {:available (boolean ir)
     :scope-requirements (vec (sort requirements))
     :gaps (into {}
                 (for [k gap-keys]
                   [k (in-scope-items (get coverage k) requirements)]))}))

(defn- markdown-files-under [path]
  (let [f (io/file path)]
    (cond
      (and (.isFile f) (str/ends-with? (.getName f) ".md")) [(.getPath f)]
      (.isDirectory f) (->> (file-seq f)
                            (filter #(.isFile %))
                            (map #(.getPath %))
                            (filter #(str/ends-with? % ".md")))
      :else [])))

(defn- matching-lines [paths terms limit]
  (let [tokens (->> terms (remove str/blank?) distinct vec)]
    (if (seq tokens)
      (->> paths
           (mapcat (fn [path]
                     (when (file? path)
                       (keep-indexed
                        (fn [idx line]
                          (when (let [line* (str/lower-case line)]
                                  (some #(str/includes? line* (str/lower-case %)) tokens))
                            {:file path
                             :line (inc idx)
                             :text (str/trim line)}))
                        (str/split-lines (slurp path))))))
           (take limit)
           vec)
      [])))

(defn- cross-document-context [repo-kind bricks requirements public-boundaries explicit-scope-terms]
  (let [terms (->> (concat (map name bricks)
                           requirements
                           public-boundaries
                           (map #(last (str/split % #"/")) public-boundaries)
                           explicit-scope-terms)
                   (remove str/blank?)
                   distinct
                   vec)
        decision-paths (if (= :template repo-kind)
                         (markdown-files-under ".llm/memory/archive/maintainer-discussions")
                         (markdown-files-under ".llm/memory/adr"))]
    {:terms terms
     :related-open-questions (matching-lines [".llm/memory/QUESTIONS.md"] terms 8)
     :related-knowledge (matching-lines [".llm/memory/KNOWLEDGE.md"] terms 8)
     :related-decisions (matching-lines decision-paths terms 8)}))

(defn- dependency-coords-in-file [path]
  (when (and (file? path) (str/ends-with? path "deps.edn"))
    (let [text (slurp path)]
      (->> (re-seq #"([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+)\s+(\{|\")" text)
           (map second)
           distinct
           sort
           vec))))

(defn- dependency-context [files archetypes]
  (if (contains? (set archetypes) :dependency-change)
    (let [coords (->> files
                      (mapcat dependency-coords-in-file)
                      distinct
                      sort
                      vec)
          libs-text (when (file? ".llm/data/libs.edn") (slurp ".llm/data/libs.edn"))
          deprecated-text (when (file? ".llm/data/deprecated-libs.patterns")
                            (slurp ".llm/data/deprecated-libs.patterns"))]
      {:available (boolean libs-text)
       :coords coords
       :in-catalog (->> coords
                        (filter #(and libs-text (str/includes? libs-text %)))
                        vec)
       :deprecated-candidates (->> coords
                                   (filter #(and deprecated-text (str/includes? deprecated-text %)))
                                   vec)})
    {:available (file? ".llm/data/libs.edn")
     :coords []
     :in-catalog []
     :deprecated-candidates []}))

(defn- enrich-required-evidence [required trace-context]
  (mapv (fn [e]
          (if (= :relevant-tests (:id e))
            (assoc e :derived-tests (:tests trace-context)
                     :derived-test-obligations (:test-obligations trace-context))
            e))
        required))

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

(defn- failure-mode [opts adoption entries]
  (let [errors (filter #(= :error (:severity %)) entries)
        unknown (filter #(= :uncategorized (:archetype %)) entries)
        generated-stale? false]
    (cond
      (seq errors) :cannot-derive
      (and (seq unknown)
           (or (not= :complete adoption)
               (= :degraded (:mode opts)))) :degraded-derive
      (and (= :strict (:mode opts)) (seq unknown)) :partial-derive
      generated-stale? :degraded-derive
      :else :ok)))

(defn- residual-declaration-placeholders []
  (assoc (zipmap residual-fields (repeat nil))
         :_required {:before-close residual-fields}))

(defn- evidence-placeholders [required-evidence]
  (mapv (fn [{:keys [id tier command]}]
          {:id id
           :tier tier
           :status nil
           :command (or command nil)
           :exit nil
           :repo-rev nil
           :tool-version nil
           :env-hash nil
           :started-at nil
           :duration-ms nil
           :tail nil
           :invalidated-by []})
        required-evidence))

(defn- merge-evidence-by-id [new-evidence old-evidence]
  (let [old-by-id (into {} (map (juxt :id identity) old-evidence))]
    (mapv (fn [entry]
            (if-let [old (get old-by-id (:id entry))]
              (merge entry
                     (select-keys old [:status
                                       :exit
                                       :repo-rev
                                       :tool-version
                                       :env-hash
                                       :started-at
                                       :duration-ms
                                       :tail
                                       :invalidated-by]))
              entry))
          new-evidence)))

(defn- preserve-active-declarations [packet old-packet]
  (cond-> packet
    (:llm-declared old-packet)
    (assoc :llm-declared (:llm-declared old-packet))

    (:intent old-packet)
    (assoc :intent (:intent old-packet))

    (seq (:evidence old-packet))
    (assoc :evidence (merge-evidence-by-id (:evidence packet) (:evidence old-packet)))))

(defn- declared-value? [v]
  (cond
    (nil? v) false
    (and (string? v) (str/blank? v)) false
    (and (coll? v) (empty? v)) false
    :else true))

(defn- declaration-value [v]
  (if (= "none" (str/lower-case (str/trim (str v))))
    :none
    v))

(defn- missing-residual-fields [packet]
  (let [declared (:llm-declared packet)]
    (->> residual-fields
         (remove #(declared-value? (get declared %)))
         vec)))

(defn derive-packet [opts]
  (let [started (now-ms)
        kind (repo-kind)
        adoption (adoption-mode)
        explicit-scope-terms (vec (:scope-terms opts))
        files (changed-files opts)
        entries (mapv #(classify-path kind %) files)
        bricks (->> entries
                    (mapcat (fn [{:keys [component base]}] [component base]))
                    (remove nil?)
                    (map keyword)
                    distinct
                    sort
                    vec)
        public-boundaries (->> entries (mapcat :public-boundaries) distinct sort vec)
        archetypes (->> entries (map :archetype) distinct sort vec)
        brick-requirements (requirement-ids-for-bricks bricks)
        brick-context (brick-context-for-bricks bricks)
        trace-context (trace-context-for-boundaries public-boundaries)
        requirements (->> (concat brick-requirements (:requirements trace-context))
                          distinct
                          sort
                          vec)
        direct-projects (->> entries (keep :project) (map keyword) distinct sort vec)
        affected-projects (->> (concat (map name direct-projects)
                                       (workspace-projects-for-bricks bricks))
                               distinct
                               sort
                               vec)
        design-coverage (design-coverage-context requirements)
        cross-doc-context (cross-document-context kind
                                                  bricks
                                                  requirements
                                                  public-boundaries
                                                  explicit-scope-terms)
        dependency-context (dependency-context files archetypes)
        required-evidence (enrich-required-evidence (evidence-set archetypes) trace-context)
        failure (failure-mode opts adoption entries)]
    {:schema/version schema-version
     :kind :review-fatigue-packet
     :task/id (or (:task/id opts)
                  (str (.toString (java.time.LocalDate/now))
                       "-structural-evidence"))
     :status (or (:status opts) :active)
     :repo-kind kind
     :adoption-mode adoption
     :mode (:mode opts)
     :baseline (cond-> {:type (if (:base opts) :git-diff :working-tree)
                        :git-rev (git-rev)
                        :default-branch (default-branch)
                        :comparison-points {:last-close-record nil
                                            :last-template-migration nil
                                            :last-generated-index-sync nil
                                            :last-close-on-same-bricks nil}}
                 (:base opts) (assoc :base (:base opts))
                 (:head opts) (assoc :head (:head opts)))
     :derivation {:status failure
                  :profile-ms {:total (elapsed-ms started)}
                  :rules-version schema-version}
     :actual-scope {:paths files
                    :query-scope explicit-scope-terms
                    :bricks bricks
                    :projects direct-projects
                    :affected-projects affected-projects
                    :public-boundaries public-boundaries
                    :requirements requirements
                    :brick-context brick-context
                    :archetypes archetypes}
     :save-policy (save-policy archetypes (count files))
     :required-evidence required-evidence
     :evidence (evidence-placeholders required-evidence)
     :llm-declared (residual-declaration-placeholders)
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
                            vec)
                       :coverage-gaps (:gaps design-coverage)}
     :derivation/audit entries
     :trace-context trace-context
     :design-coverage design-coverage
     :cross-document-context cross-doc-context
     :dependency-context dependency-context
     :evidence-tier-spec-version schema-version}))

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

(defn- matching-line-list [items]
  (if (seq items)
    (str/join
     "\n"
     (for [{:keys [file line text]} items]
       (str "- `" file ":" line "` " text)))
    "- none"))

(defn- named-list [items]
  (bullet-list (map str items)))

(defn- declared-value-markdown [v]
  (cond
    (nil? v)
    "- TBD: write `none` or concrete items before close"

    (= :none v)
    "- none"

    (sequential? v)
    (bullet-list (map pr-str v))

    (map? v)
    (str "```edn\n" (with-out-str (pprint/pprint v)) "```")

    :else
    (str "- " (pr-str v))))

(defn- residual-section [packet title key]
  (str "## " title "\n\n"
       (declared-value-markdown (get-in packet [:llm-declared key]))
       "\n\n"))

(defn- coverage-gap-markdown [gaps]
  (if (some seq (vals gaps))
    (str/join
     "\n\n"
     (for [[k items] gaps
           :when (seq items)]
       (str "### `" k "`\n" (bullet-list (map pr-str items)))))
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
       "\n\n### Affected Projects\n"
       (bullet-list (get-in packet [:actual-scope :affected-projects]))
       "\n\n### Public Boundaries\n"
       (bullet-list (get-in packet [:actual-scope :public-boundaries]))
       "\n\n### Requirements\n"
       (bullet-list (get-in packet [:actual-scope :requirements]))
       "\n\n### Archetypes\n"
       (bullet-list (map name (get-in packet [:actual-scope :archetypes])))
       "\n\n### Brick Context\n"
       "- provides: "
       (pr-str (get-in packet [:actual-scope :brick-context :provides]))
       "\n- groups: "
       (pr-str (get-in packet [:actual-scope :brick-context :groups]))
       "\n- not-for: "
       (pr-str (get-in packet [:actual-scope :brick-context :not-for]))
       "\n\n## Must Review\n\n"
       (bullet-list (get-in packet [:human-attention :must-review]))
       "\n\n## Safe To Skim\n\n"
       (bullet-list (get-in packet [:human-attention :safe-to-skim]))
       "\n\n## Trace Context\n\n"
       "- trace-index available: "
       (pr-str (get-in packet [:trace-context :available]))
       "\n- matched records: "
       (pr-str (get-in packet [:trace-context :matched-records]))
       "\n\n### Trace-Derived Tests\n"
       (named-list (get-in packet [:trace-context :tests]))
       "\n\n### Trace-Derived Test Obligations\n"
       (named-list (get-in packet [:trace-context :test-obligations]))
       "\n\n## Design Coverage Gaps In Scope\n\n"
       (coverage-gap-markdown (get-in packet [:human-attention :coverage-gaps]))
       "\n\n## Cross-Document Context\n\n"
       "### Related Open Questions\n"
       (matching-line-list (get-in packet [:cross-document-context :related-open-questions]))
       "\n\n### Related Knowledge\n"
       (matching-line-list (get-in packet [:cross-document-context :related-knowledge]))
       "\n\n### Related Decisions / Archive Entries\n"
       (matching-line-list (get-in packet [:cross-document-context :related-decisions]))
       "\n\n## Dependency Context\n\n"
       "- lib catalog available: "
       (pr-str (get-in packet [:dependency-context :available]))
       "\n\n### Touched Coordinates\n"
       (named-list (get-in packet [:dependency-context :coords]))
       "\n\n### Coordinates In Catalog\n"
       (named-list (get-in packet [:dependency-context :in-catalog]))
       "\n\n### Deprecated Candidates\n"
       (named-list (get-in packet [:dependency-context :deprecated-candidates]))
       "\n\n## Required Evidence\n\n"
       (evidence-list (:required-evidence packet))
       "\n\n"
       (residual-section packet
                         "Semantic Impact Not Derived By Structure"
                         :semantic-impact-not-derived)
       (residual-section packet
                         "Unknowns Not Captured By Derivation"
                         :unknowns-not-captured-by-derivation)
       (residual-section packet
                         "Cross-Brick Effects Not In Trace Index"
                         :cross-brick-effects-not-in-trace-index)
       (residual-section packet
                         "Override / Scope Extension"
                         :override)
       (residual-section packet
                         "Remaining Fatigue"
                         :remaining-fatigue)))

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
        md-path (str out-dir "/" task-id ".md")
        existed? (file? edn-path)
        packet* (if existed?
                  (preserve-active-declarations packet (edn/read-string (slurp edn-path)))
                  packet)]
    (write-edn! edn-path packet*)
    (write-markdown! md-path packet*)
    (println "Review Fatigue Packet generated:")
    (println " " edn-path)
    (println " " md-path)
    (when existed?
      (println "Existing residual declarations were preserved."))))

(defn inspect [opts]
  (let [packet (if-let [path (or (:from opts) (:out opts))]
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

(defn check-residual-declared [opts]
  (let [path (or (:packet opts) (:from opts) (first (:extra-args opts)))]
    (when-not path
      (binding [*out* *err*]
        (println "Usage: structural-evidence check-residual --packet .llm/work/<task-id>.edn"))
      (System/exit 2))
    (let [packet (edn/read-string (slurp path))
          status (:status packet)
          missing (missing-residual-fields packet)]
      (cond
        (and (= :closed status) (seq missing))
        (do
          (binding [*out* *err*]
            (println "Residual declaration incomplete for closed packet:" path)
            (doseq [field missing]
              (println " -" field))
            (println "Use :none or a concrete value for each field before close."))
          (System/exit 1))

        (seq missing)
        (do
          (println "Residual declaration pending until close:" path)
          (doseq [field missing]
            (println " -" field)))

        :else
        (println "Residual declaration complete:" path)))))

(defn- active-packet-files []
  (let [dir (io/file default-out-dir)]
    (if (.isDirectory dir)
      (->> (file-seq dir)
           (filter #(.isFile %))
           (map #(.getPath %))
           (filter #(str/ends-with? % ".edn"))
           (remove #(str/includes? % ".predict.edn"))
           (remove #(str/includes? % ".intent.edn"))
           (filter (fn [path]
                     (let [status (:status (edn/read-string (slurp path)))]
                       (not (contains? #{:clean-close :closed} status)))))
           sort
           vec)
      [])))

(defn- closed-record-files []
  (let [dir (io/file ".llm/evidence/closed")]
    (if (.isDirectory dir)
      (->> (file-seq dir)
           (filter #(.isFile %))
           (map #(.getPath %))
           (filter #(str/ends-with? % ".edn"))
           sort
           vec)
      [])))

(defn- read-packet-file [path]
  (try
    (edn/read-string (slurp path))
    (catch Throwable _
      nil)))

(defn- closed-records []
  (->> (closed-record-files)
       (keep (fn [path]
               (when-let [packet (read-packet-file path)]
                 (assoc packet :record/path path))))
       vec))

(defn- latest-evidence-by-id []
  (->> (closed-records)
       (sort-by #(or (:closed-at %) ""))
       (mapcat (fn [packet]
                 (for [entry (:evidence packet)]
                   [(:id entry)
                    (assoc entry
                           :task/id (:task/id packet)
                           :closed-at (:closed-at packet))])))
       (reduce (fn [m [id entry]] (assoc m id entry)) {})))

(defn- status-line-for-packet [path]
  (let [packet (edn/read-string (slurp path))
        missing (missing-residual-fields packet)]
    (str "- " (:task/id packet)
         " (" (name (or (:save-policy packet) :unknown-save))
         ", " (name (or (get-in packet [:derivation :status]) :unknown-derive))
         ", residual: " (if (seq missing) "pending" "declared")
         ")")))

(defn- evidence-status-suffix [latest entry]
  (if-let [result (get latest (:id entry))]
    (str " last=" (name (or (:status result) :unknown))
         " task=" (:task/id result)
         (when-let [rev (:repo-rev result)]
           (str " rev=" (subs rev 0 (min 8 (count rev)))))
         (when-let [duration (:duration-ms result)]
           (str " duration-ms=" duration)))
    " last=none"))

(defn- record-matches-terms? [packet terms]
  (let [terms* (map normalize-token terms)
        values (map normalize-token (scalars packet))]
    (boolean
     (some (fn [term]
             (some #(or (= term %)
                        (str/includes? % term)
                        (str/includes? term %))
                   values))
           terms*))))

(defn run-status [opts]
  (let [packet (derive-packet opts)
        active (active-packet-files)
        closed (closed-record-files)
        latest-evidence (latest-evidence-by-id)]
    (println "== Evidence Status at HEAD ==")
    (println)
    (println "Authority Plane:")
    (println "  Related open QUESTIONS:" (count (get-in packet [:cross-document-context :related-open-questions])))
    (println "  Related KNOWLEDGE entries:" (count (get-in packet [:cross-document-context :related-knowledge])))
    (println "  Related decisions/archive entries:" (count (get-in packet [:cross-document-context :related-decisions])))
    (println)
    (println "Structure Plane:")
    (when (seq (get-in packet [:actual-scope :query-scope]))
      (println "  Query scope:" (str/join ", " (get-in packet [:actual-scope :query-scope]))))
    (println "  Touched paths:" (count (get-in packet [:actual-scope :paths])))
    (println "  Touched bricks:" (str/join ", " (map name (get-in packet [:actual-scope :bricks]))))
    (println "  Affected projects:" (str/join ", " (get-in packet [:actual-scope :affected-projects])))
    (println "  Public boundaries:" (str/join ", " (get-in packet [:actual-scope :public-boundaries])))
    (println)
    (println "Index Plane:")
    (println "  design-ir.edn:" (if (get-in packet [:design-coverage :available]) "available" "missing"))
    (println "  trace-index.edn:" (if (get-in packet [:trace-context :available]) "available" "missing"))
    (println "  libs.edn:" (if (get-in packet [:dependency-context :available]) "available" "missing"))
    (println)
    (println "Verification Plane:")
    (doseq [{:keys [id tier command]} (:required-evidence packet)]
      (println " -" id "[" (name tier) "]"
               (or command "(manual command required)")
               (evidence-status-suffix latest-evidence {:id id})))
    (println)
    (println "Evidence Plane:")
    (println "  Active Review Fatigue Packets:")
    (if (seq active)
      (doseq [path active] (println " " (status-line-for-packet path)))
      (println "  - none"))
    (println "  Closed records:" (count closed))
    (println)
    (println "Trust Diff / Gaps:")
    (let [gaps (get-in packet [:human-attention :coverage-gaps])]
      (if (some seq (vals gaps))
        (doseq [[k items] gaps :when (seq items)]
          (println " -" k ":" (count items)))
        (println " - none in current derived scope")))))

(defn- record-summary-line [packet]
  (str "- " (:task/id packet)
       " (" (name (or (:status packet) :unknown-status))
       ", " (name (or (:save-policy packet) :unknown-save))
       ", paths=" (count (get-in packet [:actual-scope :paths]))
       ", evidence=" (count (:evidence packet))
       ")"
       (when-let [closed-at (:closed-at packet)]
         (str " closed-at=" closed-at))))

(defn search-records [opts]
  (let [terms (vec (or (:scope-terms opts) (:extra-args opts)))
        records (closed-records)
        matches (if (seq terms)
                  (filter #(record-matches-terms? % terms) records)
                  records)]
    (println "== Evidence Record Search ==")
    (println "Scope terms:" (if (seq terms) (str/join ", " terms) "(all)"))
    (println)
    (if (seq matches)
      (doseq [packet matches]
        (println (record-summary-line packet))
        (when-let [override (get-in packet [:llm-declared :override])]
          (when (declared-value? override)
            (println "  override:" (pr-str override))))
        (let [must-review (take 3 (get-in packet [:human-attention :must-review]))]
          (when (seq must-review)
            (println "  must-review:" (str/join ", " must-review)))))
      (println "- none"))))

(defn predict [opts]
  (let [task-id (or (:task/id opts)
                    (str (.toString (java.time.LocalDate/now)) "-evidence-task"))
        packet (assoc (derive-packet (assoc opts :task/id task-id :status :predicted))
                      :intent {:text (or (:intent opts) "")
                               :recorded-at (.toString (java.time.Instant/now))})
        base (str default-out-dir "/" task-id)]
    (write-edn! (str base ".intent.edn") (:intent packet))
    (write-edn! (str base ".predict.edn") packet)
    (write-markdown! (str base ".predict.md") packet)
    (println "Evidence prediction generated:")
    (println " " (str base ".intent.edn"))
    (println " " (str base ".predict.edn"))
    (println " " (str base ".predict.md"))))

(defn declare-residual [opts]
  (let [task-id (or (:task/id opts) (first (:extra-args opts)))
        _ (when-not task-id
            (binding [*out* *err*]
              (println "Usage: structural-evidence declare --task TASK-ID [--all-none|--semantic-impact TEXT ...]"))
            (System/exit 2))
        path (str default-out-dir "/" task-id ".edn")
        predict-path (str default-out-dir "/" task-id ".predict.edn")
        packet (cond
                 (file? path)
                 (edn/read-string (slurp path))

                 (file? predict-path)
                 (assoc (edn/read-string (slurp predict-path)) :status :active)

                 :else
                 nil)
        _ (when-not packet
            (binding [*out* *err*]
              (println "No active packet found:" path)
              (println "Run evidence predict or propose-review-packet.sh first."))
            (System/exit 2))
        updates (if (:all-none opts)
                  (zipmap residual-fields (repeat :none))
                  (update-vals (:declare opts) declaration-value))
        packet* (update packet :llm-declared merge updates)
        md-path (str default-out-dir "/" task-id ".md")]
    (write-edn! path packet*)
    (write-markdown! md-path packet*)
    (println "Residual declarations updated:")
    (println " " path)
    (println " " md-path)
    (if-let [missing (seq (missing-residual-fields packet*))]
      (do
        (println "Still pending:")
        (doseq [field missing] (println " -" field)))
      (println "Residual declaration complete."))))

(defn- run-evidence-command [entry]
  (let [cmd (:command entry)]
    (if (str/blank? (str cmd))
      (assoc entry
             :status :not-run
             :repo-rev (git-rev)
             :tail "No command is defined for this evidence item.")
      (let [started (now-ms)
            started-at (.toString (java.time.Instant/now))
            {:keys [exit out err]} (shell/sh "bash" "-lc" cmd)
            output (str out (when (seq err) (str "\n" err)))]
        (assoc entry
               :status (if (zero? exit) :pass :fail)
               :exit exit
               :repo-rev (git-rev)
               :started-at started-at
               :duration-ms (elapsed-ms started)
               :tail (when-not (zero? exit) (tail-lines output 40)))))))

(defn run-evidence [opts]
  (let [task-id (or (:task/id opts) (first (:extra-args opts)))
        _ (when-not task-id
            (binding [*out* *err*]
              (println "Usage: structural-evidence run --task TASK-ID"))
            (System/exit 2))
        path (str default-out-dir "/" task-id ".edn")
        _ (when-not (file? path)
            (binding [*out* *err*]
              (println "No active packet found:" path)
              (println "Run propose-review-packet.sh first."))
            (System/exit 2))
        packet (edn/read-string (slurp path))
        evidence* (mapv run-evidence-command (:evidence packet))
        packet* (assoc packet :evidence evidence*)
        md-path (str default-out-dir "/" task-id ".md")
        failed (->> evidence* (filter #(= :fail (:status %))) (map :id) vec)]
    (write-edn! path packet*)
    (write-markdown! md-path packet*)
    (println "Evidence command results recorded:")
    (println " " path)
    (println " " md-path)
    (if (seq failed)
      (do
        (println "Failed evidence:")
        (doseq [id failed] (println " -" id))
        (System/exit 1))
      (println "No command-backed evidence failed."))))

(defn- scope-diff [predicted actual key]
  (let [p (set (get-in predicted [:actual-scope key]))
        a (set (get-in actual [:actual-scope key]))]
    {:predicted-only (vec (sort (remove a p)))
     :actual-only (vec (sort (remove p a)))}))

(defn- divergence? [diffs]
  (boolean
   (some seq
         (mapcat (juxt :predicted-only :actual-only) (vals diffs)))))

(defn- scope-expanded? [diffs]
  (boolean
   (some seq (map :actual-only (vals diffs)))))

(defn- none-declaration? [v]
  (= :none v))

(defn- missing-evidence-statuses [packet]
  (->> (:evidence packet)
       (filter #(nil? (:status %)))
       (map :id)
       vec))

(defn- failed-evidence-statuses [packet]
  (->> (:evidence packet)
       (filter #(= :fail (:status %)))
       (map :id)
       vec))

(defn- print-residual-actions [task-id missing diffs]
  (println "Next actions:")
  (println "  Fill residual declarations with:")
  (println "   ./.llm/scripts/evidence.sh declare --task" task-id "\\")
  (doseq [field missing]
    (case field
      :semantic-impact-not-derived
      (println "     --semantic-impact \"none\" \\")

      :unknowns-not-captured-by-derivation
      (println "     --unknowns \"none\" \\")

      :cross-brick-effects-not-in-trace-index
      (println "     --cross-brick-effects \"none\" \\")

      :override
      (if (and diffs (scope-expanded? diffs))
        (println "     --override \"actual scope expanded intentionally because ...\" \\")
        (println "     --override \"none\" \\"))

      :remaining-fatigue
      (println "     --remaining-fatigue \"none\" \\")))
  (println "  Replace `none` with concrete text when there is residual impact.")
  (when (and diffs (divergence? diffs))
    (println "  Predict/actual divergence exists; if actual scope is intentional, declare it in --override.")))

(defn close [opts]
  (let [task-id (or (:task/id opts) (first (:extra-args opts)))
        _ (when-not task-id
            (binding [*out* *err*]
              (println "Usage: structural-evidence close --task TASK-ID"))
            (System/exit 2))
        predicted-path (str default-out-dir "/" task-id ".predict.edn")
        active-path (str default-out-dir "/" task-id ".edn")
        predicted (when (file? predicted-path) (edn/read-string (slurp predicted-path)))
        derived-actual (derive-packet (assoc opts :task/id task-id :status :active))
        actual (if (file? active-path)
                 (preserve-active-declarations derived-actual (edn/read-string (slurp active-path)))
                 derived-actual)
        evidence-missing (missing-evidence-statuses actual)
        evidence-failed (failed-evidence-statuses actual)
        diffs (when predicted
                {:bricks (scope-diff predicted actual :bricks)
                 :paths (scope-diff predicted actual :paths)
                 :public-boundaries (scope-diff predicted actual :public-boundaries)})
        override-required? (and predicted
                                (scope-expanded? diffs)
                                (none-declaration? (get-in actual [:llm-declared :override])))
        missing (cond-> (missing-residual-fields actual)
                  override-required? (conj :override))
        record (assoc actual
                      :status (if (seq missing) :blocked-close :clean-close)
                      :closed-at (.toString (java.time.Instant/now))
                      :predict-vs-actual diffs)]
    (println "== Evidence Close ==")
    (println "Task:" task-id)
    (println "Close mode:" (:status record))
    (when-not predicted
      (println "Warning: no predict record found; close used actual scope only."))
    (when predicted
      (println "Predict vs actual:" (pr-str diffs)))
    (when override-required?
      (println "Scope expansion detected: :override must be a concrete justification, not :none."))
    (when (seq evidence-failed)
      (println "Failed evidence:" (pr-str evidence-failed)))
    (when (seq evidence-missing)
      (println "Evidence not yet recorded:" (pr-str evidence-missing)))
    (if (seq missing)
      (do
        (println "Blocked: residual fields are not declared:")
        (doseq [field missing] (println " -" field))
        (write-edn! active-path record)
        (write-markdown! (str default-out-dir "/" task-id ".md") record)
        (println "Active packet updated with blocked-close state:")
        (println " " active-path)
        (println " " (str default-out-dir "/" task-id ".md"))
        (print-residual-actions task-id missing diffs)
        (println "No closed record written.")
        (System/exit 1))
      (let [out (str ".llm/evidence/closed/" task-id ".edn")]
        (write-edn! active-path record)
        (write-markdown! (str default-out-dir "/" task-id ".md") record)
        (write-edn! out record)
        (println "Active packet updated with clean-close state:")
        (println " " active-path)
        (println " " (str default-out-dir "/" task-id ".md"))
        (println "Closed evidence record written:" out)))))

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
                         (derive-packet {:changed-files ["DESIGN.md"]}))
        closed-missing (assoc project-design :status :closed)
        closed-declared (assoc project-design
                               :status :closed
                               :llm-declared
                               (assoc (zipmap residual-fields (repeat :none))
                                      :_required {:before-close residual-fields}))]
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
    (assert! "derived packet leaves residual fields pending"
             (seq (missing-residual-fields project-design)))
    (assert! "closed packet with pending residual fields is invalid"
             (seq (missing-residual-fields closed-missing)))
    (assert! "closed packet with explicit none residual fields is valid"
             (empty? (missing-residual-fields closed-declared)))
    (println "structural-evidence self-test: OK")))

(defn -main [& args]
  (try
    (let [[cmd & more] args
          opts (parse-args more)]
      (case cmd
        "derive" (run-derive opts)
        "propose" (propose opts)
        "inspect" (inspect opts)
        "check-residual" (check-residual-declared opts)
        "status" (run-status opts)
        "search" (search-records opts)
        "predict" (predict opts)
        "declare" (declare-residual opts)
        "run" (run-evidence opts)
        "close" (close opts)
        "self-test" (self-test opts)
        (do
          (binding [*out* *err*]
            (println "Usage:")
            (println "  structural-evidence derive [--base BASE] [--head HEAD] [--out PATH] [--strict|--degraded] [--profile]")
            (println "  structural-evidence propose [--task-id ID] [--out-dir DIR] [--strict|--degraded]")
            (println "  structural-evidence inspect [--base BASE] [--head HEAD] [--from PATH]")
            (println "  structural-evidence check-residual --packet PATH")
            (println "  structural-evidence status [--scope TERM[,TERM...]] [--base BASE] [--head HEAD]")
            (println "  structural-evidence search [--scope TERM[,TERM...]]")
            (println "  structural-evidence predict --task TASK-ID --intent TEXT [--changed-file PATH]")
            (println "  structural-evidence declare --task TASK-ID [--all-none|--semantic-impact TEXT ...]")
            (println "  structural-evidence run --task TASK-ID")
            (println "  structural-evidence close --task TASK-ID")
            (println "  structural-evidence self-test"))
          (System/exit 2))))
    (finally
      (shutdown-agents))))
