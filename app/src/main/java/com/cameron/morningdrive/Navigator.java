package com.cameron.morningdrive;

import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.widget.Toast;

public final class Navigator {
    static Intent intent(String address) {
        return new Intent(Intent.ACTION_VIEW,Uri.parse("google.navigation:q="+Uri.encode(address.trim())+"&mode=d"))
            .setPackage("com.google.android.apps.maps");
    }
    public static void test(Activity a,String address) {
        if(address.trim().isEmpty()) { Toast.makeText(a,"Enter a destination first.",Toast.LENGTH_LONG).show(); return; }
        try { a.startActivity(intent(address)); }
        catch(ActivityNotFoundException e) { Toast.makeText(a,"Install or enable Google Maps first.",Toast.LENGTH_LONG).show(); }
        catch(SecurityException e) { Toast.makeText(a,"Google Maps could not be opened.",Toast.LENGTH_LONG).show(); }
    }
}
