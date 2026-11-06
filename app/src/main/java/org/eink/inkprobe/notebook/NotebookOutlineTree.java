package org.eink.inkprobe.notebook;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** The persisted outline is a flat preorder list; collapsing never changes it. */
final class NotebookOutlineTree {
    private NotebookOutlineTree() { }

    static boolean hasChildren(List<NotebookOutline.Entry> entries, int index) {
        return index + 1 < entries.size() && entries.get(index + 1).depth > entries.get(index).depth;
    }

    static List<Integer> visible(List<NotebookOutline.Entry> entries, Set<UUID> collapsed) {
        List<Integer> result = new ArrayList<>();
        int hiddenBelow = -1;
        for (int i = 0; i < entries.size(); i++) {
            NotebookOutline.Entry entry = entries.get(i);
            if (hiddenBelow >= 0 && entry.depth > hiddenBelow) continue;
            hiddenBelow = -1;
            result.add(i);
            if (collapsed.contains(entry.id) && hasChildren(entries, i)) hiddenBelow = entry.depth;
        }
        return result;
    }
}
