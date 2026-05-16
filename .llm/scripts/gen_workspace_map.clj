(ns gen-workspace-map
  (:refer-clojure :exclude [ensure])
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.pprint :as pprint]
   [clojure.string :as str]
   [derivation-manifest :as derivation]))

(def generator-path ".llm/scripts/gen_workspace_map.clj")
(def default-projects-file "docs/PROJECTS.md")
(def default-workspace-file "docs/WORKSPACE.md")
(def default-index-file ".llm/data/workspace-map.edn")
(def default-manifest-file ".llm/data/workspace-map.manifest.edn")

(def projects-header
  "<!-- GENERATED FILE. DO NOT EDIT BY HAND.

Sources:
- workspace.edn
- projects/*/project.edn
- projects/*/deps.edn
- bases/*/brick.edn
- components/*/brick.edn

Regenerate with:
  clj -Sdeps '{:paths [\".llm/scripts\"]}' -X gen-workspace-map/generate
-->

# Project Map

このファイルは自動生成物です。直接編集しないでください。
deploy / build 意図を変える場合は `projects/<name>/project.edn` を更新してから再生成します。
")

(def workspace-header
  "<!-- GENERATED FILE. DO NOT EDIT BY HAND.

Sources:
- workspace.edn
- deps.edn
- .llm/repo-context.edn
- projects/*/project.edn
- projects/*/deps.edn
- components/*/brick.edn
- bases/*/brick.edn

Regenerate with:
  clj -Sdeps '{:paths [\".llm/scripts\"]}' -X gen-workspace-map/generate
-->

# Workspace Map

このファイルは自動生成物です。直接編集しないでください。
workspace 全体の構造事実は Polylith / tools.deps に委譲し、この文書は設計意図 EDN と構成ファイルから生成される閲覧用ビューです。
")

(defn- file? [path]
  (.isFile (io/file path)))

(defn- directory? [path]
  (.isDirectory (io/file path)))

(defn- children [path]
  (->> (or (seq (.listFiles (io/file path))) [])
       (filter #(.isDirectory %))
       (sort-by #(.getName %))))

(defn- read-edn-file [path]
  (try
    (edn/read-string (slurp path))
    (catch Throwable e
      (throw (ex-info (str "Invalid EDN: " path " - " (.getMessage e))
                      {:path path})))))

(defn- write-file! [path content]
  (io/make-parents path)
  (spit path content))

(defn- error! [& messages]
  (throw (ex-info (str/join "\n" messages) {})))

(defn- warn! [& messages]
  (binding [*out* *err*]
    (println (str/join "\n" messages))))

(defn- workspace-map-manifest [projects-file workspace-file index-file]
  (assoc
   (derivation/make-manifest
    {:id :workspace-map
     :tool "gen-workspace-map"
     :output-path ".llm/data/workspace-map"
     :generator-path generator-path
     :tool-input-paths [".llm/scripts/derivation_manifest.clj"]
     :input-paths ["workspace.edn"
                   "deps.edn"
                   ".llm/repo-context.edn"
                   "projects"
                   "components"
                   "bases"]
     :input-policy {:missing :explicit-empty
                    :directory-roots :recursive-digest}
     :generated-at "deterministic"
     :regenerate-command "clj -Sdeps '{:paths [\".llm/scripts\"]}' -X gen-workspace-map/generate"})
   :derivation/outputs [projects-file workspace-file index-file]))

(defn- render-manifest [manifest]
  (str ";; GENERATED - do not edit by hand.\n"
       ";; Source of truth: workspace.edn, deps.edn, .llm/repo-context.edn, projects/*/project.edn, components/*/brick.edn, bases/*/brick.edn\n"
       ";; Regenerate with: clj -Sdeps '{:paths [\".llm/scripts\"]}' -X gen-workspace-map/generate\n"
       (with-out-str
         (pprint/pprint {derivation/manifest-key manifest}))))

(defn- repo-context []
  (when (file? ".llm/repo-context.edn")
    (read-edn-file ".llm/repo-context.edn")))

(defn- adoption-mode []
  (let [ctx (repo-context)]
    (or (:adoption-mode ctx)
        (if (= :template (:repo-kind ctx))
          :complete
          :retrofit))))

(defn- contains-todo? [x]
  (cond
    (string? x) (str/includes? x "TODO")
    (keyword? x) (= "TODO" (name x))
    (map? x) (some contains-todo? (concat (keys x) (vals x)))
    (coll? x) (some contains-todo? x)
    :else false))

(defn- keyword-coll? [x]
  (and (coll? x) (every? keyword? x)))

(defn- string-coll? [x]
  (and (coll? x) (every? string? x)))

(defn- nonblank-string? [x]
  (and (string? x) (not (str/blank? x))))

(defn- dirs [root]
  (when (directory? root)
    (map #(hash-map :path (.getPath %) :name (.getName %)) (children root))))

(defn- brick-dirs []
  (concat
   (map #(assoc % :kind :component) (or (dirs "components") []))
   (map #(assoc % :kind :base) (or (dirs "bases") []))))

(defn- project-dirs []
  (or (dirs "projects") []))

(defn- load-brick [{:keys [path kind]}]
  (let [edn-path (str path "/brick.edn")]
    (when (file? edn-path)
      (assoc (read-edn-file edn-path)
             :brick/path path
             :brick/type kind))))

(defn- bricks []
  (->> (brick-dirs)
       (keep load-brick)
       vec))

(defn- workspace-projects []
  (let [ws (when (file? "workspace.edn") (read-edn-file "workspace.edn"))]
    (-> ws :projects keys set)))

(defn- local-root-targets [deps-path]
  (if (file? deps-path)
    (let [deps (read-edn-file deps-path)]
      (->> (:deps deps)
           (keep (fn [[_ v]]
                   (when-let [root (:local/root v)]
                     root)))
           set))
    #{}))

(defn- external-deps [deps-path]
  (if (file? deps-path)
    (let [deps (read-edn-file deps-path)]
      (->> (:deps deps)
           (remove (fn [[_ v]] (:local/root v)))
           (map first)
           set))
    #{}))

(defn- target->include [target]
  (when-let [[_ root name] (re-find #"(?:^|.*/)(bases|components)/([^/]+)$" target)]
    (case root
      "bases" [:bases (keyword name)]
      "components" [:components (keyword name)])))

(defn- includes-from-deps [deps-path]
  (reduce (fn [acc target]
            (if-let [[k v] (target->include target)]
              (update acc k conj v)
              acc))
          {:bases #{} :components #{}}
          (local-root-targets deps-path)))

(defn- project-skeleton [project-path]
  (let [name (.getName (io/file project-path))
        deps-includes (includes-from-deps (str project-path "/deps.edn"))]
    {:project/name (keyword name)
     :project/type :TODO
     :project/purpose "TODO: describe this deploy/build unit"
     :project/entrypoints #{}
     :project/includes deps-includes
     :project/requirements []
     :project/build {:kind :TODO}}))

(defn- load-project [path]
  (let [edn-path (str path "/project.edn")]
    (when-not (file? edn-path)
      (error! (str "ERROR: " edn-path " is required for every project directory")))
    (assoc (read-edn-file edn-path)
           :project/path path
           :project/deps-includes (includes-from-deps (str path "/deps.edn"))
           :project/external-deps (external-deps (str path "/deps.edn")))))

(defn- missing-projects []
  (let [registered (disj (workspace-projects) "development")
        dirs-by-name (into {} (map (juxt :name :path) (project-dirs)))]
    (->> registered
         (map #(get dirs-by-name % (str "projects/" %)))
         (remove #(file? (str % "/project.edn")))
         vec)))

(defn- project-files []
  (->> (project-dirs)
       (map :path)
       (filter #(file? (str % "/project.edn")))))

(defn- validate-project! [p]
  (let [path (str (:project/path p) "/project.edn")]
    (when-not (keyword? (:project/name p))
      (error! (str "ERROR: " path " must have keyword :project/name")))
    (when-not (keyword? (:project/type p))
      (error! (str "ERROR: " path " must have keyword :project/type")))
    (when (and (not= :TODO (:project/type p))
               (not (contains? #{:app :library} (:project/type p))))
      (error! (str "ERROR: " path " :project/type must be one of #{:app :library}; use optional :project/runtime for service/worker/cli/batch/lambda details")))
    (when (and (contains? p :project/runtime)
               (not (keyword? (:project/runtime p))))
      (error! (str "ERROR: " path " must have keyword :project/runtime when present")))
    (when-not (nonblank-string? (:project/purpose p))
      (error! (str "ERROR: " path " must have non-empty :project/purpose")))
    (when-not (keyword-coll? (:project/entrypoints p))
      (error! (str "ERROR: " path " must have :project/entrypoints as a collection of keywords")))
    (when-not (string-coll? (:project/requirements p))
      (error! (str "ERROR: " path " must have :project/requirements as a collection of strings")))
    (when-not (map? (:project/includes p))
      (error! (str "ERROR: " path " must have :project/includes map")))
    (doseq [k [:bases :components]]
      (when-not (keyword-coll? (get-in p [:project/includes k] #{}))
        (error! (str "ERROR: " path " :project/includes " k " must be a collection of keywords"))))
    (when-not (map? (:project/build p))
      (error! (str "ERROR: " path " must have :project/build map")))
    (when (or (contains? p :project/provides)
              (contains? p :project/capabilities))
      (error! (str "ERROR: " path " must not own capabilities; capability ownership belongs to component brick.edn")))))

(def requirement-definition-pattern
  #"(?m)^[ \t]{0,3}(?:#{1,6}[ \t]+|[-*][ \t]+)([A-Z][A-Z0-9]+-[0-9]+)\b")

(defn- strip-fenced-code-blocks [text]
  (str/replace text #"(?s)```.*?```" ""))

(defn- design-requirement-ids []
  (if (file? "DESIGN.md")
    (->> (re-seq requirement-definition-pattern (strip-fenced-code-blocks (slurp "DESIGN.md")))
         (map second)
         set)
    #{}))

(defn- duplicate-design-requirement-ids []
  (if (file? "DESIGN.md")
    (->> (re-seq requirement-definition-pattern (strip-fenced-code-blocks (slurp "DESIGN.md")))
         (map second)
         frequencies
         (keep (fn [[id n]] (when (< 1 n) id)))
         sort)
    []))

(defn- validate-cross-project! [projects bricks]
  (let [base-names (set (map :brick/name (filter #(= :base (:brick/type %)) bricks)))
        component-names (set (map :brick/name (filter #(= :component (:brick/type %)) bricks)))
        entrypoints (set (keep :brick/entrypoint (filter #(= :base (:brick/type %)) bricks)))
        ws-projects (disj (workspace-projects) "development")
        project-names (set (map (comp name :project/name) projects))
        unregistered-projects (remove ws-projects project-names)
        missing-project-edn (remove project-names ws-projects)
        design-ids (design-requirement-ids)
        duplicate-design-ids (duplicate-design-requirement-ids)]
    (when (seq duplicate-design-ids)
      (error!
       "ERROR: DESIGN.md has duplicate requirement ids:"
       (str/join "\n" (map #(str "  " %) duplicate-design-ids))))
    (when (and (= :complete (adoption-mode))
               (seq unregistered-projects))
      (error! "ERROR: project.edn exists for projects not registered in workspace.edn:"
              (str/join "\n" (map #(str "  " %) unregistered-projects))))
    (when (and (= :complete (adoption-mode))
               (seq missing-project-edn))
      (error! "ERROR: workspace.edn registers projects without project.edn:"
              (str/join "\n" (map #(str "  " %) missing-project-edn))))
    (doseq [p projects]
      (doseq [b (get-in p [:project/includes :bases])
              :when (not (contains? base-names b))]
        (error! (str "ERROR: " (:project/path p) " includes missing base " b)))
      (doseq [c (get-in p [:project/includes :components])
              :when (not (contains? component-names c))]
        (error! (str "ERROR: " (:project/path p) " includes missing component " c)))
      (doseq [e (:project/entrypoints p)
              :when (and (not= e :TODO)
                         (not (contains? entrypoints e)))]
        (error! (str "ERROR: " (:project/path p) " entrypoint " e " is not provided by any base brick.edn")))
      (doseq [req (:project/requirements p)
              :when (and (seq design-ids)
                         (not (contains? design-ids req)))]
        (error! (str "ERROR: " (:project/path p) " references requirement id not found in DESIGN.md: " req)))
      (when (seq (:project/external-deps p))
        (error! (str "ERROR: " (:project/path p) "/deps.edn must contain :local/root deps only, found external deps: "
                     (str/join ", " (sort (:project/external-deps p))))))
      (let [declared (:project/includes p)
            deps (:project/deps-includes p)]
        (when (not= (update-vals declared set) (update-vals deps set))
          (let [message ["project.edn includes differ from project deps.edn :local/root entries:"
                         (str "  " (:project/path p))
                         (str "  project.edn: " (pr-str declared))
                         (str "  deps.edn:     " (pr-str deps))]]
            (if (= :complete (adoption-mode))
              (apply error! "ERROR:" message)
              (apply warn! "WARN:" message))))))
    (doseq [p unregistered-projects]
      (warn! (str "WARN: projects/" p " has project.edn but is not registered in workspace.edn :projects")))
    (doseq [p missing-project-edn]
      (warn! (str "WARN: workspace.edn registers project " p " but projects/" p "/project.edn is missing")))))

(defn- migration-warnings [projects]
  (concat
   (for [p projects :when (contains-todo? p)]
     (str "WARN: " (:project/path p) "/project.edn contains TODO placeholders"))
   (for [p projects :when (empty? (:project/entrypoints p))]
     (str "WARN: " (:project/path p) " has empty :project/entrypoints; fill deploy entrypoints before migration is complete"))
   (for [p projects
         :when (and (= :app (:project/type p))
                    (empty? (get-in p [:project/includes :bases])))]
     (str "WARN: " (:project/path p) " has :project/type :app but includes no base; use :project/type :library only for non-deployable bundle projects"))))

(defn- report-quality! [projects {:keys [strict?]}]
  (let [warnings (vec (migration-warnings projects))]
    (if (and strict? (seq warnings))
      (apply error!
             "ERROR: Project Map has unresolved migration-quality warnings in :adoption-mode :complete:"
             warnings)
      (doseq [warning warnings]
        (warn! warning)))))

(defn- normalize-project [p]
  (into (sorted-map)
        (keep (fn [k]
                (when (contains? p k)
                  [k (cond
                       (set? (get p k)) (into (sorted-set) (get p k))
                       (map? (get p k)) (update-vals (get p k) #(if (set? %) (into (sorted-set) %) %))
                       (coll? (get p k)) (vec (sort (get p k)))
                       :else (get p k))])))
        [:project/path
         :project/name
         :project/type
         :project/runtime
         :project/purpose
         :project/entrypoints
         :project/includes
         :project/requirements
         :project/build]))

(defn- normalize-brick [b]
  (into (sorted-map)
        (keep (fn [k]
                (when (contains? b k)
                  [k (cond
                       (set? (get b k)) (into (sorted-set) (get b k))
                       (coll? (get b k)) (vec (sort (get b k)))
                       :else (get b k))])))
        [:brick/path
         :brick/name
         :brick/type
         :brick/entrypoint
         :brick/provides
         :brick/uses
         :brick/requirements]))

(defn- render-index [projects bricks]
  (let [normalized-projects (mapv normalize-project (sort-by :project/path projects))
        normalized-bricks (mapv normalize-brick (sort-by :brick/path bricks))]
    (str ";; GENERATED - do not edit by hand.\n"
         ";; Source of truth: workspace.edn, deps.edn, .llm/repo-context.edn, projects/*/project.edn, components/*/brick.edn, bases/*/brick.edn\n"
         ";; Regenerate with: clj -Sdeps '{:paths [\".llm/scripts\"]}' -X gen-workspace-map/generate\n"
         (with-out-str
           (pprint/pprint
            {:workspace {:projects (into (sorted-set) (workspace-projects))
                         :adoption-mode (adoption-mode)}
             :projects normalized-projects
             :bricks normalized-bricks})))))

(defn- bullet-list [items empty-text]
  (if (seq items)
    (str/join "\n" (map #(str "- `" % "`") items))
    empty-text))

(defn- render-project [p]
  (str "\n## " (:project/path p) "\n\n"
       "- Name: `" (:project/name p) "`\n"
       "- Type: `" (:project/type p) "`\n"
       (when (:project/runtime p)
         (str "- Runtime: `" (:project/runtime p) "`\n"))
       "- Purpose: " (:project/purpose p) "\n"
       "- Entrypoints:\n" (bullet-list (:project/entrypoints p) "- none") "\n"
       "- Bases:\n" (bullet-list (get-in p [:project/includes :bases]) "- none") "\n"
       "- Components:\n" (bullet-list (get-in p [:project/includes :components]) "- none") "\n"
       "- Requirements:\n" (bullet-list (:project/requirements p) "- none") "\n"))

(defn- render-projects-doc [projects]
  (str projects-header
       (if (seq projects)
         (apply str (map render-project (sort-by :project/path projects)))
         "\nNo deploy projects are present yet.\n")))

(defn- render-workspace-doc [projects bricks]
  (str workspace-header
       "\n## Projects\n"
       (bullet-list (map :project/name projects) "- none")
       "\n\n## Bricks\n"
       (bullet-list (map :brick/name bricks) "- none")
       "\n\n## Generated Index\n"
       "- `.llm/data/workspace-map.edn`\n"))

(defn- load-projects []
  (mapv load-project (project-files)))

(defn generate
  [{:keys [projects-file workspace-file index-file manifest-file auto-create?]}]
  (let [projects-file (or projects-file default-projects-file)
        workspace-file (or workspace-file default-workspace-file)
        index-file (or index-file default-index-file)
        manifest-file (or manifest-file default-manifest-file)
        missing (missing-projects)]
    (when (and (seq missing) auto-create?)
      (doseq [path missing]
        (io/make-parents (str path "/project.edn"))
        (when-not (directory? path)
          (.mkdirs (io/file path)))
        (write-file! (str path "/project.edn")
                     (with-out-str (pprint/pprint (project-skeleton path))))
        (println "generated skeleton" (str path "/project.edn"))))
    (when (and (seq missing) (not auto-create?))
      (error! "ERROR: project.edn is missing. Generate skeletons first:"
              "  clj -Sdeps '{:paths [\".llm/scripts\"]}' -X gen-workspace-map/generate :auto-create? true"))
    (let [projects (load-projects)
          bricks (bricks)]
      (doseq [p projects] (validate-project! p))
      (validate-cross-project! projects bricks)
      (report-quality! projects {:strict? false})
      (write-file! projects-file (render-projects-doc projects))
      (write-file! workspace-file (render-workspace-doc projects bricks))
      (write-file! index-file (render-index projects bricks))
      (write-file! manifest-file
                   (render-manifest
                    (workspace-map-manifest projects-file workspace-file index-file)))
      (println "generated" projects-file)
      (println "generated" workspace-file)
      (println "generated" index-file)
      (println "generated" manifest-file))))

(defn ensure [_]
  (generate {:auto-create? true}))

(defn propose-missing [_]
  (let [missing (missing-projects)]
    (if (empty? missing)
      (println "No missing project.edn files.")
      (doseq [path missing]
        (println ";; ------------------------------------------------------------")
        (println ";; Proposal for" (str path "/project.edn"))
        (println ";; Review TODO fields before writing this file.")
        (pprint/pprint (project-skeleton path))))))

(defn check [_]
  (let [projects (load-projects)
        bricks (bricks)
        expected-projects (render-projects-doc projects)
        expected-workspace (render-workspace-doc projects bricks)
        expected-index (render-index projects bricks)
        expected-manifest (render-manifest
                           (workspace-map-manifest default-projects-file
                                                   default-workspace-file
                                                   default-index-file))
        outputs-exist? (or (file? default-projects-file)
                           (file? default-workspace-file)
                           (file? default-index-file)
                           (file? default-manifest-file))]
    (doseq [p projects] (validate-project! p))
    (validate-cross-project! projects bricks)
    (report-quality! projects {:strict? (= :complete (adoption-mode))})
    (doseq [path [default-projects-file default-workspace-file default-index-file default-manifest-file]]
      (when (and (or (seq projects) outputs-exist?) (not (file? path)))
        (error! (str "ERROR: " path " is missing. Run gen-workspace-map/generate."))))
    (if (or (and (empty? projects) (not outputs-exist?))
            (and (= expected-projects (slurp default-projects-file))
                 (= expected-workspace (slurp default-workspace-file))
                 (= expected-index (slurp default-index-file))
                 (= expected-manifest (slurp default-manifest-file))))
      (println "check-workspace-map: OK")
      (error! "ERROR: project/workspace generated docs are not synchronized."
              "Fix: clj -Sdeps '{:paths [\".llm/scripts\"]}' -X gen-workspace-map/generate"))))
