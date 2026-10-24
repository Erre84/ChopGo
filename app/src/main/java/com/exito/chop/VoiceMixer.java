package com.exito.chop;
/** Independent streaming voices with one choke group per source. */
final class VoiceMixer {
 static final class Voice {short[] pcm;double cursor,step;int from,to,id,age,group=-1;boolean release;double gain=1;final Vintage colour;PadParams params;RealtimeSlice stream;float speed=1,pitch=1;Voice(int sr){colour=new Vintage(sr);}}
 final Voice[] voices=new Voice[16];final float[] scratch;final int sr;int ids,activeCount;volatile double playhead=-1;volatile short[] playheadPcm;
 // Touch-controlled vinyl transport. Only the target position changes on the UI thread.
 private short[] vinylPcm;private double vinylCursor,vinylTarget,vinylGain;private int vinylFrom,vinylTo,vinylGroup,vinylRate;private boolean vinylTouch,vinylReleasing;
 VoiceMixer(int rate){sr=rate;scratch=new float[4096];for(int i=0;i<voices.length;i++)voices[i]=new Voice(rate);}
 synchronized int on(short[] p,int rate,int from,int to,PadParams s){return on(p,rate,from,to,s,-1,false,1,1);}
 synchronized int on(short[] p,int rate,int from,int to,PadParams s,int group,boolean mono){return on(p,rate,from,to,s,group,mono,1,1);}
 synchronized void choke(int group){for(Voice v:voices)if(v.pcm!=null&&v.group==group)v.release=true;}
 synchronized int on(short[] pcm,int rate,int from,int to,PadParams p,int group,boolean mono,float speed,float pitch){if(mono&&group>=0)choke(group);Voice v=voices[0];for(Voice a:voices){if(a.pcm==null){v=a;break;}if(a.age>v.age)v=a;}v.pcm=pcm;v.from=Math.max(0,from);v.to=Math.min(pcm.length/2,to);v.cursor=v.from;v.step=rate/(double)sr;v.id=++ids;v.age=0;v.gain=1;v.release=false;v.group=group;v.params=p.copy();v.colour.reset();v.speed=speed;v.pitch=pitch;v.stream=new RealtimeSlice(pcm,rate,sr,v.from,v.to);v.stream.parameters(speed,pitch);return v.id;}
 synchronized void level(int id,float amount){for(Voice v:voices)if(v.id==id&&v.pcm!=null){v.gain=Math.max(0,Math.min(1,amount));break;}}
 synchronized void parameters(int group,float speed,float pitch){for(Voice v:voices)if(v.pcm!=null&&v.group==group&&!v.release){v.speed=speed;v.pitch=pitch;v.stream.parameters(speed,pitch);}}
 synchronized void vinylStart(short[] pcm,int rate,int from,int to,int frame,int group){
  if(pcm==null||pcm.length<4)return;
  vinylPcm=pcm;vinylRate=Math.max(8000,rate);vinylFrom=Math.max(0,Math.min(pcm.length/2-2,from));
  vinylTo=Math.min(pcm.length/2,Math.max(vinylFrom+2,to));vinylGroup=group;
  vinylCursor=vinylTarget=Math.max(vinylFrom,Math.min(vinylTo-1,frame));vinylGain=0;vinylTouch=true;vinylReleasing=false;
  choke(group);
 }
 synchronized void vinylMove(int frame){if(vinylPcm!=null&&vinylTouch)vinylTarget=Math.max(vinylFrom,Math.min(vinylTo-1,frame));}
 synchronized void vinylStop(){vinylTouch=false;vinylReleasing=true;}
 synchronized boolean vinylActive(){return vinylPcm!=null;}
 synchronized double vinylPosition(){return vinylPcm==null?-1:vinylCursor/Math.max(1,vinylPcm.length/2.);}
 /** Bidirectional scrub: samples advance only when the finger moves, with a short click-free ramp. */
 private void mixVinyl(float[] dest,int frames){
  if(vinylPcm==null)return;
  double maxStep=4.0*vinylRate/sr;
  double fade=1.0/Math.max(1,sr*.003);
  for(int i=0;i<frames;i++){
   double distance=vinylTarget-vinylCursor;
   double step=vinylReleasing?0:Math.max(-maxStep,Math.min(maxStep,distance));
   double desired=vinylReleasing||Math.abs(distance)<.2?0:Math.min(1,Math.abs(step)/Math.max(.01,vinylRate/(double)sr));
   vinylGain+=Math.max(-fade,Math.min(fade,desired-vinylGain));
   if(vinylGain>.00001){
    dest[i*2]+=(float)(sample(vinylPcm,vinylCursor,0,vinylFrom,vinylTo)*vinylGain);
    dest[i*2+1]+=(float)(sample(vinylPcm,vinylCursor,1,vinylFrom,vinylTo)*vinylGain);
   }
   vinylCursor=Math.max(vinylFrom,Math.min(vinylTo-1,vinylCursor+step));
  }
  if(vinylReleasing&&vinylGain<.00001){vinylPcm=null;vinylReleasing=false;}
 }
 synchronized float speed(int id){for(Voice v:voices)if(v.pcm!=null&&v.id==id)return v.speed;return 1;}
 synchronized boolean active(int id){for(Voice v:voices)if(v.pcm!=null&&v.id==id&&!v.release)return true;return false;}
 synchronized double position(int id){for(Voice v:voices)if(v.pcm!=null&&v.id==id&&!v.release)return v.cursor/(v.pcm.length/2.);return -1;}
 synchronized boolean empty(){if(vinylPcm!=null)return false;for(Voice v:voices)if(v.pcm!=null)return false;return true;}
 synchronized void off(int id){for(Voice v:voices)if(v.id==id)v.release=true;}
 synchronized void stop(){vinylPcm=null;vinylTouch=false;vinylReleasing=false;for(Voice v:voices){v.pcm=null;v.stream=null;}playhead=-1;playheadPcm=null;activeCount=0;}
 static double sample(short[] pcm,double cursor,int channel,int from,int to){int i=(int)cursor;double f=cursor-i;int a=Math.max(from,i-1),b=Math.max(from,Math.min(to-1,i)),c=Math.min(to-1,i+1),d=Math.min(to-1,i+2);double x0=pcm[a*2+channel]/32768.,x1=pcm[b*2+channel]/32768.,x2=pcm[c*2+channel]/32768.,x3=pcm[d*2+channel]/32768.;return x1+.5*f*(x2-x0+f*(2*x0-5*x1+4*x2-x3+f*(3*(x1-x2)+x3-x0)));}
 synchronized int render(float[] out,int frames){java.util.Arrays.fill(out,0,frames*2,0);int count=0;playhead=-1;playheadPcm=null;for(Voice v:voices){if(v.pcm==null)continue;count++;double begin=v.cursor;int n=v.stream.render(scratch,frames);double advance=(v.stream.position()-begin)/Math.max(1,n);for(int i=0;i<n;i++){double position=begin+i*advance,envelope=Math.min(1,Math.min((position-v.from+1)/(sr*v.step*.003),(v.to-position)/(sr*v.step*.003)))*v.gain;for(int c=0;c<2;c++)scratch[i*2+c]*=Math.max(0,envelope);v.age++;if(v.release)v.gain*=Math.exp(-1/(sr*.004));}v.cursor=v.stream.position();v.colour.process(scratch,frames*2,v.params);for(int i=0;i<frames*2;i++)out[i]+=scratch[i];if(v.cursor>=v.to||v.gain<.0001){v.pcm=null;v.stream=null;}else{playhead=v.cursor/(v.pcm.length/2.);playheadPcm=v.pcm;}}mixVinyl(out,frames);activeCount=count;return count;}
 synchronized int render(float[][] out,int frames,int[] routes){for(float[] bus:out)java.util.Arrays.fill(bus,0,frames*2,0);int count=0;playhead=-1;playheadPcm=null;for(Voice v:voices){if(v.pcm==null)continue;count++;double begin=v.cursor;int n=v.stream.render(scratch,frames);double advance=(v.stream.position()-begin)/Math.max(1,n);for(int i=0;i<n;i++){double position=begin+i*advance,envelope=Math.min(1,Math.min((position-v.from+1)/(sr*v.step*.003),(v.to-position)/(sr*v.step*.003)))*v.gain;for(int c=0;c<2;c++)scratch[i*2+c]*=Math.max(0,envelope);v.age++;if(v.release)v.gain*=Math.exp(-1/(sr*.004));}v.cursor=v.stream.position();v.colour.process(scratch,frames*2,v.params);for(int i=0;i<frames*2;i++)out[routes[Math.max(0,Math.min(routes.length-1,v.group))]][i]+=scratch[i];if(v.cursor>=v.to||v.gain<.0001){v.pcm=null;v.stream=null;}else{playhead=v.cursor/(v.pcm.length/2.);playheadPcm=v.pcm;}}if(vinylPcm!=null)mixVinyl(out[routes[Math.max(0,Math.min(routes.length-1,vinylGroup))]],frames);activeCount=count;return count;}
}
