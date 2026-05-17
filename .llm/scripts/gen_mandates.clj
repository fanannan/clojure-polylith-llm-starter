(ns gen-mandates
  "Generate .llm/data/mandates.edn — a derived join index of authored mandate
   annotations.

   A mandate annotation is the visible one-line directive
   `[mandate: M-NNNN/hint type:<type> tier:<tier>]` placed above the prose it
   identifies. This generator scans CLAUDE.md and .llm/guide/*.md (the mandate
   scan range), extracts each annotation, and records an immutable derived value
   per mandate: prose home, source heading, source digest, git rev, type, tier,
   the enforcement scripts the surrounding prose names, and the template-only
   tests that declare a [MANDATE:M-NNNN] verification token.

   The index is a derived value, not an authority. The prose is the source of
   truth. Regenerate after editing any mandate annotation or its prose."
  (:require
   [clojure.java.io :as io]
   [clojure.java.shell :as shell]
   [clojure.pprint :as pprint]
   [clojure.string :as str]
   [derivation-manifest :as derivation]))

(def generator-path ".llm/scripts/gen_mandates.clj")
(def default-index-file ".llm/data/mandates.edn")
(def regenerate-command
  "clj -Sdeps '{:paths [\".llm/scripts\"]}' -X gen-mandates/generate")

;; Mandate annotation format and vocabulary (plan §5.5 / §5.6). Kept as a small
;; deliberate duplicate of the parser in check_instrument_cases.clj: that file
;; is template-only and is removed in derived projects, while this generator is
;; template-owned and must keep working. A shared namespace would couple the two
;; boundaries, so a ~40-line duplicate is the correct trade-off (plan §2.4).
(def allowed-instruction-types #{:workflow :prohibition :invariant :derived-artifact})
(def allowed-mandate-tiers #{:kernel :extended})
(def mandate-id-re #"M-\d{4}")
(def mandate-annotation-re #"(?m)^\[mandate:\s*(.+?)\]\s*$")
;; Optional explicit end marker. A mandate annotation governs the prose from its
;; line until the first of: a [/mandate] marker, the next heading, the next
;; annotation, or EOF. The marker is honored whether it sits on its own line or
;; appears inline at the end of a prose line; when inline, the text before it is
;; kept in the governed section and the marker (and anything after it) is
;; dropped. Place [/mandate] when the governed prose ends before the next
;; heading, so source/digest covers only the rule and not unrelated prose.
(def mandate-end-marker "[/mandate]")
(def heading-re #"^#{1,6}\s")
;; Enforcer artifacts a mandate's prose may name: .llm/scripts check/gen
;; scripts, clj-kondo config and polyguard hooks, and the cljfmt config.
;; enforced-by is the set of these the surrounding prose mentions.
(def enforcer-mention-re
  #"\.llm/scripts/[a-z0-9_-]+\.(?:sh|clj)|\.clj-kondo/[a-z0-9_/-]+\.(?:edn|clj)|\bcljfmt\.edn")
(def test-dir ".llm/template-only/tests")
;; A template-only test declares the mandate(s) it verifies with a visible
;; [MANDATE:M-NNNN] token in its header comment. verified-by is the reverse of
;; that declaration. The test directory is removed in derived projects, so the
;; verified-by dimension is naturally empty there (plan §5.4).
(def mandate-ref-re #"\[MANDATE:(M-\d{4})\]")

(defn- file? [path]
  (.isFile (io/file path)))

(defn- error! [& messages]
  (throw (ex-info (str/join "\n" messages) {})))

(defn- strip-fenced-code-blocks
  "Replace fenced code block lines with blank lines so annotations quoted inside
   examples are not collected, while preserving line numbering."
  [content]
  (->> (str/split-lines content)
       (reduce
        (fn [{:keys [in-code?] :as acc} line]
          (if (str/starts-with? line "```")
            (-> acc (update :lines conj "") (update :in-code? not))
            (update acc :lines conj (if in-code? "" line))))
        {:in-code? false :lines []})
       :lines
       vec))

(defn- corpus-files []
  (let [claude (io/file "CLAUDE.md")
        guide-dir (io/file ".llm/guide")
        guides (when (.isDirectory guide-dir)
                 (->> (.listFiles guide-dir)
                      (filter #(.isFile %))
                      (filter #(str/ends-with? (.getName %) ".md"))
                      (sort-by #(.getName %))))]
    (->> (cons claude guides)
         (filter #(and % (.isFile %)))
         (mapv (fn [f] {:path (str/replace (.getPath f) "\\" "/")
                        :name (.getName f)})))))

(defn- parse-annotation [body]
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

(defn- annotation-errors [path {:keys [id hint type tier]}]
  (cond-> []
    (not (and (string? id) (re-matches mandate-id-re id)))
    (conj (str path ": mandate id must match M-NNNN"))
    (not (and (string? hint) (re-matches #"[a-z0-9-]+" hint)))
    (conj (str path ": mandate hint must match [a-z0-9-]+"))
    (not (contains? allowed-instruction-types type))
    (conj (str path ": type: must be one of " (pr-str allowed-instruction-types)))
    (not (contains? allowed-mandate-tiers tier))
    (conj (str path ": tier: must be one of " (pr-str allowed-mandate-tiers)))))

(defn- section-lines
  "Given fence-stripped lines and the index of an annotation line, return the
   vector of prose lines the annotation governs. The section runs from the
   annotation line until the first of: a [/mandate] marker, the next heading,
   the next annotation, or EOF. A [/mandate] marker is honored whether it stands
   on its own line or appears inline; inline, the prose before it is kept and
   the marker and everything after it are dropped."
  [lines idx]
  (loop [i (inc idx)
         acc [(nth lines idx)]]
    (if (>= i (count lines))
      acc
      (let [line (nth lines i)
            marker (str/index-of line mandate-end-marker)]
        (cond
          marker (let [before (subs line 0 marker)]
                   (cond-> acc (not (str/blank? before)) (conj before)))
          (re-find heading-re line) acc
          (re-find mandate-annotation-re line) acc
          :else (recur (inc i) (conj acc line)))))))

(defn- nearest-heading [lines idx]
  (loop [i (dec idx)]
    (cond
      (neg? i) nil
      (re-find heading-re (nth lines i)) (str/replace (nth lines i) #"^#+\s*" "")
      :else (recur (dec i)))))

(defn- collect-file-mandates [{:keys [path name]}]
  (let [lines (strip-fenced-code-blocks (slurp path))]
    (keep-indexed
     (fn [idx line]
       (when-let [[_ body] (re-find mandate-annotation-re line)]
         (let [annotation (parse-annotation body)
               section (str/join "\n" (section-lines lines idx))
               errors (annotation-errors (str path " [mandate:" idx "]") annotation)]
           {:annotation annotation
            :errors errors
            :entry {:hint (:hint annotation)
                    :defined-in name
                    :source/heading (nearest-heading lines idx)
                    :source/digest (derivation/sha256-string section)
                    :instruction/type (:type annotation)
                    :tier (:tier annotation)
                    :status :active
                    :enforced-by (vec (sort (distinct (re-seq enforcer-mention-re section))))
                    :verified-by []}})))
     lines)))

(defn- collect-verifications
  "Scan template-only test scripts for [MANDATE:M-NNNN] tokens and return a map
   of mandate id -> set of test paths that declare verification of it. The test
   directory is absent in derived projects, where this yields an empty map."
  []
  (let [dir (io/file test-dir)]
    (if-not (.isDirectory dir)
      {}
      (reduce
       (fn [acc f]
         (let [path (str/replace (.getPath f) "\\" "/")
               ids (map second (re-seq mandate-ref-re (slurp f)))]
           (reduce (fn [m id] (update m id (fnil conj #{}) path)) acc ids)))
       {}
       (->> (.listFiles dir)
            (filter #(.isFile %))
            (filter #(str/ends-with? (.getName %) ".sh"))
            (sort-by #(.getName %)))))))

(defn- git-rev []
  (try
    (let [{:keys [exit out]} (shell/sh "git" "rev-parse" "HEAD")]
      (when (zero? exit) (str/trim out)))
    (catch Throwable _ nil)))

(defn- collect-mandates []
  (let [collected (mapcat collect-file-mandates (corpus-files))
        errors (mapcat :errors collected)
        _ (when (seq errors)
            (apply error! "ERROR: malformed mandate annotation(s):" errors))
        rev (git-rev)
        by-id (group-by #(get-in % [:annotation :id]) collected)
        dups (keep (fn [[id items]] (when (< 1 (count items)) id)) by-id)
        _ (when (seq dups)
            (error! (str "ERROR: duplicate mandate id(s): " (str/join ", " (sort dups)))))
        verifications (collect-verifications)
        unknown-refs (sort (remove (set (keys by-id)) (keys verifications)))
        _ (when (seq unknown-refs)
            (error! (str "ERROR: template-only test references unknown mandate id(s): "
                         (str/join ", " unknown-refs))))]
    (into (sorted-map)
          (map (fn [{:keys [annotation entry]}]
                 [(:id annotation)
                  (assoc entry
                         :source/git-rev rev
                         :verified-by (vec (sort (get verifications (:id annotation) #{}))))]))
          collected)))

(defn- mandates-manifest []
  (derivation/make-manifest
   {:id :mandates
    :tool "gen-mandates"
    :output-path default-index-file
    :generator-path generator-path
    :tool-input-paths [".llm/scripts/derivation_manifest.clj"]
    :input-paths ["CLAUDE.md" ".llm/guide" ".llm/template-only/tests"]
    :input-policy {:missing :explicit-empty
                   :directory-roots :recursive-digest}
    :generated-at "deterministic"
    :regenerate-command regenerate-command}))

(defn- render-index [mandates]
  (str ";; GENERATED - do not edit by hand.\n"
       ";; Derived join index of [mandate: ...] annotations in CLAUDE.md / .llm/guide/*.md.\n"
       ";; The prose is the source of truth; this index is a derived value.\n"
       ";; Regenerate with: " regenerate-command "\n"
       (with-out-str
         (pprint/pprint
          {:mandate/schema 1
           derivation/manifest-key (mandates-manifest)
           :mandates mandates}))))

(defn- write-file! [path content]
  (io/make-parents path)
  (spit path content))

(defn generate
  "Scan CLAUDE.md and .llm/guide/*.md, write .llm/data/mandates.edn."
  [_]
  (let [index-file default-index-file
        mandates (collect-mandates)]
    (write-file! index-file (render-index mandates))
    (println (str "generated " index-file " (" (count mandates) " mandates)"))))

(defn check
  "Verify .llm/data/mandates.edn matches a fresh scan of the corpus."
  [_]
  (let [index-file default-index-file]
    (if-not (file? index-file)
      (error! (str "ERROR: " index-file " is missing. Run gen-mandates/generate."))
      (let [expected (render-index (collect-mandates))
            actual (slurp index-file)]
        (if (= expected actual)
          (println "check-mandates: OK")
          (error! (str "ERROR: " index-file " is not synchronized with mandate annotations.")
                  (str "Fix: " regenerate-command)))))))
