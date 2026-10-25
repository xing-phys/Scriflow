# Pen thickness — 0.16

Tap the selected Pen icon to open **Pen thickness**. If Eraser is selected,
the first tap selects Pen; tapping Pen again opens its choices. The compact
strip keeps its existing size, orientation, position, and action count.

| Choice | Width in stored page pixels |
| --- | ---: |
| Extra fine | 1 |
| Fine | 2 |
| Medium | 4 |
| Broad | 6 |
| Extra broad | 10 |

Medium preserves the former default. These are logical page units rather than
millimetres. The dialog shows line samples at the page's fitted scale, with a
thicker border around the current choice. Selecting a row applies and closes
the dialog. Cancel/Back keeps the existing choice. A small line below the pencil
icon previews the selected width, capped to fit the compact button.

The chosen width is saved in Activity preferences and applies across notebooks
and process restarts. Unknown or invalid saved values fall back to Medium.
Selecting a width does not edit existing strokes, clear Redo, or create an
Undo command. Each stroke retains its own width through saving, erasing,
undo/redo, scrolling, reopening, thumbnails, and vector PDF export.

## Native integration and preservation

The chooser pauses native input and waits behind completed-stroke saves before
opening. It uses static white controls with no ripple, dimming, or window
animation. The paused surface is reused when closing the chooser; thickness
selection alone does not request a canonical page repaint.

Before enabling input, the bridge calls the same firmware
`FBHandSurface.setAhwPen` API as before, supplying the selected width instead of
the former constant. The editor first fits the logical width to its composed
viewport. The bridge applies its widget scale, and the viewport splitter
converts completed strokes back into page coordinates. Existing restored tracks
continue to use their own stored style.

The UP fallback previously reconstructed every completed stroke at width 4.
Each new pen-DOWN now captures an immutable style, so a delayed native commit
or subsequent pause cannot assign the next pen's width to the preceding stroke.
The ordinary native-commit path continues to read the firmware track's style.
No per-MOVE UI updates, native refresh flags, permissions, storage format, or
proprietary code were added. Version-1 notebook files remain compatible.

## Verification

`./scripts/test.sh` passes 17,749 assertions. The 357 new assertions cover
preference fallback, delayed-completion style capture, real-file mixed-width
save/reopen, undo/redo, erasing the edge of a broad stroke, viewport fitting
at half/normal/double scale, and vector PDF width output. Existing storage,
scrolling, export, clipping, toolbar-layout, and idle-status tests also pass.
The chooser and pen previews are checked in a rendered layout illustration,
not an Android screenshot. The APK is built offline for API 29 and signed with
the existing local key.

Tablet acceptance remains necessary for native appearance and latency:

1. Install `build/ink-notebook-0.16.apk` over the existing app; do not uninstall.
2. Tap the selected Pen icon. Write a dot, curve, and word at each thickness.
   Confirm visible differences during writing and after lifting the pen.
3. Change width immediately after a stroke. Confirm its original width remains.
4. Check Eraser, Undo/Redo, scrolling between pages, reopening, and PDF export
   with mixed widths. The chosen pen should remain selected after reopening.
5. Cancel the chooser and switch strip orientation; confirm the current width
   remains and ordinary controls retain their static touch appearance.
