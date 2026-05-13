(ns check-mode-scope
  "Check template/project mode boundaries from .llm/repo-context.edn.

   This script treats .llm/repo-context.edn as the SSOT. The shell wrapper only
   invokes this namespace; ownership paths are not duplicated in shell code."
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(def manifest-path ".llm/repo-context.edn")

(def template-marker
  #"本テンプレート|テンプレ自身|テンプレート自身|MAINTAINERS_GUIDE")

(defn- slurp-if-exists [path]
  (let [f (io/file path)]
    (when (.exists f)
      (slurp f))))

(defn- read-manifest []
  (when-let [s (slurp-if-exists manifest-path)]
    (edn/read-string s)))

(defn- project-dirs []
  (let [p (io/file "projects")]
    (when (.isDirectory p)
      (->> (.listFiles p)
           (filter #(.isDirectory %))
           seq))))

(defn- has-bootstrap-traces? []
  (or (when-let [workspace (slurp-if-exists "workspace.edn")]
        (not (str/includes? workspace "myorg.myapp")))
      (boolean (project-dirs))))

(defn- tracked-changed? [path]
  (let [proc (-> (ProcessBuilder. ["git" "status" "--porcelain" "--" path])
                 (.redirectErrorStream true)
                 (.start))
        out  (slurp (.getInputStream proc))
        code (.waitFor proc)]
    (and (zero? code) (not (str/blank? out)))))

(defn- list-files [root]
  (let [f (io/file root)]
    (cond
      (not (.exists f)) []
      (.isFile f) [(.getPath f)]
      :else (->> (file-seq f)
                 (filter #(.isFile %))
                 (map #(.getPath %))))))

(defn- glob-regex [pattern]
  (let [quoted (-> pattern
                   (str/replace "." "\\.")
                   (str/replace "/" "\\/")
                   (str/replace "NNNN-*" "[0-9][0-9][0-9][0-9]-[^/]*")
                   (str/replace "**" ".*")
                   (str/replace "*" "[^/]*"))]
    (re-pattern (str "^" quoted "$"))))

(defn- files-for-project-pattern [pattern]
  (cond
    (= pattern ".llm/memory/adr/NNNN-*.md")
    (->> (list-files ".llm/memory/adr")
         (filter #(re-matches #"\.llm/memory/adr/[0-9][0-9][0-9][0-9]-[^/]*\.md" %)))

    (str/starts-with? pattern "components/")
    (list-files "components")

    (str/starts-with? pattern "bases/")
    (list-files "bases")

    (str/starts-with? pattern "projects/")
    (list-files "projects")

    :else
    (->> (list-files ".")
         (filter #(re-matches (glob-regex pattern) %)))))

(defn- text-file? [path]
  (boolean (re-find #"\.(clj|cljc|cljs|edn|md|txt|sh)$" path)))

(defn- contains-pattern? [path pattern]
  (and (text-file? path)
       (when-let [s (slurp-if-exists path)]
         (boolean (re-find pattern s)))))

(defn- section-range [content n plus?]
  (let [lines (str/split-lines content)
        start-re (re-pattern (str "^## " n "\\."))
        next-re  #"^## [0-9]+\."
        start-idx (first (keep-indexed #(when (re-find start-re %2) %1) lines))]
    (when start-idx
      (let [tail (subvec (vec lines) (inc start-idx))
            end-offset (when-not plus?
                         (first (keep-indexed #(when (re-find next-re %2) %1) tail)))
            body (if (and end-offset (not plus?))
                   (subvec (vec lines) (inc start-idx) (+ (inc start-idx) end-offset))
                   (subvec (vec lines) (inc start-idx)))]
        (str/join "\n" body)))))

(defn- section-text [path section-id]
  (when-let [content (slurp-if-exists path)]
    (when-let [[_ n plus] (re-matches #"§([0-9]+)(\+)?" section-id)]
      (section-range content n (boolean plus)))))

(defn- warn [warnings msg]
  (conj warnings msg))

(defn- check-template-mode [manifest]
  (let [ownership (:ownership manifest)
        project-owned (:project-owned ownership)
        section-scoped (:section-scoped ownership)]
    (cond-> []
      true
      (into
       (mapcat (fn [pattern]
                 (->> (files-for-project-pattern pattern)
                      (filter #(contains-pattern? % template-marker))
                      (mapcat (fn [path]
                                [(str "WARN: " path ": テンプレ保守マーカー検出（project-owned 領域にテンプレ自身の決定が混入の疑い）")
                                 "WARN:   -> テンプレ保守決定は .llm/memory/archive/maintainer-discussions/ へ"]))))
               project-owned))

      true
      (into
       (mapcat (fn [[path scopes]]
                 (for [section (:project scopes)
                       :let [text (section-text path section)]
                       :when (and text (re-find template-marker text))]
                   (str "WARN: " path " " section ": テンプレ保守マーカー検出（project section に混入の疑い）")))
               section-scoped)))))

(defn- check-project-mode [manifest]
  (let [template-owned (get-in manifest [:ownership :template-owned])]
    (->> template-owned
         (filter tracked-changed?)
         (mapcat (fn [path]
                   [(str "WARN: " path " に変更あり（派生プロジェクトでテンプレ規約を変更している兆候）")
                    "WARN:   -> 変更が必要ならテンプレ側に PR/Issue で還元するのが原則"]))
         vec)))

(defn run [_]
  (let [manifest (read-manifest)
        repo-kind (:repo-kind manifest)]
    (cond
      (nil? manifest)
      (do
        (println "ERROR: .llm/repo-context.edn 不在（旧テンプレート由来の派生プロジェクトなら :repo-kind :project の manifest を追加してください）")
        (System/exit 0))

      (and (= repo-kind :template) (has-bootstrap-traces?))
      (do
        (println "ERROR: check-mode-scope: manifest が :repo-kind :template を主張するが bootstrap 完了痕跡あり")
        (println "  対処: BOOTSTRAP_GUIDE.md に従い manifest を :project に transform")
        (System/exit 1))

      (= repo-kind :template)
      (let [warnings (check-template-mode manifest)]
        (doseq [line warnings] (println line))
        (println (if (seq warnings)
                   (str "check-mode-scope: " (count warnings) " WARN (template モード)")
                   "check-mode-scope: OK (template モード)"))
        (System/exit 0))

      (= repo-kind :project)
      (let [warnings (check-project-mode manifest)]
        (doseq [line warnings] (println line))
        (println (if (seq warnings)
                   (str "check-mode-scope: " (count warnings) " WARN (project モード)")
                   "check-mode-scope: OK (project モード)"))
        (System/exit 0))

      :else
      (do
        (println (str "check-mode-scope: SKIP（未知の :repo-kind " (pr-str repo-kind) "）"))
        (System/exit 0)))))
