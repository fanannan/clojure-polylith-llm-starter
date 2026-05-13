(ns gen-brick-map
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.pprint :as pprint]
   [clojure.string :as str]))

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
    (catch Exception e
      (throw (ex-info (str "Invalid EDN: " path " - " (.getMessage e))
                      {:path path}
                      e)))))

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

(defn- validate-component! [path data]
  (when-not (= :component (:brick/type data))
    (error! (str "ERROR: " path " must have :brick/type :component")))
  (when-not (keyword? (:brick/name data))
    (error! (str "ERROR: " path " must have keyword :brick/name")))
  (when-not (nonblank-string? (:brick/purpose data))
    (error! (str "ERROR: " path " must have non-empty :brick/purpose")))
  (when-not (and (keyword-coll? (:brick/provides data))
                 (seq (:brick/provides data)))
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
  (->> bricks
       (mapcat (fn [b]
                 (map #(vector (api-name %) (:brick/path b) %)
                      (:brick/public-api b))))
       (group-by first)
       (keep (fn [[name entries]]
               (let [paths (set (map second entries))]
                 (when (and (< 1 (count paths))
                            (contains? allowed-generic-api-names name))
                   (str "WARN: public API function name `" name
                        "` appears in multiple bricks: "
                        (str/join ", " (sort paths))
                        ". Keep only if each brick has a clearly distinct capability in brick.edn.")))))))

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

(defn- design-requirement-ids []
  (if (file? "DESIGN.md")
    (->> (re-seq #"\b[A-Z][A-Z0-9]+-[0-9]+\b" (slurp "DESIGN.md"))
         set)
    #{}))

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
        design-ids (design-requirement-ids)
        unknown-reqs (when (seq design-ids)
                       (for [b bricks
                             req (:brick/requirements b)
                             :when (not (contains? design-ids req))]
                         [(:brick/path b) req]))]
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
    (when (seq unknown-reqs)
      (error!
       "ERROR: brick.edn references requirement ids not found in DESIGN.md:"
       (str/join "\n" (map (fn [[path req]]
                             (str "  " path " references " req))
                           unknown-reqs))))))

(defn- bullet-list [items empty-text]
  (if (seq items)
    (str/join "\n" (map #(str "- `" % "`") items))
    empty-text))

(defn- render-brick [b]
  (let [kind (:brick/type b)]
    (str
     "\n## " (:brick/path b) "\n\n"
     "- Type: `" (name kind) "`\n"
     "- Name: `" (:brick/name b) "`\n"
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
         (apply str (map render-brick (sort-by :brick/path bricks)))
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
                         (into (sorted-map)))]
    (str ";; GENERATED - do not edit by hand.\n"
         ";; Sources: components/*/brick.edn, bases/*/brick.edn, interface.clj\n"
         (with-out-str
           (pprint/pprint
            {:bricks normalized
             :capabilities capabilities
             :entrypoints entrypoints})))))

(defn generate
  "Generate docs/BRICKS.md and .llm/data/brick-map.edn from brick.edn and interface.clj."
  [{:keys [out-file index-file auto-create?]}]
  (let [out-file (or out-file "docs/BRICKS.md")
        index-file (or index-file ".llm/data/brick-map.edn")
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
    (doseq [b bricks
            warning (todo-warnings (str (:brick/path b) "/brick.edn") b)]
      (warn! warning))
    (doseq [b bricks
            warning (api-name-warnings (:brick/path b) (:brick/public-api b))]
      (warn! warning))
    (doseq [warning (duplicate-public-api-name-warnings bricks)]
      (warn! warning))
    (write-file! out-file (render bricks))
    (write-file! index-file (render-index bricks))
    (println "generated" out-file)
    (println "generated" index-file)))

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
  (let [dirs (vec (brick-dirs))]
    (if (empty? dirs)
      (println "check-brick-map: OK (no bricks)")
      (let [bricks (mapv load-brick dirs)
            expected-doc (render bricks)
            expected-index (render-index bricks)]
        (validate-cross-brick! bricks)
        (when-not (file? "docs/BRICKS.md")
          (error! "ERROR: docs/BRICKS.md is missing. Run gen-brick-map/generate after adding bricks."))
        (when-not (file? ".llm/data/brick-map.edn")
          (error! "ERROR: .llm/data/brick-map.edn is missing. Run gen-brick-map/generate after adding bricks."))
        (let [actual-doc (slurp "docs/BRICKS.md")
              actual-index (slurp ".llm/data/brick-map.edn")]
          (if (and (= expected-doc actual-doc)
                   (= expected-index actual-index))
            (println "check-brick-map: OK")
            (error! "ERROR: docs/BRICKS.md or .llm/data/brick-map.edn is not synchronized with brick.edn/interface.clj."
                    "Fix: clj -Sdeps '{:paths [\".llm/scripts\"]}' -X gen-brick-map/generate")))))))
