package com.exito.chop;
import android.content.*;import android.media.*;import android.os.*;import java.io.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicInteger;
final class Engine {
 private static Engine instance;static synchronized Engine get(Context c){if(instance==null)instance=new Engine(c.getApplicationContext());return instance;}
 final Context context;final Handler main=new Handler(Looper.getMainLooper());final ExecutorService worker=Executors.newSingleThreadExecutor();final CopyOnWriteArrayList<Runnable> listeners=new CopyOnWriteArrayList<>();final AtomicInteger renderVersion=new AtomicInteger(),playVersion=new AtomicInteger();
 volatile short[] source,rendered;volatile int rate=44100,selected=0,midiBank=0,requestedChops=16,captureLimit=16,capturedFrames,captureSeconds;volatile int[] cuts={0,1};volatile float[] waveform=new float[4096];volatile float semitones=0,tempo=1,compression=0,reverb=0,thresholdDb=-24,inputDb=-96;volatile String chopMode=PadChops.EMPTY,name="Carga audio o prueba la demo",status="Audio local · IA opcional con proveedor externo",liveStatus="Entrada desconectada",inputPackage=MainActivity.YOUTUBE,inputLabel="YouTube",inputWatch="";
 volatile boolean scratchMode;volatile boolean busy,playing,capturing,liveMode,liveReady,inputPrompt,liveMetadataReady,liveMetadataOverride,inputWeb,inputAll,inputRemote,gate,playToEnd,linkProject=true;volatile LiveBuffer liveBuffer;volatile android.net.Uri video;volatile AudioTrack track;volatile int latencyMs;volatile boolean padMarking,rendering,manualTransform,finishingTake,listenAfterTake;final ExecutorService analyser=Executors.newSingleThreadExecutor();final java.util.concurrent.atomic.AtomicBoolean analysisQueued=new java.util.concurrent.atomic.AtomicBoolean();volatile long lastInputAnalysis;volatile String inputHome="https://m.youtube.com";volatile int padPreviewId=-1,padResumeFrame;volatile LivePadTake livePadTake;volatile boolean captureVideo=true;volatile LiveVideoRecorder liveVideoTake;volatile long videoOffsetMs,playVideoOffsetMs;volatile int videoSourceFrames;volatile int playOffset,videoFrom,videoTo,videoRate=44100,videoNote=-1;volatile float videoTempo=1;volatile android.net.Uri playVideoUri;
 final Drums drums;final WebDeck webDeck;final BusMixer buses;final MasterColour.Settings masterColour=new MasterColour.Settings();final ProjectStore projects;final Slots slots;final Sequencer sequencer;final History history;final Harmony harmony;final AudioOutput output;final Mutator.Settings mutate=new Mutator.Settings();final Heat.Settings heat=new Heat.Settings();volatile PadParams[] padParams=PadParams.defaults();final AudioManager manager;final AudioFocusRequest focus;volatile boolean focusHeld;
 Engine(Context c){context=c;manager=(AudioManager)c.getSystemService(Context.AUDIO_SERVICE);drums=new Drums(this);webDeck=new WebDeck(this);buses=new BusMixer(48000);slots=new Slots(this);sequencer=new Sequencer(this);harmony=new Harmony(this);history=new History(this);projects=new ProjectStore(this);focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes()).setOnAudioFocusChangeListener(change->{if(change<0)pauseForInterruption();},main).build();output=new AudioOutput(this,manager);}
 AudioAttributes attributes(){return new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_NONE).build();}
 void changed(){main.post(()->{for(Runnable r:listeners)r.run();});}void message(String s){status=s;changed();}boolean ready(){return livePadTake==null&&source!=null&&rendered!=null&&!busy&&(!liveMode||liveReady);}
 void demo(){if(busy||capturing)return;history.begin("Demo");busy=true;worker.execute(()->{try{accept(new AudioFiles.Sample(Dsp.demo(44100),44100,"Demo original · 90 BPM"));}catch(Throwable error){busy=false;history.cancel();message(reason(error));}});}
 void importAudio(android.net.Uri uri){if(busy||capturing){message("Espera o desconecta la entrada.");return;}history.begin("Importar audio");sequencer.stop();busy=true;message("Decodificando la fuente completa…");worker.execute(()->{try{accept(AudioFiles.decode(context,uri));}catch(Throwable error){busy=false;history.cancel();message("Importación: "+reason(error));}});}
 void accept(AudioFiles.Sample s){renderVersion.incrementAndGet();rendering=false;manualTransform=false;stop();if(slots.bytes()+s.pcm.length*4L>Runtime.getRuntime().maxMemory()*.65){busy=false;history.cancel();message("No cabe esta fuente en RAM. Vacía un espacio o usa un archivo más corto.");return;}inputWatch="";source=s.pcm;rate=s.rate;name=s.name;video=s.video;videoOffsetMs=0;semitones=0;tempo=1;rendered=source;chopMode=PadChops.EMPTY;padParams=PadParams.defaults();waveform=Dsp.peaks(source,4096);cuts=new int[]{0,source.length/2};selected=0;int[] estimate=Analysis.estimate(source,rate);Slots.Slot slot=slots.list[slots.active];slot.youtube="";slot.bpm=estimate[0];slot.key=estimate[1];if(linkProject){float speed=slots.masterBpm/(float)slot.bpm;if(speed>=.5&&speed<=2){tempo=speed;semitones=(slots.masterKey%12-slot.key%12+18)%12-6;rendered=Dsp.stretch(source,rate,tempo,(float)Math.pow(2,semitones/12.));}}slots.save();slots.persistCurrent();busy=false;padMarking=true;padPreviewId=-1;padResumeFrame=0;history.end();message("Pads vacíos. PLAY escucha sin crear cortes; toca 01 INICIAR / MARCAR y después 02, 03… a tu ritmo.");}
 void freezeForEdit(){if(livePadTake!=null){finishLivePads(false);return;}if(!capturing&&!liveMode)return;liveBuffer=null;capturing=false;liveMode=false;inputPrompt=false;context.stopService(new Intent(context,CaptureService.class));context.stopService(new Intent(context,NetworkInputService.class));slots.save();history.end();message("Entrada pausada; el fragmento recibido queda listo para editar.");if(source!=null&&!finishingTake)prepare();}
 void followLive(){if(!linkProject||!liveMetadataReady||manualTransform)return;Slots.Slot s=slots.list[slots.active];float speed=slots.masterBpm/(float)Math.max(1,s.bpm);if(speed<.5||speed>2){message("Revisa BPM de origen: fuera del rango de enlace");return;}float pitch=(slots.masterKey%12-s.key%12+18)%12-6;if(tempo!=speed||semitones!=pitch){tempo=speed;semitones=pitch;parametersChanged();message("Aplicando BPM y tónica del proyecto a la entrada");}}
 void liveMetadata(int bpm,int key){if(liveMetadataOverride)return;boolean first=!liveMetadataReady;slots.list[slots.active].bpm=bpm;slots.list[slots.active].key=key;liveMetadataReady=true;if(first)followLive();}
 int start(int i){return Math.max(0,Math.min(source.length/2-1,padParams[i].start<0?cuts[i]:padParams[i].start));}int end(int i){return Math.max(start(i)+1,Math.min(source.length/2,padParams[i].end<0?cuts[i+1]:padParams[i].end));}
 void chop(int count){if(busy)return;finishPadMarking(false);freezeForEdit();if(busy)return;history.begin("Repartir cortes");if(count>0)requestedChops=count;chopMode=count==0?"threshold":"regions";if(source==null){history.end();return;}cuts=Dsp.chops(source,rate,count,thresholdDb);padParams=PadParams.defaults();selected=0;history.end();changed();}
 void manualMode(){if(busy)return;finishPadMarking(false);history.edit("Cortar al vuelo",()->{chopMode="manual";if(liveBuffer!=null)liveBuffer.clearMarks();if(source!=null)cuts=new int[]{0,source.length/2};padParams=PadParams.defaults();selected=0;});message("Pulsa CORTAR mientras escuchas · máximo 64 chops");}
 void mark(){if(padMarking){markPad();return;}if(PadChops.isMode(chopMode)){message("Usa Nueva toma pads para marcar otra vez, o Al vuelo para CORTAR.");return;}if(liveMode&&capturing){if(liveBuffer!=null&&liveReady){chopMode="manual";message(liveBuffer.mark()?"Corte marcado":"Marca demasiado próxima o máximo de 64");}return;}if(!ready()||!playing)return;if(cuts.length>=65)return;double cursor=playhead();if(cursor<0)return;int frame=(int)(cursor*source.length/2);for(int cut:cuts)if(Math.abs(cut-frame)<rate*.045)return;history.edit("Añadir corte",()->{int[] next=Arrays.copyOf(cuts,cuts.length+1);next[next.length-1]=frame;Arrays.sort(next);cuts=next;});}
 void setPitch(float value){semitones=value;manualTransform=true;parametersChanged();}
 void setTempo(float value){tempo=value;manualTransform=true;parametersChanged();}
 void parametersChanged(){Slots.Slot s=slots.list[slots.active];s.pitch=semitones;s.tempo=tempo;renderVersion.incrementAndGet();if(!capturing)rendering=true;output.mixer.parameters(slots.active,tempo,(float)Math.pow(2,semitones/12.));playVersion.incrementAndGet();changed();}
 String metadataText(){
  if(capturing&&!liveMetadataReady)return "Analizando BPM / tonalidad estimada…";
  if(source==null&&!capturing)return "Esperando sample";
  Slots.Slot s=slots.list[slots.active];int key=Analysis.transposedKey(s.key,semitones);
  return "Original ≈ "+s.bpm+" BPM · "+Analysis.KEYS[s.key]+"\nSampler ≈ "+Math.round(s.bpm*tempo)+" BPM · "+Analysis.KEYS[key];
 }
 void analyseInput(LiveBuffer input){
  long now=System.nanoTime()/1000000L;
  if(input==null||input.frames()<input.rate*4L||now-lastInputAnalysis<2000||!analysisQueued.compareAndSet(false,true))return;
  lastInputAnalysis=now;final LiveBuffer.Window w=input.window(8);
  analyser.execute(()->{try{if(!Analysis.hasSignal(w.pcm))return;final int[] estimate=Analysis.estimate(w.pcm,input.rate);main.post(()->{if(capturing&&liveBuffer==input&&!liveMetadataOverride){liveMetadata(estimate[0],estimate[1]);changed();}});}finally{analysisQueued.set(false);}});
 }
 void prepare(){
  if(capturing||livePadTake!=null){history.end();message("Ajustes del sampler actualizados. El navegador sigue emitiendo el original; Escuchar cambios reproduce el fragmento procesado.");return;}
  if(source==null||busy)return;history.begin("Tono y tempo");final int version=renderVersion.incrementAndGet();final short[] input=source;final int sr=rate;final float speed=tempo,pitch=semitones;rendering=true;
  worker.execute(()->{try{final short[] audio=Dsp.stretch(input,sr,speed,(float)Math.pow(2,pitch/12.));main.post(()->{if(version!=renderVersion.get())return;rendering=false;if(source!=input)return;rendered=audio;history.end();message("Tono y tempo actualizados · WAV preparado");});}catch(Throwable error){main.post(()->{if(version==renderVersion.get()){rendering=false;history.cancel();message("Stretch: "+reason(error));}});}});
 }
 void listenChanges(){if(capturing&&!liveReady&&livePadTake==null){message("Espera a que llegue audio de la fuente.");return;}if(livePadTake!=null){listenAfterTake=true;finishLivePads(true);return;}if(capturing)freezeForEdit();if(ready())playAll();}

 int padCount(){if(livePadTake!=null)return livePadTake.count();return source==null?0:PadChops.count(chopMode,cuts);}
 boolean hasPad(int i){return i>=0&&i<padCount();}
 boolean canTapPad(int i){if(capturing&&liveBuffer!=null&&livePadTake==null&&PadChops.EMPTY.equals(chopMode))return !busy&&i==0;if(livePadTake!=null)return !busy&&(hasPad(i)||i==padCount()&&i<64);return ready()&&(hasPad(i)||((padMarking||PadChops.EMPTY.equals(chopMode))&&i==padCount()&&i<64));}
 String padActionLabel(){if(capturing&&liveBuffer!=null&&livePadTake==null)return "MARCAR";if(livePadTake!=null)return "MARCAR";return padCount()==0?"INICIAR":padPreviewId<0?"REANUDAR":"MARCAR";}
 void startLivePads(){
  if(busy)return;if(livePadTake!=null){message("Toma en directo abierta: toca MARCAR en el siguiente pad.");return;}
  final LiveBuffer input=liveBuffer;if(input==null||!capturing)return;
  harmony.stop();sequencer.stop();drums.stop();stop();history.end();history.begin("Pads al vuelo sobre la entrada en directo");
  long available=Runtime.getRuntime().maxMemory()-(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory());int capacity=(int)Math.min(input.rate*120L,Math.max(input.rate,available/16));
  livePadTake=input.armPads(capacity);padMarking=true;padPreviewId=-1;padParams=PadParams.defaults();selected=0;
  String image="solo audio";if(captureVideo&&!inputRemote)try{liveVideoTake=CaptureService.beginVideo(input);image="audio + vídeo de pantalla";}catch(Exception error){image="audio; "+reason(error);}
  message("DIRECTO · "+image+" · hasta "+capacity/input.rate+" s. YouTube sigue sonando. Toca MARCAR en 01, 02, 03… y Terminar pads al acabar.");
 }
 void markLivePad(){
  LivePadTake take=livePadTake;LiveBuffer input=liveBuffer;if(take==null||input==null)return;
  try{int n=input.markPad(take,latencyMs);selected=n-1;message("DIRECTO · pad "+n+" iniciado; "+(n<64?"toca "+(n+1)+" para cerrar este corte y abrir el siguiente.":"pulsa Terminar pads para cerrar el último."));}catch(Exception error){message(reason(error));}
 }
 void finishLivePads(boolean announce){
  final LivePadTake take=livePadTake;final LiveBuffer input=liveBuffer;if(take==null)return;
  final LivePadTake.Result result;try{result=input!=null?input.finishPads(take):take.finish();}catch(Exception error){message(reason(error));return;}
  final LiveVideoRecorder image=liveVideoTake;liveVideoTake=null;livePadTake=null;padMarking=false;padPreviewId=-1;
  if(result.count>0&&result.pcm.length>0){source=result.pcm;rendered=source;rate=take.rate;cuts=result.cuts;chopMode=PadChops.MODE;selected=Math.min(selected,result.count-1);waveform=Dsp.peaks(source,4096);video=null;videoOffsetMs=0;name=inputLabel+" · "+result.count+" chops en directo";liveReady=true;}
  finishingTake=true;freezeForEdit();finishingTake=false;
  if(image!=null)image.requestStop();
  if(result.count==0){if(image!=null)worker.execute(()->{try{image.await().delete();}catch(Exception ignored){}});message("No se ha marcado audio. La fuente anterior se conserva.");return;}
  final short[] captured=source;final int index=slots.active;final float speed=tempo,pitch=semitones;busy=true;message("Conservando "+result.count+" chops"+(image!=null?" y cerrando el vídeo…":"…"));
  worker.execute(()->{android.net.Uri uri=null;long offset=0;String issue="";short[] audio=captured;
   try{audio=Dsp.stretch(captured,take.rate,speed,(float)Math.pow(2,pitch/12.));}catch(Throwable error){issue=" · tono/tempo no aplicado: "+reason(error);}
   if(image!=null)try{uri=android.net.Uri.fromFile(image.await());offset=Math.round((result.startFrame-image.zeroFrame)*1000./take.rate);}catch(Exception error){issue+=" · sin imagen: "+reason(error);}
   final android.net.Uri savedVideo=uri;final long savedOffset=offset;final String note=issue;final short[] prepared=audio;
   main.post(()->{if(slots.active!=index||source!=captured){busy=false;return;}rendered=prepared;video=savedVideo;videoOffsetMs=savedOffset;busy=false;rendering=false;slots.save();history.end();worker.execute(()->slots.persistCurrent());message(result.count+" chops listos"+(savedVideo!=null?" con vídeo":"")+note+". Toca los pads; MONO corta el anterior y POLI permite sumarlos.");if(listenAfterTake){listenAfterTake=false;playAll();}});
  });
 }
 double padSeconds(int i){LivePadTake t=livePadTake;return t!=null?t.seconds(i):hasPad(i)?(end(i)-start(i))/(double)rate:0;}
 void startPadMarking(){
  if(capturing&&liveBuffer!=null){startLivePads();return;}
  if(!ready()){message("Carga audio o espera a recibir un fragmento antes de marcar pads.");return;}
  if(padMarking){message("Toca "+padActionLabel()+" en el siguiente pad, o Terminar pads.");return;}
  boolean wasLive=liveMode;freezeForEdit();harmony.stop();sequencer.stop();drums.stop();stop();
  history.edit("Nueva toma de pads al vuelo",()->{chopMode=PadChops.EMPTY;cuts=new int[]{0,source.length/2};padParams=PadParams.defaults();selected=0;});
  padMarking=true;padPreviewId=-1;padResumeFrame=0;
  message((wasLive?"Entrada pausada: se usa el fragmento recibido. ":"")+"Preparado. Toca INICIAR en el pad 01: empieza la escucha y se marca el primero. Luego toca MARCAR en 02, 03…");
 }
 void markPad(){
  if(capturing&&liveBuffer!=null&&livePadTake==null){startLivePads();markLivePad();return;}
  if(livePadTake!=null){markLivePad();return;}
  if(!padMarking||!ready())return;
  final int count=padCount();double cursor=output.mixer.position(padPreviewId);int frame;
  if(cursor<0){
   if(padPreviewId>=0)padResumeFrame=count>0?cuts[count-1]:0;
   padPreviewId=playRange(padResumeFrame,source.length/2,new PadParams());
   if(padPreviewId<0){message("No se pudo iniciar la escucha. Toca "+padActionLabel()+" cuando la salida esté disponible.");return;}
   if(count>0){message("Escucha reanudada; toca MARCAR en el pad "+(count+1)+" para crear el siguiente corte.");return;}
   frame=padResumeFrame;
  }else frame=Math.max(0,(int)(cursor*(source.length/2.))-Math.round(latencyMs*rate*tempo/1000f));
  final int[] next;
  try{next=PadChops.append(cuts,PadChops.EMPTY.equals(chopMode),frame,source.length/2,rate);}
  catch(IllegalArgumentException error){message(reason(error));return;}
  history.edit("Marcar pad "+(count+1),()->{cuts=next;chopMode=PadChops.MODE;selected=count;});
  if(padCount()==64){finishPadMarking(false);message("64 chops marcados. Ya puedes tocar y ajustar IN / OUT.");}
  else message("Pad "+(count+1)+" marcado. Toca MARCAR en el pad "+(count+2)+", o Terminar pads para escucharlos.");
 }
 void padPreviewEnded(int id){
  if(!padMarking||id<0||padPreviewId!=id||output.mixer.active(id))return;
  padResumeFrame=padCount()>0?cuts[padCount()-1]:0;padPreviewId=-1;
  message("Final de escucha. Los cortes se conservan: toca "+padActionLabel()+" para seguir desde el último corte, o Terminar pads para escucharlos.");
 }
 void pauseForInterruption(){
  if(livePadTake!=null){harmony.stop();output.stop();focusHeld=false;message("La toma en directo sigue abierta. Comprueba que YouTube siga reproduciéndose.");return;}
  boolean keepPads=padMarking;int resume=padResumeFrame;
  if(keepPads){double cursor=output.mixer.position(padPreviewId);if(cursor>=0)resume=(int)(cursor*(source.length/2.));else if(padPreviewId>=0)resume=padCount()>0?cuts[padCount()-1]:0;}
  harmony.stop();sequencer.stop();drums.stop();stop();focusHeld=false;
  if(keepPads){padMarking=true;padResumeFrame=Math.max(0,Math.min(source.length/2-Math.max(1,Math.round(rate*.045f)),resume));message("Escucha en pausa; los cortes se conservan. Toca "+padActionLabel()+" para continuar.");}
 }
 int tapPad(int i){
  if(!canTapPad(i))return -1;
  if(capturing&&liveBuffer!=null&&livePadTake==null&&PadChops.EMPTY.equals(chopMode)&&i==0){startLivePads();markLivePad();return -1;}
  if(PadChops.EMPTY.equals(chopMode))padMarking=true;
  if(padMarking){if(i==padCount())markPad();else message("Ese pad ya está marcado. Toca "+padActionLabel()+" en el pad "+(padCount()+1)+", o Terminar pads para escuchar los cortes.");return -1;}
  finishPadMarking(false);if(capturing)freezeForEdit();return playSlice(i);
 }
 void finishPadMarking(boolean announce){
  if(livePadTake!=null){finishLivePads(announce);return;}
  boolean wasMarking=padMarking;padMarking=false;
  if(padPreviewId>=0){output.off(padPreviewId);padPreviewId=-1;}
  if(announce)message(padCount()>0?padCount()+" chops listos: toca los pads y ajusta IN / OUT.":"No hay pads marcados. Pulsa Nueva toma pads para intentarlo.");
  else if(wasMarking)changed();
 }
 void select(int i){selected=Math.max(0,Math.min(cuts.length-2,i));changed();}void moveCut(int index,int frame){if(index>0&&index<cuts.length-1){int[] c=cuts.clone();c[index]=Math.max(c[index-1]+1,Math.min(c[index+1]-1,frame));cuts=c;changed();}}
 int playSlice(int i){if(!ready()||!hasPad(i))return -1;select(i);return playRange(start(i),playToEnd?source.length/2:end(i),padParams[i]);}void playAll(){
  if(livePadTake!=null){message("La entrada sigue sonando; toca MARCAR para crear el siguiente chop.");return;}
  if(capturing){message("Escucha la fuente y toca 01 MARCAR. Escuchar cambios pasa al fragmento procesado.");return;}
  if(!ready()){message("Carga un sample o pulsa Demo.");return;}
  if(PadChops.EMPTY.equals(chopMode))padMarking=true;
  if(padMarking){if(padPreviewId<0||!output.mixer.active(padPreviewId)){padPreviewId=playRange(padResumeFrame,source.length/2,new PadParams());message("Escuchando. Toca el siguiente pad MARCAR para crear un corte.");}return;}
  if(ready())playRange(0,source.length/2,new PadParams());
 }

 int playRange(int from,int to,PadParams p){if(!acquireFocus()){message("Salida de audio no disponible");return -1;}playOffset=from;int id=output.on(source,rate,from,to,p,slots.active,slots.list[slots.active].mono,tempo,(float)Math.pow(2,semitones/12.));videoSourceFrames=source.length/2;playVideoOffsetMs=videoOffsetMs;playVideoUri=video;videoFrom=from;videoTo=to;videoRate=rate;videoTempo=tempo;videoNote=id;playVersion.incrementAndGet();changed();return id;}
 void playSlot(int slot,int pad){Slots.Slot s=slots.list[slot];if(s.source==null||pad<0||pad>=PadChops.count(s.mode,s.cuts))return;PadParams p=s.pads[pad];int from=p.start<0?s.cuts[pad]:p.start,to=p.end<0?s.cuts[pad+1]:p.end;if(acquireFocus()){videoNote=output.on(s.source,s.rate,from,to,p,slot,s.mono,s.tempo,(float)Math.pow(2,s.pitch/12.));videoSourceFrames=s.source.length/2;playVideoOffsetMs=s.videoOffsetMs;playVideoUri=s.video;videoFrom=from;videoTo=to;videoRate=s.rate;videoTempo=s.tempo;playVersion.incrementAndGet();changed();}}
 int playLevel(int slot,int pad,int level){
  Slots.Slot s=slots.list[slot];if(s.source==null||pad<0||pad>=PadChops.count(s.mode,s.cuts))return -1;
  PadParams p=s.pads[pad];int from=p.start<0?s.cuts[pad]:p.start,to=p.end<0?s.cuts[pad+1]:p.end;
  if(to<=from||!acquireFocus())return -1;
  int id=output.on(s.source,s.rate,from,to,p,slot,s.mono,s.tempo,(float)Math.pow(2,s.pitch/12.));
  output.mixer.level(id,Math.max(1,Math.min(16,level))/16f);
  videoNote=id;videoSourceFrames=s.source.length/2;playVideoOffsetMs=s.videoOffsetMs;playVideoUri=s.video;
  videoFrom=from;videoTo=to;videoRate=s.rate;videoTempo=s.tempo;playVersion.incrementAndGet();changed();return id;
 }
 void stop(){if(livePadTake!=null)finishLivePads(false);padMarking=false;padPreviewId=-1;playVersion.incrementAndGet();if(output!=null)output.stop();playVideoUri=null;videoNote=-1;playing=false;changed();}boolean acquireFocus(){if(capturing)return true;if(focusHeld)return true;focusHeld=manager.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;return focusHeld;}void releaseFocus(){if(!playing&&!drums.playing&&focusHeld){manager.abandonAudioFocusRequest(focus);focusHeld=false;}}
 void setScratchMode(boolean enable){
  scratchMode=enable;if(!enable)endScratch();message(enable?"SCRATCH activado · desliza la onda como un vinilo":"SCRATCH desactivado · edición y zoom normales");
 }
 void beginScratch(int frame){
  if(!scratchMode||source==null||busy||capturing||livePadTake!=null)return;
  if(!acquireFocus())return;finishPadMarking(false);output.mixer.vinylStart(source,rate,0,source.length/2,frame,slots.active);output.wake();changed();
 }
 void moveScratch(int frame){if(scratchMode&&output.mixer.vinylActive()){output.mixer.vinylMove(frame);output.wake();changed();}}
 void endScratch(){output.mixer.vinylStop();output.wake();changed();}
 double playhead(){if(padMarking)return output.mixer.position(padPreviewId);return playing&&output.mixer.playheadPcm==source?output.mixer.playhead:-1;}
 void export(boolean all){if(!ready())return;if(rendering){message("Espera a que el último ajuste esté preparado para WAV.");return;}if(!all&&!hasPad(selected)){message("Marca primero un pad.");return;}finishPadMarking(false);freezeForEdit();if(rendering){message("Preparando el WAV con el último ajuste; exporta cuando termine.");return;}final short[] pcm=rendered;final int sr=rate;double scale=pcm.length/(double)source.length;final int from=all?0:(int)(start(selected)*scale),to=all?pcm.length/2:(int)(end(selected)*scale);final PadParams pad=all?new PadParams():padParams[selected].copy();final Mutator.Settings fx=mutate.copy();fx.bpm=slots.masterBpm;final Heat.Settings h=copyHeat();final float comp=compression,verb=reverb;busy=true;worker.execute(()->{try{File d=new File(context.getCacheDir(),"exports");d.mkdirs();File f=new File(d,all?"ChopGO_Fuente.wav":"ChopGO_Chop.wav");try(OutputStream out=new FileOutputStream(f)){AudioFiles.writeWav(out,AudioRender.chop(pcm,sr,from,to,pad,comp,verb,fx,h,true),sr);}busy=false;main.post(()->MainActivity.launch(context,"save-artifact",f.getName()));}catch(Throwable error){busy=false;message("WAV: "+reason(error));}});}
 Heat.Settings copyHeat(){Heat.Settings h=new Heat.Settings();try{SettingsIO.read(SettingsIO.write(heat),h);}catch(Exception ignored){}return h;}
 static String reason(Throwable error){return error.getMessage()==null?error.getClass().getSimpleName():error.getMessage();}
}
