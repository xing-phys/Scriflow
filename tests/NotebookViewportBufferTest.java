package org.eink.inkprobe.notebook;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Actual viewport preview ledger regressions for asynchronous saves and window changes. */
public final class NotebookViewportBufferTest {
    private static int assertions;

    public static void main(String[] args) {
        earlierAcknowledgementKeepsLaterInk();
        identicalAndEmptyBatchesHaveIndependentTokens();
        pendingPagesSurviveWindowPruning();
        completeFailedGroupUsesRetainedModel();
        overlappingGroupsAndPartialWindows();
        defensiveCopiesAndStableSnapshots();
        clearRetiresOldTokens();
        invalidRequestsLeaveCurrentInkIntact();
        System.out.println("NotebookViewportBuffer: " + assertions + " assertions passed (pending ink preview).");
    }

    private static void earlierAcknowledgementKeepsLaterInk() {
        NotebookViewportBuffer buffer = new NotebookViewportBuffer();
        NotebookPage initial = page();
        NotebookPage.Stroke a = stroke(10), b = stroke(20);
        buffer.window(pages(1, initial), ids(1));
        long first = buffer.append(batch(1, a));
        Map<Integer, NotebookPage> afterA = buffer.current();
        long second = buffer.append(batch(1, b));
        ink(buffer, 1, a, b);
        buffer.complete(first, pages(1, page(a)), ids(1));
        ink(buffer, 1, a, b); // A's save completion must not erase the newer optimistic B.
        check(afterA.get(1).strokes.equals(Arrays.asList(a)), "earlier immutable preview remains unchanged");
        buffer.complete(second, pages(1, page(a, b)), ids(1));
        ink(buffer, 1, a, b); // B is acknowledged exactly once, rather than overlaid again.
        check(buffer.current().get(1).pointCount == 2, "acknowledgement does not duplicate points");
    }

    private static void identicalAndEmptyBatchesHaveIndependentTokens() {
        NotebookViewportBuffer buffer = new NotebookViewportBuffer();
        buffer.window(pages(1, page()), ids(1));
        Map<Integer, List<NotebookPage.Stroke>> empty = Collections.emptyMap();
        long emptyA = buffer.append(empty), emptyB = buffer.append(empty);
        check(emptyA != emptyB, "equal empty gestures still have different ownership tokens");
        buffer.complete(emptyA, pages(1, page()), ids(1));
        buffer.complete(emptyB, pages(1, page()), ids(1));
        ink(buffer, 1);
        NotebookPage.Stroke a = stroke(10);
        Map<Integer, List<NotebookPage.Stroke>> same = batch(1, a);
        long first = buffer.append(same), second = buffer.append(same);
        check(first != second && first != emptyA && second != emptyB, "identical inputs are separate gestures");
        buffer.complete(first, pages(1, page(a)), ids(1));
        ink(buffer, 1, a, a);
        buffer.complete(second, pages(1, page(a, a)), ids(1));
        ink(buffer, 1, a, a);
    }

    private static void pendingPagesSurviveWindowPruning() {
        NotebookViewportBuffer buffer = new NotebookViewportBuffer();
        Map<Integer, NotebookPage> loaded = pages(1, page());
        loaded.put(2, page()); loaded.put(3, page());
        buffer.window(loaded, ids(1, 2, 3));
        NotebookPage.Stroke a = stroke(10), b = stroke(20);
        long pending = buffer.append(batch(1, a));
        buffer.window(pages(2, page(b)), ids(2));
        ink(buffer, 1, a);
        ink(buffer, 2, b);
        check(!buffer.current().containsKey(3), "unneeded nonpending page is pruned");
        buffer.complete(pending, pages(1, page(a)), ids(2));
        check(!buffer.current().containsKey(1), "completed offscreen page can be pruned");
        ink(buffer, 2, b);
        buffer.window(pages(1, page(a)), ids(1));
        ink(buffer, 1, a);
        check(!buffer.current().containsKey(2), "later window retains only its requested pages");
    }

    private static void completeFailedGroupUsesRetainedModel() {
        NotebookViewportBuffer buffer = new NotebookViewportBuffer();
        Map<Integer, NotebookPage> loaded = pages(1, page()); loaded.put(2, page());
        buffer.window(loaded, ids(1, 2));
        NotebookPage.Stroke a = stroke(10), b = stroke(20), c = stroke(30);
        Map<Integer, List<NotebookPage.Stroke>> split = batch(1, a, b); split.put(2, Arrays.asList(c));
        long token = buffer.append(split);
        ink(buffer, 1, a, b); ink(buffer, 2, c);
        // The writer retains the whole accepted gesture in its model after a disk failure.
        // Removing the optimistic token must leave that model fully visible for Retry.
        Map<Integer, NotebookPage> retained = pages(1, page(a, b)); retained.put(2, page(c));
        buffer.complete(token, retained, ids(1, 2));
        ink(buffer, 1, a, b); ink(buffer, 2, c);
        check(buffer.current().get(1) == retained.get(1) && buffer.current().get(2) == retained.get(2),
            "failed group acknowledgement uses the complete retained model without an extra overlay");
    }

    private static void overlappingGroupsAndPartialWindows() {
        NotebookViewportBuffer buffer = new NotebookViewportBuffer();
        Map<Integer, NotebookPage> loaded = pages(1, page()); loaded.put(2, page());
        buffer.window(loaded, ids(1, 2));
        NotebookPage.Stroke a = stroke(10), b = stroke(20), c = stroke(30);
        Map<Integer, List<NotebookPage.Stroke>> split = batch(1, a); split.put(2, Arrays.asList(b));
        long first = buffer.append(split), second = buffer.append(batch(2, c));
        Map<Integer, NotebookPage> firstAck = pages(1, page(a)); firstAck.put(2, page(b));
        buffer.complete(first, firstAck, ids(1, 2));
        ink(buffer, 1, a); ink(buffer, 2, b, c);
        buffer.complete(second, pages(2, page(b, c)), ids(1, 2));
        ink(buffer, 1, a); ink(buffer, 2, b, c);
    }

    private static void defensiveCopiesAndStableSnapshots() {
        NotebookViewportBuffer buffer = new NotebookViewportBuffer();
        List<Integer> keep = new ArrayList<>(ids(1));
        Map<Integer, NotebookPage> loaded = pages(1, page());
        buffer.window(loaded, keep);
        loaded.clear(); keep.clear();
        check(buffer.current().size() == 1, "window and keep input containers are not retained");
        NotebookPage.Stroke a = stroke(10), b = stroke(20);
        List<NotebookPage.Stroke> strokes = new ArrayList<>(Arrays.asList(a));
        Map<Integer, List<NotebookPage.Stroke>> batch = new LinkedHashMap<>(); batch.put(1, strokes);
        long token = buffer.append(batch);
        Map<Integer, NotebookPage> immutable = buffer.current();
        strokes.add(b); batch.clear();
        ink(buffer, 1, a);
        expectUnsupported(() -> immutable.clear(), "current map is immutable");
        expectUnsupported(() -> immutable.get(1).strokes.clear(), "current page stroke list is immutable");
        Map<Integer, NotebookPage> acknowledged = pages(1, page(a));
        buffer.complete(token, acknowledged, ids(1));
        acknowledged.clear();
        ink(buffer, 1, a);
        buffer.clear();
        check(immutable.get(1).strokes.equals(Arrays.asList(a)), "published snapshot survives buffer clearing");
    }

    private static void clearRetiresOldTokens() {
        NotebookViewportBuffer buffer = new NotebookViewportBuffer();
        buffer.window(pages(1, page()), ids(1));
        long retired = buffer.append(batch(1, stroke(10)));
        buffer.clear();
        check(buffer.current().isEmpty(), "clear removes baseline and pending ink");
        buffer.window(pages(1, page()), ids(1));
        NotebookPage.Stroke b = stroke(20);
        long current = buffer.append(batch(1, b));
        check(current != retired, "tokens are not reused across canvas replacement");
        expectInvalid(() -> buffer.complete(retired, pages(1, page()), ids(1)), "stale completion cannot remove new ink");
        ink(buffer, 1, b);
        buffer.complete(current, pages(1, page(b)), ids(1));
        ink(buffer, 1, b);
    }

    private static void invalidRequestsLeaveCurrentInkIntact() {
        NotebookViewportBuffer buffer = new NotebookViewportBuffer();
        NotebookPage.Stroke a = stroke(10);
        buffer.window(pages(1, page(a)), ids(1));
        expectInvalid(() -> buffer.append(batch(2, stroke(20))), "unloaded ink page rejected");
        expectInvalid(() -> buffer.append(null), "missing batch rejected");
        expectInvalid(() -> buffer.window(pages(0, page()), ids(1)), "invalid page id rejected");
        expectInvalid(() -> buffer.window(pages(1, null), ids(1)), "missing page rejected");
        long token = buffer.append(Collections.emptyMap());
        expectInvalid(() -> buffer.complete(token, pages(1, page()), null), "invalid acknowledgement cannot remove token");
        ink(buffer, 1, a);
        buffer.complete(token, pages(1, page(a)), ids(1));
        ink(buffer, 1, a);
    }

    private static NotebookPage page(NotebookPage.Stroke... strokes) {
        return NotebookPage.empty().withSize(100, 100).withStrokes(Arrays.asList(strokes));
    }
    private static NotebookPage.Stroke stroke(int y) {
        return new NotebookPage.Stroke(2, 4, 0xff000000,
            Arrays.asList(new NotebookPage.Point(10, y, y, 1)));
    }
    private static Map<Integer, NotebookPage> pages(int id, NotebookPage page) {
        Map<Integer, NotebookPage> result = new LinkedHashMap<>(); result.put(id, page); return result;
    }
    private static Map<Integer, List<NotebookPage.Stroke>> batch(int id, NotebookPage.Stroke... strokes) {
        Map<Integer, List<NotebookPage.Stroke>> result = new LinkedHashMap<>(); result.put(id, Arrays.asList(strokes)); return result;
    }
    private static List<Integer> ids(Integer... ids) { return Arrays.asList(ids); }
    private static void ink(NotebookViewportBuffer buffer, int id, NotebookPage.Stroke... strokes) {
        NotebookPage page = buffer.current().get(id);
        check(page != null && page.strokes.equals(Arrays.asList(strokes)), "page " + id + " retains exactly the expected ink in order");
        int points = 0; for (NotebookPage.Stroke stroke : strokes) points += stroke.points.size();
        check(page.pointCount == points, "page " + id + " point count matches its strokes");
    }
    private static void expectInvalid(Runnable operation, String message) {
        try { operation.run(); throw new AssertionError(message); }
        catch (IllegalArgumentException expected) { assertions++; }
    }
    private static void expectUnsupported(Runnable operation, String message) {
        try { operation.run(); throw new AssertionError(message); }
        catch (UnsupportedOperationException expected) { assertions++; }
    }
    private static void check(boolean condition, String message) {
        assertions++; if (!condition) throw new AssertionError(message);
    }
}
