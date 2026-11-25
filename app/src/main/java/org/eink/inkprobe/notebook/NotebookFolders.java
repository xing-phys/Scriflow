package org.eink.inkprobe.notebook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Shared, display-only navigation of the validated bookshelf hierarchy. */
public final class NotebookFolders {
    private NotebookFolders() { }
    public static NotebookCatalog.Folder find(List<NotebookCatalog.Folder> folders, int id) {
        for (NotebookCatalog.Folder folder : folders) if (folder.id == id) return folder;
        return null;
    }
    public static String path(List<NotebookCatalog.Folder> folders, int id) {
        List<String> parts = new ArrayList<>();
        for (int depth = 0; id != 0 && depth <= folders.size(); depth++) {
            NotebookCatalog.Folder folder = find(folders, id);
            if (folder == null) break;
            parts.add(folder.name); id = folder.parentId;
        }
        Collections.reverse(parts);
        return String.join(" / ", parts);
    }
}
