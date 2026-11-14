package com.cameron.morningdrive;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Single-screen, dependency-free configuration and permission setup. */
public final class MainActivity extends Activity {
    private static final int BG = 0xff11191e;
    private static final int PANEL = 0xff1c272e;
    private static final int TEXT = 0xffeef5f4;
    private static final int MUTED = 0xffa8b9bd;
    private static final int ACCENT = 0xff80e4cb;
    private static final int LINE = 0xff405159;
    private static final int NOTIFICATIONS_REQUEST = 12;
    private EditText addressInput;
    private Button timeButton;
    private Button toggleButton;
    private Button alarmButton;
    private Button overlayButton;
    private Button notificationButton;
    private TextView stateText;
    private TextView nextText;
    private TextView lastText;
    private Button[] dayButtons = new Button[7];
    private int hour;
    private int minute;
    private int daysMask;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable statusTick = new Runnable() {
        @Override public void run() {
            updateStatus();
            handler.postDelayed(this, 1500);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);

        Config config = Config.load(this);
        hour = state != null ? state.getInt("hour", config.hour) : config.hour;
        minute = state != null ? state.getInt("minute", config.minute) : config.minute;
        daysMask = state != null ? state.getInt("days", config.daysMask) : config.daysMask;

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(BG);
        outer.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        outer.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(22), dp(22), dp(24));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        setContentView(outer);

        TextView title = text("Morning Drive", 29, TEXT);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        content.addView(title);
        TextView sub = text("Your route, right on schedule.", 15, MUTED);
        add(content, sub, 5);

        LinearLayout statusBox = new LinearLayout(this);
        statusBox.setOrientation(LinearLayout.VERTICAL);
        statusBox.setPadding(dp(16), dp(14), dp(16), dp(14));
        statusBox.setBackground(shape(PANEL, 0, 14));
        stateText = text("Not configured", 16, ACCENT);
        stateText.setTypeface(Typeface.DEFAULT_BOLD);
        statusBox.addView(stateText);
        nextText = text("Save your destination and schedule to begin.", 14, TEXT);
        add(statusBox, nextText, 6);
        lastText = text("", 12, MUTED);
        add(statusBox, lastText, 6);
        add(content, statusBox, 24);

        label(content, "DESTINATION", 24);
        addressInput = new EditText(this);
        addressInput.setTextColor(TEXT);
        addressInput.setHintTextColor(MUTED);
        addressInput.setTextSize(16);
        addressInput.setSingleLine(false);
        addressInput.setMaxLines(3);
        addressInput.setMinHeight(dp(56));
        addressInput.setPadding(dp(14), dp(14), dp(14), dp(14));
        addressInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS);
        addressInput.setHint("Work address, city, state");
        addressInput.setBackground(shape(PANEL, LINE, 10));
        addressInput.setText(state != null ? state.getString("address", config.address) : config.address);
        addressInput.setContentDescription("Destination address");
        add(content, addressInput, 8);

        label(content, "START NAVIGATION AT", 22);
        timeButton = button("", false);
        timeButton.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        timeButton.setTextSize(22);
        timeButton.setOnClickListener(v -> new TimePickerDialog(this, (picker, h, m) -> {
            hour = h;
            minute = m;
            updateTime();
        }, hour, minute, DateFormat.is24HourFormat(this)).show());
        add(content, timeButton, 8);
        updateTime();
        TextView zone = text("Uses your phone’s local time and time zone.", 12, MUTED);
        add(content, zone, 5);

        label(content, "REPEAT ON", 22);
        LinearLayout days = new LinearLayout(this);
        days.setOrientation(LinearLayout.HORIZONTAL);
        String[] shortDays = {"M", "T", "W", "T", "F", "S", "S"};
        String[] fullDays = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        for (int i = 0; i < 7; i++) {
            final int day = i;
            Button b = button(shortDays[i], false);
            b.setPadding(0, dp(6), 0, dp(6));
            b.setTextSize(15);
            b.setMinWidth(0);
            b.setMinimumWidth(0);
            b.setContentDescription(fullDays[i]);
            b.setOnClickListener(v -> {
                daysMask ^= 1 << day;
                updateDays();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1f);
            if (i > 0) lp.leftMargin = dp(5);
            days.addView(b, lp);
            dayButtons[i] = b;
        }
        add(content, days, 8);
        updateDays();

        Button save = button("Save", true);
        save.setOnClickListener(v -> saveConfiguration());
        add(content, save, 24);

        Button test = button("Test navigation", false);
        test.setOnClickListener(v -> {
            String destination = addressInput.getText().toString().trim();
            if (destination.isEmpty()) {
                addressInput.setError("Enter a destination first");
                addressInput.requestFocus();
                return;
            }
            Navigator.test(this, destination);
        });
        add(content, test, 10);
        TextView testNote = text("Opens Google Maps now. Complete Maps setup and allow its location access before relying on automation.", 12, MUTED);
        add(content, testNote, 7);

        label(content, "ONE-TIME SETUP", 26);
        TextView setupNote = text("Allow these three permissions, then save. While enabled, a small ongoing notification keeps unlock detection available.", 13, MUTED);
        add(content, setupNote, 8);
        alarmButton = button("Allow alarms & reminders", false);
        alarmButton.setOnClickListener(v -> openAlarmSettings());
        add(content, alarmButton, 12);
        overlayButton = button("Allow appear on top", false);
        overlayButton.setOnClickListener(v -> openSettings(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()))));
        add(content, overlayButton, 8);
        notificationButton = button("Allow notifications", false);
        notificationButton.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATIONS_REQUEST);
            } else {
                openSettings(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
            }
        });
        add(content, notificationButton, 8);

        Button battery = button("Samsung battery settings", false);
        battery.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Keep Morning Drive available")
                .setMessage("In App info, open Battery and select Unrestricted.\n\nAlso remove Morning Drive from Samsung’s Sleeping apps or Deep sleeping apps if listed. You can add it to Never auto sleeping apps under Settings → Battery → Background usage limits.\n\nForce stop prevents automation until you open Morning Drive again. Stopping its ongoing service can prevent unlock detection until the next scheduled departure or app launch.")
                .setNegativeButton("Close", null)
                .setPositiveButton("Open app info", (dialog, which) -> openAppInfo())
                .show());
        add(content, battery, 8);

        TextView behavior = text("Phone locked at departure? The trip waits until you unlock. Pending trips expire at midnight. Restart recovery happens after your first unlock. Disable cancels pending and future trips.", 13, MUTED);
        add(content, behavior, 22);
        TextView limitation = text("Google Maps handles routing and GPS. A successful handoff cannot verify that Maps started guidance; test on your phone before relying on it.", 12, MUTED);
        add(content, limitation, 10);

        toggleButton = button("Disable", false);
        toggleButton.setOnClickListener(v -> toggleEnabled());
        add(content, toggleButton, 24);
        TextView privacy = text("Settings stay on this phone. No account or tracking.", 11, MUTED);
        privacy.setGravity(Gravity.CENTER);
        add(content, privacy, 15);
        updateStatus();
    }

    @Override protected void onResume() {
        super.onResume();
        Config config = Config.load(this);
        if (config.enabled && config.valid() && ready()) {
            Engine.refresh(this);
            MonitorService.start(this);
        }
        updateStatus();
        handler.removeCallbacks(statusTick);
        handler.postDelayed(statusTick, 1500);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(statusTick);
        super.onPause();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("address", addressInput.getText().toString());
        out.putInt("hour", hour);
        out.putInt("minute", minute);
        out.putInt("days", daysMask);
        super.onSaveInstanceState(out);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        updateStatus();
    }

    private void saveConfiguration() {
        String address = addressInput.getText().toString().trim();
        if (address.isEmpty()) {
            addressInput.setError("Enter your destination address");
            addressInput.requestFocus();
            return;
        }
        if (daysMask == 0) {
            new AlertDialog.Builder(this).setTitle("Choose a day")
                    .setMessage("Select at least one day for navigation.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        if (!ready()) {
            showSetupNeeded();
            return;
        }
        Config.save(this, address, hour, minute, daysMask);
        Engine.refresh(this);
        MonitorService.start(this);
        updateStatus();
        Toast.makeText(this, "Saved. Navigation automation is enabled.", Toast.LENGTH_LONG).show();
    }

    private void toggleEnabled() {
        Config config = Config.load(this);
        if (!config.valid()) {
            Toast.makeText(this, "Save your destination and schedule first.", Toast.LENGTH_LONG).show();
            return;
        }
        if (!config.enabled && !ready()) {
            showSetupNeeded();
            return;
        }
        Config.setEnabled(this, !config.enabled);
        Engine.refresh(this);
        if (config.enabled) MonitorService.stop(this); else MonitorService.start(this);
        updateStatus();
    }

    private void showSetupNeeded() {
        StringBuilder message = new StringBuilder("Complete the following setup buttons below, then tap Save or Enable:\n");
        if (!Engine.canSchedule(this)) message.append("\n• Alarms & reminders");
        if (!Settings.canDrawOverlays(this)) message.append("\n• Appear on top");
        if (!notificationsAllowed()) message.append("\n• Notifications");
        message.append("\n\nYour entries remain on this screen.");
        new AlertDialog.Builder(this).setTitle("Finish setup first")
                .setMessage(message.toString()).setPositiveButton("OK", null).show();
    }

    private boolean notificationsAllowed() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        return manager != null && manager.areNotificationsEnabled()
                && (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED);
    }

    private boolean ready() {
        return Engine.canSchedule(this) && Settings.canDrawOverlays(this) && notificationsAllowed();
    }

    private void updateStatus() {
        if (stateText == null) return;
        Config c = Config.load(this);
        boolean configured = c.valid();
        stateText.setText(!configured ? "Not configured" : !c.enabled ? "Automation paused"
                : ready() ? "Automation enabled" : "Setup needs attention");
        stateText.setTextColor(c.enabled && ready() ? ACCENT : MUTED);
        if (!configured) {
            nextText.setText("Save your destination and schedule to begin.");
        } else if (!c.enabled) {
            nextText.setText("Your settings are saved. Tap Enable to resume.");
        } else if (!ready()) {
            nextText.setText("Restore the missing permissions below.");
        } else {
            long next = Engine.nextTime(this);
            nextText.setText(next > 0 ? "Next: " + new SimpleDateFormat(
                    DateFormat.is24HourFormat(this) ? "EEE, MMM d · HH:mm" : "EEE, MMM d · h:mm a",
                    Locale.getDefault()).format(new Date(next)) : "Waiting for the next selected day.");
        }
        lastText.setText(c.status == null ? "" : c.status);
        lastText.setVisibility(c.status == null || c.status.isEmpty() ? View.GONE : View.VISIBLE);
        toggleButton.setText(c.enabled ? "Disable" : "Enable");
        toggleButton.setEnabled(configured);
        toggleButton.setAlpha(configured ? 1f : .45f);
        updatePermissionButton(alarmButton, Engine.canSchedule(this), "Alarms & reminders allowed", "Allow alarms & reminders");
        updatePermissionButton(overlayButton, Settings.canDrawOverlays(this), "Appear on top allowed", "Allow appear on top");
        updatePermissionButton(notificationButton, notificationsAllowed(), "Notifications allowed", "Allow notifications");
    }

    private void updatePermissionButton(Button b, boolean allowed, String yes, String no) {
        b.setText(allowed ? "✓  " + yes : no);
        b.setTextColor(allowed ? ACCENT : TEXT);
        b.setEnabled(!allowed);
    }

    private void updateTime() {
        Calendar time = Calendar.getInstance();
        time.set(Calendar.HOUR_OF_DAY, hour);
        time.set(Calendar.MINUTE, minute);
        String value = DateFormat.getTimeFormat(this).format(time.getTime());
        timeButton.setText(value + "    ›");
        timeButton.setContentDescription("Navigation start time " + value + ". Tap to change.");
    }

    private void updateDays() {
        for (int i = 0; i < 7; i++) {
            boolean selected = (daysMask & (1 << i)) != 0;
            dayButtons[i].setSelected(selected);
            dayButtons[i].setBackground(shape(selected ? ACCENT : PANEL, selected ? 0 : LINE, 10));
            dayButtons[i].setTextColor(selected ? BG : TEXT);
            if (Build.VERSION.SDK_INT >= 30) dayButtons[i].setStateDescription(selected ? "Selected" : "Not selected");
        }
    }

    private void openAlarmSettings() {
        if (Build.VERSION.SDK_INT >= 31) {
            openSettings(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName())));
        }
    }

    private void openAppInfo() {
        openSettings(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName())));
    }

    private void openSettings(Intent intent) {
        try { startActivity(intent); }
        catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "Open Settings → Apps → Morning Drive to change this permission.", Toast.LENGTH_LONG).show();
        }
    }

    private void label(LinearLayout parent, String value, int top) {
        TextView t = text(value, 11, MUTED);
        t.setLetterSpacing(.12f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        add(parent, t, top);
    }

    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setLineSpacing(dp(2), 1f);
        return t;
    }

    private Button button(String value, boolean primary) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setTextColor(primary ? BG : TEXT);
        b.setMinHeight(dp(50));
        b.setMinimumHeight(dp(50));
        b.setPadding(dp(14), dp(10), dp(14), dp(10));
        b.setBackground(shape(primary ? ACCENT : PANEL, primary ? 0 : LINE, 10));
        return b;
    }

    private GradientDrawable shape(int fill, int border, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        if (border != 0) d.setStroke(dp(1), border);
        return d;
    }

    private void add(LinearLayout parent, View child, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(top);
        parent.addView(child, lp);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
