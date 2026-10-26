# Paper sizes, zoom, and screen orientation — 0.30

New notebooks use A5 paper by default. Creation offers A5, A4, ISO B5, Letter,
A6, A3, ISO B4/B6, Legal, Executive, and custom width/height in millimetres.
Each custom dimension may be 20–1000 mm; entering width greater than height
creates landscape paper. Screen orientation is an independent app setting.

Physical dimensions are stored alongside fixed logical page coordinates,
using eight logical units per millimetre for new paper. PDF export converts
the stored millimetres directly to points. Blank page insertion, page addition,
duplication, and editable backup/restore preserve those dimensions.

The page snapshot format advances to version 3. It retains page and stroke IDs
and adds optional physical dimensions. Version-1 and version-2 snapshots remain
readable; reading alone does not rewrite them. An edit publishes a version-3
snapshot through the existing atomic save path. Legacy pages have no inferred
physical dimensions: they keep their coordinates and previous PDF printing
scale. Earlier APKs cannot read version-3 snapshots or backups containing them.

The writing viewport starts at Fit width. Two-finger pinch changes scale around
the gesture centre, and finger dragging pans enlarged paper and scrolls the
continuous notebook. Fit width, Fit height, Fit page, and percentage presets
are available from the zoom controls. Percentages are relative to Fit width.
The area outside page bounds is white. Completed strokes, erasing, and lasso
selection use inverse page transforms; zoom does not rewrite saved samples.
From dev.2, faint grey screen-only lines mark the paper boundary while its
surroundings stay white. The lines are included in all editor previews and
excluded from stored strokes and PDF export.

Settings offers Portrait, Landscape, and Follow device sensor. The setting
applies to the library, writing screen, backup/restore, and PDF export. Sensor
mode uses the physical sensor even when system rotation is locked. Rotation
waits for pen lift, then uses normal Android activity recreation to release
and reconstruct the Huawei writing surface. The process-wide notebook writer
and editing history survive that recreation; page geometry stays fixed.

## Dev.2 input and rotation follow-up

The owner reported that dev.1 pinch zoom works, but resting-hand contacts can
zoom unintentionally, paper edges are hard to see, and sensor rotation does
not respond. The cause of the firmware's sensor-policy failure is not confirmed
without a connected tablet. Dev.2 replaces reliance on FULL_SENSOR with
OrientationEventListener readings and explicit orientation requests in every
production screen. Sensor listeners run only while resumed. Upright sectors
have 30-degree acceptance windows and must settle for 600 ms; flat/unknown and
diagonal readings keep the current direction. Pen lift starts a fresh settling
interval so pending stroke commits can finish first. Natural landscape devices
and reverse orientations have explicit mappings. Settings reports sensor
availability. This uses normal-rate accelerometer access, with no added
permission or changes to global rotation settings. See Android's
[orientation listener](https://developer.android.com/reference/android/view/OrientationEventListener)
and [sensor lifecycle guidance](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview).

For finger navigation, pen hover/contact takes priority and adds a 600 ms quiet
period. Rejected contacts stay rejected until release; an untouched resting
finger does not pause the native stylus path. A broad contact (24 mm when
reported by the hardware) or more than two fingers cancels navigation. Zoom
requires two contacts arriving within 350 ms, then at least 60 ms and a span
change exceeding 12 dp or 4%, whichever is larger. These are conservative app
heuristics rather than a claim of perfect palm recognition. Cancellation
restores the viewport from before the gesture. Users can disable pinch zoom
in Settings and keep menu zoom and one-finger navigation.

Android 10 does not provide Android 13's per-pointer FLAG_CANCELED signal, so
these safeguards supplement the existing mixed-input ownership/cancellation
path. Device hover delivery and contact-size reporting remain firmware-specific.
See [Android stylus and palm cancellation guidance](https://developer.android.com/develop/ui/views/touch-and-input/stylus-input/advanced-stylus-features).

## Dev.3 shared app direction

The owner confirmed dev.2 solves the preceding bugs, then reported a separate
transition issue: rotate an open note to landscape, return to the library,
and the library first appears in portrait before rotating. Each Activity had
kept its own orientation request and restarted sensor settling on resume.

Dev.3 publishes every accepted direction to a process-wide state with weak
screen registrations. Existing background screens receive it before navigation;
new and recreated screens apply it before content is built. Production manifest
entries use `screenOrientation="behind"` while launching, then the controller
applies the shared direction. This uses Android's
[inherited orientation behavior](https://developer.android.com/guide/topics/manifest/activity-element#screen).
The last accepted direction is also saved in private device preferences.
Manual Portrait/Landscape changes publish through the same path. All four
directions remain distinct, and destroyed screens detach.

Sensor calibration now uses real display size and rotation, since a background
Activity can have a resource orientation differing from the actual display.
Sensor listeners remain foreground-only. The 600 ms settling interval and
writing guard still apply to a physical turn; switching screens consumes the
already accepted shared direction immediately. No note or backup format changes.

Acceptance: rotate a note, return to the bookshelf with both the books button
and Android Back, reopen a note, and enter Settings/backup/PDF screens. Repeat
for all four sensor directions, manual Portrait/Landscape, and when holding
the tablet flat after a rotation. Confirm each screen opens in the accepted
direction without a second portrait/landscape transition. This new transition
has automated regression coverage but no connected-tablet verification yet.

This is a development prerelease. Automated storage, PDF, and geometry checks
and Android build/lint checks cover the changes. No tablet was connected during
implementation, so physical acceptance remains pending. On the signed APK,
check the following with a disposable notebook:

1. Create default A5, A4, B5, Letter, and custom paper. Reopen each, add and
   duplicate pages, back up and restore, and inspect PDF page dimensions.
2. Pinch in/out around page corners and ink; check percentage feedback, fit
   choices, white margins, horizontal panning, and continuous scrolling.
3. Write, erase, lasso, move, duplicate, undo, and redo at several zoom levels,
   including page edges and between pages. Confirm reopened writing and PDF
   geometry agree.
4. Change Portrait/Landscape and sensor mode. Rotate while idle and immediately
   after writing. Confirm stable handwriting, preserved page position, usable
   floating controls, and unchanged paper/PDF dimensions.
5. Rotate during PDF preparation, destination selection, backup, and restore;
   confirm retained operations finish once and the editor resumes normally.
