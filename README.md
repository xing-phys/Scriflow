# Scribblow

<img src="assets/branding/scribblow-icon.png" alt="Scribblow app icon" width="112" height="112">

**Handwriting for thinking.**

Scribblow combines *scribble* and *flow*: a quiet place to draft, calculate,
work through a derivation, and follow an idea. It is an early-stage notebook
app for the **Huawei MatePad Paper**.

## Compatibility and status

Development and physical testing target **MatePad Paper HMW-W09**, Android
10/API 29, firmware **HMW-W09 2.1.0.222(C00E170R1P8)**. Handwriting and pressure
rendering use Huawei APIs already present on that firmware; no proprietary
Huawei code is bundled. Other tablets and firmware versions are unverified.

The current build is **0.28.0-beta.1**, prepared for public beta testing. The
core notebook features are implemented; broader use and final tablet checks
are still needed. Source is available at
[xing-phys/Scribblow](https://github.com/xing-phys/Scribblow). No public APK has
been published yet. The
[changelog](CHANGELOG.md) summarizes changes;
[device records](docs/device-testing.md) distinguish automated checks from
physical testing. Each release APK still needs its own tablet acceptance check.

## Features

- Native handwriting with adjustable pen thickness and pressure-preserving ink.
- Multiple named notebooks, blank/lined pages, continuous finger scrolling,
  thumbnail overview, and direct page jumps.
- Adjustable whole-stroke eraser with a temporary grey trail, plus undo/redo.
- Automatic saving of completed strokes and editable restoration after reopening.
- Vector PDF export for sharing and editable `.inkbook` backup/restore.
- Movable, rotatable monochrome tools and a quiet time/battery/Wi-Fi bar.

Notes stay on the tablet. Scribblow has no account, analytics, or cloud sync and
makes no network requests. See [privacy and local storage](docs/privacy.md).

## Install and update

Accepted beta APKs will be published on
[GitHub Releases](https://github.com/xing-phys/Scribblow/releases).
Once an APK is published, copy it to the supported tablet, open it with the
tablet's file manager, and allow installation from that source when prompted.
Open **Scribblow** from the launcher. Review the app version and signing
certificate supplied with a release before installing it.

**Back up your notes before an update or a change of signing identity.** In the
library, choose **Backup** beside each notebook and save its `.inkbook` file
outside the app's private storage. Try **Restore backup** to confirm it opens
as a separate notebook. PDF export is a reading copy, not an editable backup.

An ordinary update must keep the same application ID and signing certificate.
The application ID remains `org.eink.inkprobe` to preserve identity through the
rename. If Android rejects an update because its signature differs, stop and
follow the [signing migration instructions](docs/releasing.md#moving-from-a-development-key).
**Uninstalling the app or clearing app storage deletes its private notes.**
Older APKs cannot read pages upgraded to the current version-2 format.

## Start writing

Open **My Notebook** or choose **+ Notebook**. Write with the stylus and drag
vertically with one finger to scroll. The paper icon switches blank/lined paper;
the page-plus icon appends a page. The grid opens page thumbnails.

Tap an already-selected Pen or Eraser icon to adjust its width. Drag the
six-dot grip to move the tools, or use the direction icon to rotate them.
The three-dot menu contains PDF export, save status, and **Reset tools**.
If saving fails, writing pauses and **Not saved · Retry** appears.

See the [user guide](docs/user-guide.md) for controls, saving, backups, and
troubleshooting.

## Current limits

- Erasing removes entire touched strokes. Undo/redo keeps up to 100 actions per
  page while the app process remains alive; it does not survive a process restart.
- There is no page deletion/reordering, PDF import, handwriting recognition,
  typed text, or cloud sync.
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
[issue forms](https://github.com/xing-phys/Scribblow/issues/new/choose).

## License

Scribblow's source, documentation, and original artwork are licensed under the
[MIT License](LICENSE). Third-party build tooling retains its own licenses;
see [third-party notices](THIRD_PARTY_NOTICES.md). APKs include a copy of the
MIT license notice.
