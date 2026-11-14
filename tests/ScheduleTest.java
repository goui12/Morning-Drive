package com.cameron.morningdrive;

import java.time.*;

/** Regression tests require only a JDK; no Android runtime or third-party dependencies. */
public final class ScheduleTest {
    private static int checks;
    private static final ZoneId LA=ZoneId.of("America/Los_Angeles");
    private static final long NONE=Long.MIN_VALUE;
    private static void ok(boolean condition,String message) {
        checks++; if(!condition) throw new AssertionError(message);
    }
    private static long time(String text) { return LocalDateTime.parse(text).atZone(LA).toInstant().toEpochMilli(); }
    private static long day(String text) { return LocalDate.parse(text).toEpochDay(); }
    private static Schedule.Entry on(String id) { return new Schedule.Entry(true,id,false,7,0); }
    private static Schedule.Entry[] week() {
        Schedule.Entry[] w=new Schedule.Entry[7];
        for(int i=0;i<7;i++) w[i]=new Schedule.Entry(false,"",false,7,0);
        w[0]=on("airport");w[1]=on("bank");w[2]=on("airport");w[3]=on("bank");w[4]=on("arena");
        return w;
    }
    private static Schedule.Event due(String now,Schedule.Entry[] w,long eligible,long handled,long skip) {
        return Schedule.due(time(now),LA,w,7,0,eligible,handled,skip);
    }
    private static Schedule.Event next(String now,Schedule.Entry[] w,long eligible,long handled,long skip) {
        return Schedule.next(time(now),LA,w,7,0,eligible,handled,skip);
    }
    public static void main(String[] args) {
        Schedule.Entry[] w=week();
        ok(due("2026-11-16T06:59:59",w,0,NONE,NONE)==null,"before departure");
        Schedule.Event e=due("2026-11-16T07:00:00",w,0,NONE,NONE);
        ok(e!=null&&e.time==time("2026-11-16T07:00:00"),"exact due minute");
        ok(e.locationId.equals("airport")&&e.weekday==0,"Monday airport selection");
        ok(due("2026-11-17T07:01:00",w,0,NONE,NONE).locationId.equals("bank"),"Tuesday bank selection");
        ok(due("2026-11-18T07:01:00",w,0,NONE,NONE).locationId.equals("airport"),"Wednesday airport reuse");
        ok(due("2026-11-19T07:01:00",w,0,NONE,NONE).locationId.equals("bank"),"Thursday bank reuse");
        ok(due("2026-11-20T07:01:00",w,0,NONE,NONE).locationId.equals("arena"),"Friday arena selection");
        ok(due("2026-11-16T07:01:00",w,0,NONE,NONE)!=null,"late unlock stays pending");
        ok(due("2026-11-16T23:59:59",w,0,NONE,NONE)!=null,"pending until midnight");
        ok(due("2026-11-17T00:00:00",w,0,NONE,NONE)==null,"previous day expires at midnight");
        ok(due("2026-11-21T08:00:00",w,0,NONE,NONE)==null,"Saturday off");
        ok(due("2026-11-22T08:00:00",w,0,NONE,NONE)==null,"Sunday off");
        ok(next("2026-11-20T08:00:00",w,0,NONE,NONE).time==time("2026-11-23T07:00:00"),"weekly repeat across weekend");
        ok(due("2026-11-23T07:01:00",w,0,NONE,NONE).locationId.equals("airport"),"next week retains same assignments");
        ok(due("2026-11-16T08:00:00",w,time("2026-11-16T07:00:01"),NONE,NONE)==null,"save or enable excludes earlier departure");
        ok(due("2026-11-16T08:00:00",w,time("2026-11-16T06:59:00"),NONE,NONE)!=null,"saved before time remains eligible");
        ok(due("2026-11-16T08:00:00",w,0,day("2026-11-16"),NONE)==null,"same-day dedup");
        ok(next("2026-11-16T06:00:00",w,0,day("2026-11-16"),NONE).weekday==1,"clock rollback does not repeat handled day");
        ok(due("2026-11-16T08:00:00",w,0,NONE,day("2026-11-16"))==null,"skip pending departure");
        ok(next("2026-11-15T18:00:00",w,0,NONE,day("2026-11-16")).weekday==1,"skip next Monday moves to Tuesday");
        ok(due("2026-11-23T08:00:00",w,0,NONE,day("2026-11-16"))!=null,"skip does not repeat next week");
        ok(due("2026-11-16T08:00:00",w,0,NONE,NONE)!=null,"clearing skip restores pending eligibility");
        Schedule.Entry[] onlyMonday=new Schedule.Entry[7];onlyMonday[0]=on("airport");
        ok(next("2026-11-15T18:00:00",onlyMonday,0,NONE,day("2026-11-16")).time==time("2026-11-23T07:00:00"),"one-day plan advances past skipped next week");
        ok(next("2026-11-16T08:00:00",onlyMonday,0,day("2026-11-16"),day("2026-11-23")).time==time("2026-11-30T07:00:00"),"handled today plus skipped next Monday advances fourteen days");
        Schedule.Entry[] off=new Schedule.Entry[7];
        ok(next("2026-11-15T18:00:00",off,0,NONE,NONE)==null,"all days off has no next departure");
        ok(due("2026-11-16T08:00:00",off,0,NONE,NONE)==null,"all days off has no pending departure");
        w[1]=new Schedule.Entry(true,"bank",true,8,15);
        ok(due("2026-11-17T08:14:59",w,0,NONE,NONE)==null,"custom time before due");
        ok(due("2026-11-17T08:15:00",w,0,NONE,NONE).time==time("2026-11-17T08:15:00"),"custom time applies to Tuesday");
        ok(due("2026-11-18T07:00:00",w,0,NONE,NONE)!=null,"custom time does not affect Wednesday");
        ok(Schedule.due(time("2026-11-16T06:30:00"),LA,w,6,30,0,NONE,NONE)!=null,"default time changes inherited day");
        ok(Schedule.due(time("2026-11-17T06:30:00"),LA,w,6,30,0,NONE,NONE)==null,"default time does not override custom day");
        Schedule.Entry[] sun=new Schedule.Entry[7];sun[6]=new Schedule.Entry(true,"early",true,2,30);
        ok(Schedule.next(time("2026-03-08T01:00:00"),LA,sun,7,0,0,NONE,NONE).time==time("2026-03-08T03:30:00"),"spring DST nonexistent time shifts forward");
        sun[6]=new Schedule.Entry(true,"early",true,1,30);
        long overlap=LocalDate.of(2026,11,1).atTime(1,30).atZone(LA).withEarlierOffsetAtOverlap().toInstant().toEpochMilli();
        ok(Schedule.next(time("2026-11-01T00:00:00"),LA,sun,7,0,0,NONE,NONE).time==overlap,"fall DST chooses earlier occurrence");
        ok(Schedule.due(overlap+3600000,LA,sun,7,0,0,day("2026-11-01"),NONE)==null,"fall DST cannot launch twice");
        ok(next("2026-12-31T23:00:00",week(),0,NONE,NONE).time==time("2027-01-01T07:00:00"),"year boundary");
        ZoneId ny=ZoneId.of("America/New_York");
        long ny7=LocalDateTime.parse("2026-11-16T07:00:00").atZone(ny).toInstant().toEpochMilli();
        ok(Schedule.due(ny7,ny,week(),7,0,0,NONE,NONE)!=null,"new timezone uses local 7 AM");
        w[0]=new Schedule.Entry(true,"",false,7,0);
        ok(due("2026-11-16T08:00:00",w,0,NONE,NONE)==null,"unassigned day cannot launch");
        w[0]=new Schedule.Entry(true,"bad",true,25,0);
        ok(due("2026-11-16T08:00:00",w,0,NONE,NONE)==null,"invalid time cannot launch");
        ok(Schedule.next(time("2026-11-16T00:00:00"),LA,null,7,0,0,NONE,NONE)==null,"missing week safely produces no event");
        System.out.println("PASS: "+checks+" weekly schedule regression checks");
    }
}
