package org.eink.inkprobe.notebook;

import android.content.Context;
import android.graphics.*;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Disposable, private previews of visited pages. No URL or image is fetched for a library view. */
final class NotebookBrowserPreviews {
    private static final int WIDTH = 384, HEIGHT = 288, MAX_FILE = 256 * 1024;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> MEMORY = new LruCache<String, Bitmap>(4 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap bitmap) { return bitmap.getAllocationByteCount(); }
    };
    private static final Map<NotebookBrowserPreview, String> VIEWS = new WeakHashMap<>();
    private static volatile long revision;
    private static long request, active;
    private static Runnable timeout;
    private static final class Recent {
        final String url; final long at;
        Recent(String url) { this.url = url; at = android.os.SystemClock.uptimeMillis(); }
    }
    private static final Map<NotebookBrowserEngine, Recent> RECENT = new WeakHashMap<>();

    private static String key(String url) {
        if (!NotebookBrowserAddress.isWebUrl(url)) return null;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(NotebookBrowserAddress.withoutFragment(url).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder name = new StringBuilder(); for (byte value : hash) name.append(String.format(java.util.Locale.ROOT, "%02x", value & 255)); return name.toString();
        } catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    private static File directory(Context context) throws IOException {
        File root = new File(context.getCacheDir().getCanonicalFile(), "browser-previews");
        if (!root.equals(root.getCanonicalFile()) || root.exists() && !root.isDirectory()) throw new IOException("Invalid preview cache");
        return root;
    }

    static void bind(NotebookBrowserPreview view, String url) {
        String key = key(url); VIEWS.put(view, key); view.setImageBitmap(null);
        if (key == null) return;
        Bitmap cached = MEMORY.get(key); if (cached != null) { view.setImageBitmap(cached); return; }
        long expected = revision; Context app = view.getContext().getApplicationContext();
        NotebookWebsiteTasks.run(() -> {
            File file = new File(directory(app), key + ".jpg");
            if (!file.isFile() || !file.equals(file.getCanonicalFile()) || file.length() > MAX_FILE) return null;
            BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true; BitmapFactory.decodeFile(file.getPath(), bounds);
            if (bounds.outWidth != WIDTH || bounds.outHeight != HEIGHT) return null;
            return BitmapFactory.decodeFile(file.getPath());
        }, (bitmap, error) -> {
            if (bitmap == null || expected != revision) return;
            MEMORY.put(key, bitmap);
            if (key.equals(VIEWS.get(view))) view.setImageBitmap(bitmap);
        });
    }

    static void remember(Context context, NotebookBrowserEngine page) {
        String url = page.getUrl(), key = key(url);
        if (key == null || active != 0 || NotebookBrowserData.clearing()) return;
        Recent recent = RECENT.get(page);
        if (recent != null && recent.url.equals(url) && android.os.SystemClock.uptimeMillis() - recent.at < 5000) return;
        long ticket = active = ++request, expected = revision;
        Context app = context.getApplicationContext();
        Runnable expiry = () -> { if (active == ticket) active = 0; };
        timeout = expiry; MAIN.postDelayed(expiry, 8000);
        page.capturePreview(bitmap -> {
            MAIN.removeCallbacks(expiry); if (timeout == expiry) timeout = null;
            if (active != ticket || expected != revision || !url.equals(page.getUrl())) { if (active == ticket) active = 0; if (bitmap != null) bitmap.recycle(); return; }
            if (bitmap == null) { active = 0; return; }
            Recent captured = new Recent(url); RECENT.put(page, captured);
            NotebookWebsiteTasks.run(() -> {
                try {
                    Bitmap small = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(small); canvas.drawColor(Color.WHITE);
                    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                    ColorMatrix grayscale = new ColorMatrix(); grayscale.setSaturation(0); paint.setColorFilter(new ColorMatrixColorFilter(grayscale));
                    int height = Math.min(bitmap.getHeight(), bitmap.getWidth() * 3 / 4);
                    canvas.drawBitmap(bitmap, new Rect(0, 0, bitmap.getWidth(), height), new Rect(0, 0, WIDTH, HEIGHT), paint);
                    if (expected != revision) return null;
                    File root = directory(app); if (!root.isDirectory() && !root.mkdirs()) return null;
                    File temporary = new File(root, key + ".tmp"), destination = new File(root, key + ".jpg");
                    try {
                        try (FileOutputStream output = new FileOutputStream(temporary)) { small.compress(Bitmap.CompressFormat.JPEG, 72, output); }
                        if (expected != revision || temporary.length() > MAX_FILE) return null;
                        Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                        File[] files = root.listFiles(file -> file.getName().matches("[a-f0-9]{64}\\.jpg"));
                        if (files != null) {
                            Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed()); long bytes = 0;
                            for (int i = 0; i < files.length; i++) { bytes += files[i].length(); if (i >= 160 || bytes > 16L * 1024 * 1024) Files.deleteIfExists(files[i].toPath()); }
                        }
                        return small;
                    } finally { Files.deleteIfExists(temporary.toPath()); }
                } finally { bitmap.recycle(); }
            }, (small, error) -> {
                if (active == ticket) active = 0;
                if (small == null || expected != revision) { if (RECENT.get(page) == captured) RECENT.remove(page); return; }
                MEMORY.put(key, small);
                for (Map.Entry<NotebookBrowserPreview, String> entry : new ArrayList<>(VIEWS.entrySet())) { NotebookBrowserPreview view = entry.getKey(); if (view != null && key.equals(entry.getValue())) view.setImageBitmap(small); }
            });
        });
    }

    static void documentStarted(NotebookBrowserEngine page) { RECENT.remove(page); }

    /** Called synchronously before browser owners and in-flight captures are retired. */
    static void invalidate() { revision++; active = 0; RECENT.clear(); if (timeout != null) MAIN.removeCallbacks(timeout); timeout = null; MEMORY.evictAll(); for (NotebookBrowserPreview view : new ArrayList<>(VIEWS.keySet())) view.setImageBitmap(null); }

    /** Run on website-storage, after invalidation. Only the disposable preview directory is touched. */
    static void clear(Context context) throws IOException {
        File root = directory(context); File[] files = root.listFiles();
        if (files != null) for (File file : files) if (file.getName().matches("[a-f0-9]{64}\\.(jpg|tmp)")) Files.deleteIfExists(file.toPath());
    }

    private NotebookBrowserPreviews() { }
}
