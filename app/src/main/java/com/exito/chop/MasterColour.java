package com.exito.chop;
/** Non-destructive master bit-depth/rate coloration, not a circuit-accurate sampler emulator.
 * All DSP is on the rendered stereo mix: project source PCM always remains untouched. */
final class MasterColour {
 static final String[] BIT_LABELS={"8 bits","10 bits","12 bits","16 bits"};
 static final int[] BITS={8,10,12,16};
 static final String[] RATE_LABELS={"8 kHz","11,025 kHz","16 kHz","22,05 kHz","26,04 kHz","32 kHz","40 kHz","44,1 kHz","48 kHz"};
 static final int[] RATES={8000,11025,16000,22050,26040,32000,40000,44100,48000};
 static final String[] MODE_LABELS={"DIGITAL / LP suave","S900 · LP 12 dB/oct aprox.","S950 · LP 12 dB/oct aprox."};
 static final class Settings {
  volatile boolean enabled=false;
  volatile int bitsIndex=3,rateIndex=7,filter=0;
  volatile float cutoff=.82f,mix=1f;
  void preset(int mode){enabled=mode!=0;filter=mode==2?2:mode==1?1:0;bitsIndex=mode==0?3:2;rateIndex=mode==0?7:mode==1?6:8;cutoff=mode==0?.82f:mode==1?.55f:.68f;mix=1;}
  void reset(){enabled=false;bitsIndex=3;rateIndex=7;filter=0;cutoff=.82f;mix=1;}
  Settings copy(){Settings s=new Settings();s.enabled=enabled;s.bitsIndex=bitsIndex;s.rateIndex=rateIndex;s.filter=filter;s.cutoff=cutoff;s.mix=mix;return s;}
 }
 final int outputRate;final Settings settings;private double phase;
 private final double[] one={0,0},two={0,0},held={0,0};
 MasterColour(int outputRate,Settings settings){this.outputRate=Math.max(8000,outputRate);this.settings=settings;}
 static int bound(int n,int max){return Math.max(0,Math.min(max-1,n));}
 static float limit(float n){return Math.max(0,Math.min(1,n));}
 void reset(){phase=0;for(int i=0;i<2;i++){one[i]=two[i]=held[i]=0;}}
 void process(float[] stereo,int interleavedLength){
  Settings s=settings;if(!s.enabled)return;
  int count=Math.min(stereo.length,interleavedLength)/2;
  int rate=Math.min(outputRate,RATES[bound(s.rateIndex,RATES.length)]);
  int bits=BITS[bound(s.bitsIndex,BITS.length)];int mode=bound(s.filter,3);
  float mix=limit(s.mix);if(mix<=0)return;
  double tone=limit(s.cutoff),maxBand=mode==1?16000:mode==2?19200:20000;
  // Both old Akai machines used a low-pass voice filter; we model a 2-pole slope,
  // not their analog switched-capacitor response, envelopes or their full circuitry.
  double cutoff=Math.max(180,Math.min(Math.min(maxBand,rate*.43),280+tone*tone*(maxBand-280)));
  double alpha=1-Math.exp(-2*Math.PI*cutoff/outputRate);
  double q=(1L<<(bits-1))-1.;
  for(int frame=0;frame<count;frame++){
   int i=2*frame;
   boolean capture=(phase+=rate/(double)outputRate)>=1;
   if(capture)phase-=Math.floor(phase);
   for(int ch=0;ch<2;ch++){
    int k=i+ch;double x=stereo[k];if(!Double.isFinite(x))x=0;
    one[ch]+=alpha*(x-one[ch]);
    // Selectable double-pole vintage LP vs single-pole anti-alias LP.
    if(mode!=0)two[ch]+=alpha*(one[ch]-two[ch]);
    double signal=mode==0?one[ch]:two[ch];
    if(capture){
     if(mode==1)signal=Math.tanh(signal*1.10)/Math.tanh(1.10);
     if(mode==2)signal=Math.tanh(signal*1.045)/Math.tanh(1.045);
     held[ch]=Math.rint(Math.max(-1,Math.min(1,signal))*q)/q;
    }
    stereo[k]=(float)(x*(1-mix)+held[ch]*mix);
   }
  }
 }
}
