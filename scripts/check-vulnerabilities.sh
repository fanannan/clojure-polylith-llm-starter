#!/usr/bin/env bash
# scripts/check-vulnerabilities.sh
#
# _POSSIBLE_ISSUES.md F 系拡張（サードパーティ追加）の実装。
# 本テンプレートの §2 依存承認規律を時間軸に拡張する機械化:
# 承認済み依存が後から脆弱性を抱えた場合の自動検知。
#
# 目的:
#   NIST NVD + GitHub Advisory Database で deps.edn の依存（直接・推移的）を
#   脆弱性スキャンする。`clj-watson` をラップ。
#
# 運用タイミング（完了条件には含めない、長時間実行のため）:
#   - 週次 CI
#   - release 前（必須、MAINTAINERS_GUIDE.md §5.12 参照）
#   - 重大な依存変更後
#
# NVD API key:
#   無料で https://nvd.nist.gov/developers/request-an-api-key から取得可能。
#   2026 年時点でレート制限の観点から推奨。環境変数 NVD_API_KEY で渡せる。
#   未設定でも動作するが、スキャンが遅くなる。
#
# 終了コード:
#   0: 既知の脆弱性なし
#   1: 脆弱性あり（--fail-on-result）

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$WORKSPACE_ROOT"

if [ -z "${NVD_API_KEY:-}" ]; then
  echo "WARNING: NVD_API_KEY 環境変数が未設定です。スキャンが遅くなる可能性があります。"
  echo "         https://nvd.nist.gov/developers/request-an-api-key から取得してください。"
  echo ""
fi

echo "依存脆弱性スキャン中（clj-watson、初回は数分かかる場合あり）..."

exec clj -M:vulnerability-check
