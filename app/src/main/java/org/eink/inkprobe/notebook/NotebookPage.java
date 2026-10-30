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
        final float minX, minY, maxX, maxY, maxPressure;

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
            if (penType < 0 || penType > 255 || !finite(width) || width <= 0
                    || !finite(captureScale) || captureScale <= 0 || !finite(1f / captureScale)
                    || !finite(captureWidth) || captureWidth <= 0 || captureWidth > 1024
                    || !finite(width * captureScale)) {
                throw new IllegalArgumentException("Invalid pen style");
            }
            if (points == null || points.isEmpty() || points.size() > NotebookStore.MAX_POINTS_PER_STROKE) {
                throw new IllegalArgumentException("Invalid stroke point count");
            }
            ArrayList<Point> copy = new ArrayList<>(points);
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxPressure = 1;
            for (Point point : copy) {
                if (point == null) throw new IllegalArgumentException("Missing pen point");
                if (!finite(point.x * captureScale) || !finite(point.y * captureScale))
                    throw new IllegalArgumentException("Invalid native pen coordinate");
                minX = Math.min(minX, point.x); minY = Math.min(minY, point.y);
                maxX = Math.max(maxX, point.x); maxY = Math.max(maxY, point.y);
                maxPressure = Math.max(maxPressure, point.pressure);
            }
            this.minX = minX; this.minY = minY; this.maxX = maxX; this.maxY = maxY; this.maxPressure = maxPressure;
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
    public final List<NotebookFigure> figures;

    private NotebookPage(UUID id, int width, int height, NotebookPaperSize paperSize, int pdfPage, Template template,
                         List<Stroke> strokes, int pointCount) {
        this(id, width, height, paperSize, pdfPage, template, strokes, pointCount, Collections.emptyList());
    }

    private NotebookPage(UUID id, int width, int height, NotebookPaperSize paperSize, int pdfPage, Template template,
                         List<Stroke> strokes, int pointCount, List<NotebookFigure> figures) {
        long encodedSize = 128L + 40L * strokes.size() + 20L * pointCount + 41L * figures.size();
        for (NotebookFigure figure : figures) encodedSize += figure.storedBytes();
        // Composed viewports have no figure layer and may include ink from several stored pages.
        // Apply the combined image/ink budget to durable figure pages, not transient viewports.
        if (!figures.isEmpty() && encodedSize > 32L * 1024 * 1024)
            throw new IllegalArgumentException("Page exceeds the 32 MiB snapshot limit");
        this.id = id;
        this.width = width;
        this.height = height;
        this.paperSize = paperSize;
        this.pdfPage = pdfPage;
        this.template = template;
        this.strokes = Collections.unmodifiableList(new ArrayList<>(strokes));
        this.pointCount = pointCount;
        this.figures = Collections.unmodifiableList(new ArrayList<>(figures));
    }

    public static NotebookPage empty() {
        return new NotebookPage(UUID.randomUUID(), 0, 0, null, 0, Template.BLANK, Collections.<Stroke>emptyList(), 0);
    }

    public boolean isInitialized() { return width > 0 && height > 0; }

    NotebookPage withSize(int width, int height) {
        return new NotebookPage(id, width, height, paperSize, pdfPage, template, strokes, pointCount, figures);
    }

    NotebookPage withPaperSize(NotebookPaperSize paperSize) {
        return new NotebookPage(id, width, height, paperSize, pdfPage, template, strokes, pointCount, figures);
    }

    public NotebookPage withPdfPage(int sourcePage) {
        if (sourcePage < 0 || sourcePage > NotebookIndex.MAX_PAGES)
            throw new IllegalArgumentException("Invalid source PDF page");
        return new NotebookPage(id, width, height, paperSize, sourcePage, template, strokes, pointCount, figures);
    }

    NotebookPage withTemplate(Template template) {
        return new NotebookPage(id, width, height, paperSize, pdfPage, template, strokes, pointCount, figures);
    }

    NotebookPage withStroke(Stroke stroke) {
        ArrayList<Stroke> copy = new ArrayList<>(strokes);
        copy.add(stroke);
        return new NotebookPage(id, width, height, paperSize, pdfPage, template, copy, pointCount + stroke.points.size(), figures);
    }

    NotebookPage withStrokes(List<Stroke> strokes) {
        int count = 0;
        for (Stroke stroke : strokes) count = Math.addExact(count, stroke.points.size());
        return new NotebookPage(id, width, height, paperSize, pdfPage, template, strokes, count, figures);
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

    public NotebookPage withFigures(List<NotebookFigure> figures) {
        if (figures == null || figures.size() > NotebookFigure.MAX_COUNT) throw new IllegalArgumentException("Too many figures");
        java.util.HashSet<UUID> ids = new java.util.HashSet<>();
        long bytes = 0;
        for (NotebookFigure figure : figures) {
            if (figure == null || !ids.add(figure.id) || !isInitialized()
                    || figure.left() < -.001 || figure.top() < -.001
                    || !finite(figure.right()) || !finite(figure.bottom())
                    || figure.right() > width + .001 || figure.bottom() > height + .001)
                throw new IllegalArgumentException("Figure is outside the page or has a duplicate ID");
            bytes += figure.storedBytes();
        }
        if (bytes > NotebookFigure.MAX_PAGE_BYTES) throw new IllegalArgumentException("Figures exceed the 16 MiB page limit");
        return new NotebookPage(id, width, height, paperSize, pdfPage, template, strokes, pointCount, figures);
    }

    static boolean finite(float value) { return !Float.isNaN(value) && !Float.isInfinite(value); }
}
