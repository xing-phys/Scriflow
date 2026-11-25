# Books and adjustable text

Scriflow shows ebooks directly on the homepage alongside PDF
notebooks and handwritten notes, in the same list or thumbnail layout. Existing
imports appear automatically; there is no separate Books folder and no reimport
is needed. Use the **Import book** homepage icon, open a supported file from
**Files**, or use Android's **Open with** / **Share** menu.

Import copies the original into private storage. Moving or deleting the tablet
file does not break the imported book. Identical imports reuse its reading data.
The item's **⋮** menu renames it, moves it to a normal folder, or moves it to
**Trash**. Hold to select ebooks and notebooks together. Restore from Trash, or
confirm permanent deletion. Removing a folder keeps its items on the homepage.

| Files | Reading behavior |
| --- | --- |
| EPUB | Reflowable text, or fixed pages when specified by the book |
| MOBI, AZW, PRC, AZW3 | DRM-free MOBI and KF8 text |
| FB2, FBZ, FB2.ZIP | Adjustable FictionBook text |
| TXT, HTML, HTM | Adjustable local text; UTF-8, UTF-16 BOM, GB18030 fallback |
| CBZ | Fixed comic pages |
| DJVU, DJV | Fixed bundled DjVu pages; existing text layers when available |
| PDF | Existing **Import PDF** notebook workflow and direct handwriting |

The reader does not bypass DRM. Encrypted MOBI/PalmDoc and EPUB encryption other
than supported font obfuscation produce an error asking for a DRM-free copy.
KFX, AZW4, CBR/RAR, DOC/DOCX, CHM and indirect DjVu component sets are outside
this alpha. This is a first group of common KOReader-style features, not full
KOReader compatibility.

## Reading controls

Reading fills the screen. The same floating corner islands and movable icon
toolbox used by notebooks sit above the page, without reserving header/footer rows.

- The **top-left bar** holds the book title, previous/next page icons, the reading
  percentage and the more icon. Tap the title to return to the homepage, or the
  percentage to jump through the book.
- The **top-right bar** shows the clock, battery and Wi-Fi. Link history icons
  appear there when a return/forward destination exists.
- Tap the **toolbox grip** to expand/collapse its icons, or drag the grip to move
  it. The layout icon switches between a horizontal and vertical toolbox.
  Position and collapsed state are remembered. A new reader starts collapsed.
- The **text/layout icon** opens book/serif/sans/monospace or imported TTF/OTF
  fonts, size, weight, spacing, margins, alignment, columns and page/scroll mode.
  Two columns are a paginated spread; choosing continuous scrolling returns to
  one column. Fixed books offer fit and zoom. Settings are remembered per book.
- Use the **contents**, **search**, **bookmark** and **notes** icons for navigation,
  highlights, text notes and handwritten-note links. Scans need an existing text
  layer for search; Scriflow does not perform OCR. Contents opens around the
  current heading, with disclosure arrows for expanding and collapsing branches.
- Selecting text reveals contextual **highlight**, **text note**, **copy**,
  **dictionary** and **clear selection** icons in the toolbox. The pen icon opens
  a handwritten note linked to the selection or current position.
- The **focus icon** hides controls. Android Back or the small clock restores
  them; Back also dismisses a selection or panel before leaving the book.
- Tap the right half or bottom half of the reading area to
  advance by a page in page-turn or continuous mode; tap the upper-left quarter to go back. Links and text selections keep
  their normal behavior. Swipes and arrow keys remain available. Use page buttons
  while panning a zoomed fixed page. Icons have accessible names and explanatory labels
  in their menus; text labels remain for chapter titles and settings choices.

Progress and annotations use text locations, so adjusting the font or rotating
the tablet does not depend on a screen page number. Book images and MathML are
rendered by the bundled browser engine. Fonts and book quality still determine
which glyphs and layouts are available.

## Handwriting with adjustable text

Select a passage and tap the **pen icon**, or use it without a selection to
link a note to the current reading position. Scriflow creates an ordinary native
handwritten notebook and links it to that text location. All existing notebook pen, eraser, lasso and
page tools remain available there. Android Back returns to the book.
**Notes → Go to text** follows the passage after a font/layout change;
**Open handwriting** reopens the linked notebook. Repeating Handwrite at the
same anchor opens the existing notebook.

For direct writing on the displayed text, choose **More → Write on a fixed
copy of this view → Create copy and annotate**. Scriflow captures the visible
reading area, makes a one-page PDF and opens it in the native notebook editor.
The copy has the current layout at screen resolution. It is not a whole-book
conversion, and later changes to the ebook font do not change that PDF.
Its link in Notes returns to the original reading position.

This design follows the owner's preference for passage-linked handwriting plus
optional fixed copies. It avoids tying freehand coordinates to temporary ebook
page numbers. It does not place an editable ink overlay inside flowing paragraphs.

There are precedents for handwriting in reflowable books. [Kobo documents
handwriting with its stylus](https://help.kobo.com/hc/en-us/articles/1500001927562-Write-notes-in-your-book-with-Kobo-Stylus),
including markers and returning to the original display settings after layout
changes. [Kindle Scribe's Active Canvas](https://www.aboutamazon.com/news/devices/kindle-scribe)
instead anchors a handwritten area while text flows around it. Scriflow's
linked native notebooks implement a simpler model with independent writing space.

## Storage, export and limits

**More → Export reading notes** saves JSON annotations/settings or Markdown
notes through Scriflow's Files browser. JSON includes book identity, text
anchors, bookmarks, notes and native notebook IDs; importing that JSON is not
implemented in this alpha. Handwriting remains in ordinary notebooks and can be
backed up as `.inkbook` files from the bookshelf. Those backups do not include
the original ebook or its reading state. Keep original books and exported
annotations outside private app storage before uninstalling or clearing data.

Moving an ebook to Trash retains its private original, custom font and reading
data. Confirmed permanent deletion removes those private files, while keeping
the tablet's original file and linked handwritten notebooks. Removing an
annotation also keeps its handwritten notebook. Custom shelf names remain after
reopening a book; publisher metadata does not overwrite them.

Imports are limited to 2 GiB, custom fonts to 16 MiB, saved reading state to
2 MiB and annotations to 2,000 per book. ZIP entries are limited to 32 MiB before
decompression; individual buffered resources are limited to 64 MiB. Split very
large text chapters or images if a limit is reached. Bundled DjVu books over
32 MiB use random-access page loading; full-text search and text selection are
unavailable in that mode. Smaller DjVu files can expose an existing text layer.

Book rendering is offline. Book scripts, embedded browsing frames, forms and
network content are blocked. External HTTPS links offer an explicit opening in
the separate Browser. Ebook documents do not receive the native file-reading
bridge. The engine shares the app's Gecko runtime but uses a separate trusted
extension and a separate book-storage executor.

## Implementation and validation

The MIT-licensed [foliate-js](https://github.com/johnfactotum/foliate-js) engine
handles the ebook formats; the MIT-licensed [djvu-rs](https://github.com/matyushkin/djvu-rs)
scalar WebAssembly decoder handles DjVu. Versions, dependency hashes, licences
and local patches are pinned in `renderer/ebooks/upstream.json`. All runtime
assets are bundled; opening a book downloads no code or fonts. The existing PDF
engine and notebook storage formats are retained.

Original books are hashed and stored immutably. Reading data uses CRC-framed
snapshots with recoverable previous/interrupted copies. Handwriting links are
saved before opening the native notebook. A book has no access to arbitrary
native paths; bounded range reads serve only its original and custom font.

Run `./scripts/test.sh` for host regressions, `node scripts/test-ebooks.cjs` for
real format parsing/layout/security checks in Chromium, and
`python3 scripts/check-ebooks.py` with the debug APK on an Android emulator for
the real Gecko reader, reflow/reopen, custom font and native notebook workflows.
`python3 scripts/check-ebook-controls.py` verifies full-screen reading, floating
controls, toolbox movement and actual font/size selection.
`python3 scripts/check-ebook-home.py` checks direct opening, rename, selection,
folders and Trash through the Android UI while preserving original/state bytes.
Binary fixtures are downloaded from pinned upstream commits and checksum
verified; they are excluded from the APK. Physical Huawei stylus, e-ink refresh,
large personal libraries and representative owner books still need acceptance.
