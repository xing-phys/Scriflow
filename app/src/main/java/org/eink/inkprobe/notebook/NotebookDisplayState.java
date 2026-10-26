package org.eink.inkprobe.notebook;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/** Main-thread app direction, also delivered to screens waiting in the back stack. */
final class NotebookDisplayState {
    interface Target { void onDisplayFacing(NotebookSensorRotation.Facing facing); }
    private final List<WeakReference<Target>> targets = new ArrayList<>();
    private NotebookSensorRotation.Facing current;
    private long revision;

    NotebookDisplayState(NotebookSensorRotation.Facing initial) {
        if (initial == null) throw new IllegalArgumentException("Missing display direction");
        current = initial;
    }

    NotebookSensorRotation.Facing current() { return current; }

    void attach(Target target) {
        if (target == null) throw new IllegalArgumentException("Missing display target");
        prune();
        boolean registered = false;
        for (WeakReference<Target> reference : targets) registered |= reference.get() == target;
        if (!registered) targets.add(new WeakReference<>(target));
        // New/recreated screens inherit the accepted direction before their first frame.
        target.onDisplayFacing(current);
    }

    void detach(Target target) {
        targets.removeIf(reference -> reference.get() == null || reference.get() == target);
    }

    boolean publish(NotebookSensorRotation.Facing facing) {
        if (facing == null) throw new IllegalArgumentException("Missing display direction");
        if (current == facing) return false;
        current = facing;
        long version = ++revision;
        prune();
        for (WeakReference<Target> reference : new ArrayList<>(targets)) {
            // An Activity callback can register/destroy another screen or publish a newer
            // direction. Never overwrite that newer publication with the older one.
            if (version != revision) break;
            Target target = reference.get();
            if (target != null && targets.contains(reference)) target.onDisplayFacing(facing);
        }
        return true;
    }

    private void prune() { targets.removeIf(reference -> reference.get() == null); }
}
