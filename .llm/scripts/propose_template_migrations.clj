(ns propose-template-migrations
  "Propose pending template migrations for a derived project.

   This script is side-effect free. It compares .llm/template-version.edn and
   .llm/migrations/*.edn with .llm/repo-context.edn."
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.pprint :as pprint]
   [clojure.string :as str]))

(def manifest-path ".llm/repo-context.edn")
(def version-path ".llm/template-version.edn")
(def migrations-dir ".llm/migrations")

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
           (filter #(str/ends-with? (.getName %) ".edn"))
           (sort-by #(.getName %)))
      [])))

(defn- read-migrations []
  (->> (migration-files)
       (map #(read-edn (.getPath %)))
       (sort-by :id)
       vec))

(defn- applies? [repo-kind migration]
  (let [targets (:applies-to migration)]
    (or (nil? targets)
        (contains? targets repo-kind))))

(defn proposal []
  (let [version (read-edn version-path)
        manifest (read-edn manifest-path)
        migrations (read-migrations)
        repo-kind (:repo-kind manifest)
        applied (set (:applied-migrations manifest))
        manifest-missing? (nil? manifest)
        effective-kind (if manifest-missing? :project repo-kind)
        applicable (filter #(applies? effective-kind %) migrations)
        pending (remove #(contains? applied (:id %)) applicable)]
    {:template version
     :repo {:repo-kind repo-kind
            :project-name (:project-name manifest)
            :template-source-revision (:template-source-revision manifest)
            :applied-migrations applied}
     :status (if manifest-missing? :manifest-missing :compared)
     :known-migrations (mapv :id migrations)
     :pending (mapv #(select-keys % [:id :title :summary :checks :manual-steps :requires-human-approval])
                    pending)}))

(defn run [_]
  (let [p (proposal)]
    (cond
      (= :manifest-missing (:status p))
      (do
        (println "Manifest missing; showing project migrations as judgment material.")
        (pprint/pprint p))

      (seq (:pending p))
      (do
        (println "Pending template migrations:")
        (pprint/pprint p))

      :else
      (do
        (println "No pending template migrations.")
        (pprint/pprint p)))))
