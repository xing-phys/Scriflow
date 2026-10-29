package org.eink.inkprobe.notebook;

import java.util.Arrays;
import java.util.UUID;

/** Immutable, self-contained figure. Imports are normalized to a bounded, transparent PNG. */
public final class NotebookFigure {
    public static final int MAX_COUNT = 64, MAX_BYTES = 8 * 1024 * 1024, MAX_PAGE_BYTES = 16 * 1024 * 1024;
    public final UUID id;
    public final float x, y, width, height, angle;
    private final byte[] png;
    private final float cos, sin;
    final int pixelsWide, pixelsHigh;
    final Object imageKey;

    private static final class ImageKey {
        private final byte[] digest; private final int hash;
        ImageKey(byte[] png) {
            try { digest = java.security.MessageDigest.getInstance("SHA-256").digest(png); }
            catch (java.security.NoSuchAlgorithmException unavailable) { throw new IllegalStateException(unavailable); }
            hash = Arrays.hashCode(digest);
        }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) { return other instanceof ImageKey && Arrays.equals(digest, ((ImageKey) other).digest); }
    }

    public NotebookFigure(UUID id, float x, float y, float width, float height, byte[] png) {
        this(id, x, y, width, height, 0, png, true, null);
    }

    public NotebookFigure(UUID id, float x, float y, float width, float height, float angle, byte[] png) {
        this(id, x, y, width, height, angle, png, true, null);
    }

    private NotebookFigure(UUID id, float x, float y, float width, float height, float angle, byte[] png, boolean copy, Object imageKey) {
        if (id == null || !NotebookPage.finite(x) || !NotebookPage.finite(y)
                || !NotebookPage.finite(width) || !NotebookPage.finite(height)
                || !NotebookPage.finite(angle) || width <= 0 || height <= 0
                || png == null || png.length < 33 || png.length > MAX_BYTES
                || png[0] != (byte) 137 || png[1] != 80 || png[2] != 78 || png[3] != 71
                || png[4] != 13 || png[5] != 10 || png[6] != 26 || png[7] != 10)
            throw new IllegalArgumentException("Invalid figure");
        java.nio.ByteBuffer header = java.nio.ByteBuffer.wrap(png);
        int pixelsWide = header.getInt(16), pixelsHigh = header.getInt(20);
        if (header.getInt(8) != 13 || header.getInt(12) != 0x49484452 || pixelsWide < 1 || pixelsHigh < 1
                || pixelsWide > 2048 || pixelsHigh > 2048) throw new IllegalArgumentException("Invalid figure PNG dimensions");
        this.id = id; this.x = x; this.y = y; this.width = width; this.height = height;
        this.angle = (angle % 360 + 540) % 360 - 180;
        cos = this.angle == 0 ? 1 : (float) Math.cos(Math.toRadians(this.angle));
        sin = this.angle == 0 ? 0 : (float) Math.sin(Math.toRadians(this.angle));
        this.pixelsWide = pixelsWide; this.pixelsHigh = pixelsHigh;
        this.png = copy ? png.clone() : png;
        this.imageKey = imageKey == null ? new ImageKey(this.png) : imageKey;
    }

    public byte[] png() { return png.clone(); }
    public int byteCount() { return png.length; }
    void writeTo(java.io.DataOutputStream output) throws java.io.IOException { output.write(png); }
    java.io.InputStream open() { return new java.io.ByteArrayInputStream(png); }

    public NotebookFigure placed(float x, float y, float width, float height) {
        return new NotebookFigure(id, x, y, width, height, angle, png, false, imageKey);
    }
    public NotebookFigure turned(float degrees) { return new NotebookFigure(id, x, y, width, height, degrees, png, false, imageKey); }
    NotebookFigure duplicate() { return new NotebookFigure(UUID.randomUUID(), x, y, width, height, angle, png, false, imageKey); }
    public float centerX() { return x + width / 2; }
    public float centerY() { return y + height / 2; }
    public float cosine() { return cos; }
    public float sine() { return sin; }
    public float boundsWidth() { return Math.abs(width * cosine()) + Math.abs(height * sine()); }
    public float boundsHeight() { return Math.abs(width * sine()) + Math.abs(height * cosine()); }
    public float left() { return centerX() - boundsWidth() / 2; }
    public float top() { return centerY() - boundsHeight() / 2; }
    public float right() { return centerX() + boundsWidth() / 2; }
    public float bottom() { return centerY() + boundsHeight() / 2; }
    public float cornerX(int corner) {
        float dx = (corner == 0 || corner == 3 ? -1 : 1) * width / 2;
        float dy = (corner < 2 ? -1 : 1) * height / 2;
        return centerX() + dx * cosine() - dy * sine();
    }
    public float cornerY(int corner) {
        float dx = (corner == 0 || corner == 3 ? -1 : 1) * width / 2;
        float dy = (corner < 2 ? -1 : 1) * height / 2;
        return centerY() + dx * sine() + dy * cosine();
    }
    public boolean contains(float px, float py) {
        float dx = px - centerX(), dy = py - centerY();
        return Math.abs(dx * cosine() + dy * sine()) <= width / 2
            && Math.abs(-dx * sine() + dy * cosine()) <= height / 2;
    }
    /** Retain orientation and source pixels; fit the complete rotated envelope inside the page. */
    public NotebookFigure fitted(float pageWidth, float pageHeight) {
        float factor = Math.min(1, Math.min(pageWidth / boundsWidth(), pageHeight / boundsHeight()));
        float w = width * factor, h = height * factor, bx = boundsWidth() * factor / 2, by = boundsHeight() * factor / 2;
        float cx = Math.max(bx, Math.min(pageWidth - bx, centerX()));
        float cy = Math.max(by, Math.min(pageHeight - by, centerY()));
        return placed(cx - w / 2, cy - h / 2, w, h);
    }

    /** Decode no fewer source pixels than the figure occupies on screen, up to the original resolution. */
    int sampleForPixels(double width, double height) {
        if (!Double.isFinite(width) || !Double.isFinite(height) || width <= 0 || height <= 0)
            throw new IllegalArgumentException("Invalid figure display size");
        int sample = 1;
        while (sample < 2048 && pixelsWide / (sample * 2) >= Math.ceil(width)
                && pixelsHigh / (sample * 2) >= Math.ceil(height)) sample *= 2;
        return sample;
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof NotebookFigure)) return false;
        NotebookFigure f = (NotebookFigure) other;
        return id.equals(f.id) && x == f.x && y == f.y && width == f.width && height == f.height && angle == f.angle
            && Arrays.equals(png, f.png);
    }
    @Override public int hashCode() { return id.hashCode(); }
}
