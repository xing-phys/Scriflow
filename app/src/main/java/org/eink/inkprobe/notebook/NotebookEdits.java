package org.eink.inkprobe.notebook;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Collections;
import java.util.List;

/** Session-local stroke editing. Call only after a complete pen/eraser gesture. */
public final class NotebookEdits {
    public static final int HISTORY_LIMIT = 100;
    private NotebookPage page;
    // Stroke objects (including their point arrays) are immutable and shared by every snapshot.
    private static final class History {
        final List<NotebookPage.Stroke> strokes;
        final Object link;
        History(List<NotebookPage.Stroke> strokes, Object link) { this.strokes = strokes; this.link = link; }
    }
    private final Deque<History> undo = new ArrayDeque<>();
    private final Deque<History> redo = new ArrayDeque<>();

    public NotebookEdits(NotebookPage initial) {
        if (initial == null) throw new IllegalArgumentException("Missing page");
        page = initial;
    }

    public NotebookPage current() { return page; }
    public boolean canUndo() { return !undo.isEmpty(); }
    public boolean canRedo() { return !redo.isEmpty(); }
    Object undoLink() { return undo.isEmpty() ? null : undo.peekLast().link; }
    Object redoLink() { return redo.isEmpty() ? null : redo.peekLast().link; }
    NotebookPage historyTarget(boolean forward) {
        History target = (forward ? redo : undo).peekLast();
        return target == null ? null : page.withStrokes(target.strokes);
    }
    void replaceLinked(NotebookPage next, Object link) {
        if (link == null) throw new IllegalArgumentException("Missing linked history token");
        commit(next, link);
    }

    /** Establish the logical page size once; later viewport changes cannot resize a page. */
    public void setPageSize(int width, int height) {
        if (width <= 0 || height <= 0 || width > NotebookStore.MAX_DIMENSION
                || height > NotebookStore.MAX_DIMENSION) {
            throw new IllegalArgumentException("Invalid page size");
        }
        if (!page.isInitialized()) page = page.withSize(width, height);
    }

    /** Paper selection is independent of stroke history. */
    public void setTemplate(NotebookPage.Template template) {
        if (template == null) throw new IllegalArgumentException("Missing paper template");
        page = page.withTemplate(template);
    }

    public boolean append(NotebookPage.Stroke stroke) {
        return appendAll(Collections.singletonList(stroke));
    }

    /** Disconnected pieces of one physical pen gesture share one history command. */
    public boolean appendAll(List<NotebookPage.Stroke> strokes) {
        if (strokes == null) throw new IllegalArgumentException("Missing strokes");
        if (strokes.isEmpty()) return false;
        if (!page.isInitialized()) throw new IllegalStateException("Page size is not initialized");
        if ((long) page.strokes.size() + strokes.size() > NotebookStore.MAX_STROKES) {
            throw new IllegalArgumentException("Notebook stroke/point limit reached");
        }
        ArrayList<NotebookPage.Stroke> combined = new ArrayList<>(page.strokes);
        java.util.HashSet<java.util.UUID> ids = new java.util.HashSet<>();
        for (NotebookPage.Stroke existing : page.strokes) ids.add(existing.id);
        long points = page.pointCount;
        for (NotebookPage.Stroke stroke : strokes) {
            if (stroke == null) throw new IllegalArgumentException("Missing stroke");
            if (!ids.add(stroke.id)) throw new IllegalArgumentException("Duplicate stroke ID");
            points += stroke.points.size();
            if (points > NotebookStore.MAX_TOTAL_POINTS) {
                throw new IllegalArgumentException("Notebook stroke/point limit reached");
            }
            for (NotebookPage.Point point : stroke.points) {
                if (point.x < 0 || point.y < 0 || point.x > page.width || point.y > page.height) {
                    throw new IllegalArgumentException("Stroke is outside page bounds");
                }
            }
            combined.add(stroke);
        }
        return commit(page.withStrokes(combined));
    }

    /**
     * Remove whole strokes touched by one completed eraser path, as one undoable edit.
     * Radius and coordinates use logical page pixels. Empty/no-hit paths preserve redo.
     * The caller must discard canceled gestures instead of submitting their partial paths.
     */
    public boolean erase(List<NotebookPage.Point> path, float radius) {
        return erasePaths(Collections.singletonList(path), radius);
    }

    /** Disconnected portions of one viewport gesture are one edit, without phantom joins. */
    public boolean erasePaths(List<List<NotebookPage.Point>> paths, float radius) {
        if (paths == null || !NotebookPage.finite(radius) || radius < 0) {
            throw new IllegalArgumentException("Invalid eraser path/radius");
        }
        ArrayList<Segment[]> segments = new ArrayList<>();
        ArrayList<Bounds> bounds = new ArrayList<>();
        for (List<NotebookPage.Point> path : paths) {
            if (path == null) throw new IllegalArgumentException("Missing eraser path");
            for (NotebookPage.Point point : path) {
                if (point == null) throw new IllegalArgumentException("Missing eraser point");
            }
            if (!path.isEmpty()) { segments.add(segments(path)); bounds.add(new Bounds(path)); }
        }
        if (segments.isEmpty() || page.strokes.isEmpty()) return false;
        ArrayList<NotebookPage.Stroke> kept = new ArrayList<>(page.strokes.size());
        for (NotebookPage.Stroke stroke : page.strokes) {
            double reach = (double) radius + stroke.width / 2.0;
            Bounds inkBounds = new Bounds(stroke.points);
            boolean hit = false;
            for (int i = 0; i < segments.size() && !hit; i++) {
                hit = bounds.get(i).near(inkBounds, reach) && touches(stroke, segments.get(i), reach);
            }
            if (!hit) kept.add(stroke);
        }
        if (kept.size() == page.strokes.size()) return false;
        return commit(page.withStrokes(kept));
    }

    public boolean undo() {
        if (undo.isEmpty()) return false;
        History previous = undo.removeLast();
        redo.addLast(new History(page.strokes, previous.link));
        page = page.withStrokes(previous.strokes);
        return true;
    }

    /** One completed lasso action is one history entry; moved strokes keep their IDs/order. */
    public NotebookLasso.Selection editSelection(NotebookLasso.Selection selection,
            NotebookLasso.Action action, float dx, float dy) {
        if (selection == null || action == null) throw new IllegalArgumentException("Missing selection/action");
        selection.validate(page);
        if (selection.isEmpty()) return selection;
        List<NotebookPage.Stroke> replacement = action == NotebookLasso.Action.DELETE
            ? Collections.emptyList() : NotebookLasso.translated(selection, page, dx, dy,
                action == NotebookLasso.Action.DUPLICATE);
        if (action == NotebookLasso.Action.DUPLICATE) appendAll(replacement);
        else {
            java.util.Map<java.util.UUID, NotebookPage.Stroke> byId = new java.util.HashMap<>();
            for (NotebookPage.Stroke stroke : replacement) byId.put(stroke.id, stroke);
            java.util.Set<NotebookPage.Stroke> selected = new java.util.HashSet<>(selection.strokes);
            List<NotebookPage.Stroke> next = new ArrayList<>();
            for (NotebookPage.Stroke stroke : page.strokes) {
                if (!selected.contains(stroke)) next.add(stroke);
                else if (byId.containsKey(stroke.id)) next.add(byId.get(stroke.id));
            }
            if (!next.equals(page.strokes)) commit(page.withStrokes(next));
        }
        return new NotebookLasso.Selection(page.id, replacement);
    }

    public NotebookLasso.Selection paste(NotebookLasso.Clipboard clipboard, float x, float y) {
        if (clipboard == null) throw new IllegalArgumentException("Empty clipboard");
        List<NotebookPage.Stroke> strokes = clipboard.place(page, x, y);
        appendAll(strokes);
        return new NotebookLasso.Selection(page.id, strokes);
    }

    public boolean redo() {
        if (redo.isEmpty()) return false;
        History next = redo.removeLast();
        undo.addLast(new History(page.strokes, next.link));
        page = page.withStrokes(next.strokes);
        return true;
    }

    private boolean commit(NotebookPage next) {
        return commit(next, null);
    }

    private boolean commit(NotebookPage next, Object link) {
        undo.addLast(new History(page.strokes, link));
        if (undo.size() > HISTORY_LIMIT) undo.removeFirst();
        redo.clear();
        page = next;
        return true;
    }

    private static boolean touches(NotebookPage.Stroke stroke, Segment[] eraser, double reach) {
        double squared = reach * reach;
        List<NotebookPage.Point> points = stroke.points;
        for (int i = 0; i < Math.max(1, points.size() - 1); i++) {
            Segment ink = new Segment(points.get(i), points.get(Math.min(i + 1, points.size() - 1)));
            for (Segment rub : eraser) {
                if (ink.near(rub, reach) && distanceSquared(ink, rub) <= squared) return true;
            }
        }
        return false;
    }

    private static Segment[] segments(List<NotebookPage.Point> points) {
        Segment[] result = new Segment[Math.max(1, points.size() - 1)];
        for (int i = 0; i < result.length; i++) {
            result[i] = new Segment(points.get(i), points.get(Math.min(i + 1, points.size() - 1)));
        }
        return result;
    }

    private static double distanceSquared(Segment a, Segment b) {
        double o1 = orientation(a.ax, a.ay, a.bx, a.by, b.ax, b.ay);
        double o2 = orientation(a.ax, a.ay, a.bx, a.by, b.bx, b.by);
        double o3 = orientation(b.ax, b.ay, b.bx, b.by, a.ax, a.ay);
        double o4 = orientation(b.ax, b.ay, b.bx, b.by, a.bx, a.by);
        if (a.near(b, 0) && straddles(o1, o2) && straddles(o3, o4)) return 0;
        return Math.min(Math.min(pointDistanceSquared(a.ax, a.ay, b), pointDistanceSquared(a.bx, a.by, b)),
                Math.min(pointDistanceSquared(b.ax, b.ay, a), pointDistanceSquared(b.bx, b.by, a)));
    }

    private static boolean straddles(double a, double b) {
        return (a <= 0 && b >= 0) || (a >= 0 && b <= 0);
    }

    private static double orientation(double ax, double ay, double bx, double by, double cx, double cy) {
        return (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
    }

    private static double pointDistanceSquared(double x, double y, Segment line) {
        double dx = line.bx - line.ax, dy = line.by - line.ay;
        double lengthSquared = dx * dx + dy * dy;
        double t = lengthSquared == 0 ? 0
                : Math.max(0, Math.min(1, ((x - line.ax) * dx + (y - line.ay) * dy) / lengthSquared));
        double ex = x - (line.ax + t * dx), ey = y - (line.ay + t * dy);
        return ex * ex + ey * ey;
    }

    private static class Bounds {
        double minX, minY, maxX, maxY;
        Bounds(List<NotebookPage.Point> points) {
            minX = maxX = points.get(0).x;
            minY = maxY = points.get(0).y;
            for (NotebookPage.Point point : points) {
                minX = Math.min(minX, point.x); maxX = Math.max(maxX, point.x);
                minY = Math.min(minY, point.y); maxY = Math.max(maxY, point.y);
            }
        }
        Bounds(NotebookPage.Point a, NotebookPage.Point b) {
            minX = Math.min(a.x, b.x); maxX = Math.max(a.x, b.x);
            minY = Math.min(a.y, b.y); maxY = Math.max(a.y, b.y);
        }
        boolean near(Bounds other, double reach) {
            return minX <= other.maxX + reach && other.minX <= maxX + reach
                    && minY <= other.maxY + reach && other.minY <= maxY + reach;
        }
    }

    private static final class Segment extends Bounds {
        final double ax, ay, bx, by;
        Segment(NotebookPage.Point a, NotebookPage.Point b) {
            super(a, b);
            ax = a.x; ay = a.y; bx = b.x; by = b.y;
        }
    }
}
