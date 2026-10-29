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
