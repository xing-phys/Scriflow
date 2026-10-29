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
