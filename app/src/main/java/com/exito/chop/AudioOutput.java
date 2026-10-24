package com.exito.chop;
import android.media.*;
final class AudioOutput {
 final Engine e;final VoiceMixer mixer;final Synth synth;final int rate,burst;final Object wake=new Object();volatile String info="Salida inicializando…";volatile boolean reset=true;AudioTrack track;final Dsp.Fx fx;final Mutator mutation;final Heat heat;final MasterColour masterColour;final float[][] busAudio;double drumCursor;short[] lastDrums,lastDrumSource;int tail,underruns;
 AudioOutput(Engine engine,AudioManager manager){e=engine;int r=48000,b=192;try{r=Integer.parseInt(manager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE));b=Integer.parseInt(manager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER));}catch(Exception ignored){}rate=r>=8000&&r<=192000?r:48000;burst=Math.max(96,Math.min(256,b));mixer=new VoiceMixer(rate);synth=new Synth(rate);fx=new Dsp.Fx(rate);mutation=new Mutator(rate);heat=new Heat(rate);masterColour=new MasterColour(rate,e.masterColour);busAudio=new float[4][burst*2];new Thread(this::run,"EXITO-float-audio").start();}
 int on(short[] p,int sr,int from,int to,PadParams settings,int group,boolean mono){int id=mixer.on(p,sr,from,to,settings,group,mono);e.playing=true;wake();return id;}
 int on(short[] p,int sr,int from,int to,PadParams settings,int group,boolean mono,float speed,float pitch){int id=mixer.on(p,sr,from,to,settings,group,mono,speed,pitch);e.playing=true;wake();return id;}
 void wake(){synchronized(wake){wake.notifyAll();}}int on(short[] p,int sr,int from,int to,PadParams settings){int id=mixer.on(p,sr,from,to,settings);e.playing=true;wake();return id;}void off(int id){mixer.off(id);wake();}void stop(){mixer.stop();synth.stop();tail=0;reset=true;e.playing=false;wake();}
 void run(){AudioTrack out=null;try{android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);int min=AudioTrack.getMinBufferSize(rate,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_FLOAT);out=new AudioTrack.Builder().setAudioAttributes(e.attributes()).setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build()).setTransferMode(AudioTrack.MODE_STREAM).setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY).setBufferSizeInBytes(Math.max(min,burst*8*8)).build();if(out.getState()!=AudioTrack.STATE_INITIALIZED)throw new IllegalStateException("No se pudo abrir la salida float");track=out;out.setBufferSizeInFrames(burst*2);out.setStartThresholdInFrames(burst*2);info="Float · "+rate+" Hz · "+out.getBufferSizeInFrames()+" frames · "+(out.getPerformanceMode()==AudioTrack.PERFORMANCE_MODE_LOW_LATENCY?"baja latencia":"ruta del dispositivo");e.changed();float[] samples=new float[burst*2];boolean started=false;int blocks=0;
 for(;;){if(!e.playing&&!e.drums.playing&&synth.count==0&&tail==0&&mixer.empty()){if(started){out.pause();out.flush();started=false;reset=true;e.releaseFocus();e.changed();}synchronized(wake){if(!e.playing&&!e.drums.playing&&synth.count==0&&mixer.empty())wake.wait(500);}continue;}if(reset){fx.reset();mutation.reset();heat.reset();masterColour.reset();e.buses.clear();reset=false;}
 int voices=mixer.render(busAudio,burst,e.buses.routes);
 if(voices>0)tail=e.reverb>0||e.mutate.diffusion>0?(int)(rate*2.5):0;else tail=Math.max(0,tail-burst);
 synth.renderAdd(busAudio[BusMixer.clamp(e.buses.routes[8])],burst,e.harmony.settings.volume,e.harmony.settings.sineVolume);short[] loop=e.drums.rendered;if(loop!=lastDrums){drumCursor=lastDrums!=null&&loop!=null&&lastDrumSource==e.drums.pcm?drumCursor/(lastDrums.length/2.)*(loop.length/2.):0;lastDrums=loop;lastDrumSource=e.drums.pcm;}boolean drums=e.drums.playing&&loop!=null&&loop.length>=4;
 for(int i=0;i<burst;i++){
  if(drums){
    double fadeIn = drumCursor / Math.max(1.0, e.drums.rate * .001);
    double fadeOut = (loop.length / 2.0 - drumCursor) / Math.max(1.0, e.drums.rate * .001);
    double fade = Math.max(0.0, Math.min(1.0, Math.min(fadeIn, fadeOut)));
    int group = BusMixer.clamp(e.buses.routes[9]);
    for(int c=0;c<2;c++){
      busAudio[group][2*i+c] += VoiceMixer.sample(loop,drumCursor,c,0,loop.length/2) * e.drums.volume * fade;
    }
    drumCursor+=e.drums.rate/(double)rate;if(drumCursor>=loop.length/2)drumCursor%=loop.length/2;}
 }
 e.buses.process(busAudio,burst);BusMixer.sum(samples,busAudio,samples.length);
 e.mutate.bpm=e.slots.masterBpm;fx.process(samples,samples.length,e.compression,e.reverb);
 mutation.process(samples,samples.length,e.mutate);heat.process(samples,samples.length,e.heat,e.slots.masterBpm);masterColour.process(samples,samples.length);
 for(int i=0;i<samples.length;i++){double x=samples[i],a=Math.abs(x);if(a>.89)x=Math.copySign(.89+.11*Math.tanh((a-.89)/.11),x);samples[i]=(float)(Double.isFinite(x)?x:0);}
 if(!started){out.play();started=true;}for(int offset=0;offset<samples.length;){int n=out.write(samples,offset,samples.length-offset,AudioTrack.WRITE_BLOCKING);if(n<0)throw new IllegalStateException("Salida de audio "+n);if(n==0)break;offset+=n;}synchronized(mixer){synchronized(synth){if(mixer.empty()&&synth.count==0&&tail==0&&e.playing){e.playing=false;final int endedId=e.padPreviewId;e.main.post(()->e.padPreviewEnded(endedId));e.changed();}}}if(++blocks%128==0&&out.getUnderrunCount()>underruns){underruns=out.getUnderrunCount();int size=out.getBufferSizeInFrames();if(size<burst*8)out.setBufferSizeInFrames(size+burst);info="Float · "+rate+" Hz · "+out.getBufferSizeInFrames()+" frames · "+underruns+" interrupciones";}}
 }catch(Throwable error){info="Salida no disponible: "+Engine.reason(error);e.playing=false;e.drums.playing=false;e.message(info);}finally{track=null;if(out!=null)try{out.pause();out.flush();out.release();}catch(Exception ignored){}}}
}
