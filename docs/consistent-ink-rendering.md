# Consistent pressure rendering - 0.21

The saved notebook is a vector model, not a copied image of the low-latency
layer. Each stroke already stores its nominal brush width, pen type, color,
coordinates, pressure and event times. Live writing and native restoration use
Huawei's brush, which turns those samples into a variable-width shape. Before
0.21, scrolling and PDF instead connected the same points with a constant-width
line. This explains both reported symptoms without any lost pressure data.

## Shared geometry

`NotebookStrokeRenderer` replays each saved stroke through the installed
`android.eink.view.HandPen` brush, using the same DOWN/MOVE/UP sequence as
`HWTrackHelper`. Its Canvas is zero-sized and offscreen: it accumulates the native
path without binding a surface or changing live handwriting. `getTrack().getPath()`
provides the complete filled pressure outline. A separate pen per type and thread
avoids sharing mutable native brush state between scrolling, overview and export.
Each returned path is independently owned; later replay cannot mutate it.

Scrolling previews and overview thumbnails fill that native path. The old
nominal-width stroke and thumbnail minimum-width inflation are removed. The
existing page-tile cache and page clipping remain in use. No outline generation,
extra UI update or storage write is added to the live pen MOVE path.

PDF uses the same native outline, flattened with a maximum curve approximation
error of 0.05 source-page units. `NotebookStrokeOutline` preserves contour breaks
and winding/even-odd fill rules. One vector fill per stroke preserves holes and
opacity without seams from independently painted pressure segments. The original
width/pressure/time samples are passed to the brush unchanged. There is no
fixed-width fallback that could silently recreate the reported defect.

PDF remains a streaming writer. It holds one source page and one stroke outline
at a time, not a whole-book raster or PDF byte array. Content streams use Flate
compression, exact compressed lengths and byte-offset cross references. All
native geometry is still editable in the source notebook; flattened outlines
are export/rendering results, not a new saved page format.

## Verification

Build, run the host suite, then run the headless check with the tablet connected:

```sh
./scripts/build.sh
./scripts/test.sh
./scripts/check-stroke-rendering.sh
```

The device helper creates only synthetic strokes under its own temporary
directory in `/data/local/tmp`, pulls the results into ignored
`artifacts/consistent-ink-0.21/`, and removes its device files. It never opens
NotebookStore, user notes or the display surface. The native fixture contains
14 strokes covering constant and varying pressure, five widths, dots, curves,
and self-crossing contours.

Huawei replay, the Canvas renderer and the PDF all measure nominal-width-10
light/heavy lines at 5/10 pixels. No binary ink boundary differs by more than one
source pixel; native, Canvas and PDF rasterizers have different antialiasing.
The tool also checks path ownership across brush reuse and cancellation/restart.
The final two-page blank/lined PDF passes the tablet's PdfRenderer and independent
Poppler rendering. The PDF remains vector-based; compression changes no geometry
and reduces this fixture from 1,434,878 to 292,887 bytes.

The installed APK also passes outline access under its ordinary app UID,
blank/lined native restoration and in-place edit/pause-boundary regressions.
All 19,820 host assertions pass, including PDF structure/geometry, fill rules,
opacity, failures and unchanged notebook files/history/scroll state. Host outline
fixtures test transport only; native brush fidelity is established on the tablet.

The user confirms that scrolling and freshly exported PDFs match the native
canvas. Existing notes benefit from their already saved pressure samples.
Previously exported PDFs retain their old appearance until exported again.
