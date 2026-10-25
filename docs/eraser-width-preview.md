# Eraser width and grey trail — 0.19

Select Eraser, then tap its selected icon again to open the compact width bar.
Drag or tap its track, then release to apply and close. Tap outside or press
Back to cancel. The existing popup-removal cancellation fix is shared by both
tools. The strip's size, orientation toggle, and movement stay the same.

| Tool | Width range | Step | Default |
| --- | ---: | ---: | ---: |
| Pen | 1.0–10.0 | 0.1 | 4.0 |
| Eraser | 4–240 | 1 | 36 |

Widths are logical page units, not millimetres. Eraser width is its full
diameter; the default matches the former fixed radius of 18. The eraser has its
own preference, leaving the saved pen width unchanged. The width bar uses a grey
circle sample and a compact integer label for Eraser. Both tool choices survive
reopening and notebook changes.

While the stylus is down, a grey stroke follows its erasing path with the
selected diameter and round ends. The page remains unchanged in the document
model until release. On UP, every touched whole ink stroke is removed using the
existing durable erase/undo operation. The trail and touched ink disappear in
the same canonical page update. Unaffected strokes remain. Undo restores the
removed ink; Redo removes it again. A no-hit gesture clears only the trail.
Cancellation discards the partial gesture and restores the unedited page.
The grey trail is never an ink track, history entry, saved stroke, or PDF element.

## Drawing and preservation

Normal pen writing still uses the established firmware AHW path. Eraser keeps
AHW disabled and receives ordinary stylus events. Each DOWN captures its eraser
diameter; changing preferences cannot alter a gesture in progress. Historical
MOVE samples are included in both the captured path and preview. Pending segments
are batched at 16 ms; an UP flushes its final segment before submitting the edit.

The preview uses the firmware's public `lockActiveCanvas`/`unlockActiveCanvas`
methods to paint only new grey segments into the existing native buffer. It then
calls `FBHandSurface.invalidate(left, top, right, bottom, mode)` using the widget's
current `ViewAPI.getUpdateMode`. This follows the locally inspected firmware's
active-buffer drawing pattern without choosing a new refresh-mode number.
Native inspection also confirms left/top/right/bottom region arguments: the
implementation clamps right/bottom to surface extents and rejects inverted
bounds before forwarding the update.
Round-cap damage includes a two-pixel antialias fringe and is clipped to the
physical surface. Both Canvas clipping and damage-region subtraction protect
the floating controls. No page composition, track replacement, file write, or
toolbar/status update occurs per eraser MOVE.

On release, the preview stays visible while the worker applies and saves the
erase. `setTrackList` then paints the bound paper and remaining ink together;
the paper is not rebound to produce a separate empty frame. A visible preview
prevents the old clean/no-hit redraw shortcut. Cancel, pause, tool change, and
surface shutdown remove queued preview work. Cancellation and subsequent
canonical restoration clear already-painted preview pixels. An error retains
the app's existing Not saved/Retry behavior.

Page mapping clips eraser centres against each visible page expanded by the
eraser radius. The centres remain outside page bounds where appropriate:
clamping them to a page edge would distort the visible footprint and turn a
near miss into an erase. This also lets a wide brush centred in the page gap
touch ink near both page edges. Stored ink remains strictly within page bounds;
only the transient eraser paths may have outside coordinates.

## Verification

`./scripts/test.sh` passes **19,730 assertions**. The 1,179 new eraser checks cover
all selectable widths, independent settings, the release/removal cancellation
regression at broad widths, page-gap and margin hits, viewport scaling, native
damage coverage/exclusion, and real-file erase/reopen/undo/redo. Existing page
mapping tests now check radius-expanded eraser bounds while retaining strict
stored-ink bounds. Existing pen, storage, export, slider, and status checks pass.

The API 29 APK builds offline using the existing signing key. A synthetic
before/during/after illustration is rendered using the real edit model and tool
symbols. It is not a tablet screenshot. Native grey appearance, preview latency,
display damage, and simultaneous optical cleanup cannot be established offline.

Tablet acceptance:

1. Install `build/ink-notebook-0.19.apk` over the existing app; do not uninstall.
2. Select Eraser, tap it again, choose 36, and release. Reopen its slider and
   verify 36. Check the pen retains its own previous width.
3. Erase across a few disposable ink strokes. Confirm a grey round-ended trail
   follows while down. On release, the touched whole strokes and trail should
   disappear together, leaving other writing and the toolbar steady.
4. Repeat at small and large diameters, including a dot, a no-hit gesture,
   a curved path, and a broad path through the gap between two visible pages.
5. Check Undo/Redo, return to Pen, scroll, reopen, and export PDF. There should
   be no saved grey trail and the two tool widths should remain independent.
