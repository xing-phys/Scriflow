#!/usr/bin/env bash
# Exercise timing and durable notebook storage without Android SDK stub execution.
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
for required in javac java; do
  command -v "$required" >/dev/null || { echo "Missing prerequisite: $required" >&2; exit 1; }
done
rm -rf build/host-tests
mkdir -p build/host-tests
javac -encoding UTF-8 -source 8 -target 8 -d build/host-tests \
  app/src/main/java/org/eink/inkprobe/CallbackTiming.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookPage.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookStore.java \
  tests/CallbackTimingTest.java tests/NotebookStoreTest.java
java -ea -cp build/host-tests org.eink.inkprobe.CallbackTimingTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookStoreTest
