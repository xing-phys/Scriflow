package org.eink.inkprobe.notebook;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;

/** Worker-owned native PDF session. A page is rasterized directly into a viewport tile at the current zoom. */
final class NotebookPdfDisplayRenderer implements Closeable {
    private final ParcelFileDescriptor descriptor;
    private final PdfRenderer renderer;
    private PdfRenderer.Page current;
    private int currentPage;

    NotebookPdfDisplayRenderer(File source) throws IOException {
        descriptor = ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY);
        try { renderer = new PdfRenderer(descriptor); }
        catch (IOException | RuntimeException failure) { descriptor.close(); throw failure; }
    }

    Bitmap render(NotebookPdfTiles.Tile tile) throws IOException {
        if (tile.page > renderer.getPageCount()) throw new IOException("The PDF page is unavailable");
        try {
            if (currentPage != tile.page) {
                if (current != null) { current.close(); current = null; currentPage = 0; }
                current = renderer.openPage(tile.page - 1); currentPage = tile.page;
            }
            PdfRenderer.Page page = current;
            Bitmap bitmap = Bitmap.createBitmap(tile.width, tile.height, Bitmap.Config.ARGB_8888);
            try {
                bitmap.eraseColor(Color.WHITE);
                Matrix transform = new Matrix();
                transform.setValues(new float[]{(float) tile.fullWidth / page.getWidth(), 0, -tile.left,
                    0, (float) tile.fullHeight / page.getHeight(), -tile.top, 0, 0, 1});
                page.render(bitmap, null, transform, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                return bitmap;
            } catch (RuntimeException | Error failure) { bitmap.recycle(); throw failure; }
        } catch (IllegalArgumentException | SecurityException failure) {
            throw new IOException("Could not render the PDF page", failure);
        }
    }

    @Override public void close() throws IOException {
        try {
            if (current != null) { current.close(); current = null; currentPage = 0; }
            renderer.close();
        } finally { descriptor.close(); }
    }
}
