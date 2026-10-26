# Changelog

The first public release, 0.28.0-beta.1, used the name Scribblow. Current
development uses Scriflow. Earlier version numbers below
identify development milestones. Detailed historical verification is in
[development history](docs/development-history.md); pending checks in those
records describe their original milestone, not the current acceptance status.

## [0.31.0-dev.1](https://github.com/xing-phys/Scriflow/releases/tag/v0.31.0-dev.1) — PDF notebooks and outlines (2026-10-26)

- Import a PDF as a separate editable notebook through the system file picker.
  Preserve an immutable private copy of the original PDF and keep handwritten
  strokes separate. Render mixed page sizes and rotated pages as backgrounds.
- Export original PDF content with added vector pressure ink, retaining source
  PDF text, links, destinations, bookmarks, and other catalog data. PDF page
  duplication and ordinary blank page insertion use the same notebook controls.
- Follow PDF links with finger taps, navigate imported or manually edited note
  outlines, and return using Previous view / Next view icons. Outline targets
  follow stable page identities across page reordering.
- Include original PDF bytes and outlines in version-2 editable backups; continue
  reading earlier backups and page formats. New page snapshots use version 4.
- Advance versionCode to 37 with the same application ID and release certificate.
  25,673 host assertions, both Android lint/build variants, and the standalone
  APK build pass; synthetic PDF renders were visually inspected. Publish as a
  development prerelease at the owner's explicit request. Physical
  tablet acceptance and large-PDF performance checks remain pending.

## [0.30.0-dev.3](https://github.com/xing-phys/Scriflow/releases/tag/v0.30.0-dev.3) — Synchronized app orientation (2026-10-26)

- Share the accepted screen direction across the library, editor, Settings,
  backup/restore, and PDF export. Update screens in the back stack when the
  direction changes, so returning to the bookshelf does not reuse its old
  portrait request and then wait for another sensor reading.
- Apply the shared direction before creating screen content; new screens
  inherit the direction beneath them during launch. Remember the last accepted
  direction across process restart and preserve both reverse directions.
- Calibrate sensor directions from real display dimensions, independent of
  a background Activity's potentially stale resource configuration. Keep the
  existing settling interval and pen deferral for actual physical turns.
- Advance versionCode to 36, retaining the application ID, signing key, file
  formats, and existing paper/zoom/palm behavior. Host regressions, Android
  builds/lint, and packaged orientation checks pass. The navigation transition
  still needs confirmation on the tablet.

## [0.30.0-dev.2](https://github.com/xing-phys/Scriflow/releases/tag/v0.30.0-dev.2) — Page edges and touch safeguards (2026-10-26)

- Draw faint page outlines on the white writing background at every zoom,
  including scrolling and selection previews. Guides are screen-only.
- Replace the firmware-managed FULL_SENSOR request with a foreground sensor
  listener and explicit orientation requests. Use stable gravity sectors,
  a 600 ms settling interval, and pen-contact deferral; retain direction when
  flat or ambiguous. Settings reports sensor availability. No new permission
  or system-setting access is needed.
- Give the stylus priority over finger navigation using hover/contact state
  and a 600 ms quiet period. Keep rejected palm contacts suppressed until
  release; leave native writing enabled for an unengaged resting touch.
- Filter broad contacts and more than two fingers. Require two fresh contacts
  and deliberate span change before zoom; discard temporary zoom/pan on
  cancelled gestures. Add a Two-finger zoom switch in Settings.
- Advance versionCode to 35, retaining the application ID, release key, page
  format, and physical PDF dimensions. Automated checks pass; the sensor and
  palm changes still need confirmation on the actual tablet.

## [0.30.0-dev.1](https://github.com/xing-phys/Scriflow/releases/tag/v0.30.0-dev.1) — Paper sizes, zoom, and orientation (2026-10-26)

- Create an A5 notebook in one tap with **Quick A5 note**, or choose A5, A4,
  ISO B5, Letter, other standard paper, or custom width/height when creating
  a named notebook. The fresh-install starter notebook also uses A5.
- Export new pages at their selected physical PDF size. Preserve physical
  dimensions through page addition, blank insertion, duplication, and editable
  backup/restore. Existing notes retain their logical coordinates and PDF scale.
- Add two-finger pinch zoom, percentage feedback, page-fitting choices, fixed
  percentage presets, horizontal panning, and white space outside paper.
  New notes start at Fit width. Map handwriting, erasing, and lasso edits
  through the current page transform.
- Add app-wide Portrait, Landscape, and Follow device sensor settings. Rebuild
  the native writing surface after rotation, keep its position and zoom, and
  defer sensor rotation during a pen contact. Preserve creation/rename drafts
  across activity recreation.
- Advance page snapshots to version 3 with optional physical paper dimensions.
  Continue reading version 1/2 without changing their coordinates or IDs.
  Older APKs cannot read pages saved in version 3; keep editable backups.
- Advance Android versionCode to 34, retaining the application ID and permanent
  public signing certificate. Publish as a development prerelease at the
  owner's explicit request. Automated checks pass; physical tablet acceptance
  of pinch gestures, landscape handwriting, and sensor rotation remains pending.

## [0.29.0-dev.3](https://github.com/xing-phys/Scriflow/releases/tag/v0.29.0-dev.3) — Floating tools and status islands (2026-10-26)

- Replace rounded-square tools with compact 32 dp circular buttons inside a
  pill-shaped dock, retaining horizontal/vertical layouts and grip dragging.
- Tap the six-dot grip to collapse the dock to one circle; tap again to expand.
  Dragging still works when collapsed. Remember position, orientation, and
  collapsed state; Reset tools expands and restores the initial placement.
- Replace the fixed header with separate notebook/page and time/battery/Wi-Fi
  islands at the top corners. Extend the page through the former header area
  and protect all floating controls from native ink, including overlapping bounds.
- Distinguish grip taps from drags using touch slop, cancel toggles for
  multi-pointer/cancelled gestures, and retain idle-only device-status updates.
- Advance Android versionCode to 33, preserving the application ID, permanent
  public signing identity, notebook storage, and file formats.
- Publish as a development prerelease at the owner's explicit request. Host
  tests, Android lint/builds, and signed APK verification pass. No tablet is
  connected; physical validation of the UI and native ink exclusions is pending.

## [0.29.0-dev.2](https://github.com/xing-phys/Scriflow/releases/tag/v0.29.0-dev.2) — Lasso and page management (2026-10-26)

- Release whole-stroke lasso selection and overview page management: reorder,
  duplicate, insert blank pages, delete, and separate page undo/redo. Page order
  uses a version-2 index after the first management edit; earlier APKs cannot
  open managed notebooks. Back up editable notes before updating.

- Complete a lasso from either native or Android stylus lift, without requiring
  a saved-track/edit callback. Retain native dashed drawing during the gesture;
  explicitly close and display the original curve after lift, including empty
  selections with a visible selected-stroke count.
- Add a nonmodal action bar beside the region: Copy, Duplicate, Cut, Delete,
  and Done. Drag inside with a finger or stylus to preview movement; commit one
  undoable edit on release. Cache native pressure paths/background once per drag
  and coalesce display updates to 32 ms without manual waveform overrides.
- Preserve selection through an unsettled native-widget replacement, cancel
  mixed-pointer/unfinished drags, and keep all outlines and preview frames out
  of saved notes and exports. The completion fix adds no schema changes beyond
  the page-order index introduced in dev.1.
- Publish the exact signed versionCode 32 APK accepted by the owner, without
  rebuilding or renaming its internal version. The owner confirmed that the
  updated interaction works well and authorized publication. This is general
  acceptance, without an item-by-item checklist or measured latency claims.
- Pass 22,326 host assertions, Android lint, debug/release builds, and signed
  APK verification. Retain the permanent public signing identity.

## Development — 0.29.0-dev.1

- Manage pages in the overview: move earlier/later or to a final page number,
  duplicate, insert blank before/after, and delete with confirmation. Keep at
  least one page. Separate page undo/redo retains 50 changes in the current process.
- Publish page order through a checksummed version-2 index mapping display
  positions to stable storage slots. Reordering never renames or rewrites ink;
  duplication gives pages and strokes fresh IDs. Existing books remain in their
  original index format until page management is used.
- Bind saved scroll positions to the order generation so a restart after
  reordering/deleting cannot reuse an old page number. Refresh overview and
  editor caches after management; export and backup follow the current order.
- Retain removed/uncommitted page files privately for recovery without exposing
  them in current notebooks or exports. Preserve pending commands for exact retry
  after ambiguous save completion; never overwrite unrelated orphan pages.
- Advance development versionCode to 31. No public release or tablet installation
  is performed; physical acceptance remains pending.

- Add a Lasso tool using Huawei's native dashed pen for the live trail, with no
  application redraw or document edit during stylus movement.
- Select fully enclosed whole strokes on one page. Drag inside the selection
  and lift to move it; open its actions to duplicate, copy, cut, paste, delete,
  deselect, or select all on that page.
- Keep copied vector ink in a private process-local clipboard for pasting
  across pages and notebooks. Preserve pressure, timing, style, and ordering;
  moves retain IDs and copies receive new IDs.
- Save each completed edit as one undoable page action through the existing
  snapshot/retry path. Selection and its temporary trail never enter notes,
  backup, export, or history. File formats remain unchanged.
- Host geometry/storage tests, Android lint and debug/release builds pass.
  Native dashed input and physical e-ink responsiveness still need tablet
  acceptance; no device was connected during implementation.

## [0.28.0-beta.2](https://github.com/xing-phys/Scriflow/releases/tag/v0.28.0-beta.2) — Scriflow identity (2026-10-26)

- Rename the app to **Scriflow**, bringing together scribe, script, and scribble
  with flow. Retain the flowing-S monochrome symbol and the tagline
  **Handwriting for thinking.**
- Update launcher and screen labels, PDF credits, generated APK names,
  documentation, and repository links to the new name.
- Advance Android `versionCode` to 30 while preserving application ID,
  permanent release signing identity, notebook storage, formats, and page IDs.
- Prefer `SCRIFLOW_*` signing variables; accept a complete legacy `SCRIBBLOW_*`
  environment only when no new variables are set, so existing private backups
  remain usable without mixing credentials.
- Keep the published Scribblow 0.28.0-beta.1 artifact and its checksum unchanged.
  Publish the renamed beta with the owner’s authorization.

## [0.28.0-beta.1](https://github.com/xing-phys/Scriflow/releases/tag/v0.28.0-beta.1) — First public beta (2026-10-26)

- Publish the signed APK and checksum after the owner's tablet acceptance.
- License Scribblow's source, documentation, and original artwork under MIT;
  include the MIT notice in APKs and retain the Gradle Wrapper's Apache 2.0
  license and notices separately.
- Adopt three-part prerelease numbering for public testing. Android
  `versionCode` advances to 29 independently of the displayed version.
- Separate public-release signing from development signing; ordinary contributor
  builds can produce an unsigned release without private credentials.
- Restrict diagnostics to development entry points and keep internal screens
  private in release builds.
- Fix the lint indentation error and add automated build/test checks.
- Reorganize user, build, release, privacy, and development documentation.
- Sign local release candidates with the dedicated Scribblow release key,
  verified against its recorded public certificate fingerprint.

## 0.27 — Scribblow identity

- Adopt the Scribblow name, flowing-S monochrome launcher icon, and PDF credits.
- Preserve application ID, notebook storage, page identities, and update
  compatibility with the development signing identity.

## 0.26 — Firmware-managed refresh

- Use Huawei refresh policy 13 and remove manual waveform overrides introduced
  in 0.23–0.25. Physical testing on the target tablet confirmed scrolling and
  immediate writing without transition flashes or freezing. See
  [the refresh record](docs/fast-refresh.md) for scope and limits.

## 0.22 — Durable document foundation

- Add persistent page/stroke IDs, a version-1-to-version-2 page migration,
  editable `.inkbook` backup/restore, and resumable restore publication.
- Add document/device boundaries, coordinate transforms, independent background
  work, and standard Gradle tooling. PDF import, rich text, and AI remain future
  work rather than implemented features.

## 0.20–0.21 — Native eraser and consistent ink

- Move the temporary eraser trail onto native handwriting.
- Match pressure-aware brush outlines across writing, scrolling, thumbnails,
  and vector PDF export.

## 0.14–0.19 — Compact tools and widths

- Add draggable, rotatable icon tools, a compact device-status bar, pen/eraser
  width sliders, and eraser preview.
- Correct slider completion so the selected width is retained on release.

## 0.10–0.13 — Library, scrolling, overview, and PDF

- Add named notebooks, continuous finger scrolling, page thumbnails and jumps,
  and whole-notebook vector PDF export.

## 0.7–0.9 — Editable notebooks

- Add durable native handwriting restoration, whole-stroke erasing, undo/redo,
  multiple pages, and blank/lined paper.
- Preserve the original notebook in place as the app grows.

Earlier ink and display experiments are retained in the
[device test record](docs/device-testing.md) and [diagnostics guide](docs/ink-probe.md).
