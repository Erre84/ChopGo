package com.exito.chop;
final class Filters {
 static final String[] ROLES={"Neutra","Drumloop","Kick","Snare","Hi-hat","Bajo","Sample alto","Sample bajo","Varios"};
 static final class Biquad {double b0=1,b1,b2,a1,a2,z1,z2;double tick(double x){double y=b0*x+z1;z1=b1*x-a1*y+z2;z2=b2*x-a2*y;return y;}void clear(){z1=z2=0;}
 void set(int kind,double freq,double q,double db,int sr){freq=Math.max(15,Math.min(sr*.45,freq));double w=2*Math.PI*freq/sr,c=Math.cos(w),s=Math.sin(w),alpha=s/(2*q),a=Math.pow(10,db/40),a0=1+alpha;
 if(kind==0){b0=(1-c)/2;b1=1-c;b2=b0;a1=-2*c;a2=1-alpha;}else if(kind==1){b0=(1+c)/2;b1=-1-c;b2=b0;a1=-2*c;a2=1-alpha;}else{b0=1+alpha*a;b1=-2*c;b2=1-alpha*a;a0=1+alpha/a;a1=-2*c;a2=1-alpha/a;}
 b0/=a0;b1/=a0;b2/=a0;a1/=a0;a2/=a0;}}
 static final class Role {final Biquad[] hp={new Biquad(),new Biquad()},peak={new Biquad(),new Biquad()};int role=-1,sr;Role(int rate){sr=rate;}void set(int r){if(role==r)return;role=r;double[] lows={15,25,25,100,350,25,180,30,30},freqs={1000,85,70,2200,8500,100,1600,250,1000},gains={0,1,1.5,1,1,1,1,-1,0};for(int c=0;c<2;c++){hp[c].set(1,lows[r],.707,0,sr);peak[c].set(2,freqs[r],.8,gains[r],sr);hp[c].clear();peak[c].clear();}}double tick(double x,int c){return role<=0?x:peak[c].tick(hp[c].tick(x));}}
 static final class Svf {double ic1,ic2;double tick(double x,double freq,double resonance,int type,int sr){double g=Math.tan(Math.PI*Math.max(20,Math.min(sr*.42,freq))/sr),k=2-1.9*Math.max(0,Math.min(1,resonance)),a=1/(1+g*(g+k)),v1=(ic1+g*(x-ic2))*a,v2=ic2+g*v1;ic1=2*v1-ic1;ic2=2*v2-ic2;double hp=x-k*v1-v2;return type==1?v2:type==2?hp:type==3?v1:type==4?v2+hp:type==5?hp+v2-k*v1:type==6?v2+v1:hp+v1;}void clear(){ic1=ic2=0;}}
}
