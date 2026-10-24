package com.exito.chop;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.database.Cursor;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;

final class AudioFiles {
    static final int MAX_SECONDS = 3600;
    static final class Sample {
        final short[] pcm; final int rate; final String name; final Uri video;
        Sample(short[] pcm, int rate, String name) {this(pcm,rate,name,null);}
        Sample(short[] pcm, int rate, String name,Uri video) {this.pcm=pcm;this.rate=rate;this.name=name;this.video=video;}
    }
    static String name(Context c, Uri uri) {
        try (Cursor cur=c.getContentResolver().query(uri,null,null,null,null)) {
            if(cur!=null && cur.moveToFirst()) {int ix=cur.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(ix>=0)return cur.getString(ix);}
        } catch(Exception ignored) {}
        return "Audio importado";
    }
    static Sample decode(Context context, Uri uri) throws Exception {
        MediaExtractor extractor = new MediaExtractor(); MediaCodec codec = null;
        try {
            extractor.setDataSource(context,uri,null);
            MediaFormat format = null; boolean hasVideo=false;int audioTrack=-1;
            for (int i=0;i<extractor.getTrackCount();i++) {
                MediaFormat f=extractor.getTrackFormat(i);String mime=f.getString(MediaFormat.KEY_MIME);
                if(mime!=null&&mime.startsWith("video/"))hasVideo=true;
                if(mime!=null && mime.startsWith("audio/")&&audioTrack<0){format=f;audioTrack=i;}
            }
            if(format==null)throw new IOException("El archivo no contiene audio compatible.");
            extractor.selectTrack(audioTrack);
            int rate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE), channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT), encoding=2;
            if(rate<8000||rate>192000)throw new IOException("Frecuencia de muestreo no compatible.");
            codec=MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
            codec.configure(format,null,null,0);codec.start();
            boolean inDone=false,outDone=false,truncated=false;
            File raw=File.createTempFile("exito-decode-",".pcm",context.getCacheDir());long pcmBytes=0;Runtime runtime=Runtime.getRuntime();long available=runtime.maxMemory()-(runtime.totalMemory()-runtime.freeMemory());long maxBytes=Math.max(1024*1024,Math.min((long)(runtime.maxMemory()*.35),(long)(available*.55)));
            try(OutputStream pcm=new BufferedOutputStream(new FileOutputStream(raw))){MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            long deadline=System.currentTimeMillis()+300000;
            while(!outDone){
                if(System.currentTimeMillis()>deadline)throw new IOException("El archivo tarda demasiado en decodificarse.");
                if(!inDone){int ix=codec.dequeueInputBuffer(10000);if(ix>=0){ByteBuffer in=codec.getInputBuffer(ix);in.clear();int n=extractor.readSampleData(in,0);if(n<0){codec.queueInputBuffer(ix,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inDone=true;}else{codec.queueInputBuffer(ix,0,n,extractor.getSampleTime(),0);extractor.advance();}}}
                int ix=codec.dequeueOutputBuffer(info,10000);
                if(ix==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){MediaFormat f=codec.getOutputFormat();rate=f.getInteger(MediaFormat.KEY_SAMPLE_RATE);channels=f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);if(f.containsKey(MediaFormat.KEY_PCM_ENCODING))encoding=f.getInteger(MediaFormat.KEY_PCM_ENCODING);}
                else if(ix>=0){
                    ByteBuffer out=codec.getOutputBuffer(ix);out.order(ByteOrder.LITTLE_ENDIAN);out.position(info.offset);out.limit(info.offset+info.size);
                    int bytes=encoding==4?4:encoding==2?2:0;
                    if(bytes==0)throw new IOException("El decodificador devuelve un formato PCM no compatible.");
                    int frames=info.size/(bytes*channels), max=rate*MAX_SECONDS;
                    for(int f=0;f<frames;f++){
                        if(pcmBytes/4>=max||pcmBytes>=maxBytes)throw new IOException("La fuente completa no cabe en memoria; usa un archivo más corto. No se ha truncado.");
                        short l=0,r=0;
                        for(int ch=0;ch<channels;ch++){
                            short v=encoding==4?(short)(Math.max(-1,Math.min(.999969,out.getFloat()))*32768):out.getShort();
                            if(ch==0)l=v;if(ch==1)r=v;
                        }
                        if(channels==1)r=l;
                        pcm.write(l&255);pcm.write((l>>8)&255);pcm.write(r&255);pcm.write((r>>8)&255);pcmBytes+=4;
                    }
                    if((info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0)outDone=true;
                    codec.releaseOutputBuffer(ix,false);
                }
            }
            }catch(Throwable error){raw.delete();throw error;}
            try{if(pcmBytes<4)throw new IOException("El archivo no contiene audio");short[] shorts=new short[(int)(pcmBytes/2)];try(InputStream in=new BufferedInputStream(new FileInputStream(raw))){byte[] block=new byte[16384];int at=0,n;while((n=in.read(block))>0){if(n%2!=0)throw new IOException("PCM incompleto");for(int i=0;i<n;i+=2)shorts[at++]=(short)((block[i]&255)|(block[i+1]<<8));}}return new Sample(shorts,rate,name(context,uri),hasVideo?uri:null);}finally{raw.delete();}
        } finally {if(codec!=null){try{codec.stop();}catch(Exception ignored){}codec.release();}extractor.release();}
    }
    static void writeWav(OutputStream out,short[] pcm,int rate) throws IOException {
        writeWav(out,pcm,rate,2);
    }
    static void writeWav(OutputStream out,short[] pcm,int rate,int channels)throws IOException{
        writeHeader(out,pcm.length,rate,channels);byte[] b=new byte[8192];int pos=0;
        for(short v:pcm){b[pos++]=(byte)v;b[pos++]=(byte)(v>>8);if(pos==b.length){out.write(b);pos=0;}}
        if(pos>0)out.write(b,0,pos);
    }
    static void writeHeader(OutputStream out,int samples,int rate)throws IOException{
        writeHeader(out,samples,rate,2);
    }
    static void writeHeader(OutputStream out,int samples,int rate,int channels)throws IOException{
        int size=samples*2;ByteBuffer h=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        h.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36+size).put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short)1).putShort((short)channels).putInt(rate).putInt(rate*channels*2).putShort((short)(channels*2)).putShort((short)16).put("data".getBytes(StandardCharsets.US_ASCII)).putInt(size);out.write(h.array());
    }
}
