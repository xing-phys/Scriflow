# Multiple-page notebook — 0.9

## User behavior

`‹` / `›` navigate to the previous/next page. `+ Page` appends an empty page at
the end, using the current page's blank/lined paper. The second line shows
`Page X of Y`. Each page saves its own paper and strokes and retains independent
undo/redo history while the process stays alive. A new process restores the
last selected page with fresh history. The current limit is 1,000 pages.
Deletion, reordering, separate notebooks, and export remain future work.

The toolbar fits in its existing row; the canvas height remains unchanged.
Blank/Lined now toggles directly rather than opening a paper dialog. All toolbar
buttons use a static white background and black text with no ripple, elevation,
state animator, or focus highlight. Logical availability and action guards
prevent invalid or concurrent actions without changing all buttons' appearance.
The selected tool still changes its check-mark label. Dialog action buttons use
the same static styling, and dialogs disable background dimming and window fades.
Save status updates after pen idle; fast edits no
longer force an intermediate `Saving…` message.

The previous toolbar flash was consistent with the theme's press animation and
the Activity disabling/re-enabling every button during each edit. These sources
are removed in code. Physical panel behavior still needs user confirmation;
page changes necessarily change page content.

## Data and failure handling

The existing page stays at `files/notebook/page.nbk`, including its original
backup/journal behavior. It is Page 1 and is not copied or migrated. Additional
pages use `files/notebook/pages/2/page.nbk`, etc., with the same version-1 format.
`book.nbi` stores page count and current page as a small versioned CRC snapshot.
It publishes via synced temporary file and same-directory rename with backup.
Complete absence of index files means the legacy one-page notebook. A corrupt
primary is preserved and fails closed rather than silently resetting page order.

`NotebookBook` owns cached `NotebookDocument` instances on the Session worker.
Each document retains its own model and history. Navigation follows all queued
completed pen UPs, confirms outgoing durability, validates the target, and saves
the selected index before changing the active document. Indexed missing/corrupt
pages do not become blank replacements. Save failures retain the previous
visible page and its in-memory work. Retry accepts only acknowledged or exactly
attempted index states and completes the pending navigation without duplicate
page creation. Per-page exact-state recovery remains in use.

A new page's first durable snapshot includes dimensions and template together,
before its index entry is published. After a crash between page and index
publication, an exact empty unindexed page of the expected dimensions/template
can be adopted without rewriting it. Nonempty, corrupt, or unexpected orphan
data is preserved and rejected. This targets app interruption/force-stop;
sudden hardware power loss has not been tested.

## Rendering

The writing surface is paused across each navigation operation. Completed UPs
are enqueued before navigation. Normal page/paper changes reuse the native
surface and its mutable paper bitmap: the bitmap is repainted offscreen for a
template change, then paper and replacement tracks are posted together. No
separate paper binding introduces a background-only frame. No-hit erasing and
unchanged info-dialog dismissal skip content redraws. An unsettled vendor track
or changed surface geometry still uses a fresh widget as a safety fallback.
Native pen handwriting remains unchanged; no per-MOVE UI or disk work is added.

## Offline validation and device acceptance

All 2,099 host assertions pass. Tests cover the index, page isolation/history, legacy compatibility,
atomic new-page initialization, interrupted creation, last-page restoration,
navigation retry, and missing/corrupt data preservation. The API 29 app builds
offline with versionCode 10/versionName 0.9 and the existing signing key.
The optional synthetic native check also exercises blank/lined page switching
while checking surface/bitmap identity and exact native vector readback. It was
compiled but not run on a device for this release. No ADB access was used.

Install `build/ink-notebook-0.9.apk` over the existing app; do not uninstall it.

1. Confirm old writing is still on Page 1. Add a short word and wait for Saved.
2. Tap `+ Page`, write on Page 2, toggle its paper, and return to Page 1.
   Check that both writing and paper stay separate.
3. Try Undo/Redo on each page, switch away and back, and check independent history.
4. Switch pages immediately after a stroke, reopen the app, and check the last
   page and its saved writing. Repeat with three or more pages.
5. Tap each toolbar button and use eraser/undo/redo repeatedly. The toolbar
   should stay visually steady apart from intended label/content changes.
   Check that the pen remains smooth after navigation.
