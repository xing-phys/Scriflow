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
  app/src/main/java/org/eink/inkprobe/notebook/NotebookFigure.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookMarkdown.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookMarkdownDraft.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookMarkdownTiles.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookFigureHit.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookFigureGeometry.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookWorkspace.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookCacheBudget.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookStylusShortcut.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookSelectionExport.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookPageCatalog.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookPdfTiles.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookPdfStaging.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookPdfImportFlow.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookOutline.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookViewHistory.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookLinkTapGesture.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookPaperSize.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookStore.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookEdits.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookLasso.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookInkTransfer.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookSelectionMove.java \
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
  app/src/main/java/org/eink/inkprobe/notebook/NotebookCropBox.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookReadingSettings.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookReadingStore.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookPdfCrop.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookPdfContrast.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookPageTurn.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookPdfHold.java \
  app/src/main/java/org/eink/inkprobe/notebook/NotebookPdfText.java \
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
  tests/NotebookReadingTest.java tests/NotebookPdfReadingInteractionTest.java tests/NotebookFiguresTest.java tests/NotebookMarkdownTest.java tests/NotebookMarkdownInteractionTest.java tests/NotebookMarkdownRasterizerTest.java tests/NotebookFigureFixture.java tests/NotebookFigureHitTest.java tests/NotebookFigureTapTest.java tests/NotebookFigureTransformTest.java tests/NotebookInlineFigureTest.java \
  tests/NotebookWorkspaceActivationTest.java tests/NotebookWorkspaceTest.java tests/NotebookCacheBudgetTest.java tests/NotebookWorkspaceDisplayTest.java \
  tests/NotebookReaderInteractionTest.java tests/NotebookWindowGripTest.java tests/NotebookResizeOutlineTest.java \
  tests/NotebookPopupHostTest.java tests/NotebookStatusControlsTest.java tests/NotebookNoteActionsTest.java tests/NotebookWritingHistoryTest.java tests/NotebookZenModeTest.java tests/NotebookPdfImportFlowTest.java tests/NotebookFigureImportFlowTest.java tests/NotebookStylusShortcutTest.java \
  tests/NotebookEditsTest.java tests/NotebookDocumentTest.java tests/NotebookLassoTest.java \
  tests/NotebookLassoInteractionTest.java \
  tests/NotebookSelectionMoveTest.java tests/NotebookInkTransferTest.java tests/NotebookEditRestoreTest.java \
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
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookPdfReadingInteractionTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookReadingTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookMarkdownTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookMarkdownInteractionTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookMarkdownRasterizerTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookFiguresTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookFigureHitTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookFigureTapTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookFigureTransformTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookInlineFigureTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookWorkspaceActivationTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookWorkspaceTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookCacheBudgetTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookWorkspaceDisplayTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookReaderInteractionTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookWindowGripTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookResizeOutlineTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookPopupHostTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookStatusControlsTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookNoteActionsTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookWritingHistoryTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookZenModeTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookPdfImportFlowTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookFigureImportFlowTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookStylusShortcutTest
java -ea -cp build/host-tests org.eink.inkprobe.CallbackTimingTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookStoreTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookEditsTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookLassoTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookLassoInteractionTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookSelectionMoveTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookInkTransferTest
java -ea -cp build/host-tests org.eink.inkprobe.notebook.NotebookEditRestoreTest
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
bash scripts/build-markdown-assets.sh
node scripts/test-markdown.cjs
bash scripts/test-pdf-engine.sh
