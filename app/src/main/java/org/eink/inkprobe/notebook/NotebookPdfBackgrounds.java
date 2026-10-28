package org.eink.inkprobe.notebook;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Main-thread bitmap cache with one worker operation in flight and no queue of obsolete zoom/scroll requests. */
final class NotebookPdfBackgrounds {
    interface Listener {
        void changed();
        void failed(IOException error);
    }
    private static final long CACHE_PIXELS = 6_000_000;
    private final Context context;
    private final boolean loadLinks;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> new Thread(r, "notebook-pdf-display"));
    private final Map<NotebookPdfTiles.Tile, Bitmap> cache = new LinkedHashMap<>();
    private final Map<Integer, List<NotebookPdfAsset.Link>> links = new LinkedHashMap<>();
    private Set<NotebookPdfTiles.Tile> wanted = Collections.emptySet();
    private Set<Integer> pages = Collections.emptySet();
    private File source;
    private volatile int epoch;
    private boolean working, closed, failed;
    // These two fields belong exclusively to worker.
    private NotebookPdfDisplayRenderer renderer;
    private File rendererSource;

    NotebookPdfBackgrounds(Context context, Listener listener) {
        this(context, listener, true);
    }

    NotebookPdfBackgrounds(Context context, Listener listener, boolean loadLinks) {
        this.context = context.getApplicationContext(); this.listener = listener; this.loadLinks = loadLinks;
    }

    boolean prepare(File source, List<NotebookPdfTiles.Tile> visible, Set<Integer> visiblePages) {
        if (closed) return false;
        if (this.source == null ? source != null : !this.source.equals(source)) {
            clear(); this.source = source;
        }
        wanted = new LinkedHashSet<>(visible); pages = new LinkedHashSet<>(visiblePages);
        trim(); schedule();
        for (NotebookPdfTiles.Tile tile : wanted) if (!cache.containsKey(tile)) return false;
        return true;
    }

    List<NotebookPdfAsset.Link> links(int page) {
        List<NotebookPdfAsset.Link> result = links.get(page);
        return result == null ? Collections.emptyList() : result;
    }

    /** Drawing only consumes completed pixels; native rendering and link parsing never run in pen callbacks. */
    boolean draw(Canvas canvas, int page, int width, int height, Paint paint) {
        boolean drawn = false;
        // Retain a bounded fallback while a pinch asks for a sharper resolution, then paint exact tiles above it.
        for (int pass = 0; pass < 2; pass++) {
            for (Map.Entry<NotebookPdfTiles.Tile, Bitmap> item : cache.entrySet()) {
                NotebookPdfTiles.Tile tile = item.getKey();
                if (tile.page != page || wanted.contains(tile) != (pass == 1)) continue;
                canvas.drawBitmap(item.getValue(), null, new RectF(tile.leftIn(width), tile.topIn(height),
                    tile.rightIn(width), tile.bottomIn(height)), paint);
                drawn = true;
            }
        }
        return drawn;
    }

    private void schedule() {
        if (working || closed || failed || source == null) return;
        NotebookPdfTiles.Tile missing = null;
        for (NotebookPdfTiles.Tile tile : wanted) if (!cache.containsKey(tile)) { missing = tile; break; }
        int linkPage = 0;
        if (missing == null && loadLinks) for (int page : pages) if (!links.containsKey(page)) { linkPage = page; break; }
        if (missing == null && linkPage == 0) return;
        final NotebookPdfTiles.Tile tile = missing;
        final int page = linkPage, request = epoch;
        final File file = source;
        working = true;
        worker.execute(() -> {
            Bitmap bitmap = null;
            List<NotebookPdfAsset.Link> resultLinks = null;
            IOException failure = null;
            try {
                if (request == epoch) {
                    if (tile != null) {
                        if (renderer == null || !file.equals(rendererSource)) {
                            closeRenderer(); renderer = new NotebookPdfDisplayRenderer(file); rendererSource = file;
                        }
                        bitmap = renderer.render(tile);
                    } else resultLinks = NotebookPdfEngine.links(context, file, page);
                }
            } catch (IOException | RuntimeException error) {
                failure = new IOException("Could not display source PDF page " + (tile == null ? page : tile.page), error);
            }
            final Bitmap result = bitmap;
            final List<NotebookPdfAsset.Link> pageLinks = resultLinks;
            final IOException error = failure;
            main.post(() -> {
                working = false;
                if (closed || request != epoch) {
                    if (result != null) result.recycle();
                    schedule(); return;
                }
                if (error != null) {
                    if (result != null) result.recycle();
                    failed = true; listener.failed(error); return;
                }
                boolean changed = false;
                if (result != null) {
                    if (wanted.contains(tile)) { cache.put(tile, result); changed = true; }
                    else result.recycle();
                }
                if (pageLinks != null && pages.contains(page)) links.put(page, pageLinks);
                trim(); schedule();
                if (changed) listener.changed();
            });
        });
    }

    private void trim() {
        long pixels = 0, required = 0;
        for (NotebookPdfTiles.Tile tile : cache.keySet()) pixels += tile.pixels();
        for (NotebookPdfTiles.Tile tile : wanted) required += tile.pixels();
        long budget = Math.max(CACHE_PIXELS, required);
        Iterator<Map.Entry<NotebookPdfTiles.Tile, Bitmap>> old = cache.entrySet().iterator();
        while (old.hasNext()) {
            Map.Entry<NotebookPdfTiles.Tile, Bitmap> item = old.next();
            if (!wanted.contains(item.getKey()) && (!pages.contains(item.getKey().page) || pixels > budget)) {
                pixels -= item.getKey().pixels(); item.getValue().recycle(); old.remove();
            }
        }
        links.keySet().retainAll(pages);
    }

    void clear() {
        ++epoch; wanted = Collections.emptySet(); pages = Collections.emptySet(); failed = false;
        for (Bitmap bitmap : cache.values()) bitmap.recycle();
        cache.clear(); links.clear();
    }

    void close() {
        closed = true; clear();
        worker.execute(() -> { closeRenderer(); NotebookPdfEngine.closeNavigation(); });
        worker.shutdown();
    }

    private void closeRenderer() {
        if (renderer != null) try { renderer.close(); } catch (IOException ignored) { }
        renderer = null; rendererSource = null;
    }
}
