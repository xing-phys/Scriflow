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

    /** Auto-close the outline. Any centreline contact selects the entire stroke, including boundary touches. */
    public static Selection select(NotebookPage page, List<NotebookPage.Point> polygon) {
        if (page == null || polygon == null) throw new IllegalArgumentException("Missing lasso/page");
        for (NotebookPage.Point point : polygon) if (point == null) throw new IllegalArgumentException("Missing lasso point");
        List<NotebookPage.Stroke> selected = new ArrayList<>();
        if (polygon.size() < 3 || !hasArea(polygon)) return new Selection(page.id, selected);
        float l = Float.MAX_VALUE, t = Float.MAX_VALUE, r = -Float.MAX_VALUE, b = -Float.MAX_VALUE;
        for (NotebookPage.Point p : polygon) { l = Math.min(l, p.x); t = Math.min(t, p.y); r = Math.max(r, p.x); b = Math.max(b, p.y); }
        for (NotebookPage.Stroke stroke : page.strokes) {
            Bounds bounds = new Bounds(Collections.singletonList(stroke));
            if (bounds.right < l || bounds.left > r || bounds.bottom < t || bounds.top > b) continue;
            boolean touches = false;
            for (int i = 0; i < stroke.points.size() && !touches; i++) {
                NotebookPage.Point p = stroke.points.get(i);
                touches = contains(polygon, p.x, p.y);
                if (!touches && i > 0) touches = segmentTouches(polygon, stroke.points.get(i - 1), p);
            }
            if (touches) selected.add(stroke);
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

    /** Reposition existing ink on another page without changing its identities or captured style. */
    static List<NotebookPage.Stroke> relocated(Selection selection, NotebookPage source,
                                              NotebookPage destination, float centerX, float centerY) {
        selection.validate(source);
        if (selection.isEmpty()) return Collections.emptyList();
        if (!destination.isInitialized()) throw new IllegalArgumentException("The destination page is not ready");
        Bounds bounds = selection.bounds;
        if (bounds.right - bounds.left > destination.width || bounds.bottom - bounds.top > destination.height)
            throw new IllegalArgumentException("Selected ink is larger than the destination page");
        return translated(selection.strokes,
            clamp(centerX - (bounds.left + bounds.right) / 2, -bounds.left, destination.width - bounds.right),
            clamp(centerY - (bounds.top + bounds.bottom) / 2, -bounds.top, destination.height - bounds.bottom), false);
    }

    private static List<NotebookPage.Stroke> translated(List<NotebookPage.Stroke> strokes, float dx, float dy, boolean copy) {
        if (!NotebookPage.finite(dx) || !NotebookPage.finite(dy)) throw new IllegalArgumentException("Invalid ink offset");
        List<NotebookPage.Stroke> result = new ArrayList<>();
        for (NotebookPage.Stroke stroke : strokes) {
            if (!copy && dx == 0 && dy == 0) { result.add(stroke); continue; }
            List<NotebookPage.Point> points = new ArrayList<>();
            for (NotebookPage.Point p : stroke.points) points.add(new NotebookPage.Point(p.x + dx, p.y + dy, p.time, p.pressure));
            result.add(new NotebookPage.Stroke(copy ? UUID.randomUUID() : stroke.id,
                stroke.penType, stroke.width, stroke.color, points, stroke.captureScale, stroke.captureWidth));
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

    /** Sparse segments can cross the region even when neither recorded endpoint lies inside it. */
    private static boolean segmentTouches(List<NotebookPage.Point> polygon, NotebookPage.Point a, NotebookPage.Point b) {
        double dx = b.x - a.x, dy = b.y - a.y;
        for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
            NotebookPage.Point c = polygon.get(j), d = polygon.get(i);
            double ex = d.x - c.x, ey = d.y - c.y;
            double ac = cross(dx, dy, c.x - a.x, c.y - a.y);
            double ad = cross(dx, dy, d.x - a.x, d.y - a.y);
            double ca = cross(ex, ey, a.x - c.x, a.y - c.y);
            double cb = cross(ex, ey, b.x - c.x, b.y - c.y);
            if ((ac > 0 && ad < 0 || ac < 0 && ad > 0)
                    && (ca > 0 && cb < 0 || ca < 0 && cb > 0)) return true;
            if (Math.abs(ac) < 0.00001 && onSegment(c, a, b)
                    || Math.abs(ad) < 0.00001 && onSegment(d, a, b)
                    || Math.abs(ca) < 0.00001 && onSegment(a, c, d)
                    || Math.abs(cb) < 0.00001 && onSegment(b, c, d)) return true;
        }
        return false;
    }

    private static boolean onSegment(NotebookPage.Point p, NotebookPage.Point a, NotebookPage.Point b) {
        return p.x >= Math.min(a.x, b.x) && p.x <= Math.max(a.x, b.x)
            && p.y >= Math.min(a.y, b.y) && p.y <= Math.max(a.y, b.y);
    }
    private static double cross(double ax, double ay, double bx, double by) { return ax * by - ay * bx; }
}
