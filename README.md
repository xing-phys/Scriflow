# Ink Notebook

A small notebook app for Huawei MatePad Paper HMW-W09, with blank/lined pages,
native low-latency handwriting, automatic saving of completed strokes, and
editable restoration after reopening.

The current build is an early development APK, version 0.17, with compact, rotatable icon
tools, a notebook library, continuously scrolling pages, a page overview with
jumps, PDF export, undo/redo, and a whole-stroke eraser.
Handwriting uses a black ballpoint pen with adjustable thickness. The original diagnostic screen remains
available separately for development.

## Use

Open **Ink Notebook** to see your library. Tap **My Notebook** to open your
existing pages, or **+ Notebook** to create and open a named notebook. **Rename**
changes a notebook's title without moving its files. Each notebook remembers its
own last page; the editor's **books icon** returns to the library. Names may
contain up to 80 Unicode characters. This milestone supports up to 200 notebooks.

Write on a page; completed strokes save automatically in the background.
There is no manual save button or routine saved-stroke counter. The menu shows
the page's save state, and a save failure pauses writing and exposes **Not saved · Retry**.
The **paper icon** changes
the current page's paper without clearing writing. Tap it to toggle between
blank and lined paper. The menu shows the page's save state.

**Drag vertically with one finger** to scroll, then lift it to stop at any
position, including between pages. **Write with the stylus** on either visible
page. There is no snapping or inertial fling. The **left/right arrows** jump to page tops,
and the **page-plus icon** appends an empty page at the end. New pages inherit the active
page's paper. Each page keeps its own writing and paper choice. The indicator
shows **Notebook name · X / Y** for the most recently written page, or the page nearest
the viewport center after scrolling. Eraser, Undo/Redo, and paper choice use
that page context. The fractional scroll position is saved per notebook.
Your existing single-page notebook becomes Page 1 without moving its file.
There is a limit of 1,000 pages; deletion and reordering are not yet included.

Tap the **grid icon** to browse whole-page thumbnails.
The current page has a black border and a **Current** label. Tap a thumbnail to
jump to that page's top, or enter a number beside **Go to page** and tap **Go**.
**Close** or Android Back returns to your exact previous scroll position.
Thumbnails load as you browse; opening the overview does not edit your pages.

The tools float over the page as a **single row or single column**, with smaller
36 dp buttons. Tap the **direction icon beside the grip** to switch orientation.
Drag the **six-dot grip** with a finger or stylus to move the strip; it stops
where you release it and remembers its position and orientation between sessions.
**Pen** uses a pencil symbol;
**Eraser** uses a tilted eraser. The selected tool has a **thick border**, while
the other has a thin border. The curved arrows are Undo/Redo, the grid opens
the overview, and the three-dot menu offers page information, PDF export, and
**Reset tools**. Reset returns to the initial horizontal, top-center position.
Only the grip moves the palette. Touching controls has no ripple or pressed
inversion; the page remains writable outside the floating tools.

Tap the **already-selected Pen icon** to open a **small width slider beside it**.
Drag its point left for thinner strokes or right for thicker strokes; a sample
line and number preview the width. **Release to apply and close** the bar, or
tap outside/press Back to cancel. Tap anywhere on its track to select directly.
Widths range from **1.0 to 10.0**, in 0.1 steps, with **4.0** as the original
default. The choice is remembered between sessions and across notebooks.
The small line beneath the pencil previews the selected thickness. Changing
the pen affects only new strokes; saved writing, undo/redo, and exported PDFs
retain each stroke's own width.

The slim top bar shows the notebook name and page number on the left, with time,
a battery icon and percentage, and a Wi-Fi icon on the right. Wi-Fi indicates
connected/disconnected state. Time follows the tablet's 12/24-hour setting and
updates by the minute. Device-status changes wait until writing has been idle
for about a second; there are no per-second display updates.

To export the open notebook, tap **three-dot menu → Export PDF**. All pages are prepared in
order, including their writing and blank/lined backgrounds, then the Android
save picker lets you choose a filename and location. Wait for **PDF saved**.
The PDF contains sharp vector strokes, with page proportions preserved and a
standard A4-width scale. Export leaves the notebook editable in the app.
Cancelling the location picker lets you choose again without preparing it again.

Choose **Eraser**, move the stylus across writing, then lift it to remove every
touched stroke. There is no live eraser preview; a gesture removes entire strokes,
not small pieces of a stroke. Choose **Pen** to resume smooth native writing.
**Undo/Redo** remember up to 100 writing or erase actions **per page** within the current app
process. They survive leaving and returning while that process remains alive;
restarting the process begins a new history. Erasing, undo, and redo autosave.
New writing or a successful erase clears Redo; a no-hit erase preserves it.
Changing paper leaves the stroke history available.

Saving runs on a background worker after the firmware commits a complete stroke.
The page retains vector points, pressure, timestamps, and pen style, so reopening
restores editable native tracks rather than a screenshot. The paper background
is static. Idle UI and system-status changes wait until the pen is idle; there
is no live counter or periodic toolbar refresh during handwriting.

Completed strokes are queued for durable saving when lifted. Pause briefly to
let the background writer finish before force-stopping the app; the menu reports
its save state. An unfinished stroke is discarded when leaving the app. Notes are stored on this tablet in
the app's private directory; uninstalling the app removes that data. Keep the
local signing key when installing updates over an existing version.
Version 0.17 reads the unchanged version-1 page format from earlier releases. Install it as
an update over the existing app; **do not uninstall** to install the update.

This firmware is sensitive to unrelated background display activity. During
testing, Clash Meta's **Show Traffic** notification generated one-second updates
that interfered with handwriting. Turning that feature off while retaining the
VPN substantially improved the original probe. See the
[device investigation](docs/device-testing.md) for evidence and remaining limits.

## Build and install

Prerequisites: Linux x86_64, Java/Javac 8, Python 3, curl, and unzip.
ADB is optional for installing or testing through USB.
No Android Studio or Gradle is required. Bootstrap downloads checksum-verified
Android API 29 and Build Tools 30.0.3 into ignored `.tools/`.

```bash
./scripts/bootstrap.sh
./scripts/test.sh
./scripts/build.sh
adb -d install --no-incremental -r build/inkprobe-debug.apk
adb -d shell am start -W -n org.eink.inkprobe/.notebook.NotebookLibraryActivity
./scripts/read-display-policy.sh
```

The package remains `org.eink.inkprobe` so updates retain this workspace's app
identity. Its launcher label is **Ink Notebook**. It is debug signed with an
ignored local key and requests only the normal `ACCESS_NETWORK_STATE` permission
for the Wi-Fi indicator. It makes no network requests. No proprietary
Huawei code is bundled. Reflection calls use classes already on this firmware;
failures pause writing explicitly.

## Verification

Host tests exercise callback timing, continuous-path eraser hit testing,
bounded undo/redo, and the real-file notebook store/coordinator. They cover a
frozen 0.7 document fixture, edits followed by reopening, interrupted appends,
checksum corruption, and save-error recovery with optimistic unsaved edits.
They do not execute Android SDK stubs.
Index and notebook tests also cover legacy Page 1 byte preservation, per-page
history and paper isolation, last-page restoration, interrupted page creation,
and failed navigation without replacing the visible page or overwriting data.
Library tests cover old notebook files remaining in place, independent notebook
positions/history, naming, creation, catalog retry, and preservation of missing
or corrupt data. The checksummed catalog treats names as metadata; paths use
stable numeric IDs.
Scrolling tests additionally cover clipping and strict page bounds, strokes
crossing page boundaries, per-page gesture undo, mixed finger/pen ownership,
pending preview acknowledgements, and saved fractional positions. These tests
cannot establish native scrolling speed or physical pen latency.
Overview tests cover read-only page previews, accepted unsaved in-memory ink,
unchanged page/index/position files, and isolated corrupt or missing preview
pages. Reading an uncached thumbnail does not retain its document in the editor.
PDF tests validate vector coordinates, ruling, dots, opacity, Unicode titles,
exact cross-reference offsets and stream lengths, output failures, cancellation,
and real-library export isolation. Synthetic PDFs are also rendered with Poppler
for visual review; Android picker behavior still needs tablet acceptance.

The debug build also contains an isolated on-device restoration check. It opens
only synthetic drawings and writes results under `files/notebook-selftest/`,
separate from the actual notebook:

```bash
adb -d shell am start -W -n org.eink.inkprobe/.notebook.NotebookSelfTestActivity --es template lined
adb -d exec-out run-as org.eink.inkprobe cat files/notebook-selftest/result.json
```

Use `--es template blank` for blank paper. This verifies native vector restoration
and background rendering; real pen testing is still required for physical
latency, completed-stroke capture, and continued writing after reopening.

Add `--ez pauseBoundary true` to the disposable check to simulate completed-UP,
partial, and cancelled input around a pause. This is explicitly a simulated
callback-order test, not a physical stylus benchmark.

Add `--ez inPlaceEdits true` to exercise synthetic erase/undo/redo and page
switching with opposite paper templates on the same surface and bitmap. This
optional device check is prepared for 0.9 but was not run during offline development.

To explicitly verify the actual notebook's restored native tracks in a debug
build, cold-launch NotebookActivity with `--ez verifyRestore true`, then read
`files/notebook-verification/result.json`. The check compares coordinates,
pressure, timestamps, and style and exports only result metadata. It does not
change the document and does not run during normal use.

The first milestone passed physical acceptance on this tablet: smooth continued
writing after reopening, visible save-count updates, and 36 saved strokes with
all 22 pre-restart strokes preserved exactly. Both templates passed native
restoration checks. This is a device-specific usability result, not a measured
optical latency claim.

Version 0.8 passed user testing for erasing, undo, and redo. The user observed
occasional disappearance/reappearance of all ink during those edits. Version
0.8.1 keeps the native surface and background in place and updates tracks in a
single posted frame; a no-hit erase skips the redraw. This visual fix was
developed offline and still needs physical tablet confirmation. See
[editing milestone and installation checks](docs/notebook-editing.md) and
[the edit refresh fix](docs/notebook-edit-refresh.md).

Version 0.9 removes Material button ripples and animations and keeps toolbar
appearance static while saves/edits run. Availability remains enforced in click
handlers and exposed to accessibility. Ordinary page and paper changes reuse the
bound bitmap and native surface, posting the new content together. The button
flash improvement and new page UI still need physical tablet acceptance. See
[multiple-page milestone](docs/notebook-pages.md).

Version 0.10 adds the library home screen and keeps the original notebook at its
existing path as **My Notebook**. New notebook creation, selection, and renaming
run on the same background worker as writing. The app builds offline with the
same signing key; the new library UI still needs tablet acceptance. See
[notebook library milestone](docs/notebook-library.md).

Version 0.11 keeps one native writing surface fixed on screen. Finger dragging
uses cached page previews; after release, the exact visible page portions become
native editable tracks again. Pen input waits until all visible content has
loaded. Completed strokes are saved to explicit page IDs, including strokes
that cross a page gap. The new hardware path still needs tablet acceptance.
See [continuous pages](docs/continuous-pages.md).

Version 0.12 adds a thumbnail overview and direct page-number jumps. Preview
reads and rendering share the serialized worker, with one request in flight and
a 12-bitmap cache. Closing the overview resumes the existing native surface;
choosing a page uses the same validated window read as page navigation. The
13,347 host assertions pass and the signed APK builds offline; the new overview
still needs tablet acceptance. See [page overview and jumps](docs/page-overview.md).

Version 0.13 adds whole-notebook PDF export through the system save picker.
Preparation reads one page at a time on the notebook worker, then a separate
file worker copies the completed PDF to the selected location. Export errors
remain separate from notebook save errors. All 16,902 host assertions pass;
the production writer's blank/lined sample pages pass structural and visual
checks. See [PDF export](docs/pdf-export.md) for behavior and tablet checks.

Version 0.14 replaces the editor's text toolbar with draggable vector-icon
controls. Native pen clipping excludes the palette, and toolbar contacts pause
handwriting until all contacts lift. The selected Pen/Eraser border changes
without animating the other controls. All 16,947 host assertions pass, including
physical screen-to-surface exclusion checks. Icons and both selected states were
visually inspected; window movement, pen protection, and refresh behavior still
need tablet acceptance. See [floating tools](docs/floating-tools.md).

Version 0.15 shrinks the controls to a single rotatable strip and replaces the
large status row with a 24 dp header. Routine save counts and the redundant
Overview label are removed. Device-status events coalesce until pen idle, and
Wi-Fi is observed passively even when a VPN is active. All 17,392 host assertions
pass, including 445 new strip-layout and status-coalescing checks. Both
orientations were visually inspected; live status and pen behavior still need
tablet acceptance. See [compact tools and status](docs/compact-tools-status.md).

Version 0.16 adds five pen thicknesses through the selected Pen icon, with a
remembered choice and line samples. Widths use logical page units, preserving
their proportions when scrolling, reopening, or exporting. Completed-UP fallback
capture now retains the style from that stroke's DOWN. All 17,749 host assertions
pass, including 357 mixed-width, durable editing, fitting, and export checks.
The signed APK builds offline; live native widths still need tablet acceptance.
See [pen thickness](docs/pen-thickness.md).

Version 0.17 replaces the thickness dialog with a compact slider anchored to the
Pen button. The toolbar remains visible; the bar fits beside either orientation
and stays within the writing area near screen edges. Widths between the former
presets are now supported. Input stays paused until all slider contacts lift,
and outside taps cancel without drawing on the page. All 18,474 host assertions
pass; the signed APK builds offline. Slider placement is visually checked in a
rendered illustration; native touch, refresh, and writing still need tablet
acceptance. See [pen thickness](docs/pen-thickness.md).

See [milestone design and acceptance](docs/notebook-milestone.md), the
[device test record](docs/device-testing.md), and the retained
[Ink Probe guide](docs/ink-probe.md).
