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
    final NotebookPdfImportFlow flow = new NotebookPdfImportFlow();
    volatile String message = "Opening PDF picker…";

    NotebookPdfImport(Context context) {
        this.context = context.getApplicationContext(); session = NotebookSession.get(context);
    }
    void observe(Runnable listener) { this.listener = listener; listener.run(); }
    void detach() { listener = null; }
    private void changed() { main.post(() -> { if (listener != null) listener.run(); }); }

    void importPdf(Uri uri) {
        if (!flow.beginImport()) return;
        message = "Copying original PDF…"; changed();
        try { files.execute(() -> runImport(uri)); }
        catch (RuntimeException | OutOfMemoryError error) { failed(error); }
    }

    private void runImport(Uri uri) {
        File staging = null;
        try {
            staging = new File(context.getFilesDir(), "pdf-stage-" + UUID.randomUUID());
            preparePdf(uri, staging);
        } catch (IOException | RuntimeException | OutOfMemoryError error) {
            discardStaging(staging); failed(error);
        }
    }

    private void preparePdf(Uri uri, File staging) throws IOException {
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
        message = "Reading PDF page sizes and outline…"; changed();
        NotebookPdfAsset asset = NotebookPdfEngine.inspect(context, original);
        if (asset.pages.isEmpty() || asset.pages.size() > NotebookIndex.MAX_PAGES)
            throw new IOException("PDF import supports 1–" + NotebookIndex.MAX_PAGES + " pages");
        List<NotebookViewport.Size> sizes = new ArrayList<>(asset.pages.size());
        for (NotebookPdfAsset.Page source : asset.pages)
            sizes.add(new NotebookViewport.Size(source.width, source.height));
        List<NotebookPage> pages = NotebookPdfStaging.pages(staging, sizes);
        NotebookIndex index = new NotebookIndex(staging); index.load();
        index.initializeImported(pages.size());
        NotebookPageCatalog.seed(staging, pages);
        List<NotebookOutline.Entry> headings = new ArrayList<>();
        for (NotebookPdfAsset.Heading heading : asset.outline) {
            if (heading.pdfPage >= 1 && heading.pdfPage <= pages.size())
                headings.add(new NotebookOutline.Entry(heading.title, pages.get(heading.pdfPage - 1).id,
                    heading.fraction, heading.depth));
        }
        NotebookOutline.save(staging, headings);
        String name = displayName(uri, asset.title);
        NotebookBackup.Restored prepared = new NotebookBackup.Restored(staging, name, pages.size());
        message = "Adding PDF notebook…"; changed();
        session.importBackup(prepared, error -> {
            discardStaging(staging);
            if (error != null) { failed(error); return; }
            flow.imported(session.notebookId()); message = "Opening “" + name + "”…"; changed();
        });
    }

    /** Only the private source directory is disposable; renamed transaction/recovery directories are untouched. */
    private void discardStaging(File staging) {
        if (staging == null) return;
        try { if (staging.exists()) NotebookBackup.deleteTree(staging); }
        catch (RuntimeException | OutOfMemoryError ignored) { /* Keep a private partial stage if cleanup itself is resource-limited. */ }
    }

    private void failed(Throwable error) {
        String detail = error instanceof OutOfMemoryError || error.getCause() instanceof OutOfMemoryError
            ? "This PDF needs more memory than is available. Check the library for a completed import, or try a smaller PDF."
            : error.getMessage() == null ? "Try a different PDF." : error.getMessage();
        // Retain only the small message, not the failing parser's potentially large object graph.
        main.post(() -> { flow.failed(); message = "Couldn’t import PDF. " + detail; changed(); });
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
