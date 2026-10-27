package org.eink.inkprobe.notebook;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

/** Real-file safety checks for folders, trash, purge/reuse and first-page previews. */
public final class NotebookShelfTest {
    private static final List<File> roots = new ArrayList<>();
    private static int assertions;
    private interface Task { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        try {
            foldersTrashAndPreview();
            permanentDeleteLegacyAndReuse();
            purgeCannotTouchOtherDirectories();
            failedTrashPublicationAndRetry();
            interruptedPurgeSurvivesRestart();
            restoreBackupIntoPurgedSlots();
            catalogValidationAndCapacity();
            System.out.println("NotebookShelf: " + assertions + " assertions passed (real files, purge recovery and backup reuse).");
        } finally { for (File root : roots) NotebookBackup.deleteTree(root); }
    }

    private static void foldersTrashAndPreview() throws Exception {
        File root = root(); NotebookLibrary library = ready(root);
        UUID first = library.current().id; library.append(stroke(12));
        library.createFolder("Quantum dynamics"); int folder = library.folders().get(0).id;
        library.createNotebook("Second", NotebookPaperSize.PRESETS.get(1), folder); library.append(stroke(48));
        UUID second = library.current().id;
        library.addPage(); library.append(stroke(70)); library.saveScrollPosition(2, .4);
        byte[] catalog = bytes(root, "library.nbl"), index = bytes(root, "notebooks/2/book.nbi");
        byte[] ink = bytes(root, "notebooks/2/page.nbk"), scroll = bytes(root, "notebooks/2/scroll.nbs");
        check(library.previewNotebook(1).id.equals(first), "preview reads another notebook's first page");
        check(library.previewNotebook(2).id.equals(second), "preview reads page one even when another page is selected");
        check(library.notebookId() == 2 && library.pageNumber() == 2, "preview keeps book and page selection");
        check(Arrays.equals(catalog, bytes(root, "library.nbl")) && Arrays.equals(index, bytes(root, "notebooks/2/book.nbi")), "previews never publish catalog or page order");
        check(Arrays.equals(ink, bytes(root, "notebooks/2/page.nbk")) && Arrays.equals(scroll, bytes(root, "notebooks/2/scroll.nbs")), "previews preserve ink and scroll bytes");
        library.renameNotebook(2, "Renamed"); library.renameFolder(folder, "Research");
        check(library.entries().get(1).folderId == folder, "rename keeps notebook folder");
        library.trashNotebook(2);
        check(library.current() == null && library.entries().get(1).status == NotebookCatalog.TRASH, "trashing current notebook retires its editor");
        check(Arrays.equals(ink, bytes(root, "notebooks/2/page.nbk")), "trash retains original page bytes");
        check(library.error() == null, "normal trash leaves writer healthy");
        expect(() -> library.previewNotebook(2)); check(library.error() == null, "trash cannot be previewed and preview failures stay local");
        NotebookLibrary cold = catalog(root);
        check(cold.folders().get(0).name.equals("Research") && cold.entries().get(1).folderId == folder, "folders persist through restart");
        check(cold.entries().get(1).status == NotebookCatalog.TRASH, "trash persists through restart");
        cold.restoreNotebook(2); cold.openNotebook(2);
        check(cold.pageCount() == 2 && cold.previewPage(1).id.equals(second), "restore retains page count and identity");
        check(cold.scrollPosition().page == 2 && cold.scrollPosition().fraction == .4, "restore retains scroll anchor");
        cold.moveNotebook(1, folder); cold.trashNotebook(1); cold.deleteFolder(folder);
        check(cold.folders().isEmpty() && cold.entries().get(0).folderId == 0 && cold.entries().get(1).folderId == 0, "removing folder keeps active and trashed notebooks at root");
        cold.restoreNotebook(1); cold.openNotebook(1);
        check(cold.current().id.equals(first) && cold.current().strokes.size() == 1, "restoring after folder removal preserves writing");
    }

    private static void permanentDeleteLegacyAndReuse() throws Exception {
        File root = root(); NotebookLibrary library = ready(root);
        library.append(stroke(11)); library.addPage(); library.append(stroke(22));
        Files.write(new File(root, "source.pdf").toPath(), new byte[]{1, 2, 3});
        library.createNotebook("Keep", NotebookPaperSize.A5); library.append(stroke(66));
        UUID keep = library.current().id; byte[] other = bytes(root, "notebooks/2/page.nbk");
        library.trashNotebook(1); library.permanentlyDeleteNotebook(1);
        check(!new File(root, "page.nbk").exists() && !new File(root, "pages").exists() && !new File(root, "source.pdf").exists(), "legacy purge removes all pages and PDF");
        check(new File(root, "library.nbl").isFile() && Arrays.equals(other, bytes(root, "notebooks/2/page.nbk")), "legacy purge preserves catalog and other notebooks");
        check(library.entries().get(0).status == NotebookCatalog.PURGED && library.availableNotebookId() == 1, "purged legacy slot frees capacity");
        library.createNotebook("Reused", NotebookPaperSize.PRESETS.get(1));
        check(library.notebookId() == 1 && new File(root, "notebooks/1/page.nbk").isFile(), "reused legacy slot is isolated from library metadata");
        check(library.current().strokes.isEmpty() && library.pageCount() == 1 && library.current().paperSize.equals(NotebookPaperSize.PRESETS.get(1)), "reused slot has fresh paper and no old ink");
        library.append(stroke(33)); library.trashNotebook(1); library.permanentlyDeleteNotebook(1);
        check(!new File(root, "notebooks/1").exists(), "purging reused legacy slot removes nested directory");
        library.openNotebook(2); check(library.current().id.equals(keep), "other notebook remains editable after two purges");
        library.trashNotebook(2); library.permanentlyDeleteNotebook(2);
        NotebookLibrary empty = catalog(root);
        check(empty.entries().stream().allMatch(e -> e.status == NotebookCatalog.PURGED), "empty bookshelf stays empty across restart");
        check(!new File(root, "page.nbk").exists(), "opening empty bookshelf never invents a legacy notebook");
        empty.createNotebook("Fresh", NotebookPaperSize.A5); check(empty.current().strokes.isEmpty(), "create works after deleting every notebook");
    }

    private static void purgeCannotTouchOtherDirectories() throws Exception {
        File root = root(); NotebookLibrary library = ready(root);
        library.createNotebook("Delete", NotebookPaperSize.A5); library.trashNotebook(2);
        File outside = root(); Files.write(new File(outside, "keep").toPath(), new byte[]{7});
        NotebookBackup.deleteTree(new File(root, "notebooks/2"));
        Files.createSymbolicLink(new File(root, "notebooks/2").toPath(), outside.toPath());
        expect(() -> library.permanentlyDeleteNotebook(2));
        check(new File(outside, "keep").isFile(), "purge refuses a notebook directory redirected outside its slot");
        Files.delete(new File(root, "notebooks/2").toPath()); library.retry();
        check(library.entries().get(1).status == NotebookCatalog.PURGED, "purge can finish once unsafe path is removed");
    }

    private static void failedTrashPublicationAndRetry() throws Exception {
        File root = root(); NotebookLibrary library = ready(root); library.append(stroke(10));
        byte[] ink = bytes(root, "page.nbk");
        check(new File(root, "library.nbl.tmp").mkdir(), "block catalog publication");
        expect(() -> library.trashNotebook(1));
        check(library.entries().get(0).status == NotebookCatalog.ACTIVE && library.current() != null, "failed trash keeps acknowledged editor and metadata");
        check(Arrays.equals(ink, bytes(root, "page.nbk")), "failed trash never changes ink");
        check(new File(root, "library.nbl.tmp").delete(), "unblock catalog"); library.retry();
        check(library.entries().get(0).status == NotebookCatalog.TRASH && library.current() == null, "retry publishes exact pending trash operation");
    }

    private static void interruptedPurgeSurvivesRestart() throws Exception {
        File root = root(); NotebookLibrary library = ready(root); library.createNotebook("Delete", NotebookPaperSize.A5);
        library.append(stroke(20)); library.trashNotebook(2);
        File outside = root(); Files.write(new File(outside, "keep").toPath(), new byte[]{9});
        File link = new File(root, "notebooks/2/blocked"); Files.createSymbolicLink(link.toPath(), outside.toPath());
        expect(() -> library.permanentlyDeleteNotebook(2));
        check(new NotebookCatalog(root).load().entries.get(1).status == NotebookCatalog.PURGING, "purge intent is durable before partial file removal");
        check(new File(outside, "keep").isFile(), "partial purge cannot follow a child symlink");
        Files.delete(link.toPath()); NotebookLibrary restarted = catalog(root);
        check(restarted.entries().get(1).status == NotebookCatalog.PURGED && !new File(root, "notebooks/2").exists(), "restart finishes interrupted purge exactly once");
        restarted.createNotebook("New", NotebookPaperSize.A5);
        check(restarted.notebookId() == 2 && restarted.current().strokes.isEmpty(), "slot reuse follows completed purge");
    }

    private static void restoreBackupIntoPurgedSlots() throws Exception {
        File source = root(); NotebookLibrary original = ready(source); original.append(stroke(88));
        ByteArrayOutputStream archive = new ByteArrayOutputStream();
        NotebookBackup.write(archive, "Restored", 1, original::previewPage, () -> false);
        File root = root(); NotebookLibrary library = ready(root); library.createFolder("Keep folder");
        library.trashNotebook(1); library.permanentlyDeleteNotebook(1);
        NotebookBackup.Restored restored = NotebookBackup.read(new ByteArrayInputStream(archive.toByteArray()), new File(root(), "stage"), () -> false);
        library.importNotebook(restored); library.load();
        check(library.notebookId() == 1 && library.current().strokes.get(0).points.get(0).x == 88, "backup restore reuses purged legacy slot");
        check(library.folders().size() == 1, "backup restore preserves library folders");
        library.createNotebook("Other", NotebookPaperSize.A5); library.trashNotebook(2); library.permanentlyDeleteNotebook(2);
        restored = NotebookBackup.read(new ByteArrayInputStream(archive.toByteArray()), new File(root(), "stage"), () -> false);
        check(new File(root, "library.nbl.tmp").mkdir(), "interrupt reused-slot restore publication");
        final NotebookBackup.Restored interrupted = restored;
        expect(() -> library.importNotebook(interrupted));
        check(new File(root, "restore.pending").isFile(), "reused-slot restore leaves durable recovery record");
        check(new File(root, "library.nbl.tmp").delete(), "unblock reused-slot restore");
        NotebookLibrary cold = catalog(root); cold.load();
        check(cold.notebookId() == 2 && cold.current().strokes.get(0).points.get(0).x == 88, "restart completes reused-slot backup restore");
        check(cold.entries().size() == 2 && cold.folders().size() == 1, "restore recovery neither duplicates slots nor discards folders");
    }

    private static void catalogValidationAndCapacity() throws Exception {
        NotebookCatalog catalog = new NotebookCatalog(root()); catalog.load();
        List<NotebookCatalog.Folder> folders = Collections.singletonList(new NotebookCatalog.Folder(1, "Research"));
        List<NotebookCatalog.Entry> entries = new ArrayList<>(); entries.add(new NotebookCatalog.Entry(1, "First", 1, NotebookCatalog.ACTIVE));
        catalog.save(entries, 1, folders);
        expect(() -> catalog.save(entries, 1, Collections.emptyList()));
        expect(() -> catalog.save(entries, 1, Arrays.asList(new NotebookCatalog.Folder(1, "A"), new NotebookCatalog.Folder(2, "a"))));
        for (int id = 2; id <= NotebookCatalog.MAX_NOTEBOOKS; id++) {
            entries.add(new NotebookCatalog.Entry(id, "Note " + id)); catalog.save(entries, 1, folders);
        }
        check(catalog.current().availableId() == 0, "full library blocks new allocation");
        entries.set(50, entries.get(50).withStatus(NotebookCatalog.TRASH)); catalog.save(entries, 1, folders);
        check(catalog.current().availableId() == 0, "trash retains its capacity until permanently deleted");
        entries.set(50, entries.get(50).withStatus(NotebookCatalog.PURGED)); catalog.save(entries, 1, folders);
        check(catalog.current().availableId() == 51, "permanent deletion reclaims a slot even at capacity");
    }

    private static NotebookLibrary ready(File root) throws Exception {
        NotebookLibrary library = catalog(root); library.load(); library.initializeSize(100, 120); return library;
    }
    private static NotebookLibrary catalog(File root) throws Exception { NotebookLibrary library = new NotebookLibrary(root); library.loadCatalog(); return library; }
    private static File root() throws Exception { File root = Files.createTempDirectory("notebook-shelf-").toFile(); roots.add(root); return root; }
    private static byte[] bytes(File root, String name) throws Exception { return Files.readAllBytes(new File(root, name).toPath()); }
    private static NotebookPage.Stroke stroke(float x) { return new NotebookPage.Stroke(2, 3, 0xff000000, Collections.singletonList(new NotebookPage.Point(x, 20, 100, 1))); }
    private static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
    private static void expect(Task task) throws Exception { try { task.run(); } catch (IOException expected) { assertions++; return; } throw new AssertionError("Expected IOException"); }
}
