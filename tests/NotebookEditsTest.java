package org.eink.inkprobe.notebook;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Host checks for edit history and continuous-path geometry, without Android. */
public final class NotebookEditsTest {
    private static int assertions;

    public static void main(String[] args) {
        drawingHistoryAndBranches();
        crossingsAndWholeGesture();
        radiusAndDegeneratePaths();
        segmentEndpointDistances();
        templateSurvivesHistory();
        boundedHistorySharesImmutableStrokes();
        invalidEditsAreAtomic();
        initialPageSizing();
        disconnectedEraserPathsShareOneHistoryCommand();
        disconnectedPenPiecesShareOneHistoryCommand();
        System.out.println("NotebookEdits: " + assertions + " assertions passed (host JVM).");
    }

    private static void disconnectedPenPiecesShareOneHistoryCommand() {
        NotebookEdits edits = new NotebookEdits(page());
        NotebookPage.Stroke existing = stroke(100, 100, 200, 100);
        NotebookPage.Stroke first = stroke(10, 20, 0, 20), last = stroke(0, 80, 10, 80);
        edits.append(existing);
        check(edits.appendAll(Arrays.asList(first, last)), "page exit and reentry pieces accepted as one gesture");
        equal(3, edits.current().strokes.size(), "every disconnected pen piece retained");
        check(edits.undo(), "one undo removes whole pen gesture");
        equal(1, edits.current().strokes.size(), "undo removes both disconnected pieces");
        check(edits.current().strokes.get(0) == existing, "undo preserves previous writing");
        NotebookPage before = edits.current();
        check(!edits.appendAll(Collections.<NotebookPage.Stroke>emptyList()), "empty append group is no-op");
        check(edits.current() == before && edits.canRedo(), "empty group preserves page and redo");
        expectIllegal(() -> edits.appendAll(Arrays.asList(first, null)));
        expectIllegal(() -> edits.appendAll(Arrays.asList(first, stroke(-1, 30, 10, 30))));
        expectIllegal(() -> edits.appendAll(null));
        check(edits.current() == before && edits.canRedo(), "invalid later piece preserves complete model and history");
        check(edits.redo(), "one redo restores whole pen gesture");
        check(edits.current().strokes.get(1) == first && edits.current().strokes.get(2) == last,
                "redo shares exact immutable pen pieces");

        NotebookEdits full = new NotebookEdits(page().withStrokes(Collections.nCopies(NotebookStore.MAX_STROKES - 1, existing)));
        expectIllegal(() -> full.appendAll(Arrays.asList(first, last)));
        equal(NotebookStore.MAX_STROKES - 1, full.current().strokes.size(), "whole-group stroke limit validated before commit");
        check(!full.canUndo(), "failed whole-group stroke limit creates no history");
        NotebookPage.Stroke many = new NotebookPage.Stroke(2, 4, 0xff000000,
                Collections.nCopies(NotebookStore.MAX_POINTS_PER_STROKE, new NotebookPage.Point(10, 10, 0, 1)));
        NotebookEdits nearlyFull = new NotebookEdits(page().withStrokes(Collections.nCopies(4, many)));
        expectIllegal(() -> nearlyFull.appendAll(Arrays.asList(many, many)));
        equal(800000, nearlyFull.current().pointCount, "whole-group point limit validated before commit");
        check(!nearlyFull.canUndo(), "failed whole-group point limit creates no history");
    }

    private static void disconnectedEraserPathsShareOneHistoryCommand() {
        NotebookEdits edits = new NotebookEdits(page());
        NotebookPage.Stroke first = stroke(10, 10, 20, 10);
        NotebookPage.Stroke middle = stroke(50, 10, 60, 10);
        NotebookPage.Stroke last = stroke(90, 10, 100, 10);
        edits.append(first); edits.append(middle); edits.append(last);
        check(edits.erasePaths(Arrays.asList(path(15, 10), path(95, 10)), 0), "separate visible path portions hit both ends");
        equal(1, edits.current().strokes.size(), "disconnected paths do not invent a connecting segment");
        check(edits.current().strokes.get(0) == middle, "middle stroke survives phantom bridge avoidance");
        check(edits.undo(), "one undo restores every disconnected eraser portion");
        equal(3, edits.current().strokes.size(), "whole gesture restored");
        check(edits.redo(), "one redo removes both end strokes");
        equal(1, edits.current().strokes.size(), "whole gesture redone");
        check(edits.undo(), "restore redo state for validation");
        check(!edits.erasePaths(Arrays.asList(Collections.<NotebookPage.Point>emptyList(), path(500, 500)), 0), "empty and no-hit portions are no-op");
        check(edits.canRedo(), "no-hit union preserves redo");
        boolean invalid = false;
        try { edits.erasePaths(Arrays.asList(path(15, 10), null), 0); }
        catch (IllegalArgumentException expected) { invalid = true; }
        check(invalid && edits.current().strokes.size() == 3, "all path validation finishes before an edit");
    }

    private static void drawingHistoryAndBranches() {
        NotebookPage initial = page();
        NotebookEdits edits = new NotebookEdits(initial);
        check(!edits.canUndo() && !edits.canRedo(), "new session has no history");
        check(!edits.undo() && !edits.redo(), "empty history operations are no-ops");
        NotebookPage.Stroke first = stroke(10, 10, 20, 20);
        NotebookPage.Stroke second = stroke(30, 30, 40, 40);
        check(edits.append(first) && edits.append(second), "append records two commands");
        equal(0, initial.strokes.size(), "initial model remains immutable");
        equal(4, edits.current().pointCount, "appended point total");
        check(edits.undo(), "undo second append");
        check(edits.current().strokes.get(0) == first, "undo preserves exact first stroke");
        check(edits.canRedo(), "undo offers redo");
        check(!edits.erase(Collections.<NotebookPage.Point>emptyList(), 10), "empty path not an edit");
        check(!edits.erase(path(500, 500), 10), "no-hit gesture not an edit");
        check(edits.canRedo(), "no-op erasing preserves redo");
        check(edits.redo(), "redo second append");
        check(edits.current().strokes.get(1) == second, "redo preserves exact stroke");
        check(edits.undo(), "undo before branch");
        check(edits.append(stroke(50, 50, 60, 60)), "new drawing branch");
        check(!edits.canRedo() && !edits.redo(), "new drawing invalidates redo");
        check(edits.undo() && edits.undo(), "undo branch then original");
        equal(0, edits.current().strokes.size(), "all drawing undone");
        equal(0, edits.current().pointCount, "point total after undo");
    }

    private static void crossingsAndWholeGesture() {
        NotebookEdits edits = new NotebookEdits(page());
        NotebookPage.Stroke a = stroke(10, 100, 900, 100);
        NotebookPage.Stroke b = stroke(10, 300, 900, 300);
        NotebookPage.Stroke c = stroke(10, 800, 900, 800);
        edits.append(a); edits.append(b); edits.append(c);
        NotebookPage before = edits.current();
        check(edits.erase(path(450, 50, 450, 500), 0), "sparse long segments cross between endpoints");
        equal(1, edits.current().strokes.size(), "one gesture removes both crossed strokes");
        equal(2, edits.current().pointCount, "erase updates point count");
        check(edits.current().strokes.get(0) == c, "unhit stroke retained in order");
        equal(3, before.strokes.size(), "erase leaves old page immutable");
        check(edits.undo(), "one undo restores entire multi-stroke erase");
        check(edits.current().strokes.equals(before.strokes), "restored exact references/order");
        check(edits.redo(), "redo entire erase");
        equal(1, edits.current().strokes.size(), "redo removes both again");
        check(edits.undo(), "restore for eraser branch");
        check(edits.erase(path(700, 790, 700, 810), 0), "new successful eraser branch");
        check(!edits.canRedo(), "successful eraser clears redo");
    }

    private static void radiusAndDegeneratePaths() {
        NotebookEdits edits = new NotebookEdits(page());
        edits.append(new NotebookPage.Stroke(2, 4, 0xff000000, path(50, 50)));
        check(!edits.erase(path(57.01f, 50), 5), "outside radius plus stroke half width misses dot");
        check(edits.erase(path(57, 50), 5), "touching exact radius plus half width erases dot");
        equal(0, edits.current().pointCount, "dot erase point count");
        edits.undo();
        check(!edits.erase(path(55, 55), 5), "diagonal circle miss despite overlapping boxes");
        check(edits.erase(path(50, 50, 50, 50), 0), "repeated zero-length eraser points hit");
        edits.undo();
        check(edits.erase(path(0, 50, 100, 50), 0), "eraser segment crosses dot between endpoints");
        edits.append(new NotebookPage.Stroke(2, 4, 0xff000000, path(100, 100, 100, 100)));
        check(edits.erase(path(100, 102), 0), "zero-length ink segment hit at half width");
        check(!edits.erase(path(1, 1), 100), "empty page erase is no-op");
    }

    private static void segmentEndpointDistances() {
        NotebookEdits edits = new NotebookEdits(page());
        edits.append(stroke(100, 100, 200, 100));
        check(!edits.erase(path(100, 110, 200, 110), 7.9f), "parallel segments just outside reach");
        check(edits.erase(path(100, 110, 200, 110), 8), "parallel segments exactly within reach");
        edits.undo();
        check(!edits.erase(path(210, 110, 220, 120), 12), "endpoint diagonal outside radius");
        check(edits.erase(path(210, 110, 220, 120), 13), "endpoint diagonal inside radius");
        edits.undo();
        check(edits.erase(path(150, 100, 250, 100), 0), "collinear overlap");
        edits.undo();
        check(!edits.erase(path(203, 100, 250, 100), 0), "collinear separated beyond ink width");
        check(edits.erase(path(202, 100, 250, 100), 0), "collinear end-cap touch");
        edits.undo();
        check(edits.erase(path(100, 100, 50, 50), 0), "shared endpoints intersect");

        NotebookEdits diagonal = new NotebookEdits(page());
        diagonal.append(stroke(100, 100, 900, 900));
        check(diagonal.erase(path(100, 900, 900, 100), 0), "diagonal X crossing without nearby samples");
    }

    private static void templateSurvivesHistory() {
        NotebookEdits edits = new NotebookEdits(page());
        edits.append(stroke(1, 1, 2, 2));
        edits.setTemplate(NotebookPage.Template.LINED);
        edits.undo();
        check(edits.current().template == NotebookPage.Template.LINED, "undo does not change paper");
        edits.setTemplate(NotebookPage.Template.BLANK);
        check(edits.canRedo(), "paper change preserves redo");
        edits.redo();
        check(edits.current().template == NotebookPage.Template.BLANK, "redo does not restore old paper");
        equal(1000, edits.current().width, "fixed page width through history");
        equal(1200, edits.current().height, "fixed page height through history");
    }

    private static void boundedHistorySharesImmutableStrokes() {
        NotebookEdits edits = new NotebookEdits(page());
        NotebookPage.Stroke shared = stroke(10, 10, 20, 20);
        java.util.List<NotebookPage.Stroke> originals = new java.util.ArrayList<>();
        for (int i = 0; i < NotebookEdits.HISTORY_LIMIT + 5; i++) {
            NotebookPage.Stroke unique = new NotebookPage.Stroke(shared.penType, shared.width, shared.color, shared.points);
            originals.add(unique); edits.append(unique);
        }
        int undos = 0;
        while (edits.undo()) undos++;
        equal(NotebookEdits.HISTORY_LIMIT, undos, "history limited to recent commands");
        equal(5, edits.current().strokes.size(), "oldest changes remain after history exhausted");
        for (NotebookPage.Stroke stroke : edits.current().strokes) {
            check(stroke == originals.get(edits.current().strokes.indexOf(stroke)), "history shares original immutable stroke objects");
        }
        int redos = 0;
        while (edits.redo()) redos++;
        equal(NotebookEdits.HISTORY_LIMIT, redos, "every retained command can redo");
        equal(NotebookEdits.HISTORY_LIMIT + 5, edits.current().strokes.size(), "redo all retained history");
        NotebookEdits reopened = new NotebookEdits(edits.current());
        check(!reopened.canUndo() && !reopened.canRedo(), "history is session local");
    }

    private static void invalidEditsAreAtomic() {
        NotebookEdits edits = new NotebookEdits(page());
        edits.append(stroke(1, 1, 2, 2)); edits.undo();
        NotebookPage before = edits.current();
        expectIllegal(() -> edits.erase(path(1, 1), -1));
        expectIllegal(() -> edits.erase(path(1, 1), Float.NaN));
        expectIllegal(() -> edits.erase(null, 1));
        expectIllegal(() -> edits.erase(Arrays.asList((NotebookPage.Point) null), 1));
        expectIllegal(() -> edits.append(stroke(-1, 0, 10, 10)));
        expectIllegal(() -> edits.append(null));
        expectIllegal(() -> edits.setTemplate(null));
        check(edits.current() == before && edits.canRedo(), "invalid edits preserve model and history");
    }

    private static void initialPageSizing() {
        NotebookEdits edits = new NotebookEdits(NotebookPage.empty());
        edits.setTemplate(NotebookPage.Template.LINED);
        expectIllegal(() -> edits.setPageSize(0, 1000));
        edits.setPageSize(1000, 1200);
        check(edits.current().isInitialized(), "page size established");
        check(edits.current().template == NotebookPage.Template.LINED, "initial sizing preserves paper");
        check(!edits.canUndo(), "initial sizing does not record a stroke edit");
        edits.append(stroke(1, 1, 2, 2));
        edits.undo();
        edits.setPageSize(300, 400);
        equal(1000, edits.current().width, "later viewport cannot change initialized size");
        equal(1200, edits.current().height, "later viewport cannot change initialized height");
        check(edits.canRedo(), "sizing preserves history");
        edits.redo();
        equal(1, edits.current().strokes.size(), "redo remains usable after sizing");
    }

    private static NotebookPage page() { return NotebookPage.empty().withSize(1000, 1200); }
    private static NotebookPage.Stroke stroke(float... xy) {
        return new NotebookPage.Stroke(2, 4, 0xff000000, path(xy));
    }
    private static List<NotebookPage.Point> path(float... xy) {
        java.util.ArrayList<NotebookPage.Point> points = new java.util.ArrayList<>();
        for (int i = 0; i < xy.length; i += 2) points.add(new NotebookPage.Point(xy[i], xy[i + 1], i, 1));
        return points;
    }
    private static void expectIllegal(Runnable task) {
        assertions++;
        try { task.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected IllegalArgumentException");
    }
    private static void equal(int expected, int actual, String label) {
        check(expected == actual, label + ": expected " + expected + ", got " + actual);
    }
    private static void check(boolean condition, String label) {
        assertions++;
        if (!condition) throw new AssertionError(label);
    }
}
