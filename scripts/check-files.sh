#!/usr/bin/env bash
# Real java.nio operations on Android 10, followed by UI checks on a disposable emulator.
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || {
  echo 'This check uses disposable files and permission changes on an emulator only.' >&2; exit 1;
}
export JAVA_HOME="$ROOT/.tools/jdk-17.0.21+9"
export PATH="$JAVA_HOME/bin:$PATH"
OUTPUT=build/file-manager-check
mkdir -p "$OUTPUT/classes" "$OUTPUT/dex"
javac -encoding UTF-8 -source 8 -target 8 -d "$OUTPUT/classes" \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookFiles.java tests/NotebookFilesTest.java
.tools/build-tools-36.0.0/d8 -JXmx256M --min-api 29 --lib .tools/platform-29/android.jar \
  --output "$OUTPUT/dex" "$OUTPUT"/classes/org/eink/inkprobe/notebook/NotebookFiles*.class
python3 - <<'PY'
from zipfile import ZipFile
with ZipFile('build/file-manager-check/check.jar', 'w') as archive:
    archive.write('build/file-manager-check/dex/classes.dex', 'classes.dex')
PY
REMOTE="/data/local/tmp/scriflow-files-check-$$"
trap 'adb shell rm -rf "$REMOTE" >/dev/null 2>&1 || true' EXIT
adb shell mkdir -p "$REMOTE"
adb push "$OUTPUT/check.jar" "$REMOTE/check.jar" >/dev/null
adb shell "CLASSPATH=$REMOTE/check.jar app_process -Djava.io.tmpdir=$REMOTE /system/bin org.eink.inkprobe.notebook.NotebookFilesTest"
python3 scripts/check-files-ui.py
