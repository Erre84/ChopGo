package com.exito.chop;
import android.os.*;import java.util.*;
final class Harmony {
 static final String[] CHORDS={"Nota","Tercera","Quinta","Tríada","Séptima","Sus2","Sus4"},PROGRESSIONS={"I – V – vi – IV","ii – V – I – I","i – VI – III – VII","I – IV – V – I"},ARPS={"Apagado","Ascendente","Descendente","Arriba / abajo","Aleatorio","Octavas"};
 static final int[][] DEGREES={{0,4,5,3},{1,4,0,0},{0,5,2,6},{0,3,4,0}};
 static final class Settings {volatile int instrument=0,scale=0,octave=3,chord=0,progression=0,preset=0,arpMode=0,arpDivision=2,arpOctaves=1,arpPreset=0,arpSwing=12,sineDegree=0,sineOctave=2,sineAttackMs=6,sineReleaseMs=120,sineLengthMs=1500;volatile float volume=.65f,sineVolume=.4f;}
 final Settings settings=new Settings();final Engine e;final Handler handler=new Handler(Looper.getMainLooper());boolean playing,arpPlaying,sinePlaying;int sineId=-1,sineNote=-1;int step,arpStep;long next,arpNext;int[] held=new int[0],arpHeld=new int[0];final java.util.Random random=new java.util.Random();
 Harmony(Engine engine){e=engine;}
 int[] on(int degree){if(!e.acquireFocus())return new int[0];int[] notes=Scale.chord(e.slots.masterKey,settings.scale,settings.octave,degree,settings.chord),ids=new int[notes.length];for(int i=0;i<ids.length;i++)ids[i]=e.output.synth.on(5+settings.preset,notes[i]);e.playing=true;e.output.wake();e.changed();return ids;}
 void off(int[] ids){for(int id:ids)e.output.synth.off(id);e.output.wake();}void stop(){stopSine();playing=false;arpPlaying=false;handler.removeCallbacks(tick);handler.removeCallbacks(arpTick);off(held);off(arpHeld);held=new int[0];arpHeld=new int[0];e.changed();}
 void toggle(){if(playing){stop();return;}playing=true;step=0;next=SystemClock.uptimeMillis();handler.post(tick);}
 final Runnable tick=new Runnable(){public void run(){if(!playing)return;off(held);int chord=settings.chord;settings.chord=Math.max(3,chord);held=on(DEGREES[settings.progression][step%4]);settings.chord=chord;step++;next+=(long)(240000./e.slots.masterBpm);handler.postAtTime(this,next);e.changed();}};
 int sineMidi(){return Scale.note(e.slots.masterKey,settings.scale,Math.max(0,Math.min(6,settings.sineOctave)),Math.max(0,Math.min(6,settings.sineDegree)));}
 String sineLabel(){int note=sineMidi();String[] keys={"C","C#","D","D#","E","F","F#","G","G#","A","A#","B"};return keys[note%12]+(note/12-1)+" · "+Math.round(440*Math.pow(2,(note-69)/12.)) +" Hz";}
 void stopSine(){if(!sinePlaying)return;sinePlaying=false;e.output.synth.off(sineId);sineId=-1;sineNote=-1;e.output.wake();e.changed();}
 void toggleSine(){if(sinePlaying){stopSine();return;}if(!e.acquireFocus())return;
   int note=sineMidi();sineId=e.output.synth.onSine(note,settings.sineAttackMs/1000f,settings.sineReleaseMs/1000f);sineNote=note;sinePlaying=true;e.playing=true;e.output.wake();e.changed();}
 void retuneSine(){if(sinePlaying&&sineNote!=sineMidi()){sineNote=sineMidi();e.output.synth.retune(sineId,sineNote);}}
 void exportSine(){if(e.busy){e.message("Termina la operación actual antes de exportar la senoide.");return;}
   final int note=sineMidi(),length=Math.max(100,Math.min(8000,settings.sineLengthMs)),attack=settings.sineAttackMs,release=settings.sineReleaseMs;
   final float level=settings.sineVolume;e.worker.execute(()->{try{java.io.File dir=new java.io.File(e.context.getCacheDir(),"exports");dir.mkdirs();String[] names={"C","Cs","D","Ds","E","F","Fs","G","Gs","A","As","B"};
    java.io.File file=new java.io.File(dir,"ChopGO_Seno_"+names[note%12]+(note/12-1)+"_"+length+"ms.wav");
    short[] pcm=SineTone.renderStereo(note,44100,length,attack,release,level);
    try(java.io.OutputStream out=new java.io.FileOutputStream(file)){AudioFiles.writeWav(out,pcm,44100);}
    e.main.post(()->MainActivity.launch(e.context,"save-artifact",file.getName()));
   }catch(Exception ex){e.message("Seno a WAV: "+Engine.reason(ex));}});
 }
 void toggleArp(){if(arpPlaying){arpPlaying=false;handler.removeCallbacks(arpTick);off(arpHeld);arpHeld=new int[0];e.changed();return;}
   if(settings.arpMode==0)settings.arpMode=1;arpStep=0;arpNext=SystemClock.uptimeMillis();arpPlaying=true;handler.post(arpTick);}
 final Runnable arpTick=new Runnable(){public void run(){if(!arpPlaying)return;
  off(arpHeld);arpHeld=new int[0];
  int mode=settings.arpMode,preset=Math.floorMod(settings.arpPreset,HipHopArps.COUNT);
  int tick=arpStep&15,span=Math.max(1,Math.min(3,settings.arpOctaves));
  if(mode!=0 && HipHopArps.hit(preset,tick)){
   int[] chord=Scale.chord(e.slots.masterKey,settings.scale,settings.octave,0,3);
   int noteIndex=HipHopArps.degree(preset,arpStep,chord.length*span);
   if(mode==2)noteIndex=chord.length*span-1-noteIndex;
   else if(mode==3){int zig=Math.max(1,chord.length*span*2-2),v=arpStep%zig;noteIndex=v<chord.length*span?v:zig-v;}
   else if(mode==4)noteIndex=random.nextInt(chord.length*span);
   else if(mode==5)noteIndex=(arpStep%span)*chord.length;
   noteIndex=Math.max(0,Math.min(chord.length*span-1,noteIndex));
   int note=chord[noteIndex%chord.length]+12*(noteIndex/chord.length);
   if(e.acquireFocus()){arpHeld=new int[]{e.output.synth.on(5+settings.preset,note)};e.playing=true;e.output.wake();}
  }
  arpStep++;
  long base=(long)Math.max(30,60000.0/Math.max(30,e.slots.masterBpm)/(1<<Math.max(0,Math.min(4,settings.arpDivision))));
  long swing=(long)(base*Math.min(45,Math.max(0,settings.arpSwing))/100.0);
  arpNext+=base+((arpStep&1)==0?swing:-swing);
  handler.postAtTime(this,arpNext);e.changed();}};
 byte[] midi()throws java.io.IOException{ArrayList<MidiIO.Note> notes=new ArrayList<>();double bar=240./e.slots.masterBpm;for(int i=0;i<4;i++)for(int n:Scale.chord(e.slots.masterKey,settings.scale,settings.octave,DEGREES[settings.progression][i],Math.max(3,settings.chord)))notes.add(new MidiIO.Note(i*bar,(i+1)*bar-.02,n,96));return MidiIO.file(notes,e.slots.masterBpm,"EXITO · progresión "+SynthPresetBank.NAMES[settings.preset],new int[]{0,48,32,45,10}[settings.instrument]);}
}
