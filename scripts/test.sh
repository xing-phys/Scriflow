#!/usr/bin/env bash
# Exercise timing, editing and durable storage without Android SDK stub execution.
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
  app/src/main/java/org/eink/inkprobe/notebook/NotebookEdits.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookDocument.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookIndex.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookBook.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookCatalog.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookLibrary.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookViewport.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookScrollStore.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookFingerStream.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookViewportBuffer.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookPdf.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookOverlayClip.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookToolStrip.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookDeviceStatusState.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookPenConfig.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookPenSliderLayout.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookPenSliderState.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookPenSliderClose.java \
  tests/CallbackTimingTest.java tests/NotebookStoreTest.java \
  tests/NotebookEditsTest.java tests/NotebookDocumentTest.java \
  tests/NotebookIndexTest.java tests/NotebookBookTest.java \
  tests/NotebookCatalogTest.java tests/NotebookLibraryTest.java \
  tests/NotebookViewportTest.java tests/NotebookScrollStoreTest.java \
  tests/NotebookFingerStreamTest.java tests/NotebookViewportBufferTest.java \
  tests/NotebookPdfTest.java tests/NotebookOverlayClipTest.java \
  tests/NotebookToolStripTest.java tests/NotebookDeviceStatusStateTest.java \
  tests/NotebookPenConfigTest.java tests/NotebookPenSliderTest.java \
  tests/NotebookPenSliderCloseTest.java
java -ea -cp build/host-tests org.eink.inkprobe.CallbackTimingTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookStoreTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookEditsTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookDocumentTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookIndexTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookBookTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookCatalogTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookLibraryTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookViewportTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookScrollStoreTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookFingerStreamTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookViewportBufferTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookPdfTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookOverlayClipTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookToolStripTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookDeviceStatusStateTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookPenConfigTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookPenSliderTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookPenSliderCloseTest
