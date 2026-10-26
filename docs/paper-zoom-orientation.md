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

Settings offers Portrait, Landscape, and Follow device sensor. The setting
applies to the library, writing screen, backup/restore, and PDF export. Sensor
mode uses the physical sensor even when system rotation is locked. Rotation
waits for pen lift, then uses normal Android activity recreation to release
and reconstruct the Huawei writing surface. The process-wide notebook writer
and editing history survive that recreation; page geometry stays fixed.

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
