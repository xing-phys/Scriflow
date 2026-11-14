package org.eink.inkprobe.notebook;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Private, independent page snapshots. Only complete archive/metadata pairs are published. */
final class NotebookWebsites {
    private static final int MAGIC = 0x4e575331; // NWS1
    private static final String ARCHIVE = "page.mht", METADATA = "website.nws";
    private final File root;

    static final class Saved {
        final String id, title, url;
        final long savedAt, bytes;
        Saved(String id, String title, String url, long savedAt, long bytes) {
            this.id = id; this.title = title; this.url = url; this.savedAt = savedAt; this.bytes = bytes;
        }
    }

    static final class Pending {
        final String id;
        final File directory, archive;
        private Pending(String id, File directory) {
            this.id = id; this.directory = directory; this.archive = new File(directory, ARCHIVE);
        }
    }

    NotebookWebsites(File filesDirectory) throws IOException {
        root = new File(filesDirectory.getCanonicalFile(), "websites");
    }

    List<Saved> list() throws IOException {
        checkRoot();
        List<Saved> entries = new ArrayList<>();
        if (!root.exists()) return entries;
        File[] directories = root.listFiles();
        if (directories == null) throw new IOException("Could not read Saved websites.");
        for (File directory : directories) {
            if (!validId(directory.getName())) continue;
            // One interrupted or damaged entry must not hide other saved pages.
            try { entries.add(get(directory.getName())); }
            catch (IOException error) { /* Leave the damaged bytes available for recovery. */ }
        }
        entries.sort(Comparator.comparingLong((Saved entry) -> entry.savedAt).reversed().thenComparing(entry -> entry.id));
        return entries;
    }

    Saved get(String id) throws IOException {
        File directory = directory(id);
        File metadata = child(directory, METADATA), archive = child(directory, ARCHIVE);
        if (!metadata.isFile() || metadata.length() > 65536) throw new IOException("This saved page is missing or damaged.");
        try (DataInputStream input = new DataInputStream(new FileInputStream(metadata))) {
            if (input.readInt() != MAGIC) throw new IOException("Unknown saved website format.");
            String title = input.readUTF(), url = input.readUTF();
            long savedAt = input.readLong(), bytes = input.readLong();
            if (title.isEmpty() || title.length() > 1024 || !NotebookBrowserAddress.isWebUrl(url)
                    || savedAt <= 0 || bytes <= 0 || input.read() != -1 || !archive.isFile() || archive.length() != bytes)
                throw new IOException("This saved page is incomplete or damaged.");
            verifyArchive(archive);
            return new Saved(id, title, url, savedAt, bytes);
        }
    }

    File archive(Saved entry) throws IOException {
        get(entry.id);
        return child(directory(entry.id), ARCHIVE);
    }

    Pending begin() throws IOException {
        checkRoot();
        if (!root.isDirectory() && !root.mkdirs()) throw new IOException("Could not create Saved websites. Check available storage.");
        cleanAbandoned();
        String id = UUID.randomUUID().toString();
        File staging = new File(root, ".saving-" + id);
        if (!staging.mkdir()) throw new IOException("Could not prepare the saved page. Check available storage.");
        return new Pending(id, staging);
    }

    Saved finish(Pending pending, String title, String url) throws IOException {
        checkPending(pending);
        if (!NotebookBrowserAddress.isWebUrl(url)) throw new IOException("Only website pages can be saved.");
        verifyArchive(pending.archive);
        title = title == null ? "" : title.trim();
        if (title.isEmpty()) title = url;
        if (title.length() > 1024) title = title.substring(0, 1024);
        long savedAt = System.currentTimeMillis(), bytes = pending.archive.length();
        try (FileOutputStream output = new FileOutputStream(pending.archive, true)) { output.getFD().sync(); }
        File metadata = child(pending.directory, METADATA);
        try (FileOutputStream file = new FileOutputStream(metadata); DataOutputStream output = new DataOutputStream(file)) {
            output.writeInt(MAGIC); output.writeUTF(title); output.writeUTF(url);
            output.writeLong(savedAt); output.writeLong(bytes); output.flush(); file.getFD().sync();
        }
        File destination = directory(pending.id);
        if (destination.exists() || !pending.directory.renameTo(destination))
            throw new IOException("Could not publish the saved page. Check available storage.");
        return new Saved(pending.id, title, url, savedAt, bytes);
    }

    void abandon(Pending pending) throws IOException {
        checkPending(pending);
        removeTree(pending.directory);
    }

    void delete(String id) throws IOException {
        File directory = directory(id);
        File deleted = child(root, ".deleting-" + id);
        if (directory.exists() && (deleted.exists() || !directory.renameTo(deleted)))
            throw new IOException("Could not delete this saved page. Please retry.");
        // A crash during removal cannot make a half-deleted page reappear.
        if (deleted.exists()) removeTree(deleted);
    }

    private void checkRoot() throws IOException {
        if (Files.isSymbolicLink(root.toPath()) || !root.getCanonicalFile().equals(root) || (root.exists() && !root.isDirectory()))
            throw new IOException("Saved websites has an invalid storage path.");
    }

    private File directory(String id) throws IOException {
        checkRoot();
        if (!validId(id)) throw new IOException("Invalid saved website.");
        return child(root, id);
    }

    private File child(File parent, String name) throws IOException {
        File file = new File(parent, name);
        if (Files.isSymbolicLink(file.toPath()) || !file.getCanonicalFile().equals(file)) throw new IOException("Invalid saved website storage path.");
        return file;
    }

    private void checkPending(Pending pending) throws IOException {
        checkRoot();
        if (pending == null || !validId(pending.id)
                || !child(root, ".saving-" + pending.id).equals(pending.directory))
            throw new IOException("Invalid pending website save.");
        child(pending.directory, ARCHIVE);
    }

    private static boolean validId(String id) {
        return id != null && id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    private static void verifyArchive(File archive) throws IOException {
        if (!archive.isFile() || archive.length() == 0) throw new IOException("The website did not produce an offline copy. Please retry after the page loads.");
        byte[] bytes = new byte[8192];
        int length;
        try (FileInputStream input = new FileInputStream(archive)) { length = input.read(bytes); }
        String header = new String(bytes, 0, Math.max(0, length), StandardCharsets.US_ASCII).toLowerCase(Locale.ROOT);
        int end = header.indexOf("\r\n\r\n");
        if (end < 0) end = header.indexOf("\n\n");
        if (end < 0 || !header.substring(0, end).contains("multipart/related"))
            throw new IOException("The website did not produce a readable offline archive.");
    }

    private void cleanAbandoned() throws IOException {
        File[] files = root.listFiles();
        if (files == null) throw new IOException("Could not read Saved websites.");
        long cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L;
        for (File file : files) {
            String name = file.getName();
            boolean deleting = name.startsWith(".deleting-") && validId(name.substring(10));
            boolean oldSave = name.startsWith(".saving-") && validId(name.substring(8)) && file.lastModified() < cutoff;
            if (deleting || oldSave) removeTree(child(root, name));
        }
    }

    private static void removeTree(File file) throws IOException {
        // Do not follow links, including links in abandoned/corrupt directories.
        if (!Files.isSymbolicLink(file.toPath()) && file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) throw new IOException("Could not remove saved website files.");
            for (File child : children) removeTree(child);
        }
        Files.deleteIfExists(file.toPath());
    }
}
