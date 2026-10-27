# Pressure geometry across zoom, scrolling and PDF

## Width modes in 0.31.0-dev.4

Settings now offers fixed width on the page (default) and fixed width on the
screen. The original Huawei ballpoint, pressure samples, and event times are
retained. Merely preserving a zoom-scaled native nib was insufficient: a width
of four page units became two native pixels at scale 0.5, disabling the native
pressure rule, while the same selected width at scale 1 retained pressure.

Page mode uses a separate installed `HandPen(type, true)` bound through
`setSurfaceBoard(ISurface)`. AHW remains enabled with transparent ink for native
sample capture and completion bookkeeping. At pen-down the editor chooses the
page under the stylus and freezes its scale. Each sample reaches the visible
brush in page coordinates, with unchanged pressure/time and selected width.
The native framebuffer matrix scales the finished geometry; surface save/restore
scopes that transform to each call. This uses the same brush as the source-scale
0.21 correction rather than introducing a pressure curve.

Static inspection of the cached firmware establishes that `applyMC` applies
floating-window exclusion clips before the drawing matrix. AHW input itself is
not inverse transformed by that matrix. The helper therefore transforms input
explicitly and posts conservative physical dirty bounds: the native brush's
automatic posts use untransformed bounds. No Java path, bitmap, or UI is rebuilt
per MOVE. The bound paper cache receives the completed vector after its stroke
has been handed to document saving; no extra full-surface redraw is posted then.
Pause, tool changes, and surface recreation discard any partial manual pen.

Screen mode keeps direct native AHW with an unchanged native-pixel nib width.
`NotebookPenConfig.nativeInput` retains its exact native width even when inverse
fitting floats cross the four-pixel boundary. Page mode instead retains the
canonical selected brush width and inverse viewport capture scale. Both feed
the same existing version-5 storage, replay, and vector PDF paths. Existing
strokes are not reinterpreted when the setting changes.

Host tests execute the actual mode/style and native bridge code with explicit
firmware contract fixtures. They verify inverse coordinates, raw pressure/time,
scoped matrices, physical bounds, cancellation/lifecycle assumptions, durable
round trips, and export inputs. These fixtures do not emulate Huawei's brush or
establish physical latency. The native headless diagnostic also covers both
mode geometries; live framebuffer acceptance still requires the MatePad.

## Captured geometry introduced in dev.2

The 0.21 pressure-outline fix remains necessary, but scaling input samples and
brush width before replay does not scale Huawei's resulting brush shape exactly.
The firmware uses pixel-based pressure rules. Inspection of the previously
collected MatePad `libeinkpen.so` finds that `YuanZhuBi::getStrokeWidthByPressure`
returns the nominal width below four native pixels. At four pixels and above it
returns the integer truncation of `width * (1 + pressure) / 2`. The firmware also
has pixel-based speed calculations. A source-space replay followed by display
scaling can therefore differ from the original low-latency ink.

Each newly captured stroke now retains `captureScale`, the native input pixels
per logical page unit at writing time, and its exact native `captureWidth`.
Reconstructing that width by multiplying transformed floats can round from
four pixels to just below four, changing the native pressure rule. Rendering
replays the native brush at its
original width and input coordinate scale, then scales the completed vector
outline back to logical page coordinates. Scrolling, thumbnails, selection and
PDF use that same outline. A view transform changes coordinates, nominal width
and capture scale together; pressure and event timestamps remain unchanged.
Lasso moves, clipboard copies, page duplication, undo/redo and backups preserve
the metadata.

Viewport composition retains the complete original stroke samples. The renderer
draws the full finished vector under a saved `Canvas.clipRect` for its visible
page, restoring the Canvas clip afterwards. Dev.2 used `Path.op(INTERSECT)` to
rewrite the outline; the owner reported that this returned false on the tablet
and stopped writing with “Could not clip handwriting to its page.” Dev.3 removes
that boolean path operation entirely. Even an empty clip is a normal drawing
case, not an error. Export and selection receive the complete outline and apply
their own page clip. Earlier code clipped the sample sequence,
which could replace the original DOWN with a later MOVE and change the brush's
endpoint taper while scrolling. Native brush widths stay within the original
supported range even for unusually broad legacy ink shown at high zoom.

The native widget's `IHWTrack` contains samples, style and bounds, with no path
replacement API. Its normal track helper regenerates the brush at the current
view scale. Restoration now paints the canonical outlines onto the existing
owned viewport bitmap together with its paper and PDF background, while
retaining transparent native tracks for firmware completion bookkeeping. Cached
firmware inspection shows that the track renderer passes the complete ARGB
color to `SkPaint::setColor`. New writing uses the installed native brush on the
same framebuffer surface, with the page/screen input routing described above;
no Java path generation, bitmap painting, storage write or UI invalidation is
added to pen MOVE callbacks.

Page format 5 adds two floats per stroke, retaining all existing framing,
checksums, points, identities, pressure and time values. Versions 1–4 remain
readable without rewriting their files on open. Their original native capture
scale was never recorded, so they retain the historical source-space replay
(`captureScale = 1`). It cannot be reconstructed reliably from old samples.
Their first edit saves format 5 atomically and retains the predecessor file as
a backup; older APKs cannot read format-5 pages or backups containing them.

The host suite covers native-pixel rounding examples, view transforms,
unclipped brush input, durable appends and snapshots, legacy non-mutating reads,
migration backups, lasso and clipboard copies. The native headless helper
`scripts/check-stroke-rendering.sh` additionally compares native replay against
Canvas and vector PDF at five capture scales and checks that transparent native
placeholder tracks draw no pixels. It uses only synthetic data and never opens
user notebooks. These new native checks and the live widget restoration need a
connected MatePad; they were not run during this update because no device was
connected.
