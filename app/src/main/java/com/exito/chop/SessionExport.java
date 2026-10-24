package com.exito.chop;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import java.nio.charset.StandardCharsets;
/** Offline WAV exports. No stem separation from commercial recordings: tracks follow
 * the user's own eight decks, drum lane and four routed buses. */
final class SessionExport {
 static final int SR=44100,BARS=4;
 static void saveText(ZipOutputStream z,String path,String text)throws IOException{z.putNextEntry(new ZipEntry(path));z.write(text.getBytes(StandardCharsets.UTF_8));z.closeEntry();}
 static void saveAudio(ZipOutputStream z,String path,float[] signal,int sr)throws IOException{
  short[] pcm=new short[signal.length];for(int i=0;i<pcm.length;i++)pcm[i]=Mutator.quantize(signal[i]);
  z.putNextEntry(new ZipEntry(path));AudioFiles.writeWav(z,pcm,sr);z.closeEntry();
 }
 static void start(Engine e,boolean allChops,boolean wet){
  if(e.busy||e.rendering||e.capturing){e.message("Termina la captura o ajuste antes de exportar.");return;}
  e.finishPadMarking(false);e.slots.save();e.harmony.stop();e.sequencer.stop();e.drums.stop();e.stop();e.busy=true;
  e.message(allChops?"Exportando TODOS los chops, usados o no…":"Renderizando el máster y stems de los 4 compases…");
  e.worker.execute(()->{
   try{
    File dir=new File(e.context.getCacheDir(),"exports");dir.mkdirs();
    File file=new File(dir,allChops?"ChopGO_Todos_Los_Bancos_Chops.zip":"ChopGO_Master_y_Stems.zip");
    try(ZipOutputStream z=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(file)))){
      if(allChops)allChops(e,z,wet);else mix(e,z);
    }
    e.busy=false;e.main.post(()->MainActivity.launch(e.context,"save-artifact",file.getName()));
   }catch(Throwable ex){e.busy=false;e.message("Exportación: "+Engine.reason(ex));}
  });
 }
 static void allChops(Engine e,ZipOutputStream z,boolean wet)throws Exception{
  StringBuilder index=new StringBuilder("deck,bank,pad,start_seconds,end_seconds,used_in_sequence,wav\n");int total=0;
  for(int deck=0;deck<8;deck++){
   Slots.Slot slot=e.slots.list[deck];if(slot.source==null||slot.rendered==null)continue;
   int count=PadChops.count(slot.mode,slot.cuts);if(count==0)continue;
   double factor=slot.rendered.length/(double)slot.source.length;
   for(int i=0;i<Math.min(64,count);i++){
    PadParams p=slot.pads[i];int start=p.start>=0?p.start:slot.cuts[i],end=p.end>=0?p.end:slot.cuts[i+1];
    int a=(int)(start*factor),b=(int)(end*factor);if(b<=a)continue;
    String path=String.format(Locale.US,"Deck_%d/Bank_%c/Chop_%02d.wav",deck+1,(char)('A'+i/16),i+1);
    short[] audio=AudioRender.chop(slot.rendered,slot.rate,a,b,p,e.compression,e.reverb,e.mutate.copy(),e.copyHeat(),wet);
    z.putNextEntry(new ZipEntry(path));AudioFiles.writeWav(z,audio,slot.rate);z.closeEntry();
    boolean used=false;for(int st=0;st<16;st++)if(e.sequencer.source[st]==deck&&e.sequencer.pad[st]==i)used=true;
    index.append(deck+1).append(',').append((char)('A'+i/16)).append(',').append(i+1).append(',').append(start/(double)slot.rate).append(',').append(end/(double)slot.rate).append(',').append(used).append(',').append(path).append('\n');total++;
   }
  }
  saveText(z,"CHOPS_INDEX.csv",index.toString());
  saveText(z,"LEEME.txt","ChopGO · "+total+" chops de los 8 decks / cuatro bancos por deck.\nIncluye TODOS los chops existentes, también los que no aparecen en el secuenciador.\nNo se inventan chops para decks vacíos; usa los cortes guardados.\n"+(wet?"Efectos individuales + cadena de exportación aplicados.\n":"Audio sin la cadena global de efectos.\n"));
 }
 static void add(float[] dest,short[] pcm,int pcmRate,int startFrame,int length){
  int n=pcm.length/2;for(int i=0;i<length&&startFrame+i<dest.length/2;i++){
    double pos=i*pcmRate/(double)SR;int j=(int)pos;double frac=pos-j;if(j>=n)break;
    for(int ch=0;ch<2;ch++){double s=pcm[j*2+ch]/32768.;if(j+1<n)s+=(pcm[(j+1)*2+ch]/32768.-s)*frac;dest[(startFrame+i)*2+ch]+=(float)s;}
  }
 }
 static void mix(Engine e,ZipOutputStream z)throws Exception{
  int bpm=Math.max(30,Math.min(300,e.slots.masterBpm));int frames=(int)Math.ceil(BARS*240.0/bpm*SR),length=frames*2;
  if((long)length*4*15>Runtime.getRuntime().maxMemory()*.33)throw new IOException("Proyecto demasiado largo para renderizado móvil de 4 compases: libera memoria.");
  float[][] tracks=new float[10][length];
  // Sequencer: current 16-step pattern repeated over four bars, without requiring playback.
  for(int bar=0;bar<BARS;bar++)for(int step=0;step<16;step++){
    int deck=e.sequencer.source[step],pad=e.sequencer.pad[step];if(deck<0||deck>=8)continue;
    Slots.Slot s=e.slots.list[deck];if(s.source==null||s.rendered==null||pad<0||pad>=PadChops.count(s.mode,s.cuts))continue;
    PadParams p=s.pads[pad];double scale=s.rendered.length/(double)s.source.length;
    int from=(int)((p.start<0?s.cuts[pad]:p.start)*scale),to=(int)((p.end<0?s.cuts[pad+1]:p.end)*scale);
    if(to<=from)continue;
    short[] clip=AudioRender.chop(s.rendered,s.rate,from,to,p,0,0,new Mutator.Settings(),new Heat.Settings(),false);
    int offset=(int)Math.round(((bar*16+step)*60.0/bpm/4)*SR);
    int old=offset;float[] individual=tracks[deck];
    // Per-step gain is baked into the stem, not only the live player.
    float gain=Math.max(1,Math.min(16,e.sequencer.level[step]))/16f;
    if(gain>=.999f)add(individual,clip,s.rate,old,frames-old);
    else {float[] isolated=new float[Math.min(clip.length*2,Math.max(0,(frames-old)*2))];
     add(isolated,clip,s.rate,0,isolated.length/2);
     for(int a=0;a<isolated.length&&old*2+a<individual.length;a++)individual[old*2+a]+=isolated[a]*gain;
    }
  }
  if(e.drums.rendered!=null&&e.drums.rendered.length>0){short[] loop=e.drums.rendered;double pos=0;int n=loop.length/2;
   for(int i=0;i<frames;i++){int k=(int)pos%n;double f=pos-(int)pos;int next=(k+1)%n;
    for(int ch=0;ch<2;ch++)tracks[9][2*i+ch]=(float)((loop[2*k+ch]*(1-f)+loop[2*next+ch]*f)/32768*e.drums.volume);
    pos+=e.drums.rate/(double)SR;if(pos>=n)pos%=n;
   }
  }
  for(int i=0;i<8;i++)saveAudio(z,String.format(Locale.US,"Stems_decks/Deck_%02d_PREBUS.wav",i+1),tracks[i],SR);
  saveAudio(z,"Stems_decks/Drums_PREBUS.wav",tracks[9],SR);
  // Instrument input is live-only in 0.3.x: write no fake recorded notes.
  float[][] groups=new float[4][length];for(int i=0;i<tracks.length;i++){int route=BusMixer.clamp(e.buses.routes[i]);for(int j=0;j<length;j++)groups[route][j]+=tracks[i][j];}
  BusMixer offline=new BusMixer(SR);offline.read(e.buses.json());
  for(int at=0;at<frames;at+=512){int n=Math.min(512,frames-at);for(int b=0;b<4;b++){
    float[] block=Arrays.copyOfRange(groups[b],at*2,(at+n)*2);
    offline.processSingle(block,n,b);System.arraycopy(block,0,groups[b],at*2,n*2);
  }}
  for(int b=0;b<4;b++)saveAudio(z,String.format(Locale.US,"Stems_buses/Bus_%d_POSTFX.wav",b+1),groups[b],SR);
  float[] master=new float[length];BusMixer.sum(master,groups,length);
  Dsp.Fx fx=new Dsp.Fx(SR);Mutator mutate=new Mutator(SR);Heat heat=new Heat(SR);MasterColour masterColour=new MasterColour(SR,e.masterColour.copy());
  Mutator.Settings settings=e.mutate.copy();settings.bpm=bpm;Heat.Settings warmth=e.copyHeat();
  for(int i=0;i<frames;i+=512){int n=Math.min(512,frames-i);float[] block=Arrays.copyOfRange(master,2*i,2*(i+n));fx.process(block,2*n,e.compression,e.reverb);mutate.process(block,2*n,settings);heat.process(block,2*n,warmth,bpm);masterColour.process(block,2*n);
   for(int j=0;j<block.length;j++){double x=block[j],a=Math.abs(x);if(a>.89)x=Math.copySign(.89+.11*Math.tanh((a-.89)/.11),x);master[2*i+j]=(float)(Double.isFinite(x)?x:0);}
  }
  saveAudio(z,"ChopGO_MASTER_44k1_24bit-no_16bit.wav",master,SR);
  saveText(z,"LEEME.txt","ChopGO · exportación de 4 compases a "+bpm+" BPM, WAV estéreo 44.1 kHz / 16 bits.\n"+
   "PREBUS: 8 decks (secuenciador de 16 pasos repetido) y loop de drums completo, incluso si reproducción estaba detenida.\n"+
   "POSTFX: 4 buses con EQ y drive individual. MASTER: suma de buses más cadena máster + color global de bits/frecuencia/filtro.\n"+
   "IMPORTANTE: No hay una grabación de notas de instrumentos en el secuenciador actual; no se fabrica un stem de instrumento.\n"+
   "Esta mezcla reproduce una plantilla fija de 4 compases, NO una captura arbitraria de una interpretación en directo.\n"+
   "Los stems prebus no incorporan los FX de bus; sumar todos los prebus no reproduce necesariamente el master.\n");
 }
}
