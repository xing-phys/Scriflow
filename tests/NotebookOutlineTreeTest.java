package org.eink.inkprobe.notebook;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Collapsing is view state and cannot alter outline destinations or hierarchy. */
public final class NotebookOutlineTreeTest {
    public static void main(String[] args) {
        UUID page = UUID.randomUUID();
        List<NotebookOutline.Entry> entries = Arrays.asList(
            new NotebookOutline.Entry("A", page, 0, 0),
            new NotebookOutline.Entry("B", page, 0, 1),
            new NotebookOutline.Entry("C", page, 0, 3),
            new NotebookOutline.Entry("D", page, 0, 1),
            new NotebookOutline.Entry("E", page, 0, 0));
        Set<UUID> collapsed = new HashSet<>();
        check(NotebookOutlineTree.visible(entries, collapsed).equals(Arrays.asList(0, 1, 2, 3, 4)), "all headings initially visible");
        check(NotebookOutlineTree.hasChildren(entries, 0) && NotebookOutlineTree.hasChildren(entries, 1), "nested headings expose expand controls");
        check(!NotebookOutlineTree.hasChildren(entries, 2) && !NotebookOutlineTree.hasChildren(entries, 4), "leaves have no expand control");
        collapsed.add(entries.get(1).id);
        check(NotebookOutlineTree.visible(entries, collapsed).equals(Arrays.asList(0, 1, 3, 4)), "collapse hides all deeper descendants only");
        collapsed.add(entries.get(0).id);
        check(NotebookOutlineTree.visible(entries, collapsed).equals(Arrays.asList(0, 4)), "ancestor collapse hides its whole branch");
        collapsed.remove(entries.get(0).id);
        check(NotebookOutlineTree.visible(entries, collapsed).equals(Arrays.asList(0, 1, 3, 4)), "reopening preserves nested collapse");
        collapsed.remove(entries.get(1).id);
        check(NotebookOutlineTree.visible(entries, collapsed).size() == entries.size(), "expanding restores every destination");
        System.out.println("NotebookOutlineTree: 7 assertions passed (nested collapse, sibling boundaries and restored expansion).");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
