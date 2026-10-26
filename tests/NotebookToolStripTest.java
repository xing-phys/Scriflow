package org.eink.inkprobe.notebook;

/** Real tablet sizes, axis switching and bounded fallback without a second row. */
public final class NotebookToolStripTest {
    private static int assertions;
    public static void main(String[] args) {
        NotebookToolStrip.Layout horizontal = layout(false, 686, 894, 1);
        equal(32, horizontal.button, "compact circular buttons");
        equal(616, horizontal.width, "all seventeen controls fit in one tablet row");
        equal(40, horizontal.height, "slim pill height");
        equal(32, horizontal.stripHeight, "no wrapped second row");
        check(!horizontal.scrolling, "tablet does not need horizontal scrolling");
        NotebookToolStrip.Layout vertical = layout(true, 686, 894, 1);
        equal(horizontal.width, vertical.height, "rotating preserves all controls");
        equal(horizontal.height, vertical.width, "rotating makes one narrow column");
        equal(32, vertical.stripWidth, "no second column");
        check(!vertical.scrolling, "tablet does not need vertical scrolling");
        NotebookToolStrip.Layout densityTwo = layout(false, 1372, 1788, 2);
        equal(64, densityTwo.button, "physical pixels scale with actual tablet density");
        equal(1232, densityTwo.width, "physical palette width");
        equal(80, densityTwo.height, "physical palette height");
        NotebookToolStrip.Layout smaller = layout(false, 580, 894, 1);
        check(smaller.button < 32 && smaller.button >= 28, "limited width reduces buttons before scrolling");
        check(!smaller.scrolling, "reduced strip still fits");
        NotebookToolStrip.Layout narrow = layout(false, 320, 894, 1);
        equal(28, narrow.button, "small fallback preserves minimum control size");
        equal(320, narrow.width, "fallback window stays on screen");
        check(narrow.scrolling, "extra controls stay on one scrollable axis");
        equal(28, narrow.stripHeight, "narrow fallback never wraps into rows");
        NotebookToolStrip.Layout shortScreen = layout(true, 686, 320, 1);
        equal(narrow.stripWidth, shortScreen.stripHeight, "vertical fallback preserves every control");
        equal(320, shortScreen.height, "vertical fallback stays within available height");
        check(shortScreen.scrolling, "short column scrolls instead of wrapping");
        for (boolean axis : new boolean[]{false, true}) {
            NotebookToolStrip.Layout collapsed = NotebookToolStrip.measure(axis, 1, 686, 894, 32, 28, 4, 4);
            equal(40, collapsed.width, "collapsed dock is only one circle plus padding");
            equal(40, collapsed.height, "collapsed dock has no empty strip");
            check(!collapsed.scrolling, "collapsed grip stays visible in either orientation");
        }
        for (int width : new int[]{1, 40, 200, 480, 686, 1404}) {
            for (int height : new int[]{1, 40, 320, 894, 1872}) {
                for (boolean axis : new boolean[]{false, true}) {
                    NotebookToolStrip.Layout result = layout(axis, width, height, 1);
                    check(result.width > 0 && result.width <= width, "bounded width");
                    check(result.height > 0 && result.height <= height, "bounded height");
                    check(result.button > 0 && result.button <= 32, "positive control size");
                    equal(result.button, axis ? result.stripWidth : result.stripHeight, "only one row/column");
                    equal(17L * result.button + 16L * 4,
                        axis ? result.stripHeight : result.stripWidth, "all controls and gaps accounted for");
                }
            }
        }
        reject(() -> NotebookToolStrip.measure(false, 0, 100, 100, 36, 28, 4, 6));
        reject(() -> NotebookToolStrip.measure(false, 13, 0, 100, 36, 28, 4, 6));
        reject(() -> NotebookToolStrip.measure(false, 13, 100, 100, 20, 28, 4, 6));
        reject(() -> NotebookToolStrip.measure(false, 64, Integer.MAX_VALUE, Integer.MAX_VALUE,
            36, 28, Integer.MAX_VALUE, 6));
        System.out.println("NotebookToolStrip: " + assertions + " assertions passed (single-axis palette).");
    }
    private static NotebookToolStrip.Layout layout(boolean vertical, int width, int height, int density) {
        return NotebookToolStrip.measure(vertical, 17, width, height, 32*density, 28*density, 4*density, 4*density);
    }
    private static void reject(Runnable task) {
        try { task.run(); throw new AssertionError("Invalid layout accepted"); }
        catch (IllegalArgumentException expected) { assertions++; }
    }
    private static void equal(long expected, long actual, String detail) { check(expected == actual, detail); }
    private static void check(boolean condition, String detail) { assertions++; if (!condition) throw new AssertionError(detail); }
}
