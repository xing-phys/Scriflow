# MatePad Paper handwriting investigation and development plan

The app is now named **Scriflow** (formerly **Scribblow**); see
[branding](docs/branding.md). Historical app names, log tags, and APK filenames below
describe the original experiments.

Initial review: 2026-10-23. Implementation and device trials began afterward;
see [the current test record](docs/device-testing.md) and [prototype](README.md).

## Objective and current scope

Develop an understanding of the device-specific handwriting path and validate a
minimal experiment before implementing a note-taking application. The initial
review used read-only ADB commands, a temporary copy of a system framework,
and public source code. Subsequent authorized work installed the Ink Probe
diagnostic app. Ordinary-app firmware access and stroke capture now work;
consistent low latency across startup and USB conditions remains under test.

## Evidence from this tablet

- Model: HMW-W09.
- Firmware: `HMW-W09 2.1.0.222(C00E170R1P8)`.
- Android base: Android 10 / API 29; ordinary ADB shell, without root.
- Display: 1404 × 1872 physical pixels; reported density 320 dpi.
- Installed system package includes `com.huawei.hinote`.
- `/system/framework/eink.framework.jar` contains a DEX implementation of
  Huawei-specific `android.eink` classes.
- Framework SHA-256:
  `f267c5ff648348528185a29a082c12f4f36048059f909906ce7fc1eac4effdcd`.

Selected signatures extracted from that framework:

```text
android.eink.view.HWSurfaceView(Context)
HWSurfaceView.getFBSurface()
HWSurfaceView.lockActiveCanvas()
HWSurfaceView.unlockActiveCanvas()
FBHandSurface.setAutoHandWrite(boolean)
FBHandSurface.setAhwToolTypes(int[])
FBHandSurface.setAhwPen(int, float, int)
FBHandSurface.setAhwTouchListener(IAhwTouchListener)
IAhwTouchListener.onAhwAddPoint(int, float, float, long, float)
```

The framework also contains `HWEditView`, eraser helpers, and refresh interfaces.
Method presence establishes an investigation route; it does not establish that
our APK can call these methods or that all features work on this build.

Temporary inspection files are under `/tmp/matepad-investigation/`. They may be
removed by the OS. The proprietary framework is not included in this workspace.

## External evidence and its limits

Huawei acknowledges third-party app handwriting lag and lists adapted apps:
<https://consumer.huawei.com/cn/support/content/zh-cn15879907/>

An independent KOReader release reports working firmware handwriting and saved
annotations on the same device family without root:
<https://github.com/plateaukao/koreader/releases/tag/matepad-paper-v1>

The concrete implementation inspected is `HuaweiAhw.kt` on the launcher's
`supernote_ink` branch, pinned here for reproducibility:
<https://github.com/plateaukao/android-luajit-launcher/blob/e87346ffe1344f1e0d2ee0434813b5ae2a6ee259/app/src/main/java/org/koreader/launcher/device/huawei/HuaweiAhw.kt>

Related surface stacking:
<https://github.com/plateaukao/android-luajit-launcher/blob/e87346ffe1344f1e0d2ee0434813b5ae2a6ee259/app/src/main/java/org/koreader/launcher/device/StylusBridge.kt>

The implementation creates a Huawei handwriting surface, enables firmware ink,
and receives completed strokes through a listener. This supports a two-path
design: immediate system ink plus an application-owned document model.

Important qualification: the `matepad-paper-v1` tag pins an older launcher
revision that did not contain the Huawei adapter in our inspection. The release
points to `supernote_ink`, where the adapter is present. Source-to-APK
reproducibility must be checked before using the release as a test dependency.
The user tested the released APK on this tablet and reports that strokes appear
only after writing finishes, with no visible live ink. This is evidence that its
reported behavior does not reproduce here. Stroke capture and firmware live ink
must therefore be validated independently; the adapter is an unverified reference.

Huawei also advertises an Android Pencil Engine:
<https://developer.huawei.com/consumer/cn/hms/huawei-pencilengine/>
The public overview alone does not establish compatibility with this firmware or
use of this e-ink handwriting path. Evaluate it if a supported device-specific
integration can be obtained; do not assume modern HarmonyOS APIs apply here.

## Proposed architecture

```text
Stylus input
  ├─ Huawei Auto-HandWrite surface → immediate visible ink
  └─ captured point callbacks → stroke model → saved document
                                      └─ page renderer
```

The app must synchronize the live ink surface with the rendered document. Once
the document contains a stroke, its temporary ink can be cleared without losing
it or producing a visible gap. Merely selecting a fast screen waveform does not
solve this synchronization problem.

Use a native Android APK with Kotlin and a small custom drawing/surface layer as
the initial experiment. Isolate Huawei calls behind a device adapter with
capability checks and explicit errors. Retain an ordinary Android rendering
backend for a baseline and graceful fallback. No need to bundle Huawei's system
framework in the APK.

## Proposed stages and success criteria

### 1. Validate access in an ordinary APK

Create a disposable diagnostic APK with a blank writing rectangle, an enable
switch, and a small status panel. Try reflective class loading, surface creation,
listener registration, and Auto-HandWrite activation. Record each result and
exception. Restrict drawing to the writing rectangle and release the surface on
pause/destruction.

Success: live ink and point callbacks both work from our own package without
root or system signing. If access fails, identify class-loading, API restriction,
permission, or surface-lifecycle causes before building application features.

### 2. Compare handwriting paths

Provide ordinary Android drawing and Huawei firmware ink modes in the same
test app. Compare both with the native Notes app using repeated short strokes,
fast curves, dots, and long strokes.

Measure callback timing and event completeness in software. For actual
pen-to-visible-ink latency, use external high-frame-rate video when available;
ADB screenshots and frame logs cannot establish physical panel latency.

Success: firmware mode feels comparable to Notes under the same conditions,
without missing stroke beginnings/endings. Report measured latency only when
the optical measurement supports it.

### 3. Make visible ink and stored strokes agree

Store coordinates, timestamps, pressure when valid, tool, and pen attributes.
Define explicit transforms between input, surface, screen, and document space.
Start with a fixed-width black pen so the two renderers can be compared easily.
Do not assume pressure semantics or pen style constants without checking them.

Render completed strokes into the persistent page layer and clear temporary ink
only after the page update is ready. Test rapid consecutive strokes and pen-down
during a pending handover. Use an asynchronous storage path with a bounded,
durable stroke journal; keep disk work outside input callbacks.

Success: no disappearing, duplicated, shifted, or unexpectedly reshaped strokes;
saved content reloads correctly.

### 4. Handle editing, refresh, and lifecycle

Add stroke undo/redo, an eraser, and finger-versus-pen handling. Test palm contact,
toolbar interaction, dialogs, page changes, rotation, background/foreground,
screen lock, and surface recreation. Disable or clip live ink when it must not
draw over controls. Decide behavior for cancellation and interruptions.

Treat redraws and waveform selection separately from low-latency ink. Start
with the system's existing strategy. Tune region updates and refresh-on-idle
only when observations justify it; avoid forced full refreshes while writing.

Success: no unintended ink outside the canvas, no stuck overlay after leaving
the app, reliable erasing and undo, and acceptable accumulated ghosting.

### 5. Build the small note-taking MVP

After the handwriting experiment passes, add notebooks/pages, autosave and
recovery, a few pen widths, and export. Keep stored strokes independent of
Huawei's runtime APIs so documents remain portable. Choose PDF/background
annotation, recognition, synchronization, and additional platforms later based
on the intended product.

Success: write a page, edit it, close/reopen the app, and export it with matching
content. Broader product design follows the handwriting feasibility result.

## Tooling and next decision

ADB already works. The default `java` is OpenJDK 8; check all installed JDKs and
Android SDK locations before choosing a compatible Gradle/Android build setup.
An IDE is optional. No build dependencies were installed during this review.

The recommended next implementation is stages 1–2: a minimal handwriting test
APK. Its purpose is to prove ordinary-app access and writing quality on this
exact firmware before committing to the full note-taking app.
