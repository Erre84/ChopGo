package com.exito.chop;

import android.content.Context;
import android.net.Uri;
import java.io.*;
import java.net.*;

/** Resolve a shared/pasted video and load its media, rather than just opening YouTube. */
final class YoutubeImporter {
    static final String UA="Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Mobile Safari/537.36";
    static String id(String text)throws Exception{
        Uri uri=Uri.parse(text.trim());String host=uri.getHost();if(host==null)throw new IOException("Pega un enlace completo de YouTube.");host=host.toLowerCase(java.util.Locale.ROOT);String id=null;
        if(host.equals("youtu.be"))id=uri.getLastPathSegment();
        else if(host.equals("youtube.com")||host.endsWith(".youtube.com")){id=uri.getQueryParameter("v");if(id==null&&uri.getPathSegments().size()>=2&&(uri.getPathSegments().get(0).equals("shorts")||uri.getPathSegments().get(0).equals("embed")))id=uri.getPathSegments().get(1);}
        if(id==null||!id.matches("[a-zA-Z0-9_-]{11}"))throw new IOException("No se reconoce el enlace de vídeo.");return id;
    }
    static void load(Context context,String text){
        Engine e=Engine.get(context);if(e.busy||e.capturing){e.message("Desconecta la entrada actual y espera a que termine la carga.");return;}
        final String videoId;try{videoId=id(text);}catch(Exception error){e.message(Engine.reason(error));return;}
        e.sequencer.stop();e.drums.stop();e.stop();e.history.begin("Cargar enlace YouTube");e.busy=true;e.liveStatus="Resolviendo el flujo de YouTube…";e.message(e.liveStatus);final int slot=e.slots.active;
        e.worker.execute(()->{File temp=null;try{
            SourceResolver.Result result=SourceResolver.resolve(videoId);e.liveStatus="Flujo encontrado · cargando audio en memoria…";e.message(e.liveStatus);
            HttpURLConnection media;
            try{media=open(result.url);}catch(Exception error){if(!result.video)throw error;result.url=result.audioUrl;result.video=false;media=open(result.url);}
            if(result.video&&media.getContentLengthLong()>60L*1024*1024){media.disconnect();result.url=result.audioUrl;result.video=false;media=open(result.url);}
            temp=new File(context.getFilesDir(),"youtube-"+slot+"-"+System.currentTimeMillis()+(result.video?".mp4":".audio"));
            long begin=System.currentTimeMillis(),last=0;
            try(InputStream in=media.getInputStream();OutputStream out=new BufferedOutputStream(new FileOutputStream(temp))){
                byte[] block=new byte[32768];int n;long total=0;
                while((n=in.read(block))!=-1){total+=n;if(total>100L*1024*1024||System.currentTimeMillis()-begin>180000)throw new IOException("El vídeo es demasiado grande o tarda demasiado. Prueba un fragmento más corto.");out.write(block,0,n);
                    if(System.currentTimeMillis()-last>500){last=System.currentTimeMillis();e.liveStatus=String.format(java.util.Locale.US,"Cargando YouTube · %.1f MB",total/1048576.);e.message(e.liveStatus);}
                }
            }finally{media.disconnect();}
            e.liveStatus="Decodificando la muestra y preparando los cortes…";e.message(e.liveStatus);
            AudioFiles.Sample sample=AudioFiles.decode(context,Uri.fromFile(temp));e.accept(new AudioFiles.Sample(sample.pcm,sample.rate,result.title,sample.video));
            if(e.source!=sample.pcm){temp.delete();return;}
            if(!result.video)temp.delete();
            e.slots.list[slot].youtube=result.watch;e.inputWatch=result.watch;e.slots.writeMetadata();e.liveStatus="Audio de YouTube cargado · "+(e.cuts.length-1)+" chops · fuente "+(slot+1);e.message(e.liveStatus);
            e.main.post(()->{try{MainActivity.launch(context,"listen-loaded",null);}catch(Exception error){e.message("Muestra cargada. Puedes abrir el panel desde la app.");}});
        }catch(Throwable error){if(temp!=null)temp.delete();e.busy=false;e.history.cancel();e.liveStatus="No se ha cargado audio de YouTube: "+Engine.reason(error);e.message(e.liveStatus);e.main.post(()->MainActivity.launch(context,"youtube-live-fallback","https://www.youtube.com/watch?v="+videoId));}});
    }
    static HttpURLConnection open(String text)throws Exception{
        URL url=new URL(text);String host=url.getHost().toLowerCase(java.util.Locale.ROOT);
        if(!url.getProtocol().equals("https")||!(host.endsWith(".googlevideo.com")||host.endsWith(".youtube.com")))throw new IOException("Origen multimedia no compatible.");
        HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setConnectTimeout(12000);c.setReadTimeout(18000);c.setRequestProperty("User-Agent",UA);
        int code=c.getResponseCode();String type=c.getContentType();
        if(code<200||code>=300||(type!=null&&(type.contains("text/html")||type.contains("application/json")))){c.disconnect();throw new IOException("El servidor no entregó un archivo de audio/vídeo (HTTP "+code+").");}
        return c;
    }
}
