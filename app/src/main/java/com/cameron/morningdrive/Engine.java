package com.cameron.morningdrive;

import android.app.*;
import android.content.*;
import android.os.Build;
import java.time.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class Engine {
    public static final class Departure {
        public final long time,epochDay;
        public final Config.Location location;
        private Departure(Schedule.Event event,Config.Location location) { this.time=event.time; this.epochDay=event.epochDay; this.location=location; }
    }
    static AlarmManager alarms(Context c) { return (AlarmManager)c.getSystemService(Context.ALARM_SERVICE); }
    public static boolean canSchedule(Context c) { return Build.VERSION.SDK_INT<31||alarms(c).canScheduleExactAlarms(); }
    static PendingIntent alarmIntent(Context c) {
        return PendingIntent.getBroadcast(c,1,new Intent(c,EventReceiver.class).setAction("com.cameron.morningdrive.DEPART"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private static Schedule.Event event(Config v,boolean due) {
        if(!v.enabled||!v.valid()) return null;
        long now=System.currentTimeMillis(); ZoneId zone=ZoneId.systemDefault();
        return due?Schedule.due(now,zone,v.entries(),v.defaultHour,v.defaultMinute,v.eligibleAfter,v.lastHandled,v.skipDay)
                :Schedule.next(now,zone,v.entries(),v.defaultHour,v.defaultMinute,v.eligibleAfter,v.lastHandled,v.skipDay);
    }
    public static Departure dueDeparture(Context c) {
        Config v=Config.load(c); Schedule.Event e=event(v,true);
        if(e==null) return null;
        Config.Location place=v.findLocation(e.locationId);
        return place==null?null:new Departure(e,place);
    }
    public static long due(Context c) { Departure d=dueDeparture(c); return d==null?0:d.time; }
    public static Config.Location dueLocation(Context c) { Departure d=dueDeparture(c); return d==null?null:d.location; }
    public static long nextTime(Context c) { Schedule.Event e=event(Config.load(c),false); return e==null?0:e.time; }
    public static String nextSummary(Context c) {
        Config v=Config.load(c);
        if(!v.enabled) return v.valid()?"Paused · your weekly schedule is saved":"Add locations and assign your days";
        Schedule.Event pending=event(v,true),next=pending!=null?pending:event(v,false);
        if(next==null) return "No upcoming departure";
        Config.Location place=v.findLocation(next.locationId);
        String format=android.text.format.DateFormat.is24HourFormat(c)?"EEE, MMM d · HH:mm":"EEE, MMM d · h:mm a";
        return (pending!=null?"Pending: ":"Next: ")+new SimpleDateFormat(format,Locale.getDefault()).format(new Date(next.time))+" · "+place.name;
    }
    public static void skipNext(Context c) {
        if(hasSkip(c)) return; // One visible, undoable skip at a time.
        Config v=Config.load(c); Schedule.Event target=event(v,true); if(target==null) target=event(v,false);
        if(target==null) return;
        Config.skip(c,target.epochDay);
        Config.status(c,"Skipped "+LocalDate.ofEpochDay(target.epochDay)+". Your repeating week is unchanged.");
        refresh(c); MonitorService.update(c);
    }
    public static boolean hasSkip(Context c) { return Config.load(c).skipDay>=LocalDate.now().toEpochDay(); }
    public static void clearSkip(Context c) {
        Config.skip(c,Long.MIN_VALUE); Config.status(c,"Skip cancelled. Your weekly schedule is unchanged.");
        refresh(c); MonitorService.start(c);
    }
    public static void refresh(Context c) {
        AlarmManager a=alarms(c); a.cancel(alarmIntent(c));
        long next=nextTime(c); if(next==0) return;
        if(!canSchedule(c)) { Config.status(c,"Setup needed: allow Alarms & reminders."); return; }
        try { a.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next,alarmIntent(c)); }
        catch(SecurityException e) { Config.status(c,"Alarms permission unavailable. Open setup."); }
    }
}
