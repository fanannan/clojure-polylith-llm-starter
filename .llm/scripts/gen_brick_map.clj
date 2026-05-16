(ns gen-brick-map
  (:refer-clojure :exclude [ensure])
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.pprint :as pprint]
   [clojure.string :as str]
   [derivation-manifest :as derivation]))

(def generator-path ".llm/scripts/gen_brick_map.clj")
(def default-doc-file "docs/BRICKS.md")
(def default-index-file ".llm/data/brick-map.edn")
(def default-manifest-file ".llm/data/brick-map.manifest.edn")

(def generated-header
  "<!-- GENERATED FILE. DO NOT EDIT BY HAND.

Sources:
- DESIGN.md
- components/*/brick.edn
- bases/*/brick.edn
- components/*/src/**/interface.clj
- bases/*/src/**/interface.clj

Regenerate with:
  clj -Sdeps '{:paths [\".llm/scripts\"]}' -X gen-brick-map/generate
-->

# Brick Map

このファイルは自動生成物です。直接編集しないでください。
brick の責務や capability を変える場合は各 `brick.edn` を、公開 API を変える場合は各 `interface.clj` を更新してから再生成します。
")

(defn- directory? [path]
  (.isDirectory (io/file path)))

(defn- file? [path]
  (.isFile (io/file path)))

(defn- children [path]
  (->> (or (seq (.listFiles (io/file path))) [])
       (filter #(.isDirectory %))
       (sort-by #(.getName %))))

(defn- brick-dirs []
  (concat
   (when (directory? "components")
     (map #(hash-map :kind :component :path (.getPath %)) (children "components")))
   (when (directory? "bases")
     (map #(hash-map :kind :base :path (.getPath %)) (children "bases")))))

(defn- read-edn-file [path]
  (try
    (edn/read-string (slurp path))
    (catch Throwable e
      (throw (ex-info (str "Invalid EDN: " path " - " (.getMessage e))
                      {:path path})))))

(defn- repo-context []
  (when (file? ".llm/repo-context.edn")
    (read-edn-file ".llm/repo-context.edn")))

(defn- adoption-mode []
  (let [ctx (repo-context)]
    (or (:adoption-mode ctx)
        (if (= :template (:repo-kind ctx))
          :complete
          :retrofit))))

(defn- nonblank-string? [x]
  (and (string? x) (not (str/blank? x))))

(defn- keyword-coll? [x]
  (and (coll? x) (every? keyword? x)))

(defn- string-coll? [x]
  (and (coll? x) (every? string? x)))

(defn- error! [& messages]
  (throw (ex-info (str/join "\n" messages) {})))

(defn- warn! [& messages]
  (binding [*out* *err*]
    (println (str/join "\n" messages))))

(defn- brick-map-manifest [out-file index-file]
  (assoc
   (derivation/make-manifest
    {:id :brick-map
     :tool "gen-brick-map"
     :output-path ".llm/data/brick-map"
     :generator-path generator-path
     :tool-input-paths [".llm/scripts/derivation_manifest.clj"]
     :input-paths ["DESIGN.md"
                   ".llm/repo-context.edn"
                   "components"
                   "bases"]
     :input-policy {:missing :explicit-empty
                    :directory-roots :recursive-digest}
     :generated-at "deterministic"
     :regenerate-command "clj -Sdeps '{:paths [\".llm/scripts\"]}' -X gen-brick-map/generate"})
   :derivation/outputs [out-file index-file]))

(defn- render-manifest [manifest]
  (str ";; GENERATED - do not edit by hand.\n"
       ";; Source of truth: DESIGN.md, components/*/brick.edn, bases/*/brick.edn, interface.clj\n"
       ";; Regenerate with: clj -Sdeps '{:paths [\".llm/scripts\"]}' -X gen-brick-map/generate\n"
       (with-out-str
         (pprint/pprint {derivation/manifest-key manifest}))))

(defn- contains-todo? [x]
  (cond
    (string? x) (str/includes? x "TODO")
    (keyword? x) (= "TODO" (name x))
    (map? x) (some contains-todo? (concat (keys x) (vals x)))
    (coll? x) (some contains-todo? x)
    :else false))

(defn- todo-warnings [path data]
  (when (contains-todo? data)
    [(str "WARN: " path " contains TODO placeholders; review and replace them before migration is complete")]))

(def capability-pattern
  #"^[a-z][a-z0-9-]*/[a-z][a-z0-9-]*$")

(defn- valid-capability? [x]
  (and (keyword? x)
       (re-matches capability-pattern (subs (str x) 1))))

(defn- capability-op [capability]
  (some-> capability str (subs 1) (str/split #"/") second))

(defn- capability-domain [capability]
  (some-> capability str (subs 1) (str/split #"/") first))

(defn- todo-skeleton? [data]
  (contains-todo? data))

(defn- validate-component! [path data]
  (when-not (= :component (:brick/type data))
    (error! (str "ERROR: " path " must have :brick/type :component")))
  (when-not (keyword? (:brick/name data))
    (error! (str "ERROR: " path " must have keyword :brick/name")))
  (when (and (contains? data :brick/group)
             (not (keyword? (:brick/group data))))
    (error! (str "ERROR: " path " must have keyword :brick/group when present")))
  (when-not (nonblank-string? (:brick/purpose data))
    (error! (str "ERROR: " path " must have non-empty :brick/purpose")))
  (when-not (keyword-coll? (:brick/provides data))
    (error! (str "ERROR: " path " component must have :brick/provides as a collection of keywords")))
  (when (and (empty? (:brick/provides data))
             (not (todo-skeleton? data)))
    (error! (str "ERROR: " path " component must provide non-empty :brick/provides")))
  (when-not (string-coll? (:brick/requirements data))
    (error! (str "ERROR: " path " must have :brick/requirements as a collection of strings")))
  (when (and (contains? data :brick/not-for)
             (not (keyword-coll? (:brick/not-for data))))
    (error! (str "ERROR: " path " must have :brick/not-for as a collection of keywords")))
  (when (:brick/entrypoint data)
    (error! (str "ERROR: " path " component must not have :brick/entrypoint"))))

(defn- validate-base! [path data]
  (when-not (= :base (:brick/type data))
    (error! (str "ERROR: " path " must have :brick/type :base")))
  (when-not (keyword? (:brick/name data))
    (error! (str "ERROR: " path " must have keyword :brick/name")))
  (when (and (contains? data :brick/group)
             (not (keyword? (:brick/group data))))
    (error! (str "ERROR: " path " must have keyword :brick/group when present")))
  (when-not (nonblank-string? (:brick/purpose data))
    (error! (str "ERROR: " path " must have non-empty :brick/purpose")))
  (when-not (keyword? (:brick/entrypoint data))
    (error! (str "ERROR: " path " base must have keyword :brick/entrypoint")))
  (when (contains? data :brick/provides)
    (error! (str "ERROR: " path " base must not have :brick/provides; capabilities are owned by components")))
  (when-not (keyword-coll? (:brick/uses data))
    (error! (str "ERROR: " path " must have :brick/uses as a collection of keywords")))
  (when-not (string-coll? (:brick/requirements data))
    (error! (str "ERROR: " path " must have :brick/requirements as a collection of strings"))))

(defn- interface-files [brick-path]
  (->> (file-seq (io/file brick-path))
       (filter #(.isFile %))
       (filter #(= "interface.clj" (.getName %)))
       (sort-by #(.getPath %))))

(defn- public-api [brick-path]
  (->> (interface-files brick-path)
       (mapcat
        (fn [f]
          (let [text (slurp f)
                ns-name (second (re-find #"\(ns\s+([^\s\)]+)" text))]
            (for [[_ defn-name] (re-seq #"\(defn\s+([^\s\)]+)" text)]
              (if ns-name
                (str ns-name "/" defn-name)
                defn-name)))))
       sort
       vec))

(def allowed-generic-api-names
  #{"create"
    "validate"
    "parse"
    "format"
    "start"
    "stop"
    "init"
    "close"
    "health"
    "routes"
    "handler"})

(defn- api-name [qualified]
  (last (str/split qualified #"/")))

(defn- suspicious-generic-name? [qualified]
  (let [n (api-name qualified)]
    (and (contains? allowed-generic-api-names n)
         (not (str/includes? qualified ".interface/")))))

(defn- api-name-warnings [brick-path api]
  (let [generic (filter suspicious-generic-name? api)]
    (when (seq generic)
      [(str "WARN: " brick-path " has generic public API names outside a clear interface namespace: "
            (str/join ", " generic))])))

(defn- duplicate-public-api-name-warnings [bricks]
  (->> (filter #(= :component (:brick/type %)) bricks)
       (mapcat (fn [b]
                 (map #(vector (api-name %) b %)
                      (:brick/public-api b))))
       (group-by first)
       (keep (fn [[name entries]]
               (let [bricks-with-api (map second entries)
                     paths (set (map :brick/path bricks-with-api))
                     matching-caps (set (for [b bricks-with-api
                                              cap (:brick/provides b)
                                              :when (= name (capability-op cap))]
                                          cap))]
                 (when (and (< 1 (count paths))
                            (contains? allowed-generic-api-names name)
                            (not= (count paths) (count matching-caps)))
                   (str "WARN: public API function name `" name
                        "` appears in multiple bricks: "
                        (str/join ", " (sort paths))
                        ". Keep only if each brick has a clearly distinct :<domain>/"
                        name " capability in brick.edn.")))))))

(defn- public-api-name-set [brick]
  (set (map api-name (:brick/public-api brick))))

(defn- component-interface-warnings [brick]
  (when (and (= :component (:brick/type brick))
             (seq (:brick/provides brick))
             (empty? (:brick/public-api brick)))
    [(str "WARN: " (:brick/path brick)
          " provides capabilities but has no public API in interface.clj; create an interface function or remove the unimplemented capability")]))

(defn- component-capability-warnings [brick]
  (let [api-names (public-api-name-set brick)]
    (concat
     (for [cap (:brick/provides brick)
           :when (not (valid-capability? cap))]
       (str "WARN: " (:brick/path brick) " capability `" cap
            "` should use :<domain>/<operation> form"))
     (for [cap (:brick/provides brick)
           :let [op (capability-op cap)]
           :when (and op
                      (seq api-names)
                      (not (contains? api-names op)))]
       (str "WARN: " (:brick/path brick) " capability `" cap
            "` has no matching public function `" op
            "` in interface.clj; keep only if the capability is intentionally represented by a differently named API")))))

(defn- empty-provides-warnings [brick]
  (when (and (= :component (:brick/type brick))
             (empty? (:brick/provides brick)))
    [(str "WARN: " (:brick/path brick)
          " has empty :brick/provides; fill capability ownership before migration is complete")]))

(defn- group-capability-domain-warnings [bricks]
  (for [b (filter #(and (= :component (:brick/type %))
                        (:brick/group %)
                        (seq (:brick/provides %)))
                  bricks)
        :let [group-name (name (:brick/group b))
              capability-domains (set (keep capability-domain (:brick/provides b)))]
        :when (not (contains? capability-domains group-name))]
    (str "WARN: " (:brick/path b) " is in group `" (:brick/group b)
         "` but none of its capability domains match the group: "
         (str/join ", " (sort (map str (:brick/provides b))))
         ". Keep only if this group is an intentional navigation aid.")))

(defn- same-group-operation-warnings [bricks]
  (->> (filter #(and (= :component (:brick/type %)) (:brick/group %)) bricks)
       (mapcat (fn [b]
                 (for [cap (:brick/provides b)
                       :let [op (capability-op cap)]
                       :when op]
                   [[(:brick/group b) op] b cap])))
       (group-by first)
       (keep (fn [[[group op] entries]]
               (let [paths (set (map #(-> % second :brick/path) entries))]
                 (when (< 1 (count paths))
                   (str "WARN: group `" group "` has multiple component capabilities with operation `"
                        op "`: "
                        (str/join ", "
                                  (sort (map (fn [[_ b cap]]
                                               (str (:brick/path b) " " cap))
                                             entries)))
                        ". Review whether these are distinct responsibilities or a split/merge smell.")))))))

(defn- multi-group-base-warnings [bricks]
  (let [capability->group (->> (filter #(= :component (:brick/type %)) bricks)
                               (mapcat (fn [b]
                                         (for [cap (:brick/provides b)
                                               :when (:brick/group b)]
                                           [cap (:brick/group b)])))
                               (into {}))]
    (for [b (filter #(= :base (:brick/type %)) bricks)
          :let [groups (set (keep capability->group (:brick/uses b)))]
          :when (<= 3 (count groups))]
      (str "WARN: " (:brick/path b) " uses capabilities across "
           (count groups) " groups: " (str/join ", " (sort (map str groups)))
           ". Review whether the base is carrying too much orchestration."))))

(defn- brick-name [path]
  (keyword (.getName (io/file path))))

(defn- skeleton [kind path]
  (let [api (public-api path)]
    (case kind
      :component
      (cond->
       {:brick/name (brick-name path)
        :brick/type :component
        :brick/purpose "TODO: describe this component's responsibility"
        :brick/provides #{}
        :brick/not-for #{}
        :brick/requirements []}
        (seq api) (assoc :brick/public-api-candidates api))

      :base
      (cond->
       {:brick/name (brick-name path)
        :brick/type :base
        :brick/purpose "TODO: describe this base entrypoint and delegation responsibility"
        :brick/entrypoint :TODO
        :brick/uses #{}
        :brick/requirements []}
        (seq api) (assoc :brick/public-api-candidates api)))))

(defn- load-brick [{:keys [kind path]}]
  (let [edn-path (str path "/brick.edn")]
    (when-not (file? edn-path)
      (error! (str "ERROR: " edn-path " is required for every " (name kind) " brick")))
    (let [data (read-edn-file edn-path)]
      (case kind
        :component (validate-component! edn-path data)
        :base (validate-base! edn-path data))
      (assoc data
             :brick/path path
             :brick/public-api (public-api path)))))

(defn- missing-bricks []
  (->> (brick-dirs)
       (remove #(file? (str (:path %) "/brick.edn")))
       vec))

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

(defn- validate-cross-brick! [bricks]
  (let [provided (mapcat (fn [b] (map #(vector % b) (:brick/provides b))) (filter #(= :component (:brick/type %)) bricks))
        duplicate-provides (->> provided
                                (group-by first)
                                (keep (fn [[cap pairs]]
                                        (when (< 1 (count pairs))
                                          [cap (map #(-> % second :brick/path) pairs)]))))
        provided-set (set (map first provided))
        unknown-uses (for [b (filter #(= :base (:brick/type %)) bricks)
                           use (:brick/uses b)
                           :when (not (contains? provided-set use))]
                       [(:brick/path b) use])
        not-for-conflicts (for [b bricks
                                cap (:brick/not-for b)
                                :let [owned-or-used (set (concat (:brick/provides b)
                                                                 (:brick/uses b)))]
                                :when (contains? owned-or-used cap)]
                            [(:brick/path b) cap])
        design-ids (design-requirement-ids)
        unknown-reqs (when (seq design-ids)
                       (for [b bricks
                             req (:brick/requirements b)
                             :when (not (contains? design-ids req))]
                         [(:brick/path b) req]))
        referenced-reqs (set (mapcat :brick/requirements bricks))
        unassigned-reqs (when (seq design-ids)
                          (remove referenced-reqs design-ids))
        duplicate-design-ids (duplicate-design-requirement-ids)]
    (when (seq duplicate-design-ids)
      (error!
       "ERROR: DESIGN.md has duplicate requirement ids:"
       (str/join "\n" (map #(str "  " %) duplicate-design-ids))))
    (when (seq duplicate-provides)
      (error!
       "ERROR: duplicate component capabilities:"
       (str/join "\n" (map (fn [[cap paths]]
                             (str "  " cap " provided by " (str/join ", " paths)))
                           duplicate-provides))))
    (when (seq unknown-uses)
      (error!
       "ERROR: base uses capabilities that no component provides:"
       (str/join "\n" (map (fn [[path use]]
                             (str "  " path " uses " use))
                           unknown-uses))))
    (when (seq not-for-conflicts)
      (error!
       "ERROR: brick.edn has :brick/not-for conflicts:"
       (str/join "\n" (map (fn [[path cap]]
                             (str "  " path " conflicts on " cap))
                           not-for-conflicts))))
    (when (seq unknown-reqs)
      (error!
       "ERROR: brick.edn references requirement ids not found in DESIGN.md:"
       (str/join "\n" (map (fn [[path req]]
                             (str "  " path " references " req))
                           unknown-reqs))))
    (when (seq unassigned-reqs)
      (warn!
       "WARN: DESIGN.md has requirement ids not referenced by any brick.edn:"
       (str/join "\n" (map #(str "  " %) (sort unassigned-reqs)))))))

(defn- migration-quality-warnings [bricks]
  (concat
   (mapcat #(todo-warnings (str (:brick/path %) "/brick.edn") %) bricks)
   (mapcat empty-provides-warnings bricks)
   (mapcat component-interface-warnings bricks)
   (mapcat component-capability-warnings (filter #(= :component (:brick/type %)) bricks))
   (mapcat #(api-name-warnings (:brick/path %) (:brick/public-api %)) bricks)
   (duplicate-public-api-name-warnings bricks)))

(defn- group-advisory-warnings [bricks]
  (concat
   (group-capability-domain-warnings bricks)
   (same-group-operation-warnings bricks)
   (multi-group-base-warnings bricks)))

(defn- report-migration-quality! [bricks {:keys [strict?]}]
  (let [warnings (vec (migration-quality-warnings bricks))]
    (if (and strict? (seq warnings))
      (apply error!
             "ERROR: Brick Map has unresolved migration-quality warnings in :adoption-mode :complete:"
             warnings)
      (doseq [warning warnings]
        (warn! warning)))))

(defn- report-group-advisories! [bricks]
  (doseq [warning (group-advisory-warnings bricks)]
    (warn! warning)))

(defn- bullet-list [items empty-text]
  (if (seq items)
    (str/join "\n" (map #(str "- `" % "`") items))
    empty-text))

(defn- render-group-section [bricks]
  (let [grouped (->> bricks
                     (filter :brick/group)
                     (group-by :brick/group)
                     (into (sorted-map)))
        ungrouped (->> bricks
                       (remove :brick/group)
                       (map :brick/path)
                       sort
                       vec)]
    (str
     "\n## Groups\n\n"
     (if (or (seq grouped) (seq ungrouped))
       (str
        (apply str
               (for [[group bs] grouped]
                 (str "### `" group "`\n\n"
                      (bullet-list (sort (map :brick/path bs)) "- none")
                      "\n\n")))
        (when (seq ungrouped)
          (str "### Ungrouped\n\n"
               (bullet-list ungrouped "- none")
               "\n\n")))
       "No bricks are present yet.\n\n"))))

(defn- render-brick [b]
  (let [kind (:brick/type b)]
    (str
     "\n## " (:brick/path b) "\n\n"
     "- Type: `" (name kind) "`\n"
     "- Name: `" (:brick/name b) "`\n"
     (when (:brick/group b)
       (str "- Group: `" (:brick/group b) "`\n"))
     "- Purpose: " (:brick/purpose b) "\n"
     (case kind
       :component
       (str "- Provides:\n" (bullet-list (:brick/provides b) "- none") "\n")
       :base
       (str "- Entrypoint: `" (:brick/entrypoint b) "`\n"
            "- Uses:\n" (bullet-list (:brick/uses b) "- none") "\n"))
     (when (seq (:brick/not-for b))
       (str "- Not for:\n" (bullet-list (:brick/not-for b) "- none") "\n"))
     "- Requirements:\n" (bullet-list (:brick/requirements b) "- none") "\n"
     "- Public API:\n" (bullet-list (:brick/public-api b) "- none") "\n")))

(defn- render [bricks]
  (str generated-header
       (if (seq bricks)
         (str (render-group-section bricks)
              (apply str (map render-brick (sort-by :brick/path bricks))))
         "\nNo bricks are present yet.\n")))

(defn- write-file! [path content]
  (io/make-parents path)
  (spit path content))

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
         :brick/group
         :brick/purpose
         :brick/provides
         :brick/not-for
         :brick/entrypoint
         :brick/uses
         :brick/requirements
         :brick/public-api]))

(defn- render-index [bricks]
  (let [normalized (mapv normalize-brick (sort-by :brick/path bricks))
        capabilities (->> normalized
                          (filter #(= :component (:brick/type %)))
                          (mapcat (fn [b]
                                    (map #(vector % (:brick/path b)) (:brick/provides b))))
                          (into (sorted-map)))
        entrypoints (->> normalized
                         (filter #(= :base (:brick/type %)))
                         (map (fn [b] [(:brick/entrypoint b) (:brick/path b)]))
                         (into (sorted-map)))
        groups (->> normalized
                    (filter :brick/group)
                    (group-by :brick/group)
                    (map (fn [[group bs]]
                           [group (vec (sort (map :brick/path bs)))]))
                         (into (sorted-map)))]
    (str ";; GENERATED - do not edit by hand.\n"
         ";; Source of truth: DESIGN.md, components/*/brick.edn, bases/*/brick.edn, interface.clj\n"
         ";; Regenerate with: clj -Sdeps '{:paths [\".llm/scripts\"]}' -X gen-brick-map/generate\n"
         (with-out-str
           (pprint/pprint
            {:bricks normalized
             :groups groups
             :capabilities capabilities
             :entrypoints entrypoints})))))

(defn generate
  "Generate docs/BRICKS.md, .llm/data/brick-map.edn, and sidecar manifest."
  [{:keys [out-file index-file manifest-file auto-create?]}]
  (let [out-file (or out-file default-doc-file)
        index-file (or index-file default-index-file)
        manifest-file (or manifest-file default-manifest-file)
        missing (missing-bricks)
        _ (when (and (seq missing) auto-create?)
            (doseq [{:keys [kind path]} missing]
              (let [target (str path "/brick.edn")]
                (write-file! target (with-out-str (pprint/pprint (skeleton kind path))))
                (println "generated skeleton" target))))
        _ (when (and (seq missing) (not auto-create?))
            (error! "ERROR: brick.edn is missing. Generate skeletons first:"
                    "  clj -Sdeps '{:paths [\".llm/scripts\"]}' -X gen-brick-map/generate :auto-create? true"))
        bricks (mapv load-brick (brick-dirs))]
    (validate-cross-brick! bricks)
    (report-migration-quality! bricks {:strict? false})
    (report-group-advisories! bricks)
    (write-file! out-file (render bricks))
    (write-file! index-file (render-index bricks))
    (write-file! manifest-file (render-manifest (brick-map-manifest out-file index-file)))
    (println "generated" out-file)
    (println "generated" index-file)
    (println "generated" manifest-file)))

(defn propose-missing
  "Print brick.edn skeleton proposals for bricks that do not have brick.edn yet."
  [_]
  (let [missing (->> (brick-dirs)
                     (remove #(file? (str (:path %) "/brick.edn")))
                     vec)]
    (if (empty? missing)
      (println "No missing brick.edn files.")
      (doseq [{:keys [kind path]} missing]
        (println ";; ------------------------------------------------------------")
        (println ";; Proposal for" (str path "/brick.edn"))
        (println ";; Review TODO fields before writing this file.")
        (pprint/pprint (skeleton kind path))))))

(defn ensure
  "Create missing brick.edn skeletons, regenerate downstream Brick Map files, and warn on TODOs."
  [_]
  (generate {:auto-create? true}))

(defn check
  "Validate brick.edn and compare generated Brick Map with docs/BRICKS.md."
  [_]
  (let [dirs (vec (brick-dirs))
        outputs-exist? (or (file? default-doc-file)
                           (file? default-index-file)
                           (file? default-manifest-file))]
    (if (and (empty? dirs) (not outputs-exist?))
      (println "check-brick-map: OK (no bricks)")
      (let [bricks (mapv load-brick dirs)
            expected-doc (render bricks)
            expected-index (render-index bricks)
            expected-manifest (render-manifest
                               (brick-map-manifest default-doc-file default-index-file))]
        (validate-cross-brick! bricks)
        (report-migration-quality! bricks {:strict? (= :complete (adoption-mode))})
        (report-group-advisories! bricks)
        (when-not (file? default-doc-file)
          (error! "ERROR: docs/BRICKS.md is missing. Run gen-brick-map/generate after adding bricks."))
        (when-not (file? default-index-file)
          (error! "ERROR: .llm/data/brick-map.edn is missing. Run gen-brick-map/generate after adding bricks."))
        (when-not (file? default-manifest-file)
          (error! "ERROR: .llm/data/brick-map.manifest.edn is missing. Run gen-brick-map/generate."))
        (let [actual-doc (slurp default-doc-file)
              actual-index (slurp default-index-file)
              actual-manifest (slurp default-manifest-file)]
          (if (and (= expected-doc actual-doc)
                   (= expected-index actual-index)
                   (= expected-manifest actual-manifest))
            (println "check-brick-map: OK")
            (error! "ERROR: docs/BRICKS.md, .llm/data/brick-map.edn, or .llm/data/brick-map.manifest.edn is not synchronized with brick.edn/interface.clj."
                    "Fix: clj -Sdeps '{:paths [\".llm/scripts\"]}' -X gen-brick-map/generate")))))))
