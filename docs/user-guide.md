# Scriflow user guide

Scriflow is designed for Huawei MatePad Paper HMW-W09 on Android 10/API 29,
firmware `HMW-W09 2.1.0.222(C00E170R1P8)`. Other devices and firmware have not
been verified. See the [README](../README.md) for installation and current limits.

This guide follows alpha prerelease **0.40.0-alpha.2**; the published stable release is
**0.33.2**. This build reads older notes; ordinary edits use page format 8 and
individually sized Markdown uses format 9. Older APKs cannot read formats they
do not support. Physical checks of this exact APK remain pending.

## Notebooks and pages

Open **Scriflow** to see the library. Tap **My Notebook** to open existing
pages, or **+ Notebook** to create and open a named notebook. Tap **⋮** beside
a notebook for Rename, Move to folder, Backup, or Move to Trash. **Rename** changes
its title without moving its files. Names may contain up to 80 Unicode
characters; the library supports up to 200 notebooks. Tap the **notebook name**
in the editor's top-left bar to return to the library. Each notebook remembers its own
reading position. **+ Folder** creates a folder at the bookshelf root; use its
**⋮** menu to rename or remove it. Removing a folder keeps its notebooks.

**Trash** holds deleted notebooks. Its **⋮** menu restores a notebook to its
folder, or permanently deletes its pages and imported PDF after confirmation.
Permanent deletion frees a notebook slot. See [bookshelf settings](bookshelf-settings.md)
for configurable list/thumbnail layouts and storage compatibility. Settings
uses **− / +** buttons to change portrait/landscape row and column counts;
there is no number field or keyboard to open.

Hold a notebook on the bookshelf or in Trash to enter selection mode. Tap more
notebooks or **Select all**, then use **Actions** to move selected notebooks,
move them to Trash, restore them, or permanently delete selected Trash items
after confirmation. Individual permanent deletion remains available from the
notebook's **⋮** menu.

Tap **Quick A5 note** to create and open an A5 notebook immediately with an
automatic name. **+ Notebook** opens the named creation form.
New notebooks default to **A5 (148 × 210 mm)**. The creation form offers
standard paper sizes, including A4, ISO B5, and Letter, and a **Custom** choice
with width and height in millimetres. Newly added or inserted blank pages
inherit the source page's dimensions. PDF export uses the chosen physical size;
changing screen orientation or zoom does not change the paper. Existing notes
keep their original coordinates and PDF printing scale.

## Browser and saved websites

Tap **Browser** on the bookshelf. Tap the **dotted button** to expand or collapse
all browser controls, and drag it to move them. Enter an HTTPS website address
and tap **Go**. Use Back, Forward, Reload and pinch zoom while browsing. After
loading, tap **Save** and wait for **Saved to Saved websites · available offline**.
The fullscreen-corners icon enters **Zen mode**, leaving only the top-right
clock. Tap the clock or press Android Back to exit. Outside Zen, Android Back
closes an expanded panel before following page history.

Return to the bookshelf and open the permanent **Saved websites** folder.
Tap a saved page to read it without Wi-Fi. **Open online** opens its original
address in the live browser. Back/Forward also work offline after section links
and retain history across rotation. Each save keeps the current page and captured
resources; linked pages need their own saves. Scroll to load wanted images
before saving. Forms, live video and other interactive services need the online
page. Use the trash icon beside a saved page to remove its local copy after
confirmation. The browser bundles the current Firefox engine for modern websites and math.
New saves include rendered formulas, fonts, styles and images in a local HTML
copy; older saved copies remain readable. See [Browser and offline websites](browser.md) for details.

## Markdown blocks

Use the top-left **⋮ → Markdown M↓ icon** to insert a block. Type Markdown,
then tap the small eye for rendered content, or tap outside to keep code visible.
Select a block with a finger; its code/eye icon switches views and its pen icon
in code mode reopens editing. The **A with arrows** sets this block's preview
font size; Settings provides the default for new rendered and raw blocks. Move, resize, rotate, copy/cut/delete and drag
between pages just like a figure. Tap outside to finish.

Tables, lists, task checkboxes, highlighted code, footnotes, Mermaid/callouts
and mathematical LaTeX render offline. Use `$…$` for inline math and `$$…$$`
for display math; aligned equations, matrices, physics and chemistry notation
are supported. Original source stays editable. See [Markdown blocks](markdown-blocks.md)
for syntax, recovery, size limits and compatibility.

## AI chat

Open **Settings → AI chat**, select a saved provider or tap **Add provider** to
create a named profile, enter its API key, check the
API base URL, then tap **Fetch available models**. Models appear directly below
the button as names with checkboxes and settings gears. Check the ones you
want and tap **Save AI settings**. Uncheck a model to remove it from the chat
picker without deleting its history. There is no Model ID entry or separate
Manage saved models screen.

Tap a model's gear for its automatically filled context/input limits, output
maximum, reply budget, image support and thinking settings. Manual edits are
optional. **Apply** stages changes; **Save AI settings** saves them. **Use fetched
defaults** resets the form to its latest fetched values. Fetching again keeps
your overrides. Thinking defaults to Automatic; unavailable specifications use
provider defaults. The connection can also be saved before choosing a model.

Tap **AI chat** on the bookshelf to start a new chat or open saved chat history
as an independent main window with no notes open. Tap the toolbox notes button
and choose a note when you want a floating note; only notes you explicitly
minimize appear in the notes tray. Closing or minimizing the main chat returns
to the bookshelf. From a note, tap the chat-bubble sparkle
in the toolbox to toggle its tray. **+** opens a
new floating chat, **History** opens saved sessions, and bubble icons restore
minimized chats. The tray works even with no windows open. Every new window
has a separate session and draft.

Use the same corner controls as notes: **tap bottom-left** for options or
**drag bottom-left** to move; **tap bottom-right** to minimize or **drag
bottom-right** to resize. A thin outline follows the finger, with the top-left
corner fixed; content refits on release. The menu offers Position, Minimize,
Close chat and size presets. Position choices are Left, Right, Top, Bottom and
Floating window, plus **Main window** for chats. A docked chat reserves the
remaining space for notes; a main chat puts notes in floating windows. Minimize
every note to use only chat; the note-specific top-left bar disappears. Use the
notes tray to restore a note.
Restore minimized chats from the toolbox tray. Minimizing preserves an active
reply; closing stops its request and keeps history. Other windows remain open.

Send a question, then ask follow-ups; streamed replies render Markdown, math, code and
tables using the existing local engine. The **send up-arrow** is beside the
message field in the same row; **Ctrl+Enter** also sends. It changes to a stop
square during generation, a retry arrow after interruption, or a save icon
after a storage error. Stopping or interrupting a stream saves its visible partial
reply. Copy the last reply from the options menu. Chats and text/image
drafts save automatically. Tap **History in the toolbox tray** to find saved
sessions, newest first. Tap a row to reopen it with its
original model and follow-up context; an already-open session is raised instead.
Use its trash icon to confirm deletion. History is shared across notebooks;
empty untouched sessions are not saved. Older chats are imported automatically.
All window placements, minimized states and sessions restore across rotation,
Settings and app reopen. Returning from Settings retains each window's original
model. Tap the **model name in the message bar** to select a saved model
for a new window; existing windows remain open. This choice becomes the toolbox
default. AI settings remain in app Settings; there is no settings icon in the chat.

Sending shares this chat's text and history with the configured provider;
provider usage may incur charges. Notebook pages and PDFs are not automatically
included. Paste text, or tap the chat icon beside a lasso/image/Markdown selection
to append that content to the last active chat (restoring it if minimized).
Tap a chat before selecting content to choose the target. Markdown appends as source; ink/images append as local
image drafts. Remove unwanted image chips with ×, add a question, then tap the send arrow.
Use an image-capable model for ink/images. Keys are encrypted on the tablet. See
[AI chat](ai-chat.md) for provider setup, recovery, limits and privacy.

## Figures and multiple notes

Use **⋮ → Insert figure…** for SVG or Android-supported images. The file picker
opens directly; choosing an image inserts it and returns to the note automatically.
Tap an inserted figure with one finger to select it, including in an inactive note.
Drag the body to move, the lower-right handle to resize, or the upper-right
rotation handle to turn it to any angle. Drag across page boundaries; holding
near the top or bottom scrolls to further pages. Nearby icons offer Copy, Cut,
Delete, and Paste when a copied figure is available. Paste is also in the note’s
**⋮** menu. Tap outside to finish; each completed gesture saves an undoable edit
without a separate editor or Apply button. **⋮ → Figures…** remains available.
SVG is rasterized into the stored PNG figure.
Lasso actions now include **Export…** for SVG, PNG, JPEG, WebP, or PDF.

Use the **overlapping-notes button in the toolbox** for a left/right/top/bottom
split or floating window. With several notes open, it toggles a tray containing
**+** and any minimized notes. Tap inside a note with the stylus to edit it.
The notes share one toolbox; the top-left status island shows the active note’s
title, page and zoom. Fingers independently scroll and pinch every note.
Tap the lower-left corner control for placement and closing options; tap the
lower-right control to minimize. Drag the
left control to move a floating note or the right control to resize it without
waiting for a hold. A boundary follows resizing; the note refits after release.
See [figures and workspace](figures-and-workspace.md) for controls, limits,
storage compatibility, and remaining hardware checks.

## PDF crop, single-page reading, contrast and dictionary

Open **⋮ → Reading view** (the page-with-lines icon) in the top-left bar.
Choose Continuous or Single page. Continuous defaults to Fit width; Single page
defaults to Fit page. Use the page arrows or a horizontal swipe at whole-page
size to turn one page. You can still pinch and pan within a single page; zoomed
horizontal drags pan rather than turn pages.

PDF pages offer crop Off, Auto, Semi-auto and Manual. Auto finds the content;
Manual uses your exact area; Semi-auto finds content inside your area. Tap two
opposite corners, or drag edges/corners and the inside of the box. Choose the
current page, all pages, odd pages or even pages with the scope control, then
apply with the check icon. Use X to cancel. Increase PDF contrast for darker
text and images; Reset contrast restores the original tones. These settings
are remembered for each notebook and do not alter ink or exported PDFs.

Hold a finger on a PDF word to select it and open Eudic's small dictionary
window. Move the stylus away first so palm rejection allows finger input.
Copy and dictionary icons appear near the word; tap elsewhere to dismiss.
Install Eudic and allow its floating-window permission in the tablet's system
settings. If it is absent, selection and Copy still work. Image-only scans need
an existing text layer; Scriflow does not OCR them. See [PDF reading](pdf-reading.md)
for behavior, storage and validation limits.

## Zoom and screen orientation

For PDFs, choose **Import PDF** in the library. Each PDF becomes a separate
notebook with editable handwriting and its own retained source file. Finger
taps follow PDF links; the stylus writes over them. Open **Outline** to navigate
imported bookmarks or add, edit, and remove your own headings. Use the arrows
beside parent headings to collapse or expand their descendants. **Previous view**
and **Next view** appear only when available at the lower right of the note, or
beside the minimize control inside a floating note. They restore zoom and
position after link, outline, and page-overview jumps. Scrolling, zooming, and
page arrows do not enter that history.
Web links show their target and ask before opening the browser.
See [PDF notebooks](pdf-import.md) for export preservation, backups, and limits.

New notes open at **Fit width**. Pinch with **two fingers** to zoom in or out;
a percentage indicator appears during zoom. The zoom controls offer **Fit width**,
**Fit height**, **Fit page**, and fixed percentages. Drag with a finger to pan
across enlarged pages and scroll through the notebook. Space outside the paper
is white. Ink stays attached to the page when you zoom or rotate.
Faint grey lines mark the page edges on screen; these guides are not exported
as ink or PDF content.

Tap the **zoom percentage** in the top-left island to open the zoom
controls and **App settings**.

Tap the **time/battery/Wi-Fi area** to open Settings, or use **App settings** in the editor zoom menu. Choose **Portrait**,
**Landscape**, or **Follow device sensor**. The choice is saved for the whole
app. Sensor mode listens to the tablet directly, including when system rotation
is locked. Hold the tablet tilted rather than flat; it turns after a short,
stable hold. While writing, rotation waits until you lift the pen. Settings
shows whether a sensor was detected. No additional permission is required.
**Settings → Screen → Keep screen on** prevents the normal timeout while Scriflow
is visible. It releases when the app pauses; the cover, Power button, and system
sleep remain in control. Cover behavior still needs verification on the MatePad.
All screens share the accepted direction. The bookshelf, note, Settings, and
transfer screens keep the same orientation when you move between them;
navigation does not wait for another sensor reading. The settling interval
applies when physically turning the tablet. Screen rotation rebuilds the
writing surface without changing stored page dimensions.

Finger navigation is ignored while the pen hovers nearby, during writing,
and for **200 ms (0.2 seconds)** after the last pen contact/hover event, including
hover exit. Continuing to hold the pen near the screen keeps finger navigation
blocked. Lift it away before using your fingers. This palm-rejection delay
does not delay closing a lasso outline on lift. A rejected resting
hand must lift before it can start navigation. Broad contacts and gestures with more than two fingers are
also rejected when the device reports them.

For pinch zoom, place both fingers within 0.35 seconds, then deliberately
change their separation. Small contact jitter does not zoom. If an accepted
navigation gesture is cancelled as a palm/mixed-input gesture, its temporary
zoom and pan are discarded. In **Settings → Touch gestures**, turn **Two-finger
zoom** off to use the percentage menu for zoom while keeping one-finger
scrolling available.

## Writing and saving

Write with the stylus. Completed strokes save automatically in the background;
there is no manual save button. The three-dot menu shows the page's save state.
If a save fails, writing pauses and **Not saved · Retry** appears. Retry before
leaving or stopping the app so unsaved work has a chance to be written.

Open the top-left **⋮** menu and choose **Use lined
background** or **Use blank background** to switch the current page without
clearing its writing. PDF pages keep their original backgrounds.
**Drag vertically with one finger** to scroll,
then lift it to stop anywhere, including between pages. Write with the stylus
on either visible page. Scrolling has no page snapping or inertial fling.

The **left/right arrows beside the page counter in the top-left bar** jump to
page tops. The **page-plus icon** inserts an
empty page immediately after the current page and selects it, using that page's paper. Each page keeps its own writing and
paper choice. The page limit is 1,000; page management is available from 0.29.
These limits are not demonstrated large-notebook performance guarantees.

The top-left floating island reads **Notebook name · X / Y**. Its page is the
most recently written page, or the page nearest the viewport center after scrolling. Eraser,
Undo/Redo, and paper choice use that context. Fractional scroll position is
saved per notebook. Existing pages keep their contents when their order changes.

## Overview and jumps

Tap the **x/y page counter in the top-left bar** to manage pages and browse
whole-page thumbnails. The current page has a
black border and a **Current** label. Tap a thumbnail to jump to its page top,
or enter a number beside **Go to page** and tap **Go**. **Close** or Android
Back returns to the exact previous scroll position when the page order is
unchanged. Thumbnails load as you browse; opening the overview does not edit pages.
Settings → Page overview sets portrait and landscape columns separately (defaults:
5 and 8). Use **− / +** beside each count. The bookshelf has separate
row/column preferences.

Choose **Manage**, then tap a page for its actions. You can also hold a thumbnail
to open the same actions while browsing:

- **Move earlier/later** shifts the page by one position. **Move to page…** accepts
  its final page number, from 1 to the notebook's current page count.
- **Duplicate** places an independent copy immediately after the original,
  preserving its paper and handwriting appearance.
- **Insert blank before/after** adds a page with the chosen page's paper and size.
- **Delete…** asks for confirmation, then removes the page from the notebook.
  The notebook always keeps at least one page.

Changes save automatically. **Undo page change** and **Redo page change** appear
at the bottom of the overview; they are separate from the toolbar's ink history.
They retain up to 50 page changes per notebook until the app restarts. A new
page change clears page redo; writing on an existing page keeps that history.
Restoring a page brings back its writing and its available ink undo history.

After managing pages, Close returns to the selected page's top. New thumbnails,
navigation, PDF exports, and editable backups follow the new order. Removed
pages are excluded from exports and backups. Their private files remain for
recovery; page deletion does not immediately reclaim storage.

The first page-management edit upgrades that notebook's order index. Earlier
APKs cannot read the managed notebook; preserve an editable backup before updating.

## Floating tools

The controls form a single row or column of separate 32 dp circular buttons,
with 6 dp gaps and no enclosing bar. Tap the **direction icon beside the grip** to switch
orientation. Drag the **six-dot grip** with a finger or stylus to move the dock;
it stops where released. **Tap the grip once** to collapse all tools into that
one circle; tap again to expand. The collapsed grip can still be dragged.
Position, orientation, and collapsed state are remembered. Only the grip moves
the tools.

**Pen** uses a pencil symbol; **Eraser** uses a tilted eraser. A thick border
marks the selected tool. **Lasso** uses a dashed loop (available in development
builds after 0.28.0-beta.2). Undo/Redo appear beside the top-right device status
while writing, each only when available. Page management,
page-turn arrows and the bookshelf action are in the top-left bar. The vertical
**⋮** beside the zoom percentage opens separate circular actions to the right
of the note bar: Insert figure, Insert Markdown, Reading view/PDF crop/contrast,
Page information/PDF export, the page’s blank/lined background action, and Paste
figure when available. The three dots disappear and the note bar shortens while
the actions are open. The circles have small gaps; on narrow panes, additional
rows stay in the lane to the right of the bar. Select an action, tap the page,
or press Back to close the actions and restore the three dots. Tap a figure
directly to move, resize, rotate or delete it; there is no figure-chooser action.
Hold an icon for its tooltip. Page information also offers **Reset tools**.
Reset expands the dock and returns it to its initial horizontal
position near the top center, below the status islands. Touching controls has
no ripple or pressed inversion; the page stays writable outside the tools.

## Zen mode (0.33.2-dev.3)

Tap the **fullscreen-corners icon in the toolbox** to enter Zen mode. The
top-left bar and Undo/Redo disappear; the top-right bar becomes a small clock
showing hours, minutes and seconds. The clock view has a transparent background
inside its rounded container, so there is no square white patch over the edges.
Tap the icon again to restore the normal
bars and eligible Undo/Redo controls. The toolbox, notes, zoom and writing tools
keep their current state. If writing pauses because of an error, the normal
Retry bar returns so recovery stays accessible.

Zen mode survives rotation of the open editor. A newly opened editor starts in
normal mode.

## Pen thickness

Tap the **already-selected Pen icon** to open a small width slider beside it.
Drag left for thinner or right for thicker strokes. A sample line and number
preview the width. **Release to apply; the slider stays open.** Adjust it again
as needed, then touch outside or press Back to close while keeping the last
applied value. Cancelling an unfinished adjustment restores that last value.
Beginning a stroke outside the bar closes it and accepts the first stroke.
Tapping the track selects a width directly.

Widths run from **1.0 to 10.0**, in 0.1 steps; the original default is **4.0**.
The setting is remembered across sessions and notebooks. A line beneath the
pencil previews it. Width changes affect new strokes. Saved writing, undo/redo,
scroll previews, thumbnails, and PDFs preserve each stroke's own style and
recorded pressure shape.

On the supported MatePad firmware, **double tap the M-Pencil body** to switch
from Pen to Eraser. From Eraser or Lasso, double tap to select Pen. A small icon
appears at the top center for 650 ms and disappears. The shortcut works while
a note is open in the foreground, including the active floating note; lift the
tip before tapping. It leaves an unfinished stroke intact and switches after
it finishes. It does not change tools while a menu/editor dialog is open.

## Erasing and undo/redo

Choose **Eraser**, move the stylus across writing, then lift it to remove every
touched stroke. A temporary **grey trail** follows the erasing path through
the native handwriting engine at the selected constant diameter. Its width
stays the same at light and heavy pressure. After the erase completes, the
touched ink and trail disappear together. Erasing removes whole strokes rather
than portions, so the rest of a touched stroke can disappear beyond the trail.

Tap the **already-selected Eraser icon** to choose **Stroke eraser** or
**Region eraser**. Stroke mode shows its width slider: **4–240** in
steps of 1, with **36** as the default diameter. Like the Pen bar, it remains
open after applying a value. It is independent of pen width and remembered
across sessions. A wide eraser can reach page edges from the
gap between pages. A no-hit gesture clears only the trail; cancelling leaves
the ink intact. Very fast passes at the maximum width can show visible lag.
Native offscreen checks verify the corrected constant-width brush geometry;
the current build's optical latency and physical pen feel still need tablet
acceptance. Choose **Pen** to resume writing.

From **0.35.0-alpha.8**, choose the **dashed-loop eraser icon** for Region mode.
Draw a loop with the stylus and lift to erase immediately; the loop closes
itself, using the same selection rule as Lasso. Entire strokes inside or
intersecting the region are removed, including any portions outside the loop.
Figures, Markdown blocks and original PDF content are not erased. A loop can
span visible pages; crop margins and wholly offscreen strokes are protected.
Each affected page receives one undo step, as with the normal eraser. A
cancelled or empty/no-hit loop changes no ink or history.

Region mode hides the width slider. Choose the ordinary eraser icon to restore
Stroke mode and its previous width. The mode is remembered across sessions;
M-Pencil body double-tap from Pen selects the remembered eraser mode. Finger
scrolling, pinch zoom and ordinary Lasso selection keep their existing behavior.
The region preview reuses the native dashed brush; physical preview and pen
interaction still need tablet confirmation for this update.

In **0.33.2-dev.2**, Undo/Redo are beside the top-right clock/battery/Wi-Fi bar,
outside the toolbox. From **0.35.0-alpha.6**, these are separate circular buttons
outside the right status pill, with spacing between them. Each appears only when its action is available. Scrolling,
zooming, navigation, window changes, reading another pane and opening controls
hide both until another Pen writing contact. Availability updates between strokes.
Tapping Undo/Redo keeps the controls available so repeated Undo and subsequent
Redo remain possible. Hidden actions take no space or touch target.

**Undo/Redo** retains up to 100 writing or erase actions **per page** within
the current app process. It survives leaving and returning while the process
stays alive, but process restart begins a new history. Erasing, undo, and redo
autosave. New writing or a successful erase clears Redo; a no-hit erase
preserves it. Changing paper leaves stroke history available.

## Selecting and moving ink

Choose **Lasso** and circle the strokes you want. A native dashed line follows
the stylus. **Lift to visibly close the curve**, including a final edge back to
its start when you leave a gap. A whole stroke is selected when any part of its
centreline enters or touches the closed region, including crossing its boundary. Selection
works on one page at a time; it does not cut strokes into pieces.

The outline closes on the next input-queue turn after stylus lift, without the
former wait of up to 256 ms for a native saved-track notification. Selection
calculation and page restoration may still take processing time; this is not
a guarantee of zero optical delay.

A small strip of action icons appears beside a nonempty region.
**Drag anywhere inside the curve with a finger or the stylus** to move the ink.
The floating group follows your pointer across page edges. Hold near the top or
bottom screen edge to scroll through more pages, then lift over the destination.
The strokes stay together at their original logical size. On release the group
is fitted wholly inside the destination page. A page too small for the complete
group rejects the move; ink is not clipped or reduced to fit.
The nearby icons are:

- **Move…** targets any page by number and places the group near its centre,
  including pages too far away to drag to conveniently.
- **Duplicate** makes an offset copy and selects it.
- **Copy** keeps the selected ink on Scriflow's private clipboard.
- **Cut** copies, then removes the selection; **Delete** removes it directly.
- **Export** saves the selected ink in a chosen image/vector format.

Tap outside the curve to finish; there is no Done button. Then tap the
already-selected Lasso icon for these additional actions:

- **Paste** places a fresh copy near the visible centre of the current page.
  You can then drag it into position. Copy/paste works across pages and
  notebooks, while the app process stays alive. It is not the Android system
  clipboard. Paste is offered when copied ink fits the destination page.
- **Select all on this page** selects its ink and shows the action bar.

If the region touches no strokes, its outline stays visible without an action
strip. Tap outside it, then draw a region that overlaps the desired ink.
While a region is active, a finger inside moves it. A tap outside clears
it for ordinary scrolling; a two-finger navigation gesture also clears it.
Change tools, navigate to another page, or press Back to clear the selection.
Back or cancellation during a drag discards the unfinished move. Undo/Redo also
clears the region. Selecting, copying, and deselecting do not change history or
saved notes. Existing pressure, thickness, colour, stroke identity and sample
timing are retained by a move; duplicate/paste creates independent stroke IDs.

A cross-page move is one linked **Undo/Redo** action on both pages. Undoing it
from either page restores both sides together. If either page has later edits,
undo those first. A completed same-page move, duplicate, cut, paste or delete
uses ordinary per-page history. These histories remain process-local.

Cross-page saves use a recovery journal so an interrupted save is completed on
retry or restart without losing the moved group. This recovery survives process
death; the Undo/Redo history itself does not. Source selection still begins on
one page: drawing a polygon across page boundaries does not select several
pages at once.

Earlier lasso interaction was accepted in 0.29.0-dev.2. That feedback does not
establish physical acceptance of these new cross-page controls or closure timing.
See the [device record](device-testing.md) for current testing and limits.

## Status and display refresh

Two small floating islands show the notebook/page at the top left and time,
battery level, and Wi-Fi state at the top right. The paper fills the screen
behind them, including the space between the islands; there is no reserved
header row. Time follows the tablet's 12/24-hour setting and
updates by the minute. Device-status changes wait until writing has been idle
for about a second; no per-second updates run during handwriting.

Huawei's firmware manages handwriting and scrolling through the app's refresh
policy. On the tested firmware, physical acceptance of version 0.26 confirmed
scrolling followed by immediate writing without transition flashes, inversion,
or freezing. This is a user observation, not a measured optical-latency claim;
each new release still needs acceptance. See [refresh investigation](fast-refresh.md).

Use the tablet's **Refresh now** action to clear residual images. No global
refresh setting or extra permission is needed. Unrelated background display
activity can interfere with this firmware: during development, Clash Meta's
**Show Traffic** notification caused one-second updates that disrupted writing.
Disabling that feature while retaining the VPN improved the original probe.
See the [device record](device-testing.md) for the evidence and limits.

## Saving and safe updates

Saving runs after firmware completion of each stroke. Stored pages retain vector
points, pressure, timestamps, and pen styles; reopening restores editable native
tracks rather than a screenshot. Background work and idle status changes wait
until the pen is idle.

After lifting the pen, pause briefly for the background writer before
force-stopping the app. Check the menu's save state if uncertain. An unfinished
stroke is discarded when leaving the app. A save error can leave accepted edits
only in memory until a successful retry.

Notes live in the app's private storage. **Uninstalling or clearing storage
deletes them.** Back up each notebook outside that storage before updating.
An ordinary update requires the same application ID and signing certificate.
If an APK is rejected for a signature mismatch, follow the
[signing migration procedure](releasing.md#moving-from-a-development-key).

The alpha can read page formats 1–7 without changing them on open. Their
first edit atomically upgrades them to format 8 and retains the predecessor
as a backup. Older APKs cannot read upgraded pages. Do not downgrade by
installing an old APK over current notes, or move raw legacy page files as a
substitute for editable backup/restore.

## Editable backups and restore

In the library, **Backup** beside a notebook saves an editable `.inkbook`
archive through Android's file picker. Keep it outside the app's private
storage and retain another copy on a separate device if the notes matter.

**Restore backup** above the library list validates an archive and adds a
**new notebook**, preserving existing notebooks. Writing, pressure, pen styles,
page/stroke IDs, names, and page order survive. The restored book starts at
Page 1. Undo history and device settings are excluded.

Backups are **unencrypted** and limited to 512 MiB uncompressed per archive
(64 MiB per page). If a destination-provider write fails, an incomplete file
may remain; retry with a new location. After saving a backup, restore it as a
separate notebook and open representative pages to confirm it is usable before
depending on it for migration. See [privacy](privacy.md) for storage details.

## PDF export

In an open notebook, choose **three-dot menu → Export PDF**. All pages prepare
in order, including writing and blank/lined backgrounds, then the Android save
picker asks for a filename and location. Wait for **PDF saved**. Cancelling the
location picker lets you choose again without preparing the PDF again.

PDFs contain sharp vector outlines from the native pressure brush, preserve
page proportions, and use a standard A4-width scale. Export leaves the notebook
editable. Export again to replace PDFs produced by older fixed-width rendering.
**A PDF is a sharing copy, not a restorable editable backup.** Use `.inkbook`
for backup and migration.
