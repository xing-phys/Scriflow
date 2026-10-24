# Ink Notebook

A small notebook app for Huawei MatePad Paper HMW-W09, with blank/lined pages,
native low-latency handwriting, automatic saving of completed strokes, and
editable restoration after reopening.

The current build is an early development APK, version 0.11, with a notebook
library, continuously scrolling pages, undo/redo, and a whole-stroke eraser. Pen selection and
export are later milestones. Handwriting uses one black ballpoint pen. The
original diagnostic screen remains available separately for development.

## Use

Open **Ink Notebook** to see your library. Tap **My Notebook** to open your
existing pages, or **+ Notebook** to create and open a named notebook. **Rename**
changes a notebook's title without moving its files. Each notebook remembers its
own last page; the editor's **Books** button returns to the library. Names may
contain up to 80 Unicode characters. This milestone supports up to 200 notebooks.

Write on a page, then pause briefly. **Saved · N strokes**
confirms how many completed strokes have been saved; an empty page shows
**Autosave on**. There is no manual save button. The **Blank/Lined** button changes
the current page's paper without clearing writing. Tap it to toggle between
blank and lined paper. The menu shows the page's save state.

**Drag vertically with one finger** to scroll, then lift it to stop at any
position, including between pages. **Write with the stylus** on either visible
page. There is no snapping or inertial fling. **‹ / ›** still jump to page tops,
and **+ Page** appends an empty page at the end. New pages inherit the active
page's paper. Each page keeps its own writing and paper choice. The indicator
shows **Page X of Y** for the most recently written page, or the page nearest
the viewport center after scrolling. Eraser, Undo/Redo, and paper choice use
that page context. The fractional scroll position is saved per notebook.
Your existing single-page notebook becomes Page 1 without moving its file.
There is a limit of 1,000 pages; deletion and reordering are not yet included.

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
is static. Save-status changes wait until the pen is idle; there is no live
counter or periodic toolbar refresh during handwriting.

Completed strokes survive app force-stop once **Saved** is shown. An unfinished
stroke is discarded when leaving the app. Notes are stored on this tablet in
the app's private directory; uninstalling the app removes that data. Keep the
local signing key when installing updates over an existing version.
Version 0.11 reads the unchanged version-1 page format from earlier releases. Install it as
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
ignored local key and requests no permissions or network access. No proprietary
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

See [milestone design and acceptance](docs/notebook-milestone.md), the
[device test record](docs/device-testing.md), and the retained
[Ink Probe guide](docs/ink-probe.md).
