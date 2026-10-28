package org.eink.inkprobe.notebook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Stable note identities and portable window geometry; editing focus never exchanges panes. */
public final class NotebookWorkspace {
    public enum Position { LEFT, RIGHT, TOP, BOTTOM, FLOATING }
    public static final class Pane {
        public final int notebookId;
        public int page = 1;
        public Position position = Position.FLOATING;
        public boolean collapsed, hasView;
        public float split = .5f, x = .42f, y = .18f, width = .55f, height = .58f;
        /** Zoom is relative to fit width; center is a fraction of the centered page. */
        public float zoom = 1, centerX = .5f, centerY = .5f;
        Pane(int id) { notebookId = id; }
    }
    public static final class Box {
        public final int x, y, width, height;
        Box(int x, int y, int width, int height) { this.x = x; this.y = y; this.width = width; this.height = height; }
    }
    private final List<Pane> panes = new ArrayList<>();
    public List<Pane> panes() { return Collections.unmodifiableList(panes); }
    public Pane find(int id) { for (Pane pane : panes) if (pane.notebookId == id) return pane; return null; }
    public Pane base() { return panes.isEmpty() ? null : panes.get(0); }
    public Pane add(int id, Position position) {
        if (id <= 0 || position == null || find(id) != null) throw new IllegalArgumentException("Invalid note window");
        Pane pane = new Pane(id); panes.add(pane);
        float offset = (panes.size() - 2) * .09f;
        pane.x = (.24f + offset) % .8f; pane.y = (.12f + offset) % .7f;
        place(pane, position); return pane;
    }
    /** Docking a third window floats the previous dock, keeping at most two split notes. */
    public void place(Pane pane, Position position) {
        require(pane);
        if (pane == base() && panes.size() > 1) promoteOther(pane);
        if (position != Position.FLOATING) for (Pane other : panes)
            if (other != base() && other != pane && other.position != Position.FLOATING) other.position = Position.FLOATING;
        pane.position = position;
    }
    public void resize(Pane pane, float delta, boolean reset) {
        require(pane);
        if (floating(pane)) {
            pane.width = reset ? .55f : clamp(pane.width + delta, .3f, .95f);
            pane.height = reset ? .58f : clamp(pane.height + delta, .3f, .95f);
        } else {
            for (Pane dock : panes) if (dock != base() && !dock.collapsed && dock.position != Position.FLOATING)
                dock.split = reset ? .5f : clamp(dock.split + (pane == base() ? -delta : delta), .25f, .75f);
        }
    }
    public boolean floating(Pane pane) { return pane != base() && pane.position == Position.FLOATING; }
    public void minimize(Pane pane) {
        require(pane);
        if (pane == base()) { promoteOther(pane); pane.position = Position.FLOATING; }
        pane.collapsed = true;
    }
    public void close(Pane pane) { require(pane); if (pane == base()) promoteOther(pane); panes.remove(pane); }
    public Pane replacement(Pane pane) {
        for (Pane other : panes) if (other != pane && !other.collapsed) return other;
        return null;
    }
    private void promoteOther(Pane pane) {
        Pane other = replacement(pane);
        if (other == null) return;
        panes.remove(other); panes.add(0, other);
    }
    private void require(Pane pane) { if (!panes.contains(pane)) throw new IllegalArgumentException("Unknown note window"); }
    public Box box(Pane pane, int w, int h) {
        require(pane);
        if (floating(pane)) {
            int fw = Math.max(1, Math.round(w * clamp(pane.width, .3f, .95f)));
            int fh = Math.max(1, Math.round(h * clamp(pane.height, .3f, .95f)));
            return new Box(Math.round(clamp(pane.x, 0, 1) * (w - fw)), Math.round(clamp(pane.y, 0, 1) * (h - fh)), fw, fh);
        }
        Pane dock = null;
        for (Pane other : panes) if (other != base() && !other.collapsed && other.position != Position.FLOATING) { dock = other; break; }
        if (dock == null) return new Box(0, 0, w, h);
        boolean horizontal = dock.position == Position.LEFT || dock.position == Position.RIGHT;
        int side = Math.max(1, Math.round((horizontal ? w : h) * clamp(dock.split, .25f, .75f)));
        boolean secondary = pane == dock;
        switch (dock.position) {
            case LEFT: return secondary ? new Box(0, 0, side, h) : new Box(side, 0, w - side, h);
            case RIGHT: return secondary ? new Box(w - side, 0, side, h) : new Box(0, 0, w - side, h);
            case TOP: return secondary ? new Box(0, 0, w, side) : new Box(0, side, w, h - side);
            default: return secondary ? new Box(0, h - side, w, side) : new Box(0, 0, w, h - side);
        }
    }
    /** A compact, versioned preference value. No document data is stored here. */
    public String encode() {
        StringBuilder value = new StringBuilder("1\n");
        for (Pane p : panes) value.append(p.notebookId).append(',').append(p.page).append(',').append(p.position.name())
            .append(',').append(p.collapsed).append(',').append(p.split).append(',').append(p.x).append(',').append(p.y)
            .append(',').append(p.width).append(',').append(p.height).append(',').append(p.hasView).append(',').append(p.zoom)
            .append(',').append(p.centerX).append(',').append(p.centerY).append('\n');
        return value.toString();
    }
    public void restore(String value) {
        panes.clear();
        if (value == null || !value.startsWith("1\n")) return;
        for (String line : value.substring(2).split("\n")) {
            try {
                String[] f = line.split(",");
                if (f.length != 13) continue;
                int id = Integer.parseInt(f[0]);
                if (id <= 0 || find(id) != null) continue;
                Pane p = new Pane(id); p.page = Math.max(1, Integer.parseInt(f[1])); p.position = Position.valueOf(f[2]);
                p.collapsed = Boolean.parseBoolean(f[3]); p.split = clamp(Float.parseFloat(f[4]), .25f, .75f);
                p.x = clamp(Float.parseFloat(f[5]), 0, 1); p.y = clamp(Float.parseFloat(f[6]), 0, 1);
                p.width = clamp(Float.parseFloat(f[7]), .3f, .95f); p.height = clamp(Float.parseFloat(f[8]), .3f, .95f);
                p.hasView = Boolean.parseBoolean(f[9]); p.zoom = Float.parseFloat(f[10]);
                if (!NotebookPage.finite(p.zoom) || p.zoom <= 0) p.zoom = 1;
                p.centerX = clamp(Float.parseFloat(f[11]), 0, 1); p.centerY = clamp(Float.parseFloat(f[12]), -1, 2);
                panes.add(p);
            } catch (IllegalArgumentException ignored) { /* Ignore only the damaged preference entry. */ }
        }
        Pane dock = null;
        for (Pane p : panes) if (p != base() && p.position != Position.FLOATING) {
            if (dock == null) dock = p; else p.position = Position.FLOATING;
        }
    }
    static float clamp(float value, float low, float high) { return NotebookPage.finite(value) ? Math.max(low, Math.min(high, value)) : low; }
}
