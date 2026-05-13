(ns propose-adoption-plan
  "Produce a side-effect free adoption plan for an existing Clojure/Polylith repo.

   This is not a recommendation research tool. It only turns local repo signals
   and manifest state into ordered migration work for human approval."
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.pprint :as pprint]
   [detect-repo-profile :as profile]))

(def manifest-path ".llm/repo-context.edn")

(defn- slurp-if-exists [path]
  (let [f (io/file path)]
    (when (.isFile f)
      (slurp f))))

(defn- read-manifest []
  (when-let [s (slurp-if-exists manifest-path)]
    (try
      (edn/read-string s)
      (catch Exception _ nil))))

(defn- task [phase title severity evidence action]
  {:phase phase
   :title title
   :severity severity
   :evidence evidence
   :action action})

(defn- manifest-tasks [manifest detected]
  (cond-> []
    (nil? manifest)
    (conj (task :manifest
                "Create .llm/repo-context.edn"
                :required
                "manifest is missing"
                "Run propose-repo-context.sh, review with the human owner, then run apply-repo-context-migration.sh."))

    (and manifest (not= :project (:repo-kind manifest)))
    (conj (task :manifest
                "Confirm repo kind"
                :required
                (str "manifest :repo-kind is " (pr-str (:repo-kind manifest)))
                "For derived or retrofitted repos, change :repo-kind to :project after human approval."))

    (and manifest (nil? (:adoption-mode manifest)))
    (conj (task :manifest
                "Add adoption mode"
                :required
                ":adoption-mode is missing"
                "Start existing repos with :adoption-mode :retrofit."))

    (and manifest (empty? (:capabilities manifest)))
    (conj (task :manifest
                "Confirm capabilities"
                :required
                (str "detected capabilities: " (pr-str (:capabilities detected)))
                "Review detected capabilities and write the accepted set to .llm/repo-context.edn."))

    (and manifest (= :retrofit (:adoption-mode manifest)))
    (conj (task :adoption-mode
                "Promote out of retrofit"
                :required
                ":adoption-mode is :retrofit"
                "Treat retrofit as temporary inventory. Resolve required tasks, then promote to :partial and finally :complete."))))

(defn- polylith-tasks [detected]
  (let [signals (:signals detected)]
    (cond-> []
      (= :polylith (:workspace-kind detected))
      (conj (task :polylith
                  "Converge to strict template gates"
                  :required
                  "workspace.edn / components / bases / projects shape detected"
                  "Run check-workspace-integrity.sh and clj -M:poly check after manifest approval; treat :retrofit as temporary inventory before promoting to :partial and then :complete."))

      (and (= :polylith (:workspace-kind detected))
           (not (:projects-dir signals)))
      (conj (task :polylith
                  "Review projects directory"
                  :recommended
                  "workspace.edn exists but projects/ directory is missing"
                  "Decide the deploy project structure and create projects/ as part of Polylith alignment; keep :adoption-mode :retrofit only until that decision is made."))

      (and (= :polylith (:workspace-kind detected))
           (not (:cljfmt signals)))
      (conj (task :tooling
                  "Adopt cljfmt capability"
                  :required
                  "cljfmt config or alias was not detected"
                  "Add cljfmt configuration/alias as part of convergence to this template's strict formatting gate."))

      (and (= :polylith (:workspace-kind detected))
           (not (contains? (:capabilities detected) :malli)))
      (conj (task :contracts
                  "Adopt Malli contracts"
                  :required
                  "Malli dependency was not detected"
                  "Add Malli as part of convergence to this template's totality/contract discipline; enable interface contract checks after adoption.")))))

(defn- plain-clojure-tasks [detected]
  (cond-> []
    (= :plain-clojure (:workspace-kind detected))
    (conj (task :plain-clojure
                "Plan Polylith adoption"
                :required
                "deps.edn/src/test shape detected without Polylith workspace"
                "This template is intended for Polylith workspaces. Keep :adoption-mode :retrofit only while planning the conversion; create workspace.edn and components/bases/projects structure after human approval."))))

(defn plan []
  (let [manifest (read-manifest)
        detected (profile/detect ".")
        template-mode? (= :template (:repo-kind manifest))
        tasks (if template-mode?
                []
                (vec (concat (manifest-tasks manifest detected)
                             (polylith-tasks detected)
                             (plain-clojure-tasks detected))))]
    {:detected detected
     :manifest (select-keys manifest [:repo-kind :project-name :workspace-kind :adoption-mode :capabilities :applied-migrations])
     :note (when template-mode?
             "template mode: adoption planning is for derived/retrofitted repositories, so no project adoption tasks are proposed")
     :summary {:task-count (count tasks)
               :required (count (filter #(= :required (:severity %)) tasks))
               :recommended (count (filter #(= :recommended (:severity %)) tasks))
               :optional (count (filter #(= :optional (:severity %)) tasks))}
     :tasks tasks}))

(defn run [_]
  (println "Adoption plan proposal (side-effect free):")
  (pprint/pprint (plan)))
