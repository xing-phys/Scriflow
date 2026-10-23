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

The revised build is installed. A short, specifically identified Native edit pen
comparison is pending. Configuration matching is not yet proof of resolved latency.
