# PDF notebooks, outlines, and view history — 0.31

Choose **Import PDF** in the library to open the system file picker directly.
Select a PDF and wait for import to finish; the imported notebook opens
automatically, named after the file. There is no separate Choose PDF or success
confirmation screen. Cancelling the picker returns to the library. Import
failures offer an explicit retry. The app keeps a private, unchanged copy of the
source file; moving or deleting the selected external file does not affect the
notebook.

From **0.33.0-dev.5**, outstanding picker results and in-process import jobs
belong to retained import state, so landscape recreation does not launch a second
picker/import. Completion opens the note once, only from a resumed, attached
Activity. Recoverable memory exhaustion in PDF preparation/publication reports
an import error with retry controls rather than escaping the worker. If the
process is killed during import, return to the library to check for the published
note before retrying. The reported exit after selecting a PDF
in landscape still needs physical confirmation on the supported tablet.

Write, sketch, erase your ink, undo/redo, select ink with the lasso, and manage
pages using the ordinary notebook tools. The PDF itself is a background;
erasing affects added strokes. PDF pages can have different sizes and rotations.
Adding or inserting a blank page creates ordinary writing paper; duplicating a
PDF page retains its PDF background and copies its ink.

Tap a PDF link with a finger. Internal links navigate within the notebook;
web links show a confirmation with their target before opening an installed
browser. Dragging continues to scroll and
the stylus continues to write over links. Use **Previous view** and **Next
view** to return after following a reference or jumping to an outline entry.
Scrolling, panning, zooming, page arrows, overview page jumps, and adding pages
do not create history entries or clear an existing forward path.
Internal GoTo and named destinations, web/URI links, and standard PDF page
navigation actions are supported. Remote-file, embedded-file, and JavaScript
actions remain in exported PDFs but are not activated inside the notebook.
View history lasts for the current editor instance; closing it or recreating
the Activity clears history.

The **Outline** icon opens the notebook's headings. Imported PDF bookmarks
populate it automatically. Add your own heading at the current position, edit
its title or nesting, or remove it. Destinations use stable page identities, so
reordering pages keeps headings attached to their pages. A heading whose page
was deleted becomes available again if the page deletion is undone.

**Export PDF** preserves source PDF content and adds pressure-shaped vector
ink. It keeps original PDF page resources, text, links, named destinations,
and catalog data instead of rasterizing the source into a new document.
Original page content remains selectable and searchable in a PDF reader.
Page-management edits are reflected in the export. A link to a page deliberately
deleted from the note cannot navigate to a visible page in that note.

Use **Backup** for an editable `.inkbook`: the original PDF bytes, ink, page
order, and note outline are included. Restore creates a separate notebook.
Backups remain unencrypted. This version writes page snapshots version 5 and
backup archives version 2; it reads earlier formats, but earlier app versions
cannot read these new files. Keep independent backups before updating.

PDF import is limited to 256 MiB and 1,000 pages. Password-protected PDFs need
an unlocked copy. Large PDF memory use, picker behavior, PDF ink restoration,
and sustained stylus performance require testing on the supported tablet.

Implementation: `NotebookPdfEngine` inspects and exports with PDFBox Android;
Android's `PdfRenderer` rasterizes visible 512-pixel tiles at the current zoom.
The renderer and current page stay open between tiles; this display cache never
replaces or rasterizes the original source PDF. A bitmap-only source page still
has the resolution of its original image. In-app text selection is not yet
implemented; exported PDF text remains selectable in a PDF reader.
The immutable `source.pdf`
is stored with each PDF notebook, and page snapshots reference its one-based
source page. `NotebookOutline` stores checksummed, atomic outline metadata
with page UUID destinations. Rendering, import, and export run outside live
pen callbacks. No network or broad storage permission is added.

Imports publish one index for the full page count and avoid duplicate page
syncs. Up to four workers create independent staged page files; each file is
synced, and all writers finish before publication or cleanup. A checksummed,
disposable `navigation.cache` supplies page geometry and
UUIDs without decoding every stroke on open; `source.pdf.metadata` caches PDF
geometry and outline metadata. Link annotations load only for viewed pages.
Cache damage causes a rebuild and cannot replace missing or damaged ink.
Completed ink retains the durability barriers used by normal note saves.
See [ink capture geometry](ink-capture-scale.md) for new-stroke consistency and
the limit of historical files that never stored their original capture scale.
