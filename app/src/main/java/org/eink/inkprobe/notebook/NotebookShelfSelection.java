package org.eink.inkprobe.notebook;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Notebook selection is scoped to the visible shelf or Trash. */
final class NotebookShelfSelection {
    private final Set<Integer> selected = new LinkedHashSet<>();
    private final Set<String> books = new LinkedHashSet<>();
    private boolean active;
    boolean active() { return active; }
    int size() { return selected.size()+books.size(); }
    void start() { active = true; }
    void clear() { active = false; selected.clear();books.clear(); }
    boolean contains(Object item) { return item instanceof NotebookEbookStore.Book?books.contains(((NotebookEbookStore.Book)item).id):selectable(item) && selected.contains(((NotebookCatalog.Entry) item).id); }
    void toggle(Object item) {
        if (!selectable(item)) return;
        if(item instanceof NotebookEbookStore.Book){active=true;String id=((NotebookEbookStore.Book)item).id;if(!books.add(id))books.remove(id);return;}
        active = true; int id = ((NotebookCatalog.Entry) item).id; if (!selected.add(id)) selected.remove(id);
    }
    void selectAll(List<Object> items) {
        active = true; for (Object item : items) if(item instanceof NotebookEbookStore.Book)books.add(((NotebookEbookStore.Book)item).id);else if (selectable(item)) selected.add(((NotebookCatalog.Entry) item).id);
    }
    void retain(List<Object> items) {
        Set<Integer> visible = new LinkedHashSet<>();
        Set<String> visibleBooks=new LinkedHashSet<>();
        for (Object item : items) if(item instanceof NotebookEbookStore.Book)visibleBooks.add(((NotebookEbookStore.Book)item).id);else if (selectable(item)) visible.add(((NotebookCatalog.Entry) item).id);
        selected.retainAll(visible);
        books.retainAll(visibleBooks);
    }
    List<Integer> notebooks() { return new ArrayList<>(selected); }
    List<String> ebooks(){return new ArrayList<>(books);}
    String[] bookSnapshot(){return books.toArray(new String[0]);}
    void restoreBooks(String[] values){if(values!=null)for(String value:values)if(value!=null&&value.matches("[a-f0-9]{64}")){books.add(value);active=true;}}
    int[] snapshot() { int[] result = new int[selected.size()]; int i = 0; for (int id : selected) result[i++] = id; return result; }
    void restore(int[] values) {
        clear(); if (values == null) return; active = true;
        for (int value : values) if (value >= 1 && value <= NotebookCatalog.MAX_NOTEBOOKS) selected.add(value);
    }
    static boolean selectable(Object item) {
        if(item instanceof NotebookEbookStore.Book)return true;
        if (!(item instanceof NotebookCatalog.Entry)) return false;
        int status = ((NotebookCatalog.Entry) item).status;
        return status == NotebookCatalog.ACTIVE || status == NotebookCatalog.TRASH;
    }
}
