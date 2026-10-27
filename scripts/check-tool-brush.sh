#!/usr/bin/env bash
# Compare native eraser/ballpoint footprints with synthetic offscreen Canvas samples only.
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
mkdir -p build/tool-brush-check/classes build/tool-brush-check/dex artifacts/native-tool-brush
javac -encoding UTF-8 -source 8 -target 8 -classpath .tools/platform-29/android.jar \
  -d build/tool-brush-check/classes tools/NotebookToolBrushCheck.java
.tools/build-tools-30.0.3/d8 --min-api 29 --lib .tools/platform-29/android.jar \
  --output build/tool-brush-check/dex \
  build/tool-brush-check/classes/org/eink/inkprobe/notebook/NotebookToolBrushCheck.class
python3 - <<'PY'
from zipfile import ZipFile
with ZipFile('build/tool-brush-check/check.jar', 'w') as archive:
    archive.write('build/tool-brush-check/dex/classes.dex', 'classes.dex')
PY
REMOTE="/data/local/tmp/scriflow-tool-brush-check-$$"
trap 'adb -d shell rm -rf "$REMOTE" >/dev/null 2>&1 || true' EXIT
adb -d shell mkdir -p "$REMOTE"
adb -d push build/tool-brush-check/check.jar "$REMOTE/check.jar" >/dev/null
adb -d shell "CLASSPATH=$REMOTE/check.jar app_process /system/bin org.eink.inkprobe.notebook.NotebookToolBrushCheck" \
  > artifacts/native-tool-brush/result.json
python3 - <<'PY'
import json
from pathlib import Path
result = json.loads(Path('artifacts/native-tool-brush/result.json').read_text())
assert result['simpleWidthMatchesAcrossPressure']
print('Native tool brush: %d synthetic checks passed; no notebook files opened.' % len(result['checks']))
PY
