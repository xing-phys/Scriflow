# Markdown blocks

Open the top-left three dots and tap the Markdown **M↓** icon to insert a block.
Type Markdown in the small editor beside the block. Tap its eye icon to finish
with the rendered view, or tap outside to finish with the code view. There is
no Apply/Cancel footer or separate document screen.

Tap a block with a finger to select it. Its nearby **code/eye** icon switches
between source and rendering. In code mode the nearby pen icon reopens the
source editor. Drag the block to move it, drag its lower-right handle to resize,
or drag its upper-right handle to rotate to any angle. Copy, Cut, Delete and
Paste use the existing figure controls. Blocks move across pages, including
edge scrolling, and each completed change is undoable. A cross-page move undoes
both pages together. Copying between notebooks retains editable Markdown.
Inactive split/floating notes use the same rendering and selection path.

## Rendering

The offline renderer supports CommonMark/GitHub-style Markdown: headings,
paragraphs, emphasis, strikethrough, quotes, lists, nested/task lists, tables,
links, inline/fenced code with highlighting, footnotes, definition lists,
subscript/superscript, and sanitized HTML. It also supports Mermaid diagrams,
`::: note`/`::: tip`/warning-style containers and `[!NOTE]`-style callouts.
HTML details are expanded in the static appearance. Language-specific Markdown
extensions are not a single universal standard; arbitrary plugins are not run.

MathJax 4 typesets inline `$…$` or `\(…\)`, display `$$…$$` or `\[…\]`,
LaTeX environments, MathML, and `math`, `latex` or `tex` fences. AMS equations,
labels/references, matrices, aligned expressions, physics notation, bra/ket,
chemistry, mathtools and common math alphabets use locally bundled SVG glyphs.
An invalid formula remains visible with its error; the original source is kept.
This is mathematical LaTeX support, rather than a complete LaTeX document engine.

Text/code fonts, complete CJK fonts, math glyphs and diagram code are packaged
inside the APK. No CDN, network permission or online service is required.
External Markdown image URLs become an explicit embedded-image placeholder;
embedded data images and separately inserted figures remain local. Block
appearances are static: links, task checkboxes, details and diagrams are not
interactive controls inside the note.

Source and rendered appearances are separate one-page vector PDFs. The note
uses native PDF tiles at its current display scale, including after zooming,
rotation and resizing; it does not upscale one fixed screenshot. Resizing
scales the block's existing layout, like resizing a figure. A short-lived
WebView compiles edited source; it is destroyed afterward and never replaces
the native handwriting canvas. PDF export imports the active appearance as a
vector form, retaining embedded fonts, text and formula paths.

From **0.35.0-alpha.3**, a note containing visible Markdown gets a stationary
presentation through the same ordinary View/PopupWindow composition used by the
clear drag preview. It draws the complete paper, figures and saved ink directly
on that canvas. It does not merely draw transparent text over the distorted
native bitmap, and saved handwriting remains above figures.

The complete native frame is retained underneath. Before pen input, scrolling,
modal edits or lasso manipulation, the added view hides and the existing native
writing path continues. It returns only after input is settled and the page is
ready; no per-pen-MOVE rendering or storage work is added. The window cannot
capture touches/focus, excludes status/tools/other note regions, and retires on
editor disposal. A drawing failure falls back to the intact native frame.
Existing saved blocks use this display path without recompilation or storage
changes; source, vector appearances, transparency and exports are preserved.

The earlier **alpha.2 raster-mode change did not resolve the owner's report**.
Its tests checked PDF tile mode/parity, not the final stationary presentation.
The owner confirmed that **alpha.3 makes stationary Markdown and the moving
block clear**. They also reported a brief return to the distorted native frame
at drag start and distorted other blocks throughout the drag.

From **0.35.0-alpha.4**, the figure overlay draws the complete clear paper scene
from idle selection through dragging, resizing, rotation and save restoration.
The stationary layer remains in place when entering figure selection. Other
blocks stay on the ordinary View canvas; the moving block replaces its original
position exactly once, including during cross-page moves and pending saves.
Saved handwriting is composed above the figures. Ordinary figure-only notes
retain their existing preview path. Native live writing is unchanged.
Automated tests cover the complete multi-block scene and transition callbacks;
the alpha.4 drag-transition result still needs physical tablet confirmation.

Each block accepts up to 128 KiB of UTF-8 source and 4 MiB per appearance.
Layout uses a 640 CSS-pixel width and a maximum height of 16,384 pixels.
Oversized blocks should be split. Existing per-page object and snapshot limits
still apply, and reported failures preserve the source draft.

## Durability and compatibility

Typing is saved to a private, checksummed recovery draft before compilation.
Outside dismissal commits source mode; rendering commits rendered mode. The
draft is retired only after the page writer acknowledges success. Reopening
the notebook recovers interrupted typing, including after activity recreation.
Drafts for another notebook are retained. If the original page/block has been
removed or independently changed, recovery creates a new block rather than
overwriting newer content. Typing drafts are local recovery files, not part of
editable notebook backups until the block is successfully committed.

**This build reads page versions 1–7 without rewriting them on open, and writes
format 8 on edits and in new editable backups. Older APKs, including stable
0.33.2, cannot open format-8 pages or backups. Keep independent editable backups
before updating; do not downgrade over edited notes.**

Format 8 adds an object-kind byte plus editable UTF-8 source and the two vector
appearances to the existing ordered figure collection. Geometry, page identity,
rotation, clipping, linked history, backup and PDF export share the existing
figure implementation. Images retain their original PNG payload. Migration
retains the exact predecessor snapshot. Private drafts and notebook files are
unencrypted, under the existing app-private storage policy.

## Validation

The initial 0.34 implementation passed **250,255 JVM assertions in 71 suites**,
plus **five real-browser cases**. The 0.35.0-alpha.2 correction adds raster-mode
and normal/preview parity checks plus a sixth browser case for the reported text.
The updated full suite passes 252,140 JVM assertions in 78 suites and six browser
cases, along with debug/release lint, builds and permanent-signer verification. Host checks exercise real storage/reopen, v7 migration and exact predecessor
retention, backup/restore, linked cross-page history, failed publication/retry,
source/vector corruption and size limits, current-zoom rotated tile geometry,
and production source-editor/action callbacks across queued I/O boundaries.
The browser harness runs the exact packaged assets with their CSP and verifies
math, diagrams, CJK, source snapshots, sanitized content and zero external
requests. PDFBox export checks use actual generated PDFs at 0°, 37°, 90° and
−53°, embedded fonts, extractable text and vector paths. Poppler output was
visually inspected for multilingual baselines, equations, tables and export.

Run `scripts/test.sh` for these checks, then Android debug/release lint/builds.
`scripts/build-release.sh` prepares the installable APK with the existing
permanent certificate. The APK verifier checks every generated Markdown asset
against its packaged SHA-256 inventory. Pinned npm packages and fonts are built
by `scripts/build-markdown-assets.sh`; upstream licenses ship in
`assets/markdown-NOTICES.txt`.

The owner authorized continuing without the tablet. This feature's native
WebView/PDF display, physical keyboard/stylus routing, pane interactions,
e-ink refresh and performance still need on-device acceptance. No new battery,
optical-latency or firmware measurements are claimed. The owner explicitly
authorized publication on 2026-10-30. **0.34.0-alpha.1** promotes dev.1 with
version/documentation changes only and the permanent signer. It is a GitHub
prerelease; 0.33.2 remains the latest stable release. See the
[alpha release notes](releases/0.34.0-alpha.1.md).
