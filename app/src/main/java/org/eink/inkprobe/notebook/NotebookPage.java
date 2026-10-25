package org.eink.inkprobe.notebook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Immutable, Android-independent page. Coordinates are fixed logical page pixels. */
public final class NotebookPage {
    public enum Template { BLANK, LINED }

    public static final class Point {
        public final float x, y, pressure;
        /** Pen event time; retained as recorded, not a wall clock timestamp. */
        public final long time;

        public Point(float x, float y, long time, float pressure) {
            if (!finite(x) || !finite(y) || !finite(pressure) || pressure < 0 || time < 0) {
                throw new IllegalArgumentException("Invalid pen point");
            }
            this.x = x;
            this.y = y;
            this.time = time;
            this.pressure = pressure;
        }
    }

    public static final class Stroke {
        public final UUID id;
        public final int penType, color;
        public final float width;
        public final List<Point> points;

        public Stroke(int penType, float width, int color, List<Point> points) {
            this(UUID.randomUUID(), penType, width, color, points);
        }

        public Stroke(UUID id, int penType, float width, int color, List<Point> points) {
            if (id == null) throw new IllegalArgumentException("Missing stroke ID");
            this.id = id;
            if (penType < 0 || penType > 255 || !finite(width) || width <= 0 || width > 1024) {
                throw new IllegalArgumentException("Invalid pen style");
            }
            if (points == null || points.isEmpty() || points.size() > NotebookStore.MAX_POINTS_PER_STROKE) {
                throw new IllegalArgumentException("Invalid stroke point count");
            }
            ArrayList<Point> copy = new ArrayList<>(points);
            for (Point point : copy) {
                if (point == null) throw new IllegalArgumentException("Missing pen point");
            }
            this.penType = penType;
            this.width = width;
            this.color = color;
            this.points = Collections.unmodifiableList(copy);
        }
    }

    /** Both dimensions are zero until the first writing surface establishes page size. */
    public final UUID id;
    public final int width, height;
    public final Template template;
    public final List<Stroke> strokes;
    public final int pointCount;

    private NotebookPage(UUID id, int width, int height, Template template, List<Stroke> strokes, int pointCount) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.template = template;
        this.strokes = Collections.unmodifiableList(new ArrayList<>(strokes));
        this.pointCount = pointCount;
    }

    public static NotebookPage empty() {
        return new NotebookPage(UUID.randomUUID(), 0, 0, Template.BLANK, Collections.<Stroke>emptyList(), 0);
    }

    public boolean isInitialized() { return width > 0 && height > 0; }

    NotebookPage withSize(int width, int height) {
        return new NotebookPage(id, width, height, template, strokes, pointCount);
    }

    NotebookPage withTemplate(Template template) {
        return new NotebookPage(id, width, height, template, strokes, pointCount);
    }

    NotebookPage withStroke(Stroke stroke) {
        ArrayList<Stroke> copy = new ArrayList<>(strokes);
        copy.add(stroke);
        return new NotebookPage(id, width, height, template, copy, pointCount + stroke.points.size());
    }

    NotebookPage withStrokes(List<Stroke> strokes) {
        int count = 0;
        for (Stroke stroke : strokes) count = Math.addExact(count, stroke.points.size());
        return new NotebookPage(id, width, height, template, strokes, count);
    }

    static NotebookPage restored(int width, int height, Template template, List<Stroke> strokes,
                                 int pointCount) {
        return restored(UUID.randomUUID(), width, height, template, strokes, pointCount);
    }

    static NotebookPage restored(UUID id, int width, int height, Template template,
                                  List<Stroke> strokes, int pointCount) {
        if (id == null || template == null) throw new IllegalArgumentException("Missing page metadata");
        return new NotebookPage(id, width, height, template, strokes, pointCount);
    }

    static boolean finite(float value) { return !Float.isNaN(value) && !Float.isInfinite(value); }
}
