package com.cameron.morningdrive;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import android.provider.Settings;

public final class MonitorService extends Service {
    private static final String CHANNEL="automation";
    private static final int ID=17;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private long lastAttempt;
    private boolean registered;
    private final Runnable check=() -> evaluate();
    private final BroadcastReceiver unlockReceiver=new BroadcastReceiver() {
        @Override public void onReceive(Context c,Intent i) {
            handler.removeCallbacks(check); handler.postDelayed(check,650);
        }
    };
    public static boolean unlocked(Context c) {
        KeyguardManager k=(KeyguardManager)c.getSystemService(KEYGUARD_SERVICE);
        PowerManager p=(PowerManager)c.getSystemService(POWER_SERVICE);
        return p.isInteractive()&&!k.isKeyguardLocked()&&!k.isDeviceLocked();
    }
    public static void start(Context c) { start(c,false); }
    public static void update(Context c) { start(c,true); }
    private static void start(Context c,boolean updateOnly) {
        if(!Config.load(c).enabled) return;
        try { c.startForegroundService(new Intent(c,MonitorService.class).setAction(updateOnly?"STATUS":"CHECK")); }
        catch(RuntimeException e) { Config.status(c,"Android stopped the unlock monitor. Open Morning Drive to resume it."); }
    }
    public static void stop(Context c) { c.stopService(new Intent(c,MonitorService.class)); }
    @Override public void onCreate() {
        super.onCreate();
        NotificationChannel channel=new NotificationChannel(CHANNEL,"Automation status",NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Keeps scheduled navigation ready and listens for phone unlocks.");
        channel.setSound(null,null); channel.enableVibration(false); channel.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        IntentFilter filter=new IntentFilter();
        filter.addAction(Intent.ACTION_USER_PRESENT); filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_TIME_CHANGED); filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        if(Build.VERSION.SDK_INT>=33) registerReceiver(unlockReceiver,filter,Context.RECEIVER_EXPORTED);
        else registerReceiver(unlockReceiver,filter);
        registered=true;
    }
    @Override public int onStartCommand(Intent intent,int flags,int id) {
        if(Build.VERSION.SDK_INT>=34) startForeground(ID,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(ID,notification());
        Config v=Config.load(this);
        if(!v.enabled||!v.valid()) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY; }
        Engine.refresh(this);
        if(intent==null||!"STATUS".equals(intent.getAction())) {
            handler.removeCallbacks(check); handler.postDelayed(check,650);
        } else updateNotification();
        return START_STICKY;
    }
    private void evaluate() {
        Config v=Config.load(this);
        if(!v.enabled||!v.valid()) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return; }
        Engine.refresh(this);
        long due=Engine.due(this);
        if(due!=0) {
            if(!unlocked(this)) Config.status(this,"Departure pending. Navigation will be attempted after you unlock.");
            else if(!Settings.canDrawOverlays(this)) Config.status(this,"Departure pending. Allow Appear on top or tap Start navigation.");
            else if(SystemClock.elapsedRealtime()-lastAttempt>2000) {
                lastAttempt=SystemClock.elapsedRealtime();
                Config.status(this,"Departure pending until Google Maps opens.");
                try { startActivity(new Intent(this,GateActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_NO_ANIMATION)); }
                catch(RuntimeException e) { Config.status(this,"Automatic launch blocked. Tap Start navigation below."); }
            }
        } else if(v.status.toLowerCase(java.util.Locale.ROOT).contains("pending")) {
            Config.status(this,"No pending departure. Waiting for the next scheduled trip.");
        }
        updateNotification();
    }
    private void updateNotification() { getSystemService(NotificationManager.class).notify(ID,notification()); }
    private Notification notification() {
        PendingIntent settings=PendingIntent.getActivity(this,10,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        boolean pending=Engine.due(this)!=0;
        String text=Engine.nextSummary(this)+(pending?" · unlock to navigate":"");
        Notification.Builder n=new Notification.Builder(this,CHANNEL).setSmallIcon(com.cameron.morningdrive.R.drawable.ic_notification)
            .setContentTitle("Morning Drive is enabled").setContentText(text)
            .setContentIntent(settings).setOngoing(true).setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE).setCategory(Notification.CATEGORY_SERVICE);
        if(pending) {
            PendingIntent go=PendingIntent.getActivity(this,11,new Intent(this,GateActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            n.addAction(new Notification.Action.Builder(null,"Start navigation",go).build());
        }
        return n.build();
    }
    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if(registered) unregisterReceiver(unlockReceiver);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent i) { return null; }
}
