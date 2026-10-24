package com.exito.chop;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.*;
import android.media.projection.*;
import android.os.*;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Continuous internal input; no record/stop/import cycle and no WAV written here. */
public final class CaptureService extends Service {
    static volatile CaptureService current;LiveVideoRecorder videoRecorder;
    static LiveVideoRecorder beginVideo(LiveBuffer input)throws Exception{CaptureService c=current;if(c==null||!c.running||c.buffer!=input||c.projection==null)throw new IOException("La entrada de pantalla no está disponible.");if(c.videoRecorder!=null)throw new IOException("Cierra la toma antes de iniciar otro vídeo.");return c.videoRecorder=new LiveVideoRecorder(c,c.projection,input);}
    volatile boolean running=false;
    volatile AudioRecord recorder;
    MediaProjection projection;
    LiveBuffer buffer;
    final AtomicBoolean renderQueued=new AtomicBoolean();
    long lastRender=0,lastAnalysis=0,started=0,lastSignal=0;
    int inputBpm=90,inputKey=0;
    boolean metadata=false;
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent==null){stopSelf();return START_NOT_STICKY;}
        if("stop".equals(intent.getAction())){halt();return START_NOT_STICKY;}
        if(running)return START_NOT_STICKY;
        Engine e=Engine.get(this);
        try{
            startForeground(202,Notifs.make(this,"Entrada en directo · esperando audio de YouTube",CaptureService.class),ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            Intent data=intent.getParcelableExtra("projection");if(data==null)throw new IllegalArgumentException("Falta el permiso de audio interno.");
            MediaProjectionManager manager=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            projection=manager.getMediaProjection(intent.getIntExtra("result",Activity.RESULT_CANCELED),data);
            projection.registerCallback(new MediaProjection.Callback(){@Override public void onStop(){halt();}},new Handler(Looper.getMainLooper()));
            int uid=getPackageManager().getApplicationInfo(e.inputPackage,0).uid;
            AudioPlaybackCaptureConfiguration.Builder capture=new AudioPlaybackCaptureConfiguration.Builder(projection);
            if(e.inputAll)capture.excludeUid(android.os.Process.myUid());else capture.addMatchingUid(getPackageManager().getApplicationInfo(e.inputPackage,0).uid);
            AudioPlaybackCaptureConfiguration config=capture.addMatchingUsage(AudioAttributes.USAGE_MEDIA).addMatchingUsage(AudioAttributes.USAGE_UNKNOWN).addMatchingUsage(AudioAttributes.USAGE_GAME).build();
            int min=AudioRecord.getMinBufferSize(44100,AudioFormat.CHANNEL_IN_STEREO,AudioFormat.ENCODING_PCM_16BIT);
            recorder=new AudioRecord.Builder().setAudioFormat(new AudioFormat.Builder().setSampleRate(44100).setChannelMask(AudioFormat.CHANNEL_IN_STEREO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setAudioPlaybackCaptureConfig(config).setBufferSizeInBytes(Math.max(16384,min*2)).build();
            if(recorder.getState()!=AudioRecord.STATE_INITIALIZED)throw new IllegalStateException("El dispositivo no permite iniciar la entrada.");
            e.history.begin("Entrada de audio en directo");e.sequencer.stop();e.drums.stop();e.stop();e.slots.save();e.padParams=PadParams.defaults();e.selected=0;e.semitones=0;e.tempo=1;
            buffer=new LiveBuffer(44100,32);e.liveBuffer=buffer;e.liveReady=false;e.liveMetadataReady=false;e.liveMetadataOverride=false;e.liveMode=true;e.chopMode=PadChops.EMPTY;e.padMarking=true;e.manualTransform=false;
            e.capturing=true;e.captureSeconds=0;e.capturedFrames=0;e.inputDb=-96;e.inputPrompt=false;
            e.liveStatus="Esperando audio · "+e.inputLabel+". Reproduce el vídeo.";started=SystemClock.elapsedRealtime();
            running=true;current=this;recorder.startRecording();e.message(e.liveStatus);
            new Thread(this::read,"EXITO-live-input").start();
            if(e.inputAll)startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));else MainActivity.openYouTube(this);
        }catch(Throwable error){
            e.inputPrompt=false;e.liveMode=false;e.capturing=false;e.liveStatus="Entrada no disponible: "+Engine.reason(error);e.message(e.liveStatus);cleanup();stopSelf();
        }
        return START_NOT_STICKY;
    }
    void read(){Engine e=Engine.get(this);boolean failed=false;long lastUi=0;
        try{
            short[] block=new short[2048];
            while(running){
                AudioRecord input=recorder;if(input==null)break;
                int n=input.read(block,0,block.length,AudioRecord.READ_BLOCKING);
                if(n<0){if(running)throw new IOException("Error de audio interno "+n);break;}
                if(n==0)continue;n-=n%2;long now=SystemClock.elapsedRealtime();int peak=0;
                for(int i=0;i<n;i++)peak=Math.max(peak,Math.abs((int)block[i]));
                e.inputDb=(float)Math.max(-96,20*Math.log10(Math.max(1,peak)/32768.));
                if(peak>=8)lastSignal=now;
                buffer.append(block,n);e.capturedFrames=(int)Math.min(Integer.MAX_VALUE,buffer.frames());
                e.captureSeconds=(int)(buffer.frames()/44100);e.analyseInput(buffer);
                if(now-lastUi>=200){
                    lastUi=now;
                    if(e.livePadTake!=null)e.liveStatus="PAD DIRECTO · "+e.livePadTake.count()+"/64 · "+String.format(java.util.Locale.US,"%.1f s",e.livePadTake.frames()/(double)buffer.rate)+(lastSignal>0&&now-lastSignal<1200?" · audio entrando":" · comprueba que YouTube suene");
                    else if(e.playing||e.drums.playing)e.liveStatus="Escuchando chops/drums · YouTube puede quedar pausado por Android.";
                    else if(lastSignal>0&&now-lastSignal<1200)e.liveStatus="Audio entrando · últimos "+Math.min(e.captureSeconds,e.captureLimit)+" s · cortes al vuelo";
                    else if(now-started>5000)e.liveStatus="Sin señal. Reproduce YouTube; si ya suena, la entrada puede estar bloqueada.";
                    else e.liveStatus="Esperando audio · "+e.inputLabel+". Reproduce el vídeo.";
                    e.changed();LivePadTake take=e.livePadTake;if(take!=null&&take.full())e.main.post(()->{if(e.livePadTake==take)e.finishPadMarking(true);});
                }
                if(peak>=8&&buffer.frames()>4410&&now-lastRender>=600&&!e.busy&&renderQueued.compareAndSet(false,true)){
                    lastRender=now;publish(now);
                }
            }
        }catch(Throwable error){failed=true;e.liveStatus="Entrada interrumpida: "+Engine.reason(error);e.message(e.liveStatus);}
        finally{
            running=false;cleanup();
            if(e.liveBuffer==buffer){LivePadTake interrupted=e.livePadTake;if(interrupted!=null)e.main.post(()->{if(e.livePadTake==interrupted)e.finishPadMarking(false);});e.liveBuffer=null;e.capturing=false;e.liveMode=false;e.inputPrompt=false;e.inputDb=-96;
                if(!failed){e.liveStatus=e.liveReady?"Entrada desconectada. El último fragmento sigue disponible.":"Entrada desconectada sin recibir audio. La fuente anterior se conserva.";e.message(e.liveStatus);}e.changed();}
            stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
        }
    }
    void publish(long now){
        Engine e=Engine.get(this);final LiveBuffer current=buffer;
        if(e.livePadTake!=null){final LivePadTake take=e.livePadTake;final LiveBuffer.Window recent=current.window(1);e.worker.execute(()->{try{float[] wave=Dsp.peaks(recent.pcm,1024);e.main.post(()->{if(e.livePadTake==take){e.waveform=wave;e.changed();if(take.full())e.finishPadMarking(true);}});}finally{renderQueued.set(false);}});return;}
        final LiveBuffer.Window window=current.window(e.captureLimit);
        final float tempo=e.tempo,pitch=e.semitones,threshold=e.thresholdDb;final String mode=e.chopMode;final int count=e.requestedChops,index=e.slots.active;
        e.worker.execute(()->{try{
            int[] cuts=PadChops.isMode(mode)?new int[]{0,window.pcm.length/2}:mode.equals("manual")?window.manualCuts:Dsp.chops(window.pcm,44100,mode.equals("threshold")?0:count,threshold);
            float[] wave=Dsp.peaks(window.pcm,1024);
            short[] rendered=Dsp.stretch(window.pcm,44100,tempo,(float)Math.pow(2,pitch/12.));
            e.main.post(()->{try{
                if(!running||e.livePadTake!=null||e.liveBuffer!=current||e.slots.active!=index||e.busy||e.tempo!=tempo||e.semitones!=pitch||!e.chopMode.equals(mode))return;
                e.source=window.pcm;e.rendered=rendered;e.rate=44100;e.waveform=wave;e.cuts=cuts;e.selected=Math.min(e.selected,cuts.length-2);e.video=null;e.name=e.inputLabel+" · entrada en directo";
                e.liveReady=true;e.changed();
            }finally{renderQueued.set(false);}});
        }catch(Throwable error){renderQueued.set(false);e.liveStatus="No se pudo actualizar la onda: "+Engine.reason(error);e.changed();}});
    }
    void halt(){running=false;AudioRecord input=recorder;if(input!=null)try{input.stop();}catch(Exception ignored){}if(input==null)stopSelf();}
    void cleanup(){if(current==this)current=null;if(videoRecorder!=null)videoRecorder.requestStop();AudioRecord input=recorder;recorder=null;if(input!=null){try{input.stop();}catch(Exception ignored){}input.release();}if(projection!=null){MediaProjection old=projection;projection=null;old.stop();}}
    @Override public void onDestroy(){halt();super.onDestroy();}
}
