# Lasso selection — 0.29.0-dev.2

The Lasso tool retains the native AHW drawing path. The supported firmware
exposes `HandPenConfig.PEN_TYPE_DASH`; Lasso
selects that brush at a fixed 2.5 viewport-pixel width in black. Pen and eraser
retain their existing settings. No manual waveform override is introduced.

During MOVE, the firmware draws the trail and the adapter captures primitive
coordinates. There is no application Canvas drawing, invalidation, hit testing,
history command, or file write. Native samples and standard Android pointer
samples are captured independently; either observer's UP can complete selection.
When both arrive before restoration, native sample geometry is preferred, and
cancellation from either observer wins. Android historical samples are included.
No native edit/track-commit notification is required. This replaces dev.1's
eraser-style completion gate, which made selection depend on a saved-track event.

After UP, native input is disabled and a short bounded check lets the widget's
private track accumulator settle (16 ms steps, at most 16 checks). A persistently
unsettled widget is retired through the existing replacement path; the selection
and its original outline survive that replacement. The temporary native dash is
never added to completed ink. Pause and tool changes discard partial gestures.

Hit testing runs on the serialized document writer after UP. The polygon closes
explicitly by appending the start coordinate, and the visible outline uses that
same closed path. Hit testing uses an even-odd interior with its boundary included. Whole
stroke centrelines must be enclosed. Segment intersections are checked, including
sparse segments crossing concave regions; checking sample vertices alone would
select those incorrectly. Collinear/empty gestures select nothing. Broad bounds
reject distant strokes first. Selection currently targets the page where the
gesture first touches paper; it does not span pages. A path entirely in a gap
uses the centered page for empty-selection feedback.

The original closed curve stays visible, including when no whole strokes were
selected. It is composed into the native widget's owned viewport background,
separately from page/PDF/thumbnail rendering. A small nonmodal window beside the
region displays the stroke count and Copy, Duplicate, Cut, Delete and Done.
It stays within the viewport and avoids the main palette when space allows.
An empty region says "No strokes selected"; only Done is actionable.

While the region exists, AHW stays disabled and its owned Android pointer stream
handles both fingers and stylus. A pointer starting inside the curve moves the
group. Old Android contacts from the lasso itself are ignored if native lift
arrived first; an accepted drag still receives its UP/CANCEL while input is
paused. Paper and unselected ink are cached in a bitmap, and pressure outlines of
selected strokes are generated once at drag start. MOVE translates those cached
paths and the closed outline, coalescing surface posts to a 32 ms interval. No
native brush replay, hit testing or storage write occurs per drag MOVE. No frame
timer runs while stationary. UP commits one shared offset clamped to the page;
CANCEL, extra contacts, Back or Activity pause discard an unfinished move.

Done or a tap outside clears the region. Clear it before finger scrolling or
drawing another lasso. With no region active, tapping the selected Lasso icon
offers Paste and Select all. There are no marching ants, modal Move step, or
manual display-waveform changes.

`NotebookLasso` holds immutable selection/clipboard snapshots. Edits reject a
selection from another page or a changed stroke snapshot. Move retains object
IDs and original stroke order; duplicate/paste creates new IDs. Copy preserves
the original samples even if the source later moves or is deleted. The clipboard
stays in the process-wide session and is neither persisted nor shared with Android
apps. Paste does not scale ink to fit a smaller page.

`NotebookEdits`, `NotebookDocument`, `NotebookBook`, `NotebookLibrary`, and
`NotebookSession` route each edit through the existing per-page history and
validated snapshot publication/retry path. No schema or migration is needed.
No-op moves preserve redo. Clipboard/select operations do not create history
entries or write page data. Navigation, tool changes, Back, and history
actions clear selection; Activity pause also clears it.

## Verification

`./scripts/test.sh` includes lasso geometry, dots, boundary contact, sparse
concavity crossings, self-crossing loops, move clamping, pressure/time/ID
preservation, stale selections, grouped undo/redo, fresh/repeated copy IDs,
oversized clipboard rejection, cross-page commands, exact disk preservation
on selection, reopening, and failed-delete recovery with retained history.
`NotebookLassoInteractionTest` also exercises explicit closure without native
commit callbacks, both pointer observer orders, native-sample preference,
cancellation precedence, capture reuse/growth, finger/stylus pointer ownership,
late/paused-stream delivery, tap jitter, mixed-contact cancellation, drag previews without disk changes,
one-edit release, outline/ink offsets, empty feedback and bounded action placement.
The eraser retains its separate temporary-track ordering regressions.

Host tests, standalone APK build, Gradle debug/release assembly, and lint passed
on 2026-10-26. The owner reported smooth native dashes and working page management
in dev.1, but unusable selection after lift. After testing the signed dev.2 APK,
the owner confirmed that it works well and authorized release. This general
acceptance does not establish measured drag latency or individual results for
every operation; see the [device record](device-testing.md).

## Tablet acceptance

1. Install the development APK as an update with the matching signing identity.
   Use a disposable test page and preserve backups of important notes.
2. Write several strokes, including a dot, a long line, and pressure variation.
   Circle a subset in Lasso, deliberately leaving a gap at the end. Confirm the
   curve visibly closes on lift and shows nearby actions. Crossing strokes stay
   unselected. Circle blank space and confirm the closed empty region gives feedback.
3. Drag inside the curve using a finger, then the stylus. Confirm the preview
   follows movement and the group lands together,
   clamps at all page edges, and retains thickness/pressure appearance. Check
   Undo/Redo restores the whole action in one step.
4. Duplicate, copy, cut, delete, and paste. Copy before moving/deleting the source;
   paste onto another page and notebook. Repeated paste must produce independent
   ink. Undo separately on source and destination pages.
5. Tap outside, then scroll; switch tools/pages, press Back, and background/resume
   during a partial lasso. Confirm no dashed trail is saved or exported, no
   unfinished move changes ink, and writing resumes with the previous pen width.
   Add a second contact during a drag and verify cancellation without a jump.
6. Check blank/lined paper, horizontal/vertical tool strips, tool movement, and
   immediate switching between writing, lasso and scrolling. Physical acceptance
   must assess lag, ghosting, flashes, and stalls on the supported firmware.
