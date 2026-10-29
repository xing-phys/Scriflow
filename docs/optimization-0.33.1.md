# Optimization follow-up after 0.33.0

The owner authorized implementation on 2026-10-29 after the read-only review.
Each step preserves saved data, native AHW input and the complete-frame buffering
that prevents floating-window operations from exposing white intermediate frames.
Development APKs retain the permanent signing certificate. They are local tablet
test candidates; the public stable release remains 0.33.0.

## Step 1: page geometry (0.33.1-dev.1, code 55)

Ordinary pan changes share immutable page sizes/tops/heights/scales. A clamped,
unchanged pan returns the same viewport, so a vertical scroll at fit width does
not allocate a new page layout. Zoom recalculates geometry once while retaining
the immutable size list; its subsequent anchor/pan update shares that geometry.
Page visibility, hit testing, scroll anchors and nearest-page lookup use indexed
searches rather than scanning every page. Inclusive pen boundaries, strict
visible-area boundaries, gaps, earliest-page ties and float rounding are retained.

The full host suite passed, including 48,009 additional geometry assertions
against linear reference queries and immutable-layout ownership. Debug/Release
lint and builds passed, and the permanently signed APK was installed as an update
on HMW-W09 firmware 2.1.0.222(C00E170R1P8). The existing real-device workspace/
reader/tool/PDF harness passed all 31 checks, including inactive five-page finger
scrolling/pinch/focus, multi-note layouts and 20 window-operation cycles.

`scripts/check-viewport-performance.sh` compiles the exact v0.33.0 geometry as
an isolated reference and runs both implementations in the same Android process
with synthetic mixed-size pages. It reads no notebooks or display surfaces.
Seven timed samples alternate execution order after warmup. For 600 pages and
20,000 pan/visible/nearest/hit/anchor operations, median times were **713.155 ms
before** and **11.140 ms after**, with identical result checksums. This is a
geometry microbenchmark, not a claim of 64× faster scrolling or handwriting.
Raw local logs remain ignored in `artifacts/optimization-0.33.1/`.

## Step 2: exact ink geometry (0.33.1-dev.2, code 56)

The active preview and inactive readers now retain bounded exact native outlines
for immutable source strokes. A stable admission subset avoids cyclic cache
thrashing on pages larger than the budget: uncached strokes still replay at full
quality. The active view admits at most 65,536 points, each reader 32,768 points,
and either cache at most 1,024 strokes. Returned paths are owned copies, so
selection transforms cannot mutate cached vectors. Page changes prune stale
owners; pausing/minimizing releases view caches. Reader moves no longer rebuild
and prune a set of every loaded stroke on every input sample.

Stroke bounds/maximum pressure are computed during existing immutable-stroke
validation, then reused for viewport intersection instead of rescanning all its
points. These derived fields do not change serialization. Focus handoff draws
source-page outlines through the viewport transform rather than allocating a new
screen-coordinate point list and replaying it for each stroke.

Host cache tests verify exact pixels, ownership, cancellation, page replacement,
zero-budget behavior, cleanup and stable over-budget admission. Full regressions
and Debug/Release lint/builds passed. Native comparison had **zero ARGB pixel
differences** for cached versus uncached ink. The source-coordinate handoff had
29 antialiasing-value differences and zero threshold/boundary differences versus
the previous composed replay. All prior pressure/capture-scale/PDF/clipping
checks passed. The signed dev.2 update passed all **36 device checks**, including
window stress, finger reading, native lasso/tool panels and sleep/Home return.

A native-geometry-only workload (256 strokes, 32,768 points, eight replay passes,
seven alternating timed samples after warmup) measured **784.888 ms uncached**
versus **1.645 ms with warm paths**, with matching geometry checksums. This
isolates repeated brush reconstruction; it excludes raster/display work and is
not a claim of a comparable whole-app speedup. Live AHW callbacks remain unchanged.

## Step 3: worker-prepared figures (0.33.1-dev.3, code 57)

Active/inactive note layers and the figure placement editor consume prepared
pixels; PNG decoding never runs from their draw callbacks. One worker request
per layer is in flight, and queued obsolete zoom/source requests are skipped.
Native writing resumes only after current visible figures are prepared, as for
PDF tiles. A retained handoff frame also waits for figure readiness. Source
changes, cancellation and hiding discard obsolete results without republishing
old pixels. Decode errors retain the existing recoverable failure behavior.

PNG sampling uses stored original dimensions and conservatively retains at least
the pixels occupied on screen, up to the original resolution. Thumbnail workers
also decode for their output size; full-size worker/export drawing remains
available. Visible layers hold prepared bitmaps directly, avoiding repeated
redecoding when the shared cache cannot retain all displayed images. Placement
and duplication share an opaque immutable-asset key, while independent source
images cannot alias merely because they have the same restored UUID.

The full host suite and Debug/Release lint/builds passed. The signed update passed
**37 device checks** covering real native/tool/workspace controls, cold UI-decode
rejection, size/ownership/collision cases, figure editor loading, source/zoom
races and sleep/Home return. In a two-figure fixture, each original was 2048²;
fit-width prepared pixels totaled **2 MiB rather than 32 MiB** for full decodes.
The zoomed figure reacquired original 2048-pixel resolution, then released the
larger visible pixels after zooming back. Shared-cache eviction did not trigger
UI decoding during native-paper redraw. These figures describe this fixture,
not a fixed reduction in total app memory. Raw captures remain private/ignored.

## Step 4: shared optional caches and memory pressure (0.33.1-dev.4, code 58)

Visible notes share the six-million-pixel optional PDF allowance. Current sharp
tiles always remain resident; their required pixels may exceed the shared
allowance to preserve resolution. Active and inactive outline allowances total
at most 131,072 admitted points across the active view and visible readers.
Only currently visible source pages enter the active outline admission plan,
and a focus handoff promptly clears the retired active view's cache. Private
cached paths draw directly without allocating owned wrappers on every repaint;
paths returned to callers still have independent ownership.

Memory-pressure callbacks discard optional image/outlining/fallback caches and
close worker-owned PDF readers/navigation resources. Current native framebuffer
contents, requested PDF tiles, prepared visible figures and stored ink remain
available. A blank/non-PDF viewport releases an unused source-PDF reader.
The figure decoder thread expires after 30 seconds idle and restarts on demand;
submission failures enter the recoverable error path. Content-derived image
keys reuse decoded pixels across independent loads and panes without retaining
compressed PNG arrays in the shared cache keys. The optional image-cache byte
allowance also charges an estimated per-entry overhead.

Four synthetic 600×700 PDF panes were warmed through eight zoom levels each.
The same sharp requested tiles stayed ready while aggregate cached pixels fell
from **8,388,608 (32 MiB)** with independent allowances to **5,242,880 (20 MiB)**
with shared allowances. Memory trimming then closed native readers while keeping
current sharp pixels ready; new requests reopened readers lazily. These cache
figures do not describe total process memory or guarantee fixed memory savings
for arbitrary documents/pane layouts.

The exact permanently signed code-58 APK was installed as an update on the same
HMW-W09 firmware. The final combined helper passed **58 real-device checks**:
workspace/native tool integration, three floating companions, inactive finger
scroll/pinch/focus, split limits, 20 window cycles, PDF cleanup/races, figure
sampling/editor/source identity/races, memory callbacks, and sleep/Home return.
The full host suite passed **245,280 assertions in 57 suites**. Debug/Release lint
and builds passed with no lint errors; all native pressure/capture-scale/PDF/
clipping/cache comparisons passed. APK identity and the permanent signer were
verified again. The helper was uninstalled after each run. Cleanup was restricted
to QA names recorded during this optimization task, with the latest QA note
retained and earlier task/user notebooks left alone.

Raw local logs and synthetic captures remain in ignored
`artifacts/optimization-0.33.1/`. The final local update APK is
`build/releases/scriflow-0.33.1-dev.4.apk`, with its adjacent SHA-256 file.
The public stable release remains 0.33.0. No system/firmware settings or document
formats changed. USB-powered tests do not establish battery life, physical cover
behavior or optical pen latency; those limits from the previous audit remain.

Reproduce after preparing a signed release and pushing the generated large-PDF
fixture as described in the previous audit:

```sh
bash scripts/check-device-workspace.sh -e testStylus true -e audit true \
  -e expectReleased true -e figures true -e budget true -e lifecycle true
bash scripts/check-stroke-rendering.sh
bash scripts/check-viewport-performance.sh
```
