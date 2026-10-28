# Figures, selected-ink export, and a second note

Available in **0.33.0-dev.1**, a development build. Automated model/export checks
and Android builds are available; touch, native handwriting clipping, and
floating-window behavior on the MatePad still require physical acceptance.

## Insert and edit figures

Open the editor’s **⋮ → Insert figure…**, choose an image with the system file
picker, then return to the note. The figure is centered on the current page.
Choose **⋮ → Figures…**, select a figure, and drag it to move it. Drag its
lower-right handle to resize while retaining its aspect ratio. **Apply** saves
one undoable edit; **Cancel** discards placement changes; **Delete** is undoable.
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

## Open two notes

Choose **⋮ → Open another note…**, select a different active notebook, then
choose **Left, Right, Top, Bottom, or Floating window**.

The second pane has its own previous/next page buttons, pinch zoom, and drag
panning. Tap its bottom **Fit page** strip to reset zoom. Tap **Activate** in
the inactive pane (or **Workspace… → Activate other note**) to edit that note
in its existing position. The notes never exchange positions during activation.
Only one note accepts writing, erasing, lasso operations, or other edits at a
time; the inactive note remains available for reading, navigation, pan and zoom.
Scrolling or zooming the inactive note does not activate it.

Both notes share **one floating toolbox**. Its actions always apply to the
active note. The **top-left status bar stays at the app level** and shows the
active note’s title, page and zoom, including when the floating note is active.
From 0.33.0-dev.2, both top status islands float over the full-screen note
content in every layout; opening or collapsing a second note reserves no top strip.
Each notebook retains its saved pages and session undo history. Page, view
center and zoom transfer between reading and editing, and each note keeps its
link/outline navigation history during activation switches.

Drag the floating window’s title to move it and its bottom strip to resize it.
Use the pane’s **⋮** or the main **⋮ → Workspace…** to reposition it, adjust its
size, reset the split, or close it. **−** collapses the second note; the
**Reopen note ▣** button restores it. Collapsing returns the space to the main
page. The second note, selected page, placement, size, and collapsed state are
saved across app reopen and orientation changes, including which pane is active.
Reading zoom/pan is retained during activation and collapse/reopen in the same
Activity; it resets after Activity recreation. If the second note is active,
collapsing or closing it first activates the primary note. Closing a pane never
deletes a notebook.

## Storage and architecture

Page format **6** adds an ordered figure list after the version-5 stroke list.
Each figure has a UUID, logical x/y/width/height, and length-prefixed PNG data.
Page framing, CRC checks, atomic snapshot publication and journaled stroke
appends are unchanged. Versions 1–5 still read without modification; the first
successful edit writes version 6 and retains the predecessor `.bak`. Older
APKs cannot read version-6 pages or backups containing them. Preserve independent
editable backups before testing the development build.

Figures participate in the existing per-page undo history and retry/conflict
checks. Page duplication creates fresh figure IDs. Inline image data keeps
backup/restore self-contained without external asset paths. PDFBox composes
paper, images and vector ink in that order, retaining original PDF content and
using a 16 MiB stream-memory budget before spilling to disk.

The companion reads through the existing serialized writer and the library’s
cached books without changing the active notebook, page selection, or history.
Its bounded preview is rendered on the existing render queue. Activation flushes
completed ink and retires the old native surface before changing the writer’s
active book. The editor is then placed into the activated note’s existing pane.
Inactive split panes are ordinary views; an inactive floating pane uses a popup
and joins native ink exclusion rectangles. An active floating pane hosts the
single native surface in its own window. The toolbox and status bar stay anchored
to the app root. Placement changes rebuild the viewport at its retained page
anchor; resuming the app schedules through its attached root, including when
the floating editor was detached during pause.

## Verification

`./scripts/test.sh` covers figure immutability and bounds, insertion/move/delete
undo and redo, failed-save retry, v5 non-mutating reads and v6 migration,
backup round trips, page duplication, cross-page ink undo with figures,
independent cold/cached companion reads, all split positions, collapse bounds,
and SVG crop/color/opacity/fill rules. Production activation callbacks are also
executed with queued writer/surface adapters: they check exclusive input,
writer-before-surface ordering, unchanged pane identities, read-view transfer,
failed-save rejection, stale/background continuations, and closing/collapsing
an active second pane. The PDF engine regression checks figures
with and without transparency on ordinary and imported PDF pages, their pixel
sizes and transformation matrices, and retention of original content. Host
PDF tests use ImageIO as the Android bitmap-decoder adapter; they do not prove
the tablet’s codecs or native rendering.

For device acceptance, use disposable notes: insert SVG/transparent PNG/rotated
JPEG, annotate and move figures, undo/redo and reopen, export all five lasso
formats, activate/collapse/reopen all five workspace placements, and verify
that activation keeps both notes in place and updates the shared status/toolbox.
Drag/resize the floating window with either note active; leave and resume the
app or a file picker with the floating editor active; rotate with both notes
open; verify that pen input never appears in the inactive note or under controls.
