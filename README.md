# Clojure Polylith LLM Starter

**IDEA.md にアイデアを書くだけで、Polylith Architecture の Clojure コードを LLM が極めて効率良く構築するためのテンプレート。**

_A Clojure + Polylith template for building production code from a free-form idea, with an LLM as your primary collaborator._

## このテンプレートが提供する一本道

```text
IDEA.md（自由記述の着想）
   ↓ LLM が翻案
DESIGN.md(仕様正本: 目的・ユースケース・受入基準)
   ↓ 機械抽出
design-ir.edn（要件・使用例・テスト義務の EDN）
   ↓ Polylith 構造へ落とす
components / bases / projects（brick 単位の物理境界）
   ↓ Malli 契約と REPL で検証
公開関数 + interface + テスト + trace metadata
   ↓ 自動 gate と機械検査
commit → release
```

各ステップで、LLM が判断する箇所と機械が導出する箇所が明確に分離されている。人間が止めるのは不可逆な判断と曖昧性だけである。

## なぜ「IDEA を書くだけ」で成立するのか

5 つの仕組みが**同時に**揃っているから、LLM 単独でこの一本道が動く。

### ① IDEA → DESIGN の自動翻案

`IDEA.md` に「何を作りたいか」「避けたいこと」「制約」を自由に書く。LLM は次のセッションでそれを読み、`DESIGN.md` への反映案、矛盾、質問候補、受入基準、テスト義務に分解する。仕様を完成形で書く必要はない。

### ② 仕様の機械可読化

`DESIGN.md` から requirement / use case / acceptance criteria / constraint を EDN として抽出する（`design-ir.edn`）。実装側の公開関数とテストには `:trace/requirements` / `:trace/use-cases` / `:trace/test-obligations` という metadata を付ける。`trace-impact.sh` で「この要件に関係するコードとテスト」「今回の変更が影響する要件」を逆引きできる。

### ③ Polylith による物理的境界

[Polylith](https://polylith.gitbook.io/) は brick（components / bases）と interface で物理的に境界を区切るアーキテクチャ。LLM が触る範囲が brick 単位に局所化され、`poly check` が境界違反を CI で fail させる。LLM が「ついでに別領域を直す」誘惑が構造的に封じられる。

### ④ Malli による動的契約

[Malli](https://github.com/metosin/malli) で関数の入出力契約 (`m/=>`) を書き、instrumentation を有効化すると、契約違反が即例外化する。動的言語のまま境界を fail-closed にできる。LLM が型を捏造しても、REPL を 1 回叩けば顕在化する。

### ⑤ 「次に何をすべきか」を機械が返す

迷ったら 1 コマンド:

```bash
$ ./.llm/scripts/evidence.sh what-now
```

このコマンドは git diff・Polylith 構造・検証履歴を見て、**今やるべき次の 1 アクションを 1 つだけ返す**。

```text
== Evidence What Now ==
State: gate-blocked
Task: 2026-05-16-evidence-gate-8eaa73b417494283
Next: ./.llm/scripts/evidence.sh declare --task 2026-05-16-evidence-gate-8eaa73b417494283 --all-none
Reason: save-required change has no matching packet or close record
```

`git commit` を打つと pre-commit hook が走り、「この変更にはどの検証が必要か」を変更内容から判定する。必要な検証が記録されていなければ commit は止まり、何を埋めるべきかが指示される。

LLM は workflow 手順を暗記しない。人間も暗記しない。

## Why Clojure × LLM

静的型に頼らずに「短いループで誤りを顕在化させる」道具立てが、Clojure には揃っている。

- **REPL**: 編集から検証までを 1 ターン内で閉じる。LLM の出力をそのターンで反証できる
- **Malli**: 関数契約と instrumentation で、動的言語でも境界を fail-closed にできる
- **Polylith**: brick / interface で物理的に境界を区切る
- **不変データ・データ指向**: 副作用を最外層に寄せ、内側を純粋に保つ。LLM が触る範囲を局所推論できる

このテンプレートは、この 4 つの上に「**変更範囲と必要な検証を、LLM の自己申告ではなく構造から機械的に導出する**」層を重ねている（**Structural Evidence View**、構造的証拠ビュー）。LLM は導出できなかった部分だけを自然言語で明示する。

## 実用性 — 人間が覚えること

人間側の認知負荷は次の 5 項目に圧縮されている。

1. アイデアを `IDEA.md` に書く（仕様完成形でなくてよい）
2. 着手前に `evidence.sh what-now` を見る
3. 迷ったら `.llm/memory/QUESTIONS.md` に質問を立てる
4. commit は pre-commit hook に任せる（順序を覚えない）
5. 仕様変更は `DESIGN.md` を直接書き換える（履歴は git と決定記録が保全）

これ以外はガイドとスクリプトとフックが肩代わりする。LLM は毎セッション規約文書を読むことが機械強制されており、変更種別ごとの検査・完了条件・REPL 必須トリガ・自己停止プロトコルはそこから全て参照可能。
∵ CLAUDE.md

## 開発の自律性 — LLM はどこまで走るか

LLM は次の連鎖で自走する。**人間が止めるのは不可逆な判断と、自己解釈できない曖昧性だけ**である。

```text
session 開始
  └→ briefing が「直近の検証済変更」「次の 1 アクション」を冒頭で表示
edit
  └→ evidence.sh what-now が次の 1 アクションを返す
git add / git commit
  └→ pre-commit hook が変更内容を見て必要な検証を判定。未検証なら止める
       └→ 止まったら、何を埋めるべきかも機械が指示する
LLM が埋める
  └→ 機械が導出できなかった「意味的影響」「残った未知」「他 brick への影響」を自然言語で書く
検証を実行
  └→ どのコマンドが、どの環境で、何秒で、どの exit code で終わったかを自動記録
完了
  └→ 「予測した変更範囲」と「実際の変更範囲」を機械が照合し、ズレがあれば指摘する
```

LLM が独断で進めてよい範囲と、必ず人間に渡す範囲は 4 段階の権限階層（人間専権 / 承認必須 / 実施後報告 / 独断可）で機械的に判定される。
∵ .llm/guide/COLLABORATION_GUIDE.md

### Claude / Codex / 人間で同じ仕組み

`./.llm/scripts/` 配下のスクリプト群は、Claude Code でも Codex でも人間でも同じものを使う。pre-commit hook は repo-local で 1 回有効化すれば、誰が commit しても同じ gate を通る。

## 結果として — 監視疲労が消える

このテンプレートが目指す副次効果は、**LLM の出力を人間が常時監視しなくても済む構造**である。LLM が大量にコードを書ける時代に、人間がレビュー役として消耗し続ける構造は持続しない。

このテンプレートは:

- 機械が検査できるものは機械に検査させる
- 人間はプロダクト判断・仕様判断・不可逆な設計判断に集中する
- LLM が触る範囲を Polylith brick 単位に小さく保つ
- Malli 契約と REPL 検証で、動的言語でも短いフィードバックループを作る
- 仕様 / 知識 / 決定履歴 / 判断保留を混ぜない
- 仕様と実装の対応を EDN 生成物で追跡する
- 検証は LLM の自己申告ではなく、可能な限り構造から導出する
- 迷走した LLM を人間が後から救うのではなく、早めに止める

Rust のように静的型で閉じる方向ではなく、Clojure では Malli 契約・REPL・Polylith・lint・生成 EDN・構造的証拠ビューを組み合わせて、実用上の検査可能性と局所推論性を作る。

## 一般的な仕様駆動開発との違い

一般的な SDD ツールは仕様を first-class artifact として扱い、要件 → 設計 → タスク → 実装の流れを整える。本テンプレートもその思想を共有するが、**異なる前提**から**異なる構造**で同じ目的に到達している。

一般的な SDD ツールの多くは「LLM やステークホルダーが repo 内文書を読まないチームでも仕様駆動を維持する」ことを目的とし、dashboard / 外部 issue tracker 同期 / tasks ファイル等の**外部表示による補償機構**を備える。

本テンプレートは前提を逆に取る。「**LLM が毎セッション必ず repo 内文書を読む**」ことを `session-briefing.sh` と `.llm/data/*.edn`、および毎セッション必読規約で機械化し、補償機構そのものを不要にする。
∵ CLAUDE.md

### 一般的な補償機構と本テンプレの対応

| 一般的な SDD の機能 | 想定されている弱点 | 本テンプレの構造的封じ込め |
|---|---|---|
| 進捗 dashboard / status 表示 | 人間ステークホルダーが repo を読まない | 毎セッション必読 + 権限階層で LLM の自律度を判定 |
| 作業計画ファイルと並列マーカー | 作業計画が会話で消える | brick = 再利用単位 + `poly check` + brick metadata で要件紐付け |
| 外部 issue tracker との双方向同期 | repo 内 markdown を読まない文化 | `QUESTIONS.md` / `ADR` を repo 内 markdown として保持、git 履歴と一体管理 |
| proposal → apply → archive の状態機械 | 仕様変更の所在が分散 | `QUESTIONS.md` の状態遷移 + 採用モード分類 |
| エージェント規約ファイルの拡張 | エージェントごとの規約分散 | `CLAUDE.md` に一極集中、`AGENTS.md` は最小 bootstrap |
| マルチエージェント orchestration | 単一 LLM の context 限界 | 自己停止プロトコル + subagent 分離 + REPL primary workbench |
| task / evidence の記録 | PR description や会話に散逸 | 構造的証拠ビューが actual scope / required evidence / 残った未知を機械導出し台帳化 |

一般的な機能が「無い」のではなく、本テンプレでは**別の構造でそもそも問題が発生しない**設計になっている。

### 「読む」前提と「読まない」前提の分岐

| | 一般的な SDD の暗黙前提 | 本テンプレの明示前提 |
|---|---|---|
| 想定チーム規模 | 大規模 / 多役割 | 単独〜小規模 |
| 一次作業面 | IDE / 外部 issue tracker / dashboard | repo 内 markdown と REPL |
| LLM の repo 読み込み | 部分的・任意 | 毎セッション必須・機械強制 |
| 仕様変更の伝達 | 外部通知・同期 | git 履歴と repo 内文書で完結 |

この分岐により、本テンプレは「読む」前提でしか成立しない強み（4 種文書純度 / IR 機械照合 / 多層機械検査 / REPL primary workbench / 構造的証拠ビューによる機械導出）を獲得する代わりに、「読まないチーム」へのスケールを意図的に放棄している。**トレードオフではなく設計選択**。

### 重点の違い

| 観点 | 一般的な SDD | 本テンプレート |
|---|---|---|
| 対象 | 多言語・汎用 | Clojure + Polylith に特化 |
| 仕様の役割 | 実装前の合意文書 | 実装判断・IR 生成・テスト義務・構造検査の起点 |
| LLM との関係 | 仕様を LLM に渡して実装させる | LLM が IDEA を DESIGN へ構造化し、検査結果で自己修正する |
| 強制力 | 文書・タスク管理・レビュー中心 | clj-kondo / Splint / Polylith / Malli / scripts / clj-watson / hook で fail させる |
| モジュール境界 | 設計規約に依存しがち | Polylith brick と interface で物理的に区切る |
| 仕様と実装の照合 | ツール依存、または手動 | design-ir、brick/workspace map、trace metadata で照合 |
| 仕様とテストの照合 | 受入基準とテストの対応をレビューで追う | test obligation と `deftest` metadata を照合 |
| task close の根拠 | PR 説明や会話に残りがち | 構造的証拠ビューで actual scope / required evidence / review attention を導出 |
| 知識管理 | spec / plan / tasks に集約しがち | DESIGN / KNOWLEDGE / ADR / QUESTIONS を分離 |
| 開発ループ | テスト・CI 中心 | REPL primary workbench + Malli instrumentation + CI + hook gate |
| 人間の役割 | 仕様作成者・レビュー者 | 不可逆判断と承認に集中し、検査は機械へ寄せる |

つまり、本テンプレートは「仕様をよく書くための道具」ではなく、**IDEA から実装・検査・記憶・承認・検証記録までをつなぎ、人間の注意力に依存する箇所を減らすための Clojure Polylith 向け実装**である。

> ⚠️ **このファイルはテンプレート配布時の入口。**
> 派生プロジェクトでは初期化完了時にプロダクト README として完全置換する。
¤ .llm/guide/BOOTSTRAP_GUIDE.md §4
¤ .llm/templates/PROJECT_README.md
¤ .llm/guide/TEMPLATE_USAGE_GUIDE.md

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
| **IDEA 差し替えで試したい人** | `.llm/template-only/examples/ideas/README.md` | demo IDEA のコピー手順 |
| **保守者として benchmark を回す人** | `.llm/template-only/benchmark/README.md` | gate 間 segment 観測と setup 手順 |
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

## Demo / Benchmark

`IDEA.md` だけを差し替えて試す demo IDEA は、template repo 専用の
`.llm/template-only/examples/ideas/` に置いている。

```bash
cp .llm/template-only/examples/ideas/IDEA.webhook-idempotency-processor.md IDEA.md
```

その後、通常の「開始手順（LLM 駆動初期化）」に従う。
`.llm/template-only/` は派生プロジェクトには残さない領域であり、bootstrap 完了後に削除する。

保守者が benchmark として観測する場合は、直接 agent を起動せず、まず setup を実行する。

```bash
./.llm/template-only/benchmark/setup-run.sh \
  --scenario webhook-idempotency-processor \
  --agent codex \
  --model <model-name>
```

benchmark は無人完走テストではない。L0 / L1 gate 間の自律 segment を観測し、
承認は runner 側の marker に記録する。考え方の正本:
¤ .llm/template-only/benchmark/README.md

benchmark harness 自体の自走性は、simulation smoke として別に検査できる。
これは LLM が人間承認を代行した benchmark evidence ではなく、setup / hook /
marker 記録が壊れていないことの保守テストである。

```bash
./.llm/template-only/tests/check-benchmark-setup-smoke.sh
```

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
| demo IDEA を試す | `.llm/template-only/examples/ideas/README.md` | IDEA コピー手順とサンプル一覧 |
| benchmark を準備する | `.llm/template-only/benchmark/README.md` | protocol と `setup-run.sh` の考え方 |
| benchmark harness を検査する | `.llm/template-only/tests/README.md` | simulation smoke と保守 E2E |
| 日常開発を進める | `CLAUDE.md` | 毎セッション最初から |
| 着想から仕様を起こす | `IDEA.md` → `DESIGN.md` | IDEA は自由記載、DESIGN は仕様正本 |
| 仕様を埋める・直す | `DESIGN.md` | §0 と該当節 |
| IDEA から仕様へ翻案する | `.llm/guide/SPEC_GUIDE.md` | reconciliation と test obligation の節 |
| 技術選定を決める | `.llm/guide/STACK_GUIDE.md` | 冒頭の位置づけ + 該当機能節 |
| Polylith 構造を決める | `.llm/guide/POLYLITH_GUIDE.md` | 冒頭の前提 + 該当手順節 |
| Structural Evidence workflow を使う | `.llm/guide/STRUCTURAL_EVIDENCE_QUICKSTART.md` | `what-now` / `status` / `search` / `is-verified` / `why` / `stale` / `gate` / `declare` / `run` / `close` |
| scope と evidence を導出する | `.llm/scripts/README.md` | Structural Evidence View の節 |
| 権限や承認で迷う | `.llm/guide/COLLABORATION_GUIDE.md` | §2 を正本として読む |
| 何を記録するか迷う | `.llm/memory/QUESTIONS.md` / `.llm/memory/KNOWLEDGE.md` / `.llm/memory/adr/README.md` | 各文書冒頭の更新トリガー表 |

## 前提ツール

- **JVM LTS**
- **Clojure CLI**（`clj` コマンド、tools.deps ベース）
- **Git**（必須。このテンプレートは git repository 内で動く前提。Structural Evidence workflow、session briefing、trace / scope 導出、pre-commit gate は `git diff` / `git status` / `git log` / `git rev-parse` を使う）
- **LLM コーディングエージェント**（Claude Code 等、`CLAUDE.md` を読める LLM）
- **Babashka**（任意）。`bb` が `PATH` にある場合、一部の `.llm/scripts/` Clojure wrapper は起動を高速化するため自動的に bb を使う。無い場合は通常どおり `clj` を使うため、bb は配布物の必須条件ではない。

初期化に着手する前に、これらの実行ファイルの過不足を**一度だけ**確認できる：

```bash
./.llm/scripts/check-toolchain.sh
```

検出と不足分の導入提案のみを行い、インストールは自動実行しない。`clj-kondo` / `cljfmt` / `Splint` / `clj-watson` / `Polylith` は `deps.edn` の tools.deps alias であり、`clj` が取得するため個別インストールは不要。
∵ .llm/guide/BOOTSTRAP_GUIDE.md §0

Runtime の選択は `LLM_CLJ_RUNTIME` で制御できる。

| 値 | 挙動 |
|---|---|
| `auto` または未設定 | `bb` があれば bb、無ければ `clj` |
| `bb` | bb を必須にする。bb が無ければ失敗 |
| `clj` | Clojure CLI を強制 |

bb 実行で失敗した場合、暗黙に `clj` へ fallback しない。失敗を隠さず、必要なら `LLM_CLJ_RUNTIME=clj` を明示して再実行する。
Structural Evidence の `run` が記録する `:tool-version` には、実際に選択された runtime に加えて、その環境で検出できた `clj` / `bb` の利用可否とバージョンも入る。これにより、bb で得た evidence と clj で得た evidence を後から区別できる。

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
│   ├── check-workspace-integrity.sh  総合検査(完了条件から起動、§5.5)
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
│   ├── check-vulnerabilities.sh      clj-watson による脆弱性スキャン(release 前)
│   ├── gen_lib_catalog.clj           技術選定の判断済み推奨集の EDN block から生成物を生成
│   ├── lint-import-hooks.sh          依存ライブラリ提供の clj-kondo hook 取込
│   ├── detect-repo-profile.sh        既存 repo の workspace-kind / capabilities 候補を検出
│   ├── propose-repo-context.sh       repo-context.edn 候補を表示(副作用なし)
│   ├── propose-template-migrations.sh  migration ledger と適用済み migration の差分を表示
│   ├── propose-adoption-plan.sh      既存 repo の移行作業順を local signals から提示
│   ├── llm-template-adopt.sh         detect / propose / migration / adoption plan を順に表示する統合入口
│   ├── check-repo-context-consistency.sh  capability 依存・adoption mode・migration ledger 参照の検査
│   ├── apply-repo-context-migration.sh  承認後に repo-context.edn を作成
│   ├── install-llm-template.sh       未導入 repo へテンプレートファイルを dry-run first で導入
│   ├── session-briefing.sh           SessionStart 時の状態ブリーフィング(REPL 状態含む)
│   ├── repl-eval.sh                  稼働中 nREPL へ eval 送信(LLM 向け、CLAUDE.md §9)
│   └── repl_eval.clj                 repl-eval.sh の Clojure 実装(clj -X:repl-eval)
│
├── .llm/data/                ← 仕様・構造・技術選定から生成される機械可読 index
│   ├── libs.edn                      lib-catalog 全 entry(Malli 検証済)
│   ├── design-ir.edn                 DESIGN 由来の requirement / use case / test obligation
│   ├── trace-index.edn               仕様 ID と public boundary / deftest の impact index(trace metadata 追加後に生成)
│   ├── deprecated-libs.patterns      deps.edn 採用検知用パターン
│   ├── forbidden-requires.patterns   require 検知用パターン
│   └── conflicts.patterns            併用禁止ペアパターン
│
├── .llm/templates/           ← 派生プロジェクトへコピー/貼り付ける Markdown 雛形 / 断片。**正本は guide / CLAUDE**、本ディレクトリは規約の運用補助
│   ├── README.md                    ディレクトリの位置づけと雛形 / 断片一覧
│   ├── PROJECT_README.md            派生プロジェクト README の半自動生成雛形
│   └── fixture-state-summary.md     越境 UC PR 本文断片(POLYLITH_GUIDE §7.4.1 関連)
│
├── .llm/template-only/       ← テンプレート repo 専用。demo IDEA、benchmark、保守 E2E。派生後は削除
│   ├── examples/ideas/              IDEA.md にコピーして試す demo / benchmark 用着想メモ
│   ├── benchmark/                   IDEA 起点の開発体験を観測し、保守改善へ戻す仕組み
│   └── tests/                       テンプレート自身の重い E2E。通常ゲート外
│
├── docs/BRICKS.md            ← brick.edn / interface.clj から生成される閲覧用 Brick Map(派生プロジェクトで brick 作成後に生成、直接編集しない)
├── docs/PROJECTS.md          ← project.edn / project deps から生成される閲覧用 Project Map(project 作成後に生成、直接編集しない)
├── docs/WORKSPACE.md         ← workspace 全体の生成ビュー(直接編集しない)
├── docs/TRACE.md             ← trace metadata から生成される仕様 impact map(直接編集しない)
│
├── .clj-kondo/config.edn        lint 機械化(polyguard hook 同梱)
├── .clj-kondo/polyguard/        custom hook(機械化第 2 層: 本テンプレート固有パターン)
├── .gitignore
├── cljfmt.edn                   フォーマッタ
├── deps.edn                     tools.deps(必須技術基盤のみ，本番依存は brick deps.edn に)
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
| brick 構成・機能分担の把握 | `docs/BRICKS.md`(閲覧用生成物) / `.llm/data/brick-map.edn`(検索用生成物)。正本は各 `brick.edn` と `interface.clj`。任意の `:brick/group` は類似 brick の俯瞰用で、構造境界ではない |
| project / workspace 構成の把握 | `docs/PROJECTS.md` / `docs/WORKSPACE.md`(閲覧用生成物) / `.llm/data/workspace-map.edn`(検索用生成物)。正本は `project.edn`、`workspace.edn`、`deps.edn`、`brick.edn` |
| LLM と人間の協働方針で迷った | `.llm/guide/COLLABORATION_GUIDE.md` |
| テンプレート自体の改修 | `.llm/guide/MAINTAINERS_GUIDE.md` |
| 判断に迷った時(Q を立てる) | `.llm/memory/QUESTIONS.md` |
| 契約・不変条件の記録 | `.llm/memory/KNOWLEDGE.md` |
| 重要な設計判断の記録 | `.llm/memory/adr/README.md`(運用ルール) |
| ワークスペース整合性検査スクリプト・機械化 5 層構造 | `.llm/scripts/README.md` |

## 設計の基底思想(要約)

- **構造的証拠ビュー（Structural Evidence View）**: LLM の自己申告ではなく git diff / Polylith 構造 / 生成 index / 検査 script から「変更範囲・必要な検証・残った未知」を機械導出。`evidence.sh` workflow と pre-commit hook で gate
- **疲労最小化**: LLM の誤りを構造的に封じる(全域性・不変性・副作用の隔離)
- **機械化 5 層**: 第 1 層 clj-kondo 組込 linter / 第 2 層 `.clj-kondo/polyguard/` custom hook / 第 3 層 Splint / 第 4 層 `.llm/scripts/check-*.sh`(設定・構造検査) + Polylith `poly check` + Malli instrumentation / 第 5 層 clj-watson(時間軸脆弱性)。規約を人間の注意力ではなくツールで強制(詳細は `MAINTAINERS_GUIDE.md` §5.10)
- **単一の正本(SSOT)生成**: `.llm/scripts/gen_lib_catalog.clj` が `STACK_GUIDE` の `;; lib-catalog` EDN block 群を検証・合成し `.llm/data/` 配下に生成物を出力する。shell script はその生成物を読む
- **Brick Map 生成**: 各 `brick.edn` と `interface.clj` を正本として閲覧用 Map / `.llm/data/brick-map.edn` を生成し、component/base の意味違反・重複 capability・drift を検査する。任意の `:brick/group` は類似 brick の俯瞰用 index として生成し、再分割 smell は advisory warning に留める
- **Project / Workspace Map 生成**: 各 `project.edn`、`workspace.edn`、`deps.edn`、`brick.edn` から閲覧用 Map / `.llm/data/workspace-map.edn` を生成し、deploy intent と project deps の整合を検査する
- **REPL as Primary Workbench**: `.llm/scripts/repl-eval.sh` により LLM が稼働中 nREPL に eval / load-file を送信。永続 session で状態を継続し、編集から検証までを同一ターンで閉じる
- **技術選定の判断済み推奨集**: 必須技術基盤はワークスペースルートで常に採用し、追加ライブラリは必要な brick の `deps.edn` に配置する。判断済み推奨集は `.llm/guide/STACK_GUIDE.md`
- **4 種の文書分離**: 仕様(DESIGN) / 知識(KNOWLEDGE) / 決定履歴(ADR) / 判断保留(QUESTIONS)
- **自己停止プロトコル**: LLM が時間感覚なく詰まった時、ターン数閾値で停止し Q を立てる

詳細は `CLAUDE.md` §1 と `.llm/guide/MAINTAINERS_GUIDE.md`。

## ライセンス

＜TODO: プロジェクトに応じて設定＞
