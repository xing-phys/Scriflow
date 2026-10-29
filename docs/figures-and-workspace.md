# Figures, selected-ink export, and multiple notes

Figures, export and multiple notes are included in stable **0.33.0**.
**0.33.1** adds direct finger selection of figures and immediate
corner dragging with a resize boundary. These latest interactions passed host
checks and signed Android builds; their physical tablet checks remain pending.
**0.33.2** includes the inline figure controls below, arbitrary rotation and
cross-page dragging. Its latest UI and exact release APK remain physically untested.

## Insert and edit figures

Open **⋮ → Insert figure…** to go directly to the system file picker. Choosing
an image inserts it at the current page’s center and returns to the note
automatically. Cancelling returns without changing the note; failures offer retry.
Tap a figure with one finger to select it, then drag its body to move it in the
existing note view. The **⋮ → Figures…** list remains available. Drag its
lower-right handle to resize while retaining its aspect ratio, or its upper-right
rotation handle to turn to any angle. Move across page boundaries; holding near
the top or bottom edge scrolls to further pages. The complete rotated image fits
inside its destination page when released.

Small Copy, Cut and Delete icons appear beside the selected figure. Copy/Cut make
Paste available there and in the note’s **⋮** menu; the clipboard stays inside
Scriflow until its process restarts. Pasting creates an independent figure.
Tap outside to finish, with no separate editor, footer, Apply or Cancel buttons.
Each completed gesture is one undoable edit; a cancelled gesture restores its
starting position. Undoing a cross-page move restores both pages together.
Dragging outside a selected figure pans the note; two fingers still zoom.
Figures are drawn above paper/PDF content and below handwriting. The stroke
eraser and stroke lasso do not select or erase figures.

SVG/SVGZ uses AndroidSVG 1.4. PNG, JPEG, WebP, GIF, BMP and other image formats
use the tablet’s Android image decoder; formats such as TIFF, EPS, PSD, or AVIF
are not promised on Android 10. Unsupported or malformed files report an error.
Animated images become a still image. SVG scripts and external linked resources
are not loaded. Include necessary artwork in the SVG itself.

Imports are normalized to **PNG**, retaining transparency and orientation as
provided by the decoder. SVG is rasterized at import, so inserted SVG figures
are not editable vectors. The longest edge is at most **2,048 pixels**. Input is
limited to 32 MiB, stored PNG to 8 MiB, and each page to 64 figures / 16 MiB of
figure data, within the existing 32 MiB snapshot limit. Original source files
are not linked or modified. Figures survive reopening, page duplication,
`.inkbook` backup/restore, thumbnails, and whole-note PDF export.

## Export a lasso selection

Select handwriting with Lasso, then choose **Export…** in the nearby action
panel. Prepare **SVG, PNG, JPEG, WebP, or PDF**, then choose **Save…** and a
location in the system picker. A cancelled picker retains the prepared file
for retry. A failed provider write retains the prepared file and offers retry;
the failed external destination may be incomplete.

Only selected strokes are exported, without page rules, PDF backgrounds,
figures, or the selection outline. The crop follows the native pressure-shaped
outlines with eight logical units of padding. Color, opacity and fill rules
are preserved. SVG and PDF keep vector ink; PNG/WebP keep transparency and
JPEG uses a white background. Bitmap exports use up to 2× logical resolution,
capped at 2,048 pixels on their longest edge. Export does not change the note.

## Open and manage notes

Tap the **overlapping-notes button in the toolbox**. With one note open, select
another notebook and choose **Left, Right, Top, Bottom, or Floating window**.
With several notes open, the button toggles a small tray beside that button:
**+** opens another floating note. Each minimized note has its own page-icon
button to restore it.
Each icon identifies its notebook through its tooltip/accessibility name.
Only notebooks not already in the workspace appear in the picker.

There can be **two split notes at most**, alongside multiple floating notes.
Docking a different note floats the previous secondary split note. Closing a
window never deletes its notebook.

**Tap inside a note with the stylus to edit it.** The activating tap is consumed
before switching the writer, so it does not leave ink in either note. Notes
retain their placement during focus switches. Fingers independently scroll
through the continuous page stack and pinch to zoom in every visible note;
finger navigation does not change editing focus. Tapping a figure in an inactive
note activates that note and opens the figure controls.
New readers start at **fit width**. Ink remains vector-rendered, and PDF tiles
are requested at the current
screen resolution after pan/zoom. Imported raster figures retain their source
resolution limits.

Each note has two small controls over its lower corners, with no reserved top
or bottom bar. Tap the **bottom-left options icon** for position, size presets,
minimize, or close. In a floating note, **drag** this icon to move the window
without waiting for a hold. Tap the **bottom-right minimize icon** to hide the
window in the toolbox tray; **drag** it to resize from its lower-right corner.
A lightweight boundary follows the resize gesture while page rendering keeps
its previous size; the note refits after release. Small finger jitter remains
a tap. See the [interaction follow-up](window-figure-interactions.md) for validation.
Minimizing or closing the editable window first hands editing to another
visible note. The last visible note remains open.

All notes share **one floating toolbox** whose actions apply to the editable
note. From **0.33.0-dev.5**, its application sublayer keeps it above note windows,
status islands and selection actions regardless of which panel was opened last. The **top-left status island** shows its title, page and zoom without an
“Active” prefix, including when a floating note is editable. Both status islands
float over full-screen note content in every layout. Each notebook retains its
saved pages and session undo history. Page, logical view center and width-relative
zoom transfer between reading and editing; note windows, minimized states,
placement, size and reading positions persist across recreation and app reopen.
Per-note link/outline navigation history is retained during focus switches in
the same Activity. Existing two-note preferences migrate on first open.

## Storage and architecture

Stable 0.33.1 used page format **6**, which adds an ordered figure list after
the version-5 stroke list. Each figure has a UUID, logical x/y/width/height,
and length-prefixed PNG data. Stable **0.33.2** writes format **7**, adding
the figure’s clockwise rotation angle. Original PNG bytes stay unchanged when
moving, resizing or rotating; raster sharpness remains limited by source pixels.
Page framing, CRC checks, atomic snapshot publication and journaled stroke
appends are unchanged. Versions 1–6 still read without modification; the first
successful edit writes version 7 and retains the predecessor `.bak`. Older
APKs, including stable 0.33.1, cannot read version-7 pages or backups containing
them. New editable backups also contain version-7 pages. Preserve independent
editable backups before updating.

Figures participate in the existing per-page undo history and retry/conflict
checks. Page duplication creates fresh figure IDs. Inline image data keeps
backup/restore self-contained without external asset paths. PDFBox composes
paper, images and vector ink in that order, retaining original PDF content and
using a 16 MiB stream-memory budget before spilling to disk.

The companion reads through the existing serialized writer and the library’s
cached books without changing the active notebook, page selection, or history.
Readers retain only visible immutable pages and cached vector stroke outlines.
Each reader uses the same continuous viewport geometry and PDF tile cache as
the editor, without flattening handwriting into a thumbnail. PDF link parsing
is disabled in readers; link/outline tools belong to the editable note.
From **0.33.0-dev.4**, activation flushes completed ink and pauses native input
before changing the writer's active book, then reuses the same native widget in
the selected pane. Its callback lifetime is separate from the writer-operation
generation, so retained input uses the current notebook while retired widgets
cannot submit strokes. The incoming reader stays visible until native content
is ready. An outgoing editor temporarily retains a complete viewport image until
its original vector pages and PDF tiles are ready; this image is never used as
a permanent zoom source.
Inactive split panes are ordinary views; an inactive floating pane uses a popup
and joins native ink exclusion rectangles. An active floating pane hosts the
single native surface in its own window. The toolbox and status bar stay anchored
to the app root. Floating-window operations retain unrelated reader caches and
the native editor. If editor dimensions are unchanged, only exclusions and a
complete existing paper/track post change. A resized editor waits for matching
native geometry, then recomposes its viewport at the retained logical anchor.
Paper/ink rendering uses a staging bitmap, followed by one opaque copy into
the bound bitmap, so repaint never exposes a white intermediate. Resuming the
app schedules through its attached root, including when the floating editor
was detached during pause.

## Verification

`./scripts/test.sh` covers figure immutability and bounds, insertion/move/delete
undo and redo, failed-save retry, legacy non-mutating reads and v7 migration,
backup round trips, page duplication, cross-page ink undo with figures,
independent cold/cached companion reads, all split positions, multiple floating
notes, minimized-state persistence, the two-split limit,
and SVG crop/color/opacity/fill rules. Production activation callbacks are also
executed with queued writer/surface adapters: they check exclusive input,
writer-before-surface ordering, unchanged pane identities, read-view transfer,
failed-save rejection, stale/background continuations, and closing/collapsing
any editable pane. Reader callbacks run with synthetic touch/drawing adapters
to check continuous cross-page scrolling, pinch anchors, width fitting, vector
replay, PDF tile resolution, cache retirement and minimized-note restoration.
Corner-grip callbacks check tap/drag intent, immediate movement, anchored resizing,
cancel rollback, busy-writer rejection and delayed-callback retirement.
Display callbacks check editor retention for float open/move/resize/close and
minimize/restore, asynchronous layout/native-size races, background completion,
current/stale native stroke ownership, complete-frame retention through PDF
loading, and paint-before-bind / staging-before-copy order. These host adapters
do not establish the visible waveform or latency on the physical e-ink screen.
The PDF engine regression checks figures
with and without transparency on ordinary and imported PDF pages, their pixel
sizes and transformation matrices, and retention of original content. Host
PDF tests use ImageIO as the Android bitmap-decoder adapter; they do not prove
the tablet’s codecs or native rendering.

Dev.5 also exercises real v6-to-v7 migration, arbitrary-angle geometry/hit testing,
linked figure-move undo/redo, interrupted two-page save recovery, backups and
rotated PDF image matrices. Production inline gesture callbacks run with queued
UI adapters for outside taps, nearby actions, pan/pinch cancellation, corner
resize/rotation, edge scrolling and save/native-ready ordering. See
[inline figures](inline-figures.md) for the implementation and acceptance checklist.

For device acceptance, use disposable notes: insert SVG/transparent PNG/rotated
JPEG, annotate and move figures, undo/redo and reopen, export all five lasso
formats, open a split plus at least two floating notes, and switch editing with
stylus taps while retaining all placements. Scroll across page boundaries and
zoom text/ink in each inactive note. Minimize several windows and restore them
individually from the tray. Hold/drag both corner controls, dock a third note,
leave/resume a floating editor, and rotate with multiple notes open. Verify the
status/toolbox follows focus, zoom detail resolves, gestures during writing are
ignored, and pen input never appears in an inactive note or under controls. For dev.5,
position a floating note/status/selection panel over the toolbox and confirm the
toolbox remains in front; complete a lasso and open its actions, width sliders,
zoom indicator and figure editor while a floating note is editable.
For dev.4, watch a main note containing both PDF text and recent native ink while
moving, resizing, opening, closing, minimizing and restoring floating notes with
either pane editable. The main note must never go white; newly exposed areas
must retain their ink, focus changes must remain covered until content is ready,
and pen capture must follow the resized/moved window accurately.
