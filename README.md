# Scriflow

<img src="assets/branding/scriflow-icon.png" alt="Scriflow app icon" width="112" height="112">

**Handwriting for thinking.**

Scriflow brings together *scribe*, *script*, and *scribble* with *flow*: a quiet
place to draft, calculate, work through a derivation, and follow an idea. It is
an early-stage notebook app for the **Huawei MatePad Paper**, formerly named
**Scribblow**.

## Compatibility and status

Development and physical testing target **MatePad Paper HMW-W09**, Android
10/API 29, firmware **HMW-W09 2.1.0.222(C00E170R1P8)**. Handwriting and pressure
rendering use Huawei APIs already present on that firmware; no proprietary
Huawei code is bundled. Other tablets and firmware versions are unverified.

The latest public prerelease is **[Scriflow 0.30.0-dev.2](https://github.com/xing-phys/Scriflow/releases/tag/v0.30.0-dev.2)**,
released on 2026-10-26 with the owner's explicit authorization. It adds faint
page boundaries, stronger palm and pinch safeguards, and direct sensor-driven
rotation, retaining the same application ID and public signing certificate for
in-place updates. Automated checks pass; tablet validation of these changes
is pending. The core notebook features are implemented; broader use and
long-session performance still need feedback.
The [changelog](CHANGELOG.md) summarizes changes; [device records](docs/device-testing.md)
distinguish automated checks, publication authorization, and physical testing.

## Features

- Native handwriting with adjustable pen thickness and pressure-preserving ink.
- Multiple named notebooks, blank/lined pages, continuous finger scrolling,
  thumbnail overview, and direct page jumps.
- A5 by default for new notes, standard paper sizes and custom dimensions,
  with matching physical PDF page sizes.
- Two-finger zoom, a percentage indicator, and fit width/height/page choices.
- Portrait, landscape, or automatic sensor rotation from Settings.
- Adjustable whole-stroke eraser with a temporary grey trail, plus undo/redo.
- Native dashed lasso to select whole strokes, move, duplicate, copy/cut/paste,
  or delete them, with a closed region, nearby actions, and finger/stylus dragging.
- Manage pages from the overview: reorder, duplicate, insert blank pages, delete,
  and undo/redo page changes.
- Automatic saving of completed strokes and editable restoration after reopening.
- Vector PDF export for sharing and editable `.inkbook` backup/restore.
- Compact circular tools in a movable, rotatable, collapsible dock, plus floating
  notebook and time/battery/Wi-Fi islands over a full-screen page.

Notes stay on the tablet. Scriflow has no account, analytics, or cloud sync and
makes no network requests. See [privacy and local storage](docs/privacy.md).

## Install and update

Download the latest public **[Scriflow 0.30.0-dev.2 APK](https://github.com/xing-phys/Scriflow/releases/download/v0.30.0-dev.2/scriflow-0.30.0-dev.2.apk)**
from [GitHub Releases](https://github.com/xing-phys/Scriflow/releases/tag/v0.30.0-dev.2),
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
Version 0.30 writes version-3 pages with optional physical paper dimensions;
older APKs cannot read pages saved by this version. Opening an existing page
keeps its stored coordinates and previous PDF printing scale.
Version 0.29 introduces a version-2 page-order index
when page management is first used; earlier APKs cannot open that managed book.

## Start writing

Open **My Notebook** or choose **+ Notebook**, select a paper size, and create
the note. New notes default to A5 and open fitted to the screen width. Pinch
with two fingers to zoom. Write with the stylus and drag with one finger to
scroll. The paper icon switches blank/lined paper;
the page-plus icon appends a page. The grid opens page thumbnails.

Tap an already-selected Pen or Eraser icon to adjust its width. Drag the
six-dot grip to move the tools, tap it to collapse/expand them, or use the
direction icon to rotate them.
The three-dot menu contains PDF export, save status, and **Reset tools**.
Open **Settings** in the library, or tap the notebook title/percentage in the
editor for zoom controls and **App settings**. Choose portrait, landscape, or
sensor rotation there.
If saving fails, writing pauses and **Not saved · Retry** appears.

See the [user guide](docs/user-guide.md) for controls, saving, backups, and
troubleshooting.

## Current limits

- Erasing removes entire touched strokes. Undo/redo keeps up to 100 actions per
  page while the app process remains alive; it does not survive a process restart.
- There is no PDF import, handwriting recognition,
  typed text, or cloud sync.
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
