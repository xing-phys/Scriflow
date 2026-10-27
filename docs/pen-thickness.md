# Pen thickness and pressure

Version 0.31.0-dev.5 restores direct native handwriting for both choices in
**Settings → Pen width when zooming**:

- **Fixed width on page** (default): fit the selected logical width to the
  viewport before enabling the native pen, as in 0.30.0-dev.3. The native brush's
  pressure response can vary across zoom; identical response is unresolved.
- **Fixed width on screen**: configure a fixed nib width in the composed screen
  viewport. Completed ink retains what was written and scales with the page
  afterward.

Both modes use the original direct native AHW pipeline. The separate pen driven
from Java callbacks in dev.4 caused unacceptable delay and has been deleted.
Changing settings affects future strokes; stored capture geometry remains
unchanged. See [direct handwriting restoration](native-writing-restoration.md).

Version 0.21 uses the native pressure brush for scrolling, thumbnails, and PDF
export, correcting the previous fixed-width rendering. A stored width is the
brush setting; pressure can make visible ink thinner. Existing strokes already
contain the required pressure/time samples. See [consistent ink rendering](consistent-ink-rendering.md).

Version 0.19 extends this same compact slider to Eraser with an independent,
larger width range. See [eraser width and grey trail](eraser-width-preview.md).

Tap the selected Pen icon to open a **224 × 48 dp width bar** beside the button.
If Eraser is selected, first select Pen, then tap it again to open the slider.
The toolbar stays visible in its current position and orientation. The slider
opens below a horizontal toolbar or beside a vertical one, using the opposite
side when necessary near the screen edge.

Slide the point left/right to adjust the width. Its small stroke sample and
number update while dragging. **Release to apply; the bar stays open.** You can
adjust it repeatedly without reopening it. Tapping the track selects directly.
An outside touch or Back closes the bar and keeps the last applied width;
cancelling an unfinished adjustment restores that last applied value. Starting
to write outside the bar closes it and accepts that first stroke normally.
No additional confirmation button is needed.

The range is **1.0 to 10.0** in **0.1 steps**, measured in the selected page/screen
units rather than millimetres. **4.0** preserves the original default. All five choices
from 0.16 (1, 2, 4, 6, 10) restore unchanged, and intermediate widths now persist
too. The existing `penWidth` preference is retained across notebooks and process
restarts; invalid/out-of-range values fall back to 4.0. Selecting a width affects
new writing only. It does not edit old strokes, create an Undo command, or clear
Redo. Each stroke keeps its width in storage, undo/redo, scrolling, reopening,
thumbnails, and PDF export. No file format change is required.

## Native integration

Opening the bar briefly pauses handwriting and waits behind completed-stroke
saves, then writing resumes with the bar visible. The popup occupies only its
compact bounds, which are excluded from native ink. It does not place an input
shield over the writing area: outside contacts continue to the editor while
closing the bar, so a new stroke need not be repeated.

Contact with the slider pauses native writing until the adjustment ends. The
slider tracks one pointer; other fingers cannot change its width. If that
pointer lifts first, its value freezes until the remaining contacts lift. A
successful release applies and saves the value, resumes native writing, and
leaves the bar open. Back during contact waits for contact completion before
closing and cancels only the unfinished adjustment. An Activity pause closes
the popup immediately with native input already disabled.

Only the bar is invalidated while adjusting. There is no page, toolbar, or
preference update per MOVE, and the controls have no ripple, pressed inversion,
shadow, dimming, or window animation. Applying changes the selected live pen and
reuses the paused native tracks; changing thickness alone does not repaint the
document. The popup preserves the editor's window focus/fullscreen state.

Version 0.18 fixes a reentrant popup-closing bug reported on the tablet: the bar
dragged and closed, but reopening still showed 6.0 and the live pen stayed at
the old width. In 0.17, `finish(selectedWidth)` started removing the popup before
locking its result. A removal-generated CANCEL could reenter `finish(null)` and
deliver cancellation instead of the released width. The old completion code
reproduces the reported 6.0-to-1.0 failure in the host test.

The close coordinator introduced in 0.18 locks its first decision before calling
window removal and delivers it exactly once. Later removal-generated cancellation
or duplicate dismissal cannot replace it. In the current persistent bar, each
completed adjustment becomes the baseline for the next one; closing or cancelling
a later unfinished adjustment keeps that most recently applied width. Android 10's
[popup removal order](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-10.0.0_r47/core/java/android/widget/PopupWindow.java#L1875-L1984)
and [child-touch cancellation](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-10.0.0_r47/core/java/android/view/ViewGroup.java#L2722-L2739)
support this callback sequence. This is source analysis and a reproduced event
sequence, not a captured callback trace from the tablet.

Before dev.4, the editor fitted logical width to its composed viewport, and the
native bridge called `FBHandSurface.setAhwPen` with the corresponding width.
The two current routes are described above and in the capture-geometry note.
Completed native tracks retain their recorded style. The completed-UP fallback
captures immutable style at DOWN, so a later width selection cannot change the
preceding stroke. No refresh flags, permissions, or proprietary code were added.

## Historical slider verification (0.18)

`./scripts/test.sh` passes **18,517 assertions**, including 43 completion checks
for synchronous/deferred removal, duplicate cancellation, external dismissal,
accessibility selection, and multi-contact release. These exercise the same
completion coordinator used by the popup. The 607 existing slider checks cover
endpoint clamping, all 91 width positions, remembered intermediate widths,
outside cancellation, multi-contact ownership, and placement at screen edges
and in small windows. The 475 pen-style checks exercise fractional widths through
real-file saving/reopening, undo/redo, broad-stroke erasing, half/normal/double
viewport fitting, and vector PDF export. All existing notebook, scrolling,
export, toolbar, and status checks also pass.

The slider is visually inspected in a rendered layout illustration using the
production placement code and vector symbols. This is not an Android screenshot.
The APK builds offline for API 29 with the existing signing key.

Tablet checks for the current persistent bar remain necessary:

1. Install the current development APK with the matching signing identity over
   the existing app; do not uninstall.
2. Starting from the previous width, select 1.0 and release. Confirm the bar stays
   open at 1.0. Adjust it again to 10.0, then to an intermediate width. Close and
   reopen it to check the last applied value. Write dots and curves after each
   selection; new strokes should have different widths.
   Existing strokes should retain their own widths. Check after an app restart too.
3. Tap outside or press Back while open. Confirm the most recently applied
   width remains. With the bar open, begin a new stroke
   outside it and confirm the first stroke is accepted once. Try two fingers and
   confirm input resumes only once both lift, using the original controlling
   finger's final value.
4. Move the toolbar near each screen edge and switch its orientation. The bar
   should remain visible and reachable, with static toolbar buttons during drag.
5. Check reopening, scrolling, Eraser, Undo/Redo, and PDF export with mixed widths.
