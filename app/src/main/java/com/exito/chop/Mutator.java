package com.exito.chop;

/** Original DSP approximations inspired by the publicly described categories of Chroma Console.
 * This does not contain Hologram code, impulse responses or a model of its hardware. */
final class Mutator {
    static final double[] SYNC_RATES={4,2,1,2./3,1.5,.5},SYNC_TIMES={.25,.5,1,1.5,2./3,2};
    static final String[][] NAMES={
        {"OFF","Drive","Sweeten","Fuzz","Howl","Swell"},
        {"OFF","Doubler","Vibrato","Phaser","Tremolo","Pitch"},
        {"OFF","Cascade","Reels","Space","Collage","Reverse"},
        {"OFF","Filter","Squash","Cassette","Broken","Interference"}
    };
    static final class Settings {
        volatile int character=0,movement=0,diffusion=0,texture=0;
        volatile float charAmount=.35f,moveAmount=.35f,diffAmount=.35f,texAmount=.35f;
        volatile float tone=.5f,rate=.3f,time=.3f,drift=.15f,mix=1;
        volatile float sensitivity=.5f,moveDrift=.15f,diffDrift=.15f,output=1,bpm=90;volatile int filterMode=0,phaserStages=6,division=2;volatile boolean sync=false;volatile float[] volumes={1,1,1,1};
        volatile int[] order={0,1,2,3};
        int type(int group){return group==0?character:group==1?movement:group==2?diffusion:texture;}
        void type(int group,int value){if(group==0)character=value;else if(group==1)movement=value;else if(group==2)diffusion=value;else texture=value;}
        float amount(int group){return group==0?charAmount:group==1?moveAmount:group==2?diffAmount:texAmount;}
        void amount(int group,float value){if(group==0)charAmount=value;else if(group==1)moveAmount=value;else if(group==2)diffAmount=value;else texAmount=value;}
        Settings copy(){Settings s=new Settings();s.character=character;s.movement=movement;s.diffusion=diffusion;s.texture=texture;s.charAmount=charAmount;s.moveAmount=moveAmount;s.diffAmount=diffAmount;s.texAmount=texAmount;s.tone=tone;s.rate=rate;s.time=time;s.drift=drift;s.mix=mix;s.order=order.clone();s.sensitivity=sensitivity;s.moveDrift=moveDrift;s.diffDrift=diffDrift;s.output=output;s.bpm=bpm;s.filterMode=filterMode;s.phaserStages=phaserStages;s.division=division;s.sync=sync;s.volumes=volumes.clone();return s;}
        boolean enabled(){return character+movement+diffusion+texture>0&&mix>0;}
    }
    final int sr,size;final float[][][] rings;final int[] pos=new int[4];final double[][] low=new double[4][2];
    final double[][] phX=new double[2][12],phY=new double[2][12],feedback=new double[4][2];
    final float[][] space=new float[8][];final int[] spacePos=new int[8];
    final double[] swell=new double[2],envelope=new double[2];
    long clock;int rng=134719;double random=0,targetRandom=0;final double[] charEnv=new double[2];
    Mutator(int sampleRate){sr=sampleRate;size=sr*3;rings=new float[4][2][size];int[] lengths={1116,1188,1277,1356};for(int c=0;c<2;c++)for(int k=0;k<4;k++)space[c*4+k]=new float[(lengths[k]+c*23)*sr/44100];}
    double noise(){rng^=rng<<13;rng^=rng>>>17;rng^=rng<<5;return rng/(double)Integer.MAX_VALUE;}
    double read(int group,int c,double delay){double p=pos[group]-Math.max(1,Math.min(size-2,delay));while(p<0)p+=size;int i=(int)p;double f=p-i;return rings[group][c][i]*(1-f)+rings[group][c][(i+1)%size]*f;}
    static double sat(double x,double drive){return Math.tanh(x*drive)/Math.tanh(drive);}
    double pitch(int group,int c,double x,double ratio){if(Math.abs(ratio-1)<.001)return x;double window=sr*.055,phase=(clock*(1-ratio)/window)%1;if(phase<0)phase++;double p2=(phase+.5)%1,w=.5-.5*Math.cos(phase*Math.PI*2);return read(group,c,sr*.015+phase*window)*w+read(group,c,sr*.015+p2*window)*(1-w);}
    void process(float[] data,int samples,Settings s){if(!s.enabled())return;
        for(int i=0;i<samples;i+=2){
            double dryL=data[i],dryR=data[i+1],l=dryL,r=dryR;
            if(clock%256==0)targetRandom=noise();random+=.0004*(targetRandom-random);
            for(int group:s.order){int type=s.type(group);double amount=s.amount(group);feedback[group][0]=feedback[group][1]=0;double outL=effect(group,type,0,l,amount,s),outR=effect(group,type,1,r,amount,s);rings[group][0][pos[group]]=(float)Math.max(-4,Math.min(4,l+feedback[group][0]));rings[group][1][pos[group]]=(float)Math.max(-4,Math.min(4,r+feedback[group][1]));pos[group]=(pos[group]+1)%size;l=outL*s.volumes[group];r=outR*s.volumes[group];}
            data[i]=(float)((dryL+(l-dryL)*s.mix)*s.output);data[i+1]=(float)((dryR+(r-dryR)*s.mix)*s.output);clock++;
        }
    }
    void reset(){for(float[][] a:rings)for(float[] b:a)java.util.Arrays.fill(b,0);for(float[] b:space)java.util.Arrays.fill(b,0);for(double[] b:low)java.util.Arrays.fill(b,0);for(double[] b:phX)java.util.Arrays.fill(b,0);for(double[] b:phY)java.util.Arrays.fill(b,0);java.util.Arrays.fill(envelope,0);java.util.Arrays.fill(swell,0);java.util.Arrays.fill(charEnv,0);random=targetRandom=0;clock=0;}
    void process(short[] data,int samples,Settings s){float[] b=new float[samples];for(int i=0;i<samples;i++)b[i]=data[i]/32768f;process(b,samples,s);for(int i=0;i<samples;i++)data[i]=quantize(b[i]);}
    static short quantize(double x){if(!Double.isFinite(x))x=0;if(Math.abs(x)>.92)x=Math.copySign(.92+.08*Math.tanh((Math.abs(x)-.92)/.08),x);return(short)(Math.max(-1,Math.min(.999969,x))*32768);}
    double effect(int g,int type,int c,double x,double a,Settings s){if(type==0||a<=0)return x;
        double t=clock/(double)sr,lfo=Math.sin(t*2*Math.PI*(s.sync?s.bpm/60.*SYNC_RATES[s.division]:.15+s.rate*7)+(c*.35)),y=x;
        if(g==0){
            double alpha=.025+.7*s.tone;low[g][c]+=alpha*(x-low[g][c]);double shaped=s.tone<.5?low[g][c]*2*s.tone+x*(1-2*s.tone):x+(x-low[g][c])*(s.tone-.5);
            switch(type){
                case 1:y=sat(shaped,1+a*8)*.85;break;
                case 2:{charEnv[c]=Math.max(Math.abs(shaped),charEnv[c]*.9997);double threshold=.3-a*.2,gr=charEnv[c]>threshold?Math.pow(threshold/charEnv[c],.5*a):1;y=sat(shaped*gr*(1+a*.4),1+a*1.8)*.94;break;}
                case 3:y=Math.max(-.8,Math.min(.8,shaped*(1+a*25)));if(Math.abs(x)<.003*a)y=0;break;
                case 4:{double f=2*Math.sin(Math.PI*(180+s.tone*1900)/sr),q=.2+(1-a)*.6;double lp=swell[c],bp=envelope[c];lp+=f*bp;double hp=shaped-lp-q*bp;bp+=f*hp;swell[c]=Math.max(-4,Math.min(4,lp));envelope[c]=Math.max(-4,Math.min(4,bp));y=sat(bp,1+a*6)*.65;break;}
                case 5:{double target=Math.min(1,Math.abs(x)*(2+s.sensitivity*22));double coefficient=target>swell[c]?Math.exp(-1/(sr*(.01+a*.6))):.999; swell[c]=coefficient*swell[c]+(1-coefficient)*target;y=x*Math.min(1,swell[c]*3);break;}
            }
        }else if(g==1){
            switch(type){
                case 1:y=x+read(g,c,sr*(.009+.035*s.rate+.003*a*lfo))*.65*a;break;
                case 2:y=x*(1-a)+read(g,c,sr*(.015+.011*a*(lfo+s.drift*s.moveDrift*2*random*.3)))*a;break;
                case 3:{double coef=.25+.65*(lfo*.5+.5),v=x;for(int stage=0;stage<Math.max(2,Math.min(12,s.phaserStages));stage++){double z=-coef*v+phX[c][stage]+coef*phY[c][stage];phX[c][stage]=v;phY[c][stage]=z;v=z;}y=x*(1-a*.6)+v*a*.6;break;}
                case 4:{double wave=a>.78?(lfo>0?1:0):(lfo*.5+.5);y=x*(1-a+a*wave);break;}
                case 5:y=x*(1-a)+pitch(g,c,x,Math.pow(2,(s.rate-.5)*2))*a;break;
            }
        }else if(g==2){
            double seconds=s.sync?60./Math.max(30,s.bpm)*SYNC_TIMES[Math.max(0,Math.min(5,s.division))]:.055+s.time*.70;
            if(type==1||type==2){double mod=(type==2?.003:.0006)*(lfo+random*s.drift*s.diffDrift*2),d=read(g,c,sr*(seconds+mod));low[g][c]+=(type==1?.18:.43)*(d-low[g][c]);feedback[g][c]=sat(low[g][c]*(.14+a*.7),1+(type==2?2:1)*a);y=x+d*a*.8;
            }else if(type==3){double sum=0;for(int k=0;k<4;k++){int ix=c*4+k,p=spacePos[ix];float z=space[ix][p];space[ix][p]=(float)(x*.18+z*(.5+s.time*.32));spacePos[ix]=(p+1)%space[ix].length;sum+=z;}y=x*(1-a*.6)+sum*a;}
            else if(type==4){int window=Math.max(128,(int)(sr*(.055+s.time*.32)));long block=clock/window;double phase=clock%window;double delay=window+(block%4==0?phase:phase*.5);y=x*(1-a*.65)+read(g,c,delay)*a;}
            else if(type==5){int window=Math.max(128,(int)(sr*(.04+s.time*.35)));double phase=clock%window;double win=Math.sin(Math.PI*phase/window);y=x*(1-a*.5)+read(g,c,window+phase*2)*a*win;}
        }else{
            switch(type){
                case 1:{double cutoff=s.tone<.5?100*Math.pow(180,s.tone*2):30*Math.pow(70,(s.tone-.5)*2),alpha=1-Math.exp(-2*Math.PI*cutoff/sr);low[g][c]+=alpha*(x-low[g][c]);double filtered=s.filterMode==1?low[g][c]:s.filterMode==2?x-low[g][c]:s.tone<.5?low[g][c]:x-low[g][c];y=x*(1-a)+filtered*a;break;}
                case 2:{double env=Math.max(Math.abs(x),envelope[c]*.9995);envelope[c]=env;double limit=.4-a*.32,scale=env>limit?Math.pow(limit/env,.82):1;y=sat(x*scale*(1+a),1+a*3);break;}
                case 3:{double wow=Math.sin(t*2*Math.PI*.65)*.0015+Math.sin(t*2*Math.PI*7.1)*.00045;double v=read(g,c,sr*(.012+wow*a*(1+s.drift)));low[g][c]+=(.18-.12*a)*(v-low[g][c]);y=x*(1-a)+sat(low[g][c],1+a*3)*a;break;}
                case 4:{double cycle=(t*(.3+s.rate*2))%1,drop=cycle>.78?(.5+.5*Math.sin(t*30)):1;double v=read(g,c,sr*(.01+.035*a*(1-drop)));y=x*(1-a)+v*drop*a;break;}
                case 5:{double levels=Math.pow(2,14-10*a),v=Math.rint(x*levels)/levels;double disturbance=noise()*.025*a*(.5+.5*Math.sin(t*1.9));if(Math.sin(t*(6+s.rate*30))>.98)v*=1-a;y=v+disturbance;break;}
            }
        }
        return Double.isFinite(y)?Math.max(-4,Math.min(4,y)):0;
    }
}
