# Page overview and jumps — 0.12

## Use

Tap the page indicator below the toolbar to open **Pages**. Thumbnails show the
whole page, including blank/lined paper and saved writing. The context page
used by Undo/Redo has a black border and a **Current** label. The grid initially
opens at that page. Scroll the grid to browse other pages.

Tap a thumbnail to jump to its page top. For a distant page, enter its number
in **Go to page** and tap **Go**, or use the keyboard's Go action. Invalid or
empty numbers leave the overview open with a range message. Tapping the current
page also jumps to its top. **Close** and Android Back resume writing at the
exact prior fractional scroll position. Page selection does not create an undo
step or change the selected pen/eraser tool.

## Input and display

Opening pauses native handwriting and flushes any already completed UP event.
A barrier on the existing writer queue runs before the dialog is shown, so the
latest accepted ink is available to preview. The overview uses a separate
fullscreen dialog window above the firmware's top-Z handwriting surface. The
underlying native surface and paper remain allocated. Input stays paused until
the dialog is closed or the selected page has loaded.

Close/Back resumes the paused surface directly, without recomposing the ink or
changing the scroll anchor. A chosen page goes through the existing settled
viewport read, after earlier writes, before native input resumes. Navigation
stores the new scroll anchor as usual. Leaving the Activity dismisses the
overview, cancels outstanding preview work, and saves the editor position.

Controls use static backgrounds with no ripple, dimming, or window animation.
The numeric input has no blinking cursor. Thumbnail contents update individually
as their preview becomes available; loading one does not refresh the whole grid.
Actual e-ink refresh behavior still requires physical verification.

## Bounded, read-only previews

The grid reuses cells. Only visible missing previews are requested, with at most
one queued or running read/render. Each is a 320 × 384 RGB565 bitmap, preserving
page aspect ratio. The LRU cache holds 12 bitmaps (about 2.8 MiB of pixel data),
plus any visible cells still displaying an evicted bitmap and one render in
flight. Long vector paths are streamed in chunks rather than copied into a
second complete path. Fast scrolling does not enqueue every page passed.

Preview work runs on the same serialized worker as document writes, outside the
editor snapshot/error publication path. It checks a cancellation token before
reading and before rendering, and checks notebook identity before reading.
Closing cancels the request; any never-delivered bitmap is released. Rendering
checks cancellation between strokes and within long path chunks. A running
file read can finish, but no backlog of preview requests remains before
subsequent writing.

`previewPage` returns accepted immutable in-memory content for an already loaded
page, including retained unsaved edits. Uncached pages are read without creating
or retaining an editing document. Previewing never changes selection, history,
the scroll anchor, or notebook files. A corrupt/missing page shows **Preview
unavailable** locally without pausing the healthy editor or overwriting data.
Choosing that page still uses normal validation and error handling.

## Verification

All 13,347 host assertions pass, including 131 new checks for preview isolation,
byte-for-byte preservation of files, dirty in-memory content, missing/corrupt
pages, invalid dimensions, and absence of preview cache retention. Existing
tests cover fractional scroll persistence and page-local edits. The full API 29
app compiles, and the APK is signed with the existing local key as versionCode
13, versionName 0.12. No device/ADB access was used for this feature; host checks
cannot verify firmware window stacking, keyboard behavior, or physical latency.

Install `build/ink-notebook-0.12.apk` over the existing app without uninstalling.

1. With at least three distinctly written pages, scroll halfway between two
   pages. Open the indicator and check the thumbnails and current-page label.
2. Close, then repeat using Android Back. The exact writing position, selected
   tool, and ink should remain; try a new stroke immediately.
3. Tap a distant thumbnail. Confirm it lands on the correct page and writing,
   erasing, Undo/Redo, and paper changes still apply to that page.
4. Test numeric jumps to 1, the last page, and an empty/out-of-range value. Check
   the keyboard closes with the overview and does not obscure resumed writing.
5. Open the overview immediately after completing a stroke. Check that stroke
   appears in the preview and remains after returning to the editor.
6. Browse quickly, close while previews are loading, then write. Reopen the
   notebook from Books and confirm the last jump position is retained.
