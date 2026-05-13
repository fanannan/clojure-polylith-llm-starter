# `.llm/scripts/template-tests/`

このディレクトリは、テンプレート自身の E2E シナリオテストを置く。
派生プロジェクトのアプリケーションテストではない。

通常の完了条件では実行しない。generator / checker / migration script を変更した時、またはテンプレート release 前に実行する。

## 実行

```bash
./.llm/scripts/template-tests/check-map-scenarios.sh
```

この検査は `/tmp` に synthetic Polylith-like repos を作成し、`brick.edn` / `project.edn` / generated map の移行・生成・検査・修復シナリオを確認する。

## 位置づけ

- 日常ゲート: `../check-workspace-integrity.sh`
- テンプレート保守 E2E: `./check-map-scenarios.sh`

`check-map-scenarios.sh` は、日常作業の高速ループに入れない。テンプレート配布物の信頼性を確認するための重い保守テストとして扱う。

