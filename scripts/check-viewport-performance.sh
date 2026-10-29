#!/usr/bin/env bash
# Compare current signed-release geometry with v0.33.0, synthetic inputs only.
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
APP=app/build/outputs/apk/release/app-release.apk
CLASSES=app/build/intermediates/javac/release/compileReleaseJavaWithJavac/classes
BT=.tools/build-tools-30.0.3
SDK=.tools/platform-29/android.jar
python3 scripts/verify-apk.py "$APP" --variant release --signature signed --certificate-sha256 "$(cat release-signing-certificate.sha256)"
mkdir -p build/viewport-performance/classes build/viewport-performance/dex artifacts/optimization-0.33.1
git show v0.33.0:app/src/main/java/org/eink/inkprobe/notebook/NotebookViewport.java > build/viewport-performance/baseline.txt
python3 - <<'PY'
from pathlib import Path
p=Path('build/viewport-performance')
(p/'ViewportBaseline.java').write_text((p/'baseline.txt').read_text().replace('NotebookViewport','ViewportBaseline'))
PY
javac -encoding UTF-8 -source 8 -target 8 -classpath "$SDK:$CLASSES" -d build/viewport-performance/classes \
  build/viewport-performance/ViewportBaseline.java tools/NotebookViewportBenchmark.java
python3 - <<'PY'
from pathlib import Path
p=Path('build/viewport-performance')
(p/'classes.txt').write_text('\n'.join(str(f) for f in (p/'classes').rglob('*.class'))+'\n')
PY
"$BT/d8" --min-api 29 --lib "$SDK" --classpath "$APP" --output build/viewport-performance/dex @build/viewport-performance/classes.txt
python3 - <<'PY'
from zipfile import ZipFile
with ZipFile('build/viewport-performance/check.jar','w') as z:z.write('build/viewport-performance/dex/classes.dex','classes.dex')
PY
REMOTE="/data/local/tmp/scriflow-viewport-$$"
trap 'adb -d shell rm -rf "$REMOTE" >/dev/null 2>&1 || true' EXIT
adb -d shell mkdir -p "$REMOTE"
adb -d push build/viewport-performance/check.jar "$REMOTE/check.jar" >/dev/null
adb -d push "$APP" "$REMOTE/app.apk" >/dev/null
adb -d shell "CLASSPATH=$REMOTE/check.jar:$REMOTE/app.apk app_process /system/bin org.eink.inkprobe.notebook.NotebookViewportBenchmark" | tee artifacts/optimization-0.33.1/viewport-performance.json
python3 - <<'PY'
import json
from pathlib import Path
r=json.loads(Path('artifacts/optimization-0.33.1/viewport-performance.json').read_text())
assert r['sameResult'] and r['currentMedianMs']>0
PY
