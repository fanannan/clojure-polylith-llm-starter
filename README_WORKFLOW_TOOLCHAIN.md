# README_WORKFLOW_TOOLCHAIN.md — 作業ループとツールチェーン

本文書は、テンプレート本体と派生プロジェクトで異なる作業ループを、支えるツールチェーンと対応づけて読むためのユーザー向け説明である。

README は初見の入口であり、本文書は「実際にどのループを回すのか」「どの tool が何を保証するのか」を理解するための補助文書である。テンプレートの動作制御文書でも、自動生成文書でもない。日常作業の正本は CLAUDE、各手順の正本は対応 guide / script 側にある。

¤ CLAUDE.md
¤ .llm/guide/RUNBOOK_STRUCTURAL_EVIDENCE.md
¤ .llm/guide/TEMPLATE_USAGE_GUIDE.md

---

## 1. まず分ける軸

作業ループは、先に repo mode で分ける。

| mode | 判定元 | 主な作業 | 決定記録 |
|---|---|---|---|
| テンプレート本体 | `.llm/repo-context.edn :repo-kind :template` | 配布物、guide、script、生成器、template-only E2E の保守 | maintainer archive |
| 派生プロジェクト | `.llm/repo-context.edn :repo-kind :project` | product 仕様、brick 実装、test、運用知識の蓄積 | ADR / KNOWLEDGE / QUESTIONS / DESIGN |

この区別を曖昧にしない。テンプレート本体で project-owned 領域を触らず、派生プロジェクトでは `.llm/template-only/` を完了後に残さない。

---

## 2. 共通の制御面

どのループでも、最初に現在状態を機械から読む。

```bash
bash .llm/scripts/session-briefing.sh
./.llm/scripts/evidence.sh what-now
```

`session-briefing.sh` は mode、所有権、次に読む文書、Evidence Plane を短く出す。`evidence.sh what-now` は active view、staged diff、未宣言 residual、stale record などから次の 1 action を返す。

編集後は staged diff を正本にして Structural Evidence gate を通す。

```bash
git add <changed-files>
./.llm/scripts/check-evidence-gate.sh --staged
```

save-required な差分では、必要に応じて `declare` / `run` / `close` まで進める。Review Fatigue Packet は正本ではなく生成 view であり、新しい requirement / knowledge / decision を中で定義しない。

---

## 3. テンプレート本体: テスト時のループ

テンプレート本体のテストは、アプリケーション機能ではなく、配布物・規約・生成器・検査 script が壊れていないかを見る。

```text
session briefing
  → 変更対象を確認
  → task-specific な軽い検査
  → 必要な template-only E2E
  → workspace / evidence gate
  → 保守判断を maintainer archive または現行 guide / script へ吸収
```

代表コマンド:

```bash
bash .llm/scripts/session-briefing.sh
./.llm/scripts/evidence.sh what-now
./.llm/scripts/check-workspace-integrity.sh
```

変更対象に応じて template-only E2E を追加する。

```bash
./.llm/template-only/tests/check-session-briefing-scenarios.sh
./.llm/template-only/tests/check-design-ir-scenarios.sh
./.llm/template-only/tests/check-map-scenarios.sh
./.llm/template-only/tests/check-trace-metadata-scenarios.sh
./.llm/template-only/tests/check-obligation-frontier-scenarios.sh
```

`.llm/template-only/tests/` は重い保守 E2E であり、日常 gate ではない。`session-briefing.sh` の L0 Template Test Recommendation は候補提示であって、自動実行ではない。

---

## 4. テンプレート本体: ベンチマーク時のループ

テンプレートの benchmark は性能計測ではない。LLM がテンプレートの導線どおりに動けるかを、gate 間の自律 segment として観測する。

```text
scenario を選ぶ
  → setup-run.sh で demo repo / observer store を作る
  → agent を gate 間だけ走らせる
  → L0 / L1 承認点で止める
  → marker / diff / observation を採点
  → テンプレート改善点を maintainer archive へ戻す
```

代表入口:

```bash
./.llm/template-only/benchmark/setup-run.sh \
  --scenario <scenario-name> \
  --agent codex \
  --model <model-name>
```

harness 自体は LLM なしの smoke で検査する。

```bash
./.llm/template-only/tests/check-benchmark-setup-smoke.sh
```

観測結果は model 能力値の点推定ではない。テンプレート文書、session briefing、bootstrap gate、script のどこに摩擦があったかを見るための材料である。

---

## 5. 派生プロジェクト: 実装時のループ

派生プロジェクトの実装は、DESIGN から public boundary、test、evidence へ接続するループである。

```text
session briefing
  → DESIGN / KNOWLEDGE / QUESTIONS / ADR を確認
  → trace / brick map / workspace map で影響範囲を見る
  → REPL で小さく確認
  → code / interface / m=> / test を編集
  → poly check / poly test
  → Structural Evidence close
```

代表コマンド:

```bash
bash .llm/scripts/session-briefing.sh
./.llm/scripts/evidence.sh what-now
./.llm/scripts/repl-eval.sh --expr '(dev.user/status)'
```

編集後:

```bash
./.llm/scripts/repl-eval.sh --load-file <changed-file>
clj -M:poly check
clj -M:poly test
./.llm/scripts/check-workspace-integrity.sh
```

`interface.clj`、`m/=>` 契約、外部入力 schema、runtime wiring、defrecord / protocol を触る場合は、`poly test` の前に REPL eval で観察する。REPL で観察した挙動は `interface_test.clj` などへテスト化し、CLI gate へ進める。

base / project 作成、依存追加、公開 API 変更、仕様変更などは承認 gate を挟む。自走できるのは、合意済み前提の一貫適用と検証までである。

---

## 6. 派生プロジェクト: テスト時のループ

派生プロジェクトのテストは、DESIGN の受入基準から出る test obligation と実際の `deftest` を結びつける。

```text
DESIGN の受入基準を確認
  → design-ir / trace impact を確認
  → fixture / seed を REPL で観察
  → deftest に trace metadata を付ける
  → 対象 test / poly test
  → trace index / workspace integrity
  → poly test :all
```

代表コマンド:

```bash
./.llm/scripts/gen-design-ir.sh
./.llm/scripts/trace-impact.sh --health
./.llm/scripts/gen-trace-index.sh
clj -M:poly test
./.llm/scripts/check-workspace-integrity.sh
clj -M:poly test :all
```

`test obligation` は「何を検証すべきか」であり、実テスト完了ではない。`deftest` の `:trace/test-obligations` と実際の assertion が必要である。

越境ユースケースでは、fixture を想像で書かない。先に REPL で境界 state を観察する。

```clojure
(safe-reset!)
(seed-<uc>!)
(probe ...)
```

`seed-all!` は convenience であり、test の前提は原則として個別の `seed-<uc>!` を明示する。

---

## 7. 派生プロジェクト: 性能 benchmark の位置づけ

派生プロジェクトの性能 benchmark は、テンプレート benchmark とは別物である。DESIGN の性能受入基準から生える test obligation / evidence であり、固定 dataset、seed、実行条件、環境を記録する。

| 用途 | 代表手段 |
|---|---|
| 関数・アルゴリズムの micro benchmark | criterium |
| Web API / service の負荷確認 | wrk 等の外部負荷ツール |
| 起動成果物の代表動線確認 | smoke recipe |

性能 benchmark は日常の `poly test` とは分ける。実行コストが高いものは、release 前、性能変更時、性能受入基準に関わる変更時に evidence として記録する。

---

## 8. 支えるツールチェーン

| 層 | ツール | 役割 |
|---|---|---|
| 前提確認 | `check-toolchain.sh` | JVM / `clj` / git / 任意 bb の過不足検出。インストールはしない |
| mode / ownership | `.llm/repo-context.edn`, `session-briefing.sh`, `check-mode-scope.sh` | template / project、所有権、次 action surface を提示・検査 |
| 仕様・記憶 | `DESIGN.md`, `KNOWLEDGE.md`, `QUESTIONS.md`, ADR, maintainer archive | 仕様、現在知識、未決、決定履歴、テンプレ保守判断を分離 |
| 仕様生成 view | `gen-design-ir.sh`, `check-design-ir.sh` | DESIGN から requirement / UC / test obligation を抽出し freshness を検査 |
| 構造生成 view | `gen_brick_map.clj`, `gen_workspace_map.clj` | brick / project / workspace の capability と deploy intent を検査 |
| trace | `gen-trace-index.sh`, `trace-impact.sh`, `check-trace-metadata.sh` | 仕様 ID と public boundary / deftest の対応を検査 |
| REPL | nREPL, `repl-eval.sh`, `dev.user/*` | live state、Malli instrumentation、runtime wiring を同一ターンで確認 |
| 契約 | Malli `m/=>` | interface 境界の入出力を fail-closed にする |
| Polylith | `clj -M:poly check`, `clj -M:poly test` | brick 依存、影響範囲テスト、全体回帰 |
| 静的検査 | clj-kondo, `.clj-kondo/polyguard`, Splint | 構文、namespace、テンプレ固有パターン、idiom を検査 |
| 形式 | cljfmt | format drift を防ぐ |
| 技術選定 | `gen_lib_catalog.clj`, `check-deprecated-libs.sh`, `check-forbidden-requires.sh`, `check-conflicting-libs.sh` | STACK_GUIDE の lib catalog から採用・禁止・衝突を検査 |
| 証跡 | `evidence.sh`, `check-evidence-gate.sh`, `check-evidence-boundary.sh` | staged diff から required evidence を導出し、packet drift を止める |
| git hook | `.githooks/pre-commit`, `install-git-hooks.sh` | Claude / Codex / human が同じ commit gate を通る |
| release / 定期 | `check-vulnerabilities.sh` | clj-watson による時間軸の依存脆弱性確認 |

`bb` は任意の高速化手段であり、必須ではない。`LLM_CLJ_RUNTIME=auto|bb|clj` で Clojure script runner の runtime を選ぶ。bb 実行で失敗した場合に暗黙 fallback しないのは、evidence の再現性を曖昧にしないためである。

---

## 9. 誤用しやすい境界

- README は入口であり、日常作業の正本ではない
- generated docs / `.llm/data/*.edn` は正本ではなく derived view
- Review Fatigue Packet は正本ではなく close 用の生成 view
- template benchmark と派生プロジェクト性能 benchmark は別物
- `poly test` は回帰確認であり、REPL instrumentation による契約検証を置き換えない
- stable tag 未整備なら、影響範囲 test に頼らず `clj -M:poly test :all` に倒す
- project の設計判断は ADR、template の保守判断は maintainer archive に流す
