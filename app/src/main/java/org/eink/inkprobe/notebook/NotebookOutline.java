package org.eink.inkprobe.notebook;

import java.io.*;
import java.util.*;
import java.util.zip.CRC32;

/** Portable outline metadata. Destinations follow stable page identities, never display numbers. */
public final class NotebookOutline {
    private static final int MAGIC = 0x4e424f31, VERSION = 1;
    public static final int MAX_ENTRIES = 10000, MAX_DEPTH = 64, MAX_TITLE_LENGTH = 1024;
    private static final int MAX_BYTES = 24 * 1024 * 1024;

    public static final class Entry {
        public final UUID id, pageId;
        public final String title;
        /** Vertical position measured from the top of the logical page. */
        public final double fraction;
        public final int depth;

        public Entry(String title, UUID pageId, double fraction, int depth) {
            this(UUID.randomUUID(), title, pageId, fraction, depth);
        }
        public Entry(UUID id, String title, UUID pageId, double fraction, int depth) {
            if (id == null || pageId == null || title == null) throw new IllegalArgumentException("Missing outline metadata");
            title = title.trim();
            if (title.isEmpty() || title.length() > MAX_TITLE_LENGTH || title.indexOf('\0') >= 0)
                throw new IllegalArgumentException("Invalid outline title");
            if (Double.isNaN(fraction) || Double.isInfinite(fraction) || fraction < 0 || fraction >= 1
                    || depth < 0 || depth > MAX_DEPTH) throw new IllegalArgumentException("Invalid outline destination");
            this.id = id; this.title = title; this.pageId = pageId;
            this.fraction = fraction == 0 ? 0 : fraction; this.depth = depth;
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Entry)) return false;
            Entry entry = (Entry) other;
            return id.equals(entry.id) && title.equals(entry.title) && pageId.equals(entry.pageId)
                && fraction == entry.fraction && depth == entry.depth;
        }
        @Override public int hashCode() { return Objects.hash(id, title, pageId, fraction, depth); }
    }

    private NotebookOutline() { }

    /** Reads are non-mutating, including recovery after interruption between atomic renames. */
    public static List<Entry> load(File directory) throws IOException {
        File document = new File(directory, "outline.nbo"), backup = new File(directory, "outline.nbo.bak"),
            temporary = new File(directory, "outline.nbo.tmp");
        File source = document.exists() ? document : backup.exists() ? backup : temporary.exists() ? temporary : null;
        if (source == null) return Collections.emptyList();
        if (source.length() > MAX_BYTES || source.length() < 20) throw new IOException("Invalid notebook outline size");
        try (InputStream input = new FileInputStream(source)) { return readSnapshot(input); }
    }

    /** Validates fully before replacing an outline, retaining the previous acknowledged file. */
    public static List<Entry> save(File directory, List<Entry> entries) throws IOException {
        List<Entry> copy = validated(entries);
        // Refuse to rotate an unreadable existing outline out of the way.
        load(directory);
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create outline directory");
        File document = new File(directory, "outline.nbo"), backup = new File(directory, "outline.nbo.bak"),
            temporary = new File(directory, "outline.nbo.tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            writeSnapshot(output, copy); output.getFD().sync();
        }
        if (document.exists()) {
            if (backup.exists() && !backup.delete()) throw new IOException("Cannot rotate notebook outline backup");
            if (!document.renameTo(backup)) throw new IOException("Cannot preserve notebook outline backup");
        }
        if (!temporary.renameTo(document)) {
            if (!document.exists() && backup.exists()) backup.renameTo(document);
            throw new IOException("Cannot publish notebook outline");
        }
        return copy;
    }

    public static void writeSnapshot(OutputStream output, List<Entry> entries) throws IOException {
        List<Entry> copy = validated(entries);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream payload = new DataOutputStream(bytes);
        payload.writeInt(copy.size());
        for (Entry entry : copy) {
            writeId(payload, entry.id); payload.writeUTF(entry.title); writeId(payload, entry.pageId);
            payload.writeDouble(entry.fraction); payload.writeInt(entry.depth);
        }
        payload.flush(); byte[] data = bytes.toByteArray();
        if (data.length + 20 > MAX_BYTES) throw new IOException("Notebook outline exceeds storage limits");
        DataOutputStream out = new DataOutputStream(output);
        out.writeInt(MAGIC); out.writeInt(VERSION); out.writeInt(data.length); out.writeInt(~data.length);
        out.write(data); out.writeInt(checksum(data)); out.flush();
    }

    public static List<Entry> readSnapshot(InputStream input) throws IOException {
        DataInputStream in = new DataInputStream(input);
        try {
            if (in.readInt() != MAGIC || in.readInt() != VERSION) throw new IOException("Unsupported notebook outline");
            int length = in.readInt();
            if (length < 4 || length > MAX_BYTES - 20 || in.readInt() != ~length) throw new IOException("Invalid outline length");
            byte[] data = new byte[length]; in.readFully(data);
            if (in.readInt() != checksum(data) || in.read() != -1) throw new IOException("Notebook outline checksum failed");
            DataInputStream payload = new DataInputStream(new ByteArrayInputStream(data));
            int count = payload.readInt();
            if (count < 0 || count > MAX_ENTRIES || (long) count * 47 > payload.available()) throw new IOException("Invalid outline count");
            List<Entry> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) entries.add(new Entry(readId(payload), payload.readUTF(), readId(payload),
                payload.readDouble(), payload.readInt()));
            if (payload.available() != 0) throw new IOException("Unexpected outline data");
            return validated(entries);
        } catch (IllegalArgumentException error) { throw new IOException("Invalid notebook outline metadata", error); }
    }

    private static List<Entry> validated(List<Entry> entries) throws IOException {
        if (entries == null || entries.size() > MAX_ENTRIES) throw new IOException("Invalid notebook outline count");
        ArrayList<Entry> copy = new ArrayList<>(entries); Set<UUID> ids = new HashSet<>();
        for (Entry entry : copy) if (entry == null || !ids.add(entry.id)) throw new IOException("Missing or duplicate outline entry");
        return Collections.unmodifiableList(copy);
    }
    private static UUID readId(DataInputStream in) throws IOException { return new UUID(in.readLong(), in.readLong()); }
    private static void writeId(DataOutputStream out, UUID id) throws IOException {
        out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits());
    }
    private static int checksum(byte[] bytes) { CRC32 crc = new CRC32(); crc.update(bytes); return (int) crc.getValue(); }
}
