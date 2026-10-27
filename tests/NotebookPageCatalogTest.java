package org.eink.inkprobe.notebook;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

/** Large-import publication and navigation metadata must not decode every page's ink on open. */
public final class NotebookPageCatalogTest {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        File directory = Files.createTempDirectory("notebook-navigation-").toFile();
        try {
            largeImport(directory);
            checksums(new File(directory, "metadata"));
            System.out.println("NotebookPageCatalog: " + assertions + " assertions passed (600-page staging and navigation).");
        } finally { NotebookBackup.deleteTree(directory); }
    }

    private static void largeImport(File root) throws Exception {
        File stage = new File(root, "stage"); stage.mkdirs();
        try (FileOutputStream source = new FileOutputStream(new File(stage, "source.pdf"))) {
            source.write("%PDF-1.4\n".getBytes("US-ASCII")); source.getFD().sync();
        }
        long started = System.nanoTime();
        List<NotebookViewport.Size> sizes = new ArrayList<>();
        for (int number = 1; number <= 600; number++) sizes.add(new NotebookViewport.Size(900 + number % 3, 1200 + number % 5));
        List<NotebookPage> pages = NotebookPdfStaging.pages(stage, sizes);
        for (int number = 1; number <= 600; number++) {
            File dir = NotebookBackup.pageDirectory(stage, number);
            check(!new File(dir, "page.nbk.bak").exists() && !new File(dir, "page.nbk.tmp").exists(), "A staged page is written once");
        }
        NotebookIndex index = new NotebookIndex(stage); index.load(); index.initializeImported(600);
        check(index.current().pageCount == 600 && index.current().selectedPage == 1, "One publication initializes the full index");
        check(!new File(stage, "book.nbi.bak").exists(), "Import does not publish 599 intermediate indices");
        NotebookPageCatalog.seed(stage, pages);
        long staged = System.nanoTime();
        NotebookBook book = new NotebookBook(stage); book.load();
        List<NotebookPageCatalog.Entry> metadata = book.navigationMetadata();
        check(metadata.size() == 600, "All navigation metadata available");
        for (int i = 0; i < pages.size(); i++) {
            NotebookPage page = pages.get(i); NotebookPageCatalog.Entry entry = metadata.get(i);
            check(entry.id.equals(page.id) && entry.width == page.width && entry.height == page.height && entry.pdfPage == i + 1,
                "Imported metadata retains every page identity and geometry");
        }
        // A cache is enough for navigation, but it must never become a substitute for missing ink.
        File last = new File(NotebookBackup.pageDirectory(stage, 600), "page.nbk");
        File hidden = new File(last.getParentFile(), "test-hidden-page");
        check(last.renameTo(hidden), "Temporarily hide an offscreen page");
        long coldStarted = System.nanoTime();
        NotebookBook cold = new NotebookBook(stage); cold.load();
        check(cold.navigationMetadata().get(599).id.equals(pages.get(599).id), "Cold navigation uses the compact cache without opening other pages");
        long opened = System.nanoTime();
        fails(() -> cold.previewPage(600), "Cached metadata cannot replace a missing source page");
        check(hidden.renameTo(last), "Restore hidden test page");
        book.managePages(NotebookBook.PageAction.MOVE, 600, 1);
        check(book.navigationMetadata().get(0).id.equals(pages.get(599).id), "Navigation follows stable slots after reordering");
        book.managePages(NotebookBook.PageAction.DUPLICATE, 1, 0);
        check(book.navigationMetadata().size() == 601, "One new slot refreshes navigation metadata");
        check(!book.navigationMetadata().get(1).id.equals(pages.get(599).id), "Duplicate has its own navigation identity");
        fails(() -> index.initializeImported(600), "Staging initializer never overwrites an existing index");
        fails(() -> NotebookStore.initializePdfImport(stage, 900, 1200, 1), "Staging initializer never overwrites a page");
        System.out.println("Navigation host timings: 600-page stage " + millis(staged - started)
            + " ms; cold cached open " + millis(opened - coldStarted) + " ms (not tablet measurements).");
    }

    private static void checksums(File directory) throws Exception {
        directory.mkdirs();
        NotebookPage page = NotebookStore.initializePdfImport(directory, 900, 1200, 1);
        NotebookPageCatalog.Entry read = new NotebookStore(directory).metadata();
        check(read.id.equals(page.id) && read.pdfPage == 1, "Metadata-only page reader understands current snapshots");
        NotebookPageCatalog.seed(directory, Collections.singletonList(page));
        File cache = new File(directory, "navigation.cache"); byte[] bytes = Files.readAllBytes(cache.toPath());
        bytes[20] ^= 1; Files.write(cache.toPath(), bytes);
        check(NotebookPageCatalog.load(directory).isEmpty(), "Damaged navigation cache is discarded");
        check(new NotebookStore(directory).load().id.equals(page.id), "Cache damage never changes page data");
        File snapshot = new File(directory, "page.nbk"); bytes = Files.readAllBytes(snapshot.toPath());
        bytes[20] ^= 1; Files.write(snapshot.toPath(), bytes);
        fails(() -> new NotebookStore(directory).metadata(), "Metadata reader rejects snapshot checksum errors");
    }

    private interface Action { void run() throws Exception; }
    private static void fails(Action action, String message) throws Exception {
        try { action.run(); throw new AssertionError(message); }
        catch (IOException expected) { assertions++; }
    }
    private static long millis(long duration) { return duration / 1_000_000; }
    private static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
}
