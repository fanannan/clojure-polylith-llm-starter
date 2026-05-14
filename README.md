# Clojure Polylith LLM Starter

**Clojure + Polylith に特化した、LLM 協働型の仕様駆動開発テンプレート。**

このテンプレートは、LLM に大量のコードを書かせるための雛形ではありません。LLM が間違えたときに、人間が監視役として消耗せず、仕様・境界・契約・REPL・自動検査で早く修復できるようにするための開発基盤です。

人間は最初から完全な仕様を書く必要はありません。未整理の着想を IDEA に書くと、LLM が DESIGN への反映案、質問、受入基準、test obligation、Polylith 構造候補へ分解します。DESIGN は仕様正本として扱われ、design-ir、brick map、workspace map、trace-index、Malli 契約、自動テスト、README 生成へ接続されます。

> ⚠️ **このファイルはテンプレート配布時の入口です。**
>
> 派生プロジェクトではこの README を編集し続けず、初期化完了時に **プロダクト README として完全置換**する。
> プロダクト README は DESIGN / design-ir / 技術選定 / 起動手順から LLM が半自動生成し、人間が外向け説明としてレビューする。
> 完全置換後にテンプレートの由来や使い方を読み返す場合は、guide 側の参照入口を使う。
¤ .llm/guide/BOOTSTRAP_GUIDE.md §4
¤ .llm/templates/PROJECT_README.md
¤ .llm/guide/TEMPLATE_USAGE_GUIDE.md

## 何ができるか

このテンプレートは、Clojure + Polylith プロジェクトの立ち上げと継続開発に、次の流れを用意します。

```text
IDEA
  ↓ LLM が整理・質問・反映案を作る
DESIGN
  ↓ requirement / use case / acceptance criteria / constraint を抽出
design-ir.edn
  ↓ brick-map / workspace-map / libs.edn と照合
Polylith 構造・Malli 契約・trace metadata・REPL 検証・自動テスト
  ↓
継続的な drift 検出と README 半自動生成
```

主な機能:

- **IDEA から始められる**: 自由記載の着想メモを LLM が DESIGN 反映案、矛盾、質問候補へ分解する
- **DESIGN を仕様正本にする**: 実装判断、IR 生成、capability plan、受入基準、テスト義務の起点を 1 箇所に集約する
- **design-ir で仕様を機械可読化する**: requirement、use case、constraint、test obligation を EDN として抽出し、実装側の分析情報と照合する
- **Polylith 境界へ落とす**: LLM が触る範囲を brick 単位に局所化し、interface と Malli 契約で境界を明示する
- **仕様とコード・テストを trace する**: public boundary の `:trace/requirements` / `:trace/use-cases` と `deftest` の `:trace/test-obligations` を design-ir と照合し、仕様 drift を早く検出する。`trace-impact.sh` で「この要件に関係するコードとテスト」「この公開関数が満たす仕様」「今回の変更で影響する要件」を確認できる
- **REPL を主作業台にする**: 永続 nREPL と Malli instrumentation により、編集から検証までを短いループで閉じる
- **多層の機械検査で止める**: clj-kondo、polyguard hook、Splint、Polylith、Malli、`.llm/scripts/`、clj-watson で advisory ではなく fail させる
- **記憶を混ぜない**: DESIGN、KNOWLEDGE、ADR、QUESTIONS を分離し、現在形仕様・現時点知識・不変決定履歴・判断保留を別々に扱う
- **承認権限を分ける**: L0 人間専権、L1 承認必須、L2 実施後報告、L3 独断可を明示し、人間の判断を不可逆部分に集中させる
- **派生プロジェクト README を生成する**: 初期化完了時に、テンプレート README をプロダクト README として半自動生成・完全置換する

## 背景にある哲学

このテンプレートの最上位目標は、LLM 時代の開発で人間が消耗しないことです。

LLM は大量のコードを生成できますが、人間がその出力を常時監視し、微妙な誤りを見つけ続ける構造は長続きしません。問題はコードを書く速さではなく、生成されたコードを信頼できるか、誤りを早く局所化できるか、手戻りを小さくできるかです。

そのため、このテンプレートは次の方針を取ります。

- 機械が検査できるものは機械に検査させる
- 人間はプロダクト判断、仕様判断、不可逆な設計判断に集中する
- LLM が触る範囲を Polylith brick 単位に小さく保つ
- Malli 契約と REPL 検証で、動的言語でも短いフィードバックループを作る
- 仕様、知識、決定履歴、判断保留を混ぜない
- 仕様と実装の対応を EDN 生成物で追跡する
- 迷走した LLM を人間が後から救うのではなく、早めに止める

Clojure の不変データ、REPL 駆動、データ指向設計、Polylith の明示的な境界は、この目的と相性が良い。Rust のように静的型で閉じる方向ではなく、Clojure では Malli 契約、REPL、Polylith、lint、生成 EDN を組み合わせて、実用上の検査可能性と局所推論性を作ります。

## 一般的な仕様駆動開発との違い

一般的な SDD ツールは、仕様を first-class artifact として扱い、要件 → 設計 → タスク → 実装の流れを整えます。本テンプレートもその思想を共有しますが、**異なる前提**から**異なる構造**で同じ目的に到達しています。

一般的な SDD ツールの多くは、「LLM やステークホルダーが repo 内文書を読まないチームでも仕様駆動を維持する」ことを目的とし、dashboard・外部 issue tracker 同期・tasks ファイル等の**外部表示による補償機構**を備えます。

本テンプレートは前提を逆に取ります。「**LLM が毎セッション必ず repo 内文書を読む**」ことを `session-briefing.sh` と `.llm/data/*.edn`、および毎セッション必読規約で機械化し、補償機構そのものを不要にします。
∵ CLAUDE.md

### 一般的な補償機構と本テンプレの対応

| 一般的な SDD の機能 | 想定されている弱点 | 本テンプレの構造的封じ込め |
|---|---|---|
| 進捗 dashboard / status 表示 | 人間ステークホルダーが repo を読まない | 毎セッション必読 + L0-L3 権限階層で LLM の自律度を判定 |
| 作業計画ファイルと並列マーカー | 作業計画が会話で消える | brick = 再利用単位 + `poly check` + `:brick/requirements` で REQ-ID 紐付け |
| 外部 issue tracker との双方向同期 | repo 内 markdown を読まない文化 | `QUESTIONS.md` / `ADR` を repo 内 markdown として保持、git 履歴と一体管理 |
| proposal → apply → archive の状態機械 | 仕様変更の所在が分散 | `QUESTIONS.md` の状態遷移 + `:adoption-mode :retrofit/:partial/:complete` |
| エージェント規約ファイルの拡張 | エージェントごとの規約分散 | `CLAUDE.md` に一極集中、`AGENTS.md` は 1 行リダイレクタ |
| マルチエージェント orchestration | 単一 LLM の context 限界 | 自己停止プロトコル + subagent 分離 + REPL primary workbench |

一般的な機能が「無い」のではなく、本テンプレでは**別の構造でそもそも問題が発生しない**設計になっています。

### 「読む」前提と「読まない」前提の分岐

一般的な SDD と本テンプレの最も深い違いは、LLM とステークホルダーが repo 内文書を**読むか読まないか**の前提にあります。

| | 一般的な SDD の暗黙前提 | 本テンプレの明示前提 |
|---|---|---|
| 想定チーム規模 | 大規模 / 多役割 | 単独〜小規模 |
| 一次作業面 | IDE / 外部 issue tracker / dashboard | repo 内 markdown と REPL |
| LLM の repo 読み込み | 部分的・任意 | 毎セッション必須・機械強制 |
| 仕様変更の伝達 | 外部通知・同期 | git 履歴と repo 内文書で完結 |

この分岐により、本テンプレは「読む」前提でしか成立しない強み (4 種文書純度 / IR 機械照合 / 多層機械検査 / REPL primary workbench) を獲得する代わりに、「読まないチーム」へのスケールを意図的に放棄しています。これは**トレードオフではなく設計選択**です。

### 重点の違い

| 観点 | 一般的な SDD | 本テンプレート |
|---|---|---|
| 対象 | 多言語・汎用 | Clojure + Polylith に特化 |
| 仕様の役割 | 実装前の合意文書 | 実装判断・IR 生成・テスト義務・構造検査の起点 |
| LLM との関係 | 仕様を LLM に渡して実装させる | LLM が IDEA を DESIGN へ構造化し、検査結果で自己修正する |
| 強制力 | 文書・タスク管理・レビュー中心 | clj-kondo / Splint / Polylith / Malli / scripts / clj-watson で fail させる |
| モジュール境界 | 設計規約に依存しがち | Polylith brick と interface で物理的に区切る |
| 仕様と実装の照合 | ツール依存、または手動 | design-ir、brick/workspace map、Clojure trace metadata で照合 |
| 仕様とテストの照合 | 受入基準とテストの対応をレビューで追う | test obligation と `deftest` metadata を照合 |
| 知識管理 | spec / plan / tasks に集約しがち | DESIGN / KNOWLEDGE / ADR / QUESTIONS を分離 |
| 開発ループ | テスト・CI 中心 | REPL primary workbench + Malli instrumentation + CI |
| 人間の役割 | 仕様作成者・レビュー者 | L0 判断と承認に集中し、検査は機械へ寄せる |

つまり、本テンプレートは「仕様をよく書くための道具」ではなく、「仕様から実装・検査・記憶・承認までをつなぎ、人間の注意力に依存する箇所を減らすための Clojure Polylith 向け実装」です。

## 向いているプロジェクト

- 新規 Clojure プロジェクト
- Polylith による明示的な境界設計を採用したいプロジェクト
- 単独開発者または小規模チーム
- LLM と長期的に共同開発する前提のプロジェクト
- 要件変更があり、仕様・知識・判断履歴を保守し続けたいプロジェクト
- REPL 駆動、Malli 契約、データ指向設計を積極的に使うプロジェクト

向いていないもの:

- 多言語汎用の SDD テンプレートを探している場合
- サンプルアプリや全部入り Web フレームワークを期待している場合
- Polylith を採用しない Clojure プロジェクト
- 大規模チームで dashboard / 外部 issue tracker が一次作業面である場合
- 既存の独自規約を温存したまま運用する必要がある場合

不適な条件下では、一般的な SDD ツールのほうが適合します。これは本テンプレートの欠陥ではなく、**設計選択の境界線**です。同じ目的 (LLM 協働の修復コスト最小化) に対して、前提条件が異なれば最適解も異なります。

## 使い始め方

| あなたの状況 | 最初に読む文書 | この README の役割 |
|---|---|---|
| **初めてテンプレートを開いた人** | 本ファイル | 入口と索引 |
| **これから初期化する人** | 本ファイル → `BOOTSTRAP_GUIDE.md` | キックオフとゲート確認 |
| **既存 Clojure / Polylith repo に導入する人** | 本ファイル → `BOOTSTRAP_GUIDE.md` §4.1 | retrofit 手順の入口 |
| **日常開発に入った LLM** | `CLAUDE.md` | 初期化後は索引だけ残る |
| **まだ仕様を書けない人** | `IDEA.md` | 自由な着想メモ。雛形だけなら無視される |
| **仕様を埋める人** | `DESIGN.md` | どこを埋めるかの導線 |
| **技術選定で迷う人** | `STACK_GUIDE.md` | 参照先の案内 |
| **派生後にテンプレートの由来を読み返す人** | `TEMPLATE_USAGE_GUIDE.md` | プロダクト README 置換後のテンプレート参照入口 |

**優先順位**:
- 初期化フローの入口は **README**
- 日常作業の正本は別紙
¤ CLAUDE.md
- 権限と承認の正本は別紙
¤ .llm/guide/COLLABORATION_GUIDE.md
- 初期化の詳細手順の正本は別紙
¤ .llm/guide/BOOTSTRAP_GUIDE.md

初期化完了後は、本ファイルを運用ルールの正本として使わない。派生プロジェクト用 README を半自動生成して完全置換する。

---

**必須技術基盤**: Clojure + tools.deps + Polylith + Malli + clj-kondo + cljfmt + Splint + clj-watson + `.llm/scripts/`。

HTTP、永続化、ライフサイクル管理などの追加技術は、必要になった用途別機能カテゴリごとに選び、brick の `deps.edn` に追加する。推奨ライブラリは判断済み推奨集として guide 側に置く。
∵ CLAUDE.md
∵ .llm/guide/STACK_GUIDE.md

## 詳細索引

| 目的 | 読む文書 | どこまで読めばよいか |
|---|---|---|
| 初期化を始める | `README.md` | 本ファイルの「開始手順」まで |
| 初期化の詳細手順を実行する | `.llm/guide/BOOTSTRAP_GUIDE.md` | ゲートと対象節だけ |
| 既存 repo に後から導入する | `README.md` → `.llm/guide/BOOTSTRAP_GUIDE.md` §4.1 | 本ファイルの「既存 repo への導入」まで |
| 日常開発を進める | `CLAUDE.md` | 毎セッション最初から |
| 着想から仕様を起こす | `IDEA.md` → `DESIGN.md` | IDEA は自由記載、DESIGN は仕様正本 |
| 仕様を埋める・直す | `DESIGN.md` | §0 と該当節 |
| IDEA から仕様へ翻案する | `.llm/guide/SPEC_GUIDE.md` | reconciliation と test obligation の節 |
| 技術選定を決める | `.llm/guide/STACK_GUIDE.md` | 冒頭の位置づけ + 該当機能節 |
| Polylith 構造を決める | `.llm/guide/POLYLITH_GUIDE.md` | 冒頭の前提 + 該当手順節 |
| 権限や承認で迷う | `.llm/guide/COLLABORATION_GUIDE.md` | §2 を正本として読む |
| 何を記録するか迷う | `.llm/memory/QUESTIONS.md` / `.llm/memory/KNOWLEDGE.md` / `.llm/memory/adr/README.md` | 各文書冒頭の更新トリガー表 |

## 前提ツール

- **JVM LTS**
- **Clojure CLI**（`clj` コマンド、tools.deps ベース）
- **Git**
- **LLM コーディングエージェント**（Claude Code 等、`CLAUDE.md` を読める LLM）

---

## 開始手順（LLM 駆動初期化）

**最小着手条件**: `DESIGN.md` の §1/§2/§3/§4/§8 の骨格と `workspace.edn` の `:top-namespace` が確定すれば、初期化を次へ進めてよい。

### 着想メモから始める低負荷フロー

人間は最初から完成した仕様を書く必要はない。自由な着想メモに、目的・背景・避けたいこと・制約・思いつきだけを書けばよい。LLM はそれを reconciliation table、DESIGN への反映案、矛盾、質問候補に分解する。
LLM は IDEA の曖昧語・業務語を、仮定・質問・DESIGN 反映案・受入基準へ展開する。数値例やカタログは固定値ではなく、プロジェクトの実情に合わせて扱う。

| 文書 | 扱い |
|---|---|
| `IDEA.md` | 任意の入力補助。存在しない場合・雛形だけの場合はスキップ |
| `DESIGN.md` | 仕様正本。実装判断、IR 生成、capability plan、テスト生成の起点。長期追跡する受入基準は `AC-001:` 形式にする |
| `.llm/data/*.edn` | 既存の分析情報。DESIGN 由来の中間表現と照合し、仕様と実装の drift を検出 |

#### 着想メモ先行キックオフプロンプト

```
このプロジェクトのテンプレートを使って初期化を行う。
まず CLAUDE.md、IDEA.md、DESIGN.md、.llm/guide/SPEC_GUIDE.md、.llm/guide/BOOTSTRAP_GUIDE.md を読んでから着手してほしい。

IDEA.md の内容を整理し、DESIGN.md への反映案を作ってください。
reconciliation table を提示し、反映済み / 保留 / 却下 / 質問化を明確にしてください。
IDEA.md が雛形だけなら、通常の最小版キックオフとして扱ってください。
DESIGN.md と食い違う内容があれば、実装へ進まず 1 点ずつ確認してください。
```

### 1 回のキックオフで始める

以下のいずれかのキックオフプロンプトを LLM エージェントに送信する。以降、LLM は `.llm/guide/BOOTSTRAP_GUIDE.md` に従い、仕様確定・構造作成・依存追加の承認を求めながら進める。

- **着想メモ先行版**（人間にとって最も軽いタイプ）: `IDEA.md` に自由記載してから送信。LLM が DESIGN 反映案と質問候補を作る
- **完全版**（事前に人間専権 (L0) 項目を決めてから送信するタイプ）: 目的・ユースケース・受入基準・エントリ種別・組織名・ドメイン名候補・デプロイ構成・環境別設定を 1 通に収める。往復数最小
- **最小版**（対話しながら埋めるタイプ）: 目的 1-2 行のみ記載して送信。残りは LLM が 1 点ずつ確認する

---

#### 完全版キックオフプロンプト

```
このプロジェクトのテンプレートを使って初期化を行う。
まず CLAUDE.md、DESIGN.md、.llm/guide/BOOTSTRAP_GUIDE.md を読んでから着手してほしい。

【プロジェクト名】<例: billing-service>
【組織名】<例: gugenkoubou>
【トップ名前空間】<例: gugenkoubou.billing>
【最初のドメイン名】<例: invoice>
【エントリ種別】<例: Web API / CLI / バッチ / ライブラリ / ワーカ / bot / GUI>
【デプロイ構成】<例: 単一 uberjar を Docker イメージに同梱して ECS で実行>
【目的】
<例: 社内経理部門の請求書発行・追跡業務を自動化する。
現状 Excel での手作業で月 100 時間の工数がかかっており、これを 10 時間以下にする>
【主要ユースケース】
1. <例: 請求書の発行と PDF 生成>
2. <例: 未払い検知と督促メール送信>
3. <例: 月次締め処理と会計データエクスポート>
【受入基準】
1. <例: 上記 3 ユースケースが全て動作する>
2. <例: 月次締め処理が 1000 件に対して 10 秒以内に完了する>
3. <例: PII を DB に平文保存しない>
【環境別設定】<任意>
<例: DB 接続情報（dev/prod）・ポート番号・外部 API キー等>

不明点は 1 点ずつ確認し、自己解釈で埋めないでほしい。
```

#### 最小版キックオフプロンプト

```
このプロジェクトのテンプレートを使って初期化を行う。
まず CLAUDE.md、DESIGN.md、.llm/guide/BOOTSTRAP_GUIDE.md を読んでから着手してほしい。

【目的】<1-2 行で>
【エントリ種別】<Web API / CLI / バッチ / ライブラリ / ワーカ / bot / GUI>

残りの人間専権 (L0) 項目（プロジェクト名・組織名・トップ名前空間・ドメイン名候補・
デプロイ構成・主要ユースケース・受入基準・環境別設定）は 1 点ずつ確認して。
```

---

### 承認構造

**主要バッチゲート 2 箇所**で実テキストを提示して承認を求める：

**実行前提表**:

| 場面 | 先に確定すること | LLM が先に提示するもの | 実行 |
|---|---|---|---|
| component を作る | 追加自体の承認 (L1) | `poly create component ...` と関連差分 | 承認後に LLM が実行 |
| base / project を作る | 作るかどうかの判断 (L0) | 候補名、用途、実行コマンド | 人間の判断確定後に実行 |
| 依存を追加する | 採用可否の判断 (L0) | 追加理由、対象 brick、`deps.edn` 差分 | 採用決定後に差分反映 |

| ゲート | 承認対象 | 権限根拠 |
|---|---|---|
| 1. 仕様 + 技術選定 | DESIGN.md 反映案／workspace.edn :top-namespace 差分／プロダクト README 生成方針／必要な用途別機能カテゴリと推奨ライブラリ案 | 承認必須 (L1)。未記載領域の技術採用は人間専権 (L0) |
| 2. 構造 + 依存 | `poly create component/base/project` 3 コマンド／brick deps.edn 追加内容（実コード） | 判断権限と実行主体を分けて扱う。component 作成は承認必須 (L1)、base/project 作成と依存採否は人間専権 (L0)。実行は承認内容に従う |

**条件付き承認必須 (L1) 成果物**（まとめて提示し、全承認または全修正指示で受ける）:

| 成果物 | 採用条件 |
|---|---|
| config.edn（必要時）／CI 設定／build.clj（uberjar 時）／dev/user.clj 調整／workspace.edn :projects 登録／ルート deps.edn :dev :extra-deps/:extra-paths | いずれも承認必須 (L1)。実内容をまとめて提示し、全承認または全修正指示で受ける |

**ゲート 3 の縮退**: 完了処理のうち LLM が承認必須 (L1) として担うのは **KNOWLEDGE 追加エントリ** と **README プロダクト版全文** のみ。ADR は承認済み判断の記録として LLM が発行し、事後報告する。

### 完了時

LLM が最終コミットコマンド（例: `git commit -m "Complete project bootstrap"`）を提示する。ユーザが実行して完了。

**BOOTSTRAP_GUIDE の移動や CLAUDE の参照表編集は行わない**。初期化完了後は通常開発フローに移る。

### 曖昧点が見つかったとき

LLM は 1 点ずつ人間に確認する。自己解釈で進めない。

### 詰まったとき

LLM が詰まった時は QUESTIONS に Q を起票して停止する。人間は Q の内容を読んで判断を提示する。
¤ .llm/memory/QUESTIONS.md

### 詳細を追いたい場合

| 目的 | 参照先 |
|---|---|
| LLM 側の技術手順 | `.llm/guide/BOOTSTRAP_GUIDE.md` |
| 承認権限・対話ルール | `.llm/guide/COLLABORATION_GUIDE.md` |
| 技術選定の判断済み推奨集 | `.llm/guide/STACK_GUIDE.md` |
| 作業原則 | `CLAUDE.md` |

---

## 既存 repo への導入

既に動いている Clojure / Polylith repo に後から本テンプレートを持ち込む場合は、新規 bootstrap 手順を最初から実行しない。既存構造を壊さないよう、導入直後は `:adoption-mode :retrofit` として扱い、検査結果は WARN 中心で棚卸しする。

plain Clojure repo に本テンプレートを導入する場合、それは **Polylith 化する意図がある**ものとして扱う。Polylith を採用しない repo 向けに本テンプレートを薄く使う運用は想定しない。

既存 Polylith repo に導入する場合も、既存流儀を温存するためではなく、**本テンプレートの厳密な記録規律・境界規律・機械検査へ移行する**ために使う。`retrofit` は一時的な棚卸し状態であり、最終的には `:adoption-mode :complete` を目指す。

詳細手順の正本:
¤ .llm/guide/BOOTSTRAP_GUIDE.md §4.1
∵ .llm/guide/MAINTAINERS_GUIDE.md §7.6
∵ .llm/guide/MAINTAINERS_GUIDE.md §7.7

### 既存テンプレート派生プロジェクトを更新する

古いテンプレート由来の repo で `.llm/repo-context.edn` が無い、または古い場合は、manifest 候補を表示してから人間が確認する。

```bash
./.llm/scripts/llm-template-adopt.sh
```

個別に確認する場合は以下を順に実行する。

```bash
./.llm/scripts/detect-repo-profile.sh
./.llm/scripts/propose-repo-context.sh
./.llm/scripts/propose-template-migrations.sh
./.llm/scripts/propose-adoption-plan.sh
./.llm/scripts/apply-repo-context-migration.sh
./.llm/scripts/check-workspace-integrity.sh
```

`apply-repo-context-migration.sh` は既定で `APPLY` 入力を要求する。属性キーワード（`:workspace-kind` / `:capabilities` / `:adoption-mode`）は自動検出されるが、manifest への反映は人間承認後に限る。

### 未導入の既存 Clojure / Polylith repo に持ち込む

テンプレート repo 側から dry-run でコピー計画を確認し、承認後に `--apply` する。既存ファイルは上書きせず、競合時は `.candidate.<timestamp>` として出力する。

```bash
./.llm/scripts/install-llm-template.sh --target /path/to/repo
./.llm/scripts/install-llm-template.sh --target /path/to/repo --apply
```

その後、target repo 側で profile 検出・manifest 候補確認・承認後適用を行う。

```bash
cd /path/to/repo
./.llm/scripts/llm-template-adopt.sh
```

個別に確認する場合は以下を順に実行する。

```bash
./.llm/scripts/detect-repo-profile.sh
./.llm/scripts/propose-repo-context.sh
./.llm/scripts/propose-template-migrations.sh
./.llm/scripts/propose-adoption-plan.sh
./.llm/scripts/apply-repo-context-migration.sh
./.llm/scripts/check-workspace-integrity.sh
```

`check-workspace-integrity.sh` は `:capabilities` に含まれる検査だけを対象にする。`:adoption-mode :retrofit` の間は、失敗しても既存 repo の作業開始を block せず WARN として表示する。`retrofit` は、plain Clojure repo では Polylith 化計画、既存 Polylith repo では本テンプレートの厳格規律への合流計画を立てるための一時状態であり、長期運用モードではない。

`propose-template-migrations.sh` は `.llm/migrations/` の migration ledger と repo 側の `:applied-migrations` を比較し、未適用の判断材料を出す。git revision は由来確認の補助情報であり、移行判定は migration id で行う。

`propose-adoption-plan.sh` は推奨ライブラリ調査ではなく、local repo の検出結果と manifest から「次に人間が確認すべき移行作業」を並べる。plain Clojure repo では Polylith 化の計画作成を必須作業として提示する。

---

## ファイル構成

```
<project-root>/
├── README.md                    ← テンプレート入口（派生時はプロダクト README へ完全置換）
├── CLAUDE.md                    ← LLM 向け作業規約（毎セッション必読）
├── DESIGN.md                    ← プロダクト仕様（初期化時に埋める）
│
├── .llm/guide/               ← プロジェクト運営ガイド
│   ├── CODING_GUIDE.md          Clojure 書き方詳細
│   ├── POLYLITH_GUIDE.md        Polylith 運用・brick コード例
│   ├── STACK_GUIDE.md           技術選定の判断済み推奨集（判断結果の記録）
│   ├── TEMPLATE_USAGE_GUIDE.md  派生後も読めるテンプレート由来・参照導線の入口
│   ├── COLLABORATION_GUIDE.md   LLM と人間の協働プロトコル
│   ├── BOOTSTRAP_GUIDE.md       初期化手順詳細（LLM 向け、完了後は CLAUDE.md §0 の参照指示で自然にスキップ）
│   └── MAINTAINERS_GUIDE.md     テンプレート自体の保守・設計原則
│
├── .llm/memory/              ← プロジェクトの記憶（実装中に蓄積）
│   ├── QUESTIONS.md             判断保留トラッカー
│   ├── KNOWLEDGE.md             現時点で有効な知識（契約・不変条件）
│   └── adr/                     アーキテクチャ決定記録
│       ├── README.md            ADR とは何か、運用ルール
│       ├── template.md          ADR 雛形
│       └── NNNN-topic.md        発行された ADR（テンプレートには含まれない）
│
├── .llm/scripts/             ← ワークスペース整合性検査・EDN 生成スクリプト
│   ├── README.md                スクリプト一覧・機械化 5 層構造
│   ├── check-workspace-integrity.sh  総合検査（完了条件から起動、§5.5）
│   ├── check-placeholders.sh         workspace.edn / deps.edn プレースホルダ残存
│   ├── check-brick-registration.sh   brick と deps.edn の登録整合
│   ├── check-deprecated-libs.sh      非推奨ライブラリの採用宣言検知
│   ├── check-forbidden-requires.sh   非推奨 namespace の require 検知
│   ├── check-conflicting-libs.sh     併用禁止ライブラリペアの検知
│   ├── check-interface-contracts.sh  interface.clj の m/=> 契約網羅
│   ├── check-trace-metadata.sh       仕様 ID と public boundary / deftest metadata の照合
│   ├── gen-trace-index.sh            trace metadata から docs/TRACE.md / trace-index.edn を生成
│   ├── check-trace-index.sh          Trace Index 生成物の drift 検査
│   ├── trace-impact.sh               要件・公開関数・変更差分から仕様上の影響範囲を表示
│   ├── check-single-ns-per-file.sh   1 ファイル 1 ns
│   ├── check-vulnerabilities.sh      clj-watson による脆弱性スキャン（release 前）
│   ├── gen_lib_catalog.clj           技術選定の判断済み推奨集の EDN block から生成物を生成
│   ├── lint-import-hooks.sh          依存ライブラリ提供の clj-kondo hook 取込
│   ├── detect-repo-profile.sh        既存 repo の workspace-kind / capabilities 候補を検出
│   ├── propose-repo-context.sh       repo-context.edn 候補を表示（副作用なし）
│   ├── propose-template-migrations.sh  migration ledger と適用済み migration の差分を表示
│   ├── propose-adoption-plan.sh      既存 repo の移行作業順を local signals から提示
│   ├── llm-template-adopt.sh         detect / propose / migration / adoption plan を順に表示する統合入口
│   ├── check-repo-context-consistency.sh  capability 依存・adoption mode・migration ledger 参照の検査
│   ├── apply-repo-context-migration.sh  承認後に repo-context.edn を作成
│   ├── install-llm-template.sh       未導入 repo へテンプレートファイルを dry-run first で導入
│   ├── session-briefing.sh           SessionStart 時の状態ブリーフィング（REPL 状態含む）
│   ├── repl-eval.sh                  稼働中 nREPL へ eval 送信（LLM 向け、CLAUDE.md §9）
│   └── repl_eval.clj                 repl-eval.sh の Clojure 実装（clj -X:repl-eval）
│
├── .llm/data/                ← 仕様・構造・技術選定から生成される機械可読 index
│   ├── libs.edn                      lib-catalog 全 entry（Malli 検証済）
│   ├── design-ir.edn                 DESIGN 由来の requirement / use case / test obligation
│   ├── trace-index.edn               仕様 ID と public boundary / deftest の impact index（trace metadata 追加後に生成）
│   ├── deprecated-libs.patterns      deps.edn 採用検知用パターン
│   ├── forbidden-requires.patterns   require 検知用パターン
│   └── conflicts.patterns            併用禁止ペアパターン
│
├── .llm/templates/           ← 派生プロジェクトへコピー/貼り付ける Markdown 雛形 / 断片。**正本は guide / CLAUDE**、本ディレクトリは規約の運用補助
│   ├── README.md                    ディレクトリの位置づけと雛形 / 断片一覧
│   ├── PROJECT_README.md            派生プロジェクト README の半自動生成雛形
│   └── fixture-state-summary.md     越境 UC PR 本文断片（POLYLITH_GUIDE §7.4.1 関連）
│
├── docs/BRICKS.md            ← brick.edn / interface.clj から生成される閲覧用 Brick Map（派生プロジェクトで brick 作成後に生成、直接編集しない）
├── docs/PROJECTS.md          ← project.edn / project deps から生成される閲覧用 Project Map（project 作成後に生成、直接編集しない）
├── docs/WORKSPACE.md         ← workspace 全体の生成ビュー（直接編集しない）
├── docs/TRACE.md             ← trace metadata から生成される仕様 impact map（直接編集しない）
│
├── .clj-kondo/config.edn        lint 機械化（polyguard hook 同梱）
├── .clj-kondo/polyguard/        custom hook（機械化第 2 層: 本テンプレート固有パターン）
├── .gitignore
├── cljfmt.edn                   フォーマッタ
├── deps.edn                     tools.deps（必須技術基盤のみ，本番依存は brick deps.edn に）
├── workspace.edn                Polylith 設定
└── development/src/dev/user.clj REPL 駆動開発エントリ
```

## 各文書への導線

| 目的 | 読むべき文書 |
|---|---|
| LLM 作業規約 | `CLAUDE.md` |
| プロダクト仕様の確認・記入 | `DESIGN.md` |
| 初期化手順の詳細 | `.llm/guide/BOOTSTRAP_GUIDE.md` |
| 技術選定 | `.llm/guide/STACK_GUIDE.md` |
| Clojure の書き方で迷った | `.llm/guide/CODING_GUIDE.md` |
| Polylith 構造判断・brick 追加 | `.llm/guide/POLYLITH_GUIDE.md` |
| brick 構成・機能分担の把握 | `docs/BRICKS.md`（閲覧用生成物） / `.llm/data/brick-map.edn`（検索用生成物）。正本は各 `brick.edn` と `interface.clj`。任意の `:brick/group` は類似 brick の俯瞰用で、構造境界ではない |
| project / workspace 構成の把握 | `docs/PROJECTS.md` / `docs/WORKSPACE.md`（閲覧用生成物） / `.llm/data/workspace-map.edn`（検索用生成物）。正本は `project.edn`、`workspace.edn`、`deps.edn`、`brick.edn` |
| LLM と人間の協働方針で迷った | `.llm/guide/COLLABORATION_GUIDE.md` |
| テンプレート自体の改修 | `.llm/guide/MAINTAINERS_GUIDE.md` |
| 判断に迷った時（Q を立てる） | `.llm/memory/QUESTIONS.md` |
| 契約・不変条件の記録 | `.llm/memory/KNOWLEDGE.md` |
| 重要な設計判断の記録 | `.llm/memory/adr/README.md`（運用ルール） |
| ワークスペース整合性検査スクリプト・機械化 5 層構造 | `.llm/scripts/README.md` |

## 設計の基底思想（要約）

- **疲労最小化**: LLM の誤りを構造的に封じる（全域性・不変性・副作用の隔離）
- **機械化 5 層**: 第 1 層 clj-kondo 組込 linter / 第 2 層 `.clj-kondo/polyguard/` custom hook / 第 3 層 Splint / 第 4 層 `.llm/scripts/check-*.sh`（設定・構造検査）+ Polylith `poly check` + Malli instrumentation / 第 5 層 clj-watson（時間軸脆弱性）。規約を人間の注意力ではなくツールで強制（詳細は `MAINTAINERS_GUIDE.md` §5.10）
- **単一の正本（SSOT）生成**: `.llm/scripts/gen_lib_catalog.clj` が `STACK_GUIDE` の `;; lib-catalog` EDN block 群を検証・合成し `.llm/data/` 配下に生成物を出力する。shell script はその生成物を読む
- **Brick Map 生成**: 各 `brick.edn` と `interface.clj` を正本として閲覧用 Map / `.llm/data/brick-map.edn` を生成し、component/base の意味違反・重複 capability・drift を検査する。任意の `:brick/group` は類似 brick の俯瞰用 index として生成し、再分割 smell は advisory warning に留める
- **Project / Workspace Map 生成**: 各 `project.edn`、`workspace.edn`、`deps.edn`、`brick.edn` から閲覧用 Map / `.llm/data/workspace-map.edn` を生成し、deploy intent と project deps の整合を検査する
- **REPL as Primary Workbench**: `.llm/scripts/repl-eval.sh` により LLM が稼働中 nREPL に eval / load-file を送信。永続 session で状態を継続し、編集から検証までを同一ターンで閉じる
- **技術選定の判断済み推奨集**: 必須技術基盤はワークスペースルートで常に採用し、追加ライブラリは必要な brick の `deps.edn` に配置する。判断済み推奨集は `.llm/guide/STACK_GUIDE.md`
- **4 種の文書分離**: 仕様（DESIGN）/ 知識（KNOWLEDGE）/ 決定履歴（ADR）/ 判断保留（QUESTIONS）
- **自己停止プロトコル**: LLM が時間感覚なく詰まった時、ターン数閾値で停止し Q を立てる

詳細は `CLAUDE.md` §1 と `.llm/guide/MAINTAINERS_GUIDE.md`。

## ライセンス

＜TODO: プロジェクトに応じて設定＞
