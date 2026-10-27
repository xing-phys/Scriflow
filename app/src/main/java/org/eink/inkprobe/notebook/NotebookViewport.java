package org.eink.inkprobe.notebook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure geometry for a continuous, zoomable stack of independently stored pages. */
public final class NotebookViewport {
    /** Percentages are relative to the width-fitted view, independent of display density. */
    public enum Fit { WIDTH, HEIGHT, PAGE, CUSTOM }
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
    public final float zoom, pan;
    private final List<Size> sizes;
    private final float[] tops, scales, heights;
    private final float totalHeight;

    public NotebookViewport(int pageCount, int logicalWidth, int logicalHeight,
                            int viewportWidth, int viewportHeight, float gap) {
        this(uniform(pageCount, logicalWidth, logicalHeight), viewportWidth, viewportHeight, gap);
    }

    public NotebookViewport(List<Size> sizes, int viewportWidth, int viewportHeight, float gap) {
        this(sizes, viewportWidth, viewportHeight, gap, 1, 0);
    }

    public NotebookViewport(int pageCount, int logicalWidth, int logicalHeight,
                            int viewportWidth, int viewportHeight, float gap, float zoom, float pan) {
        this(uniform(pageCount, logicalWidth, logicalHeight), viewportWidth, viewportHeight, gap, zoom, pan);
    }

    public NotebookViewport(List<Size> sizes, int viewportWidth, int viewportHeight, float gap, float zoom, float pan) {
        if (sizes == null || sizes.isEmpty() || sizes.size() > NotebookIndex.MAX_PAGES
                || viewportWidth <= 0 || viewportHeight <= 0 || !NotebookPage.finite(gap) || gap < 0
                || !NotebookPage.finite(zoom) || zoom <= 0 || !NotebookPage.finite(pan)) {
            throw new IllegalArgumentException("Invalid notebook viewport");
        }
        this.width = viewportWidth; this.height = viewportHeight; this.gap = gap;
        this.zoom = zoom;
        this.pan = Math.max(0, Math.min(Math.max(0, width * zoom - width), pan));
        this.sizes = Collections.unmodifiableList(new ArrayList<>(sizes));
        pageCount = sizes.size();
        tops = new float[pageCount]; scales = new float[pageCount]; heights = new float[pageCount];
        float top = 0;
        for (int i = 0; i < pageCount; i++) {
            Size size = this.sizes.get(i);
            if (size == null) throw new IllegalArgumentException("Missing page dimensions");
            scales[i] = (float) width / size.width * zoom;
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
    public float pageLeft(int pageId) { index(pageId); return zoom <= 1 ? width * (1 - zoom) / 2 : -pan; }
    public float pageRight(int pageId) { return pageLeft(pageId) + pageWidth(pageId) * scale(pageId); }
    public float maxPan() { return Math.max(0, width * zoom - width); }
    public float clampPan(float position) {
        if (!NotebookPage.finite(position)) throw new IllegalArgumentException("Invalid horizontal position");
        return Math.max(0, Math.min(maxPan(), position));
    }
    public NotebookViewport withView(float zoom, float pan) { return new NotebookViewport(sizes, width, height, gap, zoom, pan); }

    public float fittedZoom(Fit fit, int pageId) {
        float heightFit = (float) height * pageWidth(pageId) / (width * (float) pageHeight(pageId));
        if (fit == Fit.WIDTH) return 1;
        if (fit == Fit.HEIGHT) return heightFit;
        if (fit == Fit.PAGE) return Math.min(1, heightFit);
        throw new IllegalArgumentException("Custom zoom has no fitted value");
    }

    /** Keep a logical page point under the same screen position during pinch or resize. */
    public float scrollForPoint(int pageId, float logicalY, float screenY) {
        if (!NotebookPage.finite(logicalY) || !NotebookPage.finite(screenY)) throw new IllegalArgumentException("Invalid zoom anchor");
        return clampScroll(tops[index(pageId)] + logicalY * scale(pageId) - screenY);
    }
    public float panForPoint(int pageId, float logicalX, float screenX) {
        if (!NotebookPage.finite(logicalX) || !NotebookPage.finite(screenX)) throw new IllegalArgumentException("Invalid zoom anchor");
        return clampPan(logicalX * scale(pageId) - screenX);
    }
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
            if (x >= pageLeft(i + 1) && x <= pageRight(i + 1)
                    && position >= tops[i] && position <= tops[i] + heights[i]) return i + 1;
        }
        return 0;
    }

    /** Chooses the nearest page when the viewport center falls inside a gap. */
    public int centeredPage(float scroll) {
        return nearestPage(height / 2f, scroll);
    }

    public int nearestPage(float y, float scroll) {
        if (!NotebookPage.finite(y)) throw new IllegalArgumentException("Invalid page position");
        float center = clampScroll(scroll) + y;
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
            float scale = scale(id), top = pageTop(id, scroll), left = pageLeft(id);
            for (NotebookPage.Stroke stroke : page.strokes) {
                float clipLeft = Math.max(0, left), clipTop = Math.max(0, top);
                float clipRight = Math.min(width, pageRight(id));
                float clipBottom = Math.min(height, top + heights[index(id)]);
                if (!intersects(stroke, scale, left, top, clipLeft, clipTop, clipRight, clipBottom)) continue;
                List<NotebookPage.Point> screen = transform(stroke.points, scale, left, top);
                result.add(stroke.inView(stroke.width * scale, screen, stroke.captureScale / scale,
                    clipLeft, clipTop, clipRight, clipBottom));
                count = Math.addExact(count, screen.size());
            }
        }
        return NotebookPage.restored(width, height, NotebookPage.Template.BLANK, result, count);
    }

    private static boolean intersects(NotebookPage.Stroke stroke, float scale, float x, float y,
                                      float left, float top, float right, float bottom) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        float pressure = 1;
        for (NotebookPage.Point point : stroke.points) {
            minX = Math.min(minX, point.x); minY = Math.min(minY, point.y);
            maxX = Math.max(maxX, point.x); maxY = Math.max(maxY, point.y);
            pressure = Math.max(pressure, point.pressure);
        }
        // Conservative native brush padding also retains thick edges whose
        // centreline is outside the viewport, without replaying distant ink.
        float padding = 2 * Math.max(stroke.width * pressure, 1 / stroke.captureScale) * scale;
        return maxX * scale + x + padding >= left && minX * scale + x - padding <= right
            && maxY * scale + y + padding >= top && minY * scale + y - padding <= bottom;
    }

    /** Clips a completed screen stroke into independently stored page-local pieces. */
    public Map<Integer, List<NotebookPage.Stroke>> splitStroke(NotebookPage.Stroke stroke, float scroll) {
        if (stroke == null) throw new IllegalArgumentException("Missing viewport stroke");
        Map<Integer, List<NotebookPage.Stroke>> result = new LinkedHashMap<>();
        for (int id : visiblePages(scroll)) {
            float top = pageTop(id, scroll), scale = scale(id), left = pageLeft(id);
            List<List<NotebookPage.Point>> pieces = clip(stroke.points, Math.max(0, left), Math.max(0, top), Math.min(width, pageRight(id)),
                    Math.min(height, top + heights[index(id)]));
            if (pieces.isEmpty()) continue;
            List<NotebookPage.Stroke> local = new ArrayList<>();
            for (List<NotebookPage.Point> piece : pieces) {
                local.add(new NotebookPage.Stroke(stroke.penType, stroke.width / scale, stroke.color,
                        transformClipped(piece, 1 / scale, -left / scale, -top / scale, pageWidth(id), pageHeight(id)),
                        stroke.captureScale * scale, stroke.captureWidth));
            }
            result.put(id, Collections.unmodifiableList(local));
        }
        return Collections.unmodifiableMap(result);
    }

    /** Map the whole brush footprint, including centres just outside a page's visible edge. */
    public List<EraseGesture> splitErase(List<NotebookPage.Point> path, float radius, float scroll) {
        checkPath(path);
        if (!NotebookPage.finite(radius) || radius < 0) throw new IllegalArgumentException("Invalid eraser radius");
        List<EraseGesture> result = new ArrayList<>();
        for (int id : visiblePages(scroll)) {
            float top = pageTop(id, scroll), scale = scale(id), left = pageLeft(id);
            List<List<NotebookPage.Point>> pieces = clip(path, Math.max(0, left) - radius, Math.max(0, top) - radius, Math.min(width, pageRight(id)) + radius,
                    Math.min(height, top + heights[index(id)]) + radius);
            if (pieces.isEmpty()) continue;
            List<List<NotebookPage.Point>> local = new ArrayList<>();
            // Eraser paths are transient and may lie outside page bounds. Clamping the centres
            // would distort the grey footprint and turn a near miss into a deletion.
            for (List<NotebookPage.Point> piece : pieces) local.add(transform(piece, 1 / scale, -left / scale, -top / scale));
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
        NotebookTransform transform = NotebookTransform.fit(scale, x, y);
        for (NotebookPage.Point point : points) result.add(transform.map(point));
        return result;
    }

    /** Only use after geometric clipping: the inverse float transform may round past an edge. */
    private static List<NotebookPage.Point> transformClipped(List<NotebookPage.Point> points, float scale,
                                                             float x, float y, float width, float height) {
        List<NotebookPage.Point> result = new ArrayList<>();
        NotebookTransform transform = NotebookTransform.fit(scale, x, y);
        for (NotebookPage.Point point : points) result.add(clamp(transform.map(point), 0, 0, width, height));
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
