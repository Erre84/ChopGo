package com.exito.chop;
/** Estimates, not musical guarantees; source metadata remains user-editable. */
final class Analysis {
    static final String[] KEYS={"C","C#","D","D#","E","F","F#","G","G#","A","A#","B","Cm","C#m","Dm","D#m","Em","Fm","F#m","Gm","G#m","Am","A#m","Bm"};
    static boolean hasSignal(short[] p){long sum=0;int n=0,step=Math.max(2,p.length/8192);for(int i=0;i<p.length;i+=step){sum+=(long)p[i]*p[i];n++;}return n>0&&sum/(double)n>64;}
    static int transposedKey(int key,float semitones){return Math.floorMod(key%12+Math.round(semitones),12)+key/12*12;}
    static int[] estimate(short[] pcm,int sr){
        int frames=Math.min(pcm.length/2,sr*24),hop=Math.max(1,sr/150),n=frames/hop;
        double[] energy=new double[n],onset=new double[n];double mean=0;
        for(int i=0;i<n;i++){double e=0;for(int f=i*hop;f<(i+1)*hop;f+=4){double v=pcm[f*2]/32768.;e+=v*v;}energy[i]=Math.sqrt(e/Math.max(1,hop/4));if(i>0)onset[i]=Math.max(0,energy[i]-energy[i-1]);mean+=onset[i];}
        mean/=Math.max(1,n);double best=-1;int bpm=90;
        for(int candidate=60;candidate<=180;candidate++){int lag=(int)Math.round(150.*60/candidate);double score=0;for(int j=lag;j<n;j++)score+=(onset[j]-mean)*(onset[j-lag]-mean);score*=1-.12*Math.abs(candidate-100)/100.;if(score>best){best=score;bpm=candidate;}}
        double[] chroma=new double[12];int size=2048;
        for(int start=0;start+size*sr/8000<frames;start+=sr/3){
            double[] re=new double[size],im=new double[size];
            for(int i=0;i<size;i++){int f=start+(int)((long)i*sr/8000);re[i]=pcm[f*2]/32768.*(.5-.5*Math.cos(2*Math.PI*i/(size-1)));}
            fft(re,im);
            for(int k=18;k<512;k++){double freq=k*8000./size,midi=69+12*Math.log(freq/440.)/Math.log(2);int note=((int)Math.round(midi)%12+12)%12;double magnitude=Math.sqrt(re[k]*re[k]+im[k]*im[k]);chroma[note]+=Math.sqrt(magnitude)/Math.sqrt(freq);}
        }
        double[] major={6.35,2.23,3.48,2.33,4.38,4.09,2.52,5.19,2.39,3.66,2.29,2.88},minor={6.33,2.68,3.52,5.38,2.60,3.53,2.54,4.75,3.98,2.69,3.34,3.17};
        double cmean=0;for(double v:chroma)cmean+=v/12;double max=-Double.MAX_VALUE;int key=0;
        for(int mode=0;mode<2;mode++)for(int root=0;root<12;root++){double[] p=mode==0?major:minor;double pmean=0;for(double v:p)pmean+=v/12;double score=0,den=0;for(int j=0;j<12;j++){double x=chroma[(j+root)%12]-cmean,y=p[j]-pmean;score+=x*y;den+=y*y;}score/=Math.sqrt(den);if(score>max){max=score;key=root+mode*12;}}
        return new int[]{bpm,key};
    }
    static void fft(double[] re,double[] im){int n=re.length;for(int i=1,j=0;i<n;i++){int bit=n>>1;for(;(j&bit)!=0;bit>>=1)j^=bit;j^=bit;if(i<j){double t=re[i];re[i]=re[j];re[j]=t;}}
        for(int len=2;len<=n;len<<=1){double angle=-2*Math.PI/len;for(int start=0;start<n;start+=len)for(int j=0;j<len/2;j++){double co=Math.cos(angle*j),si=Math.sin(angle*j);int a=start+j,b=a+len/2;double tr=re[b]*co-im[b]*si,ti=re[b]*si+im[b]*co;re[b]=re[a]-tr;im[b]=im[a]-ti;re[a]+=tr;im[a]+=ti;}}}
}
