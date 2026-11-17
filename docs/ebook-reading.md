# Books and adjustable text

Scriflow 0.42.0-alpha.2 adds an offline **Books** shelf alongside the existing
PDF notebooks. Open **Books → Open / import book**, open a supported file from
**Files**, or use Android's **Open with** / **Share** menu. Import copies the
original into private app storage; moving or deleting the original tablet file
does not break the imported book. Identical imports reuse the same reading data.

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

- **Aa:** choose the book font, serif, sans serif, monospace or an imported
  TTF/OTF font; change size, weight, line spacing, margins, alignment, columns,
  and page turns / continuous scrolling. Fixed books offer page and width fit
  plus explicit zoom levels. Settings are remembered separately for each book.
- **Contents:** navigate the book's chapter hierarchy or fixed-page list.
- **Search:** find text throughout the book and jump to a result. Scans need an
  existing text layer; Scriflow does not perform OCR.
- **Bookmark:** mark the current reading position. **Notes** lists bookmarks,
  highlights, text notes and handwritten-note links, with actions to return to
  the passage, edit a text note, open handwriting or remove an annotation.
- Select text with a finger to **Highlight**, add a **Text note**, **Handwrite**,
  **Copy** or look up a word in the installed **Eudic** dictionary.
- Bottom arrows turn pages; the curved arrows return from internal links.
  Tap the reading percentage to jump through the book. Page-turn gestures and
  arrow keys are also available. Use the buttons while panning a zoomed fixed page.
- **More → Focus reading** hides controls. Android Back restores them; it also
  dismisses a selection or panel before leaving the book.

Progress and annotations use text locations, so adjusting the font or rotating
the tablet does not depend on a screen page number. Book images and MathML are
rendered by the bundled browser engine. Fonts and book quality still determine
which glyphs and layouts are available.

## Handwriting with adjustable text

Select a passage and tap **Handwrite**, or choose **More → Handwritten note at
this position**. Scriflow creates an ordinary native handwritten notebook and
links it to that text location. All existing notebook pen, eraser, lasso and
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
notes through Android's destination picker. JSON includes book identity, text
anchors, bookmarks, notes and native notebook IDs; importing that JSON is not
implemented in this alpha. Handwriting remains in ordinary notebooks and can be
backed up as `.inkbook` files from the bookshelf. Those backups do not include
the original ebook or its reading state. Keep original books and exported
annotations outside private app storage before uninstalling or clearing data.

Removing a book asks for confirmation and removes its private original, font
and reading data. It keeps the tablet's original file and linked handwritten
notebooks. Removing an annotation also keeps its handwritten notebook.

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
`python3 scripts/check-ebook-controls.py` verifies actual touch selection of font
and size through Android choice dialogs and persistence after leaving the book.
Binary fixtures are downloaded from pinned upstream commits and checksum
verified; they are excluded from the APK. Physical Huawei stylus, e-ink refresh,
large personal libraries and representative owner books still need acceptance.
