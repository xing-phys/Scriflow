package org.eink.inkprobe.notebook;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.HashSet;
import java.util.function.ToIntFunction;

/** The persisted outline is a flat preorder list; collapsing never changes it. */
final class NotebookOutlineTree {
    private NotebookOutlineTree() { }

    static boolean hasChildren(List<NotebookOutline.Entry> entries, int index) {
        return index + 1 < entries.size() && entries.get(index + 1).depth > entries.get(index).depth;
    }

    /** Latest destination at or before the top of the visible page. */
    static int current(List<NotebookOutline.Entry> entries, ToIntFunction<UUID> pageNumber,
                       int page, double fraction) {
        int best = -1, bestPage = 0; double bestFraction = -1;
        for (int i = 0; i < entries.size(); i++) {
            NotebookOutline.Entry entry = entries.get(i); int target = pageNumber.applyAsInt(entry.pageId);
            if (target < 1 || target > page || target == page && entry.fraction > fraction) continue;
            if (target > bestPage || target == bestPage && entry.fraction >= bestFraction) {
                best = i; bestPage = target; bestFraction = entry.fraction;
            }
        }
        return best;
    }

    /** Collapse other branches while showing the current heading and its immediate children. */
    static HashSet<UUID> collapsedByDefault(List<NotebookOutline.Entry> entries, int current) {
        HashSet<UUID> collapsed = new HashSet<>();
        for (int i = 0; i < entries.size(); i++) if (hasChildren(entries, i)) collapsed.add(entries.get(i).id);
        if (current < 0 || current >= entries.size()) return collapsed;
        NotebookOutline.Entry selected = entries.get(current); collapsed.remove(selected.id);
        int depth = selected.depth;
        for (int i = current - 1; i >= 0 && depth > 0; i--) {
            NotebookOutline.Entry entry = entries.get(i);
            if (entry.depth < depth) { collapsed.remove(entry.id); depth = entry.depth; }
        }
        return collapsed;
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
