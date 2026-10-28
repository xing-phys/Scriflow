#!/usr/bin/env bash
# Synthetic file-format/backup baseline on Android. No user notes or live writing surface.
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
APP="app/build/outputs/apk/release/app-release.apk"
CLASSES="app/build/intermediates/javac/release/compileReleaseJavaWithJavac/classes"
[[ -f "$APP" && -d "$CLASSES" ]] || { echo 'Run scripts/build-release.sh first.' >&2; exit 1; }
python3 scripts/verify-apk.py "$APP" --variant release --signature signed --certificate-sha256 "$(cat release-signing-certificate.sha256)"
rm -rf build/foundation-check/classes build/foundation-check/dex
mkdir -p build/foundation-check/classes build/foundation-check/dex artifacts/foundation-0.22
javac -encoding UTF-8 -source 8 -target 8 -classpath ".tools/platform-29/android.jar:$CLASSES" \
  -d build/foundation-check/classes tools/NotebookFoundationBenchmark.java
.tools/build-tools-30.0.3/d8 --min-api 29 --lib .tools/platform-29/android.jar \
  --classpath "$APP" --output build/foundation-check/dex \
  build/foundation-check/classes/org/eink/inkprobe/notebook/NotebookFoundationBenchmark.class
python3 - <<'PY'
from zipfile import ZipFile
with ZipFile('build/foundation-check/check.jar','w') as archive:
    archive.write('build/foundation-check/dex/classes.dex','classes.dex')
PY
REMOTE="/data/local/tmp/ink-foundation-$$"
trap 'adb -d shell rm -rf "$REMOTE" >/dev/null 2>&1 || true' EXIT
adb -d shell mkdir -p "$REMOTE"
adb -d push build/foundation-check/check.jar "$REMOTE/check.jar" >/dev/null
adb -d push "$APP" "$REMOTE/app.apk" >/dev/null
adb -d shell "CLASSPATH=$REMOTE/check.jar:$REMOTE/app.apk app_process /system/bin org.eink.inkprobe.notebook.NotebookFoundationBenchmark $REMOTE/benchmark"
adb -d pull "$REMOTE/benchmark/result.json" artifacts/foundation-0.22/benchmark.json >/dev/null
