# Page management — 0.29

The overview's **Manage** mode and thumbnail long-press open page actions:
move earlier/later/to a final position, duplicate, insert blank before/after,
and delete. Delete requires confirmation and the last page cannot be removed.
Separate **Undo page change** / **Redo page change** controls retain 50 changes
per notebook in this process. New page changes clear page redo; ink edits do
not. A moved/restored page keeps its per-page ink history while the process lives.
The UI uses static controls with no drag animation, ripple, dimming or blinking
number-field cursor. Native handwriting stays paused while the overview is open.

## Storage and compatibility

`NotebookIndex` reads the old fixed-length version-1 index without changing it.
Version 1 implies storage slots 1…N. Ordinary legacy append/navigation keeps that
format. The first management operation publishes a version-2 index with payload:

| Field | Encoding |
| --- | --- |
| Active page count / selected display position | Two signed 32-bit integers |
| Next never-reused storage slot | Signed 32-bit integer |
| Page-order generation | Positive signed 64-bit integer |
| Active storage slots in display order | One signed 32-bit integer per page |

The existing magic, version, length/complement, CRC32, fsync and temporary/backup
rename framing remains. Slot numbers must be unique, positive, and below the
next-slot value. Active count remains 1…1,000. Unknown versions, invalid mappings,
truncated/corrupt primaries, and unexpected disk states fail closed. A retained
backup is used when a primary is absent, not as a silent replacement for a corrupt
primary. Earlier APKs reject the version-2 index; keep a backup before upgrading.

Slot 1 is still the root `page.nbk`; other slots use `pages/<slot>/page.nbk`.
Display positions resolve through the index in every book operation, including
preview, editing, history, navigation and export. Document caches use slot keys,
so reordering retains the same immutable samples and process-local ink history.
No existing page files are moved or rewritten by an order change. Duplicate gives
the new page and all strokes fresh IDs while retaining pressure, event time,
coordinates, style and paper. Inserted blanks inherit the source paper and size.

Removed pages and undone insertions remain in private storage. Session history
can reference them, and predecessor indices remain recoverable. This version
does not implement reclamation/secure erasure of those files or persistent page
history. Deleted pages are absent from current overview, PDF and `.inkbook`
backups; restoring a backup gives exactly its active ordered pages.

## Publication and retry

The single document writer first validates/syncs accepted ink and the previous
scroll anchor. A duplicate/insert is written and synced in a fresh unused slot,
then the complete new order is published atomically. All target slots must exist
before publication. A process interruption leaves either the previous complete
order or the new complete order, with no half-renumbered ink files.

An in-process pending command retains exact before/after index snapshots and any
new page identity. Retry accepts only those two states and validates a prepared
page against its complete intended snapshot. Ambiguously completed publication
cannot duplicate the page a second time. Unrelated disk changes are preserved
and refused. After a process restart, abandoned prepared slots are skipped, never
adopted or overwritten. A `page.created` hint allows even the legacy toolbar Add
path to skip a slot staged by an interrupted first management edit. The hint
grants no permission to adopt/overwrite its data.

Every order edit, undo and redo advances a generation. Version-2 `scroll.nbs`
adds that generation after the old position payload. A valid scroll record for
another generation defaults to the new selected page at fraction zero, without
rewriting the record on read. Navigation within one order preserves fractional
scroll saves. This avoids a two-file transaction or an old anchor referring to
a deleted position. Existing version-1 anchors remain unchanged until needed.

The overview cancels old thumbnail requests and clears its bounded cache before
a management edit. Close/Back is gated while a command is running; Activity pause
can still dismiss it. A covered editor with obsolete page numbers does not save
its old anchor during backgrounding. Once the overview closes, editor page/tile
caches are cleared, the current layout is loaded, and canonical native tracks
are restored before handwriting resumes. Export jobs retain their existing
writer-generation checks so a page change cannot produce a mixed-order export.

## Verification and tablet acceptance

Host regressions cover v1 compatibility, exact unchanged ink files on moves,
identity/pressure/style preservation, per-page ink history across moves, inserts,
deletion of the first/last visible page, a managed non-root singleton, separate
undo/redo, new-page limits, invalid positions, corruption/truncation of v2 indices,
order-bound scroll records, interrupted rename states, rejected unrelated index
changes, retry after failed/ambiguously completed publication, orphan skipping,
backups in display order, notebook isolation and randomized command sequences.
The earlier lasso regressions remain part of the full suite.

After installing the development APK with a matching signing identity, use a
disposable notebook with distinct writing on several blank/lined pages. Try each
action, first/last destinations, Delete/Cancel, Undo/Redo, later ink edits, scrolling,
toolbar Add, overview jumps, close/reopen, background/resume during an operation,
and PDF/backup order. Confirm lasso and writing still resume smoothly. The owner
reported that page management works well in dev.1 and subsequently accepted
the signed dev.2 APK for release. No item-by-item checklist was supplied;
see the [device record](device-testing.md).
