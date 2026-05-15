# Structural Evidence Quickstart

Structural Evidence View は、LLM が scope と evidence を自己申告する代わりに、git diff、repo-kind、Polylith 構造、生成 index、検査 script から review 用 view を導出する仕組みである。

最低限の mantra:

1. LLM の出力は claim であり、trust ではない。
2. scope と required evidence は、まず機械が導出する。
3. LLM は導出不能な residual だけを `none` または具体 list で明示する。
4. 正本 gate は `check-evidence-gate.sh` であり、hook はその wrapper である。
5. Review Fatigue Packet は正本ではなく、次セッションの足場である。

## 最小手順

Git hook を使う場合は一度だけ有効化する。Claude Code / Codex / human で同じ hook が使われる。

```bash
./.llm/scripts/install-git-hooks.sh
```

通常は着手前の `predict` を必須にしない。staged diff を正本として gate する。

```bash
./.llm/scripts/evidence.sh status
```

変更後、commit 前に staged diff を gate する。

```bash
git add <changed-files>
./.llm/scripts/check-evidence-gate.sh --staged
```

save-required な差分で packet が無い場合、gate は `.llm/work/<task-id>.edn` と `.md` を自動生成して block する。生成された `.llm/work/<task-id>.md` を見て、次の `TBD` を宣言する。

- Semantic Impact Not Derived By Structure: 構造解析では見えない仕様・運用・意味上の影響
- Unknowns Not Captured By Derivation: 残った未知、QUESTIONS 候補
- Cross-Brick Effects Not In Trace Index: trace index に出ていない cross-brick 影響
- Override / Scope Extension: 導出 scope より広く見るべき理由と追加 evidence
- Remaining Fatigue: 未来に転嫁する確認コスト、expiry、次アクション

該当がなければ空欄にせず、必ず `none` と明示する。空欄と `none` は異なる。空欄は書き忘れ、`none` は確認済みの無である。

Residual は EDN を手編集せず、必ず `evidence.sh declare` で更新する。active packet がまだ無い場合でも、同じ task の `.predict.edn` があれば `declare` が `.llm/work/<task-id>.edn` を安全に作成する。`propose-review-packet.sh --staged` を再実行しても既存の residual declaration は保持される。

全て `none` として宣言できる場合:

```bash
./.llm/scripts/evidence.sh declare --task 2026-05-15-example --all-none
```

個別に宣言する場合:

```bash
./.llm/scripts/evidence.sh declare --task 2026-05-15-example \
  --semantic-impact "none" \
  --unknowns "none" \
  --cross-brick-effects "none" \
  --override "none" \
  --remaining-fatigue "none"
```

宣言後に close する。

```bash
./.llm/scripts/evidence.sh run --task 2026-05-15-example
./.llm/scripts/evidence.sh close --task 2026-05-15-example --staged
./.llm/scripts/check-evidence-gate.sh --staged
```

`run` は packet 内の command-backed evidence を実行し、exit code、repo revision、duration、失敗時の tail を active packet に記録する。command が定義されていない evidence は `:not-run` として残る。実行コストが高い場合は必要な検査を手動で走らせ、その結果を close 報告に含める。

close が blocked になった場合でも、`.llm/work/<task-id>.edn` と `.llm/work/<task-id>.md` は最新の actual scope / blocked-close state で更新される。表示された `declare` コマンドで pending residual を埋め、必要な修正を行ってから close を再実行する。

高リスク作業や大きい変更では、着手前に任意で `predict` を使う。

```bash
./.llm/scripts/evidence.sh predict --task 2026-05-15-example --intent "このタスクで何を変えるか"
```

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

過去の closed record を scope 語彙で探す場合:

```bash
./.llm/scripts/evidence.sh search --scope foo,REQ-001
```

## 詳細

¤ .llm/scripts/README.md
¤ .llm/guide/MAINTAINERS_GUIDE.md §5.15
