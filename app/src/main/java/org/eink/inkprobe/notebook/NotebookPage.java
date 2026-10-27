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
        /** Native input pixels per logical unit when this stroke was written. */
        public final float captureScale;
        /** Exact native brush width; reconstructing width * captureScale can cross a pixel threshold. */
        public final float captureWidth;
        public final List<Point> points;
        // Viewport-only clipping. Keep full samples so scrolling cannot restart the brush
        // at a clipped MOVE and change its pressure/taper geometry.
        final Clip clip;

        public Stroke(int penType, float width, int color, List<Point> points) {
            this(UUID.randomUUID(), penType, width, color, points);
        }

        public Stroke(UUID id, int penType, float width, int color, List<Point> points) {
            this(id, penType, width, color, points, 1f);
        }

        public Stroke(int penType, float width, int color, List<Point> points, float captureScale) {
            this(UUID.randomUUID(), penType, width, color, points, captureScale);
        }

        public Stroke(UUID id, int penType, float width, int color, List<Point> points, float captureScale) {
            this(id, penType, width, color, points, captureScale, width * captureScale);
        }

        public Stroke(int penType, float width, int color, List<Point> points, float captureScale, float captureWidth) {
            this(UUID.randomUUID(), penType, width, color, points, captureScale, captureWidth);
        }

        public Stroke(UUID id, int penType, float width, int color, List<Point> points,
                      float captureScale, float captureWidth) {
            this(id, penType, width, color, points, captureScale, captureWidth, null);
        }

        private Stroke(UUID id, int penType, float width, int color, List<Point> points,
                       float captureScale, float captureWidth, Clip clip) {
            if (id == null) throw new IllegalArgumentException("Missing stroke ID");
            this.id = id;
            if (penType < 0 || penType > 255 || !finite(width) || width <= 0 || (clip == null && width > 1024)
                    || !finite(captureScale) || captureScale <= 0 || !finite(1f / captureScale)
                    || !finite(captureWidth) || captureWidth <= 0 || captureWidth > 1024
                    || !finite(width * captureScale)) {
                throw new IllegalArgumentException("Invalid pen style");
            }
            if (points == null || points.isEmpty() || points.size() > NotebookStore.MAX_POINTS_PER_STROKE) {
                throw new IllegalArgumentException("Invalid stroke point count");
            }
            ArrayList<Point> copy = new ArrayList<>(points);
            for (Point point : copy) {
                if (point == null) throw new IllegalArgumentException("Missing pen point");
                if (!finite(point.x * captureScale) || !finite(point.y * captureScale))
                    throw new IllegalArgumentException("Invalid native pen coordinate");
            }
            this.penType = penType;
            this.width = width;
            this.color = color;
            this.captureScale = captureScale;
            this.captureWidth = captureWidth;
            this.clip = clip;
            this.points = Collections.unmodifiableList(copy);
        }

        Stroke inView(float width, List<Point> points, float captureScale,
                      float left, float top, float right, float bottom) {
            return new Stroke(id, penType, width, color, points, captureScale, captureWidth,
                new Clip(left, top, right, bottom));
        }
    }

    static final class Clip {
        final float left, top, right, bottom;
        Clip(float left, float top, float right, float bottom) {
            this.left = left; this.top = top; this.right = right; this.bottom = bottom;
        }
    }

    /** Both dimensions are zero until the first writing surface establishes page size. */
    public final UUID id;
    public final int width, height;
    /** One-based page in the notebook's immutable source.pdf; zero for ordinary paper. */
    public final int pdfPage;
    /** Null for legacy pages, whose original A4-width PDF normalization is retained. */
    public final NotebookPaperSize paperSize;
    public final Template template;
    public final List<Stroke> strokes;
    public final int pointCount;

    private NotebookPage(UUID id, int width, int height, NotebookPaperSize paperSize, int pdfPage, Template template,
                         List<Stroke> strokes, int pointCount) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.paperSize = paperSize;
        this.pdfPage = pdfPage;
        this.template = template;
        this.strokes = Collections.unmodifiableList(new ArrayList<>(strokes));
        this.pointCount = pointCount;
    }

    public static NotebookPage empty() {
        return new NotebookPage(UUID.randomUUID(), 0, 0, null, 0, Template.BLANK, Collections.<Stroke>emptyList(), 0);
    }

    public boolean isInitialized() { return width > 0 && height > 0; }

    NotebookPage withSize(int width, int height) {
        return new NotebookPage(id, width, height, paperSize, pdfPage, template, strokes, pointCount);
    }

    NotebookPage withPaperSize(NotebookPaperSize paperSize) {
        return new NotebookPage(id, width, height, paperSize, pdfPage, template, strokes, pointCount);
    }

    public NotebookPage withPdfPage(int sourcePage) {
        if (sourcePage < 0 || sourcePage > NotebookIndex.MAX_PAGES)
            throw new IllegalArgumentException("Invalid source PDF page");
        return new NotebookPage(id, width, height, paperSize, sourcePage, template, strokes, pointCount);
    }

    NotebookPage withTemplate(Template template) {
        return new NotebookPage(id, width, height, paperSize, pdfPage, template, strokes, pointCount);
    }

    NotebookPage withStroke(Stroke stroke) {
        ArrayList<Stroke> copy = new ArrayList<>(strokes);
        copy.add(stroke);
        return new NotebookPage(id, width, height, paperSize, pdfPage, template, copy, pointCount + stroke.points.size());
    }

    NotebookPage withStrokes(List<Stroke> strokes) {
        int count = 0;
        for (Stroke stroke : strokes) count = Math.addExact(count, stroke.points.size());
        return new NotebookPage(id, width, height, paperSize, pdfPage, template, strokes, count);
    }

    static NotebookPage restored(int width, int height, Template template, List<Stroke> strokes,
                                 int pointCount) {
        return restored(UUID.randomUUID(), width, height, template, strokes, pointCount);
    }

    static NotebookPage restored(UUID id, int width, int height, Template template,
                                  List<Stroke> strokes, int pointCount) {
        if (id == null || template == null) throw new IllegalArgumentException("Missing page metadata");
        return new NotebookPage(id, width, height, null, 0, template, strokes, pointCount);
    }

    static NotebookPage restored(UUID id, int width, int height, NotebookPaperSize paperSize,
                                 Template template, List<Stroke> strokes, int pointCount) {
        if (id == null || template == null) throw new IllegalArgumentException("Missing page metadata");
        return new NotebookPage(id, width, height, paperSize, 0, template, strokes, pointCount);
    }

    static NotebookPage restored(UUID id, int width, int height, NotebookPaperSize paperSize, int pdfPage,
                                 Template template, List<Stroke> strokes, int pointCount) {
        if (id == null || template == null) throw new IllegalArgumentException("Missing page metadata");
        if (pdfPage < 0 || pdfPage > NotebookIndex.MAX_PAGES)
            throw new IllegalArgumentException("Invalid source PDF page");
        return new NotebookPage(id, width, height, paperSize, pdfPage, template, strokes, pointCount);
    }

    static boolean finite(float value) { return !Float.isNaN(value) && !Float.isInfinite(value); }
}
