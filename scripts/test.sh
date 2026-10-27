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
  app/src/main/java/org/eink/inkprobe/notebook/NotebookBackup.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookRestore.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookTasks.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookTransform.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookPage.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookPageCatalog.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookPdfTiles.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookPdfStaging.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookOutline.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookViewHistory.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookLinkTapGesture.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookPaperSize.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookStore.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookEdits.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookLasso.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookLassoGesture.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookLassoCompletion.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookSelectionDrag.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookSelectionStream.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookSelectionPlacement.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookDocument.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookIndex.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookBook.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookCatalog.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookLibrary.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookViewport.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookScrollStore.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookFingerStream.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookPalmGuard.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookPinchGesture.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookSensorRotation.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookDisplayState.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookViewportBuffer.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookPdf.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookStrokeOutline.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookOverlayClip.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookToolStrip.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookGripGesture.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookDeviceStatusState.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookPenConfig.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookWidthRange.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookEraserCommit.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookPenSliderLayout.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookPenSliderState.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookPenSliderClose.java \
  tests/CallbackTimingTest.java tests/NotebookStoreTest.java \
  tests/NotebookEditsTest.java tests/NotebookDocumentTest.java tests/NotebookLassoTest.java \
  tests/NotebookLassoInteractionTest.java \
  tests/NotebookIndexTest.java tests/NotebookBookTest.java \
  tests/NotebookPageManagementTest.java \
  tests/NotebookPdfModelTest.java \
  tests/NotebookPdfTilesTest.java \
  tests/NotebookPageCatalogTest.java \
  tests/NotebookPdfStagingTest.java \
  tests/NotebookViewHistoryTest.java \
  tests/NotebookLinkTapGestureTest.java \
  tests/NotebookCatalogTest.java tests/NotebookLibraryTest.java tests/NotebookShelfTest.java tests/NotebookPaperSizeTest.java \
  tests/NotebookViewportTest.java tests/NotebookViewportZoomTest.java tests/NotebookScrollStoreTest.java \
  tests/NotebookFingerStreamTest.java tests/NotebookInputPolicyTest.java tests/NotebookSensorRotationTest.java tests/NotebookDisplayStateTest.java tests/NotebookViewportBufferTest.java \
  tests/NotebookPdfTest.java tests/NotebookOutlineFixture.java tests/NotebookStrokeOutlineTest.java tests/NotebookOverlayClipTest.java \
  tests/NotebookToolStripTest.java tests/NotebookGripGestureTest.java tests/NotebookDeviceStatusStateTest.java \
  tests/NotebookPenConfigTest.java tests/NotebookPenSliderTest.java \
  tests/NotebookPenSliderCloseTest.java tests/NotebookEraserTest.java tests/NotebookEraserCommitTest.java tests/NotebookFoundationTest.java \
  tests/NotebookStrokeScaleTest.java tests/NotebookViewportSaveTest.java
java -ea -cp build/host-tests org.eink.inkprobe.CallbackTimingTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookStoreTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookEditsTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookLassoTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookLassoInteractionTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookDocumentTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookIndexTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookBookTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookPageManagementTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookPdfModelTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookPdfTilesTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookPageCatalogTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookPdfStagingTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookViewHistoryTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookLinkTapGestureTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookCatalogTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookLibraryTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookShelfTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookPaperSizeTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookViewportTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookViewportZoomTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookScrollStoreTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookFingerStreamTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookInputPolicyTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookSensorRotationTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookDisplayStateTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookViewportBufferTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookPdfTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookStrokeOutlineTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookOverlayClipTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookToolStripTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookGripGestureTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookDeviceStatusStateTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookPenConfigTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookPenSliderTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookPenSliderCloseTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookEraserTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookEraserCommitTest

java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookFoundationTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookStrokeScaleTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookViewportSaveTest
bash scripts/test-stroke-renderer.sh
bash scripts/test-pdf-engine.sh
