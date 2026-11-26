package org.eink.inkprobe.notebook;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

public final class NotebookDefaultNameTest {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    private static long time(TimeZone zone, int year, int month, int day, int hour, int minute) {
        Calendar value = new GregorianCalendar(zone);
        value.clear(); value.set(year, month - 1, day, hour, minute, 0);
        return value.getTimeInMillis();
    }
    public static void main(String[] args) {
        TimeZone shanghai = TimeZone.getTimeZone("Asia/Shanghai");
        for (int minute = 48; minute <= 53; minute++)
            check(NotebookDefaultName.at(time(shanghai, 2026, 11, 26, 18, minute), shanghai).equals("2611.26188"), "six-minute bucket");
        check(NotebookDefaultName.at(time(shanghai, 2026, 11, 26, 18, 54), shanghai).equals("2611.26189"), "next bucket starts at minute 54");
        check(NotebookDefaultName.at(time(shanghai, 2026, 1, 2, 3, 5), shanghai).equals("2601.02030"), "leading zeroes and 24-hour time");
        check(NotebookDefaultName.at(time(shanghai, 2026, 12, 31, 23, 59), shanghai).equals("2612.31239"), "last bucket of the year");
        long instant = time(shanghai, 2026, 11, 26, 0, 0);
        check(NotebookDefaultName.at(instant, TimeZone.getTimeZone("UTC")).equals("2611.25160"), "local time zone determines the date");
        check(NotebookDefaultName.blank("") && NotebookDefaultName.blank("  \u2003") && !NotebookDefaultName.blank(" name "), "blank names include Unicode spaces");
        System.out.println("NotebookDefaultName: " + checks + " assertions passed.");
    }
}
