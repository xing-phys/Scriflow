package org.eink.inkprobe.notebook;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Independent screen coverage, resolution identity, and memory bounds for deeply zoomed PDF pages. */
public final class NotebookPdfTilesTest {
    private static int assertions;
    public static void main(String[] args) {
        visibleCoverage(); resolutionKeys(); hugePagesStayBounded(); pageEdgesAndMargins(); invalidGeometry();
        System.out.println("NotebookPdfTiles: " + assertions + " assertions passed (screen-resolution tile geometry).");
    }

    private static void visibleCoverage() {
        for (float zoom : new float[]{.25f, 1, 1.73f, 4, 8, 64}) {
            int screenWidth = 1404, screenHeight = 1872;
            double width = screenWidth * zoom, height = width * 1.4142;
            float left = (float) -Math.max(0, width - screenWidth) * .63f;
            float top = (float) -Math.max(0, height - screenHeight) * .31f;
            List<NotebookPdfTiles.Tile> tiles = NotebookPdfTiles.visible(7, width, height,
                left, top, screenWidth, screenHeight);
            check(!tiles.isEmpty(), "visible page has tiles at zoom " + zoom);
            long pixels = 0;
            Set<NotebookPdfTiles.Tile> unique = new HashSet<>();
            for (NotebookPdfTiles.Tile tile : tiles) {
                check(tile.page == 7 && tile.width > 0 && tile.width <= NotebookPdfTiles.EDGE
                    && tile.height > 0 && tile.height <= NotebookPdfTiles.EDGE, "each decode stays bounded");
                check(tile.fullWidth == (int) Math.ceil(width) && tile.fullHeight == (int) Math.ceil(height),
                    "each zoom rasterizes the PDF at its actual screen resolution");
                check(unique.add(tile), "visible tile does not repeat");
                check(tile.left >= 0 && tile.top >= 0 && tile.left + tile.width <= tile.fullWidth
                    && tile.top + tile.height <= tile.fullHeight, "tile is clipped to original page");
                pixels += tile.pixels();
            }
            check(pixels <= (long) (screenWidth + 2 * NotebookPdfTiles.EDGE)
                * (screenHeight + 2 * NotebookPdfTiles.EDGE), "memory depends on viewport, not zoom");
            // Sample independently in screen coordinates, including sub-pixel pans and nonintegral zoom.
            for (int y = 0; y < screenHeight; y += 61) for (int x = 0; x < screenWidth; x += 53) {
                double pageX = x - left, pageY = y - top;
                if (pageX < 0 || pageY < 0 || pageX >= width || pageY >= height) continue;
                int covers = 0;
                for (NotebookPdfTiles.Tile tile : tiles)
                    if (pageX >= tile.left && pageX < tile.left + tile.width
                            && pageY >= tile.top && pageY < tile.top + tile.height) covers++;
                check(covers == 1, "every visible pixel has exactly one sharp tile");
            }
        }
    }

    private static void resolutionKeys() {
        List<NotebookPdfTiles.Tile> first = NotebookPdfTiles.visible(1, 1404, 1872, 0, 0, 1404, 1872);
        List<NotebookPdfTiles.Tile> panned = NotebookPdfTiles.visible(1, 1404, 1872, 0, -10, 1404, 1872);
        Set<NotebookPdfTiles.Tile> keys = new HashSet<>(first);
        check(keys.containsAll(panned), "small pans reuse existing pixels within the same tile grid");
        check(first.get(0).equals(panned.get(0)) && first.get(0).hashCode() == panned.get(0).hashCode(),
            "cache key equality is independent of viewport offset");
        for (NotebookPdfTiles.Tile tile : NotebookPdfTiles.visible(1, 2808, 3744, 0, 0, 1404, 1872))
            check(!keys.contains(tile), "zoom invalidates the old low-resolution cache entry");
        check(!keys.contains(NotebookPdfTiles.visible(2, 1404, 1872, 0, 0, 1404, 1872).get(0)),
            "source pages never share cache entries");
    }

    private static void hugePagesStayBounded() {
        List<NotebookPdfTiles.Tile> tiles = NotebookPdfTiles.visible(600, 100_000_000, 200_000_000,
            -99_997_500, -199_997_500, 1404, 1872);
        long pixels = 0;
        for (NotebookPdfTiles.Tile tile : tiles) pixels += tile.pixels();
        check(pixels < 5_000_000 && tiles.size() < 24, "extreme PDF extent cannot allocate a full zoomed page");
        check(tiles.get(0).left > 99_000_000 && tiles.get(0).top > 199_000_000,
            "the renderer requests the distant visible region directly");
    }

    private static void pageEdgesAndMargins() {
        List<NotebookPdfTiles.Tile> edge = NotebookPdfTiles.visible(3, 513, 600, -500, -590, 1000, 1000);
        check(edge.size() == 2, "partial page edge requests only the intersecting grid cells");
        NotebookPdfTiles.Tile finalTile = edge.get(edge.size() - 1);
        check(finalTile.width == 1 && finalTile.height == 88, "last tile clips to native page dimensions");
        check(finalTile.rightIn(1000) == 1000 && finalTile.bottomIn(1500) == 1500,
            "logical background coordinates reach the same page edge as ink");
        check(NotebookPdfTiles.visible(1, 100, 100, 1500, 0, 1404, 1872).isEmpty(), "offscreen horizontal page skipped");
        check(NotebookPdfTiles.visible(1, 100, 100, 0, -100, 1404, 1872).isEmpty(), "page above viewport skipped");
        check(NotebookPdfTiles.visible(1, 100, 100, 0, 1872, 1404, 1872).isEmpty(), "page below viewport skipped");
    }

    private static void invalidGeometry() {
        reject(() -> NotebookPdfTiles.visible(0, 100, 100, 0, 0, 100, 100));
        reject(() -> NotebookPdfTiles.visible(1, Double.NaN, 100, 0, 0, 100, 100));
        reject(() -> NotebookPdfTiles.visible(1, 100, Double.POSITIVE_INFINITY, 0, 0, 100, 100));
        reject(() -> NotebookPdfTiles.visible(1, 100, 100, Float.NaN, 0, 100, 100));
        reject(() -> NotebookPdfTiles.visible(1, 100, 100, 0, 0, 0, 100));
        reject(() -> NotebookPdfTiles.visible(1, Integer.MAX_VALUE, 100, 0, 0, 100, 100));
    }

    private static void reject(Runnable action) {
        try { action.run(); throw new AssertionError("Invalid geometry accepted"); }
        catch (IllegalArgumentException expected) { assertions++; }
    }
    private static void check(boolean condition, String message) {
        assertions++; if (!condition) throw new AssertionError(message);
    }
}
