package org.eink.inkprobe.notebook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Constant-memory copy into an unpublished import directory, with space checks and progress. */
final class NotebookPdfCopy {
    static final long MAX_BYTES = 2L * 1024 * 1024 * 1024;
    private static final long RESERVE_BYTES = 16L * 1024 * 1024;
    interface Progress { void copied(long bytes, long expected); }
    private NotebookPdfCopy() { }

    static void copy(InputStream input, File destination, long expected, Progress progress) throws IOException {
        if (input == null) throw new IOException("Cannot open the selected PDF");
        if (expected > MAX_BYTES) throw new IOException("PDF import supports files up to 2 GiB");
        space(destination.getParentFile(), Math.max(0, expected));
        try (FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[65536]; long total = 0, checked = 0, reported = System.nanoTime(); int size;
            while ((size = input.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("PDF import interrupted");
                if (size == 0) continue;
                if (size > MAX_BYTES - total) throw new IOException("PDF import supports files up to 2 GiB");
                total += size;
                if (total - checked >= 8L * 1024 * 1024) {
                    space(destination.getParentFile(), size); checked = total;
                }
                output.write(buffer, 0, size);
                long now = System.nanoTime();
                if (now - reported >= 1_000_000_000L) {
                    progress.copied(total, expected); reported = now;
                }
            }
            if (total == 0) throw new IOException("The selected PDF is empty");
            output.getFD().sync(); progress.copied(total, total);
        }
    }

    private static void space(File directory, long needed) throws IOException {
        long available = directory.getUsableSpace();
        if (available > 0 && needed > available - RESERVE_BYTES)
            throw new IOException("Not enough free storage to import this PDF. Free some space and try again.");
    }
}
