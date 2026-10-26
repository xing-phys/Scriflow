package org.eink.inkprobe.notebook;

import static org.eink.inkprobe.notebook.NotebookSensorRotation.Facing.*;

/** Navigation regressions: returning screens inherit direction without another sensor wait. */
public final class NotebookDisplayStateTest {
    private static int assertions;
    private static final class Screen implements NotebookDisplayState.Target {
        NotebookSensorRotation.Facing requested;
        int updates;
        @Override public void onDisplayFacing(NotebookSensorRotation.Facing facing) { requested = facing; updates++; }
    }

    public static void main(String[] args) {
        shelfAlreadyMatchesBeforeReturn();
        allScreensAndReverseDirections();
        recreationAndRegistration();
        sensorSettlingOnlyForPhysicalChanges();
        reentrantScreenLifecycle();
        System.out.println("NotebookDisplayState: " + assertions + " assertions passed (shared direction and navigation).");
    }

    private static void shelfAlreadyMatchesBeforeReturn() {
        NotebookDisplayState app = new NotebookDisplayState(PORTRAIT);
        Screen shelf = new Screen(), note = new Screen();
        app.attach(shelf); app.attach(note);
        check(shelf.requested == PORTRAIT && note.requested == PORTRAIT, "initial shared portrait");
        app.publish(LANDSCAPE);
        check(note.requested == LANDSCAPE, "foreground note rotates");
        check(shelf.requested == LANDSCAPE, "background shelf updated before any resume or sensor event");
        int updates = shelf.updates;
        app.attach(shelf); // Resume the existing library Activity.
        check(shelf.requested == LANDSCAPE, "returning shelf starts in accepted landscape");
        check(shelf.updates == updates + 1, "resume reapplies current direction directly");
        check(!app.publish(LANDSCAPE), "same physical direction is not a new global rotation");
        check(shelf.updates == updates + 1, "same sensor sector causes no extra publication");
    }

    private static void allScreensAndReverseDirections() {
        NotebookDisplayState app = new NotebookDisplayState(PORTRAIT);
        Screen[] screens = {new Screen(), new Screen(), new Screen(), new Screen(), new Screen()};
        for (Screen screen : screens) app.attach(screen);
        for (NotebookSensorRotation.Facing direction : new NotebookSensorRotation.Facing[]{LANDSCAPE,
                REVERSE_PORTRAIT, REVERSE_LANDSCAPE, PORTRAIT}) {
            check(app.publish(direction), "direction accepted " + direction);
            for (Screen screen : screens) check(screen.requested == direction, "every production screen synchronized " + direction);
            Screen newScreen = new Screen(); app.attach(newScreen);
            check(newScreen.requested == direction, "new screen inherits exact direction including reverse");
            app.detach(newScreen);
        }
    }

    private static void recreationAndRegistration() {
        NotebookDisplayState app = new NotebookDisplayState(LANDSCAPE);
        Screen old = new Screen(); app.attach(old); app.attach(old); // create + resume
        int previous = old.updates;
        app.publish(PORTRAIT);
        check(old.updates == previous + 1, "registration is not duplicated on resume");
        app.detach(old); app.publish(REVERSE_LANDSCAPE);
        check(old.requested == PORTRAIT, "destroyed screen no longer receives directions");
        Screen recreated = new Screen(); app.attach(recreated);
        check(recreated.requested == REVERSE_LANDSCAPE, "recreated screen does not reuse stale portrait");
        NotebookDisplayState restarted = new NotebookDisplayState(app.current());
        Screen restored = new Screen(); restarted.attach(restored);
        check(restored.requested == REVERSE_LANDSCAPE, "restored app direction applies before sensor startup");
    }

    private static void sensorSettlingOnlyForPhysicalChanges() {
        NotebookDisplayState app = new NotebookDisplayState(PORTRAIT);
        NotebookSensorRotation sensor = new NotebookSensorRotation();
        Screen note = new Screen(), shelf = new Screen(); app.attach(note); app.attach(shelf);
        sensor.sample(270, 0);
        check(sensor.ready(599) < 0 && shelf.requested == PORTRAIT, "physical turn still waits for stability");
        app.publish(NotebookSensorRotation.facing(sensor.ready(600), false));
        check(note.requested == LANDSCAPE && shelf.requested == LANDSCAPE, "one accepted turn updates both screens");
        sensor.reset(); // Leaving the editor stops its listener, as before.
        app.attach(shelf);
        check(sensor.ready(600) < 0 && shelf.requested == LANDSCAPE, "navigation needs no new sensor reading");
        sensor.sample(-1, 700);
        check(shelf.requested == LANDSCAPE, "flat tablet keeps the shared direction when navigating");
    }

    private static void reentrantScreenLifecycle() {
        NotebookDisplayState app = new NotebookDisplayState(PORTRAIT);
        Screen leaving = new Screen(), following = new Screen();
        NotebookDisplayState.Target callback = facing -> {
            if (facing == LANDSCAPE) { app.detach(leaving); app.attach(following); }
        };
        app.attach(callback); app.attach(leaving);
        app.publish(LANDSCAPE);
        check(leaving.requested == PORTRAIT, "detached screen is skipped during publication");
        check(following.requested == LANDSCAPE, "screen created during publication inherits new direction");

        NotebookDisplayState nested = new NotebookDisplayState(PORTRAIT);
        NotebookDisplayState.Target publisher = facing -> {
            if (facing == LANDSCAPE) nested.publish(REVERSE_LANDSCAPE);
        };
        Screen observer = new Screen(); nested.attach(publisher); nested.attach(observer);
        nested.publish(LANDSCAPE);
        check(nested.current() == REVERSE_LANDSCAPE && observer.requested == REVERSE_LANDSCAPE,
            "newer direction cannot be overwritten by an earlier callback batch");
    }

    private static void check(boolean condition, String message) {
        assertions++; if (!condition) throw new AssertionError(message);
    }
}
