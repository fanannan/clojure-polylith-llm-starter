# .llm/scripts/ — ワークスペース運用スクリプト群

本ディレクトリは、`.clj-kondo/` の custom hook では捕捉できない**設定ファイル・ディレクトリ構造・配布物整合性**の検査と、シェル展開が必要な運用コマンドのラッパー、および markdown 文書から機械可読な生成物を生成する Clojure script を収容する。各スクリプトは単独でも実行できるが、基本は `check-workspace-integrity.sh` が完了条件から一括で起動する。
∵ CLAUDE.md §5.5

**Convention 拡張**（既存は shell のみ、以降は Clojure script も追加）: `gen_*.clj` は markdown 文書（典型的には `STACK_GUIDE`）から EDN / patterns 生成物を生成する。`check_*.clj` は検査、`propose_*.clj` は人間承認前の判断材料提示、`apply_*.clj` は承認後の書き込みを担う。通常は shell wrapper から `clj -Sdeps '{:paths [".llm/scripts"]}' -X <ns>/<fn>` で起動する。`deps.edn` alias はテンプレ本体での直接実行用に残すが、既存 repo への retrofit 直後でも動くよう wrapper は alias に依存しない。

hook（`.clj-kondo/polyguard/`）と script（本ディレクトリ）の役割分担は保守者向け文書に置く。
∵ MAINTAINERS_GUIDE.md §5.10

## 機械化の 5 層構造（`MAINTAINERS_GUIDE.md §5.10` の要約）

| 層 | 実装手段 | 得意領域 |
|---|---|---|
| **L1** 構文・型・未使用 | clj-kondo 組み込み linter | AST 解析、命名空間解決、アリティ検査（段階 1/2 で 38 linter を有効化） |
| **L2** 本テンプレート固有パターン | `.clj-kondo/polyguard/hooks.clj` (custom hook) | form 単体の AST 解析 |
| **L3** スタイル・イディオム | Splint (`clj -M:lint-splint`) | `(= 0 x)` → `(zero? x)` 等のイディオム違反 |
| **L4** 設定・ディレクトリ構造 | 本ディレクトリの `.llm/scripts/*.sh` | EDN 構造、ファイル実在、file-level 照合（interface 契約・1 ファイル 1 ns・brick 登録漏れ・プレースホルダ残存・非推奨ライブラリ採用） |
| **L5** 依存脆弱性（時間軸） | clj-watson (`./.llm/scripts/check-vulnerabilities.sh`) | NIST NVD + GitHub Advisory Database |

**補完関係の例**:

- **非推奨ライブラリ**（timbre）: L1 の `:discouraged-var`がコード内使用、L4 の `check-deprecated-libs.sh`が `deps.edn` 採用宣言を検知
- **`m/=>` 契約**: L2 の `analyze-m=>`が form 単体、L4 の `check-interface-contracts.sh`が `interface.clj` 内の defn との対応
- **スタイル**: L1 は構文の正しさを、L3 は慣用の美しさを検知（両方通過が本テンプレートの期待）

clj-kondo hook は per-call の AST 解析が得意で、複数 form 間の照合（file-level）は苦手。shell script は逆。役割分担で両者の強みを活かす。

## スクリプト一覧

| スクリプト | 目的 |
|---|---|
| `check-workspace-integrity.sh` | 下記の workspace 整合性検査を束ねる総合検査（完了条件から呼ぶ） |
| `check-placeholders.sh` | `workspace.edn` / `deps.edn` のプレースホルダ `myorg.myapp` 残存検査。template repo では `.llm/repo-context.edn :repo-kind :template` を根拠に配布用 placeholder を許容 |
| `check-brick-registration.sh` | `components/` / `bases/` の brick が `deps.edn` に登録されているか検査 |
| `check-brick-map.sh` | 全 brick の `brick.edn` を検査し、`docs/BRICKS.md` / `.llm/data/brick-map.edn` が `brick.edn` / `interface.clj` からの生成結果と同期しているか検査 |
| `check-workspace-map.sh` | `projects/*/project.edn` と workspace/project 生成物を検査し、`docs/PROJECTS.md` / `docs/WORKSPACE.md` / `.llm/data/workspace-map.edn` の drift を検出 |
| `gen-design-ir.sh` | `DESIGN.md` と既存 `.llm/data/*.edn` 分析情報から `.llm/data/design-ir.edn` を生成 |
| `check-design-ir.sh` | `.llm/data/design-ir.edn` が `DESIGN.md` および既存分析 EDN と同期しているか検査 |
| `gen_design_ir.clj` | `gen-design-ir.sh` / `check-design-ir.sh` の Clojure 実装。明示 requirement / use case / acceptance item と `[REQ-001]` / `[UC-1]` trace を抽出し、constraint と実装 requirement を分けて brick-map / workspace-map / libs と照合 |
| `check-trace-metadata.sh` | Clojure コード / テストコードの `:trace/*` metadata を `.llm/data/design-ir.edn` と照合。実装コード側は stable public boundary のみ許可し、AC/TO は `deftest` 側へ限定。`:adoption-mode :complete` では未対応 test obligation も失敗 |
| `check_trace_metadata.clj` | `check-trace-metadata.sh` の Clojure 実装。top-level form を読み、public boundary `defn` と `deftest` の var metadata / attr-map に置かれた `:trace/requirements` / `:trace/use-cases` / `:trace/test-obligations` を検査。空 ID・重複 ID・related IDs 不整合も検出 |
| `gen-trace-index.sh` | `design-ir.edn` と Clojure `:trace/*` metadata から `docs/TRACE.md` / `.llm/data/trace-index.edn` を生成 |
| `check-trace-index.sh` | `docs/TRACE.md` / `.llm/data/trace-index.edn` が `design-ir.edn` と trace metadata からの生成結果と同期しているか検査 |
| `gen_trace_index.clj` | trace index 生成 / 検査の Clojure 実装。requirement / use case / test obligation ごとの implementation / test 対応と impact index を作る |
| `trace-impact.sh` | `trace-index.edn` を検索し、要件・受入基準・公開関数・変更差分から、影響する public boundary・test・test obligation を表示 |
| `trace_impact.clj` | `trace-impact.sh` の Clojure 実装。DESIGN 更新前後の探索、commit 前の変更差分確認、session briefing の trace health に使う |
| `evidence.sh` | Structural Evidence workflow の主入口。`status` / `predict` / `declare` / `close` で session 開始、着手前、residual 宣言、close 直前の evidence state を扱う |
| `derive-change-scope.sh` | git diff、repo-kind 別 derivation rules、brick-map / workspace-map / trace-index / design-ir / lib-catalog から Structural Evidence の actual scope / archetype / required evidence / 関連 context を導出する。LLM の scope 自己申告を正本にしないための入口 |
| `inspect-derivation.sh` | `derive-change-scope.sh` の導出理由を path ごとに表示する。matched rule、plane、archetype、public boundary、required evidence を説明する debug / 教材用 view |
| `propose-review-packet.sh` | `.llm/work/` に Review Fatigue Packet の EDN view と Markdown view を生成する。これは生成 view であり、Authority source ではない |
| `check-residual-declared.sh` | closed packet の LLM-declared residual が `none` または具体値で明示されているかを検査する。自動 `none` 埋めを禁止する close 前チェック |
| `check-structural-evidence-self-test.sh` | Structural Evidence derivation rules の fixture self-test。template ADR 禁止、project interface change、DESIGN spec change の代表ケースを検査する |
| `structural_evidence.clj` | Structural Evidence MVP の Clojure 実装。repo-kind 分岐、evidence tier、none regulator、Review Fatigue Packet 生成を扱う |
| `check-deprecated-libs.sh` | `STACK_GUIDE.md` に埋め込まれた `;; lib-catalog` EDN block 由来の非推奨ライブラリを検知（`.llm/data/deprecated-libs.patterns` を読む） |
| `check-forbidden-requires.sh` | `STACK_GUIDE.md` に埋め込まれた `;; lib-catalog` EDN block 由来の非推奨 namespace を検知（`.llm/data/forbidden-requires.patterns` を読む） |
| `check-conflicting-libs.sh` | `STACK_GUIDE.md` に埋め込まれた `;; lib-catalog` EDN block 由来の併用禁止ペアを検知（`.llm/data/conflicts.patterns` を読む） |
| `check-doc-references.sh` | Markdown 間参照が `¤ / ∵ / ⚠` で型付けされているか検査。通常は default scope、保守監査では `--all` |
| `gen_lib_catalog.clj` | `STACK_GUIDE.md` に埋め込まれた `;; lib-catalog` EDN block 群を合成し `.llm/data/{libs.edn, deprecated-libs.patterns, forbidden-requires.patterns, conflicts.patterns}` を生成（`clj -X:gen-lib-catalog`）。schema 検証 + uniqueness 検査付き |
| `gen_brick_map.clj` | `components/*/brick.edn` / `bases/*/brick.edn` と `interface.clj` から `docs/BRICKS.md` / `.llm/data/brick-map.edn` を生成。group-first view と `:groups` index を出力し、component/base の意味違反、重複 capability、base の未提供 capability 参照、`:brick/not-for` 衝突、要求 ID 対応、任意 `:brick/group` の形式、capability と公開 API 名の対応も検査。group 由来の再分割 smell は advisory warning に留める |
| `gen_workspace_map.clj` | `projects/*/project.edn` / `workspace.edn` / `deps.edn` / `brick.edn` から `docs/PROJECTS.md` / `docs/WORKSPACE.md` / `.llm/data/workspace-map.edn` を生成。project の deploy intent、entrypoint、includes、deps との整合を検査 |
| `propose-brick-edn.sh` | `brick.edn` を持たない既存 brick に対し、`interface.clj` から分かる範囲で skeleton 案を表示する移行補助（書き込みなし） |
| `ensure-brick-map.sh` | 欠落した `brick.edn` skeleton を自動作成し、`docs/BRICKS.md` / `.llm/data/brick-map.edn` を再生成する。TODO は警告として表示 |
| `propose-project-edn.sh` | `project.edn` を持たない登録済み project に対し、`projects/<name>/deps.edn` から分かる範囲で skeleton 案を表示する移行補助（書き込みなし） |
| `ensure-workspace-map.sh` | 欠落した `project.edn` skeleton を自動作成し、`docs/PROJECTS.md` / `docs/WORKSPACE.md` / `.llm/data/workspace-map.edn` を再生成する。TODO は警告として表示 |
| `check-interface-contracts.sh` | `interface.clj` の全公開 `defn` に対応する `m/=>` 契約があるか検査 |
| `check-test-instrumentation.sh` | `interface_test.clj` が `:once` fixture で Malli instrumentation を有効化しているか検査 |
| `check_test_instrumentation.clj` | `check-test-instrumentation.sh` の Clojure 実装。`use-fixtures :once` と fixture 定義を結び付けて `malli.dev/start!` を検査 |
| `check-single-ns-per-file.sh` | 1 つの `.clj` / `.cljc` / `.cljs` ファイルに `(ns ...)` が複数ないか検査 |
| `check-vulnerabilities.sh` | `clj-watson` による依存脆弱性スキャン（release 前必須、完了条件外） |
| `lint-import-hooks.sh` | 依存ライブラリ提供の `clj-kondo` hook を `.clj-kondo/configs/` に取り込む |
| `session-briefing.sh` | セッション起動時の状態ブリーフィング。`.llm/repo-context.edn` から `:repo-kind` を読み TEMPLATE MAINTENANCE / PROJECT モードを判定（conflict 最優先、bootstrap 完了痕跡 + `:template` で non-blocking ERROR）。モード別に表示内容を切り替える（CLAUDE.md §8.0 実装着手前の確認の機械化バックアップ） |
| `check-mode-scope.sh` | テンプレ保守 vs 派生プロジェクトの所有権境界違反を機械検出。`.llm/repo-context.edn` を EDN として読み、`:project-owned` 配下のテンプレ保守マーカー混入、`:section-scoped` の section 跨ぎ違反等を報告。CLAUDE.md §1.2.1 機械化の実装 |
| `check-adr-dir-empty.sh` | TEMPLATE モードで `.llm/memory/adr/` に `README.md` / `template.md` 以外の実 ADR が残っていないか検査 |
| `check-archive-staleness.sh` | maintainer discussion archive entry の staging schema、30 日超 open、吸収先 link 切れを検査 |
| `check-no-dead-adr-refs.sh` | TEMPLATE モードの live tree に、撤去済みテンプレ ADR slug への参照が残っていないか検査 |
| `detect-repo-profile.sh` | 既存 Clojure / Polylith repo の形状を副作用なしで検出し、`:workspace-kind` / `:capabilities` 候補を EDN 出力 |
| `detect_repo_profile.clj` | `detect-repo-profile.sh` の Clojure 実装。`deps.edn` / `workspace.edn` / `.clj-kondo` / `.cljfmt.edn` / Malli 依存を検出 |
| `propose-repo-context.sh` | 既存テンプレ利用者・既存 Clojure / Polylith repo 向けに `.llm/repo-context.edn` 候補を表示（副作用なし） |
| `propose_repo_context.clj` | `propose-repo-context.sh` と `apply-repo-context-migration.sh` の Clojure 実装。人間確認前は候補表示のみ、`:write true` で manifest 書き込み |
| `propose-template-migrations.sh` | `.llm/migrations/` と `.llm/repo-context.edn :applied-migrations` を比較し、未適用 migration の判断材料を表示（副作用なし） |
| `propose_template_migrations.clj` | `propose-template-migrations.sh` の Clojure 実装。git 履歴ではなく migration id ledger で比較 |
| `propose-adoption-plan.sh` | 既存 repo の local signals と manifest から、次に確認すべき移行作業順を提示（副作用なし、推奨調査ではない） |
| `propose_adoption_plan.clj` | `propose-adoption-plan.sh` の Clojure 実装。`detect-repo-profile` の結果を作業計画に変換 |
| `llm-template-adopt.sh` | 既存 repo adoption の統合入口。detect / manifest proposal / migration proposal / adoption plan を順に表示し、承認後 apply へ誘導する（副作用なし） |
| `check-repo-context-consistency.sh` | `.llm/repo-context.edn` の capability 依存関係、adoption mode、migration ledger 参照を検査 |
| `check_repo_context_consistency.clj` | `check-repo-context-consistency.sh` の Clojure 実装 |
| `check-adoption-mode-scenarios.sh` | テンプレ本体では実運用されにくい `:retrofit` / `:partial` / `:complete` の manifest 段階挙動を fixture で検査 |
| `template-tests/check-map-scenarios.sh` | テンプレート自身の Brick / Project / Workspace Map E2E。synthetic repo で生成・移行・エラー検出・自力修復を検査する。通常ゲート外 |
| `template-tests/check-design-ir-scenarios.sh` | DESIGN IR 生成・drift 検出・既存分析 EDN 連携の E2E。通常ゲート外 |
| `apply-repo-context-migration.sh` | 人間承認後に `.llm/repo-context.edn` を作成する migration wrapper。既定では `APPLY` 入力を要求 |
| `install-llm-template.sh` | 本テンプレート未導入の既存 repo に `.llm/` / root guide を持ち込む dry-run first の installer。`--apply` でも既存ファイルは上書きせず candidate を作る |
| `repl-eval.sh` | 稼働中 nREPL に eval / load-file を送る LLM 向け client（CLAUDE.md §9 Live Workbench Protocol）。`.nrepl-port` 自動発見、永続 session 再利用（`.nrepl-session`）、`--expr` / `--load-file` / `--interrupt` / `--describe` / `--reset-session` / `--fresh` 対応 |
| `repl_eval.clj` | repl-eval.sh の Clojure 実装（`clj -X:repl-eval` の exec-fn）。nREPL 標準 op（`eval` / `load-file` / `interrupt` / `describe` / `ls-sessions` / `clone`）を subcommand で提供、bounded printing（10000 chars/response）、file/line metadata 常時付与、process 跨ぎ request-id 永続化で確実な `--interrupt` を実現。接続時に `NREPL_PORT` / `.nrepl-port` の食い違い、workspace root 不一致、`dev.user/status` capability 不一致を検出して wrong JVM への接続を防ぐ |

## 運用タイミング

### 完了条件（`CLAUDE.md §5.5`）から起動

`clj -M:poly check` と `clj -M:poly test :all` の間に以下を挟む:

```bash
./.llm/scripts/check-workspace-integrity.sh
```

これで brick 登録 / Brick Map 生成物 / Project・Workspace Map 生成物 / DESIGN IR 生成物 / プレースホルダ / 非推奨ライブラリ / interface 契約 / test instrumentation / 1 ファイル 1 ns の検査 + 併存検査 + `:local/root` / `:projects` 実在検査が一括で走る。個別スクリプトを手動で呼ぶ必要はない。

### release 前の追加検査（週次 CI / release 時）

完了条件には含めないが、時間軸を跨いだ脆弱性検知として以下を実行する:

```bash
./.llm/scripts/check-vulnerabilities.sh
```

NVD API key 推奨（`https://nvd.nist.gov/developers/request-an-api-key`）。環境変数 `NVD_API_KEY` に設定すると高速化される。

### テンプレート保守 E2E（通常ゲート外）

`.llm/scripts/template-tests/` は、テンプレート自身の配布・移行・生成・検査を確認する E2E シナリオテストを置く。派生プロジェクトのアプリケーションテストではない。

map generator / checker / migration script を変更した時、またはテンプレート release 前に実行する。

```bash
./.llm/scripts/template-tests/check-map-scenarios.sh
```

この検査は `/tmp` に synthetic Polylith-like repos を作成する。日常の `check-workspace-integrity.sh` には含めない。

### hook 取り込みの起動タイミング（`lint-import-hooks.sh`）

`tools.deps` の `:main-opts` はシェル展開されないため、以下のタイミングで手動実行が必要:

- 初回セットアップ（`BOOTSTRAP_GUIDE.md §2.9`）
- brick `deps.edn` に新ライブラリ追加後
- 推奨ライブラリ採用後
- `clj -M:outdated` による最新化後

```bash
./.llm/scripts/lint-import-hooks.sh
```

本スクリプトを完了条件に含めない理由は、新ライブラリを採用するたびに実行するのが本来の運用で、毎回の完了条件検査に入れると遅くなるため。新ライブラリ採用のたびにユーザが明示的に実行する運用が正しい。

### セッション起動時のブリーフィング（`session-briefing.sh`）

`session-briefing.sh` は pass/fail 系の検査とは役割が異なる**状態提示スクリプト**。CLAUDE §8.0 で定めた「実装着手前に DESIGN / KNOWLEDGE / QUESTIONS / ADR を確認する」運用を機械化バックアップする。
∵ CLAUDE.md §8.0

- **Claude Code**: `.claude/settings.json` の `SessionStart` hook で起動時に自動実行、出力は system-reminder として LLM の文脈に注入される
- **Codex / 他エージェント**: SessionStart hook 機構がないため `AGENTS.md` の指示に従い、LLM が起動直後に `bash .llm/scripts/session-briefing.sh` を手動実行する
- **手動実行**: 任意のタイミングで `bash .llm/scripts/session-briefing.sh` を実行し、現時点の状態を確認できる

本スクリプトは副作用ゼロ（stdout のみ）、終了コードは常に 0。`check-workspace-integrity.sh` のような重検査は呼ばず、フェーズ判定・未対応(open) Q 抽出・最新 ADR 抽出・`git log -5` に限定して軽量に保つ。

REPL 状態節の `TCP 接続確認済` は「その port が listen している」ことだけを示す。古い JVM が生きているケースを弾く最終防衛線は `repl-eval.sh` / `repl_eval.clj` の workspace identity check である。

### 既存 repo への migration / retrofit

既存テンプレ利用者、または本テンプレート未導入の Clojure / Polylith repo では、移行状態を `.llm/repo-context.edn` に集約する。自動検出は候補生成までで、manifest 反映は人間承認後に限る。

plain Clojure repo への導入は、Polylith 化へ向かう retrofit として扱う。`:workspace-kind :plain-clojure` は検出された出発点であり、完成状態ではない。

既存 Polylith repo への導入は、本テンプレートの厳密な規律へ合流する retrofit として扱う。既存規約の永続互換ではない。
∵ MAINTAINERS_GUIDE.md §7.6
∵ MAINTAINERS_GUIDE.md §7.7

```bash
# 既に .llm/scripts がある repo
./.llm/scripts/llm-template-adopt.sh

# 個別確認したい場合:
./.llm/scripts/detect-repo-profile.sh
./.llm/scripts/propose-repo-context.sh
./.llm/scripts/propose-template-migrations.sh
./.llm/scripts/propose-adoption-plan.sh
./.llm/scripts/apply-repo-context-migration.sh

# まだテンプレート未導入の repo（テンプレート repo 側から実行）
./.llm/scripts/install-llm-template.sh --target /path/to/repo
./.llm/scripts/install-llm-template.sh --target /path/to/repo --apply
```

`:adoption-mode :retrofit` の間、`check-workspace-integrity.sh` は検査失敗を WARN として提示し、作業開始を block しない。`:partial` / `:complete` へ昇格した後は、manifest の `:capabilities` に含まれる検査が通常の gate になる。`:partial` は accepted capability の gate を有効化した移行中状態、`:complete` は strict template 準拠完了状態である。plain Clojure repo では Polylith 化計画を、既存 Polylith repo では本テンプレートの strict gate への合流計画を立てるための一時状態である。

Malli / cljfmt / clj-kondo / Polylith は本テンプレートの必須基盤である。既存 Polylith repo で未検出の場合、`propose-adoption-plan.sh` は strict gate への required task として提示する。plain Clojure repo を採用対象にする場合も、最終状態は Polylith 化済みの strict template 準拠であり、plain Clojure のまま留まる運用は定義しない。

## 実装規律

### Shell script

- すべて `#!/usr/bin/env bash` + `set -euo pipefail`（Bash、厳格モード）
- ワークスペースルートへの `cd` は `SCRIPT_DIR/../..` で解決（`.llm/scripts/` の親の親がリポジトリルート）
- Babashka 不要、`grep` / `awk` / `sed` / `clojure` CLI のみ
- プラットフォーム依存: Unix 前提（macOS / Linux）。Windows サポートは将来検討

### Clojure script (`gen_*.clj`)

- shell wrapper から `clj -Sdeps '{:paths [".llm/scripts"]}' -X <ns>/<fn>` で起動し、既存 repo の `deps.edn` alias に依存しない
- テンプレ本体の利便性として、必要に応じて `deps.edn` に `:gen-<topic>` / `:check-<topic>` alias を追加する
- 依存は root `:deps` を継承（`-X` の意味論）。追加依存が必要な場合のみ alias 内 `:extra-deps` で最小限に
- Malli schema で入力を厳格検証、違反は明示的に error 終了
- 生成物は `.llm/data/` に配置、ヘッダに `;; GENERATED — do not edit by hand` を入れる 
- `check-workspace-integrity.sh` に「一時領域に再生成 → diff で drift 検知」のステップを追加し、元文書と生成物の同期を保証

`.llm/data/` 配下は source 文書からの生成物である。テンプレート本体では `clj -X:gen-lib-catalog` や `gen-design-ir.sh` で再生成し、`check-workspace-integrity.sh` が drift を検出する。派生プロジェクトは通常そのまま消費するだけでよいが、STACK_GUIDE、DESIGN、capability 定義を派生側で意図的に変更するなら、対応する生成コマンドで `.llm/data/` を再生成し、source 文書変更と同一コミットにまとめる。

### Structural Evidence View

Structural Evidence View は、LLM が scope / evidence を自己申告する代わりに、git diff・repo-kind・Polylith 構造・生成 index から review 用 view を導出する仕組みである。目的は trust score ではなく、次セッションが検証済みの足場を復元するための Review Fatigue Packet を作ること。

導出対象は touched path の分類だけではない。存在する場合は `brick-map.edn`、`workspace-map.edn`、`trace-index.edn`、`design-ir.edn`、`libs.edn` も読み、affected project、trace-derived tests、design coverage gap、dependency context、関連 QUESTIONS / KNOWLEDGE / ADR / maintainer archive を同じ packet に surface する。

主運用:

```bash
./.llm/scripts/evidence.sh status
./.llm/scripts/evidence.sh predict --task 2026-05-15-example --intent "change intent"
./.llm/scripts/propose-review-packet.sh --task-id 2026-05-15-example
./.llm/scripts/evidence.sh declare --task 2026-05-15-example --all-none
./.llm/scripts/evidence.sh close --task 2026-05-15-example
```

低レベル debug:

```bash
./.llm/scripts/derive-change-scope.sh
./.llm/scripts/inspect-derivation.sh
./.llm/scripts/check-residual-declared.sh --packet .llm/work/2026-05-15-example.edn
./.llm/scripts/check-structural-evidence-self-test.sh
```

生成物:

- `.llm/work/<task-id>.edn`: 機械可読の generated view
- `.llm/work/<task-id>.md`: 人間が読む Review Fatigue Packet

EDN の `:schema/version` は `structural-evidence.N` 系で管理する。検討段階の仮称や release 名を schema / artifact 名に使わない。

詳細な最小手順:
¤ .llm/guide/STRUCTURAL_EVIDENCE_QUICKSTART.md

`.llm/work/` は active generated view であり、git 管理しない。closed evidence record を残す場合は、派生プロジェクトの privacy / commit policy に従って `.llm/evidence/closed/` 等に移す。Structural Evidence View は第 5 の正本ではなく、Authority / Structure / Index / Verification plane への索引である。

`session-briefing.sh` は Evidence Plane として active packet / closed record / residual pending を表示する。packet は生成して終わりではなく、次セッションの読み始めに必ず surface される inter-session memory として扱う。

## 新しい検査の追加手順

1. `.llm/scripts/check-<topic>.sh` を作成（終了コード 0 / 1 で成否表現、エラー時は人間に読めるメッセージ）
2. `check-workspace-integrity.sh` の `run_step` に 1 行追加
3. 本 README の「スクリプト一覧」表を更新
4. 新しい観点・判断根拠は `MAINTAINERS_GUIDE.md §5.12`（linter 継続点検規律）または §7 の staging model に従って記録

## Markdown 参照監査

`check-doc-references.sh` は、Markdown から別の Markdown を指す参照が marker 付き独立行になっているかを検査する。

- `¤`: 実行前に読む必須参照
- `∵`: 根拠・背景参照
- `⚠`: 問題発生時のみ参照

通常運用では default scope を検査する。default scope は、常時読まれる root 文書・bootstrap/memory/script/template README など、テンプレートの主要導線に限定した高速検査である。

```bash
./.llm/scripts/check-doc-references.sh
```

保守時の全体監査では `--all` を使う。`--all` は tracked/untracked を問わず repo 内の Markdown を広く走査するが、`.git/` と `.llm/memory/archive/` は除外する。

```bash
./.llm/scripts/check-doc-references.sh --all
```

## 非採用事項

- **Babashka による実装**: 必須層を拡張しないため不採用
- **`.llm/scripts/new-brick.sh`（brick 追加自動化）**: EDN 機械編集は sed では脆弱、rewrite-clj は Clojure 起動コスト大。代わりに `check-brick-registration.sh` で**不完全さを検知**する方向に切り替えた
