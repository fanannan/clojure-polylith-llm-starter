(ns check-repo-context-consistency
  "Validate .llm/repo-context.edn consistency.

   This check focuses on manifest-internal invariants that should not depend on
   human attention: capability dependencies, adoption mode shape, and migration
   ledger consistency."
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(def manifest-path ".llm/repo-context.edn")
(def migrations-dir ".llm/migrations")

(def capability-deps
  {:deps-edn #{}
   :clj-kondo #{:deps-edn}
   :cljfmt #{:deps-edn}
   :malli #{:deps-edn}
   :polylith #{:deps-edn}
   :llm-guides #{}})

(def allowed-repo-kinds #{:template :project})
(def allowed-workspace-kinds #{:polylith :plain-clojure :unknown})
(def allowed-adoption-modes #{:retrofit :partial :complete})

(defn- slurp-if-exists [path]
  (let [f (io/file path)]
    (when (.isFile f)
      (slurp f))))

(defn- read-edn [path]
  (when-let [s (slurp-if-exists path)]
    (edn/read-string s)))

(defn- migration-files []
  (let [dir (io/file migrations-dir)]
    (if (.isDirectory dir)
      (->> (.listFiles dir)
           (filter #(.isFile %))
           (filter #(str/ends-with? (.getName %) ".edn")))
      [])))

(defn- known-migration-ids []
  (->> (migration-files)
       (map #(read-edn (.getPath %)))
       (keep :id)
       set))

(defn- error [errors msg]
  (conj errors (str "ERROR: " msg)))

(defn- warning [warnings msg]
  (conj warnings (str "WARN: " msg)))

(defn validate [manifest]
  (let [caps (set (:capabilities manifest))
        known-migrations (known-migration-ids)
        applied (set (:applied-migrations manifest))
        missing-deps (for [[cap deps] capability-deps
                           :when (contains? caps cap)
                           dep deps
                           :when (not (contains? caps dep))]
                       [cap dep])
        unknown-caps (remove #(contains? (set (keys capability-deps)) %) caps)
        unknown-applied (remove known-migrations applied)]
    (cond-> {:errors [] :warnings []}
      (not (contains? allowed-repo-kinds (:repo-kind manifest)))
      (update :errors error (str ":repo-kind が未知: " (pr-str (:repo-kind manifest))))

      (not (contains? allowed-workspace-kinds (:workspace-kind manifest)))
      (update :errors error (str ":workspace-kind が未知: " (pr-str (:workspace-kind manifest))))

      (not (contains? allowed-adoption-modes (:adoption-mode manifest)))
      (update :errors error (str ":adoption-mode が未知: " (pr-str (:adoption-mode manifest))))

      (and (= :template (:repo-kind manifest))
           (not= :complete (:adoption-mode manifest)))
      (update :errors error "template repo は :adoption-mode :complete でなければならない")

      (and (= :project (:repo-kind manifest))
           (= :plain-clojure (:workspace-kind manifest))
           (= :complete (:adoption-mode manifest)))
      (update :errors error ":workspace-kind :plain-clojure は Polylith 化前の一時状態なので :complete にできない")

      (seq missing-deps)
      (update :errors error (str ":capabilities の依存不足: "
                                 (pr-str (vec missing-deps))))

      (seq unknown-caps)
      (update :warnings warning (str "未知の capability: " (pr-str (vec unknown-caps))
                                     "。新 capability なら migration と検査対応を追加してください"))

      (and (= :project (:repo-kind manifest))
           (= :retrofit (:adoption-mode manifest)))
      (update :warnings warning ":adoption-mode :retrofit は一時状態です。propose-adoption-plan.sh で strict 化計画を確認してください")

      (seq unknown-applied)
      (update :warnings warning (str ":applied-migrations に local ledger 不在の ID があります: "
                                     (pr-str (vec unknown-applied)))))))

(defn run [_]
  (let [manifest (read-edn manifest-path)]
    (if-not manifest
      (do
        (println "WARN: .llm/repo-context.edn がありません。propose-repo-context.sh で候補を作成してください")
        (System/exit 0))
      (let [{:keys [errors warnings]} (validate manifest)]
        (doseq [line warnings] (println line))
        (doseq [line errors] (println line))
        (if (seq errors)
          (do
            (println (str "check-repo-context-consistency: FAILED (" (count errors) " errors, " (count warnings) " warnings)"))
            (System/exit 1))
          (do
            (println (if (seq warnings)
                       (str "check-repo-context-consistency: OK with " (count warnings) " WARN")
                       "check-repo-context-consistency: OK"))
            (System/exit 0)))))))
