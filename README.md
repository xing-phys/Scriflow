# Ink Notebook

A small notebook app for Huawei MatePad Paper HMW-W09. The first milestone is
one fixed page with blank or lined paper, native low-latency handwriting,
automatic saving of completed strokes, and editable restoration after reopening.

The current build is an early development APK, version 0.8, with undo/redo and
a whole-stroke eraser. Notebook/page navigation, pen selection, and export are
later milestones. Handwriting currently uses one black ballpoint pen. The
original diagnostic screen remains available separately for development.

## Use

Open **Ink Notebook**, write on Page 1, then pause briefly. **Saved · N strokes**
confirms how many completed strokes have been saved; an empty page shows
**Autosave on**. There is no manual save button. The **Blank/Lined** button changes
the paper without clearing writing. The menu shows the page's save state.

Choose **Eraser**, move the stylus across writing, then lift it to remove every
touched stroke. There is no live eraser preview; a gesture removes entire strokes,
not small pieces of a stroke. Choose **Pen** to resume smooth native writing.
**Undo/Redo** remember up to 100 writing or erase actions within the current app
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
Version 0.8 reads the unchanged version-1 notebook format from 0.7. Install it as
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
adb -d shell am start -W -n org.eink.inkprobe/.notebook.NotebookActivity
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

Version 0.8 was developed offline: its build, signature, and host behavior are
verified, but its new eraser input path still needs physical tablet acceptance.
See [editing milestone and installation checks](docs/notebook-editing.md).

See [milestone design and acceptance](docs/notebook-milestone.md), the
[device test record](docs/device-testing.md), and the retained
[Ink Probe guide](docs/ink-probe.md).
