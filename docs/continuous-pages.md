# Continuous pages — 0.11

## Use

Drag with one finger to scroll vertically through the notebook. Lift it to stop
at any position: two partial pages can remain visible. Write with the stylus on
either visible page. There is no page snapping, overscroll bounce, or inertial
fling. `+ Page` still creates an empty page at the end and moves to it; `‹ / ›`
jump to neighboring page tops. This release does not create pages automatically
when dragging beyond the last page.

Each page keeps its existing size, paper, writing, and undo history. The toolbar
page indicator follows the most recently written/erased page; after scrolling it
uses the page nearest the viewport center. Undo/Redo and Blank/Lined apply to that
context page. One physical pen stroke that crosses a gap is split into page-local
pieces; gap ink is discarded. Its pieces form one undo step per affected page.
An eraser path likewise stays separated across gaps and forms one edit per page.

Finger dragging uses a preview while pen input is paused. On lifting, native ink
resumes only after the visible pages are loaded and completed saves ahead of
that viewport have finished. A fast drag into an uncached page can briefly show
`Loading page N…`; writing is held until the real page is ready. Mixed finger/
stylus gestures stop scrolling safely after all contacts lift. Existing static
buttons and suppressed window animations remain in use.

## Architecture

The native hardware writing surface remains fixed at the screen's writing area.
`NotebookViewport` fits pages to viewport width, stacks them with a narrow gap,
and maps the fractional scroll offset. It composes only visible clipped vector
tracks into a synthetic viewport page. That viewport is never serialized as the
document: completed ink is clipped and inverse-transformed into explicit stored
page IDs. Pressure, style, and event times are retained, with interpolation at
clip boundaries. Clipped endpoints are strictly bounded after float conversion
so numeric roundoff cannot reject a completed stroke during saving.

During a finger drag, cached RGB565 page tiles are painted into one viewport
preview and posted on the existing native surface. A bounded neighboring-page
window is fetched on the worker as required. At release, a revision-aware final
read waits behind accepted stroke writes, then paper and native tracks are
restored together. Pending-preview state overlays outstanding accepted strokes
on acknowledged pages, so an older save callback cannot erase a newer preview.
No page-view SurfaceView is moved inside an Android ScrollView.

Native pen MOVE handling remains unchanged and performs no file I/O or toolbar
redraw. Ordinary contained strokes need no canonical redraw after each save.
Gap-crossing ink is cleaned up after a pen-idle interval. A rare unsettled vendor
track accumulator can require a fresh widget; that fallback explicitly preserves
the current scroll anchor rather than rereading a stale asynchronous position.

Each loaded page has an explicit identity for append/erase/undo/redo. Reading
neighbor pages does not change the legacy selected-page index. A physical stroke
group is retained in memory before persistence; partial append failures track the
exact durable or attempted prefix, and later healthy-page pieces are still saved.
Retry reconciles all dirty cached pages without duplicating accepted ink.

## Position storage and compatibility

Each notebook now also has `scroll.nbs` with a page number and fractional offset
within that page plus its following gap. It is a bounded versioned CRC snapshot,
published with a synced temporary file and backup. The anchor is written after
completed scrolling or navigation and when leaving the editor, not on MOVE.
Absence starts at the previously selected page. Corrupt, out-of-range, or
unexpected position files are preserved and fail closed. Notebook/page/catalog
formats and old handwriting files are unchanged. Position and page history stay
independent between notebooks; undo history remains process-local.

## Offline verification and tablet acceptance

All 13,216 host assertions pass. Checks cover strict clipping/bounds, page-local routing, fractional anchors,
mixed touch ownership, stale preview acknowledgements, grouped undo, partial
write retries, corruption preservation, and legacy compatibility. The complete
API 29 app compiles and is signed with the existing key, versionCode 12 and
versionName 0.11. No tablet/ADB access was used for this release. Source review
and host tests cannot confirm e-ink scrolling speed, touch routing on the actual
firmware, or physical pen latency after moving the viewport.

Install `build/ink-notebook-0.11.apk` as an update; do not uninstall the app.

1. Confirm existing writing and notebook names remain intact. Create at least
   three pages, including different paper templates and distinct writing.
2. Drag half a page and release. Confirm it stays between pages without snapping.
3. Write a dot and a short word on each visible page; scroll away and back.
   Check the writing stays attached to its page and the pen remains smooth.
4. Erase and undo on both visible pages, then try a stroke crossing a gap and
   undo its pieces on their respective pages.
5. Scroll immediately after writing, including a long drag into uncached pages.
   No recently completed stroke should disappear after release.
6. Lift the finger before/after a stylus contact and check the app resumes input.
   Return to Books, reopen, and verify the same fractional position is restored.
