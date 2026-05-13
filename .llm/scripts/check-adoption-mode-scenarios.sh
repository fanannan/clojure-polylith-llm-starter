#!/usr/bin/env bash
# .llm/scripts/check-adoption-mode-scenarios.sh
#
# :adoption-mode の段階挙動を小さい manifest fixture で検証する。
# テンプレ本体は :complete で運用されるため、:retrofit / :partial の
# 退行をこの self-check で捕捉する。

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$WORKSPACE_ROOT"

clj -Sdeps '{:paths [".llm/scripts"]}' -M -e '
(require (quote check-repo-context-consistency))

(let [strict-caps #{:deps-edn :clj-kondo :cljfmt :malli :polylith :llm-guides}
      result check-repo-context-consistency/validate
      assert-case (fn [label pred]
                    (when-not pred
                      (throw (ex-info (str "adoption mode scenario failed: " label) {})))
                    (println (str "OK: " label)))]
  (assert-case
   "template complete with strict capabilities"
   (let [{:keys [errors]} (result {:repo-kind :template
                                   :workspace-kind :polylith
                                   :adoption-mode :complete
                                   :capabilities strict-caps
                                   :applied-migrations #{}})]
     (empty? errors)))

  (assert-case
   "plain Clojure cannot be complete"
   (let [{:keys [errors]} (result {:repo-kind :project
                                   :workspace-kind :plain-clojure
                                   :adoption-mode :complete
                                   :capabilities strict-caps
                                   :applied-migrations #{}})]
     (some #(re-find #"plain-clojure" %) errors)))

  (assert-case
   "complete requires all strict capabilities"
   (let [{:keys [errors]} (result {:repo-kind :project
                                   :workspace-kind :polylith
                                   :adoption-mode :complete
                                   :capabilities (disj strict-caps :malli)
                                   :applied-migrations #{}})]
     (some #(re-find #"strict template capabilities" %) errors)))

  (assert-case
   "partial allows missing strict capabilities as WARN"
   (let [{:keys [errors warnings]} (result {:repo-kind :project
                                            :workspace-kind :polylith
                                            :adoption-mode :partial
                                            :capabilities (disj strict-caps :malli)
                                            :applied-migrations #{}})]
     (and (empty? errors)
          (some #(re-find #"partial" %) warnings))))

  (assert-case
   "retrofit is temporary and warns"
   (let [{:keys [errors warnings]} (result {:repo-kind :project
                                            :workspace-kind :polylith
                                            :adoption-mode :retrofit
                                            :capabilities strict-caps
                                            :applied-migrations #{}})]
     (and (empty? errors)
          (some #(re-find #"retrofit" %) warnings)))))
'
