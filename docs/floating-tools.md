# Floating icon tools - 0.14

## Use

The editor now has a compact floating palette instead of a full-width text
toolbar. Drag the **six-dot grip** to move it anywhere within the writing area.
It follows your finger or stylus without snapping or flinging, and stops where
you release it. The position is remembered between notebooks and app sessions.
The page/save status line remains above the writing area.

The default palette has two rows:

| Row | Controls, from left to right |
| --- | --- |
| Top | Move grip, Pen, Eraser, Undo, Redo, three-dot menu |
| Bottom | Notebook library, previous page, next page, add page, page overview, paper |

Every control has an original scalable vector symbol, a white rounded square,
and a thin black outline. The selected **Pen** or **Eraser** has a **3 dp border**;
the unselected tool has a **1 dp border**. There are no check marks or labels in
the palette. Accessibility names identify each control and the selected tool.
The paper icon shows a blank or lined sheet matching the context page.

Only the grip moves the palette; tools still act on a tap. Moving outside a
button cancels its click. Multiple contacts cancel a button click rather than
activating an unintended tool. The grip tracks its original pointer and freezes
if that pointer lifts before the remaining contacts. Ink resumes after every
contact lifts or the stream is cancelled. Clamping keeps the controls within
the visible page area on the MatePad Paper, including after layout changes.

The three-dot menu includes **Reset tools** to return to the initial top-center
position, as well as the existing page information and **Export PDF**. The grid
opens the existing overview and jumps. Closing dialogs brings the palette back
at its saved location. The palette is hidden when leaving the editor or when a
save/native error pauses writing.

The old toolbar's reserved height is returned to the writing viewport. Stored
page dimensions, vectors, paper, undo history, and fractional scroll anchors are
unchanged. A taller viewport can show more of the existing continuous notebook.
Floating controls are excluded from exported PDFs.

## Display and native input

`NotebookSymbols` defines shared, font-independent paths. `NotebookIconDrawable`
draws them on Android, while host preview helpers use the same paths for SVG
visual checks. No SVG runtime, icon font, network dependency, or raster asset is
needed. Buttons have 48 dp touch targets and 26 dp symbols.

`NotebookIconButton` draws its appearance explicitly. Pressed, hovered, focused,
and temporary busy states have no animated background, elevation, or inversion.
Only a changed selected-tool border or paper symbol invalidates that control.
Button UP dispatch is synchronous and never sets a pressed state, so its action
is queued before the toolbar gesture ends. Unavailable actions remain logically
blocked and exposed to accessibility without changing the pixels after saves.

The native `HWEditView` has top Z order, so same-window Android overlays would
sit beneath its surface. `NotebookFloatingTools` uses a nonfocusable
[PopupWindow](https://developer.android.com/reference/android/widget/PopupWindow)
above it, with no dimming, shadow, or transition. Outside contacts reach the page.
The palette is not re-laid out on routine ink readiness/save notifications.
Only active dragging moves its window, at bounded intervals with an exact final
position. Position persistence happens when the grip gesture ends.

Read-only examination of this tablet's retained framework and native code found
`FBHandSurface.setClip(Path)` is a public, whitelisted API also used by native
Notes. Its persistent initial clip tests native pen-DOWN coordinates before
delivery. The bridge installs a full-surface even-odd path with a hole for the
palette's physical screen rectangle, translated into surface-local pixels.
This uses no hidden-API bypass, permission, root, or proprietary code in the APK.
Empty bounds clear a stale framebuffer clip. Every native enable reapplies the
current clip before enabling handwriting. A clipping failure pauses writing
explicitly instead of allowing ink beneath controls.

The native clip checks DOWN, while later MOVE events can synthesize a new DOWN.
Therefore **every toolbar contact**, including an ordinary button tap or a
cancelled press, pauses handwriting synchronously before child dispatch. Native
completed UPs are accepted first. After all contacts lift, a writer barrier runs
before resuming native input and executing the queued action. A grip move updates
the exclusion before moving the window; release restores the canonical viewport
once so previously covered ink becomes visible correctly. A normal tap reuses
the paused native tracks. A paper stroke begun outside the palette may continue
behind it; its stored vector remains intact.

## Verification and tablet acceptance

All **16,947 host assertions** pass, including **45 exclusion checks** for
screen/surface offsets, partial/full coverage, out-of-bounds windows, edge-only
contact, negative origins, and integer-overflow resistance. Existing storage,
scrolling, PDF, and undo/redo regressions pass. The full API 29 app builds as
versionCode 15, versionName 0.14, signed with the existing local key. Both
Pen-selected and Eraser-selected palette previews were visually inspected using
the production icon paths and exact dimensions.

No tablet/ADB access was used. Host tests cannot verify the firmware's live
clipping, popup input routing, physical refresh behavior, or pen latency. Install
`build/ink-notebook-0.14.apk` over the existing app without uninstalling.

1. Check that existing notes remain, and identify all symbols. Tap Pen/Eraser:
   only the selected tool's outline should become thick, with no toolbar flash.
2. Move the palette using the grip with a finger, then with the stylus. Stop
   midway and near each edge; ensure it stays put and remains reachable.
3. Tap and slide off controls, including moving from the palette onto paper.
   No stray dot or stroke should appear. Then write beside the palette and check
   normal smooth writing.
4. Erase, Undo/Redo, change paper, add/jump/scroll pages, and export a PDF. Check
   that icons remain available and other buttons do not blink together.
5. Move the palette away from existing writing; check that the writing reappears.
   Open/close the menu and overview, leave and reopen the notebook, and restart
   the app. Verify placement and smooth continued writing.
6. Use **Reset tools** and confirm the palette returns to the top-center position.
