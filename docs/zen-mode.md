# Zen display mode — 0.33.2-dev.3

The toolbox fullscreen-corners button toggles Zen mode. Entering hides the
top-left status window and Undo/Redo, and reduces the top-right window to a
10 sp clock with seconds and compact padding. Exiting restores the normal bars
and any history actions still eligible under the writing-context rules.
Battery/Wi-Fi indicators return with normal mode. The clock retains the tablet's
locale and 12/24-hour preference.

The change affects app overlays only: notebook geometry, split/floating layouts,
zoom, tool choice, gesture settings, Android system bars and firmware settings
are untouched. The toolbox remains visible. Native input pauses for the toggle;
status exclusions update before presentation, exposed ink repairs through the
existing track list, and input resumes without clearing/rebinding its framebuffer.

Zen-button contacts retain the writing context; other toolbox contacts still
dismiss history as before. Cancelled Zen contacts count as ordinary interactions.
Writing during Zen cannot make history icons reappear. The editor saves the mode
across its Activity recreation; a newly opened editor defaults to normal mode.
A native/save failure exits Zen to retain the existing visible Retry path while
the toolbox is unavailable.

Only Zen schedules seconds callbacks, aligned to the next wall-clock second.
Only the small clock view invalidates on each tick, including while writing;
it does not reposition windows or redraw notebook content. Hidden battery/Wi-Fi
events update their stored values without repainting the clock. Exiting Zen and
pausing the editor remove callbacks; a stale callback cannot restart them.
Normal mode retains its minute/event-driven, idle-gated status behavior.

Validation passed **246,381 host assertions in 64 suites**, Debug/Release lint
and builds, package verification, permanent-certificate verification and the
APK checksum. Production Zen/clock callbacks are executed with host adapters
for enter/exit restoration, history suppression, unchanged page state, tiny
clock-only drawing, second-boundary scheduling, hidden-indicator coalescing,
stale timer callbacks, pause/busy/error handling and left-window exclusion
removal. The toolbox geometry fixture now covers eleven controls.

The signed candidate is `build/releases/scriflow-0.33.2-dev.3.apk`, versionCode 67,
prepared through `scripts/build-release.sh`. No device installation or physical
clock/input verification was performed.

Physical tablet placement, clock readability, refresh and latency remain pending
under the owner's instruction to continue without the device. Public stable
0.33.1 is unchanged.
