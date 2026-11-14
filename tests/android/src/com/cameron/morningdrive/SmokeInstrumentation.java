package com.cameron.morningdrive;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.time.*;

/** Emulator-only runtime tests. The companion Maps probe never performs real navigation. */
public final class SmokeInstrumentation extends Instrumentation {
    private Context app;
    private int checks;
    private String mode;
    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        mode=args==null?"":args.getString("mode","");
        start();
    }
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
            byte[] buf=new byte[4096]; int n; while((n=in.read(buf))!=-1) out.write(buf,0,n);
        }
        return out.toString("UTF-8");
    }
    private void main(Runnable r) { runOnMainSync(r); waitForIdleSync(); }
    private void reset() {
        main(() -> MonitorService.stop(app));
        app.getSharedPreferences("schedule",0).edit().clear().commit();
        Engine.refresh(app);
    }
    private Config demoPlan() {
        Config c=Config.load(app);
        c.locations.clear();
        c.locations.add(new Config.Location("airport","Airport","7000 NE Airport Way, Portland OR"));
        c.locations.add(new Config.Location("bank","Bank","123 Main Street, Portland OR"));
        c.locations.add(new Config.Location("arena","Arena","1 Center Court, Portland OR"));
        c.defaultHour=7; c.defaultMinute=0;
        for(int i=0;i<7;i++) {
            c.days[i].enabled=i<5;
            c.days[i].customTime=false;
            c.days[i].locationId=(i==0||i==2)?"airport":(i==1||i==3)?"bank":"arena";
        }
        c.days[1].customTime=true; c.days[1].hour=8; c.days[1].minute=30;
        return c;
    }
    private void testMigration() {
        long eligible=System.currentTimeMillis()-100000;
        long handled=LocalDate.now().minusDays(2).toEpochDay();
        app.getSharedPreferences("schedule",0).edit().clear()
            .putString("address","Legacy work address")
            .putInt("hour",6).putInt("minute",45).putInt("days",21)
            .putBoolean("enabled",true).putLong("eligible",eligible)
            .putLong("handled",handled).commit();
        Config old=Config.load(app);
        check(old.locations.size()==1&&"Legacy work address".equals(old.locations.get(0).address),"version 1 address migrates to saved location");
        check("Saved location".equals(old.locations.get(0).name),"legacy location has a readable name");
        check(old.defaultHour==6&&old.defaultMinute==45,"legacy departure time preserved");
        for(int i=0;i<7;i++) check(old.days[i].enabled==((21&(1<<i))!=0),"legacy weekday "+i+" preserved");
        check(old.enabled&&old.eligibleAfter==eligible&&old.lastHandled==handled,"migration preserves automation and duplicate state");
        Config again=Config.load(app);
        check(again.locations.size()==1,"repeated loading does not duplicate migrated place");
    }
    private void testWeeklyPersistence() {
        Config advance=demoPlan();
        for(Config.Day day:advance.days) day.enabled=false;
        Config.save(app,advance);
        check(!Config.load(app).enabled&&Config.load(app).locations.size()==3,"locations can be saved before any days are assigned");
        Config.save(app,demoPlan());
        Config c=Config.load(app);
        check(c.enabled&&c.valid()&&c.locations.size()==3,"save persists three named locations and activates weekly plan");
        check(c.eligibleAfter>System.currentTimeMillis()-10000,"saving excludes already missed departures");
        check("airport".equals(c.days[0].locationId)&&"airport".equals(c.days[2].locationId),"Monday and Wednesday share Airport");
        check("bank".equals(c.days[1].locationId)&&"bank".equals(c.days[3].locationId),"Tuesday and Thursday share Bank");
        check("arena".equals(c.days[4].locationId)&&!c.days[5].enabled&&!c.days[6].enabled,"Friday uses Arena and weekend stays off");
        check(c.defaultHour==7&&!c.days[0].customTime&&c.days[1].customTime&&c.days[1].hour==8&&c.days[1].minute==30,"default time and per-day override persist");
        c.findLocation("bank").name="Downtown Bank";
        c.locations.add(new Config.Location("extra","Temporary","42 Test Lane"));
        Config.save(app,c);
        c=Config.load(app);
        check("Downtown Bank".equals(c.findLocation("bank").name)&&"bank".equals(c.days[1].locationId),"renaming place retains day assignment");
        check(c.locations.size()==4,"preemptive unassigned place is saved");
        c.locations.remove(c.findLocation("extra")); Config.save(app,c);
        check(Config.load(app).findLocation("extra")==null,"removing unassigned place persists");
        long next=Engine.nextTime(app);
        check(next>System.currentTimeMillis(),"weekly next departure is in the future");
        Engine.skipNext(app);
        long skipDate=Instant.ofEpochMilli(next).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay();
        check(Config.load(app).skipDay==skipDate&&Engine.hasSkip(app),"skip-next selection persists as one calendar date");
        check(Engine.nextTime(app)>next,"skip advances to another scheduled departure");
        Engine.clearSkip(app);
        check(!Engine.hasSkip(app)&&Engine.nextTime(app)==next,"undo skip restores original departure");
        Config.setEnabled(app,false); Engine.refresh(app);
        check(!Config.load(app).enabled&&Engine.nextTime(app)==0,"disable cancels future automation");
        check(Config.load(app).locations.size()==3,"disable retains saved locations");
        Config.setEnabled(app,true);
        check(Config.load(app).enabled,"enable restores weekly automation");
    }
    /** Makes today's minute eligible only in the emulator; production Save never backdates. */
    private void makeDue(String address) {
        ZonedDateTime now=ZonedDateTime.now();
        long due=now.withSecond(0).withNano(0).toInstant().toEpochMilli();
        Config c=Config.load(app);
        c.locations.clear(); c.locations.add(new Config.Location("test","Test site",address));
        c.defaultHour=now.getHour(); c.defaultMinute=now.getMinute();
        for(Config.Day day:c.days) { day.enabled=true; day.customTime=false; day.locationId="test"; }
        Config.save(app,c); Engine.clearSkip(app);
        app.getSharedPreferences("schedule",0).edit().putBoolean("enabled",true)
            .putLong("eligible",due-1000).putLong("handled",Long.MIN_VALUE).commit();
    }
    private void scheduleAlarm(int request,long delay) {
        PendingIntent alarm=PendingIntent.getBroadcast(app,request,
            new Intent(app,EventReceiver.class).setAction("TEST_DEPART_"+request),
            PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        app.getSystemService(AlarmManager.class).setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,System.currentTimeMillis()+delay,alarm);
    }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            app=getTargetContext();
            shell("input keyevent KEYCODE_WAKEUP"); shell("wm dismiss-keyguard");
            if("arm_pending".equals(mode)) {
                reset(); Config.save(app,demoPlan());
                startActivitySync(new Intent(app,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                main(() -> {Engine.refresh(app);MonitorService.start(app);}); pause(1500);
                shell("input keyevent KEYCODE_HOME"); pause(500);
                shell("input keyevent KEYCODE_SLEEP"); pause(700);
                makeDue("Uninstrumented handoff, Portland OR");
                scheduleAlarm(43,8000);
                result.putString("stream","ARMED: pending departure; instrumentation exits before exact alarm fires.\n");
                finish(Activity.RESULT_OK,result); return;
            }
            reset(); testMigration(); reset(); testWeeklyPersistence();
            Activity activity=startActivitySync(new Intent(app,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            check(activity!=null,"weekly configuration activity launches");
            check(Engine.canSchedule(app),"exact alarms available on emulator");
            main(() -> {Engine.refresh(app);MonitorService.start(app);}); pause(1800);
            NotificationManager nm=app.getSystemService(NotificationManager.class);
            boolean ongoing=false;
            for(android.service.notification.StatusBarNotification n:nm.getActiveNotifications()) {
                if(n.getId()==17&&(n.getNotification().flags&Notification.FLAG_FOREGROUND_SERVICE)!=0) ongoing=true;
            }
            check(ongoing,"unlock monitor has foreground service notification");
            shell("input keyevent KEYCODE_HOME"); pause(500);
            shell("input keyevent KEYCODE_SLEEP"); pause(700);
            check(!MonitorService.unlocked(app),"screen-off device unavailable for navigation");
            makeDue("Test destination, Portland OR");
            check(Engine.due(app)>0&&"Test destination, Portland OR".equals(Engine.dueLocation(app).address),"due event resolves today's assigned location");
            Engine.skipNext(app);
            check(Engine.due(app)==0&&Engine.hasSkip(app),"skip cancels already pending departure");
            Engine.clearSkip(app);
            check(Engine.due(app)>0,"undo pending skip restores departure");
            main(() -> MonitorService.stop(app)); pause(400);
            app.getSharedPreferences("schedule",0).edit().putString("status","Waiting for test alarm.").commit();
            scheduleAlarm(42,1500);
            long alarmEnd=SystemClock.elapsedRealtime()+25000;
            while(!Config.load(app).status.toLowerCase().contains("pending")&&SystemClock.elapsedRealtime()<alarmEnd) pause(200);
            check(Config.load(app).lastHandled==Long.MIN_VALUE,"locked departure not prematurely handled");
            check(Config.load(app).status.toLowerCase().contains("pending"),"exact alarm receiver retains locked pending trip");
            shell("input keyevent KEYCODE_WAKEUP"); shell("wm dismiss-keyguard");
            long end=SystemClock.elapsedRealtime()+25000;
            while(Config.load(app).lastHandled==Long.MIN_VALUE&&SystemClock.elapsedRealtime()<end) pause(200);
            check(Config.load(app).lastHandled==LocalDate.now().toEpochDay(),"unlock dispatches pending destination to Maps probe");
            long handled=Config.load(app).lastHandled;
            pause(2500); // Exceed the attempt cooldown so this really tests handled-day deduplication.
            main(() -> MonitorService.start(app)); pause(1500);
            check(Config.load(app).lastHandled==handled,"recheck preserves handled-day state");
            String log=shell("logcat -d -s MorningDriveMapsProbe:I '*:S'");
            check(log.contains("google.navigation:q=Test%20destination%2C%20Portland%20OR&mode=d"),"Maps receives encoded address and driving navigation URI");
            check(log.split("google.navigation:q=",-1).length-1==1,"automatic destination sent only once");
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
