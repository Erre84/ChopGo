package com.exito.chop;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Single background video decoder: pads show stills; only the large viewer plays video. */
final class ChopThumbnailCache {
 interface Result {void done(Bitmap[] frames);}
 private final Context context;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final ExecutorService queue=Executors.newSingleThreadExecutor();
 private final AtomicInteger epoch=new AtomicInteger();
 private final LruCache<String,Bitmap> images=new LruCache<String,Bitmap>(4*1024*1024){
  @Override protected int sizeOf(String k, Bitmap bitmap){return bitmap.getByteCount();}
 };
 private String signature="";
 ChopThumbnailCache(Context context){this.context=context.getApplicationContext();}
 void clear(){epoch.incrementAndGet();signature="";}
 void shutdown(){clear();queue.shutdownNow();}
 void request(Uri uri,long offset,int rate,int bank,int[] starts,Result callback){
  if(uri==null||starts==null){clear();callback.done(new Bitmap[16]);return;}
  String fingerprint=uri.toString()+":"+offset+":"+rate+":"+bank+":"+Arrays.toString(starts);
  if(signature.equals(fingerprint))return;
  signature=fingerprint;
  final int token=epoch.incrementAndGet();
  queue.execute(()->{
   Bitmap[] frames=new Bitmap[16];MediaMetadataRetriever reader=new MediaMetadataRetriever();
   try{
    reader.setDataSource(context,uri);
    for(int n=0;n<16;n++){
     if(Thread.currentThread().isInterrupted()||token!=epoch.get())return;
     if(starts[n]<0)continue;
     long milliseconds=Math.max(0,offset+Math.round(starts[n]*1000.0/Math.max(1,rate)));
     String key=uri+":"+milliseconds;
     Bitmap thumb=images.get(key);
     if(thumb==null){
      Bitmap raw=reader.getFrameAtTime(milliseconds*1000,MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
      if(raw!=null){thumb=Bitmap.createScaledBitmap(raw,152,90,true);if(raw!=thumb)raw.recycle();images.put(key,thumb);}
     }
     frames[n]=thumb;
    }
   }catch(Exception ignored){/* Audio-only, missing or inaccessible videos show no frames. */}
   finally{try{reader.release();}catch(Exception ignored){}}
   if(token==epoch.get())main.post(()->{if(token==epoch.get())callback.done(frames);});
  });
 }
}
