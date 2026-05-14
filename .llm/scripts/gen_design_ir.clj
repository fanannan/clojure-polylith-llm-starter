(ns gen-design-ir
  "Generate a small machine-readable index from DESIGN.md.

   This is intentionally conservative. It does not try to understand arbitrary
   prose as truth; it extracts explicit IDs and checklist items, then joins them
   with existing generated analysis EDN when present."
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.pprint :as pprint]
   [clojure.set :as set]
   [clojure.string :as str]))

(def default-out ".llm/data/design-ir.edn")

(defn- file? [path]
  (.isFile (io/file path)))

(defn- write-file! [path content]
  (io/make-parents path)
  (spit path content))

(defn- read-edn-file [path]
  (try
    (edn/read-string (slurp path))
    (catch Throwable e
      (throw (ex-info (str "Invalid EDN: " path " - " (.getMessage e))
                      {:path path})))))

(defn- read-edn-if-exists [path]
  (when (file? path)
    (read-edn-file path)))

(defn- strip-fenced-code-blocks [text]
  (str/replace text #"(?s)```.*?```" ""))

(defn- strip-html-comments [text]
  (str/replace text #"(?s)<!--.*?-->" ""))

(defn- source-text []
  (if (file? "DESIGN.md")
    (-> (slurp "DESIGN.md")
        strip-fenced-code-blocks
        strip-html-comments)
    ""))

(defn- section-id [heading]
  (some-> (re-find #"^##[ \t]+([^ \t]+)" heading) second))

(defn- update-section [current line]
  (if (re-find #"^##[ \t]+" line)
    {:id (section-id line)
     :title (-> line
                (str/replace #"^##[ \t]+" "")
                str/trim)}
    current))

(def requirement-definition-pattern
  #"^[ \t]{0,3}(?:#{1,6}[ \t]+|[-*][ \t]+)([A-Z][A-Z0-9]+-[0-9]+)\b[:：]?[ \t]*(.*)$")

(def use-case-heading-pattern
  #"^[ \t]{0,3}#{3,6}[ \t]+(UC-[0-9]+)\b[:：]?[ \t]*(.*)$")

(def acceptance-item-pattern
  #"^[ \t]{0,3}[-*][ \t]+\[[ xX]\][ \t]+(.+)$")

(def explicit-obligation-id-pattern
  #"^((?:AC|TO)-[0-9]+)[:：][ \t]*(.*)$")

(def bracket-reference-pattern
  #"\[([A-Z][A-Z0-9]+-[0-9]+)\]")

(def constraint-kinds
  #{:non-functional :external-interface :technical-constraints})

(defn- section-number [{:keys [id]}]
  (some->> id (re-find #"^([0-9]+)") second))

(defn- section-kind [section]
  (case (section-number section)
    "3" :use-cases
    "4" :acceptance
    "6" :non-functional
    "7" :external-interface
    "9" :technical-constraints
    :requirements))

(defn- parse-design []
  (let [lines (str/split-lines (source-text))]
    (loop [remaining (map-indexed vector lines)
           section nil
           requirements []
           use-cases []
           acceptance []]
      (if-let [[line-idx line] (first remaining)]
        (let [section' (update-section section line)
              line-no (inc line-idx)
              req-match (re-matches requirement-definition-pattern line)
              uc-match (re-matches use-case-heading-pattern line)
              acc-match (re-matches acceptance-item-pattern line)
              section' (or section' section)]
          (recur
           (rest remaining)
           section'
           (cond-> requirements
             req-match
             (conj {:id (second req-match)
                    :text (str/trim (nth req-match 2))
                    :kind (section-kind section')
                    :line line-no
                    :section section'}))
           (cond-> use-cases
             uc-match
             (conj {:id (second uc-match)
                    :text (str/trim (nth uc-match 2))
                    :line line-no
                    :section section'}))
           (cond-> acceptance
             (and acc-match (= "4" (section-number section')))
             (conj {:text (str/trim (second acc-match))
                    :line line-no
                    :section section'}))))
        {:requirements requirements
         :use-cases use-cases
         :acceptance-criteria acceptance}))))

(defn- duplicate-ids [requirements]
  (->> requirements
       (map :id)
       frequencies
       (keep (fn [[id n]] (when (< 1 n) id)))
       sort
       vec))

(defn- constraints [requirements]
  (->> requirements
       (filter #(contains? constraint-kinds (:kind %)))
       vec))

(defn- implementation-requirements [requirements]
  (->> requirements
       (remove #(contains? constraint-kinds (:kind %)))
       vec))

(defn- sorted-strings [xs]
  (vec (sort (set (remove nil? xs)))))

(defn- fallback-obligation-id [text]
  (let [normalized (-> text str/trim str/lower-case)]
    (format "TO-%08X" (bit-and 0xffffffff (.hashCode normalized)))))

(defn- bracket-references [text]
  (->> (re-seq bracket-reference-pattern text)
       (map second)
       sorted-strings))

(defn- related-use-cases [text]
  (->> (bracket-references text)
       (filter #(re-matches #"UC-[0-9]+" %))
       sorted-strings))

(defn- related-requirements [text]
  (->> (bracket-references text)
       (remove #(re-matches #"UC-[0-9]+" %))
       (remove #(re-matches #"(?:AC|TO)-[0-9]+" %))
       sorted-strings))

(defn- test-obligation [item]
  (let [text (:text item)
        [_ explicit-id explicit-text] (re-matches explicit-obligation-id-pattern text)
        text' (if explicit-id (str/trim explicit-text) text)]
    (assoc item
           :id (or explicit-id (fallback-obligation-id text'))
           :text text'
           :related-requirements (related-requirements text')
           :related-use-cases (related-use-cases text')
           :source :acceptance-criteria
           :verification :unspecified)))

(defn- test-obligations [acceptance]
  (mapv test-obligation acceptance))

(defn- duplicate-test-obligation-ids [obligations]
  (->> obligations
       (map :id)
       frequencies
       (keep (fn [[id n]] (when (< 1 n) id)))
       sort
       vec))

(defn- unknown-related-requirements [requirements obligations]
  (let [known (set (map :id requirements))]
    (->> obligations
         (mapcat :related-requirements)
         (remove known)
         sorted-strings)))

(defn- unknown-related-use-cases [use-cases obligations]
  (let [known (set (map :id use-cases))]
    (->> obligations
         (mapcat :related-use-cases)
         (remove known)
         sorted-strings)))

(defn- read-analysis []
  {:brick-map (read-edn-if-exists ".llm/data/brick-map.edn")
   :workspace-map (read-edn-if-exists ".llm/data/workspace-map.edn")
   :libs (read-edn-if-exists ".llm/data/libs.edn")})

(defn- present-source [path value]
  {:path path :exists (boolean value)})

(defn- sorted-keywords [xs]
  (vec (sort-by str (set (remove nil? xs)))))

(defn- implementation-index [{:keys [brick-map workspace-map libs]}]
  (let [brick-reqs (mapcat :brick/requirements (:bricks brick-map))
        project-reqs (mapcat :project/requirements (:projects workspace-map))
        workspace-brick-reqs (mapcat :brick/requirements (:bricks workspace-map))]
    {:brick-requirements (sorted-strings (concat brick-reqs workspace-brick-reqs))
     :project-requirements (sorted-strings project-reqs)
     :capabilities (sorted-keywords (keys (:capabilities brick-map)))
     :entrypoints (sorted-keywords (keys (:entrypoints brick-map)))
     :library-categories (sorted-keywords (mapcat :purpose libs))}))

(defn- coverage [design analysis-index]
  (let [requirements (:requirements design)
        design-ids (set (map :id requirements))
        constraint-ids (set (map :id (constraints requirements)))
        implementation-ids (set (map :id (implementation-requirements requirements)))
        implemented (set (concat (:brick-requirements analysis-index)
                                 (:project-requirements analysis-index)))]
    {:design-requirements (sorted-strings design-ids)
     :implementation-requirements (sorted-strings implementation-ids)
     :constraint-requirements (sorted-strings constraint-ids)
     :implemented-requirements (sorted-strings (set/intersection implementation-ids implemented))
     :unassigned-implementation-requirements (sorted-strings (set/difference implementation-ids implemented))
     :unassigned-requirements (sorted-strings (set/difference implementation-ids implemented))
     :constraint-implementation-references (sorted-strings (set/intersection constraint-ids implemented))
     :unknown-implementation-requirements (sorted-strings (set/difference implemented design-ids))}))

(defn ir []
  (let [design (parse-design)
        analysis (read-analysis)
        index (implementation-index analysis)
        obligations (test-obligations (:acceptance-criteria design))]
    (into (sorted-map)
          {:source "DESIGN.md"
           :generated-by "gen-design-ir"
           :analysis-sources {:brick-map (present-source ".llm/data/brick-map.edn" (:brick-map analysis))
                              :workspace-map (present-source ".llm/data/workspace-map.edn" (:workspace-map analysis))
                              :libs (present-source ".llm/data/libs.edn" (:libs analysis))}
           :requirements (:requirements design)
           :use-cases (:use-cases design)
           :acceptance-criteria (:acceptance-criteria design)
           :constraints (constraints (:requirements design))
           :test-obligations obligations
           :implementation-index index
           :coverage (coverage design index)
           :diagnostics {:duplicate-requirement-ids (duplicate-ids (:requirements design))
                         :duplicate-test-obligation-ids (duplicate-test-obligation-ids obligations)
                         :unknown-related-requirements (unknown-related-requirements (:requirements design) obligations)
                         :unknown-related-use-cases (unknown-related-use-cases (:use-cases design) obligations)}})))

(defn- render [data]
  (str ";; GENERATED - do not edit by hand.\n"
       ";; Source of truth: DESIGN.md\n"
       ";; Analysis inputs when present: .llm/data/brick-map.edn, .llm/data/workspace-map.edn, .llm/data/libs.edn\n"
       ";; Regenerate with: clj -Sdeps '{:paths [\".llm/scripts\"]}' -X gen-design-ir/generate\n"
       (with-out-str (pprint/pprint data))))

(defn- error! [& messages]
  (throw (ex-info (str/join "\n" messages) {})))

(defn- validate! [data]
  (when-let [dups (seq (get-in data [:diagnostics :duplicate-requirement-ids]))]
    (error! "ERROR: DESIGN.md has duplicate requirement ids:"
            (str/join "\n" (map #(str "  " %) dups))))
  (when-let [dups (seq (get-in data [:diagnostics :duplicate-test-obligation-ids]))]
    (error! "ERROR: DESIGN.md has duplicate test obligation ids:"
            (str/join "\n" (map #(str "  " %) dups)))))

(defn generate
  "Generate .llm/data/design-ir.edn from DESIGN.md and existing analysis EDN."
  [{:keys [out-file]}]
  (let [out-file (or out-file default-out)
        data (ir)]
    (validate! data)
    (write-file! out-file (render data))
    (println (str "Generated " out-file))))

(defn check
  "Validate DESIGN.md extraction and compare .llm/data/design-ir.edn drift."
  [{:keys [out-file]}]
  (let [out-file (or out-file default-out)
        data (ir)
        expected (render data)]
    (validate! data)
    (when-not (file? out-file)
      (error! (str "ERROR: " out-file " is missing. Run gen-design-ir/generate after updating DESIGN.md.")))
    (if (= expected (slurp out-file))
      (println "check-design-ir: OK")
      (error! (str "ERROR: " out-file " is not synchronized with DESIGN.md or existing analysis EDN.")
              "Fix: clj -Sdeps '{:paths [\".llm/scripts\"]}' -X gen-design-ir/generate"))))
