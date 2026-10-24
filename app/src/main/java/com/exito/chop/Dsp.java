package com.exito.chop;

import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Arrays;

/** Local DSP. No network, microphone input or external service. */
final class Dsp {
    static short[] stretch(short[] stereo, int sampleRate, float speed, float pitch) {
        if (Math.abs(speed - 1) < .0001 && Math.abs(pitch - 1) < .0001) return stereo;
        return Stretch.render(stereo,sampleRate,speed,pitch);
    }

    static int[] chops(short[] pcm, int sr, int requested) {return chops(pcm,sr,requested,-24);}
    static int[] chops(short[] pcm, int sr, int requested, float thresholdDb) {
        int frames = pcm.length / 2;
        if (frames < 2) return new int[]{0, Math.max(1, frames)};
        if(requested>0){
            int count=Math.max(1,Math.min(64,Math.min(requested,frames)));
            int[] out=new int[count+1];for(int i=0;i<=count;i++)out[i]=(int)((long)frames*i/count);return out;
        }
        int hop = 256, bins = Math.max(1, frames / hop);
        double[] rms = new double[bins];
        for (int k = 0; k < bins; k++) {
            double e = 0;
            for (int j = k * hop; j < Math.min(frames, (k + 1) * hop); j++) {
                double l=pcm[j*2]/32768.,r=pcm[j*2+1]/32768.;
                e += (l*l+r*r)*.5;
            }
            rms[k] = Math.sqrt(e / hop);
        }
        ArrayList<Integer> boundaries = new ArrayList<>(); boundaries.add(0);
        int minFrames=Math.max(32,(int)(.075*sr));boolean armed=true;
        double threshold=Math.pow(10,thresholdDb/20.);
        for(int k=0;k<bins;k++){
            if(rms[k]<threshold*.65)armed=true;
            int f=Math.max(0,k*hop-64);
            if(armed&&rms[k]>=threshold){
                if(f-boundaries.get(boundaries.size()-1)>=minFrames&&frames-f>=minFrames)boundaries.add(f);
                armed=false;
                if(boundaries.size()>=64)break;
            }
        }
        boundaries.add(frames);
        int[] result = new int[boundaries.size()];
        for (int i = 0; i < result.length; i++) result[i] = boundaries.get(i);
        return result;
    }

    static short[] drums(int sr){return drums(sr,0);}
    static short[] drums(int sr,int style){
        int frames=(int)(sr*8*60./90);short[] out=new short[frames*2];java.util.Random random=new java.util.Random(19);
        for(int i=0;i<frames;i++){
            double t=i/(double)sr,beat=t*1.5,p=beat-Math.floor(beat);int step=(int)beat;
            double kick=(style==2||step%4==0||step%4==2||(style==1&&step%8==7)?Math.sin(2*Math.PI*(49*t+2*(1-Math.exp(-p*25))))*Math.exp(-p*25)*.6:0);
            double snare=(step%2==1?(random.nextDouble()*2-1)*Math.exp(-p*32)*.4:0);
            double subdivision=style==1?4:2,h=beat*subdivision-Math.floor(beat*subdivision),hat=(random.nextDouble()*2-1)*Math.exp(-h*65)*.13;
            short v=(short)((kick+snare+hat)*27000);out[i*2]=v;out[i*2+1]=v;
        }return out;
    }

    static float[] peaks(short[] pcm, int n) {
        float[] out = new float[n];
        for (int k = 0; k < n; k++) {
            int from = (int) ((long) pcm.length * k / n), to = (int) ((long) pcm.length * (k + 1) / n);
            int peak = 0;
            for (int j = from; j < to; j += 2) peak = Math.max(peak, Math.abs((int) pcm[j]));
            out[k] = peak / 32768f;
        }
        return out;
    }

    static short[] demo(int sr) {
        int frames = (int) (sr * (16 * 60. / 90));
        short[] pcm = new short[frames * 2];
        java.util.Random random = new java.util.Random(812);
        for (int i = 0; i < frames; i++) {
            double t = i / (double) sr, beat = t * 1.5, phase = beat - Math.floor(beat);
            int step = (int) Math.floor(beat);
            double kick = Math.sin(2 * Math.PI * (48 * t + 4 * (1 - Math.exp(-phase * 25)))) * Math.exp(-phase * 26) * .55;
            double snare = (step % 2 == 1 ? (random.nextDouble() * 2 - 1) * Math.exp(-phase * 36) * .33 : 0);
            double hatPhase = beat * 2 - Math.floor(beat * 2);
            double hat = (random.nextDouble() * 2 - 1) * Math.exp(-hatPhase * 60) * .10;
            double bass = Math.sin(2 * Math.PI * (step / 4 % 2 == 0 ? 55 : 65.406) * t) * .14;
            double chord = (Math.sin(2 * Math.PI * 220 * t) + Math.sin(2 * Math.PI * 261.626 * t) + Math.sin(2 * Math.PI * 329.628 * t)) * .045 * Math.exp(-phase * 3);
            double fade = Math.min(1, Math.min(t / .01, (frames / (double) sr - t) / .025));
            double x = (kick + snare + hat + bass + chord) * fade;
            pcm[i * 2] = (short) (Math.max(-.95, Math.min(.95, x)) * 32767);
            pcm[i * 2 + 1] = (short) (Math.max(-.95, Math.min(.95, x + chord * .15)) * 32767);
        }
        return pcm;
    }

    static final class Fx {
        final int sr;
        final float[][] comb = new float[8][];
        final int[] positions = new int[8];
        final float[] damp = new float[8];
        double gain = 1;
        Fx(int sampleRate) {
            sr = sampleRate;
            int[] delays = {1116, 1188, 1277, 1356};
            for (int c = 0; c < 2; c++) for (int k = 0; k < 4; k++) comb[c * 4 + k] = new float[Math.max(8, (delays[k] + c * 23) * sr / 44100)];
        }
        void reset(){for(float[] b:comb)Arrays.fill(b,0);Arrays.fill(damp,0);gain=1;}
        void process(short[] data,int samples,float compression,float reverb){float[] b=new float[samples];for(int i=0;i<samples;i++)b[i]=data[i]/32768f;process(b,samples,compression,reverb);for(int i=0;i<samples;i++)data[i]=Mutator.quantize(b[i]);}
        void process(float[] data, int samples, float compression, float reverb) {
            double amount = compression / 100., threshold = -8 - 20 * amount, ratio = 1 + 3 * amount;
            double attack = Math.exp(-1. / (.005 * sr)), release = Math.exp(-1. / (.09 * sr));
            for (int i = 0; i < samples; i += 2) {
                double l = data[i], r = data[i + 1];
                double level = 20 * Math.log10(Math.max(1e-7, Math.max(Math.abs(l), Math.abs(r))));
                double gr = compression > 0 && level > threshold ? (threshold + (level - threshold) / ratio - level) : 0;
                double target = Math.pow(10, gr / 20), coeff = target < gain ? attack : release;
                gain = coeff * gain + (1 - coeff) * target;
                double makeup = Math.pow(10, amount * 1.8 / 20);
                for (int c = 0; c < 2; c++) {
                    double dry = (c == 0 ? l : r) * gain * makeup, wet = 0;
                    for (int k = 0; k < 4; k++) {
                        int ix = c * 4 + k, p = positions[ix];
                        float delayed = comb[ix][p];
                        damp[ix] = .75f * delayed + .25f * damp[ix];
                        comb[ix][p] = (float) (dry * .23 + damp[ix] * .73);
                        positions[ix] = (p + 1) % comb[ix].length;
                        wet += delayed;
                    }
                    double x = dry * (1 - reverb * .35) + wet * reverb;
                    data[i+c]=(float)x;
                }
            }
        }
    }
}
