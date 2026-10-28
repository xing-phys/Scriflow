package org.eink.inkprobe.notebook;

public final class NotebookWorkspaceTest {
    private static int assertions;
    public static void main(String[] args) {
        geometry(); windows(); persistence();
        System.out.println("NotebookWorkspace: " + assertions + " assertions passed (multiple windows, two-pane limit, minimization and persistence).");
    }
    private static void geometry() {
        for (int w : new int[]{320, 1404, 1872}) for (int h : new int[]{480, 1872, 1404}) {
            for (NotebookWorkspace.Position position : NotebookWorkspace.Position.values()) for (float split : new float[]{0, .25f, .5f, .75f, 1, Float.NaN}) {
                NotebookWorkspace state = new NotebookWorkspace();
                NotebookWorkspace.Pane first = state.add(1, NotebookWorkspace.Position.FLOATING), second = state.add(2, position);
                second.split = split;
                NotebookWorkspace.Box a = state.box(first, w, h), b = state.box(second, w, h);
                check(a.width > 0 && a.height > 0 && b.width > 0 && b.height > 0, "Both panes remain usable");
                check(b.x >= 0 && b.y >= 0 && b.x + b.width <= w && b.y + b.height <= h, "Note stays in app bounds");
                if (position != NotebookWorkspace.Position.FLOATING) {
                    check((long) a.width * a.height + (long) b.width * b.height == (long) w * h, "Split fills the screen");
                    check(a.x + a.width <= b.x || b.x + b.width <= a.x || a.y + a.height <= b.y || b.y + b.height <= a.y, "Split never overlaps");
                } else check(a.width == w && a.height == h, "Floating note leaves the base full screen");
                state.minimize(second);
                check(state.box(first, w, h).width == w && state.box(first, w, h).height == h, "Minimizing dock frees the full screen");
                second.collapsed = false;
                box(b, state.box(second, w, h), "Restore keeps placement");
            }
        }
    }
    private static void windows() {
        NotebookWorkspace state = new NotebookWorkspace();
        NotebookWorkspace.Pane first = state.add(1, NotebookWorkspace.Position.FLOATING), second = state.add(2, NotebookWorkspace.Position.RIGHT);
        NotebookWorkspace.Pane third = state.add(3, NotebookWorkspace.Position.FLOATING), fourth = state.add(4, NotebookWorkspace.Position.FLOATING);
        check(state.panes().size() == 4 && state.floating(third) && state.floating(fourth), "Several floating notes coexist with a split");
        NotebookWorkspace.Box original = state.box(second, 1404, 1872);
        // Focus belongs to the writer, so finding any pane never exchanges identities or geometry.
        check(state.find(3) == third && state.find(1) == first && state.base() == first, "Lookup retains stable pane identities");
        box(original, state.box(second, 1404, 1872), "Focusing another note cannot move a split");
        state.place(third, NotebookWorkspace.Position.TOP);
        check(state.floating(second) && !state.floating(third), "Docking a third note floats the previous dock");
        state.resize(first, -.1f, false);
        check(Math.abs(third.split - .6f) < .001f, "Resizing primary adjusts its split complement");
        state.resize(third, 0, true); check(third.split == .5f, "Equal split restores both panes");
        state.resize(fourth, .1f, false); check(Math.abs(fourth.width - .65f) < .001f, "Floating size presets remain independent");
        state.minimize(third); state.minimize(fourth);
        check(third.collapsed && fourth.collapsed && state.replacement(first) == second, "Several minimized notes retain independent entries");
        third.collapsed = false;
        check(!state.floating(third) && fourth.collapsed, "Restoring one note leaves other minimized notes hidden");
        state.minimize(first);
        check(state.base() == second && first.collapsed && state.floating(first), "Minimizing the base promotes a visible note");
        state.close(second);
        check(state.base() == third && state.find(2) == null && state.find(4) == fourth, "Closing base keeps other windows");
        state.place(third, NotebookWorkspace.Position.LEFT);
        check(state.base() == third, "A base with no other visible notes still fills the workspace");
        try { state.add(4, NotebookWorkspace.Position.FLOATING); throw new AssertionError("Duplicate note"); }
        catch (IllegalArgumentException expected) { assertions++; }
    }
    private static void persistence() {
        NotebookWorkspace state = new NotebookWorkspace();
        state.add(1, NotebookWorkspace.Position.FLOATING);
        NotebookWorkspace.Pane pane = state.add(2, NotebookWorkspace.Position.LEFT);
        pane.collapsed = true; pane.page = 7; pane.hasView = true; pane.zoom = 3.25f; pane.centerX = .42f; pane.centerY = 1.1f;
        state.add(3, NotebookWorkspace.Position.FLOATING).width = .72f;
        NotebookWorkspace restored = new NotebookWorkspace(); restored.restore(state.encode());
        check(state.encode().equals(restored.encode()), "All windows, minimized state, geometry and reading positions round-trip");
        check(restored.find(2).zoom == 3.25f && restored.find(2).centerY == 1.1f && restored.find(2).page == 7, "Zoom and page center survive recreation");
        pane.zoom = .1f; restored.restore(state.encode()); check(restored.find(2).zoom == .1f, "Minimum editor pinch zoom survives restoration");
        pane.zoom = 12; restored.restore(state.encode()); check(restored.find(2).zoom == 12, "Height-fit zoom above eight survives restoration");
        restored.restore("1\nmalformed\n" + state.encode().substring(2));
        check(restored.panes().size() == 3, "Damaged preference entry does not discard other notes");
        restored.restore("unknown"); check(restored.panes().isEmpty(), "Unknown layout version starts empty");
    }
    private static void box(NotebookWorkspace.Box a, NotebookWorkspace.Box b, String message) { check(a.x == b.x && a.y == b.y && a.width == b.width && a.height == b.height, message); }
    private static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
}
