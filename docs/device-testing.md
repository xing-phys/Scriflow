# Device test record

Date: 2026-10-23. Device: HMW-W09, firmware
`HMW-W09 2.1.0.222(C00E170R1P8)`, Android 10/API 29.

## Prior physical observation

The user tried the published KOReader MatePad Paper APK and reports no visible
strokes until writing finishes. This is not evidence of successful low-latency
firmware ink on this device. Root cause remains unconfirmed.

## Firmware investigation

Read-only inspection of `eink.framework.jar`, native Notes bytecode, and the
handwriting library established these integration details:

- Native Notes uses `android.eink.view.edit.HWEditView`.
- Its parent selects RGBA_8888 and top Z order. Its background picture defaults
  to transparent; the diagnostic explicitly binds a white background.
- The native writable state constant is spelled `STATE_HADNWRITE` and equals 1.
- `getFBSurface()` is non-null before binding. We initialize through `View.post`
  after `surfaceChanged`, checking the active buffer.
- The framework's final binding callback runs after application holder callbacks.
- AHW disables are tracked by identity tokens. We read actual state and blocker
  count rather than assuming the enabling call proves success.
- The framebuffer can outlive surface recreation. Our own listener is detached
  before clearing references to prevent duplicate capture.
- Native handwriting points expose x/y, `eventtime`, and `pressure`.

This supports the implementation choices; it does not establish physical latency
or prove the transparent KOReader overlay is the cause of its failure.

## Checks completed

| Check | Result |
| --- | --- |
| Local bootstrap and offline build | Passed with Java 8, API 29, Build Tools 30.0.3 |
| APK signature and manifest | Verified; package `org.eink.inkprobe`, min/target API 29 |
| Java 8 lambda desugaring | Synthetic lambda classes generated; no LambdaMetafactory runtime dependency |
| Install and Activity launch | Passed on connected tablet |
| Android drawing and JSON export | Synthetic finger swipe: 1 complete stroke, 7 points, no missing DOWN; screenshot visually checked |
| HWSurfaceView initialization | Active buffer, listener attached, AHW true, stylus accepted, 0 disable tokens |
| HWEditView initialization | AHW true, writable state 1, stylus accepted, 0 disable tokens |
| Native diagnostic snapshot | Valid JSON with firmware state and track list |
| Native background/foreground | Returned with AHW true and no disable tokens |
| Code review | Corrected listener cleanup and resize reinitialization before final build |

The synthetic swipe tests ordinary drawing/export, not firmware stylus input.
The native track list was empty during initialization checks because no physical
stroke had been performed in this app at that point.

## Pending physical checks

- Continuous visible ink in Native edit and HW live.
- Captured tracks and pressure after physical strokes.
- Comparison with native Notes and ordinary drawing.
- Rapid consecutive strokes, dots, palm contact, and pen-up behavior.
- Writing across surface recreation and interruption.
- Optical latency measurements, if needed.

After these checks, select the working firmware integration for the next stage.
The current prototype intentionally has no automatic live-to-page handover in
HW live, so app-rendered completion cannot mask a missing live path.

## Physical failure and version 0.2 correction

The user reported severe latency, full-screen black/white blinking, and sometimes
a black screen remaining until another stroke. The tested mode was uncertain.
The saved v0.1 export was HW live with six complete strokes and 229 points;
it does not establish which mode was visible when the complaint occurred.
Our app was force-stopped while investigating. The stored event timeline showed
no repeated surface initialization during those captured strokes.

The user opened the smooth reference. Foreground package/activity was confirmed
as `com.huawei.hinote/.edit.EditActivityInk`, matching the APK already inspected.
No note contents were read or copied.

Read-only effective policy queries before correction returned:

```text
org.eink.inkprobe: {video_mode=11, mode=1}
com.huawei.hinote: {mode=3}
```

Inspection of the system `HwContentDetectorConfig` implementation established
that the application manifest's `hw_eink.app.refresh.mode` is consumed for live
display policy and takes precedence over fallback configuration. Native Notes
declares 3. This is separate from a launcher consumer that uses the same key for
ghost clearing on app exit; the launcher consumer alone was insufficient evidence.

Version 0.2 changes:

- Declare app refresh policy 3, matching Notes; do not change global settings.
- Set HW live to RGBA_8888 instead of its previous RGB_565 default. Native edit
  already inherited RGBA_8888. This matches the reference surface but is not yet
  independently established as the cause of flashing.
- Record declared app policy, app version, and raw view post mode in exports.
- Refuse firmware surface construction if manifest policy 3 is absent.
- Provide a host-only readback script; the hidden vendor readback API is blocked
  from ordinary APKs on this firmware. No hidden-API bypass was added.

Post-install effective query returned `{mode=3}` for both Ink Probe and Notes.
Both surfaces initialized with pixel format 1 (RGBA_8888), AHW true, stylus
accepted, and zero disable tokens. Native writable state remained 1. Both raw
view post modes read 0; no speculative waveform or global override was applied.

The revised build was physically tested in Native edit. The user reports no
blinking, but most writing is delayed and some strokes are occasionally smooth.
The retained export contains 23 native tracks. These observations establish
improvement after the policy/format changes, not consistent low latency.

## Version 0.3 timing investigation (2026-10-24)

Preserve rendering/policy/pen settings while adding an external timing listener
to Native edit. Native HWEditView's internal listener remains registered. The
framework's explicit add/remove listener methods are hidden from an ordinary
APK on this firmware, so use the exposed setter/null pair whose implementation
multiplexes external and internal handlers. No hidden-API bypass is used.

The observer stores callback arrival uptime paired with the firmware event time,
without per-point UI updates, log writes, or refresh commands. Arrival follows
the framework's internal handler and therefore includes its processing time.
This measures software delivery; it cannot measure the physical panel's latency.

Validation before physical input:

- Java host timing tests: 60 assertions passed, including delayed delivery with
  regular sample timestamps, clock errors, percentiles, reset, and ring eviction.
- APK build/sign/install passed. Effective app policy remains 3.
- Ordinary synthetic swipe: 16 recorded samples, delivery p50 24 ms, p95/max
  26 ms; all event/receipt arrays aligned, no invalid clock samples. This is not
  a real-pen benchmark.
- Native edit: AHW true, format RGBA_8888, observer attached, zero disable tokens.
  Native handler remains responsible for tracks and drawing.

Next: collect five separate physical strokes and compare event span against
arrival span and delay percentiles. Prompt event delivery despite visible delay
would direct attention to display/compositor behavior. Late or batched delivery
would direct attention to the callback/widget processing path. Fullscreen is a
confirmed window difference from Notes and a candidate subsequent isolated test;
no fullscreen or global refresh changes are bundled into this measurement build.

## Physical timing result and fullscreen comparison

The user reports all requested strokes delayed. The saved version 0.3 export
contains 20 native tracks and 1007 observer points, including earlier input in
the same session, so it is not a clean five-stroke-only dataset. Aggregate
callback delay was median 1 ms, p95 14 ms, maximum 73 ms. The final five tracks
likewise arrived throughout writing, not as a batch at pen-up. Native and observer
track counts agree. This points toward a display-side issue while leaving the
physical panel latency unmeasured.

The overlapping compositor log contains ordinary refresh pairs roughly once per
second, including idle periods. After each of the final five pen-down events,
the first logged periodic framebuffer comparison occurs 258–803 ms later.
Subsequent native disassembly established that the DOWN-time force-skip command
already copies the current buffer inside its own invocation. The later logged
copy is separate bookkeeping, so these intervals do not establish a missing
preframe handshake or a wait that gates visible ink. UP/CANCEL uses a different
notification path, despite its misleading native name `notifyDownEvent`.

Version 0.4 adds a single-variable fullscreen on/off comparison. Native Notes
was observed using FLAG_FULLSCREEN; the prototype did not. Policy, pixel format,
pen settings, and keep-screen-on behavior remain unchanged. Effective app policy
3, native AHW readiness, and actual fullscreen flag were verified after install.
Resize reinitialization passed without duplicate listener attachment or errors.

A 35-second passive fullscreen log still contains the roughly one-second refresh
pairs, so fullscreen did not eliminate that periodic activity. A single ordinary
app frame on pen-down remains an optional scheduling experiment, with no
per-move repainting; the native contract does not establish that it is required.

## Version 0.4 physical result and unrelated layer cadence

Fullscreen physical result: first stroke improved; some subsequent strokes
initially followed the stylus and then fell behind. The saved export contains
15 native tracks and 1209 observer points. Median callback delay remains 1 ms,
p95 4 ms, maximum 133 ms. This does not establish consistent smooth handwriting.

A passive pair of SurfaceFlinger samples, 10.23 seconds apart, identified the
source of periodic buffer submission:

| Layer | Frame counter change |
| --- | --- |
| Hidden StatusBar | 45158 → 45179 |
| Probe activity | 7 → 7 |
| Probe handwriting surface | 1 → 1 |
| Visible floating overlay | 2 → 2 |

The status bar submitted roughly one-second pairs despite being hidden in
fullscreen. The floating overlay did not update in this sample, so its mere
presence does not implicate it. These measurements identify the periodic buffer
producer, not its optical effect or which SystemUI feature drives it.

The physical isolate was unplugging USB while leaving version 0.4 open, writing,
then reconnecting. The user reports **consistently smoother writing unplugged**.
This supports a charging/USB-related influence, but does not distinguish charging
animation, power state, USB traffic, or another cause. The measured StatusBar
producer is hidden and absent from the visible output-layer list. Its buffer
submissions alone do not establish optical interference.

After reconnection, battery service reports USB powered, charging (status 2), and
85% charge; the StatusBar still submits near-one-second pairs. The next controls
are plugged-in native Notes writing/cadence and a bounded on-device capture
spanning cable removal. These preserve the existing app and handwriting setup.
No battery spoofing, SystemUI stopping, or global display changes have been
applied.

The device-side cadence recorder passed a 10-second connected smoke check:
two samples contained 10 new StatusBar timestamp tuples over five seconds.
Completed log retrieval and removal of its two temporary device files passed.
Host fake-ADB checks also covered serial selection, shell syntax, bounded
duration, incomplete-log retention, collection, and repeated collection.
The recorder has not yet captured an unplugged interval. Its `/proc/uptime`
sample clock includes suspend while SurfaceFlinger's timestamps do not, so
compare deduplicated rows across samples rather than subtracting those clocks.

Version 0.5 was built locally in parallel as an optional subsequent experiment:
one ordinary decor-frame request on pen-down, none on move/up. It remains off
by default, uses app-local invalidation rather than a forced waveform, records
each request time, and does not paint ink. Build/signature and existing host
tests pass. It has not yet been installed or physically tested; version 0.4
remains the current tablet build during the unplugged comparison.

## Plugged-in recovery control

The user reports that native Notes also initially follows the pen and later falls
behind while USB is connected, although it follows for longer than the probe.
After more writing, Notes becomes smooth. This means the earlier assumption of
an always-smooth plugged-in reference was incorrect.

While Notes was left open after recovery, a 10.24-second passive sample found
zero new timestamp rows on StatusBar, Notes' root and handwriting layers, and
the floating overlay. USB remained powered and charging, at 86%. Both apps hide
StatusBar from actual display output. Returning to unchanged Probe 0.4 restored
20 new StatusBar rows over 10.14 seconds; Probe's root, handwriting layer, and
floating overlay stayed unchanged.

A subsequent bounded 60-second recording in Probe showed roughly two new
StatusBar rows per second initially, then no new rows in the final samples.
USB remained powered. The last new row was at firmware monotonic time
156351.895 seconds. The user's later writing continued through 156440.392
seconds, so the periodic activity stopped before pen input ended. The user
reports Probe eventually became smooth too, after substantially more writing
than Notes. The exact optical transition time was not recorded.

The saved Probe export retains 284 native tracks and 15,331 observer samples,
including earlier trials. The new plugged-in trial occupies tracks 148–284,
spanning approximately 202 seconds. Aggregate callback delay across the export
was p50 1 ms, p95 5 ms, maximum 262 ms; the bounded last-8192-sample window has
maximum 221 ms. These are software delivery measurements, not optical latency.

Later reconnection/readback found 100% charge and battery status 4 rather than
the earlier status 2 at 86%. This later condition must not be substituted for
the charging conditions in the recovery samples. A fresh passive sample also
found an unchanged StatusBar history. No battery state was spoofed.

Exact SystemUI inspection did not establish a charging-animation cause: battery
and stylus icons examined are static vectors/level lists, battery broadcasts
skip unchanged state, clock seconds are not explicitly enabled, and the network
speed display is configured off. These checks narrow specific hypotheses;
they do not identify the recurring invalidation's source.

Known remaining window differences include Notes' system-UI flags `0x2500`
(layout stable, layout fullscreen, light status bar) versus Probe's zero, and
Notes lacking Probe's keep-screen-on flag. Both use the fullscreen window flag
in this comparison. An app-local comparison of those two differences can be
measured automatically before requesting another long physical trial.

## Version 0.6 window controls

Installed version 0.6 after saving the long version 0.4 trial. It adds separate
Notes UI (`0x2500` versus zero) and Awake (keep-screen-on) controls, while retaining
the existing fullscreen control. Requested and actual settings are exported.
Saved-instance state preserves window options and selected mode. The uninstalled
version 0.5 DOWN-frame experiment was removed; callbacks again request no app
redraw at any point in a firmware stroke.

Automatic idle samples used Native edit and fullscreen throughout. Each row is
an independent approximately 10-second before/after StatusBar history comparison;
button-triggered setup redraws were allowed to settle before sampling.

| Notes UI flags | Keep screen on | New StatusBar rows |
| --- | --- | --- |
| 0 (initial control) | On | 20 |
| 0x2500 | On | 20 |
| 0 (restored control) | On | 21 |
| 0 | Off | 20 |
| 0x2500 | Off | 20 |

Exports confirmed the actual flag combinations and native Auto-HandWrite true
throughout; effective app refresh policy remains 3. Neither known window
difference suppressed the periodic producer in this test. This is a negative
result, not a confirmed handwriting fix. The final configuration is fullscreen
on, Notes UI on, Awake off. Physical handwriting with version 0.6 is untested.

Battery was now 100%, USB powered, status 4 rather than actively charging status
2. The periodic submissions therefore do not require active charging. The next
bounded recording spans physical USB removal and reconnection, without writing
or any battery/global-display changes.

Validation: APK build and signature verification passed, effective policy readback
passed, existing host timing tests passed all 60 assertions, and own-app exports
verified the control states on the device. No new app permissions were added.
