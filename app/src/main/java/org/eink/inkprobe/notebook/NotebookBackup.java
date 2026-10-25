package org.eink.inkprobe.notebook;

import java.io.*;
import java.util.HashSet;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Portable editable notebook archive. No Android, native brush, cache, history or credentials. */
public final class NotebookBackup {
    private static final int MAGIC = 0x494e4b42, VERSION = 1;
    private static final long MAX_BYTES = 512L * 1024 * 1024;
    private static final long MAX_PAGE_BYTES = 64L * 1024 * 1024;
    public interface PageSource { NotebookPage page(int number) throws IOException; }
    public static final class Restored {
        public final File directory;
        public final String name;
        public final int pageCount;
        private Restored(File directory, String name, int count) {
            this.directory = directory; this.name = name; pageCount = count;
        }
    }
    private NotebookBackup() { }

    /** Sequential canonical page snapshots; does not take ownership of the caller's stream. */
    public static void write(OutputStream output, String name, int count, PageSource pages,
                             BooleanSupplier cancelled) throws IOException {
        if (count < 1 || count > NotebookIndex.MAX_PAGES) throw new IOException("Invalid page count");
        name = NotebookCatalog.normalizedName(name);
        ZipOutputStream zip = new ZipOutputStream(new FilterOutputStream(output) {
            @Override public void close() throws IOException { flush(); }
        });
        HashSet<UUID> ids = new HashSet<>();
        try {
            zip.putNextEntry(new ZipEntry("manifest"));
            DataOutputStream header = new DataOutputStream(zip);
            header.writeInt(MAGIC); header.writeInt(VERSION); header.writeUTF(name); header.writeInt(count);
            zip.closeEntry();
            long[] total = {0};
            for (int number = 1; number <= count; number++) {
                check(cancelled);
                NotebookPage page = pages.page(number);
                if (count > 1 && !page.isInitialized()) throw new IOException("Unsized page in notebook");
                if (!ids.add(page.id)) throw new IOException("Duplicate page ID");
                zip.putNextEntry(new ZipEntry(entryName(number)));
                NotebookStore.writeSnapshot(new FilterOutputStream(zip) {
                    @Override public void write(int value) throws IOException { account(1); out.write(value); }
                    @Override public void write(byte[] bytes, int start, int length) throws IOException {
                        account(length); out.write(bytes, start, length);
                    }
                    private void account(int length) throws IOException {
                        check(cancelled); total[0] += length;
                        if (total[0] > MAX_BYTES) throw new IOException("Notebook exceeds the 512 MiB backup limit");
                    }
                }, page);
                zip.closeEntry();
            }
            check(cancelled); zip.finish(); zip.flush();
        } finally { zip.close(); }
    }

    /** Fully validates in a private staging directory before any catalog publication. */
    public static Restored read(InputStream input, File staging, BooleanSupplier cancelled) throws IOException {
        if (staging.exists() || !staging.mkdirs()) throw new IOException("Cannot create restore staging directory");
        boolean complete = false;
        try (ZipInputStream zip = new ZipInputStream(input)) {
            expect(zip, "manifest");
            DataInputStream header = new DataInputStream(zip);
            if (header.readInt() != MAGIC || header.readInt() != VERSION) throw new IOException("Unsupported notebook backup");
            String name;
            try { name = NotebookCatalog.normalizedName(header.readUTF()); }
            catch (IllegalArgumentException error) { throw new IOException("Invalid notebook name", error); }
            int count = header.readInt();
            if (count < 1 || count > NotebookIndex.MAX_PAGES || zip.read() != -1)
                throw new IOException("Invalid backup manifest");
            HashSet<UUID> ids = new HashSet<>();
            byte[] buffer = new byte[65536]; long total = 0;
            for (int number = 1; number <= count; number++) {
                check(cancelled); expect(zip, entryName(number));
                File pageDir = pageDirectory(staging, number);
                if (!pageDir.isDirectory() && !pageDir.mkdirs()) throw new IOException("Cannot create restored page");
                File file = new File(pageDir, "page.nbk"); long size = 0;
                try (FileOutputStream out = new FileOutputStream(file)) {
                    int length;
                    while ((length = zip.read(buffer)) != -1) {
                        check(cancelled); size += length; total += length;
                        if (size > MAX_PAGE_BYTES || total > MAX_BYTES) throw new IOException("Backup exceeds restore limits");
                        out.write(buffer, 0, length);
                    }
                    out.getFD().sync();
                }
                NotebookStore store = new NotebookStore(pageDir);
                NotebookPage page = store.load();
                if (store.isRecoveredTail() || count > 1 && !page.isInitialized()) throw new IOException("Incomplete restored page");
                if (!ids.add(page.id)) throw new IOException("Duplicate page ID in backup");
            }
            if (zip.getNextEntry() != null) throw new IOException("Unexpected backup entry");
            NotebookIndex index = new NotebookIndex(staging); index.load();
            for (int countSoFar = 1; countSoFar <= count; countSoFar++) index.save(countSoFar, 1);
            check(cancelled); complete = true;
            return new Restored(staging, name, count);
        } finally { if (!complete) deleteTree(staging); }
    }

    static File pageDirectory(File book, int number) {
        return number == 1 ? book : new File(new File(book, "pages"), Integer.toString(number));
    }
    private static String entryName(int number) { return "pages/" + number + ".nbk"; }
    private static void expect(ZipInputStream zip, String name) throws IOException {
        ZipEntry entry = zip.getNextEntry();
        if (entry == null || entry.isDirectory() || !name.equals(entry.getName()))
            throw new IOException("Missing or unexpected backup entry: " + name);
    }
    private static void check(BooleanSupplier cancelled) throws IOException {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new IOException("Backup cancelled");
    }
    static void deleteTree(File directory) {
        File[] files = directory.listFiles();
        if (files != null) for (File file : files) deleteTree(file);
        directory.delete();
    }
}
