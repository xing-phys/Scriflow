# PDF reading controls — 0.35

Open the top-left **⋮**, then the **Reading view** icon (a page with lines).
The controls apply to the active notebook. Activating an inactive note with the
stylus lets you change its settings; each note retains its own preferences.

## Crop

| Mode | Displayed area |
| --- | --- |
| Off | Original PDF page, including its original margins |
| Auto | Content detected independently on each page |
| Manual | Your exact chosen rectangle |
| Semi-auto | Content detected within your chosen rectangle |

Manual and Semi-auto open the original page as a crop preview. Tap two opposite
corners to define the area; drag edges/corners to resize or the inside to move
it immediately. The first corner has a cross marker. Use Reset for the full
page. The scope button cycles through this page, all pages, odd pages and even
pages; the initial scope follows the current page's parity. The check icon
applies the area; X/Back cancels. Page-specific overrides take precedence over
odd/even defaults; applying an all-page area clears those overrides.

This follows KOReader's four crop-mode behaviors, with independent odd/even
areas. It is an independent implementation: it does not embed KOReader code or
promise pixel-identical detection. Auto examines a bounded preview, estimates
paper tone, ignores isolated speckles/thin enclosing frames, and adds a small
safety margin. Blank or tiny detections retain the full search area. Semi-auto
is useful for excluding dirty scan borders, headers or neighboring columns.
Use Manual for exact control over unusual pages.

Cropping hides margins **only on screen**. The source PDF, ink coordinates,
figures and Markdown remain unchanged. Disabling crop reveals hidden content.
New writing uses original page coordinates, so toggling crop does not move it.
New figures and lasso drops fit within the displayed area.

## Continuous or single page

Continuous scrolling at Fit width remains the default. **Single page** displays
exactly one page and initially fits the whole page, centered when necessary.
Turn pages using the arrows beside the counter or a horizontal finger swipe
at whole-page size. Pinch to zoom in/out and pan within that page. A zoomed
horizontal drag pans; it does not accidentally turn a page. Page arrows,
overview jumps, outlines and internal PDF links still work. The percentage
menu offers Fit width, Fit height, Fit page and custom zoom in either mode.
The reading-mode setting also works for ordinary paper notes.

Inactive floating/split notes use the same crop, page mode, sharp PDF tiles and
finger navigation. Changing focus preserves each pane's independent view.

## PDF contrast

Increase **PDF contrast** for darker text edges and images on e-ink. Values range
from 0.20 to 3.00; 1.00 preserves original tones, higher values darken middle
tones and lower values lighten them. Pure black and white remain unchanged.
Reset contrast restores 1.00. Changes apply when the settings panel closes.

Contrast applies to PDF pixels after native rasterization, on the PDF display
worker. It affects PDF text and images together, without thickening handwriting
or changing figures/Markdown. Old display tiles remain available while new
tones are prepared. PDF export retains the original tones and margins.

## Word selection and Eudic

Hold one finger still on a word until the tablet's long-press timeout. The word
is highlighted, Copy/lookup icons appear nearby, and Scriflow opens Eudic's
floating dictionary using its documented `eudic://peek/…` endpoint. Tap outside
to dismiss selection. Copy changes the clipboard only when explicitly tapped.

Install Eudic and grant its floating-window permission through Android settings.
The supported package names include `com.eusoft.eudic` and `com.qianyan.eudic`;
another installed edition can also resolve the documented scheme. Eudic controls
the dictionary window, translations and its own permissions/network behavior.
If no handler is installed, selection and Copy remain available.

Move the stylus away from the screen before using fingers: handwriting/palm
rejection takes precedence. Scrolling, extra contacts, broad palm contacts and
new writing cancel pending holds. Extraction runs off the UI and ink-writer
threads and caches at most four text pages. Stale results cannot open a lookup
after navigation or departure. Both active and inactive PDF notes support it.

Selection requires the PDF's embedded text layer and Unicode mapping. It does
not OCR image-only scans or recover missing/broken character mappings. Rotated
pages and original PDF crop boxes are included in word hit geometry. A
transparent Eudic activity pauses handwriting while retaining the visible note;
a fully covering activity or sleep releases the editor normally.

## Storage and validation

Crop rules, contrast and reading mode use checksummed atomic per-notebook view
files with predecessor recovery, outside notebook page files. They are local
preferences, excluded from `.inkbook` backups like existing view/workspace
settings. A restored notebook starts with default reading preferences. Page
format remains 8 (as in 0.34), reading older formats without rewriting on open.

Host checks exercise crop/ink mappings, zoom and one-page bounds, detection,
preference recovery, production touch/timer callbacks, crop gestures,
dictionary pause/resume/stop and real PDF extraction at all four rotations with
nonzero original crop boxes and embedded Unicode fonts. These are automated
contracts, not a measurement of physical e-ink or Eudic delivery. The new APK
has not been tested on a connected tablet. Physical finger/stylus routing,
Eudic permissions/window behavior, screen rotation, sleep/return, refresh and
sustained performance remain acceptance checks.

References: [KOReader crop modes](https://github.com/koreader/koreader/blob/master/frontend/ui/data/koptoptions.lua),
[Eudic URL API](https://docs.eudic.net/1/jin-jie-gong-neng/url-scheme).
