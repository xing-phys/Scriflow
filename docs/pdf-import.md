# PDF notebooks, outlines, and view history — 0.31

Choose **Import PDF** in the library, select a PDF in the system picker, and
wait for import to finish. It appears as a separate notebook, named after the
file. The app keeps a private, unchanged copy of the source file; moving or
deleting the selected external file does not affect the notebook.

Write, sketch, erase your ink, undo/redo, select ink with the lasso, and manage
pages using the ordinary notebook tools. The PDF itself is a background;
erasing affects added strokes. PDF pages can have different sizes and rotations.
Adding or inserting a blank page creates ordinary writing paper; duplicating a
PDF page retains its PDF background and copies its ink.

Tap a PDF link with a finger. Internal links navigate within the notebook;
web links open through an installed browser. Dragging continues to scroll and
the stylus continues to write over links. Use **Previous view** and **Next
view** to return after following a reference or jumping to an outline entry.
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
Backups remain unencrypted. This version writes page snapshots version 4 and
backup archives version 2; it reads earlier formats, but earlier app versions
cannot read these new files. Keep independent backups before updating.

PDF import is limited to 256 MiB and 1,000 pages. Password-protected PDFs need
an unlocked copy. Large PDF memory use, picker behavior, PDF ink restoration,
and sustained stylus performance require testing on the supported tablet.

Implementation: `NotebookPdfEngine` inspects and exports with PDFBox Android;
Android's `PdfRenderer` supplies display backgrounds. The immutable `source.pdf`
is stored with each PDF notebook, and page snapshots reference its one-based
source page. `NotebookOutline` stores checksummed, atomic outline metadata
with page UUID destinations. Rendering, import, and export run outside live
pen callbacks. No network or broad storage permission is added.
