# Scriflow user guide

Scriflow is designed for Huawei MatePad Paper HMW-W09 on Android 10/API 29,
firmware `HMW-W09 2.1.0.222(C00E170R1P8)`. Other devices and firmware have not
been verified. See the [README](../README.md) for installation and current limits.

## Notebooks and pages

Open **Scriflow** to see the library. Tap **My Notebook** to open existing
pages, or **+ Notebook** to create and open a named notebook. **Rename** changes
its title without moving its files. Names may contain up to 80 Unicode
characters; the library supports up to 200 notebooks. The editor's **books icon**
returns to the library. Each notebook remembers its own reading position.

Tap **Quick A5 note** to create and open an A5 notebook immediately with an
automatic name. **+ Notebook** opens the named creation form.
New notebooks default to **A5 (148 × 210 mm)**. The creation form offers
standard paper sizes, including A4, ISO B5, and Letter, and a **Custom** choice
with width and height in millimetres. Newly added or inserted blank pages
inherit the source page's dimensions. PDF export uses the chosen physical size;
changing screen orientation or zoom does not change the paper. Existing notes
keep their original coordinates and PDF printing scale.

## Zoom and screen orientation

New notes open at **Fit width**. Pinch with **two fingers** to zoom in or out;
a percentage indicator appears during zoom. The zoom controls offer **Fit width**,
**Fit height**, **Fit page**, and fixed percentages. Drag with a finger to pan
across enlarged pages and scroll through the notebook. Space outside the paper
is white. Ink stays attached to the page when you zoom or rotate.

Tap the notebook title/percentage in the top-left island to open the zoom
controls and **App settings**.

Open **Settings** from the library or **App settings** from the editor. Choose **Portrait**,
**Landscape**, or **Follow device sensor**. The choice is saved for the whole
app. Sensor mode follows the tablet even if system rotation is locked; while
writing, rotation waits until you lift the pen. Screen rotation rebuilds the
writing surface without changing stored page dimensions.

## Writing and saving

Write with the stylus. Completed strokes save automatically in the background;
there is no manual save button. The three-dot menu shows the page's save state.
If a save fails, writing pauses and **Not saved · Retry** appears. Retry before
leaving or stopping the app so unsaved work has a chance to be written.

The **paper icon** switches the current page between blank and lined paper
without clearing its writing. **Drag vertically with one finger** to scroll,
then lift it to stop anywhere, including between pages. Write with the stylus
on either visible page. Scrolling has no page snapping or inertial fling.

The **left/right arrows** jump to page tops. The **page-plus icon** appends an
empty page using the active page's paper. Each page keeps its own writing and
paper choice. The page limit is 1,000; page management is available from 0.29.
These limits are not demonstrated large-notebook performance guarantees.

The top-left floating island reads **Notebook name · X / Y**. Its page is the
most recently written page, or the page nearest the viewport center after scrolling. Eraser,
Undo/Redo, and paper choice use that context. Fractional scroll position is
saved per notebook. Existing pages keep their contents when their order changes.

## Overview and jumps

Tap the **grid icon** to browse whole-page thumbnails. The current page has a
black border and a **Current** label. Tap a thumbnail to jump to its page top,
or enter a number beside **Go to page** and tap **Go**. **Close** or Android
Back returns to the exact previous scroll position when the page order is
unchanged. Thumbnails load as you browse; opening the overview does not edit pages.

Choose **Manage**, then tap a page for its actions. You can also hold a thumbnail
to open the same actions while browsing:

- **Move earlier/later** shifts the page by one position. **Move to page…** accepts
  its final page number, from 1 to the notebook's current page count.
- **Duplicate** places an independent copy immediately after the original,
  preserving its paper and handwriting appearance.
- **Insert blank before/after** adds a page with the chosen page's paper and size.
- **Delete…** asks for confirmation, then removes the page from the notebook.
  The notebook always keeps at least one page.

Changes save automatically. **Undo page change** and **Redo page change** appear
at the bottom of the overview; they are separate from the toolbar's ink history.
They retain up to 50 page changes per notebook until the app restarts. A new
page change clears page redo; writing on an existing page keeps that history.
Restoring a page brings back its writing and its available ink undo history.

After managing pages, Close returns to the selected page's top. New thumbnails,
navigation, PDF exports, and editable backups follow the new order. Removed
pages are excluded from exports and backups. Their private files remain for
recovery; page deletion does not immediately reclaim storage.

The first page-management edit upgrades that notebook's order index. Earlier
APKs cannot read the managed notebook; preserve an editable backup before updating.

## Floating tools

The controls form a single row or column of 32 dp circular buttons inside a
pill-shaped dock. Tap the **direction icon beside the grip** to switch
orientation. Drag the **six-dot grip** with a finger or stylus to move the dock;
it stops where released. **Tap the grip once** to collapse all tools into that
one circle; tap again to expand. The collapsed grip can still be dragged.
Position, orientation, and collapsed state are remembered. Only the grip moves
the tools.

**Pen** uses a pencil symbol; **Eraser** uses a tilted eraser. A thick border
marks the selected tool. **Lasso** uses a dashed loop (available in development
builds after 0.28.0-beta.2). The curved arrows are Undo/Redo, the grid opens the
overview, and the three-dot menu offers page information, PDF export, and
**Reset tools**. Reset expands the dock and returns it to its initial horizontal
position near the top center, below the status islands. Touching controls has
no ripple or pressed inversion; the page stays writable outside the tools.

## Pen thickness

Tap the **already-selected Pen icon** to open a small width slider beside it.
Drag left for thinner or right for thicker strokes. A sample line and number
preview the width. **Release to apply and close**; tap outside or press Back to
cancel. Tapping the track selects a width directly.

Widths run from **1.0 to 10.0**, in 0.1 steps; the original default is **4.0**.
The setting is remembered across sessions and notebooks. A line beneath the
pencil previews it. Width changes affect new strokes. Saved writing, undo/redo,
scroll previews, thumbnails, and PDFs preserve each stroke's own style and
recorded pressure shape.

## Erasing and undo/redo

Choose **Eraser**, move the stylus across writing, then lift it to remove every
touched stroke. A temporary **grey trail** follows the erasing path through
the native handwriting engine. After the erase completes, the touched ink and
trail disappear together. Erasing removes whole strokes rather than portions.

Tap the **already-selected Eraser icon** for its width slider: **4–240** in
steps of 1, with **36** as the default diameter. It is independent of pen width
and remembered across sessions. A wide eraser can reach page edges from the
gap between pages. A no-hit gesture clears only the trail; cancelling leaves
the ink intact. Very fast passes at the maximum width can show visible lag;
normal width-36 erasing has been physically verified on the target tablet.
Choose **Pen** to resume writing.

**Undo/Redo** retains up to 100 writing or erase actions **per page** within
the current app process. It survives leaving and returning while the process
stays alive, but process restart begins a new history. Erasing, undo, and redo
autosave. New writing or a successful erase clears Redo; a no-hit erase
preserves it. Changing paper leaves stroke history available.

## Selecting and moving ink

Choose **Lasso** and circle the strokes you want. A native dashed line follows
the stylus. **Lift to visibly close the curve**, including a final edge back to
its start when you leave a gap. Strokes whose entire centreline lies inside it
are selected. A stroke crossing the boundary stays unselected. Selection
works on one page at a time; it does not cut strokes into pieces.

A small action bar appears beside the region with its selected stroke count.
**Drag anywhere inside the curve with a finger or the stylus** to move the ink;
its preview follows your movement. Lift to save the move. Movement stops at
page edges and keeps the strokes together. There is no separate Move command.
The nearby actions are:

- **Duplicate** makes an offset copy and selects it.
- **Copy** keeps the selected ink on Scriflow's private clipboard.
- **Cut** copies, then removes the selection; **Delete** removes it directly.
- **Done** clears the selection. A tap outside the curve also clears it.

After Done, tap the already-selected Lasso icon for these additional actions:

- **Paste** places a fresh copy near the visible centre of the current page.
  You can then drag it into position. Copy/paste works across pages and
  notebooks, while the app process stays alive. It is not the Android system
  clipboard. Paste is offered when copied ink fits the destination page.
- **Select all on this page** selects its ink and shows the action bar.

If the curve encloses no complete strokes, it stays visible with **No strokes
selected**. Tap Done or outside it, then draw around the complete ink strokes.
While a region is active, a finger inside moves it. Clear the selection before
finger scrolling. Change tools, navigate to another page, or press Back to clear
the selection. Undo/Redo also clears it. Each completed move, duplicate,
cut, paste, or delete is one undoable action on its page and saves automatically.
Selecting, copying, and deselecting do not change history or saved notes. Existing
pressure, thickness, colour, and sample timing are retained.

The owner confirmed that the updated interaction works well in 0.29.0-dev.2
and accepted the signed APK for release. See the [device record](device-testing.md)
for the scope of that feedback and automated checks.

## Status and display refresh

Two small floating islands show the notebook/page at the top left and time,
battery level, and Wi-Fi state at the top right. The paper fills the screen
behind them, including the space between the islands; there is no reserved
header row. Time follows the tablet's 12/24-hour setting and
updates by the minute. Device-status changes wait until writing has been idle
for about a second; no per-second updates run during handwriting.

Huawei's firmware manages handwriting and scrolling through the app's refresh
policy. On the tested firmware, physical acceptance of version 0.26 confirmed
scrolling followed by immediate writing without transition flashes, inversion,
or freezing. This is a user observation, not a measured optical-latency claim;
each new release still needs acceptance. See [refresh investigation](fast-refresh.md).

Use the tablet's **Refresh now** action to clear residual images. No global
refresh setting or extra permission is needed. Unrelated background display
activity can interfere with this firmware: during development, Clash Meta's
**Show Traffic** notification caused one-second updates that disrupted writing.
Disabling that feature while retaining the VPN improved the original probe.
See the [device record](device-testing.md) for the evidence and limits.

## Saving and safe updates

Saving runs after firmware completion of each stroke. Stored pages retain vector
points, pressure, timestamps, and pen styles; reopening restores editable native
tracks rather than a screenshot. Background work and idle status changes wait
until the pen is idle.

After lifting the pen, pause briefly for the background writer before
force-stopping the app. Check the menu's save state if uncertain. An unfinished
stroke is discarded when leaving the app. A save error can leave accepted edits
only in memory until a successful retry.

Notes live in the app's private storage. **Uninstalling or clearing storage
deletes them.** Back up each notebook outside that storage before updating.
An ordinary update requires the same application ID and signing certificate.
If an APK is rejected for a signature mismatch, follow the
[signing migration procedure](releasing.md#moving-from-a-development-key).

The app can read legacy version-1 pages without changing them on open. Their
first edit atomically upgrades them to version 2 and retains the predecessor
as a backup. Older APKs cannot read upgraded pages. Do not downgrade by
installing an old APK over current notes, or move raw legacy page files as a
substitute for editable backup/restore.

## Editable backups and restore

In the library, **Backup** beside a notebook saves an editable `.inkbook`
archive through Android's file picker. Keep it outside the app's private
storage and retain another copy on a separate device if the notes matter.

**Restore backup** above the library list validates an archive and adds a
**new notebook**, preserving existing notebooks. Writing, pressure, pen styles,
page/stroke IDs, names, and page order survive. The restored book starts at
Page 1. Undo history and device settings are excluded.

Backups are **unencrypted** and limited to 512 MiB uncompressed per archive
(64 MiB per page). If a destination-provider write fails, an incomplete file
may remain; retry with a new location. After saving a backup, restore it as a
separate notebook and open representative pages to confirm it is usable before
depending on it for migration. See [privacy](privacy.md) for storage details.

## PDF export

In an open notebook, choose **three-dot menu → Export PDF**. All pages prepare
in order, including writing and blank/lined backgrounds, then the Android save
picker asks for a filename and location. Wait for **PDF saved**. Cancelling the
location picker lets you choose again without preparing the PDF again.

PDFs contain sharp vector outlines from the native pressure brush, preserve
page proportions, and use a standard A4-width scale. Export leaves the notebook
editable. Export again to replace PDFs produced by older fixed-width rendering.
**A PDF is a sharing copy, not a restorable editable backup.** Use `.inkbook`
for backup and migration.
