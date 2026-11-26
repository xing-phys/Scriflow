package org.eink.inkprobe.notebook;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;

/** Bounded-buffer transfer; the caller owns both streams and destination publication. */
final class NotebookDownloadStream {
    interface Control { boolean cancelled(); void progress(long bytes); }
    private NotebookDownloadStream() { }

    static long copy(InputStream input, OutputStream output, long expected, Control control) throws IOException {
        byte[] buffer = new byte[64 * 1024]; long total = 0;
        while (true) {
            if (control.cancelled()) throw new InterruptedIOException("Download canceled.");
            int count = input.read(buffer);
            if (control.cancelled()) throw new InterruptedIOException("Download canceled.");
            if (count < 0) break;
            if (count == 0) continue;
            if (total > Long.MAX_VALUE - count) throw new IOException("Download is too large.");
            output.write(buffer, 0, count); total += count; control.progress(total);
        }
        if (expected >= 0 && total != expected) throw new IOException("The download ended before the expected size was received.");
        output.flush(); return total;
    }
}
