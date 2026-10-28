# MatePad Paper compatibility and resource audit — 0.33

## Scope and device

USB checks on 2026-10-28 used the owner's MatePad Paper **HMW-W09**, Android
10/API 29, firmware **HMW-W09 2.1.0.222(C00E170R1P8)**. Development candidates
0.33.0-dev.5–dev.7 were installed with the permanent release signer, preserving
the existing application ID and library. New writes used disposable QA notes;
rendering and backup benchmarks used synthetic data. Personal note contents,
device identifiers, vendor binaries and captures are excluded from the repository.

The final 0.33.0 release uses the same implementation as dev.7; its separate
installation/check record is in [device testing](device-testing.md).

## Changes supported by measurements

| Same 600 × 700 PDF viewport, eight zoom levels | Before (dev.6) | After (dev.7) |
| --- | ---: | ---: |
| Cached ARGB pixels | 5,767,168 | 2,097,152 |
| Bitmap storage | 22 MiB | 8 MiB |
| Total tile work, one run | 220 ms | 221 ms |
| Native renderer retained after clear | Yes | No |
| Source descriptor retained after clear | Yes | No |

The pixel reduction is **64%** for this small-pane fixture. It is not a claim
that all app memory or all PDFs improve by that percentage. An expanded rerun
took 223 ms. These single-run timings establish no observed slowdown, not a
statistically significant speedup.

The old fixed six-million-pixel allowance applied even to small floating panes.
The new budget retains up to two visible tile sets for zoom fallback, with the
existing six-million-pixel ceiling; required sharp tiles are always retained.
All tiles are rasterized at the requested screen resolution. Hidden/cleared
panes recycle pixels and queue native-reader cleanup behind in-flight work.
Replacing a request during rendering cannot publish stale pixels. A companion
without link parsing no longer closes the active editor's navigation reader.
Blank notes do not start a PDF thread merely to clean up an unused reader.
Display allocation exhaustion is delivered through the existing failure path.

## App and firmware checks

- **Native writing:** effective firmware application policy is **13**, matching
  the established native handwriting setup. The foreground path still uses
  Huawei AHW directly; no raster drawing, UI invalidation or shortcut work was
  added to pen MOVE callbacks. Tool switches use the existing native backend.
- **Stylus body gesture:** the owner's physical taps changed Pen ↔ Eraser and
  Lasso → Pen in an open note. The owner accepted both switches and the brief
  top-center icon. The foreground receiver uses this firmware's protected
  `com.huawei.stylus.action.BUTTON_DOUBLE_PRESSED` action. It is unregistered on
  pause and registered again on resume; no new permission is needed.
- **Workspace:** 31 device assertions cover the baseline floating/lasso/token/
  frontmost-toolbox checks, real tool/backend/icon behavior, three floating
  companions, five-page continuous reading at fit width, finger scrolling,
  pinch to 2.5×, stylus-tap editing focus, two split panes at most, 20 complete
  move/resize/minimize/restore/focus/close cycles, and PDF cache/cleanup races.
  The same native editor and saved QA ink survived the window cycles.
- **Resource stability:** the expanded 20-cycle run took 25.8 seconds. Descriptor
  counts went from 137 to 131; thread counts fluctuated from 30 to 35 without a
  per-cycle increase. PSS fluctuated with bitmap allocation/GC and settled near
  61 MB after idle. These observations do not prove absence of every leak.
- **Idle:** the expanded foreground sample consumed 14 ms process CPU in 10 s;
  other comparable runs used 19–29 ms. The lifecycle run used 32 ms CPU in 5 s
  with the screen off and 26 ms in 5 s behind Home. These are short process-CPU
  samples, not battery-life measurements.
- **Sleep/resume:** explicit sleep worked with Keep screen on enabled. Pause
  released native ink and the stylus receiver. Wake, including landscape
  Activity recreation, restored the QA note/ink and the receiver. Home/return
  also restored the same note. The harness follows the resumed Activity rather
  than a destroyed instance after rotation. No keyguard policy was changed.
- **PDF picker:** landscape import through the actual system picker opened both
  the two-page PDF and the 600-page, 9,365,725-byte fixture automatically.
  Encrypted and invalid PDFs showed recoverable import errors with retry/library
  controls. No new production-app crash was observed.
- **Ink consistency:** the firmware renderer passed 14 synthetic pressure-stroke
  comparisons, concurrent replay, capture scales 0.5/0.75/1.125/1.5/2, transparent
  placeholders and viewport clipping. Canvas/PDF threshold boundaries stayed
  within one source pixel of native replay; light/heavy widths remained 5/10.
  Forty native ballpoint/simple-eraser footprint checks also passed.
- **Durability:** the Android-runtime benchmark appended and round-tripped 100
  strokes/12,800 points through editable backup, verifying IDs, pressure, times
  and styles. One run measured append 42 ms, backup 148 ms, restore 18 ms, with
  a 33,358-byte archive. Full host suites passed 173,256 assertions across 56
  suites. Android lint/builds and release identity verification passed.

The rendering harness was corrected to use the actual PdfRenderer page viewport
and logical page dimensions divisible by every tested capture scale. Its former
hard-coded PDF-point transform and rounded logical heights introduced page-origin
shifts unrelated to stroke geometry. The one-pixel comparison threshold was
retained. Checks now use current release classes/APK rather than old debug files.

## System review and limits

The tablet reported about 5.7 GiB RAM and 2.7 GiB available during the initial
check. Thermal service reported status 0 with no active cooling throttling in
that snapshot. CPU-governor state was restricted to the shell; it was not changed.
The APK requests network-state observation only: no Internet, wake-lock,
background-service or boot-receiver permission/component was added. Device
status observes events/minute ticks, and orientation sensing stops on pause.
Keep screen on is a foreground window flag, not a CPU wake lock. The tablet's
power, thermal, security and refresh settings were left under firmware control.
The app's original Sensor orientation preference was restored after landscape
checks; its other preferences were retained.

This is evidence of compatibility and reduced resource use on **this firmware**,
not a proof of globally optimal performance or support for all Huawei devices.
Native APIs are firmware-specific and intentionally use the tested API-29
configuration. USB charging prevents useful battery-life comparisons. Overnight
idle drain, physical cover-sensor behavior, very long sessions, arbitrary complex
PDFs, optical pen latency and panel ghosting were not quantified. Very fast
maximum-width erasing retains its previously documented limitation. No system
"speed" tweaks or firmware changes are required by this release.

## Reproduce

Build a permanently signed release first. `scripts/check-device-workspace.sh`
installs a separate, same-signer external QA helper and removes it on exit; it
never makes the production APK debuggable. Optional checks:

```sh
# Push the generated host-test fixture into this app's external QA directory.
adb -d shell mkdir -p /sdcard/Android/data/org.eink.inkprobe/files/qa-fixtures
adb -d push build/pdf-engine-test/large-source.pdf \
  /sdcard/Android/data/org.eink.inkprobe/files/qa-fixtures/large-source.pdf
bash scripts/check-device-workspace.sh -e testStylus true -e audit true \
  -e expectReleased true -e lifecycle true
bash scripts/check-stroke-rendering.sh
bash scripts/check-tool-brush.sh
bash scripts/check-foundation.sh
```

`-e interactiveStylus true` leaves an open QA note waiting for physical body taps
until the external QA stop file is created (ten-minute guard). Software tests
invoke the actual receiver directly because the firmware blocks application
senders; only physical taps establish hardware delivery. Logs/screenshots stay in
ignored `artifacts/` and external QA directories. The helper creates uniquely
named notes. Optional `cleanupNames` accepts an explicit comma-separated allowlist
of disposable QA names and moves only exact matches to recoverable Trash;
`keepId` retains a selected QA note. It never permanently deletes notebooks.
