package com.cameron.morningdrive;

import android.content.*;
import java.time.*;
import java.util.*;

/** Runs the production Config source against host-only preferences and JSON. */
public final class ConfigTest {
    static final class MemoryPrefs implements SharedPreferences {
        final Map<String,Object> values=new HashMap<>();
        int commits;
        boolean failNext;
        public synchronized String getString(String k,String d){Object v=values.get(k);return v==null?d:(String)v;}
        public synchronized int getInt(String k,int d){Object v=values.get(k);return v==null?d:(Integer)v;}
        public synchronized long getLong(String k,long d){Object v=values.get(k);return v==null?d:(Long)v;}
        public synchronized boolean getBoolean(String k,boolean d){Object v=values.get(k);return v==null?d:(Boolean)v;}
        public Editor edit(){return new Editor(){
            final Map<String,Object> pending=new HashMap<>();boolean clear;
            public Editor putString(String k,String v){pending.put(k,v);return this;}
            public Editor putInt(String k,int v){pending.put(k,v);return this;}
            public Editor putLong(String k,long v){pending.put(k,v);return this;}
            public Editor putBoolean(String k,boolean v){pending.put(k,v);return this;}
            public Editor clear(){clear=true;return this;}
            public boolean commit(){synchronized(MemoryPrefs.this){commits++;if(failNext){failNext=false;return false;}if(clear)values.clear();values.putAll(pending);return true;}}
            public void apply(){commit();}
        };}
    }
    static final class TestContext extends Context {
        final MemoryPrefs prefs=new MemoryPrefs();
        public SharedPreferences getSharedPreferences(String name,int mode){if(!name.equals("schedule"))throw new AssertionError("Unexpected preference store");return prefs;}
    }
    static int checks;
    static void ok(boolean yes,String label){checks++;if(!yes)throw new AssertionError(label);}
    static void rejected(Runnable action,String label){boolean caught=false;try{action.run();}catch(IllegalArgumentException e){caught=true;}ok(caught,label);}
    static Config locations() {
        Config c=new Config();
        c.locations.add(new Config.Location("airport","Airport","7000 Airport Way, Portland OR"));
        c.locations.add(new Config.Location("bank","Bank","100 Main Street, Portland OR"));
        c.locations.add(new Config.Location("arena","Arena","1 Center Court, Portland OR"));
        return c;
    }
    static Config active() {
        Config c=locations();String[] ids={"airport","bank","airport","bank","arena"};
        for(int i=0;i<5;i++){c.days[i].enabled=true;c.days[i].locationId=ids[i];}
        return c;
    }
    public static void main(String[] args) {
        TestContext fresh=new TestContext();Config empty=Config.load(fresh);
        ok(empty.locations.isEmpty()&&!empty.enabled&&!empty.hasEnabledDays(),"new install is paused with no trips");
        ok(empty.planValid()&&!empty.valid(),"empty plan can be stored but cannot run");
        TestContext old=new TestContext();old.prefs.edit().putString("address","Old work address")
                .putInt("hour",6).putInt("minute",45).putInt("days",21).putBoolean("enabled",true)
                .putLong("eligible",123456L).putLong("handled",20000L).putLong("skip",20001L).commit();
        Config migrated=Config.load(old);
        ok(migrated.locations.size()==1&&migrated.locations.get(0).name.equals("Saved location"),"migration creates one named location");
        ok(migrated.findLocation("legacy").address.equals("Old work address"),"migration preserves address");
        ok(migrated.defaultHour==6&&migrated.defaultMinute==45,"migration preserves departure time");
        ok(migrated.days[0].enabled&&!migrated.days[1].enabled&&migrated.days[2].enabled&&!migrated.days[3].enabled&&migrated.days[4].enabled&&!migrated.days[5].enabled&&!migrated.days[6].enabled,"migration preserves weekday mask");
        ok(migrated.days[0].locationId.equals("legacy")&&!migrated.days[0].customTime,"migration uses shared default time");
        ok(migrated.enabled&&migrated.valid(),"migration remains enabled");
        ok(migrated.eligibleAfter==123456L&&migrated.lastHandled==20000L&&migrated.skipDay==20001L,"migration preserves runtime state");
        ok(old.prefs.getString("weeklyPlan","").contains("\"version\":2"),"migration persists schema version");
        int afterMigration=old.prefs.commits;Config second=Config.load(old);
        ok(old.prefs.commits==afterMigration&&second.locations.size()==1,"repeated load does not remigrate or duplicate place");
        ok(second.lastHandled==20000L&&second.days[4].enabled,"repeated load preserves migrated state");
        TestContext preemptive=new TestContext();Config places=locations();
        places.locations.get(0).name="  Airport  ";places.locations.get(0).address="  7000 Airport Way, Portland OR  ";
        Config.save(preemptive,places);Config stored=Config.load(preemptive);
        ok(stored.locations.size()==3&&!stored.enabled,"preemptive locations save without enabling trips");
        ok(stored.locations.get(0).name.equals("Airport")&&stored.locations.get(0).address.equals("7000 Airport Way, Portland OR"),"saved text is trimmed");
        rejected(()->Config.setEnabled(preemptive,true),"empty weekly schedule cannot be enabled");
        Config assigned=active();assigned.defaultHour=6;assigned.defaultMinute=30;
        assigned.days[1].customTime=true;assigned.days[1].hour=8;assigned.days[1].minute=15;
        long before=System.currentTimeMillis();Config.save(preemptive,assigned,false);Config paused=Config.load(preemptive);
        ok(!paused.enabled&&paused.valid(),"missing setup persists assigned plan safely paused");
        ok(paused.days[1].customTime&&paused.days[1].hour==8&&paused.days[1].minute==15,"per-day override roundtrips");
        ok(paused.defaultHour==6&&paused.defaultMinute==30&&!paused.days[0].customTime,"shared default roundtrips separately");
        ok(paused.days[0].locationId.equals("airport")&&paused.days[1].locationId.equals("bank")&&paused.days[2].locationId.equals("airport")&&paused.days[4].locationId.equals("arena"),"weekday destination assignments roundtrip");
        ok(paused.eligibleAfter>=before&&paused.eligibleAfter<=System.currentTimeMillis(),"save excludes prior departures using current time");
        ZoneId zone=ZoneId.of("America/Los_Angeles");long monday=LocalDateTime.parse("2026-11-16T06:30:00").atZone(zone).toInstant().toEpochMilli();
        long tuesday=LocalDateTime.parse("2026-11-17T08:15:00").atZone(zone).toInstant().toEpochMilli();
        ok(Schedule.due(monday,zone,paused.entries(),paused.defaultHour,paused.defaultMinute,0,Long.MIN_VALUE,Long.MIN_VALUE).locationId.equals("airport"),"saved default reaches scheduler");
        ok(Schedule.due(tuesday,zone,paused.entries(),paused.defaultHour,paused.defaultMinute,0,Long.MIN_VALUE,Long.MIN_VALUE).locationId.equals("bank"),"saved override reaches scheduler");
        Config.setEnabled(preemptive,true);ok(Config.load(preemptive).enabled,"valid saved week can be enabled");
        String stable=preemptive.prefs.getString("weeklyPlan","");Config.setEnabled(preemptive,false);
        ok(!Config.load(preemptive).enabled&&stable.equals(preemptive.prefs.getString("weeklyPlan","")),"disable preserves all saved plan data");
        Config draft=Config.load(preemptive);draft.locations.get(0).name="Unsaved rename";draft.days[0].enabled=false;
        Config independent=Config.load(preemptive);
        ok(independent.locations.get(0).name.equals("Airport")&&independent.days[0].enabled,"editing loaded draft is isolated until save");
        ok(independent.locations!=draft.locations&&independent.days!=draft.days&&independent.days[1]!=draft.days[1],"load returns independent collections and day objects");
        Config.handled(preemptive,21000L);Config.skip(preemptive,21001L);
        draft.lastHandled=Long.MIN_VALUE;draft.skipDay=Long.MIN_VALUE;Config.save(preemptive,draft);
        Config applied=Config.load(preemptive);
        ok(applied.lastHandled==21000L&&applied.skipDay==21001L,"stale draft cannot undo live handled or skipped state");
        ok(applied.enabled&&!applied.days[0].enabled&&applied.locations.get(0).name.equals("Unsaved rename"),"save commits entire edited plan and enables valid week");
        draft.locations.get(0).name="Changed after save";
        ok(Config.load(preemptive).locations.get(0).name.equals("Unsaved rename"),"post-save draft mutation cannot alter persistence");
        String beforeInvalid=preemptive.prefs.getString("weeklyPlan","");
        Config bad=active();bad.days[0].locationId="missing";
        rejected(()->Config.save(preemptive,bad),"invalid enabled assignment is rejected");
        ok(beforeInvalid.equals(preemptive.prefs.getString("weeklyPlan","")),"rejected save leaves prior plan intact");
        Config duplicate=active();duplicate.locations.get(1).id="airport";
        rejected(()->Config.save(preemptive,duplicate),"duplicate location IDs rejected");
        Config blank=active();blank.locations.get(1).name="  ";
        rejected(()->Config.save(preemptive,blank),"blank location name rejected");
        Config badTime=active();badTime.days[0].customTime=true;badTime.days[0].hour=24;
        rejected(()->Config.save(preemptive,badTime),"invalid custom time rejected");
        Config badDefault=active();badDefault.defaultMinute=60;
        rejected(()->Config.save(preemptive,badDefault),"invalid default time rejected");
        Config shortWeek=active();shortWeek.days=new Config.Day[2];
        rejected(()->Config.save(preemptive,shortWeek),"incomplete week rejected");
        rejected(()->Config.save(preemptive,null),"null plan rejected");
        TestContext failed=new TestContext();failed.prefs.failNext=true;boolean failure=false;
        try{Config.save(failed,active());}catch(IllegalStateException expected){failure=true;}
        ok(failure,"failed preference commit is reported");
        TestContext corrupt=new TestContext();corrupt.prefs.edit().putString("weeklyPlan","broken json").putBoolean("enabled",true).commit();
        Config safe=Config.load(corrupt);
        ok(!safe.enabled&&!safe.valid()&&safe.status.contains("could not be read"),"corrupt plan fails safely paused");
        ok(corrupt.prefs.getString("weeklyPlan","").equals("broken json"),"corrupt original data not overwritten by load");
        TestContext unknown=new TestContext();unknown.prefs.edit().putString("weeklyPlan","{\"version\":99}").putBoolean("enabled",true).commit();
        ok(!Config.load(unknown).enabled,"unknown schema fails safely paused");
        System.out.println("PASS: "+checks+" Config migration and persistence checks (actual Config.java; in-memory Android facades; host org.json)");
    }
}
