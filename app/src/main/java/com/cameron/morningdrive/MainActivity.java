package com.cameron.morningdrive;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Calendar;
import java.util.Locale;
import java.util.UUID;

/** A weekly planner with explicit saving and a small, guided first-run setup. */
public final class MainActivity extends Activity {
    private static final int BG=0xff11191e, PANEL=0xff1c272e, TEXT=0xffeef5f4;
    private static final int MUTED=0xffa8b9bd, ACCENT=0xff80e4cb, LINE=0xff405159, AMBER=0xffffd39a;
    private static final String[] DAYS={"Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday"};
    private static final String DOWNLOAD="https://github.com/goui12/Morning-Drive/releases/latest/download/Morning_Drive.apk";
    private Config draft;
    private boolean dirty, locationsOpen, setupOpen;
    private ScrollView scroll;
    private LinearLayout dayList, locationList, setupPanel;
    private TextView stateText, nextText, noteText, dirtyText;
    private Button defaultTimeButton, locationsHeader, setupHeader, toggleButton, skipButton, finishSetupButton;
    private Button alarmButton, overlayButton, notificationButton;
    private AlertDialog activeDialog;
    private String activeKind="", editLocationId="";
    private EditText nameInput, addressInput;
    private int editDay=-1;
    private Config.Day dayWorking;
    private int returnDay=-1;
    private Config.Day returnDayWorking;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable tick=new Runnable() {
        @Override public void run() { updateStatus(); handler.postDelayed(this,2000); }
    };
    private android.window.OnBackInvokedCallback backCallback;
    private interface Chosen { void use(Config.Location place); }
    private interface DaysChosen { void use(boolean[] days); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        if(Build.VERSION.SDK_INT>=30) getWindow().setDecorFitsSystemWindows(false);
        draft=Config.load(this);
        dirty=state!=null&&state.getBoolean("dirty",false);
        locationsOpen=state==null?draft.locations.isEmpty():state.getBoolean("locationsOpen");
        setupOpen=state==null?!ready():state.getBoolean("setupOpen");
        if(state!=null) restoreDraft(state.getString("draft",""));
        buildScreen();
        if(Build.VERSION.SDK_INT>=33) {
            backCallback=() -> leave();
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,backCallback);
        }
        if(state!=null) {
            final int savedY=state.getInt("scroll",0);
            scroll.post(() -> scroll.scrollTo(0,savedY));
            String kind=state.getString("dialog","");
            if("location".equals(kind)) {
                String id=state.getString("editLocation","");
                returnDay=state.getInt("returnDay",-1);
                if(returnDay>=0&&returnDay<7) {
                    try{returnDayWorking=readDay(new JSONObject(state.getString("returnDayWorking","{}")));}
                    catch(Exception ignored){returnDay=-1;}
                }
                showLocationEditor(draft.findLocation(id),null,state.getString("editName",""),state.getString("editAddress",""));
            } else if("day".equals(kind)) {
                int index=state.getInt("editDay",-1);
                if(index>=0&&index<7) {
                    try { showDayEditor(index,readDay(new JSONObject(state.getString("dayWorking","{}")))); }
                    catch(Exception ignored) { showDayEditor(index,null); }
                }
            }
        }
    }

    private void buildScreen() {
        LinearLayout outer=column(); outer.setBackgroundColor(BG);
        outer.setOnApplyWindowInsetsListener((v,insets) -> {
            if(Build.VERSION.SDK_INT>=30) {
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                android.graphics.Insets ime=insets.getInsets(WindowInsets.Type.ime());
                v.setPadding(bars.left,bars.top,bars.right,Math.max(bars.bottom,ime.bottom));
            } else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        scroll=new ScrollView(this); scroll.setFillViewport(true);
        outer.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        LinearLayout content=column(); content.setPadding(dp(20),dp(20),dp(20),dp(24));
        scroll.addView(content,new ScrollView.LayoutParams(-1,-2)); setContentView(outer);
        TextView title=text("Morning Drive",29,TEXT); title.setTypeface(Typeface.create("sans-serif-medium",0)); content.addView(title);
        add(content,text("Plan your week. We’ll open the route.",15,MUTED),4);

        LinearLayout status=card(); stateText=text("",15,ACCENT); stateText.setTypeface(Typeface.DEFAULT_BOLD); status.addView(stateText);
        nextText=text("",19,TEXT); add(status,nextText,7);
        noteText=text("",12,MUTED); add(status,noteText,6);
        finishSetupButton=button("Finish setup",false);finishSetupButton.setOnClickListener(v -> showSetup());add(status,finishSetupButton,12);
        skipButton=button("Skip next departure",false); skipButton.setOnClickListener(v -> skipNext()); add(status,skipButton,12);
        add(content,status,22);

        label(content,"USUAL DEPARTURE TIME",22);
        defaultTimeButton=button("",false); defaultTimeButton.setGravity(Gravity.START|Gravity.CENTER_VERTICAL); defaultTimeButton.setTextSize(21);
        defaultTimeButton.setOnClickListener(v -> new TimePickerDialog(this,(p,h,m) -> {
            draft.defaultHour=h; draft.defaultMinute=m; changed();
        },draft.defaultHour,draft.defaultMinute,DateFormat.is24HourFormat(this)).show());
        add(content,defaultTimeButton,8);
        add(content,text("Days use this time unless you set a different one.",12,MUTED),5);

        locationsHeader=button("",false); locationsHeader.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        locationsHeader.setOnClickListener(v -> {locationsOpen=!locationsOpen; refreshLocations();});
        add(content,locationsHeader,22); locationList=column(); add(content,locationList,8);

        label(content,"YOUR REPEATING WEEK",22);
        add(content,text("Tap a day to choose its location and time.",13,MUTED),6);
        Button assign=button("Assign one location to several days",false);
        assign.setOnClickListener(v -> chooseLocation(place -> assignLocation(place))); add(content,assign,10);
        dayList=column(); add(content,dayList,4);
        dirtyText=text("",13,AMBER); add(content,dirtyText,14);
        Button save=button("Save schedule",true); save.setOnClickListener(v -> savePlan()); add(content,save,8);
        add(content,text("Repeats each week until you change it. Changes start after you save.",12,MUTED),7);

        setupHeader=button("",false); setupHeader.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        setupHeader.setOnClickListener(v -> {setupOpen=!setupOpen; updateStatus();}); add(content,setupHeader,24);
        setupPanel=column(); add(content,setupPanel,8);
        add(setupPanel,text("These settings let Morning Drive start your route on time. Tap each button once to set it up.",13,MUTED),0);
        alarmButton=button("Allow alarms & reminders",false); alarmButton.setOnClickListener(v -> {
            if(Build.VERSION.SDK_INT>=31) open(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,packageUri()));
        }); add(setupPanel,alarmButton,12);
        overlayButton=button("Allow appear on top",false); overlayButton.setOnClickListener(v -> open(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,packageUri()))); add(setupPanel,overlayButton,8);
        notificationButton=button("Allow notifications",false); notificationButton.setOnClickListener(v -> {
            if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},12);
            else open(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));
        }); add(setupPanel,notificationButton,8);
        Button battery=button("Set battery to Unrestricted",false);
        battery.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("One battery setting")
            .setMessage("Tap Open settings, then Battery → Unrestricted.\n\nIf Samsung lists Morning Drive under Sleeping apps or Deep sleeping apps, remove it from that list.")
            .setNegativeButton("Close",null).setPositiveButton("Open settings",(d,w) -> open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,packageUri()))).show());
        add(setupPanel,battery,8);
        add(setupPanel,text("While enabled, a quiet notification keeps unlock detection ready. If the phone is locked at departure, the trip waits until you unlock. Pending trips end at midnight.",12,MUTED),12);
        Button about=button("How it works",false); about.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Morning Drive")
            .setMessage("Your saved week repeats until changed. Each day has at most one automatic navigation launch. Saving or enabling applies to future departures.\n\nA restart restores your schedule after the first unlock. Disable pauses all trips and keeps your locations.\n\nUse Test navigation on a saved location to check Google Maps and its location permission. Maps may need a location fix or initial setup.\n\nForce stop prevents automation until you reopen this app. Stopping its ongoing service can interrupt unlock detection until the next scheduled trip or app launch.\n\nYour locations and schedule stay on this phone.")
            .setPositiveButton("Got it",null).show()); add(setupPanel,about,8);

        Button update=button("Check for updates",false); update.setOnClickListener(v -> open(new Intent(Intent.ACTION_VIEW,Uri.parse(DOWNLOAD)))); add(content,update,16);
        toggleButton=button("Enable automation",false); toggleButton.setOnClickListener(v -> toggle()); add(content,toggleButton,22);
        TextView foot=text("Morning Drive 2.0 · Settings stay on this phone",11,MUTED); foot.setGravity(Gravity.CENTER); add(content,foot,14);
        refreshPlanner(); updateStatus();
    }

    private void refreshPlanner() {
        defaultTimeButton.setText(time(draft.defaultHour,draft.defaultMinute)+"   ›");
        defaultTimeButton.setContentDescription("Usual departure time "+time(draft.defaultHour,draft.defaultMinute)+". Change time.");
        dayList.removeAllViews();
        for(int i=0;i<7;i++) {
            final int index=i; Config.Day day=draft.days[i]; Config.Location place=draft.findLocation(day.locationId);
            LinearLayout row=card(); row.setPadding(dp(14),dp(10),dp(14),dp(10));
            LinearLayout heading=new LinearLayout(this); heading.setGravity(Gravity.CENTER_VERTICAL);
            TextView name=text(DAYS[i],16,TEXT); name.setTypeface(Typeface.DEFAULT_BOLD); heading.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            Switch on=new Switch(this); on.setChecked(day.enabled); on.setMinHeight(dp(48)); on.setContentDescription(DAYS[i]+" navigation");
            on.setOnCheckedChangeListener((b,checked) -> {
                if(checked&&draft.findLocation(draft.days[index].locationId)==null) {
                    on.setChecked(false);
                    chooseLocation(chosen -> {draft.days[index].locationId=chosen.id;draft.days[index].enabled=true;changed();});
                } else if(draft.days[index].enabled!=checked) {draft.days[index].enabled=checked;changed();}
            }); heading.addView(on,new LinearLayout.LayoutParams(-2,dp(48))); row.addView(heading);
            LinearLayout details=column(); details.setPadding(0,0,0,dp(5)); details.setMinimumHeight(dp(58));
            if(day.enabled) {
                TextView dest=text(place==null?"Choose a location":place.name,16,place==null?AMBER:ACCENT); details.addView(dest);
                if(place!=null) add(details,text(place.address,12,MUTED),2);
                add(details,text(time(day.customTime?day.hour:draft.defaultHour,day.customTime?day.minute:draft.defaultMinute)+(day.customTime?" · Custom time":" · Usual time")+"   ›",13,TEXT),5);
            } else {
                details.addView(text("Off",15,MUTED));
                add(details,text(place==null?"Tap to plan this day":place.name+" · "+time(day.customTime?day.hour:draft.defaultHour,day.customTime?day.minute:draft.defaultMinute),12,MUTED),3);
            }
            details.setContentDescription("Edit "+DAYS[i]+(place==null?"":", "+place.name+", "+place.address));
            details.setFocusable(true); details.setClickable(true); details.setOnClickListener(v -> showDayEditor(index,null));
            name.setOnClickListener(v -> showDayEditor(index,null));
            row.addView(details); add(dayList,row,9);
        }
        refreshLocations(); refreshDirty();
    }

    private void refreshLocations() {
        locationsHeader.setText((locationsOpen?"▾  ":"▸  ")+"Saved locations ("+draft.locations.size()+")");
        locationList.setVisibility(locationsOpen?View.VISIBLE:View.GONE); locationList.removeAllViews();
        if(draft.locations.isEmpty()) add(locationList,text("Add Airport, Bank, or any place you visit. You can assign days later.",13,MUTED),0);
        for(Config.Location place:draft.locations) {
            LinearLayout row=card(); row.setMinimumHeight(dp(64));
            TextView name=text(place.name+"   ›",16,ACCENT); name.setTypeface(Typeface.DEFAULT_BOLD); row.addView(name);
            add(row,text(place.address,12,MUTED),4);
            row.setFocusable(true); row.setClickable(true); row.setContentDescription(place.name+", "+place.address+". Location options.");
            row.setOnClickListener(v -> locationOptions(place)); add(locationList,row,8);
        }
        Button add=button("+ Add a location",false); add.setOnClickListener(v -> showLocationEditor(null,null,null,null)); add(locationList,add,9);
    }

    private void locationOptions(Config.Location place) {
        new AlertDialog.Builder(this).setTitle(place.name).setItems(new String[]{"Assign to days","Edit name or address","Test navigation","Remove location"},(d,which) -> {
            if(which==0)assignLocation(place);
            if(which==1)showLocationEditor(place,null,null,null);
            if(which==2)Navigator.test(this,place.address);
            if(which==3)removeLocation(place);
        }).setNegativeButton("Close",null).show();
    }

    private void showLocationEditor(Config.Location existing,Chosen after,String restoredName,String restoredAddress) {
        String id=existing==null?"":existing.id;
        activeKind="location"; editLocationId=id;
        LinearLayout body=dialogColumn();
        add(body,text("LOCATION NAME",11,MUTED),0); nameInput=input("e.g. Airport",false);
        nameInput.setText(restoredName!=null?restoredName:existing==null?"":existing.name); add(body,nameInput,6);
        add(body,text("FULL ADDRESS",11,MUTED),16); addressInput=input("Street, city, state",true);
        addressInput.setText(restoredAddress!=null?restoredAddress:existing==null?"":existing.address); add(body,addressInput,6);
        add(body,text("Give each place a name you’ll recognize in your week.",12,MUTED),10);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(existing==null?"Add a location":"Edit location").setView(wrap(body))
            .setNegativeButton("Cancel",null).setPositiveButton(existing==null?"Add":"Done",null).create(); activeDialog=dialog;
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name=nameInput.getText().toString().trim(),address=addressInput.getText().toString().trim();
            if(name.isEmpty()){nameInput.setError("Enter a short name");return;}
            if(address.isEmpty()){addressInput.setError("Enter the full address");return;}
            for(Config.Location p:draft.locations) if(!p.id.equals(id)&&p.name.equalsIgnoreCase(name)){nameInput.setError("That name is already saved");return;}
            Config.Location chosen;
            if(existing==null){chosen=new Config.Location(UUID.randomUUID().toString(),name,address);draft.locations.add(chosen);}
            else{chosen=existing;chosen.name=name;chosen.address=address;}
            if(returnDayWorking!=null){returnDayWorking.locationId=chosen.id;returnDayWorking.enabled=true;}
            dialog.dismiss(); locationsOpen=true; changed(); if(after!=null)after.use(chosen);
        }));
        dialog.setOnDismissListener(d -> {
            if(activeDialog==dialog){activeDialog=null;activeKind="";nameInput=null;addressInput=null;}
            int index=returnDay;Config.Day values=returnDayWorking;returnDay=-1;returnDayWorking=null;
            if(index>=0&&index<7&&values!=null&&!draft.locations.isEmpty()&&!isFinishing()&&!isChangingConfigurations())showDayEditor(index,values);
        });
        dialog.show();
    }

    private void removeLocation(Config.Location place) {
        StringBuilder used=new StringBuilder();
        for(int i=0;i<7;i++)if(place.id.equals(draft.days[i].locationId)&&draft.days[i].enabled){if(used.length()>0)used.append(", ");used.append(DAYS[i]);}
        String message=used.length()==0?"Remove this saved location?":"This will also turn off "+used+". Tap Save schedule to apply the change.";
        new AlertDialog.Builder(this).setTitle("Remove "+place.name+"?").setMessage(message).setNegativeButton("Cancel",null)
            .setPositiveButton("Remove",(d,w) -> {
                draft.locations.remove(place);
                for(Config.Day day:draft.days)if(place.id.equals(day.locationId)){day.locationId="";day.enabled=false;}
                changed();
            }).show();
    }

    private void chooseLocation(Chosen callback) {
        if(draft.locations.isEmpty()) {addFromLocationPicker(callback);return;}
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Choose a saved location").setAdapter(new BaseAdapter(){
            @Override public int getCount(){return draft.locations.size();}
            @Override public Object getItem(int p){return draft.locations.get(p);}
            @Override public long getItemId(int p){return p;}
            @Override public View getView(int p,View recycled,ViewGroup parent){
                Config.Location place=draft.locations.get(p); LinearLayout row=column();row.setPadding(dp(22),dp(14),dp(22),dp(14));row.setMinimumHeight(dp(64));
                TextView name=text(place.name,17,TEXT);name.setTypeface(Typeface.DEFAULT_BOLD);row.addView(name);add(row,text(place.address,12,MUTED),4);return row;
            }
        },(d,index) -> callback.use(draft.locations.get(index))).setNegativeButton("Cancel",null)
            .setNeutralButton("Add location",(d,w) -> addFromLocationPicker(callback)).create(); dialog.show();
    }

    private void addFromLocationPicker(Chosen callback) {
        if("day".equals(activeKind)&&dayWorking!=null&&editDay>=0) {
            returnDay=editDay;returnDayWorking=copyDay(dayWorking);
            if(activeDialog!=null)activeDialog.dismiss();
            showLocationEditor(null,null,null,null);
        } else showLocationEditor(null,callback,null,null);
    }

    private void assignLocation(Config.Location place) {
        new AlertDialog.Builder(this).setTitle("Use "+place.name).setItems(new String[]{"Choose days…","All enabled days"},(d,which) -> {
            if(which==0)chooseDays("Days for "+place.name,new boolean[7],selected -> {
                for(int i=0;i<7;i++)if(selected[i]){draft.days[i].locationId=place.id;draft.days[i].enabled=true;}
                changed();
            });
            else {
                boolean any=false;
                for(Config.Day day:draft.days)if(day.enabled){day.locationId=place.id;any=true;}
                if(any)changed();else toast("Turn on a day first, or choose specific days.");
            }
        }).setNegativeButton("Cancel",null).show();
    }

    private void chooseDays(String title,boolean[] initial,DaysChosen action) {
        boolean[] selected=initial.clone();
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title).setMultiChoiceItems(DAYS,selected,(d,w,on) -> selected[w]=on)
            .setNegativeButton("Cancel",null).setPositiveButton("Apply",null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            boolean any=false;for(boolean x:selected)any|=x;
            if(!any){toast("Choose at least one day.");return;}
            dialog.dismiss();action.use(selected);
        }));dialog.show();
    }

    private void showDayEditor(int index,Config.Day restored) {
        if(draft.locations.isEmpty()) {
            returnDay=index;returnDayWorking=copyDay(restored==null?draft.days[index]:restored);
            showLocationEditor(null,null,null,null); return;
        }
        editDay=index; dayWorking=copyDay(restored==null?draft.days[index]:restored); activeKind="day";
        final AlertDialog[] owner={null};
        LinearLayout body=dialogColumn(); Switch on=new Switch(this); on.setText("Navigate on "+DAYS[index]); on.setTextColor(TEXT);on.setTextSize(16);on.setMinHeight(dp(48));on.setChecked(dayWorking.enabled);body.addView(on);
        on.setOnCheckedChangeListener((b,value) -> dayWorking.enabled=value);
        Button location=button("",false);updateDayLocationButton(location);add(body,location,14);
        location.setOnClickListener(v -> chooseLocation(place -> {
            if(dayWorking==null)return;
            dayWorking.locationId=place.id; dayWorking.enabled=true;on.setChecked(true);updateDayLocationButton(location);
            if(owner[0]!=null){activeDialog=owner[0];activeKind="day";editDay=index;}
        }));
        Switch custom=new Switch(this);custom.setText("Use a different time");custom.setTextSize(16);custom.setTextColor(TEXT);custom.setMinHeight(dp(48));custom.setChecked(dayWorking.customTime);add(body,custom,14);
        Button departure=button(time(dayWorking.customTime?dayWorking.hour:draft.defaultHour,dayWorking.customTime?dayWorking.minute:draft.defaultMinute),false);add(body,departure,4);
        departure.setVisibility(dayWorking.customTime?View.VISIBLE:View.GONE);
        TextView usual=text("Usual time: "+time(draft.defaultHour,draft.defaultMinute),13,MUTED);add(body,usual,5);
        custom.setOnCheckedChangeListener((b,value) -> {
            dayWorking.customTime=value;
            if(value){dayWorking.hour=draft.defaultHour;dayWorking.minute=draft.defaultMinute;departure.setText(time(dayWorking.hour,dayWorking.minute));}
            departure.setVisibility(value?View.VISIBLE:View.GONE);
        });
        departure.setOnClickListener(v -> new TimePickerDialog(this,(p,h,m) -> {
            dayWorking.hour=h;dayWorking.minute=m;departure.setText(time(h,m));
        },dayWorking.hour,dayWorking.minute,DateFormat.is24HourFormat(this)).show());
        Button copy=button("Copy this day to…",false);add(body,copy,18);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(DAYS[index]).setView(wrap(body)).setNegativeButton("Cancel",null).setPositiveButton("Done",null).create();activeDialog=dialog;owner[0]=dialog;
        copy.setOnClickListener(v -> {
            if(!checkDay(dayWorking))return;
            Config.Day values=copyDay(dayWorking);
            chooseDays("Copy "+DAYS[index]+" to",new boolean[7],selected -> {
                draft.days[index]=copyDay(values);
                for(int i=0;i<7;i++)if(selected[i])draft.days[i]=copyDay(values);
                dialog.dismiss();changed();
            });
        });
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if(!checkDay(dayWorking))return;draft.days[index]=copyDay(dayWorking);dialog.dismiss();changed();
        }));
        dialog.setOnDismissListener(d -> {if(activeDialog==dialog){activeDialog=null;activeKind="";editDay=-1;dayWorking=null;}});dialog.show();
    }

    private void updateDayLocationButton(Button b) {
        Config.Location place=draft.findLocation(dayWorking.locationId);
        b.setText(place==null?"Choose location   ›":place.name+"\n"+place.address+"   ›");
        b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
    }
    private boolean checkDay(Config.Day day) {
        if(day.enabled&&draft.findLocation(day.locationId)==null){toast("Choose a location for this day.");return false;} return true;
    }
    private Config.Day copyDay(Config.Day day) {
        Config.Day result=new Config.Day();result.enabled=day.enabled;result.customTime=day.customTime;result.locationId=day.locationId;result.hour=day.hour;result.minute=day.minute;return result;
    }

    private void savePlan() {
        for(int i=0;i<7;i++)if(draft.days[i].enabled&&draft.findLocation(draft.days[i].locationId)==null){toast("Choose a location for "+DAYS[i]+".");showDayEditor(i,null);return;}
        try {
            Config.save(this,draft,ready());
            Engine.refresh(this);
            if(Config.load(this).enabled)MonitorService.start(this);else MonitorService.stop(this);
            draft=Config.load(this);dirty=false;refreshPlanner();updateStatus();
            if(draft.valid()&&!ready()){showSetup();toast("Saved. Finish setup, then tap Enable automation.");}
            else toast(draft.enabled?"Schedule saved. Your week repeats automatically.":"Saved. All days are off.");
        } catch(IllegalArgumentException|IllegalStateException e) {new AlertDialog.Builder(this).setTitle("Unable to save").setMessage(e.getMessage()).setPositiveButton("OK",null).show();}
    }
    private void toggle() {
        Config live=Config.load(this);
        if(live.enabled) {
            Config.setEnabled(this,false);Engine.refresh(this);MonitorService.stop(this);updateStatus();toast("Automation paused. Your week is saved.");return;
        }
        if(!live.valid()){toast(dirty?"Save your schedule first.":"Assign a location to a day, then save.");return;}
        if(!ready()){showSetup();toast("Complete the setup buttons, then tap Enable automation.");return;}
        if(dirty)new AlertDialog.Builder(this).setTitle("Enable the saved schedule?")
            .setMessage("Your unsaved edits will stay on screen. Enable uses the schedule you last saved.")
            .setNegativeButton("Cancel",null).setPositiveButton("Enable saved schedule",(d,w) -> enableSaved()).show();
        else enableSaved();
    }
    private void showSetup(){setupOpen=true;updateStatus();scroll.post(() -> scroll.smoothScrollTo(0,Math.max(0,setupHeader.getTop()-dp(12))));}
    private void enableSaved(){Config.setEnabled(this,true);Engine.refresh(this);MonitorService.start(this);updateStatus();}
    private void skipNext() {
        if(Engine.hasSkip(this)){Engine.clearSkip(this);Engine.refresh(this);if(Config.load(this).enabled)MonitorService.start(this);updateStatus();return;}
        new AlertDialog.Builder(this).setTitle("Skip the next departure?").setMessage(Engine.nextSummary(this)+"\n\nYour repeating week stays the same.")
            .setNegativeButton("Keep it",null).setPositiveButton("Skip once",(d,w) -> {
                Engine.skipNext(this);Engine.refresh(this);if(Config.load(this).enabled)MonitorService.start(this);updateStatus();
            }).show();
    }
    private void changed(){dirty=true;refreshPlanner();updateStatus();}
    private void refreshDirty(){dirtyText.setText(dirty?"● Unsaved changes":"Your saved schedule repeats every week.");dirtyText.setTextColor(dirty?AMBER:MUTED);}
    private void updateStatus() {
        if(stateText==null)return;
        Config live=Config.load(this);boolean ready=ready();
        stateText.setText(!live.valid()?"PLAN YOUR WEEK":!live.enabled?"AUTOMATION PAUSED":ready?"AUTOMATION ENABLED":"SETUP NEEDS ATTENTION");
        stateText.setTextColor(live.enabled&&ready?ACCENT:MUTED);
        nextText.setText(!live.valid()?"Add locations, then choose your days.":!live.enabled?"Your saved week is ready when you are.":Engine.nextSummary(this));
        noteText.setText(live.enabled?(live.status==null?"":live.status):!ready?"Complete one-time setup below to enable navigation.":"Tap Enable automation to use your saved schedule.");
        finishSetupButton.setVisibility(ready?View.GONE:View.VISIBLE);
        boolean skipped=Engine.hasSkip(this);skipButton.setText(skipped?"Undo skipped departure":"Skip next departure");
        skipButton.setVisibility(live.enabled&&live.valid()?View.VISIBLE:View.GONE);
        toggleButton.setText(live.enabled?"Disable automation":"Enable automation");
        setupHeader.setText((setupOpen?"▾  ":"▸  ")+(ready?"One-time setup · Ready":"One-time setup · Finish setup"));
        setupPanel.setVisibility(setupOpen?View.VISIBLE:View.GONE);
        permissionButton(alarmButton,Engine.canSchedule(this),"Alarms & reminders");
        permissionButton(overlayButton,Settings.canDrawOverlays(this),"Appear on top");
        permissionButton(notificationButton,notificationsAllowed(),"Notifications");
        refreshDirty();
    }
    private void permissionButton(Button button,boolean allowed,String label){button.setText(allowed?"✓  "+label+" allowed":"Allow "+label.toLowerCase(Locale.ROOT));button.setTextColor(allowed?ACCENT:TEXT);button.setEnabled(!allowed);}
    private boolean notificationsAllowed(){NotificationManager nm=getSystemService(NotificationManager.class);return nm.areNotificationsEnabled()&&(Build.VERSION.SDK_INT<33||checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED);}
    private boolean ready(){return Engine.canSchedule(this)&&Settings.canDrawOverlays(this)&&notificationsAllowed();}
    @Override protected void onResume(){super.onResume();if(draft==null)return;Config live=Config.load(this);if(live.enabled&&live.valid()&&ready()){Engine.refresh(this);MonitorService.start(this);}updateStatus();handler.removeCallbacks(tick);handler.postDelayed(tick,2000);}
    @Override protected void onPause(){handler.removeCallbacks(tick);super.onPause();}
    @Override protected void onDestroy(){if(Build.VERSION.SDK_INT>=33&&backCallback!=null)getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);super.onDestroy();}
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grants){super.onRequestPermissionsResult(requestCode,permissions,grants);updateStatus();}
    @Override public void onBackPressed(){leave();}
    private void leave(){if(!dirty){finish();return;}new AlertDialog.Builder(this).setTitle("Leave without saving?").setMessage("Your last saved schedule will keep running. These edits have not been applied.").setNegativeButton("Keep editing",null).setPositiveButton("Discard edits",(d,w) -> finish()).show();}

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("draft",serializeDraft());out.putBoolean("dirty",dirty);out.putBoolean("locationsOpen",locationsOpen);out.putBoolean("setupOpen",setupOpen);out.putInt("scroll",scroll.getScrollY());
        out.putString("dialog",activeKind);
        if("location".equals(activeKind)&&nameInput!=null){out.putString("editLocation",editLocationId);out.putString("editName",nameInput.getText().toString());out.putString("editAddress",addressInput.getText().toString());out.putInt("returnDay",returnDay);if(returnDayWorking!=null)out.putString("returnDayWorking",dayJson(returnDayWorking).toString());}
        if("day".equals(activeKind)&&dayWorking!=null){out.putInt("editDay",editDay);out.putString("dayWorking",dayJson(dayWorking).toString());}
        super.onSaveInstanceState(out);
    }
    private String serializeDraft(){
        try {JSONObject value=new JSONObject();value.put("hour",draft.defaultHour);value.put("minute",draft.defaultMinute);JSONArray places=new JSONArray();
            for(Config.Location p:draft.locations)places.put(new JSONObject().put("id",p.id).put("name",p.name).put("address",p.address));value.put("locations",places);
            JSONArray days=new JSONArray();for(Config.Day day:draft.days)days.put(dayJson(day));value.put("days",days);return value.toString();
        }catch(Exception e){return "";}
    }
    private JSONObject dayJson(Config.Day day){JSONObject value=new JSONObject();try{value.put("enabled",day.enabled).put("custom",day.customTime).put("location",day.locationId).put("hour",day.hour).put("minute",day.minute);}catch(Exception ignored){}return value;}
    private Config.Day readDay(JSONObject value){Config.Day d=new Config.Day();d.enabled=value.optBoolean("enabled",false);d.customTime=value.optBoolean("custom",false);d.locationId=value.optString("location","");d.hour=value.optInt("hour",7);d.minute=value.optInt("minute",0);return d;}
    private void restoreDraft(String raw){
        if(raw.isEmpty())return;
        try {JSONObject value=new JSONObject(raw);Config restored=Config.load(this);restored.defaultHour=value.getInt("hour");restored.defaultMinute=value.getInt("minute");restored.locations.clear();
            JSONArray places=value.getJSONArray("locations");for(int i=0;i<places.length();i++){JSONObject p=places.getJSONObject(i);restored.locations.add(new Config.Location(p.getString("id"),p.getString("name"),p.getString("address")));}
            JSONArray days=value.getJSONArray("days");if(days.length()!=7)return;for(int i=0;i<7;i++)restored.days[i]=readDay(days.getJSONObject(i));draft=restored;
        }catch(Exception ignored){dirty=false;}
    }

    private Uri packageUri(){return Uri.parse("package:"+getPackageName());}
    private void open(Intent i){try{startActivity(i);}catch(ActivityNotFoundException|SecurityException e){toast("Open your phone’s Settings or browser to continue.");}}
    private String time(int h,int m){Calendar c=Calendar.getInstance();c.set(Calendar.HOUR_OF_DAY,h);c.set(Calendar.MINUTE,m);return DateFormat.getTimeFormat(this).format(c.getTime());}
    private void toast(String s){android.widget.Toast.makeText(this,s,android.widget.Toast.LENGTH_LONG).show();}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout card(){LinearLayout l=column();l.setPadding(dp(16),dp(14),dp(16),dp(14));l.setBackground(shape(PANEL,0,13));return l;}
    private LinearLayout dialogColumn(){LinearLayout l=column();l.setPadding(dp(22),dp(8),dp(22),dp(12));return l;}
    private ScrollView wrap(View v){ScrollView s=new ScrollView(this);s.addView(v,new ScrollView.LayoutParams(-1,-2));return s;}
    private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(dp(2),1f);return t;}
    private void label(LinearLayout parent,String value,int top){TextView t=text(value,11,MUTED);t.setLetterSpacing(.1f);t.setTypeface(Typeface.DEFAULT_BOLD);add(parent,t,top);}
    private Button button(String value,boolean primary){Button b=new Button(this);b.setText(value);b.setAllCaps(false);b.setTextSize(15);b.setTypeface(Typeface.create("sans-serif-medium",0));b.setTextColor(primary?BG:TEXT);b.setMinHeight(dp(50));b.setMinimumHeight(dp(50));b.setPadding(dp(14),dp(11),dp(14),dp(11));b.setBackground(shape(primary?ACCENT:PANEL,primary?0:LINE,10));return b;}
    private EditText input(String hint,boolean address){EditText e=new EditText(this);e.setTextColor(TEXT);e.setHintTextColor(MUTED);e.setHint(hint);e.setTextSize(16);e.setMinHeight(dp(54));e.setPadding(dp(12),dp(12),dp(12),dp(12));e.setInputType(InputType.TYPE_CLASS_TEXT|(address?InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS:InputType.TYPE_TEXT_FLAG_CAP_WORDS));e.setSingleLine(!address);e.setMaxLines(address?4:1);e.setBackground(shape(PANEL,LINE,8));return e;}
    private GradientDrawable shape(int fill,int border,int radius){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));if(border!=0)d.setStroke(dp(1),border);return d;}
    private void add(LinearLayout parent,View v,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);parent.addView(v,p);}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
