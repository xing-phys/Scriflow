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
