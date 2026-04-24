#!/usr/bin/env bash
# scripts/repl-eval.sh
#
# LLM 向け nREPL client wrapper. 稼働中の long-lived JVM に eval / load-file を送る。
# CLAUDE.md §9 Live Workbench Protocol の primary 面。
#
# Usage:
#   ./.llm/scripts/repl-eval.sh --expr '(+ 1 2)'
#   ./.llm/scripts/repl-eval.sh --load-file components/user/src/poly/user/core.clj
#   ./.llm/scripts/repl-eval.sh --ns poly.user.interface --expr '(create-user {:name "t"})'
#   echo '(+ 1 2)' | ./.llm/scripts/repl-eval.sh         # stdin fallback
#   ./.llm/scripts/repl-eval.sh --interrupt              # 直近 eval を中断
#   ./.llm/scripts/repl-eval.sh --describe               # server ops / versions
#   ./.llm/scripts/repl-eval.sh --reset-session          # 永続 session 破棄
#   ./.llm/scripts/repl-eval.sh --fresh --expr '(pure-check)'  # ephemeral session
#
# Exit codes:
#   0   成功
#   1   eval-error / namespace-not-found / :ex
#   2   接続エラー・必須 op 欠落・引数不正
#   130 interrupted (UNIX 慣例)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$WORKSPACE_ROOT"

CMD="eval"
NS="dev.user"
NS_EXPLICIT=0
EXPR=""
EXPR_SET=0       # --expr / 位置引数 / stdin のいずれでセットされたら 1
LOAD=""
FRESH=""

usage() {
  awk '/^# Usage:/,/^$/' "$0" | grep -v '^$'
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --expr)          EXPR="$2"; EXPR_SET=1; shift 2 ;;
    --load-file)     LOAD="$2"; CMD="load"; shift 2 ;;
    --ns)            NS="$2"; NS_EXPLICIT=1; shift 2 ;;
    --interrupt)     CMD="interrupt"; shift ;;
    --describe)      CMD="describe"; shift ;;
    --reset-session) CMD="reset-session"; shift ;;
    --fresh)         FRESH=":fresh true"; shift ;;
    -h|--help)       usage; exit 0 ;;
    --*)             echo "ERROR: unknown option: $1" >&2; usage >&2; exit 2 ;;
    *)
      if [[ "$EXPR_SET" -eq 0 && "$CMD" == "eval" ]]; then
        EXPR="$1"; EXPR_SET=1; shift
      else
        echo "ERROR: unexpected argument: $1" >&2; usage >&2; exit 2
      fi
      ;;
  esac
done

# T1.5: --ns + --load-file の警告
if [[ "$CMD" == "load" && "$NS_EXPLICIT" -eq 1 ]]; then
  echo "WARNING: --ns は --load-file では無視されます (nREPL load-file op は ns を取らない)" >&2
fi

# stdin fallback（--expr '' のような明示的空文字指定は EXPR_SET=1 で区別される）
if [[ "$CMD" == "eval" && "$EXPR_SET" -eq 0 && -z "$LOAD" ]]; then
  if [[ -t 0 ]]; then
    echo "ERROR: --expr / --load-file / stdin のいずれか必須" >&2
    usage >&2
    exit 2
  fi
  EXPR="$(cat)"
  EXPR_SET=1
fi

# exec-args を構築
args=":command :$CMD"
[[ -n "$FRESH" ]] && args="$args $FRESH"

case "$CMD" in
  eval)
    # code は tempfile 経由で渡す (shell quoting 回避)
    tmp=$(mktemp)
    trap 'rm -f "$tmp"' EXIT
    printf '%s\n' "$EXPR" > "$tmp"
    args="$args :code-file \"$tmp\" :ns \"$NS\""
    ;;
  load)
    if [[ ! -f "$LOAD" ]]; then
      echo "ERROR: load-file 対象が存在しません: $LOAD" >&2
      exit 2
    fi
    args="$args :load-file \"$LOAD\""
    ;;
  interrupt|describe|reset-session)
    : # 追加引数不要
    ;;
esac

# shellcheck disable=SC2086
clj -X:repl-eval $args
