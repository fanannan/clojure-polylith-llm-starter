# SPEC_GUIDE.md — 仕様翻案ガイド

本文書は、自由な着想を仕様正本・中間表現・Polylith 境界・テスト義務へ変換する規律を定める。
扱う範囲は IDEA / DESIGN / design-ir / reconciliation / test obligation に限る。

| 項目 | 内容 |
|---|---|
| **対象** | IDEA から DESIGN への翻案、DESIGN 更新時の impact analysis |
| **使うタイミング** | 初期化時、仕様変更時、自由記載から実装可能な仕様へ変換する時 |
| **正本性** | 仕様翻案プロセスの正本 |
| **扱わないもの** | Clojure 実装規約、Polylith 詳細手順、ライブラリ選定、一般的なプロダクト論 |

関連する詳細は別紙を正本とする。
¤ CODING_GUIDE.md
¤ POLYLITH_GUIDE.md
¤ STACK_GUIDE.md
¤ COLLABORATION_GUIDE.md

---

## 1. 目的

人間は最初から完成した仕様を書かない。LLM は自由記載をそのまま実装に使わず、仕様として必要な構造へ翻案する。

本ガイドの目的は次の 3 つである。

1. IDEA の各記述を、DESIGN / QUESTIONS / KNOWLEDGE / ADR / 破棄へ透明に振り分ける
2. DESIGN から design-ir、capability plan、test obligation へつながる形に整える
3. LLM の意味判断と script の機械検査の境界を固定する

---

## 2. 入力の扱い

IDEA は任意入力である。存在しない場合、または雛形だけの場合はスキップする。DESIGN は常に仕様正本であり、実装判断は DESIGN を優先する。

| 入力状態 | 扱い |
|---|---|
| IDEA が存在しない | DESIGN から開始 |
| IDEA が雛形だけ | 着想入力なしとして扱う |
| IDEA に実内容がある | reconciliation table と DESIGN 反映案を提示 |
| IDEA と DESIGN が食い違う | DESIGN を優先し、意図不明な差分だけ質問化 |

---

## 3. IDEA の分解単位

LLM は IDEA を文単位で機械的に切らない。意味のまとまりごとに atom へ分解する。

| atom 種別 | DESIGN 反映先の目安 | 例 |
|---|---|---|
| 目的・解決したい問題 | §1 目的 | 月次作業を半減したい |
| 作るもの | §2.1 スコープ | 請求書を発行する |
| 作らないもの | §2.2 スコープ外 | 多通貨対応はしない |
| 利用者行動 | §3 主要ユースケース | 経理担当者が締め処理を行う |
| 完成条件 | §4 受入基準 | 1000 件を 10 秒以内に処理 |
| 業務制約 | §6 / §9 | 承認済み請求書は変更不可 |
| 外部連携 | §7 外部インターフェース | 会計システムへ CSV 出力 |
| 技術・配布 | §8 / §9 | CLI として配布 |
| 将来案 | §10 将来計画 | 英語対応は将来 |
| 未決事項 | QUESTIONS | 高速の具体値が不明 |
| 確定済みの継続ルール | KNOWLEDGE | 本番ログに PII を出さない |
| 重要な判断理由 | ADR | DB ではなくファイル保存を採用 |

atom が複数の分類にまたがる場合は、1 つへ押し込めず分割する。

---

## 4. Reconciliation

reconciliation は IDEA から DESIGN への翻案を人間が確認するための一時レビュー表である。正本ではなく、保存対象ではない。

LLM は IDEA に実内容がある時、DESIGN 更新案の前に次の表を提示する。

| ID | IDEA の記述 | 扱い | 反映先 | 理由 | 次アクション |
|---|---|---|---|---|---|
| R1 | PDF 出力したい | 反映 | DESIGN §3 / §4 | 主要ユースケース | DESIGN 更新 |
| R2 | 将来は英語対応 | 将来案 | DESIGN §10 | 現スコープ外 | DESIGN 更新 |
| R3 | 高速にしたい | 質問 | QUESTIONS | 数値基準不明 | Q 起票 |
| R4 | DB は不要 | 判断必要 | QUESTIONS / ADR 候補 | 永続化方式に影響 | 人間確認 |

管理規則:

- reconciliation は commit しない
- ID は `R1`, `R2` のような一時 ID とし、永続 ID にしない
- 承認後、各行は DESIGN / QUESTIONS / KNOWLEDGE / ADR のいずれかへ吸収する
- 吸収先のない行は破棄する
- 未吸収のまま実装へ進まない
- 却下理由を将来辿る価値がある場合だけ ADR にする

---

## 5. 仕様駆動のチェックポイント

LLM は要求ごとに、次の観点を確認する。すべてを DESIGN に長文で書く必要はないが、実装判断に必要な欠落があれば質問する。

| 観点 | 確認すること | 欠落時の扱い |
|---|---|---|
| Actor | 誰が使うか | 主要 UC の actor を質問 |
| Goal | 何を達成したいか | 目的または UC を質問 |
| Trigger | いつ・何を契機に起きるか | UC の前提を質問 |
| Success | 成功状態は何か | 受入基準へ落とす |
| Failure | 失敗・例外は何か | 例外条件を質問 |
| Rule | 業務不変条件は何か | 制約として分離 |
| Example | 具体例・境界値はあるか | Example を求める |
| Verification | 機械検証できるか | test obligation へ変換 |
| Boundary | pure / adapter / entry のどれか | capability plan で明示 |

Given / When / Then と Example Mapping は思考補助として使う。人間に記法を強制しない。

Example Mapping の分類:

| 種別 | 扱い |
|---|---|
| Rule | DESIGN の制約または受入基準 |
| Example | テスト例・境界値 |
| Question | QUESTIONS |
| Out of scope | DESIGN のスコープ外または将来計画 |

---

## 6. DESIGN への反映規則

DESIGN 反映案は現在形で書く。履歴や差分説明を DESIGN 内に残さない。

反映時の規則:

- 目的・スコープ・UC・受入基準・制約を混ぜない
- 実装案は仕様と分離する
- 「高速」「簡単」「安全」などの曖昧語は、数値・観察可能な条件・質問へ変換する
- 将来案を現在スコープに混ぜない
- 技術選定は採用理由と用途別機能カテゴリを確認する
- 受入基準は少なくとも 1 つの test obligation に落ちる形にする
- DESIGN へ反映できないものは QUESTIONS へ送る

### 6.1 機械抽出される書き方

design-ir は自然言語の意味を完全解釈しない。抽出対象は、明示 ID と §4 の checklist item に限定する。

| 対象 | 抽出される形式 | 備考 |
|---|---|---|
| requirement | `- REQ-001: ...` または `### REQ-001: ...` | §6/§7/§9 の ID は制約として扱い、実装未割当要求とは分離する |
| use case | `### UC-1: ...` | §3 配下の heading を想定 |
| test obligation | `- [ ] AC-001: ...` または `- [ ] ...` | 明示 ID は `AC-001:` / `TO-001:` の colon 付きだけを扱う |
| constraint | §6 / §7 / §9 の requirement 形式 | coverage では `:constraint-requirements` に分かれる |

自然文だけに書かれた要求は、人間には読めても機械的 trace には入らない。実装・テスト・coverage と結びたいものは、短い明示 ID を付ける。

---

## 7. DESIGN 更新時の Impact 分類

DESIGN を更新する時、LLM は変更を次のどれかへ分類する。

| 分類 | 意味 | 次アクション |
|---|---|---|
| `:additive` | 新要求の追加 | design-ir 再生成、capability plan / test obligation 追加 |
| `:clarification` | 既存要求の明確化 | テスト更新の要否確認 |
| `:narrowing` | スコープ縮小 | orphan requirement / test / capability を確認 |
| `:broadening` | スコープ拡大 | 受入基準とテスト義務を追加 |
| `:breaking` | 既存 capability / API / test に影響 | Q または ADR 候補、既存 map と tests の影響確認 |
| `:removal` | 要求削除 | orphan tests / brick requirements を確認 |

impact 分類は LLM の説明出力であり、現時点では永続ファイル化しない。

---

## 8. Test Obligation

test obligation は「実テストを書く前に、何を検証すべきか」を表す中間義務である。実テスト本体ではない。

| DESIGN 要素 | test obligation の目安 |
|---|---|
| 受入基準 | acceptance / integration test |
| UC | flow / base test |
| 業務不変条件 | property test |
| Malli schema | contract / generator-based test |
| 例外条件 | negative test |
| 性能条件 | benchmark / smoke test |
| UI 制約 | UI / E2E test |

現段階では、design-ir が受入チェックリストから test obligation を生成する。実テスト本体の自動書き換えと trace metadata 検査は次段階の機械化候補である。

DESIGN 更新時の責務境界:

- 自動更新するもの: `.llm/data/design-ir.edn`
- LLM が更新案を作るもの: capability plan、Malli 契約、実テスト本体、関連 KNOWLEDGE / QUESTIONS / ADR
- script が検出するもの: design-ir drift、ID 重複、既存分析 EDN との coverage 差分
- script がまだ保証しないもの: test obligation と実テストファイルの 1:1 trace、テスト本体の自動生成、自然言語仕様の完全整合

ID 規則:

- 長期 trace が必要な受入基準は `AC-001: ...` のように明示 ID を付ける
- 明示 ID は `AC-001:` / `TO-001:` の colon 付きだけを認める。文頭の `REQ-001` や `UC-1` は、受入基準本文として扱う
- 明示 ID がない場合、design-ir は正規化した文言から `TO-XXXXXXXX` 形式の安定 hash ID を作る
- hash ID は行番号には依存しないが、文言を変えると変わる。継続的に追う基準は明示 ID にする
- 重複した test obligation ID は check 時に失敗させる

---

## 9. Capability Plan への接続

DESIGN が承認された後、実装前に capability plan を作る。自然言語要求から直接 brick を作らない。

最小形式:

```clojure
[{:requirement "INV-001"
  :capability  :invoice/create
  :brick       :invoice
  :kind        :component
  :effect      :pure
  :public-fn   'create}]
```

判断規則:

- `:pure` 相当は時刻・乱数・I/O・可変状態を内側に置かない
- `:adapter` 相当は外部システムや技術境界を扱う
- `:entry` 相当は base / entrypoint として外部からの入力を受ける
- 既存 capability があれば新規 brick を作らない

---

## 10. LLM 判断と機械検査の境界

LLM が担うもの:

- IDEA の意味分解
- reconciliation table の作成
- DESIGN 反映案の作成
- 曖昧性の質問化
- impact 分類
- capability plan 案の作成

script が担うもの:

- 明示 requirement ID の抽出
- ID 重複の検出
- fenced code block / comment の無視
- design-ir の drift 検出
- brick-map / workspace-map / libs との照合
- coverage / orphan の検出

script が担わないもの:

- IDEA と DESIGN の自然言語完全一致
- 人間の意図の正しさ判定
- 未記載仕様の創作
- 仕様確定の承認

---

## 11. 禁止事項

- IDEA を仕様正本として実装へ進む
- reconciliation を永続正本にする
- DESIGN に変更履歴や翻案メモを残す
- test obligation を実テスト完了とみなす
- LLM の推測を承認済み仕様として扱う
- 自然言語整合性を script で完全判定しようとする
- 仕様・実装案・知識・判断履歴を同じ節に混ぜる
