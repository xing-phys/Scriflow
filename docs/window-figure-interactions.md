# Floating-note and figure interaction follow-up

The owner requested immediate corner dragging, a visible responsive resize
boundary, and direct finger selection of figures. The tablet was unavailable;
the owner chose to continue with host validation and signed APK preparation.

## Immediate floating-window controls — 0.33.1-dev.5 (code 59)

A movement past normal touch slop starts moving/resizing immediately. There is
no hold timer. An unmoved left tap still opens options; a right tap minimizes.
Rejected native-input states consume the attempted drag without later turning
it into a click. Cancellation restores the starting geometry, and retiring a
window cancels preview state without resuming a background Activity.

During resizing only four thin black edge panels follow the requested bounds.
The note's actual window and fit-width rendering keep their existing dimensions
until release, then reflow once. The edges have no page-sized surface, do not
consume touches and use the ordinary Activity panel layer beneath the toolbox.
The final note border draws in the foreground outside the writing surface.
The guide remains through size/layout settling and, for the active note, native
editor readiness. Delayed guide cleanup checks that its pane still exists.

Native input stays paused during manipulation, including asynchronous save/read
completions. The retained editor is restored after final layout. Existing native
live input and saved data formats are unchanged.

The complete host suite passed **245,307 assertions in 58 suites**, including
production corner callbacks, outline updates/footprint/layering and teardown.
Debug/Release lint and builds passed. `scripts/build-release.sh` verified the
code-59 APK against the permanent certificate. Physical finger tracking and
actual e-ink refresh behavior remain pending tablet testing.

## Direct finger selection — 0.33.1-dev.6 (code 60)

A short one-finger tap on a figure opens its existing move/resize/delete
controls. Hit testing follows the current page transform and chooses the
topmost figure, ahead of PDF links beneath it. Scrolling, pinching, broad palm
contacts and intervening handwriting cancel the tap candidate; stylus contacts
retain their writing/focus behavior.

Tapping a figure in an inactive reader first activates its note, then opens the
controls after native readiness. Stable page and figure IDs are checked again
after handoff. The editor captures the tapped page number explicitly, so a
partly visible page is edited rather than the previously centered page. The
existing figure list remains available for accessibility. Stored formats and
native handwriting rendering are unchanged.

The full host suite passed **246,324 assertions in 60 suites**, including
zoom/pan/scroll hit transforms, overlap order, stale identities, correct-page
editing and reader touch guards. Debug/Release lint and builds passed. The
signed artifact is `build/releases/scriflow-0.33.1-dev.6.apk`, verified against
`release-signing-certificate.sha256` using the permanent release certificate.
APK SHA-256:

```text
df7112265a14475bb3d9c44e4f9465a9f09f3fcb5050f2710027f4f50c2a089d
```

The owner chose to continue without the tablet. This APK has not been installed
or physically gesture-tested; finger/optical tracking measurements remain
pending. Public stable **0.33.0** is unchanged.

## Notes tray anchor — 0.33.1-dev.7 (code 61)

The notes tray now uses the notes button's actual screen position rather than
the toolbox's starting edge. Its first icon aligns with that button in either
toolbox orientation, including a scrolled strip. The existing screen-edge
clamping and opposite-side fallback remain in place.

All 246,324 host assertions in 60 suites and Debug/Release lint/builds passed.
`scripts/build-release.sh` prepared `build/releases/scriflow-0.33.1-dev.7.apk`
and verified its permanent signing certificate. No tablet installation or
physical placement check was performed, following the owner's instruction to
continue without the device.

## Minimized-note icons — 0.33.1-dev.8 (code 62)

Every minimized note now uses a vector page-icon button instead of numbered
text. Its tooltip and accessibility label retain the notebook name, and tapping
it restores that specific pane. The plus and note icons share 32 dp controls
with 4 dp gaps in either orientation; the tray dimensions follow those sizes.

All 246,324 host assertions in 60 suites and Debug/Release lint/builds passed.
`scripts/build-release.sh` prepared `build/releases/scriflow-0.33.1-dev.8.apk`
using the verified permanent release signer. Physical tablet checking remains
pending under the owner's instruction to continue without the device.
