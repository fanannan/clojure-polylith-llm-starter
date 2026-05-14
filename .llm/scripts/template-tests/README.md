# `.llm/scripts/template-tests/`

このディレクトリは、テンプレート自身の E2E シナリオテストを置く。
派生プロジェクトのアプリケーションテストではない。

通常の完了条件では実行しない。generator / checker / migration script を変更した時、またはテンプレート release 前に実行する。

## 実行

```bash
./.llm/scripts/template-tests/check-map-scenarios.sh
./.llm/scripts/template-tests/check-design-ir-scenarios.sh
```

この検査は `/tmp` に synthetic Polylith-like repos を作成し、`brick.edn` / `project.edn` / generated map の移行・生成・検査・修復シナリオを確認する。
DESIGN IR 検査は `/tmp` に synthetic repos を作成し、DESIGN 抽出・既存分析 EDN 連携・stale IR 検出を確認する。

## 位置づけ

- 日常ゲート: `../check-workspace-integrity.sh`
- テンプレート保守 E2E: `./check-map-scenarios.sh`, `./check-design-ir-scenarios.sh`

`check-map-scenarios.sh` と `check-design-ir-scenarios.sh` は、日常作業の高速ループに入れない。テンプレート配布物の信頼性を確認するための重い保守テストとして扱う。

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
