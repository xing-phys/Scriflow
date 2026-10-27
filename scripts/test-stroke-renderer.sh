#!/usr/bin/env bash
# Fault-inject failed path booleans while executing the production renderer with Java2D fixtures.
# These host contracts do not replace the MatePad brush/rasterization verification.
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
mkdir -p build/renderer-tests
javac -encoding UTF-8 -source 8 -target 8 -classpath build/host-tests -d build/renderer-tests \
  tests/renderer-support/android/graphics/*.java \
  tests/renderer-support/android/view/MotionEvent.java \
  tests/renderer-support/android/eink/view/*.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookStrokeRenderer.java \
  tests/NotebookStrokeRendererTest.java
java -Djava.awt.headless=true -ea -cp build/renderer-tests:build/host-tests \
  org.eink.inkprobe.notebook.NotebookStrokeRendererTest
