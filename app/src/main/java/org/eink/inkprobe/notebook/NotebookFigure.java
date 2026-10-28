package org.eink.inkprobe.notebook;

import java.util.Arrays;
import java.util.UUID;

/** Immutable, self-contained figure. Imports are normalized to a bounded, transparent PNG. */
public final class NotebookFigure {
    public static final int MAX_COUNT = 64, MAX_BYTES = 8 * 1024 * 1024, MAX_PAGE_BYTES = 16 * 1024 * 1024;
    public final UUID id;
    public final float x, y, width, height;
    private final byte[] png;

    public NotebookFigure(UUID id, float x, float y, float width, float height, byte[] png) {
        this(id, x, y, width, height, png, true);
    }

    private NotebookFigure(UUID id, float x, float y, float width, float height, byte[] png, boolean copy) {
        if (id == null || !NotebookPage.finite(x) || !NotebookPage.finite(y)
                || !NotebookPage.finite(width) || !NotebookPage.finite(height)
                || x < 0 || y < 0 || width <= 0 || height <= 0
                || png == null || png.length < 33 || png.length > MAX_BYTES
                || png[0] != (byte) 137 || png[1] != 80 || png[2] != 78 || png[3] != 71
                || png[4] != 13 || png[5] != 10 || png[6] != 26 || png[7] != 10)
            throw new IllegalArgumentException("Invalid figure");
        java.nio.ByteBuffer header = java.nio.ByteBuffer.wrap(png);
        int pixelsWide = header.getInt(16), pixelsHigh = header.getInt(20);
        if (header.getInt(8) != 13 || header.getInt(12) != 0x49484452 || pixelsWide < 1 || pixelsHigh < 1
                || pixelsWide > 2048 || pixelsHigh > 2048) throw new IllegalArgumentException("Invalid figure PNG dimensions");
        this.id = id; this.x = x; this.y = y; this.width = width; this.height = height;
        this.png = copy ? png.clone() : png;
    }

    public byte[] png() { return png.clone(); }
    public int byteCount() { return png.length; }
    void writeTo(java.io.DataOutputStream output) throws java.io.IOException { output.write(png); }
    java.io.InputStream open() { return new java.io.ByteArrayInputStream(png); }

    public NotebookFigure placed(float x, float y, float width, float height) {
        return new NotebookFigure(id, x, y, width, height, png, false);
    }
    NotebookFigure duplicate() { return new NotebookFigure(UUID.randomUUID(), x, y, width, height, png, false); }

    @Override public boolean equals(Object other) {
        if (!(other instanceof NotebookFigure)) return false;
        NotebookFigure f = (NotebookFigure) other;
        return id.equals(f.id) && x == f.x && y == f.y && width == f.width && height == f.height
            && Arrays.equals(png, f.png);
    }
    @Override public int hashCode() { return id.hashCode(); }
}
