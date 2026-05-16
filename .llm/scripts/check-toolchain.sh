#!/usr/bin/env bash
# .llm/scripts/check-toolchain.sh
#
# 前提ツール preflight doctor。
#
# 目的:
#   初期化に着手する前に一度だけ実行し、OS レベルのインストールが必要な
#   実行ファイルの過不足を検出して、不足分の導入方法を提案する。
#   検出と提案のみを行い、インストールは自動実行しない。
#
# 検査対象（OS レベルの実行ファイルだけ）:
#   - Java (JVM)   必須。clj / Polylith / 全ツールの実行基盤
#   - clj          必須。Clojure CLI (tools.deps)
#   - git          必須。session briefing / Structural Evidence / pre-commit gate が使う
#   - bb           任意。Babashka。.llm/scripts の optional accelerator（不在は正常状態）
#
# 検査対象に含めない理由:
#   clj-kondo / cljfmt / Splint / clj-watson / Polylith は deps.edn の
#   tools.deps alias であり、clj が Maven / Clojars / git から取得する。
#   OS レベルの個別インストールは不要なので、本 doctor は検査しない。
#
# 役割と非役割:
#   - 副作用なし（stdout のみ）。初期化開始時に一度だけ実行する preflight
#   - 完了条件 / check-workspace-integrity.sh / session-briefing.sh には
#     組み込まない（per-machine で一度きりの確認であり、毎回のゲートではない）
#
# 終了コード:
#   0  必須ツール (Java / clj / git) がすべて揃っている
#   1  必須ツールが不足、または Java が古い
#
# 参照: BOOTSTRAP_GUIDE.md §0

set -euo pipefail

# Java の下限メジャーバージョン。modern tools.deps / Polylith 前提で 11 を下限とする。
readonly JAVA_MIN_MAJOR=11

# --- OS / パッケージマネージャ判定 ----------------------------------------

os_kind() {
  case "$(uname -s)" in
    Darwin) echo "darwin" ;;
    Linux)  echo "linux" ;;
    *)      echo "other" ;;
  esac
}

linux_pkg() {
  if   command -v apt-get >/dev/null 2>&1; then echo "apt"
  elif command -v dnf     >/dev/null 2>&1; then echo "dnf"
  elif command -v pacman  >/dev/null 2>&1; then echo "pacman"
  elif command -v zypper  >/dev/null 2>&1; then echo "zypper"
  else echo "unknown"
  fi
}

readonly OS="$(os_kind)"

# --- 補助関数 --------------------------------------------------------------

# 文字列が非負整数だけで構成されているか判定する。`[ -ge ]` の前段ガード。
is_numeric() {
  case "${1:-}" in
    ''|*[!0-9]*) return 1 ;;
    *)           return 0 ;;
  esac
}

# `java -version` の最初の行から引用符内のバージョン文字列を取り出す。
# 例: `openjdk version "21.0.2" ...` → 21.0.2 / `java version "1.8.0_392"` → 1.8.0_392
java_version_string() {
  local out first
  out="$(java -version 2>&1 || true)"
  first="${out%%$'\n'*}"
  printf '%s' "${first}" | sed -nE 's/.*version "([^"]+)".*/\1/p'
}

# バージョン文字列からメジャー番号を取り出す。`1.8.0_392` → 8 / `21.0.2` → 21
major_of() {
  case "${1:-}" in
    1.*) printf '%s' "${1:-}" | cut -d. -f2 ;;
    *)   printf '%s' "${1:-}" | cut -d. -f1 ;;
  esac
}

# 不足している必須ツール名を蓄積する。
MISSING=()

# --- 検出 ------------------------------------------------------------------

echo "== 前提ツール preflight (check-toolchain.sh) =="
echo "OS: ${OS}"
echo
echo "-- 検出結果 --"

# Java（必須）
if command -v java >/dev/null 2>&1; then
  jver="$(java_version_string)"
  jmaj="$(major_of "${jver}")"
  if is_numeric "${jmaj}" && [ "${jmaj}" -ge "${JAVA_MIN_MAJOR}" ]; then
    echo "  [OK]    Java  ${jver:-?}  (major ${jmaj})"
  else
    echo "  [古い]  Java  ${jver:-?}  — major ${JAVA_MIN_MAJOR} 以上が必要"
    MISSING+=("java")
  fi
else
  echo "  [不足]  Java  — 見つかりません（必須）"
  MISSING+=("java")
fi

# clj（必須）
if command -v clj >/dev/null 2>&1; then
  desc="$(clj -Sdescribe 2>/dev/null || true)"
  cljver="$(printf '%s' "${desc}" | sed -nE 's/.*:version "([^"]+)".*/\1/p')"
  echo "  [OK]    clj   ${cljver:-(バージョン不明)}"
else
  echo "  [不足]  clj   — 見つかりません（必須・Clojure CLI / tools.deps）"
  MISSING+=("clj")
fi

# git（必須）
if command -v git >/dev/null 2>&1; then
  echo "  [OK]    git   $(git --version 2>/dev/null | sed -E 's/^git version //')"
else
  echo "  [不足]  git   — 見つかりません（必須）"
  MISSING+=("git")
fi

# bb（任意。不在は正常状態であり MISSING に数えない）
if command -v bb >/dev/null 2>&1; then
  echo "  [OK]    bb    $(bb --version 2>/dev/null)  (任意)"
else
  echo "  [任意]  bb    — 見つかりません（任意。あれば .llm/scripts を高速化）"
fi

# --- 不足分の導入提案 ------------------------------------------------------

# 不足ツールごとに OS 別の導入方法を提案する（インストールは実行しない）。
suggest() {
  case "$1" in
    java)
      echo "  ● Java (JVM, LTS 推奨):"
      case "${OS}" in
        darwin) echo "      brew install --cask temurin" ;;
        linux)
          case "$(linux_pkg)" in
            apt)    echo "      sudo apt-get install -y openjdk-21-jdk" ;;
            dnf)    echo "      sudo dnf install -y java-21-openjdk-devel" ;;
            pacman) echo "      sudo pacman -S --needed jdk-openjdk" ;;
            zypper) echo "      sudo zypper install -y java-21-openjdk-devel" ;;
            *)      echo "      ディストリのパッケージマネージャで OpenJDK 21 (LTS) を導入" ;;
          esac ;;
        *) echo "      本テンプレートは Unix (macOS / Linux) 前提です" ;;
      esac
      echo "      代替: SDKMAN (https://sdkman.io) / Adoptium (https://adoptium.net)"
      ;;
    clj)
      echo "  ● clj (Clojure CLI):"
      case "${OS}" in
        darwin) echo "      brew install clojure/tools/clojure" ;;
        linux)  echo "      公式 linux-install スクリプトを使用（要 Java / curl / rlwrap）" ;;
        *)      echo "      本テンプレートは Unix (macOS / Linux) 前提です" ;;
      esac
      echo "      公式手順: https://clojure.org/guides/install_clojure"
      ;;
    git)
      echo "  ● git:"
      case "${OS}" in
        darwin) echo "      xcode-select --install   または   brew install git" ;;
        linux)
          case "$(linux_pkg)" in
            apt)    echo "      sudo apt-get install -y git" ;;
            dnf)    echo "      sudo dnf install -y git" ;;
            pacman) echo "      sudo pacman -S --needed git" ;;
            zypper) echo "      sudo zypper install -y git" ;;
            *)      echo "      ディストリのパッケージマネージャで git を導入" ;;
          esac ;;
        *) echo "      本テンプレートは Unix (macOS / Linux) 前提です" ;;
      esac
      ;;
  esac
}

echo
if [ "${#MISSING[@]}" -eq 0 ]; then
  echo "-- 結果 --"
  echo "  必須ツール (Java / clj / git) はすべて揃っています。初期化を進められます。"
  echo "  （clj-kondo / cljfmt / Splint / clj-watson / Polylith は deps.edn の"
  echo "    tools.deps alias です。clj が取得するため個別インストールは不要。）"
  exit 0
fi

echo "-- 不足ツールと導入提案（インストールは自動実行しません） --"
for tool in "${MISSING[@]}"; do
  suggest "${tool}"
  echo
done
echo "導入後、再度このスクリプトを実行して確認してください。"
exit 1
