package org.eink.inkprobe.notebook;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Retained import: copy and validate privately before publishing a separate notebook. */
final class NotebookPdfImport {
    private static final long MAX_PDF_BYTES = 256L * 1024 * 1024;
    private static final ExecutorService files = Executors.newSingleThreadExecutor(r -> new Thread(r, "notebook-pdf-import"));
    private final Context context;
    private final NotebookSession session;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Runnable listener;
    volatile boolean busy, complete;
    volatile String message = "Choose a PDF. Its original pages, links, and outline will be kept with your editable ink.";

    NotebookPdfImport(Context context) {
        this.context = context.getApplicationContext(); session = NotebookSession.get(context);
    }
    void observe(Runnable listener) { this.listener = listener; listener.run(); }
    void detach() { listener = null; }
    private void changed() { main.post(() -> { if (listener != null) listener.run(); }); }

    void importPdf(Uri uri) {
        if (busy || complete) return;
        busy = true; message = "Copying original PDF…"; changed();
        files.execute(() -> {
            File staging = new File(context.getFilesDir(), "pdf-stage-" + UUID.randomUUID());
            try {
                if (!staging.mkdir()) throw new IOException("Cannot create PDF import staging directory");
                File original = new File(staging, "source.pdf");
                try (InputStream input = context.getContentResolver().openInputStream(uri);
                        FileOutputStream output = new FileOutputStream(original)) {
                    if (input == null) throw new IOException("Cannot open the selected PDF");
                    byte[] buffer = new byte[65536]; long total = 0; int size;
                    while ((size = input.read(buffer)) != -1) {
                        total += size;
                        if (total > MAX_PDF_BYTES) throw new IOException("PDF import supports files up to 256 MiB");
                        output.write(buffer, 0, size);
                    }
                    output.getFD().sync();
                }
                message = "Reading PDF pages, links, and outline…"; changed();
                NotebookPdfAsset asset = NotebookPdfEngine.inspect(context, original);
                if (asset.pages.isEmpty() || asset.pages.size() > NotebookIndex.MAX_PAGES)
                    throw new IOException("PDF import supports 1–" + NotebookIndex.MAX_PAGES + " pages");
                List<UUID> pageIds = new ArrayList<>();
                for (int i = 0; i < asset.pages.size(); i++) {
                    NotebookPdfAsset.Page source = asset.pages.get(i);
                    File pageDirectory = NotebookBackup.pageDirectory(staging, i + 1);
                    NotebookStore store = new NotebookStore(pageDirectory); store.load();
                    NotebookPage page = store.initializePdfPage(source.width, source.height, i + 1);
                    store.sync(); pageIds.add(page.id);
                }
                NotebookIndex index = new NotebookIndex(staging); index.load();
                for (int count = 1; count <= pageIds.size(); count++) index.save(count, 1);
                index.sync();
                List<NotebookOutline.Entry> headings = new ArrayList<>();
                for (NotebookPdfAsset.Heading heading : asset.outline) {
                    if (heading.pdfPage >= 1 && heading.pdfPage <= pageIds.size())
                        headings.add(new NotebookOutline.Entry(heading.title, pageIds.get(heading.pdfPage - 1),
                            heading.fraction, heading.depth));
                }
                NotebookOutline.save(staging, headings);
                String name = displayName(uri, asset.title);
                NotebookBackup.Restored prepared = new NotebookBackup.Restored(staging, name, pageIds.size());
                message = "Adding PDF notebook…"; changed();
                session.importBackup(prepared, error -> {
                    if (staging.exists()) NotebookBackup.deleteTree(staging);
                    busy = false;
                    if (error != null) message = "Couldn’t import PDF. " + error.getMessage();
                    else { complete = true; message = "PDF imported as “" + name + "”. Open it from the library to write, follow links, or use its outline."; }
                    changed();
                });
            } catch (IOException | RuntimeException error) {
                NotebookBackup.deleteTree(staging);
                busy = false; message = "Couldn’t import PDF. " + (error.getMessage() == null ? "Try a different PDF." : error.getMessage()); changed();
            }
        });
    }

    private String displayName(Uri uri, String fallback) {
        String name = null;
        try (Cursor cursor = context.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
        } catch (RuntimeException ignored) { /* A provider can omit its display name. */ }
        if (name == null || name.trim().isEmpty()) name = fallback;
        if (name == null || name.trim().isEmpty()) name = "Imported PDF";
        if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) name = name.substring(0, name.length() - 4);
        name = name.replaceAll("[\\p{Cntrl}\\p{Cf}]", " ").trim();
        if (name.codePointCount(0, name.length()) > NotebookCatalog.MAX_NAME_CODE_POINTS)
            name = name.substring(0, name.offsetByCodePoints(0, NotebookCatalog.MAX_NAME_CODE_POINTS));
        try { return NotebookCatalog.normalizedName(name); }
        catch (IllegalArgumentException ignored) { return "Imported PDF"; }
    }
}
