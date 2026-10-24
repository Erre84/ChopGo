package com.exito.chop;
/** Original approximate sampler colours; not calibrated replicas of hardware. */
final class Vintage {
 static final String[] NAMES={"Limpio","SP-1200 · 12 bit / 26,04 kHz","S900 · 12 bit / 7,5–40 kHz","S950 · 12 bit / 7,5–48 kHz","MPC3000 · 16 bit / 44,1 kHz"};
 final int sr;double clock,phase,lpL,lpR,outL,outR,heldL,heldR,rumble;int random=123455;final float[][] delay;int pos;final Filters.Role eq;
 Vintage(int rate){sr=rate;delay=new float[2][Math.max(32,(int)(sr*.04))];eq=new Filters.Role(rate);}
 void reset(){clock=phase=lpL=lpR=outL=outR=heldL=heldR=rumble=0;pos=0;for(float[] d:delay)java.util.Arrays.fill(d,0);for(int c=0;c<2;c++){eq.hp[c].clear();eq.peak[c].clear();}}
 double noise(){random^=random<<13;random^=random>>>17;random^=random<<5;return random/(double)Integer.MAX_VALUE;}
 void process(float[] data,int samples,PadParams p){eq.set(p.eq);int profile=p.profile;double hz=profile==1?26040:profile==2?7500+p.bandwidth*32500:profile==3?7500+p.bandwidth*40500:profile==4?44100:sr;double cutoff=profile==1?9000:Math.min(hz*.44,18000),alpha=1-Math.exp(-2*Math.PI*cutoff/sr);
 for(int i=0;i<samples;i+=2){double l=data[i],r=data[i+1];if(profile>0){if(profile<4)l=r=(l+r)*.5;lpL+=alpha*(l-lpL);lpR+=alpha*(r-lpR);phase+=hz/sr;if(phase>=1){phase-=Math.floor(phase);double levels=profile<4?2048:32768;heldL=Math.rint(Math.tanh(lpL*1.08)/1.08*levels)/levels;heldR=Math.rint(Math.tanh(lpR*1.08)/1.08*levels)/levels;}outL+=alpha*(heldL-outL);outR+=alpha*(heldR-outR);l=outL;r=outR;}if(p.twelveBit){l=Math.rint(l*2048)/2048;r=Math.rint(r*2048)/2048;}
 if(p.vinyl){double t=clock/sr,depth=.00015+.0012*p.wear,d=sr*(.015+Math.sin(t*4.084)*depth+Math.sin(t*47.75)*depth*.15),at=(pos-d+delay[0].length)%delay[0].length;int a=(int)at,b=(a+1)%delay[0].length;double f=at-a;delay[0][pos]=(float)l;delay[1][pos]=(float)r;l=delay[0][a]*(1-f)+delay[0][b]*f;r=delay[1][a]*(1-f)+delay[1][b]*f;rumble=.995*rumble+noise()*.00008;double dust=Math.abs(noise())>.9998?noise()*(.03+p.wear*.13):0,n=noise()*(.00025+p.wear*.0015)+rumble+dust;l+=n;r+=n*.9;pos=(pos+1)%delay[0].length;}
 data[i]=(float)eq.tick(l,0);data[i+1]=(float)eq.tick(r,1);clock++;}}
}
