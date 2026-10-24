package com.exito.chop;

import java.util.ArrayList;
import java.util.Arrays;

/** Audio owned by a pad take, independent of the rolling input window. */
final class LivePadTake {
 final int rate,maxFrames;final int[] marks=new int[64];
 private final ArrayList<short[]> blocks=new ArrayList<>();
 private int frames,count;private boolean finished;long startFrame=-1;
 LivePadTake(int sampleRate,int capacityFrames){rate=sampleRate;maxFrames=Math.max(1,capacityFrames);}
 synchronized int count(){return count;}
 synchronized int frames(){return frames;}
 synchronized boolean full(){return frames>=maxFrames;}
 synchronized int mark(long absoluteFrame){return addMark(absoluteFrame,frames);}
 synchronized int markHeard(long absoluteFrame){return addMark(absoluteFrame,count==0?0:(int)Math.max(0,Math.min(frames,absoluteFrame-startFrame)));}
 private int addMark(long absoluteFrame,int frame){
  if(finished)throw new IllegalStateException("La toma ya está cerrada.");if(count>=64)throw new IllegalArgumentException("64 pads marcados. Pulsa Terminar pads.");
  if(count>0&&frame-marks[count-1]<Math.max(1,Math.round(rate*.045f)))throw new IllegalArgumentException("Deja al menos 45 ms entre marcas.");
  if(full())throw new IllegalArgumentException("Límite de audio alcanzado. Pulsa Terminar pads.");if(count==0){startFrame=absoluteFrame;frame=0;}marks[count++]=frame;return count;
 }
 synchronized void append(short[] pcm,int samples){
  if(finished||count==0)return;
  int n=Math.min(samples-samples%2,(maxFrames-frames)*2);if(n<=0)return;
  blocks.add(Arrays.copyOf(pcm,n));frames+=n/2;
 }
 synchronized double seconds(int pad){return pad<0||pad>=count?0:((pad+1<count?marks[pad+1]:frames)-marks[pad])/(double)rate;}
 synchronized Result finish(){
  finished=true;int n=count;while(n>0&&frames<=marks[n-1])n--;
  short[] pcm=new short[frames*2];int offset=0;for(short[] block:blocks){System.arraycopy(block,0,pcm,offset,block.length);offset+=block.length;}
  int[] cuts=n==0?new int[]{0,Math.max(1,frames)}:Arrays.copyOf(marks,n+1);if(n>0)cuts[n]=frames;
  blocks.clear();return new Result(pcm,cuts,n,startFrame);
 }
 static final class Result {final short[] pcm;final int[] cuts;final int count;final long startFrame;Result(short[] p,int[] c,int n,long s){pcm=p;cuts=c;count=n;startFrame=s;}}
}
