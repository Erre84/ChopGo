package com.exito.chop;
/** Windowed-sinc resampling; rate conversion preserves pitch and duration. */
final class Resampler {
 final double ratio;final double[][] weights=new double[1024][32];
 Resampler(int input,int output){ratio=input/(double)output;double cutoff=.94*Math.min(1,output/(double)input);for(int phase=0;phase<1024;phase++){double frac=phase/1024.,sum=0;for(int k=0;k<32;k++){double x=(k-15-frac)*cutoff,w=.42-.5*Math.cos(2*Math.PI*k/31)+.08*Math.cos(4*Math.PI*k/31),s=Math.abs(x)<1e-9?1:Math.sin(Math.PI*x)/(Math.PI*x);weights[phase][k]=s*w*cutoff;sum+=weights[phase][k];}for(int k=0;k<32;k++)weights[phase][k]/=sum;}}
 double read(short[] pcm,int outputFrame,int channel,int from,int to){double at=from+outputFrame*ratio;if(at<from||at>=to)return 0;int frame=(int)at,phase=Math.min(1023,(int)((at-frame)*1024));double sum=0;for(int k=0;k<32;k++){int p=frame+k-15;if(p<from||p>=to)continue;double x=channel<0?(pcm[p*2]+(double)pcm[p*2+1])/65536.:pcm[p*2+channel]/32768.;sum+=x*weights[phase][k];}return sum;}
 static short[] convert(short[] pcm,int fromRate,int toRate,boolean mono){Resampler r=new Resampler(fromRate,toRate);int frames=(int)Math.round(pcm.length/2.*toRate/fromRate),channels=mono?1:2;short[] out=new short[frames*channels];for(int i=0;i<frames;i++)for(int c=0;c<channels;c++)out[i*channels+c]=(short)Math.round(Math.max(-1,Math.min(.999969,r.read(pcm,i,mono?-1:c,0,pcm.length/2)))*32768);return out;}
}
