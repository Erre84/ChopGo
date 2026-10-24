package com.exito.chop;
import org.json.*;
import java.util.Arrays;
/** Four independent stereo buses, routed from eight source decks, instruments and drums.
 * Every signal visits exactly one bus before the master. No allocation in render(). */
final class BusMixer {
 static final String[] NAMES={"BUS 1 · Samples","BUS 2 · Drums","BUS 3 · Instrumentos","BUS 4 · Auxiliar"};
 static final String[] TRACKS={"Deck 1","Deck 2","Deck 3","Deck 4","Deck 5","Deck 6","Deck 7","Deck 8","Instrumentos","Drums","Web Deck"};
 final int[] routes={0,0,0,0,0,0,0,0,2,1,3};
 final Settings[] settings=new Settings[4];
 final Filter[] processors=new Filter[4];
 static final class Settings {volatile float low=0,mid=0,high=0,drive=0,mix=1,level=1;volatile boolean mute=false;volatile String name="";}
 static final class Filter {
  final float[] lo=new float[2],hi=new float[2],tail=new float[2]; final float[] echo;
  int position; final float lowCoef,highCoef;
  Filter(int rate){lowCoef=(float)(1-Math.exp(-2*Math.PI*180/rate));highCoef=(float)(1-Math.exp(-2*Math.PI*3800/rate));echo=new float[Math.max(2,(int)(rate*.19)*2)];}
  void clear(){Arrays.fill(lo,0);Arrays.fill(hi,0);Arrays.fill(echo,0);position=0;}
  void process(float[] audio,int frames,Settings s){
   if(s.mute){Arrays.fill(audio,0,frames*2,0);return;}
   float gl=(float)Math.pow(10,s.low/20),gm=(float)Math.pow(10,s.mid/20),gh=(float)Math.pow(10,s.high/20);
   float drive=Math.max(0,s.drive),wet=Math.max(0,Math.min(1,s.mix)),gain=Math.max(0,Math.min(2,s.level));
   for(int i=0;i<frames;i++)for(int ch=0;ch<2;ch++){
     int k=2*i+ch;float x=audio[k];lo[ch]+=lowCoef*(x-lo[ch]);hi[ch]+=highCoef*(x-hi[ch]);
     float y=lo[ch]*gl+(hi[ch]-lo[ch])*gm+(x-hi[ch])*gh;
     // Independent bus drive; dry/wet lets the caller keep the original signal.
     if(drive>0){float pre=(float)Math.tanh(y*(1+drive*7));y=(float)(pre/Math.tanh(1+drive*7));}
     audio[k]=gain*(x*(1-wet)+y*wet);
   }
  }
 }
 BusMixer(int rate){for(int i=0;i<4;i++){settings[i]=new Settings();settings[i].name=NAMES[i];processors[i]=new Filter(rate);}}
 static int clamp(int r){return Math.max(0,Math.min(3,r));}
 synchronized JSONObject json()throws JSONException{JSONObject o=new JSONObject();JSONArray r=new JSONArray();for(int route:routes)r.put(route);o.put("routes",r);JSONArray f=new JSONArray();for(Settings s:settings)f.put(new JSONObject().put("name",s.name).put("low",s.low).put("mid",s.mid).put("high",s.high).put("drive",s.drive).put("mix",s.mix).put("level",s.level).put("mute",s.mute));o.put("buses",f);return o;}
 synchronized void read(JSONObject o){if(o==null){reset();return;}JSONArray r=o.optJSONArray("routes"),f=o.optJSONArray("buses");for(int i=0;i<routes.length;i++)if(r!=null)routes[i]=clamp(r.optInt(i,i==8?2:i==9?1:i==10?3:0));for(int i=0;i<4;i++){JSONObject x=f==null?null:f.optJSONObject(i);if(x==null)continue;Settings s=settings[i];s.name=x.optString("name",NAMES[i]);s.low=(float)x.optDouble("low",0);s.mid=(float)x.optDouble("mid",0);s.high=(float)x.optDouble("high",0);s.drive=(float)x.optDouble("drive",0);s.mix=(float)x.optDouble("mix",1);s.level=(float)x.optDouble("level",1);s.mute=x.optBoolean("mute",false);}clear();}
 synchronized void reset(){for(int i=0;i<routes.length;i++)routes[i]=i==8?2:i==9?1:i==10?3:0;for(int i=0;i<4;i++){Settings s=settings[i];s.name=NAMES[i];s.low=s.mid=s.high=s.drive=0;s.mix=s.level=1;s.mute=false;}clear();}
 void clear(){for(Filter f:processors)f.clear();}
 void process(float[][] buffers,int frames){for(int i=0;i<4;i++)processors[i].process(buffers[i],frames,settings[i]);}
 void processSingle(float[] buffer,int frames,int bus){processors[bus].process(buffer,frames,settings[bus]);}
 static void sum(float[] target,float[][] buffers,int n){Arrays.fill(target,0,n,0);for(float[] b:buffers)for(int i=0;i<n;i++)target[i]+=b[i];}
}
