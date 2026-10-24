package com.exito.chop;
import android.net.Uri;import org.json.*;import java.io.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;
final class Slots {
 static final class Slot {short[] source,rendered;float[] waveform;int[] cuts;PadParams[] pads=PadParams.defaults();int rate=44100,bpm=90,key=0,selected=0;float pitch=0,tempo=1;String name="Vacía",mode=PadChops.EMPTY,youtube="";Uri video;boolean mono=true;long videoOffsetMs;}
 final Slot[] list=new Slot[8];final Engine e;int active,masterBpm=90,masterKey;boolean restoreAttempted;
 Slots(Engine engine){e=engine;for(int i=0;i<8;i++)list[i]=new Slot();}
 void save(){Slot s=list[active];s.source=e.source;s.rendered=e.rendered;s.waveform=e.waveform;s.cuts=e.cuts;s.pads=e.padParams;s.rate=e.rate;s.selected=e.selected;s.pitch=e.semitones;s.tempo=e.tempo;s.name=e.name;s.mode=e.chopMode;s.video=e.video;s.videoOffsetMs=e.videoOffsetMs;}
 void apply(){e.renderVersion.incrementAndGet();e.rendering=false;Slot s=list[active];e.source=s.source;e.rendered=s.rendered;e.waveform=s.waveform==null?new float[4096]:s.waveform;e.cuts=s.cuts==null?new int[]{0,1}:s.cuts;e.padParams=s.pads;e.rate=s.rate;e.selected=Math.max(0,Math.min(e.cuts.length-2,s.selected));e.semitones=s.pitch;e.tempo=s.tempo;e.name=s.source==null?"Fuente "+(active+1)+" · carga audio":s.name;e.chopMode=s.mode;e.video=s.video;e.videoOffsetMs=s.videoOffsetMs;e.inputWatch=s.youtube;e.padMarking=s.source!=null&&PadChops.EMPTY.equals(s.mode);e.padPreviewId=-1;e.padResumeFrame=0;}
 void toggleMono(){historyMono();}void historyMono(){e.history.edit("MONO / POLI de la fuente",()->{Slot s=list[active];s.mono=!s.mono;if(s.mono)e.output.mixer.choke(active);});}
 void activate(int i){if(i==active||i<0||i>7)return;if(e.busy||e.capturing){e.message("Espera o desconecta la entrada para cambiar de fuente");return;}e.finishPadMarking(false);save();active=i;apply();e.changed();}
 void clear(int i){if(e.busy||e.capturing)return;e.history.edit("Vaciar fuente",()->{if(i==active)e.stop();list[i]=new Slot();if(i==active)apply();});}
 long bytes(){save();long n=0;for(Slot s:list){if(s.source!=null)n+=s.source.length*2L;if(s.rendered!=null&&s.rendered!=s.source)n+=s.rendered.length*2L;}return n;}
 void sync(){
  if(e.source==null||e.busy||e.capturing)return;e.history.begin("Alinear ocho fuentes");save();final int bpm=masterBpm,key=masterKey,version=e.renderVersion.incrementAndGet();
  final short[][] input=new short[8][];final int[] rates=new int[8];final float[] speed=new float[8],pitch=new float[8];boolean modes=false;
  for(int i=0;i<8;i++){Slot s=list[i];if(s.source==null)continue;input[i]=s.source;rates[i]=s.rate;speed[i]=bpm/(float)Math.max(1,s.bpm);if(speed[i]<.5||speed[i]>2){e.rendering=false;e.history.cancel();e.message("Revisa BPM originales: rango 50–200 %");return;}pitch[i]=(key%12-s.key%12+18)%12-6;modes|=key/12!=s.key/12;}
  for(int i=0;i<8;i++)if(input[i]!=null){list[i].tempo=speed[i];list[i].pitch=pitch[i];e.output.mixer.parameters(i,speed[i],(float)Math.pow(2,pitch[i]/12.));}
  e.tempo=list[active].tempo;e.semitones=list[active].pitch;e.rendering=true;e.playVersion.incrementAndGet();e.message("Tono y tempo actualizados · preparando WAV…");final boolean differentModes=modes;
  e.worker.execute(()->{try{short[][] out=new short[8][];for(int i=0;i<8;i++)if(input[i]!=null)out[i]=Dsp.stretch(input[i],rates[i],speed[i],(float)Math.pow(2,pitch[i]/12.));e.main.post(()->{if(version!=e.renderVersion.get())return;for(int i=0;i<8;i++)if(input[i]!=null&&list[i].source==input[i])list[i].rendered=out[i];if(list[active].source==e.source)e.rendered=list[active].rendered;e.rendering=false;e.history.end();e.message(differentModes?"Tónica y tempo alineados. Mayor/menor no se convierte al transponer.":"Fuentes alineadas a "+bpm+" BPM");});}catch(Throwable error){e.main.post(()->{if(version==e.renderVersion.get()){e.rendering=false;e.history.cancel();e.message("Alinear: "+Engine.reason(error));}});}});
 }
 File directory(){File f=new File(e.context.getFilesDir(),"session");f.mkdirs();return f;}
 void writeMetadata()throws Exception{History.Snapshot s=e.history.snapshot();e.history.saveAssets(s);e.history.saveSession(s);}
 void persistCurrent(){try{writeMetadata();}catch(Exception error){e.message("No se pudo conservar la sesión: "+Engine.reason(error));}}
 void saveProject(){if(e.busy)return;e.freezeForEdit();e.busy=true;e.worker.execute(()->{try{writeMetadata();e.busy=false;e.message("Sesión conservada con cortes, colores, instrumentos y efectos");}catch(Throwable error){e.busy=false;e.message("Sesión: "+Engine.reason(error));}});}
 void restore(){if(restoreAttempted)return;restoreAttempted=true;File f=new File(directory(),"state-v3.json");if(!f.exists())f=new File(directory(),"session.json");if(!f.exists())return;final File input=f;e.busy=true;e.worker.execute(()->{try{JSONObject json=new JSONObject(new String(Files.readAllBytes(input.toPath()),StandardCharsets.UTF_8));Runnable apply=e.history.prepare(json);e.main.post(()->{apply.run();e.busy=false;e.message("Sesión restaurada");});}catch(Throwable error){e.busy=false;e.message("Restauración: "+Engine.reason(error));}});}
}
