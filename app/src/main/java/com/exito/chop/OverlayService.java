package com.exito.chop;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

/** Explicitly enabled panel; stays available in YouTube floating-window mode. */
public final class OverlayService extends Service {
    static volatile boolean active=false,appVisible=false;
    WindowManager wm;LinearLayout root;WindowManager.LayoutParams params;
    final Handler handler=new Handler(Looper.getMainLooper());boolean expanded=false;float touchX,touchY;int startX,startY,compactY;
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent!=null&&"stop".equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}if(root!=null)return START_NOT_STICKY;
        if(!Settings.canDrawOverlays(this)){Engine.get(this).inputPrompt=false;stopSelf();return START_NOT_STICKY;}
        if(Build.VERSION.SDK_INT>=34)startForeground(201,Notifs.make(this,"Panel en directo · arrastra o despliega · cierra con ×",OverlayService.class),ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE|ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(201,Notifs.make(this,"Panel en directo sobre YouTube",OverlayService.class));
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(SamplerView.BG);
        int width=getResources().getDisplayMetrics().widthPixels,height=getResources().getDisplayMetrics().heightPixels;
        params=new WindowManager.LayoutParams(Math.min(width-dp(16),dp(480)),Math.min(dp(374),height-dp(80)),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,android.graphics.PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.TOP|Gravity.START;params.x=dp(8);compactY=params.y=Math.max(dp(24),Math.min((int)(width*9./16)+dp(32),height-params.height-dp(24)));
        render();root.setVisibility(Engine.get(this).inputPrompt?View.GONE:View.VISIBLE);wm.addView(root,params);active=true;handler.post(watcher);return START_NOT_STICKY;
    }
    int dp(int n){return(int)(n*getResources().getDisplayMetrics().density+.5f);}
    void render(){
        root.removeAllViews();LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(6),0,dp(3),0);header.setBackgroundColor(SamplerView.LIME);
        TextView title=new TextView(this);title.setText(expanded?"ChopGO · arrastra":"EXITO · DIRECTO · arrastra");title.setTextSize(10);title.setTextColor(SamplerView.BG);title.setTypeface(Typography.control(this));title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);header.addView(title,new LinearLayout.LayoutParams(0,dp(38),1));
        Button expand=new Button(this);expand.setText(expanded?"Reducir":"Desplegar");expand.setAllCaps(false);expand.setTextSize(10);expand.setTypeface(Typography.control(this));expand.setPadding(0,0,0,0);expand.setOnClickListener(v->toggle());header.addView(expand,new LinearLayout.LayoutParams(dp(78),dp(38)));
        Button close=new Button(this);close.setText("×");close.setTypeface(Typography.control(this));close.setPadding(0,0,0,0);close.setOnClickListener(v->stopSelf());header.addView(close,new LinearLayout.LayoutParams(dp(36),dp(38)));root.addView(header);
        title.setOnTouchListener((view,event)->{
            if(event.getAction()==MotionEvent.ACTION_DOWN){touchX=event.getRawX();touchY=event.getRawY();startX=params.x;startY=params.y;return true;}
            if(event.getAction()==MotionEvent.ACTION_MOVE){int width=getResources().getDisplayMetrics().widthPixels,height=getResources().getDisplayMetrics().heightPixels;params.x=Math.max(0,Math.min(width-params.width,startX+(int)(event.getRawX()-touchX)));params.y=Math.max(dp(20),Math.min(height-params.height-dp(10),startY+(int)(event.getRawY()-touchY)));wm.updateViewLayout(root,params);return true;}
            if(event.getAction()==MotionEvent.ACTION_UP){if(!expanded)compactY=params.y;if(Math.abs(event.getRawX()-touchX)<dp(6)&&Math.abs(event.getRawY()-touchY)<dp(6))toggle();return true;}return false;
        });
        root.addView(expanded?new SamplerView(this,true):new MiniSamplerView(this),new LinearLayout.LayoutParams(-1,0,1));
    }
    void toggle(){
        if(!expanded)compactY=params.y;expanded=!expanded;int height=getResources().getDisplayMetrics().heightPixels;
        params.height=expanded?(int)(height*.85):Math.min(dp(374),height-dp(80));params.y=expanded?dp(28):Math.min(compactY,height-params.height-dp(24));render();wm.updateViewLayout(root,params);
    }
    final Runnable watcher=new Runnable(){public void run(){if(root==null)return;boolean locked=((KeyguardManager)getSystemService(KEYGUARD_SERVICE)).isKeyguardLocked();root.setVisibility(Engine.get(OverlayService.this).inputPrompt||locked||appVisible?View.GONE:View.VISIBLE);handler.postDelayed(this,200);}};
    @Override public void onDestroy(){
        active=false;handler.removeCallbacksAndMessages(null);if(root!=null){try{wm.removeView(root);}catch(Exception ignored){}root=null;}
        AutoPanelService.dismissed=true;Engine e=Engine.get(this);e.inputPrompt=false;e.harmony.stop();e.sequencer.stop();e.drums.stop();e.stop();if(e.capturing)e.freezeForEdit();stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();
    }
}
