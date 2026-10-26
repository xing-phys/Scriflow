package org.eink.inkprobe.notebook;

/** Raw orientation samples, hysteresis, pen deferral, and natural-landscape mapping. */
public final class NotebookSensorRotationTest {
    private static int assertions;
    public static void main(String[] args) {
        stableReadingsAndJitter();
        ambiguousAndFlatReadings();
        penDefersRotation();
        allAnglesAndNaturalOrientations();
        realDisplayCalibration();
        System.out.println("NotebookSensorRotation: " + assertions + " assertions passed (sensor sectors and pen deferral).");
    }
    private static void stableReadingsAndJitter() {
        NotebookSensorRotation rotation = new NotebookSensorRotation();
        rotation.sample(270, 0);
        check(rotation.ready(599) == -1, "rotation waits for a stable hold");
        check(rotation.ready(600) == 270, "stationary sample becomes ready without a new event");
        rotation.sample(260, 650);
        check(rotation.ready(650) == 270, "within-sector jitter does not restart stability");
        rotation.sample(180, 700);
        check(rotation.ready(1299) == -1 && rotation.ready(1300) == 180, "new direction gets its own settling period");
        rotation.reset();
        check(rotation.ready(9999) == -1 && rotation.remaining(9999) == -1, "pause discards stale sensor samples");
    }
    private static void ambiguousAndFlatReadings() {
        NotebookSensorRotation rotation = new NotebookSensorRotation();
        rotation.sample(359, 100);
        check(rotation.ready(700) == 0, "upright wraparound");
        rotation.sample(45, 710);
        check(rotation.ready(2000) == -1, "diagonal angle does not chatter");
        rotation.sample(0, 2100); rotation.sample(-1, 2150);
        check(rotation.ready(3000) == -1, "flat/unknown orientation cancels a pending turn");
        rotation.sample(90, 3100);
        check(rotation.remaining(3200) == 500 && rotation.ready(3700) == 90, "valid angle recovers after flat position");
    }
    private static void penDefersRotation() {
        NotebookSensorRotation rotation = new NotebookSensorRotation();
        rotation.sample(270, 0); rotation.setWriting(true, 300);
        rotation.sample(180, 400);
        check(rotation.ready(9000) == -1 && rotation.remaining(9000) == -1, "long writing contact blocks all rotation");
        rotation.setWriting(false, 10000);
        check(rotation.ready(10599) == -1 && rotation.ready(10600) == 180, "lift starts a fresh settling interval");
        rotation.setWriting(true, 10700); rotation.sample(-1, 10800); rotation.setWriting(false, 11000);
        check(rotation.ready(20000) == -1, "flat position at pen lift never reuses an old direction");
    }
    private static void allAnglesAndNaturalOrientations() {
        for (int angle = 0; angle < 360; angle++) {
            NotebookSensorRotation rotation = new NotebookSensorRotation(); rotation.sample(angle, 0);
            int sector = ((angle + 45) / 90 * 90) % 360;
            int distance = Math.abs(angle - sector); distance = Math.min(distance, 360 - distance);
            check(rotation.ready(600) == (distance <= 30 ? sector : -1), "dead zones and wraparound for angle " + angle);
        }
        NotebookSensorRotation.Facing[] portrait = {NotebookSensorRotation.Facing.PORTRAIT,
            NotebookSensorRotation.Facing.REVERSE_LANDSCAPE, NotebookSensorRotation.Facing.REVERSE_PORTRAIT,
            NotebookSensorRotation.Facing.LANDSCAPE};
        NotebookSensorRotation.Facing[] landscape = {NotebookSensorRotation.Facing.LANDSCAPE,
            NotebookSensorRotation.Facing.PORTRAIT, NotebookSensorRotation.Facing.REVERSE_LANDSCAPE,
            NotebookSensorRotation.Facing.REVERSE_PORTRAIT};
        for (int i = 0; i < 4; i++) {
            check(NotebookSensorRotation.facing(i * 90, false) == portrait[i], "portrait-natural mapping " + i);
            check(NotebookSensorRotation.facing(i * 90, true) == landscape[i], "landscape-natural mapping " + i);
        }
    }
    private static void realDisplayCalibration() {
        for (int rotation = 0; rotation < 4; rotation++) {
            boolean sideways = rotation == 1 || rotation == 3;
            check(!NotebookSensorRotation.naturalLandscape(rotation, sideways ? 1872 : 1404, sideways ? 1404 : 1872),
                "portrait-natural real display independent of Activity resources " + rotation);
            check(NotebookSensorRotation.naturalLandscape(rotation, sideways ? 1404 : 1872, sideways ? 1872 : 1404),
                "landscape-natural real display independent of Activity resources " + rotation);
        }
    }
    private static void check(boolean value, String message) {
        assertions++; if (!value) throw new AssertionError(message);
    }
}
