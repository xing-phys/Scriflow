#!/usr/bin/env bash
# AAR resources, native engine services and AndroidX require the Android Gradle plugin.
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
if [[ -z "${JAVA_HOME:-}" && -d "$ROOT/.tools/jdk-17.0.21+9" ]]; then
  export JAVA_HOME="$ROOT/.tools/jdk-17.0.21+9"
fi
./gradlew --no-daemon :app:assembleDebug
mkdir -p build
cp app/build/outputs/apk/debug/app-debug.apk build/scriflow-debug.apk
python3 scripts/verify-apk.py build/scriflow-debug.apk --variant debug --signature signed
printf '%s\n' "Built $ROOT/build/scriflow-debug.apk"
