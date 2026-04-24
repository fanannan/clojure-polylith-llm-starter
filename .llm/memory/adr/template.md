# NNNN: <1 行タイトル>

- **Date**: YYYY-MM-DD
- **Status**: proposed <!-- proposed / accepted / superseded-by-NNNN-xxx / deprecated -->
- **Deciders**: <氏名・役割>

## Context

<!-- なぜこの判断が必要になったか。当時の状況、制約、発見した問題。
     「今から〜を決める」ではなく「〜という状況にあり、判断が必要になった」という
     事実の記述。ここを読めば、数年後でも判断の背景が理解できるように。 -->

## Decision

<!-- 何を決めたか。明確に、1〜3 文で。
     曖昧表現（「〜の方向で検討する」）は避け、確定した内容を書く。
     例（ADR 記述例として典型的な表現。本テンプレートの標準選定は STACK_GUIDE に置く）：
         「Integrant を副作用コンポーネントのライフサイクル管理に採用する。
          Component (stuartsierra/component) は採用しない」 -->

## Consequences

<!-- この判断の結果として何が起きるか。
     - 良い影響（期待した効果）
     - 悪い影響（トレードオフ、追加コスト）
     - 必要な追随作業（他文書の更新、コード移行等）
     良い面だけ書くと将来の再評価時に片手落ちになる。トレードオフを明示する。 -->

## Considered Alternatives

<!-- 検討した代替案と却下理由。
     例：
     ### A. Component (stuartsierra/component)
     - 却下理由: Integrant の derive ベース依存解決と比較して柔軟性が劣る。
                 エコシステムは Component の方が厚いが、本プロジェクトでは Integrant + Malli
                 の組み合わせを重視
     
     ### B. Mount
     - 却下理由: グローバル状態による可変性が §1.1.2 不変性の原則と整合しない -->

## Related

<!-- 関連文書。関係する他 ADR、KNOWLEDGE.md §X、DESIGN.md §X、Q-YYYY-MM-NNN。
     例：
     - Supersedes: N/A
     - Superseded by: N/A
     - Related ADR: 0002-use-malli-for-contracts.md
     - Related KNOWLEDGE: §3 アーキテクチャ上の約束
     - Derived from: Q-2026-04-001 -->
