package org.eink.inkprobe.notebook;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Real-file tests of page isolation, process-local history, and interrupted navigation. */
public final class NotebookBookTest {
    private interface Action { void run() throws Exception; }
    private static int assertions;
    private static final List<File> directories = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        try {
            legacyUpgradePreservesBytes();
            independentPagesAndHistory();
            serializedRapidOperations();
            failedIndexPublicationAndRetry();
            ambiguousIndexPublicationAndRetry();
            corruptTargetNeverChangesSelection();
            changedCachedTargetIsNotOverwritten();
            missingTargetNeverBecomesBlank();
            failedPageCreationAndRetry();
            failedCurrentEditRetainsQueuedInk();
            unexpectedIndexFailsClosed();
            orphanPageIsPreserved();
            interruptedBlankCreationIsAdopted();
            initialLoadRetry();
            invalidInitialTargetCanBeRepaired();
            targetedViewportPagesDoNotPublishSelection();
            failedSplitStrokeRetainsEveryPage();
            scrollAnchorAndRetry();
            corruptScrollNeverResetsPosition();
            oneCrossPageGestureUsesOneHistoryStepPerPage();
            failedGroupedSplitRetainsHealthyPages();
            overviewPreviewsAreReadOnlyAndUncached();
            overviewFailuresDoNotPauseTheEditor();
            overviewRetainsDirtyAcceptedInk();
            overviewIgnoresIncompleteTailWithoutRepairingIt();
            overviewRequiresLoadedBookAndValidPage();
            System.out.println("NotebookBook: " + assertions + " assertions passed (host JVM, real files).");
        } finally { for (File directory : directories) deleteTree(directory); }
    }

    private static void oneCrossPageGestureUsesOneHistoryStepPerPage() throws Exception {
        File root = directory(); NotebookBook book = ready(root); book.addPage();
        book.appendToPage(1, stroke(5)); book.appendToPage(2, stroke(75));
        NotebookViewport viewport = new NotebookViewport(2, 100, 100, 100, 180, 24);
        NotebookPage.Stroke gesture = new NotebookPage.Stroke(2, 4, 0xff000000, Arrays.asList(
                new NotebookPage.Point(10, 80, 0, 1), new NotebookPage.Point(10, 150, 10, 1),
                new NotebookPage.Point(20, 150, 20, 1), new NotebookPage.Point(20, 80, 30, 1)));
        Map<Integer, List<NotebookPage.Stroke>> split = viewport.splitStroke(gesture, 0);
        equal(2, split.get(1).size(), "one gesture exits and reenters the first page");
        for (Map.Entry<Integer, List<NotebookPage.Stroke>> entry : split.entrySet())
            book.appendStrokesToPage(entry.getKey(), entry.getValue());
        equal(3, book.page(1).strokes.size(), "both first-page pieces accepted");
        equal(2, book.page(2).strokes.size(), "second-page portion accepted independently");
        book.undoOnPage(1);
        equal(1, book.page(1).strokes.size(), "one undo removes both first-page pieces");
        equal(2, book.page(2).strokes.size(), "first-page undo preserves second-page portion");
        book.undoOnPage(2);
        equal(1, book.page(2).strokes.size(), "one second-page undo removes its portion");
        book.redoOnPage(1); book.redoOnPage(2);
        equal(3, new NotebookStore(root).load().strokes.size(), "first-page grouped redo saved");
        equal(2, new NotebookStore(new File(root, "pages/2")).load().strokes.size(), "second-page grouped redo saved");
        equal(2, book.pageNumber(), "grouped viewport edits preserve selected-page index");
    }

    private static void failedGroupedSplitRetainsHealthyPages() throws Exception {
        File root = directory(); NotebookBook book = ready(root); book.addPage();
        book.page(1); book.page(2);
        byte[] first = bytes(root, "page.nbk");
        check(new File(root, "page.nbk").delete(), "remove first-page journal before failed group");
        check(new File(root, "page.nbk").mkdir(), "block first-page grouped journal writes");
        expectIo(() -> book.appendStrokesToPage(1, Arrays.asList(stroke(10), stroke(30))));
        IOException failure = book.error();
        book.appendStrokesToPage(2, Arrays.asList(stroke(50), stroke(70)));
        check(book.error() == failure, "healthy page group preserves earlier split failure");
        equal(2, book.cachedPage(1).strokes.size(), "every failed-page group piece retained");
        equal(2, book.cachedPage(2).strokes.size(), "every healthy-page group piece accepted");
        equal(2, new NotebookStore(new File(root, "pages/2")).load().strokes.size(), "healthy-page group saved despite other-page failure");
        check(new File(root, "page.nbk").delete(), "remove failed-group obstruction");
        Files.write(new File(root, "page.nbk").toPath(), first);
        book.retry();
        equal(2, new NotebookStore(root).load().strokes.size(), "retry saves full failed-page group");
        book.undoOnPage(1); book.undoOnPage(2);
        equal(0, book.cachedPage(1).strokes.size(), "one undo removes recovered failed-page group");
        equal(0, book.cachedPage(2).strokes.size(), "one undo removes healthy-page group");
    }

    private static void legacyUpgradePreservesBytes() throws Exception {
        File root = directory();
        NotebookDocument legacy = new NotebookDocument(root);
        legacy.load(); legacy.initializeSize(1404, 1688);
        legacy.setTemplate(NotebookPage.Template.LINED); legacy.append(stroke(10));
        byte[] original = bytes(root, "page.nbk");
        NotebookBook book = new NotebookBook(root); book.load();
        equal(1, book.pageNumber(), "legacy selection"); equal(1, book.pageCount(), "legacy count");
        equal(1, book.current().strokes.size(), "legacy strokes restored");
        check(!new File(root, "book.nbi").exists(), "opening legacy page creates no index");
        book.addPage();
        equal(2, book.pageCount(), "added second page"); equal(2, book.pageNumber(), "new page selected");
        equal(0, book.current().strokes.size(), "new page has no copied strokes");
        equal(1404, book.current().width, "new page inherits logical width");
        equal(1688, book.current().height, "new page inherits logical height");
        check(book.current().template == NotebookPage.Template.LINED, "new page inherits paper");
        book.selectPage(1); book.selectPage(2); book.selectPage(1);
        check(Arrays.equals(original, bytes(root, "page.nbk")), "upgrade/navigation preserve legacy bytes exactly");
        NotebookBook reopened = reopen(root);
        equal(1, reopened.pageNumber(), "restart selects last page");
        equal(2, reopened.pageCount(), "restart restores count");
        equal(1, reopened.current().strokes.size(), "restart restores original writing");
    }

    private static void independentPagesAndHistory() throws Exception {
        File root = directory(); NotebookBook book = ready(root);
        book.append(stroke(10)); book.append(stroke(30)); book.undo();
        check(book.canRedo(), "first page has redo");
        book.addPage(); check(!book.canUndo() && !book.canRedo(), "new page has own empty history");
        book.setTemplate(NotebookPage.Template.LINED); book.append(stroke(70));
        book.erase(path(70), 1); equal(0, book.current().strokes.size(), "erase second page");
        book.selectPage(1); check(book.canRedo(), "first page redo retained");
        check(book.current().template == NotebookPage.Template.BLANK, "first paper unaffected");
        book.redo(); equal(2, book.current().strokes.size(), "redo belongs to first page");
        book.selectPage(2); check(book.current().template == NotebookPage.Template.LINED, "second paper retained");
        book.undo(); equal(1, book.current().strokes.size(), "undo restores second page erased stroke");
        firstX(70, book.current()); book.load(); check(book.canRedo(), "load barrier keeps history");
        NotebookBook reopened = reopen(root);
        equal(2, reopened.pageNumber(), "last selected page reopened"); firstX(70, reopened.current());
        check(!reopened.canUndo() && !reopened.canRedo(), "new process has no old history");
        reopened.selectPage(1); equal(2, reopened.current().strokes.size(), "other page persisted independently");
    }

    private static void serializedRapidOperations() throws Exception {
        File root = directory(); NotebookBook book = ready(root);
        for (int page = 1; page <= 12; page++) {
            equal(page, book.pageNumber(), "serial operation selected expected page");
            book.append(stroke(page));
            book.setTemplate(page % 2 == 0 ? NotebookPage.Template.LINED : NotebookPage.Template.BLANK);
            if (page < 12) book.addPage();
        }
        for (int page = 12; page >= 1; page--) {
            book.selectPage(page); firstX(page, book.current());
            check(book.current().template == (page % 2 == 0 ? NotebookPage.Template.LINED : NotebookPage.Template.BLANK),
                "serialized paper change stays on its page");
        }
        NotebookBook reopened = reopen(root); equal(12, reopened.pageCount(), "all serial additions persisted");
    }

    private static void failedIndexPublicationAndRetry() throws Exception {
        File root = directory(); NotebookBook book = ready(root); book.append(stroke(10));
        block(root, "book.nbi.tmp"); expectIo(book::addPage);
        equal(1, book.pageNumber(), "failed new index leaves old visible page");
        equal(1, book.pageCount(), "unpublished page excluded from visible count"); firstX(10, book.current());
        check(new File(root, "pages/2/page.nbk").isFile(), "new page durable before index publication");
        check(book.error() != null, "index error exposed");
        byte[] old = bytes(root, "page.nbk"); expectIo(() -> book.append(stroke(99)));
        check(Arrays.equals(old, bytes(root, "page.nbk")), "index failure prevents further writes");
        unblock(root, "book.nbi.tmp"); book.retry();
        equal(2, book.pageNumber(), "retry completes intended navigation"); equal(2, book.pageCount(), "retry publishes count once");
        equal(0, book.current().strokes.size(), "retry does not copy old strokes");
        check(book.error() == null, "retry clears index error");
        book.selectPage(1); firstX(10, book.current());
    }

    private static void ambiguousIndexPublicationAndRetry() throws Exception {
        File root = directory(); NotebookBook book = ready(root); book.addPage(); book.selectPage(1);
        block(root, "book.nbi.tmp"); expectIo(() -> book.selectPage(2)); unblock(root, "book.nbi.tmp");
        NotebookIndex external = new NotebookIndex(root); external.load(); external.save(2, 2);
        book.retry(); equal(2, book.pageNumber(), "ambiguously completed exact target accepted");
        equal(2, book.pageCount(), "ambiguous retry does not add duplicate page");
    }

    private static void corruptTargetNeverChangesSelection() throws Exception {
        File root = directory(); NotebookBook book = ready(root); book.append(stroke(10)); book.addPage();
        book.append(stroke(70)); book.selectPage(1);
        byte[] valid = bytes(root, "pages/2/page.nbk"); byte[] corrupt = valid.clone(); corrupt[0] ^= 1;
        Files.write(new File(root, "pages/2/page.nbk").toPath(), corrupt);
        expectIo(() -> book.selectPage(2)); equal(1, book.pageNumber(), "corrupt target leaves old selection");
        firstX(10, book.current()); expectIo(book::retry);
        check(Arrays.equals(corrupt, bytes(root, "pages/2/page.nbk")), "corrupt target bytes preserved");
        equal(1, reopen(root).pageNumber(), "failed navigation does not persist new selection");
        Files.write(new File(root, "pages/2/page.nbk").toPath(), valid); book.retry();
        equal(2, book.pageNumber(), "fixed target allows intended navigation"); firstX(70, book.current());
    }

    private static void changedCachedTargetIsNotOverwritten() throws Exception {
        File root = directory(); NotebookBook book = ready(root); book.append(stroke(10)); book.addPage();
        book.append(stroke(70)); book.selectPage(1);
        NotebookStore external = new NotebookStore(new File(root, "pages/2")); external.load(); external.appendStroke(stroke(90));
        byte[] unexpected = bytes(root, "pages/2/page.nbk");
        expectIo(() -> book.selectPage(2)); expectIo(book::retry);
        equal(1, book.pageNumber(), "changed cached target cannot become writable");
        check(Arrays.equals(unexpected, bytes(root, "pages/2/page.nbk")), "unexpected cached target preserved");
    }

    private static void missingTargetNeverBecomesBlank() throws Exception {
        File root = directory(); NotebookBook book = ready(root); book.addPage(); book.selectPage(1);
        deleteTree(new File(root, "pages/2"));
        expectIo(() -> book.selectPage(2)); expectIo(book::retry);
        equal(1, book.pageNumber(), "missing page retains previous selection");
        check(!new File(root, "pages/2").exists(), "missing indexed page never recreated blank");
    }

    private static void failedPageCreationAndRetry() throws Exception {
        File root = directory(); NotebookBook book = ready(root); book.setTemplate(NotebookPage.Template.LINED);
        File pages = new File(root, "pages"); Files.write(pages.toPath(), new byte[] {9});
        expectIo(book::addPage); equal(1, book.pageCount(), "failed page creation does not publish count");
        check(!new File(root, "book.nbi").exists(), "page must exist durably before index");
        check(pages.delete(), "remove simulated unavailable parent"); book.retry();
        equal(2, book.pageNumber(), "page creation retry completes navigation");
        check(book.current().template == NotebookPage.Template.LINED, "retry preserves intended template");
        equal(0, book.current().strokes.size(), "retry creates empty page");
    }

    private static void failedCurrentEditRetainsQueuedInk() throws Exception {
        File root = directory(); NotebookBook book = ready(root); book.append(stroke(10)); book.append(stroke(30));
        block(root, "page.nbk.tmp"); expectIo(book::undo); expectIo(() -> book.append(stroke(70)));
        equal(2, book.current().strokes.size(), "queued completed stroke retained after failed edit");
        expectIo(book::addPage); equal(1, book.pageCount(), "failed current save blocks navigation");
        unblock(root, "page.nbk.tmp"); book.retry();
        equal(2, reopen(root).current().strokes.size(), "retry persists queued completed ink");
        book.addPage(); book.selectPage(1); equal(2, book.current().strokes.size(), "navigation retains recovered page");
    }

    private static void unexpectedIndexFailsClosed() throws Exception {
        File root = directory(); NotebookBook book = ready(root); book.addPage(); book.addPage(); book.selectPage(1);
        block(root, "book.nbi.tmp"); expectIo(() -> book.selectPage(2)); unblock(root, "book.nbi.tmp");
        NotebookIndex external = new NotebookIndex(root); external.load(); external.save(3, 3);
        byte[] unexpected = bytes(root, "book.nbi"); expectIo(book::retry);
        equal(1, book.pageNumber(), "unexpected valid selection does not change current page");
        check(Arrays.equals(unexpected, bytes(root, "book.nbi")), "unexpected index not overwritten");
    }

    private static void orphanPageIsPreserved() throws Exception {
        File root = directory(); NotebookBook book = ready(root);
        NotebookDocument orphan = new NotebookDocument(new File(root, "pages/2"));
        orphan.load(); orphan.initializeSize(100, 100); orphan.append(stroke(70));
        byte[] original = bytes(root, "pages/2/page.nbk"); expectIo(book::addPage); expectIo(book::retry);
        equal(1, book.pageCount(), "unindexed page not silently adopted");
        check(Arrays.equals(original, bytes(root, "pages/2/page.nbk")), "unindexed page not overwritten");
        deleteTree(new File(root, "pages/2")); book.retry(); equal(2, book.pageCount(), "explicitly removed orphan unblocks retry");
    }

    private static void initialLoadRetry() throws Exception {
        File root = directory(); NotebookBook original = ready(root); original.addPage();
        byte[] valid = bytes(root, "book.nbi"); byte[] bad = valid.clone(); bad[0] ^= 1;
        Files.write(new File(root, "book.nbi").toPath(), bad);
        NotebookBook book = new NotebookBook(root); expectIo(book::load); check(book.current() == null, "bad index shows no invented page");
        expectIo(book::load); check(Arrays.equals(bad, bytes(root, "book.nbi")), "load barrier leaves corrupt index intact");
        Files.write(new File(root, "book.nbi").toPath(), valid); book.retry(); equal(2, book.pageNumber(), "initial retry restores selected page");
    }

    private static void interruptedBlankCreationIsAdopted() throws Exception {
        File root = directory(); NotebookBook book = ready(root); book.setTemplate(NotebookPage.Template.LINED);
        NotebookStore orphan = new NotebookStore(new File(root, "pages/2"));
        orphan.load(); orphan.initializePage(100, 100, NotebookPage.Template.LINED);
        byte[] original = bytes(root, "pages/2/page.nbk");
        // Simulate a process dying after new-page sync and before book.nbi publication.
        book = reopen(root); book.addPage(); equal(2, book.pageNumber(), "exact interrupted blank adopted");
        check(Arrays.equals(original, bytes(root, "pages/2/page.nbk")), "adoption preserves already synced page bytes");
        equal(0, book.current().strokes.size(), "adopted page remains empty");
        check(book.current().template == NotebookPage.Template.LINED, "adopted paper matches original intent");
        check(!new File(root, "pages/2/page.nbk.bak").exists(), "new lined page has no intermediate blank publication");
        File interrupted = directory(); NotebookBook stopped = ready(interrupted); stopped.setTemplate(NotebookPage.Template.LINED);
        File target = new File(interrupted, "pages/2"); check(target.mkdirs(), "prepare interrupted new page directory");
        Files.write(new File(target, "page.nbk.tmp").toPath(), original);
        stopped = reopen(interrupted); stopped.addPage();
        equal(2, stopped.pageNumber(), "complete first temporary snapshot adopted after restart");
        check(stopped.current().template == NotebookPage.Template.LINED, "temporary new-page recovery retains lined paper");
        check(Arrays.equals(original, bytes(interrupted, "pages/2/page.nbk")), "temporary new-page recovery publishes exact first snapshot");
        for (int variant = 0; variant < 3; variant++) {
            File other = directory(); NotebookBook blocked = ready(other);
            NotebookDocument unexpected = new NotebookDocument(new File(other, "pages/2"));
            unexpected.load(); unexpected.initializeSize(variant == 0 ? 90 : 100, 100);
            if (variant == 1) unexpected.setTemplate(NotebookPage.Template.LINED);
            if (variant == 2) {
                byte[] bad = bytes(other, "pages/2/page.nbk"); bad[0] ^= 1;
                Files.write(new File(other, "pages/2/page.nbk").toPath(), bad);
            }
            byte[] preserved = bytes(other, "pages/2/page.nbk"); expectIo(blocked::addPage);
            check(Arrays.equals(preserved, bytes(other, "pages/2/page.nbk")), "unexpected blank orphan preserved");
            equal(1, blocked.pageCount(), "unexpected blank orphan excluded from index");
        }
    }

    private static void invalidInitialTargetCanBeRepaired() throws Exception {
        File root = directory(); NotebookBook original = ready(root); original.addPage();
        byte[] valid = bytes(root, "pages/2/page.nbk");
        // A valid-format but unsized snapshot is not a usable indexed page.
        NotebookStore replacement = new NotebookStore(new File(root, "replacement"));
        replacement.load(); replacement.setTemplate(NotebookPage.Template.LINED);
        Files.write(new File(root, "pages/2/page.nbk").toPath(), bytes(root, "replacement/page.nbk"));
        NotebookBook book = new NotebookBook(root); expectIo(book::load);
        check(book.error() != null, "unsized indexed page rejected");
        Files.write(new File(root, "pages/2/page.nbk").toPath(), valid);
        book.retry(); equal(2, book.pageNumber(), "repaired initial page selected");
        equal(100, book.current().width, "initial retry rereads repaired page dimensions");
    }

    private static void targetedViewportPagesDoNotPublishSelection() throws Exception {
        File root = directory(); NotebookBook book = ready(root); book.append(stroke(10)); book.addPage(); book.append(stroke(70));
        byte[] index = bytes(root, "book.nbi"), first = bytes(root, "page.nbk");
        book.page(1); book.page(2);
        equal(2, book.pageNumber(), "viewport reads keep selected page");
        check(Arrays.equals(index, bytes(root, "book.nbi")), "viewport reads never publish selection index");
        check(Arrays.equals(first, bytes(root, "page.nbk")), "viewport reads never rewrite page");
        book.appendToPage(1, stroke(30));
        equal(2, book.pageNumber(), "late completed stroke keeps current selection");
        equal(2, book.cachedPage(1).strokes.size(), "late completed stroke goes to its explicit page");
        equal(1, book.cachedPage(2).strokes.size(), "other page untouched");
        book.setPageTemplate(1, NotebookPage.Template.LINED);
        check(book.cachedPage(1).template == NotebookPage.Template.LINED, "paper belongs to explicit page");
        check(book.cachedPage(2).template == NotebookPage.Template.BLANK, "other paper untouched");
        book.eraseOnPagePaths(1, Arrays.asList(path(10), path(30)), 0);
        equal(0, book.cachedPage(1).strokes.size(), "disconnected erase paths applied together");
        check(book.canUndoOnPage(1) && !book.canRedoOnPage(1), "page-specific history available");
        book.undoOnPage(1); equal(2, book.cachedPage(1).strokes.size(), "one undo restores all portions of eraser gesture");
        book.redoOnPage(1); equal(0, book.cachedPage(1).strokes.size(), "one redo removes all portions");
        check(Arrays.equals(index, bytes(root, "book.nbi")), "targeted editing never publishes selection index");
        byte[] backup = bytes(root, "page.nbk.bak");
        deleteTree(new File(root, "page.nbk")); deleteTree(new File(root, "page.nbk.bak"));
        equal(0, book.cachedPage(1).strokes.size(), "cachedPage does not access missing disk files");
        expectIo(() -> book.page(1));
        check(!new File(root, "page.nbk").exists(), "missing indexed viewport page not recreated");
        Files.write(new File(root, "page.nbk.bak").toPath(), backup);
    }

    private static void failedSplitStrokeRetainsEveryPage() throws Exception {
        File root = directory(); NotebookBook book = ready(root); book.addPage(); book.page(1); book.page(2);
        block(root, "page.nbk.tmp");
        // Force page 1's append to fail by replacing its writable journal with a directory.
        byte[] first = bytes(root, "page.nbk"); check(new File(root, "page.nbk").delete(), "remove page1 journal for failure simulation");
        check(new File(root, "page.nbk").mkdir(), "block page1 journal");
        expectIo(() -> book.appendToPage(1, stroke(10)));
        IOException failure = book.error();
        book.appendToPage(2, stroke(70));
        check(book.error() == failure, "later healthy split piece does not clear earlier error");
        equal(1, book.cachedPage(1).strokes.size(), "failed piece retained in source-page memory");
        equal(1, book.cachedPage(2).strokes.size(), "other completed piece still accepted");
        equal(1, new NotebookStore(new File(root, "pages/2")).load().strokes.size(), "healthy split piece persisted");
        equal(1, book.page(1).strokes.size(), "repaint reads accepted memory while saving has failed");
        expectIo(() -> book.saveScrollPosition(2, 0.25));
        check(new File(root, "page.nbk").delete(), "remove journal obstruction");
        Files.write(new File(root, "page.nbk").toPath(), first); unblock(root, "page.nbk.tmp");
        book.retry(); check(book.error() == null, "retry clears global error after every cached page saved");
        equal(1, new NotebookStore(root).load().strokes.size(), "retry persists failed nonselected page");
        equal(1, book.cachedPage(2).strokes.size(), "retry keeps healthy page piece exactly once");
    }

    private static void scrollAnchorAndRetry() throws Exception {
        File root = directory(); NotebookBook book = ready(root); book.addPage();
        NotebookBook oldUpgrade = reopen(root);
        equal(2, oldUpgrade.scrollPosition().page, "absent scroll anchor defaults to legacy selected page");
        check(oldUpgrade.scrollPosition().fraction == 0, "legacy anchor begins at page top");
        byte[] index = bytes(root, "book.nbi");
        oldUpgrade.saveScrollPosition(1, 0.375);
        equal(2, oldUpgrade.pageNumber(), "saving scroll anchor does not select a page");
        check(Arrays.equals(index, bytes(root, "book.nbi")), "scroll never rewrites page selection index");
        NotebookBook reopened = reopen(root);
        equal(1, reopened.scrollPosition().page, "restart restores scroll page");
        check(reopened.scrollPosition().fraction == 0.375, "restart restores exact viewport fraction");
        block(root, "scroll.nbs.tmp"); expectIo(() -> reopened.saveScrollPosition(2, 0.625));
        equal(1, reopened.scrollPosition().page, "failed scroll publication keeps acknowledged position");
        unblock(root, "scroll.nbs.tmp");
        // A save may have completed even though its acknowledgement was uncertain.
        NotebookScrollStore external = new NotebookScrollStore(root); external.load(1); external.save(2, 0.625);
        reopened.retry(); equal(2, reopened.scrollPosition().page, "exact ambiguous scroll save accepted");
        check(reopened.scrollPosition().fraction == 0.625, "retry retains intended fraction");
        external.load(1); external.save(1, 0.875);
        byte[] changed = bytes(root, "scroll.nbs"); expectIo(reopened::retry);
        check(Arrays.equals(changed, bytes(root, "scroll.nbs")), "unexpected viewport position not overwritten");
    }

    private static void corruptScrollNeverResetsPosition() throws Exception {
        File root = directory(); NotebookBook original = ready(root); original.append(stroke(10)); original.saveScrollPosition(1, 0.25);
        byte[] valid = bytes(root, "scroll.nbs"), bad = valid.clone(); bad[0] ^= 1;
        Files.write(new File(root, "scroll.nbs").toPath(), bad);
        NotebookBook book = new NotebookBook(root); expectIo(book::load); expectIo(book::retry);
        check(Arrays.equals(bad, bytes(root, "scroll.nbs")), "bad scroll file preserved rather than reset");
        firstX(10, book.current());
        Files.write(new File(root, "scroll.nbs").toPath(), valid); book.retry();
        check(book.scrollPosition().fraction == 0.25, "fixed scroll position loaded on explicit retry");
        NotebookScrollStore invalidPage = new NotebookScrollStore(root); invalidPage.load(1); invalidPage.save(2, 0);
        NotebookBook missing = new NotebookBook(root); expectIo(missing::load);
        check(new NotebookScrollStore(root).load(1).page == 2, "out-of-range scroll page never reset silently");
    }

    private static void overviewPreviewsAreReadOnlyAndUncached() throws Exception {
        File root = directory(); NotebookBook original = ready(root); original.append(stroke(10));
        original.addPage(); original.setTemplate(NotebookPage.Template.LINED); original.append(stroke(20));
        original.addPage(); original.append(stroke(30)); original.selectPage(2);
        original.saveScrollPosition(1, 0.375);
        NotebookBook book = reopen(root); book.append(stroke(40)); book.undo();
        NotebookPage selected = book.current();
        Map<String, byte[]> before = treeBytes(root);
        for (int round = 0; round < 3; round++) for (int number = 1; number <= 3; number++) {
            NotebookPage preview = book.previewPage(number);
            firstX(number * 10, preview);
            equal(100, preview.width, "preview retains logical width");
            check(preview.template == (number == 1 ? NotebookPage.Template.BLANK : NotebookPage.Template.LINED),
                    "preview uses each page's paper");
        }
        check(book.previewPage(2) == selected, "cached preview shares the accepted immutable page");
        equal(2, book.pageNumber(), "overview does not select any previewed page");
        equal(3, book.pageCount(), "overview does not add pages");
        check(book.scrollPosition().page == 1 && book.scrollPosition().fraction == 0.375,
                "overview does not move the saved scroll anchor");
        check(book.canRedo(), "overview preserves selected-page redo history");
        expectIo(() -> book.cachedPage(1)); expectIo(() -> book.cachedPage(3));
        check(book.error() == null, "preview does not cache unrelated editing documents");
        sameTree(before, treeBytes(root), "overview preserves every notebook byte and path");
        book.redo(); equal(2, book.current().strokes.size(), "history still works after previewing all pages");
    }

    private static void overviewFailuresDoNotPauseTheEditor() throws Exception {
        File root = directory(); NotebookBook original = ready(root); original.append(stroke(10));
        original.addPage(); original.append(stroke(20)); original.addPage(); original.append(stroke(30));
        original.selectPage(1); NotebookBook book = reopen(root);
        byte[] bad = bytes(root, "pages/2/page.nbk"); bad[0] ^= 1;
        Files.write(new File(root, "pages/2/page.nbk").toPath(), bad);
        deleteTree(new File(root, "pages/3"));
        Map<String, byte[]> before = treeBytes(root);
        expectIo(() -> book.previewPage(2)); expectIo(() -> book.previewPage(3));
        check(book.error() == null, "bad overview pages do not become notebook save errors");
        equal(1, book.pageNumber(), "failed preview keeps selected page"); firstX(10, book.current());
        sameTree(before, treeBytes(root), "failed preview neither repairs corruption nor invents missing pages");
        book.append(stroke(50)); equal(2, book.current().strokes.size(), "current editor remains writable after failed previews");
        new NotebookStore(new File(root, "pages/3")).load();
        check(!new File(root, "pages/3").exists(), "read-only missing page load creates no directory");
        NotebookStore invalid = new NotebookStore(new File(root, "pages/3")); invalid.load(); invalid.initializeEmpty();
        before = treeBytes(root); expectIo(() -> book.previewPage(3));
        check(book.error() == null, "unsized indexed thumbnail fails without pausing editor");
        sameTree(before, treeBytes(root), "unsized thumbnail is not silently initialized");
    }

    private static void overviewRetainsDirtyAcceptedInk() throws Exception {
        File root = directory(); NotebookBook book = ready(root); book.append(stroke(10)); book.append(stroke(20));
        block(root, "page.nbk.tmp"); expectIo(book::undo); expectIo(() -> book.append(stroke(30)));
        NotebookPage accepted = book.current(); IOException failure = book.error();
        Map<String, byte[]> before = treeBytes(root);
        check(book.previewPage(1) == accepted, "dirty overview uses accepted model instead of stale durable strokes");
        equal(2, accepted.strokes.size(), "dirty overview includes completed queued ink");
        check(accepted.strokes.get(1).points.get(0).x == 30, "dirty preview retains latest failed-save stroke");
        check(book.error() == failure, "dirty preview neither clears nor replaces original save failure");
        sameTree(before, treeBytes(root), "dirty preview does not retry or rewrite a failed edit");
        unblock(root, "page.nbk.tmp"); book.retry();
        check(book.error() == null, "normal retry still works after dirty preview");
        check(new NotebookStore(root).load().strokes.get(1).points.get(0).x == 30,
                "retry saves exactly the ink shown by dirty overview");
    }

    private static void overviewIgnoresIncompleteTailWithoutRepairingIt() throws Exception {
        File root = directory(); NotebookBook original = ready(root); original.addPage(); original.append(stroke(20));
        original.selectPage(1); NotebookBook book = reopen(root);
        File journal = new File(root, "pages/2/page.nbk");
        Files.write(journal.toPath(), new byte[] {0, 0, 0}, java.nio.file.StandardOpenOption.APPEND);
        byte[] interrupted = Files.readAllBytes(journal.toPath());
        firstX(20, book.previewPage(2));
        check(Arrays.equals(interrupted, Files.readAllBytes(journal.toPath())), "preview does not truncate an incomplete journal tail");
        check(book.error() == null, "recoverable preview tail does not pause editor");
    }

    private static void overviewRequiresLoadedBookAndValidPage() throws Exception {
        File root = directory(); NotebookBook book = new NotebookBook(root);
        expectIo(() -> book.previewPage(1)); check(book.error() == null, "unloaded preview guard has no global error side effect");
        check(!new File(root, "page.nbk").exists(), "unloaded preview does not create a page");
        book.load(); expectIo(() -> book.previewPage(0)); expectIo(() -> book.previewPage(2));
        check(book.error() == null, "invalid preview numbers do not pause notebook");
        check(book.previewPage(1) == book.current(), "new unsized first page can be shown from accepted memory");
        book.initializeSize(100, 100); book.append(stroke(10));
    }

    private static Map<String, byte[]> treeBytes(File root) throws IOException {
        Map<String, byte[]> result = new java.util.TreeMap<>(); treeBytes(root, root, result); return result;
    }
    private static void treeBytes(File root, File file, Map<String, byte[]> result) throws IOException {
        if (!file.exists()) return;
        String path = root.toPath().relativize(file.toPath()).toString();
        result.put(path, file.isDirectory() ? null : Files.readAllBytes(file.toPath()));
        File[] children = file.listFiles();
        if (children != null) for (File child : children) treeBytes(root, child, result);
    }
    private static void sameTree(Map<String, byte[]> expected, Map<String, byte[]> actual, String message) {
        check(expected.keySet().equals(actual.keySet()), message + " (paths)");
        for (String path : expected.keySet()) check(Arrays.equals(expected.get(path), actual.get(path)), message + " (" + path + ")");
    }

    private static NotebookBook ready(File root) throws Exception {
        NotebookBook book = reopen(root); book.initializeSize(100, 100); return book;
    }
    private static NotebookBook reopen(File root) throws Exception {
        NotebookBook book = new NotebookBook(root); book.load(); return book;
    }
    private static NotebookPage.Stroke stroke(float x) {
        return new NotebookPage.Stroke(2, 4, 0xff000000, path(x));
    }
    private static List<NotebookPage.Point> path(float x) {
        return Collections.singletonList(new NotebookPage.Point(x, 10, 100, 1));
    }
    private static File directory() throws IOException {
        File result = Files.createTempDirectory("notebook-book-test-").toFile(); directories.add(result); return result;
    }
    private static byte[] bytes(File root, String name) throws IOException { return Files.readAllBytes(new File(root, name).toPath()); }
    private static void block(File root, String name) throws IOException {
        File file = new File(root, name); if (file.exists()) check(file.delete(), "remove old temporary file");
        check(file.mkdir(), "block temporary path");
        Files.write(new File(file, "keep").toPath(), new byte[] {1});
    }
    private static void unblock(File root, String name) { deleteTree(new File(root, name)); }
    private static void deleteTree(File file) {
        File[] children = file.listFiles(); if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }
    private static void firstX(float expected, NotebookPage page) {
        check(!page.strokes.isEmpty() && page.strokes.get(0).points.get(0).x == expected, "expected page's writing is visible");
    }
    private static void expectIo(Action action) throws Exception {
        try { action.run(); throw new AssertionError("Expected IOException"); } catch (IOException expected) { assertions++; }
    }
    private static void equal(int expected, int actual, String message) { check(expected == actual, message + " (" + actual + ")"); }
    private static void check(boolean result, String message) {
        assertions++; if (!result) throw new AssertionError(message);
    }
}
