# MAINTAINER_DOCUMENT_NAMING_MIGRATION.md — 文書命名移行手順

本文書は、repo 全体の文書名を種別が分かる形へ整理するための、テンプレート保守者向け実装手順である。

現時点では置換作業を実行しない。README 整理と方針固定だけを先に行い、実際の rename、生成先変更、migration ledger 更新は別タスクとして扱う。

本文書は一時的な保守手順書である。命名移行が完了した後は、恒久ルールを保守 guide と migration ledger / adoption plan に吸収し、本文書と本文書への参照を削除する。完了後に残すと、実行済み手順が現行 guide のように見えて混乱源になる。

¤ MAINTAINERS_GUIDE.md
¤ ../../README.md
¤ ../../README_WORKFLOW_TOOLCHAIN.md
¤ ../scripts/README.md

---

## 0. 現在の状態

本文書は、移行を安全に実行できる状態へ手順を更新するための足場である。特に mandate-binding 体系の導入後は、単なる Markdown rename でも `.llm/data/mandates.edn` の `:defined-in` や derivation manifest の tool input に波及する。

したがって、次のどちらかで扱う。

- いまは rename を実行しない。本文書だけを更新し、後続タスクの前提を揃える
- rename を実行する場合は、本文書の Phase 0/1/2 に従って mandate index、derived artifact、参照、検査を同一タスクで更新する

本文書を更新せずに旧手順のまま migration を実行しない。`mandates.edn` 再生成漏れや参照取りこぼしが、過去の lib catalog 再生成漏れと同型の事故になる。

## 1. 目的

文書名だけで、おおよその利用者と性質が分かる状態にする。

- ユーザー向け理解補助を、ルートの `README_*.md` に寄せる
- 派生プロジェクト実行時に使う手順を、`RUNBOOK_*.md` として識別できるようにする
- テンプレート保守者だけが使う手順を、`MAINTAINER_*.md` として識別できるようにする
- 自動生成された閲覧用 view を、`GENERATED_VIEW_*.md` として識別できるようにする
- 正本、補助説明、実行手順、生成物の重複を減らす

完全分離を目標にしない。1 つの文書が複数の文脈から読まれることはある。ただし、主たる読者と更新責任は常に 1 つに寄せる。

## 2. 種別の目標

| 種別 | 命名 | 置き場所 | 主な読者 | 直接編集 |
|---|---|---|---|---|
| 入口 | `README.md` | repo root | 初見ユーザー | 可。ただし派生後はプロダクト README |
| ユーザー向け補助説明 | `README_<TOPIC>.md` | repo root | 採用検討者、利用者 | 可 |
| 実行手順 | `RUNBOOK_<TOPIC>.md` | `.llm/guide/` | 派生プロジェクトの作業者、LLM | 可 |
| テンプレート保守手順 | `MAINTAINER_<TOPIC>.md` | `.llm/guide/` | テンプレート保守者 | 可 |
| 概念 guide | `<TOPIC>_GUIDE.md` | `.llm/guide/` | LLM、人間、保守者 | 可 |
| 生成閲覧 view | `GENERATED_VIEW_<TOPIC>.md` | `docs/` | 人間、LLM | 不可 |
| 機械可読 index | `<topic>.edn` | `.llm/data/` | script、LLM | 不可 |

`GUIDE` は概念・方針・判断規律に残す。実行手順と保守移行手順を何でも `GUIDE` にしない。

## 3. 初回候補

| 現在 | 変更後候補 | 種別 | 備考 |
|---|---|---|---|
| `.llm/guide/STRUCTURAL_EVIDENCE_QUICKSTART.md` | `.llm/guide/RUNBOOK_STRUCTURAL_EVIDENCE.md` | 実行手順 | `QUICKSTART` は初回導入感が強く、日常操作面として弱い。M-0023 の mandate annotation を含むため、rename 後に mandates index 再生成が必須 |
| `docs/BRICKS.md` | `docs/GENERATED_VIEW_BRICKS.md` | 生成閲覧 view | 生成器、manifest、検査、参照文書を同時更新。docs は mandate scan 対象外 |
| `docs/PROJECTS.md` | `docs/GENERATED_VIEW_PROJECTS.md` | 生成閲覧 view | workspace map 生成器と同時更新。docs は mandate scan 対象外 |
| `docs/WORKSPACE.md` | `docs/GENERATED_VIEW_WORKSPACE.md` | 生成閲覧 view | workspace map 生成器と同時更新。docs は mandate scan 対象外 |
| `docs/TRACE.md` | `docs/GENERATED_VIEW_TRACE.md` | 生成閲覧 view | trace index 生成器と同時更新。docs は mandate scan 対象外 |
| `.llm/guide/MAINTAINERS_GUIDE.md` | 変更しない | 概念 guide | 保守原則の正本として広すぎるため、初回 rename 対象にしない |
| `README_WORKFLOW_TOOLCHAIN.md` | 変更しない | ユーザー向け補助説明 | `README_*.md` 規約に合う |

## 3.1 mandate / derived artifact との相互作用

### 候補 1: Structural Evidence 操作面

旧名文書には M-0023 / structural-evidence-boundary の authored mandate annotation がある。rename 後も `.llm/guide/*.md` glob に入るため annotation 自体は scan され続け、dangling mandate にはならない。

ただし `.llm/data/mandates.edn` の `:defined-in` は旧名から新名へ変わる。`.llm/guide/` corpus digest も変わるため、次を同一タスクで行う。

- CLAUDE、README、script README、作業ループ説明、保守 guide 内の旧名参照を更新する
- `gen_mandates.clj` と `check_instrument_cases.clj` の scan 範囲が新名文書を拾うことを点検する。`.llm/guide/*.md` のままならコード変更は不要
- `.llm/data/mandates.edn` を再生成する
- `check-derived-artifacts.sh` と `check-instrument-cases-smoke.sh` で drift と mandate trace を見る

### 候補 2-5: docs の generated view

`docs/` 配下は mandate annotation の scan 対象ではないため、annotation 移動は発生しない。M-0016 の prose も `docs/` 配下の閲覧用 Map を総称しているため、本文を編集しない限り mandate prose digest は変わらない。

一方で、生成 view の rename では `derivation_manifest.clj` を更新する。この file は derived artifact freshness の tool input であり、変更後は design IR、obligation index、mandates index まで stale になることがある。Phase 2 では docs の生成器だけでなく、全 derived artifact の再生成を明示的に行う。

## 4. 実装手順

### Phase 0: 棚卸し

対象語彙を repo 全体で洗い出す。

```bash
rg -n "STRUCTURAL_EVIDENCE_QUICKSTART|RUNBOOK_STRUCTURAL_EVIDENCE|M-0023|structural-evidence-boundary"
rg -n "docs/(BRICKS|PROJECTS|WORKSPACE|TRACE)\\.md|BRICKS\\.md|PROJECTS\\.md|WORKSPACE\\.md|TRACE\\.md|GENERATED_VIEW_"
rg -n "mandates\\.edn|gen_mandates|check_instrument_cases|:defined-in|check-instrument-cases"
rg -n "README_WORKFLOW_TOOLCHAIN|QUICKSTART|RUNBOOK_|MAINTAINER_"
```

棚卸しでは、Markdown 参照だけでなく、Clojure generator、shell wrapper、derivation manifest、mandate generator、instrument case checker、check script、template-only test、README の索引も含める。`.llm/data/mandates.edn` の hit は手編集対象ではなく、再生成対象の発見として扱う。

### Phase 1: 実行手順名を先に分ける

Structural Evidence の操作面を rename する場合は、まず文書 rename と参照更新だけを 1 commit に閉じる。

```bash
git mv .llm/guide/STRUCTURAL_EVIDENCE_QUICKSTART.md .llm/guide/RUNBOOK_STRUCTURAL_EVIDENCE.md
rg -n "STRUCTURAL_EVIDENCE_QUICKSTART" .
rg -n "M-0023|structural-evidence-boundary|RUNBOOK_STRUCTURAL_EVIDENCE|:defined-in" .llm/guide .llm/data .llm/scripts .llm/template-only CLAUDE.md README.md
```

更新対象は少なくとも、日常作業規約、README、作業ループ説明、script README、保守 guide、Structural Evidence script の help / output である。

mandate 体系の追加対象:

- `gen_mandates.clj` の corpus scan が rename 後の文書を拾うこと。`.llm/guide/*.md` のままなら実装変更不要
- `check_instrument_cases.clj` の authored mandate scan が rename 後の文書を拾うこと。`.llm/guide/*.md` のままなら実装変更不要
- M-0023 が新名文書に残っていること
- `.llm/data/mandates.edn` の `:defined-in` を手編集せず、generator で更新すること

再生成と検査:

```bash
./.llm/scripts/gen-mandates.sh
./.llm/scripts/check-derived-artifacts.sh
./.llm/template-only/tests/check-instrument-cases-smoke.sh
./.llm/scripts/check-doc-references.sh --all
```

### Phase 2: 生成 view 名を分ける

生成 view は、手作業 rename だけでは不十分である。生成元の定数、manifest、検査、再生成コマンド、文書導線を同時に更新する。

対象:

- `gen_brick_map.clj`
- `gen_workspace_map.clj`
- `gen_trace_index.clj`
- `derivation_manifest.clj`
- `gen_mandates.clj`
- `check-brick-map.sh`
- `check-workspace-map.sh`
- `check-trace-index.sh`
- `check-workspace-integrity.sh`
- `session-briefing.sh`
- `check-derived-artifacts.sh`
- template-only map / trace scenario tests

この phase では古い `docs/*.md` を残して二重生成しない。互換性が必要な場合は migration ledger と adoption plan に明記し、alias を一時措置として期限付きで置く。

`derivation_manifest.clj` を変更した後は、docs view 以外の derived artifact も stale になり得る。次の順で再生成し、どれかが不要なら check 側の出力で判断する。

```bash
./.llm/scripts/gen-design-ir.sh
./.llm/scripts/gen-obligation-index.sh
./.llm/scripts/gen-mandates.sh
clj -Sdeps '{:paths [".llm/scripts"]}' -X gen-brick-map/generate
clj -Sdeps '{:paths [".llm/scripts"]}' -X gen-workspace-map/generate
./.llm/scripts/gen-trace-index.sh
./.llm/scripts/check-derived-artifacts.sh
```

M-0016 の本文は `docs/` 配下の閲覧用 Map を総称している。名称変更だけなら M-0016 prose の編集は不要だが、生成物の範囲や freshness 規律を変えるなら mandate prose と `.llm/data/mandates.edn` も同時に更新する。

### Phase 3: 派生プロジェクト移行を定義する

テンプレートだけでなく派生プロジェクトも影響を受ける rename は、migration ledger に entry を追加する。adoption plan は、古いファイルが存在する repo に対して候補を表示し、人間承認後に適用する。

確認点:

- 旧名ファイルが手編集されていないか
- 生成 manifest が旧名を指していないか
- `.llm/data/*.manifest.edn` が新名を指すか
- README や guide の参照が旧名を残していないか
- pre-commit hook が新名で gate するか

### Phase 4: 検証して evidence を閉じる

最低限の検査:

```bash
./.llm/scripts/check-doc-references.sh --all
./.llm/scripts/check-mode-scope.sh
./.llm/scripts/check-workspace-integrity.sh
./.llm/scripts/check-derived-artifacts.sh
./.llm/scripts/check-evidence-gate.sh --staged
```

生成器を変更した場合は、該当する template-only E2E を追加する。

```bash
./.llm/template-only/tests/check-map-scenarios.sh
./.llm/template-only/tests/check-trace-metadata-scenarios.sh
./.llm/template-only/tests/check-session-briefing-scenarios.sh
./.llm/template-only/tests/check-instrument-cases-smoke.sh
```

## 5. 失敗しやすい点

- README の索引だけを直し、生成器や check script の出力先を残す
- `docs/` の generated view を手編集してしまう
- `.llm/data/mandates.edn` を手編集してしまう。mandate index は生成物なので `gen-mandates.sh` で再生成する
- M-0023 の `:defined-in` が変わることを見落とし、mandates index の再生成を忘れる
- `derivation_manifest.clj` 変更後の design IR / obligation index / mandates index の stale cascade を見落とす
- old / new の両方を残し、どちらが正本か不明にする
- template repo の保守判断を project ADR に記録する
- `GUIDE` を別名に置換すること自体が目的になり、読者種別が曖昧なまま増える
- migration ledger を更新せず、派生プロジェクトへの取り込み手順が会話に散逸する

## 6. 完了条件

- 文書名から、ユーザー向け補助説明、実行手順、保守手順、生成 view が識別できる
- 旧名参照が repo 全体に残っていない
- 生成器と manifest と check script が新名で一致している
- 派生プロジェクト向け migration / adoption plan がある
- Structural Evidence close record が、実行した検査と残った未知を保持している
- 恒久ルールが保守 guide / migration ledger / adoption plan に吸収され、本文書と本文書への参照を削除できる
