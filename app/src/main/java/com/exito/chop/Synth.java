package com.exito.chop;
final class Synth {
 static final String[] NAMES={"Piano · síntesis","Strings · síntesis","Bajo","Pluck","Campanas"};
 static final float[] SINE=new float[4097];static{for(int i=0;i<SINE.length;i++)SINE[i]=(float)Math.sin(i*Math.PI*2/4096);}
 static double sin(double p){p-=Math.floor(p);double at=p*4096;int i=(int)at;return SINE[i]+(SINE[i+1]-SINE[i])*(at-i);}
 static double blep(double p,double dt){if(p<dt){p/=dt;return 2*p-p*p-1;}if(p>1-dt){p=(p-1)/dt;return p*p+2*p+1;}return 0;}
 static final class Voice {int id,note,instrument,patch=-1;boolean active,release;double phase,phase2,phase3,age,gain,frequency,shape=.5,decay=1,attack=.003,releaseTime=.06;}
 final Voice[] voices=new Voice[24];final int sr;int ids;volatile int count;
 Synth(int rate){sr=rate;for(int i=0;i<voices.length;i++)voices[i]=new Voice();}
 synchronized int on(int instrument,int note){Voice v=voices[0];for(Voice a:voices){if(!a.active){v=a;break;}if(a.age>v.age)v=a;}v.id=++ids;v.note=note;v.patch=instrument>=5?Math.max(0,Math.min(59,instrument-5)):-1;
 v.instrument=v.patch<0?Math.max(0,Math.min(4,instrument)):SynthPresetBank.category(v.patch);
 if(v.patch>=0){int variant=SynthPresetBank.variation(v.patch);v.shape=.25+.06*(variant%8);v.decay=.58+variant*.11;v.attack=v.instrument==1?.055+variant*.018:v.instrument==4?.0015:v.instrument==2?.002:.002+variant*.0003;v.releaseTime=v.instrument==1?.18+variant*.05:v.instrument==4?.16+variant*.03:.06+variant*.009;}
 else{v.shape=.5;v.decay=1;v.attack=.003;v.releaseTime=.06;}
 v.frequency=440*Math.pow(2,(note-69)/12.);v.phase=v.phase2=v.phase3=v.age=0;v.gain=1;v.active=true;v.release=false;count++;return v.id;}
 synchronized int onSine(int note,float attack,float release){
  int id=on(0,note);
  for(Voice v:voices)if(v.id==id){v.instrument=5;v.patch=-1;v.attack=Math.max(.0005,Math.min(.5,attack));v.releaseTime=Math.max(.005,Math.min(2,release));break;}
  return id;
 }
 synchronized void retune(int id,int note){for(Voice v:voices)if(v.active&&v.id==id){v.note=note;v.frequency=440*Math.pow(2,(note-69)/12.);break;}}
 synchronized void off(int id){for(Voice v:voices)if(v.id==id)v.release=true;}synchronized void stop(){for(Voice v:voices)v.active=false;count=0;}
 synchronized void renderAdd(float[] out,int frames,float volume,float sineVolume){int active=0;for(Voice v:voices){if(!v.active)continue;double step=v.frequency/sr;for(int i=0;i<frames;i++){if(v.gain<.0001){v.active=false;break;}double t=v.age,x,env;if(v.instrument==5){x=sin(v.phase);env=Math.min(1,t/Math.max(.0005,v.attack));}else if(v.instrument==0){x=sin(v.phase)*.65+sin(t*v.frequency*2.002)*.22*Math.exp(-t*2)+sin(t*v.frequency*3.005)*.13*Math.exp(-t*3)+sin(t*v.frequency*4.012)*.06*Math.exp(-t*4);env=(1-Math.exp(-t*600))*Math.exp(-t*(.7+v.frequency/1600));}else if(v.instrument==1){x=((2*v.phase-1-blep(v.phase,step))+(2*v.phase2-1-blep(v.phase2,step*1.004))+(2*v.phase3-1-blep(v.phase3,step*.996)))*.25;env=Math.min(1,t/.13);}else if(v.instrument==2){x=sin(v.phase)*.8+sin(v.phase*2)*.15+sin(v.phase*3)*.05;env=(1-Math.exp(-t*500))*(.65+.35*Math.exp(-t*3));}else if(v.instrument==3){x=sin(v.phase)*.7+sin(v.phase*2)*.22*Math.exp(-t*8)+sin(v.phase*4)*.08*Math.exp(-t*16);env=(1-Math.exp(-t*900))*Math.exp(-t*2.5);}else{x=sin(v.phase+.7*sin(t*v.frequency*2.71)*Math.exp(-t*2))*.65+sin(t*v.frequency*4.07)*.18*Math.exp(-t*3)+sin(t*v.frequency*6.13)*.08*Math.exp(-t*4);env=(1-Math.exp(-t*1000))*Math.exp(-t*1.2);}if(v.patch>=0){double drive=(v.shape-.3)*1.7;x=x*(.85+v.shape*.3)+Math.sin(2*Math.PI*v.phase*(v.instrument==1?2:3))*.07*drive;
    if(v.instrument==1)env*=Math.min(1,t/Math.max(.002,v.attack));
    else env*=Math.min(1,t/Math.max(.0005,v.attack))*Math.exp(-Math.max(0,t-.01)*Math.max(0,1/v.decay-1)*.8);
   }
   if(v.release)v.gain*=Math.exp(-1/(sr*v.releaseTime));if(v.release&&v.gain*env<.0001||(!v.release&&v.instrument!=1&&v.instrument!=2&&env<.0001&&t>1)){v.active=false;break;}double signal=x*env*v.gain*(v.instrument==5?sineVolume:volume)*.35;out[i*2]+=(float)signal;out[i*2+1]+=(float)signal;v.phase=(v.phase+step)%1;v.phase2=(v.phase2+step*1.004)%1;v.phase3=(v.phase3+step*.996)%1;v.age+=1./sr;}if(v.active)active++;}count=active;}
}
