package com.cameron.morningdrive;

import android.app.*;
import android.os.Bundle;
import java.time.*;

/** Mark handled only after Android actually resumes this activity, never on a background launch attempt. */
public final class GateActivity extends Activity {
    private boolean dispatched;
    @Override public void onCreate(Bundle state) { super.onCreate(state); }
    @Override public void onResume() {
        super.onResume();
        if(dispatched) return;
        getWindow().getDecorView().post(() -> dispatch());
    }
    private void dispatch() {
        if(dispatched||isFinishing()) return;
        dispatched=true;
        long due=Engine.due(this);
        if(due==0||!MonitorService.unlocked(this)) { finish(); return; }
        try {
            startActivity(Navigator.intent(Config.load(this).address));
            Config.handled(this,Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay());
        } catch (android.content.ActivityNotFoundException e) {
            Config.status(this,"Install or enable Google Maps. Departure still pending.");
        } catch (SecurityException e) {
            Config.status(this,"Maps launch was blocked. Departure still pending.");
        }
        Engine.refresh(this);
        MonitorService.update(this);
        finish();
    }
}
