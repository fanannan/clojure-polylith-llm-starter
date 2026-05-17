# `.llm/template-only/tests/`

このディレクトリは、テンプレート自身の E2E シナリオテストを置く。
派生プロジェクトのアプリケーションテストではない。

通常の完了条件では実行しない。generator / checker / migration script を変更した時、またはテンプレート release 前に実行する。
テンプレート保守 mode では `session-briefing.sh` が L0 の提言として、現在の git diff に対応する起動候補を `L0 Template Test Recommendation（テンプレート保守テスト提言）` に表示する。

## 実行

```bash
./.llm/template-only/tests/check-map-scenarios.sh
./.llm/template-only/tests/check-design-ir-scenarios.sh
./.llm/template-only/tests/check-trace-metadata-scenarios.sh
./.llm/template-only/tests/check-obligation-frontier-scenarios.sh
./.llm/template-only/tests/check-session-briefing-scenarios.sh
./.llm/template-only/tests/check-instrument-cases-smoke.sh
./.llm/template-only/tests/check-instrument-setup-smoke.sh
./.llm/template-only/tests/check-instrument-summary-smoke.sh
./.llm/template-only/tests/check-benchmark-setup-smoke.sh
```

この検査は `/tmp` に synthetic Polylith-like repos を作成し、`brick.edn` / `project.edn` / generated map の移行・生成・検査・修復シナリオを確認する。
DESIGN IR 検査は `/tmp` に synthetic repos を作成し、DESIGN 抽出・既存分析 EDN 連携・stale IR 検出を確認する。
trace metadata 検査は `/tmp` に synthetic repos を作成し、public boundary / deftest への trace metadata と誤配置検出を確認する。
obligation frontier 検査は `/tmp` に synthetic repos を作成し、DESIGN obligation が missing-boundary / missing-test として赤くなり、boundary/test trace の追加で消えること、§2.2 / §10 由来 disposition が complete になり、backing のない disposition override と存在しない Q 参照が red になり、open Q 参照が accounted になることを確認する。
session briefing 検査は `/tmp` に synthetic repos を作成し、manifest missing / template clean / template conflict / project bootstrap / project development の各 mode で `Control Plane` の key phrase と forbidden phrase、`--audit --format edn` の構造を確認する。LLM は呼ばず、briefing という教材自体が壊れていないかだけを検査する。
instrument cases smoke は live `cases.edn` / `incident-index.edn` を Clojure EDN として読み、case family / target mode / prompt / observable expectations / incident trace の不変条件を検査する。さらに synthetic bad fixtures で unknown incident と untraced non-exploratory case が失敗することを確認する。
instrument setup smoke は `/tmp` に template / project target repo を作成し、`.llm/template-only/instrument/` や observer store が target repo に混入しないこと、capture / terminal marker が outside observer store に記録できること、path-level scorer が hard fail / expected stop を判別し template run record に mirror することを検査する。LLM は呼ばず、model score は出さない。
instrument summary smoke は synthetic `score.edn` を複数作成し、summary が N / invalid 数 / result 分布を出し、割れた結果を平均せず `:spec-ambiguous` へ route することを検査する。
benchmark setup smoke は `/tmp` に demo repo を作成し、benchmark harness が人間なしで準備・marker 記録まで自走できることだけを確認する。observer record が demo repo の外側にあり、demo repo の commit tree に benchmark protocol が混入しないことも検査する。これは benchmark evidence ではない。

Instruction-Following Instrument は、ここにある deterministic test だけでは完結しない。`check-session-briefing-scenarios.sh` は LLM を呼ぶ前の教材品質検査であり、agent 行動の採点ではない。将来の contract case は、maintainer archive の実インシデントまたは md mandate に trace し、根拠のない合成 fixture は exploratory bucket に隔離する。

## 位置づけ

- 日常ゲート: `.llm/scripts/check-workspace-integrity.sh`
- テンプレート保守 E2E: `./check-map-scenarios.sh`, `./check-design-ir-scenarios.sh`, `./check-trace-metadata-scenarios.sh`, `./check-obligation-frontier-scenarios.sh`, `./check-session-briefing-scenarios.sh`, `./check-instrument-cases-smoke.sh`, `./check-instrument-setup-smoke.sh`, `./check-instrument-summary-smoke.sh`, `./check-benchmark-setup-smoke.sh`

`check-map-scenarios.sh`、`check-design-ir-scenarios.sh`、`check-trace-metadata-scenarios.sh`、`check-obligation-frontier-scenarios.sh`、`check-session-briefing-scenarios.sh`、`check-instrument-cases-smoke.sh`、`check-instrument-setup-smoke.sh`、`check-instrument-summary-smoke.sh`、`check-benchmark-setup-smoke.sh` は、日常作業の高速ループに入れない。テンプレート配布物の信頼性を確認するための重い保守テストとして扱う。

L0 の提言は advisory であり、自動 gate ではない。該当する test が表示された場合は、bounded task の task-specific check として実行し、Structural Evidence close 時の検証結果に含める。表示がない場合でも、変更内容から必要な template-only test が分かるなら手動で追加する。

## 常備する観点

- 欠落 metadata の skeleton 生成
- TODO / partial / complete の severity 切替
- DESIGN requirement ID の unknown / duplicate / unassigned
- fenced code block 内の DESIGN requirement ID 例示は定義として扱わないこと
- base と component の capability 整合
- public API 名と capability の対応
- missing / empty `interface.clj`
- broken `brick.edn` / `project.edn`
- project entrypoint / includes / deps の整合
- project type vocabulary は `:app` / `:library` に限定し、runtime は任意補助に留めること
- project が capability ownership を持たないこと
- generated docs / index の drift と再生成
- DESIGN IR の requirement / use case / constraint / test obligation 抽出
- DESIGN IR と brick-map / workspace-map / libs の連携
- stale design-ir の検出と再生成
- constraint ID と実装 requirement ID の coverage 分離
- test obligation の明示 ID / hash ID / 重複検出
- test obligation から related requirement / use case trace を抽出し、未知参照を diagnostics に出すこと
- trace metadata は public boundary `defn` と `deftest` にだけ許可すること
- trace metadata の未知 ID と test obligation の誤配置を検出すること
- trace metadata の空 ID、重複 ID、base 内部誤配置、`:adoption-mode :complete` の未対応 obligation error、related IDs 不整合を検出すること
- Work Frontier が missing-boundary / missing-test を表示し、boundary + deftest trace が揃うと frontier から消えること
- DESIGN §2.2 / §10 由来の `:out-of-scope` / `:deferred` は complete、backing のない disposition override は `:unbacked-disposition` になること
- open Q 参照は `:blocked-by-question`、resolved Q 参照は通常評価へ復帰、存在しない Q 参照は `:unresolved-blocker` になること
- session briefing が mode / ownership / next action surface / completion gate を `Control Plane` として前方に表示し、`repo-control.sh` 等の競合 surface を案内しないこと
- `session-briefing.sh --audit --format edn` が audit EDN だけを返し、Control Plane の位置・bullet 数・next action surface・forbidden surface・budget を確認できること
- 指示追随計測器の `cases.edn` が valid EDN であり、non-exploratory case が既知 incident または authored mandate に trace すること
- 指示追随計測器の case seed は実インシデントまたは md mandate に trace し、割れた結果を平均せず `:spec-ambiguous` として文書改善候補に回すこと
- instrument setup が target repo から `.llm/template-only/instrument/` を除去し、project target では `.llm/template-only/` 全体を除去し、observer store を target repo の外に置くこと
- instrument summary が単一の pass rate を作らず、N / invalid 数 / result 分布を保持すること
- benchmark setup が demo repo から `.llm/template-only/` を除去し、post-commit snapshot、simulation approval、terminal marker を記録できること
- benchmark protocol / run record が demo repo の commit tree に入らず、observer hook の無効化と terminal marker 重複を検出できること
