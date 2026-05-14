(ns check-archive-staleness
  "Validate maintainer-discussions archive staging entries.

   The archive is a temporary process-log area. Entries must follow the schema
   defined in MAINTAINERS_GUIDE.md §7, and absorbed entries must point to
   existing files / sections."
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str])
  (:import
   [java.time LocalDate]
   [java.time.temporal ChronoUnit]))

(def archive-root ".llm/memory/archive/maintainer-discussions")
(def open-warn-days 30)

(defn- slurp-if-exists [path]
  (let [f (io/file path)]
    (when (.exists f)
      (slurp f))))

(defn- repo-kind []
  (:repo-kind (edn/read-string (or (slurp-if-exists ".llm/repo-context.edn") "{}"))))

(defn- archive-files []
  (let [root (io/file archive-root)]
    (if-not (.exists root)
      []
      (->> (file-seq root)
           (filter #(.isFile %))
           (map #(.getPath %))
           (filter #(str/ends-with? % ".md"))
           (remove #(str/ends-with? % "/README.md"))
           sort))))

(defn- entry-blocks [path]
  (let [lines (str/split-lines (slurp path))
        starts (keep-indexed #(when (re-find #"^## MD-[0-9]{4}-[0-9]{2}-[0-9]{3}:" %2) %1) lines)
        end-for (fn [idx]
                  (or (some #(when (> % idx) %) starts)
                      (count lines)))]
    (for [idx starts]
      {:path path
       :title (nth lines idx)
       :lines (subvec (vec lines) idx (end-for idx))})))

(defn- field [entry label]
  (let [re (re-pattern (str "^- \\*\\*" label "\\*\\*: ?(.*)$"))]
    (some (fn [line]
            (when-let [[_ v] (re-matches re line)]
              (str/trim v)))
          (:lines entry))))

(def allowed-states #{"open" "absorbed" "retained-process"})

(defn- parse-date [s]
  (try
    (LocalDate/parse s)
    (catch Exception _ nil)))

(defn- old-open? [date-str]
  (when-let [d (parse-date date-str)]
    (>= (.between ChronoUnit/DAYS d (LocalDate/now)) open-warn-days)))

(defn- targets [absorbed-into]
  (re-seq #"`([^`]+)`" (or absorbed-into "")))

(defn- target-path-section [target]
  (let [[_ path section] (re-matches #"([^ ]+)(?: +(§[0-9]+(?:\.[0-9]+)*))?" target)]
    [path section]))

(defn- file-exists? [path]
  (.exists (io/file path)))

(defn- section-exists? [path section]
  (if-not section
    true
    (when-let [content (slurp-if-exists path)]
      (let [n (second (re-matches #"§([0-9]+(?:\.[0-9]+)*)" section))]
        (boolean
         (or (re-find (re-pattern (str "(?m)^##+ +" (java.util.regex.Pattern/quote n) "(\\.| |$)")) content)
             (re-find (re-pattern (str "§" (java.util.regex.Pattern/quote n))) content)))))))

(defn- check-target [entry target]
  (let [[path section] (target-path-section target)]
    (cond
      (str/blank? path)
      [(str "WARN: " (:path entry) " " (:title entry) ": 吸収先 target が読めません: " target)]

      (not (file-exists? path))
      [(str "WARN: " (:path entry) " " (:title entry) ": 吸収先 file が存在しません: " path)]

      (not (section-exists? path section))
      [(str "WARN: " (:path entry) " " (:title entry) ": 吸収先 section が見つかりません: " target)]

      :else [])))

(defn- check-entry [entry]
  (let [state (field entry "状態")
        created (field entry "作成日")
        absorbed-into (field entry "吸収先")
        reason (field entry "保持理由")
        target-values (map second (targets absorbed-into))]
    (concat
     (when-not (contains? allowed-states state)
       [(str "WARN: " (:path entry) " " (:title entry) ": 状態 must be open | absorbed | retained-process")])
     (when-not (parse-date created)
       [(str "WARN: " (:path entry) " " (:title entry) ": 作成日 must be YYYY-MM-DD")])
     (when (and (= state "open") (old-open? created))
       [(str "WARN: " (:path entry) " " (:title entry) ": open のまま "
             open-warn-days " 日以上経過")])
     (when (and (= state "absorbed") (empty? target-values))
       [(str "WARN: " (:path entry) " " (:title entry) ": absorbed entry must have 吸収先")])
     (when (and (= state "retained-process") (str/blank? reason))
       [(str "WARN: " (:path entry) " " (:title entry) ": retained-process entry must have 保持理由")])
     (mapcat #(check-target entry %) target-values))))

(defn run [_]
  (if (not= :template (repo-kind))
    (do
      (println "check-archive-staleness: skipped (project mode)")
      (System/exit 0))
    (let [entries (mapcat entry-blocks (archive-files))
          warnings (mapcat check-entry entries)]
      (doseq [w warnings] (println w))
      (cond
        (seq warnings)
        (do
          (println (str "check-archive-staleness: FAILED (" (count warnings) " WARN)"))
          (System/exit 1))

        (empty? entries)
        (do
          (println "check-archive-staleness: OK (no archive entries)")
          (System/exit 0))

        :else
        (do
          (println (str "check-archive-staleness: OK (" (count entries) " entries)"))
          (System/exit 0))))))
