package org.eink.inkprobe.notebook;

import java.util.UUID;
import java.util.Arrays;

/** The return arrows follow PDF links and outline destinations; ordinary reading never grows or branches history. */
public final class NotebookViewHistoryTest {
    private static int assertions;
    public static void main(String[] args) {
        ordinaryReadingActionsNeverRecord(); linkAndOutlineRoundTrips(); ordinaryReadingPreservesForward();
        rotationRetainsBothStacks(); repeatedRotationRetainsCoordinates(); restoredHistoryKeepsNavigationPolicy();
        notebookScopeAndInvalidSnapshots(); boundedHistorySurvivesRotation(); restoredFitAdaptsToOrientation();
        System.out.println("NotebookViewHistory: " + assertions + " assertions passed (link/outline policy and rotation state).");
    }

    private static void ordinaryReadingActionsNeverRecord() {
        for (NotebookViewHistory.Navigation action : NotebookViewHistory.Navigation.values()) {
            if (action == NotebookViewHistory.Navigation.PDF_LINK || action == NotebookViewHistory.Navigation.OUTLINE) continue;
            NotebookViewHistory history = new NotebookViewHistory();
            history.record(view(1, 20, 1), action);
            history.record(view(10, 900, 4), action);
            check(!history.canBack() && !history.canForward(), action + " never creates a return destination");
        }
    }

    private static void linkAndOutlineRoundTrips() {
        NotebookViewHistory history = new NotebookViewHistory();
        NotebookViewHistory.State origin = view(1, 20, 1), link = view(7, 80, 2), outline = view(22, 140, .5f);
        history.record(origin, NotebookViewHistory.Navigation.PDF_LINK);
        history.record(link, NotebookViewHistory.Navigation.OUTLINE);
        NotebookViewHistory.State read = view(23, 400, 3);
        history.record(outline, NotebookViewHistory.Navigation.SCROLL);
        history.record(read, NotebookViewHistory.Navigation.PAGE_BUTTON);
        check(history.back(read).equals(link), "Back skips scrolling/page changes and returns to outline origin");
        check(history.back(link).equals(origin) && !history.canBack(), "next Back returns to PDF link origin");
        check(history.forward(origin).equals(link), "Forward restores exact link origin including zoom");
        check(history.forward(link).equals(read) && !history.canForward(), "Forward restores departed reading position");
    }

    private static void ordinaryReadingPreservesForward() {
        NotebookViewHistory history = new NotebookViewHistory();
        NotebookViewHistory.State origin = view(1, 0, 1), target = view(8, 60, 2);
        history.record(origin, NotebookViewHistory.Navigation.PDF_LINK);
        history.back(target);
        for (NotebookViewHistory.Navigation action : NotebookViewHistory.Navigation.values()) {
            if (action == NotebookViewHistory.Navigation.PDF_LINK || action == NotebookViewHistory.Navigation.OUTLINE) continue;
            history.record(view(3, 400, 4), action);
            check(history.canForward() && !history.canBack(), action + " preserves forward return destination");
        }
        NotebookViewHistory.State moved = view(3, 400, 4);
        check(history.forward(moved).equals(target), "Forward still restores the link destination after ordinary movement");
        history.back(target);
        history.record(moved, NotebookViewHistory.Navigation.OUTLINE);
        check(!history.canForward() && history.back(target).equals(moved), "new outline jump branches from current reading view");
    }

    private static void rotationRetainsBothStacks() {
        NotebookViewHistory history = new NotebookViewHistory();
        NotebookViewHistory.State origin = view(1, 20, 1), link = view(7, 80, 2), outline = view(22, 140, .5f);
        history.record(origin, NotebookViewHistory.Navigation.PDF_LINK);
        history.record(link, NotebookViewHistory.Navigation.OUTLINE);
        check(history.back(outline).equals(link), "prepare a previous and next destination before rotation");
        NotebookViewHistory rotated = recreate(history, 17);
        check(rotated.canBack() && rotated.canForward(), "rotation retains both return arrows");
        check(rotated.forward(link).equals(outline), "forward destination survives portrait to landscape");
        rotated = recreate(rotated, 17);
        check(rotated.back(outline).equals(link), "previous destination survives landscape to portrait");
        check(rotated.back(link).equals(origin) && !rotated.canBack(), "all older link destinations retain their order");
        check(rotated.forward(origin).equals(link) && rotated.forward(link).equals(outline), "both forward destinations retain their order");
    }

    private static void repeatedRotationRetainsCoordinates() {
        String[] fits = {"WIDTH", "HEIGHT", "PAGE", "CUSTOM"};
        for (String fit : fits) {
            NotebookViewHistory history = new NotebookViewHistory();
            NotebookViewHistory.State exact = new NotebookViewHistory.State(UUID.randomUUID(), -17.12345f, 691.9876f, .7123456f, fit);
            history.record(exact, NotebookViewHistory.Navigation.PDF_LINK);
            for (int rotation = 0; rotation < 8; rotation++) history = recreate(history, 9);
            NotebookViewHistory.State restored = history.back(view(600, 350, 3));
            check(restored.pageId.equals(exact.pageId) && restored.fit.equals(fit), "rotation preserves stable page identity and " + fit + " fit mode");
            check(Float.floatToIntBits(restored.x) == Float.floatToIntBits(exact.x)
                && Float.floatToIntBits(restored.y) == Float.floatToIntBits(exact.y)
                && Float.floatToIntBits(restored.zoom) == Float.floatToIntBits(exact.zoom), "rotation preserves exact logical coordinates and zoom");
        }
    }

    private static void restoredHistoryKeepsNavigationPolicy() {
        NotebookViewHistory history = new NotebookViewHistory();
        NotebookViewHistory.State origin = view(1, 0, 1), target = view(20, 25, 3), read = view(21, 70, 2);
        history.record(origin, NotebookViewHistory.Navigation.PDF_LINK); history.back(target);
        history = recreate(history, 3);
        for (NotebookViewHistory.Navigation action : NotebookViewHistory.Navigation.values()) {
            if (action == NotebookViewHistory.Navigation.PDF_LINK || action == NotebookViewHistory.Navigation.OUTLINE) continue;
            history.record(read, action);
            history = recreate(history, 3);
            check(!history.canBack() && history.canForward(), "rotation and " + action + " do not add history or clear Forward");
        }
        history.record(read, NotebookViewHistory.Navigation.OUTLINE);
        history = recreate(history, 3);
        check(!history.canForward() && history.back(target).equals(read), "new outline jump after rotation branches normally");
    }

    private static void notebookScopeAndInvalidSnapshots() {
        NotebookViewHistory history = new NotebookViewHistory();
        history.record(view(2, 60, 2), NotebookViewHistory.Navigation.PDF_LINK);
        byte[] saved = history.save(12);
        check(!history.restore(13, saved) && !history.canBack() && !history.canForward(), "a different notebook never inherits return destinations");
        check(!history.restore(12, null) && !history.canBack(), "missing state is safe");
        for (int size = 0; size < saved.length; size++) {
            history.record(view(3, 20, 1));
            check(!history.restore(12, Arrays.copyOf(saved, size)) && !history.canBack() && !history.canForward(), "truncated state is discarded atomically at byte " + size);
        }
        byte[] invalid = Arrays.copyOf(saved, saved.length + 1);
        check(!history.restore(12, invalid), "unrecognized trailing data is discarded");
        invalid = saved.clone(); invalid[0] ^= 1;
        check(!history.restore(12, invalid), "unknown history format is discarded");
        invalid = saved.clone(); putInt(invalid, 8, 101);
        check(!history.restore(12, invalid), "oversized back stack is discarded");
        invalid = saved.clone(); putInt(invalid, 28, Float.floatToIntBits(Float.NaN));
        check(!history.restore(12, invalid) && !history.canBack(), "non-finite view coordinates are discarded");
        invalid = saved.clone(); putInt(invalid, 36, 0);
        check(!history.restore(12, invalid), "invalid zoom is discarded");
        check(!history.restore(12, new byte[32769]), "oversized saved state is discarded");
        check(history.restore(12, saved) && history.canBack(), "a discarded snapshot does not prevent later valid restoration");
    }

    private static void boundedHistorySurvivesRotation() {
        NotebookViewHistory history = new NotebookViewHistory();
        for (int i = 0; i < 250; i++) history.record(view(i, i * 2, 1));
        byte[] saved = history.save(27);
        check(saved.length < 8192, "saved history remains small enough for Activity instance state");
        history = recreate(history, 27);
        for (int i = 249; i >= 150; i--) check(history.back(view(i + 1, 0, 1)).equals(view(i, i * 2, 1)), "bounded history keeps ordered destination " + i);
        check(!history.canBack(), "rotation preserves the 100-destination bound");
        history = recreate(history, 27);
        check(history.canForward(), "a full forward stack also survives rotation");
        NotebookViewHistory empty = recreate(new NotebookViewHistory(), 27);
        check(!empty.canBack() && !empty.canForward(), "empty history roundtrips");
    }

    private static void restoredFitAdaptsToOrientation() {
        java.util.List<NotebookViewport.Size> sizes = Arrays.asList(
            new NotebookViewport.Size(600, 1200), new NotebookViewport.Size(1200, 600), new NotebookViewport.Size(800, 1400));
        NotebookViewport portrait = new NotebookViewport(sizes, 800, 1200, 20);
        NotebookViewport landscape = new NotebookViewport(sizes, 1200, 800, 20);
        for (NotebookViewport source : Arrays.asList(portrait, landscape)) {
            NotebookViewport rotated = source == portrait ? landscape : portrait;
            for (int page = 1; page <= sizes.size(); page++) {
                for (NotebookViewport.Fit fit : NotebookViewport.Fit.values()) {
                    float percentage = fit == NotebookViewport.Fit.CUSTOM ? 1.875f : source.fittedZoom(fit, page);
                    NotebookViewHistory history = new NotebookViewHistory();
                    history.record(new NotebookViewHistory.State(new UUID(0, page), source.pageWidth(page) / 2f,
                        source.pageHeight(page) / 2f, percentage, fit.name()), NotebookViewHistory.Navigation.OUTLINE);
                    NotebookViewHistory.State restored = recreate(history, 8).back(view(3, 20, 1));
                    check(restored.fitMode() == fit, "restored navigation retains " + fit + " mode");
                    float expected = fit == NotebookViewport.Fit.CUSTOM ? percentage : rotated.fittedZoom(fit, page);
                    check(restored.zoomFor(rotated, page) == expected, "restored " + fit + " uses current orientation and destination page " + page);
                    NotebookViewport returned = rotated.withView(restored.zoomFor(rotated, page), 0);
                    if (fit == NotebookViewport.Fit.WIDTH) check(Math.abs(returned.pageRight(page) - returned.pageLeft(page) - rotated.width) < .001f,
                        "restored width fit fills the rotated display width");
                    if (fit == NotebookViewport.Fit.HEIGHT) check(Math.abs(returned.pageHeight(page) * returned.scale(page) - rotated.height) < .001f,
                        "restored height fit fills the rotated display height");
                    if (fit == NotebookViewport.Fit.PAGE) check(returned.pageWidth(page) * returned.scale(page) <= rotated.width + .001f
                        && returned.pageHeight(page) * returned.scale(page) <= rotated.height + .001f, "restored page fit keeps the entire destination visible");
                    float scroll = returned.scrollForPoint(page, restored.y, returned.height / 2f);
                    check(Math.abs(returned.pageTop(page, scroll) + restored.y * returned.scale(page) - returned.height / 2f) < .001f,
                        "restored logical vertical center follows the target after rotation");
                }
            }
        }
        NotebookViewHistory.State unknown = new NotebookViewHistory.State(new UUID(0, 1), 10, 20, 2.25f, "FUTURE_MODE");
        check(unknown.fitMode() == NotebookViewport.Fit.CUSTOM && unknown.zoomFor(landscape, 1) == 2.25f,
            "an unknown future fit mode safely retains the stored percentage");
    }

    private static NotebookViewHistory recreate(NotebookViewHistory previous, int notebookId) {
        NotebookViewHistory recreated = new NotebookViewHistory();
        check(recreated.restore(notebookId, previous.save(notebookId)), "new Activity restores its saved history");
        return recreated;
    }

    private static void putInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 24); bytes[offset + 1] = (byte) (value >>> 16);
        bytes[offset + 2] = (byte) (value >>> 8); bytes[offset + 3] = (byte) value;
    }

    private static NotebookViewHistory.State view(int page, float y, float zoom) {
        return new NotebookViewHistory.State(new UUID(0, page), page, y, zoom, "CUSTOM");
    }
    private static void check(boolean condition, String message) {
        assertions++; if (!condition) throw new AssertionError(message);
    }
}
