package com.exito.chop;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.schabi.newpipe.extractor.*;
import org.schabi.newpipe.extractor.downloader.*;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.stream.*;

/** Uses the unmodified NewPipe Extractor; no accounts, cookies or DRM processing. */
final class SourceResolver {
    private static boolean initialized=false;
    static final class Result {String url,audioUrl,title,watch;boolean video;}
    static synchronized Result resolve(String id)throws Exception{
        if(!initialized){NewPipe.init(new Http());initialized=true;}
        String watch="https://www.youtube.com/watch?v="+id;
        StreamExtractor extractor=ServiceList.YouTube.getStreamExtractor(watch);extractor.fetchPage();
        AudioStream audio=null;
        for(AudioStream stream:extractor.getAudioStreams()){
            if(stream.getDeliveryMethod()!=DeliveryMethod.PROGRESSIVE_HTTP)continue;
            boolean m4a=stream.getFormat()!=null&&"m4a".equals(stream.getFormat().getSuffix());
            boolean previousM4a=audio!=null&&audio.getFormat()!=null&&"m4a".equals(audio.getFormat().getSuffix());
            if(audio==null||(m4a&&!previousM4a)||(m4a==previousM4a&&stream.getAverageBitrate()>audio.getAverageBitrate()))audio=stream;
        }
        if(audio==null)throw new IOException("No se ha obtenido un flujo de audio compatible.");
        Result result=new Result();result.audioUrl=result.url=audio.getContent();result.title=extractor.getName();result.watch=watch;
        VideoStream video=null;int best=100000;
        try{for(VideoStream stream:extractor.getVideoStreams()){
            if(stream.getDeliveryMethod()!=DeliveryMethod.PROGRESSIVE_HTTP||stream.getFormat()==null||!"mp4".equals(stream.getFormat().getSuffix()))continue;
            java.util.regex.Matcher matcher=java.util.regex.Pattern.compile("\\d+").matcher(stream.getResolution());int height=matcher.find()?Integer.parseInt(matcher.group()):9999;
            if(height<best){video=stream;best=height;}
        }}catch(Exception ignored){}
        if(video!=null){result.url=video.getContent();result.video=true;}
        return result;
    }
    static final class Http extends Downloader {
        @Override public Response execute(Request request)throws IOException,ReCaptchaException{
            URL url=new URL(request.url());if(!"https".equals(url.getProtocol()))throw new IOException("Solo se permiten conexiones HTTPS.");
            HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setConnectTimeout(12000);c.setReadTimeout(18000);c.setRequestMethod(request.httpMethod());c.setRequestProperty("User-Agent",YoutubeImporter.UA);
            try{
                for(Map.Entry<String,List<String>> header:request.headers().entrySet())if(header.getKey()!=null){boolean first=true;for(String value:header.getValue()){if(first)c.setRequestProperty(header.getKey(),value);else c.addRequestProperty(header.getKey(),value);first=false;}}
                byte[] data=request.dataToSend();if(data!=null){c.setDoOutput(true);try(OutputStream out=c.getOutputStream()){out.write(data);}}
                int code=c.getResponseCode();if(code==403||code==429)throw new IOException("YouTube ha rechazado la solicitud (HTTP "+code+").");
                InputStream stream=code>=400?c.getErrorStream():c.getInputStream();String body="";
                if(stream!=null){if("gzip".equalsIgnoreCase(c.getContentEncoding()))stream=new GZIPInputStream(stream);try(InputStream in=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>12000000)throw new IOException("Respuesta demasiado grande.");}body=new String(out.toByteArray(),StandardCharsets.UTF_8);}}
                return new Response(code,c.getResponseMessage(),c.getHeaderFields(),body,c.getURL().toString());
            }finally{c.disconnect();}
        }
    }
}
