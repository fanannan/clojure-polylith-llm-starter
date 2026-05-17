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

| ID | IDEA の記述 | 解釈した意図 | 扱い | 反映案 / 反映先 | 置いた仮定 | 捨てた意味 | 確度 | 質問優先度 | 数値根拠 | 次アクション |
|---|---|---|---|---|---|---|---|---|---|---|
| R1 | PDF 出力したい | ユーザが成果物を帳票として保存・共有できる | 反映 | DESIGN §3 / §4 | PDF が主要成果物である | 印刷品質の詳細 | 中 | before-implementation | なし | DESIGN 反映、帳票要件を質問 |
| R2 | 将来は英語対応 | 現時点では多言語を実装しないが拡張余地は残す | 将来案 | DESIGN §10 | 初期版は日本語のみ | 初期版での翻訳実装 | 高 | defer | なし | DESIGN §10 に反映 |
| R3 | 高速にしたい | 待ち時間を短くしたい | 質問 | QUESTIONS | 主要操作が未特定 | 速度値の確定 | 低 | blocker | なし | 主要操作と許容時間を質問 |
| R4 | DB は不要 | 永続化方式を軽くしたい | 判断必要 | QUESTIONS / ADR 候補 | ファイル保存で足りる可能性 | 将来の同時編集・検索要件 | 中 | before-implementation | なし | 永続化方式を確認 |

各列の意味:

| 列 | 意味 |
|---|---|
| 解釈した意図 | LLM が読み取った利用者・業務上の意味。ここが曖昧なら反映しない |
| 反映案 / 反映先 | DESIGN / QUESTIONS / KNOWLEDGE / ADR / 破棄のどこへ吸収するか |
| 置いた仮定 | LLM が補った前提。仮定なしなら `なし` と書く |
| 捨てた意味 | 曖昧語を仕様化する過程で切り落とした含意。捨てていないなら `なし` と書く |
| 確度 | `高` / `中` / `低`。低いものを承認済み仕様として扱わない |
| 質問優先度 | §4.1 の語彙で分類する |
| 数値根拠 | 数値・閾値を置いた場合の根拠。根拠がなければ `なし` と書き、採用値にしない |

管理規則:

- reconciliation は commit しない
- ID は `R1`, `R2` のような一時 ID とし、永続 ID にしない
- 承認後、各行は DESIGN / QUESTIONS / KNOWLEDGE / ADR のいずれかへ吸収する
- 吸収先のない行は破棄する
- 未吸収のまま実装へ進まない
- 却下理由を将来辿る価値がある場合だけ ADR にする
- 数値・閾値・競合比較は、根拠がない限り採用済み仕様にしない
- 確度 `低` かつ質問優先度 `blocker` の行は、DESIGN 反映前に人間へ確認する

### 4.1 質問優先度

脱抽象化すると質問は増えやすい。LLM は質問を増やすことではなく、実装判断を止める不確実性だけを早く取り除くことを優先する。

| 優先度 | 意味 | LLM の扱い |
|---|---|---|
| `blocker` | 決まらないと DESIGN へ反映できない、または反映すると誤仕様になる | その場で 1 点だけ質問する |
| `before-implementation` | DESIGN には仮置きできるが、実装・brick・契約・テスト確定前に必要 | 仮定を明示して DESIGN 案に入れ、実装前に質問する |
| `defer` | 後続フェーズ・将来計画・運用開始前でよい | DESIGN §10、QUESTIONS、または KNOWLEDGE 候補へ送る |
| `assumption-ok` | LLM が仮定を明示すれば進められる | 仮定を reconciliation と DESIGN 案に残し、必要なら後で見直す |

質問は同時に大量に出さない。`blocker` が複数ある場合は、目的・スコープ・主要 UC・受入基準・プロジェクト固有情報の順に、最も上流の 1 点を先に確認する。

### 4.2 数値根拠

数値は疲労を減らすが、根拠のない数値は誤った精密さを生む。LLM は数値を置く時、次のどれかを明示する。

| 根拠 | 使える場面 |
|---|---|
| `user-provided` | ユーザが明示した数値 |
| `existing-system` | 現行システム・現行業務の実測値 |
| `benchmark` | ベンチマーク、検証実験、性能計測 |
| `contract/legal` | 契約、SLA、法令、業界規制 |
| `business-target` | KPI、売上、工数削減など人間が決めた目標 |
| `industry-reference` | 一般的な参考値。採用前に確認が必要 |
| `assumption-candidate` | LLM の仮置き候補。採用値ではない |
| `none` | 根拠なし。数値を仕様化せず質問化する |

`industry-reference` と `assumption-candidate` は、そのまま DESIGN の確定値にしない。DESIGN に入れる場合は「仮置き」「初期目安」「後で実測により見直す」のいずれかを明示する。

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

### 5.1 ドメイン用語の定義

「顧客」「案件」「承認済み」「完了」「有効」「最新」「管理者」「通常利用」「失敗」「成功」のような業務語は、見た目より危険である。曖昧語は品質をぼかすが、ドメイン用語の曖昧さはデータモデル、分岐、権限、テスト、監査を直接壊す。

LLM は次の条件に当てはまる用語を見つけたら、意味を定義せずに実装へ進まない。

| 条件 | 扱い |
|---|---|
| 状態遷移に影響する | DESIGN §3 / §4 または KNOWLEDGE に定義。未確定なら QUESTIONS |
| 権限・公開範囲に影響する | DESIGN §6 / §7 または KNOWLEDGE に定義。未確定なら blocker |
| データ保持・削除・監査に影響する | DESIGN §6 / §9 または KNOWLEDGE に定義。必要なら ADR |
| 外部 API / DB schema / Malli schema に現れる | 実装前に定義。仮称なら仮称であることを明示 |
| 日常語と業務語の意味がずれる | 用語定義を置く。例: 「最新」は更新日時順か承認日時順か |

置き場所の目安:

- 初期仕様に必要な用語: DESIGN の該当節に短く定義する
- 実装・運用で継続参照する不変条件: KNOWLEDGE に置く
- 複数案から選んだ理由が重要: ADR に残す
- 意味が決まっていない: QUESTIONS に `blocker` または `before-implementation` として起票する

### 5.2 曖昧語の脱抽象化

IDEA には「いい感じ」「速い」「使いやすい」「ちゃんと」「安全」「賢い」のような、主観的・相対的・暗黙的な品質語が混ざる。LLM はこれをそのまま DESIGN へ移さず、次のいずれかへ分解する。

| 分解先 | 意味 | 例 |
|---|---|---|
| 測定可能な閾値 | 数値・範囲・上限・下限として検証できる条件 | p90 応答 1 秒以内、月額コスト上限、コントラスト比 |
| 検証可能な振る舞い | 入力・操作・状態に対する出力や遷移 | 権限不足なら保存せず理由を表示、失敗時も再実行可能 |
| 観測可能な利用者経験 | ユーザが見て分かる状態・迷わない導線・説明 | 3 ステップ以内で完了、現在地が常に分かる |
| 比較基準 | 何と比べて良いか | 既存手作業より時間半減、競合 A より検索待ちが短い |
| 制約・禁止事項 | やってはいけないこと、壊してはいけない性質 | ログに PII を出さない、横スクロールを出さない |

脱抽象化の順序:

1. その語が指すカテゴリを選ぶ。UI、性能、UX、信頼性、セキュリティ、機能、データ、例外、アクセシビリティ、国際化、モバイル、AI、テスト、設計、運用、ビジネスのいずれかに寄せる
2. 依頼者が本当に欲しい結果を 1 文で言い換える。「つまり、ユーザは何に困らなくなるのか」を先に置く
3. 測定値にできるなら候補値を出す。根拠がなければ採用値にせず QUESTIONS に回す
4. 測定値にできない品質は、観測可能な振る舞いまたは受け入れ例に落とす
5. 反映案を人間に逆向きに翻訳して見せる。「この仕様化は、つまりこういう体験を目指す、という理解でよいか」を確認する

### 5.3 脱抽象化の注意事項

測定可能性は重要だが、測定しやすいものだけが重要なのではない。数値化した瞬間に、ブランド体験、安心感、違和感のなさ、プロダクトの美意識が抜け落ちることがある。LLM は数値にできない品質を捨てず、DESIGN の目的・制約・受入基準・質問候補に分けて保持する。

Goodhart の法則に注意する。カバレッジ、LCP、CVR、F1、NPS などを目標として固定すると、指標だけが改善され、本来のアウトカムが壊れる場合がある。指標は方向確認に使い、最終目的は利用者・業務・運用上の成果として表現する。

過剰仕様化を避ける。曖昧さの一部は設計余白であり、最初から pixel、閾値、アルゴリズムまで固定すると、平均的で硬い成果に収束しやすい。LLM は「固定すべき不変条件」と「実装者に任せる余白」を分ける。

早すぎる定量化を避ける。問題の本質が未解明な段階では、いきなり数値を確定せず、まず観測可能な振る舞い・完了条件・比較軸に留める。数値は実測・競合比較・運用制約が得られた段階で確定する。

脱抽象化した仕様が依頼者の意図とずれることがある。reconciliation では、変換後の文だけでなく「この変換で捨てたもの」「まだ曖昧なもの」「人間に決めてほしいもの」を明示する。

プロダクトの哲学・魂・美意識のような、言語化しきれない判断を消さない。これらは test obligation には直結しないことがあるが、目的、ブランド制約、非機能要件、禁止事項、例示、レビュー観点として残す。

### 5.4 曖昧語カタログ

この表は、人間に記法を強制するためのものでも、曖昧語を機械的に置換する辞書でもない。LLM が IDEA の自由記載から評価語を見つけた時、どの観点へ分解するかを決めるための作業用 index である。表内の数値や具体策は採用済み基準ではなく候補であり、プロダクトの目的、利用者、業務制約、技術制約、既存実装、運用体制に合わせて応用する。根拠がなければ採用せず、質問化する。

| カテゴリ | IDEA に出やすい語 | 仕様化する観点 | 代表的な変換先 |
|---|---|---|---|
| UI / ビジュアル | いい感じ、モダン、シンプル、おしゃれ、目を引く、プロっぽい、シャープ、やわらかい、リッチ、ミニマル、洗練、かっこいい、高級感、ブランド感 | デザインシステム、情報階層、色数、余白、タイポグラフィ、コントラスト、ファーストビュー、主要 CTA | §4 の見た目に関する受入基準、§6 UI 非機能要件、UI/E2E test obligation |
| パフォーマンス | 高速、サクサク、重くない、スムーズ、すぐ表示、待たされない、効率的、軽い、リアルタイム、ストレスなく動く | LCP、INP、FCP、TTI、p90/p99 応答、bundle size、転送量、主スレッドブロック、反映遅延 | §4 性能受入基準、§6 性能、benchmark / smoke test obligation |
| UX / 使いやすさ | 使いやすい、直感的、分かりやすい、親切、ユーザに優しい、ストレスフリー、迷わない、楽しい、気が利く、寄り添う、登録しやすい、購入しやすい | 主要タスク完了ステップ、クリック数、入力項目、エラー表示、Undo、Empty State、現在地表示、オンボーディング | §3 UC、§4 受入基準、§6 UX 制約、acceptance / E2E test obligation |
| 信頼性 / 可用性 | 安定、落ちない、壊れない、ちゃんと動く、信頼できる、堅牢、障害に強い、品質が高い、バグが少ない | 可用性、エラー率、p99、冪等性、トランザクション、再処理、DLQ、監査ログ、RPO/RTO、主要動線 E2E | §4 受入基準、§6 可用性、§9 技術的制約、integration / resilience test obligation |
| セキュリティ / プライバシー | セキュア、安全、漏れない、不正アクセスを防ぐ、プライバシーに配慮、改ざんされない | 認証、認可、最小権限、暗号化、PII マスク、ログ禁止、レート制限、監査ログ、削除・エクスポート手段 | §6 セキュリティ、§7 外部 interface 制約、negative / security test obligation |
| 機能要件 | 便利、いい感じに整理、うまく検索、賢く分類、自動で、最適化、レコメンド、要約、管理機能、通知、手間を減らす、ミスを減らす | actor、trigger、入力、出力、実行頻度、失敗時挙動、確認要否、評価関数、CRUD、検索、フィルタ、履歴 | §3 UC、§4 受入基準、capability plan、unit / integration test obligation |
| データ / 状態管理 | データを保存、最新状態、同期、永続化、履歴、キャッシュ、データがきれい、データを活用 | 保存先、schema、保持期間、削除ポリシー、取得頻度、衝突解決、TTL、無効化、欠損・重複検出 | §6 データ制約、§9 技術的制約、property / integration test obligation |
| エラー / 例外処理 | エラーに強い、丁寧なエラー、失敗しても大丈夫、異常時に通知 | タイムアウト、リトライ、フォールバック、冪等性、部分成功、回復手順、通知先、抑制ルール | §3 例外、§4 negative acceptance、§6 運用制約、negative / integration test obligation |
| アクセシビリティ | みんなが使える、読みやすい、色覚に配慮、高齢者にも使える、誰でも使える、アクセシブル | WCAG、キーボード操作、スクリーンリーダー、コントラスト、フォーカス順、タップ領域、文字拡大 | §6 アクセシビリティ、UI / E2E test obligation |
| 国際化 / ローカライズ | グローバル対応、日本語対応、翻訳しやすい、日本向け | 文字列外出し、MessageFormat、タイムゾーン、通貨、日付、住所・氏名形式、検索表記揺れ、RTL | §6 国際化、§7 外部制約、unit / UI test obligation |
| モバイル / レスポンシブ | スマホでも見やすい、スマホでも使える、レスポンシブ、PWA、ネイティブっぽい | breakpoint、横スクロール禁止、タッチ領域、片手操作、offline、push、OS 慣習、gesture | §4 受入基準、§6 UI 制約、responsive / E2E test obligation |
| AI / LLM | 賢い AI、自然な会話、いい感じに答える、文脈理解、ハルシネーションしない、プロンプトをいい感じに、AI っぽい、正確、精度が高い | 対象タスク、評価 dataset、Precision/Recall/F1、引用要否、不確実性表現、RAG 情報源、保持 context、拒否方針 | §3 UC、§4 受入基準、§6 AI 品質制約、golden / evaluation test obligation |
| テスト / QA | ちゃんとテスト、品質担保、再現性、テストしやすい | 単体/結合/E2E、境界値、異常系、seed 固定、時刻 mock、外部依存 stub、CI 自動実行 | §4 受入基準、test obligation、CI gate |
| コード / アーキテクチャ | きれいなコード、拡張性、保守しやすい、リファクタしやすい、いい感じの設計、変更に強い、柔軟、最小構成、将来を見据える | ドメイン境界、依存方向、データフロー、循環参照禁止、設定化、Feature Flag、Malli 契約、Polylith capability | §9 技術的制約、capability plan、architecture review / unit test obligation |
| DevOps / 運用 | すぐリリース、監視できる、運用しやすい、リリースしやすい、原因調査しやすい、導入しやすい、移行しやすい、連携しやすい | CI/CD、rollback、metrics/logs/traces、SLO/SLI、runbook、backup、migration、API 仕様、rate limit、webhook | §6 運用、§7 外部 interface、§9 技術的制約、smoke / ops test obligation |
| ビジネス / プロダクト | ユーザが喜ぶ、売上に貢献、競合に勝てる、いい KPI、成長する、マネタイズ、いいプロダクト、売れる LP、刺さるコピー、信用される | persona、JTBD、KPI、CVR/LTV/ARPU、比較対象、価格、無料/有料境界、証拠、CTA、成功時の世界像 | §1 目的、§2 スコープ、§4 受入基準、§10 将来計画 |

### 5.5 代表変換パターン

LLM は次の形を優先して使う。右辺をそのまま採用せず、対象プロダクトに合わせて値・対象・検証方法を調整する。実情に合わない例は捨て、同じ発想で別の受入基準・制約・質問へ展開する。

| 曖昧な要望 | 変換パターン |
|---|---|
| いい感じの UI | 対象ユーザの主要タスクが初回利用でも迷わず開始でき、主要機能がファーストビューまたは主導線から到達できる |
| モダンな UI | 採用するデザイン規約または design token を明示し、responsive、アクセシビリティ、micro-interaction の有無を決める |
| シンプル | 主要アクション数、画面階層、初期表示要素、advanced option の隠し方を決める |
| プロっぽい | 業務利用に必要な情報密度、権限、履歴、監査、エラー回復を備える |
| 高速 | 主要操作ごとに p90/p99、初期表示、検索、バッチ処理時間の候補値を置く |
| サクサク | 入力反応、画面遷移、非同期化、loading 表示、操作不能時間を定義する |
| 使いやすい | actor ごとに主要タスクを 3 ステップ以内などの操作条件へ落とす |
| 分かりやすい | 専門用語、ラベル、ヘルプ、エラー文言、画面目的の表示ルールへ落とす |
| 親切 | Empty State、inline error、次アクション、Undo、確認 dialog の条件へ落とす |
| 安定 | エラー率、可用性、再試行、冪等性、データ喪失防止、監視の基準へ落とす |
| 信頼できる | データ出典、更新日時、処理根拠、監査ログ、重要操作の履歴へ落とす |
| 安全 / セキュア | 認証、認可、暗号化、入力検証、ログ禁止、依存脆弱性 gate へ落とす |
| プライバシー配慮 | 収集最小化、利用目的、削除、エクスポート、PII マスクへ落とす |
| 自動化したい | trigger、入力、処理、承認要否、失敗時挙動、通知先、再実行条件へ落とす |
| いい検索 | 対象項目、部分一致、表記揺れ、filter、sort、応答時間、0 件時表示へ落とす |
| いい推薦 | 使用する signals、制約条件、推薦理由、評価指標、除外条件へ落とす |
| うまく要約 | 入力上限、出力長、保持すべき情報、捨ててよい情報、引用要否へ落とす |
| データを保存 | 保存先、schema、暗号化、保持期間、削除ポリシー、復旧手順へ落とす |
| 最新状態 | 更新頻度、楽観的更新、競合解決、stale 表示、手動 refresh の有無へ落とす |
| エラーに強い | 外部依存ごとの timeout、retry、fallback、circuit breaker、通知へ落とす |
| 失敗しても大丈夫 | 冪等性、部分成功、入力保持、取り消し、再実行、回復手順へ落とす |
| 誰でも使える | WCAG、キーボード操作、screen reader、contrast、focus、touch target へ落とす |
| スマホ対応 | breakpoint、touch target、横スクロール禁止、片手操作、主要 CTA 配置へ落とす |
| グローバル対応 | 多言語、timezone、通貨、日付、単位、法規制差分、RTL の要否へ落とす |
| 賢い AI | 対象タスク、評価 dataset、評価指標、レイテンシ、不確実性表示、引用要否へ落とす |
| ハルシネーションしない | 事実主張の引用必須、引用不能時の応答、検証 pipeline、禁止回答へ落とす |
| ちゃんとテスト | 正常系、異常系、境界値、property、E2E、CI gate、fixture 方針へ落とす |
| 再現性 | seed 固定、時刻注入、外部依存 stub、test data、環境差分の固定へ落とす |
| 拡張しやすい | 変わりやすい rule、依存方向、interface、設定化、feature flag へ落とす |
| 保守しやすい | module boundary、命名、契約、テスト、生成物 drift 検査へ落とす |
| 監視しやすい | metrics、logs、traces、dashboard、alert threshold、runbook へ落とす |
| すぐリリース | CI/CD、環境分離、rollback、smoke test、release checklist へ落とす |
| 売上に貢献 | 対象 KPI、計測方法、比較期間、A/B、成功判定、悪影響の監視へ落とす |
| いい KPI | 目的との因果、計測可能性、操作されにくさ、補助指標、逆指標へ落とす |
| いいプロダクト | 対象ユーザ、解決課題、差別化、使わないもの、成功時の状態へ落とす |

---

## 6. DESIGN への反映規則

DESIGN 反映案は現在形で書く。履歴や差分説明を DESIGN 内に残さない。

反映時の規則:

- 目的・スコープ・UC・受入基準・制約を混ぜない
- 実装案は仕様と分離する
- 「高速」「簡単」「安全」などの曖昧語は、§5.2〜§5.5 に従って数値・観察可能な条件・制約・質問へ変換する
- 将来案を現在スコープに混ぜない
- 技術選定は採用理由と用途別機能カテゴリを確認する
- 受入基準は少なくとも 1 つの test obligation に落ちる形にする
- DESIGN へ反映できないものは QUESTIONS へ送る

### 6.1 受入基準の推奨形

人間に記法を強制しない。ただし LLM が DESIGN へ展開する時は、受入基準をできるだけ次の情報が入る形へ整える。

```markdown
- [ ] AC-001: [UC-1][REQ-001] <誰が> <どの条件で> <何をしたら> <どうなる>。検証方法: <test / smoke / benchmark / manual review>
```

構成要素:

| 要素 | 意味 | 省略時の扱い |
|---|---|---|
| `AC-001:` | 長期 trace 用の test obligation ID | 省略可。ただし文言変更で hash ID が変わる |
| `[UC-1]` | 関連 use case | 分からなければ省略し、reconciliation に仮定を書く |
| `[REQ-001]` | 関連 requirement | 分からなければ省略し、実装前に capability plan で補う |
| 誰が | actor または system | §3 の actor と矛盾するなら質問 |
| どの条件で | 前提、状態、入力 | 不明なら Given として質問候補 |
| 何をしたら | 操作、trigger、API call、job 実行 | 実装 boundary 判断に使う |
| どうなる | 観測可能な成功状態 | test obligation の核 |
| 検証方法 | test 種別、手動 review、benchmark など | 未定なら `unspecified` として扱う |

例:

```markdown
- [ ] AC-001: [UC-1][INV-001] 経理担当者が承認済み仕訳を対象に月次締めを実行したら、試算表 PDF が生成され、未承認仕訳がある場合は保存せず対象一覧を表示する。検証方法: integration test
```

Given / When / Then は思考補助として使ってよいが、DESIGN では自然な日本語で構わない。重要なのは、LLM とテストが「何をもって完了か」を同じように読めることである。

### 6.2 機械抽出される書き方

design-ir は自然言語の意味を完全解釈しない。抽出対象は、明示 ID と §4 の checklist item に限定する。

| 対象 | 抽出される形式 | 備考 |
|---|---|---|
| requirement | `- REQ-001: ...` または `### REQ-001: ...` | §6/§7/§9 の ID は制約として扱い、実装未割当要求とは分離する |
| use case | `### UC-1: ...` | §3 配下の heading を想定 |
| test obligation | `- [ ] AC-001: ...` または `- [ ] ...` | 明示 ID は `AC-001:` / `TO-001:` の colon 付きだけを扱う |
| related requirement / use case | 受入基準本文中の `[REQ-001]` / `[UC-1]` | test obligation の `:related-requirements` / `:related-use-cases` に入る。未知 ID は diagnostics |
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

design-ir は受入チェックリストから test obligation を生成する。実テスト本体は LLM が通常の実装差分として作成・更新するが、対応関係は Clojure metadata で機械検査する。

DESIGN 更新時の責務境界:

- 自動更新するもの: `.llm/data/design-ir.edn`
- LLM が更新案を作るもの: capability plan、Malli 契約、public boundary の trace metadata、実テスト本体、`deftest` の test obligation metadata、関連 KNOWLEDGE / QUESTIONS / ADR
- script が検出するもの: design-ir drift、ID 重複、既存分析 EDN との coverage 差分、trace metadata の未知 ID / 空 ID / 重複 ID / 誤配置、未対応 test obligation、test metadata と test obligation の related IDs 不整合
- script がまだ保証しないもの: テスト本体の自動生成、テスト本文が本当に義務内容を満たすこと、自然言語仕様の完全整合、手動・benchmark・外部 E2E の実行完了

ID 規則:

- 長期 trace が必要な受入基準は `AC-001: ...` のように明示 ID を付ける
- 明示 ID は `AC-001:` / `TO-001:` の colon 付きだけを認める。文頭の `REQ-001` や `UC-1` は、受入基準本文として扱う
- 明示 ID がない場合、design-ir は正規化した文言から `TO-XXXXXXXX` 形式の安定 hash ID を作る
- hash ID は行番号には依存しないが、文言を変えると変わる。継続的に追う基準は明示 ID にする
- 重複した test obligation ID は check 時に失敗させる

### 8.1 Trace Metadata

trace metadata は、仕様 ID と Clojure の安定境界をつなぐための機械可読な対応表である。本文コメントや命名規約ではなく metadata にするのは、lint / script が構造として読めるようにするためである。

| 対応対象 | 置き場所 | 使用キー |
|---|---|---|
| requirement / use case と実装 public API | component `interface.clj` の公開 `defn`、base `core.clj` / `handler.clj` の公開 boundary `defn` | `:trace/requirements`, `:trace/use-cases` |
| test obligation と実テスト | `deftest` | `:trace/test-obligations` |
| brick 全体の ownership | `brick.edn` | `:brick/requirements` |

実装関数の例:

```clojure
(defn ^{:trace/requirements ["REQ-001"]
        :trace/use-cases ["UC-1"]}
  create-invoice
  [input]
  (core/create-invoice input))
```

テストの例:

```clojure
(deftest ^{:trace/test-obligations ["AC-001"]
           :trace/requirements ["REQ-001"]
           :trace/use-cases ["UC-1"]}
  create-invoice-test
  (is true))
```

禁止:

- component の `core.clj`、private helper、adapter 内部に trace metadata を付ける
- base の `system.clj`、orchestration sub-ns、外部 entrypoint ではない内部関数に trace metadata を付ける
- 実装関数に `:trace/test-obligations` を付ける
- `deftest` に `:trace/requirements` / `:trace/use-cases` だけを付け、`:trace/test-obligations` を省く
- `comment` 内の REPL 試行や補助関数に trace metadata を付ける
- DESIGN にない ID を推測で作る
- 空 vector、空文字、重複 ID を metadata に入れる

検査:

- `.llm/scripts/check-trace-metadata.sh` は `design-ir.edn` を正本として ID を照合する
- 未知 ID、空 ID、重複 ID、実装内部への trace、実装関数への `:trace/test-obligations` は error
- `deftest` の `:trace/requirements` / `:trace/use-cases` は、参照する test obligation の `:related-requirements` / `:related-use-cases` と照合する
- design-ir に存在する test obligation がどの `deftest` からも参照されない場合、`:adoption-mode :complete` では error、`:retrofit` / `:partial` では warning とする
- `.llm/scripts/gen-trace-index.sh` は `docs/GENERATED_VIEW_TRACE.md` / `.llm/data/trace-index.edn` を生成する。DESIGN 更新時は trace-index の impact map を使い、修正対象の public boundary と `deftest` を先に特定する
- `.llm/scripts/trace-impact.sh` は、要件・受入基準・公開関数・変更差分から trace-index を引く標準入口である。仕様変更時は実装前に「この要件に関係するコードとテスト」「この公開関数が満たす仕様」「今回の変更で影響する要件」を確認し、修正対象を説明してからコードへ入る

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
- 候補 group が分かる場合は Brick Map の `:groups` index で同一 group の既存 brick を確認し、既存 brick に入れない理由を capability plan に含める

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
