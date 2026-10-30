package org.eink.inkprobe.notebook;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;

/** Per-pane visible PDF tiles, with bounded previous-zoom pixels until the replacement is ready. */
final class NotebookMarkdownLayer {
    private static final class Key {
        final Object image; final NotebookPdfTiles.Tile tile;
        Key(Object image, NotebookPdfTiles.Tile tile) { this.image = image; this.tile = tile; }
        @Override public boolean equals(Object o) { return o instanceof Key && image.equals(((Key)o).image) && tile.equals(((Key)o).tile); }
        @Override public int hashCode() { return Objects.hash(image, tile); }
    }
    private final ExecutorService worker;
    private final NotebookFigureLayer.Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<Key, Bitmap> ready = new LinkedHashMap<>();
    private volatile Map<Key, NotebookFigure> wanted = Collections.emptyMap();
    private volatile int epoch;
    private boolean working, closed, failed;

    NotebookMarkdownLayer(ExecutorService worker, NotebookFigureLayer.Listener listener) { this.worker = worker; this.listener = listener; }
    boolean prepare(List<NotebookFigureLayer.Request> requests) {
        if (closed) return false;
        Map<Key, NotebookFigure> next = new LinkedHashMap<>();
        for (NotebookFigureLayer.Request request : requests) if (request.figure.markdown != null)
            for (NotebookPdfTiles.Tile tile : request.tiles) next.put(new Key(request.figure.imageKey, tile), request.figure);
        wanted = Collections.unmodifiableMap(next); trim(); schedule();
        return ready.keySet().containsAll(next.keySet());
    }
    private void schedule() {
        if (working || closed || failed) return;
        Key missing = null;
        for (Key key : wanted.keySet()) if (!ready.containsKey(key)) { missing = key; break; }
        if (missing == null) return;
        final Key key = missing; final NotebookFigure figure = wanted.get(key); final int request = epoch;
        working = true;
        worker.execute(() -> {
            Bitmap pixels = null; IOException error = null;
            try { if (request == epoch && wanted.containsKey(key)) pixels = NotebookMarkdownRasterizer.render(figure, key.tile); }
            catch (IOException | RuntimeException | OutOfMemoryError failure) { error = new IOException("Cannot display Markdown block", failure); }
            final Bitmap result = pixels; final IOException failure = error;
            main.post(() -> {
                working = false;
                if (closed || request != epoch || !wanted.containsKey(key)) {
                    if (result != null) result.recycle(); schedule(); return;
                }
                if (failure != null) { failed = true; listener.failed(failure); return; }
                if (result != null) ready.put(key, result);
                trim(); schedule(); if (result != null) listener.changed();
            });
        });
    }
    private void trim() {
        Set<Object> visible = new HashSet<>(); long required = 0;
        for (Key key : wanted.keySet()) { visible.add(key.image); required += key.tile.pixels(); }
        long pixels = 0; for (Bitmap b : ready.values()) pixels += (long)b.getWidth()*b.getHeight();
        boolean complete = ready.keySet().containsAll(wanted.keySet());
        Iterator<Map.Entry<Key, Bitmap>> old = ready.entrySet().iterator();
        while (old.hasNext()) {
            Map.Entry<Key, Bitmap> entry = old.next();
            if (!wanted.containsKey(entry.getKey()) && (!visible.contains(entry.getKey().image) || complete || pixels > Math.max(1, required)*2)) {
                Bitmap b = entry.getValue(); pixels -= (long)b.getWidth()*b.getHeight(); old.remove(); b.recycle();
            }
        }
    }
    void draw(Canvas canvas, NotebookFigure figure, Paint paint) {
        for (int pass = 0; pass < 2; pass++) for (Map.Entry<Key, Bitmap> entry : ready.entrySet()) {
            Key key = entry.getKey();
            if (!key.image.equals(figure.imageKey) || wanted.containsKey(key) != (pass == 1)) continue;
            NotebookPdfTiles.Tile tile = key.tile;
            canvas.drawBitmap(entry.getValue(), null, new RectF(figure.x + tile.leftIn(figure.width), figure.y + tile.topIn(figure.height),
                figure.x + tile.rightIn(figure.width), figure.y + tile.bottomIn(figure.height)), paint);
        }
    }
    long preparedBytes() { long total = 0; for (Bitmap b : ready.values()) total += b.getAllocationByteCount(); return total; }
    void clear() { ++epoch; wanted = Collections.emptyMap(); failed = false; for (Bitmap b : ready.values()) b.recycle(); ready.clear(); }
    void close() { closed = true; clear(); }
}
