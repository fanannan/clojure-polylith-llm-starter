# ADR (Architecture Decision Records)

本ディレクトリは、プロジェクトにおける**重要な設計判断の経緯を不変の履歴として記録**する。
原則 13（`../../.llm/guide/MAINTAINERS_GUIDE.md` §4）で定義される**決定履歴（ADR）**の実装である。

## ADR とは

**ADR = Architecture Decision Record**（アーキテクチャ決定記録）

ソフトウェア開発で行われる**重要な設計判断の経緯を、判断時点の文脈とともに短い文書として記録する手法**。
2011 年に Michael Nygard が "Documenting Architecture Decisions" で提唱し、業界で広く定着している。

### 何を解決するか

ソフトウェアプロジェクトは時間とともに以下の問題を必ず抱える：

- **「なぜこれになっているのか」が誰にもわからなくなる**（数ヶ月後、新メンバーが理由を辿れない）
- **同じ議論が何度も再燃する**（過去に却下した案が繰り返し提案される）
- **前提が変わった時に気付けない**（当時の理由が記録されていないと再検討できない）
- **判断の質が低下する**（口頭・チャットでは書く過程での思考整理が起きない）

ADR はこれらを**軽量な記述形式**で解決する。1 件あたり 1〜2 ページ、標準化された構造で書く。

### ADR の核心：不変性

**一度 accepted になった ADR は、本文を編集してはならない**。
編集したくなったら、新しい ADR を発行して、古い ADR の Status を `superseded by NNNN` に変更する。
これにより：

- 当時の判断が歴史的記録として保全される
- 後から「良い方に書き換える」バイアスが排除される
- git 履歴と合わせて完全な判断の系譜が残る

この不変性が ADR の本質。KNOWLEDGE.md（上書き更新）・QUESTIONS.md（解決して閉じる）とは根本的に異なる。

### 似て非なるもの

| 概念 | 違い |
|---|---|
| **RFC** | 新機能の**提案**段階で詳細を議論する文書。ADR は**決定後の記録** |
| **Design Doc** | 機能の**実装前の詳細設計書**（図表・API 仕様を含む）。ADR は判断部分のみ抽出 |
| **Postmortem** | **障害の振り返り**。ADR は判断の記録、Postmortem は事故の記録 |
| **CHANGELOG** | ユーザ向けの**変更点一覧**（何が変わったか）。ADR は内部向けの**なぜ変えたか** |

---

## 目的

> **呼び出し元**: 本ディレクトリの ADR 群は `CLAUDE.md §8.0`（実装着手前の確認）の「過去の決定の確認」ステップから参照される決定履歴。空の場合の扱い（空スキャンで完了）は呼び出し元 §8.0 で一括規定（による相互参照構築）。
>
> **初期状態**: 本ディレクトリは `README.md`（本文書）と `template.md` のみ、実 ADR は 0 件（テンプレート配布時）。ADR は以降のプロジェクト運営で「ADR を発行すべき基準」（後述）に該当した時に発行される。

- **なぜそう決めたか**を、判断時点の文脈と共に残す
- 将来同じ議論の再発を防ぐ（却下案の記録）
- 前提が変わった時に、判断の妥当性を再評価できるようにする
- 時間経過でチームが入れ替わっても、設計思想を伝達できる

## ADR と他文書との違い（原則 13 の適用）

| 文書 | 性質 | 更新 |
|---|---|---|
| **DESIGN.md** | 何を作るか（仕様） | 大きな方針転換時のみ改訂 |
| **KNOWLEDGE.md** | 現時点の真実（契約・不変条件） | **上書き更新**される |
| **ADR（本ディレクトリ）** | なぜそう決めたか（経緯） | **一度発行したら変更禁止** |
| **QUESTIONS.md** | 未決の判断 | open → resolved で閉じる |

**ADR の最重要特性は「不変」**。一度発行した ADR は編集しない。改訂が必要になったら、
新しい ADR を発行して旧 ADR を supersede する。これにより判断の履歴が完全に保全される。

## ADR を発行すべき基準

以下のいずれかに該当する判断は ADR を発行する：

1. **アーキテクチャ全体に影響する選択**（例: Polylith 採用、Integrant vs Component）
2. **技術スタックの追加・削除**（例: DB を PostgreSQL から DynamoDB へ変更）
3. **設計原則の新設・変更**（例: ドメイン層 I/O 禁止の導入）
4. **外部サービス連携の方針変更**（例: 認証を自前から Auth0 へ移行）
5. **却下された大きな設計案**（同じ提案の再発を防ぐため）
6. **KNOWLEDGE.md の大きな変更**（変更経緯の記録のため）
7. **QUESTIONS.md の Q が resolved になり、その決定経緯が将来参照されうる時**

以下は ADR を発行しない：

- 軽微な実装選択（関数名、ローカル変数名等）
- Malli スキーマの細部調整
- フォーマット・lint 修正
- バグ修正（通常のコミットログで十分）
- 単発の判断で、将来参照されない事項（QUESTIONS.md で resolved → アーカイブで十分）

## 採番規則

- ファイル名: **`NNNN-kebab-case-topic.md`**（例: `0001-adopt-polylith.md`）
- NNNN は 4 桁ゼロパディングの通し番号、`0001` から開始
- topic は内容を端的に表す英語 kebab-case（日本語は避ける、ファイル名の可搬性のため）
- 採番は時系列順、欠番を作らない

## 不変性ルール

### してよいこと

- 初回発行時の記述
- **status フィールドの更新のみ**（proposed → accepted、accepted → deprecated）。`accepted → superseded-by-NNNN` は により廃止（新 ADR 側の Related だけで supersede 関係を表現）

### してはいけないこと

- 本文（context / decision / consequences）の編集
- 誤字脱字の修正（発行前のみ許容。発行後は元のまま残す）
- 古い情報になった部分の更新（代わりに新 ADR を発行して supersede する）

## 書式

新規 ADR は `template.md` をコピーして作成する。必須セクション：

- **Status**: proposed / accepted / superseded-by-NNNN-xxx / deprecated
- **Context**: なぜこの判断が必要になったか（当時の状況、制約、発見した問題）
- **Decision**: 何を決めたか（明確に、1〜3 文で）
- **Consequences**: この判断の結果として何が起きるか（良い影響、悪い影響、トレードオフ）

推奨セクション：

- **Considered Alternatives**: 検討した代替案と却下理由
- **Related**: 関連する他 ADR、KNOWLEDGE.md §X、DESIGN.md §X、Q-YYYY-MM-NNN

## 運用手順

### 新規 ADR の発行

1. 次の番号を確認（`ls .llm/memory/adr/` で既存 ADR の最大番号を確認）
2. `cp .llm/memory/adr/template.md .llm/memory/adr/NNNN-topic.md`
3. 本文を記述
4. Status を `proposed` で開始、チームレビュー後に `accepted` に変更
5. 関連文書（KNOWLEDGE.md、DESIGN.md、QUESTIONS.md）への参照を追記

### 既存 ADR の改訂が必要になった時

**ADR 本文を直接編集しない**。以下の手順で（により片方向更新に簡素化）：

1. 新しい ADR を発行（次の番号で）
2. 新 ADR の Context で「`NNNN-old-topic.md` の判断を改訂する必要がある理由」を述べる
3. 新 ADR の Decision で新しい判断を述べる
4. 新 ADR の Related に `Supersedes: NNNN-old-topic.md` を記載

**旧 ADR の Status は触らない**（`accepted` のまま）。遡読者は新 ADR の Related をたどって supersede 関係を知る。従来の「旧 ADR の Status を superseded-by-NNNN に更新する」双方向更新は、チェイン整合を維持する儀式コストに対して遡読者が実質不在のため廃止した。

**最新の accepted ADR のみが生きた判断**。旧 ADR は「その時点でそう判断した」歴史的記録として残る。

### LLM による ADR 発行

LLM が ADR を発行できるのは、**以下のいずれかに該当する場合のみ**：

1. ユーザの明示的指示
2. Q resolved 時の昇格判定で「ADR 発行」が選ばれ、ユーザが承認した場合（QUESTIONS.md §0.4）

それ以外は、LLM は ADR 発行を QUESTIONS.md に Q として立て、承認を経てから発行する。
**LLM の独断による ADR 発行は禁止**。

## アンチパターン

- **ADR 内での仕様記述** → DESIGN.md に書く
- **ADR 内での現状契約記述** → KNOWLEDGE.md に書く（ADR は決定経緯のみ）
- **ADR の過剰発行**（些細な判断にも ADR） → 発行基準に該当しないものは QUESTIONS.md アーカイブで十分
- **ADR の未発行**（重要な判断をコミットメッセージにだけ書く） → 将来参照できない、発行すべき
- **superseded された ADR の削除** → 削除禁止。履歴として保全
