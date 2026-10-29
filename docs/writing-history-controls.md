# Writing history controls — 0.33.2-dev.2

Undo and Redo are next to the top-right time/battery/Wi-Fi display. They are
removed from the toolbox, which now has ten controls including its grip and
orientation button. Each history icon is visible only when the current page
has its corresponding action. Hidden icons use `GONE`, leaving no blank slot
or touch target.

Pen writing arms history visibility. Changes are published between strokes,
after the current page's history is available. Eraser and Lasso contacts do not
count as resumed writing. Active-note finger input, accepted contacts in other
readers, menus, toolbox/tool changes, page navigation, window changes and leaving
the Activity clear the writing context. Save, layout and native-ready callbacks
can update availability but cannot re-arm that context.

Undo/Redo actions preserve the writing context, allowing repeated Undo followed
by Redo. Otherwise the first Undo would hide the only route to Redo, and a fresh
stroke would clear that redo history. This exception applies only to the two
history actions; ordinary interactions require new Pen writing to show them.

Visibility updates never alter clipping during an active native stroke. Between
strokes the writer pauses, status bounds update before window placement, exposed
ink is repaired using the existing track list, and input resumes. This avoids a
framebuffer clear/rebind and guards reentrant errors. Internal viewport cleanup
preserves the writing context; user operations clear it. Pausing the Activity
also clears stored icon visibility before the next resume.

Validation passed **246,356 host assertions in 62 suites**, including 17 new
checks executing the production writing/history callbacks with queued native
and writer adapters. They cover conditional actions, repeated Undo/Redo,
navigation dismissal, stale callbacks, no mid-stroke clipping, pause/repair/
resume ordering, empty histories, Lasso/Eraser input and reentrant failures.
The final history callbacks were rechecked after limiting save acknowledgements
to history updates rather than refreshing every tool. Debug/Release lint/builds
and permanent-signature verification passed.

The signed candidate is `build/releases/scriflow-0.33.2-dev.2.apk`, versionCode 66,
prepared through `scripts/build-release.sh`. Its adjacent checksum verifies the
delivered bytes. No device installation or physical acceptance was performed.

The owner requested continuing without the tablet. Physical placement, input
latency and e-ink refresh acceptance remain pending. Saved history semantics,
stored data and firmware settings are unchanged. Public stable 0.33.1 is unchanged.
