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
