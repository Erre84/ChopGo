package com.exito.chop;

import android.content.*;
import android.graphics.*;
import android.media.*;
import android.net.Uri;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;

/** Muted, crop-capable image follows the most recently triggered pad. */
final class ChopVideoView extends FrameLayout implements TextureView.SurfaceTextureListener {
 final Engine e;final TextureView texture;final TextView message;final VideoFrame frame=new VideoFrame();
 final Runnable listener=this::refresh;final ScaleGestureDetector pinch;
 MediaPlayer player;Surface surface;Uri loaded;VideoSync sync;
 boolean ready,failed,attached,editing,moved,preferFill,autoFillPending;int videoWidth,videoHeight;long lastPosition;float downX,downY,lastX,lastY;
 ChopVideoView(Context c){
  super(c);e=Engine.get(c);setBackgroundColor(0xff102a2a);setClipChildren(true);
  texture=new TextureView(c);texture.setSurfaceTextureListener(this);addView(texture,new LayoutParams(-1,-1));
  message=new TextView(c);message.setTypeface(Typography.body(c));message.setTextSize(11);message.setTextColor(0xffd3e0d0);message.setGravity(Gravity.CENTER);int pad=(int)(12*getResources().getDisplayMetrics().density);message.setPadding(pad,pad,pad,pad);message.setBackgroundColor(0xff102a2a);addView(message,new LayoutParams(-1,-1));
  setOnClickListener(v->{if(failed&&loaded!=null){failed=false;open();}else if(!editing)MainActivity.showVideo(c,true);});
  pinch=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){public boolean onScale(ScaleGestureDetector d){frame.scale(d.getScaleFactor(),d.getFocusX(),d.getFocusY());transform();moved=true;return true;}});
 }
 void editing(boolean enabled){editing=enabled;}
 void preferFill(boolean enabled){preferFill=enabled;}
 void frameMode(int mode){if(!ready)return;if(mode==0)frame.reset();else frame.fill(mode==2);transform();saveFrame();}
 void saveFrame(){if(loaded!=null)getContext().getSharedPreferences("video-frame",0).edit().putFloat(loaded+":zoom",frame.zoom).putFloat(loaded+":x",frame.cx).putFloat(loaded+":y",frame.cy).apply();}
 void loadFrame(){frame.width=frame.height=frame.sourceWidth=frame.sourceHeight=0;SharedPreferences p=getContext().getSharedPreferences("video-frame",0);autoFillPending=preferFill&&!p.contains(loaded+":zoom");frame.restore(p.getFloat(loaded+":zoom",1),p.getFloat(loaded+":x",.5f),p.getFloat(loaded+":y",.5f));}
 void refresh(){
  if(e.capturing||e.livePadTake!=null){close();loaded=null;failed=false;show(e.liveVideoTake!=null?"Conservando imagen con los pads\nTermina la toma para verla.":"Entrada en directo\nVídeo ON conserva la imagen al marcar los pads.");return;}
  Uri uri=e.playing?e.playVideoUri:e.video;
  if(uri==null){close();loaded=null;failed=false;show(e.source==null?"VÍDEO + PADS\nCarga un vídeo o termina una toma con Vídeo ON.":"Esta fuente contiene solo audio\nCarga un vídeo desde Archivo o captura con Vídeo ON.");return;}
  if(!uri.equals(loaded)){close();loaded=uri;failed=false;loadFrame();}
  if(player==null&&!failed&&texture.isAvailable()&&attached)open();if(ready)update();
 }
 void open(){
  if(loaded==null||!texture.isAvailable()||!attached)return;close();show("Preparando imagen…");final MediaPlayer p=new MediaPlayer();player=p;
  try{
   p.setVolume(0,0);surface=new Surface(texture.getSurfaceTexture());p.setSurface(surface);p.setDataSource(getContext(),loaded);
   p.setOnVideoSizeChangedListener((m,w,h)->{if(player!=m)return;videoWidth=w;videoHeight=h;transform();});
   p.setOnPreparedListener(m->{if(player!=m)return;ready=true;m.setVolume(0,0);videoWidth=m.getVideoWidth();videoHeight=m.getVideoHeight();transform();
    sync=new VideoSync(new VideoSync.Picture(){public long positionMs(){return m.getCurrentPosition();}public void seek(long ms){m.seekTo(ms,MediaPlayer.SEEK_CLOSEST);}public void pause(){if(m.isPlaying())m.pause();}public void play(float speed){m.setPlaybackParams(new PlaybackParams().setSpeed(speed).setPitch(1));if(!m.isPlaying())m.start();}});update();});
   p.setOnSeekCompleteListener(m->{if(player!=m||sync==null)return;try{update();sync.seekComplete(SystemClock.uptimeMillis());message.setVisibility(GONE);schedule();}catch(Exception error){fail(error);}});
   p.setOnErrorListener((m,what,extra)->{if(player==m)fail(new java.io.IOException("Formato no compatible o archivo inaccesible ("+what+")."));return true;});
   p.prepareAsync();
  }catch(Exception error){fail(error);}
 }
 void update(){if(!ready||sync==null||player==null)return;try{
  boolean active=e.playVideoUri!=null&&e.playVideoUri.equals(loaded)&&e.output.mixer.active(e.videoNote);long token,desired;float speed=1;
  if(active){double position=e.output.mixer.position(e.videoNote);if(position<0)active=false;desired=Math.round(e.playVideoOffsetMs+(position>=0?position*e.videoSourceFrames:e.videoFrom)*1000./e.videoRate);token=e.videoNote;speed=e.output.mixer.speed(e.videoNote);lastPosition=desired;}
  else if(e.playing&&e.playVideoUri!=null&&e.playVideoUri.equals(loaded)){token=e.videoNote;desired=lastPosition;}
  else{token=-(long)e.selected-1;desired=e.videoOffsetMs+(e.source!=null&&e.hasPad(e.selected)?Math.round(e.start(e.selected)*1000./e.rate):0);}
  long duration=player.getDuration();if(duration>0&&desired>=duration){desired=duration-1;active=false;}
  sync.update(token,desired,active,speed,SystemClock.uptimeMillis());schedule();
 }catch(Exception error){fail(error);}}
 void schedule(){removeCallbacks(tick);if(ready&&sync!=null&&(sync.pending()||e.output.mixer.active(e.videoNote)))postDelayed(tick,40);}
 final Runnable tick=this::update;
 void transform(){if(videoWidth<=0||videoHeight<=0||texture.getWidth()<=0||texture.getHeight()<=0)return;frame.size(texture.getWidth(),texture.getHeight(),videoWidth,videoHeight);if(autoFillPending){frame.fill(false);autoFillPending=false;}Matrix m=new Matrix();m.setScale(frame.drawWidth/frame.width,frame.drawHeight/frame.height);m.postTranslate(frame.tx,frame.ty);texture.setTransform(m);}
 void show(String text){message.setText(text);message.setVisibility(VISIBLE);}
 void fail(Exception error){failed=true;close();show("No se puede mostrar este vídeo\n"+Engine.reason(error)+"\nToca para reintentar.");}
 void close(){removeCallbacks(tick);ready=false;sync=null;videoWidth=videoHeight=0;MediaPlayer old=player;player=null;if(old!=null)try{old.release();}catch(Exception ignored){}if(surface!=null){surface.release();surface=null;}}
 @Override protected void onMeasure(int w,int h){if(MeasureSpec.getMode(h)!=MeasureSpec.EXACTLY)h=MeasureSpec.makeMeasureSpec(resolveSize(Math.round(MeasureSpec.getSize(w)*9/16f),h),MeasureSpec.EXACTLY);super.onMeasure(w,h);}
 @Override public boolean onTouchEvent(MotionEvent event){
  if(!editing||!ready)return super.onTouchEvent(event);pinch.onTouchEvent(event);
  switch(event.getActionMasked()){
   case MotionEvent.ACTION_DOWN:downX=lastX=event.getX();downY=lastY=event.getY();moved=false;getParent().requestDisallowInterceptTouchEvent(true);return true;
   case MotionEvent.ACTION_POINTER_UP:if(event.getPointerCount()>1){int next=event.getActionIndex()==0?1:0;lastX=event.getX(next);lastY=event.getY(next);}return true;
   case MotionEvent.ACTION_MOVE:if(event.getPointerCount()==1&&!pinch.isInProgress()){float x=event.getX(),y=event.getY();if(Math.abs(x-downX)+Math.abs(y-downY)>8)moved=true;if(moved){frame.pan(x-lastX,y-lastY);transform();}lastX=x;lastY=y;}return true;
   case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:saveFrame();getParent().requestDisallowInterceptTouchEvent(false);if(!moved&&event.getActionMasked()==MotionEvent.ACTION_UP)performClick();return true;
  }return true;
 }
 @Override public boolean performClick(){super.performClick();return true;}
 @Override protected void onAttachedToWindow(){super.onAttachedToWindow();attached=true;e.listeners.add(listener);refresh();}
 @Override protected void onDetachedFromWindow(){attached=false;e.listeners.remove(listener);close();loaded=null;super.onDetachedFromWindow();}
 @Override public void onSurfaceTextureAvailable(SurfaceTexture t,int w,int h){refresh();}
 @Override public void onSurfaceTextureSizeChanged(SurfaceTexture t,int w,int h){transform();}
 @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture t){close();return true;}
 @Override public void onSurfaceTextureUpdated(SurfaceTexture t){}
}
