#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
bash scripts/bootstrap-pdfbox.sh
SDK="$ROOT/.tools/platform-29/android.jar"
[[ -f "$SDK" ]] || SDK="${ANDROID_HOME:-$ROOT/.tools/android-sdk}/platforms/android-29/android.jar"
[[ -f "$SDK" ]] || { echo 'Run scripts/bootstrap.sh before PDF engine tests.' >&2; exit 1; }
OUTPUT="$ROOT/build/pdf-engine-test"
mkdir -p "$OUTPUT/classes"
javac -encoding UTF-8 -source 8 -target 8 -classpath "$SDK:$ROOT/.tools/pdfbox/*" \
  -sourcepath app/src/main/java:tests/pdf-support -d "$OUTPUT/classes" \
  tests/pdf-support/android/util/Log.java tests/pdf-support/android/graphics/*.java tests/NotebookPdfEngineTest.java tests/NotebookFigureFixture.java
java -ea -cp "$OUTPUT/classes:$ROOT/.tools/pdfbox/*:$SDK" \
  org.eink.inkprobe.notebook.NotebookPdfEngineTest "$OUTPUT"
