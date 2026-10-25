# Firmware-managed scrolling and handwriting — 0.26

Device: MatePad Paper HMW-W09, firmware `HMW-W09 2.1.0.222(C00E170R1P8)`,
Android 10/API 29. Investigation date: 2026-10-25.

## Current change

Version 0.26 declares `hw_eink.app.refresh.mode=13`, matching the installed
Jnotes 3.2.7.4 (version code 541). It removes the experimental manual waveform-6
requests from every screen, native surface, and floating window. Scrolling still
pauses native auto-handwriting (AHW), draws cached page previews, then restores
editable native tracks. The original `HWEditView` input and rendering path is
retained. There is no app timer or touch callback that changes refresh mode.

`DisplayPolicy` shares the manifest declaration check between the diagnostic
screen and notebook backend. `scripts/read-display-policy.sh` verifies the
effective vendor policy from shell UID. Application policy values describe
Huawei's firmware configuration; they must not be assumed interchangeable with
generic raw waveform enum names.

No global display property, secure setting, native pen-post setting, permission,
note format, or saved brush data changes. The tablet's secure refresh setting
remains 1. Installation is an update using the existing signing identity, with
a local safety copy of the current notes. No older data is restored over new
writing.

## What failed in earlier builds

- **0.23:** forcing waveform 6 on the editor and floating toolbar made scrolling
  faster, but physical pen contact inverted black and white and stalled writing
  for a long time. Software restoration checks had passed and did not cover this
  physical pen-down failure.
- **0.24:** restricting the override to scrolling restored writing, but the user
  saw flashes on finger contact and release. Continuous dragging was smooth.
- **0.25:** requesting waveform 6 only on individual preview/restoration buffer
  posts still flashed when entering and leaving scrolling. After finger-up,
  writing before the delayed return flash inverted the display and froze again.
  Resetting the Java posting hint to 0 did not establish that the physical panel
  had finished its transition. This build failed physical acceptance.

The previous 0.25 log check observed only the compositor's generated waveform
requests. Its 80 waveform-6 posts and cleared Java hints did **not** prove that
the hardware avoided transitions or cleaning. The driver has a separate queued
posting and display-readiness path. Native library inspection found mode-change
and idle waits consistent with the symptoms, but no optical timing or complete
causal isolation was performed. Do not reinstate the manual overrides on the
strength of those earlier software checks.

## Jnotes comparison

The user physically tested writing, scrolling, and immediate writing afterward
in Jnotes and confirmed no transition flashes or freezing. Read-only queries
and live display logs during that test showed application/default/last mode 13,
compositor waveform 13, and driver acceptance of mode 13. Jnotes' manifest also
declares policy 13; the previous Ink Notebook declaration was 3.

The system PenKit library loaded by Jnotes constructs
`android.eink.view.edit.HWEditView` and enables writing through its framebuffer
surface's `setAutoHandWrite`, the same native widget and AHW entry point used by
Ink Notebook. Jnotes' protected application implementation is not fully
inspectable, so this does not establish that every part of its layer handling
is identical. No proprietary code is copied or bundled into the app.

These observations support trying the firmware-managed policy before adding a
different ink layer. They do not by themselves prove the changed app is free
of flashes or freezes.

## Verification

The 0.26 APK builds, signs, and updates successfully. All 20,160 host assertions
pass. Disposable blank and lined native checks each pass three scroll/resume
cycles plus erase/undo/redo and page/template restoration checks. They verify:

- AHW is disabled during preview and enabled after restoration.
- Firmware default mode stays 13 and the native view's post hint stays 0.
- Synthetic native points, styles, pressure, and timing survive restoration.
- No preview creates a saved document stroke; user notebook files are not opened.

Two controlled explicit FINGER gestures in the real editor selected mode 13
through preview and restoration; the driver also accepted mode 13. The display
default and last-mode readbacks were 13 after scrolling. These are software and
driver observations, not an optical measurement of blinking or pen latency.
Physical acceptance: the user repeated writing, scrolling, and writing
immediately after finger-up in version 0.26 and confirmed both work without
flashes or freezing. This supersedes the failed acceptance of versions
0.23–0.25. Ghosting and long-duration use have not been optically measured.

To reproduce the disposable check:

```bash
adb -d shell am start -S -W -n org.eink.inkprobe/.notebook.NotebookSelfTestActivity \
  --es template lined --ez inPlaceEdits true --ez scrollRefresh true
adb -d exec-out run-as org.eink.inkprobe cat files/notebook-selftest/result.json
```

Repeat with `--es template blank`. Standard shell `input swipe` reports
`TOOL_TYPE_UNKNOWN` on this firmware and does not exercise this app's finger
scrolling branch. A controlled input check must explicitly set
`MotionEvent.PointerProperties.toolType=FINGER`; it cannot replace a physical
stylus test.

Local evidence is under ignored `artifacts/refresh-policy-0.26/` and
`artifacts/refresh-handoff-investigation/`. Earlier experiments remain under
`artifacts/fast-refresh-0.23/`, `artifacts/fast-refresh-0.24/`, and
`artifacts/fast-refresh-0.25/`.
