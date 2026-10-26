# PDF export - 0.21

## Use

Open a notebook and tap **⋮**, then **Export PDF**. Every page in the notebook
is exported in order. Preparation shows the page being processed. When ready,
the Android save picker opens with the notebook name as the suggested filename.
Choose a location and tap Save; wait until the app shows **PDF saved**, then
tap **Done** to return to writing.

Cancelling the picker keeps the prepared PDF available through **Save PDF**.
**Cancel** during preparation stops the export. Cancelling while copying or a
provider failure can leave an incomplete file at the selected location; failed
saves explain this and let you choose a new location. The app does not delete
chosen destination documents. Local preparation files are removed on completion,
cancellation, or preparation failure. They may be evicted by Android; **Retry**
then prepares a new PDF. Abandoned cache files after process termination are
subject to normal Android cache cleanup.

The saved PDF contains filled vector outlines from Huawei's native pressure
brush, retaining per-stroke style, pressure-shaped width, dots, color/opacity,
and each page's blank/lined background. Before 0.21, export drew fixed-width
centre lines and ignored the saved pressure samples. Existing notes now render
correctly without migration; older PDFs need to be exported again.
From 0.30, new pages store physical dimensions: A5 exports at 148 × 210 mm,
and other presets or custom sizes export at their selected width and height.
Zoom and screen rotation do not affect PDF dimensions. Legacy pages without
physical dimensions retain a 595.2756-point (210 mm) page width and proportional
height. This legacy printing scale is independent of the tablet's physical size.
An extreme legacy page is scaled smaller if necessary to stay within PDF's
14,400-point page-height limit.
Pages are clipped to their stored boundaries. Continuous-view gaps and app
controls are excluded. Changing the viewport does not crop the export.

Export reads the notebook without adding history steps, changing selection or
paper, or moving the saved fractional scroll position. The original vector
notebook remains editable in the app. Export does not require a tablet connection
to the development computer.

## Implementation

The editor pauses handwriting and synchronously accepts already completed UP
events. A serialized load barrier waits for those writes before opening a
dedicated export Activity. That screen owns export and picker state independently
of the editor's native surface: opening the picker triggers Android lifecycle
events and its result can arrive before resume.

`NotebookSession.exportPdf` uses the existing writer queue. It validates notebook
identity and page count before every source read, including after process
recreation. `previewPage` supplies accepted in-memory pages or reads uncached
pages without populating the editing-document cache. Missing/corrupt pages fail
the export locally. They do not latch the notebook's save-error state or cause
blank replacements. Notebook/page/catalog formats are unchanged.

`NotebookPdf` writes PDF 1.4 directly to a stream with a bounded buffer, one
source page at a time, plus a small object-offset table. There is no whole-book
bitmap or PDF byte-array allocation. Each stroke obtains its native filled
outline; curves are flattened to 0.05 source-page units for portable vector
output. All contours of a stroke are filled once with their native winding rule,
including dots and holes. Shared graphics states retain ARGB opacity. Content
streams use lossless Flate compression, indirect compressed lengths, and exact
byte-offset cross references. UTF-16BE title
metadata supports Unicode notebook names. Numbers use locale-independent PDF
syntax. A page or output error leaves no successful prepared result. Cancellation
is checked between pages and on buffered output writes within long strokes.

The PDF is fully written, flushed, synced, closed, and validated in app cache
before destination selection. A separate worker streams it through
`ContentResolver` to the chosen URI, handling write, flush, and close failures.
This uses [Android's Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files)
with `ACTION_CREATE_DOCUMENT`, `CATEGORY_OPENABLE`, and `application/pdf`;
no broad storage permission or network permission is added.

A retained job survives configuration changes. Saved instance state records only
a finalized temporary PDF path, picker flags, and export metadata; restoration
validates that path belongs to the app cache. Interrupted preparation can restart.
An interrupted save after process death requires choosing a new location and
reports that the previous file may be incomplete. The app never assumes that a
partially copied destination is a successful export.

The export screen and menu controls use the existing static-button styling and
zero window/activity animations. Export progress updates occur while writing
is paused, with coalesced notifications rather than a spinning indicator.

## Version 0.21 verification

All **19,820 host assertions** pass, including **4,308 PDF checks** and 25 outline
transport checks. Structural tests decode the compressed streams and verify
geometry, fill rules, alpha, numeric precision, caller ownership, streaming,
cancellation, failures, exact lengths/xref offsets, and unchanged notebook files,
history and scroll position. No fallback to nominal-width lines is allowed when
native outline extraction fails.

The synthetic on-device comparison covers 14 light, heavy, pressure-ramped,
curved, dotted, thin and self-crossing strokes. Huawei replay, scrolling geometry,
and the PDF measure the light/heavy lines at 5/10 pixels for the nominal width-10
brush. Thresholded ink boundaries agree within one source pixel; rasterizer
antialiasing differs. Independent Poppler rendering and the tablet's PdfRenderer
both read the final two-page blank/lined export, and PNG details are visually
inspected. Compression reduces this fixture from 1,434,878 to 292,887 bytes
without changing geometry. Native outline extraction also passes from the
ordinary APK UID, and blank/lined restoration/edit/pause checks pass.

The user physically confirms that scrolling and fresh PDF exports both match
the writing canvas. Existing note files and the page format remain unchanged.
See [consistent ink rendering](consistent-ink-rendering.md) for implementation
and the reproducible device comparison.

## Original 0.13 verification and acceptance procedure

All **16,902 host assertions** pass, including **3,555 PDF assertions**. They
cover ordered source reads, page aspect ratios, vectors and dots, ruling,
opacity, Unicode titles, numeric syntax, xref offsets and stream lengths,
caller-owned output streams, write/flush failures, cancellation, and long paths.
Real-library tests compare every source file before/after export, preserve
undo/redo and fractional position, and verify a corrupt later page aborts
without blocking healthy-page edits. The generated two-page production-writer
fixture passes `pdfinfo` and Poppler rendering; both rendered pages were visually
inspected. The complete API 29 APK builds and uses the existing signing key,
versionCode 14 and versionName 0.13.

No tablet or ADB access was used. The Huawei picker, destination-provider
behavior, return-to-editor native pen latency, and physical e-ink refresh still
need device acceptance. Install `build/ink-notebook-0.13.apk` over the existing
app without uninstalling.

1. Open a notebook with at least three distinctly written blank/lined pages.
   Export it and verify page count, paper, dots, curves, and sharp ink when zoomed.
2. Export immediately after finishing a word; check that the final word appears.
3. Start between pages, export, return, and confirm the same scroll position and
   smooth writing, erasing, and Undo/Redo.
4. Cancel the picker, reopen it with Save PDF, and save successfully. Cancel
   preparation and verify that existing notes remain available.
5. Try a destination-provider error or unavailable location. Check that the
   notebook still writes normally and the export offers a retry.
