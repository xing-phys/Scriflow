package org.eink.inkprobe.notebook;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Bounded parallel durable writes into a private, unpublished PDF import directory. */
public final class NotebookPdfStaging {
    interface PageWriter {
        NotebookPage write(File directory, int width, int height, int sourcePage) throws IOException;
    }
    private NotebookPdfStaging() { }

    public static List<NotebookPage> pages(File directory, List<NotebookViewport.Size> sizes) throws IOException {
        return pages(directory, sizes, NotebookStore::initializePdfImport);
    }

    /** Always joins every started writer before returning, including failure and interruption. */
    static List<NotebookPage> pages(File directory, List<NotebookViewport.Size> sizes, PageWriter writer) throws IOException {
        if (directory == null || writer == null || sizes == null || sizes.isEmpty() || sizes.size() > NotebookIndex.MAX_PAGES)
            throw new IOException("Invalid PDF import pages");
        List<NotebookViewport.Size> dimensions = new ArrayList<>(sizes);
        for (NotebookViewport.Size size : dimensions)
            if (size == null || size.width > NotebookStore.MAX_DIMENSION || size.height > NotebookStore.MAX_DIMENSION)
                throw new IOException("Invalid PDF import page dimensions");
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("PDF import interrupted");
        ensureDirectory(directory);
        if (dimensions.size() > 1) ensureDirectory(new File(directory, "pages"));
        NotebookPage[] result = new NotebookPage[dimensions.size()];
        AtomicInteger next = new AtomicInteger();
        AtomicBoolean stopped = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        List<Thread> workers = new ArrayList<>(4);
        boolean interrupted = false;
        try {
            for (int worker = 0; worker < Math.min(4, dimensions.size()); worker++) {
                Thread thread = new Thread(() -> {
                    try {
                        while (!stopped.get()) {
                            int index = next.getAndIncrement();
                            if (index >= dimensions.size()) return;
                            NotebookViewport.Size size = dimensions.get(index);
                            result[index] = writer.write(NotebookBackup.pageDirectory(directory, index + 1),
                                size.width, size.height, index + 1);
                            if (result[index] == null) throw new IOException("Missing staged PDF page");
                        }
                    } catch (Throwable problem) {
                        failure.compareAndSet(null, problem); stopped.set(true);
                    }
                }, "notebook-pdf-stage-" + (worker + 1));
                workers.add(thread); thread.start();
            }
        } catch (Throwable problem) {
            failure.compareAndSet(null, problem); stopped.set(true);
        } finally {
            // Do not cancel/interrupt a file write: cleanup must wait for its real completion.
            for (Thread thread : workers) {
                boolean joined = false;
                while (!joined) try { thread.join(); joined = true; }
                catch (InterruptedException problem) {
                    interrupted = true; stopped.set(true);
                    failure.compareAndSet(null, new InterruptedIOException("PDF import interrupted"));
                }
            }
            if (interrupted) Thread.currentThread().interrupt();
        }
        if (Thread.currentThread().isInterrupted())
            failure.compareAndSet(null, new InterruptedIOException("PDF import interrupted"));
        Throwable problem = failure.get();
        if (problem instanceof Error) throw (Error) problem;
        if (problem instanceof IOException) throw (IOException) problem;
        if (problem != null) throw new IOException("Could not stage PDF pages", problem);
        return Collections.unmodifiableList(Arrays.asList(result));
    }

    private static void ensureDirectory(File directory) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory())
            throw new IOException("Cannot create staged PDF page directory");
    }
}
