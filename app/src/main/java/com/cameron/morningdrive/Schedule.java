package com.cameron.morningdrive;

import java.time.*;

/** Pure local-time weekly planner. Pending departures are computed only for today's date. */
public final class Schedule {
    public static final class Entry {
        public final boolean enabled,customTime;
        public final String locationId;
        public final int hour,minute;
        public Entry(boolean enabled,String locationId,boolean customTime,int hour,int minute) {
            this.enabled=enabled; this.locationId=locationId; this.customTime=customTime; this.hour=hour; this.minute=minute;
        }
    }
    public static final class Event {
        public final long time,epochDay;
        public final int weekday;
        public final String locationId;
        private Event(long time,LocalDate day,String id) {
            this.time=time; this.epochDay=day.toEpochDay(); this.weekday=day.getDayOfWeek().getValue()-1; this.locationId=id;
        }
    }
    public static long at(LocalDate day,int hour,int minute,ZoneId zone) {
        // java.time shifts a nonexistent DST time forward and chooses the earlier offset on overlap.
        return day.atTime(hour,minute).atZone(zone).toInstant().toEpochMilli();
    }
    private static Event event(LocalDate day,ZoneId zone,Entry[] week,int defaultHour,int defaultMinute) {
        if(week==null||week.length!=7) return null;
        Entry e=week[day.getDayOfWeek().getValue()-1];
        if(e==null||!e.enabled||e.locationId==null||e.locationId.isEmpty()) return null;
        int h=e.customTime?e.hour:defaultHour,m=e.customTime?e.minute:defaultMinute;
        if(h<0||h>23||m<0||m>59) return null;
        return new Event(at(day,h,m,zone),day,e.locationId);
    }
    public static Event due(long now,ZoneId zone,Entry[] week,int defaultHour,int defaultMinute,
                            long eligibleAfter,long handledDay,long skipDay) {
        LocalDate today=Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        Event e=event(today,zone,week,defaultHour,defaultMinute);
        return e!=null&&e.time<=now&&e.time>=eligibleAfter&&e.epochDay!=handledDay&&e.epochDay!=skipDay?e:null;
    }
    public static Event next(long now,ZoneId zone,Entry[] week,int defaultHour,int defaultMinute,
                             long eligibleAfter,long handledDay,long skipDay) {
        LocalDate today=Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        // Two weeks also covers skipping the sole enabled weekday before it arrives.
        for(int i=0;i<=14;i++) {
            Event e=event(today.plusDays(i),zone,week,defaultHour,defaultMinute);
            if(e!=null&&e.time>now&&e.time>=eligibleAfter&&e.epochDay!=handledDay&&e.epochDay!=skipDay) return e;
        }
        return null;
    }
}
