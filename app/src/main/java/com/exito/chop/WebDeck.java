package com.exito.chop;

import android.net.Uri;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Two banks of sixteen independent, disk-backed audiovisual sample pads. */
final class WebDeck {
 static final int BANKS=2, PADS_PER_BANK=16, COUNT=32;
 static final int MAX_SECONDS=45, MAX_VIDEO_MB=70;
 static final class Pad {
  String title="",url="",video="";
  String audio="";
  int rate=44100;
  float pitch=0,tempo=1;
  int bus=3;
  boolean loaded(){return !audio.isEmpty()&&new File(audio).isFile();}
 }
 static final class Clip {final short[] pcm;final int rate;Clip(short[] pcm,int rate){this.pcm=pcm;this.rate=rate;}}
 final Engine e;final Pad[] pads=new Pad[COUNT];
 private final LinkedHashMap<Integer,Clip> cache=new LinkedHashMap<Integer,Clip>(4,.75f,true){
  @Override protected boolean removeEldestEntry(Map.Entry<Integer,Clip> oldest){return size()>2;}
 };
 int bank,selected;
 WebDeck(Engine engine){e=engine;for(int i=0;i<COUNT;i++)pads[i]=new Pad();restoreLocal();}
 File directory(){File out=new File(e.context.getFilesDir(),"web-deck");out.mkdirs();return out;}
 static int valid(int i){if(i<0||i>=COUNT)throw new IllegalArgumentException("Pad web fuera de rango");return i;}
 synchronized Pad get(int index){return pads[valid(index)];}
 private File local(){return new File(directory(),"autosave.json");}
 /** The user's current unsaved web pad set survives process death. */
 synchronized void persist(){
  try{
   JSONObject j=metadata();JSONArray arr=j.getJSONArray("pads");
   for(int i=0;i<COUNT;i++)arr.getJSONObject(i).put("audio",pads[i].audio);
   File temp=new File(directory(),"autosave.partial");
   try(OutputStream out=new FileOutputStream(temp)){out.write(j.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
   java.nio.file.Files.move(temp.toPath(),local().toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
  }catch(Exception ex){e.message("No se han conservado los pads web: "+Engine.reason(ex));}
 }
 private synchronized void restoreLocal(){
  File file=local();if(!file.isFile())return;
  try{
   JSONObject saved=new JSONObject(new String(java.nio.file.Files.readAllBytes(file.toPath()),java.nio.charset.StandardCharsets.UTF_8));
   JSONArray a=saved.optJSONArray("pads");if(a==null||a.length()!=COUNT)return;
   for(int i=0;i<COUNT;i++){
    JSONObject x=a.optJSONObject(i);if(x==null)continue;Pad pad=new Pad();
    pad.title=x.optString("title","");pad.url=x.optString("url","");pad.video=x.optString("video","");
    pad.audio=x.optString("audio","");pad.rate=x.optInt("rate",44100);
    pad.pitch=(float)x.optDouble("pitch",0);pad.tempo=(float)x.optDouble("tempo",1);pads[i]=pad;
   }
   selected=Math.max(0,Math.min(31,saved.optInt("selected",0)));bank=selected/16;
  }catch(Exception ignored){}
 }
 synchronized JSONObject metadata()throws JSONException{
  JSONObject obj=new JSONObject().put("banks",BANKS).put("selected",selected);
  JSONArray list=new JSONArray();for(Pad pad:pads)list.put(new JSONObject().put("title",pad.title).put("url",pad.url)
   .put("video",pad.video).put("loaded",pad.loaded()).put("rate",pad.rate)
   .put("pitch",pad.pitch).put("tempo",pad.tempo));
  return obj.put("pads",list);
 }
 /** Copies a short chop, not the entire input: 32 lengthy videos must never live in RAM. */
 void fromSample(int index){
  valid(index);if(e.source==null){e.message("Primero carga o captura un sample.");return;}
  if(e.busy){e.message("Espera a que termine la tarea actual.");return;}
  e.slots.save();final Slots.Slot s=e.slots.list[e.slots.active];final int chop=e.selected;
  final int from,to;
  if(e.hasPad(chop)){from=e.start(chop);to=e.end(chop);}else{from=0;to=Math.min(s.source.length/2,s.rate*MAX_SECONDS);}
  final short[] source=s.source;final int sr=s.rate;
  final String name=s.name,link=s.youtube;final String video=s.video==null?"":s.video.toString();
  e.message("Guardando chop en Web Deck…");e.worker.execute(()->{try{
   writeClip(index,source,sr,from,to,name,link,video);
   e.message("WEB "+(index+1)+" · chop guardado con su fuente");
  }catch(Throwable error){e.message("WEB: "+Engine.reason(error));}});
 }
 void importFile(int index,Uri uri){
  valid(index);if(e.busy){e.message("Termina la operación del proyecto antes de cargar otro archivo");return;}e.message("Importando audio/vídeo al Web Deck…");e.worker.execute(()->{try{
   AudioFiles.Sample sample=AudioFiles.decode(e.context,uri);
   writeClip(index,sample.pcm,sample.rate,0,Math.min(sample.pcm.length/2,sample.rate*MAX_SECONDS),
    sample.name,"",sample.video==null?"":uri.toString());
   e.message("WEB "+(index+1)+" · fuente importada");
  }catch(Throwable error){e.message("WEB: "+Engine.reason(error));}});
 }
 private void writeClip(int index,short[] audio,int rate,int start,int end,String name,String link,String video)throws IOException{
  if(audio==null||start<0||end<=start||end>audio.length/2)throw new IOException("Selección vacía");
  if(rate<8000||rate>192000)throw new IOException("Frecuencia no válida");
  int frames=Math.min(end-start,MAX_SECONDS*rate);
  short[] pcm=Arrays.copyOfRange(audio,start*2,(start+frames)*2);
  File to=new File(directory(),"web-"+index+".wav"),tmp=new File(directory(),"web-"+index+".partial");
  try(OutputStream out=new BufferedOutputStream(new FileOutputStream(tmp))){AudioFiles.writeWav(out,pcm,rate);}
  if(!tmp.renameTo(to)){try(InputStream in=new FileInputStream(tmp);OutputStream out=new FileOutputStream(to)){ProjectStore.copy(in,out);}tmp.delete();}
  synchronized(this){Pad pad=new Pad();pad.audio=to.getAbsolutePath();pad.rate=rate;pad.title=name;pad.url=link;pad.video=video;pads[index]=pad;cache.remove(index);persist();}
  e.changed();
 }
 void updateLink(int index,String link){valid(index);synchronized(this){pads[index].url=link==null?"":link.trim();persist();}e.changed();}
 void parameters(int index,float pitch,float tempo){valid(index);synchronized(this){pads[index].pitch=Math.max(-24,Math.min(24,pitch));pads[index].tempo=Math.max(.5f,Math.min(2,tempo));persist();}e.changed();}
 void clearPad(int index){valid(index);File f; synchronized(this){f=new File(pads[index].audio);pads[index]=new Pad();cache.remove(index);persist();}if(f.isFile()&&f.getParentFile().equals(directory()))f.delete();e.changed();}
 synchronized void clear(){for(int i=0;i<COUNT;i++)pads[i]=new Pad();cache.clear();selected=0;bank=0;persist();e.changed();}
 void play(int index){
  valid(index);selected=index;bank=index/16;
  e.worker.execute(()->{try{
   final Clip clip=load(index);if(clip==null)throw new IOException("El pad no contiene audio");
   final Pad pad=get(index);if(!e.acquireFocus())return;
   PadParams settings=new PadParams();
   e.output.on(clip.pcm,clip.rate,0,clip.pcm.length/2,settings,10,false,pad.tempo,(float)Math.pow(2,pad.pitch/12.));
   e.changed();
  }catch(Throwable err){e.message("WEB: "+Engine.reason(err));}});
 }
 private synchronized Clip load(int index)throws IOException{
  Clip existing=cache.get(index);if(existing!=null)return existing;
  Pad pad=pads[index];if(!pad.loaded())return null;
  try(InputStream in=new BufferedInputStream(new FileInputStream(pad.audio))){
   byte[] header=new byte[44];readExact(in,header);if(header[0]!='R'||header[1]!='I'||header[8]!='W'||header[9]!='A')throw new IOException("WAV incompatible");
   int channels=u16(header,22),rate=u32(header,24),bits=u16(header,34),size=u32(header,40);
   if(channels!=2||bits!=16||rate<8000||rate>192000||size<4||size>(long)MAX_SECONDS*rate*4L)throw new IOException("WAV web fuera de límites");
   byte[] buffer=new byte[size];readExact(in,buffer);
   short[] pcm=new short[size/2];for(int i=0;i<pcm.length;i++)pcm[i]=(short)((buffer[i*2]&255)|(buffer[i*2+1]<<8));
   Clip clip=new Clip(pcm,rate);cache.put(index,clip);return clip;
  }
 }
 static int u16(byte[] b,int i){return (b[i]&255)|((b[i+1]&255)<<8);}
 static int u32(byte[] b,int i){return (b[i]&255)|((b[i+1]&255)<<8)|((b[i+2]&255)<<16)|((b[i+3]&255)<<24);}
 static void readExact(InputStream in,byte[] b)throws IOException{int p=0,n;while(p<b.length&&(n=in.read(b,p,b.length-p))>0)p+=n;if(p!=b.length)throw new EOFException("Audio incompleto");}
 /** Add independent WAV and video files to the portable project. */
 synchronized void appendZip(ZipOutputStream out)throws IOException{
  for(int i=0;i<COUNT;i++){
   Pad pad=pads[i];if(!pad.loaded())continue;
   try(InputStream in=new BufferedInputStream(new FileInputStream(pad.audio))){out.putNextEntry(new ZipEntry("web/audio/"+i+".wav"));ProjectStore.copyLimited(in,out,(long)MAX_SECONDS*192000*4+44);out.closeEntry();}
   if(pad.video.isEmpty())continue;
   Uri uri=Uri.parse(pad.video);String scheme=uri.getScheme();if(!"content".equals(scheme)&&!"file".equals(scheme))continue;
   try(InputStream in=e.context.getContentResolver().openInputStream(uri)){
    if(in==null)continue;
    out.putNextEntry(new ZipEntry("web/video/"+i+".mp4"));ProjectStore.copyLimited(in,out,MAX_VIDEO_MB*1048576L);out.closeEntry();
   }catch(FileNotFoundException ignored){}
  }
 }
 /** Prepare files without replacing the current deck until the whole archive validates. */
 static final class Prepared {final Pad[] pads=new Pad[COUNT];final File dir;final int selected;
  Prepared(File d,int s){dir=d;selected=s;}}
 Prepared extract(ZipFile zip,JSONObject state,String token)throws Exception{
  File stage=new File(directory(),"import-"+token);stage.mkdirs();
  JSONObject web=state.optJSONObject("webDeck");JSONArray items=web==null?null:web.optJSONArray("pads");
  Prepared result=new Prepared(stage,web==null?0:Math.max(0,Math.min(31,web.optInt("selected",0))));
  try{
   for(int i=0;i<COUNT;i++){
    Pad pad=new Pad();JSONObject info=items==null?null:items.optJSONObject(i);
    if(info!=null){pad.title=info.optString("title","");pad.url=info.optString("url","");pad.pitch=(float)info.optDouble("pitch",0);pad.tempo=(float)info.optDouble("tempo",1);}
    ZipEntry entry=zip.getEntry("web/audio/"+i+".wav");
    if(entry!=null){File file=new File(stage,"web-"+i+".wav");try(InputStream in=zip.getInputStream(entry);OutputStream out=new BufferedOutputStream(new FileOutputStream(file))){ProjectStore.copyLimited(in,out,(long)MAX_SECONDS*192000*4+44);}pad.audio=file.getAbsolutePath();pad.rate=info==null?44100:info.optInt("rate",44100);}
    ZipEntry movie=zip.getEntry("web/video/"+i+".mp4");
    if(movie!=null){File file=new File(stage,"video-"+i+".mp4");try(InputStream in=zip.getInputStream(movie);OutputStream out=new BufferedOutputStream(new FileOutputStream(file))){ProjectStore.copyLimited(in,out,MAX_VIDEO_MB*1048576L);}pad.video=Uri.fromFile(file).toString();}
    result.pads[i]=pad;
   }
   return result;
  }catch(Exception ex){delete(stage);throw ex;}
 }
 synchronized void install(Prepared state){System.arraycopy(state.pads,0,pads,0,COUNT);cache.clear();selected=state.selected;bank=selected/16;persist();e.changed();}
 static void delete(File dir){File[] files=dir.listFiles();if(files!=null)for(File f:files){if(f.isDirectory())delete(f);else f.delete();}dir.delete();}
}
