package org.eink.inkprobe.notebook;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Worker-only native vector rasterization. No WebView, parsing or file work enters a pen callback. */
final class NotebookMarkdownRasterizer {
    private static volatile File directory;
    private static final LinkedHashMap<Object, Reader> readers = new LinkedHashMap<>(4, .75f, true);
    private static final ScheduledExecutorService maintenance = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "markdown-idle-release"); t.setDaemon(true); return t;
    });
    private static ScheduledFuture<?> idle;
    private static long lastAccess;

    static void configure(File cache) { directory = new File(cache, "markdown-vectors"); }

    static void validate(NotebookMarkdown block) throws IOException {
        File root = directory;
        if (root == null || !root.isDirectory() && !root.mkdirs()) throw new IOException("Cannot prepare Markdown cache");
        for (boolean source : new boolean[]{false, true}) {
            File file = File.createTempFile("check-", ".pdf", root);
            try {
                try (FileOutputStream out = new FileOutputStream(file)) { out.write(block.asSource(source).appearance()); }
                Reader reader = new Reader(file);
                try {
                    float width = block.asSource(source).width() * .75f, height = block.asSource(source).height() * .75f;
                    if (Math.abs(reader.page.getWidth()-width) > 2 || Math.abs(reader.page.getHeight()-height) > 2)
                        throw new IOException("Markdown vector dimensions changed");
                } finally { reader.close(); }
            } finally { file.delete(); }
        }
    }

    private static Reader reader(NotebookFigure figure) throws IOException {
        if (figure.markdown == null) throw new IOException("No Markdown appearance");
        Reader result = readers.get(figure.imageKey);
        if (result == null) {
            File root = directory;
            if (root == null || !root.isDirectory() && !root.mkdirs()) throw new IOException("Cannot prepare Markdown cache");
            File file = File.createTempFile("block-", ".pdf", root);
            try {
                try (FileOutputStream out = new FileOutputStream(file)) { out.write(figure.markdown.appearance()); }
                result = new Reader(file); readers.put(figure.imageKey, result);
            } finally { file.delete(); } // Native descriptor remains seekable; no orphaned plaintext asset remains.
            while (readers.size() > 3) {
                Iterator<Reader> old = readers.values().iterator(); Reader first = old.next(); old.remove(); first.close();
            }
        }
        lastAccess = System.nanoTime();
        if (idle == null) idle = maintenance.schedule(NotebookMarkdownRasterizer::releaseIdle, 30, TimeUnit.SECONDS);
        return result;
    }

    static synchronized Bitmap render(NotebookFigure figure, NotebookPdfTiles.Tile tile) throws IOException {
        return reader(figure).render(tile);
    }
    static Bitmap bitmap(NotebookFigure figure, int width, int height) throws IOException {
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        result.setDensity(Bitmap.DENSITY_NONE);
        try {
            Canvas drawing = new Canvas(result);
            for (NotebookPdfTiles.Tile tile : NotebookPdfTiles.visible(1, width, height, 0, 0, width, height)) {
                Bitmap part = render(figure, tile);
                try { drawing.drawBitmap(part, tile.left, tile.top, null); } finally { part.recycle(); }
            }
            return result;
        } catch (IOException | RuntimeException | Error error) { result.recycle(); throw error; }
    }
    private static synchronized void releaseIdle() {
        idle = null;
        if (System.nanoTime() - lastAccess >= TimeUnit.SECONDS.toNanos(30)) trim();
        else idle = maintenance.schedule(NotebookMarkdownRasterizer::releaseIdle, 30, TimeUnit.SECONDS);
    }
    static synchronized void trim() {
        for (Reader reader : readers.values()) reader.close(); readers.clear();
        if (idle != null) idle.cancel(false); idle = null;
    }

    private static final class Reader {
        final ParcelFileDescriptor descriptor;
        final PdfRenderer renderer;
        final PdfRenderer.Page page;
        Reader(File file) throws IOException {
            descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            PdfRenderer opened = null;
            PdfRenderer.Page viewed = null;
            try {
                opened = new PdfRenderer(descriptor);
                if (opened.getPageCount() != 1) throw new IOException("Markdown appearance must have one page");
                viewed = opened.openPage(0);
                if (viewed.getWidth() < 1 || viewed.getHeight() < 1 || viewed.getWidth() > 32768 || viewed.getHeight() > 32768)
                    throw new IOException("Invalid Markdown appearance dimensions");
                page = viewed; renderer = opened;
            } catch (IOException | RuntimeException | Error error) {
                if (viewed != null) viewed.close();
                if (opened != null) opened.close(); descriptor.close(); throw error;
            }
        }
        Bitmap render(NotebookPdfTiles.Tile tile) {
            Bitmap bitmap = Bitmap.createBitmap(tile.width, tile.height, Bitmap.Config.ARGB_8888);
            bitmap.setDensity(Bitmap.DENSITY_NONE);
            try {
                bitmap.eraseColor(Color.TRANSPARENT);
                Matrix transform = new Matrix();
                transform.setValues(new float[]{(float) tile.fullWidth / page.getWidth(), 0, -tile.left,
                    0, (float) tile.fullHeight / page.getHeight(), -tile.top, 0, 0, 1});
                // DISPLAY enables LCD subpixel text in PDFium. Those glyph masks can
                // become patterned when software-composited into the e-ink paper,
                // although the same tile looks clean in the hardware drag overlay.
                // Our generated appearances have no print-only/view-only content;
                // PRINT keeps grayscale text antialiasing in both display paths.
                page.render(bitmap, null, transform, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
                return bitmap;
            } catch (RuntimeException | Error error) { bitmap.recycle(); throw error; }
        }
        void close() {
            try { page.close(); } finally {
                try { renderer.close(); } finally { try { descriptor.close(); } catch (IOException ignored) { } }
            }
        }
    }
}
