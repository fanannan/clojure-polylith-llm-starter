# CLAUDE.md — LLM 作業ガイド

**このプロジェクトは Clojure + Polylith ワークスペースです**。
必須技術は **Clojure + tools.deps + Polylith + Malli + clj-kondo + cljfmt + Splint + clj-watson**。
追加ライブラリ（HTTP、DB、ライフサイクル管理等）は必要な**用途別機能カテゴリ**ごとに選び、各 brick の `deps.edn` に記録する。技術選定の「判断済み推奨集」は `.llm/guide/STACK_GUIDE.md` に置く。
本ファイルは、本リポジトリで作業する LLM エージェント（Claude Code を主想定）への指示書である。
人間向けの説明ではない。**LLM はこのファイルを毎セッション必ず最初に読み、ここに書かれた規約から外れない**。

| 項目 | 内容 |
|---|---|
| **対象** | 日常作業を行う LLM |
| **使うタイミング** | 毎セッション開始時、作業着手前 |
| **正本性** | 日常作業の正本 |
| **扱わないもの** | 詳細な承認マトリクス、初期化の細手順、保守原則の全体像 |

詳細の正本:
- 権限と承認: `COLLABORATION_GUIDE.md`
- 初期化手順: `BOOTSTRAP_GUIDE.md`
- テンプレート保守原則: `MAINTAINERS_GUIDE.md`

## 本文書群の参照関係

### ルート直下（最重要）

| 文書 | 役割 | いつ読むか |
|---|---|---|
| **CLAUDE.md（本文書）** | 第一原理と常時制約 | **毎セッション必読** |
| **DESIGN.md** | プロダクト仕様（何を作るか） | 実装に着手する前、仕様を確認する時 |
| **AGENTS.md** | 非 Claude エージェント（Codex、GitHub Copilot 等が読む標準慣習、OpenAI 提唱）向けリダイレクタ。内容は「CLAUDE.md に従え」の 1 行のみ | 他エージェント実行時 |

### .llm/guide/（プロジェクト運営ガイド）

| 文書 | 役割 | いつ読むか |
|---|---|---|
| `.llm/guide/CODING_GUIDE.md` | Clojure の書き方詳細、LLM 特有の落とし穴 | コーディング判断に迷った時 |
| `.llm/guide/POLYLITH_GUIDE.md` | Polylith 構造の詳細運用、brick 追加手順、境界判断 | brick 追加時・`poly check` で詰まった時 |
| `.llm/guide/STACK_GUIDE.md` | 技術選定の判断済み推奨集（必須技術基盤、用途別機能カテゴリ、禁止・非推奨ライブラリ） | ライブラリ採用検討時、技術選定根拠の確認時 |
| `.llm/guide/COLLABORATION_GUIDE.md` | LLM と人間の協働プロトコル（役割分担・曖昧性解消・対話） | 協働方針・質問粒度・役割優先順位で迷った時 |
| `.llm/guide/BOOTSTRAP_GUIDE.md` | プロジェクト初期化手順 | **初期化期のみ**（完了後は §0 の参照指示で自然にスキップされる） |
| `.llm/guide/MAINTAINERS_GUIDE.md` | テンプレート自体の設計原則・保守者向け | テンプレート改修・ライブラリ更新・規約追加時 |

### .llm/memory/（プロジェクトの記憶）

「分類管理の原則」（仕様・知識・決定履歴・判断保留を混在させない）の実装。**役割が対照的なので混同しない**。

| 文書 | 性質 | 更新 |
|---|---|---|
| `.llm/memory/QUESTIONS.md` | 判断保留トラッカー | 未対応(open) → 解決済み(resolved) で閉じる、軌跡はアーカイブ |
| `.llm/memory/KNOWLEDGE.md` | 現時点の契約・不変条件・暗黙知 | **上書き更新**、常に最新 |
| `.llm/memory/adr/NNNN-topic.md` | 決定履歴（なぜそう決めたか） | **一度発行したら不変**、改訂は新 ADR で supersede |

困った時はまず **§1 原理**に立ち返り、必要なら上記詳細ファイルを引く。

#### 更新トリガ早見表

| 何が起きたか | 更新先 |
|---|---|
| まだ判断が確定していない | `QUESTIONS.md` |
| 継続参照する契約・不変条件が確定した | `KNOWLEDGE.md` |
| 「なぜそう決めたか」を将来辿れるように残したい | `adr/NNNN-topic.md` |
| 何を作るか自体が変わった | `DESIGN.md` |

### 参照マーカー規約

Markdown 文書から別の Markdown 文書を指す時は、本文中に裸で埋め込まず、独立行に分離して次の 3 種で型付けする。

- `¤`: 実行前に読む必須参照
- `∵`: 根拠・背景参照
- `⚠`: 問題発生時のみ参照

運用規則:

- 行頭 1 文字 + 半角スペース 1 個で始める
- 1 行 1 参照だけを書く
- 本文中の無印 `FOO.md §X` を禁止する
- 監査は `./.llm/scripts/check-doc-references.sh` が行う

### フェーズ別の参照マップ

| 状況 | 主に読むべき文書 |
|---|---|
| **プロジェクト初期化中** | `.llm/guide/BOOTSTRAP_GUIDE.md` |
| **日常開発** | CLAUDE.md（本文書） |
| **仕様確認** | DESIGN.md |
| **契約・不変条件の確認** | `.llm/memory/KNOWLEDGE.md` |
| **過去の設計判断の確認** | `.llm/memory/adr/` |
| **Clojure 書き方判断** | `.llm/guide/CODING_GUIDE.md` |
| **Polylith 構造判断** | `.llm/guide/POLYLITH_GUIDE.md` |
| **技術選定** | `.llm/guide/STACK_GUIDE.md` |
| **協働方針・質問粒度の判断** | `.llm/guide/COLLABORATION_GUIDE.md` |
| **判断に迷った時** | `.llm/memory/QUESTIONS.md` に Q を立てる |
| **テンプレート自体の改修** | `.llm/guide/MAINTAINERS_GUIDE.md` |

---

## 0. プロジェクトについて

プロダクトの目的・スコープ・仕様・プロジェクト固有情報（識別子・ランタイム・配布形態）はすべて **DESIGN.md** に置く。
本ファイル（CLAUDE.md）は**どう作るかの規約**のみを扱う（分類管理の原則）。
**LLM は実装に着手する前に、必ず DESIGN の関連セクションを確認する**。
¤ DESIGN.md
仕様が曖昧・矛盾を含む場合は QUESTIONS に Q を立てる（自己解釈で進めない）。
¤ .llm/memory/QUESTIONS.md

> **プロジェクト初期化が未完了の場合、BOOTSTRAP_GUIDE に従って完了させる**。
> ¤ .llm/guide/BOOTSTRAP_GUIDE.md
> 初期化完了後は BOOTSTRAP_GUIDE.md を参照する必要はない（DESIGN.md §1-§4, §8 の埋まり具合・brick の存在等から完了状態は判定可能）。ファイル移動は行わない。
---

## 1. 第一原理: 疲労最小化

本プロジェクトのすべての規約・ツール選定・自動化は、たった一つの原理から導出されている：
**LLM と人間の共同開発における修復コストを最小化する**。

本書でいう**疲労**とは、主観的な大変さ一般ではなく、**認知負荷・確認往復・手戻り・修復作業を含む総コスト**を指す。以後の「疲労最小化」は、この総コストを減らすという意味で用いる。

以降の全章はこの原理の実装である。**文書に書かれていない状況に遭遇したら、§1 に戻って判断する**。

### 1.1 三つの基底原則（失敗の発生源を構造的に封じる）

| 参照子 | 原則 | 内容 | Clojure における実装 |
|---|---|---|---|
| `§1.1.1` | **全域性** | 失敗を契約に持ち上げ、`nil` punning や例外握り潰しなどの逃げ道を封じる | **Malli** による `m/=>` 関数契約と境界検証 |
| `§1.1.2` | **不変性** | 変更可能性を限定し、ほとんどの値を不変に | Clojure 標準の**持続データ構造**、値中心設計、`defrecord` 限定使用 |
| `§1.1.3` | **副作用の隔離** | 副作用を最外層に集約し、内側は純粋に保つ | **純粋関数コア / 副作用シェル**、I/O は依存注入で境界から注入 |

これら三つは**相互に強化する**。一つ緩めると他二つの効果も目減りする。

**参照記法（本書および派生文書で統一）**: 表の原則を参照する時は `§1.1.1`（全域性）/ `§1.1.2`（不変性）/ `§1.1.3`（副作用の隔離）の形式を用いる。同様に §1.2 の四戦略は `§1.2.1`（機械化）/ `§1.2.2`（ループ短縮）/ `§1.2.3`（小単位分解）/ `§1.2.4`（早期破棄）。

### 1.2 四つの実装戦略（三原則を日々の開発で機能させる手段）

| 参照子 | 戦略 | 意味 | 具体実装 |
|---|---|---|---|
| `§1.2.1` | **機械化** | 規約を人間（LLM）の注意力ではなく、ツールで強制する | clj-kondo 厳格設定、cljfmt、Polylith の `poly check`、Malli instrumentation、`.llm/scripts/check-workspace-integrity.sh`（`.clj-kondo/polyguard/` と `.llm/scripts/` の役割分担は `MAINTAINERS_GUIDE.md §5.10`） |
| `§1.2.2` | **ループ短縮** | LLM の編集から検証までを秒単位に縮める | REPL 常駐、Malli instrumentation、**ターン内検証**（編集 → `poly test` → 結果読解を同ターンで閉じる、§8） |
| `§1.2.3` | **小単位分解** | 大きな塊を一気に生成させない。生成→検証→次を繰り返す | 1 関数 20 行以内、コミット細分化、タスク分解判断（§7.4） |
| `§1.2.4` | **早期破棄** | 詰まったらアプローチごと捨てる。完遂にこだわらない | 自己停止プロトコル（§7）、ブランチ破棄を悪としない |

**機械化充実の運用姿勢**: 機械化は「実装可能なら採用、コスト高すぎる場合のみ保留」の方針で積極的に充実させる。新しい機械化候補を発見した時の判断基準は「実装コストとトレードオフ」ではなく「機械化できるか否か」に寄せる。これは §1.2.5「失敗早期検知と承認設計」と同じ思想（可逆な静的検査を厚くして、不可逆な本番障害を減らす）。機械化の実装手段は 2 種（`.clj-kondo/polyguard/` = Clojure コード解析、`.llm/scripts/*.sh` = 設定ファイル・構造検査）で役割分担する（`MAINTAINERS_GUIDE.md §5.10`）。

### 1.2.5 失敗早期検知と承認設計（四戦略の補助原則）

本節は §1.2 の**四戦略に並ぶ第 5 戦略ではない**。§1.2.4 の「早期破棄」を、承認の置き方と失敗検知の設計へ適用した**補助原則**として置いている。既存参照との整合のため節番号は §1.2.5 のままとし、内容上は「四戦略の外にある承認設計の注記」として読む。

**原則**: 不要な承認プロセスよりも失敗を早く検知し、違うアプローチで成功させることを優先する。

§1.2.4 は LLM の編集試行を早く止める規律である。本節は同じ「早期ピボット」思想を、人間承認の置き方に適用する。承認を増やすこと自体は安全ではない。承認待ちで検証サイクルが伸びるなら、可逆な失敗の発見はむしろ遅くなる。

**帰結**:

- 承認は「不可逆な失敗」を防ぐためにのみ置く（§2 禁止事項はこの定義に基づく）
- 可逆・修復可能な操作（ADR 発行、実装コード生成、文書記述など）は **実施後報告（L2）** で運用し、誤りは supersede / 新規発行 / 修正コミットで回復する
- 承認の粒度を細かくすればするほど、失敗時のサイクル（提示→却下→再提示）が長大化し、**かえって失敗の早期検知を阻害する**。承認は粗い単位で、判定は早く行うことを優先する
- 「念のため承認を入れる」は本原則に反する。承認の付加は**不可逆性の根拠**を明示して初めて正当化される

**不可逆性の判定**:

| 判定 | 例 | 承認の扱い |
|---|---|---|
| 外部世界・本番データ・公開 API・依存関係・プロジェクト構造を変える | DB マイグレーション実行、brick deps.edn 変更、新規 base/project、公開関数の破壊的変更 | 人間専権/承認必須（L0/L1）。事前承認が必要 |
| git や文書履歴で明確に回復できる | 実装コード、テスト、ADR の新規発行、Q 起票 | 実施後報告/独断可（L2/L3）。検証と事後報告を優先 |
| 迷う | 影響範囲が広い、回復手順が不明、判断主体が不明 | 上位階層に倒す |

承認階層との対応は COLLABORATION_GUIDE が正本である。本書 §2 は日常作業で見落としてはならない高リスク項目の入口だけを置く。
∵ .llm/guide/COLLABORATION_GUIDE.md §2

### 1.3 原則の使い方（LLM への指示）

- **判断に迷ったら §1.1 の三原則に照らせ**。規約に書かれていない状況でも原則から導出できる
- **新しい規約や手順を提案する前に §1.2.1〜§1.2.4 の四戦略、または §1.2.5 の承認設計のどれに該当するかを明示せよ**
- **「規約で縛れば守られる」は誤り**。§1.2.1 機械化、または §7 の自己停止プロトコルのように、**守る手段**を同時に設計する
- **現時点で有効な知識の活用で再発見の疲労を避ける**: 実装中に判明した契約・不変条件・暗黙知は KNOWLEDGE に集約される。LLM は実装着手前に関連節を必ず確認し、同じ判断を繰り返さないこと（詳細は §8, §11）
¤ .llm/memory/KNOWLEDGE.md
- **「前提の明示」で解空間を早期に収束させる**: LLM との協働では、ユーザーが価値判断・運用制約・採否の向きを**早期に明示**するほど、LLM の提案解空間が適切に狭まり、設計コストが下がる。例: 「Babashka は使わない、shell script で書く」「サンプルコードはコメントで残す」のように採否の向きを与えると、LLM は条件付き生成・動的削除のような複雑な方向を自発的に閉じる。これは §1.3「現時点で有効な知識の活用」の延長で、**前提の言語化自体が協働の知識**になる。判断根拠が不明な時はユーザーに前提を訊くのが効率的（`.llm/guide/COLLABORATION_GUIDE.md` §4）
∵ .llm/guide/COLLABORATION_GUIDE.md §4

### 1.4 原則の自己適用

本書の原則は、Clojure コードだけでなく LLM の計画・レビュー・報告にも適用する。

| 原則 | LLM 出力への適用 |
|---|---|
| §1.1.1 全域性 | 「可能性」「リスク」「実装次第」を放置しない。残すなら、発動条件・検出方法・次アクション・判断主体を 1 行で契約化する。契約化できない不安は削る |
| §1.2.2 ループ短縮 | 編集→検証のループだけでなく、計画レビューのループも短縮対象。同種の指摘が 2 回出たら、次の応答で判断表または実装方針へ蒸留する |
| §1.2.4 早期破棄 | plan を 3 回以上改訂しても同じ論点が残る場合、同一アプローチ継続とみなし、継続せず「固定する原則 / 捨てる案 / 人間判断が必要な一点」に分解する |

確度評価は既定では `高` / `中` / `低` の 3 値を使う。百分率は、ユーザが求めた場合か、比較上どうしても必要な場合に限る。百分率を使う時も数学的精度を装わず、判断材料・未知点・下げた理由を併記する。

人間判断との整合確認と、自律を止める境界は §1.5 に従う。

### 1.5 人間判断と LLM 自律の境界

人間は、目的・価値判断・背景制約・外部事情を担う。LLM は、合意済み前提の一貫適用・実装・検証を担う。両者は役割が違うため、LLM は人間の最新発言に機械的に追従せず、既存の原則・manifest・文書体系・過去合意との整合を確認する。

人間の判断は揺れることがある。これは人間が不正確だからではなく、LLM より広い背景情報を保持し、それらを毎回完全に一貫した形で言語化することが難しいためである。LLM はこの揺れに流されず、揺れを検出したら早期に原因と対処を人間に返す。

一方で、LLM と人間の理解が整合していると確認できた範囲では、LLM は自律的に進める。確認不要な作業まで止めることは、§1.2.2 ループ短縮と §1.2.5 承認設計に反する。
∵ .llm/guide/COLLABORATION_GUIDE.md §2

LLM は、次の条件をすべて満たす範囲では自律的に実装・修正・検証を進める。

- 目的・成功条件が明確である
- 既存の原則・manifest・文書体系と衝突しない
- 変更対象と所有権が明確である
- 失敗しても git / test / review で回復できる
- 人間に新しい価値判断を求める必要がない

次のいずれかを検出したら、該当する一点だけ自律を止め、人間に確認する。

- 最新指示が既存の第一原理・保守規律・manifest と衝突する
- 以前の合意と逆方向の判断が示された
- 同じ概念に別語彙が導入され、SSOT が崩れそう
- 局所最適な指示が、全体の疲労最小化に反する
- 複数の正しさがあり、価値判断が必要
- irreversible / high-impact な変更に入る
- LLM が「この変更はできる」が「なぜ今そうすべきか」を説明できない

確認時は、作業全体を止めない。止めるべき一点だけを切り出し、並行して進められる低リスク作業があれば進める。

確認は次の形にする。

1. 衝突している前提
2. どちらを優先すると何が起きるか
3. LLM が推奨する整理案
4. 人間に決めてほしい一点

---

## 2. 禁止：勝手にやらないこと（不可逆操作）

§1 からの帰結：**自動検証では防げない**かつ**影響が大きい**操作は、LLM が勝手に実行してはならない。

意思決定は、次の「権限階層」で判定する。詳細な項目別マッピングと 4 種文書の編集権限は COLLABORATION_GUIDE が正本であり、本節は日常作業で先に見る高リスク項目の抜粋である。
∵ .llm/guide/COLLABORATION_GUIDE.md §2

判断権限（誰が決めるか）と実行主体（誰が実行するか）は別軸である。日常作業ではまず本節で高リスク項目を避け、迷ったら別紙の速読表と詳細マッピングに戻る。
∵ .llm/guide/COLLABORATION_GUIDE.md §2.0

**本節の役割**: ここでは日常作業で見落としやすい高リスク項目の**抜粋だけ**を置く。完全表にない項目や迷うケースは、本節で判断を完結させず正本に戻る。
∵ .llm/guide/COLLABORATION_GUIDE.md §2.2

| 判断権限 | この階層での LLM の振る舞い | 代表例 |
|---|---|---|
| **「人間専権」(L0)** | 決定しない。ユーザに求められた場合のみ判断材料・影響範囲・選択肢を整理 | プロダクト目的、主要スコープ、新ライブラリ採用 |
| **「承認必須」(L1)** | 案・差分・コマンドを提示し、承認後に実行 | 新規 component、interface 関数追加、DESIGN/KNOWLEDGE 改訂 |
| **「実施後報告」(L2)** | 実行して結果を報告 | core 実装、テスト追加、Q 起票、承認済判断の ADR 化 |
| **「独断可」(L3)** | 実行し、必要なら簡潔に報告 | 命名の微調整、局所的な整形、内部実装の細部 |

実施後報告 (L2) は「最後にまとめて大量差分を投げる」ことではない。レビュー可能な単位（1 phase、1 script、1 文書節、1 behavior）で、何を変え、どの gate を通したかを報告する。ユーザの `ok` / `go` は、直前に提示した次の bounded step への承認であり、未提示の全 phase への包括承認ではない。

外部の system / developer / user 指示と本書が衝突する場合、実行時の上位指示に従う。本書は repo 内 artifact の既定規律であり、外部指示を上書きしない。両方を満たせない時は、黙って片方を捨てず、どの指示を優先したかを報告する。例: commit message の言語指定が外部指示と衝突する場合は、外部指示を満たしつつ、本書の「現在形で一意に読める」条件を満たす。

以下は **判断段階** で 人間専権/承認必須（L0/L1）相当として扱う代表例である。ここでは「採否を誰が決めるか」だけを示し、具体的な提示順・実行主体は別紙の速読表と手順書に従う。迷った場合は上位階層に倒す。
∵ .llm/guide/COLLABORATION_GUIDE.md §2.0
∵ .llm/guide/BOOTSTRAP_GUIDE.md §2.0

| 高リスク項目 | 既定階層 | 補足 |
|---|---|---|
| 依存ライブラリの追加 | 人間専権 (L0) | 採否判断を人間が行う |
| brick `deps.edn` のライブラリ追加・変更 | L0 → L1 | 採否判断は L0、承認後の差分反映は L1 |
| 既存 API の破壊的変更 | 承認必須 (L1) | `interface.clj` 公開関数、Malli、DB スキーマ |
| 新規 component 追加 | 承認必須 (L1) | `poly create component` |
| 新規 base / project 追加 | 人間専権 (L0) | 構造影響が大きい |
| DB マイグレーション実行 | 人間専権 (L0) | 生成は可、実行は人間 |
| 必須技術基盤の入れ替え・削除 | 人間専権 (L0) | 設定変更も含む |
| コンポーネントの統合・分割 | 人間専権 (L0) | 境界変更は影響甚大 |
| 4 種文書の編集・状態変更 | 文書別 | 正本マトリクスを見る |

∵ .llm/guide/COLLABORATION_GUIDE.md §2.3

---

## 3. 技術構成

本テンプレートの**必須技術基盤**は以下。入れ替え不可：

- **Clojure**（言語）
- **tools.deps**（`deps.edn` による依存管理。Polylith の前提）
- **Polylith**（ワークスペース構造。§1.2.1 機械化は `poly check` による強制が核）
- **Malli**（§1.1.1 全域性の実装。`m/=>` 契約と instrumentation）
- **clj-kondo**（機械化された静的解析。`.clj-kondo/config.edn` + `.clj-kondo/polyguard/hooks.clj` は配布時点で同梱され、設定自体も必須技術基盤の一部として無効化・削除不可）
- **cljfmt**（機械化されたフォーマッタ。`cljfmt.edn` は配布時点で同梱され、設定自体も必須技術基盤の一部として無効化・削除不可）
- **Splint**（スタイル・イディオムレベル linter、clj-kondo の補完。`clj -M:lint-splint` で起動、完了条件で実行）
- **clj-watson**（依存脆弱性スキャン、時間軸を跨いだ機械化。`./.llm/scripts/check-vulnerabilities.sh` で起動、release 前必須。NVD API key 推奨）
- **`.llm/scripts/` ディレクトリ**（`check-workspace-integrity.sh` / `check-*.sh` / `lint-import-hooks.sh`、設定ファイル・ディレクトリ構造の機械的検査を担う）

必須技術基盤が固定される理由は、これらが本テンプレートの機械化・構造検証・境界契約・整形規律の前提だからである。入れ替えると個別ライブラリの変更ではなく、§1 の実装手段そのものを変更することになる。変更が必要な場合はテンプレート保守タスクとして扱う。
¤ .llm/guide/MAINTAINERS_GUIDE.md

必須技術基盤以外の技術選定（HTTP、永続化、ロギング、ライフサイクル管理、JSON 変換など）は、必要な用途別機能カテゴリごとに選ぶ。選定の論理と判断済み推奨集は `.llm/guide/STACK_GUIDE.md` に一元化されている。採用した技術は `DESIGN.md` §8.3 に記録する。

Integrant・FlowStorm・Portal は必須技術基盤ではない。前者はプロジェクトが採用したライフサイクル管理の実装例、後二者は診断補助であり、依存追加やセクション有効化を行うまで機能しない。

判断済み推奨集に載っていない領域に遭遇した場合は、§6.3 の手順に従って第一原理から判断材料を整理し、人間判断へ渡す。必須技術基盤が固定される点は変わらない。

---

## 4. 三つの基底原則の具体実装

§1.1 の原則を、Clojure コードとして実装する指針。

### 4.1 全域性（§1.1.1 の実装）

失敗を契約に持ち上げ、境界で検証する。

- **`interface.clj` の公開 `defn` に `m/=>` 契約を付ける**（境界契約は境界に集約。`core.clj` には置かない。`check-interface-contracts.sh` が機械検証）。`defn-` / `^:private` は原則として `interface.clj` に置かない。マクロ生成関数や可変長引数など契約表現で迷う場合は、`.llm/memory/QUESTIONS.md` に Q を立てる
- 外部入力（HTTP リクエスト、DB 行、外部 API レスポンス、設定ファイル）は**入口で `m/validate`**
- 開発時は Malli instrumentation を有効化（`dev/user.clj` の `(malli-on!)` を REPL 起動後に呼ぶ。ライフサイクル管理を使うプロジェクトでは起動 helper が内部的に呼ぶ）。契約違反は REPL 評価で即座に例外化
- 関数が失敗し得るなら、戻り値の型を一貫させる（常に `nil` を返すか、`{:error ...}` 形式か、一つに決める）
- コード例は別紙に置く（本テンプレートには brick サンプルは配布されない）
∵ .llm/guide/POLYLITH_GUIDE.md §2

### 4.2 不変性の活用（§1.1.2 の実装）

データ指向プログラミング。

- **素のマップ・ベクタ・セット・キーワード優先**。`defrecord` は次のいずれかのみ: (1) プロトコル多態、(2) ホットパス性能、(3) Java 相互運用
- **外部境界・コンポーネント境界・永続化境界を跨ぐマップのキーは名前空間付き**（`:user/id`、`:order/total`）。完全にローカルな一時マップは例外可。修飾子はドメイン / コンポーネント名に揃える
- **可変状態（`atom` / `ref` / `agent`）は最上位層に限定**。ここでの最上位層とは、ライフサイクルを開始・停止できる境界（`-main`、test fixture、REPL の dev helper 等）を指す。ドメイン関数内で `atom` を作らない
- 蓄積は `reduce` / `into`。ローカル `atom` で回さない
- 詳細: `.llm/guide/CODING_GUIDE.md` §2〜§7

### 4.3 副作用の隔離（§1.1.3 の実装）

純粋コア / 副作用シェル。

- **ドメイン系コンポーネント**（user, order, …）は I/O ライブラリを `require` しない（clj-kondo で警告化）
- I/O 系は**依存注入**で受け取る。ライフサイクル管理を採用する場合も、採用しない場合も、「ドメインは I/O を知らない」という原則は共通
- `println` / `prn` はアプリケーションコード（components / bases）で禁止。**例外**: ビルドスクリプトや `development/src/` 配下の一時デバッグコードでは許容する
- `with-redefs` は §1.1 全域性を破るので原則禁止（`clj-kondo` の `:discouraged-var` で警告化済）。例外的に使用する場合は **ADR で理由付け必須**（「なぜ依存注入で置き換えられなかったか」を記録）

---

## 5. 機械化された規約（§1.2.1 機械化の実装）

以下は**ツールがエラー / 警告を出す**ため、LLM はエディタ・CLI 出力を見て自己修正する。人間の記憶に頼らない。

### 5.1 clj-kondo（保存時にエディタで赤線）

`.clj-kondo/config.edn` で `error` 扱い：

- `:refer-all` / `:use` 禁止（`clojure.test` を除く）
- 未解決シンボル、未使用バインディング、重複 require
- `println` / `prn` / `with-redefs` は `:discouraged-var`
- ドメイン系コンポーネントでの I/O ライブラリ使用も `:discouraged-var`

### 5.2 cljfmt（保存時自動整形）

`cljfmt.edn` の設定で、フォーマット議論を完全排除。

### 5.3 `poly check`（Polylith 構造違反）

- コンポーネント間は `interface.clj` 経由のみ
- `base` は外部公開 API（REST/Lambda/CLI など）を持つ入口、`component` は再利用単位として内部実装を公開 API で提供
- `base` から `component` への依存は可（単方向）。`component` が `base` を参照するのは不可
- project は `:local/root` のみ
- 違反時は CI が落ちる（詳細は `.llm/guide/POLYLITH_GUIDE.md`）

### 5.4 Malli instrumentation

Malli は必須技術基盤。`dev/user.clj` で `(malli-on!)` helper を提供する：

- **ライフサイクル管理を使うプロジェクト**: 起動 helper が内部で `(malli-on!)` を呼ぶ
- **ライフサイクル管理を使わないプロジェクト**（ライブラリ配布・単発 CLI 等）: REPL 起動後に明示的に `(malli-on!)` を呼ぶ

`m/=>` 契約付き関数を REPL で呼び出した瞬間に契約違反が例外として顕在化。実行方法は本書 §9.1 と `development/src/dev/user.clj` の docstring を参照。
`poly test` / `poly test :all` の通過は回帰確認であり、契約検証完了を意味しない。
特に `interface.clj`、`m/=>` 契約、外部入力 schema の変更では、Malli instrumentation を有効化した REPL eval、または instrumentation を有効化した test fixture で契約を別途確認する。

### 5.5 完了条件（以下全通過で初めて完了報告）

完了条件はプロジェクト状態で分岐する。LLM は自分の状況を次の表で判定してからコマンドを実行する。

**判定に迷う中間状態では、安全側へ倒す**。つまり、`projects/` が未完成・`DESIGN.md` のビルド定義が未確定・初期化と通常開発の境界にある場合は、**uber ビルドだけを外し、それ以外を通す**状態として扱う。

**即判定**:

1. `workspace.edn` に `"myorg.myapp"` が残るなら、まだ初期化未完了
2. `projects/<deploy>` が無い、またはビルド定義が未確定なら、uber ビルドだけ外す
3. `projects/<deploy>` があり、ビルド定義もあるなら、全行を実行する

| 状態 | 実行する完了条件 |
|---|---|
| `workspace.edn` に配布時プレースホルダ `"myorg.myapp"` が残る | 初期化未完了。`.llm/guide/BOOTSTRAP_GUIDE.md` §2.1 を先に完了する。`check-workspace-integrity.sh` は失敗してよい状態ではなく、未完了状態の検出である |
| `projects/` が未作成（`BOOTSTRAP_GUIDE.md` §2.9 前） | 下記コマンドのうち uber ビルドだけスキップ。その他は通す |
| `projects/<deploy>` が存在し、DESIGN.md §8.4 にビルドコマンドが定義済み | 下記全行を実行 |

```bash
clj -M:lint                                    # clj-kondo（構文・型・LLM 落とし穴検知）
clj -M:lint-splint                             # Splint（スタイル・イディオム検知、clj-kondo 補完）
clj -M:format check                            # cljfmt
clj -M:poly check                              # Polylith 構造
./.llm/scripts/check-workspace-integrity.sh         # プレースホルダ残存・brick 登録・非推奨ライブラリ・:local/root 実在の総合検査
clj -M:poly test :all                          # 全テスト（全体回帰確認）
cd projects/<deploy> && clj -T:build uber      # ビルド成功（<deploy> は DESIGN.md §8.4 のビルドコマンドに合わせる）
```

release 前・週次 CI では追加で以下を実行:

- **release 前**: tag 作成、外部配布、デプロイ、納品など、依存脆弱性を後から検出すると修復コストが高い節目
- **週次 CI**: CI cron などで週 1 回以上走る定期検証。人間の手動実行でもよいが、運用主体は派生プロジェクトで決める

```bash
./.llm/scripts/check-vulnerabilities.sh             # clj-watson（時間軸を跨いだ脆弱性検知、release 前必須）
```

`./.llm/scripts/check-workspace-integrity.sh` の内訳と役割分担は scripts README にまとめる。機械化された検査群（clj-kondo 組み込み / polyguard hook / Splint / scripts / Polylith / Malli / clj-watson）は保守者向け文書で体系化する。
∵ .llm/scripts/README.md
∵ .llm/guide/MAINTAINERS_GUIDE.md §5.10

作業途中の phase では、変更種別に対応する最小 gate を先に回してよい。最終報告前、または Clojure/Polylith 構造・script 実装を触った時は上記の該当行まで戻る。

| 変更種別 | phase 中の最小 gate | 最終前 |
|---|---|---|
| Markdown 文書のみ | `check-doc-references.sh --all` と該当する文書検査 | 必要に応じて `check-workspace-integrity.sh` |
| `.llm/scripts/*.sh` / `.llm/scripts/*.clj` | 該当 script 単体 + `check-workspace-integrity.sh` | format/lint/poly check まで |
| Clojure code / Polylith 構造 | REPL 必須判定 + `poly check` / 対象 test | §5.5 の該当全行 |
| deps.edn / workspace.edn | `poly check` + 依存解決 + `check-workspace-integrity.sh` | §5.5 の該当全行 |

### 5.6 CLI 呼び出しの統一

Clojure CLI の呼び出しは `clj` に統一する。`clojure` は使用しない。

- **理由**: `clj` は `clojure` の rlwrap ラッパで、非対話用途では機能等価。不統一は §1.2.1 機械化原則に反する entropy を生み、allowlist・スクリプト・ドキュメントの重複や揺れを招く
- **適用範囲**: CLAUDE.md・`.llm/guide/` 配下・`.llm/scripts/` 配下・`.claude/settings.local.json`（いずれも git 管理下）、および LLM が新規に提案するコマンド例
- **例外**: なし（対話 REPL 起動を allowlist やスクリプトに載せる場面は現状存在しない。REPL 対話は開発者がターミナルで直接起動するのみ）

---

## 6. Polylith と技術選定の運用

Polylith 構造の操作と、追加ライブラリの採用・変更手順を扱う。`poly` CLI と判断済み推奨集を併用する。

### 6.0 component/base の公式準拠判定

- 公式基準では `base` は外部 API（REST/Lambda/CLI/gRPC 等）を公開する special brick、`component` は `interface.clj` を通じて再利用可能な機能を提供する brick。
- `component` は原則ドメイン／共通ロジック中心。ただし公式はインフラ系アダプタ（DB/API/認証等）を component として切る用途も認めているため、**外部公開そのものではなく「再利用可能性と交換可能性」**で切るケースがある。
- 設計基準（運用用）:
  - 外部世界へ窓口を開くなら `base`
  - 複数 base/component から使い回せる共通ロジックや、外部システム連携の交換可能なアダプタなら `component`
  - `base` は薄く保ち、実質処理は component へ委譲する

補助参照:
∵ .llm/guide/POLYLITH_GUIDE.md §2.3, §4

### 6.1 poly コマンド早見表

構造操作はすべて `poly` CLI 経由（手作業禁止）。

| 目的 | コマンド |
|---|---|
| 状態確認（brick 一覧・依存・変更検知） | `clj -M:poly info` |
| **構造違反の検証**（編集後に必ず実行） | `clj -M:poly check` |
| **日常作業中のテスト**（変更影響範囲の高速な回帰確認） | `clj -M:poly test` |
| **完了報告前のテスト**（§5.5 完了条件の一部、全 project 全 brick の回帰確認） | `clj -M:poly test :all` |
| **新規コンポーネント作成**(承認必須) | `clj -M:poly create component name:<name>` |
| **新規ベース作成**(人間専権) | `clj -M:poly create base name:<name>` |
| **新規プロジェクト作成**(人間専権) | `clj -M:poly create project name:<name>` |
| 依存グラフ表示 | `clj -M:poly deps` |
| ヘルプ | `clj -M:poly help` / `clj -M:poly help <cmd>` |
| **静的解析（clj-kondo）** | `clj -M:lint` |
| **スタイル解析（Splint）** | `clj -M:lint-splint` |
| **フォーマット検査** | `clj -M:format check` |
| **依存脆弱性スキャン（clj-watson、release 前）** | `./.llm/scripts/check-vulnerabilities.sh` |
| **ワークスペース整合性検査** | `./.llm/scripts/check-workspace-integrity.sh` |

**brick の書き方は別紙のコード例に従う**（本テンプレートには brick サンプルは配布されない）。
詳細手順・境界判断も別紙に置く。
∵ .llm/guide/POLYLITH_GUIDE.md §2

### 6.2 ライブラリの採用・変更

必須技術基盤以外のライブラリは、必要な用途別機能カテゴリに応じて各 brick の `deps.edn` に書く。これは**依存ライブラリの追加・削除**に該当するため、§2 禁止事項の対象であり、ユーザ承認が必須。

- **選定根拠・禁止非推奨ライブラリ**: STACK_GUIDE
∵ .llm/guide/STACK_GUIDE.md
- **初期化時の具体手順**: BOOTSTRAP_GUIDE
∵ .llm/guide/BOOTSTRAP_GUIDE.md
- **brick deps.edn への反映**: 必要な brick にのみ追加し、ルート deps.edn に二重管理しない

LLM が独自判断で brick deps.edn にライブラリを追加・削除・変更することは禁止。判断済み推奨集にない技術採用や推奨からの逸脱は、人間判断と ADR 記録を伴う。

### 6.3 判断済み推奨集の利用と原則からの導出の関係

STACK_GUIDE.md の `;; lib-catalog` カタログは、本テンプレートの第一原理と三基底原則に基づき**予め判断を済ませた結果の記録**である。毎回同じ判断を繰り返すのは疲労を生むため、予め記録して再利用する。

**利用規律**:

- カタログに記載された領域は、これを信頼して利用する(プロジェクトのゴールと矛盾しない限り)
- カタログに**未記載の領域**(該当機能節が無い技術分野、例: 特殊な科学技術計算、ゲーム、独自の用途等)に遭遇した場合、**第一原理から判断材料を整理してユーザ判断へ渡す**。これはテンプレートの欠陥ではなく、判断記録が未整備なだけ
- 原則からの導出で候補・代替・リスクを整理し、**採用可否は人間が決定**する（未記載領域は人間専権 (L0)）
- カタログにないことは思考停止の理由にならないが、LLM が独断採用する理由にもならない

**原則からの導出の手順**(未記載領域に遭遇時):

1. 要件を分解し、何が必要な用途別機能カテゴリか明確化
2. 各用途別機能カテゴリについて、疲労最小化・三基底原則・data 駆動・Malli 統合容易性・メンテナンス活動・Clojure 慣用との整合を評価基準に候補を整理
3. 候補の選定根拠と却下した代替を明示化
4. プロジェクト固有要件で判断が分かれる部分と、LLM だけでは決定できない価値判断をユーザに提示
5. 採用決定後、派生プロジェクトでは判断経緯を ADR として記録する。テンプレート保守者側で一般化できる知見なら、判断済み推奨集に追記し、記録先は保守者向け文書の規則に従う
∵ .llm/guide/STACK_GUIDE.md
∵ .llm/guide/MAINTAINERS_GUIDE.md §7

カタログの網羅追求は疲労最小化原則と自己矛盾する(網羅は永久に達成不可能)。判断記録は「知っていることを記録する」ものであり、「すべてを記録しようとする」ものではない。

---

## 7. 自己停止プロトコル（§1.2.4 早期破棄の実装）

**LLM に時間感覚はない**ため、「30 分ルール」は機能しない。
代わりに**ターン数・試行回数**で閾値化する。これが §1.2.4 早期破棄の具体実装である。

本節で使う単位:

| 用語 | 定義 |
|---|---|
| **ターン** | ユーザ入力を受けてから LLM が最終応答するまでの 1 サイクル。長い作業中の中間 update は同一ターン内の進捗報告として扱う |
| **編集 1 回** | 同一目的で同一ファイルに差分を加える 1 パッチまたは 1 フォーマット実行。別関数でも同じファイルに同じ問題を追っているなら回数に含める |
| **同一テストケース** | 同じ test var / assertion / 失敗メッセージに対応する失敗。実装側とテスト側のどちらを直しても同じ試行回数に含める |
| **同一エラー** | 行番号が変わっても、原因カテゴリと主要メッセージが同じ失敗 |
| **確度付き仮説** | `高` / `中` / `低` の 3 値で書く。百分率は使わない |

subagent / tool 呼び出し / 長い shell 実行は、ユーザへの最終応答前なら同一ターン内の作業であり、別ターンに数えない。自己停止カウンタは、対象の失敗が消えたことを検証した時、またはユーザ承認により目的自体を変更した時だけリセットする。「別の修正案を試した」だけではリセットしない。

### 7.1 自己停止の発動条件（いずれか該当で自走停止）

- 同一のテストケースを **3 回連続**で直そうとしても通らない
- 同一のエラーメッセージ（種類）が **3 ターン連続**で出ている
- 同一ファイルへの編集が **5 回**を超えた
- `poly check` / clj-kondo の同じ違反が **2 回連続**で残っている
- 新規に追加した require / import を使っても解決に近づかない
- 仮説と検証を **3 回繰り返しても収束していない**

### 7.2 詰まり状況下の進捗メモ（必要時のみ）

以下のいずれかが発動条件になったら、各ターン冒頭に進捗メモを出す:

- §7.1 自己停止条件の**閾値に近づいた時**（同一試行 2 回目、同一ファイル編集 3 回目以降）
- **仮説→検証のループに入った時**（2 回以上の仮説立案）
- **context compaction が発生した直後**（自己状態の外形化のため）

通常の 1 ターンで完結する編集（typo 修正、単純な追加等）は免除。**詰まり状況下でもメモを省略すると自己停止判定ができなくなる**ので、条件該当時は省略しない。

```
## 進捗メモ
- 目標: <1 行>
- 今回の試行: <何を変えるか>
- 前ターンからの変化: <近づいた / 同じ場所で詰まっている>
- 同一問題の連続試行: <N 回目>
```

メモは LLM 自身の自己認識と、ユーザが「LLM の詰まり度」を外部から観察する材料の両方として機能する。

### 7.3 撤退プロトコル

自己停止条件に達したら、以下の形でユーザに報告：

```
## 自己停止の報告
1. 試みた内容（最大 5 項目、箇条書き）
2. 残っている障害（エラー・構造違反・テスト失敗の具体)
3. 考えられる原因の仮説（最大 3、確度: 高 / 中 / 低）
4. 次の選択肢：
   A. 現アプローチを続行（理由を書く）
   B. ブランチを破棄して別アプローチ（推奨時は B を明示）
   C. 問題を小さく分解してやり直す
   D. 人間による設計判断を求める
```

**ブランチ破棄は悪ではない**。§1.2.4 早期破棄の原則で、完遂にこだわらず捨てる判断が疲労最小化に資する。
「ここまで書いたから完成させる」は避ける。

#### 選択肢 D を選んだ場合の処理

選択肢 D（人間による設計判断を求める）を選んだ場合、報告内容を **QUESTIONS に新規 Q として記録**する。
¤ .llm/memory/QUESTIONS.md §0.9

1. ID を採番（`Q-YYYY-MM-NNN`）、状態 `未対応(open)`
2. 本節の報告フォーマット（試みた内容・残障害・仮説）を Q の `文脈` と `選択肢` に転記
3. ユーザへの報告で「Q-YYYY-MM-NNN として記録しました」と言及
4. 以降、該当コードに `;; TODO(Q-YYYY-MM-NNN): ...` を残し、解決まで自走しない
∵ .llm/memory/QUESTIONS.md §0.8

**Q を記録せずにユーザに聞きっぱなしで放置しない**。軌跡が残らない。

### 7.4 タスク受領時の事前チェック（§1.2.3 小単位分解の実装）

新しいタスクに着手する前に、以下を自己確認：

1. **同一ターンで編集・検証・報告まで閉じるか** → No ならユーザに分割を提案
2. **成功判定が明確か** → No ならユーザに基準を確認
3. **触れるべきファイル数が 3 以下か** → No ならサブタスクに分解
4. **§2 禁止事項に触れないか** → 触れるなら承認を先に取る

---

## 8. 作業プロトコル

§1.2.3 小単位分解の実装。

### 8.0 実装着手前の確認（すべての作業に共通）

どの作業を行う時も、着手前に以下を確認する。これは §1.3「現時点で有効な知識の活用で再発見の疲労を避ける」の具体実装：

1. **仕様の確認**: DESIGN の関連節（特に §3 主要ユースケース、§4 受入基準）を読む
¤ DESIGN.md
2. **Brick / Project Map の確認（brick・機能配置・deploy 構成に関わる作業のみ）**: `docs/BRICKS.md` / `.llm/data/brick-map.edn` が存在する場合、既存 capability・担当 component・entrypoint base を確認する。project / deploy に関わる場合は `docs/PROJECTS.md` / `docs/WORKSPACE.md` / `.llm/data/workspace-map.edn` も確認する。既存 capability が見つかった場合は新規実装せず、該当 `interface.clj` 経由で利用する。生成物が無い、または drift している場合は対応する generator の再生成対象として扱う
3. **既存知識の確認**: KNOWLEDGE の関連節（対象ドメイン・境界契約・運用制約）を読む
¤ .llm/memory/KNOWLEDGE.md
4. **未決判断の確認**: QUESTIONS の `未対応(open)` / `議論中(in-discussion)` に関連する Q がないか確認する。関連 Q があれば、その解決を待つか、Q のコンテキストで作業する
¤ .llm/memory/QUESTIONS.md
5. **過去の決定の確認**: 関連する ADR があれば読む
¤ .llm/memory/adr/README.md
6. **上位文脈の確認（該当する場合のみ）**: 本リポジトリが上位プロジェクト・親 Issue・外部設計合意の下で動いているなら、その上位文脈で記録先・配置先・公開範囲が決まっていないか確認する。判定基準は次のいずれか：(a) README やプロジェクトトップに「このリポジトリは XXX の一部」のような記述がある、(b) 直近の対話・Issue で上位プロジェクトが明示されている、(c) ユーザが session 開始時に上位プロジェクトを言及した。いずれにも該当しないなら本項はスキップしてよい（空確認）。
   - **本項が無視されやすい構造的理由**: 本テンプレートの `.llm/memory/` 構造は強力で、LLM は「ここに書け」と引き寄せられる。能動的に上位を見に行かないと、上位合意を忘れて下位 KNOWLEDGE.md に書き出してしまう（実観察事例）。KNOWLEDGE/ADR の記録先 scope は §6 の協働プロトコルに従って明示する。
   ∵ .llm/guide/COLLABORATION_GUIDE.md §6

**確認深度 ladder**: 「どこまで読めば十分か」で迷わないよう、次の順で止める。前段で関連箇所が見つかればそこを読む。見つからなければ次段へ進み、最後まで見つからなければ空確認として通過する。

| 深度 | 何をするか | 十分条件 |
|---|---|---|
| L0 | `session-briefing.sh` の MODE と次に読む文書を確認 | 作業 mode と所有権が分かる |
| L1 | DESIGN / KNOWLEDGE / QUESTIONS / ADR の見出し・状態欄を scan | 対象ドメイン・未決 Q・関連 ADR の有無が分かる |
| L2 | タスク語彙・対象 namespace・機能名で `.llm/data/brick-map.edn` / `docs/BRICKS.md` / repo 全体を `rg` する | 既存 capability・担当 brick・関連節または「該当なし」を説明できる |
| L3 | 触るファイル周辺の ns/docstring/comment、`interface.clj`、近接 test を読む | コード内の局所規約と既存境界が分かる |
| L4 | README・直近対話・Issue 由来の上位文脈を確認 | 上位プロジェクトや外部合意の有無が分かる |

L3 は文書確認では代替できない。対象 namespace に docstring / comment / 近接 test がある場合、それも「関連文脈」である。コード内文脈を読まずに文書だけで十分と判断しない。

§8.0 の確認は「セッション 1 回だけ」ではなく、新しい bounded task に入るたびに差分確認として行う。ただし毎回全文を読み直さない。前回確認後に触る対象・モード・関連語彙が変わった時だけ、ladder の該当段を再実行する。context compaction / 長い中断 / 予期しない file change 通知 / 本書や `.llm/repo-context.edn` の変更を検知した時は、`session-briefing.sh`、`git status`、対象ファイルの再読で状態を再同期してから編集を続ける。

**「空確認」規約**: 上記 6 つの確認は、該当文書が空または該当事項なしの場合、**着手前確認は通過したものとみなす**（第 6 項は「上位文脈なし」を確認した場合を含む）。空を確認する行為自体が §1.3 の実装であり、スキップしてよい対象ではない。ただし、空であることを確認した後は次のステップに進む。

空確認の目的は「読む価値があるか」を毎回推測しないことにある。空であっても確認済みなら、以後の判断で「見落としたかもしれない」という再確認を避けられる。各文書の運用手順は、この §8.0 から呼び出されるプロセスを定義する。
∵ .llm/memory/KNOWLEDGE.md
∵ .llm/memory/QUESTIONS.md
∵ .llm/memory/adr/README.md

仕様・知識・未決に**実装判断へ影響する曖昧さ・矛盾・欠落**を発見したら、`.llm/memory/QUESTIONS.md` に Q を立てて**自己解釈で進めない**。誤字、言い回し、実装判断に影響しない表現揺れは Q ではなく通常のドキュメント改善候補として扱う。
¤ .llm/memory/QUESTIONS.md

**仕様曖昧性の点検項目**（用語定義・例外条件・数値基準・境界条件・受入基準整合・KNOWLEDGE との矛盾）と**質問の出し方**は別紙に一元化されている。
∵ .llm/guide/COLLABORATION_GUIDE.md §4

### 8.0.0 ターン内で閉じる検証フィードバック

LLM のフィードバックループは**編集単位でターン内に閉じる**。監視型（watch）や非同期通知には依存しない（別プロセスの出力を LLM は読めない）。

**サイクル**:

1. 編集（brick のコード、deps.edn、interface、テスト等）
2. 下表で検証粒度を決める
3. ターン内で検証を実行する
4. 結果を読む
5. 失敗があれば下記の振り分け判断に従って対処

**検証粒度の判定**:

| 編集・作業の性格 | 最初に行う検証 | 補足 |
|---|---|---|
| 調査・文書確認のみでファイル編集なし | §8.0 の確認のみ | 実行可能な検証がなければ不要 |
| typo、コメント、純粋関数、テストのみ | `clj -M:poly test` または対象テスト | 回帰確認として十分。REPL 稼働中なら eval 併用可 |
| `interface.clj` / `m/=>` 契約 / 外部入力 schema の変更 | REPL eval 必須 → `clj -M:poly test` | `poly test` は回帰確認。契約検証は instrumentation 下で別途行う |
| runtime wiring、publisher、defrecord / protocol、ns graph 変更 | REPL eval 必須。必要に応じて `(safe-reset!)` / `(hard-reset!)` | 起動中 JVM の状態に依存するため |
| deps.edn、brick 構造、workspace.edn 変更 | `clj -M:poly check` + 依存解決確認 + fresh JVM 判断 | REPL の既存 classpath では確認不足 |
| `brick.edn`、公開 API、capability 変更 | `./.llm/scripts/check-workspace-integrity.sh` | `docs/BRICKS.md` / `.llm/data/brick-map.edn` の drift、重複 capability、base の未提供 capability 参照を検出 |
| `project.edn`、project deps、workspace project 構成変更 | `./.llm/scripts/check-workspace-integrity.sh` | `docs/PROJECTS.md` / `docs/WORKSPACE.md` / `.llm/data/workspace-map.edn` の drift、entrypoint/includes/deps 整合を検出 |
| 完了報告前 | §5.5 完了条件 | `poly test :all` で全体検証 |

文書のみのテンプレ保守では REPL eval は不要である。Markdown 参照・archive staging・mode boundary など、変更対象に対応する script gate を優先する。`.llm/scripts/*.clj` / `.llm/scripts/*.sh` を触った場合は総合検査と該当 script の単体実行を行う。Clojure/Polylith の構造や runtime code を触った場合だけ、REPL と `poly` gate を通常どおり要求する。

`poly test` は stable タグからの diff で**影響範囲を自動判定**するため、LLM が毎回「どこまで走らせるか」を考える必要はない。stable タグの作成と更新は初期化完了時または CI 運用で決める。タグが未整備なら `poly test :all` に倒す。
この高速性は回帰確認の粒度に関する話であり、契約検証完了とは別問題である。

人間 smoke を伴う検証（不可逆 / 外部 state を含む場合の判定、事後 state の記述規律）は別途扱う。
∵ .llm/guide/COLLABORATION_GUIDE.md §7.9

Malli `:closed true` map の read-side 未定義キーアクセスは静的検出されない（`m/=>` の盲点）。read-site の局所化規律と各枝の検証規律を別途扱う。
∵ .llm/guide/CODING_GUIDE.md §1.15

#### REPL eval 必須トリガ（mandatory）

編集内容が以下のいずれかに該当したら、**`poly test` より先に** REPL eval で確認する。nREPL 未起動ならユーザに `clj -M:dev:nrepl` 起動を 1 度依頼し、待機だけでターンを空費せず、並行可能な静的確認・文書確認・テスト準備へ進む。ただし REPL 必須箇所の完了報告は、起動後の eval が終わるまで保留する（§9.0 共有モデル）:

| トリガ | 代表的な eval |
|---|---|
| ライフサイクル定義の追加・変更 | `(safe-reset!)` → system map がある場合は対象 key を確認 |
| tools.namespace refresh を伴う ns 再構成 | `(safe-reset!)` → `(myapp.new-ns/some-fn)` |
| `m/=>` 契約の新規付与・変更 | 対象関数を正・不正引数で呼び contract violation を観察 |
| ログ publisher / event 名の変更 | 小さな probe event で publisher 経路を確認 |
| 外部 API / DB レスポンスの map 形状を使う処理 | `(probe (fetch-row db id))` で tap> + 値保持 |
| 例外発生箇所の binding を観察するデバッグ | 導入済みの trace helper があれば使い、なければ通常 eval で再現 |
| defrecord shape / protocol method 変更 | `(hard-reset!)` 後に対象関数評価 |

`m/=>` 契約の「変更」には、schema の厳格化だけでなく緩和、戻り値 schema の変更、`[:map {:closed true}]` の key 追加・削除、外部入力 schema の read-side 前提変更を含む。境界例で迷ったら REPL 必須側に倒す。

**検出された失敗の振り分け**:

| 失敗の性格 | 対処 |
|---|---|
| 自分の編集が原因で原因が明確（typo、契約変更の波及漏れ等） | ターン内で修正（記録不要） |
| 起動中 system state / 関数挙動の確認が必要 | REPL eval で即検証、再現したら修正（REPL eval 必須トリガ該当時は必須） |
| 修正方針に判断が必要（契約変更 vs 実装変更、影響範囲の広さ等） | `.llm/memory/QUESTIONS.md` に Q を起票 |
| 将来の同種問題防止に価値ある知見 | `.llm/memory/KNOWLEDGE.md` への追記案をユーザに提示 |
| 設計判断に関わる（新規原則導入、既存原則変更等） | ADR 発行を提案、または承認済判断なら ADR 化 |
| 3 回試みても解決しない / 予想を超えて範囲が広がる | §7 自己停止プロトコル |

この振り分けに載らない「タスク」概念は本テンプレートには存在しない。作業中の全事象は既存の受け皿（QUESTIONS.md / KNOWLEDGE.md / ADR / §7 自己停止）に流す。

### 8.1 既存コンポーネントへの機能追加

1. §8.0 の確認を実施（REPL 稼働中なら `./.llm/scripts/repl-eval.sh --expr '(dev.user/status)'` で環境把握）
2. 対象の `interface.clj` に追加する公開関数のシグネチャと Malli スキーマを設計し、**ユーザに提示・確認**。承認待ちの間に該当 interface の実装へ進まない
3. `core.clj` に実装（`m/=>` 契約は置かない）
4. `interface.clj` に委譲 + `m/=>` 契約付与（境界契約の集約）
5. **REPL eval で境界挙動確認**（§8.0.0 REPL eval 必須トリガ該当時は必須、§9 Live Diagnosis Loop）:
   - 編集ファイルを即反映: `./.llm/scripts/repl-eval.sh --load-file components/<c>/src/poly/<c>/interface.clj`
   - 対象関数を評価: `./.llm/scripts/repl-eval.sh --ns poly.<c>.interface --expr '(<new-fn> <sample-args>)'`
   - `m/=>` 契約違反は instrumentation で即例外化して観察、flow-storm 導入時は `(fs-record-ns! 'poly.<c>.core)` で trace も取れる
6. `test/.../interface_test.clj` にテストとして REPL で見た挙動を反映（観察した値を正常系・境界値・契約違反・不変条件に整理し、単体 + 必要ならプロパティテストへ落とす）
7. `clj -M:poly check` → `clj -M:poly test`（REPL で動いても CLI gate は必ず通す）
8. **実装中に発見した契約・不変条件・暗黙知**があれば、ユーザに提示して KNOWLEDGE.md への追加を提案（詳細は `KNOWLEDGE.md` §0）

### 8.2 新規コンポーネント追加（承認必須）

```bash
clj -M:poly create component name:<name>
```

**重要**: `poly create` は brick ディレクトリしか作らない。ルート `deps.edn` の `:dev :extra-paths` / `:extra-deps` とワークスペース構成の追従は手動で行う。作業後は完了条件（§5.5）の `./.llm/scripts/check-workspace-integrity.sh` が登録漏れを検知する。
¤ .llm/guide/BOOTSTRAP_GUIDE.md §2.5

雛形は別紙のコード例を使う。ライフサイクル定義を提供する場合は entry base の `system.clj` に集約する。
∵ .llm/guide/POLYLITH_GUIDE.md §2

### 8.3 コミット

- 論理単位ごとに細かく
- **目安: 1 コミット = 1 つの意図**（1 関数追加、1 バグ修正、1 リファクタリング等）。実装と対応テストは通常同じコミットに含める
- 同じ意図に従属する typo・参照修正・局所整形は同一コミットに含めてよい。別 phase の判断、別機能、偶然見つけた無関係修正は分ける
- メッセージ：現在形・命令形を推奨（"Add user/create function"）。日本語でもよいが、何をしたかが現在形で一意に読めること
- ロジック変更とフォーマット変更は原則別コミット。ただし触ったファイル内で cljfmt が生んだ局所整形は同一コミット可。広範囲の機械整形は別コミット
- WIP / テスト失敗をコミットしない

### 8.4 継続的な保守（依存更新、ライブラリ差替）

プロジェクト継続運用時の依存更新、ライブラリ差替、バージョンアップの手順は保守者向け文書にまとめる。**派生プロジェクトでも同じ手順が適用できる**。`clj -M:outdated` による更新候補確認、セキュリティパッチの即時適用、メジャーアップ時の CI 通過確認、API 破壊的変更時の ADR 発行などは、テンプレート保守と派生プロジェクト保守で共通する。
¤ .llm/guide/MAINTAINERS_GUIDE.md §5.1

派生プロジェクトでの適用上の違い：

- **更新対象の範囲**: 必須技術基盤（ルート deps.edn）+ 自プロジェクトの brick deps.edn（判断済み推奨集自体は更新しない。逸脱は ADR で記録）
- **判断済み推奨集との整合**: 更新で推奨バージョンと乖離する場合、ADR 発行 + DESIGN.md §8.3 に記録
- **新ライブラリ採用**: CLAUDE.md §2 禁止事項（brick deps.edn 変更は人間専権 (L0)）、承認後に MAINTAINERS_GUIDE.md §5.2 手順適用

### 8.5 仕様変更・追加への対処

実装中または実装後に仕様変更・追加が生じた場合の基本フロー：

1. **変更内容の合意**: ユーザと変更方針を合意（権限は承認必須 (L1)、LLM 独断禁止。LLM 起因の発見の場合は QUESTIONS に Q を起票してユーザ判断を仰ぐ）
¤ .llm/memory/QUESTIONS.md
2. **規模に応じた ADR 発行**:
   - 人間専権相当（L0、目的・スコープ・主要ユースケースの根本改訂）→ ADR 必須
   - 承認必須相当（L1、ユースケース追加・受入基準改訂）→ ADR 推奨
   - 実施後報告/独断可相当（L2/L3、記述の明確化、誤字修正）→ ADR 不要
3. **DESIGN.md の書き換え**: 該当節を**現在形で新仕様に書き換える**（差分表示・追記形式にしない、過去の記述は残さない）
4. **関連文書の更新**:
   - KNOWLEDGE: 新仕様と整合しないエントリを上書き or 廃止
   ∵ .llm/memory/KNOWLEDGE.md §0.5
   - QUESTIONS: 関連する未対応(open) Q を解決済み(resolved) に遷移して事後報告、または新規 Q を起票（解決根拠が曖昧な場合のみ確認を求める）
   ∵ .llm/memory/QUESTIONS.md
5. **実装反映**: §8.1〜§8.2 の作業プロトコル適用
6. **コミット**: ADR 番号をメッセージに含める（例: `Revise DESIGN.md §3 for streaming inference (ADR-0012)`）

**DESIGN は「現在の仕様」の一次情報源**であり、変更履歴は ADR・git・QUESTIONS アーカイブで保全される（分類管理の原則による 4 種文書分離）。DESIGN 内に差分表記・追記・変更履歴を残さない（「文書の自己整合性」、疲労最小化原則）。同じ原則は KNOWLEDGE にも適用される。

詳細:
- 権限階層: COLLABORATION_GUIDE
∵ .llm/guide/COLLABORATION_GUIDE.md §2.2
- ADR 運用
∵ .llm/memory/adr/README.md
- KNOWLEDGE 更新
∵ .llm/memory/KNOWLEDGE.md §0.5
- QUESTIONS 運用
∵ .llm/memory/QUESTIONS.md

---

## 9. REPL 駆動開発（Primary Workbench、§1.2.2 ループ短縮の実装）

**本節は REPL を「検証手段の一つ」ではなく「primary 開発面」として位置づける**。CLI 検査（`poly check` / `poly test` / `lint`）は最終 gate であり、編集直後の動作確認・契約違反再現・state inspection は REPL で行う。

### 9.0.0 REPL 利用の最上位優先規則

**nREPL が起動できる状態（`.nrepl-port` が存在する、または起動依頼で起動可能）なら、関数挙動・state・契約違反の動作確認は必ず `./.llm/scripts/repl-eval.sh` 経由で行う**。これは §1.2.2 ループ短縮の中核実装であり、**LLM が能動的に閉じやすい代替手段（stateless スクリプト）を構造的に塞ぐための最上位規則**。

**禁止される代替手段**:

- `/tmp/*.clj` / `scripts/diag-*.clj` のような ad-hoc スクリプトを `clj -M` で起動して検証する。stateless スクリプトはユーザの REPL system state（ライフサイクル起動状況・namespace reload 履歴・Malli instrumentation 有効性）を反映できず、ユーザ環境と乖離した結果を生む（実観察事例：数ターンに渡る検証空転）
- `comment` フォームへの evaluation で満足する（§9.3 既出）

**極めて限定的な例外**:

- nREPL を起動できない環境（CI 等）で純粋関数の静的検証のみ行う場合
- REPL 接続を意図的に分離して再現性を確保する必要がある場合（ADR で根拠記録必須）

nREPL 未起動なら 1 度だけ起動依頼（§9.0）。起動待ちの間は、REPL を必要としない作業へ進んでよい。起動済みなら無条件に `repl-eval.sh` を使う。「念のためスクリプトで」「軽い確認だからスクリプトで」は本規則違反。

LLM がこの規則を見落としやすいのは「stateless の方が安全」という偏向（道具選択バイアス）が働くため。本規則は肯定形（REPL を使え）と否定形（スクリプトを書くな）を併記して、その偏向を構造的に閉じる。

### 9.0 共有モデル

- 人間が 1 つの `clj -M:dev:nrepl` を起動（long-lived JVM）
- CIDER / Calva / Cursive **と** LLM が**同じ nREPL に attach**する。LLM 側の attach は `./.llm/scripts/repl-eval.sh` が `.nrepl-port` を読み、nREPL eval op を送ることで実現する
- どのクライアントも session を独占しない。LLM は `--fresh` / `--reset-session` で session 分離可能だが、既定は人間と共有
- port は nREPL が `.nrepl-port` に自動書き出し、session は `.nrepl-session` に永続化
- LLM は `./.llm/scripts/repl-eval.sh` 経由で eval する。直接 `clj` を叩かない（shell quoting・port 発見の複雑化回避）
- `repl-eval.sh` は TCP 接続成功だけを信用しない。接続後に workspace root と `dev.user/status` の capability shape を検証し、別 workspace / 古い JVM / invalid workbench なら fatal で停止する。`NREPL_PORT` と `.nrepl-port` が食い違う場合も、古い環境変数による wrong JVM 接続を避けるため停止する

### 9.1 `dev.user` — capability surface（LLM と人間の共通 API）

`development/src/dev/user.clj` で以下を提供する。
配布時点では必須 helper のみが有効で、`integrant` / `FlowStorm` / `Portal` は
任意セクションとしてコメントアウトされている状態。
利用する場合は `development/src/dev/user.clj` の対応セクションを有効化し、
依存追加が必要なものは `deps.edn` 側で追加する。

常時使用可能:

```clojure
(status)              ; 最初に呼ぶ: capability / instrumentation / refresh-dirs / current-ns
(malli-on!)           ; Malli instrumentation ON
(probe x)             ; tap> + 値をそのまま返す diagnosis primitive（println の代わり）
(safe-reset!)         ; lifecycle helper or tools.namespace refresh を try/catch で包む
(hard-reset!)         ; stale-state recovery: halt → refresh-all → restart
```

プロジェクトで `integrant` を有効化した場合のみ使用可能:

```clojure
(go) (reset) (halt) (system)
```

LLM 向け trace helper として、FlowStorm 導入時のみ使用可能:
∵ .llm/guide/optional/FLOWSTORM_DEBUGGING.md

```clojure
(fs-start!)           ; trace helper 導入時のみ有効
(fs-record-ns! 'ns)   ; trace helper 導入時のみ有効
(fs-clear!)           ; trace helper 導入時のみ有効
```

人間向け GUI helper として、Portal 導入時のみ使用可能:

```clojure
(portal-open!) (portal-clear!) (portal-close!)
```

`(status)` は `:capabilities` に有効な helper 群と有効化状態を返す。文書より先に REPL で「今何が使えるか」を確認する。

有効化の見方:

| helper 群 | 有効化トリガー | 確認方法 |
|---|---|---|
| 常時使用可能 helper | 配布時点で有効 | `(status)` の `:capabilities :always` |
| lifecycle helper | 対応セクションを有効化し、必要依存を追加 | `(status)` の `:capabilities :lifecycle` |
| trace helper | FlowStorm 依存を追加し、利用可能状態にする | `(status)` の `:capabilities :trace` |
| GUI helper | Portal 依存を追加し、対応セクションを有効化 | `(status)` の `:capabilities :gui` |

`development/src/dev/user.clj` の docstring も同じ 4 区分で読む。本文と docstring で分類がずれた場合は、`(status)` の出力を優先して現在状態を判定する。

### 9.2 LLM Live Diagnosis Loop（必須）

**LLM は編集の前後で以下の分岐を回す**。ユーザからの指示を待たない。

| 状況 | 手順 |
|---|---|
| REPL 接続の初回確認 | `./.llm/scripts/session-briefing.sh` の REPL 状態節を読む。未起動なら 1 度だけ `clj -M:dev:nrepl` 起動をユーザに依頼。起動済みなら `./.llm/scripts/repl-eval.sh --expr '(dev.user/status)'` |
| 通常の関数・契約確認 | 必要なら `(malli-on!)` → `--load-file <path>` で ns の読み込み、または `--ns <ns> --expr '(<fn> <args>)'` で eval。`--load-file` は `--ns` を伴っても ns は切り替わらない（実装上は無視） |
| runtime wiring 変更 | `(safe-reset!)` で refresh / reload。system map がある場合は対象 key を確認し、必要なら次段で `hard-reset!` を使う |
| 値の形状探索 | `(probe x)` を使う。raw `println` は使わない |
| state / 時系列 / 制御フローのバグ | 導入済みの trace helper があれば優先して使う。未導入なら通常 eval に戻る |
| 人間が GUI で値を観察したい | 導入済みの GUI helper があれば `(probe x)` と組み合わせて使う。LLM はこれを標準経路にしない |
| refresh が壊れた感触 | `(hard-reset!)`。stale class / 半 reload 復旧を目的にする |
| live 挙動を確認済み | REPL 結果を `interface_test.clj` 等へテスト化し、CLI gate（`poly check` / `poly test` / `lint`）へ進む |

REPL で得た値をテストへ反映するとは、観察した具体値をそのまま固定するだけではない。正常系・境界値・契約違反・不変条件のどれを確認したのかを切り分け、再現可能な `clojure.test` または property test に落とすことを指す。

### 9.3 やってはいけない事

- **テスト代替禁止**: REPL で動いたら即 `interface_test.clj` に反映（§10）
- `comment` フォーム満足禁止。REPL で観察した挙動はテストへ反映し、CLI gate へ進む（§9.2）
- **stateless 診断スクリプト禁止**: nREPL 起動可な状況で `/tmp/*.clj` 等を作って `clj -M` で検証しない（§9.0.0 参照）
- 副作用反復（DB insert 等）は冪等性確認後に限る
- **ターン跨ぎ state 依存禁止**: session 消滅前提で、結果は必ずコード化
- **`(require ... :reload)` の常用禁止**: ns graph 変更は `(safe-reset!)`（tools.namespace を内包）、`:reload` は単一 namespace の仮説確認に限る。確認後は `safe-reset!` またはテストで再検証する
- **defrecord shape / protocol method 変更時**: `(hard-reset!)` または fresh JVM。stale class instance による沈黙バグを避ける
- 多エージェント並走時は `--fresh` か `--reset-session` で session 汚染を回避
- bounded output（目安 10000 chars/response）を超える探索は `(take 50 ...)` / `(keys m)` で絞る。repl-eval.sh も同じ目安で切るが、切られる前に LLM が読み切れる粒度へ狭める

### 9.4 Reload 規律（stale state を避ける表）

**`(safe-reset!)` を universal helper として使う**（lifecycle helper 採用時は `integrant.repl/reset`、非採用時は `tn/refresh`）。直接 `(reset)` を呼ぶのは lifecycle helper を有効化した場合のみ利用可能。

| 変更種別 | 正しい対処 |
|---|---|
| 関数追加・変更（純粋。I/O・global state・class shape に触れない） | 通常は `--load-file`。refresh が必要な場合だけ `(safe-reset!)` |
| ns 追加・削除・rename | `(safe-reset!)`（tools.namespace が graph を解決） |
| 依存追加（deps.edn 変更） | fresh JVM 再起動（ClassLoader 再構築必要）|
| ライフサイクル定義の変更 | `(safe-reset!)` で反映 |
| defrecord shape / protocol method 追加・削除・シグネチャ変更 | `(hard-reset!)` or fresh JVM |
| multimethod 再定義 | `(hard-reset!)` 推奨（stale dispatch 回避） |
| `m/=>` 契約追加・変更 | `--load-file` で reload、その後 `(malli-on!)` 再実行 |

迷う場合は `(safe-reset!)` から始める。`(hard-reset!)` は、defrecord / protocol / multimethod / dependency classpath のように stale class・半 reload が疑われる場合、または `(safe-reset!)` が失敗した場合に上げる。最初から hard reset するのは、shape 変更が明確な時に限る。

### 9.5 repl-eval.sh リファレンス

```bash
./.llm/scripts/repl-eval.sh --expr '(+ 1 2)'                      # eval
./.llm/scripts/repl-eval.sh --load-file path/to/file.clj          # load-file (file/line metadata 付き)
./.llm/scripts/repl-eval.sh --ns poly.user.interface --expr '(...)' # ns 指定 eval
echo '(+ 1 2)' | ./.llm/scripts/repl-eval.sh                       # stdin fallback
./.llm/scripts/repl-eval.sh --interrupt                           # 直近 eval を中断
./.llm/scripts/repl-eval.sh --describe                            # server ops / versions
./.llm/scripts/repl-eval.sh --reset-session                       # 永続 session 破棄
./.llm/scripts/repl-eval.sh --fresh --expr '(pure-check)'          # ephemeral session
```

Exit codes: `0` 成功 / `1` eval-error・namespace-not-found・:ex / `2` 接続・必須 op 欠落・引数不正 / `130` interrupted。

接続時の防衛線: `repl-eval.sh` は `.nrepl-port` / `NREPL_PORT` から得た port へ接続した後、`describe` の required ops、JVM の `user.dir`、`dev.user/status` の常時 capability を検証する。TCP が通っても identity check に失敗した場合は exit `2` で止まる。これは stale port でも古い JVM が listen している事故を検出するためであり、`--fresh` / `--reset-session` で回復しようとせず、表示された port / workspace / workbench 不一致を解消する。

**越境ユースケースの開発フィードバック規約**: 派生プロジェクトで複数 entity / 複数 entrypoint をまたぐ処理を扱う場合、`(safe-reset!) → (seed-all!)` の 2 行で境界状態を立ち上げられる状態を維持する。詳細は POLYLITH_GUIDE.md §7.4。
∵ .llm/guide/POLYLITH_GUIDE.md §7.4

---

## 10. テスト戦略（§1.1.1 全域性の動的検証）

| 層 | ツール | 配置 |
|---|---|---|
| インターフェーステスト | `clojure.test` + `matcher-combinators` | `components/<name>/test/.../interface_test.clj` |
| プロパティテスト | `test.check` + `malli.generator` | 同上 |
| base 統合テスト | `clojure.test` + テストコンテナ | `bases/<name>/test/` |

- **テストは原則 interface 経由で書く**（実装変更に頑健）
- モックは §1.1.3 副作用隔離の失敗サイン。**依存注入で回避**
- **検証はターン内で同期的に閉じる**（§8.0.0）。監視モード（watch）には依存しない。LLM は編集のたびに自分で `poly test` を走らせ、結果を読む

### 10.1 越境ユースケースの検証規律

複数 entity / 複数 entrypoint をまたぐ処理（越境ユースケース）は、テスト戦略上、**3 つの規律**を伴う。3 規律は POLYLITH_GUIDE.md の「越境ユースケースの機械化（上位原理）」の派生 2 / 派生 3 / 派生間の運用順序に対応する。
∵ .llm/guide/POLYLITH_GUIDE.md

- **共有 state は seed helper / fixture で機械化する**: テスト・smoke 検証で共有 state を作る時、人間手順（README に書かれた起動コマンド列）に逃がさず、`dev.fixtures` の `(seed-<uc>!)` / `(seed-all!)` に集約する。詳細は POLYLITH_GUIDE.md §7.4
- **越境 tx を持つ orchestration には原子性を主張する境界テストを置く**: 「複数 entity が同一 tx に属する」ことを test で assert する。検証手段は採用 DB に依存する（next.jdbc / XTDB / Datomic 等）。テンプレートは「assert する」規律のみを規約化し、手段は派生プロジェクトの DB 選択に委ねる。DB 別の典型実装は POLYLITH_GUIDE.md の上位原理節（派生 3）
- **test の precondition を明示する**: 各 test がどの `seed-<uc>!` を前提とするかを test docstring または直前コメントで明示する。「seed-all! 済前提」のような抽象表現は禁止し、必要な seed helper を個別列挙する。fixture を REPL で観察してから test の precondition を確定する規律は POLYLITH_GUIDE.md §7.4.1

---

## 11. プロジェクト記憶の運用

プロジェクトに関わる情報は、**分類管理の原則に基づき 4 種類の文書に分離**される。役割が対照的なので混同しない。
∵ .llm/guide/MAINTAINERS_GUIDE.md §4

本文書では日常判断に必要な短縮定義だけを置く。分類管理の原則の本定義は保守者向け文書に置き、各文書の運用手順は各 §0 を正本とする。
∵ .llm/guide/MAINTAINERS_GUIDE.md §4

### 11.1 4 種類の文書と参照先

| 種別 | 配置 | 書くもの | 更新方式 | 詳細運用の参照先 |
|---|---|---|---|---|
| **仕様（DESIGN）** | `DESIGN.md` | 何を作るか、合意済みの目的・スコープ・受入基準 | 現在形で上書き。履歴は残さない | `DESIGN.md` §0 |
| **現時点で有効な知識（KNOWLEDGE）** | `.llm/memory/KNOWLEDGE.md` | 現時点の契約・不変条件・暗黙知 | 上書き更新、常に最新 | `KNOWLEDGE.md` §0 |
| **決定履歴（ADR）** | `.llm/memory/adr/NNNN-topic.md` | なぜそう決めたか、却下した代替案 | 発行後不変。改訂は新 ADR | `.llm/memory/adr/README.md` |
| **判断保留（QUESTIONS）** | `.llm/memory/QUESTIONS.md` | 未決の判断、自己停止 D の受け皿 | 未対応(open) → 解決済み(resolved) / 却下(wontfix) / 統合済み(superseded) | `QUESTIONS.md` §0 |

編集権限・協働プロトコルは別紙に一元化されている。**Q を立てるべき場面の一覧**も別紙に置く。LLM の仕様書開発者としての複数役割は §11.3。
∵ .llm/guide/COLLABORATION_GUIDE.md
∵ .llm/memory/QUESTIONS.md §1

分類に迷う場合は、先に QUESTIONS に Q を立てる。分類自体を自己解釈しない。
¤ .llm/memory/QUESTIONS.md

**TEMPLATE mode の読み替え**: `.llm/repo-context.edn :repo-kind :template` で作業している時、テンプレート自身の保守決定は ADR にしない。テンプレ保守の議論・却下案・吸収先は maintainer archive staging に置き、現行ルールへ吸収後に削除または圧縮する。派生プロジェクトの ADR 規律をテンプレート本体へ持ち込まない。規約変更を過去文書へどこまで遡及するかは MAINTAINERS_GUIDE の遡及適用規則に従う。
∵ .llm/guide/MAINTAINERS_GUIDE.md §7

### 11.2 サイクル全体図（羅針盤）

4 種文書は単独ではなく連動して動く。以下がそのサイクル。作業中は常にこれを羅針盤として参照する：

```mermaid
flowchart TD
  IMPL[実装中の発見・判断の必要] --> Q_NEW[QUESTIONS.md に Q 起票<br>状態=未対応(open)]
  SPEC_AMBIG[DESIGN.md の曖昧性発見] --> Q_NEW
  STOP[自己停止プロトコル §7.3 D] --> Q_NEW

  Q_NEW --> DISCUSS[ユーザと議論<br>状態=議論中(in-discussion)]
  DISCUSS --> DECIDE{判断確定}

  DECIDE -->|却下| WONTFIX[却下(wontfix) として記録]
  DECIDE -->|統合| SUPERSEDED[統合済み(superseded) として他 Q に集約]
  DECIDE -->|採用| PROMOTE{反映先判定}

  PROMOTE -->|現時点の契約・不変条件・暗黙知| K_ADD[KNOWLEDGE.md に追記<br>上書き更新]
  PROMOTE -->|なぜそう決めたかの不変記録| A_NEW[adr/NNNN-topic.md 新規発行<br>状態=accepted]
  PROMOTE -->|プロダクト仕様への影響| D_UPDATE[DESIGN.md 改訂提案]
  PROMOTE -->|一度きりの判断| NONE[追加反映なし<br>アーカイブのみ]

  K_ADD --> ARCHIVE[QUESTIONS.md §3 に 解決済み(resolved) として移動]
  A_NEW --> ARCHIVE
  D_UPDATE --> ARCHIVE
  NONE --> ARCHIVE
  WONTFIX --> ARCHIVE
  SUPERSEDED --> ARCHIVE

  K_ADD -.->|方針・採否・逸脱理由を伴う時| A_NEW

  ARCHIVE --> END[記録保全完了]
```

**具体シナリオ 3 例**：

- **シナリオ A**（新しい不変条件発見 → KNOWLEDGE 直接追加）: 実装中に明白な契約を発見、Q 不要、ユーザ承認の上 KNOWLEDGE 追加
- **シナリオ B**（境界判断 → Q 経由で ADR 発行）: コンポーネント境界の議論を Q として起票、採用案を ADR として記録
- **シナリオ C**（技術選定 → Q 経由で ADR + KNOWLEDGE 両方）: 決定経緯は ADR（不変）、運用規約は KNOWLEDGE（上書き更新）

各シナリオの詳細手順と反映先判定の基準は別紙に置く。
∵ .llm/guide/COLLABORATION_GUIDE.md §6
∵ .llm/memory/QUESTIONS.md §0.5

**KNOWLEDGE と ADR を同時に更新する基準**:

- 局所的な契約・不変条件・運用知識の追記だけなら KNOWLEDGE のみ
- 技術採用、推奨からの逸脱、境界変更、恒久運用ルール変更など**「なぜそうしたか」**を将来辿る必要があるなら ADR も発行
- KNOWLEDGE の本文が変わるだけでなく、判断根拠や採否理由も更新される場合は ADR を伴わせる

### 11.3 LLM の仕様書開発者としての役割

本テンプレートは「Clojure コードを書く LLM」のための指示書であると同時に、**仕様書を LLM と人間が対話的に構築・成熟させるフレームワーク**でもある。LLM は以下の複数の役割を並行して担う：

| 役割 | 責務 |
|---|---|
| **実装者** | Clojure コードを書く |
| **仕様提案者** | DESIGN.md の曖昧性を指摘、明示化を提案 |
| **知識記録者** | 実装中の発見を KNOWLEDGE に記録提案 |
| **決定履歴保全者** | 重要判断の ADR 発行提案 |
| **未決判断管理者** | 自己解釈できない判断を Q として起票 |

**LLM は受け身で実装するだけではない**。ただし編集権限は限定され、4 種文書の編集・状態変更は**文書種別と操作種別ごとに異なる**。本節では再定義しない。協働の詳細プロトコルと、Q を立てるべき場面は別紙に置く。
∵ .llm/guide/COLLABORATION_GUIDE.md §2.3
∵ .llm/guide/COLLABORATION_GUIDE.md
∵ .llm/memory/QUESTIONS.md §1
