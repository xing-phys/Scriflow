#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
mkdir -p build/cast-tests
javac -encoding UTF-8 -source 8 -target 8 -d build/cast-tests \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookCastFrames.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookCastPacer.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookCastServer.java tests/NotebookCastServerTest.java
java -ea -cp build/cast-tests org.eink.inkprobe.notebook.NotebookCastServerTest
node --check app/src/main/assets/cast/viewer.js
