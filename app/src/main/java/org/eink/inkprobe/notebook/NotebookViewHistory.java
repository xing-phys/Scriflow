package org.eink.inkprobe.notebook;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.UUID;

/** Process-local browser-style view history, independent of ink undo and durable scroll state. */
public final class NotebookViewHistory {
    private static final int LIMIT = 100;
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
        @Override public boolean equals(Object other) {
            if (!(other instanceof State)) return false;
            State state = (State) other;
            return pageId.equals(state.pageId) && x == state.x && y == state.y && zoom == state.zoom && fit.equals(state.fit);
        }
        @Override public int hashCode() { return Objects.hash(pageId, x, y, zoom, fit); }
    }

    /** Capture the outgoing view immediately before a user follows a destination. */
    public void record(State current) {
        require(current); push(back, current); forward.clear();
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
    private static void require(State state) { if (state == null) throw new IllegalArgumentException("Missing notebook view"); }
    private static void push(Deque<State> stack, State state) {
        if (state.equals(stack.peekLast())) return;
        stack.addLast(state); if (stack.size() > LIMIT) stack.removeFirst();
    }
}
