# RUNBOOK_STRUCTURAL_EVIDENCE.md — Structural Evidence 操作手順

Structural Evidence View は、LLM が scope と evidence を自己申告する代わりに、git diff、repo-kind、Polylith 構造、生成 index、検査 script から review 用 view を導出する仕組みである。

Structural Evidence View は変更後の close 機構であり、DESIGN 由来 obligation の未完了を選ぶ作業面ではない。Work Frontier / obligation coverage は、既存 check の typed failure を read-only に射影する別の生成 view として保守する。task artifact や task queue を作らない点は Structural Evidence と同じである。
∵ .llm/guide/MAINTAINERS_GUIDE.md §5.16

最低限の mantra:

1. LLM の出力は claim であり、trust ではない。
2. scope と required evidence は、まず機械が導出する。
3. LLM は導出不能な residual だけを `none` または具体 list で明示する。
4. 正本 gate は `check-evidence-gate.sh` であり、hook はその wrapper である。
5. Review Fatigue Packet は正本ではなく、次セッションの足場である。

## 最小手順

迷ったら、まず次の 1 アクションを query する。

```bash
./.llm/scripts/evidence.sh what-now
```

`what-now` は required derived view freshness、active view、human declaration、staged diff、未宣言 residual、未実行 evidence、stale candidate を見て、次に実行すべき command を 1 つ返す。LLM / human は workflow 手順を暗記せず、ここを作業面として使う。

current fingerprint と一致しない generated view は、現在作業の blocker ではなく housekeeping として扱う。fingerprint から外れた human declaration は orphan declaration として surface され、自動削除しない。`what-now` が `detached-active-packet-housekeeping` や `orphan-declaration` を返した場合は、まず表示された `inspect --from .llm/work/views/<task>.edn` または declaration path を確認する。内容確認なしに `declare --all-none` を実行してはならない。

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

save-required な差分で active view が無い場合、gate は `.llm/work/views/<task-id>.edn` と `.md` を自動生成して block する。生成された `.llm/work/views/<task-id>.md` を見て、次の `TBD` を宣言する。

- Semantic Impact Not Derived By Structure: 構造解析では見えない仕様・運用・意味上の影響
- Unknowns Not Captured By Derivation: 残った未知、QUESTIONS 候補
- Cross-Brick Effects Not In Trace Index: trace index に出ていない cross-brick 影響
- Override / Scope Extension: 導出 scope より広く見るべき理由と追加 evidence
- Remaining Fatigue: 未来に転嫁する確認コスト、expiry、次アクション

該当がなければ空欄にせず、必ず `none` と明示する。空欄と `none` は異なる。空欄は書き忘れ、`none` は確認済みの無である。

Residual は EDN を手編集せず、必ず `evidence.sh declare` で更新する。`declare` は `.llm/work/declarations/<task-id>.edn` だけを更新する。`propose-review-packet.sh --staged` を再実行しても、fingerprint が一致する residual declaration だけが view に再付着し、一致しない declaration は orphan として保持される。

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

`run` は active view 内の command-backed evidence を実行し、exit code、repo revision、duration、失敗時の tail を `.llm/work/runs/<task-id>.edn` に記録する。command が定義されていない evidence は `:not-run` として残る。実行コストが高い場合は必要な検査を手動で走らせ、その結果を close 報告に含める。
`run` は login shell を使わず、固定した最小環境で command を実行する。`tool-version` と `env-hash` も record に残るため、T-Mechanical evidence は「どの環境で再実行可能な結果か」を後から確認できる。`tool-version` には実際に選ばれた runtime と、その環境で検出できた `clj` / `bb` の利用可否・バージョンが含まれる。

## Task / Commit / Session

- Task: 1 つの intent と 1 つの close mode を持つ atomic working set。
- Commit: 1 つの task に属する。1 task が複数 commit に分かれることは許容するが、1 commit に複数 task を混ぜない。
- Session: active view / declaration が残っていれば次セッションで resume する。新しい task を始める前に briefing の Evidence Plane と `evidence.sh what-now` を確認し、必要に応じて `status` で詳細を見る。

close が blocked になった場合、`.llm/work/views/<task-id>.md` は blocked-close state で更新される。表示された `declare` コマンドで pending residual を埋め、必要な修正や evidence run を行ってから close を再実行する。

[mandate: M-0023/structural-evidence-boundary type:invariant tier:extended]

Review Fatigue Packet（Structural Evidence packet）と declaration は generated view であり Authority source ではない。packet の中で新しい requirement・decision・knowledge を定義しない。境界規律は 3 検査が守る。`.llm/scripts/check-evidence-gate.sh` が save-required な staged diff を packet / close record の有無で gate し、`.llm/scripts/check-evidence-boundary.sh` が packet 内に新 authority が定義されていないか検査し、`.llm/scripts/check-residual-declared.sh` が residual declaration の充足を検査する。

[/mandate]

close 前や workspace check では次の validator が packet 境界を確認する。

```bash
./.llm/scripts/check-evidence-boundary.sh
```

過去 record の evidence は、`invalidated-by` に記録された path / brick / requirement / public boundary が後続変更に触れた場合、stale candidate として surface される。stale は「即無効」ではなく、「再利用前に再検証すべき」という signal である。

stale / unknown record を一覧する場合:

```bash
./.llm/scripts/evidence.sh stale
```

古い closed record に `invalidated-by` が無い場合は、テンプレート更新時に一度だけ backfill する。

```bash
./.llm/scripts/evidence.sh backfill-invalidated-by --dry-run
./.llm/scripts/evidence.sh backfill-invalidated-by
```

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

packet を close する前に、assembled EDN view で residual が明示されていることを確認する。

```bash
./.llm/scripts/check-residual-declared.sh --packet .llm/work/views/<task-id>.edn
```

closed record に未宣言 residual が残っている場合、この検査は失敗する。`:status :active` の間は pending field を表示する。

## 次セッションでの扱い

`session-briefing.sh` は Evidence Plane を表示し、`.llm/work/views/` の active view、`.llm/work/declarations/` の human declaration、residual pending、closed record、`what-now` を冒頭に出す。generated view は「書いた人の記録」ではなく、次セッションの LLM が再確認疲労を避けるための派生 view であり、human declaration だけを preserve 対象として扱う。

過去の closed record を scope 語彙で探す場合:

```bash
./.llm/scripts/evidence.sh search --scope foo,REQ-001
```

claim の検証状況や支えている証跡を query する場合:

```bash
./.llm/scripts/evidence.sh is-verified REQ-001
./.llm/scripts/evidence.sh why REQ-001
```

## 詳細

¤ .llm/scripts/README.md
¤ .llm/guide/MAINTAINERS_GUIDE.md §5.15
