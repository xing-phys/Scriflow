# Changelog

The first public release, 0.28.0-beta.1, used the name Scribblow. Current
development uses Scriflow. Earlier version numbers below
identify development milestones. Detailed historical verification is in
[development history](docs/development-history.md); pending checks in those
records describe their original milestone, not the current acceptance status.

## 0.33.1-dev.5 — Immediate floating-note drag and resize (2026-10-29)

- Remove the corner-control hold timer. Movement begins dragging immediately;
  stationary taps retain options/minimize actions, with busy/cancel protection.
- Track resize bounds using four thin untouchable edge panels. Refit the actual
  note once after release, retain its foreground border and pause native input
  through pending layout/save callbacks.
- Host tests and signed Debug/Release validation pass. VersionCode 59; physical
  tablet checks remain pending at the owner's request. See the
  [interaction record](docs/window-figure-interactions.md).

## 0.33.1-dev.4 — Coordinate optional cache memory (2026-10-29)

- Share PDF fallback and outline allowances across visible notes while retaining
  required sharp pixels. Release unused source readers for blank viewports and
  trim optional resources on Android memory callbacks without clearing native
  framebuffer contents or visible figure pixels.
- Cache only currently visible active-page outlines, clear retired handoff
  caches promptly and avoid owned-path allocations for private cached drawing.
- Share identical decoded image content across independent reloads, account for
  cache-entry overhead and retire the figure decoder thread after idle.
- The final signed update passes 58 tablet checks, 245,280 host assertions,
  native rendering comparisons and lint/build/signature checks. A four-pane PDF
  fixture reduced cached pixels from 32 to 20 MiB with all sharp tiles retained.
  VersionCode 58; local candidate, public stable release unchanged.

## 0.33.1-dev.3 — Prepare figure pixels off the UI thread (2026-10-29)

- Decode visible figures on a worker at a resolution sufficient for their screen
  size; retain original resolution for zoom/export. Apply the same preparation
  to inactive readers and the figure editor, with complete-frame readiness.
- Pin prepared visible pixels across shared-cache eviction and share immutable
  duplicate assets safely. Skip stale loads after source, zoom and lifecycle
  changes; preserve recoverable error handling.
- Full host/lint/build checks and 37 tablet assertions pass. Two 2048² sources
  used 2 MiB of fit-width prepared pixels versus 32 MiB as full decodes; zoom
  reacquires original resolution. VersionCode 57, local signed candidate.

## 0.33.1-dev.2 — Reuse exact pressure outlines (2026-10-29)

- Cache a bounded, stable subset of immutable stroke outlines for active previews
  and inactive readers; retain full-quality replay outside the cache budget.
  Preserve caller path ownership and release old view caches on pause/minimize.
- Reuse derived stroke bounds and source-page vectors during viewport composition
  and focus handoff. Remove per-MOVE reader stroke-set reconstruction.
- Native cached/uncached pixels match exactly; handoff threshold geometry is
  unchanged. Full host/native regressions and 36 tablet checks pass. A geometry
  replay microbenchmark improved from 785 ms to 1.6 ms with warm paths; raster
  work and optical/display latency are excluded. VersionCode 56, local candidate.

## 0.33.1-dev.1 — Indexed scrolling geometry (2026-10-29)

- Share immutable page geometry when panning; reuse unchanged viewports and avoid
  copying page sizes on zoom anchor updates. Index visible/hit/anchor/nearest
  searches while preserving page/gap/float boundary behavior.
- Validate against linear reference geometry and the connected-tablet workspace
  suite. The synthetic 600-page geometry workload improved from 713 ms to 11 ms
  median, with identical results; this is not an overall app-speed measurement.
- Retain direct native ink, complete-frame display updates, stored formats and
  permanent signing identity. VersionCode 55; local signed tablet candidate.
  See [optimization record](docs/optimization-0.33.1.md).

## 0.33.0 — Floating notes and M-Pencil shortcut (2026-10-28)

- Promote the verified dev.7 implementation to stable, versionCode 54. Retain
  the permanent signing certificate and in-place update identity.
- Include figures, lasso export, two split panes plus multiple floating notes,
  the notes tray, stylus-tap focus, continuous sharp inactive-note reading,
  retained main-editor rendering, frontmost tools and direct PDF import.
- Include the physically accepted M-Pencil body Pen/Eraser shortcut and brief
  top-center tool hint. Reduce the measured small-pane PDF cache by 64% and
  release hidden native PDF readers/descriptors.
- Connected HMW-W09 checks include landscape system-picker import (two and 600
  pages), recoverable invalid/encrypted PDF errors, real workspace/lasso/tool
  integration, repeated window operations, native ink consistency, backup round
  trips and sleep/wake/Home-return. See the [audit](docs/matepad-audit-0.33.md).
- Page format 6 requires this version or newer after editing; retain editable
  backups before upgrading. Other devices/firmware, overnight battery drain,
  physical cover sensing and optical latency remain unverified.

## 0.33.0-dev.7 — Measured PDF resource reductions (2026-10-28)

- Release native PDF readers/descriptors on the PDF worker when a pane is hidden
  or its source is cleared. Closing a read-only companion no longer discards the
  active editor's link-navigation reader. Blank panes do not start unused PDF
  workers for cleanup; display memory exhaustion follows the existing error path.
- Bound zoom fallback pixels to two visible tile sets, retaining the full-screen
  ceiling and all required sharp tiles. The connected-tablet small-pane fixture
  fell from 5,767,168 to 2,097,152 cached pixels (64% less) without a measured
  rendering-time increase.
- Extend signed-release device checks with multi-note scrolling/pinch/focus,
  split limits, 20-cycle window stress, resource counts, idle timing and lifecycle
  checks. Run native rendering/storage tools against current signed release
  classes instead of stale debug outputs. Correct the PDF comparison fixture's
  page rounding/viewport transform; retain the one-pixel outline tolerance.
- Keep the native live-writing pipeline, saved ink and permanent signer intact.
  VersionCode 53. See [MatePad audit](docs/matepad-audit-0.33.md).

## 0.33.0-dev.6 — M-Pencil body double tap (2026-10-28)

- Listen to the foreground M-Pencil body double-tap action verified on the
  connected MatePad firmware. Pen switches to Eraser; Eraser or Lasso switches
  to Pen. An unfinished native stroke is allowed to finish before switching.
- Show the selected Pen/Eraser vector icon briefly at the top center (650 ms),
  then remove it without animation. The toolbox remains above this hint.
- Ignore duplicate/late/background gestures; clear queued gestures on pause and
  focus handoff. Ordinary pen samples/up events do no shortcut work when idle.
- Keep direct native handwriting, captured ink, document formats and permanent
  signing identity unchanged. VersionCode 52.

## 0.33.0-dev.5 — Frontmost tools and direct PDF import (2026-10-28)

- Keep the toolbox on the higher application subpanel layer so opening notes,
  status islands, and lasso controls cannot cover it; retain its window during
  tool gestures rather than dismissing/reopening it to change stacking order.
- Show editor popups through the live Activity window token while keeping their
  floating-note geometry. Fix the lasso popup's nested-window token failure and
  apply the same host to width controls, zoom badges, figures and other panels.
  A stale token during Activity teardown no longer becomes a native ink error.
- Open the PDF file picker immediately from Import PDF, without a second Choose
  PDF action, then open the imported notebook automatically after publication.
- Retain picker/import/open phases across landscape recreation and guard UI
  handoff against paused, destroyed, finishing or unattached Activity instances.
  Add regressions for duplicate results, cancellation, failure retry and one-time
  opening. PDF staging/publication memory failures report an import error instead
  of escaping the worker; the physical landscape case still needs confirmation.
- VersionCode 51; permanent signer, application ID and notebook formats unchanged.

## 0.33.0-dev.4 — Keep the main note visible during window changes (2026-10-28)

- Stop destroying/recreating the native editor for floating-note move, resize,
  open, close, minimize and restore operations. Retain unrelated readers and
  update only layout/input exclusions when the editor dimensions are unchanged.
- Repost existing painted paper/native tracks after exposing covered areas.
  Recompose resized viewports on the same widget after native geometry is ready.
- Reuse the paused editor during focus handoff. Keep the incoming reader visible
  until native content is ready; retain the outgoing note's complete frame until
  its vector pages and PDF tiles are ready. No blank loading frame is exposed.
- Render paper/ink updates into a staging bitmap before copying complete pixels
  to the bound display bitmap; never clear that bound bitmap while replaying ink.
- Add production-callback regressions for layout/native-size races, retained
  widget ownership, stale stroke rejection, frame retention and bitmap post order.
- VersionCode 50; permanent signing identity and document formats unchanged.
  Physical e-ink refresh and native input acceptance remain pending on the tablet.

## 0.33.0-dev.3 — Multi-note windows and continuous reading (2026-10-28)

- Remove the redundant “Active” prefix from the shared note status island.
- Move note management to a toolbox button. Open the second note directly, then
  use its adjacent tray to add floating notes and restore individual minimized
  notes. Keep at most two split notes while allowing multiple floating notes.
- Switch editing focus by tapping inside a note with the stylus. Keep one native
  writer and preserve every note's position, page, zoom and session undo history.
- Replace inactive-note bars with two small corner icons: options/move on the
  left, minimize/resize on the right. Hold before dragging a floating window.
- Replace single-page bitmap previews with continuous, width-fitted viewports,
  finger scrolling/pinching, vector ink and PDF tiles at the current zoom.
- Persist independent window/read states and migrate earlier two-note layouts.
- VersionCode 49; permanent signing identity and document formats unchanged.
  Host regression and signed-build verification cover the implementation;
  physical stylus, e-ink and window behavior still require tablet acceptance.

## 0.33.0-dev.2 — Full-screen workspace status islands (2026-10-28)

- Remove the reserved 48 dp top strip when a second note is open or collapsed.
  Both note panes use the full screen beneath the existing top-left and
  top-right floating status islands, matching the single-note layout.
- Retain the permanent release signing identity; versionCode 48.

## 0.33.0-dev.1 — Figures, selection export, and second-note workspace (2026-10-28)

- Insert SVG/SVGZ and Android-supported images, stored as bounded PNG figures.
  Move, resize and delete figures with undo/redo; retain them in thumbnails,
  PDF exports, page duplication and editable backups.
- Export lasso-selected handwriting as SVG, PNG, JPEG, WebP or PDF, cropped to
  native pressure outlines. Vector formats retain vector ink; PNG/WebP retain
  transparency.
- Open another note to the left, right, top or bottom, or in a movable/resizable
  floating window. Navigate the inactive note independently, activate either
  note without moving it, share one toolbox and active-note status bar,
  collapse/reopen the second pane, and retain placement across app restarts.
- Introduce page format 6 with embedded figures; older page formats remain
  readable and migrate on edit. Earlier APKs cannot read migrated pages.
- Add pinned AndroidSVG 1.4 and its Apache-2.0 license to both builds.
- VersionCode 47. Host regressions and build/lint checks cover the implementation;
  native touch and handwriting acceptance on hardware remains pending.
  See [figures and workspace](docs/figures-and-workspace.md).

## [0.32.0](https://github.com/xing-phys/Scriflow/releases/tag/v0.32.0) — First stable release (2026-10-27)

- Publish the first stable GitHub release, incorporating all changes through
  the locally tested 0.32.0-dev.4 candidate. Increase versionCode to 46 and
  retain the permanent signing key, app ID and existing data formats.
- Include native handwriting, paper sizes and zoom, sensor rotation, PDF
  import/annotation/export, outlines, editable backups, page management and
  per-page undo/redo from the earlier prereleases.
- Include compact bookshelf/Settings layouts, portrait/landscape thumbnail
  grids, folders and recoverable Trash, flat dialog borders, status-area
  Settings access, current-page insertion and foreground-only Keep screen on.
- Fix folder/Trash taps, replace grid number fields with −/+ controls, match
  the eraser preview width, and keep width sliders open across adjustments.
- Select whole strokes on any lasso overlap and move selections across pages
  with recoverable saves and paired undo/redo. Fix input restoration after
  dropping a selection and remove artificial lasso-completion polling.
- Reduce the finger-navigation quiet period to 200 ms after pen contact/hover.
  Physical support remains limited to the tested MatePad Paper firmware;
  cover-lock/battery and sustained-use validation are still incomplete.

## 0.32.0-dev.4 — Partial-overlap lasso selection (2026-10-27, local test candidate)

- Select the whole stroke when any part of its centreline enters or touches
  the closed lasso region, including sparse segments with both endpoints outside.
- Reduce the finger-navigation quiet period after pen contact/hover from
  600 ms to 200 ms. Active pen contact/hover still blocks finger navigation.
- Keep the application/signing identity and existing data formats; versionCode 45.

## 0.32.0-dev.3 — Resume input after cross-page lasso moves (2026-10-27, local test candidate)

- Fix a paused editor after dropping a lasso selection onto another page. Keep
  viewport restoration pending while the asynchronous page/PDF window loads,
  so its completion reinstalls the viewport and resumes native input.
- Add a regression executing the production Activity callbacks with queued page
  reads, including stale reads, PDF readiness, generation changes and save errors.
- No page/catalog format or handwriting-brush changes.

## 0.32.0-dev.2 — Touch and lasso fixes (2026-10-27, local test candidate)

- Replace bookshelf/page-grid numeric fields with bounded −/+ controls.
- Keep bookshelf cell controls stable during grid relayout and use valid positive
  item IDs. Fix folder opening/management and the first Trash item's menu.
- Match the eraser's grey trail to its constant-width hit-test brush.
- Keep pen/eraser width sliders open across adjustments; outside input closes
  them without deliberately consuming the first writing gesture.
- Move selected ink between pages by dragging, edge scrolling, or Move… . Use a
  recoverable two-page transaction and paired undo/redo; preserve stroke geometry.
- Remove the lasso's artificial 16 × 16 ms completion polling. The separate
  600 ms finger-navigation guard after pen contact/hover remains unchanged.
- Host regressions, Android build/lint and native eraser diagnostics pass.
  Hands-on UI/gesture acceptance is left to the owner; no automation success
  is claimed for the firmware-incompatible UI harness.

## [0.32.0-dev.1](https://github.com/xing-phys/Scriflow/releases/tag/v0.32.0-dev.1) — Compact bookshelf and notebook management (2026-10-27)

- Compact bookshelf text and row spacing, with two list columns in landscape.
  Optional first-page thumbnails have independent portrait/landscape row and
  column counts. Page overview columns are independently configurable too.
- Persistent folders with create, rename, move and remove-folder actions.
  Removing a folder keeps its notebooks at the bookshelf root.
- Deleted notebooks go to Trash, with restore and confirmed permanent deletion.
  Interrupted permanent deletion resumes safely; fully deleted slots can be
  reused. Legacy root deletion preserves the library catalog and other books.
- Compact Settings and a zoom grid with several presets per row. Dialogs and
  popups use flat borders without Material shadows.
- Move the vertical three-dot menu beside the zoom percentage. Put Bookshelf
  last in the tool dock. The time/battery/Wi-Fi area opens Settings and appears
  on the bookshelf, Settings, page overview and transfer screens.
- The add-page tool inserts after the current page and selects the new page.
- Add foreground-only Keep screen on, using Android's window hint. No CPU wake
  lock, wake-up request, or system power-setting changes. Cover/power sleep and
  physical display acceptance still require the MatePad; none is connected.
- Read version-1 catalogs and write version 2 with folders/trash. Earlier APKs
  cannot read the upgraded catalog. Page and editable-backup formats are unchanged.

## [0.31.0-dev.5](https://github.com/xing-phys/Scriflow/releases/tag/v0.31.0-dev.5) — Restore direct native handwriting (2026-10-27)

- Remove dev.4's Java-driven second pen, per-sample surface transforms and
  framebuffer posts, and stroke-end bitmap rendering. Both width modes now use
  Huawei's direct native AHW input, prediction and visible drawing path.
- Restore the point observer, native commit handler, commit matching, pause
  flush and listener attachment verbatim from `0.30.0-dev.3` (`f40ef50`). Keep
  exact capture-width/scale metadata for completed-stroke replay and PDF export.
- Keep page width as the default, using the pre-0.31 native width-fitting rule;
  screen width remains optional. Withdraw the claim that direct native page
  mode has identical pressure response at every zoom. Settings states this limit.
- Keep the rotation-history fix, PDF import/export, existing ink and page format
  5. Increase versionCode to 41 with the same application ID and release key.
  Host/build checks verify the restoration; physical latency remains unmeasured
  because the tablet is not connected.

## [0.31.0-dev.4](https://github.com/xing-phys/Scriflow/releases/tag/v0.31.0-dev.4) — Rotation history and pen width modes (2026-10-27)

Superseded by dev.5 after the owner reported unacceptable writing delay in the
manual page-mode pipeline. The rotation-history fix is retained in dev.5.

- Preserve both previous-view and next-view stacks across screen rotation,
  scoped to the current notebook and stable page identities. Restore logical
  view anchors; recompute fitted views for the new orientation while retaining
  custom zoom percentages. Ordinary scrolling still creates no history entries.
- Add Settings → Pen width when zooming, with **Fixed width on page** as the
  default and **Fixed width on screen** as the alternative. Freeze each stroke's
  width mode and scale at pen-down; changing settings never reshapes existing ink.
- Keep Huawei's original ballpoint and raw pressure/timing. Page mode feeds
  page-space samples to the installed native brush and scales its finished
  framebuffer drawing, avoiding zoom-dependent pressure thresholds. Screen mode
  retains direct native handwriting with a constant native nib width.
- Preserve captured geometry for scrolling, reopening and vector PDF export.
  Save accepted strokes before display cache work; discard partial manual pen
  state during pause, tool changes, and surface recreation.
- Advance versionCode to 40 with the same signing identity and version-5 page
  format. Host suites pass, including rotation history, both width modes,
  native bridge contracts and save/reopen/export. Debug/release lint and builds,
  signed APK verification and native diagnostic compilation pass. The owner
  confirms dev.3 solved the clipping failure. Physical
  confirmation of this new native page-mode bridge remains pending.

## [0.31.0-dev.3](https://github.com/xing-phys/Scriflow/releases/tag/v0.31.0-dev.3) — Handwriting clipping hotfix (2026-10-27)

- Fix “Cannot update notebook ink: Could not clip handwriting to its page,”
  which paused both ordinary and PDF notes after writing or restoring ink.
  Replace the fallible boolean path intersection with a saved Canvas rectangle
  clip. Retain complete pressure geometry and restore the caller's drawing state,
  including when the visible clip is empty.
- Distinguish a drawing pause from a storage failure in the status and error
  dialog. Keep the existing retry and in-memory page retention.
- Add a deterministic host regression that reproduces the exact dev.2 error
  under failed path operations, plus raster clip/state checks and viewport-to-
  storage tests for upgraded notes, new notes, and imported PDF notes. Extend
  the native renderer diagnostic to exercise actual composed viewport clips.
- Advance versionCode to 39 with the same signing identity and page format.
  Host regressions, debug/release lint and APK builds, signed artifact checks,
  and native diagnostic compilation pass. Physical tablet confirmation remains
  pending; no tablet is connected.

## [0.31.0-dev.2](https://github.com/xing-phys/Scriflow/releases/tag/v0.31.0-dev.2) — PDF and ink regression fixes (2026-10-27)

- Fix PDF export failing when PDFBox closed the caller's stream before its final
  flush and file sync, affecting imported PDFs and notes with outlines.
- Replace enlarged fixed-resolution previews with visible PDF tiles rendered
  at the current zoom. Retain the native PDF renderer and current page; bound
  cached pixels and avoid queues of obsolete scroll/zoom renders.
- Retain new strokes' native capture scale and exact native brush width, replay
  their original pressure geometry, and clip completed vectors rather than
  restarting the brush at the visible screen edge. Use the same completed
  outlines for restored writing, scrolling, thumbnails, selection, and export.
- Write version-5 pages with backward reads of versions 1–4. Preserve older
  strokes and their files on open; missing historical capture scale cannot be
  inferred reliably. First edits retain the earlier file as a backup.
- Record previous/next views only for internal link and outline jumps. Scrolling,
  zooming, and ordinary page navigation leave this history alone. Confirm an
  external link's target before opening a browser or another app.
- Import each PDF page once with one durability sync and publish the page index
  once. Use four bounded page writers and wait for all before publication or
  cleanup. Cache compact page geometry/identities and PDF outlines; load links lazily
  with one retained reader. Avoid decoding every saved stroke while opening.
- Advance versionCode to 38 with the same application ID and signing identity.
  31,012 host assertions across 40 suites, both lint/build variants, standalone
  app and native diagnostic compilation pass. Host performance records are separate from pending
  native handwriting, visual sharpness, and timing checks on the tablet.

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
