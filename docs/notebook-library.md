# Notebook library — 0.10

## Behavior

The launcher now opens a library of notebooks. Tap a name to open it, use
`+ Notebook` to name and create an empty notebook, and use its `Rename` action
to change its title. Create opens the notebook for writing immediately.
`Books` in the editor returns to the library. Android Back also returns to the
previous library screen. Each notebook retains its own pages, paper choices,
last selected page, and process-local per-page undo/redo history. A cold launch
shows the library; opening a notebook restores that notebook's last page.

Existing pages appear inside `My Notebook`. No files are moved or copied.
The editor's second line shows a shortened notebook name and `Page X of Y`;
its fixed single-line height preserves the writing viewport. Library rows
truncate long titles, while Rename exposes the full title.

Names are trimmed, single-line Unicode text up to 80 code points. Empty,
multiline/control, and invalid Unicode names are rejected. Duplicate names are
allowed: notebook identity uses a stable numeric ID. Limits are 200 notebooks
and 1,000 pages per notebook. Deletion, reordering, export, and cloud sync remain
outside this milestone.

## Storage and recovery

The legacy notebook remains in `files/notebook/` (original `page.nbk`, `book.nbi`,
and `pages/N/` files). Additional notebooks live in
`files/notebook/notebooks/2/`, etc., each using the existing book/page formats.
`files/notebook/library.nbl` records notebook IDs, titles, and selected ID.
Names never form filesystem paths. Complete catalog absence yields `My Notebook`
by reference without rewriting old page or index bytes.

The catalog is a bounded, versioned, UTF-8 CRC snapshot with synced temporary
publication and a backup. Corrupt primary data is preserved and fails closed.
The library worker validates and syncs a target before publishing its selection;
failed selection or creation retains the previously open notebook. Retry accepts
only the exact acknowledged or attempted catalog state, then completes the same
pending operation without duplicating a notebook. A new notebook's durable empty
first-page file precedes catalog publication; it gets its coordinate dimensions
from the native surface when first opened. Valid empty orphan creation files can
be adopted after interruption; unexpected or corrupt orphan data is preserved.
Hardware power-loss durability is not established by these app-interruption tests.

Cached `NotebookBook` objects retain independent page models and histories on the
process-wide Session worker. Completed pen UPs enter that queue before returning
to the library. Missing/corrupt additional notebooks do not become silent blank
replacements. Save errors retain memory and pause writes while offering Retry.
Catalog listing itself does not open notebook data or hide the list on a book
error. After a process restart, durable pages/positions remain and histories reset.

## UI and validation

New controls use the existing static black-on-white buttons. Naming and error
dialogs disable background dimming and window fades; transitions between library
and editor disable activity animations. Naming-dialog submission guards remain
busy until the queued operation finishes, including the asynchronous dismissal
callback. Generation guards ignore stale results after leaving a screen.
Normal pen rendering and the in-place edit/page path remain unchanged.

All 2,725 host assertions pass. Tests exercise catalog encoding, corruption and interruption handling,
legacy byte preservation, independent book/page positions and histories,
creation, rename, selection, and exact-state retries. The complete API 29 app
builds offline with versionCode 11/versionName 0.10 using the existing signing
key. No ADB/device access was used. Host tests and compilation cannot establish
physical display behavior or stylus latency for the new navigation flow.

Install `build/ink-notebook-0.10.apk` over the existing app, keeping it installed.

1. Open My Notebook and confirm old pages and its last selected page are present.
2. Return using Books, create another notebook, and write a distinct word.
3. Add a page and change its paper; open My Notebook again and confirm isolation.
4. Rename either notebook and confirm writing/positions are unchanged.
5. Switch between them, test each page's Undo/Redo, then reopen the app and check
   both notebooks retain their pages and positions.
6. Check Books, Open, New, Rename, and dialog buttons for unwanted flashing, and
   confirm continued handwriting remains smooth.
