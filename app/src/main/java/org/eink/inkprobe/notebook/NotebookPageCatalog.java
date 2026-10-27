package org.eink.inkprobe.notebook;

import java.io.*;
import java.util.*;
import java.util.zip.CRC32;

/** Disposable, compact navigation metadata. Ink files remain authoritative and independently validated. */
public final class NotebookPageCatalog {
    private static final int MAGIC = 0x4e504d31, VERSION = 1;
    private static final String NAME = "navigation.cache";
    public static final class Entry {
        public final UUID id;
        public final int width, height, pdfPage;
        public Entry(UUID id, int width, int height, int pdfPage) {
            if (id == null || width < 0 || height < 0 || (width == 0) != (height == 0)
                    || width > NotebookStore.MAX_DIMENSION || height > NotebookStore.MAX_DIMENSION
                    || pdfPage < 0 || pdfPage > NotebookIndex.MAX_PAGES || pdfPage > 0 && width == 0)
                throw new IllegalArgumentException("Invalid navigation metadata");
            this.id = id; this.width = width; this.height = height; this.pdfPage = pdfPage;
        }
        public static Entry of(NotebookPage page) { return new Entry(page.id, page.width, page.height, page.pdfPage); }
    }
    private NotebookPageCatalog() { }

    /** Missing or damaged caches are rebuilt; they can never replace or recover notebook data. */
    public static Map<Integer, Entry> load(File directory) {
        File file = new File(directory, NAME);
        if (!file.isFile() || file.length() < 16 || file.length() > 16L + 32L * NotebookIndex.MAX_PAGES)
            return Collections.emptyMap();
        try (DataInputStream input = new DataInputStream(new FileInputStream(file))) {
            if (input.readInt() != MAGIC || input.readInt() != VERSION) return Collections.emptyMap();
            int count = input.readInt();
            if (count < 1 || count > NotebookIndex.MAX_PAGES || file.length() != 16L + 32L * count)
                return Collections.emptyMap();
            byte[] bytes = new byte[count * 32]; input.readFully(bytes);
            CRC32 crc = new CRC32(); crc.update(bytes);
            if (input.readInt() != (int) crc.getValue()) return Collections.emptyMap();
            DataInputStream values = new DataInputStream(new ByteArrayInputStream(bytes));
            Map<Integer, Entry> entries = new LinkedHashMap<>(); Set<UUID> ids = new HashSet<>();
            for (int i = 0; i < count; i++) {
                int slot = values.readInt();
                Entry entry = new Entry(new UUID(values.readLong(), values.readLong()), values.readInt(), values.readInt(), values.readInt());
                if (slot < 1 || entries.put(slot, entry) != null || !ids.add(entry.id)) return Collections.emptyMap();
            }
            return entries;
        } catch (IOException | IllegalArgumentException failure) { return Collections.emptyMap(); }
    }

    /** Cache publication is best effort and never changes the notebook's save-error state. */
    public static void save(File directory, Map<Integer, Entry> entries) {
        if (entries.isEmpty() || entries.size() > NotebookIndex.MAX_PAGES) return;
        File temporary = new File(directory, NAME + ".tmp");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(entries.size() * 32);
            DataOutputStream values = new DataOutputStream(bytes);
            for (Map.Entry<Integer, Entry> item : entries.entrySet()) {
                Entry entry = item.getValue();
                values.writeInt(item.getKey()); values.writeLong(entry.id.getMostSignificantBits());
                values.writeLong(entry.id.getLeastSignificantBits()); values.writeInt(entry.width);
                values.writeInt(entry.height); values.writeInt(entry.pdfPage);
            }
            byte[] data = bytes.toByteArray(); CRC32 crc = new CRC32(); crc.update(data);
            try (DataOutputStream output = new DataOutputStream(new FileOutputStream(temporary))) {
                output.writeInt(MAGIC); output.writeInt(VERSION); output.writeInt(entries.size());
                output.write(data); output.writeInt((int) crc.getValue());
            }
            if (!temporary.renameTo(new File(directory, NAME))) temporary.delete();
        } catch (IOException ignored) { temporary.delete(); }
    }

    /** Import already knows all identities and dimensions; do not reopen hundreds of empty pages. */
    public static void seed(File directory, List<NotebookPage> pages) {
        Map<Integer, Entry> entries = new LinkedHashMap<>();
        for (int i = 0; i < pages.size(); i++) entries.put(i + 1, Entry.of(pages.get(i)));
        save(directory, entries);
    }
}
