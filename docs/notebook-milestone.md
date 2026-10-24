# One-page notebook milestone

## Scope

The user chose blank/lined notebooks as the main use case and authorized this
milestone: one page with native handwriting, template choice, autosave, and
editable restoration. The next product features are separate from this
persistence gate.

## Data and display

`NotebookPage` is an immutable, Android-independent model. It stores the first
canvas dimensions as fixed logical page pixels, a blank/lined template, and
strokes with pen type, width, color, and x/y/event-time/pressure points.
Later viewport sizes map to this coordinate system. The Activity is portrait
for this milestone. Pressure is retained without assuming a maximum of one.

`NotebookInk` uses the same native `HWEditView`, display policy 3, RGBA8888
surface, stylus acceptance, black ballpoint, and native handwriting state proven
by the probe. The native editor owns live ink. Its internal listener remains
registered; the app observes pen state and native edit completion.

The pen-UP event alone is too early to assume the editor has committed its
track. The adapter waits for both a native edit and its matching pen-UP time,
then synchronously emits the completed model stroke. Either listener may run
first. This avoids a posted completion being lost when the Activity immediately
pauses. UP and native track completion can also arrive as separate queue messages.
If a pause occurs between them, primitive input buffers preserve a received,
completed UP using the configured pen style. Partial and cancelled strokes are
discarded, and repeated stops cannot duplicate a completion. MOVE events allocate
no duplicate app point objects, redraw no app UI, and perform no disk I/O. Normal
completed points are copied from the native track. Handwriting starts disabled
at widget construction until the saved document and drawing surface are ready.

Restoration creates native points/tracks through the editor's track creator,
then calls `setTrackList`. Template backgrounds are opaque viewport-sized
bitmaps. Ruling is rendered once when preparing the page. Restored vector
coordinates, pressure, timestamps, and pen styles are independently checked by
the isolated on-device self-test. Native undo history is not persisted.

## Autosave and lifecycle

One process-wide `NotebookSession` owns a serialized background worker. Every
completed stroke appends a checksummed record and syncs it. A recreated Activity
queues its load behind outstanding writes. The session and its pending writes
survive Activity recreation; process death is handled by the durable file.

The native widget has private partial-stroke state with no exposed reset API.
The Activity releases it when paused and restores a fresh widget on resume.
Completed strokes are saved; interrupted partial strokes are not document edits.
Templates are saved through the same worker, preserving strokes. Dialogs pause
ink and restore a fresh surface afterward.

Optional save-status updates wait for one second of pen inactivity and do not
set text again when it is unchanged. There is no periodic clock, counter, or
per-point refresh in the notebook UI. A static saved-stroke count changes only
after a completed save and a pen-idle interval, providing visible confirmation
without a live counter while writing. Errors stop handwriting and show an
explicit retry action. Pending completed strokes remain in the process's model.
Retry reloads the saved file, verifies its exact stroke prefix, and appends only
missing strokes; this avoids duplication after an ambiguous failed sync.

## On-disk record

The app-private directory is `files/notebook/`. `page.nbk` is the current page;
`.bak` retains the preceding metadata snapshot and `.tmp` is a replacement being
prepared. These files and private handwriting exports are not committed.

The format is versioned binary data using Java data-stream big-endian encoding:
a magic/version header, then snapshot or append-stroke records. Each record has
a length, its bitwise complement, payload, and CRC32. Length and count limits
are checked before allocation. The initial snapshot includes dimensions,
template, and existing strokes. Subsequent records append one completed stroke.
Changing paper publishes a synced replacement snapshot using same-directory
renames and retains the previous snapshot as a backup.

Loading ignores only an incomplete final append after a valid snapshot. Before
the next append, its incomplete tail is truncated to the last complete record.
A complete record with invalid checksum, length, version, or content stops
writing and preserves the source file. A failed primary is not silently replaced
with a blank page or an older backup. The durability target tested here is app
force-stop/interrupted writes; sudden hardware power loss is a separate check.

## Acceptance

1. Write distinct physical strokes, including a dot and curve, and wait for Saved.
2. Confirm the durable document has the completed native stroke data.
3. Force-stop the app, bypassing normal lifecycle saving, then reopen.
4. Confirm paper choice and vector tracks restore, then continue writing.
5. Switch blank/lined paper without losing strokes, and repeat reopening.

Host corruption/interruption tests and synthetic hardware restoration support
these checks, but do not substitute for real stylus input or optical latency
observations. Current device results are recorded in `device-testing.md`.
