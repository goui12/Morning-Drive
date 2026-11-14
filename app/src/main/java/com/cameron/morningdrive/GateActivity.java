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
        Engine.Departure departure=Engine.dueDeparture(this);
        if(departure==null||departure.epochDay!=LocalDate.now().toEpochDay()||!MonitorService.unlocked(this)) { finish(); return; }
        try {
            startActivity(Navigator.intent(departure.location.address));
            Config.handled(this,departure.epochDay);
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
