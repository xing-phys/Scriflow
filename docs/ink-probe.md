# Ink Probe

A small Android diagnostic app for low-latency stylus handwriting on Huawei
MatePad Paper HMW-W09. This is a handwriting experiment, not yet a notes app.

It deliberately separates firmware live ink from application-rendered strokes.
An enabled API and successful point capture do not prove visible live ink.

## Build and install

On Linux x86_64, prerequisites are Java/Javac 8, Python 3, curl, unzip, and ADB.
The build has been verified with OpenJDK 8 on Ubuntu 24.04. The bootstrap downloads
pinned Android API 29 and Build Tools 30.0.3 from Google into ignored `.tools/`;
archives are checked against pinned checksums and Google's repository metadata.
No Gradle, Android Studio, Kotlin runtime, or external application libraries are
needed for this first experiment. Java keeps this probe compatible with the
existing toolchain; a larger app can adopt Kotlin later.

```bash
./scripts/bootstrap.sh
./scripts/build.sh
adb -d install --no-incremental -r build/inkprobe-debug.apk
./scripts/read-display-policy.sh
adb -d shell am start -W -n org.eink.inkprobe/.MainActivity
```

The APK is debug-signed with a locally generated, ignored key. It requests no
permissions, has no network features, and stores diagnostics only in its own app
directory. Losing the local signing key prevents updating an existing installation
with the same identity; keep `.tools/debug.keystore` locally if retaining app data.

## Modes

| Mode | What draws the ink | What it tests |
| --- | --- | --- |
| Android | Ordinary Android View; accepts pen/finger | Baseline app rendering and point capture |
| HW live | Huawei HWSurfaceView + Auto-HandWrite | Firmware live ink only; our callbacks never repaint, even at pen-up |
| Native edit | Huawei HWEditView with its own listener and editing logic | A framework widget used by native Notes, with its default format/Z order |
| Replay | Ordinary View renders previously captured Android/HW live points | Whether point capture worked independently of live ink |

Switching modes destroys their writing surface. Android/HW live captured points
remain in memory for Replay; Native edit owns a separate track list which is
discarded when leaving its mode. Replay is intentionally unavailable directly
from Native edit. **Save log before changing modes** if retaining native tracks.
All writing is disposable diagnostic content. It is not automatically saved.

## Physical pen comparison

1. Select **Native edit** and write slow lines, fast curves, small dots, and a
   short word. Observe whether ink follows the tip while it is still touching.
2. Tap **Inspect**, then **Save log**. Inspect reports native track count and
   firmware state; it does not paint missing ink on our behalf.
3. Collect the log using the command below before another export overwrites it.
4. Select **HW live**, tap **Clear**, and repeat. If live ink is absent, tap
   **Inspect** and **Save log** before trying **Replay**. Replay showing points
   while HW live was blank distinguishes capture from live rendering.
5. Compare **Android** with real pen input. Its finger support enables automated
   baseline smoke checks but does not simulate physical stylus behavior.

The **Z** experiment applies only to HW live and reconstructs its writing surface.
Native edit preserves its own default format and Z ordering. Avoid tapping UI
controls while judging pen-to-ink response: UI updates can affect screen refresh.

```bash
mkdir -p artifacts
adb -d exec-out run-as org.eink.inkprobe cat files/probe.json > artifacts/probe.json
adb -d logcat -d -s InkProbe:I > artifacts/inkprobe-logcat.txt
```

The export includes device/build, canvas size, setup events, and captured points
in surface pixel coordinates. Each point has x, y, firmware/Android event time,
and pressure. Pressure and timestamp semantics are retained for investigation,
not assumed calibrated. Native edit's points are under
`firmwareSurface.tracks_x_y_time_pressure`; other modes' points are under
`strokes`. `Save log` overwrites only this app's prior `probe.json`.

Version 0.3 adds a callback timing observer in Native edit as well as HW live.
The exposed firmware listener setter adds an observer alongside the native
widget's own handler; removing our observer leaves the widget handler intact.
Callbacks do not redraw, update status, or write files. Each stroke's
`callbackArrivalUptimeMs` aligns one-to-one with its existing point array.
`callbackTiming` reports delivery-delay percentiles from at most the most recent
8192 valid samples. Invalid or future event timestamps are counted separately.
Inter-event gaps include pauses between strokes and are not latency estimates.

```bash
python3 scripts/analyze-probe.py artifacts/probe.json
./scripts/test.sh
```

The host tests cover delivery timing versus event sampling, clock mismatch,
nearest-rank percentiles, reset, snapshot immutability, and bounded storage. The
observer runs after the widget's internal processing; its reported delay includes
that processing. Keep rendering unchanged while collecting the first timing trial.

Software logs and screenshots cannot measure physical pen-to-ink latency.
External high-frame-rate video is needed for quantitative latency claims.

## Current status and limitations

Version 0.1 failed the physical comparison: severe delay and black/white blinking.
Version 0.2 declares `hw_eink.app.refresh.mode=3`, matching native Notes, and
uses RGBA_8888 for both firmware surfaces. On this firmware, missing app metadata
causes an automatic policy (`mode=1, video_mode=11`), which differs from Notes.
These application-policy values are distinct from raw EPDC waveform numbers.

The host policy query verifies the effective value without changing settings.
The ordinary APK cannot access that hidden vendor readback API, so its own export
labels the manifest value as **declared**, rather than claiming it is effective.
Firmware mode construction refuses a missing/incorrect manifest declaration.
Physical testing, rather than successful policy readback, establishes writing quality.

The version 0.2 physical result was **no blinking, but usually delayed ink with
occasional smooth strokes**. Version 0.3 instruments that remaining problem; it
does not claim to fix latency.

Version 0.4 adds a **Fullscreen** comparison button. The export records both
the requested fullscreen state and actual window flags. Toggling it changes
only `FLAG_FULLSCREEN`; policy, pen configuration, keep-screen-on behavior,
and callback timing remain unchanged. Save a trial before switching, since
resizing the writing surface can affect in-memory widget contents.

Version 0.6 adds independent **Notes UI** and **Awake** comparisons. Notes UI
changes only system-UI layout/color flags between zero and `0x2500`, the value
observed in native Notes. Awake changes only `FLAG_KEEP_SCREEN_ON`. With
Fullscreen on, Notes UI on, and Awake off, these known window settings match
Notes. This is a controlled comparison, not a confirmed latency fix. Exports
include requested settings, actual window flags, actual system-UI visibility,
and actual keep-screen-on state. Window settings and selected mode survive
activity recreation; captured strokes remain disposable.

Automatic idle comparisons found about 20 new StatusBar timestamp rows per ten
seconds with either option and with both, including the restored control. These
window differences did not suppress that producer. After suppressing the Clash
notification updates described below, the user reports almost smooth writing
from the beginning, with only occasional short pauses.

The uninstalled version 0.5 experiment requesting a frame at pen-down was
removed in 0.6. Further native inspection showed that firmware already takes its
preframe snapshot during the pen-down command. Our callbacks again request no
app frames at any point in the stroke.

Fullscreen sometimes improved stroke beginnings, but writing could later fall
behind. The user reports consistently smoother writing with USB unplugged.
Plugged-in native Notes and Probe can both become smooth after more writing,
with Probe taking longer in the reported trial. Periodic hidden StatusBar
buffers stopped in recovered Notes, restarted on returning to Probe, then also
stopped during a longer Probe trial while pen input continued. Their causal
effect and the trigger for this changing activity remain unconfirmed.

For a comparison that survives cable removal, use the bounded device-side
recorder. Start with the desired app/mode already open; this tool does not launch
apps or change settings.

```bash
python3 scripts/offline-layer-cadence.py start artifacts/usb-comparison
# Unplug USB, write, and reconnect after at least 65 seconds.
python3 scripts/offline-layer-cadence.py collect artifacts/usb-comparison
```

The recorder samples battery power state and `StatusBar#0`'s rolling timestamp
history every five seconds for one minute, then exits. Collection pins the
original device serial and removes its two temporary device files after saving
the completed log. Compare new, deduplicated timestamp rows between samples;
old rows remain in the history even when no frames arrive. `/proc/uptime` and
SurfaceFlinger timestamps use different clock domains, so do not subtract them.
These are layer submissions, not optical refresh or handwriting latency.

On this Huawei USB mode, cable removal can stop the ADB service and interrupt
the recorder. An incomplete log is retained by default. After the recorded
start time plus duration plus ten seconds, it can be explicitly recovered with
`collect artifacts/usb-comparison --allow-partial`; such a log is labeled partial
and must not be treated as evidence of an unplugged interval unless it actually
contains unplugged samples.

## Background notification interference

A metadata-only system trace identified Clash Meta's background service posting
its traffic notification every second, followed by SystemUI's paired frames.
The installed Clash build uses a one-second ticker for this feature. With the
user's authorization, **Settings → App → Service → Show Traffic** was switched
off while Clash was stopped, then Clash was restarted. Its VPN service remained
running; new StatusBar submissions fell from about 20 per ten seconds to zero
in the measured idle comparison. On a fresh Native edit surface, the user then
reported almost smooth writing from the beginning with only seldom occurring
pauses, a substantial improvement. The saved continuous stroke lasted 15.8
seconds and retained all 1,389 observed samples in the diagnostic export.

Callback delay was p50 1 ms, p95 3 ms, maximum 65 ms. One mid-stroke sample was
delivered 50 ms after its timestamp; no optical pause timestamps were recorded,
so its relationship to the remaining visible pauses is unconfirmed. These
results establish a useful working configuration, not complete parity with
native Notes or an optical latency measurement.

This establishes a source of display activity that exists outside the notes
app's own layers. A static floating overlay does not exclude notification-driven
activity from another background app. Avoid routine traffic/clock/status updates
inside a handwriting app's own UI as well. Compatibility testing should include
background notifications and a freshly opened writing surface.

See [the investigation plan](../matepad-handwriting-plan.md) and
[device test record](device-testing.md).

Firmware-specific calls use reflection and explicit checks. They are not a
portable Android SDK. No proprietary firmware is bundled. The current app has
one fixed-width black pen, simple baseline rendering, and no durable notebooks,
pressure-sensitive brush, undo UI, export-to-PDF, or complete palm rejection.
The baseline ignores fingers briefly after pen contact; this is not full
multi-pointer palm handling. Native edit inherits its framework behavior.

Drawing surfaces deactivate on pause and unregister listeners on disposal.
Buffer initialization waits until after the framework's surface binding callback.
App-owned point callbacks do not write to disk or repaint HW live. Diagnostic
snapshots are explicit, and file writes run on a worker thread.
