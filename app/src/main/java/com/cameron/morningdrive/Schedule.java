package com.cameron.morningdrive;

import java.time.*;

/** Pure scheduling logic, local wall-clock time; one departure per local date. */
public final class Schedule {
    public static long at(LocalDate day, int hour, int minute, ZoneId zone) {
        return day.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli();
    }
    public static boolean selected(LocalDate day, int mask) {
        return (mask & (1 << (day.getDayOfWeek().getValue() - 1))) != 0;
    }
    public static long due(long now, ZoneId zone, int hour, int minute, int mask,
                           long eligibleAfter, long handledDay) {
        LocalDate day = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        long event = at(day, hour, minute, zone);
        return selected(day, mask) && event <= now && event >= eligibleAfter
                && handledDay != day.toEpochDay() ? event : 0;
    }
    public static long next(long now, ZoneId zone, int hour, int minute, int mask,
                            long eligibleAfter, long handledDay) {
        LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        for (int i=0; i<=7; i++) {
            LocalDate day = today.plusDays(i);
            long event = at(day, hour, minute, zone);
            if (selected(day, mask) && event > now && event >= eligibleAfter
                    && handledDay != day.toEpochDay()) return event;
        }
        return 0;
    }
}
