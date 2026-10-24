# Notebook editing milestone — 0.8

## Scope and use

Adds undo, redo, and a whole-stroke eraser to the existing blank/lined page.
Page navigation and additional notebooks remain later work. Package identity
`org.eink.inkprobe`, local signing key, logical page coordinates, and notebook
format version 1 are preserved. Existing 0.7 writing needs no migration.

The toolbar provides Pen, Eraser, Undo, Redo, and paper selection within its
existing height. A check mark identifies the current tool. In Eraser mode,
move the stylus across writing and lift it: every touched stroke is removed as
one undoable action. This is a stroke eraser, not a pixel eraser. It has a fixed
18 logical-pixel radius plus half the stroke width and has no live preview.
Finger input does not draw or erase. Choose Pen to return to native handwriting.

Undo and redo retain at most 100 edits in the current app process, including
writing and successful erase gestures. Activity recreation and paper selection
retain that history. A new process starts a fresh history while retaining the
saved page. New writing or a successful erase clears redo; a no-hit erase does
not. Paper changes are separate from stroke history.

## Implementation and durability

`NotebookEdits` is an Android-independent immutable-page editor. Its history
shares immutable stroke objects, preserving pressure, timestamps, and styles.
Whole-stroke hit testing measures distances between continuous line segments,
including crossings, dots, and zero-length segments. Broad bounds skip distant
strokes. One completed eraser path is one history command even if it touches
several strokes.

Normal handwriting still uses the established Huawei automatic handwriting
path. Eraser mode explicitly disables that path and consumes standard Android
stylus/eraser-tip events before the vendor widget can edit native tracks. It
collects primitive coordinates and performs no drawing, disk work, or status
updates on MOVE. A completed UP sends its path to the background document worker.
CANCEL, pause, and interrupted gestures discard the partial eraser path.

In version 0.8, the worker completes hit testing and saving before the Activity restores the
canonical document in a fresh native widget. Native input is paused during the
edit. Completed pen UPs awaiting a vendor track notification are submitted before
queued undo/erase operations. Old widget callbacks cannot overwrite new UI state.
A no-hit eraser gesture currently also reconstructs the canvas, so an unnecessary
refresh is possible even though it changes neither document nor history.

Version 0.8.1 replaces that edit-time reconstruction with an in-place track
update and skips clean no-hit redraws. See `notebook-edit-refresh.md` for the
source investigation, exceptional safety fallback, and validation limits.

Pen strokes retain the append-and-sync journal. Stroke edits use a validated,
synced replacement snapshot and the existing backup/rename procedure. Format
version 1 is unchanged. This targets app interruption and force-stop recovery;
sudden hardware power-loss durability has not been tested.

`NotebookDocument` owns the model, history, and storage on one serialized worker.
On a save failure it retains completed edits in memory and stops further writes.
Retry reloads and validates the complete disk page against the last acknowledged
page or the exact target of the failed write. Dimensions, template, styles, and
every point must match. A mere stroke prefix is insufficient after deletion.
Unexpected or corrupt data is preserved and retry refuses to overwrite it.
Accepted states are reconciled with the intended in-memory page and synced.
The acknowledged/attempted states are tracked before each recovery write so a
second failure remains retryable. Recovery does not reset editing history.

## Offline verification — 2026-10-24

`./scripts/test.sh` passes 1,629 assertions:

- 60 callback-order/timing assertions.
- 1,388 storage assertions, including frozen 0.7 fixture loading, replacing and
  appending, invalid-input preservation, and interrupted edit publication.
- 79 edit-engine assertions, covering undo/redo, branching, the history bound,
  multi-stroke gesture undo, no-hit behavior, and segment/dot geometry.
- 102 document assertions, covering edit persistence, process/history lifetime,
  ambiguous completed writes, queued work after failure, retry, and rejection
  of unexpected or corrupt disk data.

The complete API 29 app builds offline using the existing key. The new APK has
versionCode 8/versionName 0.8. Its signing certificate SHA-256 remains
`62672ba8b918e0556821363eb325b18f3cf7591814166cb88b00e1211e371000`.
Source review checked native input gating, callback ordering, and lifecycle
boundaries. These checks cannot establish physical stylus delivery or latency.
No ADB access, app installation, or tablet changes were performed for this update.

## Install and physical acceptance

1. Transfer `build/ink-notebook-0.8.apk` to the tablet and install it as an update.
   Keep the existing app installed; uninstalling deletes its private notes.
2. Open Ink Notebook and confirm the existing page and paper are present.
3. With Pen selected, write three separate test strokes. Wait for Saved, then
   try Undo and Redo; each tap should remove or restore one stroke.
4. Select Eraser. Cross two test strokes and lift the pen. Both should disappear
   together. Undo should restore both; Redo should remove both again.
5. Undo an erase and erase only blank space. Redo should still be available.
   New writing should clear Redo. Switch paper and confirm Undo still works.
6. Select Pen, check continued writing feels smooth, wait for Saved, then close
   and reopen. Confirm the edited page remains. After process restart, Undo/Redo
   should start empty as documented.

The key unverified behavior is ordinary stylus event delivery while Huawei
automatic handwriting is disabled for Eraser. If it does not erase on lifting,
return to Pen and report the result. The previously validated low-latency pen
path remains in use. Background notification redraws can still interfere with
this firmware; keep Clash's Show Traffic setting off as established earlier.

User feedback after installing 0.8 confirmed that erasing, undo, and redo all
work. It also identified occasional ink-only blinking during edits, addressed
by the subsequent 0.8.1 refresh change.
