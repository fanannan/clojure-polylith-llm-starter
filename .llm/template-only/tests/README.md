# `.llm/template-only/tests/`

このディレクトリは、テンプレート自身の E2E シナリオテストを置く。
派生プロジェクトのアプリケーションテストではない。

通常の完了条件では実行しない。generator / checker / migration script を変更した時、またはテンプレート release 前に実行する。

## 実行

```bash
./.llm/template-only/tests/check-map-scenarios.sh
./.llm/template-only/tests/check-design-ir-scenarios.sh
./.llm/template-only/tests/check-trace-metadata-scenarios.sh
./.llm/template-only/tests/check-benchmark-setup-smoke.sh
```

この検査は `/tmp` に synthetic Polylith-like repos を作成し、`brick.edn` / `project.edn` / generated map の移行・生成・検査・修復シナリオを確認する。
DESIGN IR 検査は `/tmp` に synthetic repos を作成し、DESIGN 抽出・既存分析 EDN 連携・stale IR 検出を確認する。
trace metadata 検査は `/tmp` に synthetic repos を作成し、public boundary / deftest への trace metadata と誤配置検出を確認する。
benchmark setup smoke は `/tmp` に demo repo を作成し、benchmark harness が人間なしで準備・marker 記録まで自走できることだけを確認する。これは benchmark evidence ではない。

## 位置づけ

- 日常ゲート: `.llm/scripts/check-workspace-integrity.sh`
- テンプレート保守 E2E: `./check-map-scenarios.sh`, `./check-design-ir-scenarios.sh`, `./check-trace-metadata-scenarios.sh`, `./check-benchmark-setup-smoke.sh`

`check-map-scenarios.sh`、`check-design-ir-scenarios.sh`、`check-trace-metadata-scenarios.sh`、`check-benchmark-setup-smoke.sh` は、日常作業の高速ループに入れない。テンプレート配布物の信頼性を確認するための重い保守テストとして扱う。

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
- benchmark setup が demo repo から `.llm/template-only/` を除去し、post-commit snapshot、simulation approval、terminal marker を記録できること
