(ns structural-evidence
  "Derive structural evidence views from local repository structure.

   This namespace is intentionally side-effect light. The derived packet is a
   generated review view, not a new authority source."
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.java.shell :as shell]
   [clojure.pprint :as pprint]
   [clojure.set :as set]
   [clojure.string :as str]
   [derivation-manifest :as derivation])
  (:import
   [java.security MessageDigest]))

(def schema-version "structural-evidence.1")

(def default-out-dir ".llm/work")
(def work-view-dir ".llm/work/views")
(def work-declaration-dir ".llm/work/declarations")
(def work-run-dir ".llm/work/runs")
(def structural-evidence-generator-path ".llm/scripts/structural_evidence.clj")

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

(defn- command-result [& args]
  (try
    (let [{:keys [exit out err]} (apply shell/sh args)]
      {:exit exit
       :out (str/trim (str out))
       :err (str/trim (str err))})
    (catch Throwable t
      {:exit 127
       :out ""
       :err (.getMessage t)})))

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

(defn- bytes->hex [bytes]
  (apply str (map #(format "%02x" (bit-and % 0xff)) bytes)))

(defn- sha1-hex [s]
  (let [digest (MessageDigest/getInstance "SHA-1")]
    (.update digest (.getBytes (str s) "UTF-8"))
    (bytes->hex (.digest digest))))

(defn- stable-evidence-env []
  (let [base {"PATH" (or (System/getenv "EVIDENCE_PATH")
                         "/usr/local/bin:/usr/bin:/bin")
              "HOME" (or (System/getenv "HOME") "")
              "LANG" "C.UTF-8"
              "LC_ALL" "C.UTF-8"
              "CI" "true"}
        passthrough-keys ["JAVA_HOME" "M2_HOME" "GRAALVM_HOME"
                          "CLOJURE_VERSION" "USER" "TZ"]]
    (into base
          (for [k passthrough-keys
                :let [v (System/getenv k)]
                :when (not (str/blank? (str v)))]
            [k v]))))

(defn- env-assignment [k v]
  (str k "=" v))

(defn- env-hash [env]
  (sha1-hex (pr-str (into (sorted-map) env))))

(defn- clj-runtime-version []
  (let [{:keys [exit out err]} (command-result "clj" "-Sdescribe")]
    (if (zero? exit)
      (let [description (try
                          (edn/read-string out)
                          (catch Throwable _ nil))]
        {:available true
         :version (or (:version description)
                      (:clojure-version description)
                      :unknown)})
      {:available false
       :error (tail-lines (str out "\n" err) 5)})))

(defn- bb-runtime-version []
  (let [{:keys [exit out err]} (command-result "bb" "--version")]
    (if (zero? exit)
      {:available true
       :version (or (first (str/split-lines out)) :unknown)}
      {:available false
       :error (tail-lines (str out "\n" err) 5)})))

(def ^:private runtime-versions
  (delay {:clj (clj-runtime-version)
          :bb (bb-runtime-version)}))

(defn- tool-version []
  {:runtime (or (some-> (System/getenv "LLM_CLJ_RUNTIME_SELECTED") keyword)
                :unknown)
   :requested-runtime (or (System/getenv "LLM_CLJ_RUNTIME") "auto")
   :runtimes @runtime-versions
   :clojure (clojure-version)
   :babashka (System/getProperty "babashka.version")
   :jvm (System/getProperty "java.version")
   :java-vm (System/getProperty "java.vm.name")
   :os (System/getProperty "os.name")
   :os-version (System/getProperty "os.version")
   :shell "env -i ... bash -c"})

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
      "--staged" (recur (assoc m :diff-source :staged) xs)
      "--working-tree" (recur (assoc m :diff-source :working-tree) xs)
      "--advisory" (recur (assoc m :advisory true) xs)
	      "--no-write" (recur (assoc m :no-write true) xs)
	      "--dry-run" (recur (assoc m :dry-run true) xs)
      "--confirm" (recur (assoc m :confirm true) xs)
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

(defn- parse-name-status-line [line]
  (let [[status & paths] (str/split line #"\t")
        path (last paths)]
    (when (seq path)
      {:status status
       :path path})))

(defn- git-name-status [opts]
  (let [source (:diff-source opts)
        base (:base opts)
        head (or (:head opts) "HEAD")
        lines (cond
                (= :staged source)
                (shell-lines "git" "diff" "--cached" "--name-status")

                base
                (shell-lines "git" "diff" "--name-status" (str base "..." head))

                :else
                (concat (shell-lines "git" "diff" "--name-status")
                        (shell-lines "git" "diff" "--cached" "--name-status")
                        (map #(str "A\t" %) (shell-lines "git" "ls-files" "--others" "--exclude-standard"))))]
    (->> lines
         (keep parse-name-status-line)
         (group-by :path)
         (map (fn [[_ entries]] (last entries)))
         (sort-by :path)
         vec)))

(defn- changed-files [opts]
  (if (seq (:changed-files opts))
    (->> (:changed-files opts) distinct sort vec)
    (->> (git-name-status opts)
         (map :path)
         distinct
         sort
         vec)))

(defn- object-id-for-path [opts path status]
  (cond
    (str/starts-with? (str status) "D")
    "deleted"

    (= :staged (:diff-source opts))
    (or (first (shell-lines "git" "rev-parse" (str ":" path))) "missing")

    (:base opts)
    (or (first (shell-lines "git" "rev-parse" (str (or (:head opts) "HEAD") ":" path))) "missing")

    (file? path)
    (or (first (shell-lines "git" "hash-object" path)) "missing")

    :else
    "missing"))

(defn- fingerprint-content-data [fingerprint]
  {:paths (:paths fingerprint)
   :path-status (:path-status fingerprint)
   :path-hashes (:path-hashes fingerprint)})

(defn- fingerprint-content-digest [fingerprint]
  (or (:content-digest fingerprint)
      (sha1-hex (pr-str (fingerprint-content-data fingerprint)))))

(defn- change-fingerprint [opts files]
  (let [statuses (if (seq (:changed-files opts))
                   (mapv (fn [path] {:status "M" :path path}) files)
                   (git-name-status opts))
        status-by-path (into {} (map (juxt :path :status) statuses))
        path-hashes (into (sorted-map)
                          (for [path files]
                            [path (object-id-for-path opts path (get status-by-path path))]))
        source (cond
                 (= :staged (:diff-source opts)) :staged-diff
                 (:base opts) :git-diff
                 :else :working-tree)
        data {:source source
              :base (:base opts)
              :head (or (:head opts) "HEAD")
              :paths (vec files)
              :path-status (into (sorted-map) status-by-path)
              :path-hashes path-hashes}
        content-digest (fingerprint-content-digest data)
        digest (sha1-hex (pr-str data))]
    (assoc data
           :digest digest
           :content-digest content-digest
           :derived-at (.toString (java.time.Instant/now)))))

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
        (= ".llm/work/.gitignore" path)
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

(def rule-match-explanations
  {:template-governance "template repo: CLAUDE.md, .gitignore, .llm/repo-context.edn, or .llm/guide/*"
   :script-change "template repo: .llm/scripts/*"
   :maintainer-archive "template repo: .llm/memory/archive/*"
   :template-adr-forbidden "template repo: .llm/memory/adr/* except README.md/template.md is forbidden"
   :generated-index ".llm/data/*"
   :docs-only "markdown or documentation path"
   :component-interface "project repo: components/<brick>/src/**/interface.clj"
   :component-internal "project repo: components/<brick>/src/**/*.clj|cljc|cljs"
   :component-brick-intent "project repo: components/<brick>/brick.edn"
   :base-entrypoint "project repo: bases/<brick>/src/**/*.clj|cljc|cljs"
   :base-brick-intent "project repo: bases/<brick>/brick.edn"
   :project-intent "project repo: projects/<project>/project.edn"
   :spec-memory "project repo: DESIGN.md or .llm/memory/*"
   :dependency-change "deps.edn or nested */deps.edn"
   :uncategorized "no derivation rule matched"})

(defn- rule-match-explanation [rule]
  (get rule-match-explanations rule "rule explanation unavailable"))

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

(def generic-context-terms
  #{"README.md" "DESIGN.md" "interface.clj" "core.clj" "deps.edn" "md" "clj"})

(defn- context-search-term? [term]
  (let [t (str/trim (str term))]
    (and (not (str/blank? t))
         (>= (count t) 3)
         (not (contains? generic-context-terms t)))))

(defn- matching-lines [paths terms limit]
  (let [tokens (->> terms
                    (filter context-search-term?)
                    distinct
                    vec)]
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
                   (filter context-search-term?)
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
  (zipmap residual-fields (repeat nil)))

(defn- evidence-invalidated-by [{:keys [id]} {:keys [paths bricks requirements public-boundaries]}]
  (let [paths* (vec (sort (distinct paths)))
        bricks* (vec (sort (map name (distinct bricks))))
        requirements* (vec (sort (distinct requirements)))
        boundaries* (vec (sort (distinct public-boundaries)))]
    (vec
     (concat
      (when (seq paths*)
        [{:type :path-changed :paths paths*}])
      (when (and (seq bricks*)
                 (contains? #{:poly-check
                              :relevant-tests
                              :check-interface-contracts
                              :check-trace-metadata
                              :check-brick-map
                              :check-workspace-map}
                            id))
        [{:type :brick-changed :bricks bricks*}])
      (when (and (seq requirements*)
                 (contains? #{:check-design-ir
                              :check-trace-index
                              :check-trace-metadata
                              :relevant-tests}
                            id))
        [{:type :requirement-changed :requirements requirements*}])
      (when (and (seq boundaries*)
                 (contains? #{:check-interface-contracts
                              :check-trace-metadata
                              :relevant-tests}
                            id))
        [{:type :public-boundary-changed :public-boundaries boundaries*}])))))

(defn- evidence-placeholders [required-evidence scope]
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
           :invalidated-by (evidence-invalidated-by {:id id} scope)})
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

(declare view-path
         view-markdown-path
         predicted-view-path
         declaration-path
         run-result-path
         read-packet-file
         prune-stale-generated-views!
         current-task-id
         fingerprint-matches?
         diff-source-flag)

(defn- structural-view-input [view]
  {:path (str "change-fingerprint:"
              (name (get-in view [:change/fingerprint :source] :unknown)))
   :input/kind :virtual
   :digest (str "sha1:" (get-in view [:change/fingerprint :digest] "missing"))})

(def structural-evidence-static-input-paths
  [".llm/repo-context.edn"
   ".llm/data/design-ir.edn"
   ".llm/data/trace-index.edn"
   ".llm/data/brick-map.edn"
   ".llm/data/workspace-map.edn"
   ".llm/data/libs.edn"
   ".llm/data/deprecated-libs.patterns"
   ".llm/memory/QUESTIONS.md"
   ".llm/memory/KNOWLEDGE.md"])

(defn- markdown-inputs-under [path]
  (let [f (io/file path)]
    (cond
      (and (.isFile f) (str/ends-with? (.getName f) ".md"))
      [(.getPath f)]

      (.isDirectory f)
      (->> (file-seq f)
           (filter #(.isFile %))
           (map #(.getPath %))
           (filter #(str/ends-with? % ".md"))
           sort
           vec)

      :else
      [path])))

(defn- structural-evidence-input-paths []
  (vec
   (distinct
    (concat structural-evidence-static-input-paths
            (if (= :template (repo-kind))
              (markdown-inputs-under ".llm/memory/archive/maintainer-discussions")
              (markdown-inputs-under ".llm/memory/adr"))))))

(defn- structural-evidence-observed-inputs [view]
  (vec
   (cons (structural-view-input view)
         (map derivation/observed-input
              (structural-evidence-input-paths)))))

(defn- structural-view-manifest
  ([view]
   (structural-view-manifest view nil))
  ([view generated-at]
   (derivation/make-manifest
    {:id :structural-evidence-view
     :tool "structural-evidence"
     :output-path (if (= :predicted (:status view))
                    (predicted-view-path (:task/id view))
                    (view-path (:task/id view)))
     :generator-path structural-evidence-generator-path
     :tool-input-paths [".llm/scripts/derivation_manifest.clj"]
     :observed-inputs (structural-evidence-observed-inputs view)
     :input-policy {:change-fingerprint :required
                    :missing :explicit-empty
                    :memory-scan :observed-markdown-files}
     :generated-at generated-at
     :regenerate-command (str "./.llm/scripts/propose-review-packet.sh --task "
                              (:task/id view)
                              (diff-source-flag view))})))

(defn- stamp-view-derivation [view]
  (derivation/with-manifest view (structural-view-manifest view)))

(defn- view-freshness [view]
  (let [manifest (derivation/artifact-manifest view)]
    (cond
      (nil? manifest)
      {:status :broken-manifest
       :reason (str "missing " derivation/manifest-key)
       :derivation/id :structural-evidence-view}

      (not= derivation/schema-version (:derivation/schema manifest))
      {:status :broken-manifest
       :reason (str "unsupported schema " (pr-str (:derivation/schema manifest)))
       :derivation/id (:derivation/id manifest)}

      :else
      (let [current (structural-view-manifest view (:derivation/generated-at manifest))
            fresh? (= (:derivation/action-key manifest)
                      (:derivation/action-key current))]
        {:status (if fresh? :fresh :stale)
         :derivation/id (:derivation/id manifest)
         :recorded-action-key (:derivation/action-key manifest)
         :current-action-key (:derivation/action-key current)
         :changed-generator (when (not= (:derivation/generator manifest)
                                        (:derivation/generator current))
                              {:recorded (:derivation/generator manifest)
                               :current (:derivation/generator current)})
         :changed-inputs (when (not= (:derivation/inputs manifest)
                                     (:derivation/inputs current))
                           {:recorded (:derivation/inputs manifest)
                            :current (:derivation/inputs current)})}))))

(defn- fresh-derived-view? [packet-or-view]
  (= :fresh
     (or (get-in packet-or-view [:artifact/components :derived-view :freshness :status])
         (:status (view-freshness packet-or-view)))))

(defn- ensure-fresh-derived-view! [view task-id]
  (let [freshness (view-freshness view)]
    (when-not (= :fresh (:status freshness))
      (binding [*out* *err*]
        (println "Structural Evidence derived view is stale or has a broken manifest.")
        (println "Task:" task-id)
        (println "Freshness:" (name (:status freshness)))
        (when-let [reason (:reason freshness)]
          (println "Reason:" reason))
        (println "Regenerate the derived view:")
        (println (str " ./.llm/scripts/propose-review-packet.sh --task " task-id (diff-source-flag view))))
      (System/exit 1))))

(defn- declaration-artifact [packet llm-declared intent]
  {:schema/version schema-version
   :kind :structural-evidence-declaration
   :artifact/regime :declaration
   :task/id (:task/id packet)
   :status :active
   :change/fingerprint (:change/fingerprint packet)
   :intent intent
   :llm-declared llm-declared
   :updated-at (.toString (java.time.Instant/now))})

(defn- run-result-artifact [packet evidence]
  {:schema/version schema-version
   :kind :structural-evidence-run-result
   :artifact/regime :transient-observation
   :artifact/lifecycle :evidence-run-result
   :task/id (:task/id packet)
   :status :recorded
   :change/fingerprint (:change/fingerprint packet)
   :evidence evidence
   :updated-at (.toString (java.time.Instant/now))})

(defn- view-artifact [packet]
  (-> packet
      (assoc :kind :review-fatigue-derived-view
             :artifact/regime :derived-view)
      (dissoc :llm-declared
              :intent
              :predict-vs-actual
              :closed-at
              :closed-git-rev)
      stamp-view-derivation))

(defn- read-artifact [path]
  (when (file? path)
    (edn/read-string (slurp path))))

(defn- declaration-for [task-id]
  (read-artifact (declaration-path task-id)))

(defn- run-result-for [task-id]
  (read-artifact (run-result-path task-id)))

(defn- same-fingerprint-digest? [a b]
  (and (get-in a [:change/fingerprint :digest])
       (= (get-in a [:change/fingerprint :digest])
          (get-in b [:change/fingerprint :digest]))))

(defn- same-fingerprint-content? [a b]
  (let [a-fingerprint (:change/fingerprint a)
        b-fingerprint (:change/fingerprint b)]
    (and a-fingerprint
         b-fingerprint
         (= (fingerprint-content-digest a-fingerprint)
            (fingerprint-content-digest b-fingerprint)))))

(defn- assemble-packet
  ([view]
   (assemble-packet view (declaration-for (:task/id view)) (run-result-for (:task/id view))))
  ([view declaration run-result]
   (let [declaration* (when (same-fingerprint-digest? view declaration)
                        declaration)
         run-result* (when (same-fingerprint-digest? view run-result)
                       run-result)
         declared (or (:llm-declared declaration*)
                      (residual-declaration-placeholders))
         evidence (merge-evidence-by-id (:evidence view)
                                        (:evidence run-result*))
         derived-view-path (if (= :predicted (:status view))
                             (predicted-view-path (:task/id view))
                             (view-path (:task/id view)))
         freshness (view-freshness view)]
     (cond-> (assoc view
                    :kind :review-fatigue-packet
                    :artifact/container :review-fatigue-packet
                    :artifact/components
                    {:derived-view {:path derived-view-path
                                    :regime :derived-view
                                    :freshness freshness}
                     :declaration {:path (declaration-path (:task/id view))
                                   :regime :declaration
                                   :status (cond
                                             declaration* :attached
                                             declaration :orphaned
                                             :else :pending)}
                     :run-result {:path (run-result-path (:task/id view))
                                  :regime :transient-observation
                                  :lifecycle :evidence-run-result
                                  :status (cond
                                            run-result* :recorded
                                            run-result :stale
                                            :else :pending)}
                     :closed-record {:path (str ".llm/evidence/closed/" (:task/id view) ".edn")
                                     :regime :immutable-record
                                     :status :pending}}
                    :llm-declared declared
                    :evidence evidence)
       (:intent declaration*)
       (assoc :intent (:intent declaration*))))))

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
        fingerprint (change-fingerprint opts files)
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
        evidence-scope {:paths files
                        :bricks bricks
                        :requirements requirements
                        :public-boundaries public-boundaries}
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
     :change/fingerprint fingerprint
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
     :evidence (evidence-placeholders required-evidence evidence-scope)
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

(defn- delete-file-if-exists! [path]
  (let [f (io/file path)]
    (when (.isFile f)
      (.delete f))))

(defn- delete-generated-view-files! [task-id]
  (doseq [path [(view-path task-id)
                (view-markdown-path task-id)]]
    (delete-file-if-exists! path)))

(defn- task-path
  ([dir task-id]
   (task-path dir task-id ".edn"))
  ([dir task-id suffix]
   (str dir "/" task-id suffix)))

(defn- view-path [task-id]
  (task-path work-view-dir task-id))

(defn- view-markdown-path [task-id]
  (task-path work-view-dir task-id ".md"))

(defn- predicted-view-path [task-id]
  (task-path work-view-dir task-id ".predict.edn"))

(defn- predicted-view-markdown-path [task-id]
  (task-path work-view-dir task-id ".predict.md"))

(defn- declaration-path [task-id]
  (task-path work-declaration-dir task-id))

(defn- run-result-path [task-id]
  (task-path work-run-dir task-id))

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
       "## Change Fingerprint\n\n"
       "- source: `" (name (get-in packet [:change/fingerprint :source])) "`\n"
       "- digest: `" (get-in packet [:change/fingerprint :digest]) "`\n"
       "- content-digest: `" (get-in packet [:change/fingerprint :content-digest]) "`\n"
       "- paths: " (count (get-in packet [:change/fingerprint :paths])) "\n\n"
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
  (let [packet (assemble-packet (view-artifact (derive-packet opts)))]
    (if-let [out (:out opts)]
      (do
        (write-edn! out packet)
        (println out))
      (print-edn packet))))

(defn propose [opts]
  (let [packet (derive-packet opts)
        view (view-artifact packet)
        assembled (assemble-packet view)
        out-dir (or (:out-dir opts) default-out-dir)
        _ (when (not= out-dir default-out-dir)
            (throw (ex-info "--out-dir is no longer supported; Structural Evidence work artifacts use regime-specific directories."
                            {:out-dir out-dir})))
        task-id (:task/id view)
        edn-path (view-path task-id)
        md-path (view-markdown-path task-id)
        existed? (file? edn-path)
        pruned (prune-stale-generated-views! view {:keep-task-id task-id})]
    (write-edn! edn-path view)
    (write-markdown! md-path assembled)
    (println "Review Fatigue Packet generated:")
    (println " " edn-path)
    (println " " md-path)
    (when (seq pruned)
      (println "Pruned stale generated views:")
      (doseq [{:keys [edn md]} pruned]
        (println " " edn)
        (println " " md)))
    (when existed?
      (println "Existing derived view was replaced; declaration and run artifacts were preserved separately."))))

(defn inspect [opts]
  (let [packet (if-let [path (or (:from opts) (:out opts))]
	                 (read-packet-file path)
                 (assemble-packet (view-artifact (derive-packet opts))))]
    (println "Structural Evidence Derivation")
    (println "Task:" (:task/id packet))
    (println "Status:" (get-in packet [:derivation :status]))
    (println)
    (doseq [{:keys [path rule archetype plane component base project public-boundaries severity]} (:derivation/audit packet)]
      (println "Touched path:" path)
      (println "  Matched rule:" rule)
      (println "  Matched by:" (rule-match-explanation rule))
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
        (println "Usage: structural-evidence check-residual --packet .llm/work/views/<task-id>.edn"))
      (System/exit 2))
    (let [packet (read-packet-file path)
          _ (when-not packet
              (binding [*out* *err*]
                (println "Cannot read Structural Evidence artifact:" path))
              (System/exit 2))
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
  (let [dir (io/file work-view-dir)]
    (if (.isDirectory dir)
      (->> (file-seq dir)
           (filter #(.isFile %))
           (map #(.getPath %))
           (filter #(str/ends-with? % ".edn"))
           (remove #(str/includes? % ".predict.edn"))
           (filter (fn [path]
                     (let [status (:status (edn/read-string (slurp path)))]
                       (not (contains? #{:clean-close :closed} status)))))
           sort
           vec)
      [])))

(defn- generated-view-with-human-work? [task-id]
  (or (file? (declaration-path task-id))
      (file? (run-result-path task-id))))

(defn- stale-generated-view-for? [current-fingerprint packet]
  (or (not (fingerprint-matches? packet current-fingerprint))
      (not (fresh-derived-view? packet))))

(defn- prune-stale-generated-views!
  ([current-packet]
   (prune-stale-generated-views! current-packet {}))
  ([current-packet {:keys [keep-task-id]}]
   (let [current-fingerprint (:change/fingerprint current-packet)]
     (->> (active-packet-files)
          (keep (fn [path]
                  (when-let [packet (read-packet-file path)]
                    (let [task-id (current-task-id packet)]
                      (when (and (not= keep-task-id task-id)
                                 (stale-generated-view-for? current-fingerprint packet)
                                 (not (generated-view-with-human-work? task-id)))
                        (delete-generated-view-files! task-id)
                        {:task-id task-id
                         :edn path
                         :md (view-markdown-path task-id)})))))
          vec))))

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
    (let [artifact (read-artifact path)]
      (case (:kind artifact)
        :review-fatigue-derived-view
        (assemble-packet artifact)

        :structural-evidence-declaration
        artifact

        :structural-evidence-run-result
        artifact

        :structural-evidence-closed-record
        artifact

        :review-fatigue-packet
        artifact

        artifact))
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
  (let [packet (read-packet-file path)
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
  (let [terms* (->> terms
                    (filter context-search-term?)
                    (map normalize-token))
        values (map normalize-token (scalars packet))]
    (boolean
     (some (fn [term]
             (some #(or (= term %)
                        (str/includes? % term)
                        (str/includes? term %))
                   values))
           terms*))))

(defn- set-intersects? [xs ys]
  (boolean (seq (set/intersection (set xs) (set ys)))))

(def ^:private changed-paths-since
  (memoize
   (fn [rev include-working-tree?]
     (if (str/blank? (str rev))
       nil
       (set (concat (shell-lines "git" "diff" "--name-only" rev "HEAD")
                    (when include-working-tree?
                      (changed-files {}))))))))

(defn- invalidation-hit? [dep changed-paths current-packet]
  (case (:type dep)
    :path-changed
    (if changed-paths
      (set-intersects? (:paths dep) changed-paths)
      :unknown)

    :brick-changed
    (set-intersects? (:bricks dep)
                     (map name (get-in current-packet [:actual-scope :bricks])))

    :requirement-changed
    (set-intersects? (:requirements dep)
                     (get-in current-packet [:actual-scope :requirements]))

    :public-boundary-changed
    (set-intersects? (:public-boundaries dep)
                     (get-in current-packet [:actual-scope :public-boundaries]))

    false))

(defn- evidence-staleness [entry record current-packet include-working-tree?]
  (let [deps (:invalidated-by entry)
        rev (or (:closed-git-rev record) (:repo-rev entry))]
    (cond
      (empty? deps) {:status :unknown
                     :reason "no invalidated-by dependencies recorded"}
      (not include-working-tree?) {:status :valid
                                   :reason "same change content; current diff is not an invalidating follow-up change"}
      :else (let [changed-paths (changed-paths-since rev include-working-tree?)
                  hits (map #(invalidation-hit? % changed-paths current-packet) deps)]
              (cond
                (some true? hits) {:status :stale-candidate
                                   :reason "a dependency changed after the evidence was recorded"}
                (some #{:unknown} hits) {:status :unknown
                                         :reason "missing close revision for dependency comparison"}
                :else {:status :valid
                       :reason "no invalidating change detected"})))))

(defn- record-staleness [record current-packet]
  (let [same-fingerprint? (same-fingerprint-content? record current-packet)
        include-working-tree? (not same-fingerprint?)
        checks (mapv #(assoc (evidence-staleness % record current-packet include-working-tree?)
                             :id (:id %))
                     (:evidence record))
        statuses (set (map :status checks))]
    {:status (cond
               (contains? statuses :stale-candidate) :stale-candidate
               (contains? statuses :unknown) :unknown
               :else :valid)
     :checks checks}))

(defn- closed-record-staleness [current-packet]
  (->> (closed-records)
       (map (fn [record]
              (assoc (record-staleness record current-packet)
                     :task/id (:task/id record)
                     :record/path (:record/path record)
                     :closed-at (:closed-at record))))
       vec))

(defn- interface-entry? [{:keys [path]}]
  (and path (str/ends-with? path "interface.clj") (file? path)))

(defn- public-defn-name [boundary]
  (last (str/split (str boundary) #"/")))

(defn- missing-malli-contracts [entries]
  (->> entries
       (filter interface-entry?)
       (mapcat (fn [{:keys [path public-boundaries]}]
                 (let [text (slurp path)]
                   (for [boundary public-boundaries
                         :let [defn-name (public-defn-name boundary)]
                         :when (not (re-find (re-pattern (str "\\(m/=>\\s+"
                                                               (java.util.regex.Pattern/quote defn-name)
                                                               "\\b"))
                                             text))]
                     boundary))))
       distinct
       sort
       vec))

(defn- scope-gap-items [packet]
  (let [coverage-gaps (get-in packet [:human-attention :coverage-gaps])
        public-boundaries (get-in packet [:actual-scope :public-boundaries])
        trace-available? (get-in packet [:trace-context :available])
        trace-records (get-in packet [:trace-context :matched-records])
        missing-contracts (missing-malli-contracts (:derivation/audit packet))
        open-q (get-in packet [:cross-document-context :related-open-questions])
        knowledge (get-in packet [:cross-document-context :related-knowledge])]
    (vec
     (concat
      (for [[k items] coverage-gaps
            :when (seq items)]
        {:type k :severity :warn :count (count items)})
      (when (and (seq public-boundaries)
                 (not trace-available?))
        [{:type :trace-index-missing
          :severity :warn
          :items ["run ./.llm/scripts/gen-trace-index.sh"]}])
      (when (and (seq public-boundaries)
                 trace-available?
                 (zero? trace-records))
        [{:type :public-boundary-without-trace
          :severity :warn
          :items public-boundaries}])
      (when (seq missing-contracts)
        [{:type :public-boundary-without-malli-contract
          :severity :warn
          :items missing-contracts}])
      (when (seq open-q)
        [{:type :open-questions-in-scope
          :severity :info
          :count (count open-q)}])
      (when (seq knowledge)
        [{:type :knowledge-in-scope
          :severity :info
          :count (count knowledge)}])))))

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
    (println "  Archetype breakdown:")
    (let [by-archetype (group-by :archetype (:derivation/audit packet))]
      (if (seq by-archetype)
        (doseq [[archetype entries] (sort-by (comp name key) by-archetype)]
          (println "   -" archetype "->" (str/join ", " (map :path entries))))
        (println "   - none")))
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
    (println "  Active Review Fatigue Views:")
    (if (seq active)
      (doseq [path active] (println " " (status-line-for-packet path)))
      (println "  - none"))
    (println "  Closed records:" (count closed))
    (let [staleness (closed-record-staleness packet)
          stale-count (count (filter #(= :stale-candidate (:status %)) staleness))
          unknown-count (count (filter #(= :unknown (:status %)) staleness))]
      (println "  Stale candidates:" stale-count)
      (println "  Unknown freshness:" unknown-count))
    (println)
    (println "Trust Diff / Gaps:")
    (let [gaps (scope-gap-items packet)]
      (if (seq gaps)
        (doseq [{:keys [type severity count items]} gaps]
          (println " -" type "[" (name severity) "]"
                   (or count (count items))
                   (when (seq items)
                     (str " " (str/join ", " (take 5 items))))))
        (println " - none in current derived scope")))))

(defn- record-summary-line [packet]
  (str "- " (:task/id packet)
       " (" (name (or (:status packet) :unknown-status))
       ", " (name (or (:save-policy packet) :unknown-save))
       ", paths=" (count (get-in packet [:actual-scope :paths]))
       ", evidence=" (count (:evidence packet))
       ")"
       (when-let [closed-at (:closed-at packet)]
         (str " closed-at=" closed-at))
       (when-let [rev (:closed-git-rev packet)]
         (str " closed-rev=" (subs rev 0 (min 8 (count rev)))))))

(defn search-records [opts]
  (let [terms (vec (or (:scope-terms opts) (:extra-args opts)))
        records (closed-records)
        current-packet (derive-packet opts)
        matches (if (seq terms)
                  (filter #(record-matches-terms? % terms) records)
                  records)]
    (println "== Evidence Record Search ==")
    (println "Scope terms:" (if (seq terms) (str/join ", " terms) "(all)"))
    (println)
    (if (seq matches)
      (doseq [packet matches]
        (let [staleness (record-staleness packet current-packet)]
          (println (record-summary-line packet)
                   "staleness=" (name (:status staleness))))
        (when-let [override (get-in packet [:llm-declared :override])]
          (when (declared-value? override)
            (println "  override:" (pr-str override))))
        (let [must-review (take 3 (get-in packet [:human-attention :must-review]))]
          (when (seq must-review)
            (println "  must-review:" (str/join ", " must-review)))))
      (println "- none"))))

(defn- evidence-packet-files []
  (let [work-dir (io/file work-view-dir)
        declaration-dir (io/file work-declaration-dir)]
    (vec
     (sort
      (concat
       (when (.isDirectory work-dir)
         (->> (file-seq work-dir)
              (filter #(.isFile %))
              (map #(.getPath %))
              (filter #(str/ends-with? % ".edn"))
              (remove #(str/includes? % ".predict.edn"))))
       (when (.isDirectory declaration-dir)
         (->> (file-seq declaration-dir)
              (filter #(.isFile %))
              (map #(.getPath %))
              (filter #(str/ends-with? % ".edn"))))
       (closed-record-files))))))

(defn- string-values [x]
  (->> (tree-seq coll? seq x)
       (filter string?)))

(def id-reference-pattern
  #"\b(?:REQ|AC|UC|TO)-[0-9A-Za-z_-]+\b")

(defn- known-design-ids []
  (let [design-ir (read-edn-if-exists ".llm/data/design-ir.edn")]
    (set (mapcat #(re-seq id-reference-pattern %)
                 (string-values design-ir)))))

(def decision-definition-pattern
  #"(?i)\b(?:decision|status)\s*:\s*(?:accepted|rejected|superseded)\b")

(def knowledge-definition-pattern
  #"(?m)\bK-[0-9A-Za-z_-]+\s*:")

(defn- llm-written-packet-fields [packet]
  (select-keys packet [:intent :llm-declared :predict-vs-actual]))

(defn- packet-boundary-violations [path]
  (when-let [packet (read-packet-file path)]
    (let [declared-text (str/join "\n" (string-values (llm-written-packet-fields packet)))
          known-ids (known-design-ids)
          mentioned-ids (set (mapcat #(re-seq id-reference-pattern %) (string-values (llm-written-packet-fields packet))))
          unknown-ids (sort (set/difference mentioned-ids known-ids))]
      (vec
       (concat
        (when (seq unknown-ids)
          [{:file path
            :type :unknown-requirement-id-in-packet
            :message (str "packet LLM-written fields mention IDs not present in design-ir: "
                          (str/join ", " unknown-ids)
                          "; define them in DESIGN or move uncertainty to QUESTIONS")}])
        (when (re-find decision-definition-pattern declared-text)
          [{:file path
            :type :decision-finalized-in-packet
            :message "packet residual text appears to finalize a decision; move it to ADR or maintainer archive"}])
        (when (re-find knowledge-definition-pattern declared-text)
          [{:file path
            :type :knowledge-defined-in-packet
            :message "packet residual text appears to define knowledge; move it to KNOWLEDGE or maintainer archive"}]))))))

(defn check-boundary [opts]
  (let [violations (->> (evidence-packet-files)
                        (mapcat packet-boundary-violations)
                        vec)]
    (if (seq violations)
      (do
        (println "Evidence boundary violations:")
        (doseq [{:keys [file type message]} violations]
          (println "-" file type)
          (println " " message))
	        (System/exit 1))
	      (println "evidence boundary: OK"))))

(defn- minimal-query-packet [_terms]
  (let [files (changed-files {})]
    {:change/fingerprint (change-fingerprint {} files)
     :actual-scope {:paths files
                    :bricks []
                    :requirements []
                    :public-boundaries []
                    :archetypes []}}))

(defn- evidence-related-to-term? [entry term trace-tests]
  (let [term* (normalize-token term)
        values (map normalize-token (scalars (:invalidated-by entry)))
        tests (map normalize-token trace-tests)]
    (or (some #(or (= term* %)
                   (str/includes? % term*)
                   (str/includes? term* %))
              values)
        (and (seq tests)
             (some #(some (fn [test] (str/includes? % test)) values)
                   tests)))))

(defn- staleness-reason [summary]
  (or (some (fn [{:keys [status reason]}]
              (when (= :stale-candidate status) reason))
            (:checks summary))
      (some (fn [{:keys [status reason]}]
              (when (= :unknown status) reason))
            (:checks summary))
      (some :reason (:checks summary))))

(defn- verification-context [term]
  (let [terms [term]
        design-ir (read-edn-if-exists ".llm/data/design-ir.edn")
        trace-index (read-edn-if-exists ".llm/data/trace-index.edn")
        design-maps (vec (maps-matching-tokens design-ir terms))
        trace-maps (vec (maps-matching-tokens trace-index terms))
        records (->> (closed-records)
                     (filter #(record-matches-terms? % terms))
                     vec)
        current-packet (minimal-query-packet terms)
        stale-by-task (into {}
                            (map (fn [record]
                                   [(:task/id record) (record-staleness record current-packet)])
                                 records))
        tests (values-for-keys trace-maps [:tests :test :test-var :test-vars :implementation-tests])
        requirements (values-for-keys trace-maps [:requirements :requirement :requirement-id :trace/requirements])
        boundaries (values-for-keys trace-maps [:public-boundaries :public-boundary :var :vars :implementation])
        passing-evidence (->> records
                              (mapcat :evidence)
                              (filter #(= :pass (:status %)))
                              (filter #(evidence-related-to-term? % term tests))
                              (map :id)
                              distinct
                              sort
                              vec)
        status (cond
                 (and (seq trace-maps) (seq tests) (seq passing-evidence)) :verified
                 (or (seq design-maps) (seq trace-maps) (seq records)) :partially-verified
                 :else :unverified)]
    {:term term
     :status status
     :design {:available (boolean design-ir)
              :matched-records (count design-maps)}
     :trace {:available (boolean trace-index)
             :matched-records (count trace-maps)
             :requirements requirements
             :public-boundaries boundaries
             :tests tests}
	     :evidence {:closed-records (mapv (fn [record]
	                                        (let [summary (get stale-by-task (:task/id record))]
	                                          {:task/id (:task/id record)
	                                           :status (:status record)
	                                           :staleness (:status summary)
	                                           :staleness-reason (staleness-reason summary)
	                                           :closed-at (:closed-at record)}))
	                                      records)
	                :passing-evidence passing-evidence}}))

(defn- verification-term [opts]
  (or (first (:extra-args opts))
      (first (:scope-terms opts))))

(defn run-is-verified [opts]
  (let [term (verification-term opts)]
    (when-not term
      (binding [*out* *err*]
        (println "Usage: structural-evidence is-verified REQ-ID|public-boundary"))
      (System/exit 2))
    (let [ctx (verification-context term)]
      (if (= :edn (:format opts))
        (print-edn {:verification ctx})
        (do
          (println "== Evidence Verification ==")
          (println "Term:" term)
          (println "Status:" (name (:status ctx)))
          (println "Design matches:" (get-in ctx [:design :matched-records]))
          (println "Trace matches:" (get-in ctx [:trace :matched-records]))
          (println "Requirements:" (str/join ", " (get-in ctx [:trace :requirements])))
          (println "Public boundaries:" (str/join ", " (get-in ctx [:trace :public-boundaries])))
          (println "Tests:" (str/join ", " (get-in ctx [:trace :tests])))
          (println "Passing evidence:" (str/join ", " (get-in ctx [:evidence :passing-evidence])))
          (println "Closed records:" (count (get-in ctx [:evidence :closed-records]))))))))

(defn run-why [opts]
  (let [term (verification-term opts)]
    (when-not term
      (binding [*out* *err*]
        (println "Usage: structural-evidence why REQ-ID|public-boundary|task-id"))
      (System/exit 2))
    (let [ctx (verification-context term)]
      (if (= :edn (:format opts))
        (print-edn {:why ctx})
        (do
          (println "== Evidence Why ==")
          (println "Claim:" term)
          (println "Overall:" (name (:status ctx)))
          (println)
          (println "Evidence chain:")
          (println "- Design records matched:" (get-in ctx [:design :matched-records]))
          (println "- Trace records matched:" (get-in ctx [:trace :matched-records]))
          (doseq [req (get-in ctx [:trace :requirements])]
            (println "  - requirement:" req))
          (doseq [boundary (get-in ctx [:trace :public-boundaries])]
            (println "  - public boundary:" boundary))
          (doseq [test (get-in ctx [:trace :tests])]
            (println "  - test:" test))
	          (doseq [record (get-in ctx [:evidence :closed-records])]
	            (println "  - closed record:" (:task/id record)
	                     "[" (name (or (:staleness record) :unknown)) "]"
	                     (when-let [reason (:staleness-reason record)]
	                       (str "- " reason))))
          (when-not (or (pos? (get-in ctx [:design :matched-records]))
                        (pos? (get-in ctx [:trace :matched-records]))
                        (seq (get-in ctx [:evidence :closed-records])))
            (println "- no supporting evidence found")))))))

(defn- none-declaration? [v]
  (or (= :none v)
      (and (string? v)
           (= "none" (str/lower-case (str/trim v))))))

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

(defn- fingerprint-matches? [packet fingerprint]
  (= (:digest (:change/fingerprint packet))
     (:digest fingerprint)))

(defn- fingerprint-content-matches? [packet fingerprint]
  (and (:change/fingerprint packet)
       fingerprint
       (= (fingerprint-content-digest (:change/fingerprint packet))
          (fingerprint-content-digest fingerprint))))

(defn- gate-task-id [fingerprint]
  (str (.toString (java.time.LocalDate/now))
       "-evidence-gate-"
       (subs (:digest fingerprint) 0 16)))

(defn- active-packets []
  (->> (active-packet-files)
       (keep read-packet-file)
       vec))

(defn- predict-packets []
  (let [dir (io/file work-view-dir)]
    (if (.isDirectory dir)
      (->> (file-seq dir)
           (filter #(.isFile %))
           (map #(.getPath %))
           (filter #(str/ends-with? % ".predict.edn"))
           (keep read-packet-file)
           vec)
      [])))

(defn- declaration-files []
  (let [dir (io/file work-declaration-dir)]
    (if (.isDirectory dir)
      (->> (file-seq dir)
           (filter #(.isFile %))
           (map #(.getPath %))
           (filter #(str/ends-with? % ".edn"))
           sort
           vec)
      [])))

(defn- declarations []
  (->> (declaration-files)
       (keep read-packet-file)
       vec))

(defn- task-id-from-work-file [path]
  (let [name (.getName (io/file path))]
    (some (fn [[pattern]]
            (second (re-matches pattern name)))
          [[#"(.+)\.predict\.edn"]
           [#"(.+)\.predict\.md"]
           [#"(.+)\.intent\.edn"]
           [#"(.+)\.edn"]
           [#"(.+)\.md"]])))

(defn- closed-record-exists? [task-id]
  (file? (str ".llm/evidence/closed/" task-id ".edn")))

(defn- legacy-root-work-files []
  (let [dir (io/file default-out-dir)]
    (if (.isDirectory dir)
      (->> (.listFiles dir)
           (filter #(.isFile %))
           (map #(.getPath %))
           (remove #(= ".llm/work/.gitignore" %))
           sort
           vec)
      [])))

(defn- legacy-human-declaration? [path artifact]
  (or (str/ends-with? path ".intent.edn")
      (declared-value? (:intent artifact))
      (some declared-value?
            (vals (select-keys (:llm-declared artifact) residual-fields)))))

(defn- prune-decision [path]
  (let [task-id (task-id-from-work-file path)
        artifact (when (and (str/ends-with? path ".edn")
                            (file? path))
                   (read-edn-if-exists path))
        current-regime (cond
                         (str/starts-with? path work-declaration-dir) :declaration
                         (str/starts-with? path work-run-dir) :transient-observation
                         (str/starts-with? path work-view-dir) :derived-view
                         :else :legacy-root)
        closed? (and task-id (closed-record-exists? task-id))]
    (cond
      (nil? task-id)
      {:path path :action :preserve :reason :unknown-work-artifact}

      closed?
      {:path path :action :prune :reason :absorbed-by-closed-record}

      (= :declaration current-regime)
      {:path path :action :preserve :reason :human-declaration}

      (= :transient-observation current-regime)
      {:path path :action :preserve :reason :unclosed-transient-observation}

      (= :derived-view current-regime)
      (if (generated-view-with-human-work? task-id)
        {:path path :action :preserve :reason :derived-view-has-human-work}
        {:path path :action :prune :reason :stale-generated-view})

      (legacy-human-declaration? path artifact)
      {:path path :action :preserve :reason :legacy-human-declaration}

      :else
      {:path path :action :prune :reason :legacy-generated-view})))

(defn- current-work-files []
  (let [dirs [work-view-dir work-declaration-dir work-run-dir]]
    (->> dirs
         (map io/file)
         (filter #(.isDirectory %))
         (mapcat file-seq)
         (filter #(.isFile %))
         (map #(.getPath %))
         sort
         vec)))

(defn- prune-work-decisions []
  (->> (concat (legacy-root-work-files)
               (current-work-files))
       distinct
       sort
       (mapv prune-decision)))

(defn prune-work-artifacts [opts]
  (let [decisions (prune-work-decisions)
        prunable (filter #(= :prune (:action %)) decisions)
        preserved (filter #(= :preserve (:action %)) decisions)
        confirm? (:confirm opts)]
    (println "== Structural Evidence Work Prune ==")
    (println "Mode:" (if confirm? "confirm" "dry-run"))
    (println)
    (println "Prunable generated/transient artifacts:" (count prunable))
    (doseq [{:keys [path reason]} prunable]
      (println " -" (if confirm? "deleted" "would-delete") path "[" (name reason) "]")
      (when confirm?
        (delete-file-if-exists! path)))
    (println)
    (println "Preserved human/unknown artifacts:" (count preserved))
    (doseq [{:keys [path reason]} preserved]
      (println " - keep" path "[" (name reason) "]"))
    (when-not confirm?
      (println)
      (println "Run with --confirm to delete only the prunable artifacts listed above."))))

(defn- clean-close? [packet]
  (contains? #{:clean-close :closed} (:status packet)))

(defn- matching-packets [packets fingerprint]
  (filter #(fingerprint-matches? % fingerprint) packets))

(defn- matching-packets-by-content [packets fingerprint]
  (filter #(fingerprint-content-matches? % fingerprint) packets))

(defn- packet-failed-evidence [packet]
  (failed-evidence-statuses packet))

(defn- latest-clean-record [records]
  (->> records
       (filter clean-close?)
       (sort-by #(or (:closed-at %) ""))
       last))

(defn- diff-source-flag [packet]
  (case (get-in packet [:change/fingerprint :source])
    :staged-diff " --staged"
    ""))

(defn- action [command rationale]
  {:command command
   :rationale rationale})

(defn- block
  ([type] {:type type})
  ([type details] {:type type :details details}))

(defn- required-derived-view-plan []
  (when-let [status (->> (derivation/existing-artifacts)
                         (map derivation/freshness)
                         (remove #(= :fresh (:status %)))
                         first)]
    (let [state (if (= :broken-manifest (:status status))
                  :broken-derived-view-manifest
                  :stale-required-derived-view)
          cmd (or (:regenerate-command status)
                  "./.llm/scripts/check-derived-artifacts.sh")]
      {:state state
       :artifact-path (:artifact/path status)
       :blocked-on [(block (:status status)
                           (select-keys status
                                        [:artifact/path
                                         :reason
                                         :recorded-action-key
                                         :current-action-key]))]
       :next-action (action cmd
                            "a required derived view is not fresh; regenerate it before deriving evidence or planning work")})))

(def work-frontier-category-rank
  {:red 0
   :accounted 1
   :unknown 2})

(def work-frontier-state-rank
  {:missing-boundary 0
   :missing-trace 1
   :missing-test 2
   :orphan-boundary 3
   :orphan-test 4
   :unbacked-disposition 5
   :unresolved-blocker 6
   :stale-evidence 7
   :blocked-by-question 8
   :manual-verification-required 9})

(def work-frontier-kind-rank
  {:requirement 0
   :use-case 1
   :test-obligation 2
   :constraint 3
   :orphan-requirement 4
   :orphan-use-case 5
   :orphan-test-obligation 6})

(defn- work-frontier-items []
  (when-let [index (read-edn-if-exists ".llm/data/obligation-index.edn")]
    (->> (:obligations index)
         (remove #(= :complete (:category %)))
         (sort-by (juxt #(if (seq (get-in % [:frontier :blocked-by])) 1 0)
                        #(get work-frontier-category-rank (:category %) 99)
                        #(get work-frontier-state-rank (:state %) 99)
                        #(get-in % [:frontier :depth] 0)
                        #(get work-frontier-kind-rank (:kind %) 99)
                        :id))
         vec)))

(defn- work-frontier-head-plan [stale-candidates housekeeping]
  (when-let [item (first (work-frontier-items))]
    {:state :work-frontier-head
     :obligation-id (:id item)
     :obligation {:id (:id item)
                  :kind (:kind item)
                  :state (:state item)
                  :category (:category item)
                  :source (:source item)
                  :frontier (:frontier item)}
     :blocked-on []
     :next-action (action "./.llm/scripts/derive-work-frontier.sh"
                          "no current diff is pending; inspect the next unfinished DESIGN obligation")
     :stale-candidates stale-candidates
     :housekeeping housekeeping}))

(defn- orphan-declaration-plan [_current-fingerprint]
  (when-let [declaration (->> (declarations)
                              (filter (fn [declaration]
                                        (let [task-id (:task/id declaration)
                                              active-view (read-artifact (view-path task-id))
                                              predicted-view (read-artifact (predicted-view-path task-id))
                                              view (or active-view predicted-view)]
                                          (or (nil? view)
                                              (not= (get-in declaration [:change/fingerprint :digest])
                                                    (get-in view [:change/fingerprint :digest]))))))
                              first)]
    (let [task-id (:task/id declaration)]
      {:state :orphan-declaration
       :task-id task-id
       :declaration-path (declaration-path task-id)
       :blocked-on [(block :orphan-declaration
                           {:fingerprint (get-in declaration [:change/fingerprint :digest])})]
       :next-action (action (str "./.llm/scripts/evidence.sh inspect --from "
                                 (or (when (file? (view-path task-id))
                                       (view-path task-id))
                                     (declaration-path task-id)))
                            "a human declaration is no longer attached to the current derived view; inspect it before pruning or reusing it")})))

(defn- format-blocked-on [items]
  (str/join ", "
            (map (fn [{:keys [type details]}]
                   (if (seq details)
                     (str (name type) "=" (pr-str details))
                     (name type)))
                 items)))

(defn- current-task-id [packet]
  (or (:task/id packet)
      (gate-task-id (:change/fingerprint packet))))

(defn- active-packet-plan [active stale-candidates]
  (let [task (current-task-id active)
        missing (missing-residual-fields active)
        failed (failed-evidence-statuses active)
        not-run (missing-evidence-statuses active)
        source-flag (diff-source-flag active)
        packet-path (view-path task)]
    (cond
      (seq missing)
      {:state :active-packet-pending-residual
       :task-id task
       :packet-path packet-path
       :blocked-on [(block :residual missing)]
       :next-action (action (str "./.llm/scripts/evidence.sh declare --task " task " --all-none")
                            "residual fields are still nil; use concrete field declarations when residual impact exists")
       :stale-candidates stale-candidates}

      (seq failed)
      {:state :active-packet-failed-evidence
       :task-id task
       :packet-path packet-path
       :blocked-on [(block :failed-evidence failed)]
       :next-action (action (str "fix failed evidence: " (str/join ", " failed))
                            "command-backed evidence failed")
       :stale-candidates stale-candidates}

      (seq not-run)
      {:state :active-packet-needs-evidence-run
       :task-id task
       :packet-path packet-path
       :blocked-on [(block :missing-evidence not-run)]
       :next-action (action (str "./.llm/scripts/evidence.sh run --task " task)
                            "some command-backed evidence has not been recorded")
       :stale-candidates stale-candidates}

      :else
      {:state :active-packet-ready-to-close
       :task-id task
       :packet-path packet-path
       :blocked-on []
       :next-action (action (str "./.llm/scripts/evidence.sh close --task " task source-flag)
                            "residual declarations and command-backed evidence are complete")
       :stale-candidates stale-candidates})))

(defn- housekeeping-item [packet]
  (let [task (current-task-id packet)]
    {:task-id task
     :packet-path (view-path task)
     :fingerprint (get-in packet [:change/fingerprint :digest])
     :freshness (get-in packet [:artifact/components :derived-view :freshness])
     :missing-residual (missing-residual-fields packet)
     :missing-evidence (missing-evidence-statuses packet)
     :failed-evidence (failed-evidence-statuses packet)}))

(defn- detached-active-packet-plan [active stale-candidates]
  (let [task (current-task-id active)
        packet-path (view-path task)]
    {:state :detached-active-packet-housekeeping
     :task-id task
     :packet-path packet-path
     :blocked-on [(block :detached-active-packet
                         {:fingerprint (get-in active [:change/fingerprint :digest])})]
     :next-action (action (str "./.llm/scripts/evidence.sh inspect --from " packet-path)
                          "active derived view does not match the current change fingerprint; inspect before declaring residuals or discarding generated work files")
     :housekeeping [(housekeeping-item active)]
     :stale-candidates stale-candidates}))

(defn- what-now-plan [opts]
  (if-let [derived-plan (required-derived-view-plan)]
    derived-plan
    (let [packet0 (derive-packet opts)
          task-id (current-task-id packet0)
          packet (assoc packet0 :task/id task-id)
          fingerprint (:change/fingerprint packet)
          files (get-in packet [:actual-scope :paths])
          save-policy (:save-policy packet)
          closed-matches (matching-packets-by-content (closed-records) fingerprint)
          active-all (active-packets)
          active-fresh (filter fresh-derived-view? active-all)
          active-stale (first (remove fresh-derived-view? active-all))
          active-matches (matching-packets active-fresh fingerprint)
          active-match (first active-matches)
          active-mismatch (first (remove #(fingerprint-matches? % fingerprint) active-fresh))
          housekeeping (not-empty (cond-> []
                                    (and active-mismatch (seq files))
                                    (conj (housekeeping-item active-mismatch))

                                    (and active-stale (seq files))
                                    (conj (housekeeping-item active-stale))))
          clean-record (latest-clean-record closed-matches)
          stale-summary (when clean-record (record-staleness clean-record packet))
          stale-candidates (->> (closed-record-staleness packet)
                                (filter #(= :stale-candidate (:status %)))
                                (take 5)
                                vec)
          orphan-declaration (orphan-declaration-plan fingerprint)
          source-flag (diff-source-flag packet)]
      (cond
        active-match
        (active-packet-plan active-match stale-candidates)

        (and (empty? files) active-mismatch)
        (detached-active-packet-plan active-mismatch stale-candidates)

        (and (empty? files) active-stale)
        (detached-active-packet-plan active-stale stale-candidates)

        orphan-declaration
        (assoc orphan-declaration :stale-candidates stale-candidates)

        (and clean-record (= :stale-candidate (:status stale-summary)))
        {:state :matching-close-record-stale-candidate
         :task-id (:task/id clean-record)
         :record-path (:record/path clean-record)
         :blocked-on [(block :stale-candidate (:checks stale-summary))]
         :next-action (action (str "./.llm/scripts/propose-review-packet.sh --task " task-id source-flag)
                              "a matching close record exists, but one or more evidence dependencies changed")
         :stale-candidates stale-candidates
         :housekeeping housekeeping}

        clean-record
        {:state :commit-ready
         :task-id (:task/id clean-record)
         :record-path (:record/path clean-record)
         :blocked-on []
         :next-action (action "git commit"
                              "matching clean close record exists for the current change fingerprint")
         :stale-candidates stale-candidates
         :housekeeping housekeeping}

        (empty? files)
        (or (work-frontier-head-plan stale-candidates housekeeping)
            {:state :no-change
             :blocked-on []
             :next-action (action "none"
                                  "no changed paths, active derived view, or unfinished obligation")
             :stale-candidates stale-candidates
             :housekeeping housekeeping})

        (not= :required save-policy)
        {:state :evidence-optional
         :blocked-on []
         :next-action (action "git commit"
                              "current change is not save-required by Structural Evidence policy")
         :stale-candidates stale-candidates
         :housekeeping housekeeping}

        :else
        {:state :packet-required
         :task-id task-id
         :blocked-on [(block :packet-required)]
         :next-action (action (str "./.llm/scripts/propose-review-packet.sh --task " task-id source-flag)
                              "save-required change has no active derived view or close record")
         :stale-candidates stale-candidates
         :housekeeping housekeeping}))))

(defn run-what-now [opts]
  (let [plan (what-now-plan opts)]
    (if (= :edn (:format opts))
      (print-edn {:what-now plan})
      (do
        (println "== Evidence What Now ==")
        (println "State:" (name (:state plan)))
        (when-let [task (:task-id plan)]
          (println "Task:" task))
        (when-let [packet-path (:packet-path plan)]
          (println "Packet:" packet-path))
        (when-let [record-path (:record-path plan)]
          (println "Record:" record-path))
        (when-let [artifact-path (:artifact-path plan)]
          (println "Artifact:" artifact-path))
        (when-let [declaration-path (:declaration-path plan)]
          (println "Declaration:" declaration-path))
        (when-let [obligation (:obligation plan)]
          (println "Obligation:" (:id obligation)
                   (str "(" (name (:category obligation)) ","
                        (name (:state obligation)) ","
                        (name (:kind obligation)) ")"))
          (when-let [frontier (:frontier obligation)]
            (when-let [requires (seq (:requires frontier))]
              (println "Requires:" (str/join ", " requires)))
            (when-let [blocked-by (seq (:blocked-by frontier))]
              (println "Blocked by:" (str/join ", " blocked-by)))
            (when-let [depth (:depth frontier)]
              (when (pos? depth)
                (println "Frontier depth:" depth)))))
        (println "Next:" (get-in plan [:next-action :command]))
        (println "Reason:" (get-in plan [:next-action :rationale]))
        (when (seq (:blocked-on plan))
          (println "Blocked on:" (format-blocked-on (:blocked-on plan))))
        (when (seq (:housekeeping plan))
          (println "Housekeeping:")
          (doseq [item (:housekeeping plan)]
            (println " -" (:task-id item)
                     (:packet-path item)
                     "(active derived view does not match current fingerprint)")))
        (when (seq (:stale-candidates plan))
          (println "Stale candidates:" (count (:stale-candidates plan)))
          (doseq [candidate (:stale-candidates plan)]
            (println " -" (:task/id candidate)
                     (str "(" (name (:status candidate)) ")"))))))))

(defn- gate-fail! [opts lines]
  (doseq [line lines]
    (println line))
  (if (:advisory opts)
    (println "Evidence gate advisory mode: not blocking.")
    (System/exit 1)))

(defn- gate-pass! [message]
  (println message))

(defn- write-gate-packet! [packet]
  (let [view (view-artifact packet)
        assembled (assemble-packet view)
        task-id (:task/id view)
        edn-path (view-path task-id)
        md-path (view-markdown-path task-id)]
    (write-edn! edn-path view)
    (write-markdown! md-path assembled)
    {:edn edn-path :md md-path}))

(defn run-gate [opts]
  (let [packet0 (derive-packet opts)
        fingerprint (:change/fingerprint packet0)
        task-id (or (:task/id opts) (gate-task-id fingerprint))
        packet (assoc packet0 :task/id task-id)
        save-policy (:save-policy packet)
        files (get-in packet [:actual-scope :paths])
        _ (when-not (:no-write opts)
            (prune-stale-generated-views! packet {:keep-task-id task-id}))
        closed-matches (matching-packets-by-content (closed-records) fingerprint)
        active-matches (matching-packets (filter fresh-derived-view? (active-packets)) fingerprint)
        predict-matches (matching-packets (filter fresh-derived-view? (predict-packets)) fingerprint)
        clean-record (->> closed-matches
                          (filter clean-close?)
                          (sort-by #(or (:closed-at %) ""))
                          last)
        active (first active-matches)]
    (println "== Structural Evidence Gate ==")
    (println "Source:" (name (get-in packet [:change/fingerprint :source])))
    (println "Fingerprint:" (:digest fingerprint))
    (println "Changed paths:" (count files))
    (println "Save policy:" (name save-policy))
    (cond
      (empty? files)
      (gate-pass! "Evidence gate: no changed paths; pass.")

      (not= :required save-policy)
      (gate-pass! "Evidence gate: save policy is not required; pass.")

      (= :cannot-derive (get-in packet [:derivation :status]))
      (gate-fail! opts
                  ["Evidence gate blocked: derivation failed."
                   "Run ./.llm/scripts/inspect-derivation.sh --staged for details."])

      clean-record
      (let [missing (missing-residual-fields clean-record)
            failed (packet-failed-evidence clean-record)
            staleness (record-staleness clean-record packet)
            stale-evidence (->> (:checks staleness)
                                (filter #(= :stale-candidate (:status %)))
                                (map :id)
                                vec)]
        (cond
          (seq missing)
          (gate-fail! opts
                      ["Evidence gate blocked: matching close record is incomplete."
                       (str "Missing residual fields: " (pr-str missing))])

          (seq failed)
          (gate-fail! opts
                      ["Evidence gate blocked: matching close record has failed evidence."
                       (str "Failed evidence: " (pr-str failed))])

          (seq stale-evidence)
          (gate-fail! opts
                      ["Evidence gate blocked: matching close record has stale evidence."
                       (str "Stale evidence: " (pr-str stale-evidence))
                       "Run evidence again or create a fresh packet for the current diff."])

          :else
          (gate-pass! (str "Evidence gate: matching close record found for task "
                           (:task/id clean-record)
                           "; pass."))))

      active
      (let [missing (missing-residual-fields active)
            failed (packet-failed-evidence active)]
        (gate-fail! opts
                    (concat
                     ["Evidence gate blocked: matching active derived view is not closed."
                      (str "Task: " (:task/id active))]
                     (when (seq missing)
                       [(str "Missing residual fields: " (pr-str missing))
                        (str "Declare residuals: ./.llm/scripts/evidence.sh declare --task "
                             (:task/id active)
                             " --all-none")])
                     (when (seq failed)
                       [(str "Failed evidence: " (pr-str failed))])
                     [(str "Run evidence, then close: ./.llm/scripts/evidence.sh run --task "
                           (:task/id active)
                           " && ./.llm/scripts/evidence.sh close --task "
                           (:task/id active))])))

      (seq predict-matches)
      (let [predicted (first predict-matches)]
        (gate-fail! opts
                    ["Evidence gate blocked: matching predicted view exists but no active view or closed record matches this diff."
                     (str "Task: " (:task/id predicted))
                     (str "Create packet: ./.llm/scripts/propose-review-packet.sh --task "
                          (:task/id predicted)
                          " --staged")
                     (str "Declare residuals: ./.llm/scripts/evidence.sh declare --task "
                          (:task/id predicted)
                          " --all-none")
                     (str "Record evidence: ./.llm/scripts/evidence.sh run --task "
                          (:task/id predicted))
                     (str "Close: ./.llm/scripts/evidence.sh close --task "
                          (:task/id predicted)
                          " --staged")]))

      :else
      (let [paths (when-not (:no-write opts)
                    (write-gate-packet! packet))]
        (gate-fail! opts
                    (concat
                     ["Evidence gate blocked: save-required change has no matching derived view or close record."
                      (str "Task: " task-id)]
                     (when paths
                       [(str "Active derived view created: " (:edn paths))
                        (str "Review view: " (:md paths))])
                     [(str "Declare residuals: ./.llm/scripts/evidence.sh declare --task "
                           task-id
                           " --all-none")
                      (str "Record evidence: ./.llm/scripts/evidence.sh run --task " task-id)
                      (str "Close: ./.llm/scripts/evidence.sh close --task " task-id)]))))))

(defn predict [opts]
  (let [task-id (or (:task/id opts)
                    (str (.toString (java.time.LocalDate/now)) "-evidence-task"))
        packet (assoc (derive-packet (assoc opts :task/id task-id :status :predicted))
                      :intent {:text (or (:intent opts) "")
                               :recorded-at (.toString (java.time.Instant/now))})
        view (view-artifact packet)
        declaration (declaration-artifact packet
                                          (:llm-declared packet)
                                          (:intent packet))
        assembled (assemble-packet view declaration nil)
        declaration-path* (declaration-path task-id)
        view-path* (predicted-view-path task-id)
        md-path (predicted-view-markdown-path task-id)]
    (write-edn! declaration-path* declaration)
    (write-edn! view-path* view)
    (write-markdown! md-path assembled)
    (println "Evidence prediction generated:")
    (println " " declaration-path*)
    (println " " view-path*)
    (println " " md-path)))

(defn declare-residual [opts]
  (let [task-id (or (:task/id opts) (first (:extra-args opts)))
        _ (when-not task-id
            (binding [*out* *err*]
              (println "Usage: structural-evidence declare --task TASK-ID [--all-none|--semantic-impact TEXT ...]"))
            (System/exit 2))
        view-path* (view-path task-id)
        predict-path (predicted-view-path task-id)
        view (or (read-artifact view-path*)
                 (read-artifact predict-path))
        _ (when-not view
            (binding [*out* *err*]
              (println "No Structural Evidence derived view found:" view-path*)
              (println "Run evidence predict or propose-review-packet.sh first."))
            (System/exit 2))
        _ (ensure-fresh-derived-view! view task-id)
        packet (assemble-packet view)
        updates (if (:all-none opts)
                  (zipmap residual-fields (repeat :none))
                  (update-vals (:declare opts) declaration-value))
        declaration (declaration-artifact packet
                                          (merge (:llm-declared packet) updates)
                                          (:intent packet))
        packet* (assemble-packet view declaration (run-result-for task-id))
        declaration-path* (declaration-path task-id)
        md-path (if (= :predicted (:status view))
                  (predicted-view-markdown-path task-id)
                  (view-markdown-path task-id))]
    (write-edn! declaration-path* declaration)
    (write-markdown! md-path packet*)
    (println "Residual declarations updated:")
    (println " " declaration-path*)
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
             :tool-version (tool-version)
             :env-hash (env-hash (stable-evidence-env))
             :tail "No command is defined for this evidence item.")
      (let [env (stable-evidence-env)
            started (now-ms)
            started-at (.toString (java.time.Instant/now))
            {:keys [exit out err]} (apply shell/sh
                                          (concat ["env" "-i"]
                                                  (map (fn [[k v]]
                                                         (env-assignment k v))
                                                       env)
                                                  ["bash" "-c" cmd]))
            output (str out (when (seq err) (str "\n" err)))]
        (assoc entry
               :status (if (zero? exit) :pass :fail)
               :exit exit
               :repo-rev (git-rev)
               :tool-version (tool-version)
               :env-hash (env-hash env)
               :started-at started-at
               :duration-ms (elapsed-ms started)
               :tail (when-not (zero? exit) (tail-lines output 40)))))))

(defn run-evidence [opts]
  (let [task-id (or (:task/id opts) (first (:extra-args opts)))
        _ (when-not task-id
            (binding [*out* *err*]
              (println "Usage: structural-evidence run --task TASK-ID"))
            (System/exit 2))
        view-path* (view-path task-id)
        _ (when-not (file? view-path*)
            (binding [*out* *err*]
              (println "No active derived view found:" view-path*)
              (println "Run propose-review-packet.sh first."))
            (System/exit 2))
        view (read-artifact view-path*)
        _ (ensure-fresh-derived-view! view task-id)
        packet (assemble-packet view)
        evidence* (mapv run-evidence-command (:evidence packet))
        run-result (run-result-artifact packet evidence*)
        packet* (assemble-packet view (declaration-for task-id) run-result)
        run-path (run-result-path task-id)
        md-path (view-markdown-path task-id)
        failed (->> evidence* (filter #(= :fail (:status %))) (map :id) vec)]
    (write-edn! run-path run-result)
    (write-markdown! md-path packet*)
    (println "Evidence command results recorded:")
    (println " " run-path)
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
        predicted-path (predicted-view-path task-id)
        active-path (view-path task-id)
        predicted-view (read-artifact predicted-path)
        active-view (read-artifact active-path)
        _ (when-not active-view
            (binding [*out* *err*]
              (println "No active derived view found:" active-path)
              (println "Run ./.llm/scripts/propose-review-packet.sh first."))
            (System/exit 2))
        _ (ensure-fresh-derived-view! active-view task-id)
        current-view (view-artifact (derive-packet (assoc opts :task/id task-id :status :active)))
        active-digest (get-in active-view [:change/fingerprint :digest])
        current-digest (get-in current-view [:change/fingerprint :digest])
        _ (when (not= active-digest current-digest)
            (binding [*out* *err*]
              (println "Active derived view is stale for the current diff.")
              (println "Active fingerprint:" active-digest)
              (println "Current fingerprint:" current-digest)
              (println "Regenerate the derived view before closing:")
              (println (str " ./.llm/scripts/propose-review-packet.sh --task " task-id (diff-source-flag current-view))))
            (System/exit 1))
        predicted (when predicted-view (assemble-packet predicted-view))
        actual (assemble-packet active-view)
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
        blocked? (or (seq missing) (seq evidence-missing) (seq evidence-failed))
        record (-> actual
                   (assoc :kind :structural-evidence-closed-record
                          :artifact/regime :immutable-record
                          :status (if blocked? :blocked-close :clean-close)
                          :closed-at (.toString (java.time.Instant/now))
                          :closed-git-rev (git-rev)
                          :predict-vs-actual diffs)
                   (assoc-in [:artifact/components :closed-record :status]
                             (if blocked? :pending :written)))]
    (println "== Evidence Close ==")
    (println "Task:" task-id)
    (println "Close mode:" (:status record))
    (when-not predicted-view
      (println "Warning: no predicted view found; close used actual scope only."))
    (when predicted
      (println "Predict vs actual:" (pr-str diffs)))
    (when override-required?
      (println "Scope expansion detected: :override must be a concrete justification, not :none."))
    (when (seq evidence-failed)
      (println "Failed evidence:" (pr-str evidence-failed)))
    (when (seq evidence-missing)
      (println "Evidence not yet recorded:" (pr-str evidence-missing)))
    (if blocked?
      (do
        (when (seq missing)
          (println "Blocked: residual fields are not declared:")
          (doseq [field missing] (println " -" field))
          (print-residual-actions task-id missing diffs))
        (when (seq evidence-failed)
          (println "Blocked: failed evidence must be fixed before close."))
        (when (seq evidence-missing)
          (println "Blocked: evidence must be recorded before close."))
        (write-markdown! (view-markdown-path task-id) record)
        (println "Active view markdown updated with blocked-close state:")
        (println " " (view-markdown-path task-id))
        (println "No closed record written.")
        (System/exit 1))
      (let [out (str ".llm/evidence/closed/" task-id ".edn")]
        (write-edn! out record)
        (doseq [path [active-path
                      (view-markdown-path task-id)
                      predicted-path
                      (predicted-view-markdown-path task-id)
                      (declaration-path task-id)
                      (run-result-path task-id)]]
          (delete-file-if-exists! path))
        (println "Closed evidence record written:" out)
        (println "Work artifacts removed:")
        (println " " active-path)
        (println " " (declaration-path task-id))
        (println " " (run-result-path task-id))))))

(defn- record-scope [record]
  {:paths (get-in record [:actual-scope :paths])
   :bricks (get-in record [:actual-scope :bricks])
   :requirements (get-in record [:actual-scope :requirements])
   :public-boundaries (get-in record [:actual-scope :public-boundaries])})

(defn- inferred-closed-git-rev [record]
  (or (:closed-git-rev record)
      (some :repo-rev (:evidence record))
      (get-in record [:baseline :git-rev])))

(defn- backfilled-record [record]
  (let [scope (record-scope record)
        evidence* (mapv (fn [entry]
                          (if (seq (:invalidated-by entry))
                            entry
                            (assoc entry :invalidated-by
                                   (evidence-invalidated-by entry scope))))
                        (:evidence record))]
    (cond-> (assoc record :evidence evidence*)
      (and (nil? (:closed-git-rev record))
           (inferred-closed-git-rev record))
      (assoc :closed-git-rev (inferred-closed-git-rev record)))))

(defn backfill-invalidated-by [opts]
  (let [changes (->> (closed-record-files)
                     (map (fn [path]
                            (let [record (read-packet-file path)
                                  updated (backfilled-record record)]
                              {:path path
                               :changed? (not= record updated)
                               :record updated})))
                     vec)]
    (doseq [{:keys [path changed? record]} changes]
      (when changed?
        (if (:dry-run opts)
          (println "would update" path)
          (do
            (write-edn! path record)
            (println "updated" path)))))
    (println "backfill-invalidated-by:"
             (count (filter :changed? changes))
             "of"
             (count changes)
             (if (:dry-run opts) "would update" "updated"))))

(defn run-stale [opts]
  (let [packet (derive-packet opts)
        stale (closed-record-staleness packet)]
    (println "== Evidence Stale Records ==")
    (if (seq stale)
      (doseq [{task-id :task/id path :record/path status :status checks :checks} stale
              :when (or (= :all (:format opts))
                        (not= :valid status))]
        (println "-" task-id "[" (name status) "]" path)
        (doseq [{id :id check-status :status reason :reason} checks
                :when (not= :valid check-status)]
          (println "  -" id "[" (name check-status) "]" reason)))
      (println "- none"))))

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
        project-design-view (view-artifact project-design)
        staged-design-record (update project-design
                                     :change/fingerprint
                                     merge
                                     {:source :staged-diff
                                      :base nil
                                      :head "HEAD"
                                      :digest "source-specific-staged"})
        range-design-current (update project-design
                                     :change/fingerprint
                                     merge
                                     {:source :git-diff
                                      :base "HEAD~1"
                                      :head "HEAD"
                                      :digest "source-specific-range"})
        legacy-staged-design-record (update staged-design-record
                                            :change/fingerprint
                                            dissoc
                                            :content-digest)
        legacy-range-design-current (update range-design-current
                                            :change/fingerprint
                                            dissoc
                                            :content-digest)
        backfilled (backfilled-record (assoc project-design
                                             :evidence
                                             (mapv #(assoc % :invalidated-by [])
                                                   (:evidence project-design))))
        no-deps-record (assoc project-design
                              :status :clean-close
                              :closed-git-rev "fixture-rev"
                              :evidence
                              (mapv #(assoc % :invalidated-by [])
                                    (:evidence project-design)))
        same-content-record (assoc project-design
                                   :status :clean-close
                                   :closed-git-rev "fixture-rev")
        closed-missing (assoc project-design :status :closed)
        closed-declared (assoc project-design
                               :status :closed
                               :llm-declared
                               (zipmap residual-fields (repeat :none)))
        packet-required-plan (with-redefs [repo-context (constantly {:repo-kind :project :adoption-mode :complete})
                                           git-rev (constantly "fixture-rev")
                                           default-branch (constantly "main")
                                           required-derived-view-plan (constantly nil)
                                           active-packets (constantly [])
                                           closed-records (constantly [])
                                           closed-record-staleness (constantly [])]
                               (what-now-plan {:changed-files ["DESIGN.md"]}))
        stale-active-packet (assoc project-interface :task/id "fixture-stale-active")
        current-diff-with-stale-active (with-redefs [repo-context (constantly {:repo-kind :project :adoption-mode :complete})
                                                     git-rev (constantly "fixture-rev")
                                                     default-branch (constantly "main")
                                                     required-derived-view-plan (constantly nil)
                                                     active-packets (constantly [stale-active-packet])
                                                     closed-records (constantly [])
                                                     closed-record-staleness (constantly [])]
                                         (what-now-plan {:changed-files ["DESIGN.md"]}))
        idle-with-stale-active (with-redefs [repo-context (constantly {:repo-kind :project :adoption-mode :complete})
                                             git-rev (constantly "fixture-rev")
                                             default-branch (constantly "main")
                                             changed-files (constantly [])
                                             required-derived-view-plan (constantly nil)
                                             active-packets (constantly [stale-active-packet])
                                             closed-records (constantly [])
                                             closed-record-staleness (constantly [])]
                                 (what-now-plan {:changed-files []}))
        frontier-head-plan (with-redefs [repo-context (constantly {:repo-kind :project :adoption-mode :complete})
                                          git-rev (constantly "fixture-rev")
                                          default-branch (constantly "main")
                                          changed-files (constantly [])
                                          required-derived-view-plan (constantly nil)
                                          active-packets (constantly [])
                                          closed-records (constantly [])
                                          closed-record-staleness (constantly [])
                                          declarations (constantly [])
                                          read-edn-if-exists
                                          (fn [path]
                                            (when (= ".llm/data/obligation-index.edn" path)
                                              {:obligations [{:id "REQ-001"
                                                              :kind :requirement
                                                              :state :missing-boundary
                                                              :category :red
                                                              :source {:path "DESIGN.md"
                                                                       :line 12}}]}))]
                              (what-now-plan {:changed-files []}))
        tool-info (tool-version)
        boundary-file (java.io.File/createTempFile "structural-evidence-boundary" ".edn")
        boundary-path (.getPath boundary-file)]
    (spit boundary-file (pr-str {:task/id "fixture-boundary"
                                 :llm-declared {:semantic-impact-not-derived
                                                "REQ-STRUCTURAL-EVIDENCE-UNKNOWN: this must live in DESIGN, not a packet"}}))
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
    (assert! "string none is accepted as none declaration"
             (none-declaration? "none"))
    (assert! "residual declaration placeholders do not persist schema metadata"
             (not (contains? (:llm-declared project-design) :_required)))
    (assert! "gate task id uses 64-bit digest prefix"
             (re-find #"-evidence-gate-[0-9a-f]{16}$"
                      (gate-task-id (:change/fingerprint project-design))))
    (assert! "content digest matches staged close records to commit-range gates"
             (and (not (fingerprint-matches? staged-design-record
                                             (:change/fingerprint range-design-current)))
                  (fingerprint-content-matches? staged-design-record
                                                (:change/fingerprint range-design-current))))
    (assert! "content digest can be inferred for legacy closed records"
             (fingerprint-content-matches? legacy-staged-design-record
                                           (:change/fingerprint legacy-range-design-current)))
    (assert! "required evidence records invalidation dependencies"
             (every? #(seq (:invalidated-by %)) (:evidence project-design)))
	    (assert! "backfill restores invalidation dependencies"
	             (every? #(seq (:invalidated-by %)) (:evidence backfilled)))
	    (assert! "backfill infers closed git revision"
	             (= "fixture-rev" (:closed-git-rev backfilled)))
    (assert! "record without invalidation dependencies has unknown freshness"
             (= :unknown (:status (record-staleness no-deps-record project-design))))
    (assert! "matching content does not invalidate evidence with its own diff"
             (= :valid (:status (record-staleness same-content-record project-design))))
    (assert! "what-now packet-required blocked-on is structured"
             (= [{:type :packet-required}] (:blocked-on packet-required-plan)))
	    (assert! "what-now does not let stale active packet steal current diff"
	             (and (= :packet-required (:state current-diff-with-stale-active))
	                  (seq (:housekeeping current-diff-with-stale-active))))
	    (assert! "what-now surfaces stale active packet as idle housekeeping"
	             (and (= :detached-active-packet-housekeeping
	                     (:state idle-with-stale-active))
	                  (str/includes? (get-in idle-with-stale-active [:next-action :command])
	                                 " inspect ")))
    (assert! "what-now surfaces Work Frontier head when idle"
             (and (= :work-frontier-head (:state frontier-head-plan))
                  (= "REQ-001" (:obligation-id frontier-head-plan))))
    (assert! "legacy work artifact task id parsing preserves intent suffix"
             (= "fixture-task" (task-id-from-work-file ".llm/work/fixture-task.intent.edn")))
    (assert! "legacy intent artifacts are human declarations"
             (legacy-human-declaration? ".llm/work/fixture-task.intent.edn" {:text "intent"}))
	    (assert! "tool-version records both clj and bb runtime probes"
	             (and (contains? (:runtimes tool-info) :clj)
	                  (contains? (:runtimes tool-info) :bb)
	                  (boolean? (get-in tool-info [:runtimes :clj :available]))
	                  (boolean? (get-in tool-info [:runtimes :bb :available]))))
	    (assert! "boundary check catches unknown requirement IDs in LLM-written fields"
	             (seq (packet-boundary-violations boundary-path)))
    (assert! "Structural Evidence derived view carries a fresh action key"
             (fresh-derived-view? project-design-view))
    (assert! "Structural Evidence derived view without manifest is not fresh"
             (not (fresh-derived-view? (dissoc project-design-view derivation/manifest-key))))
    (.delete boundary-file)
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
        "what-now" (run-what-now opts)
        "is-verified" (run-is-verified opts)
	        "why" (run-why opts)
	        "stale" (run-stale opts)
	        "check-boundary" (check-boundary opts)
	        "backfill-invalidated-by" (backfill-invalidated-by opts)
        "prune-work" (prune-work-artifacts opts)
	        "gate" (run-gate opts)
        "predict" (predict opts)
        "declare" (declare-residual opts)
        "run" (run-evidence opts)
        "close" (close opts)
        "self-test" (self-test opts)
        (do
          (binding [*out* *err*]
            (println "Usage:")
            (println "  structural-evidence derive [--base BASE] [--head HEAD] [--out PATH] [--strict|--degraded] [--profile]")
            (println "  structural-evidence propose [--task-id ID] [--strict|--degraded]")
            (println "  structural-evidence inspect [--base BASE] [--head HEAD] [--from PATH]")
            (println "  structural-evidence check-residual --packet .llm/work/views/TASK-ID.edn")
            (println "  structural-evidence status [--scope TERM[,TERM...]] [--base BASE] [--head HEAD]")
            (println "  structural-evidence search [--scope TERM[,TERM...]]")
            (println "  structural-evidence what-now [--format edn]")
	            (println "  structural-evidence is-verified REQ-ID|public-boundary [--format edn]")
	            (println "  structural-evidence why REQ-ID|public-boundary|task-id [--format edn]")
	            (println "  structural-evidence stale [--format all]")
	            (println "  structural-evidence check-boundary")
	            (println "  structural-evidence backfill-invalidated-by [--dry-run]")
            (println "  structural-evidence prune-work [--confirm]")
            (println "  structural-evidence gate [--staged|--base BASE --head HEAD] [--advisory] [--no-write]")
            (println "  structural-evidence predict --task TASK-ID --intent TEXT [--changed-file PATH]")
            (println "  structural-evidence declare --task TASK-ID [--all-none|--semantic-impact TEXT ...]")
            (println "  structural-evidence run --task TASK-ID")
            (println "  structural-evidence close --task TASK-ID")
            (println "  structural-evidence self-test"))
          (System/exit 2))))
    (finally
      (shutdown-agents))))
