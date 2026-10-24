package com.exito.chop;

import java.util.ArrayList;

/** Bounded, in-memory stereo history for live input. Never writes a recording. */
final class LiveBuffer {
    final int rate;
    private final short[] ring;
    private int write=0,filled=0;
    private long total=0;
    private final ArrayList<Long> marks=new ArrayList<>();
    private LivePadTake padTake;
    LiveBuffer(int sampleRate,int seconds){rate=sampleRate;ring=new short[sampleRate*seconds*2];}
    synchronized void append(short[] data,int samples){
        samples-=samples%2;
        for(int i=0;i<samples;i++){ring[write]=data[i];write=(write+1)%ring.length;}
        filled=Math.min(ring.length,filled+samples);total+=samples/2;
        if(padTake!=null)padTake.append(data,samples);
        long oldest=total-filled/2;while(!marks.isEmpty()&&marks.get(0)<=oldest)marks.remove(0);
    }
    synchronized long frames(){return total;}
    synchronized LivePadTake armPads(int maxFrames){if(padTake!=null)throw new IllegalStateException("Ya hay una toma de pads abierta.");return padTake=new LivePadTake(rate,maxFrames);}
    synchronized int markPad(LivePadTake take){if(take!=padTake)throw new IllegalStateException("La entrada ha cambiado.");return take.mark(total);}
    synchronized int markPad(LivePadTake take,int latencyMs){
        if(latencyMs<=0)return markPad(take);
        if(take!=padTake)throw new IllegalStateException("La entrada ha cambiado.");
        int delay=(int)Math.min(filled/2,Math.max(0,Math.min(500,latencyMs))*rate/1000L);
        long at=total-delay;
        if(take.count()>0)return take.markHeard(at);
        int n=take.markHeard(at);
        if(delay>0){Window pre=window(1);take.append(java.util.Arrays.copyOfRange(pre.pcm,pre.pcm.length-delay*2,pre.pcm.length),delay*2);}
        return n;
    }
    synchronized LivePadTake.Result finishPads(LivePadTake take){if(take!=padTake)throw new IllegalStateException("La entrada ha cambiado.");padTake=null;return take.finish();}
    synchronized boolean mark(){if(marks.size()>=63)return false;long last=marks.isEmpty()?0:marks.get(marks.size()-1);if(total-last<rate*.045)return false;marks.add(total);return true;}
    synchronized void clearMarks(){marks.clear();}
    synchronized Window window(int seconds){
        int samples=Math.min(filled,Math.max(1,seconds)*rate*2);short[] data=new short[samples];
        int from=(write-samples+ring.length)%ring.length,n=Math.min(samples,ring.length-from);
        System.arraycopy(ring,from,data,0,n);System.arraycopy(ring,0,data,n,samples-n);
        long start=total-samples/2;ArrayList<Integer> cuts=new ArrayList<>();cuts.add(0);
        for(long mark:marks)if(mark>start&&mark<total)cuts.add((int)(mark-start));
        cuts.add(samples/2);int[] bounds=new int[cuts.size()];for(int i=0;i<bounds.length;i++)bounds[i]=cuts.get(i);
        return new Window(data,start,total,bounds);
    }
    static final class Window {
        final short[] pcm;final long start,end;final int[] manualCuts;
        Window(short[] data,long a,long b,int[] cuts){pcm=data;start=a;end=b;manualCuts=cuts;}
    }
}
