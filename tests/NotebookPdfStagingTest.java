package org.eink.inkprobe.notebook;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Parallel import must finish every file writer before publication or failed-stage cleanup. */
public final class NotebookPdfStagingTest {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        File directory = Files.createTempDirectory("notebook-pdf-stage-").toFile();
        try {
            ordered(new File(directory, "ordered"));
            joinsBeforeCleanup(new File(directory, "failed"), false);
            joinsBeforeCleanup(new File(directory, "interrupted"), true);
            System.out.println("NotebookPdfStaging: " + assertions + " assertions passed.");
        } finally { NotebookBackup.deleteTree(directory); }
    }

    private static void ordered(File directory) throws Exception {
        List<NotebookViewport.Size> sizes = new ArrayList<>();
        for (int i = 0; i < 13; i++) sizes.add(new NotebookViewport.Size(700 + i, 1000 + i));
        AtomicInteger active = new AtomicInteger(), maximum = new AtomicInteger();
        List<NotebookPage> pages = NotebookPdfStaging.pages(directory, sizes, (file, width, height, page) -> {
            int running = active.incrementAndGet(); maximum.accumulateAndGet(running, Math::max);
            try { return NotebookStore.initializePdfImport(file, width, height, page); }
            finally { active.decrementAndGet(); }
        });
        check(pages.size() == sizes.size() && maximum.get() <= 4 && active.get() == 0, "At most four writers and none remain after success");
        Set<UUID> identities = new HashSet<>();
        for (int i = 0; i < pages.size(); i++) {
            NotebookPage page = pages.get(i), saved = new NotebookStore(NotebookBackup.pageDirectory(directory, i + 1)).load();
            check(page.pdfPage == i + 1 && page.width == 700 + i && page.height == 1000 + i,
                "Parallel completion preserves source page order and geometry");
            check(saved.id.equals(page.id) && identities.add(page.id), "Every returned page has its own complete durable file");
        }
    }

    private static void joinsBeforeCleanup(File directory, boolean interrupt) throws Exception {
        List<NotebookViewport.Size> sizes = Arrays.asList(new NotebookViewport.Size(700, 1000), new NotebookViewport.Size(701, 1001),
            new NotebookViewport.Size(702, 1002), new NotebookViewport.Size(703, 1003));
        CountDownLatch entered = new CountDownLatch(4), release = new CountDownLatch(1), failed = new CountDownLatch(1), returned = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicReference<Throwable> observed = new AtomicReference<>();
        AtomicBoolean interruptRestored = new AtomicBoolean();
        Thread caller = new Thread(() -> {
            try {
                NotebookPdfStaging.pages(directory, sizes, (file, width, height, page) -> {
                    active.incrementAndGet(); entered.countDown();
                    try {
                        if (!interrupt && page == 1) {
                            await(entered); failed.countDown(); throw new IOException("Injected stage failure");
                        }
                        await(release);
                        return NotebookStore.initializePdfImport(file, width, height, page);
                    } finally { active.decrementAndGet(); }
                });
                observed.set(new AssertionError("Staging should report failure/interruption"));
            } catch (IOException expected) {
                if (interrupt && !(expected instanceof InterruptedIOException)) observed.set(expected);
                interruptRestored.set(Thread.currentThread().isInterrupted());
                if (active.get() != 0) observed.set(new AssertionError("Cleanup raced an active writer"));
                // The importer deletes failed staging immediately after this method throws.
                NotebookBackup.deleteTree(directory);
            } catch (Throwable unexpected) { observed.set(unexpected); }
            finally { returned.countDown(); }
        }, "staging-test-caller");
        caller.start();
        try {
            check(entered.await(5, TimeUnit.SECONDS), "All four independent page writes entered");
            if (interrupt) caller.interrupt();
            else check(failed.await(5, TimeUnit.SECONDS), "One writer reported its injected failure");
            check(!returned.await(100, TimeUnit.MILLISECONDS), "Failure/interruption waits for blocked in-flight writers");
        } finally { release.countDown(); caller.join(5000); }
        check(!caller.isAlive() && returned.getCount() == 0, "Caller completes after every writer has joined");
        if (observed.get() != null) throw new AssertionError("Unexpected staging result", observed.get());
        check(active.get() == 0 && !directory.exists(), "Failed staging can be removed without a writer recreating files");
        if (interrupt) check(interruptRestored.get(), "Interrupted caller retains its interrupt status after joining writers");
    }

    private static void await(CountDownLatch latch) throws IOException {
        try { if (!latch.await(5, TimeUnit.SECONDS)) throw new IOException("Test worker timed out"); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new InterruptedIOException("Test worker interrupted"); }
    }
    private static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
}
