# CODING_GUIDE.md — Clojure コーディング規約

本文書は **`../CLAUDE.md` §1 の原則を Clojure コードとして具体化する**詳細資料である。
CLAUDE が「日々の作業フロー」を規定するのに対し、本文書は**設計判断・コーディング判断に迷った時**に使う。
∵ ../CLAUDE.md

- **../CLAUDE.md §1 の三基底原則**（全域性 / 不変性 / 副作用隔離）は本文書 §2 で Clojure として詳細展開
- **LLM が特有に犯す誤り**は §1 に先頭配置（毎コミット前の確認対象）
- 一般的な書き方規約は §3 以降

規約の各項目は「**Rule（結論）/ Why（理由）/ Do（良い例）/ Don't（悪い例）**」の 4 点セットで記述する。

**迷ったら 3 つ**: シンプルに、データで、純粋関数で。これが全体の通奏低音である。

---

## 1. LLM が陥りやすい落とし穴（**毎コミット前に確認**）

この章を最初に置くのは、**LLM（特に訓練データに Java/Python が大量に含まれるモデル）が Clojure で特有に起こす失敗パターン**が、一般の Clojure スタイルガイドにはない差分として最も重要だからである。

### 1.1 OOP 志向への引き戻し（§1.1 不変性・データ指向に反する）

**兆候**: 意味もなく `defrecord` を作る、`defprotocol` を「インターフェース定義」として使う、`defmulti` で疑似クラス階層を作る。

**対処**: §3.1 の 3 条件を満たさないなら **すべてマップと関数で書き直す**。

### 1.2 過剰な `defprotocol`

**兆候**: 1 実装しかないのに protocol を切る。

**対処**: 1 実装なら関数 1 本で十分。将来拡張の「抽象化」は YAGNI。

### 1.3 `for` と `doseq` の混同

**Rule**: `for` は**遅延シーケンスを返す**。副作用目的なら必ず `doseq`。

```clojure
;; ✅ Do: 副作用なら doseq
(doseq [x xs] (println x))   ; 本例の println は副作用の代表として示したもの。
                              ; アプリケーションコードでは §2.3.3 の通り println 禁止（mulog/log か tap> を使う）

;; ❌ Don't: 副作用なのに for（遅延されて実行されないことがある）
(for [x xs] (println x))
```

### 1.4 Java 的な try/catch の濫用（§1.1 全域性に反する）

**兆候**: 可能性のある失敗すべてに `try/catch` を巻く。

**対処**: 純粋関数は例外を投げないように書く。例外は I/O 境界に限定。業務失敗は戻り値で表現。

### 1.5 `atom` をローカル変数のように使う（§1.1 不変性に反する）

**兆候**: 関数内に `let [acc (atom [])]` を作り、`swap!` で蓄積する。

**対処**: ほぼ全て `reduce` か `into` で書ける。引数設計とは別問題なので、まず蓄積処理を値変換として書く。

### 1.6 `loop/recur` の過剰使用

**兆候**: `reduce` や `map` で書ける処理を `loop` で書く。

**対処**: まず高階関数で書けないか検討。3 引数以上の accumulator が必要なら `loop` を検討、それでも大抵 `reduce` の acc をマップにすれば済む。

### 1.7 深い `update-in` / `assoc-in`

**兆候**: 3 段以上のパスで更新している（`(update-in m [:a :b :c :d] ...)`）。

**対処**: データ構造が間違っている兆候（§3.3）。平坦化を検討。

### 1.8 `println` デバッグの残置

**対処**: `tap>` を使うか、`mulog/log` で構造化。そもそも REPL で評価すれば戻り値が見える。

### 1.9 `comment` フォームの中のコード忘れ（機械化不可能な努力目標）

**注記**: `(comment ...)` は Clojure の慣用として **REPL 駆動開発中の試行コード置き場**として広く使われ、意図的に「動かないコード」「仮のコード」を置くのが標準作法。`.clj-kondo/config.edn` の `:skip-comments true` はこの慣用を壊さないための適切な設定。本規約は**機械的に検知できない意図論の問題**で、努力目標として残す。

**兆候**: 実装を `(comment ...)` に書いたまま、本体に戻し忘れる。

**対処**: コミット前に `git diff` で `(comment` ブロックの増減を目視。明らかに本体に戻すべき実装コードを comment 内に残さない。試行・検証のコードと実装忘れの区別は人間の判断に委ねる（機械化しようとすると REPL 駆動の試行コードに false positive が出る）。

### 1.10 スレッディングの過剰な縦長化

**兆候**: `->>` が 10 段以上続き、中間の意図が追えない。

**対処**: 3〜5 段ごとに補助関数に切り出し、名前で意図を表現する。

### 1.11 Malli スキーマの付け忘れ（§1.1 全域性に反する）

**兆候**: 公開関数に `m/=>` がない。

**対処**: 公開 API を書いたら即座にスキーマを付ける。スキーマが書けないのは**入力が不明確**な兆候で、設計に戻る合図。

### 1.12 Destructuring の過剰化

**兆候**: 関数引数の destructuring が 5 階層以上ネストし、引数リストが 10 行になる。

**対処**: 関数を分解するか、引数マップを浅く保つ設計に戻る。

```clojure
;; ❌ Don't
(defn process [{:keys [user config]
                :as   ctx
                {{:keys [timeout retries]} :http
                 :keys [db-url]} :config}] ...)

;; ✅ Do: 必要な部分だけ取り出して補助関数に渡す
(defn process [ctx]
  (let [http-opts (-> ctx :config :http)
        db-url    (-> ctx :config :db-url)]
    ...))
```

### 1.13 ライブラリの追加を提案しがち（§1 機械化に反する）

**兆候**: 標準で解けることに外部ライブラリを提案する。

**対処**: `clojure.string`, `clojure.set`, `clojure.walk`, `clojure.data` で足りないか先に確認。依存追加は承認必須。
¤ ../CLAUDE.md §2

### 1.14 ドキュメントの引用過多

**兆候**: 関数 docstring に長文で一般的な説明（「この関数は…」）を書く。

**対処**: docstring は 1〜3 行。「何を返すか」と「重要な副作用・前提」のみ。

---

## 2. 三つの基底原則の詳細展開（**../CLAUDE.md §1.1 の具体化**）

../CLAUDE.md §1.1 で示した三基底原則を、Clojure コードとして深く展開する。
本文書の §3 以降の個別規約は、この三原則からの派生である。

### 2.1 全域性（Totality）の Clojure 実装

**原則**: 関数の成功・失敗を契約に持ち上げ、呼び出し側に処理を強制する。`nil` で沈黙したり、例外で逃げたりしない。

#### 2.1.1 Malli 契約の徹底

- 公開関数（`interface.clj` の `defn`）には **`m/=>` を必ず付ける**（`defn-` は免除、`core.clj` は内部実装のため任意）
- 契約は境界（`interface.clj`）に集約する（§1.1.1 全域性「境界契約は境界で宣言」）。`core.clj` には置かない。これにより REPL / テスト / 他 brick からの呼び出しは interface 経由で instrumentation が効き、`check-interface-contracts.sh` が interface.clj 内の `(m/=>)` 存在を機械検証する
- スキーマは関数定義の直後に書く（関数と契約を一体で読めるように）

```clojure
;; interface.clj（境界）
(ns myorg.myapp.user.interface
  (:require [malli.core :as m]
            [myorg.myapp.user.core :as core]))

(m/=> find-user [:=> [:cat :any :uuid] [:maybe core/User]])
(defn find-user [db id] (core/find-user db id))
```

#### 2.1.2 境界での `m/validate`

- HTTP リクエスト、DB 行、外部 API レスポンス、設定ファイルの読込は**入口で明示的に検証**
- Reitit の `:parameters` / `:responses` に Malli スキーマを書けば、リクエスト/レスポンス検証が自動化される（これは §1.2.1 機械化）

#### 2.1.3 失敗の表現方法

関数が失敗し得る場合、以下のいずれか一つに決める：

- **`nil` を返す**（検索系: `find-user`、`get-config` など）
- **例外を投げる**（境界条件違反・システム異常のみ）
- **タグ付きマップを返す**（`{:ok user}` / `{:error reason}` のような 2 値）

**Rule**: 同一関数で `nil` と例外を使い分けない。**常に同じ型を返す**。

#### 2.1.4 instrumentation の活用

- `dev/user.clj` で `(malli-on!)` ヘルパが提供される。起動方法はプロジェクト構成で 2 分岐:
  - **Integrant を採用し、ライフサイクル管理 helper を有効化済み**: `(go)` が内部で `(malli-on!)` を呼ぶ
  - **Integrant 非採用**（ライブラリ配布・単発 CLI 等）: REPL 起動後に明示的に `(malli-on!)` を呼ぶ
- 契約違反は REPL 呼び出し即時に例外。LLM は例外メッセージから自己修正できる
- 詳細は CLAUDE.md §5.4 / §9.1 と `development/src/dev/user.clj` の docstring

### 2.2 不変性（Immutability）の Clojure 実装

**原則**: 変更可能な状態を最小化する。Clojure は言語がこれを支えるので、**意識的に捨てない限り不変性は保たれる**。

#### 2.2.1 データ指向プログラミング

- **素のマップ・ベクタ・セット・キーワードを第一選択**
- `defrecord`/`deftype` は §3.1 の 3 条件のみ

#### 2.2.2 持続データ構造の活用

- `assoc` / `update` / `conj` は非破壊。元の値は変わらない
- 大規模更新時は `into` や `reduce` を使う（`transient` はホットパスのみ §9.4）

#### 2.2.3 名前空間付きキーワード

- `:user/id` のようにドメイン名で修飾。マップ合流時の衝突を防ぎ、Malli レジストリと整合

#### 2.2.4 状態は最外層に集約

- **関数内で `atom` を作らない**。蓄積は `reduce`（§1.5 参照）
- トップレベル状態は、ライフサイクル管理下のコンポーネントに格納（ライフサイクル管理に Integrant を採用する場合は Integrant コンポーネント、§2.3 参照）

### 2.3 副作用の隔離（Effect Isolation）の Clojure 実装

**原則**: 副作用（I/O、可変状態、時刻取得、乱数）を最外層に集約し、内側は純粋に保つ。"Functional Core, Imperative Shell"。

#### 2.3.1 依存方向の単方向化

```
[ base: api ]                 ← HTTP entry、ライフサイクル配線
      ↓ uses
[ domain components ]         ← 純粋ドメイン（I/O require 禁止）
      ↓ uses
[ I/O components ]            ← DB, HTTP client, logger（ライフサイクル管理下）
```

- ドメイン系コンポーネント（user, order, …）は `next.jdbc`, `hato`, `ring` 等を **require しない**
- ドメイン関数が副作用を必要とするなら、**副作用関数を引数として受け取る**

#### 2.3.2 ライフサイクル管理によるコンポーネント化（Integrant 採用時）

ライフサイクル管理に Integrant を採用する場合、副作用の隔離は Integrant で実現する。採否は実際のライフサイクル管理要件で決める：

- 副作用を持つオブジェクト（DB 接続プール、HTTP サーバ、ロガー）はすべて Integrant key として定義
- `init-key` で起動、`halt-key!` で停止。ライフサイクル管理を統一
- `integrant.repl/reset` で開発時の再起動を機械化（§1.2.2 ループ短縮）

**ライブラリ配布時**: Integrant は含めない（ライブラリがライフサイクル管理を強制しない作法）。ライフサイクルはライブラリ利用者に委ねる。

**Integrant を採用しない場合**: ライフサイクル管理が必要なら、`-main` 内での直列起動・`with-open` 等で明示的にリソース管理する。

技術選定の詳細は別紙に置く。
∵ STACK_GUIDE.md §3.1

#### 2.3.3 副作用の明示化

- `println` / `prn` はアプリケーションコード（components / bases）で禁止。代わりに `mulog/log` で構造化ログ、または `tap>` でデータ確認。**例外**: ビルドスクリプト（`projects/<deploy>/build.clj`）や `development/src/` 配下の一時デバッグコードでは許容（mulog 依存を引き込む疲労を避けるため）
∵ ../CLAUDE.md §4.3
- `with-redefs` は§1.1 全域性を破るので原則禁止。`clj-kondo` の `:discouraged-var` で警告化済。例外使用時は **ADR で理由付け必須**とする（「なぜ依存注入で置き換えられなかったか」を記録、依存注入が技術的に不可能な Java interop の境界など）

#### 2.3.4 モックは設計失敗のサイン

- テストで `with-redefs` を使いたくなったら、**依存を引数で受け取る形に設計変更**
- これにより §1.1 全域性（テストが契約を実際に検証する）も保たれる

### 2.4 整理優先の姿勢（開発・保守共通）

> 「新規追加より整理を優先する」という姿勢は、**文書とコードの両面で有効**であり、本テンプレートの品質維持の両輪をなす。
> 文書側の具体化は `MAINTAINERS_GUIDE.md` §9.3 を参照。本節はコード側を扱う。

#### 2.4.1 基本姿勢

コードに何かを**加える前に、既存コードを点検する**。以下の順序で判断：

1. **既存のコード・関数・Malli スキーマで実現できないか** → できるなら追加しない（YAGNI）
2. **既存のコードを軽微に整理すれば実現できないか** → そちらを優先
3. **新しい抽象・ライブラリ・コンポーネントが本当に必要か** → 必要性を Q で議論
4. **上記を経て必要と判断されたものだけを追加する**

この姿勢は§1.2.3 小単位分解、§1.2.4 早期破棄と同系統の規律であり、**コードベースの複雑さを累積させない**ためのもの。

#### 2.4.2 具体場面

**場面 A: 新しい関数を書きたい時**

- 類似の既存関数が `components/*/interface.clj` にないか確認
- 既存関数の引数を 1 つ追加するだけで済まないか検討
- 本当に別関数が必要なら追加（DRY 違反を避ける）

**場面 B: 新しい Malli スキーマを書きたい時**

- 既存スキーマで `[:and ExistingSchema ...]` で表現できないか
- 既存スキーマの拡張（`[:merge ExistingMap ExtraMap]`）で済まないか
- 新規スキーマは本当に別の意味的存在か（単なる文脈違いなら既存を流用）

**場面 C: 新しいコンポーネントを切りたい時**

- 既存コンポーネントの `core.clj` を分割するだけで責務分離できないか
- 既存 `interface.clj` に関数を追加するだけで済まないか
- 新規コンポーネントは `../CLAUDE.md` §2 禁止事項の範疇ではないが、Q を立てて議論する（`QUESTIONS.md` §1.1）

**場面 D: 既存コードに類似コードを書き足しそうな時**

- **DRY 違反の予兆**。類似 3 箇所で明らかに同型なら共通化を検討
- ただし**過度な抽象化は避ける**（KISS）。類似が 2 箇所なら保留、3 箇所以上で抽象化判断

#### 2.4.3 整理の具体手法

既存コードを整理する時の代表的な手法：

| 手法 | 適用場面 | 注意点 |
|---|---|---|
| **関数分割** | 1 関数が 20 行を超える、または責務が複数 | `interface.clj` の公開 API を壊さない |
| **共通化** | 同型コードが 3 箇所以上 | 無理な共通化は後で剥がすコストが大きい。確信できる時だけ |
| **スキーマの整理** | Malli スキーマが重複・類似 | `m/schema` の `:registry` で名前付き登録して参照 |
| **component 内の namespace 分割** | `core.clj` が肥大化 | `interface.clj` からの委譲関係を維持 |
| **不要コードの削除** | 参照されていない関数・スキーマ | `clj-kondo --lint` の unused 警告を活用 |

#### 2.4.4 コード整理を行うタイミング

- **新機能追加の着手前**: §2.4.1 の判断フローを必ず適用
- **バグ修正の前**: 該当箇所を直す前に、周辺に整理できる余地がないか点検
- **新規 brick 作成前**: 既存 brick で対応できないか必ず検討（`POLYLITH_GUIDE.md` §5）
- **コミット前レビュー**: 今回の変更で冗長が生じていないか最終確認

#### 2.4.5 整理を避けるべき場面

整理優先は原則だが、**例外もある**：

- **仕様が流動的で、将来の変化が読めない時**: 早期共通化は将来の足かせになる。重複を許容し、収束を待つ
- **原則 5（LLM は削除が苦手）に抵触する大規模整理**: 誤削除のリスクが高い。Q を立てて人間の承認を得る
- **関係者間の合意が未確立の整理**: 共通化は設計判断を含む。独断で進めず Q または ADR で議論

**整理が整理のための整理にならないよう**、常に「この整理は将来のどの疲労を防ぐか」を自問する。

---

## 3. データ設計

CLAUDE.md §1.1.2 不変性・§1.1.1 全域性の具体化。

### 3.1 素のマップが第一選択

**Rule**: データ表現はマップ・ベクタ・セット・キーワードで。`defrecord`/`deftype` は以下の 3 条件のいずれかを満たす時のみ。

1. プロトコルディスパッチによる多態性が必要
2. ホットパスでフィールドアクセスが性能クリティカル
3. Java 相互運用で具象クラスが必要

```clojure
;; ✅ Do
(def user {:user/id #uuid "..." :user/name "Alice"})

;; ❌ Don't
(defrecord User [id name])
```

### 3.2 キーは名前空間付きキーワード

**Rule**: `:id` ではなく `:user/id`。修飾子はドメイン名またはコンポーネント名。

```clojure
;; ✅ Do
{:user/id u :user/name n :order/id o}

;; ❌ Don't
{:id u :name n :order-id o}
```

### 3.3 ネストより平坦

**Rule**: 深いネストを避け、必要ならキーを結合した平坦な表現。`update-in` 3 段以上は設計の臭い。

```clojure
;; ✅ Do
{:user/id id :user/name name :user/address-street "..." :user/address-city "..."}

;; ❌ Don't
{:user {:profile {:address {:street "..." :city "..."}}}}
```

---

## 4. 関数設計

CLAUDE.md §1.1.1 全域性・§1.1.3 副作用隔離の具体化。

### 4.1 純粋関数を最大化

**Rule**: 副作用（I/O、state 変更、時刻取得、乱数）を含む関数は最外層に集約、内側は純粋。

```clojure
;; ✅ Do
(defn calculate-total [items tax-rate]
  (* (reduce + (map :price items)) (inc tax-rate)))

;; ❌ Don't
(defn calculate-total [items tax-rate]
  (let [total (* (reduce + (map :price items)) (inc tax-rate))]
    (log/info "total:" total)
    (db/save-total! total)
    total))
```

### 4.2 引数設計：マップ vs 位置

**Rule**: 2 引数以内で順序が自明なら位置引数。3 引数以上、またはオプション性があるならマップ引数。

```clojure
;; ✅ Do
(defn divide [numerator denominator] ...)
(defn connect [{:keys [host port user password ssl?]}] ...)

;; ❌ Don't
(defn connect [host port user password ssl?] ...)
```

### 4.3 戻り値は一貫させる

**Rule**: 関数の戻り値の型を一貫させる（§2.1.3）。nil か例外かマップか、いずれかに決める。

### 4.4 関数は小さく

**Rule**: 1 関数は概ね 20 行以内。50 行を超えたら分解。

**Why**: REPL でインクリメンタルに試せなくなる。§1.2 ループ短縮を損なう。

---

## 5. 制御フロー

### 5.1 分岐の使い分け

| 分岐数 | 形式 | 使い所 |
|---|---|---|
| 1 分岐（真のみ） | `when` / `when-not` | 副作用 or 条件付き値 |
| 2 分岐（値） | `if` | 二値の選択 |
| 2 分岐（束縛 + 分岐） | `if-let` / `when-let` | nil チェックと分岐を同時に |
| 多分岐（ガード） | `cond` | 条件が異なる節の列挙 |
| 多分岐（値の等価） | `case` | 定数との比較（高速） |
| 多分岐（述語 + 値） | `condp` | `contains?` などの述語適用 |

```clojure
;; ✅ Do
(cond
  (empty? xs)      :empty
  (= 1 (count xs)) :single
  :else            :many)

;; ❌ Don't
(if (empty? xs) :empty (if (= 1 (count xs)) :single :many))
```

### 5.2 スレッディングマクロ

**Rule**: データ変換は `->>`、オブジェクト的な逐次更新は `->`、nil 短絡は `some->`、条件適用は `cond->`。

ただし 1 本の threading が 10 段超えたら設計を疑う（§1.10）。

### 5.3 loop/recur は最終手段

**Rule**: `reduce`、`iterate`、`map`、`for` で書けるなら `loop` を使わない（§1.6）。

---

## 6. 名前付け

| 対象 | 規則 | 例 |
|---|---|---|
| 関数（動詞） | kebab-case | `create-user`, `find-by-id` |
| 述語 | 末尾 `?` | `valid?`, `empty?` |
| 副作用を伴う関数 | 末尾 `!` | `reset!`, `save!` |
| 動的 var | earmuffs `*...*` | `*db*`, `*current-user*` |
| 定数（ns レベル） | 普通の kebab-case | `default-timeout` |
| プライベート | `defn-` または `^:private` | — |
| 名前空間キーワード | 修飾子はドメイン名 | `:user/id`, `:order/status` |

**Rule**: ローカル束縛も意味のある名前で（`x`, `tmp`, `data` は避ける）。

---

## 7. エラーハンドリング

§1.1 全域性の具体化。

### 7.1 例外は `ex-info` で構造化

**Rule**: 例外は必ず `(ex-info "message" {:data ...})`。生の `Exception.` や文字列のみは禁止。

```clojure
;; ✅ Do
(throw (ex-info "User not found" {:type ::not-found :user-id id}))

;; ❌ Don't
(throw (Exception. (str "not found: " id)))
```

### 7.2 catch 節は限定的に

**Rule**: `(catch Exception e ...)` で握り潰さない。具体的な例外型か `ex-data` の `:type` で分岐。

### 7.3 回復可能な失敗は値で

**Rule**: 業務ロジックでの「失敗」は例外ではなく戻り値（§2.1.3）。

---

## 8. 状態管理

§1.1 不変性の具体化。

### 8.1 状態の種類と選択

| 種別 | 用途 | 使用頻度 |
|---|---|---|
| `atom` | 独立した単一の可変セル | ★★★ |
| `ref` | 複数セルの協調トランザクション（STM） | ★ |
| `agent` | 非同期・順序付き更新 | ★ |
| 動的 var | リクエストスコープ等の文脈 | ★ |

**Rule**: 迷ったら `atom`。

### 8.2 状態は最外層に集約

**Rule**: 関数内で `atom` を作るな。蓄積は `reduce`。トップレベル状態はライフサイクル管理下のコンポーネントに（Integrant 採用時は Integrant key）。

```clojure
;; ✅ Do
(reduce (fn [acc x] (conj acc (transform x))) [] items)

;; ❌ Don't
(let [result (atom [])]
  (doseq [x items] (swap! result conj (transform x)))
  @result)
```

---

## 9. パフォーマンス

### 9.1 測ってから最適化

**Rule**: `criterium` で計測する前に最適化しない。

### 9.2 reflection を排除

**Rule**: `(set! *warn-on-reflection* true)` を dev で ON。警告が出たら型ヒント。

### 9.3 中間 seq を潰す時は transducers

**Rule**: `(->> coll (map f) (filter g) (map h))` の連結で性能問題なら transducers に。

```clojure
(into [] (comp (map f) (filter g) (map h)) coll)
```

### 9.4 transient はホットパスのみ

**Rule**: `transient`/`persistent!` は数千要素以上のタイトループのみ。

### 9.5 lazy seq の head holding に注意

**Rule**: 巨大な lazy seq をトップレベル var に束縛しない。head が保持されシーケンス全体がメモリに残る。

---

## 10. 並行処理

### 10.1 core.async は過剰使用を避ける

**Rule**: `future`、`pmap`、`CompletableFuture` で足りるなら `core.async` を使わない。

### 10.2 go-block でブロッキング I/O を避ける

**Rule**: `go` ブロック内で HTTP 呼び出しや DB アクセスをしない。必要なら `thread` または専用プール。

### 10.3 pmap はワークサイズが大きい時だけ

**Rule**: `pmap` は各要素の処理が十分重い場合（最低数 ms）のみ。

---

## 11. マクロ

### 11.1 まず関数で足りないか疑う

**Rule**: マクロを書く前に、関数 + 高階関数で書けないか考える。

### 11.2 マクロを書く 3 条件

1. 評価タイミングの制御
2. 新しい構文形式の導入（DSL）
3. ボイラープレートの除去（関数 + データで書けないか先に検討）

### 11.3 マクロは実装を関数に委譲

```clojure
(defn- my-impl [form] ...)
(defmacro my-macro [& body] (my-impl body))
```

---

## 12. REPL 駆動開発

§1.2.2 ループ短縮の具体化。

### 12.1 Rich Comment

**Rule**: 各 ns 末尾に `(comment ...)` で REPL 試行を残す。

```clojure
(comment
  (require '[myapp.user :as u] :reload)
  (u/create {:name "Alice" :email "a@example.com"}))
```

### 12.2 tap> でデータを見る

**Rule**: デバッグ時は `println` ではなく `tap>`（§1.8）。

### 12.3 REPL 確認はテストに昇格

**Rule**: `comment` で確認した不変条件は、即座に `deftest` または `defspec` に移す。

---

## 13. テスト

§1.1 全域性の動的検証。

### 13.1 純粋関数は簡単、副作用関数は高コスト

**Rule**: テストが書きにくいと感じたら、**関数を分解して純粋部分を切り出す**。テストではなく設計を直す。

### 13.2 モックは設計失敗のサイン（§2.3.4 再掲）

**Rule**: `with-redefs` を使いたくなったら、**依存を引数で受け取る設計**に変える。

### 13.3 プロパティテストで不変条件

**Rule**: 純粋関数には `test.check` でプロパティを書く。Malli スキーマがあれば `malli.generator` でほぼ無料。

### 13.4 matcher-combinators で部分一致

**Rule**: 完全一致不要な場面では `match?` を使う。

---

## 14. 名前空間

### 14.1 1 ファイル 1 名前空間

### 14.2 require は `:as` 優先

**Rule**: `:refer` は `clojure.test` 等の例外のみ。通常は `:as`。

### 14.3 循環依存は発見次第直す

**Rule**: 共通部分を新規 ns に抽出。

### 14.4 ns 宣言は整理

**Rule**: `:require` 内はアルファベット順にソート。

---

## 15. Docstring とコメント

### 15.1 docstring の書き方

**Rule**: `defn` の docstring は「何を返すか」を主語省略の終止形で。1 行目は短く、詳細は空行後。

### 15.2 仮引数名で意味を表現、docstring で引数説明をしない

### 15.3 インラインコメントは意図のみ

**Rule**: コードが「何をするか」ではなく「なぜそうするか」をコメント。

---

## 付録: コミット前チェックリスト

§1.2.1 機械化で自動検出されるもの（clj-kondo、cljfmt、Polylith の `poly check`、Malli instrumentation）に加えて、LLM が手動で確認する項目：

- [ ] 公開関数すべてに Malli `m/=>` 契約あり（§2.1.1）
- [ ] 副作用が最外層に隔離されている（§2.3）
- [ ] `defrecord` を使っている場合、§3.1 の 3 条件のいずれかを満たす
- [ ] `loop/recur` を使っている場合、`reduce` で書けないことを確認した
- [ ] `with-redefs` を使っている場合、依存注入で回避できないことを確認した
- [ ] `core.async` を使っている場合、`future`/`pmap` で足りないことを確認した
- [ ] スレッディングが 10 段未満
- [ ] 関数が 50 行未満
- [ ] `update-in` のパスが 3 段未満
- [ ] `println` / `prn` が残っていない
- [ ] `comment` フォームに実装コードが残っていない
- [ ] ns の `:require` がソート済み
- [ ] `clj -M:lint` / `clj -M:poly check` がゼロ警告
- [ ] **§1 のどの落とし穴にも該当しない**
