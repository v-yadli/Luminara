package com.yadli.luminara;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class ScreenReceiver extends BroadcastReceiver {
    
    // THANKS JASON
    private static boolean screenIsOn = true;
    private static boolean lastSyncState = true;
    
    public enum ScreenState{
    	off_to_on,
    	on_to_off,
    	other
    }
    
    public static ScreenState checkScreenState()
    {
    	if(screenIsOn && !lastSyncState){
    		lastSyncState = true;
    		return ScreenState.off_to_on;
    	}
    	if(!screenIsOn && lastSyncState){
    		lastSyncState = false;
    		return ScreenState.on_to_off;
    	}
    	return ScreenState.other;
    }
 
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
            // DO WHATEVER YOU NEED TO DO HERE
        	Log.d("visualizer", "screen turned off");
            screenIsOn = false;
        } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
            // AND DO WHATEVER YOU NEED TO DO HERE
            screenIsOn = true;
        	Log.d("visualizer", "screen turned on");
        }
    }
 
}
