# Structural Evidence Quickstart

Structural Evidence View は、LLM が scope と evidence を自己申告する代わりに、git diff、repo-kind、Polylith 構造、生成 index、検査 script から review 用 view を導出する仕組みである。

最低限の mantra:

1. LLM の出力は claim であり、trust ではない。
2. scope と required evidence は、まず機械が導出する。
3. LLM は導出不能な residual だけを `none` または具体 list で明示する。
4. Review Fatigue Packet は正本ではなく、次セッションの足場である。

## 最小手順

```bash
./.llm/scripts/evidence.sh status
./.llm/scripts/evidence.sh predict --task 2026-05-15-example --intent "このタスクで何を変えるか"
```

変更完了後、close 直前に実態と予測を照合する。

```bash
./.llm/scripts/propose-review-packet.sh --task-id 2026-05-15-example
./.llm/scripts/evidence.sh close --task 2026-05-15-example
```

生成された `.llm/work/<task-id>.md` または close の出力を見て、次の `TBD` を埋める。

- Semantic Impact Not Derived By Structure: 構造解析では見えない仕様・運用・意味上の影響
- Unknowns Not Captured By Derivation: 残った未知、QUESTIONS 候補
- Cross-Brick Effects Not In Trace Index: trace index に出ていない cross-brick 影響
- Override / Scope Extension: 導出 scope より広く見るべき理由と追加 evidence
- Remaining Fatigue: 未来に転嫁する確認コスト、expiry、次アクション

該当がなければ空欄にせず、必ず `none` と明示する。空欄と `none` は異なる。空欄は書き忘れ、`none` は確認済みの無である。

## 触らなくてよいもの

- Actual Scope: paths、bricks、projects、public boundaries、archetypes は機械が導出する
- Required Evidence: archetype から機械が選ぶ
- Must Review / Safe To Skim: 導出結果から機械が分類する

LLM がこれらを主観で上書きしてはいけない。導出が不足している場合は Override / Scope Extension に理由と evidence impact を書く。

## Close 前チェック

packet を close する前に、EDN view で residual が明示されていることを確認する。

```bash
./.llm/scripts/check-residual-declared.sh --packet .llm/work/<task-id>.edn
```

`:status :closed` の packet に未宣言 residual が残っている場合、この検査は失敗する。`:status :active` の間は pending field を表示する。

## 次セッションでの扱い

`session-briefing.sh` は Evidence Plane を表示し、`.llm/work/` の active packet と residual pending を冒頭に出す。packet は「書いた人の記録」ではなく、次セッションの LLM が再確認疲労を避けるための inter-session memory である。

## 詳細

¤ .llm/scripts/README.md
¤ .llm/guide/MAINTAINERS_GUIDE.md §5.15
