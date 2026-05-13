(ns detect-repo-profile
  "Detect the current repository shape for template adoption/migration.

   The result is advisory. It is intentionally side-effect free so humans can
   approve the proposed manifest before any file is written."
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(defn- file [root path]
  (io/file root path))

(defn- exists? [root path]
  (.exists (file root path)))

(defn- dir? [root path]
  (.isDirectory (file root path)))

(defn- slurp-if-exists [root path]
  (let [f (file root path)]
    (when (.isFile f)
      (slurp f))))

(defn- read-edn-file [root path]
  (when-let [s (slurp-if-exists root path)]
    (try
      (edn/read-string s)
      (catch Exception _ nil))))

(defn- workspace-top-namespace [root]
  (or (some-> (read-edn-file root "workspace.edn") :top-namespace)
      (when-let [s (slurp-if-exists root "workspace.edn")]
        (second (re-find #":top-namespace\s+\"([^\"]+)\"" s)))))

(defn- git-root-name [root]
  (let [f (.getCanonicalFile (io/file root))]
    (.getName f)))

(defn- deps-files [root]
  (let [root-file (io/file root)]
    (->> (file-seq root-file)
         (filter #(.isFile %))
         (filter #(= "deps.edn" (.getName %)))
         (remove #(str/includes? (.getPath %) (str java.io.File/separator ".git" java.io.File/separator))))))

(defn- deps-contain? [root pattern]
  (boolean
   (some (fn [f]
           (try
             (re-find pattern (slurp f))
             (catch Exception _ nil)))
         (deps-files root))))

(defn- polylith? [root]
  (or (exists? root "workspace.edn")
      (and (dir? root "components")
           (dir? root "bases")
           (dir? root "projects"))))

(defn- plain-clojure? [root]
  (or (exists? root "deps.edn")
      (dir? root "src")
      (dir? root "test")))

(defn detect
  "Return an advisory profile map for root."
  ([] (detect "."))
  ([root]
   (let [root (or root ".")
         workspace-kind (cond
                          (polylith? root) :polylith
                          (plain-clojure? root) :plain-clojure
                          :else :unknown)
         capabilities (cond-> #{}
                        (exists? root "deps.edn") (conj :deps-edn)
                        (polylith? root) (conj :polylith)
                        (dir? root ".clj-kondo") (conj :clj-kondo)
                        (or (exists? root ".cljfmt.edn")
                            (deps-contain? root #"cljfmt")) (conj :cljfmt)
                        (deps-contain? root #"metosin/malli|malli/malli|malli\.core") (conj :malli)
                        (and (dir? root ".llm/guide")
                             (exists? root "CLAUDE.md")) (conj :llm-guides))
         project-name (or (workspace-top-namespace root)
                          (git-root-name root)
                          "myorg.myapp")]
     {:workspace-kind workspace-kind
      :project-name project-name
      :capabilities capabilities
      :signals {:deps-edn (exists? root "deps.edn")
                :workspace-edn (exists? root "workspace.edn")
                :components-dir (dir? root "components")
                :bases-dir (dir? root "bases")
                :projects-dir (dir? root "projects")
                :clj-kondo-dir (dir? root ".clj-kondo")
                :cljfmt (or (exists? root ".cljfmt.edn")
                            (deps-contain? root #"cljfmt"))
                :llm-guides (dir? root ".llm/guide")}})))

(defn run [{:keys [root]}]
  (prn (detect (or root "."))))
