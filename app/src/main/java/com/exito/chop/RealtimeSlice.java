package com.exito.chop;
import java.nio.ShortBuffer;
/** A bounded streaming voice whose controls can change at its current source position. */
final class RealtimeSlice {
 final short[] pcm;final int rate,outputRate,from,to;private Sonic sonic;private int fed;private boolean ended;private float speed=1,pitch=1;private double cursor;private final short[] output=new short[4096];
 RealtimeSlice(short[] p,int rate,int outputRate,int from,int to){pcm=p;this.rate=rate;this.outputRate=outputRate;this.from=from;this.to=to;cursor=from;fed=from;}
 double position(){return cursor;}
 void parameters(float speed,float pitch){speed=Math.max(.5f,Math.min(2,speed));pitch=Math.max(.5f,Math.min(2,pitch));if(this.speed==speed&&this.pitch==pitch)return;this.speed=speed;this.pitch=pitch;sonic=null;fed=(int)cursor;ended=false;}
 int render(float[] out,int frames){
  java.util.Arrays.fill(out,0,frames*2,0);if(cursor>=to)return 0;double step=rate*(double)speed/outputRate;
  if(speed==1&&pitch==1){int n=0;while(n<frames&&cursor<to){for(int c=0;c<2;c++)out[n*2+c]=(float)VoiceMixer.sample(pcm,cursor,c,from,to);cursor+=step;n++;}return n;}
  if(sonic==null)sonic=new Sonic(rate,2,speed,pitch,outputRate);
  while(sonic.getOutputSize()<frames*4&&!ended){if(fed<to){int n=Math.min(1024,to-fed);sonic.queueInput(ShortBuffer.wrap(pcm,fed*2,n*2));fed+=n;}else{sonic.queueEndOfStream();ended=true;}}
  int n=Math.min(frames,sonic.getOutputSize()/4);if(n>0){sonic.getOutput(ShortBuffer.wrap(output,0,n*2));for(int i=0;i<n*2;i++)out[i]=output[i]/32768f;cursor=Math.min(to,cursor+n*step);}else if(ended)cursor=to;return n;
 }
}
