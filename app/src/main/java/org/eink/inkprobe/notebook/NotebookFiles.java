package org.eink.inkprobe.notebook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.FileVisitResult;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;
import java.nio.file.LinkOption;
import java.util.UUID;

/** Shared-volume operations. Never traverses links, replaces a destination, or touches a volume root. */
public final class NotebookFiles {
    public enum Sort { NAME, NEWEST, LARGEST, TYPE }
    public interface Control {
        void check() throws InterruptedIOException;
        void progress(String name, long bytes);
    }
    public static final Control NONE = new Control() {
        @Override public void check() { }
        @Override public void progress(String name, long bytes) { }
    };
    public static final class Entry {
        public final File file;
        public final boolean directory;
        public final long bytes, modified;
        Entry(File file) {
            this.file = file; directory = file.isDirectory();
            bytes = directory ? 0 : file.length(); modified = file.lastModified();
        }
    }
    public static final class Listing {
        public final List<Entry> entries = new ArrayList<>();
        public int skipped;
        public boolean limited;
    }
    private final List<File> roots = new ArrayList<>();

    public NotebookFiles(Collection<File> volumes) throws IOException {
        for (File volume : volumes) {
            File root = volume.getCanonicalFile();
            if (!roots.contains(root)) roots.add(root);
        }
    }

    public File checked(File file, boolean allowRoot) throws IOException {
        File absolute = file.getAbsoluteFile().toPath().normalize().toFile();
        File canonical = file.getCanonicalFile();
        if (!absolute.equals(canonical)) throw new IOException("Symbolic links are not supported: " + file.getName());
        for (File root : roots) {
            if (canonical.equals(root)) {
                if (allowRoot) return canonical;
                throw new IOException("A storage root cannot be changed.");
            }
            if (canonical.toPath().startsWith(root.toPath())) return canonical;
        }
        throw new IOException("This location is outside shared tablet storage.");
    }

    private File volume(File file) throws IOException {
        // Android's java.nio provider deliberately throws SecurityException from
        // getFileStore. Our canonical, mounted shared-volume roots identify drives.
        for (File root : roots) if (file.toPath().startsWith(root.toPath())) return root;
        throw new IOException("This storage volume is no longer available.");
    }

    public static String name(String value) throws IOException {
        if (value == null || value.trim().isEmpty() || value.equals(".") || value.equals("..")
                || value.indexOf('/') >= 0 || value.indexOf('\\') >= 0
                || value.codePoints().anyMatch(c -> Character.isISOControl(c)))
            throw new IOException("Enter a file name without slashes or control characters.");
        return value;
    }

    public Listing list(File folder, String query, boolean hidden, Sort sort, Control control) throws IOException {
        File base = checked(folder, true);
        if (!base.isDirectory()) throw new IOException("This folder is unavailable. It may have been moved or disconnected.");
        Listing result = new Listing();
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<File> pending = new ArrayList<>(); pending.add(base);
        int examined = 0;
        while (!pending.isEmpty()) {
            control.check();
            File directory = pending.remove(pending.size() - 1);
            File[] children = directory.listFiles();
            if (children == null) {
                if (directory.equals(base)) throw new IOException("Cannot read this folder. Check storage access or reconnect the drive.");
                result.skipped++; continue;
            }
            for (File child : children) {
                control.check();
                if (!hidden && child.getName().startsWith(".")) continue;
                try { checked(child, false); }
                catch (IOException inaccessible) { result.skipped++; continue; }
                if (++examined > 50000 || result.entries.size() >= (needle.isEmpty() ? 10000 : 1000)) {
                    result.limited = true; pending.clear(); break;
                }
                if (needle.isEmpty() || child.getName().toLowerCase(Locale.ROOT).contains(needle))
                    result.entries.add(new Entry(child));
                if (!needle.isEmpty() && child.isDirectory()) pending.add(child);
            }
            if (needle.isEmpty()) break;
        }
        Comparator<Entry> order = Comparator.comparing((Entry e) -> !e.directory);
        if (sort == Sort.NEWEST) order = order.thenComparing(Comparator.comparingLong((Entry e) -> e.modified).reversed());
        else if (sort == Sort.LARGEST) order = order.thenComparing(Comparator.comparingLong((Entry e) -> e.bytes).reversed());
        else if (sort == Sort.TYPE) order = order.thenComparing(e -> extension(e.file.getName()));
        result.entries.sort(order.thenComparing(e -> e.file.getName().toLowerCase(Locale.ROOT))
            .thenComparing(e -> e.file.getAbsolutePath()));
        return result;
    }

    public static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public File createFolder(File parent, String value) throws IOException {
        File target = checked(new File(checked(parent, true), name(value)), false);
        Files.createDirectory(target.toPath()); return target;
    }

    public File rename(File source, String value) throws IOException {
        source = checked(source, false);
        File target = checked(new File(source.getParentFile(), name(value)), false);
        if (source.equals(target)) return source;
        if (target.exists()) throw new IOException("Already exists: " + target.getName());
        Files.move(source.toPath(), target.toPath()); return target;
    }

    /** Collapse selected descendants so a directory and its children are handled once. */
    public List<File> selection(Collection<File> files) throws IOException {
        Set<File> checked = new LinkedHashSet<>();
        for (File file : files) {
            File source = checked(file, false);
            if (!source.exists()) throw new IOException("No longer available: " + source.getName());
            checked.add(source);
        }
        List<File> result = new ArrayList<>();
        for (File source : checked) {
            boolean contained = false;
            for (File parent = source.getParentFile(); parent != null; parent = parent.getParentFile()) {
                if (checked.contains(parent)) { contained = true; break; }
            }
            if (!contained) result.add(source);
        }
        return result;
    }

    public void transfer(Collection<File> files, File destination, boolean move, Control control) throws IOException {
        File folder = checked(destination, true);
        if (!folder.isDirectory()) throw new IOException("Choose an available destination folder.");
        List<File> sources = selection(files);
        List<File> targets = new ArrayList<>();
        // Preflight the whole batch before changing any source or destination.
        for (File source : sources) {
            File target = checked(new File(folder, source.getName()), false);
            if (folder.toPath().startsWith(source.toPath())) throw new IOException("A folder cannot be pasted into itself or its subfolders.");
            if (target.exists() || targets.contains(target))
                throw new IOException("Already exists: " + target.getName() + ". Rename it or choose another folder; no files were replaced.");
            targets.add(target);
        }
        int completed = 0;
        try {
            for (int i = 0; i < sources.size(); i++) {
                control.check();
                File source = sources.get(i), target = targets.get(i);
                if (move && volume(source).equals(volume(folder))) {
                    Files.move(source.toPath(), target.toPath());
                } else {
                    File stage = new File(folder, ".scriflow-copy-" + UUID.randomUUID());
                    Map<File, Stamp> copied = new LinkedHashMap<>();
                    try {
                        copy(source, stage, control, 0, copied);
                        control.check();
                        if (move) for (Map.Entry<File, Stamp> item : copied.entrySet()) item.getValue().verify(item.getKey());
                        if (target.exists()) throw new IOException("Already exists: " + target.getName());
                        Files.move(stage.toPath(), target.toPath());
                    } finally { if (stage.exists()) removeTree(stage, NONE); }
                    // Only remove the source after its complete copy is published. Once committed,
                    // cancellation waits for this item's cleanup; a failure retains the new copy.
                    if (move) removeCopied(source, copied);
                }
                completed++;
            }
        } catch (IOException failure) {
            throw new IOException(completed + " of " + sources.size() + " items completed. " + failure.getMessage(), failure);
        }
    }

    private static final class Stamp {
        final long bytes, modified;
        final boolean directory;
        final Object key;
        Stamp(File file) throws IOException {
            BasicFileAttributes attrs = Files.readAttributes(file.toPath(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            bytes = attrs.size(); modified = attrs.lastModifiedTime().toMillis(); directory = attrs.isDirectory(); key = attrs.fileKey();
        }
        void verify(File file) throws IOException {
            Stamp current = new Stamp(file);
            if (bytes != current.bytes || modified != current.modified || directory != current.directory
                    || !java.util.Objects.equals(key, current.key))
                throw new IOException("The source changed during moving: " + file.getName() + ". Its remaining contents were kept.");
        }
    }

    private void removeCopied(File file, Map<File, Stamp> copied) throws IOException {
        checked(file, false); Stamp stamp = copied.get(file);
        if (stamp == null) throw new IOException("New source content was kept: " + file.getName());
        stamp.verify(file);
        if (stamp.directory) {
            File[] children = file.listFiles();
            if (children == null) throw new IOException("Cannot read the original folder; the complete destination copy was kept.");
            for (File child : children) removeCopied(child, copied);
        }
        Files.delete(file.toPath());
    }

    private void copy(File source, File target, Control control, int depth, Map<File, Stamp> copied) throws IOException {
        control.check(); checked(source, false); checked(target, false);
        if (depth > 128) throw new IOException("Folder nesting is too deep: " + source.getName());
        copied.put(source, new Stamp(source));
        if (source.isDirectory()) {
            Files.createDirectory(target.toPath());
            File[] children = source.listFiles();
            if (children == null) throw new IOException("Cannot read: " + source.getName());
            for (File child : children) copy(child, new File(target, child.getName()), control, depth + 1, copied);
        } else {
            if (!source.isFile()) throw new IOException("Unsupported file: " + source.getName());
            long expected = source.length(), modified = source.lastModified(), bytes = 0;
            long available = target.getParentFile().getUsableSpace();
            if (available > 0 && expected > available) throw new IOException("Not enough free storage for " + source.getName());
            Files.createFile(target.toPath());
            try (InputStream input = Files.newInputStream(source.toPath()); FileOutputStream output = new FileOutputStream(target)) {
                byte[] buffer = new byte[128 * 1024]; int count;
                while ((count = input.read(buffer)) != -1) {
                    control.check(); output.write(buffer, 0, count); bytes += count;
                    control.progress(source.getName(), bytes);
                }
                output.getFD().sync();
            }
            if (bytes != expected || source.length() != expected || source.lastModified() != modified)
                throw new IOException("The source changed during copying: " + source.getName() + ". The source was kept.");
        }
        // Some removable filesystems cannot preserve timestamps; the copy is still usable.
        target.setLastModified(source.lastModified());
    }

    public void delete(Collection<File> files, Control control) throws IOException {
        List<File> sources = selection(files);
        int completed = 0;
        try {
            for (File source : sources) { removeTree(source, control); completed++; }
        } catch (IOException failure) {
            throw new IOException(completed + " of " + sources.size() + " items deleted. " + failure.getMessage(), failure);
        }
    }

    private void removeTree(File file, Control control) throws IOException {
        checked(file, false);
        Files.walkFileTree(file.toPath(), new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attrs) throws IOException {
                control.check(); checked(path.toFile(), false); return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                control.check();
                // walkFileTree never follows links. Removing a link only removes the link itself.
                if (!attrs.isSymbolicLink()) checked(path.toFile(), false);
                Files.delete(path); control.progress(path.getFileName().toString(), -1); return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path path, IOException error) throws IOException {
                if (error != null) throw error;
                control.check(); Files.delete(path); return FileVisitResult.CONTINUE;
            }
        });
    }
}
