# Scribblow user guide

Scribblow is designed for Huawei MatePad Paper HMW-W09 on Android 10/API 29,
firmware `HMW-W09 2.1.0.222(C00E170R1P8)`. Other devices and firmware have not
been verified. See the [README](../README.md) for installation and current limits.

## Notebooks and pages

Open **Scribblow** to see the library. Tap **My Notebook** to open existing
pages, or **+ Notebook** to create and open a named notebook. **Rename** changes
its title without moving its files. Names may contain up to 80 Unicode
characters; the library supports up to 200 notebooks. The editor's **books icon**
returns to the library. Each notebook remembers its own reading position.

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
paper choice. The page limit is 1,000; deletion and reordering are not included.
These limits are not demonstrated large-notebook performance guarantees.

The header reads **Notebook name · X / Y**. Its page is the most recently
written page, or the page nearest the viewport center after scrolling. Eraser,
Undo/Redo, and paper choice use that context. Fractional scroll position is
saved per notebook. Legacy single-page notebooks remain Page 1 in place.

## Overview and jumps

Tap the **grid icon** to browse whole-page thumbnails. The current page has a
black border and a **Current** label. Tap a thumbnail to jump to its page top,
or enter a number beside **Go to page** and tap **Go**. **Close** or Android
Back returns to the exact previous scroll position. Thumbnails load as you
browse; opening the overview does not edit pages.

## Floating tools

The controls form a single row or column of 36 dp buttons. Tap the **direction
icon beside the grip** to switch orientation. Drag the **six-dot grip** with a
finger or stylus to move the strip; it stops where released and remembers its
position and orientation. Only the grip moves the tools.

**Pen** uses a pencil symbol; **Eraser** uses a tilted eraser. A thick border
marks the selected tool. The curved arrows are Undo/Redo, the grid opens the
overview, and the three-dot menu offers page information, PDF export, and
**Reset tools**. Reset returns the strip to its initial horizontal, top-center
position. Touching controls has no ripple or pressed inversion; the page stays
writable outside the tools.

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

## Status and display refresh

The slim header shows the notebook/page on the left and time, battery level,
and Wi-Fi state on the right. Time follows the tablet's 12/24-hour setting and
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
