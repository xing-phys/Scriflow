package org.eink.inkprobe.notebook;

import java.io.*;
import java.util.*;
import java.util.zip.CRC32;

/** Disposable geometry/outline index. Source PDF bytes remain the authority, never this cache. */
final class NotebookPdfMetadata {
    private static final int MAGIC = 0x4e504d31, VERSION = 1, MAX_BYTES = 24 * 1024 * 1024;
    private NotebookPdfMetadata() { }

    static File file(File source) { return new File(source.getParentFile(), source.getName() + ".metadata"); }

    /** Missing, old, interrupted, or corrupt caches simply cause a new source inspection. */
    static NotebookPdfAsset read(File source) {
        File cache = file(source);
        if (!source.isFile() || !cache.isFile() || cache.length() < 40 || cache.length() > MAX_BYTES) return null;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(cache)))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION
                    || in.readLong() != source.length() || in.readLong() != source.lastModified()) return null;
            int length = in.readInt();
            if (length < 8 || length > MAX_BYTES - 32 || cache.length() != length + 32L) return null;
            byte[] bytes = new byte[length]; in.readFully(bytes);
            if (in.readInt() != checksum(bytes) || in.read() != -1) return null;
            DataInputStream data = new DataInputStream(new ByteArrayInputStream(bytes));
            String title = data.readUTF();
            if (title.length() > NotebookOutline.MAX_TITLE_LENGTH) return null;
            int count = data.readInt();
            if (count < 1 || count > NotebookIndex.MAX_PAGES || count * 8L > data.available()) return null;
            List<NotebookPdfAsset.Page> pages = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int width = data.readInt(), height = data.readInt();
                if (width < 1 || height < 1 || width > NotebookStore.MAX_DIMENSION || height > NotebookStore.MAX_DIMENSION)
                    return null;
                pages.add(new NotebookPdfAsset.Page(width, height));
            }
            int headings = data.readInt();
            if (headings < 0 || headings > NotebookOutline.MAX_ENTRIES || headings * 18L > data.available()) return null;
            List<NotebookPdfAsset.Heading> outline = new ArrayList<>(headings);
            for (int i = 0; i < headings; i++) {
                String heading = data.readUTF();
                int page = data.readInt(), depth = data.readInt(); double fraction = data.readDouble();
                if (heading.trim().isEmpty() || heading.length() > NotebookOutline.MAX_TITLE_LENGTH || heading.indexOf('\0') >= 0
                        || page < 1 || page > count || depth < 0 || depth > NotebookOutline.MAX_DEPTH
                        || !Double.isFinite(fraction) || fraction < 0 || fraction >= 1) return null;
                outline.add(new NotebookPdfAsset.Heading(heading, page, depth, fraction));
            }
            return data.available() == 0 ? new NotebookPdfAsset(title, pages, outline) : null;
        } catch (IOException | RuntimeException invalidCache) { return null; }
    }

    /** Cache publication is atomic but does not need durable-write barriers: it is rebuildable. */
    static void write(File source, NotebookPdfAsset asset) {
        File cache = file(source), temporary = new File(cache.getPath() + ".tmp");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream data = new DataOutputStream(bytes);
            data.writeUTF(asset.title); data.writeInt(asset.pages.size());
            for (NotebookPdfAsset.Page page : asset.pages) { data.writeInt(page.width); data.writeInt(page.height); }
            data.writeInt(asset.outline.size());
            for (NotebookPdfAsset.Heading heading : asset.outline) {
                data.writeUTF(heading.title); data.writeInt(heading.pdfPage);
                data.writeInt(heading.depth); data.writeDouble(heading.fraction);
            }
            data.flush(); byte[] payload = bytes.toByteArray();
            if (payload.length > MAX_BYTES - 32) return;
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(temporary)))) {
                out.writeInt(MAGIC); out.writeInt(VERSION); out.writeLong(source.length()); out.writeLong(source.lastModified());
                out.writeInt(payload.length); out.write(payload); out.writeInt(checksum(payload));
            }
            temporary.renameTo(cache);
        } catch (IOException | RuntimeException unavailableCache) { /* Cache failure cannot reject a valid PDF. */ }
        finally { temporary.delete(); }
    }

    private static int checksum(byte[] bytes) { CRC32 crc = new CRC32(); crc.update(bytes); return (int) crc.getValue(); }
}
