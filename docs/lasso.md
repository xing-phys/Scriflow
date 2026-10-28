# Lasso selection and cross-page movement

The Lasso tool retains the native AHW drawing path. The supported firmware
exposes `HandPenConfig.PEN_TYPE_DASH`; Lasso selects that brush at a fixed
2.5 viewport-pixel width in black. Normal Pen input keeps its direct native
pipeline. No manual waveform override is introduced.

During MOVE, the firmware draws the trail and the adapter captures primitive
coordinates. There is no application Canvas drawing, hit testing, history command
or file write for each lasso sample. Native samples and standard Android pointer
samples are captured independently; either observer's UP can finish the region.
When both arrive before restoration, native sample geometry is preferred, and
cancellation from either observer wins. Android historical samples are included.
A native saved-track/edit notification is not required.

After UP, native input is disabled and completion runs on the next input-queue
turn, after the observer callback returns. The former 16 ms polling loop, which
could wait up to 256 ms, is removed. The closed outline can be shown while the
serialized writer calculates the selected strokes. Before restoring canonical
ink, the existing native-accumulator check still refuses to reuse an unsettled
widget; the editor replaces it safely while retaining the selection. The
temporary native dash never becomes completed or saved ink. This removes an
artificial wait; it does not establish zero processing or optical latency.

## Selecting a region

The polygon closes explicitly by appending its first coordinate. The visible
outline and hit testing use that same closed path. Hit testing uses an even-odd
interior with the boundary included. Any centreline contact with the region
selects the whole stroke. Segment intersections catch crossings even when both
recorded endpoints lie outside, including concave regions and boundary touches. Collinear/empty
gestures select nothing, and broad bounds reject distant strokes first.

Selection starts on the page first touched by the gesture. A polygon spanning
several pages does not select ink from several pages at once. A path entirely
in a gap uses the centred page for empty-selection feedback. The resulting
single-page group can subsequently move to any page in the notebook.

The original curve remains visible even when no strokes were selected.
It is composed separately from saved page/PDF/thumbnail ink. A small nonmodal
window beside the region shows its stroke count and **Move…**, Copy, Duplicate,
Cut, Delete, Export… and Done. It stays within the viewport and avoids the main palette
when space allows. An empty region says "No strokes selected"; only Done is
available.

## Moving across pages

While the region exists, AHW stays disabled and its Android pointer stream
handles fingers and stylus. Starting inside the curve moves the group. Old
contacts from drawing the lasso itself cannot start a drag, and an accepted
pointer still receives UP/CANCEL while native input is paused.

The group floats under its original grab point without stopping at source-page
edges. Its logical dimensions and pressure geometry stay intact; its screen
scale follows the destination paper's fitted scale. Hold near the top or bottom
edge to scroll through further pages. Edge scrolling uses a bounded ramp every
40 ms while needed. It stops when the pointer leaves the edge band, reaches a
document boundary, or the drag ends.

Selected native pressure paths are cached once for the drag. Nearby page
backgrounds are cached with the selected source strokes excluded; high zoom
uses bounded viewport rendering. Pointer previews and asynchronous page/PDF
loads update the visible background without writing document data per MOVE.

Lift over a destination to save the move. The nearest page is used when the
pointer lies in a page gap. The shared offset fits the complete group inside
that page; it does not clip strokes or shrink them to fit. A destination smaller
than the selected group's bounds rejects the move and retains the original ink.
CANCEL, Back or Activity pause discards an unfinished move. Additional contacts
cannot accidentally commit a jump; a two-finger navigation gesture clears the
selection and hands over to navigation.

**Move…** offers an alternative for distant destinations. Choose a page number,
using the number controls, and move the group to that page's centre. The same
complete-group size check applies. Done or a tap outside clears the region for
ordinary scrolling or drawing another lasso. With no region active, tapping the
selected Lasso icon offers Paste and Select all.

Finger navigation and finger dragging still obey palm rejection: they are
blocked while the pen is down/hovering and for **200 ms (0.2 seconds)** after the last pen
contact/hover event, including hover exit. Continuing to hover keeps them
blocked. This interval is independent of closing the lasso on UP.

## Saving, recovery and history

`NotebookLasso` holds immutable selection/clipboard snapshots. Edits reject a
selection from another page or a changed stroke snapshot. Moving keeps stroke
IDs, pressure, timing, colour and captured width/scale; duplicate/paste creates
new IDs. Copy preserves the original samples even if the source later moves or
is deleted. The private clipboard lasts only for the app process and is not
shared with Android apps. Paste does not scale ink to fit a smaller page.

A same-page move uses the existing per-page save/history path. A cross-page
move validates both participants and publishes a recovery journal before
changing either saved page. The receiving page is saved before the outgoing
copy is removed. Interrupted publication is completed on retry or process
restart, preserving the entire moved group. Unexpected page changes or a corrupt
journal fail closed instead of overwriting unrelated writing. This is recovery
from application interruption, not a claim of hardware power-loss durability.

One cross-page move is one linked Undo/Redo action on both pages. Undoing from
either participant restores both sides together; redo reapplies both. Later
edits on either page must be undone first, so a linked action cannot overwrite
newer writing. The pairing follows stable page slots through page reordering.
Undo/Redo history remains process-local, with the existing per-page limit;
the recovery journal survives process death, but editing history does not.

No-op same-page moves preserve redo. Selecting, copying and deselecting do not
change history or saved files. Navigation, tool changes, Back and history actions
clear the selection; Activity pause clears it too. No page or editable-backup
format migration is required for cross-page movement.

## Verification and tablet acceptance

Host tests cover lasso geometry, dots, boundaries, sparse concavity crossings,
explicit closure without native saved-track notifications, both observer orders,
cancellation precedence, stale/paused pointer streams and preview isolation.
Cross-page geometry checks include mixed paper sizes, zoom, pan, page gaps,
grab anchoring, distant destinations, oversized rejection and bounded edge
scrolling. Real-file transfer checks cover identities/styles, paired history,
intervening edits, page reordering, retained queued ink, interrupted publication
and restart recovery.

Earlier lasso interaction was accepted on the tablet in 0.29.0-dev.2. That
historical feedback does not establish physical acceptance of current cross-page
movement, edge-scrolling performance or closure timing. Automated/synthetic
checks do not measure tip-to-display latency or prove the absence of optical
refresh artifacts. See the [device record](device-testing.md) for current results.

On disposable notes, check:

1. Draw open-ended loops around lines and dots. Confirm the curve closes on
   lift; crossing strokes remain unselected and empty regions give feedback.
2. Drag with both stylus and finger across adjacent pages, page gaps, and pages
   of different sizes/zoom. Hold at both screen edges to reach distant pages.
3. Try Move… to a distant page, a group that exactly fits, and a destination too
   small for the group. Confirm no clipping, lost ink or silent scaling.
4. Cancel during a drag, add a second contact, press Back, and background/resume.
   No unfinished preview should change saved ink.
5. Undo/Redo from either participant, including after later ink edits. Close and
   reopen, then inspect PDF/backup output to confirm the saved destination.
6. Compare blank/lined/PDF backgrounds and dense notes for lag, flashes, ghosting
   and stalls. Repeat immediate writing, erasing and navigation with normal pen
   pressure; live handwriting must keep its established native path.

## Export selected ink (0.33 development)

**Export…** prepares SVG, PNG, JPEG, WebP, or PDF from the selected pressure
outlines. The crop excludes paper, figures, and unselected strokes. Vector
formats retain vector ink; PNG/WebP preserve transparency. Save through the
system picker; cancelling leaves the prepared file available for retry. See
[figures and workspace](figures-and-workspace.md) for details.
