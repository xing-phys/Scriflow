package org.eink.inkprobe.notebook;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.zip.CRC32;

/** Immutable book originals and recoverable reading state, outside notebook page formats. */
final class NotebookEbookStore {
    static final long MAX_BOOK_BYTES = 2L * 1024 * 1024 * 1024;
    static final int MAX_STATE_BYTES = 2 * 1024 * 1024;
    private static final int MAGIC = 0x45424b31;
    private final File root;
    static final class Book {
        final String id, title, documentTitle, label, format, author;
        final int folderId;
        final boolean trashed;
        final long bytes, opened;
        Book(String id, Properties p, long bytes) {
            this.id = id; documentTitle=p.getProperty("title", "Book");label=p.getProperty("label");
            title=label==null?documentTitle:label;format = p.getProperty("format", "");
            int folder;try{folder=Integer.parseInt(p.getProperty("folder","0"));}catch(NumberFormatException ignored){folder=0;}
            folderId=Math.max(0,folder);trashed=Boolean.parseBoolean(p.getProperty("trashed","false"));
            author = p.getProperty("author", ""); this.bytes = bytes;
            long date; try { date = Long.parseLong(p.getProperty("opened", "0")); } catch (NumberFormatException ignored) { date = 0; }
            opened = date;
        }
    }
    interface Progress { void copied(long bytes); }
    NotebookEbookStore(File files) { root = new File(files, "ebooks"); }
    static boolean supported(String name) { return !format(name).isEmpty(); }
    static String format(String name) {
        if (name == null) return "";
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".fb2.zip")) return "fbz";
        int dot = lower.lastIndexOf('.'); String type = dot < 0 ? "" : lower.substring(dot + 1);
        if (type.equals("prc") || type.equals("azw")) type = "mobi";
        if (type.equals("djv")) type = "djvu";
        if (type.equals("htm")) type = "html";
        return Arrays.asList("epub", "mobi", "azw3", "fb2", "fbz", "cbz", "txt", "html", "djvu").contains(type) ? type : "";
    }
    private File directory(String id) throws IOException {
        if (id == null || !id.matches("[a-f0-9]{64}")) throw new IOException("Invalid book identity.");
        return new File(root, id);
    }
    File source(String id) throws IOException {
        File file = new File(directory(id), "source");
        if (!file.isFile()) throw new IOException("This book is unavailable. Import the original again.");
        return file;
    }
    File font(String id) throws IOException { return new File(directory(id), "custom-font"); }
    void font(String id, InputStream input) throws IOException {
        source(id); if (input == null) throw new IOException("The font could not be opened.");
        File file = font(id), next = new File(file + ".next");
        try {
            long bytes = 0; byte[] buffer = new byte[32768]; int count;
            try (FileOutputStream output = new FileOutputStream(next)) {
                while ((count = input.read(buffer)) != -1) {
                    bytes += count; if (bytes > 16 * 1024 * 1024) throw new IOException("Choose a TTF/OTF font up to 16 MiB.");
                    output.write(buffer, 0, count);
                }
                output.getFD().sync();
            }
            byte[] magic = new byte[4]; try (DataInputStream in = new DataInputStream(new FileInputStream(next))) { in.readFully(magic); }
            if (!Arrays.equals(magic, new byte[]{0,1,0,0}) && !Arrays.equals(magic, new byte[]{'O','T','T','O'})) throw new IOException("Choose a TrueType (.ttf) or OpenType (.otf) font.");
            java.nio.file.Files.move(next.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally { if (next.exists()) next.delete(); }
    }
    Book book(String id) throws IOException {
        Properties p = properties(new File(directory(id), "metadata"));
        if (!supported("book." + p.getProperty("format", ""))) throw new IOException("Unknown book format.");
        return new Book(id, p, source(id).length());
    }
    List<Book> list() throws IOException {
        List<Book> books = new ArrayList<>(); File[] entries = root.listFiles();
        if (entries == null) { if (root.exists()) throw new IOException("Cannot read the book library."); return books; }
        for (File entry : entries) if (entry.getName().matches("[a-f0-9]{64}")) books.add(book(entry.getName()));
        books.sort(Comparator.comparingLong((Book b) -> b.opened).reversed().thenComparing(b -> b.title.toLowerCase(Locale.ROOT)));
        return books;
    }
    String importBook(InputStream input, String filename, Progress progress) throws IOException {
        String type = format(filename);
        if (type.isEmpty()) throw new IOException("Choose an EPUB, MOBI, AZW3, DjVu, FB2, CBZ, HTML or text book.");
        if (input == null) throw new IOException("The selected file could not be opened.");
        if (!root.isDirectory() && !root.mkdirs()) throw new IOException("Cannot create the book library.");
        File stage = new File(root, ".import-" + UUID.randomUUID());
        if (!stage.mkdir()) throw new IOException("Cannot stage the book import.");
        try {
            MessageDigest digest = sha256(); long bytes = 0; byte[] buffer = new byte[128 * 1024]; int count;
            try (FileOutputStream output = new FileOutputStream(new File(stage, "source"))) {
                while ((count = input.read(buffer)) != -1) {
                    if (count == 0) continue;
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Book import cancelled.");
                    bytes += count;
                    if (bytes > MAX_BOOK_BYTES) throw new IOException("Books up to 2 GiB can be imported.");
                    if (root.getUsableSpace() < count) throw new IOException("Not enough free space to import this book.");
                    output.write(buffer, 0, count); digest.update(buffer, 0, count); progress.copied(bytes);
                }
                output.getFD().sync();
            }
            if (bytes == 0) throw new IOException("This file is empty.");
            // Include the format: the same raw text can intentionally be read as HTML or TXT.
            digest.update(type.getBytes(StandardCharsets.UTF_8)); String id = hex(digest.digest());
            File destination = directory(id);
            if (destination.exists()) { if(book(id).trashed)trash(id,false); return id; }
            Properties metadata = new Properties(); metadata.setProperty("title", title(filename)); metadata.setProperty("format", type);
            metadata.setProperty("opened", Long.toString(System.currentTimeMillis())); writeProperties(new File(stage, "metadata"), metadata);
            if (!stage.renameTo(destination)) throw new IOException("Cannot publish the imported book.");
            return id;
        } finally { if (stage.exists()) deleteTree(stage); }
    }
    synchronized String state(String id) throws IOException {
        File file = new File(directory(id), "state");
        if (!file.exists() && !new File(file + ".next").exists() && !new File(file + ".bak").exists()) return "{}";
        return new String(readFrame(file), StandardCharsets.UTF_8);
    }
    synchronized void state(String id, String json) throws IOException {
        source(id); writeFrame(new File(directory(id), "state"), json.getBytes(StandardCharsets.UTF_8));
    }
    synchronized void metadata(String id, String title, String author) throws IOException {
        File file = new File(directory(id), "metadata"); Properties p = properties(file);
        if (title != null && !title.trim().isEmpty()) p.setProperty("title", clean(title, 240));
        if (author != null) p.setProperty("author", clean(author, 240));
        p.setProperty("opened", Long.toString(System.currentTimeMillis())); writeProperties(file, p);
    }
    synchronized void rename(String id,String title)throws IOException {
        if(title==null||title.trim().isEmpty())throw new IOException("Enter a book name.");
        Properties p=properties(new File(directory(id),"metadata"));p.setProperty("label",clean(title,240));
        writeProperties(new File(directory(id),"metadata"),p);
    }
    synchronized void move(String id,int folder)throws IOException {
        if(folder<0)throw new IOException("Invalid book folder.");
        Properties p=properties(new File(directory(id),"metadata"));p.setProperty("folder",Integer.toString(folder));
        writeProperties(new File(directory(id),"metadata"),p);
    }
    synchronized void trash(String id,boolean value)throws IOException {
        Properties p=properties(new File(directory(id),"metadata"));p.setProperty("trashed",Boolean.toString(value));
        writeProperties(new File(directory(id),"metadata"),p);
    }
    void remove(String id) throws IOException { File dir = directory(id); if (dir.exists()) deleteTree(dir); }
    synchronized void purge(String id)throws IOException{
        if(!book(id).trashed)throw new IOException("Only ebooks in Trash can be deleted permanently.");
        remove(id);
    }
    static String title(String name) {
        if (name == null) return "Book";
        String value = new File(name).getName(); int dot = value.lastIndexOf('.');
        return clean(dot > 0 ? value.substring(0, dot) : value, 240);
    }
    private static String clean(String value, int limit) {
        String text = value.replaceAll("[\\p{Cntrl}\\p{Cf}]", " ").trim();
        return text.isEmpty() ? "Book" : text.substring(0, Math.min(limit, text.length()));
    }
    private static Properties properties(File file) throws IOException {
        Properties p = new Properties(); p.load(new ByteArrayInputStream(readFrame(file))); return p;
    }
    private static void writeProperties(File file, Properties values) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); values.store(bytes, "Scriflow book"); writeFrame(file, bytes.toByteArray());
    }
    static void writeFrame(File file, byte[] data) throws IOException {
        if (data.length > MAX_STATE_BYTES) throw new IOException("This book's annotations exceed the 2 MiB state limit. Export and trim older notes before adding more.");
        File next = new File(file + ".next"), backup = new File(file + ".bak");
        try (FileOutputStream stream = new FileOutputStream(next); DataOutputStream out = new DataOutputStream(stream)) {
            out.writeInt(MAGIC); out.writeInt(data.length); out.write(data); out.writeInt(crc(data)); out.flush(); stream.getFD().sync();
        }
        if (file.exists()) {
            boolean valid; try { readOne(file); valid = true; } catch (IOException broken) { valid = false; }
            if (valid) {
                if (backup.exists() && !backup.delete()) throw new IOException("Cannot retain the previous book state.");
                if (!file.renameTo(backup)) throw new IOException("Cannot retain the previous book state.");
            } else if (!file.delete()) throw new IOException("Cannot replace damaged book state; recovery files retained.");
        }
        if (!next.renameTo(file)) throw new IOException("Cannot save book state; recovery files retained.");
    }
    static byte[] readFrame(File file) throws IOException {
        IOException problem = null;
        for (File choice : new File[]{file, new File(file + ".next"), new File(file + ".bak")}) if (choice.exists()) {
            try { return readOne(choice); } catch (IOException error) { problem = error; }
        }
        throw new IOException("Cannot read this book's saved data; recovery copies were retained.", problem);
    }
    private static byte[] readOne(File file) throws IOException {
        if (file.length() < 12 || file.length() > MAX_STATE_BYTES + 12) throw new IOException("Invalid book state size.");
        try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
            if (in.readInt() != MAGIC) throw new IOException("Unknown book state format.");
            int count = in.readInt();
            if (count < 0 || count != file.length() - 12) throw new IOException("Truncated book state.");
            byte[] data = new byte[count]; in.readFully(data);
            if (in.readInt() != crc(data) || in.read() != -1) throw new IOException("Book state checksum failed.");
            return data;
        }
    }
    private static int crc(byte[] bytes) { CRC32 crc = new CRC32(); crc.update(bytes); return (int) crc.getValue(); }
    private static MessageDigest sha256() { try { return MessageDigest.getInstance("SHA-256"); } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); } }
    private static String hex(byte[] bytes) { StringBuilder result = new StringBuilder(); for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value & 255)); return result.toString(); }
    private static void deleteTree(File file) throws IOException {
        File[] children = file.listFiles(); if (children != null) for (File child : children) deleteTree(child);
        if (!file.delete() && file.exists()) throw new IOException("Cannot remove a private book staging file.");
    }
}
