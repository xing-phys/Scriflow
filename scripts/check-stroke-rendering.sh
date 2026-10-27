#!/usr/bin/env bash
# Compare native replay, scrolling geometry, thumbnails, and exported vectors on a connected MatePad.
# Uses only a synthetic page in /data/local/tmp; no app data or user notebooks are opened.
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
[[ -f build/scriflow-debug.apk ]] || { echo 'Run ./scripts/build.sh first.' >&2; exit 1; }
rm -rf build/render-check/classes build/render-check/dex
mkdir -p build/render-check/classes build/render-check/dex artifacts/consistent-ink-scale
javac -encoding UTF-8 -source 8 -target 8 -classpath ".tools/platform-29/android.jar:build/classes" \
  -d build/render-check/classes tools/NotebookRenderingCheck.java
python3 - <<'PY'
from pathlib import Path
Path('build/render-check/classes.txt').write_text('\n'.join(str(p) for p in Path('build/render-check/classes').rglob('*.class'))+'\n')
PY
.tools/build-tools-30.0.3/d8 --min-api 29 --lib .tools/platform-29/android.jar \
  --classpath build/scriflow-debug.apk --output build/render-check/dex @build/render-check/classes.txt
python3 - <<'PY'
from zipfile import ZipFile
with ZipFile('build/render-check/check.jar','w') as archive:
    archive.write('build/render-check/dex/classes.dex','classes.dex')
PY
REMOTE="/data/local/tmp/ink-render-check-$$"
trap 'adb -d shell rm -rf "$REMOTE" >/dev/null 2>&1 || true' EXIT
adb -d shell mkdir -p "$REMOTE"
adb -d push build/render-check/check.jar "$REMOTE/check.jar" >/dev/null
adb -d push build/scriflow-debug.apk "$REMOTE/app.apk" >/dev/null
STATUS=0
adb -d shell "CLASSPATH=$REMOTE/check.jar:$REMOTE/app.apk app_process /system/bin org.eink.inkprobe.notebook.NotebookRenderingCheck $REMOTE/output" || STATUS=$?
adb -d pull "$REMOTE/output/." artifacts/consistent-ink-scale/ >/dev/null
exit "$STATUS"
