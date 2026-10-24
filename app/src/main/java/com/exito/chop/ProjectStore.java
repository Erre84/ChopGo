package com.exito.chop;

import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.zip.*;

/** Named, portable self-contained projects. ZIP contains state.json and source/drum WAVs. */
final class ProjectStore {
    static final String SUFFIX=".chopgo",LEGACY_SUFFIX=".exitochop";
    final Engine e;
    private volatile String currentName="";
    ProjectStore(Engine e){this.e=e;currentName=e.context.getSharedPreferences("exito-projects",0).getString("current","");}
    String current(){return currentName;}
    File directory(){File d=new File(e.context.getFilesDir(),"projects");d.mkdirs();return d;}
    static String clean(String text){String s=(text==null?"":text).replaceAll("[^\\p{L}\\p{N} _.-]", " ").trim();if(s.length()>64)s=s.substring(0,64).trim();while(s.startsWith("."))s=s.substring(1);if(s.isEmpty())s="Sin titulo";return s;}
    File fileFor(String title){return new File(directory(),clean(title)+SUFFIX);}
    String[] names(){File[] fs=directory().listFiles((f,n)->(n.endsWith(SUFFIX)||n.endsWith(LEGACY_SUFFIX))&&f.isFile());if(fs==null)return new String[0];Arrays.sort(fs,(a,b)->Long.compare(b.lastModified(),a.lastModified()));String[] out=new String[fs.length];for(int i=0;i<fs.length;i++)out[i]=fs[i].getName().substring(0,fs[i].getName().length()-(fs[i].getName().endsWith(LEGACY_SUFFIX)?LEGACY_SUFFIX.length():SUFFIX.length()));return out;}
    void setCurrent(String s){currentName=s;e.context.getSharedPreferences("exito-projects",0).edit().putString("current",s).apply();e.changed();}
    boolean begin(){if(e.busy||e.rendering){e.message("Espera a terminar el proceso actual antes de cambiar de proyecto.");return false;}e.freezeForEdit();if(e.busy||e.livePadTake!=null){e.message("Termina la captura antes de guardar el proyecto.");return false;}e.history.end();e.harmony.stop();e.sequencer.stop();e.drums.stop();e.stop();e.busy=true;return true;}
    void saveNamed(String title){if(!begin())return;final String name=clean(title);e.message("Guardando «"+name+"» con su audio original…");e.worker.execute(()->{try{History.Snapshot snapshot=e.history.snapshot();File target=fileFor(name);write(snapshot,target,name);e.history.saveAssets(snapshot);e.history.saveSession(snapshot);e.main.post(()->{setCurrent(name);e.busy=false;e.message("Proyecto guardado: "+name);});}catch(Throwable ex){e.main.post(()->{e.busy=false;e.message("No se pudo guardar el proyecto: "+Engine.reason(ex));});}});}
    void exportTo(Uri uri){if(!begin())return;final String name=currentName.isEmpty()?"Sin titulo":currentName;e.message("Preparando archivo del proyecto…");e.worker.execute(()->{File temp=new File(e.context.getCacheDir(),"exito-export.tmp");try{History.Snapshot snapshot=e.history.snapshot();write(snapshot,temp,name);try(InputStream in=new BufferedInputStream(new FileInputStream(temp));OutputStream out=e.context.getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException("Destino de exportación inaccesible");copy(in,out);}e.history.saveAssets(snapshot);e.history.saveSession(snapshot);e.main.post(()->{e.busy=false;e.message("Proyecto exportado con WAV y ajustes");});}catch(Throwable ex){e.main.post(()->{e.busy=false;e.message("Exportación: "+Engine.reason(ex));});}finally{temp.delete();}});}
    /** Portable archive keeps the original PCM and, when available, the recorded/local video. */
    private void write(History.Snapshot snapshot,File destination,String name)throws Exception{
        File temp=new File(destination.getAbsolutePath()+".tmp");temp.getParentFile().mkdirs();
        try(ZipOutputStream out=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(temp)))){
            out.setLevel(Deflater.BEST_SPEED);
            JSONObject state=new JSONObject(snapshot.json);state.put("projectName",name);state.put("webDeck",e.webDeck.metadata());
            // Validate video sources before writing metadata: no dangling references in a portable project.
            JSONArray slots=state.getJSONArray("slots");
            for(int i=0;i<slots.length();i++){
                JSONObject slot=slots.optJSONObject(i);if(slot==null||!slot.optBoolean("loaded"))continue;
                String source=slot.optString("video","");if(source.isEmpty())continue;
                Uri uri=Uri.parse(source);
                if(!"file".equals(uri.getScheme())&&!"content".equals(uri.getScheme())){
                    slot.put("video","");continue;
                }
                try(InputStream probe=e.context.getContentResolver().openInputStream(uri)){
                    if(probe==null)slot.put("video","");
                }catch(Exception missing){slot.put("video","");}
            }
            out.putNextEntry(new ZipEntry("state.json"));out.write(state.toString().getBytes(StandardCharsets.UTF_8));out.closeEntry();
            Set<String> added=new HashSet<>();
            for(History.Asset a:snapshot.audio){if(a.pcm==null||!added.add(a.id))continue;
                out.putNextEntry(new ZipEntry("audio/"+a.id+".wav"));AudioFiles.writeWav(out,a.pcm,a.rate);out.closeEntry();}
            for(int i=0;i<slots.length();i++){
                JSONObject slot=slots.optJSONObject(i);if(slot==null||!slot.optBoolean("loaded"))continue;
                String source=slot.optString("video","");if(source.isEmpty())continue;
                try(InputStream in=e.context.getContentResolver().openInputStream(Uri.parse(source))){
                    if(in==null)throw new IOException("No se puede abrir el vídeo de la fuente "+(i+1));
                    out.putNextEntry(new ZipEntry("video/"+i+".mp4"));copyLimited(in,out,350L*1024*1024);out.closeEntry();
                }
            }
            e.webDeck.appendZip(out);
        }catch(Throwable ex){temp.delete();throw ex;}
        Files.move(temp.toPath(),destination.toPath(),StandardCopyOption.REPLACE_EXISTING);
    }
    void loadNamed(String name){File file=fileFor(name);if(!file.exists())file=new File(directory(),clean(name)+LEGACY_SUFFIX);if(!file.exists()){e.message("El proyecto ya no está disponible");return;}load(file,null);}
    void importFrom(Uri uri){if(!begin())return;e.message("Importando el proyecto completo…");e.worker.execute(()->{File temp=new File(e.context.getCacheDir(),"exito-import-"+System.nanoTime()+SUFFIX);try(InputStream in=e.context.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(temp)){if(in==null)throw new IOException("No se puede abrir el archivo");copy(in,out);loadWorker(temp,true);}catch(Throwable ex){e.main.post(()->{e.busy=false;e.message("Importación: "+Engine.reason(ex));});}finally{temp.delete();}});}
    private void load(File file,Void unused){if(!begin())return;e.message("Abriendo proyecto…");e.worker.execute(()->{try{loadWorker(file,false);}catch(Throwable ex){e.main.post(()->{e.busy=false;e.message("No se pudo abrir el proyecto: "+Engine.reason(ex));});}});}
    private void loadWorker(File file,boolean imported)throws Exception{
        try(ZipFile zip=new ZipFile(file)){
            ZipEntry entry=zip.getEntry("state.json");if(entry==null||entry.getSize()>2_000_000L)throw new IOException("No es un proyecto ChopGO válido");byte[] bytes;try(InputStream in=zip.getInputStream(entry);ByteArrayOutputStream buf=new ByteArrayOutputStream()){byte[] block=new byte[8192];int n;while((n=in.read(block))>=0){buf.write(block,0,n);if(buf.size()>2_000_000)throw new IOException("Metadatos demasiado grandes");}bytes=buf.toByteArray();}
            JSONObject state=new JSONObject(new String(bytes,StandardCharsets.UTF_8));if(!state.has("slots")||state.optInt("version",-1)!=3)throw new IOException("Formato de proyecto incompatible");
            LinkedHashSet<String> required=new LinkedHashSet<>();JSONArray slots=state.getJSONArray("slots");if(slots.length()!=8)throw new IOException("Número de fuentes incorrecto");for(int i=0;i<slots.length();i++){JSONObject s=slots.optJSONObject(i);if(s!=null&&s.optBoolean("loaded"))require(required,s.optString("audioId",""));}JSONObject drums=state.optJSONObject("drums");if(drums!=null)require(required,drums.optString("audioId",""));
            for(String id:required){ZipEntry audio=zip.getEntry("audio/"+id+".wav");if(audio==null)throw new IOException("Falta audio de una fuente: "+id);if(audio.getSize()>550_000_000L)throw new IOException("Audio demasiado grande");File to=new File(e.history.assets,id+".wav");if(!to.exists()){File temp=new File(e.history.assets,id+".partial");try(InputStream in=zip.getInputStream(audio);OutputStream out=new BufferedOutputStream(new FileOutputStream(temp))){copyLimited(in,out,550_000_000L);}catch(Throwable ex){temp.delete();throw ex;}Files.move(temp.toPath(),to.toPath(),StandardCopyOption.REPLACE_EXISTING);}}
            // Old project files remain readable; new ones restore bundled pictures too.
            File videoDir=new File(e.context.getFilesDir(),"project-videos");videoDir.mkdirs();
            String token=Integer.toHexString((file.getAbsolutePath()+":"+file.length()+":"+file.lastModified()).hashCode());
            for(int i=0;i<slots.length();i++){
                ZipEntry picture=zip.getEntry("video/"+i+".mp4");if(picture==null)continue;
                if(picture.getSize()>350L*1024*1024)throw new IOException("Vídeo demasiado grande en fuente "+(i+1));
                File saved=new File(videoDir,"exito-"+token+"-"+i+".mp4");
                if(!saved.exists()){
                    File partial=new File(videoDir,saved.getName()+".partial");
                    try(InputStream in=zip.getInputStream(picture);OutputStream out=new BufferedOutputStream(new FileOutputStream(partial))){copyLimited(in,out,350L*1024*1024);}
                    catch(Throwable ex){partial.delete();throw ex;}
                    Files.move(partial.toPath(),saved.toPath(),StandardCopyOption.REPLACE_EXISTING);
                }
                JSONObject slot=slots.optJSONObject(i);if(slot!=null)slot.put("video",Uri.fromFile(saved).toString());
            }
            WebDeck.Prepared webState=e.webDeck.extract(zip,state,token);
            Runnable apply=e.history.prepare(state);String name=clean(state.optString("projectName",file.getName().replace(SUFFIX,"").replace(LEGACY_SUFFIX,"")));
            if(imported){File dest=fileFor(name);if(dest.exists())dest=fileFor(name+" importado "+System.currentTimeMillis());Files.copy(file.toPath(),dest.toPath(),StandardCopyOption.REPLACE_EXISTING);}
            e.main.post(()->{apply.run();e.webDeck.install(webState);e.history.clearEdits();setCurrent(name);e.busy=false;e.worker.execute(()->e.slots.persistCurrent());e.message("Proyecto abierto: "+name);});
        }
    }
    static void require(Set<String> ids,String id)throws IOException{if(id==null||id.isEmpty())return;if(!id.matches("[0-9a-fA-F-]{36}"))throw new IOException("Identificador de audio inválido");ids.add(id);}
    void createNew(){if(!begin())return;e.message("Preparando proyecto nuevo…");e.worker.execute(()->{try{JSONObject empty=new JSONObject().put("version",3).put("manualPadsDefault",true).put("bpm",90).put("key",0).put("active",0).put("linkProject",true).put("fx",SettingsIO.write(new Mutator.Settings())).put("heat",SettingsIO.write(new Heat.Settings())).put("harmony",SettingsIO.write(new Harmony.Settings()));JSONArray list=new JSONArray();for(int i=0;i<8;i++)list.put(new JSONObject().put("loaded",false));empty.put("slots",list);int[] unset=new int[16];Arrays.fill(unset,-1);empty.put("stepSources",new JSONArray(unset));empty.put("stepPads",new JSONArray(unset));empty.put("drums",new JSONObject().put("name","Boom bap · 90 BPM").put("bpm",90).put("original",90).put("volume",.55));Runnable apply=e.history.prepare(empty);e.main.post(()->{apply.run();e.webDeck.clear();e.history.clearEdits();setCurrent("");e.busy=false;e.worker.execute(()->e.slots.persistCurrent());e.message("Proyecto nuevo · guarda con un nombre cuando quieras");});}catch(Throwable ex){e.main.post(()->{e.busy=false;e.message("Nuevo proyecto: "+Engine.reason(ex));});}});}
    static void copy(InputStream in,OutputStream out)throws IOException{byte[] buffer=new byte[32768];int n;while((n=in.read(buffer))>=0)out.write(buffer,0,n);}
    static void copyLimited(InputStream in,OutputStream out,long max)throws IOException{byte[] buffer=new byte[32768];int n;long total=0;while((n=in.read(buffer))>=0){total+=n;if(total>max)throw new IOException("El archivo supera el límite permitido");out.write(buffer,0,n);}}
}
