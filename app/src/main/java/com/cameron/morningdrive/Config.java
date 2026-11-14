package com.cameron.morningdrive;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;

/** Saved repeating plan. Runtime state is separate so editing a draft cannot undo a departure. */
public final class Config {
    public static final class Location {
        public String id,name,address;
        public Location(String id,String name,String address) { this.id=id; this.name=name; this.address=address; }
    }
    public static final class Day {
        public boolean enabled,customTime;
        public String locationId="";
        public int hour=7,minute=0;
        public Day() {}
    }
    public ArrayList<Location> locations=new ArrayList<>();
    public Day[] days=new Day[7];
    public int defaultHour=7,defaultMinute=0;
    public boolean enabled;
    public long eligibleAfter=Long.MAX_VALUE,lastHandled=Long.MIN_VALUE,skipDay=Long.MIN_VALUE;
    public String status="Add locations, assign days, then save your week.";
    public Config() { for(int i=0;i<7;i++) days[i]=new Day(); }
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("schedule",Context.MODE_PRIVATE); }
    public Location findLocation(String id) {
        if(id==null) return null;
        for(Location l:locations) if(l!=null && id.equals(l.id)) return l;
        return null;
    }
    private static boolean text(String s) { return s!=null&&!s.trim().isEmpty(); }
    static boolean time(int h,int m) { return h>=0&&h<24&&m>=0&&m<60; }
    public boolean hasEnabledDays() {
        if(days==null) return false;
        for(Day d:days) if(d!=null&&d.enabled) return true;
        return false;
    }
    public boolean planValid() {
        if(!time(defaultHour,defaultMinute)||days==null||days.length!=7||locations==null) return false;
        HashSet<String> ids=new HashSet<>();
        for(Location l:locations) if(l==null||!text(l.id)||!text(l.name)||!text(l.address)||!ids.add(l.id)) return false;
        for(Day d:days) {
            if(d==null) return false;
            if(d.enabled && (findLocation(d.locationId)==null || (d.customTime&&!time(d.hour,d.minute)))) return false;
        }
        return true;
    }
    public boolean valid() { return planValid()&&hasEnabledDays(); }
    Schedule.Entry[] entries() {
        Schedule.Entry[] week=new Schedule.Entry[7];
        for(int i=0;i<7;i++) {
            Day d=days[i];
            week[i]=new Schedule.Entry(d.enabled,d.locationId,d.customTime,d.hour,d.minute);
        }
        return week;
    }
    static JSONObject plan(Config v) throws org.json.JSONException {
        JSONObject root=new JSONObject().put("version",2).put("defaultHour",v.defaultHour).put("defaultMinute",v.defaultMinute);
        JSONArray places=new JSONArray(),week=new JSONArray();
        for(Location l:v.locations) places.put(new JSONObject().put("id",l.id).put("name",l.name.trim()).put("address",l.address.trim()));
        for(Day d:v.days) week.put(new JSONObject().put("enabled",d.enabled).put("location",d.locationId)
                .put("custom",d.customTime).put("hour",d.hour).put("minute",d.minute));
        return root.put("locations",places).put("days",week);
    }
    public static Config load(Context c) {
        SharedPreferences p=prefs(c);
        Config v=new Config();
        v.enabled=p.getBoolean("enabled",false); v.eligibleAfter=p.getLong("eligible",Long.MAX_VALUE);
        v.lastHandled=p.getLong("handled",Long.MIN_VALUE); v.skipDay=p.getLong("skip",Long.MIN_VALUE);
        v.status=p.getString("status",v.status);
        String saved=p.getString("weeklyPlan",null);
        if(saved==null) {
            String old=p.getString("address","");
            if(old!=null&&!old.trim().isEmpty()) {
                v.locations.add(new Location("legacy","Saved location",old));
                v.defaultHour=p.getInt("hour",7); v.defaultMinute=p.getInt("minute",0);
                int mask=p.getInt("days",31);
                for(int i=0;i<7;i++) { v.days[i].enabled=(mask&(1<<i))!=0; v.days[i].locationId="legacy"; }
                try { p.edit().putString("weeklyPlan",plan(v).toString()).commit(); }
                catch(org.json.JSONException e) { v.enabled=false; v.status="Your old schedule needs to be saved again."; }
            }
            return v;
        }
        try {
            JSONObject root=new JSONObject(saved);
            if(root.getInt("version")!=2) throw new IllegalArgumentException("Unknown plan version");
            v.defaultHour=root.getInt("defaultHour"); v.defaultMinute=root.getInt("defaultMinute");
            JSONArray places=root.getJSONArray("locations"),week=root.getJSONArray("days");
            if(week.length()!=7) throw new IllegalArgumentException("Invalid week");
            for(int i=0;i<places.length();i++) {
                JSONObject l=places.getJSONObject(i);
                v.locations.add(new Location(l.getString("id"),l.getString("name"),l.getString("address")));
            }
            for(int i=0;i<7;i++) {
                JSONObject d=week.getJSONObject(i); Day out=v.days[i];
                out.enabled=d.getBoolean("enabled"); out.locationId=d.optString("location","");
                out.customTime=d.getBoolean("custom"); out.hour=d.getInt("hour"); out.minute=d.getInt("minute");
            }
            if(!v.planValid()) throw new IllegalArgumentException("Invalid plan");
        } catch(Exception e) {
            v=new Config(); v.status="Saved plan could not be read. Please configure your week again.";
        }
        return v;
    }
    public static void save(Context c,Config draft) { save(c,draft,true); }
    public static void save(Context c,Config draft,boolean activate) {
        if(draft==null||!draft.planValid()) throw new IllegalArgumentException("Give each enabled day a valid location and time.");
        try {
            boolean active=draft.valid()&&activate;
            // Do not write handled/skip from the draft: they can change while the editor is open.
            if(!prefs(c).edit().putString("weeklyPlan",plan(draft).toString()).putBoolean("enabled",active)
                    .putLong("eligible",System.currentTimeMillis())
                    .putString("status",active?"Week saved. This schedule repeats until changed.":draft.valid()?"Week saved. Finish setup, then tap Enable.":"Locations saved. Assign a day to start automation.").commit())
                throw new IllegalStateException("Unable to save schedule");
        } catch(org.json.JSONException e) { throw new IllegalArgumentException("Unable to save schedule",e); }
    }
    public static void setEnabled(Context c,boolean enabled) {
        if(enabled&&!load(c).valid()) throw new IllegalArgumentException("Assign a location to at least one day first.");
        SharedPreferences.Editor e=prefs(c).edit().putBoolean("enabled",enabled);
        if(enabled) e.putLong("eligible",System.currentTimeMillis());
        e.putString("status",enabled?"Automation enabled. Waiting for your next departure.":"Paused. Your saved week is unchanged.").commit();
    }
    static void status(Context c,String text) { prefs(c).edit().putString("status",text).apply(); }
    static void handled(Context c,long day) {
        prefs(c).edit().putLong("handled",day).putString("status","Navigation request sent to Google Maps.").commit();
    }
    static void skip(Context c,long day) { prefs(c).edit().putLong("skip",day).commit(); }
}
