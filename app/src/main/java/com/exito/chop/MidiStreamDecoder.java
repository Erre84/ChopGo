package com.exito.chop;

/** Running-status-aware MIDI 1.0 channel voice decoder; Android independent for tests. */
final class MidiStreamDecoder {
    interface Listener { void onMessage(int status, int data1, int data2); }
    private final Listener listener;
    private int status=0, first=-1;
    private boolean inSysex=false;
    MidiStreamDecoder(Listener l){listener=l;}
    synchronized void feed(byte[] data,int offset,int length){
        if(data==null||offset<0||length<0||offset+length>data.length)return;
        for(int i=offset;i<offset+length;i++){
            int b=data[i]&255;
            if(b>=0xf8)continue; // MIDI realtime can appear between any two bytes
            if(b==0xf0){inSysex=true;status=0;first=-1;continue;}
            if(b==0xf7){inSysex=false;status=0;first=-1;continue;}
            if(inSysex)continue;
            if(b>=0x80){
                first=-1;
                status=b<0xf0?b:0; // ignore system common messages
                continue;
            }
            if(status<0x80)continue;
            int kind=status&0xf0;
            if(kind==0xc0||kind==0xd0){listener.onMessage(status,b,0);continue;}
            if(first<0){first=b;continue;}
            listener.onMessage(status,first,b);first=-1;
        }
    }
}
