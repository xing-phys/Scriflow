# Scriflow

<img src="assets/branding/scriflow-icon.png" alt="Scriflow app icon" width="112" height="112">

**Handwriting for thinking.**

Scriflow brings together *scribe*, *script*, and *scribble* with *flow*: a quiet
place to draft, calculate, work through a derivation, and follow an idea. It is
a notebook app for the **Huawei MatePad Paper**, formerly named
**Scribblow**.

## Compatibility and status

Development and physical testing target **MatePad Paper HMW-W09**, Android
10/API 29, firmware **HMW-W09 2.1.0.222(C00E170R1P8)**. Handwriting and pressure
rendering use Huawei APIs already present on that firmware; no proprietary
Huawei code is bundled. Other tablets and firmware versions are unverified.

The latest stable release is **[Scriflow 0.33.0](https://github.com/xing-phys/Scriflow/releases/tag/v0.33.0)**,
released on 2026-10-28. It adds figures and lasso export, two split panes plus
multiple floating notes, a toolbox notes tray, and stylus-tap editing focus.
Inactive notes scroll continuously and pinch with fingers, while their vector
ink and PDF tiles render at the current zoom. Window changes retain the main
editor. The toolbox stays above note/status/selection panels; PDF import opens
the system picker directly and opens the imported note on success.

Double tapping the **M-Pencil body** switches Pen/Eraser and briefly shows the
selected icon at the top center. The owner accepted the physical switches and
icons. Connected-tablet checks cover landscape PDF import, multiple floating
notes, lasso panels, native rendering, sleep/wake and Home/return. The small-pane
PDF cache fixture uses 64% fewer pixels and releases hidden native readers; see
the [MatePad audit](docs/matepad-audit-0.33.md) for measurements and limits.
Other hardware/firmware, overnight battery drain, cover-sensor behavior and
optical latency remain unverified.

The application ID and permanent signing certificate are retained for in-place
updates. **Page format 6 is written on edit and cannot be opened by older APKs.**
Keep editable backups before updating. See [figures and workspace](docs/figures-and-workspace.md),
[changelog](CHANGELOG.md), and [device records](docs/device-testing.md).

Local optimization work continues in **0.33.1-dev.2**, adding indexed
scrolling geometry and bounded exact ink-outline reuse. See the [optimization record](docs/optimization-0.33.1.md)
for measurements and device validation; the stable download above is unchanged.

## Features

- Native handwriting with adjustable pen thickness, page/screen width modes,
  and pressure-preserving ink.
- Multiple named notebooks, folders and recoverable Trash, compact list/thumbnail
  bookshelf layouts, blank/lined pages, continuous finger scrolling,
  thumbnail overview, and direct page jumps.
- A5 by default for new notes, standard paper sizes and custom dimensions,
  with matching physical PDF page sizes.
- Two-finger zoom, a percentage indicator, and fit width/height/page choices.
- Portrait, landscape, or automatic sensor rotation from Settings.
- Adjustable whole-stroke eraser with a temporary grey trail, plus undo/redo.
- Native dashed lasso to select whole strokes on any overlap, move them across
  pages, duplicate, copy/cut/paste, or delete them, with nearby actions and
  finger/stylus dragging.
- Manage pages from the overview: reorder, duplicate, insert blank pages, delete,
  and undo/redo page changes.
- Insert images, then move, resize or delete them with undo; include figures in
  thumbnails, PDF exports and editable backups.
- Export selected ink as SVG, PNG, JPEG, WebP or PDF.
- Keep up to two split notes and several floating notes open with one shared toolbox.
  Tap a note with the stylus to edit it; finger-scroll or pinch any note, and
  minimize/restore windows through the toolbox notes tray.
- Automatic saving of completed strokes and editable restoration after reopening.
- Vector PDF export for sharing and editable `.inkbook` backup/restore.
- Import PDFs as notebooks, write over their original pages, follow links,
  and export with original PDF content plus vector ink.
- Imported and editable note outlines, with previous/next view navigation.
- Compact circular tools in a movable, rotatable, collapsible dock, plus floating
  notebook and time/battery/Wi-Fi islands over a full-screen page.

Notes stay on the tablet. Scriflow has no account, analytics, or cloud sync and
makes no network requests. See [privacy and local storage](docs/privacy.md).

## Install and update

Download the signed **[Scriflow 0.33.0 APK](https://github.com/xing-phys/Scriflow/releases/download/v0.33.0/scriflow-0.33.0.apk)**
from [GitHub Releases](https://github.com/xing-phys/Scriflow/releases/tag/v0.33.0),
where its file checksum and signing certificate fingerprint are recorded.
Copy it to the supported tablet, open it with the tablet's file manager, and
allow installation from that source when prompted.
Open **Scriflow** from the launcher. Review the app version and signing
certificate supplied with a release before installing it.

**Back up your notes before an update or a change of signing identity.** In the
library, choose **Backup** beside each notebook and save its `.inkbook` file
outside the app's private storage. Try **Restore backup** to confirm it opens
as a separate notebook. PDF export is a reading copy, not an editable backup.

An ordinary update must keep the same application ID and signing certificate.
The application ID remains `org.eink.inkprobe`, and the existing public signing
key is retained, so the Scriflow rename can update the released Scribblow app
in place without moving notebooks. If Android rejects an update because its
signature differs, stop and follow the [signing migration instructions](docs/releasing.md#moving-from-a-development-key).
**Uninstalling the app or clearing app storage deletes its private notes.**
Version 0.32 writes version-2 library catalogs with folder/trash metadata on the
next catalog change. Earlier APKs cannot read an upgraded library; preserve
editable backups before updating. Page and backup formats are unchanged.
Version 0.31-dev.2 writes version-5 pages retaining the exact native width and
capture scale of new strokes; older APKs cannot read these pages. Existing page
versions 1–4 remain readable and are not rewritten on open. Old strokes' capture
scale was never recorded and cannot be recovered reliably. Version-2 editable
backups include PDF assets and outlines. Version 0.30 added physical paper dimensions. Opening an existing page
keeps its stored coordinates and previous PDF printing scale.
Version 0.29 introduces a version-2 page-order index
when page management is first used; earlier APKs cannot open that managed book.

## Start writing

Open **My Notebook** or choose **+ Notebook**, select a paper size, and create
the note. New notes default to A5 and open fitted to the screen width. Pinch
with two fingers to zoom. Write with the stylus and drag with one finger to
scroll. The paper icon switches blank/lined paper;
the page-plus icon inserts a page after the current page. The grid opens page thumbnails.

Tap an already-selected Pen or Eraser icon to adjust its width. Drag the
six-dot grip to move the tools, tap it to collapse/expand them, or use the
direction icon to rotate them.
Use **Import PDF** in the library to make a PDF notebook. The outline icon
opens imported headings and lets you add your own; finger taps follow PDF links.
Previous/next view icons return after link or outline jumps; ordinary scrolling,
zoom, and page buttons do not create history. Both stacks survive screen rotation.
Settings offers **Fixed width on page** (default), using native width fitting,
and **Fixed width on screen**, keeping the nib's screen size while writing.
Both use direct native AHW. Page mode's native pressure response can vary with
zoom; identical pressure behavior across zoom is unresolved. Completed ink
retains its captured geometry in both modes.
External links ask before opening
another app. See [PDF notebooks](docs/pdf-import.md).
The vertical three-dot menu beside the top-left percentage contains image insertion,
figure editing, and **Page information / Export PDF**, which opens save status,
PDF export, and **Reset tools**. The toolbox notes button opens and restores
note windows. Tap the time/battery/Wi-Fi area to open
**Settings** on any app screen, or use **App settings** in the compact zoom menu.
Settings includes separate portrait/landscape bookshelf rows/columns and page
overview columns, plus **Keep screen on** while the app is visible. Choose portrait, landscape, or
sensor rotation there.
If saving fails, writing pauses and **Not saved · Retry** appears.

See the [user guide](docs/user-guide.md) for controls, saving, backups, and
troubleshooting.

## Current limits

- Erasing removes entire touched strokes. Undo/redo keeps up to 100 actions per
  page while the app process remains alive; it does not survive a process restart.
- There is no handwriting recognition, typed text, or cloud sync.
- PDF import supports unlocked PDFs up to 256 MiB and 1,000 pages. Landscape USB import/open checks include a 600-page, 9 MB synthetic PDF;
  arbitrary complex PDFs and extended sessions remain unverified.
- Page management keeps at least one page in a notebook. Its separate undo/redo
  history holds 50 page changes until the process restarts. Removed page files
  remain in private storage for recovery; current exports/backups exclude them.
- Very fast passes with the maximum eraser width can lag. Finger scrolling
  has no inertia or page snapping. Use the tablet's **Refresh now** action
  when residual images need clearing.
- Limits are 200 notebooks, 1,000 pages per notebook, and 512 MiB uncompressed
  per editable backup. These are format/validation limits, not tested practical
  capacity: large-notebook memory and long-duration performance remain unmeasured.
- `.inkbook` backups are unencrypted. Keep independent copies somewhere safe.

## Build and contribute

On Linux x86_64 with a JDK, Python 3.8+, curl, and unzip installed:

```bash
./scripts/bootstrap-gradle.sh
JAVA_HOME="$PWD/.tools/jdk-17.0.21+9" ./gradlew hostTest :app:lint :app:assembleDebug :app:assembleRelease
```

Contributor checks need no private release key. Without release signing
credentials, `assembleRelease` produces an unsigned APK. Do not distribute it
as an installer. The standalone development route remains available:

```bash
./scripts/bootstrap.sh
./scripts/test.sh
./scripts/build.sh
```

See [Building](docs/building.md), [Contributing](CONTRIBUTING.md), and the
[release checklist](docs/releasing.md). The [architecture](docs/architecture.md),
[development history](docs/development-history.md), and
[name and artwork](docs/branding.md) explain the implementation and its history.
Report bugs and suggest improvements through the
[issue forms](https://github.com/xing-phys/Scriflow/issues/new/choose).

## License

Scriflow's source, documentation, and original artwork are licensed under the
[MIT License](LICENSE). Third-party build tooling retains its own licenses;
see [third-party notices](THIRD_PARTY_NOTICES.md). APKs include a copy of the
MIT license notice.
