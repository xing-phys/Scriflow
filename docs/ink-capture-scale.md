# Pressure geometry across zoom, scrolling and PDF

## Direct native input restored in 0.31.0-dev.5

Both width modes now use the pre-0.31 direct native AHW pipeline. Native code
receives input, predicts and draws visible black ink. Java observes samples and
hands completed strokes to asynchronous saving. There is no second pen, Java
point feeding, per-sample surface transform/framebuffer post, or stroke-end
bitmap rendering. See [the restoration record](native-writing-restoration.md).

Page mode remains the default. It fits the selected width to the viewport before
native input starts, using the same rule as 0.30.0-dev.3. Screen mode instead
keeps the nib's visible width fixed in the composed viewport. Both record the
actual native capture width and coordinate scale for later replay and export.
Existing ink, including dev.4 strokes, retains its stored capture parameters.

The native brush has pixel-dependent pressure and speed rules. Consequently,
page fitting can change pressure response as zoom changes. Identical pressure
response across zoom is unresolved and is no longer promised. Preserving native
input and drawing latency takes priority over replacing the live pipeline.

Dev.4 attempted to normalize pressure with a separate native HandPen driven
through Java callbacks, inverse coordinates, framebuffer transforms and explicit
posts. The owner reported unbearable latency. That implementation and its
synthetic API-contract tests have been removed; those tests never established
physical writing performance.

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
