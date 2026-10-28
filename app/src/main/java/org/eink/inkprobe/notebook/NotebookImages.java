package org.eink.inkprobe.notebook;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageDecoder;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.LruCache;
import com.caverock.androidsvg.SVG;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

/** Bounded image decoding off the ink path. No external SVG resolver or network access. */
final class NotebookImages {
    static final int MAX_EDGE = 2048, MAX_INPUT = 32 * 1024 * 1024;
    static final class Imported {
        final byte[] png; final int width, height;
        Imported(byte[] png, int width, int height) { this.png = png; this.width = width; this.height = height; }
        NotebookFigure place(NotebookPage page) {
            float scale = Math.min(page.width * .7f / width, page.height * .7f / height);
            float w = width * scale, h = height * scale;
            return new NotebookFigure(UUID.randomUUID(), (page.width - w) / 2, (page.height - h) / 2, w, h, png);
        }
    }
    private static final LruCache<UUID, Bitmap> cache = new LruCache<UUID, Bitmap>(24 * 1024 * 1024) {
        @Override protected int sizeOf(UUID id, Bitmap bitmap) { return bitmap.getAllocationByteCount(); }
        // Display lists may still reference an evicted bitmap; allow GC to reclaim it.
    };
    static Imported read(InputStream input) throws IOException {
        byte[] bytes = readBounded(input, MAX_INPUT);
        if (bytes.length > 2 && bytes[0] == 31 && bytes[1] == (byte) 139)
            bytes = readBounded(new GZIPInputStream(new ByteArrayInputStream(bytes)), MAX_INPUT);
        String header = new String(bytes, 0, Math.min(4096, bytes.length), StandardCharsets.UTF_8).trim();
        Bitmap bitmap;
        try {
            if (header.contains("<svg") || header.startsWith("<?xml")) {
                SVG.setInternalEntitiesEnabled(false);
                SVG svg = SVG.getFromInputStream(new ByteArrayInputStream(bytes));
                float w = svg.getDocumentWidth(), h = svg.getDocumentHeight();
                RectF box = svg.getDocumentViewBox();
                if (w <= 0 || h <= 0) { w = box == null ? 1024 : box.width(); h = box == null ? 1024 : box.height(); }
                if (!NotebookPage.finite(w) || !NotebookPage.finite(h) || w <= 0 || h <= 0)
                    throw new IOException("Invalid SVG dimensions");
                float scale = Math.min(1, MAX_EDGE / Math.max(w, h));
                int width = Math.max(1, Math.round(w * scale)), height = Math.max(1, Math.round(h * scale));
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                svg.setDocumentWidth("100%"); svg.setDocumentHeight("100%");
                svg.renderToCanvas(new Canvas(bitmap), new RectF(0, 0, width, height));
            } else {
                bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes)), (decoder, info, source) -> {
                    int w = info.getSize().getWidth(), h = info.getSize().getHeight();
                    float scale = Math.min(1, MAX_EDGE / (float) Math.max(w, h));
                    decoder.setTargetSize(Math.max(1, Math.round(w * scale)), Math.max(1, Math.round(h * scale)));
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                });
            }
        } catch (Exception error) { throw new IOException("Cannot read this image. Choose SVG, PNG, JPEG, WebP, GIF, BMP, or another image supported by this tablet.", error); }
        try {
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, png)) throw new IOException("Cannot prepare the image");
            if (png.size() > NotebookFigure.MAX_BYTES) throw new IOException("The image is too large after decoding (8 MiB maximum)");
            return new Imported(png.toByteArray(), bitmap.getWidth(), bitmap.getHeight());
        } finally { bitmap.recycle(); }
    }
    private static byte[] readBounded(InputStream input, int limit) throws IOException {
        if (input == null) throw new IOException("Cannot open the image");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); byte[] buffer = new byte[16384]; int count;
        while ((count = input.read(buffer)) != -1) {
            if (bytes.size() > limit - count) throw new IOException("Image exceeds the 32 MiB import limit");
            bytes.write(buffer, 0, count);
        }
        return bytes.toByteArray();
    }
    static Bitmap bitmap(NotebookFigure figure) throws IOException {
        Bitmap bitmap = cache.get(figure.id);
        if (bitmap != null) return bitmap;
        BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(figure.open(), null, bounds);
        if (bounds.outWidth < 1 || bounds.outHeight < 1 || bounds.outWidth > MAX_EDGE || bounds.outHeight > MAX_EDGE)
            throw new IOException("Invalid saved figure dimensions");
        bitmap = BitmapFactory.decodeStream(figure.open());
        if (bitmap == null) throw new IOException("Cannot decode the saved figure");
        bitmap.setDensity(Bitmap.DENSITY_NONE); cache.put(figure.id, bitmap); return bitmap;
    }
    static void draw(Canvas canvas, NotebookPage page) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        for (NotebookFigure figure : page.figures) {
            try { canvas.drawBitmap(bitmap(figure), null,
                new RectF(figure.x, figure.y, figure.x + figure.width, figure.y + figure.height), paint); }
            catch (IOException error) { throw new IllegalStateException(error.getMessage(), error); }
        }
    }
}
