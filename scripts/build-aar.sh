#!/usr/bin/env bash
# Build the :logdog library AAR into dist/
#
# Usage:
#   ./scripts/build-aar.sh              # release (default)
#   ./scripts/build-aar.sh release
#   ./scripts/build-aar.sh debug
#   VERSION=1.2.0 ./scripts/build-aar.sh release
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

BUILD_TYPE="${1:-release}"
VERSION="${VERSION:-1.0.0}"
OUT_DIR="${OUT_DIR:-dist}"

case "$BUILD_TYPE" in
  release|debug) ;;
  -h|--help)
    sed -n '2,10p' "$0"
    exit 0
    ;;
  *)
    echo "Usage: $0 [release|debug]" >&2
    exit 1
    ;;
esac

if [[ -z "${JAVA_HOME:-}" ]]; then
  if command -v /usr/libexec/java_home >/dev/null 2>&1; then
    JAVA_HOME="$(/usr/libexec/java_home)"
    export JAVA_HOME
  fi
fi

GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
export GRADLE_USER_HOME

TASK="assemble$(printf '%s' "${BUILD_TYPE}" | awk '{print toupper(substr($0,1,1)) substr($0,2)}')"
AAR_SRC="logdog/build/outputs/aar/logdog-${BUILD_TYPE}.aar"
OUT_NAME="logdog-${VERSION}-${BUILD_TYPE}.aar"
OUT_PATH="${OUT_DIR}/${OUT_NAME}"

echo "==> Building :logdog:${TASK}"
./gradlew ":logdog:${TASK}" --quiet

if [[ ! -f "$AAR_SRC" ]]; then
  echo "AAR not found: $AAR_SRC" >&2
  exit 1
fi

mkdir -p "$OUT_DIR"
cp -f "$AAR_SRC" "$OUT_PATH"

echo "==> Done"
echo "    source : $AAR_SRC"
echo "    output : $OUT_PATH"
ls -lh "$OUT_PATH"
