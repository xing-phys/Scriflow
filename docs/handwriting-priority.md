# Handwriting protection — 0.37.0-alpha.3

The owner reported occasional missing or delayed strokes compared with the
0.32/0.33 releases. A source comparison found identical native sample/commit
methods, pressure brush replay, palm/finger policies and asynchronous append
methods. Surrounding presentation changed: 0.33.2 introduced history controls
after pen-up, and 0.35.0-alpha.5 introduced ordinary View paper for Markdown with
redraws after pen-up/completion. These are plausible interference paths, not a
physically reproduced cause.

## Writing bursts and optional presentation

`NotebookWritingGate` runs on the main thread and owns only optional UI tasks.
A writing burst includes pen contact, all input reported by `hasPendingInput()`
and a **300 ms** quiet interval after the latest contact. A new DOWN cancels the
release callback. Pending native input is checked again at dispatch, including
the separate UP and native-commit observer orderings. No gate polling runs while
the pen remains down; an unsettled completion after pen-up is rechecked at 25 ms.

Each component retains one Runnable per kind of update. Repeated requests
coalesce, and the callback reads current state rather than replaying old frames.
One component runs per main-queue dispatch, with another input check before the
next. Closing/minimizing a component cancels its jobs; retiring a canvas clears
its callbacks, and Activity pause suspends presentation until resume. Counter
snapshots report requests, coalesced notices, executed jobs and pending owners;
there is no per-point logging or new MOVE work.

The interval defers secondary presentation. It never delays pen samples,
completed-stroke submission, writer acknowledgements or durable saves. The
optimistic viewport model still receives completed ink immediately, and native
live trails remain visible while View paper publication waits. The entire
`NotebookInk`, `NotebookSession`, pressure renderer and display policy remain
unchanged from 0.37.0-alpha.2.

## Components

- Automatic Undo/Redo/menu presentation and View-paper redraw requests wait
  across a burst. The existing native pause/repair sequence remains available
  after input settles when a control-layout change exposes ink. This alpha moves
  those interruptions out of short inter-stroke gaps; it does not remove every
  native pause or introduce a new firmware restoration algorithm.
- Zen seconds stop scheduling ticks during writing. Status keeps the latest
  battery/network state and catches up to the current clock after release.
- AI requests and their existing save ownership continue. Conversation widgets,
  JSON/message formatting and new WebView rendering wait; JavaScript results
  arriving during a burst cannot automatically scroll the chat. Polling pauses
  and its timeout excludes intentional writing waits. A render already running
  in WebView is not forcibly interrupted.
- Passive floating-note metadata, page/PDF/image completions and tool-hint
  expiry use the same gate. Reader gestures and retained focus-handoff frames
  keep their immediate path. Explicit popup/lifecycle cleanup cancels queued
  expiry instead of leaving a window alive.

User-requested Undo, tool changes, scrolling, resizing, note switching, lifecycle
events and error recovery retain their necessary writer barriers. Navigation
and editing can install their already-paused replacement paper immediately;
delaying that replacement would risk clearing native trails before the correct
paper is ready. The clear Markdown View composition remains attached throughout.

## Validation and remaining work

Fake-clock tests exercise 500 rapidly repeated strokes, a late native commit,
contact after enqueueing, input between queued components, coalescing and
pause/reset/close. Production-method fixtures additionally verify immediate
stroke append/preview and asynchronous acknowledgement while paper is held,
history/clock behavior across a burst, AI reply/JavaScript races, long writing
without spurious render timeouts, passive reader refreshes and popup expiration.
Existing real-file storage, geometry, renderer, browser and Android checks remain
required for the release.

These checks establish software ordering, not physical pen-to-ink latency. A
task already running on the main thread cannot be preempted by this gate, and
Android/Huawei composition and other applications still share the display.
This alpha deliberately retains the accepted native brush/AHW path and firmware
policy 13. It adds no manual waveform requests, second live brush or per-sample
framebuffer posts; the previously failed approach is recorded in
[native writing restoration](native-writing-restoration.md).

Tablet acceptance should compare rapid dots, short characters and long strokes
on ordinary and Markdown pages, with and without Zen, incoming AI replies and
loading inactive notes. Check that visible strokes survive scrolling/reopening,
and that explicit Undo/tool/navigation actions remain responsive. Compare
features in the same signed build and use disposable notes for historical APK
comparisons: older versions cannot open current format-8 files. Software callback
timestamps and screenshots do not replace external video for optical latency.
