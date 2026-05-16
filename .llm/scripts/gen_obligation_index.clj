(ns gen-obligation-index
  "Generate DESIGN-derived obligation coverage and a read-only Work Frontier.

   This is an index plane view, not a task store. The authority remains
   DESIGN.md, trace metadata, QUESTIONS, and evidence records."
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.pprint :as pprint]
   [clojure.set :as set]
   [clojure.string :as str]
   [derivation-manifest :as derivation]))

(def default-out ".llm/data/obligation-index.edn")
(def generator-path ".llm/scripts/gen_obligation_index.clj")
(def helper-path ".llm/scripts/derivation_manifest.clj")

(def constraint-kinds
  #{:non-functional :external-interface :technical-constraints})

(def complete-states
  #{:satisfied :out-of-scope :deferred :non-code :manual-verified})

(def accounted-states
  #{:blocked-by-question :manual-verification-required})

(def red-states
  #{:missing-boundary
    :missing-test
    :missing-trace
    :orphan-boundary
    :orphan-test
    :stale-evidence
    :unbacked-disposition
    :unresolved-blocker})

(defn- file? [path]
  (.isFile (io/file path)))

(defn- write-file! [path content]
  (io/make-parents path)
  (spit path content))

(defn- read-edn-if-exists [path]
  (when (file? path)
    (edn/read-string (slurp path))))

(defn- repo-context []
  (or (read-edn-if-exists ".llm/repo-context.edn") {}))

(defn- design-ir []
  (or (read-edn-if-exists ".llm/data/design-ir.edn")
      {:requirements []
       :use-cases []
       :test-obligations []}))

(defn- trace-index []
  (or (read-edn-if-exists ".llm/data/trace-index.edn")
      {:entries []
       :impact {:requirements {}
                :use-cases {}
                :test-obligations {}}}))

(def question-id-pattern
  #"Q-[0-9]{4}-[0-9]{2}-[0-9]{3}")

(def active-question-states #{"open" "in-discussion" "blocked"})

(defn- sorted-strings [xs]
  (vec (sort (set (remove nil? xs)))))

(defn- questions-text []
  (if (file? ".llm/memory/QUESTIONS.md")
    (slurp ".llm/memory/QUESTIONS.md")
    ""))

(defn- question-blocks []
  (let [lines (vec (str/split-lines (questions-text)))
        starts (keep-indexed (fn [idx line]
                               (when (re-find #"^## Q-[0-9]{4}-[0-9]{2}-[0-9]{3}:" line)
                                 idx))
                             lines)
        end-for (fn [idx]
                  (or (some #(when (> % idx) %) starts)
                      (count lines)))]
    (for [idx starts
          :let [title (nth lines idx)
                id (re-find question-id-pattern title)]]
      {:id id
       :path ".llm/memory/QUESTIONS.md"
       :line (inc idx)
       :title title
       :lines (subvec lines idx (end-for idx))})))

(defn- question-state [block]
  (some (fn [line]
          (second (re-find #"^- \*\*状態\*\*:.*\(([a-z-]+)\)" line)))
        (:lines block)))

(defn- questions-index []
  (->> (question-blocks)
       (map (fn [block]
              [(:id block) (assoc block :state (question-state block))]))
       (into (sorted-map))))

(defn- question-refs [item]
  (sorted-strings (re-seq question-id-pattern (or (:text item) ""))))

(defn- active-question-ref [questions item]
  (some (fn [id]
          (let [q (get questions id)]
            (when (contains? active-question-states (:state q))
              q)))
        (question-refs item)))

(defn- missing-question-refs [questions item]
  (->> (question-refs item)
       (remove #(contains? questions %))
       sorted-strings))

(defn- source-ref [item]
  (cond-> {:path "DESIGN.md"}
    (:line item) (assoc :line (:line item))
    (get-in item [:section :id]) (assoc :section (get-in item [:section :id]))
    (get-in item [:section :title]) (assoc :section-title (get-in item [:section :title]))))

(defn- section-id [item]
  (get-in item [:section :id]))

(defn- section-starts-with? [item prefix]
  (let [section (section-id item)]
    (or (= prefix section)
        (str/starts-with? (str section) (str prefix ".")))))

(defn- explicit-disposition [item]
  (when-let [[_ disposition] (re-find #"(?i)\bdisposition[ \t]*:[ \t]*(deferred|out-of-scope)\b"
                                      (or (:text item) ""))]
    (keyword (str/lower-case disposition))))

(defn- section-disposition [item]
  (cond
    (section-starts-with? item "2.2") :out-of-scope
    (section-starts-with? item "10") :deferred
    :else nil))

(defn- disposition-state [item]
  (or (section-disposition item)
      (when (explicit-disposition item)
        :unbacked-disposition)))

(defn- disposition-backing [item]
  (when-let [state (section-disposition item)]
    {:type :design-section
     :state state
     :path "DESIGN.md"
     :section (section-id item)
     :line (:line item)}))

(defn- blocker-state [questions item]
  (cond
    (active-question-ref questions item) :blocked-by-question
    (seq (missing-question-refs questions item)) :unresolved-blocker
    :else nil))

(defn- question-backing [questions item]
  (when-let [q (active-question-ref questions item)]
    {:type :question
     :id (:id q)
     :state (:state q)
     :path (:path q)
     :line (:line q)}))

(defn- blocker-info [questions item]
  (cond
    (question-backing questions item)
    {:backing (question-backing questions item)}

    (seq (missing-question-refs questions item))
    {:missing-questions (missing-question-refs questions item)}

    :else nil))

(defn- state-category [state]
  (cond
    (contains? complete-states state) :complete
    (contains? accounted-states state) :accounted
    (contains? red-states state) :red
    :else :unknown))

(defn- related-obligation-tests [trace obligation-ids]
  (->> obligation-ids
       (mapcat #(get-in trace [:impact :test-obligations % :tests]))
       vec))

(defn- satisfied? [implementation tests]
  (and (seq implementation) (seq tests)))

(defn- requirement-obligation [trace questions req]
  (let [id (:id req)
        constraint? (contains? constraint-kinds (:kind req))
        impact (get-in trace [:impact :requirements id] {})
        test-obligations (:test-obligations impact)
        implementation (vec (:implementation impact))
        tests (vec (concat (:tests impact)
                           (related-obligation-tests trace test-obligations)))
        state (or (disposition-state req)
                  (blocker-state questions req)
                  (cond
                    (satisfied? implementation tests) :satisfied
                    constraint? :manual-verification-required
                    (empty? implementation) :missing-boundary
                    :else :missing-test))
        backing (disposition-backing req)
        blocker (blocker-info questions req)]
    (cond-> {:id id
             :kind (if constraint? :constraint :requirement)
             :state state
             :category (state-category state)
             :text (:text req)
             :source (source-ref req)
             :requires []
             :trace {:implementation implementation
                     :tests tests
                     :test-obligations (vec test-obligations)}}
      backing (assoc :backing backing)
      blocker (assoc :blocker blocker)
      (and (explicit-disposition req) (not backing))
      (assoc :disposition {:requested (explicit-disposition req)
                           :backing nil}))))

(defn- use-case-obligation [trace questions uc]
  (let [id (:id uc)
        impact (get-in trace [:impact :use-cases id] {})
        test-obligations (:test-obligations impact)
        implementation (vec (:implementation impact))
        tests (vec (concat (:tests impact)
                           (related-obligation-tests trace test-obligations)))
        state (or (disposition-state uc)
                  (blocker-state questions uc)
                  (cond
                    (satisfied? implementation tests) :satisfied
                    (empty? implementation) :missing-boundary
                    :else :missing-test))
        backing (disposition-backing uc)
        blocker (blocker-info questions uc)]
    (cond-> {:id id
             :kind :use-case
             :state state
             :category (state-category state)
             :text (:text uc)
             :source (source-ref uc)
             :requires []
             :trace {:implementation implementation
                     :tests tests
                     :test-obligations (vec test-obligations)}}
      backing (assoc :backing backing)
      blocker (assoc :blocker blocker)
      (and (explicit-disposition uc) (not backing))
      (assoc :disposition {:requested (explicit-disposition uc)
                           :backing nil}))))

(defn- test-obligation [trace questions obligation]
  (let [id (:id obligation)
        impact (get-in trace [:impact :test-obligations id] {})
        tests (vec (:tests impact))
        state (or (disposition-state obligation)
                  (blocker-state questions obligation)
                  (if (seq tests) :satisfied :missing-test))
        backing (disposition-backing obligation)
        blocker (blocker-info questions obligation)]
    (cond-> {:id id
             :kind :test-obligation
             :state state
             :category (state-category state)
             :text (:text obligation)
             :source (source-ref obligation)
             :requires (sorted-strings (concat (:related-requirements obligation)
                                               (:related-use-cases obligation)))
             :trace {:tests tests
                     :related-requirements (vec (:related-requirements obligation))
                     :related-use-cases (vec (:related-use-cases obligation))}}
      backing (assoc :backing backing)
      blocker (assoc :blocker blocker)
      (and (explicit-disposition obligation) (not backing))
      (assoc :disposition {:requested (explicit-disposition obligation)
                           :backing nil}))))

(defn- orphan-trace-obligations [ir trace]
  (let [known-ucs (set (map :id (:use-cases ir)))
        known-tos (set (map :id (:test-obligations ir)))
        known-reqs (set (remove (set/union known-ucs known-tos)
                                (map :id (:requirements ir))))
        entries (:entries trace)
        orphan-for (fn [entry trace-key known kind-prefix]
                     (for [id (remove known (get entry trace-key))]
                       {:id id
                        :kind (keyword (str "orphan-" (name kind-prefix)))
                        :state (if (= :test (:kind entry)) :orphan-test :orphan-boundary)
                        :category :red
                        :text "Trace metadata references an ID not defined in DESIGN IR."
                        :source {:path (:path entry) :line (:line entry)}
                        :requires []
                        :trace {:entry entry}}))]
    (->> entries
         (mapcat (fn [entry]
                   (concat
                    (orphan-for entry :trace/requirements known-reqs :requirement)
                    (orphan-for entry :trace/use-cases known-ucs :use-case)
                    (orphan-for entry :trace/test-obligations known-tos :test-obligation))))
         (group-by (juxt :id :state :kind))
         vals
         (map first)
         (sort-by (juxt :kind :id))
         vec)))

(defn- requirement-items [ir]
  (let [non-requirement-ids (set/union (set (map :id (:use-cases ir)))
                                       (set (map :id (:test-obligations ir))))]
    (remove #(contains? non-requirement-ids (:id %)) (:requirements ir))))

(defn- obligations [ir trace questions]
  (vec
   (concat
    (map #(requirement-obligation trace questions %) (requirement-items ir))
    (map #(use-case-obligation trace questions %) (:use-cases ir))
    (map #(test-obligation trace questions %) (:test-obligations ir))
    (orphan-trace-obligations ir trace))))

(defn- dependency-map [items]
  (let [known (set (map :id items))]
    (into (sorted-map)
          (for [item items]
            [(:id item) (sorted-strings (filter known (:requires item)))]))))

(defn- dependent-map [deps]
  (reduce-kv (fn [m id parents]
               (reduce (fn [m* parent]
                         (update m* parent (fnil conj []) id))
                       m
                       parents))
             (sorted-map)
             deps))

(defn- dependency-depths [deps]
  (letfn [(depth [id visiting memo]
            (cond
              (contains? memo id)
              [memo (get memo id)]

              (contains? visiting id)
              [(assoc memo id 0) 0]

              :else
              (let [[memo* depths]
                    (reduce (fn [[memo' depths'] parent]
                              (let [[memo'' depth'] (depth parent (conj visiting id) memo')]
                                [memo'' (conj depths' depth')]))
                            [memo []]
                            (get deps id))]
                (let [d (if (seq depths) (inc (apply max depths)) 0)]
                  [(assoc memo* id d) d]))))]
    (first
     (reduce (fn [[memo _] id]
               (depth id #{} memo))
             [{} nil]
             (keys deps)))))

(defn- open-dependencies [item-by-id deps id]
  (->> (get deps id)
       (filter (fn [parent]
                 (not= :complete (:category (get item-by-id parent)))))
       sorted-strings))

(defn- attach-frontier-dag [items]
  (let [deps (dependency-map items)
        dependents (dependent-map deps)
        depths (dependency-depths deps)
        item-by-id (into {} (map (juxt :id identity) items))]
    (mapv (fn [item]
            (let [id (:id item)
                  requires (get deps id)
                  blocked-by (open-dependencies item-by-id deps id)]
              (assoc item
                     :frontier {:requires requires
                                :blocked-by blocked-by
                                :dependents (sorted-strings (get dependents id))
                                :depth (get depths id 0)})))
          items)))

(defn- by-category [items]
  (->> items
       (group-by :category)
       (map (fn [[k xs]] [k (vec (sort (map :id xs)))]))
       (into (sorted-map))))

(defn- state-counts [items]
  (->> items
       (map :state)
       frequencies
       (into (sorted-map))))

(defn- derivation-manifest [out-file generated-at]
  (derivation/make-manifest
   {:id :obligation-index
    :tool "gen-obligation-index"
    :output-path out-file
    :generator-path generator-path
    :tool-input-paths [helper-path]
    :input-paths [".llm/data/design-ir.edn"
                  ".llm/data/trace-index.edn"
                  ".llm/memory/QUESTIONS.md"
                  ".llm/repo-context.edn"]
    :input-policy {:untracked :error
                   :missing :explicit-empty}
    :generated-at generated-at
    :regenerate-command "./.llm/scripts/gen-obligation-index.sh"}))

(defn index
  ([] (index {}))
  ([{:keys [out-file generated-at]}]
   (let [out-file (or out-file default-out)
         ctx (repo-context)
         ir (design-ir)
         trace (trace-index)
         questions (questions-index)
         items (attach-frontier-dag (obligations ir trace questions))]
     (into (sorted-map)
           (derivation/with-manifest
            {:schema/version "obligation-index.1"
             :kind :obligation-index
             :generated-by "gen-obligation-index"
             :sources {:design-ir ".llm/data/design-ir.edn"
                       :trace-index ".llm/data/trace-index.edn"
                       :questions ".llm/memory/QUESTIONS.md"
                       :repo-context ".llm/repo-context.edn"}
             :repo-kind (:repo-kind ctx)
             :adoption-mode (:adoption-mode ctx)
             :summary {:total (count items)
                       :by-category (by-category items)
                       :by-state (state-counts items)}
             :obligations items}
            (derivation-manifest out-file generated-at))))))

(defn- render [data]
  (str ";; GENERATED - do not edit by hand.\n"
       ";; Source of truth: DESIGN.md, .llm/data/design-ir.edn, and Clojure :trace/* metadata\n"
       ";; Regenerate with: ./.llm/scripts/gen-obligation-index.sh\n"
       (with-out-str (pprint/pprint data))))

(defn- error! [& messages]
  (throw (ex-info (str/join "\n" messages) {})))

(defn- blocking-reds [data]
  (when (and (= :project (:repo-kind data))
             (= :complete (:adoption-mode data)))
    (->> (:obligations data)
         (filter #(= :red (:category %)))
         vec)))

(defn generate
  "Generate .llm/data/obligation-index.edn."
  [{:keys [out-file]}]
  (let [out-file (or out-file default-out)
        data (index {:out-file out-file})]
    (write-file! out-file (render data))
    (println (str "Generated " out-file))))

(defn check
  "Validate obligation-index drift and strict-mode red obligations."
  [{:keys [out-file]}]
  (let [out-file (or out-file default-out)
        generated-at (get-in (derivation/artifact-manifest (read-edn-if-exists out-file))
                             [:derivation/generated-at])
        data (index {:out-file out-file :generated-at generated-at})
        expected (render data)
        reds (blocking-reds data)
        accounted (filter #(= :accounted (:category %)) (:obligations data))
        freshness (derivation/freshness out-file)]
    (when-not (file? out-file)
      (error! (str "ERROR: " out-file " is missing. Run ./.llm/scripts/gen-obligation-index.sh.")))
    (when-not (= :fresh (:status freshness))
      (error! "ERROR: obligation-index derivation manifest is not fresh."
              (derivation/explain freshness)
              "Fix: ./.llm/scripts/gen-obligation-index.sh"))
    (when-not (= expected (slurp out-file))
      (error! (str "ERROR: " out-file " is not synchronized with design-ir/trace metadata.")
              "Fix: ./.llm/scripts/gen-obligation-index.sh"))
    (when (seq reds)
      (error! "ERROR: obligation coverage has red obligations in :adoption-mode :complete:"
              (str/join "\n" (map #(str "  " (:id %) " " (:state %)) reds))))
    (println (str "check-obligation-index: OK"
                  " (" (get-in data [:summary :total]) " obligations"
                  (when (seq accounted)
                    (str ", " (count accounted) " accounted"))
                  ")"))))

(def state-rank
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

(def kind-rank
  {:requirement 0
   :use-case 1
   :test-obligation 2
   :constraint 3
   :orphan-requirement 4
   :orphan-use-case 5
   :orphan-test-obligation 6})

(defn- frontier-items [data]
  (->> (:obligations data)
       (remove #(= :complete (:category %)))
       (sort-by (juxt #(if (seq (get-in % [:frontier :blocked-by])) 1 0)
                      #(get state-rank (:state %) 99)
                      #(get-in % [:frontier :depth] 0)
                      #(get kind-rank (:kind %) 99)
                      :id))
       vec))

(defn- location [item]
  (let [{:keys [path line section]} (:source item)]
    (str path
         (when line (str ":" line))
         (when (and section (not line)) (str " " section)))))

(defn- suggested-action [item]
  (case (:state item)
    :missing-boundary "add public boundary trace or move the obligation to a backed disposition"
    :missing-trace "connect implementation/test trace metadata to the DESIGN obligation"
    :missing-test "add a deftest with matching :trace/test-obligations"
    :manual-verification-required "record fresh procedural evidence or convert the obligation to a test-backed check"
    :blocked-by-question "resolve the linked QUESTIONS entry, then re-run the frontier"
    :orphan-boundary "remove the stale trace id or define the obligation in DESIGN"
    :orphan-test "remove the stale test trace id or define the obligation in DESIGN"
    :unbacked-disposition "move the obligation to its backing DESIGN section or remove the disposition override"
    "inspect the obligation state and backing artifacts"))

(defn- stale-derived-artifact []
  (->> (derivation/existing-artifacts)
       (map derivation/freshness)
       (remove #(= :fresh (:status %)))
       first))

(defn frontier
  "Print the current Work Frontier as a read-only projection."
  [_]
  (let [stale (stale-derived-artifact)
        _ (when stale
            (error! "ERROR: Work Frontier requires fresh derived artifacts."
                    (derivation/explain stale)
                    "Next: regenerate the stale artifact, then run ./.llm/scripts/check-derived-artifacts.sh"))
        data (read-edn-if-exists default-out)
        _ (when-not data
            (error! "ERROR: .llm/data/obligation-index.edn is missing."
                    "Next: ./.llm/scripts/gen-obligation-index.sh"))
        items (frontier-items data)]
    (println "== Work Frontier ==")
    (println "Source: obligation-index projection")
    (println "Total obligations:" (get-in data [:summary :total]))
    (if (seq items)
      (doseq [item (take 10 items)]
        (println "-" (name (:category item))
                 (name (:state item))
                 (:id item)
                 (str "(" (name (:kind item)) ", " (location item) ")"))
        (when-let [requires (seq (get-in item [:frontier :requires]))]
          (println "  requires:" (str/join ", " requires)))
        (when-let [blocked-by (seq (get-in item [:frontier :blocked-by]))]
          (println "  blocked-by:" (str/join ", " blocked-by)))
        (when-let [depth (get-in item [:frontier :depth])]
          (when (pos? depth)
            (println "  frontier-depth:" depth)))
        (println "  ->" (suggested-action item)))
      (println "- empty (all obligations are complete, or DESIGN has no obligations)"))))
