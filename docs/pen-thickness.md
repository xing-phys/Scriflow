# Pen thickness — 0.18

Tap the selected Pen icon to open a **224 × 48 dp width bar** beside the button.
If Eraser is selected, first select Pen, then tap it again to open the slider.
The toolbar stays visible in its current position and orientation. The slider
opens below a horizontal toolbar or beside a vertical one, using the opposite
side when necessary near the screen edge.

Slide the point left/right to adjust the width. Its small stroke sample and
number update while dragging. **Release to apply and close** the bar; tapping
the track selects directly. An outside tap or Back cancels without changing
the saved width. No additional confirmation button is needed.

The range is **1.0 to 10.0** in **0.1 steps**, measured in stored page units
rather than millimetres. **4.0** preserves the original default. All five choices
from 0.16 (1, 2, 4, 6, 10) restore unchanged, and intermediate widths now persist
too. The existing `penWidth` preference is retained across notebooks and process
restarts; invalid/out-of-range values fall back to 4.0. Selecting a width affects
new writing only. It does not edit old strokes, create an Undo command, or clear
Redo. Each stroke keeps its width in storage, undo/redo, scrolling, reopening,
thumbnails, and PDF export. No file format change is required.

## Native integration

Opening the bar pauses handwriting and waits behind completed-stroke saves.
A transparent popup above the native surface paints only the compact bar. Its
input shield covers the writing area, consuming outside contacts until their
final UP. The slider tracks one pointer; other fingers cannot change its width.
If that pointer lifts first, its value freezes until the remaining contacts
lift. Back during contact also waits before dismissing. An Activity pause closes
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

The completion coordinator now locks its first decision before calling window
removal and delivers it exactly once. Later removal-generated cancellation or
duplicate dismissal cannot replace it; an actual outside/Back/system cancellation
before release still leaves the width unchanged. Android 10's
[popup removal order](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-10.0.0_r47/core/java/android/widget/PopupWindow.java#L1875-L1984)
and [child-touch cancellation](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-10.0.0_r47/core/java/android/view/ViewGroup.java#L2722-L2739)
support this callback sequence. This is source analysis and a reproduced event
sequence, not a captured callback trace from the tablet.

As in 0.16, the editor fits logical width to its composed viewport, and the
native bridge calls `FBHandSurface.setAhwPen` with the corresponding width.
Completed native tracks retain their recorded style. The completed-UP fallback
captures immutable style at DOWN, so a later width selection cannot change the
preceding stroke. No refresh flags, permissions, or proprietary code were added.

## Verification

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

Tablet acceptance remains necessary:

1. Install `build/ink-notebook-0.18.apk` over the existing app; do not uninstall.
2. Starting from the previous width, select 1.0 and release. Reopen the slider:
   it should show 1.0. Repeat at 10.0, then at an intermediate width. Write dots
   and curves after each selection; new strokes should have different widths.
   Existing strokes should retain their own widths. Check after an app restart too.
3. Tap outside or press Back while open. Confirm the previous width remains and
   no extra stroke appears. Try two fingers and confirm input resumes only once
   both lift, using the original controlling finger's final value.
4. Move the toolbar near each screen edge and switch its orientation. The bar
   should remain visible and reachable, with static toolbar buttons during drag.
5. Check reopening, scrolling, Eraser, Undo/Redo, and PDF export with mixed widths.
