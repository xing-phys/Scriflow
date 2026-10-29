# In-note figure controls — 0.33.2-dev.5

## Interaction

Insert figure opens the system file picker directly and returns to the note
after insertion or cancellation (dev.4). Dev.5 selects an existing figure in the
same viewport, without replacing the note, fitting an isolated page or showing
an Apply/Cancel footer. Tap the figure with a finger, including in an inactive
note, to select it. Drag the body to move, the lower-right handle to resize with
its aspect ratio intact, or the upper-right rotation handle to turn to any angle.
Controls stay within the pane and avoid status, toolbox and other-note bounds.
Resizing uses the handle’s grab offset, including when chrome displaces it.

Nearby icons offer Copy, Cut, Delete, and Paste after copying. Paste also appears
in the note’s three-dot menu. Copies get independent IDs; the clipboard is local
to the app process and can be used in another note. Tap outside to finish. A
completed gesture saves one undoable edit; cancellation rolls back that gesture.
Outside drags pan, and two fingers zoom. Drag across pages, or hold at the top or
bottom edge to scroll to further pages. On release the full rotated envelope is
fitted inside the destination page. A cross-page move undoes/redoes both pages
together; later edits on either page protect the paired history from overwrite.

## Rendering and lifecycle

The firmware’s native HWEditView sits above ordinary same-window Android views.
The editor therefore uses a transparent, nonfocusable, non-touchable PopupWindow
hosted with the Activity token through NotebookPopupHost. It does not take input
away from the note. The native selection stream forwards touch events to the
editor, which hit-tests the nearby icons and handles. Native handwriting stays
paused while a figure is selected. A tap outside restores normal input.

Only selection graphics and the moving figure are drawn in the popup. Its canvas
is clipped around status bars, the toolbox, corner controls and other floating
notes; the toolbox retains its higher application sublayer. The existing page
stack and native widget remain in place. Idle selection does not redraw a
background bitmap. During movement only the selected source figure is omitted
from native paper, while its source pixels follow the gesture in the overlay.
Navigation uses the existing complete-frame paper/ink preview. After a save or
cancel, preview pixels remain until the current page window and canonical native
render are ready. Stale ready callbacks cannot release a pending save.

Image preparation runs on the existing figure worker at the requested display
resolution, with original-pixel fallback while preparation completes. Rotation
is a canvas transform; it never repeatedly resamples or rewrites the PNG.
Popup teardown cancels edge-scroll callbacks and releases its image cache.
Existing pause, workspace and retry lifecycles retire the selection before
replacing a native widget. No new permissions or system settings are used.

## Storage compatibility

**Dev.5 writes page format 7 on edit. It reads versions 1–6 without rewriting
them, but older APKs, including stable 0.33.1, cannot read version-7 pages or
editable backups containing them. Preserve independent backups before updating.**

Format 7 adds a clockwise angle to each figure. Logical unrotated rectangles may
have negative origins if their rotated envelope remains inside the page. Bounds,
hit testing, thumbnails, inactive readers and PDF export use the same transform.
Original PNG bytes, alpha and resolution stay intact through every placement
change and backup. Raster detail is still limited by the imported source image.

Cross-page figure edits use the existing full-snapshot two-page redo journal.
Recovery now publishes both figures and strokes, preserving page metadata and
completed ink. This supports the same linked undo/redo and interrupted-publication
recovery used by cross-page ink moves. Same-page edits retain figure stacking
order; pasted or cross-page figures are appended above existing figures and
remain below handwriting after the canonical render is restored.

## Verification

All 248,443 host assertions pass in 67 suites. Added coverage includes:

- 2,019 checks on random rotated fits and anchored resizing, inverse hit testing,
  mixed-size page moves, real v6 migration with the exact predecessor backup,
  rotation/pixels after reopening and `.inkbook` restore, linked history and
  recovery after only the destination snapshot was published.
- 22 checks executing production input/drawing callbacks with UI adapters:
  move/resize/free rotation, displaced-handle offsets, outside taps, nearby
  action routing, pan, multi-contact cancellation, edge scrolling, transparent
  drawing/exclusions, save acknowledgement and stale/native-ready ordering.
- PDF export at 37°, 90° and −53°, checking image matrices against independently
  calculated corners and retaining the original pixel dimensions/alpha mask.
  The 37° fixture was rendered with Poppler and visually inspected.

Debug and release lint/builds pass. `scripts/build-release.sh` prepares
`build/releases/scriflow-0.33.2-dev.5.apk` (versionCode 69), verifies the permanent
certificate pin, and writes the adjacent SHA-256 checksum. Host/build logs are
in `artifacts/inline-figures/final-host.txt` and `final-build.txt`.

## Tablet acceptance

The owner instructed development to continue without the tablet. Host adapters
cannot establish physical input routing, e-ink waveform or perceived latency.
Use backed-up disposable notes when the device is available:

1. Insert SVG and transparent PNG in portrait and landscape. Confirm the picker
   opens directly and success/cancellation returns directly to the same note.
2. Select a figure at several zoom levels in a main, split and floating note.
   Confirm no footer or note refit, and that toolbox/status controls stay visible.
3. Drag the body, resize from a corner near chrome, and rotate freely. Check for
   responsive boundaries, no whole-note white flash and unchanged ink after release.
4. Drag through a page boundary and edge-scroll to further pages, including a
   different page size. Undo/redo once; both pages must change together. Reopen
   and restore an editable backup, then inspect exported PDF orientation/alpha.
5. Copy, paste to this and another note, cut and delete; undo each. Tap outside
   and write. Pan/pinch while selected, cancel a gesture, rotate the screen and
   leave/resume the app; input must recover without stale panels or stray ink.
