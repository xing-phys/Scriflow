package org.eink.inkprobe.notebook;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/** Local creation time, grouped into six-minute intervals. */
final class NotebookDefaultName {
    private NotebookDefaultName() { }

    static String now() { return at(System.currentTimeMillis(), TimeZone.getDefault()); }

    static String at(long timestamp, TimeZone zone) {
        Calendar time = Calendar.getInstance(zone, Locale.ROOT);
        time.setTimeInMillis(timestamp);
        return String.format(Locale.ROOT, "%02d%02d.%02d%02d%d",
            time.get(Calendar.YEAR) % 100, time.get(Calendar.MONTH) + 1,
            time.get(Calendar.DAY_OF_MONTH), time.get(Calendar.HOUR_OF_DAY),
            time.get(Calendar.MINUTE) / 6);
    }

    static boolean blank(String name) {
        return name == null || name.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c));
    }
}
