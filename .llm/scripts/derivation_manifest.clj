(ns derivation-manifest
  "Shared derivation manifest primitives for generated artifact freshness.

   A derivation manifest describes how a derived view was produced. It does not
   make the artifact authoritative; it only states whether the generated value is
   fresh for the observed inputs and generator source."
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.pprint :as pprint]
   [clojure.string :as str])
  (:import
   [java.security MessageDigest]
   [java.time Instant]))

(def schema-version "derivation.1")

(def manifest-key :artifact/manifest)

(def default-artifacts
  [{:path ".llm/data/design-ir.edn"
    :kind :embedded-edn}
   {:path ".llm/data/obligation-index.edn"
    :kind :embedded-edn}
   {:path "docs/BRICKS.md"
    :kind :sidecar
    :manifest-path ".llm/data/brick-map.manifest.edn"}
   {:path ".llm/data/brick-map.edn"
    :kind :sidecar
    :manifest-path ".llm/data/brick-map.manifest.edn"}
   {:path "docs/PROJECTS.md"
    :kind :sidecar
    :manifest-path ".llm/data/workspace-map.manifest.edn"}
   {:path "docs/WORKSPACE.md"
    :kind :sidecar
    :manifest-path ".llm/data/workspace-map.manifest.edn"}
   {:path ".llm/data/workspace-map.edn"
    :kind :sidecar
    :manifest-path ".llm/data/workspace-map.manifest.edn"}
   {:path "docs/TRACE.md"
    :kind :sidecar
    :manifest-path ".llm/data/trace-index.manifest.edn"}
   {:path ".llm/data/trace-index.edn"
    :kind :sidecar
    :manifest-path ".llm/data/trace-index.manifest.edn"}
   {:path ".llm/data/libs.edn"
    :kind :sidecar
    :manifest-path ".llm/data/lib-catalog.manifest.edn"}
   {:path ".llm/data/deprecated-libs.patterns"
    :kind :sidecar
    :manifest-path ".llm/data/lib-catalog.manifest.edn"}
   {:path ".llm/data/forbidden-requires.patterns"
    :kind :sidecar
    :manifest-path ".llm/data/lib-catalog.manifest.edn"}
   {:path ".llm/data/conflicts.patterns"
    :kind :sidecar
    :manifest-path ".llm/data/lib-catalog.manifest.edn"}])

(defn file? [path]
  (.isFile (io/file path)))

(defn directory? [path]
  (.isDirectory (io/file path)))

(defn now []
  (.toString (Instant/now)))

(defn- bytes->hex [bytes]
  (apply str (map #(format "%02x" (bit-and % 0xff)) bytes)))

(defn- sha256-bytes [bytes]
  (let [digest (MessageDigest/getInstance "SHA-256")]
    (.update digest bytes)
    (str "sha256:" (bytes->hex (.digest digest)))))

(defn sha256-string [s]
  (sha256-bytes (.getBytes (str s) "UTF-8")))

(defn file-digest [path]
  (when (file? path)
    (with-open [in (io/input-stream (io/file path))]
      (let [digest (MessageDigest/getInstance "SHA-256")
            buffer (byte-array 8192)]
        (loop []
          (let [n (.read in buffer)]
            (when (pos? n)
                (.update digest buffer 0 n)
              (recur))))
        (str "sha256:" (bytes->hex (.digest digest)))))))

(defn- directory-files [path]
  (->> (file-seq (io/file path))
       (filter #(.isFile %))
       (map #(.getPath %))
       sort
       vec))

(defn directory-digest [path]
  (when (directory? path)
    (sha256-string
     (pr-str
      (mapv (fn [file]
              {:path file
               :digest (file-digest file)})
            (directory-files path))))))

(defn observed-input [path]
  (cond
    (file? path)
    {:path path :digest (file-digest path)}

    (directory? path)
    {:path path :input/kind :directory :digest (directory-digest path)}

    :else
    {:path path :missing true}))

(defn- sort-entries [entries]
  (vec (sort-by (juxt :path #(or (:digest %) "") #(str (:missing %)))
                (or entries []))))

(defn- normalize-for-action-key [manifest]
  (-> manifest
      (dissoc :derivation/action-key :derivation/generated-at)
      (update :derivation/inputs sort-entries)
      (update :derivation/tool-inputs sort-entries)))

(defn action-key [manifest]
  (sha256-string (pr-str (normalize-for-action-key manifest))))

(defn make-manifest
  [{:keys [id tool output-path generator-path tool-input-paths input-paths
           observed-inputs observed-tool-inputs input-policy generated-at
           regenerate-command]}]
  (let [base (cond-> {:derivation/id id
                      :derivation/schema schema-version
                      :derivation/regime :derived-view
                      :derivation/tool tool
                      :derivation/generator (observed-input generator-path)
                      :derivation/inputs (vec (concat (mapv observed-input input-paths)
                                                       (or observed-inputs [])))
                      :derivation/input-policy (or input-policy {})
                      :derivation/output-path output-path
                      :derivation/generated-at (or generated-at (now))}
               (or (seq tool-input-paths) (seq observed-tool-inputs))
               (assoc :derivation/tool-inputs
                      (vec (concat (mapv observed-input tool-input-paths)
                                   (or observed-tool-inputs []))))

               regenerate-command
               (assoc :derivation/regenerate-command regenerate-command))]
    (assoc base :derivation/action-key (action-key base))))

(defn- refresh-observed-entry [entry]
  (cond
    (= :virtual (:input/kind entry))
    entry

    (:path entry)
    (observed-input (:path entry))

    :else
    entry))

(defn refresh-manifest [manifest]
  (make-manifest
   {:id (:derivation/id manifest)
    :tool (:derivation/tool manifest)
    :output-path (:derivation/output-path manifest)
    :generator-path (get-in manifest [:derivation/generator :path])
    :observed-tool-inputs (mapv refresh-observed-entry (:derivation/tool-inputs manifest))
    :observed-inputs (mapv refresh-observed-entry (:derivation/inputs manifest))
    :input-policy (:derivation/input-policy manifest)
    :generated-at (:derivation/generated-at manifest)
    :regenerate-command (:derivation/regenerate-command manifest)}))

(defn read-edn-artifact [path]
  (edn/read-string (slurp path)))

(defn artifact-manifest [artifact]
  (get artifact manifest-key))

(defn with-manifest [artifact manifest]
  (assoc artifact manifest-key manifest))

(defn artifact-entry [artifact]
  (if (map? artifact)
    artifact
    {:path artifact :kind :embedded-edn}))

(defn- entry-path [entry]
  (:path (artifact-entry entry)))

(defn- read-manifest [entry]
  (let [{:keys [path manifest-path]} (artifact-entry entry)]
    (if manifest-path
      (artifact-manifest (read-edn-artifact manifest-path))
      (artifact-manifest (read-edn-artifact path)))))

(defn- entries-by-path [entries]
  (into (sorted-map) (map (juxt :path identity) entries)))

(defn- changed-entries [old new key]
  (let [old-by-path (entries-by-path (key old))
        new-by-path (entries-by-path (key new))
        paths (sort (set (concat (keys old-by-path) (keys new-by-path))))]
    (vec
     (keep (fn [path]
             (let [old-entry (get old-by-path path)
                   new-entry (get new-by-path path)]
               (when (not= old-entry new-entry)
                 {:path path :recorded old-entry :current new-entry})))
           paths))))

(defn freshness [artifact]
  (let [entry (artifact-entry artifact)
        artifact-path (:path entry)
        manifest-path (:manifest-path entry)]
    (cond
      (not (file? artifact-path))
      {:status :missing
       :artifact/path artifact-path}

      (and manifest-path (not (file? manifest-path)))
      {:status :broken-manifest
       :artifact/path artifact-path
       :manifest/path manifest-path
       :reason "missing sidecar manifest"}

      :else
      (try
        (let [manifest (read-manifest entry)]
          (cond
            (nil? manifest)
            {:status :broken-manifest
             :artifact/path artifact-path
             :manifest/path manifest-path
             :reason (str "missing " manifest-key)}

            (not= schema-version (:derivation/schema manifest))
            {:status :broken-manifest
             :artifact/path artifact-path
             :manifest/path manifest-path
             :reason (str "unsupported schema " (pr-str (:derivation/schema manifest)))}

            :else
            (let [current (refresh-manifest manifest)
                  fresh? (= (:derivation/action-key manifest)
                            (:derivation/action-key current))]
              {:status (if fresh? :fresh :stale)
               :artifact/path artifact-path
               :manifest/path manifest-path
               :derivation/id (:derivation/id manifest)
               :recorded-action-key (:derivation/action-key manifest)
               :current-action-key (:derivation/action-key current)
               :regenerate-command (:derivation/regenerate-command manifest)
               :changed-generator (when (not= (:derivation/generator manifest)
                                              (:derivation/generator current))
                                    {:recorded (:derivation/generator manifest)
                                     :current (:derivation/generator current)})
               :changed-tool-inputs (changed-entries manifest current :derivation/tool-inputs)
               :changed-inputs (changed-entries manifest current :derivation/inputs)})))
        (catch Throwable e
          {:status :broken-manifest
           :artifact/path artifact-path
           :manifest/path manifest-path
           :reason (.getMessage e)})))))

(defn fresh? [artifact-path]
  (= :fresh (:status (freshness artifact-path))))

(defn explain [status]
  (case (:status status)
    :fresh
    (str "fresh " (:artifact/path status)
         " " (:recorded-action-key status))

    :missing
    (str "missing " (:artifact/path status))

    :broken-manifest
    (str "broken manifest " (:artifact/path status)
         ": " (:reason status))

    :stale
    (str "stale " (:artifact/path status)
         "\n  recorded: " (:recorded-action-key status)
         "\n  current:  " (:current-action-key status)
         (when-let [cmd (:regenerate-command status)]
           (str "\n  regenerate: " cmd))
         (when-let [g (:changed-generator status)]
           (str "\n  generator changed: " (pr-str g)))
         (when (seq (:changed-tool-inputs status))
           (str "\n  tool inputs changed: " (pr-str (:changed-tool-inputs status))))
         (when (seq (:changed-inputs status))
           (str "\n  inputs changed: " (pr-str (:changed-inputs status)))))

    (str (name (:status status)) " " (:artifact/path status))))

(defn- default-existing-artifacts []
  (vec (filter #(file? (entry-path %)) default-artifacts)))

(defn existing-artifacts []
  (default-existing-artifacts))

(defn check
  "Check freshness for generated artifacts with derivation manifests."
  [{:keys [artifacts]}]
  (let [paths (or (seq artifacts) (default-existing-artifacts))
        statuses (mapv freshness paths)
        failures (remove #(= :fresh (:status %)) statuses)]
    (doseq [status statuses]
      (println (str "check-derived-artifacts: " (explain status))))
    (when (seq failures)
      (throw (ex-info "ERROR: derived artifact freshness check failed."
                      {:failures failures})))
    (println (str "check-derived-artifacts: OK (" (count statuses) " artifacts)"))))

(defn inspect
  "Print the derivation manifest and freshness status for an artifact."
  [{:keys [artifact]}]
  (let [artifact (or artifact (first default-artifacts))
        entry (artifact-entry artifact)
        data (when (file? (:path entry))
               (if (:manifest-path entry)
                 (read-edn-artifact (:manifest-path entry))
                 (read-edn-artifact (:path entry))))]
    (println "== Derivation Artifact ==")
    (println "Artifact:" (:path entry))
    (println "Freshness:" (explain (freshness artifact)))
    (when-let [manifest (artifact-manifest data)]
      (println)
      (pprint/pprint manifest))))
