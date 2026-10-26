package org.eink.inkprobe.notebook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Whole-stroke selection and translation, independent of Android and native preview ink. */
public final class NotebookLasso {
    public enum Action { MOVE, DUPLICATE, DELETE }

    public static final class Bounds {
        public final float left, top, right, bottom;
        Bounds(List<NotebookPage.Stroke> strokes) {
            float l = Float.MAX_VALUE, t = Float.MAX_VALUE, r = -Float.MAX_VALUE, b = -Float.MAX_VALUE;
            for (NotebookPage.Stroke stroke : strokes) for (NotebookPage.Point point : stroke.points) {
                l = Math.min(l, point.x); t = Math.min(t, point.y);
                r = Math.max(r, point.x); b = Math.max(b, point.y);
            }
            left = l; top = t; right = r; bottom = b;
        }
        public boolean contains(float x, float y, float padding) {
            return x >= left - padding && x <= right + padding && y >= top - padding && y <= bottom + padding;
        }
    }

    public static final class Selection {
        public final UUID pageId;
        public final List<NotebookPage.Stroke> strokes;
        public final Bounds bounds;
        Selection(UUID pageId, List<NotebookPage.Stroke> strokes) {
            this.pageId = pageId;
            this.strokes = Collections.unmodifiableList(new ArrayList<>(strokes));
            bounds = strokes.isEmpty() ? null : new Bounds(strokes);
        }
        public boolean isEmpty() { return strokes.isEmpty(); }
        void validate(NotebookPage page) {
            if (!page.id.equals(pageId)) throw new IllegalArgumentException("Selection belongs to another page");
            Set<NotebookPage.Stroke> remaining = new HashSet<>(strokes);
            remaining.removeAll(page.strokes);
            if (!remaining.isEmpty()) throw new IllegalArgumentException("Selection has changed; select the ink again");
        }
    }

    /** Private, process-local vector clipboard; pressure, times and styles stay intact. */
    public static final class Clipboard {
        public final List<NotebookPage.Stroke> strokes;
        public final Bounds bounds;
        public Clipboard(Selection selection) {
            if (selection == null || selection.isEmpty()) throw new IllegalArgumentException("Empty selection");
            strokes = selection.strokes; bounds = selection.bounds;
        }
        public boolean fits(NotebookPage page) {
            return bounds.right - bounds.left <= page.width && bounds.bottom - bounds.top <= page.height;
        }
        public List<NotebookPage.Stroke> place(NotebookPage page, float centerX, float centerY) {
            if (!fits(page)) throw new IllegalArgumentException("Copied ink is larger than this page");
            return translated(strokes, clamp(centerX - (bounds.left + bounds.right) / 2,
                -bounds.left, page.width - bounds.right), clamp(centerY - (bounds.top + bounds.bottom) / 2,
                -bounds.top, page.height - bounds.bottom), true);
        }
    }

    private NotebookLasso() { }

    public static Selection all(NotebookPage page) { return new Selection(page.id, page.strokes); }

    /** Store the closing edge explicitly so visible feedback and hit testing use the same loop. */
    public static List<NotebookPage.Point> closedOutline(List<NotebookPage.Point> path) {
        if (path == null) throw new IllegalArgumentException("Missing lasso outline");
        ArrayList<NotebookPage.Point> closed = new ArrayList<>(path);
        for (NotebookPage.Point point : closed) if (point == null) throw new IllegalArgumentException("Missing lasso point");
        if (closed.size() > 1) {
            NotebookPage.Point first = closed.get(0), last = closed.get(closed.size() - 1);
            if (first.x != last.x || first.y != last.y)
                closed.add(new NotebookPage.Point(first.x, first.y, last.time, first.pressure));
        }
        return Collections.unmodifiableList(closed);
    }

    public static List<NotebookPage.Point> offsetOutline(List<NotebookPage.Point> path, float dx, float dy) {
        if (!NotebookPage.finite(dx) || !NotebookPage.finite(dy)) throw new IllegalArgumentException("Invalid outline offset");
        List<NotebookPage.Point> translated = new ArrayList<>(path.size());
        for (NotebookPage.Point p : path) translated.add(new NotebookPage.Point(p.x + dx, p.y + dy, p.time, p.pressure));
        return Collections.unmodifiableList(translated);
    }

    /** Auto-close the outline. Every centreline segment must be inside (boundary included). */
    public static Selection select(NotebookPage page, List<NotebookPage.Point> polygon) {
        if (page == null || polygon == null) throw new IllegalArgumentException("Missing lasso/page");
        for (NotebookPage.Point point : polygon) if (point == null) throw new IllegalArgumentException("Missing lasso point");
        List<NotebookPage.Stroke> selected = new ArrayList<>();
        if (polygon.size() < 3 || !hasArea(polygon)) return new Selection(page.id, selected);
        float l = Float.MAX_VALUE, t = Float.MAX_VALUE, r = -Float.MAX_VALUE, b = -Float.MAX_VALUE;
        for (NotebookPage.Point p : polygon) { l = Math.min(l, p.x); t = Math.min(t, p.y); r = Math.max(r, p.x); b = Math.max(b, p.y); }
        for (NotebookPage.Stroke stroke : page.strokes) {
            Bounds bounds = new Bounds(Collections.singletonList(stroke));
            if (bounds.left < l || bounds.right > r || bounds.top < t || bounds.bottom > b) continue;
            boolean inside = true;
            for (int i = 0; i < stroke.points.size() && inside; i++) {
                NotebookPage.Point p = stroke.points.get(i);
                inside = contains(polygon, p.x, p.y);
                if (inside && i > 0) inside = segmentInside(polygon, stroke.points.get(i - 1), p);
            }
            if (inside) selected.add(stroke);
        }
        return new Selection(page.id, selected);
    }

    static List<NotebookPage.Stroke> translated(Selection selection, NotebookPage page, float dx, float dy, boolean copy) {
        selection.validate(page);
        if (selection.isEmpty()) return Collections.emptyList();
        Bounds b = selection.bounds;
        return translated(selection.strokes, clamp(dx, -b.left, page.width - b.right),
            clamp(dy, -b.top, page.height - b.bottom), copy);
    }

    private static List<NotebookPage.Stroke> translated(List<NotebookPage.Stroke> strokes, float dx, float dy, boolean copy) {
        if (!NotebookPage.finite(dx) || !NotebookPage.finite(dy)) throw new IllegalArgumentException("Invalid ink offset");
        List<NotebookPage.Stroke> result = new ArrayList<>();
        for (NotebookPage.Stroke stroke : strokes) {
            if (!copy && dx == 0 && dy == 0) { result.add(stroke); continue; }
            List<NotebookPage.Point> points = new ArrayList<>();
            for (NotebookPage.Point p : stroke.points) points.add(new NotebookPage.Point(p.x + dx, p.y + dy, p.time, p.pressure));
            result.add(new NotebookPage.Stroke(copy ? UUID.randomUUID() : stroke.id,
                stroke.penType, stroke.width, stroke.color, points));
        }
        return result;
    }

    private static float clamp(float value, float min, float max) {
        if (!NotebookPage.finite(value)) throw new IllegalArgumentException("Invalid ink offset");
        return Math.max(min, Math.min(max, value));
    }

    private static boolean hasArea(List<NotebookPage.Point> p) {
        NotebookPage.Point a = p.get(0);
        for (int i = 1; i + 1 < p.size(); i++)
            if (Math.abs(cross(p.get(i).x - a.x, p.get(i).y - a.y,
                    p.get(i + 1).x - a.x, p.get(i + 1).y - a.y)) > 0.001) return true;
        return false;
    }

    public static boolean contains(List<NotebookPage.Point> polygon, double x, double y) {
        boolean inside = false;
        for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
            NotebookPage.Point a = polygon.get(j), b = polygon.get(i);
            double dx = b.x - a.x, dy = b.y - a.y;
            if (Math.abs(cross(dx, dy, x - a.x, y - a.y)) < 0.00001
                    && x >= Math.min(a.x, b.x) - 0.00001 && x <= Math.max(a.x, b.x) + 0.00001
                    && y >= Math.min(a.y, b.y) - 0.00001 && y <= Math.max(a.y, b.y) + 0.00001) return true;
            if ((a.y > y) != (b.y > y) && x < a.x + (y - a.y) * dx / dy) inside = !inside;
        }
        return inside;
    }

    /** Split at polygon intersections, including vertices; check all open intervals. */
    private static boolean segmentInside(List<NotebookPage.Point> polygon, NotebookPage.Point a, NotebookPage.Point b) {
        double dx = b.x - a.x, dy = b.y - a.y;
        if (dx == 0 && dy == 0) return true;
        List<Double> cuts = new ArrayList<>(); cuts.add(0.0); cuts.add(1.0);
        for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
            NotebookPage.Point c = polygon.get(j), d = polygon.get(i);
            double ex = d.x - c.x, ey = d.y - c.y, det = cross(dx, dy, ex, ey);
            if (Math.abs(det) < 1e-10) continue;
            double t = cross(c.x - a.x, c.y - a.y, ex, ey) / det;
            double u = cross(c.x - a.x, c.y - a.y, dx, dy) / det;
            if (t > 0 && t < 1 && u >= 0 && u <= 1) cuts.add(t);
        }
        Collections.sort(cuts);
        for (int i = 1; i < cuts.size(); i++) {
            double t = (cuts.get(i - 1) + cuts.get(i)) / 2;
            if (!contains(polygon, a.x + dx * t, a.y + dy * t)) return false;
        }
        return true;
    }
    private static double cross(double ax, double ay, double bx, double by) { return ax * by - ay * bx; }
}
