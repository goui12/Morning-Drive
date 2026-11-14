package com.cameron.morningdrive;

import android.content.Context;
import android.content.SharedPreferences;

public final class Config {
    public String address, status;
    public int hour, minute, daysMask;
    public boolean enabled;
    public long eligibleAfter, lastHandled;
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("schedule", Context.MODE_PRIVATE); }
    public static Config load(Context c) {
        SharedPreferences p=prefs(c);
        Config v=new Config();
        v.address=p.getString("address", ""); v.hour=p.getInt("hour",7); v.minute=p.getInt("minute",0);
        v.daysMask=p.getInt("days",31); v.enabled=p.getBoolean("enabled",false);
        v.eligibleAfter=p.getLong("eligible",Long.MAX_VALUE); v.lastHandled=p.getLong("handled",Long.MIN_VALUE);
        v.status=p.getString("status", "Save a destination and schedule to begin."); return v;
    }
    public boolean valid() { return !address.trim().isEmpty() && hour>=0 && hour<24 && minute>=0 && minute<60 && (daysMask&127)!=0; }
    public static void save(Context c,String address,int hour,int minute,int mask) {
        prefs(c).edit().putString("address",address.trim()).putInt("hour",hour).putInt("minute",minute)
            .putInt("days",mask&127).putBoolean("enabled",true).putLong("eligible",System.currentTimeMillis())
            .putString("status","Schedule saved. Waiting for the next departure.").commit();
    }
    public static void setEnabled(Context c,boolean enabled) {
        SharedPreferences.Editor e=prefs(c).edit().putBoolean("enabled",enabled);
        if(enabled) e.putLong("eligible",System.currentTimeMillis());
        e.putString("status",enabled ? "Automation enabled. Waiting for the next departure." : "Paused. Settings retained; pending departure cancelled.").commit();
    }
    static void status(Context c,String text) { prefs(c).edit().putString("status",text).apply(); }
    static void handled(Context c,long day) {
        prefs(c).edit().putLong("handled",day).putString("status","Navigation request sent to Google Maps.").commit();
    }
}
