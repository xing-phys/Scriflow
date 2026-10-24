package org.eink.inkprobe.notebook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Main-thread viewport pages plus completed ink awaiting ordered worker acknowledgements. */
final class NotebookViewportBuffer {
    private final Map<Integer, NotebookPage> baseline = new LinkedHashMap<>();
    private final Map<Long, Map<Integer, List<NotebookPage.Stroke>>> pending = new LinkedHashMap<>();
    private Map<Integer, NotebookPage> visible = Collections.emptyMap();
    private long nextToken = 1;

    /** Retains immutable strokes; callers may freely reuse their input map and lists afterward. */
    long append(Map<Integer, List<NotebookPage.Stroke>> batch) {
        if (batch == null) throw new IllegalArgumentException("Missing completed ink");
        Map<Integer, List<NotebookPage.Stroke>> copy = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<NotebookPage.Stroke>> entry : batch.entrySet()) {
            checkId(entry.getKey());
            if (!baseline.containsKey(entry.getKey())) throw new IllegalArgumentException("Ink page has not loaded");
            if (entry.getValue() == null) throw new IllegalArgumentException("Missing completed strokes");
            List<NotebookPage.Stroke> strokes = new ArrayList<>(entry.getValue());
            for (NotebookPage.Stroke stroke : strokes)
                if (stroke == null) throw new IllegalArgumentException("Missing completed stroke");
            copy.put(entry.getKey(), Collections.unmodifiableList(strokes));
        }
        if (nextToken == Long.MAX_VALUE) throw new IllegalStateException("Completed ink token limit reached");
        long token = nextToken++;
        pending.put(token, Collections.unmodifiableMap(copy));
        rebuild();
        return token;
    }

    /**
     * The snapshot includes the complete acknowledged model, including ink retained after a
     * failed save. Deliver acknowledgements in submission order, before any later read window.
     */
    void complete(long token, Map<Integer, NotebookPage> acknowledged, List<Integer> keep) {
        if (!pending.containsKey(token)) throw new IllegalArgumentException("Unknown completed ink token");
        Map<Integer, NotebookPage> snapshot = copyPages(acknowledged);
        Set<Integer> retained = copyKeep(keep);
        pending.remove(token);
        baseline.putAll(snapshot);
        prune(retained);
        rebuild();
    }

    /** Merge a worker read without discarding newer completed ink that is still pending. */
    void window(Map<Integer, NotebookPage> snapshot, List<Integer> keep) {
        Map<Integer, NotebookPage> copy = copyPages(snapshot);
        Set<Integer> retained = copyKeep(keep);
        baseline.putAll(copy);
        prune(retained);
        rebuild();
    }

    Map<Integer, NotebookPage> current() { return visible; }

    void clear() {
        baseline.clear(); pending.clear(); visible = Collections.emptyMap();
        // Keep tokens unique across canvas replacement; a stale acknowledgement cannot match
        // a new batch merely because its ordinal was reused after clearing the viewport.
    }

    private void prune(Set<Integer> retained) {
        for (Map<Integer, List<NotebookPage.Stroke>> batch : pending.values()) retained.addAll(batch.keySet());
        baseline.keySet().retainAll(retained);
    }

    private void rebuild() {
        Map<Integer, List<NotebookPage.Stroke>> additions = new LinkedHashMap<>();
        for (Map<Integer, List<NotebookPage.Stroke>> batch : pending.values()) {
            for (Map.Entry<Integer, List<NotebookPage.Stroke>> entry : batch.entrySet()) {
                List<NotebookPage.Stroke> strokes = additions.get(entry.getKey());
                if (strokes == null) { strokes = new ArrayList<>(); additions.put(entry.getKey(), strokes); }
                strokes.addAll(entry.getValue());
            }
        }
        Map<Integer, NotebookPage> result = new LinkedHashMap<>(baseline);
        for (Map.Entry<Integer, List<NotebookPage.Stroke>> entry : additions.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            NotebookPage page = baseline.get(entry.getKey());
            List<NotebookPage.Stroke> strokes = new ArrayList<>(page.strokes);
            strokes.addAll(entry.getValue());
            result.put(entry.getKey(), page.withStrokes(strokes));
        }
        visible = Collections.unmodifiableMap(result);
    }

    private static Map<Integer, NotebookPage> copyPages(Map<Integer, NotebookPage> pages) {
        if (pages == null) throw new IllegalArgumentException("Missing viewport snapshot");
        Map<Integer, NotebookPage> copy = new LinkedHashMap<>();
        for (Map.Entry<Integer, NotebookPage> entry : pages.entrySet()) {
            checkId(entry.getKey());
            if (entry.getValue() == null) throw new IllegalArgumentException("Missing viewport page");
            copy.put(entry.getKey(), entry.getValue());
        }
        return copy;
    }

    private static Set<Integer> copyKeep(List<Integer> keep) {
        if (keep == null) throw new IllegalArgumentException("Missing viewport window");
        Set<Integer> result = new LinkedHashSet<>();
        for (Integer id : keep) { checkId(id); result.add(id); }
        return result;
    }

    private static void checkId(Integer id) {
        if (id == null || id < 1 || id > NotebookIndex.MAX_PAGES)
            throw new IllegalArgumentException("Page is out of range");
    }
}
