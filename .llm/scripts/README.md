# .llm/scripts/ — ワークスペース運用スクリプト群

本ディレクトリは、`.clj-kondo/` の custom hook では捕捉できない**設定ファイル・ディレクトリ構造・配布物整合性**の検査と、シェル展開が必要な運用コマンドのラッパーを収容する。各スクリプトは単独でも実行できるが、基本は `check-workspace-integrity.sh` が完了条件（`CLAUDE.md §5.5`）から一括で起動する。

hook（`.clj-kondo/polyguard/`）と script（本ディレクトリ）の役割分担は `MAINTAINERS_GUIDE.md §5.10` を参照。

## 機械化の 5 層構造（`MAINTAINERS_GUIDE.md §5.10` の要約）

| 層 | 実装手段 | 得意領域 |
|---|---|---|
| **L1** 構文・型・未使用 | clj-kondo 組み込み linter | AST 解析、命名空間解決、アリティ検査（段階 1/2 で 38 linter を有効化） |
| **L2** 本テンプレート固有パターン | `.clj-kondo/polyguard/hooks.clj` (custom hook) | form 単体の AST 解析 |
| **L3** スタイル・イディオム | Splint (`clj -M:lint-splint`) | `(= 0 x)` → `(zero? x)` 等のイディオム違反 |
| **L4** 設定・ディレクトリ構造 | 本ディレクトリの `.llm/scripts/*.sh` | EDN 構造、ファイル実在、file-level 照合（・・・・） |
| **L5** 依存脆弱性（時間軸） | clj-watson (`./.llm/scripts/check-vulnerabilities.sh`) | NIST NVD + GitHub Advisory Database |

**補完関係の例**:

- **非推奨ライブラリ**（timbre）: L1 の `:discouraged-var`がコード内使用、L4 の `check-deprecated-libs.sh`が `deps.edn` 採用宣言を検知
- **`m/=>` 契約**: L2 の `analyze-m=>`が form 単体、L4 の `check-interface-contracts.sh`が `interface.clj` 内の defn との対応
- **スタイル**: L1 は構文の正しさを、L3 は慣用の美しさを検知（両方通過が本テンプレートの期待）

clj-kondo hook は per-call の AST 解析が得意で、複数 form 間の照合（file-level）は苦手。shell script は逆。役割分担で両者の強みを活かす。

## スクリプト一覧

| スクリプト | 目的 | 対応項目 |
|---|---|---|
| `check-workspace-integrity.sh` | 下記 5 種を束ねる総合検査（完了条件から呼ぶ） | |
| `check-placeholders.sh` | `workspace.edn` / `deps.edn` のプレースホルダ `myorg.myapp` 残存検査 | |
| `check-brick-registration.sh` | `components/` / `bases/` の brick が `deps.edn` に登録されているか検査 | |
| `check-deprecated-libs.sh` | `STACK_GUIDE.md §8.2` 非推奨ライブラリの `deps.edn` 採用宣言検査 | |
| `check-interface-contracts.sh` | `interface.clj` の全公開 `defn` に対応する `m/=>` 契約があるか検査 | |
| `check-single-ns-per-file.sh` | 1 つの `.clj` / `.cljc` / `.cljs` ファイルに `(ns ...)` が複数ないか検査 | |
| `check-vulnerabilities.sh` | `clj-watson` による依存脆弱性スキャン（release 前必須、完了条件外） | |
| `lint-import-hooks.sh` | 依存ライブラリ提供の `clj-kondo` hook を `.clj-kondo/configs/` に取り込む | |
| `session-briefing.sh` | セッション起動時の状態ブリーフィング（フェーズ判定・open Q・最新 ADR・直近コミット）を stdout 出力 | `§8.0` 実装着手前の確認の機械化バックアップ（状態提示、pass/fail 検査ではない） |

## 運用タイミング

### 完了条件（`CLAUDE.md §5.5`）から起動

`clj -M:poly check` と `clj -M:poly test :all` の間に以下を挟む:

```bash
./.llm/scripts/check-workspace-integrity.sh
```

これで / / / / の5 検査 + 併存検査 + `:local/root` / `:projects` 実在検査が一括で走る。個別スクリプトを手動で呼ぶ必要はない。

### release 前の追加検査（週次 CI / release 時）

完了条件には含めないが、時間軸を跨いだ脆弱性検知として以下を実行する:

```bash
./.llm/scripts/check-vulnerabilities.sh
```

NVD API key 推奨（`https://nvd.nist.gov/developers/request-an-api-key`）。環境変数 `NVD_API_KEY` に設定すると高速化される。

### hook 取り込みの起動タイミング（`lint-import-hooks.sh`）

`tools.deps` の `:main-opts` はシェル展開されないため、以下のタイミングで手動実行が必要:

- 初回セットアップ（`BOOTSTRAP_GUIDE.md §2.9`）
- brick `deps.edn` に新ライブラリ追加後
- `STACK_GUIDE.md §4.2` 推奨ライブラリ採用後
- `clj -M:outdated` による最新化後

```bash
./.llm/scripts/lint-import-hooks.sh
```

本スクリプトを完了条件に含めない理由は、新ライブラリを採用するたびに実行するのが本来の運用で、毎回の完了条件検査に入れると遅くなるため。新ライブラリ採用のたびにユーザが明示的に実行する運用が正しい。

### セッション起動時のブリーフィング（`session-briefing.sh`）

`session-briefing.sh` は pass/fail 系の検査とは役割が異なる**状態提示スクリプト**。CLAUDE.md §8.0 で定めた「実装着手前に DESIGN.md / KNOWLEDGE.md / QUESTIONS.md / adr/ を確認する」運用を機械化バックアップする。

- **Claude Code**: `.claude/settings.json` の `SessionStart` hook で起動時に自動実行、出力は system-reminder として LLM の文脈に注入される
- **Codex / 他エージェント**: SessionStart hook 機構がないため `AGENTS.md` の指示に従い、LLM が起動直後に `bash .llm/scripts/session-briefing.sh` を手動実行する
- **手動実行**: 任意のタイミングで `bash .llm/scripts/session-briefing.sh` を実行し、現時点の状態を確認できる

本スクリプトは副作用ゼロ（stdout のみ）、終了コードは常に 0。`check-workspace-integrity.sh` のような重検査は呼ばず、フェーズ判定・open Q 抽出・最新 ADR 抽出・`git log -5` に限定して軽量に保つ。

## 実装規律

- すべて `#!/usr/bin/env bash` + `set -euo pipefail`（Bash、厳格モード）
- ワークスペースルートへの `cd` は `SCRIPT_DIR/..` で解決（どこから呼んでも動く）
- 依存なし（Babashka / Clojure ランタイム不要、`grep` / `awk` / `sed` / `clojure` CLI のみ）
- プラットフォーム依存: Unix 前提（macOS / Linux）。Windows サポートは将来検討

## 新しい検査の追加手順

1. `.llm/scripts/check-<topic>.sh` を作成（終了コード 0 / 1 で成否表現、エラー時は人間に読めるメッセージ）
2. `check-workspace-integrity.sh` の `run_step` に 1 行追加
3. 本 README の「スクリプト一覧」表と「対応項目」欄を更新
4. 新しい観点・判断根拠は `MAINTAINERS_GUIDE.md §5.12`（linter 継続点検規律）または ADR として記録

## 非採用事項

- **Babashka による実装**: 必須層を拡張しないため不採用
- **`.llm/scripts/new-brick.sh`（brick 追加自動化）**: EDN 機械編集は sed では脆弱、rewrite-clj は Clojure 起動コスト大。代わりに `check-brick-registration.sh` で**不完全さを検知**する方向に切り替えた
