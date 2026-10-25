# Eraser width and native grey trail — 0.20

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
The grey trail is never a saved stroke, history entry, or PDF element.

## Native preview — 0.20

Version 0.19 used Android touch sampling, a 16 ms Canvas batch, and regional
framebuffer invalidation for the grey trail. The user confirmed the feature
worked but reported terrible preview latency. Version 0.20 removes that drawing
loop and uses the same firmware AHW path as the working pen.

Both tools keep `HWEditView` in handwriting state with AHW enabled. Pen uses its
black ballpoint configuration. Eraser uses a grey ballpoint with its selected
diameter, fitted to the physical viewport. The existing persistent native clip
protects the floating palette. The ordinary Android eraser events are consumed
to prevent the widget's destructive pixel-eraser behavior. There is no app-side
Canvas work, invalidation, page composition, file write, or UI update per MOVE.
The native point observer only captures primitive coordinates, time, and pressure.

Each eraser DOWN captures its diameter. Native point samples preserve off-page
centres so a broad brush can reach page edges from a margin or page gap. UP and
the native widget's track commit are separate observations and may arrive in
either order. `NotebookEraserCommit` joins them by end timestamp, delivering one
path only after both are present. UP pauses new AHW input while the already
queued native commit settles. A missing commit reports an error rather than
silently editing the wrong ink. Controls cannot edit an unsettled preview.

The temporary grey native track is never added to the adapter's canonical
`completed` list or emitted as a pen stroke. The listener performs the existing
whole-stroke erase, then `setTrackList` posts the bound paper and remaining ink
together. No separate paper-only frame is requested. A no-hit gesture also
replaces the preview with the unchanged canonical document. A cancelled gesture
with a native track restores the unedited page; pause, surface shutdown, and tool
changes discard unfinished capture and remove pending completion work.
Preview tracks never enter storage, undo history, or PDF output. The saved page
format, eraser range, width preferences, and pen input path are unchanged.

## Verification

`./scripts/test.sh` passes **19,035 assertions**, including 203 native-erasing
completion checks. Tests cover both native callback orders, cancelled and interrupted gestures,
duplicate delivery prevention, timestamp mismatches, width captured at DOWN,
off-page centres, and real-file erase/reopen/undo/redo with no grey saved ink.
The former manual damage helper and its tests are removed with the drawing loop.
The API 29 APK builds offline using the existing signing key.

The debug-only `NotebookSelfTestActivity` has a `nativeEraserTest` mode which
never opens NotebookStore or user notebooks. It seeds a disposable dot/curve,
checks native AHW, width and color, then verifies each physical gesture has
finished the private native accumulator before applying the model edit. It
checks the temporary track's style and removal, canonical ink, no emitted pen
stroke, and retention of the same Surface and paper bitmap. Only aggregate
results are written to the test JSON; no sampled handwriting is exported.

Launch on the tablet:

```sh
adb shell am start -S -W -n org.eink.inkprobe/.notebook.NotebookSelfTestActivity \
  --ez nativeEraserTest true --es template lined --ef eraserWidth 36
adb shell run-as org.eink.inkprobe cat files/notebook-selftest/result.json
```

On HMW-W09, native AHW readback accepts width 36 and grey `0xffaaaaaa`.
The user reports that the width-36 trail is smooth and that the trail and touched
sample ink disappear on release. Physical gestures also pass the disposable
check: the native accumulator is settled, the temporary track is removed in
place, canonical ink matches, and no preview is emitted as saved ink.

Separate on-device blank/lined regressions pass native coordinate/style/pressure/
time restoration, in-place erase/undo/redo/no-hit, paper and page switching,
retained Surface/bitmap identities, and the simulated UP-before-native-commit
pause boundary. Those simulated boundary checks establish preservation behavior;
they do not measure physical pen latency. Optical latency feedback remains a
qualitative user observation rather than a numerical tip-to-display measurement.

The maximum width 240 also passes native track/style/cleanup checks. The user
reports it stays smooth at ordinary speed but develops visible lag when drawing
very fast. They explicitly accepted retaining that limit because such a broad,
fast erase is uncommon. Smaller erasers are the usual workflow. No width cap or
preview/erase-footprint mismatch was introduced.

A measured 18.479-second width-240 gesture delivered 1,140 native samples. Mean
software delivery delay was 6.31 ms, maximum 23 ms, and maximum after the first
three seconds was 21 ms. A further 7.758-second gesture peaked at 24 ms. These
measurements show no growing application callback backlog; they do not measure
optical latency or prove the remaining delay's cause. The metadata-only layer
record found no status-bar frames in the approximate windows of those two
strokes, and no periodic floating-overlay redraws. No background apps, system
settings, or refresh waveform numbers were changed.

Tablet acceptance:

1. Install `build/ink-notebook-0.20.apk` over the existing app; do not uninstall.
2. Select Eraser, tap it again, choose 36, and release. Reopen its slider and
   verify 36. Check the pen retains its own previous width.
3. Erase across a few disposable ink strokes. Confirm the grey trail follows
   while down and disappears with touched whole strokes on release. The toolbar
   and unaffected writing should stay steady.
4. Repeat at small and large diameters, including a dot, a no-hit gesture,
   a curved path, and a broad path through the gap between two visible pages.
5. Check Undo/Redo, return to Pen, scroll, reopen, and export PDF. There should
   be no saved grey trail and the two tool widths should remain independent.
