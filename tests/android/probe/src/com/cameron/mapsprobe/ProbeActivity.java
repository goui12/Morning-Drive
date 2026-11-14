package com.cameron.mapsprobe;
import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
public final class ProbeActivity extends Activity {
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        String uri=String.valueOf(getIntent().getData());
        android.util.Log.i("MorningDriveMapsProbe",uri);
        TextView label=new TextView(this); label.setText("EMULATOR TEST PROBE\n\nReceived navigation request:\n"+uri);
        label.setTextSize(22); label.setPadding(30,90,30,30); setContentView(label);
    }
}
