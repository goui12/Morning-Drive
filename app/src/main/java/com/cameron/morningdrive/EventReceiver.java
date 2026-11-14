package com.cameron.morningdrive;

import android.content.*;

public final class EventReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i) {
        Config v=Config.load(c);
        Engine.refresh(c);
        if(v.enabled&&v.valid()) MonitorService.start(c);
        else MonitorService.stop(c);
    }
}
