package com.yadli.luminara;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class ScreenReceiver extends BroadcastReceiver {
    
    // THANKS JASON
    public static boolean screenIsOn = true;
 
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
            // DO WHATEVER YOU NEED TO DO HERE
        	Log.d("visualizer", "screen turned off");
            screenIsOn = false;
            MainActivity.powerSaveIfOnLazyCommit();
        } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
            // AND DO WHATEVER YOU NEED TO DO HERE
            screenIsOn = true;
        	Log.d("visualizer", "screen turned on");
            MainActivity.resumeIfOnPowerSave();
        }
    }
 
}
