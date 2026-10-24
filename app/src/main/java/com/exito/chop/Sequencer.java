package com.exito.chop;
import android.os.*;
import java.util.Arrays;
final class Sequencer {
    final Engine e;final Handler handler=new Handler(Looper.getMainLooper());final int[] source=new int[16],pad=new int[16],level=new int[16];volatile boolean playing=false;volatile boolean followDeck=true;int cursor=-1;long next;int copiedSource=-1,copiedPad=-1,copiedLevel=16;
    int noteClipboardSource=-1,noteClipboardPad=-1,noteClipboardLevel=16;
    boolean levelsEnabled=false,repeatHeld=false;int levelsPad=0,repeatDivision=16;
    Sequencer(Engine engine){e=engine;Arrays.fill(source,-1);Arrays.fill(pad,-1);Arrays.fill(level,16);}
    void assign(int i){if(!e.ready()||!e.hasPad(e.selected))return;e.history.edit("Asignar paso",()->{source[i]=e.slots.active;pad[i]=e.selected;level[i]=16;});}
    void clear(int i){e.history.edit("Borrar paso",()->{source[i]=-1;pad[i]=-1;level[i]=16;});}
    void copyNote(int step){if(step<0||step>=16||source[step]<0){e.message("Selecciona un paso con nota para copiar");return;}noteClipboardSource=source[step];noteClipboardPad=pad[step];noteClipboardLevel=level[step];e.message("Nota del paso "+(step+1)+" copiada. Usa PEGAR NOTA en otro paso.");}
    void pasteNote(int step){if(step<0||step>=16||noteClipboardSource<0){e.message("Copia una nota primero");return;}e.history.edit("Pegar nota",()->{source[step]=noteClipboardSource;pad[step]=noteClipboardPad;level[step]=noteClipboardLevel;});}
    void duplicateNote(int step){if(step<0||step>=15||source[step]<0){e.message("Para duplicar selecciona un paso ocupado entre 1 y 15");return;}e.history.edit("Duplicar nota",()->{source[step+1]=source[step];pad[step+1]=pad[step];level[step+1]=level[step];});}
    void copyDeck(int from,int to){if(from<0||to<0||from>=8||to>=8||from==to){e.message("Elige dos decks diferentes");return;}
      e.slots.save();Slots.Slot original=e.slots.list[from];if(original.source==null){e.message("No hay audio en ese deck");return;}
      e.history.edit("Copiar deck",()->{Slots.Slot s=new Slots.Slot();s.source=original.source;s.rendered=original.rendered;s.waveform=original.waveform==null?null:original.waveform.clone();s.cuts=original.cuts==null?null:original.cuts.clone();s.pads=new PadParams[64];for(int k=0;k<64;k++)s.pads[k]=original.pads[k].copy();s.rate=original.rate;s.bpm=original.bpm;s.key=original.key;s.selected=original.selected;s.pitch=original.pitch;s.tempo=original.tempo;s.name=original.name+" · copia";s.mode=original.mode;s.youtube=original.youtube;s.video=original.video;s.mono=original.mono;s.videoOffsetMs=original.videoOffsetMs;
        e.slots.list[to]=s;if(e.slots.active==to)e.slots.apply();});}
    void clearDeck(int deck){if(deck<0||deck>=8)return;e.slots.clear(deck);for(int i=0;i<16;i++)if(source[i]==deck)clear(i);}
    void editVelocity(int step,int next){if(step<0||step>=16||source[step]<0)return;e.history.edit("Nivel de nota",()->level[step]=Math.max(1,Math.min(16,next)));}
    void setRepeatDivision(int d){if(d!=4&&d!=8&&d!=16&&d!=32)return;repeatDivision=d;e.changed();}
    void toggle(){if(playing){stop();return;}if(e.busy||e.capturing){e.message("Termina la carga o captura antes de secuenciar.");return;}e.slots.save();boolean notes=false;for(int s:source)notes|=s>=0;if(!notes){e.message("Selecciona un chop y toca los pasos para programarlo. Mantén un paso para borrarlo.");return;}playing=true;cursor=-1;next=SystemClock.uptimeMillis();handler.post(tick);}
    final Runnable tick=new Runnable(){public void run(){if(!playing)return;cursor=(cursor+1)%16;int s=source[cursor],p=pad[cursor];if(s>=0&&e.slots.list[s].source!=null&&!e.busy){
 if(followDeck&&!e.capturing){if(e.slots.active!=s)e.slots.activate(s);if(e.slots.active==s){e.select(p);e.midiBank=p/16;}}
 e.playLevel(s,p,level[cursor]);
 }e.changed();next+=(long)(60000./e.slots.masterBpm/4);handler.postAtTime(this,next);}};
    void stop(){playing=false;cursor=-1;handler.removeCallbacks(tick);e.stop();e.changed();}
}
