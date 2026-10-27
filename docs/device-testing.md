# Device test record

The app is now named **Scriflow** (formerly **Scribblow**); see
[branding](branding.md). Historical app names, log tags, and APK filenames below
describe the original experiments.

## Scriflow 0.32.0 first stable release (2026-10-27)

The owner requests the first formal GitHub release after testing the local
development candidates on the connected MatePad Paper. VersionCode 46 promotes
the functionality of dev.4 without further app-behavior or data-format changes.
The permanent application ID and signing certificate are retained.

- Full host regressions, debug/release lint and both contributor builds pass.
  Debug and unsigned release APK verification passes. The signed release build
  is verified against the pinned permanent certificate.
- Publish `v0.32.0` as a normal GitHub release marked Latest, with
  `scriflow-0.32.0.apk`, its SHA-256 checksum and compatibility/update notes.
- Prior development APKs were installed and tested by the owner; this release
  authorization is not a record that every physical acceptance checkbox passed.
  Cover-lock/battery behavior, sustained use, maximum practical notebook size
  and other hardware/firmware remain unverified.

## Scriflow 0.32.0-dev.4 lasso overlap and navigation delay (2026-10-27)

VersionCode 45 selects whole strokes on any centreline contact with the closed
lasso region and reduces the finger-navigation quiet period to 200 ms. Active
pen contact/hover continues to block finger gestures.

- Full host suite passes, including 76 lasso assertions for partial overlap,
  sparse crossings, boundary/vertex touches, concave cutouts, outside misses
  and whole-stroke identity. The 153 input-policy assertions check rejection
  at 199 ms and recovery at 200 ms, with ongoing pen protection retained.
- Release lint/build and signed APK metadata/certificate verification pass.
  Artifact: `build/releases/scriflow-0.32.0-dev.4.apk` with its adjacent checksum.
- In-place ADB installation succeeds on the connected MatePad Paper HMW-W09.
  Package inspection confirms dev.4/versionCode 45; bookshelf launch succeeds.
  Hands-on lasso selection and finger timing acceptance remain with the owner.

## Scriflow 0.32.0-dev.3 lasso-restoration fix (2026-10-27)

The owner reports a stuck editor after moving a lasso across pages. ADB capture
shows the editor active with its lasso outline, no save-error banner, and idle
main/writer threads. Inspection identifies a missing restoration-pending state:
finishEdit starts a page read without setting scrolling, so the read callback
can return without installing the viewport or resuming native input.

Set restoration pending before installing the edited viewport. The targeted
regression executes the production completion/read callbacks under queued reads
and covers cross-page, same-page, stale/in-flight reads, PDF waits, background
activities and save failures. VersionCode 44 keeps the app/signing identity and
existing data formats. Hands-on repeat of the original gesture remains pending.

## Scriflow 0.32.0-dev.2 bugfix candidate (2026-10-27)

VersionCode 43 retains the permanent app ID, signing identity and page/catalog
formats. The connected device is MatePad Paper HMW-W09.

- Full host suite passes, including 4,076 cross-page placement/edge-scroll
  assertions, 85 real-file transfer/history/recovery assertions, and 627 slider
  assertions. Debug QA build and lint pass.
- A synthetic native brush check on the tablet confirms all 40 measurements:
  SIMPLE renders the configured widths 4/9/36/120/240 across four pressures;
  the former pressure-sensitive ballpoint rendered a width-36 trail as 18–27
  pixels at light/medium pressure. The isolated native eraser self-test confirms
  the running AHW brush is SIMPLE at width 36, with temporary ink unsaved and
  surface/paper identity retained.
- Original folder failure was observed. Updated Settings steppers were inspected
  through direct ADB UI capture. The custom UiAutomation launch harness was not
  reliable on this firmware and did not complete; its checks are not passed.
- Owner will perform hands-on folder/Trash, slider, lasso closure, cross-page
  movement/edge-scroll and paired-undo checks. Physical pen latency and display
  appearance have not been established by the synthetic checks.

## Scriflow 0.32.0-dev.1 compact bookshelf and management (2026-10-27)

The requested update covers dense bookshelf/settings layouts, landscape list
columns, independently configurable portrait/landscape grids, flat dialog
borders, a top-left vertical menu, status-area Settings access on every main
screen, folders and Trash, current-page insertion, dock order and Keep screen on.

- Version 42 keeps the application ID and permanent signing identity. Catalog
  format 2 adds folders/trash; version 1 remains readable. Page/backup formats
  and the live native handwriting implementation are unchanged.
- Real-file checks cover folder/trash persistence, restore, legacy-root and
  nested-slot purge isolation, interrupted deletion/restart, reclaimed capacity,
  backup recovery into reused slots, and read-only first-page previews.
- Add-page regression checks cover legacy/managed middle insertion, selection,
  dimensions, template, existing page bytes/identities, undo/redo and restart.
- Android compilation/lint and host checks are automated validation. Keep screen
  on uses only the resumed window flag; no wake lock or power-settings changes.
- Completed: full host suite (including 54 new bookshelf safety assertions and
  eight new page-insertion assertions), release lint/build, and signed APK
  package/API/permission/component/certificate verification. Artifact:
  `build/releases/scriflow-0.32.0-dev.1.apk` with an adjacent `.apk.sha256` file.

No ADB device is connected. Physical checks remain pending: both orientations,
thumbnail density/custom grids, flat dialogs, continued native pen performance,
foreground timeout prevention, cover locking, Power-button sleep, app-background
timeout and overnight battery use. The owner explicitly authorized commit, push
and development-prerelease publication after the automated checks. This is
publication authorization, not tablet acceptance. See [bookshelf settings](bookshelf-settings.md).

## Scriflow 0.31.0-dev.5 direct handwriting restoration (2026-10-27)

The owner confirms dev.4 retains view history through rotation, but reports
unbearable writing delay. The manual page-mode bridge was not an acceptable
replacement for the direct native AHW implementation optimized before 0.31.
Its host contract checks did not establish writing latency or physical pen feel.

- Artifact: `scriflow-0.31.0-dev.5.apk`, Android `versionCode` 41. Same app ID,
  release key and version-5 storage; existing notes and PDFs are preserved.
- `NotebookInk.point`, `nativeCommit`, `drainCommits`, `flushCompletedUps` and
  `attach` are compared verbatim with `f40ef50` (`0.30.0-dev.3`). Live pen
  configuration again selects black native ink, with the original pen type,
  accepted tools, down-event handling, AHW enablement and width-fitting rule.
- Remove `NotebookPagePen`, Java point feeding, per-sample matrix/framebuffer
  posts, and the completed-stroke bitmap update from callbacks. Completed-ink
  geometry work remains in paused restoration, previews and export.
- Both width settings use direct AHW. Page width stays the default, using the
  original native fitting; identical pressure behavior across zoom is unresolved
  and is no longer promised. Stored capture parameters remain authoritative.
- Keep the rotation-history fix and PDF features. Host tests cover direct width
  fitting, raw pressure/time, captured geometry, durability, export and history.
- Verification completed: full host regression suite, debug/release Android lint
  and builds, signed APK metadata/certificate checks, and API-29 diagnostic
  compilation. Both APKs were inspected to confirm the removed second-pen class
  is absent.

No tablet is connected. Source equivalence and build checks verify removal of
the regressed live path, not a physical latency measurement. Physical writing
acceptance on the supported MatePad remains pending.

## Scriflow 0.31.0-dev.4 rotation and pen modes (2026-10-27)

Later feedback rejects its writing latency; dev.5 removes the manual bridge.
The following records describe the checks made before that device feedback.

The owner confirms dev.3 solved the clipping failure. They subsequently report
that rotation loses previous/next view history, and that the pen's pressure
response and width change after zoom. They explicitly request both pen width
modes in Settings, with fixed width on the page as the default.

- Artifact: `scriflow-0.31.0-dev.4.apk`, Android `versionCode` 40. The permanent
  application ID, signing certificate and version-5 page format are retained.
- Both navigation stacks now survive Activity recreation with notebook-scoped,
  bounded state. Page UUIDs and logical anchors survive layout changes. Fitted
  history views adapt to the new orientation; custom percentages remain exact.
- Page mode keeps the installed native brush at the selected page-space width
  and applies zoom to its finished framebuffer drawing. Screen mode keeps
  direct native handwriting with a constant native-pixel nib. Raw pressure and
  timestamps remain unchanged in both. Existing strokes retain captured geometry.
- Cached firmware interfaces/disassembly support the new manual native page
  brush: native input is not inverse-transformed by the surface matrix; global
  exclusion clips precede that matrix; explicit physical dirty posting is
  required; native track completion updates bookkeeping without a full redraw.
- Host contracts execute production bridge methods with instrumented firmware
  fixtures. They cover both width modes, four-pixel pressure threshold input,
  zoom/orientation/PDF geometry, full timestamp precision, save/reopen/export,
  surface-state restoration and dirty bounds, cancellation and invalid input.
  These are not Huawei brush or live display emulation.
- Full host suite: **168,181 assertions across 43 suites**, including 364 view
  history checks, 53,265 pen policy/storage checks and 817 native bridge contract
  checks. Debug/release lint and builds, signed artifact verification, and
  native diagnostic Java/DEX compilation also pass.

No tablet is connected. Physical verification of the new page-mode native bridge,
pen latency, pressure feel, both rotation directions and export parity remains
pending. Check Settings' default page mode first, then screen mode, using new
strokes at several zooms on ordinary notes and PDFs. Do not downgrade over
version-5 files or clear app storage to apply this update.

## Scriflow 0.31.0-dev.3 handwriting clipping hotfix (2026-10-27)

The owner reported that writing in either ordinary or PDF notes displayed
“Not saved · Retry” and paused interaction. Their error dialog identifies
“Cannot update notebook ink: Could not clip handwriting to its page.” This is
the renderer's failed `Path.op(INTERSECT)` during viewport restoration, rather
than evidence that the page store failed. Dev.3 removes this operation and draws
the complete native outline under `Canvas.clipRect`, scoped with save/finally/
restore. Empty clips remain normal. Rendering errors now display “Writing
paused”; storage failures retain “Not saved.” Page format remains version 5.

- Artifact: `scriflow-0.31.0-dev.3.apk`, Android `versionCode` 39.
- APK SHA-256: recorded with the exact signed release asset.
- Signer certificate SHA-256: `2cc5db316df82fecc6085ff53e67cbd64f29b412f01e9a5c19bf4b34e3821709`.
- Release: [`v0.31.0-dev.3`](https://github.com/xing-phys/Scriflow/releases/tag/v0.31.0-dev.3).
- Host renderer tests execute the production renderer with Java2D graphics and
  firmware contract fixtures. They force boolean path operations to fail, compare
  clipped pixels, verify empty clips and restored Canvas state even after draw
  errors, and retain full capture geometry. These fixtures do not verify the
  Huawei brush or Android rasterizer.
- Save/reopen integration checks run the production viewport split through the
  library/book/store with real files, covering version-4 migration, new notes,
  staged PDF notes, six zoom levels, page borders/gaps, repeated appends, exact
  samples/style/capture fields, preserved migration backups and source PDFs.
- The native renderer diagnostic now compares composed viewport clips with
  Canvas reference pixels, including loops, page edges, pan/scroll, offscreen
  samples, and empty clips, and verifies drawing state restoration.
- Verification passed: all host suites, debug/release lint and APK builds,
  signed artifact identity/component checks, and native Java/DEX compilation.

No tablet is connected. Native diagnostic compilation is checked; execution and
physical confirmation of writing, scrolling, reopening and export on the supported
MatePad remain pending. The owner's screenshot confirms the original failure;
host fault injection verifies that this error path is removed.

## Scriflow 0.31.0-dev.2 PDF/ink fixes (2026-10-27)

The owner tried dev.1 and reported blurred PDF text after zoom, failed PDF
export, live/replayed ink width differences on both notes and PDFs, unwanted
scroll/page entries in view history, direct external-link launch, and slow
import/open of an approximately 8 MiB, 600-page PDF. These are device feedback,
not measurements or acceptance of the follow-up fixes.

- Artifact: `scriflow-0.31.0-dev.2.apk`, Android `versionCode` 38.
- APK SHA-256: recorded with the exact signed release asset.
- Signer certificate SHA-256: `2cc5db316df82fecc6085ff53e67cbd64f29b412f01e9a5c19bf4b34e3821709`.
- Release: [`v0.31.0-dev.2`](https://github.com/xing-phys/Scriflow/releases/tag/v0.31.0-dev.2).
- Automated verification: **31,012 assertions across 40 suites**; debug/release
  lint and APK builds; standalone app and native diagnostic compilation.
- PDF regression tests now use an actual output file and verify that export
  leaves its stream open for final flush and sync. Source PDF resources,
  malformed/valid links, duplication, outlines, and all rotations remain covered.
- 4,621 tile geometry checks cover rendering at current zoom, visible-region
  bounds, cache keys, and pixel budgets; 18 history checks restrict entries to
  links/outlines. 95 ink geometry checks cover exact width/scale, the native
  four-pixel rounding threshold, full-stroke clipping, copies, persistence,
  backward reads, and bit-exact disk conflict detection.
- 1,216 navigation checks include a 600-page import; 37 parallel staging checks
  cover ordering and joining every writer before failure/interruption cleanup.
  Every page retains a durability sync and the index is published once.
- Host-only observations: durable staging of 600 empty PDF-backed note pages
  took about 0.56–0.63 s with four writers versus about 1.2 s sequentially.
  A separate actual 600-page, >8 MiB vector PDF geometry/outline scan took
  22–24 ms. Cached book metadata opened below the timer's 1 ms resolution with
  warm OS caches. These separate figures exclude provider/UI/native rendering
  costs and are **not MatePad import or opening timings**.

No tablet was connected. Sharp tile rendering, external-link confirmation,
native restored-ink appearance, five-scale pressure/PDF comparisons, transparent
native bookkeeping tracks, pen latency, and actual import/open timing still need
the supported MatePad. Use fresh synthetic strokes at several zooms on a blank
note and PDF, then scroll, erase/undo/redo, reopen, and export both with and
without outlines. Ordinary scrolling/page actions should leave link history alone.

Version 5 retains native capture scale and exact width for new strokes. Older
strokes keep their existing parameters; their unrecorded capture scale cannot
be reconstructed reliably. Files are not rewritten on open. In-app PDF text
selection remains unimplemented; source/export PDF text is retained.

## Scriflow 0.31.0-dev.1 PDF notebooks (2026-10-26)

The owner explicitly requested PDF import with original-preserving vector ink
export, editable/imported outlines, working links, view history, and a committed,
pushed APK release. No tablet is connected. Publication is authorized as a
development prerelease; physical acceptance remains pending.

- Artifact: `scriflow-0.31.0-dev.1.apk`, Android `versionCode` 37.
- SHA-256: recorded in the release checksum asset for the exact signed file.
- Signer certificate SHA-256: `2cc5db316df82fecc6085ff53e67cbd64f29b412f01e9a5c19bf4b34e3821709`.
- Release: [`v0.31.0-dev.1`](https://github.com/xing-phys/Scriflow/releases/tag/v0.31.0-dev.1).
- Automated validation: **25,673 assertions across 35 suites**, including 67
  PDF model/storage checks, 8 link-tap checks, and 46 PDF engine checks executing
  the actual shipped PDFBox Android library on the host with a logging stub.
- Debug/release lint and Gradle APK builds pass. The standalone APK builder and
  manifest/signature checks also pass.
- Source PDF bytes remain unchanged; editable backup round-trips retain those
  exact bytes and outline UUIDs. Original PDF content streams, URI/named/numeric
  links, nested bookmarks, metadata, and attachments survive export. Tests cover
  all four rotations, crop offsets, duplication/reordering, duplicate-before-
  original bookmark identity, missing assets, malformed links, and vector ink.
- Independent Poppler rendering and visual inspection confirm ink positioning
  on synthetic cropped/rotated exports at 0/90/180/270 degrees. A deliberately
  malformed source link produces the same parsing warning in exported fixtures;
  it does not invalidate the other tested links.

Pending tablet checks: system import/restore/save pickers; actual PDF background
restoration while writing, erasing, lassoing, scrolling, zooming and rotating;
internal/external link taps with palm rejection; outline editing and history
buttons; backups of annotated PDFs; large-PDF memory and sustained performance.
Use disposable PDFs and notes. Imported file limit is 256 MiB / 1,000 pages.
Password-protected PDFs require an unlocked copy. Remote-file, embedded-file,
and JavaScript actions are retained in exports but not activated in the editor.

## Scriflow 0.30.0-dev.3 orientation synchronization (2026-10-26)

The owner confirmed dev.2 solved the preceding bugs, then reported that leaving
a landscape note briefly reveals the bookshelf in portrait before it turns.
This is general functional feedback on dev.2, without an itemized acceptance
checklist or latency measurement. Dev.3 synchronizes the accepted direction
across every production screen, including existing background screens.

- Artifact: `scriflow-0.30.0-dev.3.apk`, Android `versionCode` 36.
- SHA-256: recorded in the release checksum asset for the exact signed file.
- Signer certificate SHA-256: `2cc5db316df82fecc6085ff53e67cbd64f29b412f01e9a5c19bf4b34e3821709`.
- Release: [`v0.30.0-dev.3`](https://github.com/xing-phys/Scriflow/releases/tag/v0.30.0-dev.3).
- Automated validation: 25,552 host assertions, including 46 shared-direction
  regressions and 388 sensor/calibration checks; debug/release lint and builds;
  packaged manifest checks for inherited orientation on all five screens.

No tablet is connected for this update. The exact transition still needs
on-device confirmation. Follow the dev.3 acceptance steps in the
[feature record](paper-zoom-orientation.md). Existing sensor settling, pen
deferral, palm rejection, paper geometry, and file formats are retained.

## Scriflow 0.30.0-dev.2 follow-up (2026-10-26)

The owner reports that dev.1 two-finger zoom works, but resting-hand contact
can trigger unwanted zoom, white page edges are hard to locate, and sensor
rotation does not respond. These are user observations, not measured input
latency or a confirmed permission failure. Dev.2 adds faint page outlines,
direct orientation sensor listening, and layered palm/pinch safeguards.

- Artifact: `scriflow-0.30.0-dev.2.apk`, Android `versionCode` 35.
- SHA-256: recorded in the release checksum asset for the exact signed file.
- Signer certificate SHA-256: `2cc5db316df82fecc6085ff53e67cbd64f29b412f01e9a5c19bf4b34e3821709`.
- Release: [`v0.30.0-dev.2`](https://github.com/xing-phys/Scriflow/releases/tag/v0.30.0-dev.2).
- Automated validation: 25,498 host assertions, including 152 palm/pinch checks
  and 380 sensor-sector/pen-deferral checks; Android lint and both build variants.

No tablet is connected for this implementation. Actual hover/contact-size
delivery, orientation readings, and native landscape behavior remain pending
confirmation. Repeat the 0.30 checklist with particular attention to resting
the palm between strokes, pen-before-palm and palm-before-pen ordering, lifting
the pen away before intentional pinch, disabling pinch in Settings, tilt in
all four directions, flat-position stability, and rotating immediately after
pen lift. Page format and PDF dimensions are unchanged from dev.1.

## Scriflow 0.30.0-dev.1 authorized prerelease (2026-10-26)

The owner explicitly requested implementing paper sizes, zoom, and app-wide
orientation settings, then committing, pushing, and publishing an APK. No
tablet is connected. Publication authorization does not establish physical
acceptance; pinch touch routing, landscape native handwriting, sensor rotation,
and visible e-ink behavior remain pending. See the
[feature acceptance checklist](paper-zoom-orientation.md).

- Artifact: `scriflow-0.30.0-dev.1.apk`, Android `versionCode` 34.
- APK SHA-256: recorded in the release's `scriflow-0.30.0-dev.1.apk.sha256`
  asset and release notes for the exact published file.
- Signer certificate SHA-256: `2cc5db316df82fecc6085ff53e67cbd64f29b412f01e9a5c19bf4b34e3821709`.
- Release tag: [`v0.30.0-dev.1`](https://github.com/xing-phys/Scriflow/releases/tag/v0.30.0-dev.1).
- Automated checks: 24,966 host assertions, including 118 physical-paper
  checks and 2,501 zoom/pan/rotation-anchor checks; debug/release lint and
  builds; packaged version, permissions, license, component exposure, and APK
  signing verification.
- Independent Poppler parsing and rendered inspection of a five-page production
  PDF fixture confirm A5, A4, ISO B5, Letter, and custom landscape dimensions
  within 0.001 point. This uses synthetic host outlines, not tablet handwriting.

Physical sizes are stored in version-3 snapshots and preserved in editable
backups. Legacy notes retain their coordinates and PDF scale. Older APKs
cannot read pages saved in version 3. The application ID and permanent public
signing identity are unchanged, allowing in-place updates.

## Scriflow 0.29.0-dev.3 authorized prerelease (2026-10-26)

The owner explicitly requested committing and releasing the circular-tool,
collapsible-dock, and floating-status-island update after being informed that
no tablet was connected. This is publication authorization, not physical tablet
acceptance. Native ink exclusion around multiple islands, tap/drag behavior on
the device, visible e-ink transitions, and writing in the former header area
remain pending checks. Earlier acceptance does not establish these results.

- Artifact: `scriflow-0.29.0-dev.3.apk`, Android `versionCode` 33.
- APK SHA-256: recorded in the release's `scriflow-0.29.0-dev.3.apk.sha256`
  asset and release notes for the exact published file.
- Signer certificate SHA-256: `2cc5db316df82fecc6085ff53e67cbd64f29b412f01e9a5c19bf4b34e3821709`.
- Release tag: [`v0.29.0-dev.3`](https://github.com/xing-phys/Scriflow/releases/tag/v0.29.0-dev.3).
- Automated checks: 22,347 host assertions, including grip tap/drag intent and
  expanded/collapsed dock sizing; debug/release lint and builds; manifest,
  bundled license, signature/certificate, and staged APK checksum verification.

The existing application ID, permanent public signing identity, notebook
storage, and file formats are preserved. The release is marked as a prerelease
with physical validation pending.

## Scriflow 0.29.0-dev.2 acceptance and release (2026-10-26)

After testing the updated signed APK, the owner confirmed that it works well
and explicitly authorized committing and releasing it. This is general tablet
acceptance of the lasso follow-up below, without an item-by-item checklist,
measured latency, or a long-session/maximum-capacity benchmark. The target
remains MatePad Paper HMW-W09, firmware `HMW-W09 2.1.0.222(C00E170R1P8)`,
Android 10/API 29.

- Artifact: `scriflow-0.29.0-dev.2.apk`, Android `versionCode` 32.
- APK SHA-256: `3c9616e296e8c33d192dc94bab0175feaeff20318707788ef966e545857e0a00`.
- Signer certificate SHA-256: `2cc5db316df82fecc6085ff53e67cbd64f29b412f01e9a5c19bf4b34e3821709`.
- Release tag: `v0.29.0-dev.2`.
- Automated checks: 22,326 host assertions, debug/release builds and lint,
  release manifest/license/signature verification, and exact staged APK checksum.

The public prerelease uses the accepted staged file without rebuilding it.

## Development 0.29.0-dev.1 feedback and dev.2 follow-up (2026-10-26)

The owner tested the signed dev.1 APK and reported that page management works
well and native lasso dashes draw smoothly. After lifting the stylus, however,
no usable selection/actions appeared. This is user-reported physical behavior;
it is not a measured latency result or acceptance of all lasso operations.

Development version 0.29.0-dev.2 removes the saved-track callback dependency,
visibly closes the original outline, adds nearby actions, and supports cached
finger/stylus drag previews. Native and Android lift ordering, closure,
cancellation, pointer ownership, movement/history and popup bounds are checked
on the host. Android compilation/lint and signed packaging are checked locally.
No tablet was connected during implementation. The owner's subsequent general
acceptance is recorded above; it does not supply quantitative drag/display
measurements or individual results for every operation.

## Scriflow 0.28.0-beta.2 release (2026-10-26)

The owner explicitly authorized publication of the staged signed beta.2 APK.
This release introduces the Scriflow name, retaining application ID, public
signing certificate, notebook storage, and formats. No new item-by-item tablet
results were supplied with that authorization; beta.1 physical acceptance is
not presented as a test of beta.2. In-place update and existing-library
preservation remain useful checks for users of the renamed beta.

- Artifact: `scriflow-0.28.0-beta.2.apk`, Android `versionCode` 30.
- APK SHA-256: `5a1eb45895d2eaae3dbec118bac65015f559a8806934045c0052b3236be908fc`.
- Signer certificate SHA-256: `2cc5db316df82fecc6085ff53e67cbd64f29b412f01e9a5c19bf4b34e3821709`.
- Source/tag: `a0b92163487e932eb69ef4156ecccb3f8c4a4042` / `v0.28.0-beta.2`.
- Automated checks: 20,160 host assertions; debug/release builds and lint;
  standalone debug build; manifest, license, and signature verification;
  legacy signing configuration checks. [GitHub CI passed](https://github.com/xing-phys/Scriflow/actions/runs/40260876032).

The published file is the staged candidate, without a rebuild.

## Scribblow public beta 0.28.0-beta.1 acceptance (2026-10-26)

The owner tested the dedicated-key signed APK and confirmed that it works,
authorizing the [public beta release](https://github.com/xing-phys/Scriflow/releases/tag/v0.28.0-beta.1).
This is general tablet acceptance; the owner did not provide an item-by-item
checklist or a long-session/maximum-capacity benchmark. The target remains
MatePad Paper HMW-W09, firmware `HMW-W09 2.1.0.222(C00E170R1P8)`, Android 10/API 29;
other firmware and devices remain unverified.

- Artifact: `scribblow-0.28.0-beta.1.apk`, Android `versionCode` 29.
- APK SHA-256: `214bc1c8e2308d5fe306e158269384dac442a1798d829a74f89cbd3f105d2d53`.
- Signer certificate SHA-256: `2cc5db316df82fecc6085ff53e67cbd64f29b412f01e9a5c19bf4b34e3821709`.
- Build source/tag: `b3a1834685115b3c7b2a9c69016b63704e0a76e2` / `v0.28.0-beta.1`.
- Automated checks: 20,160 host assertions; debug/release lint and builds;
  APK manifest, bundled license, and signature verification. Public
  [CI run 40247579382](https://github.com/xing-phys/Scriflow/actions/runs/40247579382)
  also passed without private signing credentials.

The published APK is the same file the owner accepted; it was not rebuilt for
publication. These results do not replace acceptance of a future APK.

## Initial investigation and historical checks

Date: 2026-10-23. Device: HMW-W09, firmware
`HMW-W09 2.1.0.222(C00E170R1P8)`, Android 10/API 29.

2026-10-25 update: version 0.26 changes the application refresh policy from 3
to 13 after a successful physical Jnotes comparison. It removes the manual
waveform overrides that failed in versions 0.23–0.25. The policy-3 observations
below remain a historical record. See [refresh investigation](fast-refresh.md)
for the newer evidence and physical acceptance status.

## Prior physical observation

The user tried the published KOReader MatePad Paper APK and reports no visible
strokes until writing finishes. This is not evidence of successful low-latency
firmware ink on this device. Root cause remains unconfirmed.

## Firmware investigation

Read-only inspection of `eink.framework.jar`, native Notes bytecode, and the
handwriting library established these integration details:

- Native Notes uses `android.eink.view.edit.HWEditView`.
- Its parent selects RGBA_8888 and top Z order. Its background picture defaults
  to transparent; the diagnostic explicitly binds a white background.
- The native writable state constant is spelled `STATE_HADNWRITE` and equals 1.
- `getFBSurface()` is non-null before binding. We initialize through `View.post`
  after `surfaceChanged`, checking the active buffer.
- The framework's final binding callback runs after application holder callbacks.
- AHW disables are tracked by identity tokens. We read actual state and blocker
  count rather than assuming the enabling call proves success.
- The framebuffer can outlive surface recreation. Our own listener is detached
  before clearing references to prevent duplicate capture.
- Native handwriting points expose x/y, `eventtime`, and `pressure`.

This supports the implementation choices; it does not establish physical latency
or prove the transparent KOReader overlay is the cause of its failure.

## Checks completed

| Check | Result |
| --- | --- |
| Local bootstrap and offline build | Passed with Java 8, API 29, Build Tools 30.0.3 |
| APK signature and manifest | Verified; package `org.eink.inkprobe`, min/target API 29 |
| Java 8 lambda desugaring | Synthetic lambda classes generated; no LambdaMetafactory runtime dependency |
| Install and Activity launch | Passed on connected tablet |
| Android drawing and JSON export | Synthetic finger swipe: 1 complete stroke, 7 points, no missing DOWN; screenshot visually checked |
| HWSurfaceView initialization | Active buffer, listener attached, AHW true, stylus accepted, 0 disable tokens |
| HWEditView initialization | AHW true, writable state 1, stylus accepted, 0 disable tokens |
| Native diagnostic snapshot | Valid JSON with firmware state and track list |
| Native background/foreground | Returned with AHW true and no disable tokens |
| Code review | Corrected listener cleanup and resize reinitialization before final build |

The synthetic swipe tests ordinary drawing/export, not firmware stylus input.
The native track list was empty during initialization checks because no physical
stroke had been performed in this app at that point.

## Pending physical checks

- Continuous visible ink in Native edit and HW live.
- Captured tracks and pressure after physical strokes.
- Comparison with native Notes and ordinary drawing.
- Rapid consecutive strokes, dots, palm contact, and pen-up behavior.
- Writing across surface recreation and interruption.
- Optical latency measurements, if needed.

After these checks, select the working firmware integration for the next stage.
The current prototype intentionally has no automatic live-to-page handover in
HW live, so app-rendered completion cannot mask a missing live path.

## Physical failure and version 0.2 correction

The user reported severe latency, full-screen black/white blinking, and sometimes
a black screen remaining until another stroke. The tested mode was uncertain.
The saved v0.1 export was HW live with six complete strokes and 229 points;
it does not establish which mode was visible when the complaint occurred.
Our app was force-stopped while investigating. The stored event timeline showed
no repeated surface initialization during those captured strokes.

The user opened the smooth reference. Foreground package/activity was confirmed
as `com.huawei.hinote/.edit.EditActivityInk`, matching the APK already inspected.
No note contents were read or copied.

Read-only effective policy queries before correction returned:

```text
org.eink.inkprobe: {video_mode=11, mode=1}
com.huawei.hinote: {mode=3}
```

Inspection of the system `HwContentDetectorConfig` implementation established
that the application manifest's `hw_eink.app.refresh.mode` is consumed for live
display policy and takes precedence over fallback configuration. Native Notes
declares 3. This is separate from a launcher consumer that uses the same key for
ghost clearing on app exit; the launcher consumer alone was insufficient evidence.

Version 0.2 changes:

- Declare app refresh policy 3, matching Notes; do not change global settings.
- Set HW live to RGBA_8888 instead of its previous RGB_565 default. Native edit
  already inherited RGBA_8888. This matches the reference surface but is not yet
  independently established as the cause of flashing.
- Record declared app policy, app version, and raw view post mode in exports.
- Refuse firmware surface construction if manifest policy 3 is absent.
- Provide a host-only readback script; the hidden vendor readback API is blocked
  from ordinary APKs on this firmware. No hidden-API bypass was added.

Post-install effective query returned `{mode=3}` for both Ink Probe and Notes.
Both surfaces initialized with pixel format 1 (RGBA_8888), AHW true, stylus
accepted, and zero disable tokens. Native writable state remained 1. Both raw
view post modes read 0; no speculative waveform or global override was applied.

The revised build was physically tested in Native edit. The user reports no
blinking, but most writing is delayed and some strokes are occasionally smooth.
The retained export contains 23 native tracks. These observations establish
improvement after the policy/format changes, not consistent low latency.

## Version 0.3 timing investigation (2026-10-24)

Preserve rendering/policy/pen settings while adding an external timing listener
to Native edit. Native HWEditView's internal listener remains registered. The
framework's explicit add/remove listener methods are hidden from an ordinary
APK on this firmware, so use the exposed setter/null pair whose implementation
multiplexes external and internal handlers. No hidden-API bypass is used.

The observer stores callback arrival uptime paired with the firmware event time,
without per-point UI updates, log writes, or refresh commands. Arrival follows
the framework's internal handler and therefore includes its processing time.
This measures software delivery; it cannot measure the physical panel's latency.

Validation before physical input:

- Java host timing tests: 60 assertions passed, including delayed delivery with
  regular sample timestamps, clock errors, percentiles, reset, and ring eviction.
- APK build/sign/install passed. Effective app policy remains 3.
- Ordinary synthetic swipe: 16 recorded samples, delivery p50 24 ms, p95/max
  26 ms; all event/receipt arrays aligned, no invalid clock samples. This is not
  a real-pen benchmark.
- Native edit: AHW true, format RGBA_8888, observer attached, zero disable tokens.
  Native handler remains responsible for tracks and drawing.

Next: collect five separate physical strokes and compare event span against
arrival span and delay percentiles. Prompt event delivery despite visible delay
would direct attention to display/compositor behavior. Late or batched delivery
would direct attention to the callback/widget processing path. Fullscreen is a
confirmed window difference from Notes and a candidate subsequent isolated test;
no fullscreen or global refresh changes are bundled into this measurement build.

## Physical timing result and fullscreen comparison

The user reports all requested strokes delayed. The saved version 0.3 export
contains 20 native tracks and 1007 observer points, including earlier input in
the same session, so it is not a clean five-stroke-only dataset. Aggregate
callback delay was median 1 ms, p95 14 ms, maximum 73 ms. The final five tracks
likewise arrived throughout writing, not as a batch at pen-up. Native and observer
track counts agree. This points toward a display-side issue while leaving the
physical panel latency unmeasured.

The overlapping compositor log contains ordinary refresh pairs roughly once per
second, including idle periods. After each of the final five pen-down events,
the first logged periodic framebuffer comparison occurs 258–803 ms later.
Subsequent native disassembly established that the DOWN-time force-skip command
already copies the current buffer inside its own invocation. The later logged
copy is separate bookkeeping, so these intervals do not establish a missing
preframe handshake or a wait that gates visible ink. UP/CANCEL uses a different
notification path, despite its misleading native name `notifyDownEvent`.

Version 0.4 adds a single-variable fullscreen on/off comparison. Native Notes
was observed using FLAG_FULLSCREEN; the prototype did not. Policy, pixel format,
pen settings, and keep-screen-on behavior remain unchanged. Effective app policy
3, native AHW readiness, and actual fullscreen flag were verified after install.
Resize reinitialization passed without duplicate listener attachment or errors.

A 35-second passive fullscreen log still contains the roughly one-second refresh
pairs, so fullscreen did not eliminate that periodic activity. A single ordinary
app frame on pen-down remains an optional scheduling experiment, with no
per-move repainting; the native contract does not establish that it is required.

## Version 0.4 physical result and unrelated layer cadence

Fullscreen physical result: first stroke improved; some subsequent strokes
initially followed the stylus and then fell behind. The saved export contains
15 native tracks and 1209 observer points. Median callback delay remains 1 ms,
p95 4 ms, maximum 133 ms. This does not establish consistent smooth handwriting.

A passive pair of SurfaceFlinger samples, 10.23 seconds apart, identified the
source of periodic buffer submission:

| Layer | Frame counter change |
| --- | --- |
| Hidden StatusBar | 45158 → 45179 |
| Probe activity | 7 → 7 |
| Probe handwriting surface | 1 → 1 |
| Visible floating overlay | 2 → 2 |

The status bar submitted roughly one-second pairs despite being hidden in
fullscreen. The floating overlay did not update in this sample, so its mere
presence does not implicate it. These measurements identify the periodic buffer
producer, not its optical effect or which SystemUI feature drives it.

The physical isolate was unplugging USB while leaving version 0.4 open, writing,
then reconnecting. The user reports **consistently smoother writing unplugged**.
This supports a charging/USB-related influence, but does not distinguish charging
animation, power state, USB traffic, or another cause. The measured StatusBar
producer is hidden and absent from the visible output-layer list. Its buffer
submissions alone do not establish optical interference.

After reconnection, battery service reports USB powered, charging (status 2), and
85% charge; the StatusBar still submits near-one-second pairs. The next controls
are plugged-in native Notes writing/cadence and a bounded on-device capture
spanning cable removal. These preserve the existing app and handwriting setup.
No battery spoofing, SystemUI stopping, or global display changes have been
applied.

The device-side cadence recorder passed a 10-second connected smoke check:
two samples contained 10 new StatusBar timestamp tuples over five seconds.
Completed log retrieval and removal of its two temporary device files passed.
Host fake-ADB checks also covered serial selection, shell syntax, bounded
duration, incomplete-log retention, collection, and repeated collection.
The recorder has not yet captured an unplugged interval. Its `/proc/uptime`
sample clock includes suspend while SurfaceFlinger's timestamps do not, so
compare deduplicated rows across samples rather than subtracting those clocks.

Version 0.5 was built locally in parallel as an optional subsequent experiment:
one ordinary decor-frame request on pen-down, none on move/up. It remains off
by default, uses app-local invalidation rather than a forced waveform, records
each request time, and does not paint ink. Build/signature and existing host
tests pass. It has not yet been installed or physically tested; version 0.4
remains the current tablet build during the unplugged comparison.

## Plugged-in recovery control

The user reports that native Notes also initially follows the pen and later falls
behind while USB is connected, although it follows for longer than the probe.
After more writing, Notes becomes smooth. This means the earlier assumption of
an always-smooth plugged-in reference was incorrect.

While Notes was left open after recovery, a 10.24-second passive sample found
zero new timestamp rows on StatusBar, Notes' root and handwriting layers, and
the floating overlay. USB remained powered and charging, at 86%. Both apps hide
StatusBar from actual display output. Returning to unchanged Probe 0.4 restored
20 new StatusBar rows over 10.14 seconds; Probe's root, handwriting layer, and
floating overlay stayed unchanged.

A subsequent bounded 60-second recording in Probe showed roughly two new
StatusBar rows per second initially, then no new rows in the final samples.
USB remained powered. The last new row was at firmware monotonic time
156351.895 seconds. The user's later writing continued through 156440.392
seconds, so the periodic activity stopped before pen input ended. The user
reports Probe eventually became smooth too, after substantially more writing
than Notes. The exact optical transition time was not recorded.

The saved Probe export retains 284 native tracks and 15,331 observer samples,
including earlier trials. The new plugged-in trial occupies tracks 148–284,
spanning approximately 202 seconds. Aggregate callback delay across the export
was p50 1 ms, p95 5 ms, maximum 262 ms; the bounded last-8192-sample window has
maximum 221 ms. These are software delivery measurements, not optical latency.

Later reconnection/readback found 100% charge and battery status 4 rather than
the earlier status 2 at 86%. This later condition must not be substituted for
the charging conditions in the recovery samples. A fresh passive sample also
found an unchanged StatusBar history. No battery state was spoofed.

Exact SystemUI inspection did not establish a charging-animation cause: battery
and stylus icons examined are static vectors/level lists, battery broadcasts
skip unchanged state, clock seconds are not explicitly enabled, and the network
speed display is configured off. These checks narrow specific hypotheses;
they do not identify the recurring invalidation's source.

Known remaining window differences include Notes' system-UI flags `0x2500`
(layout stable, layout fullscreen, light status bar) versus Probe's zero, and
Notes lacking Probe's keep-screen-on flag. Both use the fullscreen window flag
in this comparison. An app-local comparison of those two differences can be
measured automatically before requesting another long physical trial.

## Version 0.6 window controls

Installed version 0.6 after saving the long version 0.4 trial. It adds separate
Notes UI (`0x2500` versus zero) and Awake (keep-screen-on) controls, while retaining
the existing fullscreen control. Requested and actual settings are exported.
Saved-instance state preserves window options and selected mode. The uninstalled
version 0.5 DOWN-frame experiment was removed; callbacks again request no app
redraw at any point in a firmware stroke.

Automatic idle samples used Native edit and fullscreen throughout. Each row is
an independent approximately 10-second before/after StatusBar history comparison;
button-triggered setup redraws were allowed to settle before sampling.

| Notes UI flags | Keep screen on | New StatusBar rows |
| --- | --- | --- |
| 0 (initial control) | On | 20 |
| 0x2500 | On | 20 |
| 0 (restored control) | On | 21 |
| 0 | Off | 20 |
| 0x2500 | Off | 20 |

Exports confirmed the actual flag combinations and native Auto-HandWrite true
throughout; effective app refresh policy remains 3. Neither known window
difference suppressed the periodic producer in this test. This is a negative
result, not a confirmed handwriting fix. The final configuration is fullscreen
on, Notes UI on, Awake off. Physical handwriting with version 0.6 is untested.

Battery was now 100%, USB powered, status 4 rather than actively charging status
2. The periodic submissions therefore do not require active charging. The next
bounded recording spans physical USB removal and reconnection, without writing
or any battery/global-display changes.

Validation: APK build and signature verification passed, effective policy readback
passed, existing host timing tests passed all 60 assertions, and own-app exports
verified the control states on the device. No new app permissions were added.

## Interrupted offline capture

After the user unplugged and reconnected USB, host USB descriptors exposed only
MTP/storage, with no ADB interface. The user restored debugging by switching
USB debugging off and on. The on-device recorder had ended without its COMPLETE
marker; it retained only five plugged-in samples, elapsed 0–21 seconds, with
no new StatusBar rows after its initial history. No unplugged sample was saved,
so this capture cannot establish the effect of cable removal. It must not be
used as an unplugged control.

The collector now supports explicitly recovering a valid-header partial log
after the recorded start time plus duration plus ten seconds. Default collection
still retains incomplete logs. Recovery labels the log PARTIAL and records
`complete=false`; cleanup removes only the recorder's two generated files after
the local save. Both host fake-ADB checks and this actual partial recovery passed.

## Clash traffic notification identified

A five-second metadata-only gfx/view/window/Binder trace contained five calls
from PID 7343, `com.github.metacubex.clash.meta:background`, to
`INotificationManager` transaction 6. This exact firmware's Stub maps transaction
6 to `enqueueNotificationWithTag`. Calls were approximately one second apart.
Each was followed by SystemUI fetching its notification within 7–10 ms, then
the paired UI frames within 23–66 ms. No notification text or note contents
were inspected to identify the sender.

Read-only inspection of the installed Clash APK, version 2.11.34.Meta/code
211034, confirmed that DynamicNotificationModule uses a one-second ticker and
posts traffic counters while interactive. App Settings' Show Traffic switch
maps to ServiceStore.dynamicNotification, is disabled while Clash runs, and
selects the dynamic versus static notification module at service startup.
Public counterparts:

- [Dynamic notification module](https://github.com/MetaCubeX/ClashMetaForAndroid/blob/main/service/src/main/java/com/github/kr328/clash/service/clash/module/DynamicNotificationModule.kt)
- [App settings control](https://github.com/MetaCubeX/ClashMetaForAndroid/blob/main/design/src/main/java/com/github/kr328/clash/design/AppSettingsDesign.kt)
- [Service module selection](https://github.com/MetaCubeX/ClashMetaForAndroid/blob/main/service/src/main/java/com/github/kr328/clash/service/ClashService.kt)

After being asked about a brief VPN restart to change only this preference,
the user stopped Clash. Automatic measurement then found zero new StatusBar
rows over ten seconds. The setting was observed ON, switched OFF through its
normal UI, and Clash was restarted. Its foreground TunService was verified
running. Other App-settings switches retained their original OFF states.

| Condition | New StatusBar rows per approximately ten seconds |
| --- | --- |
| Clash running, Show Traffic on | About 20 |
| Clash stopped by user | 0 |
| Clash restarted, Show Traffic off | 0 |

This identifies and suppresses the recurring notification-driven redraws while
retaining the VPN. A new Native edit surface is open for a short physical pen
test. Handwriting improvement and the exact reason earlier cable/state changes
affected the symptom remain unconfirmed until that test.

## Physical result with static Clash notification

On a fresh Native edit surface in version 0.6, with Clash running and Show Traffic
off, the user reports **almost smooth writing throughout from the beginning,
with only occasional brief sticking; substantially better than before**.

The saved export contains one complete 15.839-second stroke, 1,389 observer
samples, and one native widget track. AHW remained true, writable state 1, and
zero disable tokens. Actual system-UI flags were 0x2500, fullscreen on, and
keep-screen-on off. Callback delay was p50 1 ms, p95 3 ms, maximum 65 ms.
The 65 ms maximum occurred at DOWN. One later sample, 13.007 seconds into the
stroke, had 50 ms callback delay; the following event-time gap was 61 ms.
There were no other mid-stroke callback delays above 20 ms.

No optical timestamps identify the reported rare pauses, so this software sample
cannot establish that the 50 ms callback delay caused a visible pause. No matching
app GC/suspension log was found. Further changes should target reproducible
remaining pauses rather than infer a new cause from this single event.

The controlled notification change removed the recurring redraws and produced a
large reported physical improvement. This establishes a practical configuration
for continuing development, while consistent native-Notes parity and robust
behavior around unrelated background updates remain open checks. Clash is
running with only Show Traffic changed to off; no proprietary code or private
stroke/log exports are committed.

## Version 0.7: one-page notebook

The user authorized the first notebook milestone: blank/lined paper, native
writing, automatic saving, and editable restoration. The launcher now opens
NotebookActivity as Ink Notebook; the original probe remains a separate
development Activity. No application permissions or network features were added.

Host verification passed: 60 timing assertions and 1,254 real-file storage
assertions. Storage tests cover style/coordinate/time/pressure round trips,
every truncation boundary of the final append, appending after tail recovery,
checksum/header/content corruption without overwriting source bytes, and
metadata replacement with backups.

On the tablet, isolated synthetic restoration checks passed for both templates:
two native tracks and nine points matched coordinates, pressure, timestamps,
and pen styles. Blank paper's background had no ruling; lined paper's opaque
bitmap had 24 ruled rows at the measured test height. A separate simulated
callback-boundary check passed: an UP preceding native commit was emitted once
on stop, repeated stop did not duplicate it, and partial/cancelled strokes were
discarded. This simulated check does not claim real stylus latency coverage.

First physical result: the user reports smooth writing, but the unchanging
Saved label did not communicate what had been saved. The document contained
22 completed strokes and 799 points on lined paper, in a 1404×1688 logical
canvas. The Saved indicator was improved to show the saved stroke count after
a quiet interval; it remains unchanged while the pen is active.

After a force-stop and reopen, the page file was byte-for-byte unchanged.
An explicit debug check of the actual reopened widget confirmed all 22 tracks
and 799 points matched the saved model, including coordinates, pressure,
timestamps, and pen styles. Switching the actual page to blank paper and back
to lined paper, with force-stops between, passed the same full native-track
comparison in both cases. These checks did not inspect handwritten text or
commit private stroke data.

The final physical acceptance check passed: the user confirms the previous
writing is present, the saved count increases after adding writing, and writing
remains smooth. The updated durable lined page contains 36 strokes and 1,120
points, with no incomplete tail. Exact comparison confirms all original 22
strokes and 799 points remain unchanged as the document prefix, including pen
style, coordinates, timestamps, and pressure. Thus 14 new strokes and 321 points
were appended successfully after restoration.

The one-page milestone is accepted: blank/lined paper, native writing, completed
stroke autosave, forced-stop recovery, and continued writing after reopening.
The source also guards handwriting before restoration is ready and preserves a
completed UP when pause precedes its native track notification. No further app
or device changes were made for this final metadata/file comparison.


## Version 0.20: native grey eraser preview (2026-10-25)

The user reported severe eraser-preview delay in 0.19. The preview used ordinary
Android touch input and manual framebuffer updates. It now uses Huawei AHW with
a grey ballpoint and the chosen eraser diameter. The existing pen path stays
unchanged. Only a completed model erase is saved; the temporary native grey
track is removed with the touched ink in one canonical update.

On HMW-W09, configuration checks pass for widths 4, 36 and 240 with AHW enabled
and color `0xffaaaaaa`. Physical width-36 testing completed six gestures,
including an actual hit and no-hit cleanup. Native track style, settled native
accumulator, canonical track restoration, zero pen-stroke emission, and retained
Surface/paper identities all pass. The user confirms smooth trail tracking and
that the trail and touched ink disappear on release.

Maximum-width physical testing also passes native style, accumulator and cleanup
checks. The user reports visible lag when moving the 240-wide brush very fast,
with smooth behavior at more moderate speeds. They chose not to spend further
time on that uncommon extreme. The 4–240 range and accurate erase footprint are
retained; the default is still 36.

A debug-only observer on the disposable canvas measured a continuous
18.479-second gesture: 1,140 samples, mean delivery delay 6.31 ms, maximum 23 ms,
and late maximum 21 ms. Another 7.758-second gesture peaked at 24 ms. No growing
application event backlog appears in those samples. These are software delivery
timings, not optical measurements. The layer timestamp check found no StatusBar
frames in the approximate long-stroke windows and no periodic floating-overlay
frames. The remaining optical limit's cause is not established. No system
settings or background services were changed.

After the final code change, blank/lined native pen restoration checks pass,
including coordinate/style/pressure/time round-trip, in-place erase/undo/redo,
no-hit behavior, paper/page switching and Surface/bitmap retention. Simulated
pause-boundary checks still preserve a completed UP exactly once while discarding
partial and cancelled pen input. The native-erasing join is separately tested
in both observer orders, cancellation, reset, timestamp mismatch and real-file
undo/redo. The host suite passes 19,035 assertions.

All physical checks used NotebookSelfTestActivity's disposable synthetic page.
It never opened a user notebook, and only aggregate timing/count results were
retrieved. The firmware binaries and sampled handwriting are not committed.
The signed API 29 APK uses the existing local update key; page format and tool
width preferences remain compatible with earlier versions.


## Version 0.21: pressure-consistent scrolling and PDF (2026-10-25)

The user observed thicker, constant-width strokes in scrolling and exported PDF,
while the native canvas retained pressure variation. Stored strokes already had
style, coordinates, pressure and event timestamps. The Android preview and PDF
renderers used only the nominal width and point centre line.

Both now obtain the native brush's filled outline via an offscreen HandPen
replay. Overview thumbnails use the same geometry without their old minimum
stroke-width inflation. No data migration or change to live AHW is needed.
PDF output fills the complete native outline once; it does not simulate pressure
with separate overlapping line segments. Native curves are flattened to 0.05
source-page units, and output streams are compressed losslessly.

A headless on-device check generates only synthetic data and never opens the
notebook store. It compares 14 strokes against HWTrackHelper's own replay,
including light/heavy/variable pressure, widths 1/3/4/6/10, dots, tight curves,
and a self-crossing loop. At nominal width 10, native replay, the new Canvas
renderer and the exported PDF all measure the light/heavy strokes at 5/10 pixels.
No thresholded ink boundary differs by more than one source pixel; antialiasing
accounts for non-identical raster pixels. Reused native pens do not mutate
previous paths, and cancelling replay does not contaminate the next stroke.
The generated two-page PDF passes PdfRenderer, pdfinfo and Poppler rendering;
blank/lined PNGs and pressure details are inspected. Lossless compression reduces
the synthetic PDF from 1,434,878 to 292,887 bytes with unchanged geometry.

Disposable APK-UID tests confirm access to the native outline API from the
installed app. Blank/lined native restore, in-place edit, page/paper switching,
Surface/bitmap retention and simulated UP-before-commit checks pass. The host
suite passes 19,820 assertions, including decoded PDF stream geometry, fill
rules, opacity, compressed lengths, xref offsets, cancellation, errors and real
file/history/scroll-position preservation.

Physical acceptance: the user confirms scrolling and fresh PDF export both
match the writing canvas. No user note contents or handwritten samples were
retrieved for the synthetic comparison. The existing signing key and page format
are retained. Old PDF files are not rewritten automatically; re-export uses the
correct pressure-shaped outlines from the already saved points.
