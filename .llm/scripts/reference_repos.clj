(ns reference-repos
  "Manage the .llm/reference-repos.edn allowlist of local Polylith repos that
   may be read as design comparison material (POLYLITH_GUIDE.md §9).

   This namespace only reads files. It never builds, runs, executes, or imports
   a referenced repo (§9.3 のセキュリティ境界)。add / list-entries / check は
   .llm/scripts/reference-repos.sh から呼ばれる -X エントリ。"
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [detect-repo-profile :as profile]))

(def allowlist-path ".llm/reference-repos.edn")
(def template-id "clojure-polylith-llm-starter")

;; allowlist ファイルが存在しない場合のみ使う最小ヘッダ。
;; 通常はテンプレート同梱の既存ファイルからヘッダをそのまま引き継ぐ。
(def ^:private default-header
  (str ";; .llm/reference-repos.edn — 参照を許可するローカル repo の allowlist（任意ファイル）\n"
       ";;\n"
       ";; reference-repos.sh add/list/check で管理する。詳細は POLYLITH_GUIDE.md §9。\n\n"))

(defn- abs-path [path]
  (.getCanonicalPath (.getAbsoluteFile (io/file path))))

(defn- read-allowlist
  "Return the parsed allowlist map, or nil when the file is absent."
  []
  (when (.isFile (io/file allowlist-path))
    (try
      (edn/read-string (slurp allowlist-path))
      (catch Exception e
        (throw (ex-info (str "Invalid EDN in " allowlist-path ": " (.getMessage e)) {}))))))

(defn- allowed-repos []
  (vec (:allowed-repos (read-allowlist))))

(defn- file-header
  "Leading comment/blank text of the allowlist file (everything before the map)."
  []
  (if (.isFile (io/file allowlist-path))
    (let [text (slurp allowlist-path)
          idx (str/index-of text "{")]
      (if idx (subs text 0 idx) default-header))
    default-header))

(defn- render-allowlist [repos]
  (if (seq repos)
    (str "{:allowed-repos\n ["
         (str/join "\n  " (map pr-str (sort (distinct repos))))
         "]}\n")
    "{:allowed-repos []}\n"))

(defn- write-allowlist! [repos]
  (spit allowlist-path (str (file-header) (render-allowlist repos))))

(defn- repo-context [repo-root]
  (let [f (io/file repo-root ".llm/repo-context.edn")]
    (when (.isFile f)
      (try (edn/read-string (slurp f)) (catch Exception _ nil)))))

(defn- template-derived?
  "True when repo-root's manifest is derived from clojure-polylith-llm-starter."
  [repo-root]
  (let [ctx (repo-context repo-root)]
    (= template-id (or (:template-name ctx) (:derived-from ctx)))))

(defn- polylith-repo? [repo-root]
  (= :polylith (:workspace-kind (profile/detect repo-root))))

(defn- validate-repo
  "Return a vector of problem strings for repo-root; empty when it qualifies."
  [repo-root]
  (cond
    (not (.isDirectory (io/file repo-root)))
    [(str "ディレクトリが存在しません: " repo-root)]

    (not (polylith-repo? repo-root))
    [(str "Polylith repo ではありません（workspace.edn / components+bases+projects なし）")]

    (not (template-derived? repo-root))
    [(str ".llm/repo-context.edn の :template-name / :derived-from が \""
          template-id "\" ではありません")]

    :else []))

(defn- brick-edns
  "Read each brick.edn under repo-root, returning name/authors/license summaries."
  [repo-root]
  (for [kind ["components" "bases"]
        :let [dir (io/file repo-root kind)]
        :when (.isDirectory dir)
        child (sort-by #(.getName %) (.listFiles dir))
        :when (.isDirectory child)
        :let [edn-file (io/file child "brick.edn")]
        :when (.isFile edn-file)
        :let [data (try (edn/read-string (slurp edn-file)) (catch Exception _ nil))]]
    {:path (str kind "/" (.getName child))
     :name (:brick/name data)
     :authors (:brick/authors data)
     :license (:brick/license data)}))

(defn- print-err [s]
  (binding [*out* *err*] (println s)))

(defn add
  "Add path to the allowlist after verifying it is a derived Polylith repo."
  [{:keys [path]}]
  (when (str/blank? (str path))
    (print-err "ERROR: add は :path 引数が必要です")
    (System/exit 2))
  (let [repo-root (abs-path path)
        problems (validate-repo repo-root)]
    (cond
      (seq problems)
      (do (print-err (str "ERROR: " repo-root " は参照 repo として登録できません:"))
          (doseq [p problems] (print-err (str "  - " p)))
          (System/exit 1))

      (some #(= repo-root (abs-path %)) (allowed-repos))
      (println (str "既に登録済みです: " repo-root))

      :else
      (do (write-allowlist! (conj (allowed-repos) repo-root))
          (println (str "参照 repo を登録しました: " repo-root))))))

(defn list-entries
  "Print the currently allowlisted reference repos."
  [_]
  (let [repos (allowed-repos)]
    (if (empty? repos)
      (println "参照可能 repo は未登録です（任意機能）。reference-repos.sh add <path> で登録します。")
      (do (println (str "参照可能 repo: " (count repos) " 件"))
          (doseq [r (sort repos)] (println (str "  - " r)))))))

(defn- check-entry
  "Validate one allowlist entry and print its status. Return 1 on problem, else 0."
  [repo]
  (let [repo-root (abs-path repo)
        problems (validate-repo repo-root)]
    (println "")
    (println (str "- " repo))
    (if (seq problems)
      (do (doseq [p problems] (print-err (str "  ERROR: " p)))
          1)
      (do (println (str "  OK: Polylith / " template-id " 系列"))
          (doseq [b (brick-edns repo-root)]
            (println (str "  brick " (:path b)
                           "  authors=" (pr-str (:authors b))
                           "  license=" (pr-str (:license b)))))
          0))))

(defn check
  "Verify every allowlist entry and surface its bricks (L0 判定材料)."
  [_]
  (let [repos (allowed-repos)]
    (if (empty? repos)
      (println "SKIP: 参照可能 repo は未登録です（任意機能）")
      (let [problem-count (reduce + 0 (map check-entry repos))]
        (if (pos? problem-count)
          (do (print-err (str "\nERROR: 無効な参照 repo エントリが " problem-count " 件あります"))
              (System/exit 1))
          (println (str "\ncheck-reference-repos: OK (" (count repos) " 件)")))))))
