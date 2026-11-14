package com.cameron.morningdrive;

import android.app.*;
import android.content.*;
import android.os.Build;
import java.time.*;

public final class Engine {
    static AlarmManager alarms(Context c) { return (AlarmManager)c.getSystemService(Context.ALARM_SERVICE); }
    public static boolean canSchedule(Context c) { return Build.VERSION.SDK_INT<31 || alarms(c).canScheduleExactAlarms(); }
    static PendingIntent alarmIntent(Context c) {
        return PendingIntent.getBroadcast(c,1,new Intent(c,EventReceiver.class).setAction("com.cameron.morningdrive.DEPART"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    static long due(Context c) {
        Config v=Config.load(c); if(!v.enabled||!v.valid()) return 0;
        return Schedule.due(System.currentTimeMillis(),ZoneId.systemDefault(),v.hour,v.minute,v.daysMask,v.eligibleAfter,v.lastHandled);
    }
    public static long nextTime(Context c) {
        Config v=Config.load(c); if(!v.enabled||!v.valid()) return 0;
        return Schedule.next(System.currentTimeMillis(),ZoneId.systemDefault(),v.hour,v.minute,v.daysMask,v.eligibleAfter,v.lastHandled);
    }
    public static void refresh(Context c) {
        AlarmManager a=alarms(c); a.cancel(alarmIntent(c));
        long next=nextTime(c); if(next==0) return;
        if(!canSchedule(c)) { Config.status(c,"Setup needed: allow Alarms & reminders."); return; }
        try { a.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next,alarmIntent(c)); }
        catch(SecurityException e) { Config.status(c,"Alarms permission unavailable. Open setup."); }
    }
}
