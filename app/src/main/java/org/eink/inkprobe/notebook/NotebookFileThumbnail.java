package org.eink.inkprobe.notebook;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.pdf.PdfRenderer;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Bounded, memory-only local previews. Unsupported files keep a type placeholder. */
final class NotebookFileThumbnail extends FrameLayout {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ThreadPoolExecutor WORK = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(48), new ThreadPoolExecutor.DiscardPolicy());
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(4 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getAllocationByteCount(); }
    };
    private final ImageView image;
    private final LinearLayout placeholder;
    private String bound;

    NotebookFileThumbnail(Context context, File file, boolean directory) {
        super(context);
        setBackground(NotebookUi.rounded(getResources().getDisplayMetrics().density, 5));
        image = new ImageView(context); image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(image, new LayoutParams(-1, -1));
        placeholder = new LinearLayout(context); placeholder.setOrientation(LinearLayout.VERTICAL); placeholder.setGravity(Gravity.CENTER);
        placeholder.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        ImageView icon = new ImageView(context); icon.setImageDrawable(new NotebookIconDrawable(directory ? NotebookIconDrawable.Icon.FOLDER : NotebookIconDrawable.Icon.READING));
        placeholder.addView(icon, new LinearLayout.LayoutParams(dp(32), dp(32)));
        TextView description = new TextView(context); description.setGravity(Gravity.CENTER); description.setTextColor(0xff555555); description.setTextSize(11);
        String name = file.getName(); int dot = name.lastIndexOf('.');
        description.setText(directory ? "Folder" : dot >= 0 && name.length() - dot <= 8 ? name.substring(dot + 1).toUpperCase(Locale.ROOT) : "File");
        LinearLayout.LayoutParams textSpace = new LinearLayout.LayoutParams(-2, -2); textSpace.topMargin = dp(5); placeholder.addView(description, textSpace);
        addView(placeholder, new LayoutParams(-1, -1));
        if (directory || !file.isFile() || !file.canRead()) return;
        String lower = name.toLowerCase(Locale.ROOT);
        boolean pdf = lower.endsWith(".pdf");
        if (!pdf && !lower.matches(".*\\.(png|jpe?g|webp|gif|bmp)$")) return;
        bound = file.getAbsolutePath() + ":" + file.lastModified() + ":" + file.length();
        Bitmap cached = CACHE.get(bound);
        if (cached != null) { show(cached); return; }
        final String key = bound; WeakReference<NotebookFileThumbnail> target = new WeakReference<>(this);
        WORK.execute(() -> {
            Bitmap bitmap = decode(file, pdf);
            if (bitmap == null) return;
            MAIN.post(() -> {
                CACHE.put(key, bitmap);
                NotebookFileThumbnail view = target.get();
                if (view != null && key.equals(view.bound)) view.show(bitmap);
            });
        });
    }

    private void show(Bitmap bitmap) { image.setImageBitmap(bitmap); placeholder.setVisibility(View.GONE); }

    private static Bitmap decode(File file, boolean pdf) {
        try {
            if (file.length() > 128L * 1024 * 1024) return null;
            Bitmap source;
            if (pdf) {
                try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
                     PdfRenderer renderer = new PdfRenderer(fd)) {
                    if (renderer.getPageCount() < 1) return null;
                    try (PdfRenderer.Page page = renderer.openPage(0)) {
                        int width = 192, height = Math.max(1, Math.min(256, (int) (192L * page.getHeight() / Math.max(1, page.getWidth()))));
                        source = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                        source.eraseColor(Color.WHITE); page.render(source, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    }
                }
            } else {
                BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(file.getPath(), bounds);
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
                BitmapFactory.Options options = new BitmapFactory.Options(); options.inSampleSize = 1;
                while (Math.max(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 384) options.inSampleSize *= 2;
                source = BitmapFactory.decodeFile(file.getPath(), options);
                if (source == null) return null;
            }
            float scale = Math.min(1f, 256f / Math.max(source.getWidth(), source.getHeight()));
            int width = Math.max(1, Math.round(source.getWidth() * scale)), height = Math.max(1, Math.round(source.getHeight() * scale));
            Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            ColorMatrix matrix = new ColorMatrix(); matrix.setSaturation(0); paint.setColorFilter(new ColorMatrixColorFilter(matrix));
            Canvas canvas = new Canvas(result); canvas.drawColor(Color.WHITE);
            canvas.drawBitmap(source, null, new Rect(0, 0, width, height), paint); source.recycle();
            return result;
        } catch (Exception | OutOfMemoryError ignored) { return null; }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
