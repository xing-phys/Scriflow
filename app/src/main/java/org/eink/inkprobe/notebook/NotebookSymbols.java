package org.eink.inkprobe.notebook;

/** Shared, resolution-independent line art in a 24 by 24 coordinate space. */
public final class NotebookSymbols {
    public static final float SIZE = 24f;
    public static final float STROKE_WIDTH = 1.8f;

    /** A deliberately small path API, usable by Android Canvas and host SVG previews. */
    public interface Sink {
        void begin();
        void moveTo(float x, float y);
        void lineTo(float x, float y);
        void quadTo(float x1, float y1, float x2, float y2);
        void cubicTo(float x1, float y1, float x2, float y2, float x3, float y3);
        void close();
        void end(boolean filled);
    }

    private NotebookSymbols() {}

    public static void draw(String icon, Sink out) {
        if (icon == null || out == null) throw new IllegalArgumentException("Missing icon or sink");
        switch (icon) {
            case "LIBRARY":
                polygon(out, false, 3, 4, 7, 4, 7, 20, 3, 20);
                polygon(out, false, 8, 7, 12, 7, 12, 20, 8, 20);
                polygon(out, false, 13, 5, 17, 4, 21, 19, 17, 20);
                line(out, 3, 8, 7, 8);
                line(out, 16, 16, 20, 15);
                break;
            case "PREVIOUS":
                line(out, 15, 5, 8, 12, 15, 19);
                break;
            case "NEXT":
                line(out, 9, 5, 16, 12, 9, 19);
                break;
            case "ADD_PAGE":
                paper(out);
                line(out, 9, 14, 15, 14);
                line(out, 12, 11, 12, 17);
                break;
            case "PEN":
                out.begin();
                out.moveTo(4, 16);
                out.lineTo(15.6f, 4.4f);
                out.quadTo(16.4f, 3.6f, 17.2f, 4.4f);
                out.lineTo(19.6f, 6.8f);
                out.quadTo(20.4f, 7.6f, 19.6f, 8.4f);
                out.lineTo(8, 20);
                out.lineTo(4, 20);
                out.close();
                out.end(false);
                line(out, 14, 6, 18, 10);
                line(out, 4, 16, 8, 20);
                break;
            case "ERASER":
                out.begin();
                out.moveTo(4.2f, 13.3f);
                out.lineTo(12.5f, 5);
                out.quadTo(13.2f, 4.3f, 14, 5);
                out.lineTo(20, 11);
                out.quadTo(20.7f, 11.7f, 20, 12.5f);
                out.lineTo(12.5f, 20);
                out.lineTo(10.5f, 20);
                out.lineTo(4.2f, 13.7f);
                out.quadTo(4, 13.5f, 4.2f, 13.3f);
                out.close();
                out.end(false);
                line(out, 8.3f, 9.2f, 16.3f, 17.2f);
                line(out, 14, 20, 21, 20);
                break;
            case "UNDO":
                line(out, 8, 4, 3, 9, 8, 14);
                out.begin();
                out.moveTo(3, 9);
                out.lineTo(13, 9);
                out.cubicTo(17.4f, 9, 21, 12.6f, 21, 17);
                out.lineTo(21, 20);
                out.end(false);
                break;
            case "REDO":
                line(out, 16, 4, 21, 9, 16, 14);
                out.begin();
                out.moveTo(21, 9);
                out.lineTo(11, 9);
                out.cubicTo(6.6f, 9, 3, 12.6f, 3, 17);
                out.lineTo(3, 20);
                out.end(false);
                break;
            case "PAPER_BLANK":
                paper(out);
                break;
            case "PAPER_LINED":
                paper(out);
                line(out, 9, 11, 16, 11);
                line(out, 9, 14.5f, 16, 14.5f);
                line(out, 9, 18, 16, 18);
                break;
            case "MORE":
                circle(out, 5, 12, 1.6f);
                circle(out, 12, 12, 1.6f);
                circle(out, 19, 12, 1.6f);
                break;
            case "OVERVIEW":
                polygon(out, false, 4, 3, 10, 3, 10, 10, 4, 10);
                polygon(out, false, 14, 3, 20, 3, 20, 10, 14, 10);
                polygon(out, false, 4, 14, 10, 14, 10, 21, 4, 21);
                polygon(out, false, 14, 14, 20, 14, 20, 21, 14, 21);
                break;
            case "MOVE":
                for (int y = 5; y <= 19; y += 7) {
                    circle(out, 8, y, 1.2f);
                    circle(out, 16, y, 1.2f);
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown notebook icon: " + icon);
        }
    }

    private static void paper(Sink out) {
        polygon(out, false, 6, 3, 15, 3, 20, 8, 20, 21, 6, 21);
        line(out, 15, 3, 15, 8, 20, 8);
    }

    private static void line(Sink out, float... coordinates) {
        out.begin();
        out.moveTo(coordinates[0], coordinates[1]);
        for (int i = 2; i < coordinates.length; i += 2) {
            out.lineTo(coordinates[i], coordinates[i + 1]);
        }
        out.end(false);
    }

    private static void polygon(Sink out, boolean filled, float... coordinates) {
        out.begin();
        out.moveTo(coordinates[0], coordinates[1]);
        for (int i = 2; i < coordinates.length; i += 2) {
            out.lineTo(coordinates[i], coordinates[i + 1]);
        }
        out.close();
        out.end(filled);
    }

    private static void circle(Sink out, float x, float y, float radius) {
        float c = radius * 0.55228475f;
        out.begin();
        out.moveTo(x + radius, y);
        out.cubicTo(x + radius, y + c, x + c, y + radius, x, y + radius);
        out.cubicTo(x - c, y + radius, x - radius, y + c, x - radius, y);
        out.cubicTo(x - radius, y - c, x - c, y - radius, x, y - radius);
        out.cubicTo(x + c, y - radius, x + radius, y - c, x + radius, y);
        out.close();
        out.end(true);
    }
}
