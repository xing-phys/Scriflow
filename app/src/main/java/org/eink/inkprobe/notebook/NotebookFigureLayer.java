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
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Main-thread visible figure layer. PNG decode runs on a worker, never from drawing or pen callbacks. */
final class NotebookFigureLayer {
    interface Listener { void changed(); void failed(IOException error); }
    static final class Request {
        final NotebookFigure figure; final int sample;
        Request(NotebookFigure figure, double width, double height) {
            this.figure = figure; sample = figure.sampleForPixels(width, height);
        }
    }
    private static final class Pixels {
        final Bitmap bitmap; final int sample;
        Pixels(Bitmap bitmap, int sample) { this.bitmap = bitmap; this.sample = sample; }
    }
    private static final ExecutorService decoder = new ThreadPoolExecutor(0, 1, 30, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(), r -> new Thread(r, "notebook-figure-display"));
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF box = new RectF();
    private final Listener listener;
    private final Map<Object, Pixels> ready = new LinkedHashMap<>();
    private volatile Map<Object, Request> wanted = Collections.emptyMap();
    private volatile int epoch;
    private boolean working, failed, closed;
    NotebookFigureLayer(Listener listener) { this.listener = listener; }

    static List<Request> visible(NotebookViewport viewport, Map<Integer, NotebookPage> pages, float scroll) {
        List<Request> result = new ArrayList<>();
        for (int number : viewport.visiblePages(scroll)) {
            NotebookPage page = pages.get(number); if (page == null) continue;
            float scale = viewport.scale(number), left = viewport.pageLeft(number), top = viewport.pageTop(number, scroll);
            for (NotebookFigure f : page.figures) {
                if (left + (f.x + f.width) * scale <= 0 || top + (f.y + f.height) * scale <= 0
                        || left + f.x * scale >= viewport.width || top + f.y * scale >= viewport.height) continue;
                result.add(new Request(f, f.width * (double) scale, f.height * (double) scale));
            }
        }
        return result;
    }
    boolean prepare(List<Request> requests) {
        if (closed) return false;
        Map<Object, Request> next = new LinkedHashMap<>();
        for (Request request : requests) {
            Object key = request.figure.imageKey; Request old = next.get(key);
            if (old == null || request.sample < old.sample) next.put(key, request);
        }
        wanted = Collections.unmodifiableMap(next); ready.keySet().retainAll(next.keySet()); schedule();
        for (Map.Entry<Object, Request> item : next.entrySet()) {
            Pixels pixels = ready.get(item.getKey());
            if (pixels == null || pixels.sample != item.getValue().sample) return false;
        }
        return true;
    }
    private void schedule() {
        if (working || failed || closed) return;
        Request missing = null;
        for (Map.Entry<Object, Request> item : wanted.entrySet()) {
            Pixels pixels = ready.get(item.getKey());
            if (pixels == null || pixels.sample != item.getValue().sample) { missing = item.getValue(); break; }
        }
        if (missing == null) return;
        final Request request = missing; final Object key = request.figure.imageKey; final int generation = epoch;
        working = true;
        try { decoder.execute(() -> {
            Bitmap bitmap = null; IOException failure = null;
            try {
                Request latest = wanted.get(key);
                if (generation == epoch && latest != null && latest.sample == request.sample)
                    bitmap = NotebookImages.bitmap(request.figure, request.sample);
            } catch (IOException | RuntimeException | OutOfMemoryError error) { failure = new IOException("Could not display the figure", error); }
            final Bitmap result = bitmap; final IOException error = failure;
            main.post(() -> {
                working = false;
                if (closed || generation != epoch) { schedule(); return; }
                Request current = wanted.get(key);
                if (current == null || current.sample != request.sample) { schedule(); return; }
                if (error != null) { failed = true; listener.failed(error); return; }
                if (result != null) ready.put(key, new Pixels(result, request.sample));
                schedule(); if (result != null) listener.changed();
            });
        }); } catch (RuntimeException | OutOfMemoryError error) {
            working = false; failed = true; listener.failed(new IOException("Could not load the figure", error));
        }
    }
    /** Prepared exact-resolution pixels or the preceding zoom's fallback; no decoding. */
    void draw(Canvas canvas, NotebookPage page) { for (NotebookFigure figure : page.figures) draw(canvas, figure); }
    void draw(Canvas canvas, NotebookFigure figure) {
        Pixels pixels = ready.get(figure.imageKey);
        box.set(figure.x, figure.y, figure.x + figure.width, figure.y + figure.height);
        if (pixels != null) canvas.drawBitmap(pixels.bitmap, null, box, paint);
        else if (wanted.containsKey(figure.imageKey)) {
            paint.setColor(0xff555555); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1); canvas.drawRect(box, paint);
            paint.setStyle(Paint.Style.FILL); paint.setTextSize(Math.min(16, figure.height / 4));
            canvas.drawText(failed ? "Figure unavailable" : "Loading figure…", figure.x + 2, figure.y + Math.min(18, figure.height / 2), paint);
        }
    }
    long preparedBytes() { long bytes = 0; for (Pixels p : ready.values()) bytes += p.bitmap.getAllocationByteCount(); return bytes; }
    void clear() { ++epoch; wanted = Collections.emptyMap(); ready.clear(); failed = false; }
    void close() { closed = true; clear(); }
}
