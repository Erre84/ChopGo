package com.exito.chop;

import android.media.*;
import java.io.*;
import java.nio.*;
import java.util.*;
/** One decoder + output, optional bounded PCM recorder. No MediaProjection. */
final class RadioStream {
 interface Events {void status(String message);}
 private volatile boolean playing=false,recording=false;
 private volatile Thread thread;
 private volatile String station="";
 private final Events callback;
 private final Object lock=new Object();
 private short[] recorded;private int used,rate;
 private static final int LIMIT_SECONDS=45;
 RadioStream(Events callback){this.callback=callback;}
 boolean playing(){return playing;}
 boolean recording(){return recording;}
 String station(){return station;}
 void play(String name,String stream){
  stop();if(!RadioDirectory.safe(stream)){callback.status("URL de radio no válida");return;}
  station=name;playing=true;thread=new Thread(()->decode(stream),"ChopGO-RadioStream");thread.start();
 }
 void stop(){playing=false;recording=false;Thread t=thread;if(t!=null)t.interrupt();}
 void arm(){synchronized(lock){if(!playing||rate<8000){callback.status("Primero sintoniza una emisora compatible");return;}recorded=new short[rate*2*LIMIT_SECONDS];used=0;recording=true;callback.status("RADIO · grabando (máx. 45 s)");}}
 static final class Take {final short[] pcm;final int rate;final String name;Take(short[] data,int rate,String n){this.pcm=data;this.rate=rate;this.name=n;}}
 Take take(){synchronized(lock){if(recorded==null)return null;recording=false;
  if(used<rate/5)return null;short[] data=Arrays.copyOf(recorded,used);recorded=null;used=0;return new Take(data,rate,station);
 }}
 private void append(short[] audio,int n){synchronized(lock){if(!recording||recorded==null)return;int copy=Math.min(n,recorded.length-used);
  if(copy>0){System.arraycopy(audio,0,recorded,used,copy);used+=copy;}
  if(used==recorded.length){recording=false;callback.status("RADIO · límite 45 s alcanzado. Pulsa Guardar toma antes de cambiar de emisora.");}
 }}
 private void decode(String url){MediaExtractor x=new MediaExtractor();MediaCodec codec=null;AudioTrack track=null;
  try{
   x.setDataSource(url);MediaFormat format=null;int channel=-1;
   for(int i=0;i<x.getTrackCount();i++){MediaFormat f=x.getTrackFormat(i);String mime=f.getString(MediaFormat.KEY_MIME);
    if(mime!=null&&mime.startsWith("audio/")){format=f;channel=i;break;}}
   if(format==null)throw new IOException("La radio no ofrece audio compatible para grabación directa");
   x.selectTrack(channel);int sr=format.getInteger(MediaFormat.KEY_SAMPLE_RATE),channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
   if(sr<8000||sr>96000||channels<1||channels>2)throw new IOException("Formato de radio incompatible");
   rate=sr;codec=MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));codec.configure(format,null,null,0);codec.start();
   int bufferSize=Math.max(sr/5*4,AudioTrack.getMinBufferSize(sr,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_16BIT));
   if(bufferSize<=0)throw new IOException("No se puede abrir la salida de audio");
   track=new AudioTrack(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
    new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build(),
    bufferSize,AudioTrack.MODE_STREAM,AudioManager.AUDIO_SESSION_ID_GENERATE);
   if(track.getState()!=AudioTrack.STATE_INITIALIZED)throw new IOException("AudioTrack no disponible");track.play();
   callback.status("RADIO · escuchando "+station+" · REC para samplear");
   MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();boolean eos=false;int encoding=AudioFormat.ENCODING_PCM_16BIT;
   while(playing&&!Thread.currentThread().isInterrupted()&&!eos){
    int input=codec.dequeueInputBuffer(50000);
    if(input>=0){ByteBuffer b=codec.getInputBuffer(input);b.clear();int n=x.readSampleData(b,0);
     if(n<0){codec.queueInputBuffer(input,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);eos=true;}
     else {codec.queueInputBuffer(input,0,n,x.getSampleTime(),0);x.advance();}}
    int output=codec.dequeueOutputBuffer(info,50000);
    if(output==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){MediaFormat out=codec.getOutputFormat();int r=out.getInteger(MediaFormat.KEY_SAMPLE_RATE);
     int ch=out.getInteger(MediaFormat.KEY_CHANNEL_COUNT);if(r!=sr||ch<1||ch>2)throw new IOException("La emisora cambió el formato de audio");channels=ch;
     if(out.containsKey(MediaFormat.KEY_PCM_ENCODING))encoding=out.getInteger(MediaFormat.KEY_PCM_ENCODING);
    }else if(output>=0){ByteBuffer out=codec.getOutputBuffer(output);if(out!=null&&info.size>0){out.order(ByteOrder.LITTLE_ENDIAN);out.position(info.offset);out.limit(info.offset+info.size);
      int bytes=encoding==AudioFormat.ENCODING_PCM_FLOAT?4:encoding==AudioFormat.ENCODING_PCM_16BIT?2:0;
      if(bytes==0)throw new IOException("PCM no compatible");
      int frames=info.size/(bytes*channels);short[] pcm=new short[frames*2];
      for(int i=0;i<frames;i++){short left=0,right=0;for(int ch=0;ch<channels;ch++){
       short s=bytes==4?(short)(Math.max(-1,Math.min(.999969,out.getFloat()))*32767):out.getShort();
       if(ch==0)left=s;else right=s;
      }if(channels==1)right=left;pcm[2*i]=left;pcm[2*i+1]=right;}
      if(playing){int at=0;while(at<pcm.length&&playing){int n=track.write(pcm,at,pcm.length-at);if(n<0)throw new IOException("Error salida de audio");if(n==0)break;at+=n;}append(pcm,at);}
     }if((info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0)eos=true;codec.releaseOutputBuffer(output,false);}
   }
  }catch(Throwable ex){if(playing)callback.status("RADIO · "+ex.getMessage()+". Prueba otra emisora MP3/AAC.");}
  finally{playing=false;recording=false;if(track!=null){try{track.pause();track.flush();track.release();}catch(Exception ignored){}}
   if(codec!=null){try{codec.stop();}catch(Exception ignored){}codec.release();}x.release();}
 }
}
