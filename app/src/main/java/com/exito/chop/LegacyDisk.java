package com.exito.chop;
import java.util.*;
/** S900 disk/sample structures and packing researched from akaiutil, Klaus Michael Indlekofer, GPL-2.0-or-later. This port: GPL-3.0-or-later. */
final class LegacyDisk {
 static void little(byte[] b,int at,int value,int count){for(int i=0;i<count;i++)b[at+i]=(byte)(value>>(i*8));}
 static byte[] name(String text){byte[] out=new byte[10];Arrays.fill(out,(byte)' ');text=text.toUpperCase(Locale.US).replaceAll("[^A-Z0-9 .#+-]"," ");for(int i=0;i<Math.min(10,text.length());i++)out[i]=(byte)text.charAt(i);return out;}
 static byte[] sample(short[] mono,int rate,String title){int half=(mono.length+1)/2;byte[] b=new byte[60+3*half];System.arraycopy(name(title),0,b,0,10);little(b,16,half*2,4);little(b,20,rate,2);little(b,22,60*16,2);b[26]='O';little(b,28,mono.length,4);little(b,36,mono.length,4);b[43]='N';for(int i=0;i<half;i++){int a=mono[i],z=i+half<mono.length?mono[i+half]:0;b[60+i*2]=(byte)((a&240)|((z>>4)&15));b[61+i*2]=(byte)(a>>8);b[60+half*2+i]=(byte)(z>>8);}return b;}
 static final class Disk {final byte[] bytes=new byte[800*1024];int next=4,files;double seconds;boolean fits(byte[] sample,double duration){return files<32&&next+(sample.length+1023)/1024<=800&&seconds+duration<=11.5;}void add(byte[] sample,String name,double duration){if(!fits(sample,duration))throw new IllegalArgumentException("No cabe en el disco S900");int blocks=(sample.length+1023)/1024,entry=files*24;System.arraycopy(name(name),0,bytes,entry,10);bytes[entry+16]='S';little(bytes,entry+17,sample.length,3);little(bytes,entry+20,next,2);System.arraycopy(sample,0,bytes,next*1024,sample.length);for(int i=0;i<blocks;i++)little(bytes,1536+(next+i)*2,i==blocks-1?0x8000:next+i+1,2);next+=blocks;files++;seconds+=duration;}}
}
