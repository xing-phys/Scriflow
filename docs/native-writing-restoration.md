# Direct handwriting restoration in 0.31.0-dev.5

The accepted source baseline is `f40ef50`, release `0.30.0-dev.3`. Huawei's
native AHW implementation receives pen input, predicts and draws ink directly
to the framebuffer. Java observes samples and passes completed strokes to
asynchronous saving. This separation protects latency.

Dev.4 kept the brush algorithm but changed the live pipeline: transparent AHW
captured samples, Java fed a second HandPen, transformed the native surface and
posted dirty rectangles per sample. It also rendered completed ink into a bitmap
from the completion callback. The owner reported unacceptable delay. A matching
API contract or final shape does not establish acceptable live latency.

Dev.5 deletes that second pen and restores these methods verbatim from the
baseline: `point`, `nativeCommit`, `drainCommits`, `flushCompletedUps`, `attach`.
Live pen setup again selects native black ink and the original AHW tool/type,
down-event and enablement configuration. Page mode supplies the original fitted
width; screen mode changes only the width configured before writing begins.

The remaining additions to the baseline retain exact completed-stroke capture
width and scale and render already completed ink during paused restoration or
export. Transparent restored tracks are bookkeeping for completed ink already
painted on the paper background. They are distinct from the active, black native
pen. No Java brush feeding, manual framebuffer posting or bitmap drawing belongs
in a live point or completed-stroke callback.

Page fitting still changes the width seen by Huawei's pixel-based brush. Its
pressure response can therefore vary across zoom. This limitation is unresolved;
changing the live drawing pipeline to hide it is not an accepted solution.

Verification includes direct source comparison, host storage/geometry/history
regressions and Android builds/lint. Host graphics fixtures check completed-stroke
rendering only. Physical latency must be checked on the MatePad; no tablet was
connected while preparing this release.
