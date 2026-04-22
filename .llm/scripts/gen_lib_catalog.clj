(ns gen-lib-catalog
  "Generator for .llm/data/libs.edn and related pattern files.

   Reads .llm/guide/STACK_GUIDE.md for fenced EDN blocks marked with
   `;; lib-catalog` on the first line. Each block contains a vector of lib
   entry maps (see entry-schema). The generator:

     1. extracts all lib-catalog blocks (multi-block is supported and
        concatenated into a single list)
     2. validates each entry via Malli
     3. enforces uniqueness rules ([:ids :coord] + :purpose pair, and
        :purpose × :recommended)
     4. emits artifacts consumed by downstream tools:
        - .llm/data/libs.edn                    (full list, pretty-printed)
        - .llm/data/deprecated-libs.patterns    (shell pattern|reason for
                                                  deps.edn coord matching)
        - .llm/data/forbidden-requires.patterns (shell pattern|reason for
                                                  require namespace matching)

   Usage:
     clj -X:gen-lib-catalog
     clj -X:gen-lib-catalog :out-dir '\"/tmp/out\"'

   See .llm/guide/MAINTAINERS_GUIDE.md §5.10 for the mechanization layer
   classification, and the generator plan under .llm/plans/."
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.pprint :as pprint]
   [clojure.string :as str]
   [malli.core :as m]
   [malli.error :as me]))

;; ---------------------------------------------------------------------------
;; Schema
;; ---------------------------------------------------------------------------

(def ^:private tag-values
  "STACK_GUIDE.md §8.0 で定義された 6 種の標準理由タグ。"
  #{:security :maintenance-stopped :license :replacement-available
    :philosophy-mismatch :conditional})

(def entry-schema
  "Universal lib catalog entry schema (see plan §Schema(整理版 — 5 群集約))."
  [:map {:closed true}
   [:purpose {:optional true} [:vector {:min 1} keyword?]]
   [:ids [:map {:closed true}
          [:coord symbol?]
          [:aliases {:optional true} [:vector symbol?]]
          [:ns {:optional true} string?]]]
   [:judgment
    [:map {:closed true}
     [:status [:enum :deprecated :recommended :acceptable :conditional :scope-excluded]]
     [:severity {:optional true} [:enum :forbidden :superseded]]
     [:replacement {:optional true} [:or symbol? [:vector symbol?]]]
     [:version {:optional true} string?]
     [:applicable-when {:optional true} string?]]]
   [:reasons {:optional true}
    [:map {:closed true}
     [:text {:optional true} string?]
     [:tags {:optional true} [:vector (into [:enum] tag-values)]]]]
   [:relations {:optional true}
    [:map {:closed true}
     [:bundles {:optional true} [:vector symbol?]]
     [:conflicts-with {:optional true} [:vector [:tuple symbol? string?]]]
     [:pairs-with {:optional true} [:map-of keyword? symbol?]]]]])

;; ---------------------------------------------------------------------------
;; Block extraction
;; ---------------------------------------------------------------------------

(def ^:private marker-regex
  "Matches the first-line marker `;; lib-catalog` (with optional surrounding
   whitespace). 完全一致が規約。"
  #"^\s*;;\s*lib-catalog\s*$")

(def ^:private fenced-block-regex
  "Multiline non-greedy capture of a fenced edn block body (between opening
   ```edn and closing ```)."
  #"(?ms)^```edn\s*\r?\n(.*?)\r?\n```")

(defn- lib-catalog-block?
  "Returns true if body's first non-empty line matches the `;; lib-catalog` marker."
  [body]
  (some->> (str/split-lines body)
           (drop-while str/blank?)
           first
           (re-matches marker-regex)
           some?))

(defn- extract-blocks
  "Return a seq of block body strings (content between fences) whose first
   line is the lib-catalog marker."
  [content]
  (keep (fn [[_ body]]
          (when (lib-catalog-block? body) body))
        (re-seq fenced-block-regex content)))

(defn- parse-block
  "Read a vector-of-maps from a block body. edn/read-string skips comment
   lines, so the marker line is discarded automatically."
  [body]
  (try
    (edn/read-string body)
    (catch Exception e
      (throw (ex-info "EDN parse error in lib-catalog block"
                      {:body body
                       :message (ex-message e)}
                      e)))))

;; ---------------------------------------------------------------------------
;; Validation
;; ---------------------------------------------------------------------------

(defn- validate-entry!
  "Throws on schema violation with a humanised Malli error."
  [entry idx]
  (when-not (m/validate entry-schema entry)
    (throw (ex-info (str "Schema violation at entry #" idx)
                    {:entry entry
                     :errors (me/humanize (m/explain entry-schema entry))}))))

(defn- assert-consistent-duplicate!
  "Same-pair duplicates are allowed if entries are structurally equal (supports
   per-stack blocks referencing cross-cutting libs). Only conflicting duplicates
   (same coord+purpose but different judgment / reasons / etc.) error out."
  [seen-atom pair entry]
  (if-let [prev (@seen-atom pair)]
    (when-not (= prev entry)
      (throw (ex-info (str "Conflicting duplicate for [[:ids :coord] :purpose] pair: " (pr-str pair))
                      {:pair pair :previous prev :current entry})))
    (swap! seen-atom assoc pair entry)))

(defn- assert-recommended-unique!
  "Same-purpose :recommended duplicates are allowed only when the entries are
   structurally equal (same lib recommended from multiple per-stack blocks).
   Different coords recommended for the same purpose is an error."
  [recommended-atom purpose entry]
  (if-let [prev (@recommended-atom purpose)]
    (when-not (= prev entry)
      (throw (ex-info (str ":recommended uniqueness violation for :purpose "
                           (pr-str purpose))
                      {:purpose purpose
                       :first-coord  (get-in prev  [:ids :coord])
                       :second-coord (get-in entry [:ids :coord])})))
    (swap! recommended-atom assoc purpose entry)))

(defn- assert-invariants!
  "Entry-level invariants that aren't expressible directly in the schema."
  [entry]
  (let [status (get-in entry [:judgment :status])]
    (when (and (= status :recommended) (nil? (:purpose entry)))
      (throw (ex-info ":status :recommended requires :purpose"
                      {:entry entry})))
    (when (and (= status :conditional)
               (str/blank? (get-in entry [:judgment :applicable-when])))
      (throw (ex-info ":status :conditional requires :applicable-when"
                      {:entry entry})))
    (when (and (contains? (:judgment entry) :severity)
               (not= status :deprecated))
      (throw (ex-info ":judgment :severity is only meaningful with :status :deprecated"
                      {:entry entry :status status})))))

(defn- check-uniqueness!
  "Verify [:ids :coord] × :purpose pair and :recommended × :purpose uniqueness,
   accepting structurally-equal duplicates across blocks (per-stack SSOT)."
  [entries]
  (let [seen (atom {})
        recommended (atom {})]
    (doseq [entry entries]
      (assert-invariants! entry)
      (let [coord   (get-in entry [:ids :coord])
            purpose (:purpose entry)
            status  (get-in entry [:judgment :status])]
        (assert-consistent-duplicate! seen [coord purpose] entry)
        (when (= status :recommended)
          (assert-recommended-unique! recommended purpose entry))))))

(defn- dedup-entries
  "Collapse structurally-equal duplicates while preserving first-occurrence order."
  [entries]
  (:result
   (reduce (fn [{:keys [seen result]} entry]
             (let [pair [(get-in entry [:ids :coord]) (:purpose entry)]]
               (if (contains? seen pair)
                 {:seen seen :result result}
                 {:seen (conj seen pair) :result (conj result entry)})))
           {:seen #{} :result []}
           entries)))

;; ---------------------------------------------------------------------------
;; Output generation
;; ---------------------------------------------------------------------------

(def ^:private key-order
  "Top-level key order for pretty-printing (plan convention)."
  [:purpose :ids :judgment :reasons :relations])

(defn- ordered-map
  "Return entry as array-map in the canonical key order, preserving only
   keys that are actually present."
  [entry]
  (apply array-map
         (mapcat (fn [k] (when (contains? entry k) [k (entry k)])) key-order)))

(defn- ensure-dir!
  [dir]
  (let [f (io/file dir)]
    (.mkdirs f)
    f))

(defn- emit-libs-edn!
  "Write .llm/data/libs.edn — the full list of entries, pretty-printed."
  [out-file entries]
  (spit out-file
        (str ";; GENERATED by .llm/scripts/gen_lib_catalog.clj — do not edit by hand.\n"
             ";; Source : .llm/guide/STACK_GUIDE.md §4.2 (per-stack blocks) + §8\n"
             ";; Re-run : clj -X:gen-lib-catalog\n"
             "\n"
             (with-out-str
               (pprint/pprint (mapv ordered-map entries))))))

(defn- regex-escape-dots
  [s]
  (str/replace s "." "\\."))

(defn- deprecated? [entry]
  (= :deprecated (get-in entry [:judgment :status])))

(defn- all-coords
  "Primary coord plus aliases."
  [entry]
  (cons (get-in entry [:ids :coord])
        (get-in entry [:ids :aliases] [])))

(defn- short-reason
  "Build a single-line reason from :reasons :text plus the :replacement hint."
  [entry]
  (let [text (or (get-in entry [:reasons :text]) "")
        repl (get-in entry [:judgment :replacement])
        rep-str (cond
                  (symbol? repl) (str "推奨: " repl)
                  (sequential? repl) (str "推奨: " (str/join " または " repl))
                  :else nil)]
    (->> [text rep-str]
         (remove str/blank?)
         (str/join "。"))))

(defn- emit-deprecated-patterns!
  "Emit one `<coord-pattern>|<reason>` line per deprecated coord or alias."
  [out-file entries]
  (let [lines (for [entry (filter deprecated? entries)
                    coord (all-coords entry)
                    :when coord]
                (str (regex-escape-dots (str coord)) "|" (short-reason entry)))]
    (spit out-file
          (str "# GENERATED by .llm/scripts/gen_lib_catalog.clj — do not edit by hand.\n"
               "# Source : .llm/guide/STACK_GUIDE.md §8\n"
               "# Re-run : clj -X:gen-lib-catalog\n"
               "#\n"
               "# Format: <regex-pattern>|<short-reason>\n"
               (when (seq lines) (str "\n" (str/join "\n" lines) "\n"))))))

(defn- emit-forbidden-requires!
  "Emit one `<ns-prefix-pattern>|<reason>` line per deprecated entry with :ns."
  [out-file entries]
  (let [lines (for [entry (filter deprecated? entries)
                    :let [ns-str (get-in entry [:ids :ns])]
                    :when ns-str]
                (str (regex-escape-dots ns-str) "|" (short-reason entry)))]
    (spit out-file
          (str "# GENERATED by .llm/scripts/gen_lib_catalog.clj — do not edit by hand.\n"
               "# Source : .llm/guide/STACK_GUIDE.md §8\n"
               "# Re-run : clj -X:gen-lib-catalog\n"
               "#\n"
               "# Format: <ns-regex-pattern>|<short-reason>\n"
               (when (seq lines) (str "\n" (str/join "\n" lines) "\n"))))))

(defn- canonical-conflict-pair
  "Sort a coord pair alphabetically to avoid duplicate bidirectional records."
  [coord-a coord-b]
  (let [[a b] (sort [(str coord-a) (str coord-b)])]
    [a b]))

(defn- collect-conflict-pairs
  "Extract [coord-a coord-b reason] triples from entries' :relations :conflicts-with.
   Pairs are canonicalised (alphabetical order) and de-duplicated by pair key."
  [entries]
  (->> (for [entry entries
             :let [coord (get-in entry [:ids :coord])]
             [other-coord reason] (get-in entry [:relations :conflicts-with] [])
             :let [[a b] (canonical-conflict-pair coord other-coord)]]
         [a b reason])
       (reduce (fn [seen [a b reason]]
                 (update seen [a b] (fn [prev] (or prev reason))))
               {})
       (map (fn [[[a b] reason]] [a b reason]))
       (sort-by first)))

(defn- emit-conflicts-patterns!
  "Emit one `<coord-a-regex>|<coord-b-regex>|<reason>` line per conflict pair.
   Consumer (check-conflicting-libs.sh) fails when both coords are present in
   the same deps.edn."
  [out-file entries]
  (let [lines (for [[a b reason] (collect-conflict-pairs entries)]
                (str (regex-escape-dots a) "|"
                     (regex-escape-dots b) "|"
                     reason))]
    (spit out-file
          (str "# GENERATED by .llm/scripts/gen_lib_catalog.clj — do not edit by hand.\n"
               "# Source : .llm/guide/STACK_GUIDE.md §3 の :relations :conflicts-with\n"
               "# Re-run : clj -X:gen-lib-catalog\n"
               "#\n"
               "# Format: <coord-a-regex>|<coord-b-regex>|<reason>\n"
               "# 同一 deps.edn 内で coord-a / coord-b の両方が宣言されている場合に\n"
               "# check-conflicting-libs.sh が error 終了する。\n"
               (when (seq lines) (str "\n" (str/join "\n" lines) "\n"))))))

;; ---------------------------------------------------------------------------
;; Entry point (tools.deps :exec-fn)
;; ---------------------------------------------------------------------------

(defn- read-entries
  [source]
  (let [content (slurp source)
        blocks  (extract-blocks content)]
    (when (empty? blocks)
      (throw (ex-info (str "No `;; lib-catalog` fenced blocks found in " source)
                      {:source source})))
    (let [entries (vec (mapcat parse-block blocks))]
      (doseq [[idx entry] (map-indexed vector entries)]
        (validate-entry! entry idx))
      (check-uniqueness! entries)
      (dedup-entries entries))))

(defn generate
  "Main entry. opts keys:
     :source   — STACK_GUIDE.md path (default .llm/guide/STACK_GUIDE.md)
     :out-dir  — output directory    (default .llm/data)"
  [opts]
  (let [source  (or (:source opts) ".llm/guide/STACK_GUIDE.md")
        out-dir (or (:out-dir opts) ".llm/data")
        entries (read-entries source)
        dir     (ensure-dir! out-dir)]
    (emit-libs-edn! (io/file dir "libs.edn") entries)
    (emit-deprecated-patterns! (io/file dir "deprecated-libs.patterns") entries)
    (emit-forbidden-requires! (io/file dir "forbidden-requires.patterns") entries)
    (emit-conflicts-patterns! (io/file dir "conflicts.patterns") entries)
    (println (format "gen-lib-catalog: %d entries → %s/{libs.edn,deprecated-libs.patterns,forbidden-requires.patterns,conflicts.patterns}"
                     (count entries)
                     (.getPath dir)))
    {:entries (count entries)
     :out-dir (.getPath dir)}))
