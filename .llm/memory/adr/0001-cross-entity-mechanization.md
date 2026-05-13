# 0001: 越境ユースケースの機械化 — 上位原理と派生間運用順序

- **Date**: 2026-05-13
- **Status**: accepted
- **Deciders**: sawada.takahiro（テンプレート保守者）

## Context

派生プロジェクトでの 2 つの観察事例（fanannan/clojure-polylith-llm-starter Issue #12 / Issue #13）が、テンプレート規約に未整理だった共通領域を surface した。

**Issue #12 観察**: 派生プロジェクトで以下 3 つの運用パターンが自発的に立ち上がった。

1. 複数 entity 同時更新（越境 tx）処理を handler.clj から `<uc>-orchestration` ns へ抽出するパターン
2. `(safe-reset!) → (seed-all!)` の 2 行で smoke test 環境を立ち上げる規約
3. 越境 tx の原子性を境界テストで assert するパターン（XTDB `_system_from` 一致の事例）

これらは個別 tips ではなく、Polylith 構造で**見落とされやすく再発見コストの高い「越境ユースケース」**という共通領域に対する 3 つの機械化手段だった。

**Issue #13 観察**: 派生プロジェクト PR #35 で Test Plan を先に書いて fixture を後で合わせる悪循環が発生（fixture 不足発覚 → 後追い拡張 → 既書 test 再設計）。具体的には `seed-sample-attendances!` の 2 日分制約や「全生徒同時刻 attendance」による double-booking 判定で reschedule 候補が再現できなかった。これは Issue #12 で立てた 3 派生がチェックリストとして並列扱いされており、**派生間に必要な運用順序が明文化されていなかった**ことから生じた。

両 Issue は、テンプレートが「越境ユースケース」という領域を機械化の最優先対象として位置づけ、その運用方針を確定する必要を示した。

## Decision

本テンプレートは **「越境ユースケースの機械化」を上位原理として確立**し、以下を規約化する。

1. **上位原理**: 越境ユースケース（複数 entity / 複数 entrypoint をまたぐ処理）は、人間の記憶ではなく ns 配置（抽出）・REPL helper（起動）・境界テスト（検証）の 3 機械化手段で再現可能にする。CLAUDE.md §1.2.1 機械化 + §1.2.2 ループ短縮の合成。
2. **派生 1（抽出）**: 越境処理を `<uc>-orchestration` ns に抽出。単一 entrypoint 専用なら base 内 sub-ns、複数 entrypoint 共有なら component 昇格。
3. **派生 2（起動）**: `dev.fixtures` 内に `(seed-all!)` / `(seed-<uc>!)` を立て、`(safe-reset!) → (seed-all!)` の 2 行で smoke test 環境を立ち上げ可能に維持。`seed-<uc>!` は UC 単位で独立・最小、test の前提は原則 `seed-<uc>!`、`seed-all!` は convenience のみ。
4. **派生 3（検証）**: 越境 tx を持つ orchestration には原子性を主張する境界テストを置く。検証手段は採用 DB に依存する（テンプレートは DB 中立、規律のみ規約化）。
5. **派生間の運用順序**: 派生 3（test）は派生 2（fixture）が提供する境界 state を前提とするため、fixture を REPL で実際に観察してから test の precondition を確定する。fixture 未観察の想像 state で concrete な Test Plan / test を確定することを禁止。派生 1（orchestration 配置）は派生 2 / 3 の観察後に調整可能（反復可）。
6. **fragment 配置**: PR / MR の本文で fixture state を共有する fragment を `.llm/templates/fixture-state-summary.md` に置く（`.github/` は作成せず platform-neutral fragment として運用）。

## Consequences

**良い影響**:

- 越境ユースケースという領域が「見落とされやすい領域」から「最優先機械化対象」に位置づけ変更され、派生プロジェクトでの再発見コストが下がる
- 3 派生（抽出 / 起動 / 検証）が「同じ原理の異なる側面」として読めるため、新しい越境 UC に遭遇した時の判定基準が明確
- fixture 観察ファースト規律により、Test Plan / fixture 順序逆転による手戻り loop（§1.2.2 ループ短縮違反）を構造的に防止
- fixture 肥大化抑制規約により、`seed-all!` 由来の偶然 state 干渉（Issue #13 の double-booking 事例）を構造的に防止
- platform-neutral fragment の導入により、GitHub / GitLab / Forgejo 等のいずれの platform でも同じ規約が適用可能

**悪い影響・トレードオフ**:

- テンプレートの規約量が増え、派生プロジェクトの初期学習コストが上がる（ただし派生 2 / 3 の規約は派生プロジェクトが最初の越境 UC を実装する段階で初めて必要になるため、初期化期には負担にならない）
- `.llm/templates/` という新ディレクトリの追加により、保守対象が増える（README / MAINTAINERS / check-doc-references.sh への登録で正規化済み）
- fragment 本文に他文書参照を含めない方針は、由来文書の追跡を templates README に集中させるため、fragment の更新時に README も忘れず更新する必要がある
∵ ../../templates/README.md

**必要な追随作業**:

- 派生プロジェクトでの規約採用実績が累積した段階で、`dev.user/status` への seed helper 自動検出機構を再評価（Q-2026-05-002）
- 後続の platform-neutral fragment 追加（例: bug-report template）時は同じ構造（README / MAINTAINERS / check-doc-references への登録）に従う

## Considered Alternatives

### A. 3 派生を個別 tips として各文書に分散記載

- 却下理由: 共通の上位原理が抽出されず、「なぜこの 3 つを採用するのか」の説明が欠ける。将来 4 つ目のパターンが現れた時の判定基準にもなれない（§1.3 知識活用に反する）

### B. 派生 3（原子性検証）を全面不採用とし、XTDB 固有実装ごと棄却

- 却下理由: §1.1.3 副作用隔離は副作用配置の原理であって、越境 write の原子性を境界で検証する規約を提供しない。原則は §1.1.1 全域性の延長として独立に成立するため、実装手段を棄却しても原則は救出する必要がある

### C. `.github/PULL_REQUEST_TEMPLATE.md` を作成して PR template を機械化

- 却下理由: 現テンプレートは Git を前提にするが GitHub workflow を正本化していない。`.github/` を入れると GitLab / Forgejo 等を使う派生プロジェクトに不要な platform 仮定を持ち込む。`.llm/templates/` を新設して platform-neutral fragment として配布する方が原理整合的

### D. 派生 1 → 2 → 3 の順序を厳格に強制

- 却下理由: 派生 1 の orchestration 配置（base 内 sub-ns vs component 昇格）は fixture / test で共有度が見えてから調整するのが正常パターン。厳格な順序強制は反復可な調整を不可逆な決定として扱うことになり、§1.2.4 早期破棄と整合しない。「fixture 未観察の concrete Test Plan 確定を禁止」という否定形で書くのが適切

### E. Issue #12 と Issue #13 を別 ADR として発行

- 却下理由: Issue #13 は Issue #12 で立てた上位原理に運用順序という制約を追加する性質。両 Issue を貫く論理は同一なので 1 ADR にまとめる。Issue #13 観察がなければ運用順序の必要性が surface しなかった点は本文で明記

## Related

- Supersedes: N/A
- Superseded by: N/A
- Related Issue: fanannan/clojure-polylith-llm-starter#12, #13
- Related implementation: commit `0a18a9d`（Issue #12 対応）、commit `b86264e`（Issue #13 対応）
- Related KNOWLEDGE: N/A（本判断は規約として guide に集約済み）
- Related QUESTIONS: Q-2026-05-002（`dev.user/status` の seed helper 自動検出機構、本 ADR で確立した命名規約と肥大化抑制が定着すれば動機が強化される）
- 規約の正本: 以下の各文書に集約済み
∵ ../../guide/POLYLITH_GUIDE.md §7.4.1
∵ ../../guide/POLYLITH_GUIDE.md §7.4.2
∵ ../../guide/COLLABORATION_GUIDE.md §7.9
∵ ../../guide/COLLABORATION_GUIDE.md §7.10
∵ ../../../CLAUDE.md §10.1
