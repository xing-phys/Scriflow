package org.eink.inkprobe.notebook;

/** Coordinate regressions for floating controls above a native writing surface. */
public final class NotebookOverlayClipTest {
    private static int assertions;

    public static void main(String[] args) {
        expect(0, 0, 1404, 1700, 20, 40, 500, 160, 20, 40, 500, 160);
        // The surface starts below the header; no logical-page scale belongs here.
        expect(0, 172, 1404, 1700, 40, 200, 500, 320, 40, 28, 500, 148);
        expect(37, 172, 1404, 1700, 50, 220, 500, 320, 13, 48, 463, 148);
        // Partial coverage at every edge, followed by a window covering the whole surface.
        expect(100, 200, 400, 600, 50, 180, 220, 290, 0, 0, 120, 90);
        expect(100, 200, 400, 600, 450, 750, 700, 900, 350, 550, 400, 600);
        expect(100, 200, 400, 600, -100, -200, 900, 1000, 0, 0, 400, 600);
        // Moving outside or merely touching an edge must clear the old native clip.
        absent(100, 200, 400, 600, -10, 210, 100, 300);
        absent(100, 200, 400, 600, 500, 210, 700, 300);
        absent(100, 200, 400, 600, 110, 100, 200, 200);
        absent(100, 200, 400, 600, 110, 800, 200, 900);
        absent(100, 200, 400, 600, 110, 220, 110, 300);
        absent(100, 200, 400, 600, 110, 300, 200, 220);
        absent(0, 0, 0, 600, 10, 20, 200, 300);
        absent(0, 0, 400, -1, 10, 20, 200, 300);
        // Extreme coordinates must not overflow into the writable area.
        absent(Integer.MIN_VALUE, 0, 400, 600, Integer.MAX_VALUE - 10, 10, Integer.MAX_VALUE, 30);
        absent(Integer.MAX_VALUE - 100, 0, 400, 600, Integer.MIN_VALUE, 10, Integer.MIN_VALUE + 10, 30);
        expect(-50, -100, 400, 600, -20, -80, 100, 20, 30, 20, 150, 120);
        System.out.println("NotebookOverlayClip: " + assertions + " assertions passed (physical screen exclusion).");
    }

    private static void expect(int sx, int sy, int w, int h, int l, int t, int r, int b,
                               int expectedL, int expectedT, int expectedR, int expectedB) {
        NotebookOverlayClip.Hole hole = NotebookOverlayClip.localHole(sx, sy, w, h, l, t, r, b);
        check(hole != null);
        check(hole.left == expectedL); check(hole.top == expectedT);
        check(hole.right == expectedR); check(hole.bottom == expectedB);
    }

    private static void absent(int sx, int sy, int w, int h, int l, int t, int r, int b) {
        check(NotebookOverlayClip.localHole(sx, sy, w, h, l, t, r, b) == null);
    }

    private static void check(boolean condition) {
        assertions++;
        if (!condition) throw new AssertionError("Floating-control exclusion assertion " + assertions);
    }
}
