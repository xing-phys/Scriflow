package org.eink.inkprobe.notebook;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/** Explicit note copies also become ordinary Android text or PNG clipboard items. */
final class NotebookClipboard {
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static long revision;
    interface Image { NotebookAiAttachment render() throws IOException; }

    static void ink(Context context, List<NotebookPage.Stroke> strokes) {
        List<NotebookPage.Stroke> snapshot = new ArrayList<>(strokes);
        image(context, () -> NotebookAiSelection.ink(snapshot));
    }
    static void figure(Context context, NotebookFigure figure) {
        if (figure.markdown != null) {
            revision++;
            manager(context).setPrimaryClip(ClipData.newPlainText("Markdown selection", figure.markdown.source));
        } else image(context, () -> NotebookAiSelection.figure(figure));
    }
    private static ClipboardManager manager(Context context) {
        return (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
    }
    private static long timestamp(ClipboardManager clipboard) {
        ClipDescription description = clipboard.getPrimaryClipDescription();
        return description == null ? -1 : description.getTimestamp();
    }
    private static void image(Context source, Image image) {
        Context context = source.getApplicationContext();
        ClipboardManager clipboard = manager(context);
        long request = ++revision, previous = timestamp(clipboard);
        worker.execute(() -> {
            File file = null;
            try {
                byte[] png = image.render().bytes();
                File directory = new File(context.getCacheDir(), "note-clipboard");
                if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create clipboard image");
                // Keep a bounded set of recent clipboard URIs across process restarts.
                File[] files = directory.listFiles();
                if (files != null) {
                    java.util.Arrays.sort(files, java.util.Comparator.comparingLong(File::lastModified).reversed());
                    for (int i = 15; i < files.length; i++) files[i].delete();
                }
                file = new File(directory, UUID.randomUUID() + ".png");
                try (FileOutputStream output = new FileOutputStream(file)) { output.write(png); }
                File result = file;
                main.post(() -> {
                    // A later note copy or copy in another app must win over this worker.
                    if (request != revision || timestamp(clipboard) != previous) { result.delete(); return; }
                    Uri uri = new Uri.Builder().scheme("content").authority(context.getPackageName() + ".noteclipboard")
                        .appendPath(result.getName()).build();
                    clipboard.setPrimaryClip(ClipData.newUri(context.getContentResolver(), "Note selection", uri));
                    Toast.makeText(context, "Copied as image", Toast.LENGTH_SHORT).show();
                });
            } catch (IOException | RuntimeException | OutOfMemoryError error) {
                if (file != null) file.delete();
                main.post(() -> { if (request == revision) Toast.makeText(context,
                    "Copied in notes. Could not create a clipboard image; try a smaller selection.", Toast.LENGTH_LONG).show(); });
            }
        });
    }
    private NotebookClipboard() { }
}
