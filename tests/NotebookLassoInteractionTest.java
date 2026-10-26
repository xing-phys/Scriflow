package org.eink.inkprobe.notebook;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Pointer-UP completion, explicit visible closure, movement ownership and nearby action geometry. */
public final class NotebookLassoInteractionTest {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        liftClosesWithoutNativeCommit(); completionOrders(); selectionStream(); cancellationAndReuse(); dragOwnership(); previewThenOneEdit(); placement();
        System.out.println("NotebookLassoInteraction: " + assertions + " assertions passed (host JVM, real files).");
    }

    private static void liftClosesWithoutNativeCommit() {
        NotebookLassoGesture gesture = new NotebookLassoGesture(); gesture.begin();
        gesture.add(10, 10, 100, 0.5f); gesture.add(80, 10, 120, 0.5f);
        gesture.add(80, 80, 140, 0.5f); gesture.add(10, 80, 160, 0);
        // No native track or edit notification is supplied. Standard UP is sufficient.
        List<NotebookPage.Point> closed = gesture.finish(false);
        check(closed.size() == 5 && !gesture.isActive(), "UP creates an explicitly closed outline");
        samePosition(closed.get(0), closed.get(4), "closing edge joins the original start");
        check(closed.get(4).time == 160, "closure keeps the terminal event time");
        check(gesture.finish(false) == null, "duplicate UP cannot select twice");
        check(NotebookLasso.closedOutline(closed).size() == 5, "closure is idempotent");
        NotebookPage page = NotebookPage.empty().withSize(100, 100).withStroke(stroke());
        check(NotebookLasso.select(page, closed).strokes.size() == 1, "open original gesture encloses ink through closing edge");
        check(NotebookLasso.contains(closed, 20, 20), "closed region receives pointer input inside it");
        check(!NotebookLasso.contains(closed, 5, 5), "outside pointer distinguished");
        List<NotebookPage.Point> moved = NotebookLasso.offsetOutline(closed, 7, 9);
        samePosition(moved.get(0), moved.get(4), "moving keeps the outline closed");
        check(closed.get(0).x == 10 && moved.get(0).x == 17, "original immutable outline preserved");
        try { closed.clear(); throw new AssertionError("Expected immutable outline"); }
        catch (UnsupportedOperationException expected) { assertions++; }
    }

    private static void cancellationAndReuse() {
        NotebookLassoGesture gesture = new NotebookLassoGesture();
        check(gesture.finish(false) == null, "UP without DOWN ignored");
        gesture.begin(); gesture.add(1, 2, 1, 1); check(gesture.finish(true).isEmpty(), "cancel does not select a partial path");
        gesture.begin(); gesture.add(1, 2, 2, 1); gesture.reset(); check(gesture.finish(false) == null, "pause/tool reset discards partial input");
        gesture.begin();
        for (int i = 0; i < 512; i++) gesture.add(i, i % 25, 10 + i, i % 3 / 2f);
        List<NotebookPage.Point> complete = gesture.finish(false);
        check(complete.size() == 513, "capture grows primitive buffers without losing samples");
        for (int i = 0; i < 512; i++) check(complete.get(i).x == i && complete.get(i).time == 10 + i, "retained movement/history sample");
        gesture.begin(); gesture.add(20, 20, 1000, 0.1f); gesture.add(25, 20, 1001, 0.1f);
        check(gesture.finish(false).size() == 3, "new gesture does not contain previous samples");
    }

    private static void completionOrders() {
        List<NotebookPage.Point> android = Collections.singletonList(new NotebookPage.Point(1, 2, 3, 1));
        List<NotebookPage.Point> nativePath = Arrays.asList(android.get(0), new NotebookPage.Point(2, 3, 4, 1));
        NotebookLassoCompletion completion = new NotebookLassoCompletion();
        check(completion.offer(android, false), "Android UP schedules completion without native track callback");
        check(!completion.offer(nativePath, true), "native UP does not schedule another selection");
        check(completion.take() == nativePath && !completion.isPending(), "native sample density preferred before restoration");
        check(completion.take() == null, "completion consumed exactly once");
        check(completion.offer(nativePath, true), "native UP alone can complete selection");
        check(!completion.offer(android, false) && completion.take() == nativePath, "reverse observer order preserves native geometry");
        completion.offer(android, false); completion.offer(Collections.emptyList(), true); completion.offer(nativePath, true);
        check(completion.take().isEmpty(), "cancellation wins over a later UP");
        completion.offer(Collections.emptyList(), false); completion.offer(nativePath, true);
        check(completion.take().isEmpty(), "Android cancellation cannot be undone by late native UP");
        completion.offer(nativePath, true); completion.reset(); check(!completion.isPending(), "pause clears queued completion");
    }

    private static void dragOwnership() {
        NotebookSelectionDrag drag = new NotebookSelectionDrag();
        // The same owner is used for a finger or stylus pointer; there is no scroll handoff.
        for (int pointer : new int[]{2, 9}) {
            drag.begin(pointer, 30, 30, true, 6);
            drag.move(pointer + 1, 90, 90, -20, 60, -20, 60);
            check(drag.dx == 0 && drag.dy == 0, "unowned contact cannot move selected ink");
            drag.move(pointer, 32, 31, -20, 60, -20, 60);
            check(!drag.isMoving(), "tap jitter does not become a move");
            drag.move(pointer, 200, -200, -20, 60, -20, 60);
            check(drag.isMoving() && drag.dx == 60 && drag.dy == -20, "shared group offset clamps to page edges");
            NotebookSelectionDrag.Result result = drag.finish(false);
            check(result.moved && !result.cancelled && !result.outside && result.dx == 60, "one completed owned move");
            check(!drag.isActive() && drag.finish(false) == null, "release consumes ownership exactly once");
        }
        drag.begin(1, 10, 10, true, 3); drag.move(1, 20, 20, -10, 50, -10, 50); drag.cancel();
        drag.move(1, 40, 40, -10, 50, -10, 50);
        NotebookSelectionDrag.Result mixed = drag.finish(false);
        check(mixed.cancelled && mixed.dx == 0 && mixed.dy == 0, "second contact cancels without committing a jump");
        drag.begin(1, 10, 10, true, 3); drag.move(1, 20, 20, -10, 50, -10, 50);
        check(drag.finish(true).cancelled, "system cancellation cannot move ink");
        drag.begin(1, 1, 1, false, 3); drag.move(1, 50, 50, -10, 50, -10, 50);
        NotebookSelectionDrag.Result outside = drag.finish(false);
        check(outside.outside && !outside.moved && outside.dx == 0, "outside gesture only dismisses selection");
    }

    private static void selectionStream() {
        NotebookSelectionStream stream = new NotebookSelectionStream(); stream.activate(100);
        check(!stream.accept(0, 80, true), "late Android DOWN from the lasso cannot drag its region");
        check(!stream.accept(2, 90, true) && !stream.accept(1, 95, true), "old lasso MOVE/UP also ignored");
        check(!stream.accept(0, 110, false), "fresh contact cannot start during selection restoration");
        check(!stream.accept(2, 120, true), "unowned MOVE cannot start halfway through a contact");
        check(stream.accept(0, 130, true), "new finger/stylus contact accepted after selection activates");
        check(stream.accept(2, 140, false), "drag MOVE still delivered while native input is paused");
        check(stream.accept(5, 150, false) && stream.accept(6, 160, false), "extra-contact events reach cancellation handler");
        check(stream.accept(1, 170, false), "owned UP delivered despite paused native input");
        check(!stream.accept(1, 171, true), "terminal event cannot be delivered twice");
        check(stream.accept(0, 180, true) && stream.accept(3, 190, false), "owned system cancellation reaches region handler");
        stream.activate(200); check(!stream.accept(2, 210, true), "recreated selection cannot inherit an old stream");
        stream.accept(0, 220, true); stream.reset(); check(!stream.accept(1, 230, true), "lifecycle reset rejects old terminal event");
    }

    private static void previewThenOneEdit() throws Exception {
        File directory = Files.createTempDirectory("lasso-interaction-").toFile();
        try {
            NotebookDocument document = new NotebookDocument(directory); document.load(); document.initializeSize(100, 100);
            document.append(stroke());
            NotebookLassoGesture gesture = new NotebookLassoGesture(); gesture.begin();
            gesture.add(10, 10, 100, 1); gesture.add(60, 10, 120, 1); gesture.add(60, 60, 140, 1); gesture.add(10, 60, 160, 1);
            List<NotebookPage.Point> outline = gesture.finish(false);
            NotebookLasso.Selection selection = NotebookLasso.select(document.current(), outline);
            byte[] original = Files.readAllBytes(new File(directory, "page.nbk").toPath());
            NotebookSelectionDrag drag = new NotebookSelectionDrag(); drag.begin(7, 20, 20, true, 3);
            for (int i = 0; i < 40; i++) {
                drag.move(7, 20 + i, 20 + i / 2f, -20, 60, -20, 60);
                List<NotebookPage.Point> preview = NotebookLasso.offsetOutline(outline, drag.dx, drag.dy);
                samePosition(preview.get(0), preview.get(preview.size() - 1), "preview outline stays closed");
            }
            check(Arrays.equals(original, Files.readAllBytes(new File(directory, "page.nbk").toPath())), "all drag previews leave saved notes unchanged");
            NotebookSelectionDrag.Result move = drag.finish(false);
            NotebookLasso.Selection moved = document.editSelection(selection, NotebookLasso.Action.MOVE, move.dx, move.dy);
            check(moved.strokes.get(0).id.equals(selection.strokes.get(0).id), "drag keeps stroke identity");
            List<NotebookPage.Point> movedOutline = NotebookLasso.offsetOutline(outline, move.dx, move.dy);
            NotebookPage.Point movedPoint = moved.strokes.get(0).points.get(0);
            check(NotebookLasso.contains(movedOutline, movedPoint.x, movedPoint.y), "region and selected ink move together");
            check(document.undo(), "completed drag is one undoable edit");
            check(document.current().strokes.get(0) == selection.strokes.get(0), "one undo restores the original stroke");
            check(document.canRedo(), "redo retained");
            List<NotebookPage.Point> emptyOutline = NotebookLasso.closedOutline(Arrays.asList(
                new NotebookPage.Point(70, 70, 0, 1), new NotebookPage.Point(90, 70, 0, 1), new NotebookPage.Point(90, 90, 0, 1)));
            check(NotebookLasso.select(document.current(), emptyOutline).isEmpty(), "empty selection remains a distinct outline state");
            check(emptyOutline.size() == 4 && document.canRedo(), "empty outline can be displayed without changing history");
        } finally { NotebookBackup.deleteTree(directory); }
    }

    private static void placement() {
        int[] above = NotebookSelectionPlacement.place(700, 900, 420, 78, 8, 300, 700, 500, 880, 0, 0, 0, 0);
        check(above[1] == 614, "actions move above a region near the bottom");
        int[] avoid = NotebookSelectionPlacement.place(700, 900, 420, 78, 8, 150, 200, 400, 400, 0, 400, 700, 500);
        check(avoid[1] == 114, "actions avoid the main tools when the other side is free");
        Random random = new Random(4109);
        for (int i = 0; i < 400; i++) {
            int width = 100 + random.nextInt(1400), height = 100 + random.nextInt(1800);
            int barWidth = Math.min(width, 420), barHeight = Math.min(height, 78);
            int[] position = NotebookSelectionPlacement.place(width, height, barWidth, barHeight, 8,
                random.nextInt(width * 3) - width, random.nextInt(height * 3) - height,
                random.nextInt(width * 3), random.nextInt(height * 3), 0, 0, width, 64);
            check(position[0] >= 0 && position[0] + barWidth <= width, "nearby actions remain horizontally visible");
            check(position[1] >= 0 && position[1] + barHeight <= height, "nearby actions remain vertically visible");
        }
    }

    private static NotebookPage.Stroke stroke() {
        return new NotebookPage.Stroke(2, 4, 0xff000000, Arrays.asList(
            new NotebookPage.Point(20, 20, 0, 0.3f), new NotebookPage.Point(40, 40, 20, 0.8f)));
    }
    private static void samePosition(NotebookPage.Point a, NotebookPage.Point b, String message) { check(a.x == b.x && a.y == b.y, message); }
    private static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
}
