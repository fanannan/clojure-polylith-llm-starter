#!/usr/bin/env bash
# scripts/check-vulnerabilities.sh
#
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
# NVD API key（必須、2026 年時点で clj-watson が強制）:
#   1. https://nvd.nist.gov/developers/request-an-api-key から無料取得
#   2. 環境変数 NVD_API_KEY に設定:
#        export NVD_API_KEY="xxxx-xxxx-xxxx-xxxx"
#      または `direnv` / `.envrc` で管理
#
# 緊急回避（非推奨、CI では使わない）:
#   NVD_API_KEY 未設定でも強制実行したい場合:
#     FORCE_NO_NVD_KEY=1 ./scripts/check-vulnerabilities.sh
#   NVD データ更新が遅くなり、スキャン精度が落ちる可能性あり。
#
# 終了コード:
#   0: 既知の脆弱性なし
#   1: 脆弱性あり、または NVD_API_KEY 未設定（FORCE_NO_NVD_KEY=1 未指定時）

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$WORKSPACE_ROOT"

if [ -z "${NVD_API_KEY:-}" ]; then
  if [ "${FORCE_NO_NVD_KEY:-}" != "1" ]; then
    cat >&2 <<'EOF'
ERROR: NVD_API_KEY 環境変数が未設定です。

clj-watson (v6+) は NIST NVD API の利用制限のため、API key の設定を強く要求します。

対処:
  1. 無料取得: https://nvd.nist.gov/developers/request-an-api-key
  2. 環境変数設定:
       export NVD_API_KEY="xxxx-xxxx-xxxx-xxxx"
     または direnv / .envrc で管理

緊急回避（非推奨、CI では使わない）:
  FORCE_NO_NVD_KEY=1 ./scripts/check-vulnerabilities.sh
EOF
    exit 1
  fi
  echo "WARNING: NVD_API_KEY 未設定ですが FORCE_NO_NVD_KEY=1 で続行します（精度低下あり）。"
  echo ""
  echo "依存脆弱性スキャン中（clj-watson、API key なし、時間がかかります）..."
  exec clj -M:vulnerability-check --run-without-nvd-api-key
fi

echo "依存脆弱性スキャン中（clj-watson、NVD API key あり）..."
exec clj -M:vulnerability-check --nvd-api-key "$NVD_API_KEY"
