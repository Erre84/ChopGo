package com.exito.chop;
/** Clean, phase-continuous single-frequency sine WAV generator. 16-bit stereo. */
final class SineTone {
 private SineTone(){}
 static short[] renderStereo(int midi,int sampleRate,int durationMs,int attackMs,int releaseMs,float level){
  if(sampleRate<8000||sampleRate>192000)throw new IllegalArgumentException("Frecuencia de muestreo no válida");
  int frames=(int)Math.round(sampleRate*Math.max(100,Math.min(8000,durationMs))/1000.0);
  int attack=Math.max(1,Math.min(frames/2,attackMs*sampleRate/1000));
  int release=Math.max(1,Math.min(frames/2,releaseMs*sampleRate/1000));
  double frequency=440*Math.pow(2,(midi-69)/12.0);
  double amplitude=.35*Math.max(0,Math.min(1,level));
  short[] pcm=new short[frames*2];double phase=0,step=2*Math.PI*frequency/sampleRate;
  for(int i=0;i<frames;i++){
   double in=i>=attack?1:0.5-0.5*Math.cos(Math.PI*i/attack);
   int tail=frames-1-i;
   double out=tail>=release?1:0.5-0.5*Math.cos(Math.PI*Math.max(0,tail)/release);
   double x=Math.sin(phase)*amplitude*in*out;
   short value=(short)Math.round(Math.max(-1,Math.min(1,x))*32767);
   pcm[2*i]=pcm[2*i+1]=value;
   phase+=step;if(phase>2*Math.PI)phase-=2*Math.PI;
  }
  return pcm;
 }
}
