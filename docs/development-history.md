# Development history and verification records

These notes were moved from the original README. They record what was implemented
and verified at each milestone, including checks that were still pending **at
that time**. Later device results may supersede earlier pending or failed checks;
see the [current device record](device-testing.md) and
[refresh acceptance for 0.26](fast-refresh.md). They do not certify a newly built
public-release APK. Use the [release checklist](releasing.md) for that.

Historical app names, signing descriptions, and APK paths refer to the artifacts
used at that milestone. The current app is Scribblow; current build and signing
instructions are in [Building](building.md). Diagnostic commands below apply
only to development builds.

## Automated and on-device verification


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
[editing milestone and installation checks](notebook-editing.md) and
[the edit refresh fix](notebook-edit-refresh.md).

Version 0.9 removes Material button ripples and animations and keeps toolbar
appearance static while saves/edits run. Availability remains enforced in click
handlers and exposed to accessibility. Ordinary page and paper changes reuse the
bound bitmap and native surface, posting the new content together. The button
flash improvement and new page UI still need physical tablet acceptance. See
[multiple-page milestone](notebook-pages.md).

Version 0.10 adds the library home screen and keeps the original notebook at its
existing path as **My Notebook**. New notebook creation, selection, and renaming
run on the same background worker as writing. The app builds offline with the
same signing key; the new library UI still needs tablet acceptance. See
[notebook library milestone](notebook-library.md).

Version 0.11 keeps one native writing surface fixed on screen. Finger dragging
uses cached page previews; after release, the exact visible page portions become
native editable tracks again. Pen input waits until all visible content has
loaded. Completed strokes are saved to explicit page IDs, including strokes
that cross a page gap. The new hardware path still needs tablet acceptance.
See [continuous pages](continuous-pages.md).

Version 0.12 adds a thumbnail overview and direct page-number jumps. Preview
reads and rendering share the serialized worker, with one request in flight and
a 12-bitmap cache. Closing the overview resumes the existing native surface;
choosing a page uses the same validated window read as page navigation. The
13,347 host assertions pass and the signed APK builds offline; the new overview
still needs tablet acceptance. See [page overview and jumps](page-overview.md).

Version 0.13 adds whole-notebook PDF export through the system save picker.
Preparation reads one page at a time on the notebook worker, then a separate
file worker copies the completed PDF to the selected location. Export errors
remain separate from notebook save errors. All 16,902 host assertions pass;
the production writer's blank/lined sample pages pass structural and visual
checks. See [PDF export](pdf-export.md) for behavior and tablet checks.

Version 0.14 replaces the editor's text toolbar with draggable vector-icon
controls. Native pen clipping excludes the palette, and toolbar contacts pause
handwriting until all contacts lift. The selected Pen/Eraser border changes
without animating the other controls. All 16,947 host assertions pass, including
physical screen-to-surface exclusion checks. Icons and both selected states were
visually inspected; window movement, pen protection, and refresh behavior still
need tablet acceptance. See [floating tools](floating-tools.md).

Version 0.15 shrinks the controls to a single rotatable strip and replaces the
large status row with a 24 dp header. Routine save counts and the redundant
Overview label are removed. Device-status events coalesce until pen idle, and
Wi-Fi is observed passively even when a VPN is active. All 17,392 host assertions
pass, including 445 new strip-layout and status-coalescing checks. Both
orientations were visually inspected; live status and pen behavior still need
tablet acceptance. See [compact tools and status](compact-tools-status.md).

Version 0.16 adds five pen thicknesses through the selected Pen icon, with a
remembered choice and line samples. Widths use logical page units, preserving
their proportions when scrolling, reopening, or exporting. Completed-UP fallback
capture now retains the style from that stroke's DOWN. All 17,749 host assertions
pass, including 357 mixed-width, durable editing, fitting, and export checks.
The signed APK builds offline; live native widths still need tablet acceptance.
See [pen thickness](pen-thickness.md).

Version 0.17 replaces the thickness dialog with a compact slider anchored to the
Pen button. The toolbar remains visible; the bar fits beside either orientation
and stays within the writing area near screen edges. Widths between the former
presets are now supported. Input stays paused until all slider contacts lift,
and outside taps cancel without drawing on the page. All 18,474 host assertions
pass; the signed APK builds offline. Slider placement is visually checked in a
rendered illustration; native touch, refresh, and writing still need tablet
acceptance. See [pen thickness](pen-thickness.md).

Version 0.18 fixes slider releases being discarded during popup removal. The
selected width is now final before closing starts, so a synchronous cancellation
cannot overwrite it. A regression reproduces the reported 6.0-to-1.0 failure
with the old logic and passes after the fix. All 18,517 host assertions pass,
including 43 popup-completion checks. The signed APK builds offline with the
existing key; applying widths still needs verification on the tablet.

Version 0.19 adds a separate 4–240 eraser-width slider and a temporary grey trail.
Preview segments draw directly into the bound native buffer with bounded damage
updates that exclude the floating controls. A single canonical post removes the
trail and touched strokes after the erase finishes; no preview enters the saved
note or PDF. Radius-aware mapping preserves wide-brush hits near page edges and
gaps. All 19,730 host assertions pass; the signed APK builds offline with the
existing key. The grey trail's optical appearance, latency, and cleanup still
need tablet acceptance. See [eraser widths and preview](eraser-width-preview.md).

Version 0.20 moves the eraser preview onto Huawei's native AHW handwriting path.
It replaces Android touch sampling, Canvas batching, and manual refresh requests
with a temporary grey native stroke. Erasure waits for both UP and native track
completion; the canonical page update removes the preview and touched ink in
one post. Preview tracks are excluded from saving and history. The callback
ordering, cancellation, width capture, and real-file erase/undo/redo checks pass
(19,035 host assertions). On-device blank/lined restoration and edit regressions
pass; the user confirms the width-36 grey trail is smooth and clears correctly.
Very fast passes at the maximum width 240 can still show visible lag; normal
width-36 erasing is confirmed smooth. See [native eraser preview](eraser-width-preview.md)
for device verification and the accepted wide-brush limitation.

Version 0.21 fixes scrolling previews and PDF exports looking thicker and losing
pressure variation. Pressure and event timing were already saved; the old
renderers ignored them. Both now reuse Huawei's native brush outlines, also used
for overview thumbnails. PDF outlines stay vector-based and use lossless stream
compression. All 19,820 host assertions and native geometry/restoration checks
pass. The user confirms scrolling and newly exported PDFs both match the writing
canvas. See [consistent ink rendering](consistent-ink-rendering.md).

See [milestone design and acceptance](notebook-milestone.md), the
[device test record](device-testing.md), and the retained
[ink diagnostics guide](ink-probe.md).

Version 0.22 adds the foundation for future document types: persistent page/stroke IDs,
a read-compatible v1-to-v2 migration, editable backup/restore, a resumable restore transaction,
an editor/device interface, explicit coordinate transforms, independent render/export workers,
and standard Gradle tooling. It preserves the live Huawei pen and eraser path. The remaining
PDF/Markdown/AI model and engine choices are documented rather than represented by unsupported
placeholder objects. See [architecture](architecture.md) for ownership, format, limits,
and the next extension steps.

Version 0.27 adopts the **Scribblow** name, a flowing-S launcher icon, and
Scribblow PDF credits. The application ID, signing key, notebook formats,
legacy page identities, and handwriting path are retained. Install it as an
update over the existing app. The standalone build now writes
`build/scribblow-debug.apk`; older versioned APK filenames in the development
records refer to the original artifacts. See [branding](branding.md).
