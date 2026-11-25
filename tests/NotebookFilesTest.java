package org.eink.inkprobe.notebook;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Real temporary files: safe batch operations, cancellation, boundaries and hostile links. */
public final class NotebookFilesTest {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        File temporary = Files.createTempDirectory("scriflow-files-").toFile().getCanonicalFile();
        try {
            File root = new File(temporary, "storage"); Files.createDirectory(root.toPath());
            NotebookFiles files = new NotebookFiles(Collections.singletonList(root));
            File folder = files.createFolder(root, "Physics notes");
            File text = write(new File(folder, "Résumé 中文.txt"), "quantum dynamics");
            write(new File(folder, ".hidden"), "hidden");
            File nested = files.createFolder(folder, "nested");
            File paper = write(new File(nested, "paper.PDF"), "%PDF-fixture");
            check(files.list(folder, "", false, NotebookFiles.Sort.NAME, NotebookFiles.NONE).entries.get(0).directory, "Folders sort before files");
            check(files.list(folder, "", false, NotebookFiles.Sort.NAME, NotebookFiles.NONE).entries.size() == 2, "Hidden files stay hidden by default");
            check(files.list(folder, "", true, NotebookFiles.Sort.NAME, NotebookFiles.NONE).entries.size() == 3, "Hidden files can be shown");
            NotebookFiles.Listing search = files.list(root, "PAPER.pdf", false, NotebookFiles.Sort.NEWEST, NotebookFiles.NONE);
            check(search.entries.size() == 1 && search.entries.get(0).file.equals(paper), "Search descends folders and ignores name case");
            check(NotebookFiles.extension("paper.PDF").equals("pdf"), "Uppercase extensions are supported");
            List<File> selected = files.selection(Arrays.asList(folder, nested, paper, folder));
            check(selected.size() == 1 && selected.get(0).equals(folder), "Overlapping selections are processed once");

            File reserved = files.reserveFile(folder, "export.svg");
            check(reserved.isFile() && reserved.length() == 0, "Export reserves a new file in the selected folder");
            Files.write(reserved.toPath(), "keep".getBytes(StandardCharsets.UTF_8));
            fails(() -> files.reserveFile(folder, "export.svg"), "Export cannot replace an existing file");
            check(read(reserved).equals("keep"), "A conflicting export preserves existing bytes");
            fails(() -> files.reserveFile(root, "../outside"), "Export rejects path traversal");
            reserved.delete();

            for (String name : new String[]{"", " ", ".", "..", "../escape", "a/b", "a\\b", "line\nbreak", "nul\0"})
                fails(() -> NotebookFiles.name(name), "Unsafe file name is rejected");
            fails(() -> files.createFolder(root, "Physics notes"), "Creating a folder never reuses an existing one");
            fails(() -> files.delete(Collections.singletonList(root), NotebookFiles.NONE), "Storage roots cannot be deleted");
            fails(() -> files.rename(root, "renamed"), "Storage roots cannot be renamed");
            fails(() -> files.checked(new File(temporary, "storage-sibling/private"), true), "A similarly named sibling is outside the boundary");
            fails(() -> files.checked(new File(root, "../../outside"), true), "Path traversal cannot escape shared storage");
            fails(() -> files.transfer(Collections.singletonList(folder), nested, false, NotebookFiles.NONE), "Copy into a descendant is rejected");
            check(text.isFile() && paper.isFile(), "Rejected operations preserve source files");

            File copies = files.createFolder(root, "copies");
            File standalone = write(new File(root, "paper.PDF"), "%PDF-standalone");
            files.transfer(Arrays.asList(folder, standalone), copies, false, NotebookFiles.NONE);
            check(read(new File(copies, "Physics notes/Résumé 中文.txt")).equals("quantum dynamics"), "Folder copies preserve Unicode names and file contents");
            check(new File(copies, "Physics notes/.hidden").isFile(), "Copy includes hidden folder contents");
            check(new File(copies, "paper.PDF").isFile() && paper.isFile(), "Batch copy preserves every source");
            fails(() -> files.transfer(Collections.singletonList(text), folder, false, NotebookFiles.NONE), "Same-folder paste never overwrites its source");
            File occupied = write(new File(copies, text.getName()), "existing bytes");
            File extra = write(new File(root, "extra.txt"), "extra");
            fails(() -> files.transfer(Arrays.asList(extra, text), copies, false, NotebookFiles.NONE), "A late name conflict preflights the entire batch");
            check(!new File(copies, "extra.txt").exists() && read(occupied).equals("existing bytes"), "Conflicts preserve existing files and avoid partial batch copies");
            fails(() -> files.rename(extra, "copies"), "Rename cannot replace an existing folder");
            File renamed = files.rename(extra, "renamed.txt");
            check(!extra.exists() && read(renamed).equals("extra"), "Rename preserves content");
            File moved = files.createFolder(root, "moved");
            files.transfer(Collections.singletonList(renamed), moved, true, NotebookFiles.NONE);
            check(!renamed.exists() && read(new File(moved, "renamed.txt")).equals("extra"), "Move publishes content and removes its source");

            File secondRoot = new File(temporary, "second-volume"); Files.createDirectory(secondRoot.toPath());
            NotebookFiles volumes = new NotebookFiles(Arrays.asList(root, secondRoot));
            File across = volumes.createFolder(root, "across");
            write(new File(across, "source.txt"), "cross-volume payload");
            write(new File(across, ".hidden"), "hidden payload");
            volumes.transfer(Collections.singletonList(across), secondRoot, true, NotebookFiles.NONE);
            check(!across.exists() && read(new File(secondRoot, "across/source.txt")).equals("cross-volume payload")
                && new File(secondRoot, "across/.hidden").isFile(), "Cross-volume move publishes the complete folder before deleting originals");

            byte[] large = new byte[512 * 1024]; Arrays.fill(large, (byte) 37);
            File source = new File(root, "large.bin"); Files.write(source.toPath(), large);
            final boolean[] cancelled = {false};
            NotebookFiles.Control stop = new NotebookFiles.Control() {
                @Override public void check() throws InterruptedIOException { if (cancelled[0]) throw new InterruptedIOException("cancelled"); }
                @Override public void progress(String name, long bytes) { cancelled[0] = true; }
            };
            fails(() -> files.transfer(Collections.singletonList(source), copies, false, stop), "Cancel interrupts a streamed copy");
            check(source.length() == large.length && !new File(copies, "large.bin").exists(), "Cancelled copy keeps source and publishes no incomplete destination");
            check(Arrays.stream(copies.list()).noneMatch(n -> n.startsWith(".scriflow-copy-")), "Cancelled copy removes its temporary staging file");
            cancelled[0] = false;
            fails(() -> volumes.transfer(Collections.singletonList(source), secondRoot, true, stop), "Cross-volume moves can be cancelled before publication");
            check(source.length() == large.length && !new File(secondRoot, "large.bin").exists(), "Cancelled cross-volume move keeps every original byte");
            final boolean[] changed = {false};
            NotebookFiles.Control mutate = new NotebookFiles.Control() {
                @Override public void check() { }
                @Override public void progress(String name, long bytes) {
                    if (!changed[0]) {
                        changed[0] = true;
                        try { Files.write(source.toPath(), new byte[]{1, 2, 3}); }
                        catch (IOException problem) { throw new AssertionError(problem); }
                    }
                }
            };
            fails(() -> files.transfer(Collections.singletonList(source), copies, false, mutate), "A changing source is detected before publication");
            check(source.exists() && !new File(copies, "large.bin").exists(), "A changed original is kept and its partial copy is removed");

            File outside = write(new File(temporary, "private.txt"), "private");
            File link = new File(folder, "linked-private.txt"); Files.createSymbolicLink(link.toPath(), outside.toPath());
            check(files.list(folder, "", true, NotebookFiles.Sort.NAME, NotebookFiles.NONE).skipped == 1, "Listing skips links into private storage");
            fails(() -> files.checked(link, false), "A link cannot be shared or opened through the model");
            File clean = files.createFolder(root, "clean");
            fails(() -> files.transfer(Collections.singletonList(folder), clean, false, NotebookFiles.NONE), "Recursive copy refuses to follow links");
            check(clean.list().length == 0 && read(outside).equals("private"), "Failed linked copy cleans staging and never touches the link target");
            files.delete(Arrays.asList(folder, nested), NotebookFiles.NONE);
            check(!folder.exists() && read(outside).equals("private"), "Recursive deletion unlinks nested links and keeps their targets");
            files.delete(Collections.singletonList(copies), NotebookFiles.NONE);
            check(!copies.exists(), "Recursive deletion removes copied folders including hidden files");
            System.out.println("NotebookFiles: " + assertions + " assertions passed.");
        } finally {
            Files.walkFileTree(temporary.toPath(), new java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
                @Override public java.nio.file.FileVisitResult visitFile(java.nio.file.Path path, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                    Files.delete(path); return java.nio.file.FileVisitResult.CONTINUE;
                }
                @Override public java.nio.file.FileVisitResult postVisitDirectory(java.nio.file.Path path, IOException error) throws IOException {
                    if (error != null) throw error; Files.delete(path); return java.nio.file.FileVisitResult.CONTINUE;
                }
            });
        }
    }
    private interface Action { void run() throws IOException; }
    private static void fails(Action action, String message) throws IOException {
        try { action.run(); } catch (IOException expected) { check(true, message); return; }
        throw new AssertionError(message);
    }
    private static File write(File file, String value) throws IOException { Files.write(file.toPath(), value.getBytes(StandardCharsets.UTF_8)); return file; }
    private static String read(File file) throws IOException { return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8); }
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
}
