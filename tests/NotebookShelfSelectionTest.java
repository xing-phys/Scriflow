package org.eink.inkprobe.notebook;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

/** Selection is scoped to visible notebooks; reversible batches publish together. */
public final class NotebookShelfSelectionTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        NotebookCatalog.Entry first = new NotebookCatalog.Entry(1, "First");
        NotebookCatalog.Entry second = new NotebookCatalog.Entry(2, "Second");
        NotebookCatalog.Folder folder = new NotebookCatalog.Folder(1, "Folder");
        List<Object> items = Arrays.asList(folder, first, second);
        NotebookShelfSelection selection = new NotebookShelfSelection();
        selection.toggle(first); selection.toggle(second);
        check(selection.active() && selection.size() == 2 && selection.contains(first), "long press and tap select distinct notebooks");
        selection.toggle(first); check(!selection.contains(first) && selection.contains(second), "tapping a selected item removes it");
        selection.selectAll(items); check(selection.size() == 2, "select all targets visible notebooks, not folders");
        NotebookShelfSelection restored = new NotebookShelfSelection(); restored.restore(selection.snapshot());
        restored.retain(Arrays.asList(second)); check(restored.size() == 1 && restored.contains(second), "rotation restores selection and removes items no longer visible");
        restored.clear(); check(!restored.active() && restored.size() == 0, "cancel leaves selection mode");

        File root = Files.createTempDirectory("shelf-selection-").toFile();
        try {
            NotebookLibrary library = new NotebookLibrary(root); library.load();
            library.createNotebook("Second", NotebookPaperSize.A5); library.createNotebook("Third", NotebookPaperSize.A5);
            library.createFolder("Research"); int folderId = library.folders().get(0).id;
            library.manageNotebooks(Arrays.asList(1, 2), NotebookLibrary.BulkAction.MOVE, folderId);
            check(library.entries().get(0).folderId == folderId && library.entries().get(1).folderId == folderId, "moving selection publishes both destinations");
            library.manageNotebooks(Arrays.asList(1, 2), NotebookLibrary.BulkAction.TRASH, 0);
            check(library.entries().get(0).status == NotebookCatalog.TRASH && library.entries().get(1).status == NotebookCatalog.TRASH, "both notebooks enter recoverable Trash");
            byte[] before = Files.readAllBytes(new File(root, "library.nbl").toPath());
            try { library.manageNotebooks(Arrays.asList(1, 3), NotebookLibrary.BulkAction.RESTORE, 0); throw new AssertionError("invalid mixed batch"); }
            catch (java.io.IOException expected) { }
            check(Arrays.equals(before, Files.readAllBytes(new File(root, "library.nbl").toPath())), "invalid selection publishes no partial batch");
            library.retry(); library.manageNotebooks(Arrays.asList(1, 2), NotebookLibrary.BulkAction.RESTORE, 0);
            check(library.entries().get(0).status == NotebookCatalog.ACTIVE && library.entries().get(1).status == NotebookCatalog.ACTIVE, "both notebooks restore to their original folder");
            check(library.entries().get(0).folderId == folderId && library.entries().get(1).folderId == folderId, "restore preserves folder assignments");
        } finally { NotebookBackup.deleteTree(root); }
        System.out.println("NotebookShelfSelection: " + checks + " assertions passed (selection, rotation and atomic reversible batches). ");
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
