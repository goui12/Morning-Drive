package com.cameron.morningdrive;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** JVM regression checks for scheduling boundaries; no Android or test framework dependency. */
public final class ScheduleTest {
    private static final ZoneId LA = ZoneId.of("America/Los_Angeles");
    private static final int WEEKDAYS = 31;
    private static final long NEVER = Long.MIN_VALUE;
    private static int checks;
    private static long utc(String value) { return Instant.parse(value).toEpochMilli(); }
    private static long day(String value) { return LocalDate.parse(value).toEpochDay(); }
    private static void eq(String name, long expected, long actual) {
        checks++;
        if (expected != actual) throw new AssertionError(name + ": expected " + expected + ", got " + actual);
    }
    public static void main(String[] args) {
        long fri7 = utc("2026-11-13T15:00:00Z");
        long fri701 = utc("2026-11-13T15:01:00Z");
        long fri6 = utc("2026-11-13T14:00:00Z");
        long mon7 = utc("2026-11-16T15:00:00Z");
        eq("before departure is not due", 0, Schedule.due(fri6, LA, 7, 0, WEEKDAYS, 0, NEVER));
        eq("at departure is due", fri7, Schedule.due(fri7, LA, 7, 0, WEEKDAYS, 0, NEVER));
        eq("unlock at 7:01 retains 7:00 occurrence", fri7, Schedule.due(fri701, LA, 7, 0, WEEKDAYS, 0, NEVER));
        eq("late evening is still pending", fri7, Schedule.due(utc("2026-11-14T07:59:59Z"), LA, 7, 0, WEEKDAYS, 0, NEVER));
        eq("pending expires at local midnight", 0, Schedule.due(utc("2026-11-14T08:00:00Z"), LA, 7, 0, WEEKDAYS, 0, NEVER));
        eq("Saturday excluded", 0, Schedule.due(utc("2026-11-14T16:00:00Z"), LA, 7, 0, WEEKDAYS, 0, NEVER));
        eq("next before departure", fri7, Schedule.next(fri6, LA, 7, 0, WEEKDAYS, 0, NEVER));
        eq("next skips the weekend", mon7, Schedule.next(fri7, LA, 7, 0, WEEKDAYS, 0, NEVER));
        eq("duplicate unlock cannot repeat handled day", 0, Schedule.due(fri701, LA, 7, 0, WEEKDAYS, 0, day("2026-11-13")));
        eq("handled previous day does not block today", fri7, Schedule.due(fri701, LA, 7, 0, WEEKDAYS, 0, day("2026-11-12")));
        eq("enable after departure does not catch stale trip", 0, Schedule.due(fri701, LA, 7, 0, WEEKDAYS, fri7 + 1, NEVER));
        eq("enable before departure catches trip", fri7, Schedule.due(fri701, LA, 7, 0, WEEKDAYS, fri6, NEVER));
        eq("enable after departure finds next selected day", mon7, Schedule.next(fri701, LA, 7, 0, WEEKDAYS, fri701, NEVER));
        eq("single Friday selection rolls a full week", utc("2026-11-20T15:00:00Z"), Schedule.next(fri701, LA, 7, 0, 16, fri6, NEVER));
        eq("no selected days means no next occurrence", 0, Schedule.next(fri6, LA, 7, 0, 0, 0, NEVER));
        eq("no selected days means not due", 0, Schedule.due(fri701, LA, 7, 0, 0, 0, NEVER));
        eq("unconfigured eligibility prevents due", 0, Schedule.due(fri701, LA, 7, 0, WEEKDAYS, Long.MAX_VALUE, NEVER));
        eq("unconfigured eligibility prevents next", 0, Schedule.next(fri701, LA, 7, 0, WEEKDAYS, Long.MAX_VALUE, NEVER));
        eq("spring missing 02:30 shifts through DST gap", utc("2026-03-08T10:30:00Z"), Schedule.at(LocalDate.parse("2026-03-08"), 2, 30, LA));
        eq("autumn overlap chooses earlier 01:30", utc("2026-11-01T08:30:00Z"), Schedule.at(LocalDate.parse("2026-11-01"), 1, 30, LA));
        eq("second repeated 01:30 does not launch again", 0, Schedule.due(utc("2026-11-01T09:30:00Z"), LA, 1, 30, 64, 0, day("2026-11-01")));
        eq("summer 7am follows PDT", utc("2026-07-13T14:00:00Z"), Schedule.at(LocalDate.parse("2026-07-13"), 7, 0, LA));
        eq("zone change follows local 7am", utc("2026-11-13T12:00:00Z"), Schedule.at(LocalDate.parse("2026-11-13"), 7, 0, ZoneId.of("America/New_York")));
        eq("new year rollover", utc("2027-01-01T15:00:00Z"), Schedule.next(utc("2026-12-31T20:00:00Z"), LA, 7, 0, WEEKDAYS, 0, NEVER));
        eq("Monday bit mapping", 1, Schedule.selected(LocalDate.parse("2026-11-16"), 1) ? 1 : 0);
        eq("Sunday bit mapping", 1, Schedule.selected(LocalDate.parse("2026-11-15"), 64) ? 1 : 0);
        System.out.println("PASS: " + checks + " schedule regression checks");
    }
}
