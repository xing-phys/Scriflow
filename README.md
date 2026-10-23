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

Software logs and screenshots cannot measure physical pen-to-ink latency.
External high-frame-rate video is needed for quantitative latency claims.

## Current status and limitations

See [the investigation plan](matepad-handwriting-plan.md) and
[device test record](docs/device-testing.md).

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
