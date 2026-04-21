# BOOTSTRAP_GUIDE.md — 初期化時のみ使用する手順書

本文書は**テンプレートから派生した新規プロジェクトの初期化作業**における、**LLM 側の詳細手順**を扱う。

## README.md との役割分担

初期化作業には 2 つの視点が必要で、2 つの文書で分担している：

| 文書 | 視点 | 主な対象 |
|---|---|---|
| `../README.md` §開始手順 | **人間の作業フロー**（意思決定・承認・プロンプト例） | 人間 |
| `../.llm/guide/BOOTSTRAP_GUIDE.md`（本文書） | **LLM の技術手順**（ファイル編集・コマンド実行・整合性確認） | LLM |

README.md が「**誰が何をするか**」を示すのに対し、本文書は「**LLM がどうファイルを操作するか**」を示す。
**人間は通常 README.md のプロンプト例を使えば済む**。本文書は LLM が詳細手順を参照する際に使う。

## 本文書の位置づけ

- **対象読者**: プロジェクト立ち上げ期の LLM（人間向け手順は README.md）
- **使用期間**: 初期化作業中のみ（数時間〜数日）
- **参照原則**: `../CLAUDE.md` §1 の疲労最小化原則、MAINTAINERS_GUIDE.md 原則 11（判断とプロセスの対称性）
- **完了後の扱い**: `archived/BOOTSTRAP_GUIDE.md` に移動、CLAUDE.md 冒頭の参照も削除

**§2 禁止事項（CLAUDE.md）に基づき、各ステップでユーザの明示承認が必要**。LLM が勝手に進めない。

---

## 1. 前提

本テンプレートは技術スタックを**必須層 + stack 層**として配布する：

- **必須層**（ワークスペースルートの `deps.edn` の `:deps` および必須エイリアス）: Clojure + tools.deps + Polylith + Malli + clj-kondo + cljfmt。全プロジェクトで常に採用、入れ替え不可
- **stack 層**: 目的別の推奨構成（web-api stack / batch stack / cli stack / library stack 等）。**STACK_GUIDE.md §4.2 は推奨カタログであり、実ライブラリ依存は各 brick の `deps.edn` に書かれる**（Polylith の本番ビルドはそこから依存解決する）
- **横断層**（dev-tools stack）: 開発支援。ワークスペースルートの `deps.edn` の `:dev :extra-deps` に配置（本番ビルドに混入させない）

**真実の一箇所化**: ライブラリ依存の一次情報源は **brick の deps.edn**。ワークスペースルートの deps.edn には stack 層の依存を書かない（二重管理を回避）。

初期化作業は、**プロジェクトの想定を決定し、採用 stack を選び、最初の brick を作成して推奨ライブラリを brick deps.edn に反映する**ことである。

詳細な設計意図は以下の文書を参照：

- **`../CLAUDE.md` §1**: 疲労最小化の第一原理と三基底原則
- **`../CLAUDE.md` §3**: 技術スタック（必須層の要約）
- **`../CLAUDE.md` §6.2**: stack の採用・変更（参照先の案内）
- **`STACK_GUIDE.md`**: **技術スタック選定の一次情報源（推奨カタログ）**（§1 概念、§2 階層、§3 機能別選定根拠、§4 stack 定義、§5 ブートストラップでの使い方、§6 整合性チェック、§8 禁止・非推奨ライブラリ）
- **`MAINTAINERS_GUIDE.md` §5.9**: STACK_GUIDE.md の保守規律
- **`POLYLITH_GUIDE.md` §2**: brick のコード例と deps.edn 構造

---

## 2. ブートストラップ手順

以下の順序で実施する。各ステップ完了後、次へ進む前に**動作確認**を行う。

### 2.0 自律オーケストレーションの流れ（ゲート位置マップ）

README.md のキックオフプロンプトを受信した LLM は、以下のゲート位置で人間の承認を求めつつ、実手順は §2.1〜§2.9 および §4 に従う。**ガバナンス（L0/L1/L2/L3、ADR の L2 規定、特別承認・部分承認の不採用）は `COLLABORATION_GUIDE.md` §2 を一次情報源とする**。本節はフロー順序の指針であり、権限階層の再定義ではない。

#### 前提読解

- `../CLAUDE.md` §1-§3（特に §1.2.5 失敗早期検知 > 事前承認）、`../DESIGN.md` §0、本文書 §2.1-§2.9、`STACK_GUIDE.md` §4.1、**`COLLABORATION_GUIDE.md` §2-§4（§2.2 マッピング、§2.3 マトリクス、§2.3.1 特別承認・部分承認不採用、§3.1 ブートストラップモード）** を読む
- キックオフから人間が記入済の L0 コンテンツ（目的・ユースケース・受入基準・エントリ種別・組織名・ドメイン名候補・デプロイ構成・環境別設定）を抽出
- 不足・矛盾は `COLLABORATION_GUIDE.md` §4 ONE BY ONE で解消

#### 主要バッチゲート

| ゲート | 直前で提示する内容（実テキスト / 実操作） | 承認後に実施する節 |
|---|---|---|
| ★ゲート 1（仕様 + stack） | `../DESIGN.md` §1-§4,§8 反映案／`workspace.edn` :top-namespace 差分／`../README.md` 冒頭差分／採用 stack 提案（STACK_GUIDE.md §4.2 記載有無を明示） | §2.1 |
| ★ゲート 2（構造 + 依存） | `poly create component/base/project` 3 コマンド／brick `deps.edn` 追加内容（実コード） | §2.3, §2.4 |

ゲート 3 は**縮退**（`COLLABORATION_GUIDE.md` §2.2 で ADR 発行を L2 化済）:

| 最終提示 | 内容 | 権限 |
|---|---|---|
| KNOWLEDGE 追加エントリ（実テキスト） | §4 | L1 |
| README プロダクト版（実テキスト全文） | §4 | L1 |

ADR の発行（§4）は L2 として LLM が自動実施、事後報告。ゲート 1/2 で承認済の決定内容を形式化するだけのため、事前承認不要（誤記は新 ADR で supersede）。

#### ゲート外の個別 L1 承認（成果物単位で事前提示）

| 成果物 | 対応する §番号 | 採用条件 |
|---|---|---|
| `workspace.edn` :projects 登録 | §2.5 | 常に |
| ルート `deps.edn` :dev :extra-deps/:extra-paths 更新 | §2.5 | 常に |
| `dev/user.clj` 調整（Integrant / Portal セクション削除） | §2.6 | 常に |
| Integrant `config.edn` | §2.7 | Integrant 採用プロジェクトのみ |
| `build.clj` | §2.8 | uberjar 配布時のみ |
| CI 設定ファイル | §2.9 前後 | 常に |

いずれも L1 として扱い、実内容を事前提示して個別承認を得る（ゲートに丸め込まない。影響範囲が異なる L1 変更を 1 つの Yes/No に潰さないため）。

#### 整合性チェックと仕上げ

- §2.9 整合性チェックを自動実行、結果報告
- §4 のうち LLM が実施: ADR 自動発行（L2）、KNOWLEDGE 追加・README プロダクト版書き換え（ゲート 3 縮退承認後）
- §4 のうち人間が実行: **最終コミットのみ**

**CLAUDE.md §2 禁止事項は例外なく維持する**（`COLLABORATION_GUIDE.md` §2.3.1 参照）。本文書 §4 は BOOTSTRAP_GUIDE.md の archived/ 移動・CLAUDE.md 文書参照表編集を**指示しない**（該当儀式は廃止）。

曖昧性検出・自己停止・Q 起票は `../CLAUDE.md` §7, §8, §11 および `COLLABORATION_GUIDE.md` §4 の規定通り。ONE BY ONE 原則は維持。

### 2.1 プロジェクト想定と採用 stack の決定（ユーザ判断必須）

以下を決定する：

- [ ] **プロジェクトの主たる性格**を決定し、**採用 stack** を選択（STACK_GUIDE.md §4.1 の表を参照）
  - 例: Web API → web-api stack、バッチ → batch stack、CLI → cli stack、ライブラリ配布 → library stack
- [ ] **補助的な性格**がある場合、**追加 stack** を併用選択
  - 例: Web API + バッチ併設 → web-api stack + batch stack
- [ ] **dev-tools stack 併用の可否**（強く推奨）
- [ ] ドメイン名（例: `billing`、`inventory`、`content`）を決定
- [ ] デプロイ構成（単一 uberjar / 複数 uberjar / Docker / Lambda）を決定
- [ ] `workspace.edn` の `:top-namespace` を実プロジェクト名に変更（`myorg.myapp` から）
- [ ] **`../DESIGN.md` の必須項目（§1 目的、§2 スコープ、§3 主要ユースケース、§4 受入基準、§8 プロジェクト固有情報）を埋める**
- [ ] **DESIGN.md §8.3 採用 stack 欄に採用 stack を記録**（例: web-api stack + dev-tools stack）
- [ ] DESIGN.md の推奨項目（§5〜§7）のうち該当するものを埋める

**ここで決めたことを `../.llm/memory/QUESTIONS.md` に `Q` として記録する必要はない**（確定事項として扱う）。
ただし、決定できずに保留した事項があれば Q として記録し、ブロッカーとして明示する。

**stack 選択に迷う場合**: STACK_GUIDE.md §4.1 選定基準を参照。それでも迷う場合は **Q を立ててユーザに相談**（自己判断禁止）。

**DESIGN.md の必須項目が埋まっていない状態で §2.2 以降に進まない**。仕様が曖昧だと実装判断が迷走する（原則 13）。

### 2.2 ワークスペースルート deps.edn の変更は不要

**ワークスペースルートの `deps.edn` は変更しない**（必須層のみなので stack 選択とは無関係）。dev-tools stack を採用する場合のみ、§2.5 で `:dev :extra-deps` に追加する。

### 2.3 最初の brick を作成

**§2 禁止事項により、base / project の作成はユーザ承認必須**。

```bash
# ドメインコンポーネント
clj -M:poly create component name:<domain>

# エントリベース（承認必須）
clj -M:poly create base name:<entry>

# デプロイプロジェクト（承認必須）
clj -M:poly create project name:<deploy>
```

生成された brick に、**POLYLITH_GUIDE.md §2 のコード例を参照して**中身を実装する。独自の流儀を発明しない。

### 2.4 採用 stack の推奨ライブラリを brick deps.edn に反映

**§2 禁止事項により、依存追加はユーザ承認必須**。

採用 stack ごとに、STACK_GUIDE.md §4.2 の該当節の「推奨ライブラリ」表を **brick の deps.edn** に記述する：

- **base の deps.edn**（`bases/<entry>/deps.edn`）: 主たる stack の推奨ライブラリ（HTTP サーバ、ルーティング、JSON、Integrant 等 I/O を含むもの）
- **component の deps.edn**（`components/<domain>/deps.edn`）: I/O 系ライブラリは書かない（ドメイン純粋性）。Malli は必須層なので common に依存
- **projects/<deploy>/deps.edn**: `:local/root` で brick を参照するのみ（POLYLITH_GUIDE.md §2.3）

**具体例**（web-api stack + dev-tools stack 採用、PostgreSQL 使用時）:

```clojure
;; bases/<entry>/deps.edn
{:paths ["src" "resources"]
 :deps  {org.clojure/clojure          {:mvn/version "1.12.0"}
         metosin/malli                {:mvn/version "0.16.4"}
         ;; web-api stack 推奨（STACK_GUIDE.md §4.2.3）
         integrant/integrant          {:mvn/version "0.13.1"}
         aero/aero                    {:mvn/version "1.1.6"}
         ring/ring-core               {:mvn/version "1.13.0"}
         ring/ring-jetty-adapter      {:mvn/version "1.13.0"}
         metosin/reitit               {:mvn/version "0.7.2"}
         metosin/reitit-ring          {:mvn/version "0.7.2"}
         metosin/reitit-malli         {:mvn/version "0.7.2"}
         metosin/jsonista             {:mvn/version "0.3.11"}
         com.brunobonacci/mulog       {:mvn/version "0.9.0"}
         com.brunobonacci/mulog-json  {:mvn/version "0.9.0"}
         ;; Web API で DB を使う場合（STACK_GUIDE.md §3.6 参照）
         com.github.seancorfield/next.jdbc  {:mvn/version "1.3.967"}
         com.github.seancorfield/honeysql   {:mvn/version "2.6.1230"}
         com.zaxxer/HikariCP                {:mvn/version "6.2.1"}
         ;; プロジェクト固有の DB ドライバ
         org.postgresql/postgresql    {:mvn/version "42.7.4"}
         ;; 使う component への依存
         poly/<domain>                {:local/root "../../components/<domain>"}}}
```

**推奨から外れる場合**（組織方針で mulog → timbre 差替等）: ADR 発行 + DESIGN.md §8.3 に逸脱明記（STACK_GUIDE.md §5.4 参照）。

### 2.5 workspace.edn / ワークスペースルート deps.edn の更新

- [ ] `workspace.edn` の `:projects` に新 project を登録
- [ ] ワークスペースルート `deps.edn` の `:dev :extra-paths` に新 brick を追加（components / bases の src・resources・test）、および `projects/<deploy>/resources`（config.edn を `io/resource` で読めるようにするため）
- [ ] **ワークスペースルート `deps.edn` の `:dev :extra-deps` に新 brick を `:local/root` で登録**（tools.deps の仕様上、`:extra-paths` のみでは brick 側の `deps.edn` の `:deps` が推移的解決されない。brick を `:local/root` 登録することで初めて brick deps.edn の依存が REPL で利用可能になる）:
  ```clojure
  ;; :dev :extra-deps 内
  poly/inventory {:local/root "components/inventory"}
  poly/api       {:local/root "bases/api"}
  ```
- [ ] **dev-tools stack 採用時**: ワークスペースルート `deps.edn` の `:dev :extra-deps` に開発支援ライブラリを追加（STACK_GUIDE.md §4.2.10 参照）:
  ```clojure
  ;; :dev :extra-deps 内
  djblue/portal                {:mvn/version "0.58.5"}
  org.clojure/test.check       {:mvn/version "1.1.1"}
  nubank/matcher-combinators   {:mvn/version "3.9.1"}
  ;; Integrant を含む stack 併用時
  integrant/repl               {:mvn/version "0.4.0"}
  ```

**なぜ `:local/root` 登録が必要か**（選択肢 H の帰結）: brick deps.edn を一次情報源とする方針では、brick の依存は brick の deps.edn に書かれている。tools.deps は `:extra-paths` からソースを読むが、各 brick の deps.edn を自動解決しない。development が全 brick を統合して REPL で使うには、`:local/root` で brick を依存として登録する必要がある。これにより brick の deps.edn の `:deps` が推移的に解決され、REPL で `(require ...)` できるようになる。

### 2.6 development/src/dev/user.clj の調整

配布版の dev/user.clj は Integrant によるライフサイクル管理と Portal によるデータ可視化を使う構成を想定した完成例として、Malli instrumentation、Integrant ライフサイクル制御、Portal ヘルパーの 3 セクションを同梱している。プロジェクトで使わないライブラリに対応するセクションは、本テンプレートの方針（YAGNI、疲労最小化、原則 5 LLM は削除が苦手）に従って**削除する**：

- **Integrant を使わないプロジェクト**（ライブラリ配布・単発 CLI 実行など、I/O リソースのライフサイクル管理が不要な場合）: Integrant セクション（ns の require にある Integrant 関連 3 行と、`config` / `ig-state` / `go` / `reset` / `halt` / `system` の定義）を削除する
- **Integrant を使うプロジェクト**（Web サービス・バッチ・ワーカ等）: Integrant セクションをコメント解除して、`config` 関数を実装する
- **Portal を使わないプロジェクト**: Portal セクション（`portal-instance` / `portal-tap-fn` の atom、`portal` / `portal-clear` / `portal-close` の定義）を削除する
- **Portal を使うプロジェクト**: deps.edn の `:dev` に `djblue/portal` を追加し、Portal セクションはそのまま使う

Malli instrumentation セクションはすべてのプロジェクトで有効化したまま使う（必須層）。

「削除」は単純な不要コードの除去であり、承認 L1（COLLABORATION_GUIDE.md §2.3）。dev/user.clj は配布物の一部だが、派生プロジェクトに応じた調整が前提のファイル。try-catch による防御は「依存がまだ導入されていない起動直後の REPL が壊れないため」の一時的な安全網であって、未使用コードを残し続けることを正当化するものではない。

dev/user.clj の具体例は POLYLITH_GUIDE.md §2.4 参照。

### 2.7 Integrant 設定ファイル作成（Integrant を使う場合のみ）

Integrant を使うプロジェクトでのみ実施する。ライブラリ配布や単発 CLI などで Integrant を使わない場合は本節をスキップ：

- [ ] `projects/<deploy>/resources/config.edn` を作成（POLYLITH_GUIDE.md §2.3 のコード例参照）
- [ ] aero の `#profile` / `#env` で環境別設定を記述
- [ ] `development/src/dev/user.clj` の `config` 関数を実装（POLYLITH_GUIDE.md §2.4 参照）
- [ ] `bases/<entry>/src/.../system.clj` で Integrant `defmethod init-key` / `halt-key!` を実装

### 2.8 build.clj 作成（uberjar 配布時のみ）

- [ ] `projects/<deploy>/build.clj` を POLYLITH_GUIDE.md §2.3 の標準形で作成
- [ ] `lib`、`main-ns`、`version` を実プロジェクトに合わせる

### 2.9 動作確認（STACK_GUIDE.md §6 整合性チェック）

全項目が通過することを確認：

**clj-kondo hook の初回取り込み**:

- [ ] 新ライブラリ採用後、以下のスクリプトで各ライブラリ提供の clj-kondo hook を取り込む（tools.deps の `:main-opts` はシェル展開されないため、エイリアスに埋め込めない。の実装）:
  ```bash
  ./.llm/scripts/lint-import-hooks.sh
  ```
  これにより `.clj-kondo/.cache/` および `.clj-kondo/configs/` が更新され、以後の `clj -M:lint` でライブラリ固有の lint ルールが機能する。**再実行のタイミング**: brick deps.edn に新ライブラリを追加したとき、`clj -M:outdated` で依存を更新したとき、`STACK_GUIDE.md §4.2` 推奨ライブラリを採用したとき

**brick 単位の依存解決確認**:

- [ ] 各 brick が依存解決できる:
  ```bash
  cd bases/<entry> && clj -Spath > /dev/null && echo ok
  cd components/<domain> && clj -Spath > /dev/null && echo ok
  ```

**workspace 整合性の総合検査**:

- [ ] プレースホルダ残存・brick 登録漏れ・非推奨ライブラリ採用の一括検査:
  ```bash
  ./.llm/scripts/check-workspace-integrity.sh
  ```

**workspace 全体の品質確認**:

- [ ] `clj -M:lint` がゼロ警告（clj-kondo + polyguard custom hook）
- [ ] `clj -M:lint-splint` がゼロ警告（Splint、スタイル・イディオムにより必須層化）
- [ ] `clj -M:format check` が通る
- [ ] `clj -M:poly check` が通る
- [ ] `clj -M:dev:nrepl` で REPL 起動、`(go)` が例外なく完走（Integrant を含む stack の場合）
- [ ] `(reset)` が動作（Integrant を含む stack の場合）
- [ ] 実装した brick の関数を REPL から呼び出して動作確認
- [ ] `clj -M:poly test :all` がすべて成功
- [ ] `cd projects/<deploy> && clj -T:build uber` がビルド成功

**依存脆弱性スキャン**（release 前必須、ブートストラップ時は任意）:

- [ ] `./.llm/scripts/check-vulnerabilities.sh` が通る（clj-watson、NIST NVD + GitHub Advisory Database）
  - **NVD API key 推奨**: 無料で `https://nvd.nist.gov/developers/request-an-api-key` から取得し、環境変数 `NVD_API_KEY` に設定するとスキャンが高速化される
  - 完了条件（`CLAUDE.md §5.5`）には含めない（実行時間が長いため）。週次 CI / release 前で実行

**採用 stack ごとの確認事項（STACK_GUIDE.md §4.2.X）**:

- [ ] 採用した各 stack の「採用時の確認事項」をすべて点検（STACK_GUIDE.md §4.2.X 参照）

---

## 3. 初期化完了チェックリスト

以下すべてを満たしたら初期化完了：

- [ ] **`../DESIGN.md` の必須項目（§1〜§4、§8）が埋まっている**
- [ ] DESIGN.md §8.3 採用 stack 欄に採用 stack が記録されている
- [ ] `workspace.edn` の `:top-namespace` が実プロジェクト名
- [ ] 採用 stack の推奨ライブラリ（STACK_GUIDE.md §4.2）が brick の deps.edn に反映されている
- [ ] STACK_GUIDE.md の推奨から逸脱した場合、ADR が発行されている
- [ ] 最低 1 組の component + base + project が存在
- [ ] §2.9 の動作確認がすべて通過
- [ ] 採用各 stack の §4.2.X 採用時の確認事項がすべて点検済み
- [ ] CI が設定されている（lint / format / poly check / poly test / uber build、brick 依存解決確認含む）
- [ ] 初回の `stable` タグが打たれている（CI 通過後）
- [ ] `../.llm/memory/QUESTIONS.md` に残っている open Q を点検済み
- [ ] **`../README.md` がプロダクト向けに書き換えられている**（§4 で実施）

---

## 4. 完了後の作業

初期化が完了したら、以下を実施する。**BOOTSTRAP_GUIDE.md の移動や CLAUDE.md 文書参照表の編集は不要**（機能的に冗長な儀式であり、`COLLABORATION_GUIDE.md` §2.3.1 の方針に従い廃止。完了後は CLAUDE.md §0 の参照指示により本文書は自然にスキップされる）。

1. **`../README.md` をプロダクト向け README として完全に書き換える**（L1、ゲート 3 承認対象）
   - テンプレート配布時の README.md は本テンプレートの説明に特化している
   - プロダクト README には、プロダクトの機能紹介・利用者向けビルド手順・API 紹介等を記述
   - 迷ったら `../DESIGN.md` §1 目的と §3 主要ユースケースをベースに書き起こす
2. 初期化中に立てた `../.llm/memory/QUESTIONS.md` の `open` Q を点検し、解決したものを `resolved` に
3. 解決した Q の結果が継続参照されるものは `../.llm/memory/KNOWLEDGE.md` へ昇格（L1、ゲート 3 承認対象、実テキストで提示）
4. 重要な設計判断（採用 stack の根拠、STACK_GUIDE.md 推奨からの逸脱、技術選定等）は `../.llm/memory/adr/NNNN-topic.md` として ADR を発行（**L2、LLM 独断実施、事後報告**。`COLLABORATION_GUIDE.md` §2.2）。決定内容はゲート 1/2 で既に承認済なので、ADR は形式化に過ぎない。誤記は新 ADR で supersede
5. 初期化完了をコミット（例: `"Complete project bootstrap"`）— **このコマンドは LLM が提示、実行はユーザが行う**

以降は `../CLAUDE.md` §8 作業プロトコルで日常開発に移行する。本文書（BOOTSTRAP_GUIDE.md）は物理的には残るが、CLAUDE.md §0 の参照指示（「初期化が未完了の場合のみ参照」）により、完了後は自動的に読まれない。

---

## 5. ブートストラップ失敗時の対処

各ステップで動作確認が通らない場合：

### 5.1 brick の依存解決が失敗する（`cd bases/<name> && clj -Spath` で例外）

- brick の deps.edn の記述内容を STACK_GUIDE.md §4.2 該当節と照合
- バージョンの組合せが Maven Central / Clojars に実在するか確認
- プロジェクト固有で追加したライブラリ（DB ドライバ等）のバージョン整合性を確認
- component の deps.edn で I/O ライブラリを誤って書いていないか確認（ドメイン純粋性）
- 解消できない場合は、**逆操作で直前のコミット状態に戻す**（CLAUDE.md §7 自己停止プロトコル、選択肢 B ブランチ破棄）

### 5.2 `poly check` が通らない

手作業で brick を作成していないか確認。`poly create` 経由でないと構造が認識されない。
POLYLITH_GUIDE.md §5 「Polylith 特有の頻出誤りと対処」も参照。

### 5.3 `(go)` で例外、または REPL 起動時に ClassNotFoundException

Integrant と Malli の起動順序、`set-refresh-dirs` の対象、`add-tap` の配線などを確認。`development/src/dev/user.clj` の docstring を確認。**よくある失敗**:

- **ClassNotFoundException（brick 依存未解決）**: `(require 'acme.inventory.api.system)` で `java.lang.ClassNotFoundException: integrant.core` 等が発生する場合、ワークスペースルート `deps.edn` の `:dev :extra-deps` に brick が `:local/root` 登録されていない。tools.deps は `:extra-paths` だけでは brick の deps.edn を自動解決しない。→ §2.5 参照、`poly/<domain> {:local/root "components/<domain>"}` 等を追加
- **FileNotFoundException: config.edn**: `config.edn` が classpath に含まれていない。開発時は `projects/<deploy>/resources` が `:dev :extra-paths` に追加されているか確認（§2.5）、または dev/user.clj の `config` 関数で `io/file` でファイルパス直接指定する代替手段も可（POLYLITH_GUIDE.md §2.4）
- **config.edn の未作成**: Integrant を含む stack を採用したのに config.edn が存在しない → §2.7 参照
- **aero の `#env` 参照先未定義**: 環境変数が未設定、または aero の記法ミス
- **Integrant key の init-key 未定義**: `defmethod ig/init-key :xxx [_ _] ...` を書き忘れ
- **dev/user.clj の Integrant セクション未有効化**: `(ig-repl/set-prep! config)` 等が配布時のまま無効化状態

### 5.4 採用 stack の §4.2.X 採用時の確認事項を満たしていない

STACK_GUIDE.md §4.2.X の「採用時の確認事項」リストを点検し、漏れている項目を補完する。特に：

- 設定ファイル（config.edn、logging publisher 設定等）の未作成
- プロジェクト固有ライブラリ（DB ドライバ、キュークライアント等）の未追加
- 必要な機能カテゴリ（HTTP サーバ実装、JSON 処理等）のライブラリが未採用

**注意**: §4.2 の推奨ライブラリ完全一致は求められていない。機能カテゴリを満たす別ライブラリを採用している場合は OK（ただし逸脱理由を ADR で記録）。

### 5.5 uberjar ビルドに dev-tools ライブラリが混入している

`:dev :extra-deps` に配置すべき開発支援ライブラリが brick の deps.edn に誤って書かれている可能性。brick 側から削除し、ワークスペースルートの `:dev :extra-deps` に移動する。

### 5.6 何を試しても動かない

`../CLAUDE.md` §7 自己停止プロトコルの発動条件に達したら：

1. 選択肢 D（人間による設計判断を求める）を選ぶ
2. `../.llm/memory/QUESTIONS.md` §0.9 の手順で新規 Q を立てる
3. ユーザの判断を待つ（自走しない）
