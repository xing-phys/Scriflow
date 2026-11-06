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
            case "SEND":
                line(out,12,20,12,4);line(out,5,11,12,4,19,11);break;
            case "STOP":
                polygon(out,true,6,6,18,6,18,18,6,18);break;
            case "RETRY":
                out.begin();out.moveTo(20,9);out.cubicTo(18,3,9,2,5,7);out.cubicTo(0,13,5,21,12,21);out.cubicTo(16,21,20,18,21,14);out.end(false);
                line(out,20,3,20,9,14,9);break;
            case "SAVE":
                polygon(out,false,4,3,17,3,21,7,21,21,3,21,3,3);polygon(out,false,7,3,7,9,16,9,16,3);polygon(out,false,7,21,7,14,17,14,17,21);break;
            case "SETTINGS":
                out.begin();
                for(int i=0;i<32;i++){
                    double angle=Math.PI*2*i/32-Math.PI/2;float radius=i%4==1||i%4==2?10:7.7f;
                    float x=12+(float)Math.cos(angle)*radius,y=12+(float)Math.sin(angle)*radius;
                    if(i==0)out.moveTo(x,y);else out.lineTo(x,y);
                }
                out.close();out.end(false);circle(out,12,12,3.1f);
                break;
            case "AI_CHAT":
                line(out, 4, 3, 20, 3, 22, 5, 22, 15, 20, 17, 10, 17, 5, 21, 5, 17, 2, 15, 2, 5, 4, 3);
                polygon(out, false, 12, 6, 13.5f, 9.5f, 17, 11, 13.5f, 12.5f, 12, 16, 10.5f, 12.5f, 7, 11, 10.5f, 9.5f);
                break;
            case "LIBRARY":
                polygon(out, false, 3, 4, 7, 4, 7, 20, 3, 20);
                polygon(out, false, 8, 7, 12, 7, 12, 20, 8, 20);
                polygon(out, false, 13, 5, 17, 4, 21, 19, 17, 20);
                line(out, 3, 8, 7, 8);
                line(out, 16, 16, 20, 15);
                break;
            case "FOLDER":
                line(out, 3, 19, 3, 5, 10, 5, 12, 8, 21, 8, 21, 19, 3, 19);
                break;
            case "PREVIOUS":
                line(out, 15, 5, 8, 12, 15, 19);
                break;
            case "NEXT":
                line(out, 9, 5, 16, 12, 9, 19);
                break;
            case "VIEW_BACK":
                line(out, 10, 5, 3, 12, 10, 19);
                line(out, 3, 12, 21, 12);
                line(out, 19, 5, 21, 5, 21, 19, 19, 19);
                break;
            case "VIEW_FORWARD":
                line(out, 14, 5, 21, 12, 14, 19);
                line(out, 3, 12, 21, 12);
                line(out, 5, 5, 3, 5, 3, 19, 5, 19);
                break;
            case "OUTLINE":
                circle(out, 4, 5, 1);
                line(out, 8, 5, 21, 5);
                line(out, 10, 10, 21, 10);
                circle(out, 4, 15, 1);
                line(out, 8, 15, 21, 15);
                line(out, 10, 20, 21, 20);
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
            case "LASSO":
                line(out, 4, 10, 5, 7, 8, 5);
                line(out, 11, 4, 14, 4, 17, 5);
                line(out, 20, 7, 21, 10, 20, 13);
                line(out, 18, 15, 15, 17, 12, 17);
                line(out, 9, 17, 6, 16, 4, 14);
                out.begin(); out.moveTo(9, 16); out.cubicTo(4, 18, 5, 22, 9, 21); out.end(false);
                break;
            case "REGION_ERASER":
                line(out, 3, 11, 4, 7, 7, 4);
                line(out, 10, 3, 14, 3, 17, 5);
                line(out, 3, 15, 5, 19, 8, 20);
                polygon(out, false, 9, 15, 16, 8, 22, 14, 16, 20, 14, 20);
                line(out, 12, 12, 19, 19);
                line(out, 19, 21, 23, 21);
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
            case "NOTES":
                polygon(out, false, 3, 7, 16, 7, 16, 21, 3, 21);
                line(out, 7, 3, 21, 3, 21, 17);
                break;
            case "MINIMIZE":
                line(out, 5, 19, 19, 19);
                break;
            case "PLUS":
                line(out, 5, 12, 19, 12);
                line(out, 12, 5, 12, 19);
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
            case "LAYOUT_HORIZONTAL":
                for (float x : new float[]{2, 9.5f, 17})
                    polygon(out, false, x, 9, x + 5, 9, x + 5, 15, x, 15);
                break;
            case "LAYOUT_VERTICAL":
                for (float y : new float[]{2, 9.5f, 17})
                    polygon(out, false, 9, y, 15, y, 15, y + 5, 9, y + 5);
                break;
            case "BATTERY":
                polygon(out, false, 2, 7, 20, 7, 20, 17, 2, 17);
                polygon(out, true, 20, 10, 22, 10, 22, 14, 20, 14);
                break;
            case "FULLSCREEN":
                line(out, 3, 9, 3, 3, 9, 3);
                line(out, 15, 3, 21, 3, 21, 9);
                line(out, 21, 15, 21, 21, 15, 21);
                line(out, 9, 21, 3, 21, 3, 15);
                break;
            case "MARKDOWN":
                polygon(out, false, 2, 5, 22, 5, 22, 20, 2, 20);
                line(out, 5, 16, 5, 9, 8, 12, 11, 9, 11, 16);
                line(out, 17, 9, 17, 16); line(out, 14, 13, 17, 16, 20, 13);
                break;
            case "DICTIONARY":
                polygon(out,false,3,3,16,3,16,20,3,20);line(out,6,7,12,7);line(out,6,11,10,11);
                circle(out,16,15,4);line(out,19,18,23,22);break;
            case "READING":
                polygon(out,false,4,2,20,2,20,22,4,22);line(out,8,7,16,7);line(out,8,11,16,11);line(out,8,15,13,15);
                break;
            case "CLOSE":
                line(out,5,5,19,19);line(out,19,5,5,19);break;
            case "CHECK":
                line(out,3,12,9,18,21,5);break;
            case "CODE":
                line(out, 7, 5, 2, 12, 7, 19); line(out, 17, 5, 22, 12, 17, 19); line(out, 14, 4, 10, 20);
                break;
            case "RENDER_MARKDOWN":
                out.begin(); out.moveTo(2, 12); out.cubicTo(7, 3, 17, 3, 22, 12);
                out.cubicTo(17, 21, 7, 21, 2, 12); out.close(); out.end(false); circle(out, 12, 12, 3);
                break;
            case "FONT_SIZE":
                line(out,2,19,7,5,12,19);line(out,4,14,10,14);
                line(out,15,8,19,4,23,8);line(out,19,4,19,20);line(out,15,16,19,20,23,16);
                break;
            case "INSERT_FIGURE":
                polygon(out, false, 3, 4, 18, 4, 18, 18, 3, 18);
                line(out, 4, 16, 9, 11, 13, 15, 16, 12, 18, 14); circle(out, 14, 8, 1.5f);
                line(out, 18, 15, 18, 23); line(out, 14, 19, 22, 19);
                break;
            case "FIGURES":
                polygon(out, false, 3, 7, 18, 7, 18, 21, 3, 21);
                line(out, 7, 3, 22, 3, 22, 17);
                line(out, 4, 19, 9, 14, 13, 18, 16, 15, 18, 17); circle(out, 14, 11, 1.5f);
                break;
            case "INFORMATION":
                circle(out, 12, 7, 1);
                line(out, 10, 11, 12, 11, 12, 17); line(out, 10, 17, 14, 17);
                break;
            case "DUPLICATE":
                polygon(out, false, 8, 8, 21, 8, 21, 21, 8, 21);
                line(out, 16, 4, 4, 4, 4, 16); line(out, 11, 14, 18, 14); line(out, 14.5f, 10.5f, 14.5f, 17.5f);
                break;
            case "MOVE_PAGE":
                paper(out); line(out, 3, 14, 15, 14); line(out, 11, 10, 15, 14, 11, 18);
                break;
            case "EXPORT":
                line(out, 4, 13, 4, 21, 20, 21, 20, 13); line(out, 12, 16, 12, 3); line(out, 7, 8, 12, 3, 17, 8);
                break;
            case "COPY":
                polygon(out, false, 8, 8, 20, 8, 20, 21, 8, 21);
                line(out, 16, 4, 4, 4, 4, 16);
                break;
            case "CUT":
                circle(out, 6, 18, 3); circle(out, 18, 18, 3);
                line(out, 4, 3, 17, 16); line(out, 20, 3, 7, 16);
                break;
            case "DELETE":
                polygon(out, false, 6, 7, 18, 7, 17, 21, 7, 21);
                line(out, 4, 5, 20, 5); line(out, 9, 5, 9, 2, 15, 2, 15, 5);
                line(out, 10, 10, 10, 18); line(out, 14, 10, 14, 18);
                break;
            case "PASTE":
                polygon(out, false, 5, 5, 19, 5, 19, 21, 5, 21);
                polygon(out, false, 9, 2, 15, 2, 15, 7, 9, 7);
                line(out, 8, 11, 16, 11); line(out, 8, 15, 16, 15);
                break;
            case "ROTATE":
                out.begin(); out.moveTo(19, 9); out.cubicTo(15, 0, 3, 4, 4, 13);
                out.cubicTo(5, 21, 16, 23, 20, 16); out.end(false);
                line(out, 19, 3, 19, 9, 13, 9);
                break;
            case "RESIZE":
                line(out, 4, 10, 4, 4, 10, 4); line(out, 4, 4, 20, 20);
                line(out, 14, 20, 20, 20, 20, 14);
                break;
            case "EXIT_FULLSCREEN":
                line(out, 3, 8, 8, 8, 8, 3);
                line(out, 16, 3, 16, 8, 21, 8);
                line(out, 21, 16, 16, 16, 16, 21);
                line(out, 8, 21, 8, 16, 3, 16);
                break;
            case "WIFI":
            case "WIFI_OFF":
                out.begin(); out.moveTo(3, 9);
                out.cubicTo(8, 4, 16, 4, 21, 9); out.end(false);
                out.begin(); out.moveTo(6, 12);
                out.cubicTo(9.5f, 8.5f, 14.5f, 8.5f, 18, 12); out.end(false);
                out.begin(); out.moveTo(9, 15);
                out.cubicTo(10.5f, 13.5f, 13.5f, 13.5f, 15, 15); out.end(false);
                circle(out, 12, 18, 1.1f);
                if ("WIFI_OFF".equals(icon)) line(out, 3, 3, 21, 21);
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
