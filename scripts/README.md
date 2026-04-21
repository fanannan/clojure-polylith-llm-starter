# scripts/ — ワークスペース運用スクリプト群

本ディレクトリは、`.clj-kondo/` の custom hook では捕捉できない**設定ファイル・ディレクトリ構造・配布物整合性**の検査と、シェル展開が必要な運用コマンドのラッパーを収容する。各スクリプトは単独でも実行できるが、基本は `check-workspace-integrity.sh` が完了条件（`CLAUDE.md §5.5`）から一括で起動する。

`_POSSIBLE_ISSUES.md` D-4 / D-5 / D-6 / F-1 / F-3、および運用姿勢 G-2（hook と script の役割分担）の実装。

## hook と script の役割分担

| 領分 | 実装場所 | 得意領域 |
|---|---|---|
| コード内の構文・構造パターン | `.clj-kondo/hooks/` + `.clj-kondo/config.edn` | Clojure コード AST 解析（関数行数、`m/=>` 付与、`catch Throwable` 禁止、非推奨ライブラリの**コード使用**） |
| 設定ファイル・ディレクトリ構造 | `scripts/*.sh` | EDN 構造、ファイル実在、deps.edn 採用宣言、プレースホルダ、brick 登録 |

両者は補完関係。例えば非推奨ライブラリ `timbre` の使用は、コード内では `clj-kondo` の `:discouraged-var`（A-6）が、`deps.edn` の採用宣言は `check-deprecated-libs.sh`（F-3）が捕捉する。

## スクリプト一覧

| スクリプト | 目的 | 対応項目 |
|---|---|---|
| `check-workspace-integrity.sh` | 下記 3 種を束ねる総合検査（完了条件から呼ぶ） | F-1 |
| `check-placeholders.sh` | `workspace.edn` / `deps.edn` のプレースホルダ `myorg.myapp` 残存検査 | D-6 |
| `check-brick-registration.sh` | `components/` / `bases/` の brick が `deps.edn` に登録されているか検査 | D-4 |
| `check-deprecated-libs.sh` | `STACK_GUIDE.md §8.2` 非推奨ライブラリの `deps.edn` 採用宣言検査 | F-3 |
| `lint-import-hooks.sh` | 依存ライブラリ提供の `clj-kondo` hook を `.clj-kondo/configs/` に取り込む | D-5 |

## 運用タイミング

### 完了条件（`CLAUDE.md §5.5`）から起動

`clj -M:poly check` と `clj -M:poly test :all` の間に以下を挟む:

```bash
./scripts/check-workspace-integrity.sh
```

これで D-4 / D-6 / F-3 の 3 検査 + 併存検査 + `:local/root` / `:projects` 実在検査が一括で走る。個別スクリプトを手動で呼ぶ必要はない。

### hook 取り込みの起動タイミング（`lint-import-hooks.sh`）

`tools.deps` の `:main-opts` はシェル展開されないため、以下のタイミングで手動実行が必要:

- 初回セットアップ（`BOOTSTRAP_GUIDE.md §2.9`）
- brick `deps.edn` に新ライブラリ追加後
- `STACK_GUIDE.md §4.2` 推奨ライブラリ採用後
- `clj -M:outdated` による最新化後

```bash
./scripts/lint-import-hooks.sh
```

本スクリプトを完了条件に含めない理由は、新ライブラリを採用するたびに実行するのが本来の運用で、毎回の完了条件検査に入れると遅くなるため。新ライブラリ採用のたびにユーザが明示的に実行する運用が正しい。

## 実装規律

- すべて `#!/usr/bin/env bash` + `set -euo pipefail`（Bash、厳格モード）
- ワークスペースルートへの `cd` は `SCRIPT_DIR/..` で解決（どこから呼んでも動く）
- 依存なし（Babashka / Clojure ランタイム不要、`grep` / `awk` / `sed` / `clojure` CLI のみ）
- プラットフォーム依存: Unix 前提（macOS / Linux）。Windows サポートは将来検討

## 新しい検査の追加手順

1. `scripts/check-<topic>.sh` を作成（終了コード 0 / 1 で成否表現、エラー時は人間に読めるメッセージ）
2. `check-workspace-integrity.sh` の `run_step` に 1 行追加
3. 本 README の「スクリプト一覧」表と「対応項目」欄を更新
4. 新しい観点が `_POSSIBLE_ISSUES.md` に記録されていない場合、該当節（F 系）に追記

## 非採用事項

- **Babashka による実装**: 必須層を拡張しないため不採用（`_POSSIBLE_ISSUES.md` D 群方針）
- **`scripts/new-brick.sh`（brick 追加自動化）**: EDN 機械編集は sed では脆弱、rewrite-clj は Clojure 起動コスト大。代わりに `check-brick-registration.sh` で**不完全さを検知**する方向に切り替えた（`_POSSIBLE_ISSUES.md` D-4）
