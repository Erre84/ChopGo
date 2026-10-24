package com.exito.chop;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.*;
import android.media.projection.MediaProjection;
import android.os.*;
import android.view.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** One video stream per consented projection; its audio clock is the live input. */
final class LiveVideoRecorder {
 final File file;final LiveBuffer input;final CountDownLatch done=new CountDownLatch(1);
 MediaCodec codec;MediaMuxer muxer;Surface surface;VirtualDisplay display;
 final long startedAt=SystemClock.elapsedRealtime();volatile boolean stopping;volatile Throwable error;volatile long zeroFrame;volatile int written;
 LiveVideoRecorder(Context c,MediaProjection projection,LiveBuffer buffer)throws Exception{
  input=buffer;File directory=new File(c.getFilesDir(),"pad-videos");directory.mkdirs();file=new File(directory,"take-"+System.currentTimeMillis()+".mp4");
  try{
   android.graphics.Rect bounds=((WindowManager)c.getSystemService(Context.WINDOW_SERVICE)).getMaximumWindowMetrics().getBounds();
   double scale=Math.min(1,720./Math.max(1,bounds.width()));int width=Math.max(2,((int)(bounds.width()*scale))&~1),height=Math.max(2,((int)(bounds.height()*scale))&~1);
   MediaFormat format=MediaFormat.createVideoFormat("video/avc",width,height);format.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);format.setInteger(MediaFormat.KEY_BIT_RATE,2000000);format.setInteger(MediaFormat.KEY_FRAME_RATE,30);format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);
   codec=MediaCodec.createEncoderByType("video/avc");codec.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);surface=codec.createInputSurface();muxer=new MediaMuxer(file.getAbsolutePath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);codec.start();
   zeroFrame=input.frames();display=projection.createVirtualDisplay("EXITO pad video",width,height,c.getResources().getConfiguration().densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,surface,null,new Handler(Looper.getMainLooper()));
   new Thread(this::drain,"EXITO-pad-video").start();
  }catch(Throwable failure){cleanup(false);file.delete();throw new IOException("Vídeo no disponible: "+Engine.reason(failure),failure);}
 }
 void drain(){boolean started=false;int track=-1;long first=-1,last=-1,deadline=0;MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
  try{
   while(true){if(SystemClock.elapsedRealtime()-startedAt>300000)requestStop();if(stopping&&deadline==0)deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);if(deadline>0&&System.nanoTime()>deadline)throw new IOException("El codificador de vídeo no ha terminado.");
    int index=codec.dequeueOutputBuffer(info,10000);
    if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){track=muxer.addTrack(codec.getOutputFormat());muxer.start();started=true;}
    else if(index>=0){
     try{if((info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)!=0)info.size=0;
      if(info.size>0&&started){if(first<0){first=info.presentationTimeUs;zeroFrame=input.frames()-Math.round((System.nanoTime()/1000.-first)*input.rate/1000000.);}
       ByteBuffer data=codec.getOutputBuffer(index);data.position(info.offset);data.limit(info.offset+info.size);info.presentationTimeUs=Math.max(last+1,info.presentationTimeUs-first);last=info.presentationTimeUs;muxer.writeSampleData(track,data,info);written++;}
      if((info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0)break;
     }finally{codec.releaseOutputBuffer(index,false);}
    }
   }
  }catch(Throwable failure){error=failure;}finally{cleanup(started);if(written==0||error!=null)file.delete();done.countDown();}
 }
 synchronized void requestStop(){if(stopping)return;stopping=true;if(display!=null){display.release();display=null;}try{if(codec!=null)codec.signalEndOfInputStream();}catch(Exception ignored){}}
 File await()throws Exception{requestStop();if(!done.await(6,TimeUnit.SECONDS))throw new IOException("Vídeo pendiente de cerrar; el audio está conservado.");if(error!=null)throw new IOException(Engine.reason(error),error);if(written==0||!file.isFile())throw new IOException("No se recibieron imágenes de la pantalla.");return file;}
 synchronized void cleanup(boolean started){if(display!=null){display.release();display=null;}if(codec!=null){try{codec.stop();}catch(Exception ignored){}codec.release();codec=null;}if(surface!=null){surface.release();surface=null;}if(muxer!=null){try{if(started)muxer.stop();}catch(Exception failure){error=failure;}try{muxer.release();}catch(Exception ignored){}muxer=null;}}
}
