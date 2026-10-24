package com.exito.chop;
import android.accessibilityservice.AccessibilityService;import android.view.accessibility.AccessibilityEvent;import android.content.Intent;import android.provider.Settings;
public final class AutoPanelService extends AccessibilityService {
 static volatile boolean dismissed;boolean inYoutube;long last;
 @Override protected void onServiceConnected(){Engine.get(this).slots.restore();}
 @Override public void onAccessibilityEvent(AccessibilityEvent event){if(event.getEventType()!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED||event.getPackageName()==null)return;String p=event.getPackageName().toString();if(p.equals(getPackageName())||p.equals("android")||p.startsWith("com.android.systemui"))return;if(!p.equals(MainActivity.YOUTUBE)){inYoutube=false;dismissed=false;return;}if(inYoutube||dismissed)return;inYoutube=true;if(!getSharedPreferences("exito",0).getBoolean("autoPanel",false)||!Settings.canDrawOverlays(this)||OverlayService.active)return;long now=android.os.SystemClock.elapsedRealtime();if(now-last<2000)return;last=now;Engine e=Engine.get(this);e.inputPrompt=false;if(!e.capturing){e.inputAll=e.inputRemote=e.inputWeb=false;e.inputPackage=MainActivity.YOUTUBE;e.inputLabel="YouTube";}try{startForegroundService(new Intent(this,OverlayService.class));}catch(Exception error){e.message("Panel automático: "+Engine.reason(error));}}
 @Override public void onInterrupt(){}
}
