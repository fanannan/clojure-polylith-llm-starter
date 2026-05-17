# POLYLITH_GUIDE.md — Polylith 構造運用ガイド

本文書は **`../CLAUDE.md` §1 原理のうち「機械化」（§1.2.1）を Polylith という構造で実装する**ための詳細資料である。
CLAUDE.md が常時制約、CODING_GUIDE.md が Clojure の書き方を規定するのに対し、本文書は**Polylith 特有の構造判断と手順**を扱う。

| 項目 | 内容 |
|---|---|
| **対象** | Polylith 構造を触る人間と LLM |
| **使うタイミング** | brick 追加時、境界判断時、`poly check` 失敗時 |
| **正本性** | Polylith 構造判断と手順の正本 |
| **読み方** | まず冒頭の位置づけと前提を読み、その後に該当手順節だけ読む |

## いつ本文書を読むか

- 新規 brick（component / base / project）を追加する時
- `poly check` / `poly test` でエラーが出て対処に迷う時
- コンポーネント境界の設計判断（切るか、統合するか、共通化するか）に迷う時
- `workspace.edn` や `tag-patterns` の運用を変更する時

## Polylith の位置づけ（原理との接続）

Polylith は **../CLAUDE.md §1.1.3 副作用の隔離 + §1.2.1 機械化** の実装である：

- **`interface.clj` による境界**: ドメインとアダプタの依存方向を構造で強制
- **`poly check` による機械検証**: 規約違反を人間の注意力ではなく CLI で検出
- **`poly test` による影響範囲テスト**: §1.2.2 ループ短縮の実現（変更箇所に応じて必要最小限のテストだけ実行）

---

## 越境ユースケースの機械化（上位原理）

複数 entity / 複数 entrypoint をまたぐ処理（以下「越境ユースケース」）は、Polylith 構造の上で**見落とされやすく再発見コストの高い領域**である。本テンプレートはこれを `../CLAUDE.md §1.2.1 機械化` と `§1.2.2 ループ短縮` の合成として最優先の機械化対象に位置づける。

**上位原理**: 越境ユースケースは、人間の記憶ではなく **ns 配置（抽出）・REPL helper（起動）・境界テスト（検証）** の 3 つの機械化手段で再現可能にする。新しい越境ユースケースに遭遇した時は、以下の 3 派生それぞれに該当する作業が完了しているか自己点検する。

### 派生 1: 抽出 — orchestration ns

越境処理は handler.clj に書かず、`<uc>-orchestration` という名前付き ns へ抽出する。配置基準は entrypoint の共有度に依存する条件型判定（§2.2.1）：

- **単一 entrypoint 専用**（HTTP API だけが呼ぶ等）→ `bases/<base>/src/.../<uc>_orchestration.clj` に sub-ns 配置
- **複数 entrypoint 共有**（API + CLI + UI が同じ処理を呼ぶ）→ `components/<uc>-orchestration/` として component 昇格

handler.clj は入出力変換のみに薄く保ち、実処理は orchestration へ委譲する。

### 派生 2: 起動 — `(safe-reset!) → (seed-all!)` 2 行原則

越境ユースケースの開発・PR smoke test では、その境界を **2 行で立ち上げられる状態**を維持する：

```clojure
(safe-reset!)   ; refresh / lifecycle reset
(seed-all!)     ; seed helper による fixture 投入
```

- 配置: `development/src/dev/fixtures.clj`（派生プロジェクト側で実体化）
- 命名: 全体を担う `(seed-all!)` と、UC 単位の `(seed-<uc>!)` の 2 階層
- `seed-all!` は `(doseq [f [seed-<uc1>! seed-<uc2>! ...]] (f))` で組み立てる

本テンプレート配布時には `dev/fixtures.clj` は置かない（brick 未配置と整合）。派生プロジェクトが brick を生やしたタイミングで本規約に従って実体化する。詳細手順は §7.4。

### 派生 3: 検証 — 越境 tx の原子性 assert

越境 tx を持つ orchestration には、**原子性を主張する境界テストを 1 つ以上置く**。これは `../CLAUDE.md §1.1.1 全域性` の境界契約の延長として独立に成立する規律であり、「越境処理を書いた」という事実から自動的に発生する義務である。

検証**手段**は採用 DB に依存する。テンプレートは中立を保ち、規約化するのは **「assert する」** 規律のみ：

| 採用 DB | 原子性 assert の典型実装 |
|---|---|
| next.jdbc（RDBMS） | tx handle を 1 つに固定し、orchestration 内で取得 connection の `System/identityHashCode` 一致を assert、または `with-transaction` ネスト検出 |
| XTDB | 各 entity の `_system_from` metadata 一致を assert（`(= 1 (count (set system-froms)))`） |
| Datomic | 単一 tx report に期待 datom がすべて含まれることを assert、または期待 datom の `:tx` が同一であることを assert |

派生プロジェクトはこの 3 派生を「越境ユースケースを書く時のチェックリスト」として運用する。**該当する派生をすべて満たす**。具体的には、抽出（派生 1）と起動（派生 2）はすべての越境ユースケース（read-only な集計や外部 API 連携を含む）に適用する。検証（派生 3）は**越境 tx を持つ orchestration**（複数 entity の write を 1 tx で行う処理）にのみ必須。

### 派生間の運用順序

派生 3（test）は派生 2（fixture）が提供する境界 state を前提とするため、**fixture を REPL で実際に観察してから test の precondition を確定する**。fixture 未観察の想像 state で Test Plan を書くと、後追いの fixture 変更が既書 test の設計を反復させる（`../CLAUDE.md §1.2.2 ループ短縮`違反）。派生 1（orchestration 配置）は派生 2 / 3 の観察後に調整可能。具体的な手順は §7.4.1。

---

## 1. brick 種別と責務

| 種別 | 責務 | §1.1 原則のどれを担うか |
|---|---|---|
| **component** | 再利用可能な機能ブロック。`interface.clj` で公開し、`core.clj` で実装。ドメインロジック・共通ユーティリティ・外部システム向けアダプタとしても成立 | ドメイン実装は **不変性 + 全域性**、副作用を持つ実装は **副作用隔離** |
| **base** | アプリ外部に対する公開 API（REST / CLI / Lambda / gRPC / GraphQL など）を担う入口。内部実装は薄く、実体の処理は component に委譲する（公式: base は public API を公開する special brick） | **副作用隔離**（副作用の入口・配線を集約） |
| **project** | デプロイ単位。components + bases の組合せを `:local/root` で参照 | **機械化**（配線のみ、ライブラリ依存禁止） |
| **development** | 開発用の統合 REPL。全 brick を単一プロセスで読み込む | **ループ短縮**（§1.2.2） |

### 1.1 Brick Map と `brick.edn`

brick の機能分担は Markdown を正本にしない。各 brick 直下の `brick.edn` を機械可読な設計意図の正本とし、閲覧用 Brick Map と検索用の `.llm/data/brick-map.edn` は `brick.edn` と `interface.clj` から生成する。

目的は、必要な機能をどの component に頼ればよいか、base がどの entrypoint からどの component capability を使うか、重複実装が発生していないかを常に機械検査できる状態にすることである。

`brick.edn` はコードの代替正本ではない。公開 API と Malli 契約の正本は `interface.clj`、実装事実の正本は `core.clj` / `system.clj` 等、ライブラリ依存の正本は brick の `deps.edn` である。`brick.edn` が担うのは、コードだけから安全に復元できない責務・capability ownership・not-for・要求対応・作成者・ライセンスである。作成者（`:brick/authors`）とライセンス（`:brick/license`）は、別 repo の brick を参照・流用する際の著作権判断に使う（§9.4）。

`:brick/group` を導入する場合、その役割は類似 brick の検索・俯瞰・再分割 smell の発見に限定する。group は Polylith の構造単位ではなく、依存境界、capability ownership、project inclusion、テスト範囲、deploy 単位を決める正本にしてはならない。構造事実は component / base / project、`interface.clj`、`workspace.edn`、`deps.edn`、`project.edn`、`poly check` に残す。

`:brick/group` は任意の単数 keyword とする。set や vector は許容しない。複数 group を許すと brick の主責務が曖昧になり、group が tag 的に使われて構造判断へ流れやすい。補助的な検索軸が必要になった場合は、`:brick/group` を多値化せず、別概念として採否を判断する。

新規 brick では、実装より先に `brick.edn` を作る。`brick.edn` で capability ownership を決めてから `interface.clj` の公開 API と `m/=>` 契約を設計し、実装を進める。

仕様 trace metadata は `brick.edn` より細かい関数単位の対応を表す。`brick.edn :brick/requirements` は brick 全体の粗い ownership、`interface.clj` / base boundary の `:trace/requirements` / `:trace/use-cases` は公開関数単位の対応である。component の `core.clj`、private helper、base の `system.clj` や orchestration sub-ns には trace metadata を置かない。base boundary として trace metadata を許可するのは、外部 entrypoint に近い `core.clj` / `handler.clj` の公開 `defn` に限定する。

component の `brick.edn` は capability の所有を表す。

```clojure
{:brick/name :invoice
 :brick/type :component
 :brick/group :invoice
 :brick/purpose "請求書エンティティの生成・検証・金額計算"
 :brick/provides #{:invoice/create :invoice/validate :invoice/total-amount}
 :brick/not-for #{:pdf/render :email/send :http/response}
 :brick/requirements ["INV-01" "INV-02"]
 :brick/authors ["Hanako Tanaka <hanako@example.com>"]
 :brick/license "Apache-2.0"}
```

`component` は pure domain component と adapter component の両方を取り得る。現時点の `brick.edn` schema には `:brick/effect` を導入しないが、設計時は `:pure` / `:adapter` / `:entry` 相当の区別を必ず考える。特に `:pure` 相当の component では、時刻・乱数・I/O・可変状態を内側に置かない。将来この区別を機械検査へ接続する場合は、`brick.edn` に `:brick/effect` を追加する案を検討する。

base の `brick.edn` は外部 entrypoint と利用 capability を表す。base は capability を所有しないため、`:brick/provides` を書かない。

```clojure
{:brick/name :web-api
 :brick/type :base
 :brick/group :public-api
 :brick/purpose "HTTP API として外部リクエストを受け、component の機能へ委譲する"
 :brick/entrypoint :http-api
 :brick/uses #{:invoice/create :invoice/validate}
 :brick/requirements ["API-01"]
 :brick/authors ["Hanako Tanaka <hanako@example.com>"]}
```

`docs/BRICKS.md` と `.llm/data/brick-map.edn` は自動生成物であり、直接編集しない。再生成は次で行う。

```bash
clj -Sdeps '{:paths [".llm/scripts"]}' -X gen-brick-map/generate
```

`./.llm/scripts/check-workspace-integrity.sh` は、全 brick の `brick.edn` 存在、component/base の意味違反、重複 capability、base の未提供 capability 参照、`:brick/not-for` との衝突、capability 命名、capability と公開 API 名の対応、閲覧用 Brick Map / `.llm/data/brick-map.edn` の drift を検査する。あわせて `brick.edn` の宣言作成者（`:brick/authors`）が git 履歴と不一致なら L0 reconciliation 対象として検出する（§9.4）。

`check-interface-contracts.sh` が検査するのは、`interface.clj` の公開 `defn` に対応する `m/=>` が存在することまでである。arity 整合、schema の意味妥当性、失敗表現の妥当性はこの gate では保証しない。これらは Malli instrumentation 下の REPL eval、interface test、必要に応じた property test で検証する。

既存 repo の導入時など、`brick.edn` を持たない brick がある場合は、まず skeleton 案を出す。

```bash
./.llm/scripts/propose-brick-edn.sh
```

この提案は書き込みを行わない。実際に欠落 skeleton と下流生成物を作る場合は次を使う。

```bash
./.llm/scripts/ensure-brick-map.sh
```

`:brick/name`、`:brick/type`、公開 API 候補は実装から推測できるが、`:brick/purpose`、`:brick/provides`、`:brick/not-for`、`:brick/requirements`、`:brick/authors`、`:brick/license` は設計意図・著作権情報なので TODO として生成される。TODO は警告であり、移行完了前に人間/LLM が DESIGN と実装を見て確定する。

TODO・空の `:brick/provides`・曖昧な capability / API 対応は、`:adoption-mode :retrofit` / `:partial` では WARN、`:complete` では ERROR として扱う。これにより既存 repo 導入時は移行を止めず、strict template 準拠後は未確認事項を残さない。

要求 ID 対応の検査順序:

1. `brick.edn` が参照する `:brick/requirements` の ID が DESIGN に存在するかを検査する。存在しない ID 参照は誤リンクなので ERROR
2. DESIGN にある要求 ID がどの brick にも参照されていない場合は WARN。初期仕様・将来機能・未実装要求があり得るため、ただちに ERROR にはしない
3. 未割当要求を実装対象にする時は、先に対応する component capability または base entrypoint を決め、`brick.edn` に ID を記録してから実装する

### 1.2 既存機能の探索手順

ある機能が既存 brick に実装済みかを調べる時は、次の順に確認する。

1. capability 名が分かる場合は `.llm/data/brick-map.edn` の `:capabilities` を見る
2. 機能名・要求 ID・自然言語の語彙しか分からない場合は `docs/BRICKS.md` と `.llm/data/brick-map.edn` を検索する
3. 候補 group が分かる場合は `.llm/data/brick-map.edn` の `:groups` と閲覧用 Brick Map の `Groups` を見て、同一 group の既存 brick を確認する
4. 見つかった capability は、提供元 component の `interface.clj` 経由で利用する
5. 見つからない場合だけ、新規 component capability の追加候補として扱う
6. 新規追加前に、同一 group の既存 component の `:brick/provides` / `:brick/not-for` も確認し、既存 brick に入れるべきか、意図的に対象外とされていないかを確認する

この順序を飛ばして新規 component を作ると、同じ capability の重複実装を誘発する。`check-workspace-integrity.sh` は重複 capability を検出するが、探索手順は編集前に重複を避けるためのプロトコルである。

新規 brick を提案する場合は、少なくとも次を説明する。

```text
- 候補 group: :billing
- 同一 group の既存 brick: components/invoice, components/payment
- 既存 brick に入れない理由: 既存 brick の :brick/not-for に該当、または capability domain が異なる
- 新規 brick 候補: components/refund
- 提供 capability: #{:refund/create :refund/cancel}
```

### 1.3 公開関数名の規律

公開関数名は、`brick.edn` の capability と対応して理解できる名前にする。関数名だけで混乱する状態は、LLM が既存機能を見落として重複実装する原因になる。

原則:

- `interface.clj` の公開関数は、`brick.edn` の `:brick/provides` に対応する操作名を表す
- capability の operation 部は Clojure 関数名規約に従う。述語は末尾 `?`、破壊的操作は末尾 `!` を付けてよい（例: `:event-store/record!`、`:event-store/idempotent?`、`:event-normalizer/valid?`）。generated Brick Map の capability 検証もこの形式を受理する
- 同一 brick 内で自明な場合だけ `create`、`validate`、`parse`、`format` などの短い動詞を許容する
- 複数 entity / 複数 capability を扱う brick では、`create-invoice`、`validate-invoice`、`calculate-total-amount` のように対象語を含める
- base の公開関数は entrypoint の配線を表す名前にし、ドメイン機能を所有しているような名前にしない
- 既存 repo の既存関数名は、導入時に破壊的 rename しない。まず `brick.edn` と generated Brick Map で意味を補い、必要なら後続の通常変更として alias / deprecation / rename を計画する
- 複数 brick に同じ短い公開関数名が現れる場合は、generated Brick Map の警告対象とする。ただし、同種の操作を別 brick に分けるために `invoice/create`、`customer/create` のように関数名を揃えることは許容する。この場合、各 brick の `:brick/provides` が `:<domain>/<operation>` 形式で一意であり、呼び出し側の namespace alias と合わせて意味が完成している必要がある

命名判断:

| 状況 | 推奨 |
|---|---|
| component が単一 entity の主要操作だけを持つ | `create` / `validate` など短い名前を許容 |
| component が複数 entity または複数 capability を持つ | 対象語を含める |
| 同じ動詞が複数 capability に対応し得る | capability 名に寄せて具体化 |
| 同種の操作を別 brick で揃える | 関数名の重複を許容。ただし `:brick/provides` は一意にし、namespace alias で意味が読めること |
| 既存関数名が曖昧だが外部利用がある | rename せず `brick.edn` に意味を記録し、移行計画を別途作る |

例:

```clojure
;; OK: namespace + 関数名 + capability ownership で意味が明確
(invoice/create input)  ; :invoice/create
(customer/create input) ; :customer/create

;; 要再考: brick 名も capability も曖昧
(manager/create input)
```

### 1.4 Project Map と Workspace Map

project には capability を持たせない。project は deploy / build 単位であり、どの base entrypoint を出荷し、どの brick を束ねるかという意図だけを `projects/<name>/project.edn` に記録する。

```clojure
{:project/name :api
 :project/type :app
 :project/runtime :service
 :project/purpose "HTTP API を uberjar として出荷する"
 :project/entrypoints #{:http-api}
 :project/includes {:bases #{:web-api}
                    :components #{:invoice :customer}}
 :project/requirements ["API-01"]
 :project/build {:kind :uberjar}}
```

`:project/type` は検査ポリシーであり、語彙は `:app` / `:library` の 2 つに限定する。`:app` は実行・deploy される成果物で、base / entrypoint を原則必須とする。`:library` は非 deploy の bundle で、base / entrypoint なしを許容する。service / worker / CLI / batch / lambda などの実行形態は、必要な場合だけ任意の `:project/runtime` に書く。

非 deploy の library project は次のように書く。

```clojure
{:project/name :domain-lib
 :project/type :library
 :project/purpose "Reusable domain bundle"
 :project/entrypoints #{}
 :project/includes {:bases #{}
                    :components #{:invoice :customer}}
 :project/requirements []
 :project/build {:kind :jar}}
```

`project.edn` は deploy intent の正本であり、classpath や依存の正本ではない。実際の project 依存は `projects/<name>/deps.edn` が正本であり、Polylith の構造事実は `poly check` に委譲する。`project.edn` と `deps.edn` の includes がずれている場合は生成検査で警告する。

workspace については、手書きの追加正本を増やさない。`workspace.edn`、`deps.edn`、`.llm/repo-context.edn`、`brick.edn`、`project.edn` から、閲覧用 Workspace Map と検索用の `.llm/data/workspace-map.edn` を生成する。

生成:

```bash
clj -Sdeps '{:paths [".llm/scripts"]}' -X gen-workspace-map/generate
```

既存 repo の導入時など、`project.edn` が欠落している場合は skeleton 案を表示できる。

```bash
./.llm/scripts/propose-project-edn.sh
```

実際に欠落 skeleton と下流生成物を作る場合は次を使う。

```bash
./.llm/scripts/ensure-workspace-map.sh
```

Project / Workspace Map の検査は、`workspace.edn :projects` と `projects/*/project.edn` の対応、project entrypoint と base `:brick/entrypoint` の対応、project includes と実在 brick、project deps の `:local/root`、project deps への外部ライブラリ直書き禁止を確認する。

---

## 2. brick のコード例（**この節が書き方の正本**）

本テンプレートには初期状態で brick サンプルは**配布されない**（特定ドメイン仮定を避けるため）。
代わりに、各種別の**標準的な書き方**を以下にコード例として示す。
`poly create` で生成した brick を埋める時、**この節を参照して同じ流儀で書く**。独自の流儀を発明しない。

### 2.1 component — 純粋ドメイン

#### `components/<domain>/deps.edn`

```clojure
;; ドメイン系 component の deps.edn（基本方針）
;; ★ このテンプレートでは、コンポーネントは再利用と置換が目的の単位として扱う。
;;   まずは I/O を持たない実装寄せを基本とする。
;;   ただし外部連携をまとめて差し替え可能にする目的で“アダプタ component”を
;;   置く場合は、next.jdbc や hato 等の依存追加を許容する（公式の位置づけと整合）。
;; ※ バージョンは参考値。正本は STACK_GUIDE.md §2.1 / §3 機能別節（コピペ時に要確認）
{:paths ["src" "resources"]
 :deps  {org.clojure/clojure {:mvn/version "1.12.0"}
         metosin/malli       {:mvn/version "0.16.4"}}
 :aliases
 {:test
  {:extra-paths ["test"]
   :extra-deps  {org.clojure/test.check     {:mvn/version "1.1.1"}
                 nubank/matcher-combinators {:mvn/version "3.9.1"}}}}}
```

#### `components/<domain>/src/myorg/myapp/<domain>/interface.clj`

```clojure
(ns myorg.myapp.<domain>.interface
  "<domain> コンポーネントの公開 API。
   外部からはこの名前空間のみが require 可能。
   実装は core 以下に配置。`m/=>` 関数契約は境界である
   ここに集約する（§1.1.1 全域性）。"
  (:require
   [malli.core :as m]
   [myorg.myapp.<domain>.core :as core]))

;; --- 型 ---
(def Entity       core/Entity)
(def CreateInput  core/CreateInput)

;; --- 関数契約（境界契約の集約）---
(m/=> create   [:=> [:cat CreateInput] Entity])
(m/=> validate [:=> [:cat :any] :boolean])

;; --- API（委譲）---
(defn ^{:trace/requirements ["REQ-001"]
        :trace/use-cases ["UC-1"]}
  create
  [input]
  (core/create input))

(defn ^{:trace/requirements ["REQ-002"]}
  validate
  [entity]
  (core/validate entity))
```

#### `components/<domain>/src/myorg/myapp/<domain>/core.clj`

```clojure
(ns myorg.myapp.<domain>.core
  "<domain> コンポーネントの実装本体。
   他コンポーネントから require 禁止（poly check で検出）。
   `m/=>` 契約は interface.clj 側に集約するため、本ファイルでは
   スキーマ定義と純粋関数のみを置く。"
  (:require
   [malli.core :as m]))

;; --- スキーマ（§1.1.1 全域性の境界契約）---
(def Entity
  [:map
   [:<domain>/id    :uuid]
   [:<domain>/name  [:string {:min 1 :max 100}]]
   [:<domain>/created-at inst?]])

(def CreateInput
  [:map
   [:id :uuid]
   [:created-at inst?]
   [:name [:string {:min 1 :max 100}]]])

;; --- 純粋関数 ---
(defn create
  "Entity を構築する純粋関数。id / created-at は呼び出し側で生成して渡す。"
  [{:keys [id name created-at]}]
  {:<domain>/id         id
   :<domain>/name       name
   :<domain>/created-at created-at})

(defn validate
  "Entity がスキーマに適合するかを返す。"
  [entity]
  (m/validate Entity entity))

;; --- リッチコメント ---
(comment
  (create {:id (random-uuid) :name "test" :created-at (java.time.Instant/now)})
  (validate (create {:id (random-uuid) :name "test" :created-at (java.time.Instant/now)})))
```

時刻・乱数・UUID 生成は非決定性を持つため、ドメイン component の `core.clj` では呼び出さない。
base / orchestration / REPL fixture などの外側で生成し、値として component に渡す。
これにより純粋関数、プロパティテスト、REPL 再現性を保つ。

#### `components/<domain>/test/myorg/myapp/<domain>/interface_test.clj`

```clojure
(ns myorg.myapp.<domain>.interface-test
  "テストは原則 interface 経由で書く（実装変更に頑健）。"
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [clojure.test.check.clojure-test :refer [defspec]]
   [clojure.test.check.properties :as prop]
   [malli.core :as m]
   [malli.dev :as mdev]
   [malli.dev.pretty :as mpretty]
   [malli.generator :as mg]
   [matcher-combinators.test]
   [myorg.myapp.<domain>.interface :as d]))

(defn with-malli-instrumentation [f]
  (mdev/start! {:report (mpretty/reporter)})
  (try
    (f)
    (finally
      (mdev/stop!))))

(use-fixtures :once with-malli-instrumentation)

(deftest ^{:trace/test-obligations ["AC-001"]
           :trace/requirements ["REQ-001"]
           :trace/use-cases ["UC-1"]}
  create-test
  (testing "create は必須項目から Entity を構築する"
    (let [id #uuid "00000000-0000-0000-0000-000000000001"
          created-at #inst "2026-01-01T00:00:00.000-00:00"
          e (d/create {:id id :name "Alice" :created-at created-at})]
      (is (match? {:<domain>/name       "Alice"
                   :<domain>/id         id
                   :<domain>/created-at created-at}
                  e))
      (is (m/validate d/Entity e)))))

;; プロパティテスト: Malli スキーマから自動生成される generator を活用
(defspec create-always-yields-valid-entity 100
  (prop/for-all [input (mg/generator d/CreateInput)]
    (m/validate d/Entity (d/create input))))
```

`poly test` の通過は高速な回帰確認であり、契約検証完了そのものではない。
`m/=>` 契約をテスト実行中にも有効化したい場合、test fixture で Malli instrumentation を `:once` で有効化する。
この fixture は `with-malli-instrumentation` のような named `defn` として定義し、`use-fixtures :once` から参照する。匿名 fixture はテンプレート規約外。
fixture の形は `mdev/start!` で開始し、`(try (f) (finally (mdev/stop!)))` で必ず停止する完全形を使う。
テンプレート側では `.llm/scripts/check-test-instrumentation.sh` が `interface_test.clj` の fixture 欠落を検査し、`check-workspace-integrity.sh` から実行される。

### 2.2 base — HTTP API エントリの例

以下は **HTTP API エントリの具体例**。採用ライブラリは STACK_GUIDE の機能別節で確認し、HTTP API 以外の entry base では必要な機能カテゴリに合わせて読み替える。ライフサイクル管理が不要なライブラリ配布や単発 CLI では lifecycle helper 不要、`-main` 直列起動で構成する。
∵ STACK_GUIDE.md §3

#### `bases/<entry>/deps.edn`

```clojure
;; HTTP エントリ base の deps.edn
;; HTTP API entry base の構成例（Ring + Reitit + Integrant + Malli + mulog を束ねる）
;; ※ バージョンは参考値。正本は STACK_GUIDE.md §2.1 / §3 機能別節（コピペ時に要確認）
{:paths ["src" "resources"]
 :deps  {org.clojure/clojure         {:mvn/version "1.12.0"}
         metosin/malli               {:mvn/version "0.16.4"}
         ring/ring-core              {:mvn/version "1.13.0"}
         ring/ring-jetty-adapter     {:mvn/version "1.13.0"}
         metosin/reitit              {:mvn/version "0.7.2"}
         metosin/reitit-ring         {:mvn/version "0.7.2"}
         metosin/reitit-malli        {:mvn/version "0.7.2"}
         metosin/muuntaja            {:mvn/version "0.6.10"}   ; reitit の format-middleware で使用（§2.3 参照）
         metosin/jsonista            {:mvn/version "0.3.11"}
         integrant/integrant         {:mvn/version "0.13.1"}
         aero/aero                   {:mvn/version "1.1.6"}
         com.brunobonacci/mulog      {:mvn/version "0.9.0"}
         com.brunobonacci/mulog-json {:mvn/version "0.9.0"}   ; :console-json publisher 実装（§2.3 config.edn 参照）
         ;; 使う component への依存
         poly/<domain>               {:local/root "../../components/<domain>"}}}
```

#### `bases/<entry>/src/myorg/myapp/<entry>/core.clj`（エントリ）

```clojure
(ns myorg.myapp.<entry>.core
  "<entry> ベースのエントリポイント。uberjar 実行時の -main。"
  (:require
   [aero.core :as aero]
   [clojure.java.io :as io]
   [com.brunobonacci.mulog :as mulog]
   [integrant.core :as ig]
   [myorg.myapp.<entry>.system])   ; defmethod 登録のため require
  (:gen-class))

(defn -main [& _args]
  (let [profile (keyword (or (System/getenv "APP_PROFILE") "prod"))
        cfg     (aero/read-config (io/resource "config.edn") {:profile profile})
        system  (ig/init cfg)]
    (mulog/log ::started :profile profile)
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. ^Runnable #(do (mulog/log ::stopping)
                                              (ig/halt! system))))
    @(promise)))
```

#### `bases/<entry>/src/myorg/myapp/<entry>/system.clj`（Integrant 配線）

```clojure
(ns myorg.myapp.<entry>.system
  "Integrant defmethod 集約。config.edn の各キーに対応する init-key / halt-key! を定義。"
  (:require
   [com.brunobonacci.mulog :as mulog]
   [integrant.core :as ig]
   [myorg.myapp.<entry>.handler :as handler]
   [ring.adapter.jetty :as jetty]))

(defmethod ig/init-key ::logger [_ {:keys [type] :as cfg}]
  (mulog/start-publisher! (assoc cfg :type type)))

(defmethod ig/halt-key! ::logger [_ stop-fn]
  (when stop-fn (stop-fn)))

(defmethod ig/init-key ::handler [_ deps]
  (handler/build-app deps))

(defmethod ig/init-key ::server [_ {:keys [handler port]}]
  (mulog/log ::starting-server :port port)
  (jetty/run-jetty handler {:port port :join? false}))

(defmethod ig/halt-key! ::server [_ ^org.eclipse.jetty.server.Server server]
  (.stop server))
```

#### `bases/<entry>/src/myorg/myapp/<entry>/handler.clj`（Reitit ルータ）

```clojure
(ns myorg.myapp.<entry>.handler
  "★ 他コンポーネントは必ず interface 経由で require する。"
  (:require
   [muuntaja.core :as muuntaja]
   [myorg.myapp.<domain>.interface :as d]
   [reitit.coercion.malli]
   [reitit.ring :as ring]
   [reitit.ring.coercion :as rrc]
   [reitit.ring.middleware.muuntaja :as muuntaja-mw]
   [reitit.ring.middleware.parameters :as parameters]))

(defn- create-handler [_deps]
  (fn [{:keys [parameters]}]
    {:status 201
     :body   (d/create (:body parameters))}))

(defn- health-handler [_req]
  {:status 200 :body {:status "ok"}})

(defn routes [deps]
  [["/health" {:get health-handler}]
   ["/<domain>"
    {:post {:summary    "<domain> を作成する"
            :parameters {:body d/CreateInput}
            :responses  {201 {:body d/Entity}}
            :handler    (create-handler deps)}}]])

(defn build-app [deps]
  (ring/ring-handler
   (ring/router
    (routes deps)
    {:data {:coercion   reitit.coercion.malli/coercion
            :muuntaja   muuntaja/instance
            :middleware [parameters/parameters-middleware
                         muuntaja-mw/format-middleware
                         rrc/coerce-exceptions-middleware
                         rrc/coerce-request-middleware
                         rrc/coerce-response-middleware]}})
   (ring/create-default-handler)))
```

#### 2.2.1 越境処理の抽出 — `<uc>-orchestration` ns

複数 entity を跨ぐ処理や、複数 entrypoint（HTTP / CLI / UI 等）から共有される処理を handler.clj に直書きしない。**`<uc>-orchestration` という名前付き ns に抽出**する（上位原理「越境ユースケースの機械化」派生 1）。

**配置基準（条件型）**:

- **単一 entrypoint 専用**: orchestration が 1 つの base からしか呼ばれないことが明らかなら `bases/<entry>/src/myorg/myapp/<entry>/<uc>_orchestration.clj` に sub-ns で置く
- **複数 entrypoint 共有**: 2 つ以上の base / entrypoint から同じ orchestration を呼ぶなら、即座に `components/<uc>-orchestration/` として component に昇格させる

「将来 UI からも呼ぶかもしれない」という推測で先に component 化しない（YAGNI）。実際に 2 つ目の呼び出し元が発生した時点で昇格させる。逆に、最初から複数 entrypoint で呼ぶ予定が明確なら、最初から component に置く。

**抽象例（base 内 sub-ns 版）**:

```clojure
;; bases/<entry>/src/myorg/myapp/<entry>/<uc>_orchestration.clj
(ns myorg.myapp.<entry>.<uc>-orchestration
  "<uc> ユースケース：複数 entity を跨ぐ orchestration。
   本 base の handler.clj から呼ばれる（単一 entrypoint 専用）。
   別 entrypoint からも呼ぶ必要が生じた時点で components/<uc>-orchestration/ へ昇格する。"
  (:require
   [myorg.myapp.<domain-a>.interface :as a]
   [myorg.myapp.<domain-b>.interface :as b]))

(defn process [deps inputs]
  ;; 越境 tx の組み立て：1 つの tx で a と b を更新
  ;; 検証側（派生 3）でこの原子性を境界テストにより assert する
  ...)
```

**handler 側**:

```clojure
;; handler.clj は薄く保つ：入出力変換のみ
(:require
 [myorg.myapp.<entry>.<uc>-orchestration :as uc])

(defn- <uc>-handler [deps]
  (fn [{:keys [parameters]}]
    {:status 201
     :body   (uc/process deps (:body parameters))}))
```

`refactor → feature` の 2 コミット分割（既存 handler から orchestration を抽出するコミット → orchestration を拡張するコミット）は一般原則に従う。本節では追加規約化しない。
∵ ../CLAUDE.md §8.3
∵ ../CLAUDE.md §1.2.3

### 2.3 project — デプロイ単位

#### `projects/<deploy>/deps.edn`（★ `:local/root` のみ、ライブラリ依存禁止）

```clojure
{:paths []
 :deps  {poly/<entry>  {:local/root "../../bases/<entry>"}
         poly/<domain> {:local/root "../../components/<domain>"}}
 :aliases
 {:build
  {:deps      {io.github.clojure/tools.build {:git/tag "v0.10.5" :git/sha "2a21b7a"}}
   :ns-default build}}}
```

#### `projects/<deploy>/build.clj`（uberjar 構築）

```clojure
(ns build
  "<deploy> project の uberjar 構築。`clj -T:build uber` で実行。"
  (:require [clojure.tools.build.api :as b]))

(def lib       'myorg/<deploy>)
(def main-ns   'myorg.myapp.<entry>.core)
(def version   (format "0.1.%s" (b/git-count-revs nil)))
(def class-dir "target/classes")
(def uber-file (format "target/%s-%s-standalone.jar" (name lib) version))

(defn- basis [] (b/create-basis {:project "deps.edn"}))

(defn clean [_] (b/delete {:path "target"}))

(defn uber [_]
  (clean nil)
  (b/copy-dir
   {:src-dirs   (-> (basis) :paths
                    (concat ["resources"
                             "../../components/<domain>/resources"
                             "../../bases/<entry>/resources"]))
    :target-dir class-dir})
  (b/compile-clj {:basis (basis) :ns-compile [main-ns] :class-dir class-dir})
  (b/uber {:class-dir class-dir :uber-file uber-file :basis (basis) :main main-ns})
  (println "Built:" uber-file))
```

#### `projects/<deploy>/resources/config.edn`（aero 経由の Integrant 設定）

```clojure
{:myorg.myapp.<entry>.system/logger
 {:type :console-json}

 :myorg.myapp.<entry>.system/handler
 {}

 :myorg.myapp.<entry>.system/server
 {:handler #ig/ref :myorg.myapp.<entry>.system/handler
  :port    #profile {:dev  3000
                     :test 0
                     :prod #long #or [#env PORT 8080]}}}
```

### 2.4 development/src/dev/user.clj の config 関数実装例

初期配布の `dev/user.clj` では `config` 関数が placeholder。最初の project を作成後、以下のように実装：

```clojure
(defn config []
  (aero/read-config (io/resource "config.edn") {:profile :dev}))
```

`:require` に `[aero.core :as aero]` と `[clojure.java.io :as io]` を追加する。

**前提**: `projects/<deploy>/resources` がワークスペースルート `deps.edn` の `:dev :extra-paths` に追加されていること（BOOTSTRAP_GUIDE.md §2.5）。これにより `io/resource "config.edn"` が classpath 経由で読める。本番（`core.clj` の `-main`）と開発で読み込み方法が統一される。

**代替**: 何らかの理由で `:dev :extra-paths` に追加できない場合、`(io/file "projects/<deploy>/resources/config.edn")` でファイルパス直接指定も動作する（ただし本番と開発で読み込み方法が違う点に留意）。

---

## 3. 新規 brick 追加手順

### 3.1 新規コンポーネント追加（**ユーザ承認必須**）

**根拠**: component はドメイン境界または再利用境界を 1 単位追加する操作であり、局所的ではあるが公開境界と依存方向に影響する。base / project ほど配備構成全体へは波及しないため、権限は承認必須 (L1) に置く。

```bash
clj -M:poly create component name:<name>
```

これで以下が自動生成される：

```
components/<name>/
├── deps.edn
├── resources/
├── src/myorg/myapp/<name>/
│   ├── interface.clj
│   └── core.clj
└── test/myorg/myapp/<name>/
    └── interface_test.clj
```

その後の手順：

1. 実装前に §1.2 の探索手順を再実行し、候補 group、同一 group の既存 brick、既存 brick に入れない理由を確認する
2. `brick.edn` を作成し、`:brick/type :component`、`:brick/provides`、`:brick/requirements`、必要に応じて任意の `:brick/group` を記録する
3. `deps.edn` に必要な依存を追加（**ライブラリ依存はここに書く**、project には書かない）
4. 本文書 §2.1 component コード例を参照して以下を埋める：
   - `core.clj`: Malli スキーマ + 純粋関数（`m/=>` 契約は置かない）
   - `interface.clj`: core への薄い委譲 + `m/=>` 契約集約（境界契約、§1.1.1）（100 行超えたら実装漏れ、core に戻す）
   - `interface_test.clj`: clojure.test + プロパティテスト
5. Integrant key を提供する場合は entry base の `system.clj`（§2.2 のコード例）の defmethod 集約に追加
6. project の `deps.edn` に `:local/root` で登録
7. development の `deps.edn` の `:dev` エイリアス `:extra-paths` にソースパスを追加（`components/<name>/src` 等）
8. **development の `deps.edn` の `:dev` エイリアス `:extra-deps` に `:local/root` で登録**（`poly/<name> {:local/root "components/<name>"}`）。これにより brick deps.edn の `:deps` が推移的解決され、REPL で利用可能になる
9. `clj -Sdeps '{:paths [".llm/scripts"]}' -X gen-brick-map/generate` で `docs/BRICKS.md` を再生成
10. `clj -M:poly check` で構造検証
11. `clj -M:poly test project:<project-name>` で特定 project 配下の brick テストを実行（全 project・全 brick を流すなら `clj -M:poly test :all`）

### 3.2 新規ベース追加（**人間専権**）

**根拠**: base の追加は entrypoint・起動経路・I/O 配線の単位を増やす操作であり、component 追加より構造影響が大きい。実装追加ではなく配備・運用構成の判断を含むため、人間専権 (L0) とする。

```bash
clj -M:poly create base name:<name>
```

用途の例：HTTP API（既存 entry base と）は別の CLI、Lambda 関数、バッチジョブなど。

その後、`bases/<name>/brick.edn` に `:brick/type :base`、`:brick/entrypoint`、`:brick/uses` を記録する。base は capability を所有しないため `:brick/provides` を書かない。続いて `bases/<name>/deps.edn` に必要なライブラリを追加、本文書 §2.2 base コード例を雛形として `core.clj`/`system.clj` などを実装し、`docs/BRICKS.md` を再生成する。

### 3.3 新規プロジェクト追加（**人間専権**）

**根拠**: project の追加はデプロイ単位・ビルド単位・CI 単位を新設する操作であり、ワークスペース全体の配備構成を変える。不可逆性と波及範囲が大きいため、人間専権 (L0) とする。

```bash
clj -M:poly create project name:<name>
```

デプロイ単位を分ける時に使う（例: Web API と worker を別の uberjar にする等）。

その後：

1. `projects/<name>/deps.edn` に `:local/root` で components / bases を参照
2. `projects/<name>/build.clj` を新規作成（本文書 §2.3 の build.clj 例を雛形として使う）
3. `workspace.edn` の `:projects` に登録
4. CI に組込（lint / format / poly check / test / build uber）

---

## 4. コンポーネント境界の判断（**最重要の判断領域**）

**自己判断禁止**。迷ったら必ず `../.llm/memory/QUESTIONS.md` に Q を立てる。

### 4.1 新規コンポーネントを切るべき兆候

- **ドメインの言葉で名前が付けられる**（`user`, `order`, `inventory` など、業務用語で呼べる）
- **外部 API を公開しないが、再利用可能な共通ロジックを持つ**（複数 base/component で使う）
- 複数の base / component から同じロジックを参照する
- I/O の種類が独立している（別の DB、別の外部サービス）
- 責務が独立しており、変更理由が別々
- 外部サービス向けのラッパーを「置換可能なアダプタ」として共有したい（外部依存の抽象化）

補足（公式との整合）: コンポーネントはドメイン寄りが基本だが、認証・DB/外部API などの
"integration point" を component として切り出す設計も公式文書は許容している。

### 4.2 切るべきでない兆候

- 機能単位で切ろうとしている（`create-user`, `delete-user` を別 component にしたい等） → それはドメインとして一つの component
- interface が 1〜2 関数しかない → まだ切るには早い
- 他 component から呼ばれる予定がない → base の内部で関数として切り出すだけで足りる
- 「将来使うかもしれない」という予感だけ → YAGNI

### 4.3 迷う場合の原則（公式観点）

- **外部公開 API の責務を持つなら base**（HTTP/Lambda/CLI など）
- **再利用可能なロジックや交換可能な連携層なら component**
- **base から見て delegate が自然なら component、base 自身がプロトコルを構成するなら base**

### 4.4 Polylith の最大の罠：切りすぎ

Polylith の例題は教育目的で粒度が細かい傾向がある。それを真似ると小さな interface が乱立し、逆に保守性が下がる。実用の判定軸は：

- **ドメインモデルの一塊**
- **外部システムとのアダプタ一単位**
- **横断的関心事一単位**

この 3 分類で粒度を判定する。

### 4.5 コンポーネントの統合・分割（**承認必須**）

既存コンポーネントの統合・分割は **../CLAUDE.md §2 で禁止事項**。影響が甚大なので必ず人間承認を得る。
判断が必要な場合は Q を立てる：

```
## Q-YYYY-MM-NNN: <component-a> と <component-b> の統合検討

**文脈**: 両コンポーネントの interface が X と Y を相互参照する実装になってきた
**選択肢**:
 A. 統合する（共通性が高い）
 B. 共通部分を新規 component に抽出（関心事分離）
 C. 現状維持（結合は許容範囲）
**推奨**: B（Polylith 流儀に沿う）
**影響範囲**: ...
```

---

## 5. Polylith 特有の頻出誤りと対処

| 誤り | 兆候 | 対処 |
|---|---|---|
| 内部 NS への直接 require | `poly check`: `Cannot reference internal namespaces from another brick` | `interface.clj` に関数追加し、interface 経由に変更 |
| 循環参照 | `poly check`: circular dependency | 共通部分を新規 component に抽出（`poly create component`）。既存 2 component を直接合流させない |
| project の deps が肥大化 | `projects/*/deps.edn` にライブラリが直書きされている | components / bases に移動。project の `deps.edn` は `:local/root` のみにする（本文書 §2.3 参照） |
| base 同士の参照 | `poly check`: base→base 依存 | 共通ロジックを component に抽出 |
| `interface.clj` の肥大化 | 100 行超 | 実装が interface に漏れている。`core.clj` に切り出して interface は薄い委譲に戻す |
| `poly test` が走らない | 変更検知が効かない | `git status` で未コミット変更を確認。Polylith は git 基準で diff を取る |
| development project が壊れる | `(reset)` で循環エラー | Integrant `defmethod` の重複・キー名衝突を確認 |
| brick 作成を手作業で行う | `poly check`: brick 認識されず | 削除して `poly create` で作り直す |
| library 依存が development にだけある | 本番ビルドで依存不足 | 本来の所属 component / base の `deps.edn` に移動 |
| `stable` タグが動かない | `poly info` で stable point が出ない | `workspace.edn` の `:tag-patterns` を確認。CI で `git tag stable-<timestamp>` が打たれているか確認（Polylith 公式では `poly create tag` のような専用コマンドは存在せず、git tag を使う） |

---

## 6. workspace.edn の運用

### 6.1 最重要キー

```clojure
{:top-namespace "myorg.myapp"            ; 全 brick の名前空間プレフィックス
 :interface-ns  "interface"              ; interface ファイル名（固定）
 :projects      {"development" {:alias "dev"}
                 "<deploy>"    {:alias "<deploy>"}}  ; DESIGN.md §8.4 で定めた project / deploy 名
 :vcs           {:name "git" :auto-add false}
 :tag-patterns  {:stable  "^stable-.*"     ; CI 通過時に打つ安定タグ
                 :release "^v[0-9].*"}}    ; 本番リリースタグ
```

### 6.2 tag-patterns の役割

`poly test` は `stable` タグからの diff で影響範囲を判定する。
したがって：

- **CI が通ったら必ず `stable-<timestamp>` タグを打つ**（人間操作または自動化）
- タグを打たないと、古い影響範囲判定に基づいて過小なテストしか走らない

### 6.3 CI の `fetch-depth`

GitHub Actions などの CI では `fetch-depth: 0` を指定する。
さもないと `poly test` が git 履歴を取れず、影響範囲判定が壊れる。また `build.clj` の `git-count-revs` も機能しない。

```yaml
- uses: actions/checkout@v4
  with:
    fetch-depth: 0
```

---

## 7. development project の運用

### 7.1 役割

- **全 brick を単一 REPL で触れる**環境（§1.2.2 ループ短縮の実装）
- `clj -M:dev:nrepl` で起動
- Integrant を使うプロジェクトでは、`development/src/dev/user.clj` のライフサイクル管理セクションを有効化した後、`(go)` で Integrant system + Malli instrumentation を一括起動する
- Integrant を使わないプロジェクトでは REPL 起動後に明示的に `(malli-on!)` を呼んで Malli instrumentation を有効化する

### 7.2 新規 brick 追加時の development 更新

新規 component / base を追加したら、workspace ルート `deps.edn` の `:dev` エイリアスを 2 箇所更新する：

**`:extra-paths` に追加**（ソースパス、REPL で namespace を読むため）:

```clojure
"components/<name>/src"
"components/<name>/resources"
"components/<name>/test"
```

**`:extra-deps` に追加**（`:local/root` 登録、brick deps.edn の依存を推移的解決するため）:

```clojure
poly/<name> {:local/root "components/<name>"}
```

これを忘れると、新規 brick は以下のいずれかの問題を起こす:

- `:extra-paths` 忘れ → `(reset)` しても REPL から namespace が見えない
- `:extra-deps` 忘れ → brick の依存ライブラリが `ClassNotFoundException`

### 7.3 development の :extra-deps への追加は開発ツール + brick :local/root のみ

プロダクションで使うライブラリ依存は**本来の所属 brick の `deps.edn`** に追加する（選択肢 H、真実の一箇所化、STACK_GUIDE.md §1.2）。development の `:dev :extra-deps` に直接追加してよいのは：

- **開発ツール**: nrepl, portal, integrant.repl, tools.namespace, test.check, matcher-combinators など（本番ビルドに混入させない）
- **brick の `:local/root` 登録**: `poly/<name> {:local/root "..."}` の形（brick deps.edn の依存を REPL で使うため）

### 7.4 開発フィードバック規約 — `(safe-reset!) → (seed-all!)` 2 行原則

越境ユースケースの開発・PR smoke test では、`development` プロセス上で**境界状態を 2 行で立ち上げられる**ように維持する（上位原理「越境ユースケースの機械化」派生 2）。これは `../CLAUDE.md §1.2.2 ループ短縮` を smoke test レベルで実装する規律である。

**配置と命名**:

- 配置: `development/src/dev/fixtures.clj`
- 命名:
  - 全体 fixture: `(seed-all!)`
  - UC 単位 fixture: `(seed-<uc>!)`（例: `(seed-order-cancellation!)`, `(seed-monthly-report!)`）

**抽象実装例**:

```clojure
;; development/src/dev/fixtures.clj
(ns dev.fixtures
  "派生プロジェクトの smoke test / REPL 駆動開発用 fixture 集約。
   (safe-reset!) → (seed-all!) の 2 行で全 UC を試せる状態にする。"
  (:require
   [myorg.myapp.<domain-a>.interface :as a]
   [myorg.myapp.<domain-b>.interface :as b]))

(defn seed-<uc1>!
  "<uc1> ユースケースの境界状態を投入する。
   越境 tx で a と b に minimum viable な値を書く。"
  []
  ...)

(defn seed-<uc2>! [] ...)

(defn seed-all!
  "全 UC の seed helper を順に呼ぶ。
   2 行 smoke test の右側。`(safe-reset!) (seed-all!)` で完全な開発状態。"
  []
  (doseq [f [seed-<uc1>! seed-<uc2>!]]
    (f)))
```

**運用規律**:

- 新しい越境 UC を追加したら、対応する `seed-<uc>!` を `dev/fixtures.clj` に追加し、`seed-all!` の vector にも登録する
- seed helper は冪等にする（`(seed-all!)` を連続呼び出ししてもエラーにならない）
- 本テンプレート配布時には `dev/fixtures.clj` は置かない（brick 未配置と整合、YAGNI）。派生プロジェクトが最初の越境 UC を実装した時点で本節に従って作る

**今後の拡張候補**: 派生プロジェクトが立てた `seed-<uc>!` を `dev.user/status` の `:capabilities` に自動検出させる機構は、helper 命名規約・配置・capability shape が安定した後に検討する。fixture-first 規律（§7.4.1）と肥大化抑制（§7.4.2）が定着するほど、`(status)` から「現在どの `seed-<uc>!` が利用可能か」を確認する需要が高まり、自動検出の動機が強化される。
∵ ../memory/QUESTIONS.md §2

#### 7.4.1 実装順序: fixture 観察ファースト

越境 UC を実装する時、fixture を REPL で実際に観察してから test の precondition を確定する。fixture 未観察の想像 state で Test Plan を書く反パターンは、後追いの fixture 変更が既書 test の設計を反復させる（`../CLAUDE.md §1.2.2 ループ短縮`違反）。

**推奨手順**:

1. 越境 UC の orchestration interface を仮置きする（派生 1、シグネチャと Malli schema）
2. UC が要求する境界 state を `seed-<uc>!` に実装する（派生 2）
3. REPL で `(safe-reset!)` → `(seed-<uc>!)` を実行し、UC を呼び出して **実 state を観察する**（`(probe ...)` や `(dev.user/status)` を使う）
4. 観察した state に基づいて test の precondition を確定し、test を書く（派生 3）
5. Test Plan / PR 本文は 4 完了後に書く（観察済 state を Fixture state summary として記載、`.llm/templates/fixture-state-summary.md` の fragment を使う）
6. 必要なら派生 1 の配置を調整する（複数 entrypoint で必要なら component 昇格、§2.2.1）

ステップ 3 が肝心。fixture を REPL で観察してから test を設計することで、想像 state と実 state の乖離による設計反復を防ぐ。

**許容される事前作業**:

派生 1 の orchestration interface 仮置きと、test の受入条件・テスト観点の**粗いスケッチ**は fixture 観察前でも書いてよい。禁止されるのは fixture 未観察の想像 state に基づいて **concrete な test 本体 / Test Plan を確定する**こと。

#### 7.4.2 fixture 肥大化の抑制

fixture-first 規律の副作用として、`seed-all!` がすべての UC state を混ぜて肥大化し、UC 間で意図しない state 干渉が発生するリスクがある（例: 「全 entity が同時刻 attendance」になり double-booking 判定で候補が消える等の観察事例）。

**規約**:

- `seed-<uc>!` は **UC 単位で独立・最小**にする（minimum viable boundary state）。他 UC の state に依存しない
- `seed-all!` は convenience であり、**test の前提は原則 `seed-<uc>!`** を使う。`(use-fixtures :once (fn [f] (seed-<uc>!) (f)))` のように個別 seed を選んで呼ぶ
- shared `seed-all!` 由来の偶然 state に依存する test を避ける。`seed-all!` 全体に依存すると、新規 UC 追加で既存 test が影響を受ける
- 「複数 UC を組み合わせた state が必要」な場合は、専用の `seed-<scenario>!` を別途定義し、`seed-all!` には混ぜない。複合シナリオは独立した名前を持つ

**判定の目安**: ある test が `seed-all!` でしか動かない場合、その test は暗黙の state 依存を持っている疑いがある。必要な seed を `seed-<uc1>! seed-<uc2>!` のように明示的に列挙できるかを点検する。

#### 7.4.3 service 型 project の起動 smoke レシピ

`(safe-reset!) → (seed-all!)` の 2 行原則は REPL プロセス上の境界状態を立ち上げる規律である。service 型 project（Web API・ワーカ等、長時間稼働する成果物）では、これに加えて **uber ビルド成果物を起動し代表リクエストで応答を確かめる起動 smoke** を、再現可能な 1 コマンドに常設する。

uber ビルド成功は成果物が生成できることだけを示し、起動して応答することは示さない。起動 smoke を常設する目的は、「動きますか」が実装完了報告の時点で既に答え済みになる状態を作ることである。これは完了条件の一部として要求される。
∵ ../CLAUDE.md §5.5

**レシピの構成**（1 コマンドに集約する）:

1. `(seed-all!)` 相当の境界 state 投入（DB を使う project では smoke 用の ephemeral / in-memory store）
2. 成果物の起動（DESIGN §8.4.1 の起動コマンド、または development プロセス上の system 起動）
3. 代表リクエストの送出と応答の assert（HTTP service なら正常系 1 件 + 主要な異常系。例: webhook 受信なら署名付き request の受理応答と不正署名の拒否応答）
4. プロセスの停止と後片付け

**配置**:

- レシピ本体は `development/src/dev/user.clj` の smoke セクションに置く。テンプレートはこのセクションを commented scaffold として配布するため、派生プロジェクトはコメント解除し、TODO を brick / deploy 構成に合わせて実装する。smoke を `projects/<deploy>/` のスクリプトへ分離してもよい
- fixture データ（`dev/fixtures.clj`）は引き続き配布しない（fixture は brick 固有の値であり scaffold 化の利得がない）。smoke レシピは構造が汎用なため scaffold を配布する、という非対称はこの差に基づく
- 起動コマンド・代表リクエスト・期待応答は DESIGN §8.4.1 と受入基準から導く

**運用規律**:

- service 型 project の完了報告前に smoke を 1 コマンドで実行し、結果を読む
- smoke は冪等にする（連続実行してもエラーにならない）
- CLI / バッチ / ライブラリ project では、起動 smoke の代わりに代表入力での 1 回実行または公開関数呼び出しの確認を smoke とする

---

## 8. CI に組込むべき最小セット

完了条件を CI で自動化する：
¤ ../CLAUDE.md §5.5

```bash
clj -M:lint                                          # clj-kondo
clj -M:format check                                  # cljfmt
clj -M:poly check                                    # Polylith 構造
clj -M:poly test :all                                # 全テスト
cd projects/<deploy> && clj -T:build uber            # ビルド（<deploy> は DESIGN.md §8.4 で定めた project / deploy 名）
```

通過したら `stable-<timestamp>` タグを打つ（§6.2）。

CI 通過時以外にも、人間が `git tag stable-$(date +%Y%m%d-%H%M%S)` を手動で打つのは問題ない。ただし CI が通らない状態でタグを打つと、以降の `poly test` が誤った安定点を参照する。Polylith 公式では `poly create tag` のような専用コマンドは存在せず、安定タグの付与は git tag で行う（`workspace.edn` の `:tag-patterns {:stable "^stable-.*"}` にマッチするタグ名にする）。

---

## 9. 参照 repo の利用（任意）

別の Polylith repo の brick 設計を、実装時の**比較材料**として読んでよい。ただし他 repo はコード参考に限定し、`:local/root` 組込み・自動再利用・cross-workspace catalog は採らない（重すぎる）。

### 9.1 allowlist

参照してよいローカル repo は `.llm/reference-repos.edn` の `:allowed-repos` に人間が明示的に列挙する。このファイルが存在し path が列挙されていること自体が、「これらの repo を read-only で読んでよい」という人間の承認である。ファイルが無い、または `:allowed-repos` が空なら参照は許可されていない。

allowlist の追加・一覧・有効性検査には `.llm/scripts/reference-repos.sh` を使う。`reference-repos.sh add <path>` はパス実在・Polylith repo か・テンプレート由来かを検証してから追加し、`list` で登録内容、`check` で各エントリの有効性と brick 一覧を表示する。EDN を直接手編集してもよいが、出自条件を自動検証する `add` を推奨する。

参照先 repo は、その `.llm/repo-context.edn` の `:template-name` または `:derived-from` が `clojure-polylith-llm-starter` 系列である（= 同系テンプレート由来）場合のみ参照可とする。出自証明は repo 単位で足り、brick 単位のマーカーは設けない。

### 9.2 何を読んでよいか

allowlist された参照 repo では、次を比較材料として読んでよい。

- `brick.edn`（capability / 境界の切り方）
- `interface.clj`（公開 API の形）
- Malli 契約（`m/=>` の書き方、schema 設計）
- 近接 test（境界テストの観点）
- `deps.edn`（用途別機能カテゴリごとのライブラリ選定）

`reference-repos.sh check` は各参照先 repo の brick 一覧と、各 brick の `:brick/authors` / `:brick/license` を表示する。何を比較材料にでき、どの brick が著作権上 L0 判定を要するか（§9.4）を事前に把握できる。

### 9.3 セキュリティ境界（厳守）

- **read-only**: 参照 repo を編集しない
- **allowlist 限定**: `.llm/reference-repos.edn` に無い repo は読まない
- **classpath 非組込み**: `:local/root` や deps で依存化しない
- **コード非実行**: 参照 repo の build / test / script を実行しない
- **コピーしない**: 参照先のコードをコピーせず、現在 repo の DESIGN と trace に合わせて再実装する。参照 repo は比較材料であって import 元ではない

`.llm/data/brick-map.edn` の構造は、参照カタログの比較材料としても流用してよい。

### 9.4 参照 brick の流用と L0 判定

参照先 brick を比較材料に再実装する時、著作権・ライセンスの判断が必要になる。アイデアだけの流用でも、別作者の成果は法的リスクを持ちうる。判断材料は `brick.edn` の `:brick/authors`（作成者）と `:brick/license`（ライセンス、SPDX 識別子。欠落時は参照先 repo ルートの LICENSE 継承と解釈する）である。

**cross-repo 参照の L0 判定**:

1. 参照先 brick の `brick.edn :brick/authors` を読む。
2. 現作業者の git author（`git config user.name` / `user.email`）と照合する。
3. `:brick/authors` が現作業者と一致する単一作者なら、通常の再実装承認フロー（§8.1）で進めてよい。
4. `:brick/authors` に現作業者以外が含まれる、複数作者である、または欠落・TODO のままなら、**L0（人間専権）**。LLM は再実装へ進まず、流用範囲・ライセンス論点・著作権リスク・選択肢を整理して人間に提示する。アイデアのみの流用もこの判定の対象とする。

`reference-repos.sh check` は各参照先 brick の `:brick/authors` / `:brick/license` を表示するので、L0 判定の材料を事前に俯瞰できる。

**within-repo の宣言・証拠不一致**:

自 repo でも、`brick.edn` の宣言 `:brick/authors` と git 履歴から導出した実際の作者が食い違う場合がある。`check-workspace-integrity.sh`（`gen-brick-map` の check）はこの不一致を検出し、`:adoption-mode :complete` では ERROR、`:retrofit` / `:partial` では WARN とする。不一致は L0 reconciliation 対象であり、人間が宣言の修正か、作者が変わった正当性の判断を行う。git 履歴のない未コミット新規 brick は不一致扱いしない。

**元 repo の ID を持ち込まない**:

再実装は取り込み先の DESIGN / QUESTIONS / ADR に合わせる。参照先の `:trace/requirements` / `:trace/use-cases` / `:trace/test-obligations` メタデータ、`;; TODO(Q-...)` コメント、ADR 参照を持ち込まず、取り込み先の ID 体系へ再マッピングする。誤って持ち込んだ場合、`check-trace-metadata.sh`（未知 ID）・`gen-brick-map` の `:brick/requirements` 検査（DESIGN 不在 ID）・TODO 検査が事後の安全網として検出する。

---

## 10. 関連文書へのリンク

- **../CLAUDE.md §1**: Polylith を採用する原理的根拠（機械化 + 副作用隔離）
- **../CLAUDE.md §6.1**: poly コマンド早見表
- **../CLAUDE.md §8.2**: 新規コンポーネント追加の作業プロトコル（概要）
- **CODING_GUIDE.md §2.3**: 副作用の隔離（Polylith の構造原理と整合）
- **Polylith 公式**: https://polylith.gitbook.io/
