# BOOTSTRAP_GUIDE.md — 初期化時のみ使用する手順書

本文書は**テンプレートから派生した新規プロジェクトの初期化作業**における、**LLM 側の詳細手順**を扱う。

## README.md との役割分担

初期化作業には 2 つの視点が必要で、2 つの文書で分担している：

| 文書 | 視点 | 主な対象 |
|---|---|---|
| `../README.md` §開始手順 | **人間の作業フロー**（意思決定・承認・プロンプト例） | 人間 |
| `../.llm/guide/BOOTSTRAP_GUIDE.md`（本文書） | **LLM の技術手順**（ファイル編集・コマンド実行・整合性確認） | LLM |

README.md が「**誰が何をするか**」を示すのに対し、本文書は「**LLM がどうファイルを操作するか**」を示す。
**人間は通常 README のプロンプト例を使えば済む**。本文書は LLM が詳細手順を確認する時だけ使う。

## 本文書の位置づけ

- **対象読者**: プロジェクト立ち上げ期の LLM（人間向け手順は README.md）
- **使用期間**: 初期化作業中のみ（数時間〜数日）
- **参照原則**: 疲労最小化原則、判断とプロセスの対称性
∵ ../CLAUDE.md §1
∵ MAINTAINERS_GUIDE.md
- **完了後の扱い**: 物理移動や CLAUDE の参照削除は不要。フェーズ判定で自然に読まれなくなる
∵ ../CLAUDE.md §0

**§2 禁止事項（CLAUDE.md）に基づき、各ステップでユーザの明示承認が必要**。LLM が勝手に進めない。

---

## 1. 前提

本テンプレートは、必須技術と追加ライブラリを分けて扱う：

- **必須技術**: Clojure + tools.deps + Polylith + Malli + clj-kondo + cljfmt + Splint + clj-watson + `.llm/scripts/`。全プロジェクトで常に採用、入れ替え不可
- **追加ライブラリ**: HTTP、永続化、ライフサイクル管理、開発支援など、必要な用途別機能カテゴリごとに選ぶ。実依存は各 brick の `deps.edn` に書く
- **開発支援ライブラリ**: ワークスペースルート `deps.edn` の `:dev :extra-deps` に置き、本番ビルドへ混入させない

**真実の一箇所化**: ライブラリ依存の一次情報源は **brick の deps.edn**。ワークスペースルートの deps.edn に本番依存を書かない。

初期化作業は、**プロジェクトの想定を決め、最初の brick を作成し、必要な追加ライブラリを brick deps.edn に反映する**ことである。

必要に応じて参照する文書：

- **`../CLAUDE.md`**: 常時制約
- **`STACK_GUIDE.md`**: 技術選定の推奨カタログ
- **`POLYLITH_GUIDE.md`**: brick のコード例と deps.edn 構造

---

## 2. 初期化手順

以下の順序で実施する。各ステップ完了後、次へ進む前に**動作確認**を行う。

### 2.0 自律オーケストレーションの流れ（ゲート位置マップ）

README のキックオフプロンプトを受信した LLM は、以下のゲート位置で人間の承認を求めつつ、実手順は §2.1〜§2.9 および §4 に従う。権限階層は COLLABORATION_GUIDE を一次情報源とする。本節はフロー順序の指針であり、権限階層の再定義ではない。
∵ COLLABORATION_GUIDE.md

#### 前提読解

- CLAUDE、DESIGN、本文書を読む
¤ ../CLAUDE.md
¤ ../DESIGN.md
- ライブラリ選定が必要な時だけ STACK_GUIDE を読む
¤ STACK_GUIDE.md
- キックオフから人間が記入済の人間専権 (L0) コンテンツ（目的・ユースケース・受入基準・エントリ種別・組織名・ドメイン名候補・デプロイ構成・環境別設定）を抽出
- 不足・矛盾は 1 点ずつ確認する

#### 主要バッチゲート

| ゲート | 直前で提示する内容（実テキスト / 実操作） | 承認後に実施する節 |
|---|---|---|
| ★ゲート 1（仕様 + 技術選定） | `../DESIGN.md` 反映案／`workspace.edn` :top-namespace 差分／`../README.md` 冒頭差分／必要な用途別機能カテゴリと推奨ライブラリ案 | §2.1 |
| ★ゲート 2（構造 + 依存） | `poly create component/base/project` 3 コマンド／brick `deps.edn` 追加内容（実コード） | §2.3, §2.4 |

ゲート 3 は**縮退**（`COLLABORATION_GUIDE.md` §2.2 で ADR 発行を実施後報告 (L2) 化済）:

| 最終提示 | 内容 | 権限 |
|---|---|---|
| KNOWLEDGE 追加エントリ（実テキスト） | §4 | 承認必須 (L1) |
| README プロダクト版（実テキスト全文） | §4 | 承認必須 (L1) |

ADR の発行（§4）は実施後報告 (L2) として LLM が自動実施し、事後報告する。ゲート 1/2 で承認済の決定内容を形式化するだけのため、事前承認不要（誤記は新 ADR で supersede）。

#### 条件付き承認必須 (L1) 成果物（一括提示）

| 成果物 | 対応する §番号 | 採用条件 |
|---|---|---|
| `workspace.edn` :projects 登録 | §2.5 | 常に |
| ルート `deps.edn` :dev :extra-deps/:extra-paths 更新 | §2.5 | 常に |
| `dev/user.clj` 調整 | §2.6 | 常に |
| ライフサイクル設定ファイル | §2.7 | 必要な場合のみ |
| `build.clj` | §2.8 | uberjar 配布時のみ |
| CI 設定ファイル | §2.9 前後 | 採用する場合のみ |

いずれも承認必須 (L1) として扱い、実内容をまとめて提示する。承認は `COLLABORATION_GUIDE.md` §2.3.1 の通り「全承認または全修正指示」で受け、成果物ごとの部分承認プロトコルは採用しない。

#### 整合性チェックと仕上げ

- §2.9 整合性チェックを自動実行、結果報告
- §4 のうち LLM が実施: ADR 自動発行（実施後報告 (L2)）、KNOWLEDGE 追加・README プロダクト版書き換え（最終提示の承認後）
- §4 のうち人間が実行: **最終コミットのみ**

**CLAUDE §2 の禁止事項は例外なく維持する**。本文書 §4 は BOOTSTRAP_GUIDE の移動・CLAUDE 文書参照表編集を**指示しない**。

曖昧性検出・自己停止・Q 起票は `../CLAUDE.md` §7, §8, §11 および `COLLABORATION_GUIDE.md` §4 の規定通り。ONE BY ONE 原則は維持。

### 2.1 プロジェクト想定の決定（ユーザ判断必須）

以下を決定する：

- [ ] **プロジェクトの主たる性格**を決定（例: Web API、CLI、バッチ、ライブラリ配布）
- [ ] **補助的な性格**がある場合は併記（例: Web API + バッチ併設）
- [ ] 開発支援ライブラリを追加するか決定
- [ ] ドメイン名（例: `billing`、`inventory`、`content`）を決定
- [ ] デプロイ構成（単一 uberjar / 複数 uberjar / Docker / Lambda）を決定
- [ ] `workspace.edn` の `:top-namespace` を実プロジェクト名に変更（`myorg.myapp` から）
- [ ] **`../DESIGN.md` の必須項目（§1 目的、§2 スコープ、§3 主要ユースケース、§4 受入基準、§8 プロジェクト固有情報）を埋める**
- [ ] **DESIGN.md §8.3 技術選定欄にエントリ種別・追加する用途別機能カテゴリ・採用ライブラリを記録**
- [ ] DESIGN.md の推奨項目（§5〜§7）のうち該当するものを埋める

**ここで決めたことを `../.llm/memory/QUESTIONS.md` に `Q` として記録する必要はない**（確定事項として扱う）。
ただし、決定できずに保留した事項があれば Q として記録し、ブロッカーとして明示する。

**技術選定に迷う場合**: 推奨カタログを確認し、それでも迷う場合は **Q を立ててユーザに相談**（自己判断禁止）。

**DESIGN.md の必須項目が埋まっていない状態で §2.2 以降に進まない**。仕様が曖昧だと実装判断が迷走する（分類管理の原則）。

### 2.2 ワークスペースルート deps.edn の変更は不要

**ワークスペースルートの `deps.edn` に本番依存は追加しない**。開発支援ライブラリを採用する場合のみ、§2.5 で `:dev :extra-deps` に追加する。

### 2.3 最初の brick を作成

**§2 禁止事項により、component / base / project の作成はユーザ承認必須**。

```bash
# ドメインコンポーネント（承認必須）
clj -M:poly create component name:<domain>

# エントリベース（承認必須）
clj -M:poly create base name:<entry>

# デプロイプロジェクト（承認必須）
clj -M:poly create project name:<deploy>
```

生成された brick は、**POLYLITH_GUIDE のコード例に従って**中身を実装する。独自の流儀を発明しない。
¤ POLYLITH_GUIDE.md §2

### 2.4 必要な用途別機能カテゴリの推奨ライブラリを brick deps.edn に反映

**§2 禁止事項により、依存追加はユーザ承認必須**。

deps.edn へ入れるライブラリは必要な用途別機能カテゴリ単位で最小化する。推奨カタログを確認し、必要なものだけを **brick の deps.edn** に記述する：

- **base の deps.edn**（`bases/<entry>/deps.edn`）: I/O、ルーティング、永続化、設定、ログ等の境界ライブラリ
- **component の deps.edn**（`components/<domain>/deps.edn`）: I/O 系ライブラリは書かない（ドメイン純粋性）。Malli は必須技術基盤なので common に依存
- **projects/<deploy>/deps.edn**: `:local/root` で brick を参照するのみ
∵ POLYLITH_GUIDE.md §2.3

**deps.edn の形**:

```clojure
;; bases/<entry>/deps.edn
{:paths ["src" "resources"]
 :deps  {;; 必要な用途別機能カテゴリのライブラリだけを書く
         ;; 使う component への依存もここに書く
         poly/<domain> {:local/root "../../components/<domain>"}}}
```

**推奨から外れる場合**: ADR 発行 + DESIGN.md §8.3 に逸脱理由を記録する。

### 2.5 workspace.edn / ワークスペースルート deps.edn の更新

- [ ] `workspace.edn` の `:projects` に新 project を登録
- [ ] ワークスペースルート `deps.edn` の `:dev :extra-paths` に新 brick を追加（components / bases の src・resources・test）、および `projects/<deploy>/resources`（config.edn を `io/resource` で読めるようにするため）
- [ ] **ワークスペースルート `deps.edn` の `:dev :extra-deps` に新 brick を `:local/root` で登録**（tools.deps の仕様上、`:extra-paths` のみでは brick 側の `deps.edn` の `:deps` が推移的解決されない。brick を `:local/root` 登録することで初めて brick deps.edn の依存が REPL で利用可能になる）:
  ```clojure
  ;; :dev :extra-deps 内
  poly/inventory {:local/root "components/inventory"}
  poly/api       {:local/root "bases/api"}
  ```
- [ ] **開発支援ライブラリ採用時**: ワークスペースルート `deps.edn` の `:dev :extra-deps` に追加:
  ```clojure
  ;; :dev :extra-deps 内
  djblue/portal                {:mvn/version "0.58.5"}
  org.clojure/test.check       {:mvn/version "1.1.1"}
  nubank/matcher-combinators   {:mvn/version "3.9.1"}
  ;; ライフサイクル管理の REPL 補助など、開発時だけ必要なもの
  ```

**なぜ `:local/root` 登録が必要か**（選択肢 H の帰結）: brick deps.edn を一次情報源とする方針では、brick の依存は brick の deps.edn に書かれている。tools.deps は `:extra-paths` からソースを読むが、各 brick の deps.edn を自動解決しない。development が全 brick を統合して REPL で使うには、`:local/root` で brick を依存として登録する必要がある。これにより brick の deps.edn の `:deps` が推移的に解決され、REPL で `(require ...)` できるようになる。

### 2.6 development/src/dev/user.clj の調整

配布版の dev/user.clj は、必須の Malli instrumentation と任意の開発補助セクションを同梱している。プロジェクトで使わない任意セクションは削除する：

- ライフサイクル管理を使わないプロジェクト: 対応セクションを削除する
- データ可視化などの開発補助を使わないプロジェクト: 対応セクションを削除する
- 使う場合: コメント解除し、必要な設定関数だけ実装する

Malli instrumentation セクションはすべてのプロジェクトで有効化したまま使う（必須技術基盤）。

「削除」は単純な不要コードの除去であり、承認 L1。dev/user.clj は派生プロジェクトに応じた調整が前提のファイル。

dev/user.clj の具体例は別紙に置く。
∵ POLYLITH_GUIDE.md §2.4

### 2.7 ライフサイクル設定ファイル作成（必要な場合のみ）

I/O リソースの起動・停止管理が必要な場合のみ実施する。ライブラリ配布や単発 CLI などでは本節をスキップ：

- [ ] `projects/<deploy>/resources/config.edn` を作成
∵ POLYLITH_GUIDE.md §2.3
- [ ] aero の `#profile` / `#env` で環境別設定を記述
- [ ] `development/src/dev/user.clj` の `config` 関数を実装
- [ ] `bases/<entry>/src/.../system.clj` で起動・停止処理を実装

### 2.8 build.clj 作成（uberjar 配布時のみ）

- [ ] `projects/<deploy>/build.clj` を POLYLITH_GUIDE.md §2.3 の標準形で作成
- [ ] `lib`、`main-ns`、`version` を実プロジェクトに合わせる

### 2.9 動作確認

全項目が通過することを確認：

**clj-kondo hook の初回取り込み**:

- [ ] 新ライブラリ採用後、以下のスクリプトで各ライブラリ提供の clj-kondo hook を取り込む（tools.deps の `:main-opts` はシェル展開されないため、エイリアスに埋め込めない）:
  ```bash
  ./.llm/scripts/lint-import-hooks.sh
  ```
  これにより `.clj-kondo/.cache/` および `.clj-kondo/configs/` が更新され、以後の `clj -M:lint` でライブラリ固有の lint ルールが機能する。新ライブラリを追加・更新した時は再実行する

**brick 単位の依存解決確認**:

- [ ] 各 brick が依存解決できる:
  ```bash
  cd bases/<entry> && clj -Spath > /dev/null && echo ok
  cd components/<domain> && clj -Spath > /dev/null && echo ok
  ```

**workspace 整合性の総合検査**:

- [ ] プレースホルダ残存・brick 登録漏れ・非推奨ライブラリ採用・生成 artifact drift の一括検査:
  ```bash
  ./.llm/scripts/check-workspace-integrity.sh
  ```

**workspace 全体の品質確認**:

- [ ] 完了条件の全コマンドが通過する（lint / lint-splint / format check / poly check / workspace-integrity / poly test :all / uber ビルド）
¤ ../CLAUDE.md §5.5
- [ ] `clj -M:dev:nrepl` で REPL 起動、ライフサイクル管理を使う場合は起動・再起動 helper が動作
- [ ] 実装した brick の関数を REPL から呼び出して動作確認

> コマンド列の一次情報源は CLAUDE に置く。本節では再掲しない（SSOT）。REPL 起動と brick 動作確認は初期化特有の初回確認事項のため、ここに残す。
> ¤ ../CLAUDE.md §5.5

**依存脆弱性スキャン**（release 前必須、初期化時は任意）:

- [ ] `./.llm/scripts/check-vulnerabilities.sh` が通る（clj-watson）
∵ ../CLAUDE.md §5.5
  - **NVD API key 推奨**: 無料で `https://nvd.nist.gov/developers/request-an-api-key` から取得し、環境変数 `NVD_API_KEY` に設定するとスキャンが高速化される

**用途別機能カテゴリごとの確認事項**:

- [ ] 採用した各用途別機能カテゴリの確認事項をすべて点検

---

## 3. 初期化完了チェックリスト

以下すべてを満たしたら初期化完了：

- [ ] **`../DESIGN.md` の必須項目（§1〜§4、§8）が埋まっている**
- [ ] DESIGN.md §8.3 技術選定欄にエントリ種別・追加する用途別機能カテゴリ・採用ライブラリが記録されている
- [ ] `workspace.edn` の `:top-namespace` が実プロジェクト名
- [ ] 必要な用途別機能カテゴリのライブラリが brick の deps.edn に反映されている
- [ ] 推奨から逸脱した場合、ADR が発行されている
- [ ] 最低 1 組の component + base + project が存在
- [ ] §2.9 の動作確認がすべて通過
- [ ] 採用した各用途別機能カテゴリの確認事項がすべて点検済み
- [ ] CI が設定されている（lint / format / poly check / poly test / uber build、brick 依存解決確認含む）
- [ ] 初回の `stable` タグが打たれている（CI 通過後）
- [ ] `../.llm/memory/QUESTIONS.md` に残っている open Q を点検済み
- [ ] **`../README.md` がプロダクト向けに書き換えられている**（§4 で実施）

---

## 4. 完了後の作業

初期化が完了したら、以下を実施する。**BOOTSTRAP_GUIDE の移動や CLAUDE 文書参照表の編集は不要**。

1. **`../README.md` をプロダクト向け README として完全に書き換える**（承認必須 (L1)、ゲート 3 承認対象）
   - テンプレート配布時の README.md は本テンプレートの説明に特化している
   - プロダクト README には、プロダクトの機能紹介・利用者向けビルド手順・API 紹介等を記述
   - 迷ったら `../DESIGN.md` §1 目的と §3 主要ユースケースをベースに書き起こす
2. 初期化中に立てた `../.llm/memory/QUESTIONS.md` の `open` Q を点検し、解決したものを `resolved` に
3. 解決した Q の結果が継続参照されるものは KNOWLEDGE へ反映（承認必須 (L1)、ゲート 3 承認対象、実テキストで提示）
¤ ../.llm/memory/KNOWLEDGE.md
4. 重要な設計判断（技術選定、推奨からの逸脱等）は `../.llm/memory/adr/NNNN-topic.md` として ADR を発行し、事後報告する
5. 初期化完了をコミット（例: `"Complete project bootstrap"`）— **このコマンドは LLM が提示、実行はユーザが行う**

以降は CLAUDE の作業プロトコルで日常開発に移行する。本文書（BOOTSTRAP_GUIDE）は物理的には残るが、フェーズ判定により、完了後は自動的に読まれない。
¤ ../CLAUDE.md §8
∵ ../CLAUDE.md §0

---

## 5. 初期化失敗時の対処

各ステップで動作確認が通らない場合：

### 5.1 brick の依存解決が失敗する（`cd bases/<name> && clj -Spath` で例外）

- brick の deps.edn の記述内容を推奨カタログと照合
- バージョンの組合せが Maven Central / Clojars に実在するか確認
- プロジェクト固有で追加したライブラリ（DB ドライバ等）のバージョン整合性を確認
- component の deps.edn で I/O ライブラリを誤って書いていないか確認（ドメイン純粋性）
- 解消できない場合は、自己停止プロトコルに従って停止し、ユーザに復旧方針を確認する。`git revert` やブランチ破棄は同一ではないため、どちらを使うかは状況に応じて明示する
¤ ../CLAUDE.md §7

### 5.2 `poly check` が通らない

手作業で brick を作成していないか確認。`poly create` 経由でないと構造が認識されない。
Polylith 特有の頻出誤りと対処は別紙に置く。
⚠ POLYLITH_GUIDE.md §5

### 5.3 `(go)` が未定義、`(go)` で例外、または REPL 起動時に ClassNotFoundException

Malli の起動順序、`set-refresh-dirs` の対象、任意の開発補助配線を確認。`development/src/dev/user.clj` の docstring を確認。**よくある失敗**:

- **Unable to resolve symbol: go**: ライフサイクル管理セクションがコメントアウトされたまま。ライフサイクル管理を採用するなら `development/src/dev/user.clj` の該当セクションを有効化する。採用しないなら `(go)` は使わず `(malli-on!)` と通常 eval で確認する
- **ClassNotFoundException（brick 依存未解決）**: ワークスペースルート `deps.edn` の `:dev :extra-deps` に brick が `:local/root` 登録されていない。tools.deps は `:extra-paths` だけでは brick の deps.edn を自動解決しない
- **FileNotFoundException: config.edn**: `config.edn` が classpath に含まれていない。開発時は `projects/<deploy>/resources` が `:dev :extra-paths` に追加されているか確認（§2.5）、または dev/user.clj の `config` 関数で `io/file` でファイルパス直接指定する代替手段も可
⚠ POLYLITH_GUIDE.md §2.4
- **config.edn の未作成**: ライフサイクル管理を採用したのに設定ファイルが存在しない
- **aero の `#env` 参照先未定義**: 環境変数が未設定、または aero の記法ミス
- **起動・停止定義の不足**: ライフサイクル管理の init / halt 相当を書き忘れ
- **dev/user.clj の任意セクション未有効化**: 必要な開発補助コードが配布時のまま無効化状態

### 5.4 必要な用途別機能カテゴリの §3 機能別節の採用時の確認事項相当を満たしていない

採用した用途別機能カテゴリの確認事項を点検し、漏れている項目を補完する。特に：

- 設定ファイル（config.edn、logging publisher 設定等）の未作成
- プロジェクト固有ライブラリ（DB ドライバ、キュークライアント等）の未追加
- 必要な用途別機能カテゴリ（HTTP サーバ実装、JSON 処理等）のライブラリが未採用

**注意**: §3 機能別節の推奨ライブラリ完全一致は求められていない。用途別機能カテゴリを満たす別ライブラリを採用している場合は OK（ただし逸脱理由を ADR で記録）。

### 5.5 uberjar ビルドに dev-tools ライブラリが混入している

`:dev :extra-deps` に配置すべき開発支援ライブラリが brick の deps.edn に誤って書かれている可能性。brick 側から削除し、ワークスペースルートの `:dev :extra-deps` に移動する。

### 5.6 何を試しても動かない

`../CLAUDE.md` §7 自己停止プロトコルの発動条件に達したら：

1. 選択肢 D（人間による設計判断を求める）を選ぶ
2. `../.llm/memory/QUESTIONS.md` §0.9 の手順で新規 Q を立てる
3. ユーザの判断を待つ（自走しない）
