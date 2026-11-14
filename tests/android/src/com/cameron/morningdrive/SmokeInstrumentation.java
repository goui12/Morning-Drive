package com.cameron.morningdrive;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.time.*;

/** Emulator-only integration smoke test; resets the target app's test configuration. */
public final class SmokeInstrumentation extends Instrumentation {
    private Context app;
    private int checks;
    private String mode;
    @Override public void onCreate(Bundle args) { super.onCreate(args); mode=args==null?"":args.getString("mode",""); start(); }
    private void check(boolean ok,String name) {
        if(!ok) throw new AssertionError(name);
        checks++;
        Bundle b=new Bundle(); b.putString("stream","PASS "+name+"\n"); sendStatus(0,b);
    }
    private void pause(long ms) { SystemClock.sleep(ms); }
    private String shell(String command) throws Exception {
        ParcelFileDescriptor fd=getUiAutomation().executeShellCommand(command);
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)) {
            byte[] buf=new byte[4096]; int n; while((n=in.read(buf))!=-1)out.write(buf,0,n);
        }
        return out.toString("UTF-8");
    }
    private void main(Runnable r) { runOnMainSync(r); waitForIdleSync(); }
    private void makeDue() {
        ZonedDateTime now=ZonedDateTime.now();
        long due=now.withSecond(0).withNano(0).toInstant().toEpochMilli();
        app.getSharedPreferences("schedule",0).edit()
            .putString("address","Test destination, Portland OR")
            .putInt("hour",now.getHour()).putInt("minute",now.getMinute()).putInt("days",127)
            .putBoolean("enabled",true).putLong("eligible",due-1000)
            .putLong("handled",Long.MIN_VALUE).commit();
    }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            app=getTargetContext();
            if("arm_pending".equals(mode)) {
                shell("input keyevent KEYCODE_WAKEUP"); shell("wm dismiss-keyguard");
                startActivitySync(new Intent(app,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                Config.save(app,"Uninstrumented handoff, Portland OR",7,0,127);
                main(() -> {Engine.refresh(app);MonitorService.start(app);});
                pause(1200);
                shell("input keyevent KEYCODE_HOME"); pause(500);
                shell("input keyevent KEYCODE_SLEEP"); pause(500);
                makeDue();
                app.getSharedPreferences("schedule",0).edit().putString("address","Uninstrumented handoff, Portland OR").commit();
                PendingIntent outside=PendingIntent.getBroadcast(app,43,new Intent(app,EventReceiver.class).setAction("OUTSIDE_DEPART"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
                app.getSystemService(AlarmManager.class).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,System.currentTimeMillis()+8000,outside);
                result.putString("stream","ARMED: pending departure; instrumentation exits before exact alarm fires.\n");
                finish(Activity.RESULT_OK,result); return;
            }
            shell("input keyevent KEYCODE_WAKEUP"); shell("wm dismiss-keyguard");
            app.getSharedPreferences("schedule",0).edit().clear().commit();
            Activity activity=startActivitySync(new Intent(app,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            check(activity!=null,"configuration activity launches");
            check(Engine.canSchedule(app),"exact alarm permission available");
            Config.save(app,"Test destination, Portland OR",7,0,31);
            Config c=Config.load(app);
            check(c.enabled&&c.valid()&&c.daysMask==31,"save persists valid weekday configuration and enables");
            check(c.eligibleAfter>System.currentTimeMillis()-10000,"save excludes already missed departures");
            main(() -> { Engine.refresh(app); MonitorService.start(app); });
            pause(1500);
            check(Engine.nextTime(app)>System.currentTimeMillis(),"next departure scheduled in future");
            NotificationManager nm=app.getSystemService(NotificationManager.class);
            boolean ongoing=false;
            for(android.service.notification.StatusBarNotification n:nm.getActiveNotifications()) {
                if(n.getId()==17&&(n.getNotification().flags&Notification.FLAG_FOREGROUND_SERVICE)!=0) ongoing=true;
            }
            check(ongoing,"unlock monitor has foreground-service notification");
            main(() -> { Config.setEnabled(app,false); Engine.refresh(app); MonitorService.stop(app); });
            pause(300);
            check(!Config.load(app).enabled&&Engine.nextTime(app)==0,"disable cancels future departures");
            check(Config.load(app).address.equals("Test destination, Portland OR"),"disable retains address");
            main(() -> { Config.setEnabled(app,true); Engine.refresh(app); MonitorService.start(app); });
            check(Config.load(app).enabled,"enable restores automation");
            // Ensure monitor is started while UI foreground, then make today's trip due with display off.
            pause(1000);
            shell("input keyevent KEYCODE_HOME"); pause(700);
            shell("input keyevent KEYCODE_SLEEP"); pause(700);
            check(!MonitorService.unlocked(app),"screen-off device considered locked/unavailable");
            makeDue();
            app.getSharedPreferences("schedule",0).edit().putString("status","Waiting for test alarm.").commit();
            PendingIntent alarm=PendingIntent.getBroadcast(app,42,new Intent(app,EventReceiver.class).setAction("TEST_DEPART"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
            app.getSystemService(AlarmManager.class).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,System.currentTimeMillis()+1500,alarm);
            long alarmEnd=SystemClock.elapsedRealtime()+15000;
            while(!Config.load(app).status.contains("pending")&&SystemClock.elapsedRealtime()<alarmEnd) pause(200);
            check(Config.load(app).lastHandled==Long.MIN_VALUE,"locked departure not prematurely marked handled");
            check(Config.load(app).status.contains("pending"),"exact alarm receiver keeps locked departure pending");
            shell("input keyevent KEYCODE_WAKEUP"); shell("wm dismiss-keyguard");
            long end=SystemClock.elapsedRealtime()+15000;
            while(Config.load(app).lastHandled==Long.MIN_VALUE&&SystemClock.elapsedRealtime()<end) pause(200);
            check(Config.load(app).lastHandled==LocalDate.now().toEpochDay(),"unlock dispatches pending navigation to Maps probe");
            check(Config.load(app).status.contains("request sent"),"foreground Maps handoff recorded");
            long handled=Config.load(app).lastHandled;
            pause(2500); // Beyond launch-attempt throttle: duplicate check must exercise dedup, not cooldown.
            main(() -> MonitorService.start(app)); pause(1200);
            check(Config.load(app).lastHandled==handled,"rechecking handled departure preserves deduplication");
            String log=shell("logcat -d -s MorningDriveMapsProbe:I '*:S'");
            check(log.contains("google.navigation:q=Test%20destination%2C%20Portland%20OR&mode=d"),"Maps receives encoded destination and driving-navigation URI");
            check(log.split("google.navigation:q=",-1).length-1==1,"handled departure is dispatched only once");
            main(() -> {Config.setEnabled(app,false);Engine.refresh(app);MonitorService.stop(app);});
            result.putString("stream","\nSUCCESS: "+checks+" checks passed. Emulator uses a Maps intent probe, not real guidance.\n");
            finish(Activity.RESULT_OK,result);
        } catch(Throwable t) {
            result.putString("stream","\nFAIL after "+checks+" checks: "+t+"\n");
            android.util.Log.e("MorningDriveSmoke","Test failed",t);
            finish(Activity.RESULT_CANCELED,result);
        }
    }
}
