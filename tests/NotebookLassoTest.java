package org.eink.inkprobe.notebook;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Geometry, identity/history, cross-page clipboard and real-file edit/retry regressions. */
public final class NotebookLassoTest {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        geometry(); historyAndClipboard(); invalidAndStale(); persistence(); multiPage();
        System.out.println("NotebookLasso: " + assertions + " assertions passed (host JVM, real files).");
    }

    private static void geometry() {
        NotebookPage.Stroke dot = stroke(20, 20), inside = stroke(20, 30, 40, 30);
        NotebookPage.Stroke crossing = stroke(10, 40, 90, 40), outside = stroke(90, 90);
        NotebookPage page = page(dot, inside, crossing, outside);
        List<NotebookPage.Point> square = path(10, 10, 60, 10, 60, 60, 10, 60);
        NotebookLasso.Selection selected = NotebookLasso.select(page, square);
        check(selected.strokes.equals(Arrays.asList(dot, inside, crossing)), "partial overlap selects the whole stroke");
        List<NotebookPage.Point> reversed = new ArrayList<>(square); Collections.reverse(reversed);
        check(NotebookLasso.select(page, reversed).strokes.equals(selected.strokes), "orientation independent");
        check(NotebookLasso.select(page, path(10, 10, 60, 10, 60, 60, 10, 60, 10, 10)).strokes.equals(selected.strokes), "explicit closure");
        check(NotebookLasso.select(page, path()).isEmpty(), "empty gesture");
        check(NotebookLasso.select(page, path(0, 0, 40, 40, 80, 80)).isEmpty(), "line cannot select");
        check(NotebookLasso.select(page(stroke(10, 10, 60, 10)), square).strokes.size() == 1, "boundary stroke included");
        List<NotebookPage.Point> concave = path(0, 0, 80, 0, 80, 80, 60, 80, 60, 20, 20, 20, 20, 80, 0, 80);
        check(NotebookLasso.select(page(stroke(10, 60, 70, 60)), concave).strokes.size() == 1, "partly inside concave region selects the whole stroke");
        check(NotebookLasso.select(page(stroke(10, 40, 30, 0, 70, 40)), concave).strokes.size() == 1, "stroke may enter and leave through polygon vertices");
        check(NotebookLasso.select(page(stroke(10, 10, 70, 10)), concave).strokes.size() == 1, "inside concave region");
        List<NotebookPage.Point> bow = path(0, 0, 80, 80, 0, 80, 80, 0);
        check(NotebookLasso.select(page(stroke(40, 10), stroke(40, 70), stroke(5, 40)), bow).strokes.size() == 2,
            "self crossing uses even-odd fill");
        check(NotebookLasso.select(page(stroke(20, 20, 20, 20)), square).strokes.size() == 1, "zero-length segments");
        check(NotebookLasso.select(page(stroke(0, 35, 90, 35)), square).strokes.size() == 1,
            "crossing with both sample endpoints outside is selected");
        check(NotebookLasso.select(page(stroke(0, 10, 90, 10)), square).strokes.size() == 1,
            "collinear overlap with the lasso boundary is selected");
        check(NotebookLasso.select(page(stroke(0, 0, 10, 10)), square).strokes.size() == 1,
            "touching only a polygon vertex is selected");
        check(NotebookLasso.select(page(stroke(0, 11, 11, 0)), square).isEmpty(),
            "overlapping bounding boxes alone do not select an outside stroke");
        check(NotebookLasso.select(page(stroke(0, 30, 9, 30)), square).isEmpty(),
            "stroke ending before the lasso is not selected");
        check(NotebookLasso.select(page(stroke(30, 40, 50, 40)), concave).isEmpty(),
            "stroke wholly in a concave cutout is not selected");
        check(NotebookLasso.select(page(stroke(0, 0, 90, 0, 90, 90, 0, 90, 0, 0)), square).isEmpty(),
            "surrounding the lasso without touching its region is not overlap");
        NotebookPage.Stroke partial = stroke(0, 35, 90, 35);
        NotebookLasso.Selection whole = NotebookLasso.select(page(partial), square);
        check(whole.strokes.get(0) == partial && whole.bounds.left == 0 && whole.bounds.right == 90,
            "partial contact retains the entire original stroke, not a clipped fragment");
        check(NotebookLasso.select(page, square).bounds.contains(20, 20, 0), "selection bounds");
    }

    private static void historyAndClipboard() {
        NotebookPage.Stroke a = stroke(10, 10, 20, 20), middle = stroke(80, 80), b = stroke(30, 30);
        NotebookPage original = page(a, middle, b);
        NotebookEdits editor = new NotebookEdits(original);
        NotebookLasso.Selection selected = NotebookLasso.select(original, path(0, 0, 50, 0, 50, 50, 0, 50));
        NotebookLasso.Clipboard clipboard = new NotebookLasso.Clipboard(selected);
        NotebookLasso.Selection moved = editor.editSelection(selected, NotebookLasso.Action.MOVE, 200, -200);
        check(editor.current().strokes.get(1) == middle, "move preserves unselected stroke/order");
        check(moved.strokes.get(0).id.equals(a.id), "move preserves identity");
        near(80, moved.strokes.get(0).points.get(0).x, "whole group clamped at right edge");
        near(0, moved.strokes.get(0).points.get(0).y, "whole group clamped at top edge");
        near(100, moved.bounds.right, "right bound");
        near(a.width, moved.strokes.get(0).width, "width preserved");
        for (int i = 0; i < a.points.size(); i++) {
            NotebookPage.Point before = a.points.get(i), after = moved.strokes.get(0).points.get(i);
            near(before.pressure, after.pressure, "pressure preserved");
            check(before.time == after.time, "time preserved");
        }
        check(editor.undo(), "move undo"); check(editor.current().strokes.equals(original.strokes), "exact move undo");
        check(editor.redo(), "move redo"); check(editor.current().strokes.get(0) == moved.strokes.get(0), "exact move redo");
        NotebookLasso.Selection duplicate = editor.editSelection(moved, NotebookLasso.Action.DUPLICATE, -10, 10);
        check(editor.current().strokes.size() == 5, "duplicate whole group");
        check(!duplicate.strokes.get(0).id.equals(a.id), "duplicate has fresh ID");
        check(editor.undo() && editor.current().strokes.size() == 3, "one undo for duplicate group");
        editor.editSelection(moved, NotebookLasso.Action.MOVE, 0, 0);
        check(editor.canRedo(), "no-op move preserves redo");
        editor.editSelection(moved, NotebookLasso.Action.MOVE, 200, -200);
        check(editor.canRedo(), "clamped no-op preserves redo");
        editor.editSelection(moved, NotebookLasso.Action.DELETE, 0, 0);
        check(editor.current().strokes.equals(Collections.singletonList(middle)), "delete only selection");
        check(!editor.canRedo(), "successful delete clears redo");
        check(editor.undo() && editor.current().strokes.size() == 3, "one undo for delete");
        NotebookLasso.Selection pasted = editor.paste(clipboard, 0, 100);
        near(0, pasted.bounds.left, "paste clamped left"); near(100, pasted.bounds.bottom, "paste clamped bottom");
        check(!pasted.strokes.get(0).id.equals(a.id), "paste fresh IDs");
        check(editor.undo() && editor.current().strokes.size() == 3, "paste group undo");
        check(editor.redo() && editor.current().strokes.size() == 5, "paste group redo");
        NotebookLasso.Selection again = editor.paste(clipboard, 50, 50);
        check(!again.strokes.get(0).id.equals(pasted.strokes.get(0).id), "repeat paste fresh IDs");
        check(clipboard.strokes.get(0) == a, "clipboard retains pre-move ink");
    }

    private static void invalidAndStale() {
        NotebookEdits editor = new NotebookEdits(page(stroke(10, 10)));
        NotebookLasso.Selection selected = NotebookLasso.all(editor.current());
        editor.editSelection(selected, NotebookLasso.Action.MOVE, 10, 10);
        NotebookPage before = editor.current();
        expectInvalid(() -> editor.editSelection(selected, NotebookLasso.Action.DELETE, 0, 0));
        check(editor.current() == before, "stale selection cannot edit");
        expectInvalid(() -> editor.editSelection(NotebookLasso.all(page(stroke(1, 1))), NotebookLasso.Action.DELETE, 0, 0));
        expectInvalid(() -> editor.editSelection(NotebookLasso.all(before), NotebookLasso.Action.MOVE, Float.NaN, 0));
        NotebookLasso.Clipboard large = new NotebookLasso.Clipboard(NotebookLasso.all(page(stroke(0, 0, 100, 100))));
        NotebookEdits small = new NotebookEdits(NotebookPage.empty().withSize(50, 50));
        check(!large.fits(small.current()), "oversized clipboard reported");
        expectInvalid(() -> small.paste(large, 0, 0));
        check(!small.canUndo() && small.current().strokes.isEmpty(), "oversized paste preserves history/page");
    }

    private static void persistence() throws Exception {
        File directory = Files.createTempDirectory("notebook-lasso-").toFile();
        try {
            NotebookDocument doc = new NotebookDocument(directory); doc.load(); doc.initializeSize(100, 100);
            doc.appendAll(Arrays.asList(stroke(10, 10, 20, 20), stroke(30, 30)));
            NotebookLasso.Selection selected = NotebookLasso.all(doc.current());
            byte[] prior = Files.readAllBytes(new File(directory, "page.nbk").toPath());
            NotebookLasso.select(doc.current(), path(0, 0, 50, 0, 50, 50, 0, 50));
            check(Arrays.equals(prior, Files.readAllBytes(new File(directory, "page.nbk").toPath())), "selection never saves preview");
            selected = doc.editSelection(selected, NotebookLasso.Action.MOVE, 20, 20);
            NotebookPage saved = new NotebookStore(directory).load();
            near(30, saved.strokes.get(0).points.get(0).x, "move persisted");
            check(saved.strokes.get(0).id.equals(selected.strokes.get(0).id), "saved move ID");
            check(doc.undo(), "persisted move undo");
            near(10, new NotebookStore(directory).load().strokes.get(0).points.get(0).x, "undo persisted");
            check(doc.redo(), "persisted move redo");
            File blocker = new File(directory, "page.nbk.tmp");
            check(blocker.mkdir(), "block replacement write");
            Files.write(new File(blocker, "keep").toPath(), new byte[]{1});
            try { doc.editSelection(selected, NotebookLasso.Action.DELETE, 0, 0); throw new AssertionError("Expected save failure"); }
            catch (IOException expected) { assertions++; }
            check(doc.current().strokes.isEmpty(), "failed delete retained in memory");
            check(new NotebookStore(directory).load().strokes.size() == 2, "failed delete preserves disk");
            Files.delete(new File(blocker, "keep").toPath()); Files.delete(blocker.toPath());
            doc.retry(); check(new NotebookStore(directory).load().strokes.isEmpty(), "retry persists deletion");
            check(doc.undo(), "history survives retry");
            check(new NotebookStore(directory).load().strokes.size() == 2, "retry undo persists both strokes");
            NotebookLasso.Clipboard clip = new NotebookLasso.Clipboard(NotebookLasso.all(doc.current()));
            doc.paste(clip, 70, 70);
            NotebookDocument reopened = new NotebookDocument(directory); reopened.load();
            check(reopened.current().strokes.size() == 4 && !reopened.canUndo(), "paste reopens, history remains session-local");
            check(!reopened.current().strokes.get(0).id.equals(reopened.current().strokes.get(2).id), "paste IDs survive reopen");
        } finally { delete(directory); }
    }

    private static void multiPage() throws Exception {
        File directory = Files.createTempDirectory("notebook-lasso-book-").toFile();
        try {
            NotebookBook book = new NotebookBook(directory); book.load(); book.initializeSize(100, 100);
            book.appendStrokesToPage(1, Arrays.asList(stroke(10, 10), stroke(20, 20)));
            NotebookLasso.Selection selected = NotebookLasso.all(book.page(1));
            NotebookLasso.Clipboard clip = new NotebookLasso.Clipboard(selected);
            book.addPage(); book.pasteOnPage(2, clip, 50, 50);
            check(book.page(1).strokes.size() == 2 && book.page(2).strokes.size() == 2, "copy across pages");
            check(!book.page(1).strokes.get(0).id.equals(book.page(2).strokes.get(0).id), "cross-page copy IDs");
            book.editSelectionOnPage(1, selected, NotebookLasso.Action.DELETE, 0, 0);
            check(book.page(1).strokes.isEmpty() && book.page(2).strokes.size() == 2, "cut targets source page");
            check(book.undoOnPage(1) && book.page(1).strokes.size() == 2, "source page undo");
            check(book.undoOnPage(2) && book.page(2).strokes.isEmpty(), "destination page undo");
        } finally { delete(directory); }
    }

    private static NotebookPage page(NotebookPage.Stroke... strokes) {
        return NotebookPage.empty().withSize(100, 100).withStrokes(Arrays.asList(strokes));
    }
    private static NotebookPage.Stroke stroke(float... xy) { return new NotebookPage.Stroke(2, 4, 0xff000000, path(xy)); }
    private static List<NotebookPage.Point> path(float... xy) {
        List<NotebookPage.Point> points = new ArrayList<>();
        for (int i = 0; i < xy.length; i += 2) points.add(new NotebookPage.Point(xy[i], xy[i + 1], 100 + i, 0.25f + i * 0.1f));
        return points;
    }
    private static void expectInvalid(Runnable task) {
        try { task.run(); throw new AssertionError("Expected invalid input"); }
        catch (IllegalArgumentException expected) { assertions++; }
    }
    private static void check(boolean result, String label) { assertions++; if (!result) throw new AssertionError(label); }
    private static void near(float expected, float actual, String label) { check(Math.abs(expected - actual) < 0.0001, label + ": " + actual); }
    private static void delete(File file) throws IOException {
        File[] files = file.listFiles(); if (files != null) for (File child : files) delete(child);
        Files.deleteIfExists(file.toPath());
    }
}
