package org.eink.inkprobe.notebook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.UUID;

/** Browser-style view history, independent of ink undo and durable scroll state. */
public final class NotebookViewHistory {
    public enum Navigation { PDF_LINK, OUTLINE, SCROLL, PAN, ZOOM, PAGE_BUTTON, PAGE_OVERVIEW, ADD_PAGE }
    private static final int LIMIT = 100;
    private static final int SNAPSHOT_MAGIC = 0x4e564831; // NVH1
    private static final int SNAPSHOT_LIMIT = 32 * 1024;
    private final Deque<State> back = new ArrayDeque<>(), forward = new ArrayDeque<>();

    public static final class State {
        public final UUID pageId;
        public final float x, y, zoom;
        public final String fit;
        public State(UUID pageId, float x, float y, float zoom, String fit) {
            if (pageId == null || fit == null || !NotebookPage.finite(x) || !NotebookPage.finite(y)
                    || !NotebookPage.finite(zoom) || zoom <= 0) throw new IllegalArgumentException("Invalid notebook view");
            this.pageId = pageId; this.x = x; this.y = y; this.zoom = zoom; this.fit = fit;
        }
        public NotebookViewport.Fit fitMode() {
            try { return NotebookViewport.Fit.valueOf(fit); }
            catch (IllegalArgumentException unknown) { return NotebookViewport.Fit.CUSTOM; }
        }
        /** Fit modes follow the current display and destination page; custom percentages stay exact. */
        public float zoomFor(NotebookViewport viewport, int pageNumber) {
            NotebookViewport.Fit mode = fitMode();
            return mode == NotebookViewport.Fit.CUSTOM ? zoom : viewport.fittedZoom(mode, pageNumber);
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof State)) return false;
            State state = (State) other;
            return pageId.equals(state.pageId) && x == state.x && y == state.y && zoom == state.zoom && fit.equals(state.fit);
        }
        @Override public int hashCode() { return Objects.hash(pageId, x, y, zoom, fit); }
    }

    /** Capture the outgoing view only for a PDF link or outline jump. Scrolling, zoom, and ordinary page changes do not record. */
    public void record(State current) {
        require(current); push(back, current); forward.clear();
    }
    /** A central policy keeps normal reading movements out of link/outline return history. */
    public void record(State current, Navigation navigation) {
        if (navigation == null) throw new IllegalArgumentException("Missing navigation cause");
        if (navigation == Navigation.PDF_LINK || navigation == Navigation.OUTLINE) record(current);
    }
    public boolean canBack() { return !back.isEmpty(); }
    public boolean canForward() { return !forward.isEmpty(); }
    public State back(State current) {
        require(current); if (back.isEmpty()) return null;
        State target = back.removeLast(); push(forward, current); return target;
    }
    public State forward(State current) {
        require(current); if (forward.isEmpty()) return null;
        State target = forward.removeLast(); push(back, current); return target;
    }
    public void clear() { back.clear(); forward.clear(); }

    /** Small, notebook-scoped Activity state; page UUIDs remain stable across layout changes. */
    public byte[] save(int notebookId) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(SNAPSHOT_MAGIC); out.writeInt(notebookId);
            writeStack(out, back); writeStack(out, forward);
            out.flush();
            return bytes.size() <= SNAPSHOT_LIMIT ? bytes.toByteArray() : null;
        } catch (IOException invalid) {
            // Activity recreation must still succeed if a future view has an oversized label.
            return null;
        }
    }

    /** Ignore stale or invalid saved UI state instead of blocking access to the notebook. */
    public boolean restore(int notebookId, byte[] snapshot) {
        clear();
        if (snapshot == null || snapshot.length > SNAPSHOT_LIMIT) return false;
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(snapshot));
            if (in.readInt() != SNAPSHOT_MAGIC || in.readInt() != notebookId) return false;
            Deque<State> restoredBack = readStack(in), restoredForward = readStack(in);
            if (in.read() != -1) return false;
            back.addAll(restoredBack); forward.addAll(restoredForward);
            return true;
        } catch (IOException | IllegalArgumentException invalid) {
            return false;
        }
    }

    private static void writeStack(DataOutputStream out, Deque<State> stack) throws IOException {
        out.writeInt(stack.size());
        for (State state : stack) {
            out.writeLong(state.pageId.getMostSignificantBits()); out.writeLong(state.pageId.getLeastSignificantBits());
            out.writeFloat(state.x); out.writeFloat(state.y); out.writeFloat(state.zoom); out.writeUTF(state.fit);
        }
    }

    private static Deque<State> readStack(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > LIMIT) throw new IOException("Invalid view history size");
        Deque<State> stack = new ArrayDeque<>();
        for (int i = 0; i < count; i++) {
            UUID page = new UUID(in.readLong(), in.readLong());
            stack.addLast(new State(page, in.readFloat(), in.readFloat(), in.readFloat(), in.readUTF()));
        }
        return stack;
    }

    private static void require(State state) { if (state == null) throw new IllegalArgumentException("Missing notebook view"); }
    private static void push(Deque<State> stack, State state) {
        if (state.equals(stack.peekLast())) return;
        stack.addLast(state); if (stack.size() > LIMIT) stack.removeFirst();
    }
}
