package org.eink.inkprobe.notebook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure geometry for a continuous, width-fitted stack of independently stored pages. */
public final class NotebookViewport {
    public static final class Size {
        public final int width, height;
        public Size(int width, int height) {
            if (width <= 0 || height <= 0) throw new IllegalArgumentException("Missing page dimensions");
            this.width = width; this.height = height;
        }
    }

    public static final class EraseGesture {
        public final int pageId;
        /** Separate paths must not be joined across an off-page excursion. */
        public final List<List<NotebookPage.Point>> paths;
        public final float radius;
        private EraseGesture(int pageId, List<List<NotebookPage.Point>> paths, float radius) {
            this.pageId = pageId; this.radius = radius;
            List<List<NotebookPage.Point>> copy = new ArrayList<>();
            for (List<NotebookPage.Point> path : paths) copy.add(Collections.unmodifiableList(new ArrayList<>(path)));
            this.paths = Collections.unmodifiableList(copy);
        }
    }

    public final int width, height, pageCount;
    public final float gap;
    private final List<Size> sizes;
    private final float[] tops, scales, heights;
    private final float totalHeight;

    public NotebookViewport(int pageCount, int logicalWidth, int logicalHeight,
                            int viewportWidth, int viewportHeight, float gap) {
        this(uniform(pageCount, logicalWidth, logicalHeight), viewportWidth, viewportHeight, gap);
    }

    public NotebookViewport(List<Size> sizes, int viewportWidth, int viewportHeight, float gap) {
        if (sizes == null || sizes.isEmpty() || sizes.size() > NotebookIndex.MAX_PAGES
                || viewportWidth <= 0 || viewportHeight <= 0 || !NotebookPage.finite(gap) || gap < 0) {
            throw new IllegalArgumentException("Invalid notebook viewport");
        }
        this.width = viewportWidth; this.height = viewportHeight; this.gap = gap;
        this.sizes = Collections.unmodifiableList(new ArrayList<>(sizes));
        pageCount = sizes.size();
        tops = new float[pageCount]; scales = new float[pageCount]; heights = new float[pageCount];
        float top = 0;
        for (int i = 0; i < pageCount; i++) {
            Size size = this.sizes.get(i);
            if (size == null) throw new IllegalArgumentException("Missing page dimensions");
            scales[i] = (float) width / size.width;
            heights[i] = size.height * scales[i];
            tops[i] = top;
            top += heights[i] + (i + 1 < pageCount ? gap : 0);
            if (!NotebookPage.finite(top)) throw new IllegalArgumentException("Notebook geometry is too large");
        }
        totalHeight = top;
    }

    public float maxScroll() { return Math.max(0, totalHeight - height); }
    public float clampScroll(float scroll) {
        if (!NotebookPage.finite(scroll)) throw new IllegalArgumentException("Invalid scroll position");
        return Math.max(0, Math.min(maxScroll(), scroll));
    }
    public float scale(int pageId) { return scales[index(pageId)]; }
    public int pageWidth(int pageId) { return sizes.get(index(pageId)).width; }
    public int pageHeight(int pageId) { return sizes.get(index(pageId)).height; }
    public float pageTop(int pageId, float scroll) { return tops[index(pageId)] - clampScroll(scroll); }
    public float pageScroll(int pageId) { return clampScroll(tops[index(pageId)]); }

    /** Anchor belongs to the page above a gap, preserving an arbitrary continuous offset. */
    public int anchorPage(float scroll) {
        float offset = clampScroll(scroll);
        for (int i = pageCount - 1; i >= 0; i--) if (offset >= tops[i]) return i + 1;
        return 1;
    }

    public double anchorFraction(float scroll) {
        float offset = clampScroll(scroll);
        int i = anchorPage(offset) - 1;
        return ((double) offset - tops[i]) / (heights[i] + (i + 1 < pageCount ? gap : 0));
    }

    public float scrollForAnchor(int pageId, double fraction) {
        int i = index(pageId);
        if (Double.isNaN(fraction) || Double.isInfinite(fraction) || fraction < 0 || fraction > 1) {
            throw new IllegalArgumentException("Invalid scroll anchor");
        }
        double span = heights[i] + (i + 1 < pageCount ? gap : 0);
        return clampScroll((float) (tops[i] + fraction * span));
    }

    public List<Integer> visiblePages(float scroll) {
        float offset = clampScroll(scroll);
        List<Integer> visible = new ArrayList<>();
        for (int i = 0; i < pageCount; i++) {
            if (tops[i] < offset + height && tops[i] + heights[i] > offset) visible.add(i + 1);
        }
        return Collections.unmodifiableList(visible);
    }

    /** Zero means the point is outside the viewport or in a page gap. */
    public int pageAt(float x, float y, float scroll) {
        if (!NotebookPage.finite(x) || !NotebookPage.finite(y) || x < 0 || x > width || y < 0 || y > height) return 0;
        float position = y + clampScroll(scroll);
        for (int i = 0; i < pageCount; i++) {
            if (position >= tops[i] && position <= tops[i] + heights[i]) return i + 1;
        }
        return 0;
    }

    /** Chooses the nearest page when the viewport center falls inside a gap. */
    public int centeredPage(float scroll) {
        float center = clampScroll(scroll) + height / 2f;
        int closest = 1;
        float distance = Float.MAX_VALUE;
        for (int i = 0; i < pageCount; i++) {
            float delta = Math.max(tops[i] - center, Math.max(0, center - tops[i] - heights[i]));
            if (delta < distance) { distance = delta; closest = i + 1; }
        }
        return closest;
    }

    /** Builds only visible tracks; paper/background composition is handled by the native adapter. */
    public NotebookPage compose(Map<Integer, NotebookPage> pages, float scroll) {
        if (pages == null) throw new IllegalArgumentException("Missing loaded pages");
        List<NotebookPage.Stroke> result = new ArrayList<>();
        int count = 0;
        for (int id : visiblePages(scroll)) {
            NotebookPage page = pages.get(id);
            if (page == null) continue;
            if (page.width != pageWidth(id) || page.height != pageHeight(id)) {
                throw new IllegalArgumentException("Loaded page dimensions differ from viewport layout");
            }
            float scale = scale(id), top = pageTop(id, scroll);
            for (NotebookPage.Stroke stroke : page.strokes) {
                List<NotebookPage.Point> screen = transform(stroke.points, scale, 0, top);
                for (List<NotebookPage.Point> piece : clip(screen, 0, Math.max(0, top), width,
                        Math.min(height, top + heights[index(id)]))) {
                    result.add(new NotebookPage.Stroke(stroke.penType, stroke.width * scale, stroke.color, piece));
                    count = Math.addExact(count, piece.size());
                }
            }
        }
        return NotebookPage.restored(width, height, NotebookPage.Template.BLANK, result, count);
    }

    /** Clips a completed screen stroke into independently stored page-local pieces. */
    public Map<Integer, List<NotebookPage.Stroke>> splitStroke(NotebookPage.Stroke stroke, float scroll) {
        if (stroke == null) throw new IllegalArgumentException("Missing viewport stroke");
        Map<Integer, List<NotebookPage.Stroke>> result = new LinkedHashMap<>();
        for (int id : visiblePages(scroll)) {
            float top = pageTop(id, scroll), scale = scale(id);
            List<List<NotebookPage.Point>> pieces = clip(stroke.points, 0, Math.max(0, top), width,
                    Math.min(height, top + heights[index(id)]));
            if (pieces.isEmpty()) continue;
            List<NotebookPage.Stroke> local = new ArrayList<>();
            for (List<NotebookPage.Point> piece : pieces) {
                local.add(new NotebookPage.Stroke(stroke.penType, stroke.width / scale, stroke.color,
                        transformClipped(piece, 1 / scale, 0, -top / scale, pageWidth(id), pageHeight(id))));
            }
            result.put(id, Collections.unmodifiableList(local));
        }
        return Collections.unmodifiableMap(result);
    }

    /** Maps erasing without connecting separate visits to a page across the inter-page gap. */
    public List<EraseGesture> splitErase(List<NotebookPage.Point> path, float radius, float scroll) {
        checkPath(path);
        if (!NotebookPage.finite(radius) || radius < 0) throw new IllegalArgumentException("Invalid eraser radius");
        List<EraseGesture> result = new ArrayList<>();
        for (int id : visiblePages(scroll)) {
            float top = pageTop(id, scroll), scale = scale(id);
            List<List<NotebookPage.Point>> pieces = clip(path, 0, Math.max(0, top), width,
                    Math.min(height, top + heights[index(id)]));
            if (pieces.isEmpty()) continue;
            List<List<NotebookPage.Point>> local = new ArrayList<>();
            for (List<NotebookPage.Point> piece : pieces) local.add(transformClipped(piece, 1 / scale, 0,
                    -top / scale, pageWidth(id), pageHeight(id)));
            result.add(new EraseGesture(id, local, radius / scale));
        }
        return Collections.unmodifiableList(result);
    }

    private int index(int pageId) {
        if (pageId < 1 || pageId > pageCount) throw new IllegalArgumentException("Page is out of range");
        return pageId - 1;
    }

    private static List<Size> uniform(int count, int width, int height) {
        if (count < 1 || count > NotebookIndex.MAX_PAGES) throw new IllegalArgumentException("Invalid page count");
        List<Size> sizes = new ArrayList<>();
        Size size = new Size(width, height);
        for (int i = 0; i < count; i++) sizes.add(size);
        return sizes;
    }

    private static List<NotebookPage.Point> transform(List<NotebookPage.Point> points, float scale, float x, float y) {
        List<NotebookPage.Point> result = new ArrayList<>();
        for (NotebookPage.Point point : points) result.add(new NotebookPage.Point(point.x * scale + x,
                point.y * scale + y, point.time, point.pressure));
        return result;
    }

    /** Only use after geometric clipping: the inverse float transform may round past an edge. */
    private static List<NotebookPage.Point> transformClipped(List<NotebookPage.Point> points, float scale,
                                                             float x, float y, float width, float height) {
        List<NotebookPage.Point> result = new ArrayList<>();
        for (NotebookPage.Point point : points) result.add(clamp(new NotebookPage.Point(point.x * scale + x,
                point.y * scale + y, point.time, point.pressure), 0, 0, width, height));
        return result;
    }

    private static void checkPath(List<NotebookPage.Point> path) {
        if (path == null || path.isEmpty()) throw new IllegalArgumentException("Missing pen path");
        for (NotebookPage.Point point : path) if (point == null) throw new IllegalArgumentException("Missing pen point");
    }

    /** Liang-Barsky segment clipping. Rejected excursions end a piece instead of drawing a chord. */
    private static List<List<NotebookPage.Point>> clip(List<NotebookPage.Point> path,
                                                       float left, float top, float right, float bottom) {
        checkPath(path);
        List<List<NotebookPage.Point>> pieces = new ArrayList<>();
        if (right < left || bottom < top) return pieces;
        if (path.size() == 1) {
            NotebookPage.Point point = path.get(0);
            if (inside(point, left, top, right, bottom)) pieces.add(Collections.singletonList(point));
            return pieces;
        }
        List<NotebookPage.Point> current = null;
        for (int i = 1; i < path.size(); i++) {
            NotebookPage.Point a = path.get(i - 1), b = path.get(i);
            double dx = (double) b.x - a.x, dy = (double) b.y - a.y;
            double[] range = {0, 1};
            if (!bound(-dx, a.x - left, range) || !bound(dx, right - a.x, range)
                    || !bound(-dy, a.y - top, range) || !bound(dy, bottom - a.y, range)) {
                current = null;
                continue;
            }
            // Interpolation can round a boundary crossing a fraction of a pixel outside the
            // rectangle. Clamp the accepted endpoints, never the original off-page path.
            NotebookPage.Point start = clamp(interpolate(a, b, range[0]), left, top, right, bottom);
            NotebookPage.Point end = clamp(interpolate(a, b, range[1]), left, top, right, bottom);
            if (current == null || range[0] > 0) {
                current = new ArrayList<>(); pieces.add(current); current.add(start);
            }
            if (!same(current.get(current.size() - 1), end)) current.add(end);
            if (range[1] < 1) current = null;
        }
        return pieces;
    }

    private static boolean inside(NotebookPage.Point p, float left, float top, float right, float bottom) {
        return p.x >= left && p.x <= right && p.y >= top && p.y <= bottom;
    }
    private static NotebookPage.Point clamp(NotebookPage.Point p, float left, float top, float right, float bottom) {
        float x = Math.max(left, Math.min(right, p.x)), y = Math.max(top, Math.min(bottom, p.y));
        return x == p.x && y == p.y ? p : new NotebookPage.Point(x, y, p.time, p.pressure);
    }
    private static boolean bound(double p, double q, double[] range) {
        if (p == 0) return q >= 0;
        double t = q / p;
        if (p < 0) { if (t > range[1]) return false; range[0] = Math.max(range[0], t); }
        else { if (t < range[0]) return false; range[1] = Math.min(range[1], t); }
        return true;
    }
    private static NotebookPage.Point interpolate(NotebookPage.Point a, NotebookPage.Point b, double t) {
        if (t == 0) return a;
        if (t == 1) return b;
        return new NotebookPage.Point((float) (a.x + ((double) b.x - a.x) * t),
                (float) (a.y + ((double) b.y - a.y) * t),
                Math.max(0, Math.round(a.time + ((double) b.time - a.time) * t)),
                (float) (a.pressure + ((double) b.pressure - a.pressure) * t));
    }
    private static boolean same(NotebookPage.Point a, NotebookPage.Point b) {
        return a.x == b.x && a.y == b.y && a.time == b.time && a.pressure == b.pressure;
    }
}
